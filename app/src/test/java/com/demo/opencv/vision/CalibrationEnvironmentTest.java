package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalibrationEnvironmentTest {
    private final String original = CalibrationEnvironment.key("phone-a", 1, 2400, 1080);

    @Test public void sameInstallationAndGeometryCanReuseCalibration() {
        assertTrue(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-a", 1, 2400, 1080)));
    }
    @Test public void restoredBackupOnAnotherPhoneCannotReuseCalibration() {
        assertFalse(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-b", 1, 2400, 1080)));
    }
    @Test public void reverseLandscapeNeedsCalibrationEvenWithSamePixelSize() {
        assertFalse(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-a", 3, 2400, 1080)));
    }
    @Test public void portraitCannotReuseLandscapeCalibration() {
        assertFalse(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-a", 0, 1080, 2400)));
    }
    @Test public void unfoldingRequiresCalibrationForNewGeometry() {
        assertFalse(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-a", 1, 2208, 1840)));
    }
    @Test public void resolutionChangeCannotReuseCalibration() {
        assertFalse(CalibrationEnvironment.matches(original, CalibrationEnvironment.key("phone-a", 1, 1600, 720)));
    }
    @Test public void legacyCalibrationWithoutEnvironmentRequiresVerificationAgain() {
        assertFalse(CalibrationEnvironment.matches("", original));
        assertFalse(CalibrationEnvironment.matches(null, original));
    }
    @Test public void missingCurrentEnvironmentNeverEnablesInput() {
        assertFalse(CalibrationEnvironment.matches(original, ""));
        assertFalse(CalibrationEnvironment.matches(original, null));
        assertFalse(CalibrationEnvironment.matches("", ""));
    }
    @Test public void invalidIdentityProducesNoEnvironment() {
        assertEquals("", CalibrationEnvironment.key("", 1, 2400, 1080));
        assertEquals("", CalibrationEnvironment.key(null, 1, 2400, 1080));
    }
    @Test public void invalidGeometryProducesNoEnvironment() {
        assertEquals("", CalibrationEnvironment.key("phone-a", 1, 0, 1080));
        assertEquals("", CalibrationEnvironment.key("phone-a", 1, 2400, -1));
    }
    @Test public void invalidRotationProducesNoEnvironment() {
        assertEquals("", CalibrationEnvironment.key("phone-a", -1, 2400, 1080));
        assertEquals("", CalibrationEnvironment.key("phone-a", 4, 2400, 1080));
    }
    @Test public void allFourDisplayRotationsAreDistinct() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int rotation = 0; rotation < 4; rotation++) keys.add(CalibrationEnvironment.key("phone-a", rotation, 2400, 1080));
        assertEquals(4, keys.size());
    }
}
