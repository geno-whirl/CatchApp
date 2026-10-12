package io.github.genowhirl.catchup.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import io.github.genowhirl.catchup.core.imports.Candidate
import io.github.genowhirl.catchup.core.imports.ImportSource
import io.github.genowhirl.catchup.core.imports.VCard

/** Reads the phone's address book (needs READ_CONTACTS) into import candidates. */
object ContactsReader {
    private val RELATIONS = mapOf(
        Relation.TYPE_SPOUSE to "spouse", Relation.TYPE_PARTNER to "partner", Relation.TYPE_CHILD to "child",
        Relation.TYPE_MOTHER to "mother", Relation.TYPE_FATHER to "father", Relation.TYPE_PARENT to "parent",
        Relation.TYPE_BROTHER to "brother", Relation.TYPE_SISTER to "sister", Relation.TYPE_FRIEND to "friend",
        Relation.TYPE_RELATIVE to "relative", Relation.TYPE_DOMESTIC_PARTNER to "partner",
    )

    /** One contact, e.g. from the system contact picker: everything we can use, even just a name. */
    fun readOne(context: Context, contactId: Long): Candidate? = read(context, contactId).firstOrNull()

    fun read(context: Context): List<Candidate> =
        // People with only a name aren't worth importing from a phone book.
        read(context, null).filter { it.contact.isNotEmpty() || it.birthday != null }

    private fun read(context: Context, contactId: Long?): List<Candidate> {
        val byContact = linkedMapOf<Long, Candidate>()
        val projection = arrayOf(
            Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY, Data.MIMETYPE,
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA7, Data.DATA10,
        )
        val types = listOf(
            Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Event.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE,
            Note.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        )
        val selection = "${Data.MIMETYPE} IN (${types.joinToString(",") { "?" }})" + if (contactId != null) " AND ${Data.CONTACT_ID} = ?" else ""
        val args = types + listOfNotNull(contactId?.toString())
        context.contentResolver.query(Data.CONTENT_URI, projection, selection, args.toTypedArray(), "${Data.DISPLAY_NAME_PRIMARY} COLLATE NOCASE")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                if (name.isEmpty()) continue
                val cand = byContact.getOrPut(id) { Candidate(name, ImportSource.CONTACTS) }
                val data1 = c.getString(3)?.trim().orEmpty()
                val type = c.getInt(4)
                when (c.getString(2)) {
                    Phone.CONTENT_ITEM_TYPE -> VCard.addContact(
                        cand.contact,
                        when (type) { Phone.TYPE_HOME -> "phone_home"; Phone.TYPE_WORK -> "phone_work"; else -> "phone" },
                        data1,
                    )
                    Email.CONTENT_ITEM_TYPE -> VCard.addContact(cand.contact, if (type == Email.TYPE_WORK) "email_work" else "email", data1)
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) cand.birthday = VCard.parseDate(data1)
                    Nickname.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) cand.nickname = data1
                    Note.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) cand.notes = listOfNotNull(cand.notes, data1).joinToString("\n\n")
                    Organization.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) cand.notes = listOfNotNull("Works at $data1.", cand.notes).joinToString("\n\n")
                    Relation.CONTENT_ITEM_TYPE -> if (data1.isNotEmpty()) cand.family += data1 to (RELATIONS[type] ?: c.getString(5))
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (cand.location == null) {
                        val city = c.getString(7)?.trim()
                        val country = c.getString(8)?.trim()
                        listOfNotNull(city?.ifEmpty { null }, country?.ifEmpty { null }).joinToString(", ").ifEmpty { null }?.let { cand.location = it }
                    }
                }
            }
        }
        return byContact.values.toList()
    }

    /** The contact's id and name from a picker result (`content://com.android.contacts/contacts/…`). */
    fun idAndName(context: Context, uri: android.net.Uri): Pair<Long, String>? =
        context.contentResolver.query(uri, arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) to c.getString(1).orEmpty() else null
        }
}
