package com.demo.opencv.vision;

/** Recognises a deliberate closed-mouth -> held-open -> closed-mouth action. */
public final class MouthDetector {
    public enum Event { NONE, OPEN_CONFIRM }
    private static final float CLOSED = .10f, OPEN = .20f;
    private static final long ARM_MS = 200, OPEN_MS = 300, RELEASE_MS = 120;
    private static final long MAX_GAP_MS = 400, COOLDOWN_MS = 800;
    private long previous = -1, closedSince = -1, openedSince = -1, releaseSince = -1;
    private long cooldownUntil;
    private boolean armed, qualified;
    private float progress;

    public void reset() {
        previous = closedSince = openedSince = releaseSince = -1;
        cooldownUntil = 0; armed = qualified = false; progress = 0;
    }
    public MouthDetector.Event update(boolean valid, float ratio, long now) {
        if (previous >= 0 && now - previous > MAX_GAP_MS) reset();
        previous = now; progress = 0;
        if (!valid || ratio < 0) { reset(); return Event.NONE; }
        if (now < cooldownUntil) return Event.NONE;
        if (!armed) {
            openedSince = releaseSince = -1; qualified = false;
            if (ratio <= CLOSED) {
                if (closedSince < 0) closedSince = now;
                if (now - closedSince >= ARM_MS) armed = true;
            } else closedSince = -1;
            return Event.NONE;
        }
        if (!qualified) {
            if (ratio >= OPEN) {
                if (openedSince < 0) openedSince = now;
                progress = Math.min(1f, (now - openedSince) / (float) OPEN_MS);
                if (now - openedSince >= OPEN_MS) qualified = true;
            } else if (ratio <= CLOSED) { openedSince = -1; progress = 0; }
            return Event.NONE;
        }
        if (ratio <= CLOSED) {
            if (releaseSince < 0) releaseSince = now;
            progress = Math.min(1f, (now - releaseSince) / (float) RELEASE_MS);
            if (now - releaseSince >= RELEASE_MS) {
                armed = false; qualified = false; openedSince = releaseSince = -1;
                closedSince = now; cooldownUntil = now + COOLDOWN_MS;
                return Event.OPEN_CONFIRM;
            }
        } else if (ratio >= OPEN) { releaseSince = -1; progress = 1f; }
        return Event.NONE;
    }
    public float progress() { return progress; }
    public boolean isArmed() { return armed; }
}
