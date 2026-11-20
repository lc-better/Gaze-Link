package com.demo.opencv.communication;

import java.util.Locale;
import net.sourceforge.pinyin4j.PinyinHelper;

/** Validates the actual Chinese characters, independently of the model's claimed initials. */
public final class InitialsCandidate {
    public final String text;

    public InitialsCandidate(String text) {
        this.text = text == null ? "" : text.trim();
    }

    public boolean matches(String requested) {
        String input = requested.trim().toUpperCase(Locale.ROOT);
        String characters = text.replaceAll("[\\p{P}\\p{Z}\\s]", "");
        if (input.isEmpty() || characters.length() != input.length()) return false;
        for (int i = 0; i < characters.length(); i++) {
            String[] readings = PinyinHelper.toHanyuPinyinStringArray(characters.charAt(i));
            if (readings == null) return false;
            boolean matched = false;
            for (String reading : readings) {
                if (Character.toUpperCase(reading.charAt(0)) == input.charAt(i)) { matched = true; break; }
            }
            if (!matched) return false;
        }
        return true;
    }
}
