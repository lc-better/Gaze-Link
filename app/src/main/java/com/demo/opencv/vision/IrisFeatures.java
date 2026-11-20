package com.demo.opencv.vision;

/** Pixel geometry in the local eye axis, independent of image translation, scale and roll. */
public final class IrisFeatures {
    public static final int BOTH = 0, LEFT = 1, RIGHT = 2;
    public static final class Eye {
        public final double horizontal, vertical, openness;
        public final boolean usable;
        Eye(double horizontal, double vertical, double openness, boolean usable) {
            this.horizontal = horizontal; this.vertical = vertical;
            this.openness = openness; this.usable = usable;
        }
    }
    public static Eye measure(double[] a, double[] b, double[] upper, double[] lower, double[] iris) {
        double dx = b[0] - a[0], dy = b[1] - a[1], width2 = dx * dx + dy * dy;
        if (width2 < 100) return new Eye(0, 0, 0, false);
        double horizontal = ((iris[0] - a[0]) * dx + (iris[1] - a[1]) * dy) / width2;
        double vertical = ((iris[1] - (a[1] + b[1]) / 2) * dx
                - (iris[0] - (a[0] + b[0]) / 2) * dy) / width2;
        double openness = Math.abs((lower[1] - upper[1]) * dx - (lower[0] - upper[0]) * dy) / width2;
        return new Eye(horizontal, vertical, openness,
                openness >= .10 && horizontal > -.1 && horizontal < 1.1);
    }
    public static boolean usable(Eye left, Eye right, int mode) {
        return mode == LEFT ? left.usable : mode == RIGHT ? right.usable : left.usable && right.usable;
    }
    private IrisFeatures() {}
}
