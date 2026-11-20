package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.demo.opencv.vision.BlinkDetector.Event.*;

public class BlinkDetectorTest {
    private final BlinkDetector detector = new BlinkDetector();
    private long now;

    private BlinkDetector.Event frame(float left, float right, long delta) {
        now += delta; return detector.update(true, left, right, now);
    }
    private void openAndArm() {
        frame(1, 1, 0); frame(1, 1, 150); frame(1, 1, 150);
    }
    private BlinkDetector.Event gesture(float left, float right, int duration) {
        assertEquals(NONE, frame(left, right, 100));
        for (int elapsed = 100; elapsed < duration; elapsed += 100) assertEquals(NONE, frame(left, right, 100));
        assertEquals(NONE, frame(1, 1, 100));
        return frame(1, 1, 100);
    }
    @Test public void shortNaturalBlinkDoesNotConfirmOrCancel() {
        openAndArm(); assertEquals(NONE, gesture(0, 0, 100));
    }
    @Test public void deliberateLeftWinkConfirmsOnlyAfterReopening() {
        openAndArm(); assertEquals(LEFT_WINK, gesture(0, 1, 500));
    }
    @Test public void deliberateRightWinkIsSeparateFromTheLeft() {
        openAndArm(); assertEquals(RIGHT_WINK, gesture(1, 0, 500));
    }
    @Test public void bilateralCancelRequiresLongerClosure() {
        openAndArm(); assertEquals(NONE, gesture(0, 0, 500));
    }
    @Test public void deliberateBilateralClosureCancelsOnReopening() {
        openAndArm(); assertEquals(BOTH_CLOSED, gesture(0, 0, 800));
    }
    @Test public void holdingTheEyesClosedCannotRepeatOrTriggerEarly() {
        openAndArm(); frame(0, 1, 100);
        for (int i = 0; i < 20; i++) assertEquals(NONE, frame(0, 1, 100));
        frame(1, 1, 100); assertEquals(NONE, frame(1, 1, 100));
    }
    @Test public void startupWithClosedEyesDoesNotIssueACommand() {
        for (int i = 0; i < 10; i++) assertEquals(NONE, frame(0, 0, 100));
        assertEquals(NONE, frame(1, 1, 100)); assertEquals(NONE, frame(1, 1, 150));
    }
    @Test public void closureCannotBridgeAMissingCameraInterval() {
        openAndArm(); frame(0, 1, 100);
        assertEquals(NONE, frame(1, 1, 600)); assertEquals(NONE, frame(1, 1, 150));
    }
    @Test public void losingTheFaceDiscardsThePartialGesture() {
        openAndArm(); frame(0, 1, 100); frame(0, 1, 300);
        assertEquals(NONE, detector.update(false, 0, 1, now + 100)); now += 100;
        frame(1, 1, 100); assertEquals(NONE, frame(1, 1, 150));
    }
    @Test public void unknownEyeProbabilityDoesNotCountAsClosed() {
        openAndArm(); assertEquals(NONE, frame(-1, 1, 100));
        for (int i = 0; i < 6; i++) assertEquals(NONE, frame(-1, 1, 100));
        frame(1, 1, 100); assertEquals(NONE, frame(1, 1, 100));
    }
    @Test public void blinkMayCloseTheTwoEyesOnAdjacentFrames() {
        openAndArm(); frame(0, 1, 100); frame(0, 0, 100);
        for (int i = 0; i < 7; i++) frame(0, 0, 100);
        frame(1, 0, 100); frame(1, 1, 100);
        assertEquals(BOTH_CLOSED, frame(1, 1, 100));
    }
    @Test public void switchingEyesWithoutReopeningIsNotALeftConfirmation() {
        openAndArm(); frame(0, 1, 100); frame(0, 1, 300); frame(1, 0, 100);
        frame(1, 1, 100); assertEquals(NONE, frame(1, 1, 100));
    }
    @Test public void cooldownAndOpenRearmAreRequiredForTheNextCommand() {
        openAndArm(); assertEquals(LEFT_WINK, gesture(0, 1, 500));
        assertEquals(NONE, gesture(0, 1, 500));
        for (int i = 0; i < 6; i++) frame(1, 1, 100);
        assertEquals(RIGHT_WINK, gesture(1, 0, 500));
    }
    @Test public void screenChangeResetPreventsAPartialGestureBecomingACommand() {
        openAndArm(); frame(0, 1, 100); frame(0, 1, 300); detector.reset();
        frame(1, 1, 100); assertEquals(NONE, frame(1, 1, 100));
    }
    private void hold(float left, float right, int duration) {
        for (int elapsed = 0; elapsed < duration; elapsed += 100)
            assertEquals(NONE, frame(left, right, 100));
    }
    private BlinkDetector.Event reopen() {
        assertEquals(NONE, frame(1, 1, 100));
        return frame(1, 1, 100);
    }
    @Test public void briefBilateralNoiseCannotTurnLeftWinkIntoCancel() {
        openAndArm(); hold(0, 1, 400); hold(0, 0, 100); hold(0, 1, 800);
        assertEquals(LEFT_WINK, reopen());
    }
    @Test public void briefBilateralNoiseCannotTurnRightWinkIntoCancel() {
        openAndArm(); hold(1, 0, 400); hold(0, 0, 100); hold(1, 0, 800);
        assertEquals(RIGHT_WINK, reopen());
    }
    @Test public void bilateralNoiseAtStartDoesNotOverrideLaterWink() {
        openAndArm(); hold(0, 0, 100); hold(0, 1, 800);
        assertEquals(LEFT_WINK, reopen());
    }
    @Test public void ambiguousFramesDoNotCountAsBilateralClosureTime() {
        openAndArm(); hold(0, 0, 100); hold(.42f, .42f, 900);
        assertEquals(NONE, reopen());
    }
    @Test public void moderatelySquintingOtherEyeStillAllowsLeftWink() {
        openAndArm(); assertEquals(LEFT_WINK, gesture(.1f, .52f, 500));
    }
    @Test public void moderatelySquintingOtherEyeStillAllowsRightWink() {
        openAndArm(); assertEquals(RIGHT_WINK, gesture(.52f, .1f, 500));
    }
    @Test public void stronglyAsymmetricClosureIsNotABilateralCancel() {
        openAndArm(); assertEquals(NONE, gesture(.04f, .26f, 800));
    }
    @Test public void changingFromSustainedWinkToBothEyesIsAmbiguous() {
        openAndArm(); hold(0, 1, 400); hold(0, 0, 800);
        assertEquals(NONE, reopen());
    }
    @Test public void separateBilateralFlashesDoNotAccumulateIntoLongClosure() {
        openAndArm();
        for (int i = 0; i < 5; i++) { hold(0, 0, 100); hold(.42f, .42f, 100); }
        assertEquals(NONE, reopen());
    }
}
