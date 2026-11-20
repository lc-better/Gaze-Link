package com.demo.opencv.vision;
import org.junit.Test;
import static org.junit.Assert.*;
public class DwellControllerTest {
    private DwellController armed() {
        DwellController d = new DwellController();
        d.update(true, 0, 0); d.update(true, 0, 300); return d;
    }
    @Test public void centreNeverTriggersAndRequiresReturnBeforeFirstChoice() {
        DwellController d = new DwellController();
        for (int t=0;t<2000;t+=100) assertEquals(0,d.update(true,1,t));
        assertFalse(d.isArmed());
        d.update(true,0,2100); d.update(true,0,2400); assertTrue(d.isArmed());
    }
    @Test public void confirmsAtElapsedTimeAndDoesNotRepeatUntilCentre() {
        DwellController d=armed();
        assertEquals(0,d.update(true,1,400)); assertEquals(0,d.update(true,1,800));
        assertEquals(1,d.update(true,1,1200)); assertEquals(0,d.update(true,1,1500));
        d.update(true,0,1600); d.update(true,0,1900);
        d.update(true,2,2000); d.update(true,2,2400); assertEquals(2,d.update(true,2,2800));
    }
    @Test public void lostTrackingBreaksDwell() {
        DwellController d=armed(); d.update(true,1,400); d.update(true,1,800);
        assertEquals(0,d.update(false,1,900)); assertEquals(0,d.update(true,1,1000));
        assertEquals(0,d.update(true,1,1400)); assertEquals(1,d.update(true,1,1800));
    }
    @Test public void changingDirectionRestartsTimeAndBlinkDoesNotSelect() {
        DwellController d=armed(); d.update(true,1,400); d.update(true,1,800);
        assertEquals(0,d.update(true,2,900)); assertEquals(0,d.update(true,2,1200));
        assertEquals(0,d.update(true,5,1300)); assertEquals(0,d.update(true,2,1400));
        d.update(true,2,1800); assertEquals(2,d.update(true,2,2200));
    }
    @Test public void frameGapAndNavigationResetRequireCentreAgain() {
        DwellController d=armed(); d.update(true,1,400); assertEquals(0,d.update(true,1,1500));
        assertFalse(d.isArmed()); d.reset(); assertEquals(0,d.getProgress(),.001);
    }
}