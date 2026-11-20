package com.demo.opencv.vision;

import android.content.Context;
import android.graphics.Bitmap;
import com.demo.opencv.UserDataManager;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** Personal eye templates, compared after mild local contrast normalisation. */
public class EyeDetection {
    private Mat[] leftTemplates, rightTemplates;
    private float sensitivity = .02f;
    private static final Size SIZE = new Size(40, 14);

    private Mat prepare(Mat input) {
        Mat result = new Mat();
        Imgproc.resize(input, result, SIZE, 0, 0, Imgproc.INTER_AREA);
        if (result.channels() == 4) Imgproc.cvtColor(result, result, Imgproc.COLOR_RGBA2GRAY);
        else if (result.channels() == 3) Imgproc.cvtColor(result, result, Imgproc.COLOR_RGB2GRAY);
        Imgproc.GaussianBlur(result, result, new Size(3, 3), 0);
        // Keep low-contrast noise from being stretched across the entire grey range.
        org.opencv.core.MatOfDouble mean = new org.opencv.core.MatOfDouble();
        org.opencv.core.MatOfDouble std = new org.opencv.core.MatOfDouble();
        Core.meanStdDev(result, mean, std);
        result.convertTo(result, CvType.CV_32F, 1.0 / Math.max(40, std.toArray()[0] * 4),
                -mean.toArray()[0] / Math.max(40, std.toArray()[0] * 4));
        mean.release(); std.release();
        return result;
    }

    public void setTemplates(Bitmap[] left, Bitmap[] right, int count) {
        close();
        leftTemplates = load(left, count);
        rightTemplates = load(right, count);
    }
    private Mat[] load(Bitmap[] bitmaps, int count) {
        Mat[] templates = new Mat[count];
        for (int i = 0; i < count; i++) {
            if (bitmaps[i] == null) continue;
            Mat raw = new Mat();
            Utils.bitmapToMat(bitmaps[i], raw);
            templates[i] = prepare(raw);
            raw.release();
        }
        return templates;
    }
    public void updateCalibrationTemplates(Context context, boolean unused) {
        UserDataManager data = (UserDataManager) context;
        setTemplates(data.getLeftCalibrationData(), data.getRightCalibrationData(), data.getCalibrationCount());
    }
    public void setSensitivity(int value) { sensitivity = value / 1000f; }
    public float getSensitivity() { return sensitivity; }
    public boolean hasTemplates() {
        return leftTemplates != null && leftTemplates.length >= 3 && leftTemplates[0] != null
                && rightTemplates[0] != null;
    }
    public double[] runEyeModel(DetectionOutput output, Mat eye, int side) {
        Mat[] templates = side == 0 ? leftTemplates : rightTemplates;
        Mat prepared = prepare(eye);
        double[] errors = new double[templates.length];
        for (int i = 0; i < templates.length; i++) {
            errors[i] = templates[i] == null ? Double.MAX_VALUE
                    : Core.norm(prepared, templates[i], Core.NORM_L2SQR) / prepared.total();
        }
        prepared.release();
        int best = GazeClassifier.bestIndex(errors);
        output.setEyeData(side, GazeClassifier.distinguishable(errors, sensitivity),
                GazeClassifier.DIRECTIONS[best], 1, (float) errors[best]);
        return errors;
    }
    public void close() {
        if (leftTemplates != null) for (Mat m : leftTemplates) if (m != null) m.release();
        if (rightTemplates != null) for (Mat m : rightTemplates) if (m != null) m.release();
        leftTemplates = rightTemplates = null;
    }
}
