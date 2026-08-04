/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.security;

import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CheckSum}.
 */
public class CheckSumTest {

    /**
     * Regression test for the boundary-split bug in the old chunked StringReader implementation.
     *
     * The old code read the input in 1024-char buffers. A surrogate pair that straddles the
     * boundary — high surrogate at index 1023, low surrogate at index 1024 — was split across
     * two separate getBytes(UTF-8) calls. Each orphaned surrogate encoded to the UTF-8
     * replacement character (EF BF BD), producing a digest that differed from the correct
     * value obtained by encoding the full string atomically.
     *
     * Input: 1023 ASCII 'a' chars followed by U+1F600 (😀, surrogate pair \uD83D\uDE00).
     * The high surrogate sits at index 1023 — the last slot in the first 1024-char buffer —
     * and the low surrogate at index 1024, the first slot of the second buffer.
     *
     * Reference SHA-256 (python3: hashlib.sha256(('a'*1023+'\U0001F600').encode('utf-8')).hexdigest()):
     * 89e5c936145508dae5702610297e5de79634b44601580fc6997ca7941244fda0
     */
    @Test
    public void calculateDigest_surrogatePairStraddlingBufferBoundary_sha256() throws NoSuchAlgorithmException {
        // 1023 ASCII chars push the high surrogate of U+1F600 to position 1023 (last in the
        // 1024-char chunk), so the low surrogate is the first char of the next chunk.
        String message = "a".repeat(1023) + "\uD83D\uDE00";
        String expected = "89e5c936145508dae5702610297e5de79634b44601580fc6997ca7941244fda0";

        String actual = CheckSum.calculateDigest(message, "SHA-256");

        Assertions.assertEquals(64, actual.length());
        Assertions.assertEquals(expected, actual);
    }

    /**
     * Same boundary-split regression test as above, using MD5.
     *
     * Reference MD5 (python3: hashlib.md5(('a'*1023+'\U0001F600').encode('utf-8')).hexdigest()):
     * d7bdb5cdc0fc1838e0d613239f9b6a3f
     */
    @Test
    public void calculateDigest_surrogatePairStraddlingBufferBoundary_md5() throws NoSuchAlgorithmException {
        String message = "a".repeat(1023) + "\uD83D\uDE00";
        String expected = "d7bdb5cdc0fc1838e0d613239f9b6a3f";

        String actual = CheckSum.calculateDigest(message, "MD5");

        Assertions.assertEquals(32, actual.length());
        Assertions.assertEquals(expected, actual);
    }

    /**
     * format() must left-pad single-digit hex values with a leading zero so that
     * every byte always contributes exactly two characters.
     */
    @Test
    public void format_zeropadsLeadingZeroBytes() {
        // byte value 0x00 → "00", 0x0f → "0f", 0xff → "ff"
        byte[] input = new byte[] { 0x00, 0x0f, (byte) 0xff };
        Assertions.assertEquals("000fff", CheckSum.format(input));
    }
}
