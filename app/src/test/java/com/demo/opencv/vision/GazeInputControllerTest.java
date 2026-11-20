package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.demo.opencv.vision.BlinkDetector.Event.*;

public class GazeInputControllerTest {
    private final GazeInputController input = new GazeInputController();
    private long now;

    private DetectionOutput frame(boolean valid, int direction, float left, float right,
                                  boolean signals, long delta) {
        now += delta;
        DetectionOutput frame = new DetectionOutput();
        frame.AnalyzedData = new GazeData();
        frame.AnalyzedData.setGazeData(valid, direction, 1, 0);
        frame.leftEyeOpenness = left; frame.rightEyeOpenness = right;
        frame.blinkSignalsValid = signals;
        input.update(frame, now);
        return frame;
    }
    private DetectionOutput open(int direction, long delta) {
        return frame(true, direction, 1, 1, true, delta);
    }
    private void centre() {
        open(0, 0); open(0, 150); assertTrue(open(0, 100).armed);
    }
    private void enableAndCentre() { input.setBlinkTracking(true); centre(); }
    private DetectionOutput gesture(float left, float right, int duration) {
        for (int elapsed = 0; elapsed < duration; elapsed += 100) {
            DetectionOutput closed = frame(false, 0, left, right, true, 100);
            assertEquals(NONE, closed.blinkEvent);
            assertEquals(0, closed.gestureOutput);
        }
        assertEquals(NONE, open(1, 100).blinkEvent);
        return open(1, 100);
    }
    @Test public void centreReadyOnExactThresholdAllowsConfirmation() {
        enableAndCentre();
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent);
        assertTrue(result.blinkCommandArmed);
        assertEquals(0, result.gestureOutput);
    }
    @Test public void rightAndBothGesturesKeepTheSameCentreRequirement() {
        enableAndCentre();
        DetectionOutput right = gesture(1, 0, 500);
        assertEquals(RIGHT_WINK, right.blinkEvent); assertTrue(right.blinkCommandArmed);
        input.reset(); centre();
        DetectionOutput both = gesture(0, 0, 800);
        assertEquals(BOTH_CLOSED, both.blinkEvent); assertTrue(both.blinkCommandArmed);
    }
    @Test public void practiceCanRecordWinkWithoutCalibratedGaze() {
        input.setBlinkTracking(true);
        frame(false, 0, 1, 1, true, 0);
        frame(false, 0, 1, 1, true, 250);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent);
        assertFalse(result.blinkCommandArmed);
    }
    @Test public void lookingAwayBeforeWinkDiscardsCentreContext() {
        enableAndCentre(); open(1, 100);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent);
        assertFalse(result.blinkCommandArmed);
    }
    @Test public void closureInterruptsPartialDwellAndCannotAlsoSelect() {
        enableAndCentre(); open(1, 100); open(1, 400);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent); assertFalse(result.blinkCommandArmed);
        for (int i = 0; i < 20; i++) assertEquals(0, open(1, 100).gestureOutput);
        centre(); open(1, 100); open(1, 400);
        assertEquals(1, open(1, 400).gestureOutput);
    }
    @Test public void missingFaceDiscardsGestureAndRequiresFreshCentre() {
        enableAndCentre(); frame(false, 0, 0, 1, true, 100);
        frame(false, 0, 0, 1, true, 300);
        frame(false, 0, -1, -1, false, 100);
        for (int i = 0; i < 4; i++) assertEquals(NONE, open(1, 100).blinkEvent);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent); assertFalse(result.blinkCommandArmed);
    }
    @Test public void cameraGapCannotReuseCentreEvenIfDwellGapLimitWasNotReached() {
        enableAndCentre(); open(0, 450);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(NONE, result.blinkEvent); assertFalse(result.blinkCommandArmed);
        for (int i = 0; i < 12; i++) open(1, 100);
        assertFalse(gesture(0, 1, 500).blinkCommandArmed);
    }
    @Test public void screenChangeCancelsAnUnfinishedGesture() {
        enableAndCentre(); frame(false, 0, 0, 1, true, 100);
        frame(false, 0, 0, 1, true, 300); input.reset();
        assertEquals(NONE, open(1, 100).blinkEvent);
        assertEquals(NONE, open(1, 100).blinkEvent);
        assertFalse(open(1, 100).blinkCommandArmed);
    }
    @Test public void rejectedNaturalBlinkDoesNotLeaveAnArmedGesture() {
        enableAndCentre(); assertEquals(NONE, gesture(0, 0, 100).blinkEvent);
        for (int i = 0; i < 12; i++) open(1, 100);
        DetectionOutput result = gesture(1, 0, 500);
        assertEquals(RIGHT_WINK, result.blinkEvent); assertFalse(result.blinkCommandArmed);
    }
    @Test public void secondWinkNeedsCentreAgainEvenAfterCooldown() {
        enableAndCentre(); assertTrue(gesture(0, 1, 500).blinkCommandArmed);
        for (int i = 0; i < 12; i++) open(1, 100);
        DetectionOutput result = gesture(0, 1, 500);
        assertEquals(LEFT_WINK, result.blinkEvent); assertFalse(result.blinkCommandArmed);
    }
    @Test public void disablingDuringGestureDropsThePendingEvent() {
        enableAndCentre(); frame(false, 0, 0, 1, true, 100);
        frame(false, 0, 0, 1, true, 300); input.setBlinkTracking(false);
        assertEquals(NONE, open(1, 100).blinkEvent);
        assertEquals(NONE, open(1, 100).blinkEvent);
        centre(); open(2, 100); open(2, 400);
        assertEquals(2, open(2, 400).gestureOutput);
    }
    @Test public void disabledBlinkTrackingPreservesDwellWithOneUsableEye() {
        frame(true, 0, -1, -1, false, 0);
        assertTrue(frame(true, 0, -1, -1, false, 250).armed);
        frame(true, 2, -1, -1, false, 100);
        frame(true, 2, -1, -1, false, 400);
        DetectionOutput result = frame(true, 2, -1, -1, false, 400);
        assertEquals(2, result.gestureOutput); assertEquals(NONE, result.blinkEvent);
    }
}
