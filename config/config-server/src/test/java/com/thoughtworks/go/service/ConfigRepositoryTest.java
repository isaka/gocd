/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.service;

import com.thoughtworks.go.GoConfigRevisions;
import com.thoughtworks.go.config.exceptions.ConfigFileHasChangedException;
import com.thoughtworks.go.config.exceptions.ConfigMergeException;
import com.thoughtworks.go.domain.GoConfigRevision;
import com.thoughtworks.go.helper.ConfigFileFixture;
import com.thoughtworks.go.util.SystemEnvironment;
import com.thoughtworks.go.util.TimeProvider;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigRepositoryTest {
    private ConfigRepository configRepo;
    private SystemEnvironment systemEnvironment;
    private Git configRepoRawGit;

    @BeforeEach
    public void setUp(@TempDir File configRepoDir) throws IOException {
        systemEnvironment = mock(SystemEnvironment.class);
        when(systemEnvironment.getConfigRepoDir()).thenReturn(configRepoDir);
        when(systemEnvironment.get(SystemEnvironment.GO_CONFIG_REPO_GC_AGGRESSIVE)).thenReturn(true);
        when(systemEnvironment.get(SystemEnvironment.GO_CONFIG_REPO_PERIODIC_GC)).thenReturn(true);
        configRepo = new ConfigRepository(systemEnvironment);
        configRepo.initialize();
        configRepoRawGit = configRepo.git();
    }

    @AfterEach
    public void tearDown() {
        configRepoRawGit.close();
        configRepo.getGitRepo().close();
    }

    @Test
    public void shouldBeAbleToCheckin() throws Exception {
        configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v1 v2", "md5-v2", "user-name", "100.9.8", new TimeProvider()));
        assertThat(configRepo.getRevision("md5-v1").getContent()).isEqualTo("v1");
        assertThat(configRepo.getRevision("md5-v2").getContent()).isEqualTo("v1 v2");
    }

    @Test @SuppressWarnings("try")
    public void shouldBeAbleToCheckInWithGlobalGpgSigningEnabled() throws Exception {
        try (UndoableUserGitConfig ignored = new UndoableUserGitConfig(c -> c.setBoolean(ConfigConstants.CONFIG_COMMIT_SECTION, null, ConfigConstants.CONFIG_KEY_GPGSIGN, true))) {
            configRepo = new ConfigRepository(systemEnvironment);
            configRepo.initialize();
            configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
            assertThat(configRepo.getRevision("md5-v1").getContent()).isEqualTo("v1");
            assertThat(configRepo.getCurrentRevCommit().getRawGpgSignature()).isNull();
        }
    }

    private static class UndoableUserGitConfig implements AutoCloseable {
        private final String originalConfig;

        public UndoableUserGitConfig(Consumer<StoredConfig> configConsumer) throws Exception {
            StoredConfig config = SystemReader.getInstance().getUserConfig();
            originalConfig = config.toText();
            configConsumer.accept(config);
        }

        @Override
        public void close() throws ConfigInvalidException, IOException {
            StoredConfig config = SystemReader.getInstance().getUserConfig();
            config.fromText(originalConfig);
        }
    }

    @Test
    public void shouldGetCommitsCorrectly() throws Exception {
        configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v2", "md5-v2", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v3", "md5-v3", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v4", "md5-v4", "user-name", "100.3.9", new TimeProvider()));

        GoConfigRevisions goConfigRevisions = configRepo.getCommits(3, 0);

        assertThat(goConfigRevisions.size()).isEqualTo(3);
        assertThat(goConfigRevisions.get(0).getContent()).isNull();
        assertThat(goConfigRevisions.get(0).getMd5()).isEqualTo("md5-v4");
        assertThat(goConfigRevisions.get(1).getMd5()).isEqualTo("md5-v3");
        assertThat(goConfigRevisions.get(2).getMd5()).isEqualTo("md5-v2");

        goConfigRevisions = configRepo.getCommits(3, 3);

        assertThat(goConfigRevisions.size()).isEqualTo(1);
        assertThat(goConfigRevisions.get(0).getMd5()).isEqualTo("md5-v1");
    }

    @Test
    public void shouldFailWhenDoesNotFindARev() throws Exception {
        configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
        try {
            configRepo.getRevision("some-random-revision");
            fail("should have failed as revision does not exist");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("There is no config version corresponding to md5: 'some-random-revision'");
        }
    }

    @Test
    public void shouldUnderstandRevision_current_asLatestRevision() throws Exception {
        configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v1 v2", "md5-v2", "user-name", "100.9.8", new TimeProvider()));
        assertThat(configRepo.getRevision("current").getMd5()).isEqualTo("md5-v2");
    }

    @Test
    public void shouldReturnNullWhenThereAreNoCheckIns() throws Exception {
        assertThat(configRepo.getRevision("current")).isNull();
    }

    @Test
    public void shouldNotCommitWhenNothingChanged() throws Exception {
        configRepo.checkin(new GoConfigRevision("v1", "md5-v1", "user-name", "100.3.9", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision("v1 v2", "md5-v1", "loser-name", "501.9.8", new TimeProvider()));//md5 is solely trusted
        Iterator<RevCommit> commitIterator = configRepo.revisions().iterator();
        int size = 0;
        while (commitIterator.hasNext()) {
            size++;
            commitIterator.next();
        }
        assertThat(size).isEqualTo(1);
    }

    @Test
    public void shouldShowDiffBetweenTwoConsecutiveGitRevisions() throws Exception {
        configRepo.checkin(goConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 33), "md5-1"));
        RevCommit previousCommit = configRepo.revisions().iterator().next();
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 60), "md5-2", "user-2", "13.2", new TimeProvider()));
        RevCommit latestCommit = configRepo.revisions().iterator().next();
        String configChangesLine1 = "-<cruise schemaVersion='33'>";
        String configChangesLine2 = "+<cruise schemaVersion='60'>";
        String actual = configRepo.findDiffBetweenTwoRevisions(latestCommit, previousCommit);
        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine2);
    }

    @Test
    public void shouldShowDiffBetweenAnyTwoGitRevisionsGivenTheirMd5s() throws Exception {
        configRepo.checkin(goConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 33), "md5-1"));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 60), "md5-2", "user-2", "13.2", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 55), "md5-3", "user-1", "13.2", new TimeProvider()));
        String configChangesLine1 = "-<cruise schemaVersion='33'>";
        String configChangesLine2 = "+<cruise schemaVersion='55'>";
        String actual = configRepo.findDiffBetweenTwoRevisions(configRepo.getRevCommitForMd5("md5-3"), configRepo.getRevCommitForMd5("md5-1"));
        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine2);
    }

    @Test
    public void shouldReturnNullForFirstCommit() throws Exception {
        configRepo.checkin(goConfigRevision("something", "md5-1"));
        RevCommit firstCommit = configRepo.revisions().iterator().next();
        String actual = configRepo.findDiffBetweenTwoRevisions(firstCommit, null);

        assertThat(actual).isNull();
    }

    @Test
    public void shouldShowDiffForAnyTwoConfigMd5s() throws Exception {
        configRepo.checkin(goConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 33), "md5-1"));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 60), "md5-2", "user-2", "13.2", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 55), "md5-3", "user-2", "13.2", new TimeProvider()));

        String configChangesLine1 = "-<cruise schemaVersion='33'>";
        String configChangesLine2 = "+<cruise schemaVersion='60'>";
        String configChangesLine3 = "+<cruise schemaVersion='55'>";

        String actual = configRepo.configChangesFor("md5-2", "md5-1");

        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine2);

        actual = configRepo.configChangesFor("md5-3", "md5-1");
        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine3);
    }

    @Test
    public void shouldShowDiffForAnyTwoCommitSHAs() throws Exception {
        configRepo.checkin(goConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 33), "md5-1"));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 60), "md5-2", "user-2", "13.2", new TimeProvider()));
        configRepo.checkin(new GoConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 55), "md5-3", "user-2", "13.2", new TimeProvider()));

        GoConfigRevisions commits = configRepo.getCommits(10, 0);
        String firstCommitSHA = commits.get(2).getCommitSHA();
        String secondCommitSHA = commits.get(1).getCommitSHA();
        String thirdCommitSHA = commits.get(0).getCommitSHA();

        String configChangesLine1 = "-<cruise schemaVersion='33'>";
        String configChangesLine2 = "+<cruise schemaVersion='60'>";
        String configChangesLine3 = "+<cruise schemaVersion='55'>";

        String actual = configRepo.configChangesForCommits(secondCommitSHA, firstCommitSHA);

        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine2);

        actual = configRepo.configChangesForCommits(thirdCommitSHA, firstCommitSHA);
        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine3);
    }

    @Test
    public void shouldRemoveUnwantedDataFromDiff() throws Exception {
        configRepo.checkin(goConfigRevision(ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 33), "md5-1"));
        String configXml = ConfigFileFixture.configWithPipeline(ConfigFileFixture.SIMPLE_PIPELINE, 60);
        configRepo.checkin(new GoConfigRevision(configXml, "md5-2", "user-2", "13.2", new TimeProvider()));
        String configChangesLine1 = "-<cruise schemaVersion='33'>";
        String configChangesLine2 = "+<cruise schemaVersion='60'>";
        String actual = configRepo.configChangesFor("md5-2", "md5-1");
        assertThat(actual).contains(configChangesLine1);
        assertThat(actual).contains(configChangesLine2);
        assertThat(actual).doesNotContain("--- a/cruise-config.xml");
        assertThat(actual).doesNotContain("+++ b/cruise-config.xml");
    }

    @Test
    public void shouldThrowExceptionIfRevisionNotFound() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        try {
            configRepo.configChangesFor("md5-1", "md5-not-found");
            fail("Should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("There is no config version corresponding to md5: 'md5-not-found'");
        }
        try {
            configRepo.configChangesFor("md5-not-found", "md5-1");
            fail("Should have failed");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("There is no config version corresponding to md5: 'md5-not-found'");
        }
    }

    @Test
    public void shouldCreateBranchForARevCommit() throws Exception {
        configRepo.checkin(goConfigRevision("something", "md5-1"));
        RevCommit revCommit = configRepo.getCurrentRevCommit();
        configRepo.createBranch("branch1", revCommit);
        Ref branch = getBranch("branch1");
        assertThat(branch).isNotNull();
        assertThat(branch.getObjectId()).isEqualTo(revCommit.getId());
    }

    @Test
    public void shouldCommitIntoGivenBranch() throws Exception {
        configRepo.checkin(goConfigRevision("something", "md5-1"));
        RevCommit revCommitOnMaster = configRepo.getCurrentRevCommit();

        String branchName = "branch1";
        configRepo.createBranch(branchName, revCommitOnMaster);

        String newConfigXML = "config-xml";
        GoConfigRevision configRevision = new GoConfigRevision(newConfigXML, "md5", "user", "version", new TimeProvider());
        RevCommit branchRevCommit = configRepo.checkinToBranch(branchName, configRevision);

        assertThat(branchRevCommit).isNotNull();
        assertThat(getLatestConfigAt(branchName)).isEqualTo(newConfigXML);
        assertThat(configRepo.getCurrentRevCommit()).isEqualTo(revCommitOnMaster);
    }

    @Test
    public void shouldMergeNewCommitOnBranchWithHeadWhenThereIsNoConflict() throws Exception {
        String original = "first\nsecond\n";
        String changeOnBranch = "first\nsecond\nthird\n";
        String changeOnMaster = "1st\nsecond\n";
        String oldMd5 = "md5-1";
        configRepo.checkin(goConfigRevision(original, oldMd5));
        configRepo.checkin(goConfigRevision(changeOnMaster, "md5-2"));

        String mergedConfig = configRepo.getConfigMergedWithLatestRevision(goConfigRevision(changeOnBranch, "md5-3"), oldMd5);
        assertThat(mergedConfig).isEqualTo("1st\nsecond\nthird\n");
    }

    @Test
    public void shouldThrowExceptionWhenThereIsMergeConflict() throws Exception {
        String original = "first\nsecond\n";
        String nextUpdate = "1st\nsecond\n";
        String latestUpdate = "2nd\nsecond\n";
        configRepo.checkin(goConfigRevision(original, "md5-1"));
        configRepo.checkin(goConfigRevision(nextUpdate, "md5-2"));
        RevCommit currentRevCommitOnMaster = configRepo.getCurrentRevCommit();
        try {
            configRepo.getConfigMergedWithLatestRevision(goConfigRevision(latestUpdate, "md5-3"), "md5-1");
            fail("should have bombed for merge conflict");
        } catch (ConfigMergeException e) {
            assertThat(e.getMessage()).isEqualTo(ConfigFileHasChangedException.CONFIG_CHANGED_PLEASE_REFRESH);
        }

        List<Ref> branches = getAllBranches();
        assertThat(branches.size()).isEqualTo(1);
        assertThat(branches.get(0).getName().endsWith("master")).isTrue();
        assertThat(configRepo.getCurrentRevCommit()).isEqualTo(currentRevCommitOnMaster);
    }

    @Test
    public void shouldBeOnMasterAndTemporaryBranchesDeletedAfterGettingMergeConfig() throws Exception {
        String original = "first\nsecond\n";
        String nextUpdate = "1st\nsecond\n";
        String latestUpdate = "first\nsecond\nthird\n";
        configRepo.checkin(goConfigRevision(original, "md5-1"));
        configRepo.checkin(goConfigRevision(nextUpdate, "md5-2"));
        RevCommit currentRevCommitOnMaster = configRepo.getCurrentRevCommit();

        String mergedConfig = configRepo.getConfigMergedWithLatestRevision(goConfigRevision(latestUpdate, "md5-3"), "md5-1");

        assertThat(mergedConfig).isEqualTo("1st\nsecond\nthird\n");
        List<Ref> branches = getAllBranches();
        assertThat(branches.size()).isEqualTo(1);
        assertThat(branches.get(0).getName().endsWith("master")).isTrue();
        assertThat(configRepo.getCurrentRevCommit()).isEqualTo(currentRevCommitOnMaster);
    }

    @Test
    public void shouldSwitchToMasterAndDeleteTempBranches() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        configRepo.createBranch(ConfigRepository.BRANCH_AT_HEAD, configRepo.getCurrentRevCommit());
        configRepo.createBranch(ConfigRepository.BRANCH_AT_REVISION, configRepo.getCurrentRevCommit());
        configRepoRawGit.checkout().setName(ConfigRepository.BRANCH_AT_REVISION).call();
        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo(ConfigRepository.BRANCH_AT_REVISION);
        assertThat(configRepoRawGit.branchList().call().size()).isEqualTo(3);
        configRepo.cleanAndResetToMaster();
        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo("master");
        assertThat(configRepoRawGit.branchList().call().size()).isEqualTo(1);
    }

    @Test
    public void shouldCleanAndResetToMasterDuringInitialization() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        configRepo.createBranch(ConfigRepository.BRANCH_AT_REVISION, configRepo.getCurrentRevCommit());
        configRepoRawGit.checkout().setName(ConfigRepository.BRANCH_AT_REVISION).call();
        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo(ConfigRepository.BRANCH_AT_REVISION);

        new ConfigRepository(systemEnvironment).initialize();

        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo("master");
        assertThat(configRepoRawGit.branchList().call().size()).isEqualTo(1);
    }

    @Test
    void shouldCleanButIgnoreMasterResetIfRepoExistsButNoMasterBranch() throws Exception {
        // Start with an empty repo
        assertThat(configRepoRawGit.getRepository().getDirectory().exists()).isTrue();

        ConfigRepository configRepository = new ConfigRepository(systemEnvironment);
        configRepository.initialize();

        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo("master");
        assertThat(configRepoRawGit.branchList().call()).hasSize(0);

        // Ensure we can still use the config repo
        configRepository.checkin(goConfigRevision("v1", "md5-1"));
        assertThat(configRepoRawGit.branchList().call()).hasSize(1);
    }

    @Test
    public void shouldCleanAndResetToMasterOnceMergeFlowIsComplete() throws Exception {
        String original = "first\nsecond\n";
        String changeOnBranch = "first\nsecond\nthird\n";
        String changeOnMaster = "1st\nsecond\n";
        String oldMd5 = "md5-1";
        configRepo.checkin(goConfigRevision(original, oldMd5));
        configRepo.checkin(goConfigRevision(changeOnMaster, "md5-2"));

        configRepo.getConfigMergedWithLatestRevision(goConfigRevision(changeOnBranch, "md5-3"), oldMd5);
        assertThat(configRepoRawGit.getRepository().getBranch()).isEqualTo("master");
        assertThat(configRepoRawGit.branchList().call().size()).isEqualTo(1);
    }

    @Test
    public void shouldPerformGC() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        Long numberOfLooseObjects = (Long) configRepoRawGit.gc().getStatistics().get("sizeOfLooseObjects");
        assertThat(numberOfLooseObjects > 0L).isTrue();
        configRepo.garbageCollect();
        numberOfLooseObjects = (Long) configRepoRawGit.gc().getStatistics().get("sizeOfLooseObjects");
        assertThat(numberOfLooseObjects).isEqualTo(0L);
    }

    @Test
    public void shouldNotPerformGCWhenPeriodicGCIsTurnedOff() throws Exception {
        when(systemEnvironment.get(SystemEnvironment.GO_CONFIG_REPO_PERIODIC_GC)).thenReturn(false);
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        Long numberOfLooseObjectsOld = (Long) configRepoRawGit.gc().getStatistics().get("sizeOfLooseObjects");
        configRepo.garbageCollect();
        Long numberOfLooseObjectsNow = (Long) configRepoRawGit.gc().getStatistics().get("sizeOfLooseObjects");
        assertThat(numberOfLooseObjectsNow).isEqualTo(numberOfLooseObjectsOld);
    }

    @Test
    public void shouldGetLooseObjectCount() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        Long numberOfLooseObjects = (Long) configRepoRawGit.gc().getStatistics().get("numberOfLooseObjects");
        assertThat(configRepo.getLooseObjectCount()).isEqualTo(numberOfLooseObjects);
    }


    @Test
    public void shouldReturnNumberOfCommitsOnMaster() throws Exception {
        configRepo.checkin(goConfigRevision("v1", "md5-1"));
        configRepo.checkin(goConfigRevision("v2", "md5-2"));
        assertThat(configRepo.commitCountOnMaster()).isEqualTo(2L);
    }

    @Test
    public void shouldStripTillLastOccurrenceOfGivenString() {
        assertThat(ConfigRepository.stripTillLastOccurrenceOf("HelloWorld@@\\nfoobar\\nquux@@keep_this", "@@")).isEqualTo("keep_this");
        assertThat(ConfigRepository.stripTillLastOccurrenceOf("HelloWorld", "@@")).isEqualTo("HelloWorld");
        assertThat(ConfigRepository.stripTillLastOccurrenceOf(null, "@@")).isNull();
        assertThat(ConfigRepository.stripTillLastOccurrenceOf("", "@@")).isEqualTo("");
    }

    private GoConfigRevision goConfigRevision(String fileContent, String md5) {
        return new GoConfigRevision(fileContent, md5, "user-1", "13.2", new TimeProvider());
    }

    private String getLatestConfigAt(String branchName) throws GitAPIException {
        configRepoRawGit.checkout().setName(branchName).call();

        String content = configRepo.getCurrentRevision().getContent();

        configRepoRawGit.checkout().setName("master").call();

        return content;
    }

    Ref getBranch(@SuppressWarnings("SameParameterValue") String branchName) throws GitAPIException {
        List<Ref> branches = getAllBranches();
        for (Ref branch : branches) {
            if (branch.getName().endsWith(branchName)) {
                return branch;
            }
        }
        return null;
    }

    private List<Ref> getAllBranches() throws GitAPIException {
        return configRepoRawGit.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
    }
}
