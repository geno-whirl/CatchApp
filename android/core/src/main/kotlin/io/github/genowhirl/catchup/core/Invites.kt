package io.github.genowhirl.catchup.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The `language:` field: stored as a language tag (`es`, `pt-BR`), but names are accepted too. */
object Languages {
    /** Languages invitations can be written in. Others fall back to English. */
    val INVITES = listOf("en", "es", "fr", "de", "it", "pt", "nl")

    /** Choices offered in the app, in this order. */
    val COMMON = listOf("en", "es", "fr", "de", "it", "pt", "pt-BR", "nl", "pl", "sv", "da", "no", "ca", "el", "tr", "ru", "uk", "ar", "he", "hi", "zh", "ja", "ko")

    private val TAG = Regex("^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8})*$")
    private val iso = Locale.getISOLanguages().toSet()

    /** `"es"`, `"Spanish"`, `"español"`, `"pt_BR"` → a language tag, or null if unknown. */
    fun normalize(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (TAG.matches(value)) {
            val locale = Locale.forLanguageTag(value.replace('_', '-'))
            if (locale.language in iso) return locale.toLanguageTag()
        }
        val lower = value.lowercase()
        return iso.firstOrNull { code ->
            val l = Locale(code)
            l.getDisplayLanguage(Locale.ENGLISH).lowercase() == lower || l.getDisplayLanguage(l).lowercase() == lower
        }
    }

    /** "Spanish", "Portuguese (Brazil)" — in the phone's language. */
    fun displayName(tag: String, inLocale: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(tag).getDisplayName(inLocale).replaceFirstChar { it.titlecase(inLocale) }
}

/** When to suggest meeting, for an invitation: a day plus a part of the day or a time. */
data class InviteWhen(val date: LocalDate, val time: LocalTime? = null, val part: DayPart? = null)

/**
 * A short, friendly invitation in the person's language (English if it's not one we write).
 * `kind` is a plan kind: `Call`, `Meet`, or anything else ("Dinner"), used as is.
 */
fun inviteText(person: Person, kind: String?, at: InviteWhen, today: LocalDate, language: String? = person.language): String {
    val phrases = phrasesFor(language)
    val whenText = phrases.whenText(at, today)
    val k = kind?.trim().orEmpty()
    return when {
        k.isEmpty() || k.lowercase() in setOf("meet", "in person", "visit") -> phrases.meet(person.shortName, whenText)
        isCall(k) -> phrases.call(person.shortName, whenText)
        else -> phrases.other(person.shortName, phrases.activities[k.lowercase()] ?: phrases.untranslated(k), whenText)
    }
}

private fun phrasesFor(language: String?): Phrases {
    val locale = language?.let { Locale.forLanguageTag(it) }
    return when (locale?.language) {
        "es" -> Spanish
        "fr" -> French
        "de" -> German
        "it" -> Italian
        "pt" -> if (locale.country == "PT") Portuguese(european = true) else Portuguese(european = false)
        "nl" -> Dutch
        else -> English
    }
}

private abstract class Phrases(val locale: Locale) {
    abstract fun today(part: DayPart): String
    abstract fun tomorrow(part: DayPart): String
    abstract fun weekday(name: String, part: DayPart): String
    abstract fun later(date: String, part: DayPart): String
    abstract val todayWord: String
    abstract val tomorrowWord: String
    abstract fun onWeekday(name: String): String
    abstract fun onDate(date: String): String
    abstract fun atTime(day: String, time: LocalTime): String
    abstract fun call(name: String, whenText: String): String
    abstract fun meet(name: String, whenText: String): String
    abstract fun other(name: String, what: String, whenText: String): String
    open val datePattern = "EEEE d MMMM"
    /** The app's plan kinds (`coffee`, `lunch`…) as they'd read in an invitation. */
    open val activities: Map<String, String> = emptyMap()
    open fun untranslated(kind: String) = kind.lowercase(locale)

    fun whenText(at: InviteWhen, today: LocalDate): String {
        val days = daysBetween(today, at.date)
        val weekdayName = at.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val dateName = at.date.format(DateTimeFormatter.ofPattern(datePattern, locale))
        if (at.part != null) return when (days) {
            0 -> today(at.part)
            1 -> tomorrow(at.part)
            in 2..6 -> weekday(weekdayName, at.part)
            else -> later(dateName, at.part)
        }
        val day = when (days) {
            0 -> todayWord
            1 -> tomorrowWord
            in 2..6 -> onWeekday(weekdayName)
            else -> onDate(dateName)
        }
        return at.time?.let { atTime(day, it) } ?: day
    }
}

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

private object English : Phrases(Locale.ENGLISH) {
    override val activities = mapOf("coffee" to "a coffee", "lunch" to "lunch", "dinner" to "dinner", "walk" to "a walk")
    override fun today(part: DayPart) = when (part) {
        DayPart.MORNING -> "this morning"
        DayPart.LUNCH -> "for lunch today"
        DayPart.AFTERNOON -> "this afternoon"
        DayPart.EVENING -> "this evening"
    }
    private fun suffix(part: DayPart) = when (part) {
        DayPart.MORNING -> "morning"
        DayPart.LUNCH -> "at lunchtime"
        DayPart.AFTERNOON -> "afternoon"
        DayPart.EVENING -> "evening"
    }
    override fun tomorrow(part: DayPart) = "tomorrow ${suffix(part)}"
    override fun weekday(name: String, part: DayPart) = "on $name ${suffix(part)}"
    override fun later(date: String, part: DayPart) = when (part) {
        DayPart.LUNCH -> "on $date at lunchtime"
        else -> "on $date in the ${part.name.lowercase()}"
    }
    override val todayWord = "today"
    override val tomorrowWord = "tomorrow"
    override fun onWeekday(name: String) = "on $name"
    override fun onDate(date: String) = "on $date"
    override fun atTime(day: String, time: LocalTime) = "$day at ${time.format(HHMM)}"
    override fun call(name: String, whenText: String) = "Hi $name! Are you free for a call $whenText? 😊"
    override fun meet(name: String, whenText: String) = "Hi $name! Would you like to meet up $whenText? 😊"
    override fun other(name: String, what: String, whenText: String) = "Hi $name! Are you free for $what $whenText? 😊"
}

private object Spanish : Phrases(Locale.forLanguageTag("es")) {
    override val activities = mapOf("coffee" to "un café", "lunch" to "comer juntos", "dinner" to "cenar juntos", "walk" to "dar un paseo")
    override val datePattern = "EEEE d 'de' MMMM"
    override fun today(part: DayPart) = when (part) {
        DayPart.MORNING -> "esta mañana"
        DayPart.LUNCH -> "hoy a la hora de comer"
        DayPart.AFTERNOON -> "esta tarde"
        DayPart.EVENING -> "esta noche"
    }
    private fun suffix(part: DayPart) = when (part) {
        DayPart.MORNING -> "por la mañana"
        DayPart.LUNCH -> "a la hora de comer"
        DayPart.AFTERNOON -> "por la tarde"
        DayPart.EVENING -> "por la noche"
    }
    override fun tomorrow(part: DayPart) = "mañana ${suffix(part)}"
    override fun weekday(name: String, part: DayPart) = "el $name ${suffix(part)}"
    override fun later(date: String, part: DayPart) = "el $date ${suffix(part)}"
    override val todayWord = "hoy"
    override val tomorrowWord = "mañana"
    override fun onWeekday(name: String) = "el $name"
    override fun onDate(date: String) = "el $date"
    override fun atTime(day: String, time: LocalTime) = "$day ${if (time.hour == 1) "a la" else "a las"} ${time.format(HHMM)}"
    override fun call(name: String, whenText: String) = "¡Hola, $name! ¿Tienes un rato para una llamada $whenText? 😊"
    override fun meet(name: String, whenText: String) = "¡Hola, $name! ¿Te apetece que quedemos $whenText? 😊"
    override fun other(name: String, what: String, whenText: String) = "¡Hola, $name! ¿Te apetece $what $whenText? 😊"
}

private object French : Phrases(Locale.FRENCH) {
    override val activities = mapOf("coffee" to "un café", "lunch" to "un déj", "dinner" to "un dîner", "walk" to "une balade")
    override val datePattern = "EEEE d MMMM"
    override fun today(part: DayPart) = when (part) {
        DayPart.MORNING -> "ce matin"
        DayPart.LUNCH -> "ce midi"
        DayPart.AFTERNOON -> "cet après-midi"
        DayPart.EVENING -> "ce soir"
    }
    private fun suffix(part: DayPart) = when (part) {
        DayPart.MORNING -> "matin"
        DayPart.LUNCH -> "midi"
        DayPart.AFTERNOON -> "après-midi"
        DayPart.EVENING -> "soir"
    }
    override fun tomorrow(part: DayPart) = "demain ${suffix(part)}"
    override fun weekday(name: String, part: DayPart) = "$name ${suffix(part)}"
    override fun later(date: String, part: DayPart) = "le $date ${if (part == DayPart.LUNCH) "à midi" else "au ${suffix(part)}"}"
    override val todayWord = "aujourd'hui"
    override val tomorrowWord = "demain"
    override fun onWeekday(name: String) = name
    override fun onDate(date: String) = "le $date"
    override fun atTime(day: String, time: LocalTime) = "$day à ${time.hour}h${if (time.minute == 0) "" else "%02d".format(time.minute)}"
    override fun call(name: String, whenText: String) = "Coucou $name ! Tu serais dispo pour un appel $whenText ? 😊"
    override fun meet(name: String, whenText: String) = "Coucou $name ! Ça te dit qu'on se voie $whenText ? 😊"
    override fun other(name: String, what: String, whenText: String) = "Coucou $name ! Ça te dit, $what $whenText ? 😊"
}

private object German : Phrases(Locale.GERMAN) {
    override val activities = mapOf("coffee" to "einen Kaffee", "lunch" to "ein Mittagessen", "dinner" to "ein Abendessen", "walk" to "einen Spaziergang")
    override fun untranslated(kind: String) = kind.replaceFirstChar { it.titlecase(locale) }
    override val datePattern = "EEEE, d. MMMM"
    private fun noun(part: DayPart) = when (part) {
        DayPart.MORNING -> "Vormittag"
        DayPart.LUNCH -> "Mittag"
        DayPart.AFTERNOON -> "Nachmittag"
        DayPart.EVENING -> "Abend"
    }
    override fun today(part: DayPart) = "heute ${noun(part)}"
    override fun tomorrow(part: DayPart) = "morgen ${noun(part)}"
    override fun weekday(name: String, part: DayPart) = "am $name${noun(part).lowercase(locale)}"
    override fun later(date: String, part: DayPart) = "am $date, ${noun(part).lowercase(locale)}s"
    override val todayWord = "heute"
    override val tomorrowWord = "morgen"
    override fun onWeekday(name: String) = "am $name"
    override fun onDate(date: String) = "am $date,"
    override fun atTime(day: String, time: LocalTime) = "$day um ${time.format(HHMM)}"
    override fun call(name: String, whenText: String) = "Hallo $name! Hast du ${tidy(whenText)} Zeit zum Telefonieren? 😊"
    override fun meet(name: String, whenText: String) = "Hallo $name! Hast du Lust, dass wir uns ${tidy(whenText)} treffen? 😊"
    override fun other(name: String, what: String, whenText: String) = "Hallo $name! Hast du ${tidy(whenText)} Zeit für $what? 😊"
    // A date with no time ends in a comma ("am Dienstag, 20. Oktober,") only when more follows.
    private fun tidy(s: String) = s.removeSuffix(",")
}

private object Italian : Phrases(Locale.ITALIAN) {
    override val activities = mapOf("coffee" to "un caffè", "lunch" to "pranzare insieme", "dinner" to "cenare insieme", "walk" to "una passeggiata")
    override val datePattern = "EEEE d MMMM"
    override fun today(part: DayPart) = when (part) {
        DayPart.MORNING -> "stamattina"
        DayPart.LUNCH -> "oggi a pranzo"
        DayPart.AFTERNOON -> "oggi pomeriggio"
        DayPart.EVENING -> "stasera"
    }
    private fun suffix(part: DayPart) = when (part) {
        DayPart.MORNING -> "mattina"
        DayPart.LUNCH -> "a pranzo"
        DayPart.AFTERNOON -> "pomeriggio"
        DayPart.EVENING -> "sera"
    }
    override fun tomorrow(part: DayPart) = "domani ${suffix(part)}"
    override fun weekday(name: String, part: DayPart) = "$name ${suffix(part)}"
    override fun later(date: String, part: DayPart) = "$date ${suffix(part)}"
    override val todayWord = "oggi"
    override val tomorrowWord = "domani"
    override fun onWeekday(name: String) = name
    override fun onDate(date: String) = date
    override fun atTime(day: String, time: LocalTime) = "$day ${if (time.hour == 1) "all'" else "alle "}${time.format(HHMM)}"
    override fun call(name: String, whenText: String) = "Ciao $name! Hai tempo per una chiamata $whenText? 😊"
    override fun meet(name: String, whenText: String) = "Ciao $name! Ti va di vederci $whenText? 😊"
    override fun other(name: String, what: String, whenText: String) = "Ciao $name! Ti va $what $whenText? 😊"
}

private class Portuguese(val european: Boolean) : Phrases(Locale.forLanguageTag(if (european) "pt-PT" else "pt-BR")) {
    override val activities = mapOf("coffee" to "um café", "lunch" to "almoçarmos juntos", "dinner" to "jantarmos juntos", "walk" to "uma caminhada")
    override val datePattern = "EEEE, d 'de' MMMM"
    private fun suffix(part: DayPart) = when (part) {
        DayPart.MORNING -> "de manhã"
        DayPart.LUNCH -> "na hora do almoço"
        DayPart.AFTERNOON -> "à tarde"
        DayPart.EVENING -> "à noite"
    }
    override fun today(part: DayPart) = "hoje ${suffix(part)}"
    override fun tomorrow(part: DayPart) = "amanhã ${suffix(part)}"
    private fun on(name: String) = (if (name.startsWith("sábado") || name.startsWith("domingo")) "no " else "na ") + name
    override fun weekday(name: String, part: DayPart) = "${on(name)} ${suffix(part)}"
    override fun later(date: String, part: DayPart) = "${on(date)}, ${suffix(part)}"
    override val todayWord = "hoje"
    override val tomorrowWord = "amanhã"
    override fun onWeekday(name: String) = on(name)
    override fun onDate(date: String) = on(date)
    override fun atTime(day: String, time: LocalTime) = "$day ${if (time.hour == 1) "à" else "às"} ${time.format(HHMM)}"
    override fun call(name: String, whenText: String) =
        if (european) "Olá, $name! Tens tempo para uma chamada $whenText? 😊" else "Oi, $name! Você tem um tempinho para uma ligação $whenText? 😊"
    override fun meet(name: String, whenText: String) =
        if (european) "Olá, $name! Apetece-te combinarmos $whenText? 😊" else "Oi, $name! Vamos nos ver $whenText? 😊"
    override fun other(name: String, what: String, whenText: String) =
        "${if (european) "Olá" else "Oi"}, $name! Que tal $what $whenText? 😊"
}

private object Dutch : Phrases(Locale.forLanguageTag("nl")) {
    override val activities = mapOf("coffee" to "koffie", "lunch" to "een lunch", "dinner" to "een etentje", "walk" to "een wandeling")
    override val datePattern = "EEEE d MMMM"
    override fun today(part: DayPart) = when (part) {
        DayPart.MORNING -> "vanochtend"
        DayPart.LUNCH -> "vandaag tijdens de lunch"
        DayPart.AFTERNOON -> "vanmiddag"
        DayPart.EVENING -> "vanavond"
    }
    private fun compound(day: String, part: DayPart) = when (part) {
        DayPart.MORNING -> "${day}ochtend"
        DayPart.LUNCH -> "$day tijdens de lunch"
        DayPart.AFTERNOON -> "${day}middag"
        DayPart.EVENING -> "${day}avond"
    }
    override fun tomorrow(part: DayPart) = compound("morgen", part)
    override fun weekday(name: String, part: DayPart) = compound(name, part)
    override fun later(date: String, part: DayPart) = "op $date " + when (part) {
        DayPart.MORNING -> "'s ochtends"
        DayPart.LUNCH -> "tijdens de lunch"
        DayPart.AFTERNOON -> "'s middags"
        DayPart.EVENING -> "'s avonds"
    }
    override val todayWord = "vandaag"
    override val tomorrowWord = "morgen"
    override fun onWeekday(name: String) = "op $name"
    override fun onDate(date: String) = "op $date"
    override fun atTime(day: String, time: LocalTime) = "$day om ${time.format(HHMM)}"
    override fun call(name: String, whenText: String) = "Hoi $name! Heb je $whenText tijd om te bellen? 😊"
    override fun meet(name: String, whenText: String) = "Hoi $name! Zin om $whenText af te spreken? 😊"
    override fun other(name: String, what: String, whenText: String) = "Hoi $name! Zin in $what $whenText? 😊"
}
