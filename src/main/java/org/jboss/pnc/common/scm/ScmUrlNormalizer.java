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

import java.net.MalformedURLException;

import org.jboss.pnc.common.Urls;

/**
 * Normalizes an SCM URL to a canonical {@code host/path} form so host + group/subgroup prefixes can be
 * compared regardless of the URL syntax used.
 *
 * <p>
 * This complements {@link GitSCPUrl}, which only parses scp-like URLs ({@code git@host:group/repo.git}).
 * Scheme-based URLs ({@code https://}, {@code git+ssh://}, {@code ssh://}, {@code git://}) together with any
 * embedded userinfo and port are handled here (via {@link Urls#stripProtocolAndPort(String)}); scp-like and
 * bare {@code host[:/]path} forms are delegated to {@link GitSCPUrl}.
 *
 * <p>
 * The result is lower-cased and carries no scheme, userinfo, port, {@code .git} suffix, trailing slash or
 * {@code #ref} fragment. A fragment is a git revision selector, not part of the repository identity this class
 * compares, so it is dropped (and dropping it up front also lets {@link GitSCPUrl} parse scp-like URLs that carry
 * one). For example {@code https://git@gitlab.example.com:8080/lw/lw-builds/foo.git#tag} and
 * {@code git@gitlab.example.com:lw/lw-builds/foo} both normalize to {@code gitlab.example.com/lw/lw-builds/foo}.
 */
public class ScmUrlNormalizer {

    private ScmUrlNormalizer() {
    }

    /**
     * Normalize an SCM URL to its canonical {@code host/path} form. Returns an empty string for a null or
     * blank input.
     *
     * @param url the SCM URL in any supported form (https, git+ssh, ssh, git, scp-like, or bare host/path)
     * @return the normalized {@code host/path}, lower-cased and without scheme, userinfo, port, {@code .git}
     *         suffix, trailing slash or {@code #ref} fragment
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        // A '#ref' fragment is a git revision selector, not part of the host/path identity; drop it before parsing.
        // This also keeps GitSCPUrl happy, whose scp pattern does not accept a fragment.
        String trimmed = stripFragment(url.trim());
        String hostWithPath;
        if (trimmed.contains("://")) {
            // scheme-based URL: URI parsing drops the scheme, userinfo and port, leaving host + path
            hostWithPath = Urls.stripProtocolAndPort(trimmed);
        } else {
            // scp-like (git@host:group/repo) or a bare host[:/]path, which GitSCPUrl handles
            try {
                hostWithPath = GitSCPUrl.parse(trimmed).getHostWithPath();
            } catch (MalformedURLException e) {
                hostWithPath = trimmed;
            }
        }

        return stripSuffixes(hostWithPath).toLowerCase();
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static String stripSuffixes(String value) {
        String s = stripEnd(value, "/");
        s = stripEnd(s, ".git");
        s = stripEnd(s, "/");
        return s;
    }

    private static String stripEnd(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }
}
