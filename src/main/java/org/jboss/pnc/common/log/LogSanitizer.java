package org.jboss.pnc.common.log;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogSanitizer {
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    /**
     * Placeholder that replaces masked secret values.
     */
    public static final String REDACTED = "[REDACTED]";

    private static final String SECRET_KEYWORDS = "(pass(?:word)?|secret|token|api[_-]?key|access[_-]?key|private[_-]?key|credential)";

    // Quoted form "..." takes priority; unquoted \S+ is the fallback. Neither includes space, so
    // arguments after the value are always preserved.
    private static final String VALUE = "(?:\"[^\"]*\"|\\S+)";

    private static final Pattern CLI_LONG_OPT_SECRET = Pattern
            .compile("(?i)(--[a-z0-9_.-]*" + SECRET_KEYWORDS + "[a-z0-9_.-]*=)" + VALUE);

    private static final Pattern JAVA_PROP_SECRET = Pattern
            .compile("(?i)(-D[a-z0-9_.-]*" + SECRET_KEYWORDS + "[a-z0-9_.-]*=)" + VALUE);

    private static final Pattern ENV_VAR_SECRET = Pattern
            .compile("(?i)(\\b[a-z0-9_.-]*" + SECRET_KEYWORDS + "[a-z0-9_.-]*=)" + VALUE);

    // Minimum length 20 prevents false positives on short words ("true", "false", "null"). The character
    // class excludes space and " so the match self-terminates before a closing quote or the next argument.
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\b(Bearer\\s+)[A-Za-z0-9._\\-+/=]{20,}");

    private static final Pattern BASIC_CREDENTIALS = Pattern.compile("(?i)\\b(Basic\\s+)[A-Za-z0-9+/=]{20,}");

    // Credentials embedded in a URL's userinfo (scheme://user:token@host). Userinfo cannot contain '/',
    // '@' or whitespace, so the match self-terminates at the authority's '@'. scp-like git@host:org/repo
    // has no "://" and is left untouched.
    private static final Pattern URL_USERINFO_SECRET = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://)[^/@\\s]+@");

    public static String clean(String input) {
        return input == null ? null : CONTROL_CHARS.matcher(input).replaceAll("_");
    }

    /**
     * Masks values of the given keys in the input. Handles {@code KEY=value}, {@code -DKEY=value} and
     * {@code --KEY=value} forms; key matching is case-insensitive. Quoted values ({@code KEY="a b"}) are masked
     * entirely.
     *
     * @param input the string to mask, may be null
     * @param sensitiveKeys names of keys whose values should be masked
     * @return the masked string, or null if the input is null
     */
    public static String mask(String input, String... sensitiveKeys) {
        if (input == null || sensitiveKeys == null || sensitiveKeys.length == 0) {
            return input;
        }
        return mask(input, compileKeyPattern(sensitiveKeys));
    }

    /**
     * Masks values matched by a pattern previously built by {@link #compileKeyPattern(String...)}. Use this
     * overload when the same key set is masked repeatedly to avoid recompiling the pattern.
     *
     * @param input the string to mask, may be null
     * @param sensitiveKeyPattern pattern whose group 1 is the preserved prefix (e.g. {@code KEY=})
     * @return the masked string, or null if the input is null
     */
    public static String mask(String input, Pattern sensitiveKeyPattern) {
        return input == null ? null : sensitiveKeyPattern.matcher(input).replaceAll("$1" + REDACTED);
    }

    /**
     * Builds the pattern used by {@link #mask(String, Pattern)} for the given key names.
     *
     * @param sensitiveKeys names of keys whose values should be masked
     * @return compiled pattern
     */
    public static Pattern compileKeyPattern(String... sensitiveKeys) {
        String keys = Arrays.stream(sensitiveKeys).map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile("(?i)([A-Za-z0-9._-]*(?:" + keys + ")=)" + VALUE);
    }

    /**
     * Masks common secrets: values of keys whose names contain a known secret keyword (password, secret, token,
     * api-key, access-key, private-key, credential), {@code Bearer}/{@code Basic} credentials, and credentials
     * embedded in a URL's userinfo ({@code scheme://user:token@host}) anywhere in the input.
     *
     * @param input the string to mask, may be null
     * @return the masked string, or null if the input is null
     */
    public static String maskKnownSecrets(String input) {
        if (input == null) {
            return null;
        }
        String result = CLI_LONG_OPT_SECRET.matcher(input).replaceAll("$1" + REDACTED);
        result = JAVA_PROP_SECRET.matcher(result).replaceAll("$1" + REDACTED);
        result = ENV_VAR_SECRET.matcher(result).replaceAll("$1" + REDACTED);
        result = BEARER_TOKEN.matcher(result).replaceAll("$1" + REDACTED);
        result = BASIC_CREDENTIALS.matcher(result).replaceAll("$1" + REDACTED);
        return URL_USERINFO_SECRET.matcher(result).replaceAll("$1" + REDACTED + "@");
    }
}
