package com.demo.opencv.vision;

/** One command per dwell. A short, continuous return to centre arms the next command. */
public final class DwellController {
    private long dwellMillis = 800;
    private long started = -1, centreStarted = -1, previousFrame = -1;
    private int candidate;
    private boolean armed;
    private float progress;

    public void setDwellMillis(long value) { dwellMillis = value; reset(); }
    public void reset() {
        started = centreStarted = previousFrame = -1;
        candidate = 0;
        progress = 0;
        armed = false;
    }

    public int update(boolean valid, int direction, long now) {
        return update(valid, direction, now, 0.5f);
    }
    public int update(boolean valid, int direction, long now, float confidence) {
        // A long gap means we did not observe a continuous dwell (e.g. app resumed).
        if (previousFrame >= 0 && now - previousFrame > 500) reset();
        previousFrame = now;
        progress = 0;
        if (!valid || direction == 5) {
            started = centreStarted = -1;
            candidate = 0;
            return 0;
        }
        if (direction == 0) {
            started = -1;
            candidate = 0;
            if (centreStarted < 0) centreStarted = now;
            if (now - centreStarted >= 250) armed = true;
            return 0;
        }
        centreStarted = -1;
        if (!armed) return 0;
        if (candidate != direction) {
            candidate = direction;
            started = now;
        }
        long effectiveDwell = dwellMillis;
        if (confidence >= .75f) effectiveDwell = Math.max(500, Math.round(dwellMillis * .85f));
        else if (confidence <= .35f) effectiveDwell = Math.min(1500, Math.round(dwellMillis * 1.15f));
        progress = Math.min(1f, (now - started) / (float) effectiveDwell);
        if (progress < 1f) return 0;
        armed = false;
        started = -1;
        return direction;
    }
    public float getProgress() { return progress; }
    public boolean isArmed() { return armed; }
}
