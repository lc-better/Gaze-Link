package com.demo.opencv.communication;

import android.graphics.Bitmap;
import com.demo.opencv.UserDataManager;
import com.demo.opencv.vision.DetectionOutput;
import com.demo.opencv.vision.Model;
import com.demo.opencv.vision.IrisCalibration;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Capture stays in memory until fresh, separately observed validation trials pass. */
public final class CalibrationSession {
    public enum Phase { READY, CAPTURE, VERIFY, FAILED, DONE }
    public Phase phase = Phase.READY;
    public int target, passed, trials;
    public float progress;
    public String message = "固定手机，脸朝向屏幕。先试听，再开始。";
    private final Bitmap[] left = new Bitmap[3], right = new Bitmap[3];
    private final List<Bitmap> leftSamples = new ArrayList<>(), rightSamples = new ArrayList<>();
    private final List<Integer> validation = new ArrayList<>();
    private final HoldTracker hold = new HoldTracker();
    private long readyAt, deadline;
    private int trialIndex;
    private final Model model;
    private final UserDataManager data;
    private final boolean iris;
    private final int eyeMode;
    private final List<List<double[]>> featureSamples = new ArrayList<>();
    private IrisCalibration candidate;
    private final String[] names = {"中间", "左侧", "右侧"};

    public CalibrationSession(Model model, UserDataManager data) {
        this.model = model; this.data = data; iris = data.usesIris(); eyeMode = data.getEyeMode();
        for (int i = 0; i < 3; i++) featureSamples.add(new ArrayList<>());
    }
    public void start(long now) {
        java.util.Arrays.fill(left, null); java.util.Arrays.fill(right, null);
        for (List<double[]> rows : featureSamples) rows.clear();
        candidate = null; passed = trials = 0; capture(0, now);
    }
    public void suspend() {
        if (phase == Phase.CAPTURE || phase == Phase.VERIFY) {
            phase = Phase.READY; progress = 0; hold.reset(); model.updateCalibrationTemplates();
            message = "采集已暂停。回到舒适姿势后，点击开始重新采集；原校准保留。";
        }
    }
    public void retry(long now) { if (iris) start(now); else capture(target, now); }
    private void capture(int direction, long now) {
        target = direction; phase = Phase.CAPTURE;
        readyAt = now + 2200; deadline = readyAt + 15000;
        leftSamples.clear(); rightSamples.clear(); featureSamples.get(target).clear(); hold.reset(); progress = 0;
        message = "看" + names[target] + "圆点，头保持不动；倒数结束后自动采集。";
    }
    public void tick(DetectionOutput frame, long now) {
        if (phase != Phase.CAPTURE && phase != Phase.VERIFY) return;
        if (now < readyAt) { progress = 0; return; }
        if (phase == Phase.CAPTURE) {
            boolean valid = frame.eyesVisible && Math.abs(frame.headYaw) < 20;
            long elapsed = hold.update(valid, now);
            if (!valid || elapsed == 0) { leftSamples.clear(); rightSamples.clear(); featureSamples.get(target).clear(); }
            if (valid) {
                if (iris) featureSamples.get(target).add(frame.features);
                else { leftSamples.add(frame.leftEye); rightSamples.add(frame.rightEye); }
            }
            progress = Math.min(1, elapsed / 1200f);
            message = valid ? "保持看" + names[target] + "圆点，正在采集…"
                    : Math.abs(frame.headYaw) >= 20 ? "头部转动较大 · 请让脸朝向屏幕，只移动眼睛"
                    : (!frame.qualityMessage.isEmpty() ? frame.qualityMessage : "请让所选眼睛出现在画面中，脸朝向屏幕。");
            int count = iris ? featureSamples.get(target).size() : leftSamples.size();
            if (elapsed >= 1200 && count >= 6) {
                if (!iris) { left[target] = average(leftSamples); right[target] = average(rightSamples); }
                boolean complete = iris ? featureSamples.stream().allMatch(rows -> !rows.isEmpty())
                        : left[0] != null && left[1] != null && left[2] != null;
                if (complete) {
                    if (iris) {
                        try { candidate = IrisCalibration.train(featureSamples, eyeMode); }
                        catch (IllegalArgumentException error) { fail(error.getMessage()); return; }
                        model.setSessionIris(candidate);
                    } else model.setSessionTemplates(left, right, 3);
                    validation.clear();
                    List<Integer> sides = new ArrayList<>(java.util.Arrays.asList(1, 1, 2, 2));
                    Collections.shuffle(sides);
                    validation.add(0);
                    for (int side : sides) { validation.add(side); validation.add(0); }
                    passed = trials = trialIndex = 0;
                    verify(now);
                } else capture(target + 1, now);
            } else if (now > deadline) fail(iris ? "没有取得连续清晰画面。调整位置后重新采集三方向。" : "没有取得连续清晰画面。调整位置后重采当前方向。");
        } else {
            boolean match = frame.AnalyzedData.Success && frame.AnalyzedData.GazeType == target;
            long elapsed = hold.update(match, now);
            progress = Math.min(1, elapsed / 650f);
            String actual = frame.AnalyzedData.Success && frame.AnalyzedData.GazeType < 3
                    ? names[frame.AnalyzedData.GazeType] : "未分清";
            message = "独立验证 " + (trialIndex + 1) + "/" + validation.size() + "：看" + names[target]
                    + "圆点，保持到进度填满。当前识别：" + actual;
            if (elapsed >= 650) {
                passed++; trials++; trialIndex++;
                if (trialIndex == validation.size()) {
                    try {
                        if (iris) data.saveIrisCalibration(candidate);
                        else data.saveVerifiedCalibration(left, right, 3);
                        model.updateCalibrationTemplates();
                        phase = Phase.DONE; progress = 1;
                        message = "验证通过，已保存。回到中间后，可以开始表达。";
                        data.experience().edit().putString("last_validation_" + data.activeCalibrationKey(), passed + "/" + trials + " 项连续目标验证通过").apply();
                    } catch (IOException error) { fail("保存未完成，请重试。原校准仍然保留。"); }
                } else verify(now);
            } else if (now > deadline) {
                trials++;
                fail("“" + names[target] + "”尚未通过。请保持头部不动，" + (iris ? "重新采集三方向" : "重新采集这个方向") + "；也可先触摸使用。");
            }
        }
    }
    private void verify(long now) {
        phase = Phase.VERIFY; target = validation.get(trialIndex);
        readyAt = now + 1700; deadline = readyAt + 8000;
        hold.reset(); progress = 0;
        message = "准备验证：看" + names[target] + "圆点。此阶段不采集模板。";
    }
    private void fail(String reason) { phase = Phase.FAILED; message = reason; progress = 0; model.updateCalibrationTemplates(); }
    public long countdown(long now) { return Math.max(0, (readyAt - now + 999) / 1000); }
    public String targetName() { return names[target]; }
    public void cancel() { model.updateCalibrationTemplates(); }
    private Bitmap average(List<Bitmap> samples) {
        int width = samples.get(0).getWidth(), height = samples.get(0).getHeight();
        int[] sum = new int[width * height], pixels = new int[sum.length];
        for (Bitmap bitmap : samples) {
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int i = 0; i < sum.length; i++) sum[i] += pixels[i] & 255;
        }
        for (int i = 0; i < pixels.length; i++) {
            int grey = sum[i] / samples.size();
            pixels[i] = 0xff000000 | (grey << 16) | (grey << 8) | grey;
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }
}
