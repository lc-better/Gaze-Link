package com.demo.opencv.vision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DwellConfidenceTest {
    private DwellController armed() {
        DwellController d = new DwellController();
        d.update(true, 0, 0);
        d.update(true, 0, 300);
        return d;
    }

    @Test public void highConfidenceConfirmsEarlier() {
        DwellController d = armed();
        d.update(true, 1, 400, .9f);
        d.update(true, 1, 800, .9f);
        assertEquals(1, d.update(true, 1, 1080, .9f));
    }

    @Test public void lowConfidenceRequiresLongerDwell() {
        DwellController d = armed();
        d.update(true, 1, 400, .2f);
        d.update(true, 1, 800, .2f);
        assertEquals(0, d.update(true, 1, 1190, .2f));
        assertEquals(1, d.update(true, 1, 1320, .2f));
    }
}
