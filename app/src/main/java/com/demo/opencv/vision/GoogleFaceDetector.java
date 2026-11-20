package com.demo.opencv.vision;


import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class GoogleFaceDetector {

    FaceDetectorOptions realTimeOpts;
    FaceDetector detector;

    // required public data
    public FaceContour leftEyeContour, rightEyeContour;
    public PointF leftEyePos, rightEyePos;
    public float rightEyeOpenProb, leftEyeOpenProb, rotY, rotZ, mouthOpenRatio = -1f;
    public Rect faceBound;

    public void initialize() {

        realTimeOpts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        detector = FaceDetection.getClient(realTimeOpts);
    }

    public void analyze(List<Face> faces) {
        leftEyeContour = null;
        rightEyeContour = null;
        leftEyeOpenProb = -1f;
        rightEyeOpenProb = -1f;
        mouthOpenRatio = -1f;
        if (faces.size() == 0) { // prevent the previous input from continuing to work
            clearDetection();
            return;
        }
        Face largestFace = null;
        int maxSize = -1;
        for (Face face: faces) { // determine the largest face
            Rect boundingBox = face.getBoundingBox();
            if (boundingBox.height() * boundingBox.width() > maxSize) {
                largestFace = face;
                maxSize = boundingBox.height() * boundingBox.width();
            }
        }

        if (largestFace != null) {
            rotY = largestFace.getHeadEulerAngleY();
            rotZ = largestFace.getHeadEulerAngleZ();
            faceBound = largestFace.getBoundingBox();
            leftEyeContour = largestFace.getContour(FaceContour.LEFT_EYE);
            rightEyeContour = largestFace.getContour(FaceContour.RIGHT_EYE);

            // If classification was enabled:
            if (largestFace.getLeftEyeOpenProbability() != null) {
                leftEyeOpenProb = largestFace.getLeftEyeOpenProbability();
            }
            if (largestFace.getRightEyeOpenProbability() != null) {
                rightEyeOpenProb = largestFace.getRightEyeOpenProbability();
            }
            mouthOpenRatio = mouthRatio(largestFace.getContour(FaceContour.UPPER_LIP_BOTTOM),
                    largestFace.getContour(FaceContour.LOWER_LIP_TOP));
        }
    }

    private void clearDetection() {
        leftEyeContour = null;
        rightEyeContour = null;
        leftEyeOpenProb = -1f;
        rightEyeOpenProb = -1f;
        mouthOpenRatio = -1f;
        rotY = rotZ = 0;
        faceBound = null;
    }

    private float mouthRatio(FaceContour upper, FaceContour lower) {
        if (upper == null || lower == null || upper.getPoints().isEmpty() || lower.getPoints().isEmpty()) return -1f;
        float upperY = 0, lowerY = 0, minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        for (PointF p : upper.getPoints()) { upperY += p.y; minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); }
        for (PointF p : lower.getPoints()) { lowerY += p.y; minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); }
        upperY /= upper.getPoints().size(); lowerY /= lower.getPoints().size();
        float width = maxX - minX;
        return width < 1 ? -1f : Math.abs(lowerY - upperY) / width;
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    public boolean detect(Bitmap bm) {

        InputImage newImage = InputImage.fromBitmap(bm, 0);
        try {
            List<Face> faces = Tasks.await(detector.process(newImage));
            analyze(faces);
            return true;
        } catch (ExecutionException e) {
            clearDetection();
            Log.d("googleFaceDetector", "detection failed " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clearDetection();
            Log.d("googleFaceDetector", "detection interrupted");
            return false;
        }
    }

    public void close() {
        if (detector != null) {
            detector.close();
        }
    }
}
