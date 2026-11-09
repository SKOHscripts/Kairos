package com.skohscripts.kairos.core.alerts

import kotlin.test.Test
import kotlin.test.assertEquals

class BellTest {
    @Test
    fun theForgottenTimerRingsTwiceAndEverythingElseOnce() {
        assertEquals(Bell.SINGLE, Bell.of(ChronoAlertKind.POMODORO))
        assertEquals(Bell.DOUBLE, Bell.of(ChronoAlertKind.IDLE))
        assertEquals(Bell.SINGLE, Bell.of(ChronoAlertKind.OVER))
        assertEquals(Bell.SINGLE, Bell.AUTO_STOP)
    }
}
