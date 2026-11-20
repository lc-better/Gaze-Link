package com.demo.opencv.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GazeSmootherTest {
    @Test public void firstDirectionIsAvailableImmediately() {
        GazeSmoother smoother = new GazeSmoother();
        assertEquals(1, smoother.update(true, 1, 100));
    }

    @Test public void directionChangeNeedsShortStableWindow() {
        GazeSmoother smoother = new GazeSmoother();
        assertEquals(0, smoother.update(true, 0, 0));
        assertEquals(2, smoother.update(true, 2, 50));
        assertEquals(2, smoother.update(true, 1, 100));
        assertEquals(2, smoother.update(true, 1, 219));
        assertEquals(1, smoother.update(true, 1, 220));
    }

    @Test public void invalidFrameResetsTheStableDirection() {
        GazeSmoother smoother = new GazeSmoother();
        assertEquals(1, smoother.update(true, 1, 0));
        assertEquals(-1, smoother.update(false, 1, 20));
        assertEquals(2, smoother.update(true, 2, 40));
    }
}
