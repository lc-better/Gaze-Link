package com.demo.opencv.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MouthInputControllerTest {
    @Test public void completedMouthActionArmsOnlyWithAStableSideGaze() {
        GazeInputController input = new GazeInputController();
        input.setMouthTracking(true);
        long now = 0;
        DetectionOutput frame = new DetectionOutput();
        frame.AnalyzedData = new GazeData();
        frame.AnalyzedData.setGazeData(true, 1, 1, 0);
        frame.mouthSignalsValid = true;
        frame.mouthOpenRatio = .05f;
        input.update(frame, now);
        frame = update(input, now = 250, .05f);
        frame = update(input, now = 300, .25f);
        frame = update(input, now = 650, .25f);
        frame = update(input, now = 700, .05f);
        frame = update(input, now = 850, .05f);
        assertEquals(MouthDetector.Event.OPEN_CONFIRM, frame.mouthEvent);
        assertTrue(frame.mouthCommandArmed);
    }

    private DetectionOutput update(GazeInputController input, long now, float ratio) {
        DetectionOutput frame = new DetectionOutput();
        frame.AnalyzedData = new GazeData();
        frame.AnalyzedData.setGazeData(true, 1, 1, 0);
        frame.mouthSignalsValid = true; frame.mouthOpenRatio = ratio;
        input.update(frame, now);
        return frame;
    }
}
