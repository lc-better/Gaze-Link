package com.demo.opencv.communication;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A small offline phrase lexicon, deliberately not presented as a full Chinese IME. */
public final class LocalLexicon {
    public static final class Entry {
        public final String pinyin, text;
        public Entry(String pinyin, String text) { this.pinyin = pinyin; this.text = text; }
    }
    private final List<Entry> entries = new ArrayList<>();
    public void add(String pinyin, String text) { entries.add(new Entry(pinyin.trim(), text.trim())); }
    public static String encode(String value) {
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        StringBuilder code = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            if (c < 'a' || c > 'z') continue;
            code.append(c <= 'f' ? '0' : c <= 'm' ? '1' : c <= 't' ? '2' : '3');
        }
        return code.toString();
    }
    public List<Entry> search(String code) {
        List<Entry> result = new ArrayList<>();
        if (code.isEmpty()) return result;
        for (Entry entry : entries) if (encode(entry.pinyin).startsWith(code)) result.add(entry);
        // Exact matches precede the remaining prefix matches; personal entries keep insertion order.
        result.sort((a, b) -> Boolean.compare(!encode(a.pinyin).equals(code), !encode(b.pinyin).equals(code)));
        return result;
    }
    /** Personal entries retain priority for initials just as they do for pinyin. */
    public List<InitialsCandidate> searchInitials(String initials) {
        List<InitialsCandidate> result = new ArrayList<>();
        for (Entry entry : entries) {
            InitialsCandidate candidate = new InitialsCandidate(entry.text);
            if (!candidate.matches(initials)) continue;
            boolean duplicate = false;
            for (InitialsCandidate existing : result) if (existing.text.equals(candidate.text)) duplicate = true;
            if (!duplicate) result.add(candidate);
            if (result.size() == 3) break;
        }
        return result;
    }

    /** Returns a readable query for the four eye-selectable pinyin groups. */
    public static String describeGroups(String code) {
        if (code.isEmpty()) return "尚未选择";
        return code.replace("0", "A–F ").replace("1", "G–M ")
                .replace("2", "N–T ").replace("3", "U–Z ").trim();
    }
}
