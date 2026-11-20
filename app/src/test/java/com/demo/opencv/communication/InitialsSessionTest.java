package com.demo.opencv.communication;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class InitialsSessionTest {
    private final InitialsSession session = new InitialsSession();

    @Test public void resetAndNewRequestRejectThePreviousSentenceCallback() {
        long old = session.begin("WAN"); session.reset();
        long current = session.begin("WXHS");
        assertFalse(session.receive(old, "WAN", Arrays.asList(new InitialsCandidate("我爱你"))));
        assertTrue(session.receive(current, "WXHS", Arrays.asList(new InitialsCandidate("我想喝水"))));
        assertEquals("我想喝水", session.text());
    }
    @Test public void cancelRejectsTheQueuedCallbackAndRetainsPreviousCandidates() {
        long first = session.begin("WAN");
        session.receive(first, "WAN", Arrays.asList(new InitialsCandidate("我爱你")));
        long retry = session.begin("WAN"); session.cancel();
        assertFalse(session.receive(retry, "WAN", Arrays.asList(new InitialsCandidate("我爱您"))));
        assertFalse(session.generating()); assertEquals("我爱你", session.text());
    }
    @Test public void failedRegenerationKeepsLastConfirmedPreview() {
        long first = session.begin("WAN");
        session.receive(first, "WAN", Arrays.asList(new InitialsCandidate("我爱你")));
        long retry = session.begin("WAN");
        assertTrue(session.receive(retry, "WAN", Collections.emptyList()));
        assertEquals("我爱你", session.text()); assertFalse(session.generating());
    }
    @Test public void restoresCompletePreviousBatchAndItsSelectedIndex() {
        long first = session.begin("WAN");
        session.receive(first, "WAN", Arrays.asList(new InitialsCandidate("我爱你"), new InitialsCandidate("我爱您")));
        session.next(); assertEquals("我爱您", session.text());
        session.previous(); assertEquals("我爱你", session.text()); session.previous(); assertEquals("我爱您", session.text());
        long second = session.begin("WAN");
        session.receive(second, "WAN", Arrays.asList(new InitialsCandidate("我爱你")));
        session.restore(); assertEquals(2, session.count()); assertEquals(1, session.index());
        assertEquals("我爱您", session.text());
        session.reset(); assertFalse(session.hasCandidates()); assertFalse(session.canRestore());
    }
    @Test public void localLexiconUsesActualCharactersAndPrefersPersonalEntries() {
        LocalLexicon lexicon = new LocalLexicon();
        lexicon.add("woaini", "我爱你"); lexicon.add("wanan", "晚安"); lexicon.add("woaini", "我爱你");
        assertEquals(1, lexicon.searchInitials("WAN").size());
        assertEquals("我爱你", lexicon.searchInitials("WAN").get(0).text);
        assertTrue(lexicon.searchInitials("WXHS").isEmpty());
    }
}
