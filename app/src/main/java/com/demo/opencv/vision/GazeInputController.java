package com.demo.opencv.vision;

/** Coordinates dwell and blink input using the same camera-frame clock. */
public final class GazeInputController {
    private final DwellController dwell = new DwellController();
    private final BlinkDetector blink = new BlinkDetector();
    private final MouthDetector mouth = new MouthDetector();
    private final GazeSmoother smoother = new GazeSmoother();
    private boolean blinkTracking, mouthTracking, centreReady, gestureReady;
    private long previousFrame = -1;

    public void reset() {
        dwell.reset(); blink.reset(); mouth.reset(); smoother.reset();
        centreReady = gestureReady = false; previousFrame = -1;
    }
    public void setBlinkTracking(boolean enabled) {
        if (blinkTracking != enabled) { blinkTracking = enabled; reset(); }
    }
    public void setMouthTracking(boolean enabled) {
        if (mouthTracking != enabled) { mouthTracking = enabled; mouth.reset(); }
    }
    public void setDwellMillis(long millis) { dwell.setDwellMillis(millis); reset(); }

    public void update(DetectionOutput frame, long now) {
        if (blinkTracking && previousFrame >= 0 && now - previousFrame > 400) reset();
        previousFrame = now;
        GazeData gaze = frame.AnalyzedData;
        int direction = smoother.update(gaze.Success, gaze.GazeType, now);
        boolean gazeValid = direction >= 0;
        frame.smoothedGazeType = direction;
        frame.smoothedGazeSuccess = gazeValid;
        frame.gazeConfidence = !gazeValid ? 0 : gaze.GazeProbability <= 0 ? 1
                : Math.max(0, Math.min(1, 1 - gaze.GazeProbability / .02f));
        if (blinkTracking) {
            boolean wasBlinking = blink.inProgress();
            frame.blinkEvent = blink.update(frame.blinkSignalsValid,
                    frame.leftEyeOpenness, frame.rightEyeOpenness, now);
            // Capture readiness at closure, before the closed eye invalidates gaze.
            if (!wasBlinking && blink.inProgress()) {
                gestureReady = centreReady; centreReady = false;
            }
            if (!frame.blinkSignalsValid) gestureReady = false;
            frame.blinkCommandArmed = gestureReady;
            frame.blinkProgress = blink.progress();
        }
        if (blinkTracking && blink.blocksGaze()) {
            dwell.reset(); centreReady = false;
        } else {
            if (blinkTracking && !frame.blinkSignalsValid) dwell.reset();
            frame.gestureOutput = dwell.update(gazeValid, direction, now, frame.gazeConfidence);
            centreReady = gazeValid && direction == 0 && dwell.isArmed()
                    && frame.blinkSignalsValid;
        }
        if (mouthTracking && !(blinkTracking && blink.blocksGaze())) {
            frame.mouthEvent = mouth.update(frame.mouthSignalsValid, frame.mouthOpenRatio, now);
            frame.mouthProgress = mouth.progress();
            frame.mouthCommandArmed = gazeValid && direction > 0
                    && frame.mouthEvent == MouthDetector.Event.OPEN_CONFIRM;
        } else {
            mouth.reset(); frame.mouthEvent = MouthDetector.Event.NONE;
            frame.mouthProgress = 0; frame.mouthCommandArmed = false;
        }
        // The captured centre context belongs to this gesture only.
        if (!blink.inProgress()) gestureReady = false;
        frame.dwellProgress = dwell.getProgress();
        frame.armed = dwell.isArmed();
    }
}
