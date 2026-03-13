package org.jboss.pnc.common;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringsTest {

    @Test
    public void stripEndingSlash() {
        String string = "http://host.com/path";
        Assertions.assertEquals(string, Strings.stripEndingSlash(string + "/"));
        Assertions.assertEquals(string, Strings.stripEndingSlash(string));
    }

    @Test
    public void stripTrailingSlash() {
        String string = "path/to";
        Assertions.assertEquals(string, Strings.stripTrailingSlash("/" + string));
        Assertions.assertEquals(string, Strings.stripTrailingSlash(string));
    }

    @Test
    public void addEndingSlash() {
        String string = "http://host.com/path";
        Assertions.assertEquals(string + "/", Strings.addEndingSlash(string));
        Assertions.assertEquals(string + "/", Strings.addEndingSlash(string + "/"));
        Assertions.assertNotEquals(string + "//", Strings.addEndingSlash(string + "/"));
    }

    @Test
    public void shouldDeserializeQuery() {
        String query = "key1:value1|value2,key2:value22";
        Map<String, List<String>> map = Strings.toMap(query);

        Assertions.assertEquals(map.get("key1").size(), 2);
        Assertions.assertLinesMatch(map.get("key1"), Arrays.asList(new String[] { "value1", "value2" }));

        Assertions.assertLinesMatch(map.get("key2"), Arrays.asList(new String[] { "value22" }));

    }
}
