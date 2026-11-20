package com.demo.opencv.communication;
import org.junit.Test;
import static org.junit.Assert.*;
public class DraftTest {
    @Test public void editingRejectsLateResponse() {
        Draft d=new Draft(); d.add("喝水"); long request=d.beginRequest(); d.add("温水");
        assertFalse(d.receive(request,"我想喝水")); assertEquals("喝水温水",d.text());
    }
    @Test public void onlyNewestRequestMayUpdateSuggestion() {
        Draft d=new Draft(); d.add("喝水"); long old=d.beginRequest(), current=d.beginRequest();
        assertFalse(d.receive(old,"旧建议")); assertTrue(d.receive(current,"我想喝水"));
        assertEquals("喝水",d.text()); d.adopt(); assertEquals("我想喝水",d.text());
        d.undo(); assertEquals("喝水",d.text());
    }
    @Test public void contextChangeAndCancellationRejectResponse() {
        Draft d=new Draft(); long version=d.beginRequest(); d.setContext("你想吃什么");
        assertFalse(d.receive(version,"吃饭")); version=d.beginRequest(); d.invalidate(); assertFalse(d.receive(version,"喝水"));
    }
}