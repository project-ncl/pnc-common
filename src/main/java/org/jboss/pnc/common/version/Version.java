/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.common.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component that reads a version string and makes various modifications to it such as converting to a valid OSGi
 * version string and/or incrementing a version suffix. See: http://www.aqute.biz/Bnd/Versioning for an explanation of
 * OSGi versioning. Parses versions into the following format: &lt;major&gt;.&lt;minor&gt;.&lt;micro&gt;
 * .&lt;qualifierBase&gt;-&lt;buildnumber&gt;-&lt;buildnumber&gt;-&lt;snapshot&gt;
 * <p>
 * This class is copied from org.commonjava.maven.ext:pom-manipulation-core org.commonjava.maven.ext.core.impl.Version
 */
@SuppressWarnings("WeakerAccess") // Public API.
public class Version {
    private static final Logger logger = LoggerFactory.getLogger(Version.class);

    private final static String EMPTY_STRING = "";

    private final static String OSGI_VERSION_DELIMITER = ".";

    private final static String OSGI_QUALIFIER_DELIMITER = "-";

    /**
     * Regular expression used to match version string delimiters
     */
    private final static String DELIMITER_REGEX = "[.\\-_]";

    /**
     * Regular expression used to match version string delimiters
     */
    private final static String LEADING_DELIMITER_REGEX = "^" + DELIMITER_REGEX;

    /**
     * Regular expression used to match the major, minor, and micro versions
     */
    private final static String MMM_REGEX = "(\\d+)(" + DELIMITER_REGEX + "(\\d+)(" + DELIMITER_REGEX + "(\\d+))?)?";

    private final static Pattern mmmPattern = Pattern.compile(MMM_REGEX);

    private final static String SNAPSHOT_SUFFIX = "SNAPSHOT";

    /**
     * Regular expression used to match the parts of the qualifier "base-buildnum-snapshot" Note : Technically within
     * the rebuild-numeric the dash is currently optional and can be any delimeter type within the regex. It could be
     * made mandatory via '{1}'.
     */
    private final static String QUALIFIER_REGEX = "(.*?)((" + DELIMITER_REGEX + ")?(\\d+))?((" + DELIMITER_REGEX
            + ")?((?i:" + SNAPSHOT_SUFFIX + ")))?$";

    /**
     * Version string must start with a digit to match the regex. Otherwise we have only a qualifier.
     */
    private final static String VERSION_REGEX = "(" + MMM_REGEX + ")" + "((" + DELIMITER_REGEX + ")?" + "("
            + QUALIFIER_REGEX + "))";

    private final static Pattern versionPattern = Pattern.compile(VERSION_REGEX);

    /**
     * Used to match valid OSGi version based on section 3.2.5 of the OSGi specification
     */
    private final static String OSGI_VERSION_REGEX = "(\\d+)(\\.\\d+(\\.\\d+(\\.[\\w\\-_]+)?)?)?";

    private final static Pattern osgiPattern = Pattern.compile(OSGI_VERSION_REGEX);

    // Prevent construction.
    private Version() {
    }

    /**
     * Returns the initial numeric portion (which may be up to 3 digits long) excluding any delimeter suffix.
     *
     * @param version the version to examine
     * @return a parsed string version.
     */
    public static String getMMM(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(1);
        }
        return EMPTY_STRING;
    }

    /**
     * Get the major, minor, micro version in OSGi format
     *
     * @param version The version to parse
     * @param fill Whether to fill the minor and micro versions with zeros if they are missing
     * @return OSGi formatted major, minor, micro
     */
    static String getOsgiMMM(String version, boolean fill) {
        String mmm = getMMM(version);
        Matcher mmmMatcher = mmmPattern.matcher(mmm);
        if (mmmMatcher.matches()) {
            String osgiMMM = mmmMatcher.group(1);
            String minorVersion = mmmMatcher.group(3);
            if (!isEmpty(minorVersion)) {
                osgiMMM += OSGI_VERSION_DELIMITER + minorVersion;
            } else if (fill) {
                osgiMMM += OSGI_VERSION_DELIMITER + "0";
            }
            String microVersion = mmmMatcher.group(5);
            if (!isEmpty(microVersion)) {
                osgiMMM += OSGI_VERSION_DELIMITER + microVersion;
            } else if (fill) {
                osgiMMM += OSGI_VERSION_DELIMITER + "0";
            }
            return osgiMMM;
        }
        return EMPTY_STRING;
    }

    public static String getOsgiVersion(String version) {
        String qualifier = getQualifier(version);
        if (!isEmpty(qualifier)) {
            qualifier = OSGI_VERSION_DELIMITER + qualifier.replace(OSGI_VERSION_DELIMITER, OSGI_QUALIFIER_DELIMITER);
        }
        String mmm = getOsgiMMM(version, !isEmpty(qualifier));
        if (isEmpty(mmm)) {
            logger.warn("Unable to parse version for OSGi: {}", version);
            return version;
        }
        return mmm + qualifier;
    }

    public static String getQualifier(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(9);
        }
        return removeLeadingDelimiter(version);
    }

    public static boolean isEmpty(String string) {
        return (string == null || EMPTY_STRING.equals(string.trim()));
    }

    /**
     * Checks if the string is a valid version according to section 3.2.5 of the OSGi specification
     *
     * @param version the version
     * @return true if the version is valid
     */
    public static boolean isValidOSGi(String version) {
        return osgiPattern.matcher(version).matches();
    }

    /**
     * Remove any leading delimiters from the partial version string.
     *
     * @param versionPart the partial version
     * @return the partial version with any leading delimiters removed
     */
    static String removeLeadingDelimiter(String versionPart) {
        return versionPart.replaceAll(LEADING_DELIMITER_REGEX, "");
    }

}
