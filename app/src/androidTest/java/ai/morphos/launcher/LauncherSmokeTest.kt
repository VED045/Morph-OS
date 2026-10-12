package ai.morphos.launcher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun radialDrawerPaginatesAndHasAccessibleListAlternative() {
        compose.onNodeWithTag("all_apps").performScrollTo().performClick()
        compose.onNodeWithTag("mode_RADIAL").performScrollTo().performClick()
        compose.onNodeWithTag("radial_drawer").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNode(hasText("2 /", substring = true)).assertExists()
        compose.onNodeWithTag("list_mode").performScrollTo().performClick()
        compose.onNodeWithTag("app_list").assertExists()
    }
    @Test fun notificationDashboardDenialIsSafe() {
        compose.onNodeWithTag("controls").performClick()
        compose.onNodeWithTag("notification_dashboard").performScrollTo().performClick()
        compose.onNodeWithText("Connect your notifications").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to home").performClick()
    }
    @Test fun unifiedSearchWorksWithoutContactsPermission() {
        compose.onNodeWithTag("search_home").performClick()
        compose.onNodeWithTag("unified_search").performTextInput("Clock")
        compose.onNodeWithTag("search_result_Clock").assertExists()
        compose.onNodeWithContentDescription("Back to home").performClick()
    }
    @Test fun widgetPromptAddsPersistentFunctionalClock() {
        compose.onNodeWithTag("widgets").performScrollTo().performClick()
        compose.onNodeWithTag("widget_prompt").performTextInput("Star clock")
        compose.onNodeWithTag("preview_widget").performClick()
        compose.onNodeWithTag("add_widget").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithContentDescription("Analog clock", substring = true).assertCountEquals(2)
    }
    @Test fun offlineTextPreviewApplyUndoAndRedo() {
        compose.onNodeWithTag("studio").performClick()
        compose.onNodeWithTag("design_prompt").performTextInput("Use a list drawer")
        compose.onNodeWithTag("generate_design").performClick()
        compose.onNodeWithTag("apply_design").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("all_apps").performScrollTo().performClick()
        compose.onNodeWithTag("app_list").assertExists()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("studio").performClick()
        compose.onNodeWithTag("undo_design").performScrollTo().performClick()
        compose.onNodeWithTag("redo_design").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("redo_design").assertIsNotEnabled()
    }
    @Test fun opensDrawerSearchesAndReturnsHome() {
        compose.onNodeWithTag("all_apps").performScrollTo().performClick()
        compose.onNodeWithTag("app_search").performTextInput("a-no-app-should-have-this-name-18492")
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("all_apps").assertExists()
    }
    @Test fun themesAndHomeSurviveActivityRecreation() {
        compose.onNodeWithTag("themes").performClick()
        compose.onNodeWithTag("theme_gallery").performScrollToNode(hasTestTag("theme_nothing"))
        compose.onNodeWithTag("theme_nothing").performClick()
        compose.onNodeWithText("APPLIED").assertExists()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("morph / dot").assertIsDisplayed()
    }
}
