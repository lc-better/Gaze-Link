package com.demo.opencv.communication;
import com.demo.opencv.textEntry.KeyboardManager;
import org.junit.Test;
import static org.junit.Assert.*;
public class KeyboardTest {
    @Test public void touchAndGazeSelectSameSlot() {
        KeyboardManager a=new KeyboardManager(), b=new KeyboardManager();
        a.selectSlot(4); a.selectSlot(0); b.processInput(6); b.processInput(3);
        assertEquals("y",a.getDisplays().TextInput); assertEquals(a.getDisplays().TextInput,b.getDisplays().TextInput);
    }
    @Test public void deleteOnEmptyDoesNotInsertStar() {
        KeyboardManager k=new KeyboardManager(); k.selectSlot(5); k.selectSlot(1);
        assertEquals("",k.getDisplays().TextInput); k.selectSlot(0); k.selectSlot(0);
        assertEquals("a",k.getDisplays().TextInput); k.clear(); assertEquals("",k.getDisplays().TextInput);
    }
}