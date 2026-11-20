package com.demo.opencv.vision;

import org.junit.Test;
import static org.junit.Assert.*;

public class EyeOpennessTest {
    private final EyeOpenness eyes = new EyeOpenness();
    private void learn(double left, double right) {
        for (int t = 0; t <= 1000; t += 100) eyes.update(true, left, right, t);
        assertTrue(eyes.ready());
    }
    @Test public void ratioUsesAllThreeEyelidPairs() {
        double ratio = EyeOpenness.ratio(new double[]{0, 0}, new double[]{40, 0},
                new double[]{10, -2}, new double[]{10, 2},
                new double[]{20, -4}, new double[]{20, 4},
                new double[]{30, -6}, new double[]{30, 6});
        assertEquals(.2, ratio, 1e-9);
    }
    @Test public void ratioPreservesTranslationScaleAndHeadRoll() {
        double[][] p = {{0, 0}, {40, 0}, {10, -2}, {10, 2}, {20, -4}, {20, 4}, {30, -6}, {30, 6}};
        for (double[] point : p) {
            double x = point[0], y = point[1], angle = .6;
            point[0] = 90 + 2 * (x * Math.cos(angle) - y * Math.sin(angle));
            point[1] = 60 + 2 * (x * Math.sin(angle) + y * Math.cos(angle));
        }
        assertEquals(.2, EyeOpenness.ratio(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]), 1e-9);
        assertEquals(.2, EyeOpenness.ratio(p[1], p[0], p[2], p[3], p[4], p[5], p[6], p[7]), 1e-9);
    }
    @Test public void verySmallEyeDoesNotProduceAClosureScore() {
        assertEquals(-1, EyeOpenness.ratio(new double[]{0, 0}, new double[]{8, 0},
                new double[]{4, -1}, new double[]{4, 1}), 0);
    }
    @Test public void referenceNeedsContinuousOpenFramesForOneSecond() {
        for (int t = 0; t < 1000; t += 100) eyes.update(true, .12, .24, t);
        assertFalse(eyes.ready());
        eyes.update(true, .12, .24, 1000); assertTrue(eyes.ready());
    }
    @Test public void eachEyeHasItsOwnOpenReference() {
        learn(.12, .24);
        assertEquals(1, eyes.left(), .001); assertEquals(1, eyes.right(), .001);
        eyes.update(true, .02, .12, 1100);
        assertEquals(1.0 / 6, eyes.left(), .001); assertEquals(.5, eyes.right(), .001);
    }
    @Test public void aClosedEyeCannotBecomeTheInitialOpenReference() {
        for (int t = 0; t <= 2000; t += 100) eyes.update(true, .02, .2, t);
        assertFalse(eyes.ready()); assertEquals(-1, eyes.left(), 0);
    }
    @Test public void prolongedWinkDoesNotLowerTheLearnedReference() {
        learn(.12, .24);
        for (int t = 1100; t <= 3100; t += 100) eyes.update(true, .02, .24, t);
        assertEquals(1.0 / 6, eyes.left(), .001);
        eyes.update(true, .12, .24, 3200); assertEquals(1, eyes.left(), .001);
    }
    @Test public void lossOfFaceRestartsIncompleteLearning() {
        for (int t = 0; t <= 600; t += 100) eyes.update(true, .12, .24, t);
        eyes.update(false, -1, -1, 700);
        for (int t = 800; t <= 1700; t += 100) eyes.update(true, .12, .24, t);
        assertFalse(eyes.ready());
        eyes.update(true, .12, .24, 1800); assertTrue(eyes.ready());
    }
    @Test public void cameraGapCannotFinishLearningFromOldFrames() {
        for (int t = 0; t <= 600; t += 100) eyes.update(true, .12, .24, t);
        for (int t = 2000; t <= 2900; t += 100) eyes.update(true, .12, .24, t);
        assertFalse(eyes.ready());
        eyes.update(true, .12, .24, 3000); assertTrue(eyes.ready());
    }
    @Test public void relearningDiscardsReferencesWithoutConfusingThemWithClosedEyes() {
        learn(.12, .24); eyes.reset();
        assertFalse(eyes.ready()); assertEquals(-1, eyes.left(), 0);
        learn(.18, .1);
        assertEquals(1, eyes.left(), .001); assertEquals(1, eyes.right(), .001);
    }
}
