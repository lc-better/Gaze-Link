package com.demo.opencv.communication;

import org.junit.Test;
import static org.junit.Assert.*;

public class InitialsCandidateTest {
    @Test public void wanMeansThreeCharactersRatherThanFullPinyin() {
        assertTrue(new InitialsCandidate("我爱你").matches("wan"));
        assertFalse(new InitialsCandidate("晚安").matches("WAN"));
    }
    @Test public void verifiesEveryInitialAndIgnoresPunctuation() {
        assertTrue(new InitialsCandidate("我想喝水。").matches("WXHS"));
        assertFalse(new InitialsCandidate("我想喝水").matches("WXSH"));
        assertFalse(new InitialsCandidate("我想喝水").matches("WXH"));
        assertFalse(new InitialsCandidate("我想喝水").matches("WXHSS"));
    }
    @Test public void handlesPolyphonicAndVowelInitials() {
        assertTrue(new InitialsCandidate("重新开始").matches("CXKS"));
        assertTrue(new InitialsCandidate("饿了").matches("EL"));
    }
    @Test public void doesNotInventReadingsForNonChineseOrEmptyText() {
        assertFalse(new InitialsCandidate("WAN").matches("WAN"));
        assertFalse(new InitialsCandidate("").matches(""));
    }
}
