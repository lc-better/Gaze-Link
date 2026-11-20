package com.demo.opencv.vision;

/** Suppresses short direction flips before they reach dwell selection. */
public final class GazeSmoother {
    private static final long STABLE_MS = 120;
    private int stable = -1, candidate = -1;
    private long candidateSince = -1;

    public void reset() { stable = -1; candidate = -1; candidateSince = -1; }
    public int update(boolean valid, int direction, long now) {
        if (!valid || direction < 0 || direction > 2) { reset(); return -1; }
        // The first usable frame establishes the current direction immediately.
        // Only later direction changes need a short stability window.
        if (stable < 0 && candidate < 0) {
            stable = direction;
            return stable;
        }
        if (direction == stable) { candidate = -1; candidateSince = -1; return stable; }
        // Centre is the explicit re-arm position. Apply it immediately so a
        // user can finish one choice and prepare the next without waiting.
        // The first move away from centre is also clear enough to show at once;
        // smoothing is reserved for quick left/right flips.
        if (stable == 0 || direction == 0) {
            stable = direction;
            candidate = -1;
            candidateSince = -1;
            return stable;
        }
        if (candidate != direction) { candidate = direction; candidateSince = now; }
        if (now - candidateSince >= STABLE_MS) { stable = candidate; candidate = -1; candidateSince = -1; }
        return stable;
    }
}
