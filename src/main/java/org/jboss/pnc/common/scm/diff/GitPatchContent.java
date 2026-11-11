/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2026 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.common.scm.diff;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.Deflater;

/**
 * Writes file contents in git's patch formats, so that a diff can carry files whose content a git provider's API leaves
 * out: text (valid UTF-8 without NUL bytes) as one hunk replacing the whole file, and binary content as a
 * {@code GIT binary patch} with full ("literal") contents, which {@code git apply} recreates byte for byte. Diff text
 * is
 * kept one char per byte ({@link #RAW}).
 */
public final class GitPatchContent {

    public static final String ZERO_ID = "0000000000000000000000000000000000000000";
    private static final String BASE85 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            + "!#$%&()*+-;<=>?@^_`{|}~";
    private static final int BYTES_PER_LINE = 52;
    private static final int TEXT_PROBE_BYTES = 8000;
    private static final int DECODE_CHUNK_CHARS = 8192;

    /**
     * How diff text is kept: one char per byte (ISO-8859-1 maps bytes 0-255 to chars 0-255), so that content in any
     * encoding goes through unchanged and takes one byte per char in memory.
     */
    public static final Charset RAW = StandardCharsets.ISO_8859_1;

    private GitPatchContent() {
    }

    /** A Unicode string (a path or a patch from a JSON API) as raw diff text: its UTF-8 bytes, one char per byte. */
    public static String raw(String unicode) {
        return isAscii(unicode) ? unicode : new String(unicode.getBytes(StandardCharsets.UTF_8), RAW);
    }

    /** Raw diff text (e.g. a path read from a diff) as Unicode, decoding its bytes as UTF-8. */
    public static String unicode(String raw) {
        return isAscii(raw) ? raw : new String(raw.getBytes(RAW), StandardCharsets.UTF_8);
    }

    private static boolean isAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x80) {
                return false;
            }
        }
        return true;
    }

    /** The git object id of a blob with this content (SHA-1 of {@code "blob <size>\0" + content}). */
    public static String blobId(byte[] content) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
            return hex(sha1.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Whether the content can be written as text hunks: valid UTF-8 without NUL bytes in its beginning (git's own
     * binary heuristic looks for NUL in the first 8000 bytes).
     */
    public static boolean isText(byte[] content) {
        for (int i = 0; i < Math.min(content.length, TEXT_PROBE_BYTES); i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        // validate in chunks: decoding the whole content at once would allocate a char buffer of its size
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(content);
        CharBuffer out = CharBuffer.allocate(DECODE_CHUNK_CHARS);
        CoderResult result;
        do {
            out.clear();
            result = decoder.decode(in, out, true);
            if (result.isError()) {
                return false;
            }
        } while (result.isOverflow());
        do {
            out.clear();
            result = decoder.flush(out);
        } while (result.isOverflow());
        return !result.isError();
    }

    /**
     * The hunk adding a whole text file, with the {@code ---}/{@code +++} lines, as raw diff text. Empty content gives
     * an empty string (an empty new file only needs its header).
     *
     * @param path the path, as Unicode
     */
    public static String newTextFileHunk(String path, byte[] content) {
        StringBuilder out = new StringBuilder();
        appendTextHunk(out, "/dev/null", "b/" + raw(path), null, content);
        return out.toString();
    }

    /**
     * Appends one hunk replacing the whole old text with the whole new text (null when the file does not exist on that
     * side), with the {@code ---}/{@code +++} lines; nothing when both are empty. The content is kept byte for byte.
     *
     * @param minus the old side label as raw diff text ({@code a/<path>} or {@code /dev/null})
     * @param plus the new side label as raw diff text ({@code b/<path>} or {@code /dev/null})
     */
    static void appendTextHunk(StringBuilder out, String minus, String plus, byte[] oldContent, byte[] newContent) {
        String oldText = oldContent == null ? "" : new String(oldContent, RAW);
        String newText = newContent == null ? "" : new String(newContent, RAW);
        int oldCount = lineCount(oldText);
        int newCount = lineCount(newText);
        if (oldCount == 0 && newCount == 0) {
            return;
        }
        out.ensureCapacity(out.length() + oldText.length() + newText.length() + oldCount + newCount + 128);
        out.append("--- ").append(minus).append('\n');
        out.append("+++ ").append(plus).append('\n');
        out.append("@@ -").append(range(oldCount)).append(" +").append(range(newCount)).append(" @@\n");
        appendLines(out, '-', oldText);
        appendLines(out, '+', newText);
    }

    private static int lineCount(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int end = text.endsWith("\n") ? text.length() - 1 : text.length();
        int count = 1;
        for (int i = text.indexOf('\n'); i >= 0 && i < end; i = text.indexOf('\n', i + 1)) {
            count++;
        }
        return count;
    }

    private static String range(int count) {
        return count == 0 ? "0,0" : count == 1 ? "1" : "1," + count;
    }

    private static void appendLines(StringBuilder out, char sign, String text) {
        if (text.isEmpty()) {
            return;
        }
        boolean endsWithNewline = text.endsWith("\n");
        int end = endsWithNewline ? text.length() - 1 : text.length();
        int start = 0;
        while (start <= end) {
            int newline = text.indexOf('\n', start);
            int lineEnd = newline < 0 || newline > end ? end : newline;
            out.append(sign).append(text, start, lineEnd).append('\n');
            start = lineEnd + 1;
        }
        if (!endsWithNewline) {
            out.append("\\ No newline at end of file\n");
        }
    }

    /**
     * The {@code index} line and the {@code GIT binary patch} with the full new content (forward) and the full old
     * content (reverse, for {@code git apply -R}). {@code null} means the file does not exist on that side.
     */
    public static String binaryPatch(byte[] oldContent, byte[] newContent) {
        StringBuilder out = new StringBuilder();
        appendBinaryPatch(out, oldContent, newContent);
        return out.toString();
    }

    /** Appends {@link #binaryPatch}, without intermediate strings. */
    static void appendBinaryPatch(StringBuilder out, byte[] oldContent, byte[] newContent) {
        byte[] before = oldContent == null ? new byte[0] : oldContent;
        byte[] after = newContent == null ? new byte[0] : newContent;
        out.append("index ")
                .append(oldContent == null ? ZERO_ID : blobId(before))
                .append("..")
                .append(newContent == null ? ZERO_ID : blobId(after))
                .append("\nGIT binary patch\n");
        appendLiteral(out, after);
        out.append('\n');
        appendLiteral(out, before);
        out.append('\n');
    }

    /** A {@code literal} binary hunk: the zlib-deflated content, in git's base85 lines. */
    public static String literal(byte[] content) {
        StringBuilder out = new StringBuilder();
        appendLiteral(out, content);
        return out.toString();
    }

    private static void appendLiteral(StringBuilder out, byte[] content) {
        byte[] deflated = deflate(content);
        // 5 chars per 4 bytes, plus the length char and newline of each line
        out.ensureCapacity(out.length() + deflated.length / 4 * 5 + deflated.length / BYTES_PER_LINE * 2 + 32);
        out.append("literal ").append(content.length).append('\n');
        for (int offset = 0; offset < deflated.length; offset += BYTES_PER_LINE) {
            int length = Math.min(BYTES_PER_LINE, deflated.length - offset);
            out.append(length <= 26 ? (char) ('A' + length - 1) : (char) ('a' + length - 27));
            for (int group = 0; group < length; group += 4) {
                long value = 0;
                for (int i = 0; i < 4; i++) {
                    int index = offset + group + i;
                    int b = index < offset + length ? deflated[index] & 0xff : 0;
                    value = (value << 8) | b;
                }
                char[] chars = new char[5];
                for (int i = 4; i >= 0; i--) {
                    chars[i] = BASE85.charAt((int) (value % 85));
                    value /= 85;
                }
                out.append(chars);
            }
            out.append('\n');
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }

    private static byte[] deflate(byte[] content) {
        // the level git uses for binary patches (zlib header 78 01), so the output is the same as git's
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(content);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, content.length / 2));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
