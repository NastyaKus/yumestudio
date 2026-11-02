package com.yume.studio.data

import android.content.Context
import com.yume.studio.audio.InstrumentType
import com.yume.studio.model.Project
import com.yume.studio.model.ProjectMeta
import com.yume.studio.model.Track
import com.yume.studio.model.meta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Хранение проектов: filesDir/projects/<id>/project.json + папка audio с WAV-файлами. */
class ProjectRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    private val mutex = Mutex()
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    private val _projects = MutableStateFlow<List<ProjectMeta>>(emptyList())
    val projects: StateFlow<List<ProjectMeta>> = _projects.asStateFlow()

    fun projectDir(id: String) = File(root, id)
    fun audioDir(id: String) = File(projectDir(id), "audio").apply { mkdirs() }
    fun exportsDir() = File(context.getExternalFilesDir(null) ?: context.filesDir, "exports").apply { mkdirs() }
    private fun projectFile(id: String) = File(projectDir(id), "project.json")

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val list = root.listFiles()?.mapNotNull { dir ->
            runCatching { json.decodeFromString<Project>(File(dir, "project.json").readText()).meta() }.getOrNull()
        }.orEmpty().sortedByDescending { it.updatedAt }
        _projects.value = list
    }

    suspend fun load(id: String): Project? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<Project>(projectFile(id).readText()) }.getOrNull()
    }

    suspend fun save(project: Project) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val dir = projectDir(project.id).apply { mkdirs() }
            val tmp = File(dir, "project.json.tmp")
            tmp.writeText(json.encodeToString(Project.serializer(), project))
            tmp.renameTo(projectFile(project.id))
        }
        val meta = project.meta()
        _projects.value = (listOf(meta) + _projects.value.filter { it.id != project.id }).sortedByDescending { it.updatedAt }
    }

    suspend fun create(name: String? = null, firstTrack: InstrumentType? = null): Project {
        val id = UUID.randomUUID().toString().take(12)
        val n = _projects.value.size + 1
        val tracks = firstTrack?.let { listOf(Track(id = System.nanoTime(), name = it.title, type = it)) }.orEmpty()
        val p = Project(id = id, name = name ?: "Новый трек $n", tracks = tracks)
        save(p)
        return p
    }

    suspend fun rename(id: String, name: String) {
        val p = load(id) ?: return
        save(p.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: String): Project? = withContext(Dispatchers.IO) {
        val p = load(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString().take(12)
        projectDir(id).copyRecursively(projectDir(newId), overwrite = true)
        val copy = p.copy(id = newId, name = p.name + " (копия)", updatedAt = System.currentTimeMillis())
        save(copy)
        copy
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        projectDir(id).deleteRecursively()
        _projects.value = _projects.value.filter { it.id != id }
    }

    /** Экспорт проекта в .yume (zip с JSON и аудио). */
    suspend fun exportArchive(id: String, out: OutputStream) = withContext(Dispatchers.IO) {
        val dir = projectDir(id)
        ZipOutputStream(out.buffered()).use { zip ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun importArchive(input: InputStream): Project? = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString().take(12)
        val dir = projectDir(newId).apply { mkdirs() }
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val target = File(dir, e.name).canonicalFile
                if (target.path.startsWith(dir.canonicalPath) && !e.isDirectory) {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
                e = zip.nextEntry
            }
        }
        val p = load(newId) ?: run { dir.deleteRecursively(); return@withContext null }
        val fixed = p.copy(id = newId, updatedAt = System.currentTimeMillis())
        save(fixed)
        fixed
    }
}
