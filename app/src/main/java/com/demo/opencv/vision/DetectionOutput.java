package com.demo.opencv.vision;

import com.demo.opencv.vision.GazeData;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.ArrayList;

public class DetectionOutput {
    public GazeData LeftData; public GazeData RightData; public GazeData AnalyzedData;
    public void setEyeData(int type, Boolean Success, int GazeType, int GazeLength, float GazeProbability) {
        // 0: left, 1: right, 2: final
        if (type == 0) {
            // set left eye data
            this.LeftData.setGazeData(Success, GazeType, GazeLength, GazeProbability);
        } else if (type == 1) {
            // set right eye data
            this.RightData.setGazeData(Success, GazeType, GazeLength, GazeProbability);
        } else {
            // set analyzed gaze data
            this.AnalyzedData.setGazeData(Success, GazeType, GazeLength, GazeProbability);
        }
    }

    public void initialize(int MatNum) {
        gestureOutput = 0;
        this.RightData = new GazeData();
        this.LeftData = new GazeData();
        this.AnalyzedData = new GazeData();
        testingMats = new Mat[MatNum];
    }

    public int gestureOutput; // an input from the user (not the raw data from the frame)
    public android.graphics.Bitmap leftEye, rightEye, preview;
    public boolean eyesVisible;
    public int smoothedGazeType = -1;
    public boolean smoothedGazeSuccess;
    public float gazeConfidence;
    public float headYaw;
    public float dwellProgress;
    public boolean armed;
    public float leftEyeOpenness = -1, rightEyeOpenness = -1;
    public double leftEyeAspectRatio = -1, rightEyeAspectRatio = -1;
    public boolean blinkBaselineReady;
    public float blinkBaselineProgress;
    public boolean blinkSignalsValid, blinkCommandArmed;
    public BlinkDetector.Event blinkEvent = BlinkDetector.Event.NONE;
    public float blinkProgress;
    public float mouthOpenRatio = -1;
    public boolean mouthSignalsValid, mouthCommandArmed;
    public MouthDetector.Event mouthEvent = MouthDetector.Event.NONE;
    public float mouthProgress;
    public double[] features;
    public String qualityMessage = "";

    public Point leftNIC; // Normalized Iris Center for both eyes
    public Mat[] testingMats; // testing mats to show process of analysis
    public ArrayList<String> prevInputs; // array to store previous inputs, null until presenter
}
