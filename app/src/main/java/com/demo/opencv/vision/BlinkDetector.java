package com.demo.opencv.vision;

/** Classifies deliberate closures from sustained left, right and bilateral evidence. */
public final class BlinkDetector {
    public enum Event { NONE, LEFT_WINK, RIGHT_WINK, BOTH_CLOSED }
    private static final float OPEN = .65f;
    private static final long ARM_MS = 250, REOPEN_MS = 100, COOLDOWN_MS = 800, MAX_GAP_MS = 400;
    private long openSince = -1, closedSince = -1, reopenedSince = -1, previousFrame = -1, cooldownUntil;
    private long closedDuration, runMillis, leftHold, rightHold, bothHold;
    private Event observedBefore = Event.NONE;
    private boolean armed;
    private float progress;

    public void reset() {
        openSince = closedSince = reopenedSince = previousFrame = -1;
        cooldownUntil = closedDuration = runMillis = leftHold = rightHold = bothHold = 0;
        observedBefore = Event.NONE; armed = false; progress = 0;
    }
    public boolean inProgress() { return closedSince >= 0; }
    public boolean blocksGaze() { return inProgress() || (previousFrame >= 0 && previousFrame < cooldownUntil); }
    public float progress() { return progress; }
    private Event observe(float left, float right) {
        if (left <= .35f && right >= .50f && right - left >= .25f) return Event.LEFT_WINK;
        if (right <= .35f && left >= .50f && left - right >= .25f) return Event.RIGHT_WINK;
        if (left <= .28f && right <= .28f && Math.abs(left - right) <= .15f) return Event.BOTH_CLOSED;
        return Event.NONE;
    }
    private Event result() {
        // A stray bilateral frame cannot override a wink. Mixed sustained gestures
        // are ambiguous rather than being converted into a cancellation.
        if (bothHold >= 700 && leftHold < 250 && rightHold < 250 && closedDuration <= 2000)
            return Event.BOTH_CLOSED;
        if (closedDuration > 1500 || bothHold >= 700) return Event.NONE;
        if (leftHold >= 400 && rightHold == 0) return Event.LEFT_WINK;
        if (rightHold >= 400 && leftHold == 0) return Event.RIGHT_WINK;
        return Event.NONE;
    }
    private void accumulate(long delta) {
        runMillis += delta;
        if (observedBefore == Event.LEFT_WINK) leftHold = Math.max(leftHold, runMillis);
        else if (observedBefore == Event.RIGHT_WINK) rightHold = Math.max(rightHold, runMillis);
        else if (observedBefore == Event.BOTH_CLOSED) bothHold = Math.max(bothHold, runMillis);
    }
    public Event update(boolean valid, float left, float right, long now) {
        if (previousFrame >= 0 && now - previousFrame > MAX_GAP_MS) reset();
        long delta = previousFrame < 0 ? 0 : now - previousFrame;
        previousFrame = now; progress = 0;
        if (!valid || left < 0 || right < 0) { reset(); return Event.NONE; }
        boolean bothOpen = left >= OPEN && right >= OPEN;
        Event observed = observe(left, right);
        if (now < cooldownUntil) return Event.NONE;
        if (!armed) {
            if (!bothOpen) openSince = -1;
            else {
                if (openSince < 0) openSince = now;
                if (now - openSince >= ARM_MS) armed = true;
            }
            return Event.NONE;
        }
        if (!inProgress()) {
            if (observed != Event.NONE) {
                closedSince = now; observedBefore = observed; runMillis = 0;
                leftHold = rightHold = bothHold = 0;
            }
            return Event.NONE;
        }
        // Credit each observed interval once to the preceding frame's category.
        accumulate(delta);
        if (observed != observedBefore) runMillis = 0;
        observedBefore = observed;
        if (bothOpen) {
            if (reopenedSince < 0) { reopenedSince = now; closedDuration = now - closedSince; }
            if (now - reopenedSince < REOPEN_MS) return Event.NONE;
            Event event = result();
            closedSince = -1; armed = false; openSince = reopenedSince = -1;
            cooldownUntil = now + (event == Event.NONE ? ARM_MS : COOLDOWN_MS);
            return event;
        }
        reopenedSince = -1;
        progress = Math.min(1, Math.max(Math.max(leftHold, rightHold) / 400f, bothHold / 700f));
        return Event.NONE;
    }
}
