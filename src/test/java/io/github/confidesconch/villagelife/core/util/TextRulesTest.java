package io.github.confidesconch.villagelife.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextRulesTest {
    @Test
    void removesFormattingAndControlCharacters() {
        assertEquals("Hello World", TextRules.cleanName(" §aHello\u0007 World§r ", 32));
        assertEquals("", TextRules.cleanName(null, 32));
        assertEquals("abc", TextRules.cleanName("abcdef", 3));
    }

    @Test
    void markerColoursStayInRange() {
        assertEquals(MarkerColors.argb(0), MarkerColors.argb(-1));
        assertEquals(MarkerColors.argb(0), MarkerColors.argb(16));
        assertEquals(15, MarkerColors.clamp(15));
    }
}
