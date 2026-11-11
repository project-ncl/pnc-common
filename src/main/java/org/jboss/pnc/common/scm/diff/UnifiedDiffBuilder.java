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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Builds complete git unified diffs from what git providers' REST APIs return, so that the diff recreates the target
 * tree with {@code git apply}: content the API leaves out (binary files, files over its size limits) is read with a
 * {@link ContentFetcher} and written in full, text as one hunk replacing the whole file and binary content as a
 * {@code GIT binary patch} with literal contents. Files that cannot be read (or that the fetcher declines, e.g. over a
 * size limit) stay {@code Binary files ... differ} and are reported as incomplete. The diff is raw text, one char per
 * byte (see {@link GitPatchContent#RAW}), so file content in any encoding is kept byte for byte.
 */
public final class UnifiedDiffBuilder {

    static final String REGULAR_FILE = "100644";
    private static final String SUBMODULE = "160000";
    private static final String DIFF_GIT = "diff --git ";

    private UnifiedDiffBuilder() {
    }

    /**
     * From GitLab's JSON diff entries (compare or commit diff API: {@code old_path}, {@code new_path}, {@code a_mode},
     * {@code b_mode}, {@code new_file}, {@code deleted_file}, {@code renamed_file}, {@code diff}, {@code too_large},
     * {@code collapsed}). GitLab writes "Binary files ... differ" for binary files and an empty diff for patches over
     * its size limit (200 KiB by default).
     *
     * @param fetcher reads left-out content, or null to only report it as incomplete
     * @param fromRef the old side revision
     * @param toRef the new side revision
     * @throws IllegalArgumentException if {@code diffs} is not a JSON array
     */
    public static UnifiedDiff fromGitlab(JsonNode diffs, ContentFetcher fetcher, String fromRef, String toRef) {
        requireArray(diffs, "diffs");
        StringBuilder out = new StringBuilder();
        List<String> incomplete = new ArrayList<>();
        for (JsonNode entry : diffs) {
            String oldPath = entry.path("old_path").asText();
            FilePaths paths = new FilePaths(oldPath, entry.path("new_path").asText(oldPath));
            String aMode = entry.path("a_mode").asText("");
            String bMode = entry.path("b_mode").asText("");
            boolean newFile = entry.path("new_file").asBoolean(false);
            boolean deletedFile = entry.path("deleted_file").asBoolean(false);
            boolean renamedFile = entry.path("renamed_file").asBoolean(false);
            String diff = entry.path("diff").asText("");

            appendHeader(out, paths, aMode, bMode, newFile, deletedFile, renamedFile ? "rename" : null);
            if (diff.startsWith("@@")) {
                appendHunks(out, paths, newFile, deletedFile, GitPatchContent.raw(diff));
                continue;
            }

            boolean truncated = entry.path("too_large").asBoolean(false) || entry.path("collapsed").asBoolean(false);
            // An empty diff is complete for an empty new/deleted file (e.g. PME's marker file), a pure rename and a
            // pure mode change; otherwise GitLab left the content out.
            boolean contentMissing = !diff.isBlank() || truncated
                    || (!newFile && !deletedFile && !renamedFile && aMode.equals(bMode));
            if (contentMissing) {
                fill(out, incomplete, fetcher, fromRef, toRef, paths, newFile, deletedFile, aMode, bMode);
            }
        }
        return new UnifiedDiff(out.toString(), incomplete);
    }

    /**
     * From GitHub's JSON file entries (compare or commit API {@code files}: {@code filename},
     * {@code previous_filename}, {@code status}, {@code patch}, {@code changes}, {@code sha}). GitHub omits
     * {@code patch} for binary files and for patches over its size limits, and does not report file modes, so they come
     * from {@code modes}. A renamed, copied or mode-changed entry without line changes may still be a changed binary
     * file, so its content is compared ({@code sha} is the blob id of the new side).
     *
     * @param fetcher reads left-out content, or null to only report it as incomplete
     * @param modes resolves file modes; null for {@link ModeResolver#REGULAR}
     * @throws IllegalArgumentException if {@code files} is not a JSON array
     */
    public static UnifiedDiff fromGithub(
            JsonNode files,
            ContentFetcher fetcher,
            ModeResolver modes,
            String fromRef,
            String toRef) {
        requireArray(files, "files");
        ModeResolver resolver = modes == null ? ModeResolver.REGULAR : modes;
        StringBuilder out = new StringBuilder();
        List<String> incomplete = new ArrayList<>();
        for (JsonNode entry : files) {
            String newPath = entry.path("filename").asText();
            FilePaths paths = new FilePaths(entry.path("previous_filename").asText(newPath), newPath);
            String status = entry.path("status").asText("modified");
            boolean newFile = status.equals("added");
            boolean deletedFile = status.equals("removed");
            String copyOrRename = status.equals("renamed") ? "rename" : status.equals("copied") ? "copy" : null;
            String aMode = newFile ? "" : orRegular(resolver.mode(paths.oldPath, fromRef));
            String bMode = deletedFile ? "" : orRegular(resolver.mode(paths.newPath, toRef));
            String patch = entry.path("patch").asText("");

            appendHeader(out, paths, aMode, bMode, newFile, deletedFile, copyOrRename);
            if (patch.startsWith("@@")) {
                appendHunks(out, paths, newFile, deletedFile, GitPatchContent.raw(patch));
                continue;
            }
            boolean noLineChanges = (copyOrRename != null || status.equals("changed"))
                    && entry.path("changes").asInt(-1) == 0;
            if (noLineChanges) {
                // a pure rename/copy/mode change, or a binary file changed as well: GitHub reports both alike
                appendIfContentChanged(
                        out,
                        incomplete,
                        fetcher,
                        fromRef,
                        toRef,
                        paths,
                        entry.path("sha").asText(null),
                        aMode,
                        bMode);
            } else {
                fill(out, incomplete, fetcher, fromRef, toRef, paths, newFile, deletedFile, aMode, bMode);
            }
        }
        return new UnifiedDiff(out.toString(), incomplete);
    }

    /**
     * Fills the {@code Binary files ... differ} sections of a git unified diff (as GitHub's diff media type returns it)
     * with their content; everything else is kept byte for byte.
     * <p>
     * The diff is scanned in place: only the header lines of each file section become strings, and the output is only
     * built once a section is filled. Without such a section the result takes one copy of the diff, otherwise about
     * two (the builder and its string) on top of the input.
     *
     * @param diff the diff, as returned by the provider
     * @param fetcher reads the content of binary files
     */
    public static UnifiedDiff fillBinaryFiles(byte[] diff, ContentFetcher fetcher, String fromRef, String toRef) {
        CharSequence text = new RawText(diff);
        StringBuilder out = null;
        List<String> incomplete = new ArrayList<>();
        for (int start = 0, end; start < diff.length; start = end) {
            end = sectionEnd(diff, start);
            Section parsed = Section.parse(diff, start, end);
            if (parsed == null) {
                if (out != null) {
                    out.append(text, start, end);
                }
                continue;
            }
            if (out == null) {
                out = new StringBuilder(diff.length + 1024);
                out.append(text, 0, start);
            }
            int mark = out.length();
            List<String> lines = parsed.lines;
            for (int i = 0; i < lines.size(); i++) {
                if (i != parsed.binaryLine && !lines.get(i).startsWith("index ")) {
                    out.append(lines.get(i)).append('\n');
                }
            }
            boolean filled = fetcher != null && !SUBMODULE.equals(parsed.oldMode)
                    && !SUBMODULE.equals(parsed.newMode)
                    && appendContent(
                            out,
                            fetcher,
                            fromRef,
                            toRef,
                            new FilePaths(parsed.oldPath, parsed.newPath),
                            parsed.newFile,
                            parsed.deletedFile);
            if (!filled) {
                out.setLength(mark);
                out.append(text, start, end);
                incomplete.add(parsed.deletedFile ? parsed.oldPath : parsed.newPath);
            }
        }
        return new UnifiedDiff(out == null ? new String(diff, GitPatchContent.RAW) : out.toString(), incomplete);
    }

    /**
     * Like {@link #fillBinaryFiles(byte[], ContentFetcher, String, String)} for a diff already decoded as Unicode text.
     */
    public static UnifiedDiff fillBinaryFiles(String diff, ContentFetcher fetcher, String fromRef, String toRef) {
        return fillBinaryFiles(diff.getBytes(StandardCharsets.UTF_8), fetcher, fromRef, toRef);
    }

    /** An empty diff must come from an empty array, never from a missing or malformed field. */
    private static void requireArray(JsonNode node, String name) {
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException(
                    "Expected a JSON array of " + name + ", got " + (node == null ? "null" : node.getNodeType()));
        }
    }

    /** The Unicode paths of a file (to read its content and report it) and their raw form (to write the diff). */
    private static final class FilePaths {
        final String oldPath;
        final String newPath;
        final String rawOld;
        final String rawNew;

        FilePaths(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
            this.rawOld = GitPatchContent.raw(oldPath);
            this.rawNew = GitPatchContent.raw(newPath);
        }
    }

    /**
     * For an entry without line changes: nothing more when the content is the same on both sides, a patch when it
     * differs. The old content is compared with {@code newBlobId} (GitHub's {@code sha}) when known, otherwise with the
     * new content. Content that cannot be read makes the file incomplete; its header alone still applies.
     */
    private static void appendIfContentChanged(
            StringBuilder out,
            List<String> incomplete,
            ContentFetcher fetcher,
            String fromRef,
            String toRef,
            FilePaths paths,
            String newBlobId,
            String aMode,
            String bMode) {
        if (fetcher == null || SUBMODULE.equals(aMode) || SUBMODULE.equals(bMode) || fromRef == null
                || toRef == null) {
            incomplete.add(paths.newPath);
            return;
        }
        byte[] oldContent = fetcher.fetch(paths.oldPath, fromRef);
        if (oldContent == null) {
            incomplete.add(paths.newPath);
            return;
        }
        if (newBlobId != null && !newBlobId.isEmpty() && GitPatchContent.blobId(oldContent).equals(newBlobId)) {
            return;
        }
        byte[] newContent = fetcher.fetch(paths.newPath, toRef);
        if (newContent == null) {
            incomplete.add(paths.newPath);
            return;
        }
        if (!Arrays.equals(oldContent, newContent)) {
            appendPatch(out, paths, oldContent, newContent, false, false);
        }
    }

    private static void appendHeader(
            StringBuilder out,
            FilePaths paths,
            String aMode,
            String bMode,
            boolean newFile,
            boolean deletedFile,
            String copyOrRename) {
        out.append(DIFF_GIT).append("a/").append(paths.rawOld).append(" b/").append(paths.rawNew).append('\n');
        if (newFile) {
            out.append("new file mode ").append(orRegular(bMode)).append('\n');
        } else if (deletedFile) {
            out.append("deleted file mode ").append(orRegular(aMode)).append('\n');
        } else if (!aMode.isEmpty() && !bMode.isEmpty() && !aMode.equals(bMode)) {
            out.append("old mode ").append(aMode).append('\n');
            out.append("new mode ").append(bMode).append('\n');
        }
        if (copyOrRename != null) {
            out.append(copyOrRename).append(" from ").append(paths.rawOld).append('\n');
            out.append(copyOrRename).append(" to ").append(paths.rawNew).append('\n');
        }
    }

    private static void appendHunks(
            StringBuilder out,
            FilePaths paths,
            boolean newFile,
            boolean deletedFile,
            String rawHunks) {
        out.append("--- ").append(newFile ? "/dev/null" : "a/" + paths.rawOld).append('\n');
        out.append("+++ ").append(deletedFile ? "/dev/null" : "b/" + paths.rawNew).append('\n');
        out.append(rawHunks);
        if (!rawHunks.endsWith("\n")) {
            out.append('\n');
        }
    }

    private static void fill(
            StringBuilder out,
            List<String> incomplete,
            ContentFetcher fetcher,
            String fromRef,
            String toRef,
            FilePaths paths,
            boolean newFile,
            boolean deletedFile,
            String aMode,
            String bMode) {
        if (fetcher != null && !SUBMODULE.equals(aMode) && !SUBMODULE.equals(bMode)
                && appendContent(out, fetcher, fromRef, toRef, paths, newFile, deletedFile)) {
            return;
        }
        out.append("Binary files ")
                .append(newFile ? "/dev/null" : "a/" + paths.rawOld)
                .append(" and ")
                .append(deletedFile ? "/dev/null" : "b/" + paths.rawNew)
                .append(" differ\n");
        incomplete.add(deletedFile ? paths.oldPath : paths.newPath);
    }

    /** Appends the full content of a file in patch form; false (nothing appended) when it cannot be read. */
    private static boolean appendContent(
            StringBuilder out,
            ContentFetcher fetcher,
            String fromRef,
            String toRef,
            FilePaths paths,
            boolean newFile,
            boolean deletedFile) {
        byte[] oldContent = null;
        if (!newFile) {
            oldContent = fromRef == null ? null : fetcher.fetch(paths.oldPath, fromRef);
            if (oldContent == null) {
                return false;
            }
        }
        byte[] newContent = null;
        if (!deletedFile) {
            newContent = toRef == null ? null : fetcher.fetch(paths.newPath, toRef);
            if (newContent == null) {
                return false;
            }
        }
        appendPatch(out, paths, oldContent, newContent, newFile, deletedFile);
        return true;
    }

    /**
     * Text on both sides (readable, and maskable by consumers) as one hunk replacing the whole file; anything else as a
     * git binary patch.
     */
    private static void appendPatch(
            StringBuilder out,
            FilePaths paths,
            byte[] oldContent,
            byte[] newContent,
            boolean newFile,
            boolean deletedFile) {
        boolean text = (newFile || GitPatchContent.isText(oldContent))
                && (deletedFile || GitPatchContent.isText(newContent));
        if (text) {
            GitPatchContent.appendTextHunk(
                    out,
                    newFile ? "/dev/null" : "a/" + paths.rawOld,
                    deletedFile ? "/dev/null" : "b/" + paths.rawNew,
                    oldContent,
                    newContent);
        } else {
            GitPatchContent.appendBinaryPatch(out, oldContent, newContent);
        }
    }

    private static String orRegular(String mode) {
        return mode == null || mode.isEmpty() || mode.equals("0") ? REGULAR_FILE : mode;
    }

    private static final byte[] SECTION_START = ("\n" + DIFF_GIT).getBytes(StandardCharsets.US_ASCII);

    /**
     * The end of the file section starting at {@code start}: just after the newline before the next {@code diff --git}
     * line, or the end of the diff. Text before the first {@code diff --git} line is its own section.
     */
    static int sectionEnd(byte[] diff, int start) {
        int last = diff.length - SECTION_START.length;
        for (int i = start; i <= last; i++) {
            if (diff[i] == '\n' && matches(diff, i, SECTION_START)) {
                return i + 1;
            }
        }
        return diff.length;
    }

    private static boolean matches(byte[] diff, int at, byte[] expected) {
        return matches(diff, at, expected, 0);
    }

    /** Whether {@code diff} has the bytes of {@code expected} from {@code skip} on at {@code at}. */
    private static boolean matches(byte[] diff, int at, byte[] expected, int skip) {
        int length = expected.length - skip;
        if (at + length > diff.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (diff[at + i] != expected[skip + i]) {
                return false;
            }
        }
        return true;
    }

    /** A view of raw diff bytes as text, one char per byte (see {@link GitPatchContent#RAW}), without copying them. */
    private static final class RawText implements CharSequence {
        private final byte[] bytes;

        RawText(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int length() {
            return bytes.length;
        }

        @Override
        public char charAt(int index) {
            return (char) (bytes[index] & 0xff);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new String(bytes, start, end - start, GitPatchContent.RAW);
        }

        @Override
        public String toString() {
            return new String(bytes, GitPatchContent.RAW);
        }
    }

    /** The header of one file section of a git diff, as far as filling a binary file needs it. */
    private static final class Section {
        /** The header lines, as raw text without their newline. */
        final List<String> lines = new ArrayList<>();
        int binaryLine = -1;
        String oldPath;
        String newPath;
        String oldMode = "";
        String newMode = "";
        boolean newFile;
        boolean deletedFile;

        /**
         * The section of {@code diff} from {@code start} to {@code end} when it is a {@code Binary files ... differ}
         * section to fill, otherwise null. Only its header is read: lines up to the first content line ({@code @@},
         * {@code ---}, {@code GIT binary patch}) means the section has content already, and is not read further.
         */
        static Section parse(byte[] diff, int start, int end) {
            if (!matches(diff, start, SECTION_START, 1)) {
                return null;
            }
            Section section = new Section();
            for (int lineStart = start; lineStart < end;) {
                int newline = lineStart;
                while (newline < end && diff[newline] != '\n') {
                    newline++;
                }
                String line = new String(diff, lineStart, newline - lineStart, GitPatchContent.RAW);
                lineStart = newline + 1;
                if (section.lines.isEmpty()) {
                    section.lines.add(line);
                    String[] paths = diffGitPaths(GitPatchContent.unicode(line.substring(DIFF_GIT.length())));
                    if (paths != null) {
                        section.oldPath = paths[0];
                        section.newPath = paths[1];
                    }
                    continue;
                }
                if (line.startsWith("@@") || line.startsWith("--- ") || line.startsWith("GIT binary patch")) {
                    return null; // has content already
                }
                section.lines.add(line);
                section.header(line, section.lines.size() - 1);
            }
            return section.binaryLine < 0 || section.oldPath == null || section.newPath == null ? null : section;
        }

        private void header(String line, int index) {
            if (line.startsWith("new file mode ")) {
                newFile = true;
                newMode = line.substring("new file mode ".length());
            } else if (line.startsWith("deleted file mode ")) {
                deletedFile = true;
                oldMode = line.substring("deleted file mode ".length());
            } else if (line.startsWith("old mode ")) {
                oldMode = line.substring("old mode ".length());
            } else if (line.startsWith("new mode ")) {
                newMode = line.substring("new mode ".length());
            } else if (line.startsWith("rename from ") || line.startsWith("copy from ")) {
                oldPath = unquote(GitPatchContent.unicode(line.substring(line.indexOf(" from ") + " from ".length())));
            } else if (line.startsWith("rename to ") || line.startsWith("copy to ")) {
                newPath = unquote(GitPatchContent.unicode(line.substring(line.indexOf(" to ") + " to ".length())));
            } else if (line.startsWith("index ") && oldMode.isEmpty() && newMode.isEmpty()) {
                String[] parts = line.split(" ");
                if (parts.length == 3) {
                    oldMode = parts[2];
                    newMode = parts[2];
                }
            } else if (line.startsWith("Binary files ") && line.endsWith(" differ")) {
                binaryLine = index;
            }
        }
    }

    /**
     * The old and new paths of a {@code diff --git} line ({@code a/<old> b/<new>}, either possibly C-quoted), or null
     * when ambiguous (unquoted paths with spaces that differ; the rename lines give them then).
     */
    static String[] diffGitPaths(String rest) {
        if (rest.startsWith("\"")) {
            int end = closingQuote(rest, 1);
            if (end < 0) {
                return null;
            }
            String oldPath = stripPrefix(unquote(rest.substring(0, end + 1)), "a/");
            String newPath = stripPrefix(unquote(rest.substring(end + 1).trim()), "b/");
            return new String[] { oldPath, newPath };
        }
        if (rest.endsWith("\"")) {
            int start = rest.lastIndexOf(" \"");
            if (start < 0) {
                return null;
            }
            return new String[] {
                    stripPrefix(rest.substring(0, start), "a/"),
                    stripPrefix(unquote(rest.substring(start + 1)), "b/") };
        }
        // "a/X b/X": the same path on both sides
        if ((rest.length() - 5) % 2 == 0 && rest.startsWith("a/")) {
            int length = (rest.length() - 5) / 2;
            String oldPath = rest.substring(2, 2 + length);
            if (rest.substring(2 + length).equals(" b/" + oldPath)) {
                return new String[] { oldPath, oldPath };
            }
        }
        int separator = rest.indexOf(" b/");
        if (separator > 0 && separator == rest.lastIndexOf(" b/")) {
            return new String[] { stripPrefix(rest.substring(0, separator), "a/"), rest.substring(separator + 3) };
        }
        return null;
    }

    private static int closingQuote(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == '\\') {
                i++;
            } else if (text.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String stripPrefix(String path, String prefix) {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    /** Undoes git's C-style path quoting ({@code "caf\303\251"}); unquoted paths are returned as is. */
    static String unquote(String path) {
        if (path.length() < 2 || !path.startsWith("\"") || !path.endsWith("\"")) {
            return path;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 1; i < path.length() - 1; i++) {
            char c = path.charAt(i);
            if (c != '\\' || i + 1 >= path.length() - 1) {
                if (c < 0x80) {
                    bytes.write(c);
                } else {
                    int length = Character.isHighSurrogate(c) && i + 1 < path.length() - 1
                            && Character.isLowSurrogate(path.charAt(i + 1)) ? 2 : 1;
                    byte[] encoded = path.substring(i, i + length).getBytes(StandardCharsets.UTF_8);
                    bytes.write(encoded, 0, encoded.length);
                    i += length - 1;
                }
                continue;
            }
            char e = path.charAt(++i);
            if (e >= '0' && e <= '7' && i + 2 < path.length() - 1) {
                bytes.write(Integer.parseInt(path.substring(i, i + 3), 8));
                i += 2;
            } else {
                bytes.write(escaped(e));
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static char escaped(char e) {
        switch (e) {
            case 'n':
                return '\n';
            case 't':
                return '\t';
            case 'r':
                return '\r';
            case 'a':
                return 7;
            case 'b':
                return '\b';
            case 'f':
                return '\f';
            case 'v':
                return 11;
            default:
                return e;
        }
    }
}
