package com.demo.opencv.communication;
import org.junit.Test;
import static org.junit.Assert.*;
public class HoldTrackerTest {
    @Test public void validationRequiresContinuousFreshMatchingFrames() {
        HoldTracker h=new HoldTracker(); assertEquals(0,h.update(true,0)); assertEquals(400,h.update(true,400));
        assertEquals(0,h.update(false,500)); assertEquals(0,h.update(true,600));
        assertEquals(400,h.update(true,1000)); assertEquals(650,h.update(true,1250));
    }
    @Test public void missingFramesAndResetCancelCaptureTime() {
        HoldTracker h=new HoldTracker(); h.update(true,0); assertEquals(0,h.update(true,1000));
        h.reset(); assertEquals(0,h.update(true,1200));
    }
}