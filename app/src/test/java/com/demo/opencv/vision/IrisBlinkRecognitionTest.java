package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.demo.opencv.vision.BlinkDetector.Event.*;

/** Raw eyelid ratios through normalization and the real input coordinator. */
public class IrisBlinkRecognitionTest {
    private final EyeOpenness eyes = new EyeOpenness();
    private final GazeInputController input = new GazeInputController();
    private long now;
    private DetectionOutput frame(double left, double right, long delta) {
        now += delta; eyes.update(true, left, right, now);
        DetectionOutput output = new DetectionOutput(); output.AnalyzedData = new GazeData();
        boolean open = eyes.ready() && eyes.left() >= .65 && eyes.right() >= .65;
        output.AnalyzedData.setGazeData(open, 0, 1, 0);
        output.leftEyeOpenness = eyes.left(); output.rightEyeOpenness = eyes.right();
        output.blinkSignalsValid = eyes.ready(); input.update(output, now);
        return output;
    }
    private void prepare(double left, double right) {
        input.setBlinkTracking(true);
        frame(left, right, 0);
        for (int i = 0; i < 13; i++) frame(left, right, 100);
        assertTrue(eyes.ready());
    }
    private DetectionOutput reopen(double left, double right) {
        assertEquals(NONE, frame(left, right, 100).blinkEvent);
        return frame(left, right, 100);
    }
    @Test public void leftWinkWithCoupledSquintDoesNotBecomeBilateralCancel() {
        prepare(.16, .18);
        // A short landmark collapse is followed by the other eye remaining partly open.
        frame(.02, .04, 100);
        for (int i = 0; i < 8; i++) frame(.02, .10, 100);
        DetectionOutput result = reopen(.16, .18);
        assertEquals(LEFT_WINK, result.blinkEvent); assertTrue(result.blinkCommandArmed);
        assertEquals(0, result.gestureOutput);
    }
    @Test public void rightWinkWithCoupledSquintRemainsRightWink() {
        prepare(.18, .16); frame(.04, .02, 100);
        for (int i = 0; i < 8; i++) frame(.10, .02, 100);
        DetectionOutput result = reopen(.18, .16);
        assertEquals(RIGHT_WINK, result.blinkEvent); assertTrue(result.blinkCommandArmed);
    }
    @Test public void genuineSustainedBilateralClosureStillCancels() {
        prepare(.16, .18);
        for (int i = 0; i < 8; i++) frame(.02, .02, 100);
        DetectionOutput result = reopen(.16, .18);
        assertEquals(BOTH_CLOSED, result.blinkEvent); assertTrue(result.blinkCommandArmed);
    }
    @Test public void heavyUnequalSquintIsUncertainRatherThanCancel() {
        prepare(.16, .18);
        for (int i = 0; i < 8; i++) frame(.0064, .0468, 100);
        DetectionOutput result = reopen(.16, .18);
        assertEquals(NONE, result.blinkEvent);
    }
}
