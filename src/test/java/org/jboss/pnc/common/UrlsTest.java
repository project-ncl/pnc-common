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
package org.jboss.pnc.common;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class UrlsTest {

    @Test
    public void shouldGetHostAndPathOnly() throws MalformedURLException {
        String url = "https://github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "https://github.com:80/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "https://github.com:85/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "ssh://git@github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git+ssh://git@github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git+ssh://git@github.com:22/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git+ssh://github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "ssh://github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git://github.com/project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git@github.com:project-ncl/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/pnc.git");

        url = "git@github.com:project-ncl/subgroup/pnc.git";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("github.com/project-ncl/subgroup/pnc.git");

        url = "project-ncl";
        assertThat(Urls.keepHostAndPathOnly(url)).isEqualTo("project-ncl");

        url = "gitserver.host.com:80/productization/github.com/jboss-modules.git";
        assertThat(Urls.keepHostAndPathOnly(url))
                .isEqualTo("gitserver.host.com/productization/github.com/jboss-modules.git");
    }

    @Test
    public void shouldReplaceHostInUrl() throws MalformedURLException {
        String url = "http://example.com/test?here=no&hi=yes";
        String host = "https://localhost:8080";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("https://localhost:8080/test?here=no&hi=yes");

        url = "https://test.mu";
        host = "http://localhost:1234";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("http://localhost:1234");

        url = "https://test.mu/hi";
        host = "http://localhost:1234";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("http://localhost:1234/hi");

        url = "https://test.mu:1234/hi";
        host = "http://localhost:81";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("http://localhost:81/hi");
    }

    @Test
    public void shouldReplaceHostInUrlWithDefaultPort() throws MalformedURLException {
        String url = "http://example.com/test?here=no&hi=yes";
        String host = "https://localhost";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("https://localhost/test?here=no&hi=yes");

        host = "http://localhost";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("http://localhost/test?here=no&hi=yes");

        url = "https://test.mu:1234/hi";
        host = "http://localhost";
        assertThat(Urls.replaceHostInUrl(url, host)).isEqualTo("http://localhost/hi");
    }

    @Test
    public void shouldThrowExceptionReplaceMalformedHostInUrl() {
        final String url = "not-a-valid-url";
        final String host = "https://localhost";
        assertThatExceptionOfType(MalformedURLException.class).isThrownBy(() -> Urls.replaceHostInUrl(url, host));

        final String url2 = "http://example.com:123";
        final String host2 = "not-a-valid-url";
        assertThatExceptionOfType(MalformedURLException.class).isThrownBy(() -> Urls.replaceHostInUrl(url2, host2));
    }

}
