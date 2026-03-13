package org.jboss.pnc.common;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MapsTest {

    @Test
    public void shouldRemaplceMapValue() {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> mapA = new HashMap<>();
        Map<String, Object> mapB = new HashMap<>();
        root.put("a", mapA);
        root.put("b", mapB);
        root.put("c", new HashMap<>());

        Map<String, String> mapBB = new HashMap<>();
        mapB.put("bb", mapBB);
        mapB.put("bc", new HashMap<>());

        mapBB.put("bbb", "my value");
        Maps.deepReplace(root, "/b/bb/bbb", "new value");
        Assertions.assertEquals("new value", ((Map) ((Map) root.get("b")).get("bb")).get("bbb"));
    }

    @Test
    public void shouldCheckIfKeyExists() {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> mapA = new HashMap<>();
        Map<String, Object> mapB = new HashMap<>();
        root.put("a", mapA);
        root.put("b", mapB);
        root.put("c", new HashMap<>());

        Map<String, String> mapBB = new HashMap<>();
        mapB.put("bb", mapBB);
        mapB.put("bc", new HashMap<>());

        mapBB.put("bbb", "my value");
        Assertions.assertTrue(Maps.deepContainsKey(root, "/b"));
        Assertions.assertTrue(Maps.deepContainsKey(root, "/c"));
        Assertions.assertTrue(Maps.deepContainsKey(root, "/b/bb"));
        Assertions.assertTrue(Maps.deepContainsKey(root, "/b/bb/bbb"));

        Assertions.assertFalse(Maps.deepContainsKey(root, "/d"));
        Assertions.assertFalse(Maps.deepContainsKey(root, "/b/bb/bbc"));
        Assertions.assertFalse(Maps.deepContainsKey(root, "/b/bb/bbb/x"));
        Assertions.assertFalse(Maps.deepContainsKey(root, "/b/bb/bbb/x/x"));
    }
}
