package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;

public class IrisFeaturesTest {
    private final double[][] eye = {{0,0},{40,0},{20,-5},{20,5},{14,1}};
    private IrisFeatures.Eye measure(double[][] p) { return IrisFeatures.measure(p[0],p[1],p[2],p[3],p[4]); }
    @Test public void translationScaleAndRollPreserveIrisPosition() {
        IrisFeatures.Eye original = measure(eye);
        double[][] changed = new double[5][2]; double angle = .4;
        for (int i=0;i<5;i++) {
            changed[i][0] = 80 + 2*(eye[i][0]*Math.cos(angle)-eye[i][1]*Math.sin(angle));
            changed[i][1] = 90 + 2*(eye[i][0]*Math.sin(angle)+eye[i][1]*Math.cos(angle));
        }
        IrisFeatures.Eye result = measure(changed);
        assertEquals(original.horizontal,result.horizontal,1e-9);
        assertEquals(original.vertical,result.vertical,1e-9);
        assertEquals(original.openness,result.openness,1e-9);
        assertTrue(result.usable);
    }
    @Test public void aClosedEyeDoesNotDisableTheOtherSelectedEye() {
        IrisFeatures.Eye open = measure(eye);
        IrisFeatures.Eye closed = IrisFeatures.measure(eye[0],eye[1],new double[]{20,-1},new double[]{20,1},eye[4]);
        assertFalse(IrisFeatures.usable(open,closed,IrisFeatures.BOTH));
        assertTrue(IrisFeatures.usable(open,closed,IrisFeatures.LEFT));
        assertFalse(IrisFeatures.usable(open,closed,IrisFeatures.RIGHT));
    }
    @Test public void verySmallEyesAreNotUsable() {
        assertFalse(IrisFeatures.measure(new double[]{0,0},new double[]{8,0},eye[2],eye[3],eye[4]).usable);
    }
}
