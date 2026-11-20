package com.demo.opencv.textEntry;

/** Small eye controlled input state for Chinese sentence initials. */
public final class InitialsInput {
    /** Full contents are shown so the user does not need to infer letters from a range. */
    public static final String[] GROUPS = {"A B C D E F", "G H I J K L", "M N O P Q R", "S T U V W X", "Y Z"};
    private static final String[] LETTERS = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz"};
    private final StringBuilder value = new StringBuilder();
    private int group = -1;

    public boolean choosingGroup() { return group < 0; }
    public int group() { return group; }
    public String[] groupOptions() { return GROUPS.clone(); }
    public String[] letterOptions() {
        String letters = LETTERS[group];
        String[] result = new String[letters.length()];
        for (int i = 0; i < letters.length(); i++) result[i] = letters.substring(i, i + 1);
        return result;
    }
    public void chooseGroup(int value) { group = value; }
    public void chooseLetter(int slot) {
        String letters = LETTERS[group];
        if (slot >= 0 && slot < letters.length()) value.append(letters.charAt(slot));
        group = -1;
    }
    public void deleteLast() { if (value.length() > 0) value.deleteCharAt(value.length() - 1); }
    public void clear() { value.setLength(0); group = -1; }
    /** Clears the sentence and returns to the group picker. */
    public void resetSentence() { clear(); }
    public String text() { return value.toString(); }
}
