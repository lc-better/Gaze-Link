package com.demo.opencv.communication;

import java.util.ArrayList;
import java.util.List;

/** Input version and request version jointly prevent late suggestions replacing a newer draft. */
public final class Draft {
    private String text = "", context = "", suggestion = "";
    private long version;
    private final List<String> history = new ArrayList<>();
    public String text() { return text; }
    public String context() { return context; }
    public String suggestion() { return suggestion; }
    public long version() { return version; }
    public void replace(String value) {
        if (text.equals(value)) return;
        history.add(text); text = value; invalidate();
    }
    public void add(String value) {
        replace(text + (text.isEmpty() || value.matches(".*[\\p{IsHan}].*") ? "" : " ") + value);
    }
    public void setContext(String value) { if (!context.equals(value)) { context = value; invalidate(); } }
    public void undo() {
        if (history.isEmpty()) return;
        text = history.remove(history.size() - 1); invalidate();
    }
    public void invalidate() { version++; suggestion = ""; }
    public long beginRequest() { invalidate(); return version; }
    public boolean receive(long requestedVersion, String value) {
        if (requestedVersion != version) return false;
        suggestion = value; return true;
    }
    public void adopt() { if (!suggestion.isEmpty()) { replace(suggestion); suggestion = ""; } }
}
