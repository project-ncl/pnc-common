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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is used only for parsing git urls with scp-like syntax.
 *
 * e.g. git@github.com:project-ncl/pnc.git
 *
 * An optional {@code #fragment} (the revision/ref, e.g. git@github.com:project-ncl/pnc.git#1.0.0) is accepted. It is
 * not part of the host or the path: see {@link #getFragment()}.
 */
public class GitSCPUrl {
    private final String user;
    private final String host;
    private final String path;
    private final String owner;
    private final String repositoryName;
    private final String fragment;

    /**
     * Gitlab naming convention:
     *
     * Must start with a lowercase or uppercase letter, digit, emoji, or underscore. Can also contain dots, pluses,
     * dashes, or spaces.
     *
     * Java's \w covers [a-zA-Z_0-9] : lower, uppercase letter, digit, underscore
     *
     * We're not going to "support" emoji in our regex because it's a landmine. I don't think we'll have repos using
     * emojis :)
     *
     * We're also not adding space on the regex. Maybe we should in the future?
     *
     * The optional trailing {@code #fragment} is a revision/ref, which can contain a '/' (e.g. feature/x).
     */
    private static Pattern scpPattern = Pattern.compile(
            "^((?<user>\\w+)@)?(?<host>[-.+\\w]+)[:/]{1,2}(?<path>((?<owner>[-.+\\w]+)/)*(?<reponame>[-.+\\w]+))[/]?(#(?<fragment>[-.+/\\w]+))?$");

    private GitSCPUrl(String user, String host, String path, String owner, String repositoryName, String fragment) {
        this.user = user;
        this.host = host;
        this.path = path;
        this.owner = owner;
        this.repositoryName = repositoryName;
        this.fragment = fragment;
    }

    public static GitSCPUrl parse(String url) throws MalformedURLException {
        if (url == null) {
            throw new IllegalArgumentException("Supplied URL cannot be null");
        }

        Matcher matcher = scpPattern.matcher(url);
        if (!matcher.matches()) {
            throw new MalformedURLException("Supplied URL is in incompatible format");
        }

        return new GitSCPUrl(
                matcher.group("user"),
                matcher.group("host"),
                matcher.group("path"),
                matcher.group("owner"),
                matcher.group("reponame").replaceAll(".git$", ""),
                matcher.group("fragment"));
    }

    public String getUser() {
        return user;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    /**
     * @return the part after '#' (revision/ref) without the '#', or null if the URL has none
     */
    public String getFragment() {
        return fragment;
    }

    /**
     * @return host and path, without the user and without the fragment
     */
    public String getHostWithPath() {
        return host + "/" + path;
    }
}
