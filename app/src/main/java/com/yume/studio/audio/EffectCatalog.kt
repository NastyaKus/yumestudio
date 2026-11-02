package com.yume.studio.audio

/** Параметр эффекта (значение 0..1, как в нативном движке). */
data class FxParamDef(val name: String, val default: Float, val steps: List<String>? = null)

enum class FxCategory { PEDAL, AMP, STUDIO, VOCAL }

/**
 * Каталог эффектов. type и порядок параметров ДОЛЖНЫ совпадать с cpp/dsp/Effects.h.
 * colorIndex — индекс пастельного цвета педали в YumePalette.pedals.
 */
enum class FxDef(
    val type: Int,
    val brand: String,
    val title: String,
    val colorIndex: Int,
    val category: FxCategory,
    val params: List<FxParamDef>,
) {
    COMPRESSOR(0, "Purr Comp", "Компрессор", 0, FxCategory.STUDIO, listOf(
        FxParamDef("Порог", 0.55f), FxParamDef("Ratio", 0.35f), FxParamDef("Атака", 0.2f),
        FxParamDef("Релиз", 0.35f), FxParamDef("Усиление", 0.3f), FxParamDef("Микс", 1f))),
    GATE(1, "Hush Gate", "Нойз-гейт", 1, FxCategory.PEDAL, listOf(
        FxParamDef("Порог", 0.35f), FxParamDef("Релиз", 0.3f))),
    OVERDRIVE(2, "Neko Drive", "Овердрайв", 2, FxCategory.PEDAL, listOf(
        FxParamDef("Драйв", 0.5f), FxParamDef("Тон", 0.55f), FxParamDef("Уровень", 0.5f))),
    DISTORTION(3, "Kitsune Dist", "Дисторшн", 3, FxCategory.PEDAL, listOf(
        FxParamDef("Гейн", 0.5f), FxParamDef("Тон", 0.55f), FxParamDef("Уровень", 0.5f))),
    FUZZ(4, "Mochi Fuzz", "Фузз", 4, FxCategory.PEDAL, listOf(
        FxParamDef("Фузз", 0.5f), FxParamDef("Тон", 0.55f), FxParamDef("Уровень", 0.5f))),
    WAH(5, "Nyaa Wah", "Вау", 5, FxCategory.PEDAL, listOf(
        FxParamDef("Режим", 1f, listOf("Ручной", "Авто")), FxParamDef("Педаль", 0.5f),
        FxParamDef("Резонанс", 0.6f), FxParamDef("Чувств.", 0.6f), FxParamDef("Микс", 1f))),
    CHORUS(6, "Sakura Chorus", "Хорус", 6, FxCategory.PEDAL, listOf(
        FxParamDef("Скорость", 0.3f), FxParamDef("Глубина", 0.5f), FxParamDef("Микс", 0.5f))),
    FLANGER(7, "Comet Flanger", "Флэнджер", 7, FxCategory.PEDAL, listOf(
        FxParamDef("Скорость", 0.25f), FxParamDef("Глубина", 0.6f), FxParamDef("Обр. связь", 0.6f), FxParamDef("Микс", 0.5f))),
    PHASER(8, "Moon Phaser", "Фэйзер", 8, FxCategory.PEDAL, listOf(
        FxParamDef("Скорость", 0.3f), FxParamDef("Глубина", 0.7f), FxParamDef("Обр. связь", 0.5f), FxParamDef("Микс", 0.5f))),
    TREMOLO(9, "Heartbeat", "Тремоло", 9, FxCategory.PEDAL, listOf(
        FxParamDef("Скорость", 0.45f), FxParamDef("Глубина", 0.6f), FxParamDef("Форма", 0.2f),
        FxParamDef("Стерео", 0f, listOf("Моно", "Пан")))),
    OCTAVER(10, "Twin Octave", "Октавер", 10, FxCategory.PEDAL, listOf(
        FxParamDef("Окт. вниз", 0.6f), FxParamDef("Окт. вверх", 0.2f), FxParamDef("Прямой", 0.7f))),
    DELAY(11, "Echo Bunny", "Дилей", 11, FxCategory.PEDAL, listOf(
        FxParamDef("Время", 0.55f), FxParamDef("Повторы", 0.4f), FxParamDef("Микс", 0.3f),
        FxParamDef("Тон", 0.6f), FxParamDef("Пинг-понг", 0f, listOf("Выкл", "Вкл")))),
    REVERB(12, "Cloud Verb", "Реверберация", 12, FxCategory.PEDAL, listOf(
        FxParamDef("Размер", 0.6f), FxParamDef("Глушение", 0.45f), FxParamDef("Микс", 0.3f), FxParamDef("Ширина", 1f))),
    BITCRUSHER(13, "Lo-Fi Kitty", "Лоу-фай", 13, FxCategory.PEDAL, listOf(
        FxParamDef("Биты", 0.5f), FxParamDef("Сэмплрейт", 0.3f), FxParamDef("Микс", 0.7f), FxParamDef("Кассета", 0.3f))),
    EQ(14, "Pastel EQ", "Эквалайзер", 14, FxCategory.STUDIO, listOf(
        FxParamDef("Низ", 0.5f), FxParamDef("Середина", 0.5f), FxParamDef("Верх", 0.5f), FxParamDef("Частота сер.", 0.5f))),
    AMP(15, "Yume Amp", "Усилитель", 15, FxCategory.AMP, listOf(
        FxParamDef("Модель", 0.34f, listOf("Clean", "Crunch", "Lead", "Bass")), FxParamDef("Гейн", 0.45f),
        FxParamDef("Бас", 0.55f), FxParamDef("Середина", 0.5f), FxParamDef("Верх", 0.55f), FxParamDef("Громкость", 0.5f))),
    PITCH(16, "Idol Tune", "Тюнинг", 16, FxCategory.VOCAL, listOf(
        FxParamDef("Сила", 0.7f), FxParamDef("Тоника", 0f, listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")),
        FxParamDef("Лад", 0.5f, listOf("Хром.", "Мажор", "Минор")), FxParamDef("Микс", 1f)));

    val defaults: List<Float> get() = params.map { it.default }

    companion object {
        fun byType(type: Int): FxDef? = entries.firstOrNull { it.type == type }
        val pedals: List<FxDef> = entries.filter { it.category == FxCategory.PEDAL || it.category == FxCategory.STUDIO || it == AMP }
    }
}

/** Значение дискретного параметра → индекс шага и обратно. */
fun FxParamDef.stepIndex(value: Float): Int = steps?.let { (value * it.size).toInt().coerceIn(0, it.size - 1) } ?: 0
fun FxParamDef.stepValue(index: Int): Float = steps?.let { (index + 0.5f) / it.size } ?: 0f
