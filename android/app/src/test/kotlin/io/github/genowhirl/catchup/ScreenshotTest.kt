package io.github.genowhirl.catchup

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.genowhirl.catchup.core.DayPart
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.PlanInput
import io.github.genowhirl.catchup.core.applyOps
import io.github.genowhirl.catchup.core.parseAll
import io.github.genowhirl.catchup.core.sync.SyncState
import io.github.genowhirl.catchup.data.AppSettings
import io.github.genowhirl.catchup.ui.screens.OnboardingActions
import io.github.genowhirl.catchup.ui.screens.OnboardingScreen
import io.github.genowhirl.catchup.ui.screens.PeopleScreen
import io.github.genowhirl.catchup.ui.screens.PersonActions
import io.github.genowhirl.catchup.ui.screens.PersonScreen
import io.github.genowhirl.catchup.ui.screens.PlanWeekScreen
import io.github.genowhirl.catchup.ui.screens.PlansTab
import io.github.genowhirl.catchup.ui.screens.SettingsActions
import io.github.genowhirl.catchup.ui.screens.SettingsScreen
import io.github.genowhirl.catchup.ui.screens.TodayActions
import io.github.genowhirl.catchup.ui.screens.TodayScreen
import io.github.genowhirl.catchup.ui.theme.CatchUpTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Renders the main screens with the example people (`../examples/people`) for design review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi", application = Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 11, 18, 30)
    private val examples = File("../../examples/people").listFiles { f -> f.name.endsWith(".md") }!!
        .associate { "people/${it.name}" to it.readText() }
    // A few extras so every section has something in it.
    private val files = applyOps(
        examples,
        listOf(
            Op.SavePlan("people/jonas-weber.md", PlanInput("2026-10-14 19:30", "Video call")),
            Op.SavePlan("people/linda-moreno.md", PlanInput("2026-10-11 12:30", "Lunch", "Café Roma")),
            Op.SavePlan("people/leo-okafor.md", PlanInput("2026-11-20", "Dinner")),
        ),
    )
    private val people = parseAll(files).first
    private val noop = {}

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CatchUpTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val todayActions = TodayActions({}, { _, _ -> }, {}, { _, _ -> }, noop, {}, {}, noop, noop)

    @Test fun today() = shot("01-today") { TodayScreen(people, now, "Gene", 3, todayActions, PaddingValues()) }

    @Test fun todayDark() = shot("02-today-dark", dark = true) { TodayScreen(people, now, "Gene", 3, todayActions, PaddingValues()) }

    @Test fun peopleList() = shot("03-people") { PeopleScreen(people, now.toLocalDate(), {}, noop, noop) }

    @Test fun person() = shot("04-person") {
        PersonScreen(
            people.first { it.id == "maya-patel" }, now.toLocalDate(),
            PersonActions(noop, noop, noop, noop, {}, {}, noop, { _, _ -> }, {}, {}, {}, {}, {}, { _, _ -> }),
        )
    }

    @Test fun planWeek() {
        shot("05-plan-week") {
            PlanWeekScreen(
                people, now, 3,
                setOf(DayOfWeek.TUESDAY to DayPart.EVENING, DayOfWeek.THURSDAY to DayPart.EVENING, DayOfWeek.SATURDAY to DayPart.AFTERNOON, DayOfWeek.SUNDAY to DayPart.LUNCH),
                noop, { _, _ -> }, {}, { _, _ -> }, { _, _ -> },
            )
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Suggest who to see"))
        compose.onNodeWithText("Suggest who to see").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/06-plan-week-suggestions.png")
    }

    @Test fun plans() = shot("07-plans") { PlansTab(people, now.toLocalDate(), noop, { _, _ -> }, noop) }

    @Test fun settings() = shot("08-settings") {
        SettingsScreen(
            AppSettings(), SyncState(loaded = true), emptyMap(), notificationsAllowed = true, exactAlarmsAllowed = true,
            SettingsActions({}, noop, noop, noop, noop, noop, noop, {}),
        )
    }

    @Test fun onboarding() = shot("09-onboarding") {
        OnboardingScreen(false, false, OnboardingActions(noop, noop, noop, noop, noop, noop) { _, _, _, _ -> })
    }
}
