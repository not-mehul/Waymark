package com.waymark.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The release build's package: the profile and the timings are for it. */
const val PACKAGE = "com.waymark"

private const val WAIT_MILLIS = 5_000L

/**
 * What a traveler does first, and so what is worth compiling ahead of time:
 * open the app, open a trip, read down its timeline, and come back.
 *
 * On a fresh install the shelf is empty, so the worked example is loaded
 * first; after that it is there to open.
 */
fun MacrobenchmarkScope.openATrip() {
    device.wait(Until.hasObject(By.textContains("worked example").pkg(PACKAGE)), WAIT_MILLIS)
    device.findObject(By.text("Load the worked example"))?.click()

    val trip = device.wait(Until.findObject(By.text("London & Paris")), WAIT_MILLIS) ?: return
    trip.click()
    device.waitForIdle()

    device.findObject(By.scrollable(true))?.let { timeline ->
        timeline.setGestureMargin(device.displayWidth / 5)
        repeat(3) { timeline.fling(Direction.DOWN) }
        repeat(3) { timeline.fling(Direction.UP) }
    }
    device.pressBack()
    device.waitForIdle()
}
