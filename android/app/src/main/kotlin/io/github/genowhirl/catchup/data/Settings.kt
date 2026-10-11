package io.github.genowhirl.catchup.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.genowhirl.catchup.core.DayPart
import io.github.genowhirl.catchup.core.ReminderSettings
import io.github.genowhirl.catchup.core.sync.GitHubConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime

/** Where people are kept. */
@Serializable
sealed interface Storage {
    @Serializable @SerialName("local")
    data object Local : Storage

    @Serializable @SerialName("github")
    data class GitHub(val owner: String, val repo: String, val branch: String = "main", val root: String = "", val token: String) : Storage {
        fun config() = GitHubConfig(owner, repo, branch, root, token)
    }

    @Serializable @SerialName("drive")
    data class Drive(val folder: String = "CatchUp") : Storage
}

data class AppSettings(
    /** False until read from disk, so the UI doesn't flash onboarding. */
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val userName: String = "",
    val storage: Storage = Storage.Local,
    val weeklyPrompt: Boolean = true,
    val planningDay: DayOfWeek = DayOfWeek.SUNDAY,
    val planningTime: LocalTime = LocalTime.of(18, 0),
    val weeklyGoal: Int = 3,
    val callReminderMinutes: Int = 15,
    val meetReminderMinutes: Int = 60,
    val followUps: Boolean = true,
    val morningDigest: Boolean = true,
    val digestTime: LocalTime = LocalTime.of(9, 0),
    val dynamicColor: Boolean = false,
    /** Usual free time, e.g. "TUESDAY:EVENING", used to pre-fill weekly planning. */
    val usualFreeTime: Set<String> = setOf("TUESDAY:EVENING", "THURSDAY:EVENING", "SATURDAY:AFTERNOON", "SUNDAY:LUNCH"),
) {
    fun reminderSettings() = ReminderSettings(callMinutes = callReminderMinutes, meetMinutes = meetReminderMinutes)

    fun usualSlots(): Set<Pair<DayOfWeek, DayPart>> = usualFreeTime.mapNotNull { s ->
        val (d, p) = s.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { DayOfWeek.valueOf(d) to DayPart.valueOf(p) }.getOrNull()
    }.toSet()
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val userName = stringPreferencesKey("user_name")
        val storage = stringPreferencesKey("storage")
        val weeklyPrompt = booleanPreferencesKey("weekly_prompt")
        val planningDay = stringPreferencesKey("planning_day")
        val planningTime = stringPreferencesKey("planning_time")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val callMinutes = intPreferencesKey("call_minutes")
        val meetMinutes = intPreferencesKey("meet_minutes")
        val followUps = booleanPreferencesKey("follow_ups")
        val digest = booleanPreferencesKey("digest")
        val digestTime = stringPreferencesKey("digest_time")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val freeTime = stringSetPreferencesKey("free_time")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            loaded = true,
            onboarded = this[Keys.onboarded] ?: d.onboarded,
            userName = this[Keys.userName] ?: d.userName,
            storage = this[Keys.storage]?.let { runCatching { json.decodeFromString(Storage.serializer(), it) }.getOrNull() } ?: d.storage,
            weeklyPrompt = this[Keys.weeklyPrompt] ?: d.weeklyPrompt,
            planningDay = this[Keys.planningDay]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() } ?: d.planningDay,
            planningTime = this[Keys.planningTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.planningTime,
            weeklyGoal = this[Keys.weeklyGoal] ?: d.weeklyGoal,
            callReminderMinutes = this[Keys.callMinutes] ?: d.callReminderMinutes,
            meetReminderMinutes = this[Keys.meetMinutes] ?: d.meetReminderMinutes,
            followUps = this[Keys.followUps] ?: d.followUps,
            morningDigest = this[Keys.digest] ?: d.morningDigest,
            digestTime = this[Keys.digestTime]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: d.digestTime,
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            usualFreeTime = this[Keys.freeTime] ?: d.usualFreeTime,
        )
    }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val s = change(prefs.toSettings())
            prefs[Keys.onboarded] = s.onboarded
            prefs[Keys.userName] = s.userName
            prefs[Keys.storage] = json.encodeToString(Storage.serializer(), s.storage)
            prefs[Keys.weeklyPrompt] = s.weeklyPrompt
            prefs[Keys.planningDay] = s.planningDay.name
            prefs[Keys.planningTime] = s.planningTime.toString()
            prefs[Keys.weeklyGoal] = s.weeklyGoal
            prefs[Keys.callMinutes] = s.callReminderMinutes
            prefs[Keys.meetMinutes] = s.meetReminderMinutes
            prefs[Keys.followUps] = s.followUps
            prefs[Keys.digest] = s.morningDigest
            prefs[Keys.digestTime] = s.digestTime.toString()
            prefs[Keys.dynamicColor] = s.dynamicColor
            prefs[Keys.freeTime] = s.usualFreeTime
        }
    }
}
