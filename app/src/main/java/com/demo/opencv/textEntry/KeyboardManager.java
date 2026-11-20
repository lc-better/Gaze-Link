package com.demo.opencv.textEntry;

public class KeyboardManager {

    String textInput = ""; // stores the final text input
    String[] layer1Options = {"A B C D E F", "G H I J K L", "M N O P Q R", "S T U V W X", "Y Z , . ! ?", "空格 删除 清空"};
    String[] layer2Options = {"abcdef", "ghijkl", "mnopqr", "stuvwx", "yz,.!?", " *#"};
    int layer = 1;
    int page = 0;

    public KeyboardData getDisplays() {

        KeyboardData keyboardData = new KeyboardData();
        String[] options = new String[6];

        if (layer == 1) {
            options = layer1Options;
        } else {
            String selected = layer2Options[page];
            for (int i = 0; i < selected.length(); i++) {
                if (selected.charAt(i) == ' ') { // space operation
                    options[i] = "空格";
                } else if (selected.charAt(i) == '*') { // delete operation
                    options[i] = "删除末尾字母";
                } else if (selected.charAt(i) == '#') { // clear option
                    options[i] = "清空字母";
                } else {
                    options[i] = "" + selected.charAt(i); // letter
                }
            }
        }

        keyboardData.setOptions(options);
        keyboardData.setTextInput(textInput); // set current text entry
        return keyboardData;
    }

    public void processInput(int selection) { // 0: up, 1: left, 2: down, 3: right
        // left gaze = 1, right gaze = 2, up gaze = 0, down gaze = -1, closed = 3, left-up gaze = 4, right-up gaze = 5
        final int[] keyboardInputMat = {-1, 1, 2, 0, -1, 3, 4, 5}; // convert gaze type into keyboard input
        int input = keyboardInputMat[selection];
        if (input == -1) return;
        selectSlot(input);
    }

    public void selectSlot(int input) {
        if (layer == 1 && input < layer1Options.length) {
            layer = 2;
            page = input;
        } else if (input < layer2Options[page].length()){
            char operation = layer2Options[page].charAt(input);
            if (operation == '*') {
                if (!textInput.isEmpty()) textInput = textInput.substring(0, textInput.length() - 1); // delete operation
            } else if (operation == '#') {
                textInput = "";
            } else {
                textInput += operation; // add the letter/space
            }
            layer = 1;
        }

    }

    public void clear() { textInput = ""; layer = 1; }
    public boolean choosingGroup() { return layer == 1; }
    public void backToGroups() { layer = 1; }

    public void initialize() {

    }

}
