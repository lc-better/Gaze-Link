package com.demo.opencv.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

/** Bundled CPU model; creation and inference both run on the camera executor. */
final class IrisDetector implements AutoCloseable {
    private final Context context;
    private FaceLandmarker landmarker;
    private String initializationError;
    private long timestamp;
    private final EyeOpenness openness = new EyeOpenness();
    IrisDetector(Context context) { this.context = context.getApplicationContext(); }
    void resetBlinkBaseline() { openness.reset(); }

    void detect(Bitmap bitmap, DetectionOutput output, int eyeMode, boolean preview) {
        // MPImage.close recycles its bitmap. The UI preview therefore needs its own copy.
        if (preview) output.preview = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        try (MPImage image = new BitmapImageBuilder(bitmap).build()) {
            detect(image, bitmap, output, eyeMode, preview);
        }
    }
    private void detect(MPImage image, Bitmap bitmap, DetectionOutput output, int eyeMode, boolean preview) {
        if (initializationError != null) { output.qualityMessage = initializationError; return; }
        if (landmarker == null) {
            try {
                BaseOptions base = BaseOptions.builder().setModelAssetPath("face_landmarker.task")
                        .setDelegate(Delegate.CPU).build();
                landmarker = FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(base).setRunningMode(RunningMode.VIDEO).setNumFaces(1)
                        .setOutputFacialTransformationMatrixes(true).build());
            } catch (RuntimeException | UnsatisfiedLinkError error) {
                android.util.Log.e("GazeIris", "Cannot initialize bundled face landmarker", error);
                initializationError = "虹膜方案未能启动 · 请在照护设置切回原版方案";
                output.qualityMessage = initializationError; return;
            }
        }
        timestamp = Math.max(timestamp + 1, android.os.SystemClock.elapsedRealtime());
        FaceLandmarkerResult result = landmarker.detectForVideo(image, timestamp);
        if (result.faceLandmarks().isEmpty()) {
            openness.update(false, -1, -1, timestamp);
            output.qualityMessage = "没有看到脸 · 请让脸朝向前置相机"; return;
        }
        List<NormalizedLandmark> points = result.faceLandmarks().get(0);
        // Anatomical left: 362/263, iris 473. Anatomical right: 33/133, iris 468.
        double[][] p = new double[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            p[i][0] = points.get(i).x() * bitmap.getWidth(); p[i][1] = points.get(i).y() * bitmap.getHeight();
        }
        IrisFeatures.Eye left = IrisFeatures.measure(p[362], p[263], p[386], p[374], p[473]);
        IrisFeatures.Eye right = IrisFeatures.measure(p[33], p[133], p[159], p[145], p[468]);
        float[] matrix = result.facialTransformationMatrixes().get().get(0);
        double yaw = Math.toDegrees(Math.atan2(matrix[8], matrix[10]));
        double pitch = Math.toDegrees(Math.atan2(-matrix[9], Math.hypot(matrix[8], matrix[10])));
        output.headYaw = (float) yaw;
        // Keep gaze features unchanged; blink input uses three eyelid pairs per eye.
        output.leftEyeAspectRatio = EyeOpenness.ratio(p[362], p[263],
                p[385], p[380], p[386], p[374], p[387], p[373]);
        output.rightEyeAspectRatio = EyeOpenness.ratio(p[33], p[133],
                p[160], p[144], p[159], p[145], p[158], p[153]);
        boolean blinkGeometryValid = output.leftEyeAspectRatio >= 0
                && output.rightEyeAspectRatio >= 0 && Math.abs(yaw) < 20;
        openness.update(blinkGeometryValid, output.leftEyeAspectRatio, output.rightEyeAspectRatio, timestamp);
        output.blinkBaselineReady = openness.ready();
        output.blinkBaselineProgress = openness.progress();
        output.leftEyeOpenness = openness.left(); output.rightEyeOpenness = openness.right();
        output.blinkSignalsValid = blinkGeometryValid && openness.ready();
        output.features = new double[]{left.horizontal, left.vertical, right.horizontal, right.vertical, yaw, pitch};
        double mouthWidth = Math.hypot(p[291][0] - p[61][0], p[291][1] - p[61][1]);
        output.mouthOpenRatio = mouthWidth > 0
                ? (float) (Math.hypot(p[14][0] - p[13][0], p[14][1] - p[13][1]) / mouthWidth) : -1;
        output.mouthSignalsValid = output.mouthOpenRatio >= 0 && Math.abs(yaw) < 20;
        output.eyesVisible = IrisFeatures.usable(left, right, eyeMode);
        if (!output.eyesVisible) output.qualityMessage = "所选眼睛未清晰睁开或画面太小 · 正常眨眼会中断停留";
        if (preview) {
            Canvas canvas = new Canvas(output.preview); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            for (int index : new int[]{468, 473}) canvas.drawCircle((float) p[index][0], (float) p[index][1], 5, paint);
            output.leftEye = crop(bitmap, p[362], p[263], p[386], p[374]);
            output.rightEye = crop(bitmap, p[33], p[133], p[159], p[145]);
        }
    }
    private Bitmap crop(Bitmap bitmap, double[]... points) {
        double minX = bitmap.getWidth(), maxX = 0, minY = bitmap.getHeight(), maxY = 0;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        int x = Math.max(0, (int) minX - 4), y = Math.max(0, (int) minY - 4);
        int w = Math.min(bitmap.getWidth(), (int) maxX + 5) - x;
        int h = Math.min(bitmap.getHeight(), (int) maxY + 5) - y;
        return w > 0 && h > 0 ? Bitmap.createBitmap(bitmap, x, y, w, h) : null;
    }
    @Override public void close() { if (landmarker != null) { landmarker.close(); landmarker = null; } }
}
