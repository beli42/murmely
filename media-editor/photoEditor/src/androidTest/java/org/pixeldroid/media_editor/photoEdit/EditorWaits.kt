package org.pixeldroid.media_editor.photoEdit

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail

/**
 * Waits until the editor has loaded its image on the GL thread and the main thread is idle, so
 * changes made before (slider, filter, drawing) have reached the engine. Replaces fixed sleeps
 * of several seconds: usually done after a few hundred milliseconds.
 */
fun ActivityScenario<PhotoEditActivity>.awaitImageLoaded(timeoutMs: Long = 10_000) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        var loaded = false
        onActivity { loaded = it.isImageLoaded }
        if (loaded) break
        if (System.currentTimeMillis() > end) fail("image not loaded within $timeoutMs ms")
        Thread.sleep(POLL_MS)
    }
    instrumentation.waitForIdleSync()
}

private const val POLL_MS = 50L
