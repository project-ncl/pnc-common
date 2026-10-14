package org.jboss.pnc.common.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.common.log.LogSanitizer.REDACTED;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogSanitizerTest {

    private static final String TOKEN = "AhLsDCVVTvG9tVdwlFw2vSUDKsgtk5jt63X2q5d"; // HashiCorpIgnore notsecret

    @Test
    public void shouldReturnNullForNullInput() {
        Assertions.assertNull(LogSanitizer.clean(null));
    }

    @Test
    public void shouldReturnEmptyStringForEmptyInput() {
        Assertions.assertEquals("", LogSanitizer.clean(""));
    }

    @Test
    public void shouldNotModifyCleanString() {
        String clean = "This is a clean log message";
        Assertions.assertEquals(clean, LogSanitizer.clean(clean));
    }

    @Test
    public void shouldReplaceNullCharacter() {
        String input = "Hello\u0000World";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceTabCharacter() {
        String input = "Hello\tWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceNewlineCharacter() {
        String input = "Hello\nWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceCarriageReturnCharacter() {
        String input = "Hello\rWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceDeleteCharacter() {
        String input = "Hello\u007FWorld";
        String expected = "Hello_World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceMultipleControlCharacters() {
        String input = "Hello\u0000\t\n\r\u007FWorld";
        String expected = "Hello_____World";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldReplaceAllControlCharactersInRange() {
        // Test a few control characters from the range 0x00-0x1F
        String input = "Test\u0001\u0002\u0003\u001F";
        String expected = "Test____";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveNormalWhitespace() {
        String input = "Hello World";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveSpecialCharacters() {
        String input = "Hello!@#$%^&*()_+-=[]{}|;':\",./<>?World";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldPreserveUnicodeCharacters() {
        String input = "Hello 世界 🌍";
        Assertions.assertEquals(input, LogSanitizer.clean(input));
    }

    @Test
    public void shouldHandleStringWithOnlyControlCharacters() {
        String input = "\u0000\t\n\r\u007F";
        String expected = "_____";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void shouldHandleControlCharactersAtStartAndEnd() {
        String input = "\u0000Hello World\u007F";
        String expected = "_Hello World_";
        Assertions.assertEquals(expected, LogSanitizer.clean(input));
    }

    @Test
    public void mask_nullInput_returnsNull() {
        assertThat(LogSanitizer.mask(null, "FOO")).isNull();
    }

    @Test
    public void mask_noKeysProvided_returnsInputUnchanged() {
        assertThat(LogSanitizer.mask("FOO=bar")).isEqualTo("FOO=bar");
    }

    @Test
    public void mask_envVarKeyMatch_valueRedacted() {
        assertThat(LogSanitizer.mask("ARTIFACTORY_READ_TOKEN=" + TOKEN + " -Dother=foo", "ARTIFACTORY_READ_TOKEN"))
                .isEqualTo("ARTIFACTORY_READ_TOKEN=" + REDACTED + " -Dother=foo");
    }

    @Test
    public void mask_javaPropPrefixedKey_valueRedacted() {
        assertThat(LogSanitizer.mask("java -DrestHeaders=Authorization:Bearer -d", "restHeaders"))
                .isEqualTo("java -DrestHeaders=" + REDACTED + " -d");
    }

    @Test
    public void mask_cliOptPrefixedKey_valueRedacted() {
        assertThat(LogSanitizer.mask("cmd --myKey=" + TOKEN + " --other=x", "myKey"))
                .isEqualTo("cmd --myKey=" + REDACTED + " --other=x");
    }

    @Test
    public void mask_quotedValue_wholeValueRedacted() {
        assertThat(LogSanitizer.mask("-DrestHeaders=\"Authorization: Bearer " + TOKEN + "\" -d", "restHeaders"))
                .isEqualTo("-DrestHeaders=" + REDACTED + " -d");
    }

    @Test
    public void mask_multipleKeysInOneCall_bothRedacted() {
        assertThat(
                LogSanitizer.mask(
                        "x ARTIFACTORY_READ_TOKEN=" + TOKEN + " -DrestHeaders=" + TOKEN + " y",
                        "ARTIFACTORY_READ_TOKEN",
                        "restHeaders"))
                .isEqualTo("x ARTIFACTORY_READ_TOKEN=" + REDACTED + " -DrestHeaders=" + REDACTED + " y");
    }

    @Test
    public void mask_caseInsensitiveKeyMatch_valueRedacted() {
        assertThat(LogSanitizer.mask("TOKEN=" + TOKEN, "token")).isEqualTo("TOKEN=" + REDACTED);
    }

    @Test
    public void mask_keyNotPresent_inputUnchanged() {
        assertThat(LogSanitizer.mask("clean", "FOO")).isEqualTo("clean");
    }

    @Test
    public void mask_valueAtEndOfString_valueRedacted() {
        assertThat(LogSanitizer.mask("a=b FOO=" + TOKEN, "FOO")).isEqualTo("a=b FOO=" + REDACTED);
    }

    @Test
    public void mask_regexSpecialCharsInKey_treatedLiterally() {
        assertThat(LogSanitizer.mask("a.b=" + TOKEN + " axb=2", "a.b")).isEqualTo("a.b=" + REDACTED + " axb=2");
    }

    @Test
    public void mask_precompiledPatternOverload_valueRedacted() {
        java.util.regex.Pattern pattern = LogSanitizer.compileKeyPattern("FOO", "BAR");
        assertThat(LogSanitizer.mask("FOO=" + TOKEN + " BAR=" + TOKEN + " BAZ=3", pattern))
                .isEqualTo("FOO=" + REDACTED + " BAR=" + REDACTED + " BAZ=3");
    }

    @Test
    public void maskKnownSecrets_nullInput_returnsNull() {
        assertThat(LogSanitizer.maskKnownSecrets(null)).isNull();
    }

    @Test
    public void maskKnownSecrets_tokenEnvVar_valueRedacted() {
        assertThat(
                LogSanitizer
                        .maskKnownSecrets("ARTIFACTORY_READ_TOKEN=" + TOKEN + " ARTIFACTORY_URL=https://example.com"))
                .isEqualTo("ARTIFACTORY_READ_TOKEN=" + REDACTED + " ARTIFACTORY_URL=https://example.com");
    }

    @Test
    public void maskKnownSecrets_passwordJavaProp_valueRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("java -Dpassword=" + TOKEN + " -Dfoo=bar"))
                .isEqualTo("java -Dpassword=" + REDACTED + " -Dfoo=bar");
    }

    @Test
    public void maskKnownSecrets_bearerTokenFormA_tokenRedacted() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "java -DrestHeaders=Authorization: Bearer " + TOKEN + " -d -DrestURL=http://example.com"))
                .isEqualTo(
                        "java -DrestHeaders=Authorization: Bearer " + REDACTED + " -d -DrestURL=http://example.com");
    }

    @Test
    public void maskKnownSecrets_bearerTokenFormB_tokenRedacted() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "java \"-DrestHeaders=Authorization: Bearer " + TOKEN
                                + "\" -d -DrestURL=http://example.com"))
                .isEqualTo(
                        "java \"-DrestHeaders=Authorization: Bearer " + REDACTED
                                + "\" -d -DrestURL=http://example.com");
    }

    @Test
    public void maskKnownSecrets_bearerTokenFormC_tokenRedacted() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "java -DrestHeaders=\"Authorization: Bearer " + TOKEN
                                + "\" -d -DrestURL=http://example.com"))
                .isEqualTo(
                        "java -DrestHeaders=\"Authorization: Bearer " + REDACTED
                                + "\" -d -DrestURL=http://example.com");
    }

    @Test
    public void maskKnownSecrets_bearerShortWord_notRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("x Bearer true y")).isEqualTo("x Bearer true y");
    }

    @Test
    public void maskKnownSecrets_subsequentArgsPreserved() {
        String input = "/usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -DstrictPropertyValidation=true -DrestHeaders=\"Authorization: Bearer " + TOKEN + "\""
                + " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                + " -DrestSocketTimeout=3600";
        String result = LogSanitizer.maskKnownSecrets(input);
        assertThat(result).doesNotContain(TOKEN)
                .contains(
                        " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                                + " -DrestSocketTimeout=3600")
                .contains("-DstrictPropertyValidation=true");
    }

    @Test
    public void maskKnownSecrets_basicAuthInValue_credentialRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("Authorization: Basic " + TOKEN + "= next"))
                .isEqualTo("Authorization: Basic " + REDACTED + " next");
    }

    @Test
    public void maskKnownSecrets_secretCliLongOpt_valueRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("cmd --api-key=" + TOKEN + " --verbose"))
                .isEqualTo("cmd --api-key=" + REDACTED + " --verbose");
    }

    @Test
    public void maskKnownSecrets_noSensitiveContent_inputUnchanged() {
        String input = "java -jar app.jar -DrestURL=http://example.com -Dfoo=bar LANG=en_US.UTF-8";
        assertThat(LogSanitizer.maskKnownSecrets(input)).isEqualTo(input);
    }

    @Test
    public void maskKnownSecrets_multipleSecretsInOneLine_allRedacted() {
        String input = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + TOKEN
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -Dpassword=" + TOKEN + " -DrepoReportingRemoval=true -DrestBrewPullActive=false";
        String expected = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + REDACTED
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -Dpassword=" + REDACTED + " -DrepoReportingRemoval=true -DrestBrewPullActive=false";
        assertThat(LogSanitizer.maskKnownSecrets(input)).isEqualTo(expected);
    }

    @Test
    public void maskKnownSecrets_artifactoryTokenAndRestHeaders_bothRedacted() {
        String input = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + TOKEN
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -DstrictPropertyValidation=true -DrestHeaders=\"Authorization: Bearer " + TOKEN + "\""
                + " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                + " -DrestSocketTimeout=3600";
        String expected = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + REDACTED
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -DstrictPropertyValidation=true -DrestHeaders=\"Authorization: Bearer " + REDACTED + "\""
                + " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                + " -DrestSocketTimeout=3600";
        assertThat(LogSanitizer.maskKnownSecrets(input)).isEqualTo(expected).doesNotContain(TOKEN);
    }

    @Test
    public void maskKnownSecrets_artifactoryTokenAndRestHeadersBasic_bothRedacted() {
        String input = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + TOKEN
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -DstrictPropertyValidation=true -DrestHeaders=\"Authorization: Basic " + TOKEN + "\""
                + " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                + " -DrestSocketTimeout=3600";
        String expected = "cd /tmp/adjust && ARTIFACTORY_READ_TOKEN=" + REDACTED
                + " ARTIFACTORY_URL=https://artifactory-pnc.limited-use.ibm.com LANG=en_US.UTF-8 HOME=/opt/reqour"
                + " /usr/lib/jvm/java-11-openjdk/bin/java -jar /tmp/current/pom-manipulation-cli.jar"
                + " -DstrictPropertyValidation=true -DrestHeaders=\"Authorization: Basic " + REDACTED + "\""
                + " -d -DrestURL=http://da-prod.newcastle--runtime-prod.svc.cluster.local/rest/v-1"
                + " -DrestSocketTimeout=3600";
        assertThat(LogSanitizer.maskKnownSecrets(input)).isEqualTo(expected).doesNotContain(TOKEN);
    }

    @Test
    public void maskKnownSecrets_basicAuthHeaderWithSpaceAfterColon_credentialRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("Authorization: Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ"))
                .isEqualTo("Authorization: Basic " + REDACTED);
    }

    @Test
    public void maskKnownSecrets_basicAuthHeaderWithoutSpaceAfterColon_credentialRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("Authorization:Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ"))
                .isEqualTo("Authorization:Basic " + REDACTED);
    }

    @Test
    public void maskKnownSecrets_bearerHeaderWithoutSpaceAfterColon_tokenRedacted() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "java -DrestHeaders=\"Authorization:Bearer " + TOKEN + "\" -d -DrestURL=http://example.com"))
                .isEqualTo(
                        "java -DrestHeaders=\"Authorization:Bearer " + REDACTED
                                + "\" -d -DrestURL=http://example.com");
    }

    @Test
    public void maskKnownSecrets_basicHeaderWithoutSpaceAfterColonInRestHeaders_subsequentArgsPreserved() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "java -DrestHeaders=\"Authorization:Basic " + TOKEN + "\" -d -DrestURL=http://example.com"))
                .isEqualTo(
                        "java -DrestHeaders=\"Authorization:Basic " + REDACTED
                                + "\" -d -DrestURL=http://example.com");
    }

    @Test
    public void maskKnownSecrets_urlUserinfo_credentialRedacted() {
        assertThat(LogSanitizer.maskKnownSecrets("git clone https://oauth2:" + TOKEN + "@gitlab.com/lw/foo.git ."))
                .isEqualTo("git clone https://" + REDACTED + "@gitlab.com/lw/foo.git .");
    }

    @Test
    public void maskKnownSecrets_urlWithoutUserinfo_inputUnchanged() {
        assertThat(LogSanitizer.maskKnownSecrets("https://gitlab.com/lw/foo.git"))
                .isEqualTo("https://gitlab.com/lw/foo.git");
    }

    @Test
    public void maskKnownSecrets_scpLikeUrl_inputUnchanged() {
        assertThat(LogSanitizer.maskKnownSecrets("git@gitlab.com:lw/lw-builds/repo.git"))
                .isEqualTo("git@gitlab.com:lw/lw-builds/repo.git");
    }

    @Test
    public void maskKnownSecrets_urlUserinfoSubsequentArgsPreserved() {
        assertThat(
                LogSanitizer.maskKnownSecrets(
                        "git remote add origin https://user:" + TOKEN + "@github.ibm.com/org/repo.git --verbose"))
                .isEqualTo("git remote add origin https://" + REDACTED + "@github.ibm.com/org/repo.git --verbose");
    }
}
