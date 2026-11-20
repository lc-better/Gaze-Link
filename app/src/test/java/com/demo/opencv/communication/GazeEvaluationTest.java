package com.demo.opencv.communication;

import com.demo.opencv.vision.DwellController;
import org.junit.Test;
import static org.junit.Assert.*;

public class GazeEvaluationTest {
    @Test public void followingAllTargetsWithRealDwellProducesTwentyFiveSuccessfulTasks() {
        GazeEvaluation e=new GazeEvaluation(); DwellController dwell=new DwellController(); e.start(0);
        for (long now=0;e.phase==GazeEvaluation.Phase.RUNNING;now+=100) {
            int direction=e.target;
            e.tick(true,direction,dwell.update(true,direction,now),now);
            assertTrue(now<100000);
        }
        assertEquals(25,e.trials); assertEquals(25,e.successful);
        assertEquals(12,e.correctCommands); assertEquals(0,e.wrongCommands);
    }
    @Test public void wrongCommandsAndUnclassifiedFramesStayInReport() {
        GazeEvaluation e=new GazeEvaluation(); e.start(0);
        e.tick(false,0,1,800); e.tick(false,0,0,1000); e.tick(false,0,0,2500);
        assertEquals(1,e.trials); assertEquals(0,e.successful); assertEquals(1,e.wrongCommands);
        assertEquals(2,e.confusion[0][3]); assertTrue(e.report().contains("未分清：100%"));
    }
    @Test public void suspendAndRestartDoNotReuseIncompleteMeasurements() {
        GazeEvaluation e=new GazeEvaluation(); e.start(0); e.tick(true,1,1,1000); e.suspend();
        e.tick(true,1,1,2000); assertEquals(1,e.wrongCommands);
        e.start(3000); assertEquals(0,e.wrongCommands); assertEquals(0,e.confusion[0][1]);
    }
}
