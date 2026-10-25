package org.pixeldroid.media_editor.photoEdit

import android.app.UiAutomation
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.RunListener

/**
 * Runs all tests in one orientation, whatever the device is turned to: portrait, or landscape
 * with the runner argument orientation=landscape (see scripts/connected-tests.sh, which runs the
 * tests once in each). Registered as the runner's listener in build.gradle.kts.
 */
class OrientationListener : RunListener() {

    override fun testRunStarted(description: Description?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.setRotation(
            if (TestOrientation.landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0
        )
        // Turning takes a moment
        val wanted = if (TestOrientation.landscape) Surface.ROTATION_90 else Surface.ROTATION_0
        val until = SystemClock.uptimeMillis() + 5000
        val display = instrumentation.targetContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        while (display.rotation != wanted && SystemClock.uptimeMillis() < until) {
            SystemClock.sleep(100)
        }
    }

    override fun testRunFinished(result: Result?) {
        // Turns with the device again
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }
}

/** The orientation the tests run in, see [OrientationListener] */
object TestOrientation {
    val landscape: Boolean
        get() = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
}
