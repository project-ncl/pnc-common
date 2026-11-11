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

/**
 * Reads the content of a file at a revision of the repository a diff comes from.
 */
@FunctionalInterface
public interface ContentFetcher {

    /**
     * The whole content is kept in memory while it is written into the diff (deflated and base85-encoded for binary
     * files), so implementations should bound it: return null for a file over the caller's size limit or memory
     * budget, and the file is reported as incomplete instead.
     *
     * @param path the path, as Unicode
     * @param ref the revision
     * @return the file content, or null when it cannot be read or is over the size limit
     */
    byte[] fetch(String path, String ref);
}
