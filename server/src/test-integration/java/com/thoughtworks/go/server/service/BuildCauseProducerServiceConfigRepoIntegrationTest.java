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
package com.thoughtworks.go.server.service;

import com.thoughtworks.go.config.*;
import com.thoughtworks.go.config.materials.Materials;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterial;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterialConfig;
import com.thoughtworks.go.config.materials.git.GitMaterial;
import com.thoughtworks.go.config.materials.mercurial.HgMaterial;
import com.thoughtworks.go.config.materials.mercurial.HgMaterialConfig;
import com.thoughtworks.go.config.remote.ConfigRepoConfig;
import com.thoughtworks.go.config.remote.RepoConfigOrigin;
import com.thoughtworks.go.config.rules.Allow;
import com.thoughtworks.go.domain.MaterialRevisions;
import com.thoughtworks.go.domain.Pipeline;
import com.thoughtworks.go.domain.buildcause.BuildCause;
import com.thoughtworks.go.domain.materials.MaterialConfig;
import com.thoughtworks.go.domain.materials.Modification;
import com.thoughtworks.go.domain.materials.git.GitTestRepo;
import com.thoughtworks.go.helper.ConfigTestRepo;
import com.thoughtworks.go.helper.HgTestRepo;
import com.thoughtworks.go.helper.PipelineConfigMother;
import com.thoughtworks.go.helper.PipelineMother;
import com.thoughtworks.go.server.cronjob.GoDiskSpaceMonitor;
import com.thoughtworks.go.server.dao.DatabaseAccessHelper;
import com.thoughtworks.go.server.dao.PipelineSqlMapDao;
import com.thoughtworks.go.server.domain.PipelineTimeline;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.materials.*;
import com.thoughtworks.go.server.perf.MDUPerformanceLogger;
import com.thoughtworks.go.server.persistence.MaterialRepository;
import com.thoughtworks.go.server.scheduling.BuildCauseProducerService;
import com.thoughtworks.go.server.scheduling.ScheduleHelper;
import com.thoughtworks.go.server.scheduling.ScheduleOptions;
import com.thoughtworks.go.server.service.result.ServerHealthStateOperationResult;
import com.thoughtworks.go.serverhealth.ServerHealthService;
import com.thoughtworks.go.util.ConfigElementImplementationRegistryMother;
import com.thoughtworks.go.util.GoConfigFileHelper;
import com.thoughtworks.go.util.SystemEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class BuildCauseProducerServiceConfigRepoIntegrationTest {
    @Autowired
    private GoConfigDao goConfigDao;
    @Autowired
    private GoConfigService goConfigService;
    @Autowired
    private ServerHealthService serverHealthService;
    @Autowired
    private ScheduleHelper scheduleHelper;
    @Autowired
    private PipelineSqlMapDao pipelineDao;
    @Autowired
    private PipelineTimeline pipelineTimeline;
    @Autowired
    private DatabaseAccessHelper dbHelper;
    @Autowired
    private MaterialDatabaseUpdater materialDatabaseUpdater;
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private MaterialUpdateService materialUpdateService;
    @Autowired
    private GoConfigRepoConfigDataSource goConfigRepoConfigDataSource;
    @Autowired
    private SystemEnvironment systemEnvironment;
    @Autowired
    private MaterialConfigConverter materialConfigConverter;
    @Autowired
    private ConfigCache configCache;
    @Autowired
    private CachedGoConfig cachedGoConfig;
    @Autowired
    private PipelineScheduleQueue pipelineScheduleQueue;
    @Autowired
    private PipelineScheduler buildCauseProducer;
    @Autowired
    private BuildCauseProducerService buildCauseProducerService;
    @Autowired
    private MaterialChecker materialChecker;
    @Autowired
    private MaintenanceModeService maintenanceModeService;

    @Autowired
    private MaterialUpdateCompletedTopic topic;

    private GoDiskSpaceMonitor goDiskSpaceMonitor;

    private static final GoConfigFileHelper configHelper = new GoConfigFileHelper();

    private MagicalGoConfigXmlWriter xmlWriter;

    private ConfigTestRepo configTestRepo;
    private DiskSpaceSimulator diskSpaceSimulator;
    private HgTestRepo hgRepo;
    private HgMaterialConfig materialConfig;
    private MDUPerformanceLogger logger;
    private HgMaterial material;
    private PipelineConfig pipelineConfig;
    MaterialRevisions firstRevisions;
    private String PIPELINE_NAME;
    String fileName = "pipe1.gocd.xml";

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws Exception {

        diskSpaceSimulator = new DiskSpaceSimulator();
        hgRepo = new HgTestRepo("testHgRepo", tempDir);

        dbHelper.onSetUp();
        configHelper.usingCruiseConfigDao(goConfigDao);
        configHelper.onSetUp();

        materialConfig = hgRepo.materialConfig();
        ConfigRepoConfig config = ConfigRepoConfig.createConfigRepoConfig(materialConfig, "gocd-xml", "gocd-id");
        config.getRules().add(new Allow("refer", "*", "*"));
        configHelper.addConfigRepo(config);

        logger = mock(MDUPerformanceLogger.class);

        TestingEmailSender emailSender = new TestingEmailSender();
        SystemDiskSpaceChecker mockDiskSpaceChecker = mock(SystemDiskSpaceChecker.class);
        StageService stageService = mock(StageService.class);
        ConfigDbStateRepository configDbStateRepository = mock(ConfigDbStateRepository.class);
        goDiskSpaceMonitor = new GoDiskSpaceMonitor(goConfigService, systemEnvironment,
                serverHealthService, emailSender, mockDiskSpaceChecker, mock(ArtifactsService.class),
                stageService, configDbStateRepository);
        goDiskSpaceMonitor.initialize();

        xmlWriter = new MagicalGoConfigXmlWriter(configCache, ConfigElementImplementationRegistryMother.withNoPlugins());
        configTestRepo = new ConfigTestRepo(hgRepo, xmlWriter);
        this.material = (HgMaterial) materialConfigConverter.toMaterial(materialConfig);

        pipelineConfig = PipelineConfigMother.createPipelineConfigWithStages("pipe1", "build", "test");
        pipelineConfig.materialConfigs().clear();
        pipelineConfig.materialConfigs().add(materialConfig);
        PIPELINE_NAME = CaseInsensitiveString.str(pipelineConfig.name());

        configTestRepo.addPipelineToRepositoryAndPush(fileName, pipelineConfig);

        materialUpdateService.updateMaterial(material);
        // time for messages to pass through all services
        waitForMaterialNotInProgress();

        pipelineConfig = goConfigService.pipelineConfigNamed(pipelineConfig.name());

        pipelineScheduleQueue.clear();

        //check test setup
        Materials materials = materialConfigConverter.toMaterials(pipelineConfig.materialConfigs());
        MaterialRevisions peggedRevisions = new MaterialRevisions();
        firstRevisions = materialChecker.findLatestRevisions(peggedRevisions, materials);
        assertThat(firstRevisions.isMissingModifications()).isFalse();
    }

    @AfterEach
    public void teardown() throws Exception {
        diskSpaceSimulator.onTearDown();
        dbHelper.onTearDown();
        pipelineScheduleQueue.clear();
        configHelper.onTearDown();
    }

    private void waitForMaterialNotInProgress() throws InterruptedException {
        // time for messages to pass through all services

        int i = 0;
        while (materialUpdateService.isInProgress(material)) {
            Thread.sleep(100);
            if (i++ > 100)
                fail("material is hung - more than 10 seconds in progress");
        }
    }

    @Test
    public void shouldSchedulePipelineWhenManuallyTriggered() throws Exception {
        configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");
    }

    @Test
    public void shouldSchedulePipeline() throws Exception {
        configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        buildCauseProducerService.autoSchedulePipeline(PIPELINE_NAME, new ServerHealthStateOperationResult(), 123);
        assertThat(scheduleHelper.waitForAnyScheduled(5).keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
    }

    @Test
    public void shouldNotSchedulePipelineWhenPartIsInvalid() throws Exception {
        configTestRepo.addCodeToRepositoryAndPush(fileName, "added broken config file", "bad bad config");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        assertThat(goConfigRepoConfigDataSource.latestParseHasFailedForMaterial(material.config())).isTrue();

        buildCauseProducerService.autoSchedulePipeline(PIPELINE_NAME, new ServerHealthStateOperationResult(), 123);
        scheduleHelper.waitForNotScheduled(5, PIPELINE_NAME);
    }

    @Test
    public void shouldSchedulePipelineWhenPartIsInvalid_AndManuallyTriggered() throws Exception {
        List<Modification> lastPush = configTestRepo.addCodeToRepositoryAndPush(fileName, "added broken config file", "bad bad config");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        assertThat(goConfigRepoConfigDataSource.latestParseHasFailedForMaterial(material.config())).isTrue();

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        PipelineConfig pipelineConfigAfterSchedule = goConfigService.pipelineConfigNamed(pipelineConfig.name());
        RepoConfigOrigin configOriginAfterSchedule = (RepoConfigOrigin) pipelineConfigAfterSchedule.getOrigin();

        String lastValidPushedRevision = this.firstRevisions.latestRevision();
        assertThat(configOriginAfterSchedule.getRevision()).isEqualTo(lastValidPushedRevision);
        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(lastPush.get(0).getRevision());
    }

    @Test
    public void shouldNotSchedulePipelineWhenConfigAndMaterialRevisionsMismatch() throws Exception {
        // we will use this worker to force material update without updating config
        MaterialUpdateListener byPassWorker = new MaterialUpdateListener(topic, materialDatabaseUpdater, logger, goDiskSpaceMonitor, maintenanceModeService);
        List<Modification> mod = configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");
        byPassWorker.onMessage(new MaterialUpdateMessage(material, 123));
        //now db should have been updated, but config is still old
        RepoConfigOrigin configOrigin = (RepoConfigOrigin) goConfigService.pipelineConfigNamed(new CaseInsensitiveString(PIPELINE_NAME)).getOrigin();
        assertThat(configOrigin.getRevision()).isEqualTo(firstRevisions.latestRevision());

        buildCauseProducerService.autoSchedulePipeline(PIPELINE_NAME, new ServerHealthStateOperationResult(), 123);
        scheduleHelper.waitForNotScheduled(5, PIPELINE_NAME);
    }

    @Test
    public void shouldSchedulePipelineWhenConfigAndUpstreamConfigMatch() throws Exception {
        BuildCause buildCause = BuildCause.createWithModifications(firstRevisions, "Rick Sanchez");
        Pipeline x = PipelineMother.schedule(pipelineConfig, buildCause);
        x = pipelineDao.saveWithStages(x);
        dbHelper.passStage(x.getStages().first());

        DependencyMaterialConfig upstreamMaterialConfig = new DependencyMaterialConfig(new CaseInsensitiveString(PIPELINE_NAME), new CaseInsensitiveString(x.getStages().first().getName()));
        String downstreamPipelineName = "pipe2";
        PipelineConfig downstreamConfig = PipelineConfigMother.createPipelineConfigWithStages(downstreamPipelineName, "build", "blah");
        downstreamConfig.materialConfigs().clear();
        downstreamConfig.materialConfigs().add(materialConfig);
        downstreamConfig.materialConfigs().add(upstreamMaterialConfig);

        configTestRepo.addPipelineToRepositoryAndPush("pipe2.gocd.xml", downstreamConfig);
        DependencyMaterial dependencyMaterial = (DependencyMaterial) materialConfigConverter.toMaterial(upstreamMaterialConfig);

        materialUpdateService.updateMaterial(material);
        materialUpdateService.updateMaterial(dependencyMaterial);
        waitForMaterialNotInProgress();
        pipelineTimeline.update();

        configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");

        RepoConfigOrigin configOrigin = (RepoConfigOrigin) goConfigService.pipelineConfigNamed(new CaseInsensitiveString(PIPELINE_NAME)).getOrigin();
        RepoConfigOrigin upstreamOrigin = (RepoConfigOrigin) goConfigService.pipelineConfigNamed(new CaseInsensitiveString(downstreamPipelineName)).getOrigin();
        assertThat(configOrigin).isEqualTo(upstreamOrigin);

        scheduleHelper.autoSchedulePipelinesWithRealMaterials(downstreamPipelineName);
        scheduleHelper.waitForAnyScheduled(5);

        downstreamConfig = goConfigService.pipelineConfigNamed(downstreamConfig.name());

        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).contains(new CaseInsensitiveString(downstreamPipelineName));
        BuildCause downstreamBuildCause = pipelineScheduleQueue.toBeScheduled().get(new CaseInsensitiveString(downstreamPipelineName));
        assertThat(downstreamBuildCause.getMaterialRevisions().getRevisions().size()).isEqualTo(2);
        assertThat(buildCause.pipelineConfigAndMaterialRevisionMatch(downstreamConfig)).isFalse();
    }

    @Test
    // unfortunately there is no way to know why revisions would mismatch during manual trigger.
    // We already let all manual triggers to bypass revision match check
    public void shouldSchedulePipelineWhenConfigAndMaterialRevisionsMismatch_AndManuallyTriggered() throws Exception {
        // we will use this worker to force material update without updating config
        MaterialUpdateListener byPassWorker = new MaterialUpdateListener(topic, materialDatabaseUpdater, logger, goDiskSpaceMonitor, maintenanceModeService);
        List<Modification> lastPush = configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");
        byPassWorker.onMessage(new MaterialUpdateMessage(material, 123));
        //now db should have been updated, but config is still old
        RepoConfigOrigin configOrigin = (RepoConfigOrigin) goConfigService.pipelineConfigNamed(new CaseInsensitiveString(PIPELINE_NAME)).getOrigin();
        assertThat(configOrigin.getRevision()).isEqualTo(firstRevisions.latestRevision());

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(lastPush.get(0).getRevision());
    }


    @Test
    public void shouldReloadPipelineConfigurationWhenManuallyTriggered() throws Exception {
        // we change configuration of the pipeline by pushing new stage to config repo
        pipelineConfig = PipelineConfigMother.createPipelineConfigWithStages("pipe1", "build", "test", "newStage");
        pipelineConfig.materialConfigs().clear();
        pipelineConfig.materialConfigs().add(materialConfig);

        List<Modification> mod = configTestRepo.addPipelineToRepositoryAndPush(fileName, pipelineConfig);

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        PipelineConfig pipelineConfigAfterSchedule = goConfigService.pipelineConfigNamed(pipelineConfig.name());
        RepoConfigOrigin configOriginAfterSchedule = (RepoConfigOrigin) pipelineConfigAfterSchedule.getOrigin();

        String lastPushedRevision = mod.get(0).getRevision();
        assertThat(configOriginAfterSchedule.getRevision()).isEqualTo(lastPushedRevision);
        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(lastPushedRevision);
    }

    @Test
    public void shouldNotScheduleWhenPipelineRemovedFromConfigRepoWhenManuallyTriggered() throws Exception {
        configTestRepo.addCodeToRepositoryAndPush(fileName, "removed pipeline from configuration",
                """
                        <?xml version="1.0" encoding="utf-8"?>
                        <cruise xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="cruise-config.xsd" schemaVersion="38">
                        </cruise>""");

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        waitForMaterialNotInProgress();
        // config is correct
        cachedGoConfig.throwExceptionIfExists();
        assertThat(pipelineScheduleQueue.toBeScheduled().keySet()).doesNotContain(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(goConfigService.hasPipelineNamed(pipelineConfig.name())).isFalse();
    }


    @Test
    public void shouldReloadPipelineConfigurationAndUpdateNewMaterialWhenManuallyTriggered(@TempDir Path tempDir) throws Exception {
        GitTestRepo otherGitRepo = new GitTestRepo(tempDir);

        pipelineConfig = PipelineConfigMother.createPipelineConfigWithStages("pipe1", "build", "test");
        pipelineConfig.materialConfigs().clear();
        materialConfig = hgRepo.createMaterialConfig("dest1");
        materialConfig.setAutoUpdate(true);
        pipelineConfig.materialConfigs().add(materialConfig);
        // new material is added
        GitMaterial gitMaterial = otherGitRepo.createMaterial("dest2");
        gitMaterial.setAutoUpdate(true);
        MaterialConfig otherMaterialConfig = gitMaterial.config();
        otherMaterialConfig.setAutoUpdate(true);
        pipelineConfig.materialConfigs().add(otherMaterialConfig);

        List<Modification> mod = configTestRepo.addPipelineToRepositoryAndPush(fileName, pipelineConfig);

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        cachedGoConfig.throwExceptionIfExists();

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(20);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        PipelineConfig pipelineConfigAfterSchedule = goConfigService.pipelineConfigNamed(pipelineConfig.name());
        RepoConfigOrigin configOriginAfterSchedule = (RepoConfigOrigin) pipelineConfigAfterSchedule.getOrigin();

        String lastPushedRevision = mod.get(0).getRevision();
        assertThat(configOriginAfterSchedule.getRevision()).isEqualTo(lastPushedRevision);
        assertThat(pipelineConfig.materialConfigs()).contains(otherMaterialConfig);
        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(lastPushedRevision);

        // update of committed material happened during manual trigger
        MaterialRevisions modificationsInDb = materialRepository.findLatestModification(gitMaterial);
        assertThat(modificationsInDb.latestRevision()).isEqualTo(otherGitRepo.latestModification().get(0).getRevision());
    }


    @Test
    public void shouldSchedulePipelineRerunWithSpecifiedRevisions() throws Exception {
        List<Modification> firstBuildModifications = configTestRepo.addCodeToRepositoryAndPush("a.java", "added first code file", "some java code");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();
        cachedGoConfig.throwExceptionIfExists();

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        cachedGoConfig.throwExceptionIfExists();

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        List<Modification> secondBuildModifications = configTestRepo.addCodeToRepositoryAndPush("a.java", "added second code file", "some java code");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        pipelineScheduleQueue.clear();

        // revision will be older by 1 commit -
        // formally this is scm-config-consistency violation but we let this schedule because of manual trigger
        String explicitRevision = firstBuildModifications.get(0).getRevision();
        revisions.put(materialConfig.getPipelineUniqueFingerprint(), explicitRevision);
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, new Username(new CaseInsensitiveString("Admin")),
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        cachedGoConfig.throwExceptionIfExists();

        afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by Admin");

        PipelineConfig pipelineConfigAfterSchedule = goConfigService.pipelineConfigNamed(pipelineConfig.name());
        RepoConfigOrigin configOriginAfterSchedule = (RepoConfigOrigin) pipelineConfigAfterSchedule.getOrigin();

        String lastPushedRevision = secondBuildModifications.get(0).getRevision();
        assertThat(configOriginAfterSchedule.getRevision()).isEqualTo(lastPushedRevision);
        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(explicitRevision);
    }

    @Test
    public void shouldSchedulePipelineWithSameMaterialIn2DestinationsWhenManuallyTriggered_WithSpecifiedRevisions() throws Exception {
        pipelineConfig = PipelineConfigMother.createPipelineConfigWithStages("pipe1", "build", "test");
        pipelineConfig.materialConfigs().clear();
        materialConfig = hgRepo.createMaterialConfig("dest1");
        materialConfig.setAutoUpdate(true);
        // new material is added
        MaterialConfig otherMaterialConfig = hgRepo.createMaterialConfig("dest2");
        otherMaterialConfig.setAutoUpdate(true);

        pipelineConfig.materialConfigs().add(materialConfig);
        pipelineConfig.materialConfigs().add(otherMaterialConfig);

        List<Modification> firstBuildModifications = configTestRepo.addPipelineToRepositoryAndPush(fileName, pipelineConfig);
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();
        cachedGoConfig.throwExceptionIfExists();

        final Map<String, String> revisions = new HashMap<>();
        final Map<String, String> environmentVariables = new HashMap<>();
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, Username.ANONYMOUS,
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        cachedGoConfig.throwExceptionIfExists();

        Map<CaseInsensitiveString, BuildCause> afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        BuildCause cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by anonymous");

        List<Modification> secondBuildModifications = configTestRepo.addCodeToRepositoryAndPush("a.java", "added code file", "some java code");
        materialUpdateService.updateMaterial(material);
        waitForMaterialNotInProgress();

        pipelineScheduleQueue.clear();

        // revision in dest 1 will be older by 1 commit - this is kind of scm-config-consistency violation
        String explicitRevision = firstBuildModifications.get(0).getRevision();
        revisions.put(materialConfig.getPipelineUniqueFingerprint(), explicitRevision);
        buildCauseProducer.manualProduceBuildCauseAndSave(PIPELINE_NAME, new Username(new CaseInsensitiveString("Admin")),
                new ScheduleOptions(revisions, environmentVariables, new HashMap<>()), new ServerHealthStateOperationResult());
        cachedGoConfig.throwExceptionIfExists();

        afterLoad = scheduleHelper.waitForAnyScheduled(5);
        assertThat(afterLoad.keySet()).contains(new CaseInsensitiveString(PIPELINE_NAME));
        cause = afterLoad.get(new CaseInsensitiveString(PIPELINE_NAME));
        assertThat(cause.getBuildCauseMessage()).contains("Forced by Admin");

        PipelineConfig pipelineConfigAfterSchedule = goConfigService.pipelineConfigNamed(pipelineConfig.name());
        RepoConfigOrigin configOriginAfterSchedule = (RepoConfigOrigin) pipelineConfigAfterSchedule.getOrigin();

        String lastPushedRevision = secondBuildModifications.get(0).getRevision();
        assertThat(configOriginAfterSchedule.getRevision()).isEqualTo(lastPushedRevision);
        assertThat(pipelineConfigAfterSchedule.materialConfigs()).contains(otherMaterialConfig);
        assertThat(cause.getMaterialRevisions().latestRevision()).isEqualTo(explicitRevision);
    }

}
