package com.demo.opencv.textEntry;

import org.junit.Test;
import static org.junit.Assert.*;

public class InitialsInputTest {
    @Test public void selectsLettersFromGroupsAndReturnsToGroupPicker() {
        InitialsInput input = new InitialsInput();
        assertTrue(input.choosingGroup());
        input.chooseGroup(0);
        assertArrayEquals(new String[]{"a","b","c","d","e","f"}, input.letterOptions());
        input.chooseLetter(4);
        assertEquals("e", input.text());
        assertTrue(input.choosingGroup());
    }
    @Test public void deleteAndClearDoNotChangeGroupNavigation() {
        InitialsInput input = new InitialsInput(); input.chooseGroup(3); input.chooseLetter(2);
        input.chooseGroup(4); input.chooseLetter(0); assertEquals("uy", input.text());
        input.deleteLast(); assertEquals("u", input.text()); input.clear();
        assertEquals("", input.text()); assertTrue(input.choosingGroup());
    }
    @Test public void startsANewSentenceInTheGroupPicker() {
        InitialsInput input = new InitialsInput(); input.chooseGroup(0); input.chooseLetter(0);
        input.chooseGroup(2); input.resetSentence();
        assertEquals("", input.text()); assertTrue(input.choosingGroup());
    }
}
