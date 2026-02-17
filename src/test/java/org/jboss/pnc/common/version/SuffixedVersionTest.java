/*
 * Copyright 2018 Honza Brázdil &lt;jbrazdil@redhat.com&gt;.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.version;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 */
public class SuffixedVersionTest {

    @Test
    public void testSimpleVersions() {
        SuffixedVersion v1 = new SuffixedVersion(1, 2, 3, "", "1.2.3");
        SuffixedVersion v2 = new SuffixedVersion(1, 2, 3, "", "1.2.3");
        SuffixedVersion v3 = new SuffixedVersion(1, 2, 4, "", "1.2.4");
        SuffixedVersion v4 = new SuffixedVersion(2, 3, 4, "foobar", "2.3.4.foobar");
        Assertions.assertEquals(1, v1.getMajor());
        Assertions.assertEquals(2, v1.getMinor());
        Assertions.assertEquals(3, v1.getMicro());
        Assertions.assertEquals("", v1.getQualifier());
        Assertions.assertEquals("foobar", v4.getQualifier());
        Assertions.assertTrue(v1.equals(v2));
        Assertions.assertFalse(v1.equals(v3));
        Assertions.assertEquals("1.2.3", v1.toString());
        Assertions.assertEquals("2.3.4.foobar", v4.toString());
    }

    @Test
    public void testSuffixedVersions() {
        SuffixedVersion v1 = new SuffixedVersion(1, 2, 3, "", "suffix", 1, "1.2.3.suffix-1");
        SuffixedVersion v2 = new SuffixedVersion(1, 2, 3, "", "suffix", 1, "1.2.3.suffix-1");
        SuffixedVersion v3a = new SuffixedVersion(1, 2, 3, "", "suffix", 2, "1.2.3.suffix-2");
        SuffixedVersion v3b = new SuffixedVersion(1, 2, 3, "", "xiffus", 1, "1.2.3.xiffus-1");
        SuffixedVersion v4 = new SuffixedVersion(2, 3, 4, "foobar", "suffix", 1, "2.3.4.foobar-suffix-1");
        Assertions.assertEquals("suffix", v1.getSuffix().get());
        Assertions.assertEquals(Integer.valueOf(1), v1.getSuffixVersion().get());
        Assertions.assertEquals(Integer.valueOf(2), v3a.getSuffixVersion().get());
        Assertions.assertEquals("", v1.getQualifier());
        Assertions.assertTrue(v1.equals(v2));
        Assertions.assertFalse(v1.equals(v3a));
        Assertions.assertFalse(v1.equals(v3b));
        Assertions.assertEquals("1.2.3.suffix-1", v1.toString());
        Assertions.assertEquals("2.3.4.foobar-suffix-1", v4.toString());
    }
}
