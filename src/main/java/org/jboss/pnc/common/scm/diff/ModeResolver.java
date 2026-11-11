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
 * Looks up the git file mode (e.g. {@code 100644}, {@code 100755}, {@code 120000}) of a path at a revision, for
 * provider APIs that do not report modes.
 */
@FunctionalInterface
public interface ModeResolver {

    /** Every file is a regular file. */
    ModeResolver REGULAR = (path, ref) -> UnifiedDiffBuilder.REGULAR_FILE;

    /**
     * @return the mode, or null when unknown
     */
    String mode(String path, String ref);
}
