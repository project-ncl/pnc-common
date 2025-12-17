package org.jboss.pnc.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Strings {

    /**
     * Check if the given string is null or contains only whitespace characters.
     *
     * @param string String to check for non-whitespace characters
     * @return boolean True if the string is null, empty, or contains only whitespace (empty when trimmed). Otherwise
     *         return false.
     */
    public static boolean isEmpty(String string) {
        if (string == null) {
            return true;
        }
        return string.trim().isEmpty();
    }

    /**
     * Converts string with key:value1|value2,key2:value22 to a map where key is an entry key and values are a list of
     * items.
     */
    public static Map<String, List<String>> toMap(String string) {
        if (string == null || string.equals("")) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> map = new HashMap<>();
        try {
            String[] pairs = string.split(",");

            for (String pair : pairs) {
                String[] keyValues = pair.split(":");
                String valuesString = keyValues[1];
                String[] values = valuesString.split("\\|");
                map.put(keyValues[0], Arrays.asList(values));
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Invalid key:value string: [" + string + "]", e);
        }
        return map;
    }

    /**
     * Parse comma separated string to Integer array.
     *
     * @return An empty array when the string parameter is empty or null.
     */
    public static Integer[] deserializeInt(String string) {
        if (string == null) {
            return new Integer[0];
        }
        return Arrays.stream(string.split(","))
                .filter(s -> !s.equals(""))
                .map(Integer::parseInt)
                .toArray(Integer[]::new);
    }

    /**
     * Parse comma separated string to Long array.
     *
     * @return An empty array when the string parameter is empty or null.
     */
    public static Long[] deserializeLong(String string) {
        if (string == null) {
            return new Long[0];
        }
        return Arrays.stream(string.split(",")).filter(s -> !s.equals("")).map(Long::parseLong).toArray(Long[]::new);
    }

    /**
     * Serialize Integer array to comma separated string.
     *
     * @return An empty string when the Integer array parameter is empty or null.
     */
    public static String serializeInt(Integer[] integers) {
        if (integers == null) {
            return "";
        }
        return Arrays.stream(integers).map(i -> Integer.toString(i)).collect(Collectors.joining(","));
    }

    public static String serializeLong(Long[] longs) {
        if (longs == null) {
            return "";
        }
        return Arrays.stream(longs).map(i -> Long.toString(i)).collect(Collectors.joining(","));
    }

    public static String stripSuffix(String string, String suffix) {
        if (string == null) {
            return null;
        }
        if (suffix == null) {
            return string;
        }

        if (string.endsWith(suffix)) {
            return string.substring(0, string.length() - suffix.length());
        } else {
            return string;
        }
    }

    public static String stripProtocol(String url) {
        if (url == null) {
            return null;
        }

        String protocolDivider = "://";
        int protocolDividerIndex = url.indexOf(protocolDivider);

        if (protocolDividerIndex > -1) {
            return url.substring(protocolDividerIndex + protocolDivider.length());
        } else {
            return url;
        }
    }

    /**
     * Remove ending slash if present and return the string without ending slash
     *
     * @param string
     * @return
     */
    public static String stripEndingSlash(String string) {
        if (string == null) {
            return null;
        }
        if (string.endsWith("/")) {
            string = string.substring(0, string.length() - 1);
        }
        return string;
    }

    /**
     * Remove slash at the begining if present and return the string without ending slash
     *
     * @param string
     * @return
     */
    public static String stripTrailingSlash(String string) {
        if (string == null) {
            return null;
        }
        if (string.startsWith("/")) {
            string = string.substring(1);
        }
        return string;
    }

    /**
     * Adds ending slash if it is not present.
     *
     * @param string
     * @return
     */
    public static String addEndingSlash(String string) {
        if (string == null) {
            return null;
        }
        if (!string.endsWith("/")) {
            string += "/";
        }
        return string;
    }

    /**
     * If the value is not empty the value is returned otherwise the defaultValue is returned.
     *
     * @param value
     * @param defaultValue
     * @return
     */
    public static String valueOrDefault(String value, String defaultValue) {
        if (!isEmpty(value)) {
            return value;
        } else {
            return defaultValue;
        }
    }

    public static String nullIfBlank(String string) {
        if (string == null || string.trim().isEmpty()) {
            return null;
        }
        return string;
    }

    public static String fistCharToLower(String string) {
        char c[] = string.toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    /**
     *
     * @return true if 'search' String matches any String in the 'strings' array.
     */
    public static boolean anyStringEquals(String search, String... strings) {
        for (String string : strings) {
            if (search.equals(string)) {
                return true;
            }
        }
        return false;
    }
}
