package com.demo.opencv.vision;

/** Calibration belongs to an installation, display direction and screen geometry. */
public final class CalibrationEnvironment {
    private CalibrationEnvironment() {}

    public static String key(String installation, int rotation, int width, int height) {
        if (installation == null || installation.isEmpty() || rotation < 0 || rotation > 3
                || width <= 0 || height <= 0) return "";
        return installation + ":" + rotation + ":" + width + "x" + height;
    }

    public static boolean matches(String saved, String current) {
        return saved != null && current != null && !current.isEmpty() && saved.equals(current);
    }
}
