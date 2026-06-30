package org.jboss.pnc.common.log;

import java.util.regex.Pattern;

public class LogSanitizer {
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    public static String clean(String input) {
        return input == null ? null : CONTROL_CHARS.matcher(input).replaceAll("_");
    }
}
