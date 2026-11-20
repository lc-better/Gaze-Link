package com.demo.opencv.communication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Candidate history and request lifetime for one initials sentence. */
public final class InitialsSession {
    private List<InitialsCandidate> candidates = Collections.emptyList();
    private List<InitialsCandidate> previous = Collections.emptyList();
    private int index, previousIndex;
    private long version;
    private String request = "";
    private boolean generating;

    public long begin(String initials) { request = initials; generating = true; return ++version; }
    public boolean generating() { return generating; }
    public boolean hasCandidates() { return !candidates.isEmpty(); }
    public int count() { return candidates.size(); }
    public int index() { return index; }
    public String text() { return hasCandidates() ? candidates.get(index).text : ""; }
    public boolean canRestore() { return !previous.isEmpty(); }
    public boolean receive(long requestedVersion, String initials, List<InitialsCandidate> result) {
        if (requestedVersion != version || !initials.equals(request)) return false;
        generating = false;
        List<InitialsCandidate> valid = new ArrayList<>();
        for (InitialsCandidate item : result) if (item.matches(request)) valid.add(item);
        if (!valid.isEmpty()) {
            previous = candidates; previousIndex = index;
            candidates = valid; index = 0;
        }
        return true;
    }
    public void next() { if (hasCandidates()) index = (index + 1) % candidates.size(); }
    public void previous() { if (hasCandidates()) index = (index + candidates.size() - 1) % candidates.size(); }
    public void restore() {
        if (!canRestore()) return;
        List<InitialsCandidate> swap = candidates; int swapIndex = index;
        candidates = previous; index = previousIndex;
        previous = swap; previousIndex = swapIndex;
    }
    public void cancel() { version++; generating = false; }
    public void reset() {
        cancel(); request = ""; candidates = previous = Collections.emptyList(); index = previousIndex = 0;
    }
}
