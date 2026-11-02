package com.yume.studio

import android.app.Application
import com.yume.studio.audio.EngineBridge
import com.yume.studio.data.Prefs
import com.yume.studio.data.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YumeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: ProjectRepository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var engine: EngineBridge
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProjectRepository(this)
        prefs = Prefs(this)
        engine = EngineBridge(this)
        appScope.launch { repository.refresh() }
        appScope.launch { engine.init() }
    }
}
