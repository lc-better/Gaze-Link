package com.demo.opencv.vision;

/** Shared template order for calibration, individual eyes and binocular fusion. */
public final class GazeClassifier {
    public static final int[] DIRECTIONS = {0, 1, 2, 3, 6, 7};

    public static int bestIndex(double[] errors) {
        int best = 0;
        for (int i = 1; i < errors.length; i++) if (errors[i] < errors[best]) best = i;
        return best;
    }

    public static boolean distinguishable(double[] errors, double threshold) {
        int best = bestIndex(errors);
        double second = Double.MAX_VALUE;
        for (int i = 0; i < errors.length; i++) if (i != best) second = Math.min(second, errors[i]);
        return errors[best] <= threshold && second - errors[best] >= Math.max(0.00015, errors[best] * 0.08);
    }

    public static double[] fuse(double[] left, double[] right) {
        double[] result = new double[left.length];
        for (int i = 0; i < result.length; i++) result[i] = (left[i] + right[i]) / 2;
        return result;
    }
}
