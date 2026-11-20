package com.demo.opencv.communication;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocalLexiconTest {
    @Test public void pinyinGroupsProduceChineseNotRawPinyin() {
        LocalLexicon l=new LocalLexicon(); l.add("shui","水"); l.add("chi","吃");
        assertEquals("2131",LocalLexicon.encode("shuǐ"));
        assertEquals("水",l.search("2131").get(0).text); assertTrue(l.search("3333333").isEmpty());
    }
    @Test public void exactMatchesComeBeforePrefixesAndSupportPersonalWords() {
        LocalLexicon l=new LocalLexicon(); l.add("heshui","喝水"); l.add("he","喝"); l.add("xiaoming","小明");
        assertEquals("喝",l.search(LocalLexicon.encode("he")).get(0).text);
        assertEquals("小明",l.search(LocalLexicon.encode("xiaoming")).get(0).text);
        assertTrue(l.search("").isEmpty());
    }
}