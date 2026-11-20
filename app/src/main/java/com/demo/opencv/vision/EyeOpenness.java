package com.demo.opencv.vision;

/** Multi-pair eyelid geometry, normalized against each eye's own open reference. */
public final class EyeOpenness {
    private static final long LEARN_MS = 1000;
    private long learningSince = -1, previousFrame = -1;
    private int samples;
    private double leftSum, rightSum, leftReference, rightReference;
    private boolean ready;
    private float progress, left = -1, right = -1;

    public static double ratio(double[] a, double[] b, double[]... lids) {
        double dx = b[0] - a[0], dy = b[1] - a[1], width2 = dx * dx + dy * dy;
        if (width2 < 100) return -1;
        double height = 0;
        for (int i = 0; i < lids.length; i += 2)
            height += Math.abs((lids[i + 1][1] - lids[i][1]) * dx
                    - (lids[i + 1][0] - lids[i][0]) * dy) / width2;
        return height / (lids.length / 2);
    }
    public void reset() {
        ready = false; leftReference = rightReference = 0; previousFrame = -1;
        restartLearning(); left = right = -1;
    }
    private void restartLearning() {
        learningSince = -1; samples = 0; leftSum = rightSum = 0; progress = 0;
    }
    public void update(boolean valid, double rawLeft, double rawRight, long now) {
        if (!ready && previousFrame >= 0 && now - previousFrame > 400) restartLearning();
        previousFrame = now;
        left = right = -1;
        if (!valid) { if (!ready) restartLearning(); return; }
        if (!ready) {
            // Do not learn a clearly shut eye as the open reference.
            if (rawLeft < .08 || rawRight < .08) { restartLearning(); return; }
            if (learningSince < 0) learningSince = now;
            leftSum += rawLeft; rightSum += rawRight; samples++;
            progress = Math.min(1, (now - learningSince) / (float) LEARN_MS);
            if (progress < 1 || samples < 6) return;
            leftReference = leftSum / samples; rightReference = rightSum / samples; ready = true;
        }
        double l = rawLeft / leftReference, r = rawRight / rightReference;
        // Follow small changes in natural opening, but never adapt to a wink.
        if (l >= .85 && r >= .85) {
            leftReference += .02 * (rawLeft - leftReference);
            rightReference += .02 * (rawRight - rightReference);
        }
        left = (float) Math.min(1, l); right = (float) Math.min(1, r);
    }
    public boolean ready() { return ready; }
    public float progress() { return progress; }
    public float left() { return left; }
    public float right() { return right; }
}
