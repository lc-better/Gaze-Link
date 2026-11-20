package com.demo.opencv.vision;

import com.google.gson.Gson;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class IrisCalibrationTest {
    private List<List<double[]>> samples() {
        List<List<double[]>> samples = new ArrayList<>();
        // Left/right meanings come from calibration, even if one image axis is reversed.
        double[] positions = {.5,.68,.32};
        for (int target=0;target<3;target++) {
            List<double[]> rows = new ArrayList<>();
            for (int i=0;i<12;i++) rows.add(new double[]{positions[target]+(i%3-1)*.008,0,
                    1-positions[target]+(i%3-1)*.006,0,3,-2});
            samples.add(rows);
        }
        return samples;
    }
    @Test public void learnsPersonalDirectionAndAcceptsFreshNoisyObservations() {
        IrisCalibration model=IrisCalibration.train(samples(),IrisFeatures.BOTH);
        assertEquals(0,model.classify(new double[]{.51,.005,.49,0,3,-2}));
        assertEquals(1,model.classify(new double[]{.67,0,.33,.003,3,-2}));
        assertEquals(2,model.classify(new double[]{.33,0,.67,0,3,-2}));
    }
    @Test public void ambiguityAndUnseenPositionsAreRejected() {
        IrisCalibration model=IrisCalibration.train(samples(),IrisFeatures.BOTH);
        assertEquals(-1,model.classify(new double[]{.59,0,.41,0,3,-2}));
        assertEquals(-1,model.classify(new double[]{.95,0,.05,0,3,-2}));
    }
    @Test public void headMovementOutsidePersonalPoseInterruptsClassification() {
        IrisCalibration model=IrisCalibration.train(samples(),IrisFeatures.BOTH);
        assertEquals(-1,model.classify(new double[]{.5,0,.5,0,23,-2}));
        assertEquals(-1,model.classify(new double[]{.5,0,.5,0,3,20}));
    }
    @Test public void unselectedEyeDoesNotAffectSingleEyeClassification() {
        IrisCalibration model=IrisCalibration.train(samples(),IrisFeatures.LEFT);
        assertEquals(1,model.classify(new double[]{.68,0,100,100,3,-2}));
        model=IrisCalibration.train(samples(),IrisFeatures.RIGHT);
        assertEquals(2,model.classify(new double[]{100,100,.68,0,3,-2}));
    }
    @Test(expected=IllegalArgumentException.class) public void indistinguishableTargetsCannotBecomeACalibration() {
        List<List<double[]>> samples=samples(); samples.set(1,samples.get(0));
        IrisCalibration.train(samples,IrisFeatures.BOTH);
    }
    @Test public void persistedCalibrationRetainsEyeModeAndDecision() {
        IrisCalibration model=IrisCalibration.train(samples(),IrisFeatures.RIGHT);
        Gson gson=new Gson(); IrisCalibration loaded=gson.fromJson(gson.toJson(model),IrisCalibration.class);
        assertEquals(IrisFeatures.RIGHT,loaded.eyeMode);
        assertEquals(1,loaded.classify(new double[]{1,1,.32,0,3,-2}));
    }
}
