package com.yume.studio.audio

import androidx.annotation.DrawableRes
import com.yume.studio.R

/** Звук из SoundFont (bank/program) или пресет собственного синта (program = индекс пресета). */
data class SoundPreset(val name: String, val bank: Int, val program: Int)

enum class InstrumentType(
    val title: String,
    val engineKind: Int,
    @DrawableRes val art: Int,
    @DrawableRes val avatar: Int,
    val colorIndex: Int,
) {
    KEYS("Клавиши", NativeEngine.KIND_SF2, R.drawable.art_piano, R.drawable.ava_piano, 0),
    GUITAR("Гитара", NativeEngine.KIND_SF2, R.drawable.art_guitar, R.drawable.ava_guitar, 1),
    BASS("Бас", NativeEngine.KIND_SF2, R.drawable.art_bass, R.drawable.ava_bass, 2),
    DRUMS("Ударные", NativeEngine.KIND_DRUMS, R.drawable.art_drums, R.drawable.ava_drums, 3),
    SYNTH("Синтезатор", NativeEngine.KIND_SYNTH, R.drawable.art_synth, R.drawable.ava_synth, 4),
    STRINGS("Струнные", NativeEngine.KIND_SF2, R.drawable.art_strings, R.drawable.ava_strings, 5),
    AUDIO("Вокал", NativeEngine.KIND_AUDIO, R.drawable.art_vocal, R.drawable.ava_vocal, 6);

    val presets: List<SoundPreset> get() = Instruments.presetsFor(this)
    val isMidi: Boolean get() = this != AUDIO
}

/** Артикуляции струнных. */
enum class Articulation(val title: String) { LEGATO("Легато"), STACCATO("Стаккато"), PIZZICATO("Пиццикато"), TREMOLO("Тремоло") }

object Instruments {
    val keys = listOf(
        SoundPreset("Рояль", 0, 0),
        SoundPreset("Яркий рояль", 0, 1),
        SoundPreset("Электророяль", 0, 2),
        SoundPreset("Хонки-тонк", 0, 3),
        SoundPreset("Rhodes", 0, 4),
        SoundPreset("Rhodes + хорус", 8, 4),
        SoundPreset("FM-пиано", 0, 5),
        SoundPreset("FM-пиано + хорус", 8, 5),
        SoundPreset("Клавесин", 0, 6),
        SoundPreset("Клавинет", 0, 7),
        SoundPreset("Челеста", 0, 8),
        SoundPreset("Глокеншпиль", 0, 9),
        SoundPreset("Музыкальная шкатулка", 0, 10),
        SoundPreset("Вибрафон", 0, 11),
        SoundPreset("Маримба", 0, 12),
        SoundPreset("Ксилофон", 0, 13),
        SoundPreset("Колокола", 0, 14),
        SoundPreset("Орган Хаммонд", 0, 16),
        SoundPreset("Перкуссионный орган", 0, 17),
        SoundPreset("Рок-орган", 0, 18),
        SoundPreset("Церковный орган", 0, 19),
        SoundPreset("Аккордеон", 0, 21),
        SoundPreset("Губная гармошка", 0, 22),
        SoundPreset("Хор «А-а»", 0, 52),
        SoundPreset("Голос «У-у»", 0, 53),
        SoundPreset("Синт-голос", 0, 54),
        SoundPreset("Флейта", 0, 73),
        SoundPreset("Пан-флейта", 0, 75),
        SoundPreset("Окарина", 0, 79),
        SoundPreset("Сякухати", 0, 77),
        SoundPreset("Кларнет", 0, 71),
        SoundPreset("Гобой", 0, 68),
        SoundPreset("Альт-саксофон", 0, 65),
        SoundPreset("Труба", 0, 56),
        SoundPreset("Валторны", 0, 60),
        SoundPreset("Медная секция", 0, 61),
        SoundPreset("Арфа", 0, 46),
        SoundPreset("Кото", 0, 107),
        SoundPreset("Сямисэн", 0, 106),
        SoundPreset("Калимба", 0, 108),
        SoundPreset("Стил-драм", 0, 114),
        SoundPreset("Tinker Bell", 0, 112),
        SoundPreset("Halo Pad", 0, 94),
        SoundPreset("Warm Pad", 0, 89),
        SoundPreset("Crystal", 0, 98),
        SoundPreset("Atmosphere", 0, 99),
        SoundPreset("Star Theme", 0, 103),
    )

    val guitars = listOf(
        SoundPreset("Нейлон", 0, 24),
        SoundPreset("Акустика", 0, 25),
        SoundPreset("12-струнная", 8, 25),
        SoundPreset("Укулеле", 8, 24),
        SoundPreset("Мандолина", 16, 25),
        SoundPreset("Джаз", 0, 26),
        SoundPreset("Чистая электро", 0, 27),
        SoundPreset("Чистая + хорус", 8, 27),
        SoundPreset("Фанк", 8, 28),
        SoundPreset("Глушёная", 0, 28),
        SoundPreset("Овердрайв", 0, 29),
        SoundPreset("Дисторшн", 0, 30),
        SoundPreset("Фидбэк", 8, 30),
        SoundPreset("Флажолеты", 0, 31),
        SoundPreset("Банджо", 0, 105),
        SoundPreset("Ситар", 0, 104),
    )

    val basses = listOf(
        SoundPreset("Пальцевый", 0, 33),
        SoundPreset("Медиатор", 0, 34),
        SoundPreset("Безладовый", 0, 35),
        SoundPreset("Слэп", 0, 36),
        SoundPreset("Слэп 2", 0, 37),
        SoundPreset("Контрабас", 0, 32),
        SoundPreset("Синт-бас", 0, 38),
        SoundPreset("Синт-бас 2", 0, 39),
        SoundPreset("Synth Bass 101", 1, 38),
        SoundPreset("Acid", 8, 38),
        SoundPreset("FM-бас", 8, 39),
    )

    val drumKits = listOf(
        SoundPreset("Стандарт", 128, 0),
        SoundPreset("Стандарт 2", 128, 1),
        SoundPreset("Стандарт 3", 128, 2),
        SoundPreset("Комната", 128, 8),
        SoundPreset("Пауэр", 128, 16),
        SoundPreset("Электро", 128, 24),
        SoundPreset("808 / 909", 128, 25),
        SoundPreset("Дэнс", 128, 26),
        SoundPreset("Джаз", 128, 32),
        SoundPreset("Щётки", 128, 40),
        SoundPreset("Оркестр", 128, 48),
        SoundPreset("SFX", 128, 56),
    )

    /** Секции струнных; артикуляции меняют программу (см. stringsProgram). */
    val strings = listOf(
        SoundPreset("Скрипки", 0, 40),
        SoundPreset("Альты", 0, 41),
        SoundPreset("Виолончели", 0, 42),
        SoundPreset("Контрабасы", 0, 43),
        SoundPreset("Ансамбль", 0, 48),
        SoundPreset("Медленный ансамбль", 0, 49),
        SoundPreset("Синт-струнные", 0, 50),
        SoundPreset("Оркестровый пэд", 8, 48),
    )

    val synths = SynthPresets.all.mapIndexed { i, p -> SoundPreset(p.name, 0, i) }

    val audio = listOf(
        SoundPreset("Вокал", 0, 0),
        SoundPreset("Линейный вход", 0, 1),
    )

    fun presetsFor(type: InstrumentType): List<SoundPreset> = when (type) {
        InstrumentType.KEYS -> keys
        InstrumentType.GUITAR -> guitars
        InstrumentType.BASS -> basses
        InstrumentType.DRUMS -> drumKits
        InstrumentType.SYNTH -> synths
        InstrumentType.STRINGS -> strings
        InstrumentType.AUDIO -> audio
    }

    /** Реальная GM-программа для струнных с учётом артикуляции. */
    fun stringsProgram(section: SoundPreset, art: Articulation): Pair<Int, Int> = when (art) {
        Articulation.PIZZICATO -> 0 to 45
        Articulation.TREMOLO -> 0 to 44
        else -> section.bank to section.program
    }

    /** Подбор диапазона нот на экране для разных секций (первая нота). */
    fun stringsBaseNote(section: SoundPreset): Int = when (section.program) {
        40 -> 55   // скрипки от G3
        41 -> 48   // альты от C3
        42 -> 36   // виолончели от C2
        43 -> 28   // контрабасы от E1
        else -> 43
    }
}

/** GM-раскладка ударных для пэдов. */
data class DrumPad(val name: String, val key: Int, val colorIndex: Int)

object DrumMap {
    val pads = listOf(
        DrumPad("Бочка", 36, 0),
        DrumPad("Малый", 38, 1),
        DrumPad("Хлопок", 39, 2),
        DrumPad("Римшот", 37, 3),
        DrumPad("Хэт", 42, 4),
        DrumPad("Хэт откр.", 46, 5),
        DrumPad("Хэт нога", 44, 4),
        DrumPad("Крэш", 49, 6),
        DrumPad("Том низ.", 45, 7),
        DrumPad("Том сред.", 47, 7),
        DrumPad("Том выс.", 50, 7),
        DrumPad("Райд", 51, 6),
        DrumPad("Бубен", 54, 2),
        DrumPad("Ковбелл", 56, 3),
        DrumPad("Шейкер", 70, 5),
        DrumPad("Малый 2", 40, 1),
    )

    /** Дорожки степ-секвенсора (снизу вверх в GarageBand-стиле — сверху вниз здесь). */
    val stepRows = listOf(
        DrumPad("Бочка", 36, 0),
        DrumPad("Малый", 38, 1),
        DrumPad("Хлопок", 39, 2),
        DrumPad("Хэт", 42, 4),
        DrumPad("Хэт откр.", 46, 5),
        DrumPad("Том", 47, 7),
        DrumPad("Крэш", 49, 6),
        DrumPad("Шейкер", 70, 3),
    )

    fun name(key: Int): String = pads.firstOrNull { it.key == key }?.name ?: "Нота $key"
}

object NoteNames {
    private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val namesRu = arrayOf("До", "До#", "Ре", "Ре#", "Ми", "Фа", "Фа#", "Соль", "Соль#", "Ля", "Ля#", "Си")
    fun name(key: Int): String = names[key.mod(12)] + (key / 12 - 1)
    fun pitchClass(pc: Int): String = names[pc.mod(12)]
    fun pitchClassRu(pc: Int): String = namesRu[pc.mod(12)]
    fun isBlack(key: Int): Boolean = key.mod(12) in intArrayOf(1, 3, 6, 8, 10)
}
