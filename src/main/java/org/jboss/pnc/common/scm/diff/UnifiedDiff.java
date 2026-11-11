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

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * A git unified diff, with the files whose content could not be included (kept as {@code Binary files ... differ}).
 * The diff is kept as raw text, one char per byte (see {@link GitPatchContent#RAW}): {@link #getBytes()} and
 * {@link #writeTo(OutputStream)} give its exact bytes, whatever the encoding of the files in it.
 */
public final class UnifiedDiff {

    private static final int WRITE_CHUNK_CHARS = 64 * 1024;

    private final String content;
    private final List<String> incompleteFiles;

    /**
     * @param content the diff as raw text, one char per byte
     * @param incompleteFiles the paths whose content is not in the diff
     */
    public UnifiedDiff(String content, List<String> incompleteFiles) {
        this.content = content;
        this.incompleteFiles = incompleteFiles == null ? List.of() : List.copyOf(incompleteFiles);
    }

    /** The exact bytes of the diff. */
    public byte[] getBytes() {
        return content.getBytes(GitPatchContent.RAW);
    }

    /** Writes the exact bytes of the diff, in chunks (no copy of the whole diff). */
    public void writeTo(OutputStream out) throws IOException {
        for (int start = 0; start < content.length(); start += WRITE_CHUNK_CHARS) {
            int end = Math.min(content.length(), start + WRITE_CHUNK_CHARS);
            out.write(content.substring(start, end).getBytes(GitPatchContent.RAW));
        }
    }

    /** The size of the diff in bytes. */
    public int size() {
        return content.length();
    }

    /**
     * The diff as text, decoding its bytes as UTF-8: bytes of files in another encoding are replaced. Use
     * {@link #getBytes()} or {@link #writeTo(OutputStream)} for an exact copy.
     */
    public String getContent() {
        return GitPatchContent.unicode(content);
    }

    /** The diff as raw text, one char per byte. */
    public String getRawContent() {
        return content;
    }

    public List<String> getIncompleteFiles() {
        return incompleteFiles;
    }

    /** Whether the content of every file is in the diff. */
    public boolean isComplete() {
        return incompleteFiles.isEmpty();
    }
}
