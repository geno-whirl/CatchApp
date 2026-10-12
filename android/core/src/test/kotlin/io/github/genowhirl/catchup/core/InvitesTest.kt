package io.github.genowhirl.catchup.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvitesTest {
    private val today = LocalDate.of(2026, 10, 11) // Sunday
    private fun person(language: String?) =
        parsePerson("maya", "---\nname: Maya Patel\n${language?.let { "language: $it" } ?: ""}\n---\n").person!!

    @Test fun `language accepts tags and names`() {
        assertEquals("es", Languages.normalize("es"))
        assertEquals("es", Languages.normalize("Spanish"))
        assertEquals("de", Languages.normalize("Deutsch"))
        assertEquals("pt-BR", Languages.normalize("pt_BR"))
        assertNull(Languages.normalize("Klingonese"))
        assertEquals("fr", person("French").language)
        val unknown = parsePerson("x", "---\nname: X\nlanguage: Elvish\n---\n")
        assertNull(unknown.person!!.language)
        assertEquals(1, unknown.issues.size)
    }

    @Test fun `language is written back in its place`() {
        val path = "people/maya.md"
        val file = applyOp(
            mapOf(path to "---\nname: Maya Patel\nlocation: Lisbon\ncontact:\n  phone: \"+351 900\"\n---\n"),
            Op.SetFields(path, mapOf("language" to kotlinx.serialization.json.JsonPrimitive("pt"))),
        ).first.getValue(path)
        assertEquals("---\nname: Maya Patel\nlocation: Lisbon\nlanguage: pt\ncontact:\n  phone: \"+351 900\"\n---\n", file)
    }

    @Test fun `invitations in the person's language`() {
        val tue = InviteWhen(today.plusDays(2), part = DayPart.EVENING)
        assertEquals("Hi Maya! Would you like to meet up on Tuesday evening? 😊", inviteText(person(null), KIND_MEET, tue, today))
        assertEquals("Hi Maya! Are you free for a call this evening? 😊", inviteText(person("klingon"), KIND_CALL, InviteWhen(today, part = DayPart.EVENING), today))
        assertEquals("¡Hola, Maya! ¿Te apetece que quedemos el martes por la noche? 😊", inviteText(person("es"), KIND_MEET, tue, today))
        assertEquals("Coucou Maya ! Tu serais dispo pour un appel demain à 19h30 ? 😊", inviteText(person("fr"), KIND_CALL, InviteWhen(today.plusDays(1), LocalTime.of(19, 30)), today))
        assertEquals("Hallo Maya! Hast du Lust, dass wir uns am Dienstagabend treffen? 😊", inviteText(person("de"), KIND_MEET, tue, today))
        assertEquals("Hallo Maya! Hast du am Dienstag, 20. Oktober Zeit zum Telefonieren? 😊", inviteText(person("de"), KIND_CALL, InviteWhen(today.plusDays(9)), today))
        assertEquals("Ciao Maya! Ti va di vederci stasera? 😊", inviteText(person("it"), KIND_MEET, InviteWhen(today, part = DayPart.EVENING), today))
        assertEquals("Oi, Maya! Vamos nos ver no sábado à noite? 😊", inviteText(person("pt-BR"), KIND_MEET, InviteWhen(today.plusDays(6), part = DayPart.EVENING), today))
        assertEquals("Hoi Maya! Heb je dinsdagavond tijd om te bellen? 😊", inviteText(person("nl"), KIND_CALL, tue, today))
        assertEquals("Hi Maya! Are you free for dinner on Tuesday at 20:00? 😊", inviteText(person(null), "Dinner", InviteWhen(today.plusDays(2), LocalTime.of(20, 0)), today))
        assertEquals("¡Hola, Maya! ¿Te apetece un café mañana a las 10:30? 😊", inviteText(person("es"), "Coffee", InviteWhen(today.plusDays(1), LocalTime.of(10, 30)), today))
        assertEquals("Hallo Maya! Hast du morgen Zeit für Tennis? 😊", inviteText(person("de"), "tennis", InviteWhen(today.plusDays(1)), today))
        assertEquals("Ciao Maya! Ti va di vederci domani? 😊", inviteText(person("it"), "Visit", InviteWhen(today.plusDays(1)), today))
    }
}
