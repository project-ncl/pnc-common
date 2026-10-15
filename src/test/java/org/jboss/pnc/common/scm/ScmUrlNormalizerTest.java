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
package org.jboss.pnc.common.scm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ScmUrlNormalizerTest {

    @Test
    public void shouldNormalizeAllUrlFormsOfTheSameRepo() {
        // all URL forms of the same repo normalize to host/path (no scheme/userinfo/port/.git/trailing slash)
        String expected = "gitlab.example.com/lw/lw-builds/foo";
        assertThat(ScmUrlNormalizer.normalize("https://gitlab.example.com/lw/lw-builds/foo.git")).isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git+ssh://git@gitlab.example.com/lw/lw-builds/foo.git"))
                .isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/lw-builds/foo.git")).isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("https://gitlab.example.com/lw/lw-builds/foo/")).isEqualTo(expected);
    }

    @Test
    public void shouldDropPortInsteadOfTreatingItAsPath() {
        // host:port is dropped, not treated as an scp path segment
        assertThat(ScmUrlNormalizer.normalize("https://gitlab.example.com:8080/lw/foo.git"))
                .isEqualTo("gitlab.example.com/lw/foo");
    }

    @Test
    public void shouldNormalizeBareHostPathGroupPrefix() {
        // the form used by whitelist config entries: a bare host/group prefix
        assertThat(ScmUrlNormalizer.normalize("gitlab.example.com/lw/lw-builds"))
                .isEqualTo("gitlab.example.com/lw/lw-builds");
        assertThat(ScmUrlNormalizer.normalize("gitlab.com/some/allowed-group/"))
                .isEqualTo("gitlab.com/some/allowed-group");
    }

    @Test
    public void shouldLowerCaseAndTrim() {
        assertThat(ScmUrlNormalizer.normalize("  HTTPS://GitLab.Example.com/LW/Repo.git  "))
                .isEqualTo("gitlab.example.com/lw/repo");
    }

    @Test
    public void shouldReturnEmptyForNullOrBlank() {
        assertThat(ScmUrlNormalizer.normalize(null)).isEmpty();
        assertThat(ScmUrlNormalizer.normalize("   ")).isEmpty();
    }
}
