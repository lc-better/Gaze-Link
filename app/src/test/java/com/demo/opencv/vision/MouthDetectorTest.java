package com.demo.opencv.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MouthDetectorTest {
    @Test public void shortOpenDoesNotConfirm() {
        MouthDetector d = new MouthDetector();
        d.update(true, .05f, 0); d.update(true, .05f, 250);
        d.update(true, .25f, 300); d.update(true, .25f, 500);
        assertEquals(MouthDetector.Event.NONE, d.update(true, .05f, 550));
    }

    @Test public void completeOpenAndCloseConfirmsOnce() {
        MouthDetector d = new MouthDetector();
        d.update(true, .05f, 0); d.update(true, .05f, 250);
        d.update(true, .25f, 300); d.update(true, .25f, 650);
        d.update(true, .05f, 700);
        assertEquals(MouthDetector.Event.OPEN_CONFIRM, d.update(true, .05f, 850));
        assertEquals(MouthDetector.Event.NONE, d.update(true, .05f, 900));
    }

    @Test public void trackingGapCancelsPartialAction() {
        MouthDetector d = new MouthDetector();
        d.update(true, .05f, 0); d.update(true, .05f, 250);
        d.update(true, .25f, 300); d.update(true, .25f, 700);
        assertEquals(MouthDetector.Event.NONE, d.update(true, .05f, 1300));
    }
}
