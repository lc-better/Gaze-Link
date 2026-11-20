package com.demo.opencv.vision;
import org.junit.Test;
import static org.junit.Assert.*;
public class GazeClassifierTest {
    @Test public void fusionUsesSameDirectionAsIndividualEyeForEveryTemplate() {
        for(int i=0;i<6;i++) {
            double[] left={.04,.04,.04,.04,.04,.04}, right=left.clone(); left[i]=right[i]=.001;
            double[] fused=GazeClassifier.fuse(left,right);
            assertTrue(GazeClassifier.distinguishable(fused,.02));
            assertEquals(GazeClassifier.DIRECTIONS[i], GazeClassifier.DIRECTIONS[GazeClassifier.bestIndex(fused)]);
        }
    }
    @Test public void straightDoesNotBecomeLeft() {
        double[] fused=GazeClassifier.fuse(new double[]{.001,.03,.04},new double[]{.002,.04,.03});
        assertEquals(0,GazeClassifier.DIRECTIONS[GazeClassifier.bestIndex(fused)]);
    }
    @Test public void indistinguishableOrPoorTemplatesDoNotConfirm() {
        assertFalse(GazeClassifier.distinguishable(new double[]{.01,.01001,.02},.02));
        assertFalse(GazeClassifier.distinguishable(new double[]{.03,.05,.06},.02));
    }
    @Test public void newFrameDoesNotKeepOldCommand() {
        DetectionOutput output=new DetectionOutput(); output.gestureOutput=2; output.initialize(4);
        assertEquals(0,output.gestureOutput); assertFalse(output.AnalyzedData.Success);
    }
}