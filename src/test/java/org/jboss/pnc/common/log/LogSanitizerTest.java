package org.jboss.pnc.common.log;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogSanitizerTest {

    @Test
    public void shouldReturnNullForNullInput() {
        Assertions.assertNull(LogSanitizer.clean(null));
    }

    @Test
    public void shouldReturnEmptyStringForEmptyInput() {
        Assertions.assertEquals("", LogSanitizer.clean(""));
    }

    @Test
    public void shouldNotModifyCleanString() {
        String clean = "This is a clean log message";
        Assertions.assertEquals(clean, LogSanitizer.clean(clean));
    }

    @Test
    public void shouldReplaceNullCharacter() {
        String input = "Hello\u0000World";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceTabCharacter() {
        String input = "Hello\tWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceNewlineCharacter() {
        String input = "Hello\nWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceCarriageReturnCharacter() {
        String input = "Hello\rWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceDeleteCharacter() {
        String input = "Hello\u007FWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceMultipleControlCharacters() {
        String input = "Hello\u0000\t\n\r\u007FWorld";
        String expected = "Hello_____World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceAllControlCharactersInRange() {
        // Test a few control characters from the range 0x00-0x1F
        String input = "Test\u0001\u0002\u0003\u001F";
        String expected = "Test____";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveNormalWhitespace() {
        String input = "Hello World";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveSpecialCharacters() {
        String input = "Hello!@#$%^&*()_+-=[]{}|;':\",./<>?World";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveUnicodeCharacters() {
        String input = "Hello 世界 🌍";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldHandleStringWithOnlyControlCharacters() {
        String input = "\u0000\t\n\r\u007F";
        String expected = "_____";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldHandleControlCharactersAtStartAndEnd() {
        String input = "\u0000Hello World\u007F";
        String expected = "_Hello World_";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }
}

// Made with Bob
