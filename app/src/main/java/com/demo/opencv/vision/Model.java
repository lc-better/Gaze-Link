package com.demo.opencv.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import com.demo.opencv.ContractInterface;
import com.demo.opencv.UserDataManager;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.io.IOException;
import java.util.List;

public class Model implements ContractInterface.Model {
    private UserDataManager userData;
    private final EyeDetection detector = new EyeDetection();
    private final GoogleFaceDetector faceDetector = new GoogleFaceDetector();
    private final GazeInputController input = new GazeInputController();
    DetectionOutput detectionOutput;
    private double[] leftErrors, rightErrors;
    public volatile boolean previewEnabled;
    private IrisDetector irisDetector;
    private IrisCalibration irisCalibration;
    private boolean irisEngine;
    private int eyeMode;

    @Override public void initialize(Context context, Context application) throws IOException {
        userData = (UserDataManager) application;
        if (!OpenCVLoader.initDebug()) throw new IOException("OpenCV initialization failed");
        irisDetector = new IrisDetector(context);
        updateCalibrationTemplates();
        faceDetector.initialize();
    }
    @Override public synchronized void updateCalibrationTemplates() {
        irisEngine = userData.usesIris(); eyeMode = userData.getEyeMode();
        irisCalibration = userData.getIrisCalibration();
        irisDetector.resetBlinkBaseline();
        detector.setTemplates(userData.getLeftCalibrationData(), userData.getRightCalibrationData(),
                userData.getCalibrationCount());
        input.reset();
    }
    public synchronized void setSessionTemplates(Bitmap[] left, Bitmap[] right, int count) {
        detector.setTemplates(left, right, count);
        input.reset();
    }
    public synchronized void setSessionIris(IrisCalibration calibration) { irisCalibration = calibration; input.reset(); }
    public synchronized void resetDwell() { input.reset(); }
    public synchronized void relearnBlinkBaseline() {
        irisDetector.resetBlinkBaseline(); input.reset();
    }
    public synchronized void setBlinkTracking(boolean enabled) { input.setBlinkTracking(enabled); }
    public synchronized void setMouthTracking(boolean enabled) { input.setMouthTracking(enabled); }
    public synchronized void setDwellMillis(long millis) { input.setDwellMillis(millis); }

    @Override public void analyzeGazeOutput() {
        GazeData left = detectionOutput.LeftData, right = detectionOutput.RightData;
        if (left.Success && right.Success) {
            if (left.GazeType == 5 || right.GazeType == 5) return;
            double[] fused = GazeClassifier.fuse(leftErrors, rightErrors);
            int best = GazeClassifier.bestIndex(fused);
            detectionOutput.setEyeData(2, GazeClassifier.distinguishable(fused, detector.getSensitivity()),
                    GazeClassifier.DIRECTIONS[best], 1, (float) fused[best]);
        } else if (left.Success) detectionOutput.AnalyzedData = left;
        else if (right.Success) detectionOutput.AnalyzedData = right;
    }

    private Mat cropEye(List<PointF> points, Mat frame) {
        float minX = frame.cols(), minY = frame.rows(), maxX = 0, maxY = 0;
        for (PointF p : points) {
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
        }
        int x = Math.max(0, (int) minX - 3), y = Math.max(0, (int) minY - 3);
        int w = Math.min(frame.cols(), (int) maxX + 4) - x;
        int h = Math.min(frame.rows(), (int) maxY + 4) - y;
        if (w < 8 || h < 4) return null;
        Mat roi = frame.submat(new Rect(x, y, w, h));
        Mat eye = new Mat();
        Imgproc.resize(roi, eye, new Size(44, 18));
        Imgproc.cvtColor(eye, eye, Imgproc.COLOR_RGB2GRAY);
        roi.release();
        return eye;
    }
    private Bitmap bitmap(Mat m) {
        Bitmap bitmap = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bitmap);
        return bitmap;
    }
    @Override public synchronized DetectionOutput classifyGaze(Mat frame) {
        detectionOutput = new DetectionOutput();
        detectionOutput.initialize(4);
        detector.setSensitivity(userData.getSensitivity());
        Bitmap image = bitmap(frame);
        if (irisEngine) {
            irisDetector.detect(image, detectionOutput, eyeMode, previewEnabled);
            if (detectionOutput.eyesVisible && irisCalibration != null) {
                int direction = irisCalibration.classify(detectionOutput.features);
                detectionOutput.setEyeData(2, direction >= 0, direction >= 0 ? direction : 0, 1, 0);
                if (!irisCalibration.poseMatches(detectionOutput.features))
                    detectionOutput.qualityMessage = "头部位置与校准时不同 · 请回到原姿势，或重新校准";
            }
            return finishFrame();
        }
        faceDetector.detect(image);
        if (previewEnabled) detectionOutput.preview = image;
        else image.recycle();
        detectionOutput.headYaw = faceDetector.rotY;
        detectionOutput.leftEyeOpenness = faceDetector.leftEyeOpenProb;
        detectionOutput.rightEyeOpenness = faceDetector.rightEyeOpenProb;
        detectionOutput.blinkSignalsValid = faceDetector.faceBound != null && Math.abs(faceDetector.rotY) < 20
                && faceDetector.leftEyeOpenProb >= 0 && faceDetector.rightEyeOpenProb >= 0;
        detectionOutput.mouthSignalsValid = faceDetector.faceBound != null && faceDetector.mouthOpenRatio >= 0;
        detectionOutput.mouthOpenRatio = faceDetector.mouthOpenRatio;
        Mat left = null, right = null;
        // Unknown open probability is -1, never interpreted as a closed eye.
        boolean closed = faceDetector.leftEyeOpenProb >= 0 && faceDetector.leftEyeOpenProb < .15
                && faceDetector.rightEyeOpenProb >= 0 && faceDetector.rightEyeOpenProb < .15;
        if (!closed) {
            if (faceDetector.leftEyeContour != null) left = cropEye(faceDetector.leftEyeContour.getPoints(), frame);
            if (faceDetector.rightEyeContour != null) right = cropEye(faceDetector.rightEyeContour.getPoints(), frame);
        }
        detectionOutput.eyesVisible = left != null && right != null;
        if (left != null) {
            detectionOutput.leftEye = bitmap(left);
            if (detector.hasTemplates()) leftErrors = detector.runEyeModel(detectionOutput, left, 0);
            left.release();
        }
        if (right != null) {
            detectionOutput.rightEye = bitmap(right);
            if (detector.hasTemplates()) rightErrors = detector.runEyeModel(detectionOutput, right, 1);
            right.release();
        }
        if (closed) {
            detectionOutput.setEyeData(0, false, 5, 1, 0);
            detectionOutput.setEyeData(1, false, 5, 1, 0);
        }
        analyzeGazeOutput();
        return finishFrame();
    }
    private DetectionOutput finishFrame() {
        input.update(detectionOutput, android.os.SystemClock.elapsedRealtime());
        return detectionOutput;
    }
    @Override public synchronized void close() { faceDetector.close(); detector.close(); if (irisDetector != null) irisDetector.close(); }
}
