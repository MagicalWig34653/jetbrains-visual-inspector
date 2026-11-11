package io.github.magicalwig34653.visualinspector.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreviewAvailabilityTest {
    @Test fun supportedIdeWithJcefIsAvailable() {
        assertEquals(PreviewAvailability.AVAILABLE, PreviewAvailability.decide(true) { true })
    }

    @Test fun supportedIdeWithoutJcefIsReportedAsSuch() {
        assertEquals(PreviewAvailability.JCEF_UNSUPPORTED, PreviewAvailability.decide(true) { false })
    }

    @Test fun unsupportedIdeWinsAndNeverAsksJcef() {
        var asked = false
        assertEquals(PreviewAvailability.UNSUPPORTED_IDE, PreviewAvailability.decide(false) { asked = true; true })
        assertFalse("the plugin must not touch JCEF in other IDEs", asked)
    }
}
