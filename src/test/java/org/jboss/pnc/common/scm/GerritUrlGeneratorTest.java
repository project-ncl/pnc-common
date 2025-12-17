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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class GerritUrlGeneratorTest {

    private final GerritScmUrlGenerator scmUrlGenerator = new GerritScmUrlGenerator();

    @Test
    public void downloadUrlShouldHandleHttpCloneLink() throws ScmException {
        String url = "https://localhost/gerrit/project/repository.git";
        String downloadUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=snapshot;h=abcde;sf=tgz";

        assertThat(scmUrlGenerator.generateTarballDownloadUrl(url, "abcde")).isEqualTo(downloadUrl);
    }

    @Test
    public void downloadUrlShouldHandleSshCloneLink() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository.git";
        String downloadUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=snapshot;h=master;sf=tgz";

        assertThat(scmUrlGenerator.generateTarballDownloadUrl(url, "master")).isEqualTo(downloadUrl);
    }

    @Test
    public void downloadUrlShouldHandleCloneLinkWithoutDotGit() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository";
        String downloadUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=snapshot;h=master;sf=tgz";

        assertThat(scmUrlGenerator.generateTarballDownloadUrl(url, "master")).isEqualTo(downloadUrl);
    }

    @Test
    public void downloadUrlShouldThrowGerritExceptionOnEmptyProject() {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateTarballDownloadUrl("http://localhost", "master"));
    }

    @Test
    public void downloadUrlShouldThrowGerritExceptionOnEmptyGerritUrl() throws ScmException {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateTarballDownloadUrl("", "master"));
    }

    @Test
    public void gerritGitwebLogUrlShouldHandleHttpCloneLink() throws ScmException {

        String url = "https://localhost/gerrit/project/repository.git";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=shortlog;h=master";

        assertThat(scmUrlGenerator.generateGitwebLogUrl(url, "master")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebLogUrlShouldHandleSshCloneLink() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository.git";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=shortlog;h=master";

        assertThat(scmUrlGenerator.generateGitwebLogUrl(url, "master")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebLogUrlShouldHandleEmptyRef() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository.git";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=summary";

        assertThat(scmUrlGenerator.generateGitwebLogUrl(url, null)).isEqualTo(gitwebUrl);
        assertThat(scmUrlGenerator.generateGitwebLogUrl(url, "")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebLogUrlShouldHandleCloneLinkWithoutDotGit() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=shortlog;h=master";

        assertThat(scmUrlGenerator.generateGitwebLogUrl(url, "master")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebLogUrlShouldThrowGerritExceptionOnEmptyProject() throws ScmException {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateGitwebLogUrl("http://localhost", "master"));
    }

    @Test
    public void gerritGitwebLogUrlShouldThrowGerritExceptionOnEmptyGerritUrl() throws ScmException {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateGitwebLogUrl("", "master"));
    }

    @Test
    public void gerritGitwebCommitUrlShouldHandleHttpCloneLink() throws ScmException {

        String url = "https://localhost/gerrit/project/repository.git";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=commit;h=master";

        assertThat(scmUrlGenerator.generateGitwebCommitUrl(url, "master")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebCommitUrlShouldHandleSshCloneLink() throws ScmException {

        String url = "git+ssh://localhost/gerrit/project/repository.git";
        String gitwebUrl = "https://localhost/gerrit/gitweb?p=project/repository.git;a=commit;h=master";

        assertThat(scmUrlGenerator.generateGitwebCommitUrl(url, "master")).isEqualTo(gitwebUrl);
    }

    @Test
    public void gerritGitwebCommitUrlShouldThrowGerritExceptionOnEmptyProject() throws ScmException {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateGitwebCommitUrl("http://localhost", "master"));
    }

    @Test
    public void gerritGitwebCommitUrlShouldThrowGerritExceptionOnEmptyGerritUrl() throws ScmException {

        assertThatExceptionOfType(ScmException.class)
                .isThrownBy(() -> scmUrlGenerator.generateGitwebCommitUrl("", "master"));
    }
}