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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Rebuilds diffs the way git providers' APIs return them, with the left-out content filled in, and checks with
 * {@code git apply} that they recreate the target tree exactly.
 */
class UnifiedDiffBuilderTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** gitlab.cee leaves out per-file patches over ~200 KiB. */
    private static final int PATCH_LIMIT = 200 * 1024;

    @TempDir
    Path tmp;

    private Path repo;
    private String base;
    private String head;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(gitAvailable(), "git is required");
        repo = tmp.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init", "-q", "-b", "main");
        git(repo, "config", "core.quotepath", "true");

        // upstream sources
        write("build.gradle", "plugins { id 'java' }\nversion = '1.70'\n");
        write("pom.xml", "<project>\n<version>1.70</version>\n</project>\n");
        write("big-modified.txt", lines("upstream line ", 9000));
        write("gradlew", "#!/bin/sh\necho gradle\n");
        writeBytes("lib/old.jar", randomBytes(3000, 1));
        writeBytes("lib/changed.bin", randomBytes(2000, 2));
        writeBytes("lib/moved.bin", randomBytes(1500, 5));
        writeBytes("lib/to-rename.bin", randomBytes(3000, 8));
        write("obsolete.txt", "to delete\n");
        write("big-deleted.txt", lines("old line ", 20000)); // > 200 KiB patch, deleted text file
        writeBytes("latin1.txt", "caf\u00e9\n".getBytes(StandardCharsets.ISO_8859_1)); // text, not UTF-8
        commit("upstream");
        base = git(repo, "rev-parse", "HEAD");

        // what alignment and the groovy manipulators add or change
        write("build.gradle", "plugins { id 'java' }\nversion = '1.70.0.redhat-00001'\napply from: 'gme.gradle'\n");
        write("gme.gradle", "// GME\nallprojects { }\n");
        write("manipulation.json", "{\n  \"version\": \"1.70.0.redhat-00001\"\n}\n");
        write("groovy/allow-insecure-repos-gme.groovy", "println 'é ü – unicode'\r\nprintln 'crlf'\r\n");
        write("target/pom-manip-ext-marker.txt", "");
        write("generated/SqlLexer.java", lines("// generated lexer line ", 12000)); // > 200 KiB, new text file
        write("big-modified.txt", lines("aligned line ", 9000)); // > 200 KiB patch, modified text file
        writeBytes("lib/new.jar", randomBytes(5000, 3)); // new binary
        writeBytes("lib/with space.bin", randomBytes(700, 6)); // new binary, space in the name
        writeBytes("lib/café.bin", randomBytes(600, 7)); // new binary, quoted name
        writeBytes("lib/changed.bin", randomBytes(2100, 4)); // modified binary
        Files.move(repo.resolve("lib/moved.bin"), repo.resolve("lib/renamed.bin")); // renamed binary
        byte[] renamedAndChanged = Files.readAllBytes(repo.resolve("lib/to-rename.bin"));
        renamedAndChanged[100] ^= 0x55; // a few bytes changed: still detected as a rename
        renamedAndChanged[2000] ^= 0x55;
        Files.delete(repo.resolve("lib/to-rename.bin"));
        writeBytes("lib/renamed-changed.bin", renamedAndChanged); // renamed and modified binary
        Files.delete(repo.resolve("lib/old.jar")); // deleted binary
        Files.delete(repo.resolve("obsolete.txt"));
        Files.delete(repo.resolve("big-deleted.txt"));
        writeBytes("latin1.txt", "caf\u00e9 cr\u00e8me\n".getBytes(StandardCharsets.ISO_8859_1));
        Files.setPosixFilePermissions(repo.resolve("gradlew"), PosixFilePermissions.fromString("rwxr-xr-x"));
        commit("Reqour");
        head = git(repo, "rev-parse", "HEAD");
    }

    @Test
    void gitlabDiffIsFilledAndRecreatesTheTree() throws Exception {
        UnifiedDiff diff = UnifiedDiffBuilder.fromGitlab(gitlabLikeDiffs(), this::fetch, base, head);

        assertThat(diff.getIncompleteFiles()).isEmpty();
        assertThat(diff.getContent()).contains("GIT binary patch")
                .contains(
                        "diff --git a/target/pom-manip-ext-marker.txt b/target/pom-manip-ext-marker.txt\nnew file mode")
                .contains("+// generated lexer line 11999")
                // text over GitLab's limit: readable whole-file hunks, not binary patches
                .contains("--- a/big-modified.txt\n+++ b/big-modified.txt\n@@ -1,9000 +1,9000 @@\n-upstream line 0\n")
                .contains("+aligned line 8999\n")
                .contains("--- a/big-deleted.txt\n+++ /dev/null\n@@ -1,20000 +0,0 @@\n-old line 0\n");
        assertRecreatesTheTree(diff.getBytes());
    }

    @Test
    void gitlabDiffWithoutFetcherReportsTheLeftOutFiles() throws Exception {
        UnifiedDiff diff = UnifiedDiffBuilder.fromGitlab(gitlabLikeDiffs(), null, base, head);

        assertThat(diff.getIncompleteFiles()).containsExactlyInAnyOrder(
                "generated/SqlLexer.java",
                "big-modified.txt",
                "big-deleted.txt",
                "latin1.txt",
                "lib/new.jar",
                "lib/with space.bin",
                "lib/café.bin",
                "lib/changed.bin",
                "lib/moved.bin",
                "lib/renamed.bin",
                "lib/to-rename.bin",
                "lib/renamed-changed.bin",
                "lib/old.jar");
    }

    @Test
    void githubRawDiffBinariesAreFilledAndRecreateTheTree() throws Exception {
        // GitHub's diff media type: a plain 'git diff', binary files as "Binary files ... differ"
        byte[] raw = gitBytes(repo, "diff", "-M", base, head);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).contains("Binary files ")
                .contains("caf\u00e9 cr\u00e8me");

        UnifiedDiff diff = UnifiedDiffBuilder.fillBinaryFiles(raw, this::fetch, base, head);

        assertThat(diff.getIncompleteFiles()).isEmpty();
        assertThat(diff.getContent()).doesNotContain("Binary files ").contains("rename from lib/moved.bin");
        assertRecreatesTheTree(diff.getBytes());
    }

    @Test
    void githubRawDiffWithUnreadableFilesKeepsThem() throws Exception {
        byte[] raw = gitBytes(repo, "diff", "-M", base, head);

        UnifiedDiff diff = UnifiedDiffBuilder.fillBinaryFiles(raw, (path, ref) -> null, base, head);

        assertThat(diff.getBytes()).isEqualTo(raw);
        assertThat(diff.getIncompleteFiles()).contains("lib/new.jar", "lib/café.bin", "lib/with space.bin");
    }

    @Test
    void githubFilesAreFilledAndRecreateTheTree() throws Exception {
        ModeResolver modes = (path, ref) -> {
            try {
                String entry = git(repo, "ls-tree", ref, "--", path);
                return entry.isEmpty() ? null : entry.substring(0, entry.indexOf(' '));
            } catch (Exception e) {
                return null;
            }
        };

        UnifiedDiff diff = UnifiedDiffBuilder.fromGithub(githubLikeFiles(), this::fetch, modes, base, head);

        assertThat(diff.getIncompleteFiles()).isEmpty();
        assertThat(diff.getContent()).contains("old mode 100644\nnew mode 100755\n")
                .contains("rename from lib/to-rename.bin\nrename to lib/renamed-changed.bin\nindex ");
        assertRecreatesTheTree(diff.getBytes());
    }

    @Test
    void githubRenamesWithoutLineChangesAreComparedByContent() throws Exception {
        byte[] oldContent = { 0, 1, 2 };
        byte[] changedContent = { 0, 1, 3 };
        String entry = "[{\"filename\":\"b.bin\",\"previous_filename\":\"a.bin\",\"status\":\"renamed\","
                + "\"changes\":0%s}]";
        String header = "diff --git a/a.bin b/b.bin\nrename from a.bin\nrename to b.bin\n";

        // changed binary: the content is written although GitHub reports no changes
        UnifiedDiff changed = UnifiedDiffBuilder.fromGithub(
                JSON.readTree(String.format(entry, ",\"sha\":\"" + GitPatchContent.blobId(changedContent) + "\"")),
                (path, ref) -> path.equals("a.bin") ? oldContent : changedContent,
                null,
                "base",
                "head");
        assertThat(changed.getContent()).isEqualTo(header + GitPatchContent.binaryPatch(oldContent, changedContent));
        assertThat(changed.isComplete()).isTrue();

        // pure rename, known from the sha: only the old side is read
        List<String> fetched = new ArrayList<>();
        UnifiedDiff pure = UnifiedDiffBuilder.fromGithub(
                JSON.readTree(String.format(entry, ",\"sha\":\"" + GitPatchContent.blobId(oldContent) + "\"")),
                (path, ref) -> {
                    fetched.add(path + "@" + ref);
                    return oldContent;
                },
                null,
                "base",
                "head");
        assertThat(pure.getContent()).isEqualTo(header);
        assertThat(pure.isComplete()).isTrue();
        assertThat(fetched).containsExactly("a.bin@base");

        // pure rename without sha: both sides compared
        UnifiedDiff noSha = UnifiedDiffBuilder
                .fromGithub(JSON.readTree(String.format(entry, "")), (path, ref) -> oldContent, null, "base", "head");
        assertThat(noSha.getContent()).isEqualTo(header);
        assertThat(noSha.isComplete()).isTrue();

        // content that cannot be checked: the header still applies, but the diff is not complete
        UnifiedDiff unchecked = UnifiedDiffBuilder
                .fromGithub(JSON.readTree(String.format(entry, "")), null, null, "base", "head");
        assertThat(unchecked.getContent()).isEqualTo(header);
        assertThat(unchecked.getIncompleteFiles()).containsExactly("b.bin");
        UnifiedDiff unreadable = UnifiedDiffBuilder
                .fromGithub(JSON.readTree(String.format(entry, "")), (path, ref) -> null, null, "base", "head");
        assertThat(unreadable.getIncompleteFiles()).containsExactly("b.bin");
    }

    @Test
    void wholeFileTextHunks() {
        StringBuilder out = new StringBuilder();
        GitPatchContent.appendTextHunk(
                out,
                "a/f",
                "b/f",
                "a\nb\n".getBytes(StandardCharsets.UTF_8),
                "c".getBytes(StandardCharsets.UTF_8));
        assertThat(out.toString())
                .isEqualTo("--- a/f\n+++ b/f\n@@ -1,2 +1 @@\n-a\n-b\n+c\n\\ No newline at end of file\n");

        out.setLength(0);
        GitPatchContent.appendTextHunk(out, "a/f", "/dev/null", "x\n".getBytes(StandardCharsets.UTF_8), null);
        assertThat(out.toString()).isEqualTo("--- a/f\n+++ /dev/null\n@@ -1 +0,0 @@\n-x\n");

        out.setLength(0);
        GitPatchContent.appendTextHunk(out, "a/f", "b/f", new byte[0], new byte[0]);
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void rawTextKeepsEveryByte() throws Exception {
        assertThat(GitPatchContent.raw("plain")).isSameAs("plain");
        assertThat(GitPatchContent.raw("café").getBytes(StandardCharsets.ISO_8859_1))
                .isEqualTo("café".getBytes(StandardCharsets.UTF_8));
        assertThat(GitPatchContent.unicode(GitPatchContent.raw("é😀"))).isEqualTo("é😀");

        byte[] latin1 = "caf\u00e9\n".getBytes(StandardCharsets.ISO_8859_1);
        UnifiedDiff diff = UnifiedDiffBuilder.fillBinaryFiles(latin1, null, null, null);
        assertThat(diff.getBytes()).isEqualTo(latin1);
        java.io.ByteArrayOutputStream written = new java.io.ByteArrayOutputStream();
        diff.writeTo(written);
        assertThat(written.toByteArray()).isEqualTo(latin1);
        assertThat(diff.size()).isEqualTo(latin1.length);
    }

    @Test
    void largeTextOnlyRawDiffsAreKeptByteForByte() {
        byte[] raw = largeTextSection("big.txt", 200_000).getBytes(StandardCharsets.ISO_8859_1);

        UnifiedDiff diff = UnifiedDiffBuilder.fillBinaryFiles(raw, (path, ref) -> {
            throw new AssertionError("nothing to fetch");
        }, "base", "head");

        assertThat(diff.getBytes()).isEqualTo(raw);
        assertThat(diff.isComplete()).isTrue();
    }

    @Test
    void binarySectionsBetweenLargeTextSectionsAreFilledAndTheRestKept() {
        String before = "preamble\n" + largeTextSection("a.txt", 50_000);
        String binary = "diff --git a/lib/x.bin b/lib/x.bin\nnew file mode 100644\nindex 0000000..1234567\n"
                + "Binary files /dev/null and b/lib/x.bin differ\n";
        String unreadable = "diff --git a/lib/y.bin b/lib/y.bin\nindex 1111111..2222222 100644\n"
                + "Binary files a/lib/y.bin and b/lib/y.bin differ\n";
        String after = largeTextSection("b.txt", 50_000);
        // the last section without its final newline
        String raw = before + binary + after + unreadable + after.substring(0, after.length() - 1);
        byte[] content = { 0, 1, 2 };

        UnifiedDiff diff = UnifiedDiffBuilder.fillBinaryFiles(
                raw.getBytes(StandardCharsets.ISO_8859_1),
                (path, ref) -> path.equals("lib/x.bin") && ref.equals("head") ? content : null,
                "base",
                "head");

        assertThat(diff.getRawContent()).isEqualTo(
                before + "diff --git a/lib/x.bin b/lib/x.bin\nnew file mode 100644\n"
                        + GitPatchContent.binaryPatch(null, content) + after + unreadable
                        + after.substring(0, after.length() - 1));
        assertThat(diff.getIncompleteFiles()).containsExactly("lib/y.bin");
    }

    /** A text file section with short lines, some of them not UTF-8. */
    private static String largeTextSection(String path, int lines) {
        StringBuilder section = new StringBuilder("diff --git a/" + path + " b/" + path + "\n")
                .append("index 1111111..2222222 100644\n--- a/")
                .append(path)
                .append("\n+++ b/")
                .append(path)
                .append("\n@@ -1,")
                .append(lines)
                .append(" +1,")
                .append(lines)
                .append(" @@\n");
        for (int i = 0; i < lines; i++) {
            section.append(i % 2 == 0 ? '-' : '+').append(i % 7 == 0 ? "caf\u00e9 " : "x ").append(i).append('\n');
        }
        return section.toString();
    }

    @Test
    void unicodePathsFromJsonAreWrittenAsUtf8() throws Exception {
        JsonNode diffs = JSON.readTree(
                "[{\"old_path\":\"café.txt\",\"new_path\":\"café.txt\",\"diff\":\"@@ -1 +1 @@\\n-é\\n+è\\n\"}]");

        UnifiedDiff diff = UnifiedDiffBuilder.fromGitlab(diffs, null, null, null);

        assertThat(new String(diff.getBytes(), StandardCharsets.UTF_8)).isEqualTo(
                "diff --git a/café.txt b/café.txt\n--- a/café.txt\n+++ b/café.txt\n@@ -1 +1 @@\n-é\n+è\n");
        assertThat(diff.getContent()).isEqualTo(new String(diff.getBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void gitlabHeadersAreRebuilt() throws Exception {
        JsonNode diffs = JSON.readTree(
                "[" + "{\"old_path\":\"pom.xml\",\"new_path\":\"pom.xml\",\"a_mode\":\"100644\",\"b_mode\":\"100644\","
                        + "\"diff\":\"@@ -1 +1 @@\\n-<version>3.8.3</version>\\n+<version>3.8.3.rhlw-00014</version>\\n\"},"
                        + "{\"old_path\":\"new.txt\",\"new_path\":\"new.txt\",\"a_mode\":\"0\",\"b_mode\":\"100644\","
                        + "\"new_file\":true,\"diff\":\"@@ -0,0 +1 @@\\n+hello\"},"
                        + "{\"old_path\":\"gone.txt\",\"new_path\":\"gone.txt\",\"a_mode\":\"100644\",\"b_mode\":\"0\","
                        + "\"deleted_file\":true,\"diff\":\"@@ -1 +0,0 @@\\n-bye\\n\"},"
                        + "{\"old_path\":\"a.txt\",\"new_path\":\"b.txt\",\"a_mode\":\"100644\",\"b_mode\":\"100644\","
                        + "\"renamed_file\":true,\"diff\":\"\"},"
                        + "{\"old_path\":\"run.sh\",\"new_path\":\"run.sh\",\"a_mode\":\"100644\",\"b_mode\":\"100755\","
                        + "\"diff\":\"\"},"
                        + "{\"old_path\":\"logo.png\",\"new_path\":\"logo.png\",\"a_mode\":\"100644\",\"b_mode\":\"100644\","
                        + "\"diff\":\"\"},"
                        + "{\"old_path\":\"target/marker.txt\",\"new_path\":\"target/marker.txt\","
                        + "\"a_mode\":\"0\",\"b_mode\":\"100644\",\"new_file\":true,\"too_large\":false,\"diff\":\"\"}"
                        + "]");

        UnifiedDiff diff = UnifiedDiffBuilder.fromGitlab(diffs, null, null, null);

        assertThat(diff.getContent()).isEqualTo(
                "diff --git a/pom.xml b/pom.xml\n" + "--- a/pom.xml\n" + "+++ b/pom.xml\n" + "@@ -1 +1 @@\n"
                        + "-<version>3.8.3</version>\n" + "+<version>3.8.3.rhlw-00014</version>\n"
                        + "diff --git a/new.txt b/new.txt\n" + "new file mode 100644\n" + "--- /dev/null\n"
                        + "+++ b/new.txt\n" + "@@ -0,0 +1 @@\n" + "+hello\n" + "diff --git a/gone.txt b/gone.txt\n"
                        + "deleted file mode 100644\n" + "--- a/gone.txt\n" + "+++ /dev/null\n" + "@@ -1 +0,0 @@\n"
                        + "-bye\n" + "diff --git a/a.txt b/b.txt\n" + "rename from a.txt\n" + "rename to b.txt\n"
                        + "diff --git a/run.sh b/run.sh\n" + "old mode 100644\n" + "new mode 100755\n"
                        + "diff --git a/logo.png b/logo.png\n" + "Binary files a/logo.png and b/logo.png differ\n"
                        + "diff --git a/target/marker.txt b/target/marker.txt\n" + "new file mode 100644\n");
        assertThat(diff.getIncompleteFiles()).containsExactly("logo.png");
    }

    @Test
    void diffGitPaths() {
        assertThat(UnifiedDiffBuilder.diffGitPaths("a/x/y.bin b/x/y.bin")).containsExactly("x/y.bin", "x/y.bin");
        assertThat(UnifiedDiffBuilder.diffGitPaths("a/with space b/with space"))
                .containsExactly("with space", "with space");
        assertThat(UnifiedDiffBuilder.diffGitPaths("\"a/caf\\303\\251\" \"b/caf\\303\\251\""))
                .containsExactly("café", "café");
        assertThat(UnifiedDiffBuilder.diffGitPaths("a/old.bin b/new.bin")).containsExactly("old.bin", "new.bin");
    }

    @Test
    void literalsAndBlobIdsMatchGit() throws Exception {
        // as written by 'git diff --binary'
        assertThat(GitPatchContent.literal(new byte[0])).isEqualTo("literal 0\nHcmV?d00001\n");
        assertThat(GitPatchContent.literal(new byte[] { 0, 1, 2 })).isEqualTo("literal 3\nKcmZQzWC8#H2LJ>B\n");
        byte[] content = randomBytes(777, 9);
        Files.write(tmp.resolve("blob"), content);
        assertThat(GitPatchContent.blobId(content)).isEqualTo(git(tmp, "hash-object", tmp.resolve("blob").toString()));
    }

    @Test
    void isTextChecksTheWholeContentInChunks() {
        byte[] large = lines("é ü – line ", 200_000).getBytes(StandardCharsets.UTF_8); // several decode chunks
        assertThat(GitPatchContent.isText(large)).isTrue();

        byte[] invalidAtEnd = java.util.Arrays.copyOf(large, large.length + 1);
        invalidAtEnd[large.length] = (byte) 0xC3; // truncated two-byte sequence
        assertThat(GitPatchContent.isText(invalidAtEnd)).isFalse();

        byte[] invalidInside = large.clone();
        invalidInside[large.length / 2] = (byte) 0xFF;
        assertThat(GitPatchContent.isText(invalidInside)).isFalse();

        assertThat(GitPatchContent.isText(new byte[0])).isTrue();
        assertThat(GitPatchContent.isText(new byte[] { 'a', 0, 'b' })).isFalse();
    }

    @Test
    void newTextFileHunks() {
        assertThat(GitPatchContent.newTextFileHunk("f", "a\nb\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("--- /dev/null\n+++ b/f\n@@ -0,0 +1,2 @@\n+a\n+b\n");
        assertThat(GitPatchContent.newTextFileHunk("f", "a".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("--- /dev/null\n+++ b/f\n@@ -0,0 +1 @@\n+a\n\\ No newline at end of file\n");
        assertThat(GitPatchContent.newTextFileHunk("f", "\n\nx".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("--- /dev/null\n+++ b/f\n@@ -0,0 +1,3 @@\n+\n+\n+x\n\\ No newline at end of file\n");
        assertThat(GitPatchContent.newTextFileHunk("f", "\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("--- /dev/null\n+++ b/f\n@@ -0,0 +1 @@\n+\n");
        assertThat(GitPatchContent.newTextFileHunk("f", new byte[0])).isEmpty();
    }

    @Test
    void inputsThatAreNotArraysAreRejected() throws Exception {
        for (JsonNode node : new JsonNode[] {
                null,
                JSON.readTree("{\"diffs\":[]}"),
                JSON.readTree("{}").path("diffs") }) {
            assertThatThrownBy(() -> UnifiedDiffBuilder.fromGitlab(node, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> UnifiedDiffBuilder.fromGithub(node, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(UnifiedDiffBuilder.fromGitlab(JSON.createArrayNode(), null, null, null).getContent()).isEmpty();
    }

    @Test
    void githubFilesWithoutModeResolverAreRegularFiles() throws Exception {
        JsonNode files = JSON.readTree(
                "[{\"filename\":\"new.txt\",\"status\":\"added\",\"changes\":1,\"patch\":\"@@ -0,0 +1 @@\\n+x\"}]");

        UnifiedDiff diff = UnifiedDiffBuilder.fromGithub(files, null, null, null, "head");

        assertThat(diff.getContent()).isEqualTo(
                "diff --git a/new.txt b/new.txt\nnew file mode 100644\n--- /dev/null\n+++ b/new.txt\n@@ -0,0 +1 @@\n+x\n");
        assertThat(diff.isComplete()).isTrue();
    }

    @Test
    void unifiedDiffValues() {
        assertThat(new UnifiedDiff("", null).getIncompleteFiles()).isEmpty();
        assertThat(new UnifiedDiff("", null).isComplete()).isTrue();
        assertThat(new UnifiedDiff("", List.of("a.bin")).isComplete()).isFalse();
    }

    @Test
    void unquoteKeepsNonAsciiCharacters() {
        assertThat(UnifiedDiffBuilder.unquote("\"a/x y\\tz\"")).isEqualTo("a/x y\tz");
        assertThat(UnifiedDiffBuilder.unquote("\"a/caf\\303\\251\"")).isEqualTo("a/café");
        assertThat(UnifiedDiffBuilder.unquote("\"a/é😀\"")).isEqualTo("a/é😀");
        assertThat(UnifiedDiffBuilder.unquote("plain")).isEqualTo("plain");
    }

    private void assertRecreatesTheTree(byte[] diff) throws Exception {
        Path work = tmp.resolve("apply");
        git(tmp, "clone", "-q", repo.toString(), work.toString());
        git(work, "checkout", "-q", base);
        Path patch = tmp.resolve("filled.diff");
        Files.write(patch, diff);
        git(work, "apply", "--whitespace=nowarn", patch.toString());
        git(work, "add", "-A");
        assertThat(git(work, "write-tree")).isEqualTo(git(repo, "rev-parse", head + "^{tree}"));
    }

    /** As gitlab.cee returns them: no renames, binary files as text, empty diff with too_large for big patches. */
    private ArrayNode gitlabLikeDiffs() throws Exception {
        ArrayNode diffs = JSON.createArrayNode();
        for (String[] change : nameStatus("--no-renames")) {
            String status = change[0];
            String path = change[1];
            ObjectNode entry = diffs.addObject();
            entry.put("old_path", path);
            entry.put("new_path", path);
            entry.put("new_file", status.equals("A"));
            entry.put("deleted_file", status.equals("D"));
            entry.put("renamed_file", false);
            entry.put("a_mode", status.equals("A") ? "0" : mode(path, base));
            entry.put("b_mode", status.equals("D") ? "0" : mode(path, head));
            String patch = git(repo, "diff", "--no-renames", base, head, "--", path) + "\n";
            int hunk = patch.indexOf("\n@@");
            if (!GitPatchContent.isText(gitBytes(repo, "diff", "--no-renames", base, head, "--", path))) {
                entry.put("diff", ""); // not UTF-8: left out
                entry.put("too_large", true);
            } else if (patch.contains("\nBinary files ")) {
                entry.put("diff", patch.substring(patch.indexOf("Binary files ")));
            } else if (hunk < 0) {
                entry.put("diff", "");
            } else if (patch.length() - hunk > PATCH_LIMIT) {
                entry.put("diff", "");
                entry.put("too_large", true);
            } else {
                entry.put("diff", patch.substring(hunk + 1));
            }
        }
        return diffs;
    }

    /** As GitHub returns them: renames, no modes, no patch for binary files or big patches. */
    private ArrayNode githubLikeFiles() throws Exception {
        ArrayNode files = JSON.createArrayNode();
        for (String[] change : nameStatus("-M")) {
            String status = change[0].substring(0, 1);
            ObjectNode entry = files.addObject();
            String path = change[change.length - 1];
            entry.put("filename", path);
            if (status.equals("R")) {
                entry.put("previous_filename", change[1]);
            }
            entry.put(
                    "status",
                    status.equals("A") ? "added"
                            : status.equals("D") ? "removed" : status.equals("R") ? "renamed" : "modified");
            if (!status.equals("D")) {
                entry.put("sha", git(repo, "rev-parse", head + ":" + path));
            }
            String patch = status.equals("R") ? git(repo, "diff", "-M", base, head, "--", change[1], path) + "\n"
                    : git(repo, "diff", base, head, "--", path) + "\n";
            int hunk = patch.indexOf("\n@@");
            entry.put("changes", hunk < 0 ? 0 : 1);
            boolean utf8 = GitPatchContent.isText(patch.getBytes(StandardCharsets.UTF_8))
                    && !patch.contains("\uFFFD");
            if (hunk >= 0 && patch.length() - hunk <= PATCH_LIMIT && utf8) {
                entry.put("patch", patch.substring(hunk + 1, patch.length() - 1));
            }
        }
        return files;
    }

    private List<String[]> nameStatus(String renames) throws Exception {
        List<String[]> changes = new ArrayList<>();
        for (String line : git(repo, "-c", "core.quotepath=false", "diff", renames, "--name-status", base, head)
                .split("\n")) {
            changes.add(line.split("\t"));
        }
        return changes;
    }

    private String mode(String path, String ref) throws Exception {
        String entry = git(repo, "ls-tree", ref, "--", path);
        return entry.substring(0, entry.indexOf(' '));
    }

    private byte[] fetch(String path, String ref) {
        try {
            Process process = new ProcessBuilder("git", "show", ref + ":" + path).directory(repo.toFile()).start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                in.transferTo(out);
            }
            return process.waitFor() == 0 ? out.toByteArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void write(String path, String content) throws Exception {
        writeBytes(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(String path, byte[] content) throws Exception {
        Path file = repo.resolve(path);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private void commit(String message) throws Exception {
        git(repo, "add", "-A");
        git(repo, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-q", "-m", message);
    }

    private static String lines(String prefix, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(prefix).append(i).append('\n');
        }
        return text.toString();
    }

    private static byte[] randomBytes(int size, long seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        bytes[0] = 0; // make sure git sees it as binary
        return bytes;
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] gitBytes(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed");
        }
        return output;
    }

    private static String git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
        return output.endsWith("\n") ? output.substring(0, output.length() - 1) : output;
    }
}
