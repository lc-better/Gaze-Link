package com.demo.opencv.communication;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class InitialsResponseTest {
    @Test public void rejectsWrongSentenceEvenWhenModelClaimsTheInitialsMatch() {
        List<InitialsCandidate> items = InitialsResponse.parse("{\"candidates\":[{\"text\":\"晚安\",\"initials\":\"WAN\"},{\"text\":\"我爱你\"}]}", "WAN");
        assertEquals(1, items.size()); assertEquals("我爱你", items.get(0).text);
    }
    @Test public void acceptsMarkdownWrappedJsonAndRemovesDuplicateCandidates() {
        List<InitialsCandidate> items = InitialsResponse.parse("```json\n{\"candidates\":[{\"text\":\"我想喝水\"},{\"text\":\"我想喝水\"}]}\n```", "WXHS");
        assertEquals(1, items.size());
    }
    @Test public void malformedOrUnmatchedResponsesLeaveTheUserAbleToRetry() {
        assertTrue(InitialsResponse.parse("晚安", "WAN").isEmpty());
        assertTrue(InitialsResponse.parse("{broken}", "WAN").isEmpty());
        assertTrue(InitialsResponse.parse("{\"candidates\":[]}", "WAN").isEmpty());
    }
    @Test public void rejectsTheActualIncorrectWanResponseFromTheProvider() {
        assertTrue(InitialsResponse.parse("{\"candidates\":[{\"text\":\"我爱你们\"},{\"text\":\"忘了一年\"}]}", "WAN").isEmpty());
    }
    @Test public void keepsOnlyMatchingCandidatesFromActualWxhsResponse() {
        List<InitialsCandidate> items = InitialsResponse.parse("{\"candidates\":[{\"text\":\"我想回收\"},{\"text\":\"微信好友\"},{\"text\":\"我想喝水\"}]}", "WXHS");
        assertEquals(2, items.size()); assertEquals("我想喝水", items.get(1).text);
    }
}
