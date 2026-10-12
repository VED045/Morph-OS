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
    @Test fun homePagesCanBeAddedAndPersistAcrossRecreation() {
        compose.onNodeWithTag("home_tools").performClick()
        compose.onNodeWithTag("edit_home").performClick()
        compose.onNodeWithTag("add_home_page").performClick()
        compose.onNodeWithTag("done_home_edit").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Home page 4").assertExists().performClick()
        compose.onNode(hasText("Room for your ideas") and hasAnyAncestor(hasTestTag("workspace_page_3"))).assertIsDisplayed()
        compose.onNodeWithTag("home_pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("workspace_page_2").assertIsDisplayed()
    }
    @Test fun localModelSetupReflectsWhetherAFileIsInstalled() {
        compose.onNodeWithTag("studio").performClick()
        val installed = java.io.File(compose.activity.filesDir, "on-device/model.litertlm").let { it.isFile && it.length() >= 1048576 }
        val status = compose.onNodeWithTag("local_model_status").performScrollTo()
        if (installed) {
            status.assert(hasText("MB", substring = true))
            compose.onNodeWithTag("verify_local_model").assertIsEnabled()
        } else {
            status.assertTextEquals("No on-device model installed")
            compose.onNodeWithTag("verify_local_model").assertIsNotEnabled()
        }
    }
    @Test fun radialDrawerPaginatesAndHasAccessibleListAlternative() {
        compose.onNodeWithTag("all_apps").performClick()
        compose.onNodeWithTag("drawer_options").performClick()
        compose.onNodeWithTag("mode_RADIAL").performClick()
        compose.onNodeWithTag("radial_drawer").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNode(hasText("2 /", substring = true)).assertExists()
        compose.onNodeWithTag("drawer_options").performClick()
        compose.onNodeWithTag("list_mode").performClick()
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
        compose.onNodeWithTag("home_tools").performClick()
        compose.onNodeWithTag("widgets").performClick()
        compose.onNodeWithTag("widget_prompt").performScrollTo().performTextInput("Star clock")
        compose.onNodeWithTag("preview_widget").performScrollTo().performClick()
        compose.onNodeWithTag("add_widget").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Home page 2").performClick()
        compose.onAllNodesWithContentDescription("Analog clock", substring = true).assertCountEquals(2)
    }
    @Test fun offlineTextPreviewApplyUndoAndRedo() {
        compose.onNodeWithTag("studio").performClick()
        compose.onNodeWithTag("design_prompt").performTextInput("Use a list drawer")
        compose.onNodeWithTag("generate_design").performClick()
        compose.onNodeWithTag("apply_design").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("all_apps").performClick()
        compose.onNodeWithTag("app_list").assertExists()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("home_tools").performClick()
        compose.onNodeWithTag("studio_menu").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("undo_design").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("undo_design").performScrollTo().performClick()
        compose.onNodeWithTag("redo_design").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("redo_design").assertIsNotEnabled()
    }
    @Test fun opensDrawerSearchesAndReturnsHome() {
        compose.onNodeWithTag("all_apps").performClick()
        compose.onNodeWithTag("app_search").performTextInput("a-no-app-should-have-this-name-18492")
        compose.onNodeWithText("No matching apps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.onNodeWithTag("all_apps").assertExists()
    }
    @Test fun themesAndHomeSurviveActivityRecreation() {
        compose.onNodeWithTag("home_tools").performClick()
        compose.onNodeWithTag("themes").performClick()
        compose.onNodeWithTag("theme_gallery").performScrollToNode(hasTestTag("theme_nothing"))
        compose.onNodeWithTag("theme_nothing").performClick()
        compose.onNodeWithText("APPLIED").assertExists()
        compose.onNodeWithContentDescription("Back to home").performClick()
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("home_theme_name").assertTextEquals("Nothing OS")
    }
}
