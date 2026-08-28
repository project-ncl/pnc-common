/*
 * Copyright 2018 Honza Brázdil &lt;jbrazdil@redhat.com&gt;.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.version;

import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.EQUAL;
import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.MICRO;
import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.MINOR;
import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.QUALIFIER;
import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.RH_SUFFIX;
import static org.jboss.pnc.common.version.VersionComparator.VersionDifference.SUFFIX;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.pnc.api.constants.versions.VersionDistanceRule;
import org.jboss.pnc.api.constants.versions.VersionFilter;

/**
 * @author Honza Brázdil &lt;jbrazdil@redhat.com&gt;
 */
public class VersionAnalyzer {

    public static final Map<VersionFilter, EnumSet<VersionComparator.VersionDifference>> ALLOWED_DIFFERENCE = new HashMap<>();
    static {
        ALLOWED_DIFFERENCE.put(VersionFilter.ALL, EnumSet.allOf(VersionComparator.VersionDifference.class));
        ALLOWED_DIFFERENCE.put(VersionFilter.MAJOR, EnumSet.of(MINOR, MICRO, QUALIFIER, SUFFIX, RH_SUFFIX, EQUAL));
        ALLOWED_DIFFERENCE.put(VersionFilter.MAJOR_MINOR, EnumSet.of(MICRO, QUALIFIER, SUFFIX, RH_SUFFIX, EQUAL));
        ALLOWED_DIFFERENCE.put(VersionFilter.MAJOR_MINOR_MICRO, EnumSet.of(QUALIFIER, SUFFIX, RH_SUFFIX, EQUAL));
        ALLOWED_DIFFERENCE.put(VersionFilter.MAJOR_MINOR_MICRO_QUALIFIER, EnumSet.of(SUFFIX, RH_SUFFIX, EQUAL));
    }

    private static final Pattern VERSION_PATTERN = Pattern
            .compile("^" + VersionParser.RE_MMM + VersionParser.RE_QUALIFIER_WITH_SEPARATOR + "?");

    private final VersionParser versionParser;
    private final List<String> suffixes = new ArrayList<>();
    private final VersionDistanceRule distanceRule;

    public VersionAnalyzer(List<String> suffixes) {
        this(suffixes, VersionDistanceRule.RECOMMENDED_REPLACEMENT);
    }

    public VersionAnalyzer(List<String> suffixes, VersionDistanceRule distanceRule) {
        this.suffixes.addAll(suffixes);
        this.versionParser = new VersionParser(suffixes);
        this.distanceRule = Objects.requireNonNull(distanceRule);
    }

    public List<String> sortVersions(String querry, Collection<String> versions) {
        VersionComparator comparator = new VersionComparator(querry, distanceRule, versionParser);
        List<String> sortedVersions = versions.stream().sorted(comparator).distinct().collect(Collectors.toList());
        return sortedVersions;
    }

    public List<String> filterVersions(String query, VersionFilter vf, Collection<String> versions) {
        VersionComparator vc = new VersionComparator(query, distanceRule, versionParser);

        final Function<String, Set<SuffixedVersion>> parsingFunction;
        if (versionParser.hasSuffixesConfigured()) {
            // in case suffixes are configured, prefer parsing of suffixed versions
            parsingFunction = versionParser::parseSuffixed;
        } else {
            // otherwise, continue with parsing of version without any suffixes
            parsingFunction = v -> Set.of(VersionParser.parseUnsuffixed(v));
        }

        return versions.stream()
                .map(parsingFunction)
                .flatMap(Set::stream)
                .filter(v -> matches(vc, v, vf))
                .map(SuffixedVersion::getOriginalVersion)
                .sorted(vc)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean matches(VersionComparator vc, SuffixedVersion v, VersionFilter vf) {
        VersionComparator.VersionDifference difference = vc.difference(v);
        return ALLOWED_DIFFERENCE.get(vf).contains(difference);
    }

    public Optional<String> findBiggestMatchingVersion(String query, Collection<String> versions) {
        // in case there is some suffix in the current version analyzer's parser configured
        // so find the best matching version with the suffix
        if (versionParser.hasSuffixesConfigured()) {
            return findBiggestMatchingVersionSuffixed(query, versions);
        }

        // in case the code gets here, we know that the current version analyzer's parser has no suffixes configured
        // so in case the version, for which we search the biggest matching version has no suffix as well, and is present in the collection of provided versions, we just return this one
        String unsuffixedQuery = versionParser.parse(query).unsuffixedVersion();
        if (unsuffixedQuery.equals(query) && versions.contains(query)) {
            return Optional.of(query);
        }

        return Optional.empty();
    }

    /**
     * Find the best fitting version, from the provided versions, which is suffixed with the most desired suffix.
     *
     * @param query version, for which we find the best fitting version
     * @param versions provided list of versions, from which we search for the best fitting version
     *
     * @return the best fitting version, if any
     */
    private Optional<String> findBiggestMatchingVersionSuffixed(String query, Collection<String> versions) {
        String unsuffixedQuery = versionParser.parse(query).unsuffixedVersion();

        // this whole machinery works when we have the current version analyzer's parser has some suffixes configured
        // suppose suffixes are: 'redhat', 'foo' (in this order of preference)
        // further suppose that given is:
        //      query="1.2.3", and
        //      versions=['1.2.4.redhat-4', '1.2.3.foo-9', '1.2.3.redhat-1', '1.2.3.redhat-2', '1.2.3']

        List<SuffixedVersion> candidateSuffixedVersions = versions.stream()
                .map(versionParser::parseSuffixed)
                .flatMap(Set::stream)
                .filter(v -> unsuffixedQuery.equals(v.unsuffixedVersion()))
                .collect(Collectors.toList());
        // above code filters from the provided versions only ones, which are both:
        // suffixed, and at the same time match the unsuffixed part ('1.2.3' in this case)
        // hence: candidateSuffixedVersions=['1.2.3.foo-9', '1.2.3.redhat-1', '1.2.3.redhat-2']
        // i.e.:
        //      '1.2.3' is thrown away because it's not suffixed
        //      '1.2.4.redhat-4' is thrown away because even though it's suffixed, it doesn't match the unsuffixed part

        // finally, going through suffixes in their order of preference ('redhat' firstly, 'foo' secondly)
        // until we hit some versions with the most preferred suffix (if any)
        List<SuffixedVersion> versionsToSearch = Collections.emptyList();
        for (String suffix : suffixes) {
            versionsToSearch = candidateSuffixedVersions.stream()
                    .filter(v -> suffix.equals(v.getSuffix().get()))
                    .collect(Collectors.toList());
            // anytime we find some version from the most desired suffix, break
            if (!versionsToSearch.isEmpty()) {
                break;
            }
        }
        // since we prefer 'redhat' suffix firstly, we are left with: versionsToSearch=['1.2.3.redhat-1', '1.2.3.redhat-2']

        // now we have versionsToSearch from the most desired suffix, just take the version with the biggest number
        // i.e., at first it's '1.2.3.redhat-1', but then, it ends up being '1.2.3.redhat-2'
        String bestMatchVersion = null;
        int biggestBuildNumber = 0;
        for (SuffixedVersion ver : versionsToSearch) {
            int foundBuildNumber = ver.getSuffixVersion().get();
            if (bestMatchVersion == null || foundBuildNumber > biggestBuildNumber) {
                bestMatchVersion = ver.getOriginalVersion();
                biggestBuildNumber = foundBuildNumber;
            } else if (foundBuildNumber == biggestBuildNumber) {
                // just in case we have the same build number (=suffix version), then for instance prefer osgi version
                bestMatchVersion = getMoreSpecificVersion(bestMatchVersion, ver.getOriginalVersion());
            }
        }

        return Optional.ofNullable(bestMatchVersion);
    }

    /**
     * Assuming the two versions have the same OSGi representation, returns the more specific version. That means
     * X.Y.Z.something is preffered to X.Y.something which is preffered to X.something.
     */
    private String getMoreSpecificVersion(String first, String second) {
        Matcher firstMatcher = VERSION_PATTERN.matcher(first);
        Matcher secondMatcher = VERSION_PATTERN.matcher(second);
        if (!firstMatcher.matches()) {
            throw new IllegalArgumentException("Couldn't parse version " + first);
        }
        if (!secondMatcher.matches()) {
            throw new IllegalArgumentException("Couldn't parse version " + second);
        }
        boolean firstIsOSGi = first.equals(VersionParser.getOSGiVersion(first));
        String firstMinor = firstMatcher.group("minor");
        String firstMicro = firstMatcher.group("micro");
        boolean returnFirst;

        if (firstIsOSGi != second.equals(VersionParser.getOSGiVersion(second))) {
            returnFirst = firstIsOSGi; // One of the version is not OSGi, prefer the OSGi version
        } else if (!Objects.equals(firstMinor, secondMatcher.group("minor"))) {
            returnFirst = firstMinor != null; // One of the versions is missing minor number, prefer the one with it
        } else if (!Objects.equals(firstMicro, secondMatcher.group("micro"))) {
            returnFirst = firstMicro != null; // One of the versions is missing micro number, prefer the one with it
        } else {
            // Prefer the version that separates qualifier with '.', not something else like '-'
            // If both are the same, prefer first
            returnFirst = firstMatcher.group("qualifier").startsWith(".")
                    || !secondMatcher.group("qualifier").startsWith(".");
        }
        if (returnFirst) {
            return first;
        } else {
            return second;
        }
    }
}
