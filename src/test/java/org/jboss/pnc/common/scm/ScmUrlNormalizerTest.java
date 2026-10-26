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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    public void shouldIgnoreFragment() {
        // scp-like and scheme-based URLs with a '#ref' fragment normalize to the same host/path as without it
        String expected = "gitlab.example.com/lw/lw-builds/foo";
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/lw-builds/foo.git#abc123"))
                .isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/lw-builds/foo.git#feature/x"))
                .isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git+ssh://git@gitlab.example.com/lw/lw-builds/foo.git#abc123"))
                .isEqualTo(expected);
    }

    @Test
    public void shouldLowerCaseAndTrim() {
        assertThat(ScmUrlNormalizer.normalize("  HTTPS://GitLab.Example.com/LW/Repo.git  "))
                .isEqualTo("gitlab.example.com/lw/repo");
    }

    @Test
    public void shouldDropRefFragmentAcrossAllUrlForms() {
        // a '#ref' fragment is a revision selector, not part of the repo identity, so it is dropped on every form
        // (and the '.git' suffix is still stripped even though the fragment follows it)
        String expected = "gitlab.example.com/lw/lw-builds/foo";
        assertThat(ScmUrlNormalizer.normalize("https://gitlab.example.com/lw/lw-builds/foo.git#abc123"))
                .isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git+ssh://git@gitlab.example.com/lw/lw-builds/foo.git#v1.0"))
                .isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/lw-builds/foo.git#abc123"))
                .isEqualTo(expected);
    }

    @Test
    public void shouldReturnEmptyForNullOrBlank() {
        assertThat(ScmUrlNormalizer.normalize(null)).isEmpty();
        assertThat(ScmUrlNormalizer.normalize("   ")).isEmpty();
    }

    @Test
    public void shouldBeLenientAboutTheFragmentWhenNormalizing() {
        // normalize() only cares about host/path, so whatever follows the first '#' is dropped, valid ref or not
        String expected = "gitlab.example.com/lw/foo";
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/foo.git#")).isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/foo.git#bad ref")).isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("git@gitlab.example.com:lw/foo.git#a#b")).isEqualTo(expected);
        assertThat(ScmUrlNormalizer.normalize("https://gitlab.example.com/lw/foo.git#bad ref")).isEqualTo(expected);
    }

    @Test
    public void shouldParseSchemeUrlIntoHostPathAndFragment() {
        ScmUrlNormalizer parsed = ScmUrlNormalizer
                .parse("git+ssh://git@GitLab.Example.com:2222/lw/lw-builds/Foo.git#feature");

        assertThat(parsed.getHost()).isEqualTo("GitLab.Example.com");
        assertThat(parsed.getPath()).isEqualTo("lw/lw-builds/Foo.git");
        assertThat(parsed.getFragment()).isEqualTo("feature");
        assertThat(parsed.normalized()).isEqualTo("gitlab.example.com/lw/lw-builds/foo");
    }

    @Test
    public void shouldParseScpUrlIntoHostPathAndFragment() {
        ScmUrlNormalizer parsed = ScmUrlNormalizer.parse("git@gitlab.example.com:lw/lw-builds/foo.git#feature/x");

        assertThat(parsed.getHost()).isEqualTo("gitlab.example.com");
        assertThat(parsed.getPath()).isEqualTo("lw/lw-builds/foo.git");
        assertThat(parsed.getFragment()).isEqualTo("feature/x");
        assertThat(parsed.normalized()).isEqualTo("gitlab.example.com/lw/lw-builds/foo");
    }

    @Test
    public void shouldParseUrlWithoutFragment() {
        assertThat(ScmUrlNormalizer.parse("https://gitlab.example.com/lw/foo.git").getFragment()).isNull();
        assertThat(ScmUrlNormalizer.parse("git@gitlab.example.com:lw/foo.git").getFragment()).isNull();
    }

    @Test
    public void shouldParseBareHostPath() {
        ScmUrlNormalizer parsed = ScmUrlNormalizer.parse("gitlab.example.com/lw/lw-builds");

        assertThat(parsed.getHost()).isEqualTo("gitlab.example.com");
        assertThat(parsed.getPath()).isEqualTo("lw/lw-builds");
        assertThat(parsed.getFragment()).isNull();
    }

    @Test
    public void shouldParseNullOrBlankToEmpty() {
        for (String url : new String[] { null, "", "   " }) {
            ScmUrlNormalizer parsed = ScmUrlNormalizer.parse(url);
            assertThat(parsed.getHost()).isEmpty();
            assertThat(parsed.getPath()).isEmpty();
            assertThat(parsed.getFragment()).isNull();
            assertThat(parsed.normalized()).isEmpty();
        }
    }

    @Test
    public void shouldNotValidateTheFragmentWhenParsing() {
        // parse() does not decide which refs are acceptable: host and path are still returned and the text after
        // the first '#' is handed back as it is, for the caller to validate
        for (String url : new String[] {
                "git@gitlab.example.com:lw/foo.git#bad ref",
                "https://gitlab.example.com/lw/foo.git#bad ref" }) {
            ScmUrlNormalizer parsed = ScmUrlNormalizer.parse(url);
            assertThat(parsed.getHost()).as(url).isEqualTo("gitlab.example.com");
            assertThat(parsed.getPath()).as(url).isEqualTo("lw/foo.git");
            assertThat(parsed.getFragment()).as(url).isEqualTo("bad ref");
        }
        assertThat(ScmUrlNormalizer.parse("git@gitlab.example.com:lw/foo.git#").getFragment()).isEmpty();
        assertThat(ScmUrlNormalizer.parse("git@gitlab.example.com:lw/foo.git#a#b").getFragment()).isEqualTo("a#b");
    }

    @Test
    public void shouldRejectUnparsableUrlWhenParsing() {
        // the fragment being ignored does not make an unparsable host/path parsable
        assertThatThrownBy(() -> ScmUrlNormalizer.parse("git@evil.net#gitlab.example.com:lw/foo.git"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScmUrlNormalizer.parse("not a url")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNormalizeToTheSameValueAsParse() {
        for (String url : new String[] {
                "https://gitlab.example.com/lw/foo.git#abc",
                "git@gitlab.example.com:lw/foo.git#abc",
                "gitlab.example.com/lw/foo/" }) {
            assertThat(ScmUrlNormalizer.normalize(url)).isEqualTo(ScmUrlNormalizer.parse(url).normalized());
        }
    }
}
