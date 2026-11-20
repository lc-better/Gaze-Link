package com.demo.opencv.communication;

/** Tracks observed continuous time, rather than elapsed time across missing frames. */
public final class HoldTracker {
    private long start = -1, previous = -1;
    public long update(boolean valid, long now) {
        if (!valid || (previous >= 0 && now - previous > 500)) start = -1;
        previous = now;
        if (!valid) return 0;
        if (start < 0) start = now;
        return now - start;
    }
    public void reset() { start = previous = -1; }
}
