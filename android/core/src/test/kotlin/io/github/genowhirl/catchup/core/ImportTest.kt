package io.github.genowhirl.catchup.core

import io.github.genowhirl.catchup.core.imports.ImportFile
import io.github.genowhirl.catchup.core.imports.ImportSource
import io.github.genowhirl.catchup.core.imports.Meta
import io.github.genowhirl.catchup.core.imports.VCard
import io.github.genowhirl.catchup.core.imports.WhatsApp
import io.github.genowhirl.catchup.core.imports.Candidate
import io.github.genowhirl.catchup.core.imports.planImport
import io.github.genowhirl.catchup.core.imports.readImportFile
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportTest {
    @Test fun `vcard from an iPhone`() {
        val vcf = listOf(
            "BEGIN:VCARD", "VERSION:3.0", "N:Patel;Maya;;;", "FN:Maya Patel", "NICKNAME:May", "ORG:Acme\\, Inc.;",
            "item1.TEL;type=CELL;type=VOICE;type=pref:+1 206 555 0142", "TEL;type=WORK;type=VOICE:+1 206 555 0100",
            "EMAIL;type=INTERNET;type=HOME;type=pref:maya@example.com",
            "item2.ADR;type=HOME;type=pref:;;1 Pine St;Seattle;WA;98101;United States",
            "BDAY;X-APPLE-OMIT-YEAR=1604:1604-10-19",
            "item3.X-ABRELATEDNAMES;type=pref:Arjun", "item3.X-ABLabel:_\$!<Spouse>!\$_",
            "X-SOCIALPROFILE;type=instagram;x-user=maya.climbs:x-apple:maya.climbs",
            "NOTE:Loves matcha.\\nAllergic to cats.", "END:VCARD",
            "BEGIN:VCARD", "VERSION:3.0", "N:;;;;", "END:VCARD",
        ).joinToString("\r\n")
        val (candidates, notes) = VCard.parse(vcf)
        val c = candidates.single()
        assertEquals("Maya Patel", c.name)
        assertEquals("May", c.nickname)
        assertEquals("10-19", c.birthday)
        assertEquals("Seattle, United States", c.location)
        assertEquals(mapOf("phone" to "+1 206 555 0142", "phone_work" to "+1 206 555 0100", "email" to "maya@example.com", "instagram" to "maya.climbs"), c.contact)
        assertEquals(listOf<Pair<String, String?>>("Arjun" to "spouse"), c.family)
        assertEquals("Works at Acme, Inc.\n\nLoves matcha.\nAllergic to cats.", c.notes)
        assertEquals(listOf("Skipped 1 contact without a name."), notes)
    }

    @Test fun `vcard 2_1 quoted-printable and vcard 4`() {
        val vcf = listOf(
            "BEGIN:VCARD", "VERSION:2.1", "N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Bront=C3=AB;Zo=C3=AB;;;",
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Zo=C3=AB Bront=C3=AB", "TEL;CELL:+44 7700 900123", "BDAY:19880304",
            "CATEGORIES:myContacts,Book Club", "END:VCARD",
            "BEGIN:VCARD", "VERSION:4.0", "FN:Sam", "BDAY:--0229", "RELATED;TYPE=child:text:Robin", "END:VCARD",
        ).joinToString("\n")
        val (c) = VCard.parse(vcf)
        assertEquals("Zoë Brontë", c[0].name)
        assertEquals("1988-03-04", c[0].birthday)
        assertEquals(listOf("book-club"), c[0].tags)
        assertEquals("02-29", c[1].birthday)
        assertEquals(listOf<Pair<String, String?>>("Robin" to "child"), c[1].family)
    }

    @Test fun `whatsapp exports from iPhone and US Android`() {
        val ios = listOf(
            "[03/02/2026, 09:15:02] Maya Patel: ‎Messages and calls are end-to-end encrypted.",
            "[03/02/2026, 09:15:02] Maya Patel: Hey!",
            "[13/09/2026, 21:40:10] Me Myself: See you soon",
            "multi-line continuation",
            "[14/09/2026, 08:00:00] Maya Patel: ‎image omitted",
        ).joinToString("\n")
        val a = WhatsApp.parse(listOf(ImportFile("WhatsApp Chat - Maya Patel.zip/_chat.txt", ios))).candidates.single()
        assertEquals("Maya Patel" to "2026-09-14", a.name to a.lastContacted)
        assertEquals("4 WhatsApp messages", a.detail)

        val us = listOf(
            "10/2/26, 9:05 PM - Messages and calls are end-to-end encrypted.",
            "10/2/26, 9:05 PM - +1 206 555 0199: Hi, it's Jo",
            "10/9/26, 12:30 AM - You: Hello",
        ).joinToString("\n")
        val b = WhatsApp.parse(listOf(ImportFile("WhatsApp Chat with +1 206 555 0199.txt", us))).candidates.single()
        assertEquals("2026-10-09", b.lastContacted)
        assertEquals(mapOf("whatsapp" to "+1 206 555 0199"), b.contact)
    }

    @Test fun `whatsapp works out your name and skips groups`() {
        fun chat(other: String) = "01/10/2026, 10:00 - $other: hi\n02/10/2026, 11:00 - Alex Doe: hello"
        val (c, notes) = WhatsApp.parse(
            listOf(ImportFile("a.txt", chat("Sam Lee")), ImportFile("b.txt", chat("Robin Roe")), ImportFile("g.txt", chat("Sam Lee") + "\n03/10/2026, 12:00 - Robin Roe: yo")),
        )
        assertEquals(listOf("Sam Lee", "Robin Roe"), c.map { it.name })
        assertEquals(listOf("Skipped group chat."), notes)
        assertEquals("Jonas Weber", WhatsApp.chatTitle("WhatsApp-Chat mit Jonas Weber.txt"))
        assertNull(WhatsApp.chatTitle("notes.txt"))
    }

    private fun thread(participants: List<String>, times: List<Long>) =
        """{"participants":[${participants.joinToString(",") { "{\"name\":\"$it\"}" }}],"messages":[${times.joinToString(",") { "{\"timestamp_ms\":$it}" }}]}"""

    @Test fun `facebook friends and messenger threads`() {
        assertEquals("Zoë", Meta.fixMojibake("ZoÃ«"))
        val (c, notes) = Meta.parse(
            listOf(
                ImportFile("fb.zip/connections/friends/your_friends.json", """{"friends_v2":[{"name":"ZoÃ« BrontÃ«","timestamp":1356998400}]}"""),
                ImportFile("fb.zip/profile_information/profile_information.json", """{"profile_v2":{"name":{"full_name":"Alex Doe"}}}"""),
                ImportFile("fb.zip/messages/inbox/zoebronte_123/message_1.json", thread(listOf("Zoë Brontë", "Alex Doe"), listOf(1788264000000, 1790942400000))),
                ImportFile("fb.zip/messages/inbox/group_1/message_1.json", thread(listOf("A", "B", "Alex Doe"), listOf(1))),
            ),
            ImportSource.FACEBOOK,
        )
        assertEquals(listOf("Zoë Brontë" to "Facebook friends since 2013.", "Zoë Brontë" to null), c.map { it.name to it.howWeMet })
        assertEquals("2026-10-02", c[1].lastContacted)
        assertEquals(listOf("Skipped 1 group conversation."), notes)
    }

    @Test fun `instagram mutual follows`() {
        val following = """{"relationships_following":[{"title":"maya.climbs","string_list_data":[{"href":"https://www.instagram.com/_u/maya.climbs","timestamp":1}]},{"title":"","string_list_data":[{"href":"https://www.instagram.com/celebrity","value":"celebrity"}]}]}"""
        val followers = """[{"title":"","string_list_data":[{"href":"https://www.instagram.com/maya.climbs","value":"maya.climbs"}]}]"""
        val c = Meta.parse(listOf(ImportFile("ig/following.json", following), ImportFile("ig/followers_1.json", followers)), ImportSource.INSTAGRAM).candidates.single()
        assertEquals("maya.climbs", c.name)
        assertEquals("Mutual follow", c.detail)
    }

    @Test fun `matching merges duplicates and only proposes real changes`() {
        val mayaText = "---\nname: Maya Patel\ncontact:\n  phone: \"+1 206 555 0142\"\nlast_contacted: 2026-08-01\n---\n"
        val maya = parsePerson("maya-patel", mayaText).person!!
        val items = planImport(
            listOf(
                Candidate("May P", ImportSource.CONTACTS, birthday = "1991-10-19").apply { contact["phone"] = "206-555-0142"; contact["email"] = "maya@example.com" },
                Candidate("Maya Patel", ImportSource.WHATSAPP, lastContacted = "2026-07-01"),
                Candidate("Zoë Brontë", ImportSource.FACEBOOK, howWeMet = "Facebook friends since 2013."),
                Candidate("zoe bronte", ImportSource.FACEBOOK, lastContacted = "2026-10-02"),
            ),
            listOf(maya),
            mapOf("people/maya-patel.md" to mayaText),
        )
        assertEquals(
            listOf(Triple("May P", "maya-patel", listOf("birthday", "email")), Triple("Maya Patel", "maya-patel", emptyList()), Triple("Zoë Brontë", null, listOf("new"))),
            items.map { Triple(it.candidate.name, it.match?.id, it.changes) },
        )
        val created = items[2].op as Op.Create
        assertEquals("people/zoe-bronte.md", created.path)
        val (zoe, issues) = parsePerson("zoe-bronte", created.content)
        assertEquals(emptyList(), issues)
        assertEquals(LocalDate.of(2026, 10, 2), zoe!!.lastContacted)
    }

    @Test fun `zip files only yield relevant entries`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                for ((name, content) in listOf("connections/friends/your_friends.json" to "{}", "media/photo.jpg" to "xx", "__MACOSX/._chat.txt" to "junk", "_chat.txt" to "hi")) {
                    z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray()); z.closeEntry()
                }
            }
        }.toByteArray()
        assertEquals(listOf("e.zip/connections/friends/your_friends.json", "e.zip/_chat.txt"), readImportFile("e.zip", bytes).map { it.name })
    }
}
