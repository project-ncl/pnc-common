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
import java.net.URI;

/**
 * Parses an SCM URL and normalizes it to a canonical {@code host/path} form so host + group/subgroup prefixes can be
 * compared regardless of the URL syntax used.
 *
 * <p>
 * This complements {@link GitSCPUrl}, which only parses scp-like URLs ({@code git@host:group/repo.git}).
 * Scheme-based URLs ({@code https://}, {@code git+ssh://}, {@code ssh://}, {@code git://}) together with any
 * embedded userinfo and port are handled here (via {@link URI}); scp-like and bare {@code host[:/]path} forms are
 * delegated to {@link GitSCPUrl}.
 *
 * <p>
 * Use {@link #normalize(String)} when only the canonical string is needed. Its result is lower-cased and carries no
 * scheme, userinfo, port, {@code .git} suffix, trailing slash or {@code #ref} fragment. A fragment is a git revision
 * selector, not part of the repository identity this method compares, so it is dropped. For example
 * {@code https://git@gitlab.example.com:8080/lw/lw-builds/foo.git#tag} and
 * {@code git@gitlab.example.com:lw/lw-builds/foo} both normalize to {@code gitlab.example.com/lw/lw-builds/foo}.
 *
 * <p>
 * Use {@link #parse(String)} when the parts are needed (host, path and the fragment), so a URL is parsed once and
 * does not have to be taken apart by the caller.
 */
public class ScmUrlNormalizer {

    private static final ScmUrlNormalizer EMPTY = new ScmUrlNormalizer("", "", null);

    private final String host;
    private final String path;
    private final String fragment;

    private ScmUrlNormalizer(String host, String path, String fragment) {
        this.host = host;
        this.path = path;
        this.fragment = fragment;
    }

    /**
     * Normalize an SCM URL to its canonical {@code host/path} form. Returns an empty string for a null or blank
     * input.
     *
     * <p>
     * Whatever follows the first {@code #} is dropped even when it is not a valid revision, so the result depends
     * only on the host and the path.
     *
     * @param url the SCM URL in any supported form (https, git+ssh, ssh, git, scp-like, or bare host/path)
     * @return the normalized {@code host/path}, lower-cased and without scheme, userinfo, port, {@code .git}
     *         suffix, trailing slash or {@code #ref} fragment
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String trimmed = url.trim();
        try {
            return parse(trimmed).normalized();
        } catch (IllegalArgumentException e) {
            if (trimmed.contains("://")) {
                throw e; // a malformed scheme-based URL has always been an error
            }
            // not a parsable scp-like or host/path url: compare the text as it is
            return stripSuffixes(stripFragment(trimmed)).toLowerCase();
        }
    }

    /**
     * Parse an SCM URL into its host, path and fragment. A null or blank input gives an instance with an empty host
     * and path and no fragment.
     *
     * <p>
     * The fragment is not validated: when the URL cannot be parsed with its fragment (e.g. {@code #bad ref}), the part
     * before the first {@code #} is parsed instead and what follows the {@code #} is returned as it is by
     * {@link #getFragment()}. Callers that restrict the fragment must check it themselves.
     *
     * @param url the SCM URL in any supported form (https, git+ssh, ssh, git, scp-like, or bare host/path)
     * @return the parsed URL
     * @throws IllegalArgumentException if the URL, ignoring its fragment, cannot be parsed
     */
    public static ScmUrlNormalizer parse(String url) {
        if (url == null || url.isBlank()) {
            return EMPTY;
        }

        String trimmed = url.trim();
        try {
            return parseWithFragment(trimmed);
        } catch (IllegalArgumentException e) {
            int hash = trimmed.indexOf('#');
            if (hash < 0) {
                throw e;
            }
            ScmUrlNormalizer withoutFragment = parseWithFragment(trimmed.substring(0, hash));
            return new ScmUrlNormalizer(withoutFragment.host, withoutFragment.path, trimmed.substring(hash + 1));
        }
    }

    private static ScmUrlNormalizer parseWithFragment(String url) {
        return url.contains("://") ? parseSchemeUrl(url) : parseScpUrl(url);
    }

    /**
     * @return the host as written in the URL, without scheme, userinfo and port; empty if there is none
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the path as written in the URL, {@code .git} suffix included, without leading and trailing slash
     */
    public String getPath() {
        return path;
    }

    /**
     * @return the revision/ref after the {@code #} without the {@code #}, or null if the URL has none
     */
    public String getFragment() {
        return fragment;
    }

    /**
     * @return the same canonical form {@link #normalize(String)} returns for the parsed URL
     */
    public String normalized() {
        String hostWithPath = path.isEmpty() ? host : host + "/" + path;
        return stripSuffixes(hostWithPath).toLowerCase();
    }

    private static ScmUrlNormalizer parseSchemeUrl(String url) {
        // URI parsing drops the scheme, userinfo and port, leaving host + path (+ query) and the fragment
        URI uri = URI.create(url);
        String path = uri.getPath() == null ? "" : stripStart(uri.getPath(), "/");
        if (uri.getQuery() != null) {
            // not meaningful for an SCM URL, kept as part of the path so normalize() stays backwards compatible
            path += "?" + uri.getQuery();
        } else {
            path = stripEnd(path, "/");
        }
        return new ScmUrlNormalizer(uri.getHost() == null ? "" : uri.getHost(), path, uri.getFragment());
    }

    private static ScmUrlNormalizer parseScpUrl(String url) {
        // scp-like (git@host:group/repo) or a bare host[:/]path, which GitSCPUrl handles
        try {
            GitSCPUrl scpUrl = GitSCPUrl.parse(url);
            return new ScmUrlNormalizer(scpUrl.getHost(), scpUrl.getPath(), scpUrl.getFragment());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                    "Supplied URL: " + url + " is neither regular URI nor in Git SCP-style format.",
                    e);
        }
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

    private static String stripStart(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    private static String stripEnd(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }
}
