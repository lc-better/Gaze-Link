package com.demo.opencv.vision;

import java.util.List;

/** Three personal centroids with pooled within-target noise. No generic left/right sign assumption. */
public final class IrisCalibration {
    public int version = 1;
    public int eyeMode;
    public double[][] centres;
    public double[] scales;

    public static IrisCalibration train(List<List<double[]>> samples, int mode) {
        IrisCalibration result = new IrisCalibration();
        result.eyeMode = mode; result.centres = new double[3][6]; result.scales = new double[4];
        int count = 0;
        for (int target = 0; target < 3; target++) {
            List<double[]> rows = samples.get(target); count += rows.size();
            for (double[] row : rows) for (int k = 0; k < 6; k++) result.centres[target][k] += row[k] / rows.size();
            for (double[] row : rows) for (int k = 0; k < 4; k++) {
                double d = row[k] - result.centres[target][k]; result.scales[k] += d * d;
            }
        }
        for (int k = 0; k < 4; k++) result.scales[k] = Math.max(.018, Math.sqrt(result.scales[k] / count));
        for (int a = 0; a < 3; a++) for (int b = a + 1; b < 3; b++) {
            double separation = result.distance(result.centres[a], result.centres[b]);
            if (separation < 4) throw new IllegalArgumentException("三个方向还不够分明。请只移动眼睛看目标，重新采集；也可改用更稳定的一只眼睛。");
        }
        return result;
    }
    private double distance(double[] a, double[] b) {
        double sum = 0, weights = 0;
        for (int eye = 0; eye < 2; eye++) {
            if ((eyeMode == IrisFeatures.LEFT && eye == 1) || (eyeMode == IrisFeatures.RIGHT && eye == 0)) continue;
            for (int j = 0; j < 2; j++) {
                int k = eye * 2 + j; double weight = j == 0 ? 1 : .35;
                double delta = (a[k] - b[k]) / scales[k];
                sum += delta * delta * weight; weights += weight;
            }
        }
        return sum / weights;
    }
    public boolean poseMatches(double[] features) {
        return Math.abs(features[4] - centres[0][4]) <= 15 && Math.abs(features[5] - centres[0][5]) <= 15;
    }
    /** -1 means missing confidence; the caller interrupts the dwell immediately. */
    public int classify(double[] features) {
        if (!poseMatches(features)) return -1;
        int best = -1; double first = Double.MAX_VALUE, second = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            double d = distance(features, centres[i]);
            if (d < first) { second = first; first = d; best = i; }
            else if (d < second) second = d;
        }
        return first <= 9 && second - first >= 1 && first < second * .65 ? best : -1;
    }
}
