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
package com.thoughtworks.go.config;

import com.thoughtworks.go.config.materials.MaterialConfigs;
import com.thoughtworks.go.config.materials.PackageMaterialConfig;
import com.thoughtworks.go.config.materials.PluggableSCMMaterialConfig;
import com.thoughtworks.go.config.materials.ScmMaterialConfig;
import com.thoughtworks.go.config.materials.dependency.DependencyMaterialConfig;
import com.thoughtworks.go.config.materials.git.GitMaterialConfig;
import com.thoughtworks.go.config.materials.perforce.P4MaterialConfig;
import com.thoughtworks.go.config.materials.svn.SvnMaterialConfig;
import com.thoughtworks.go.config.merge.MergeEnvironmentConfig;
import com.thoughtworks.go.config.merge.MergePipelineConfigs;
import com.thoughtworks.go.config.remote.*;
import com.thoughtworks.go.domain.ConfigErrors;
import com.thoughtworks.go.domain.Task;
import com.thoughtworks.go.domain.config.Configuration;
import com.thoughtworks.go.domain.config.ConfigurationKey;
import com.thoughtworks.go.domain.config.ConfigurationProperty;
import com.thoughtworks.go.domain.config.ConfigurationValue;
import com.thoughtworks.go.domain.materials.MaterialConfig;
import com.thoughtworks.go.domain.packagerepository.*;
import com.thoughtworks.go.domain.scm.SCM;
import com.thoughtworks.go.domain.scm.SCMMother;
import com.thoughtworks.go.helper.*;
import com.thoughtworks.go.security.GoCipher;
import com.thoughtworks.go.util.FunctionalUtils;
import com.thoughtworks.go.util.ReflectionUtil;
import com.thoughtworks.go.util.command.UrlArgument;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.thoughtworks.go.helper.MaterialConfigsMother.*;
import static com.thoughtworks.go.helper.PipelineConfigMother.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public abstract class CruiseConfigTestBase implements FunctionalUtils {
    public GoConfigMother goConfigMother;
    protected BasicPipelineConfigs pipelines;
    protected CruiseConfig cruiseConfig;

    protected abstract CruiseConfig createCruiseConfig(BasicPipelineConfigs pipelineConfigs);

    protected abstract BasicCruiseConfig createCruiseConfig();

    protected PartialConfig createPartial() {
        return PartialConfigMother.withPipelineInGroup("remote-pipe-1", "remote_group");
    }

    @Test
    public void cloneForValidationShouldKeepProposedPartials() {
        cruiseConfig.setPartials(List.of(createPartial()));
        assertThat(cruiseConfig.getPartials().size()).isEqualTo(1);
        cruiseConfig = cruiseConfig.cloneForValidation();
        assertThat(cruiseConfig.getPartials().size()).isEqualTo(1);
    }

    @Test
    public void shouldLoadPasswordForGivenMaterialFingerprint() {
        MaterialConfig svnConfig = svn("url", "loser", "boozer", true);
        PipelineConfig one = PipelineConfigMother.pipelineConfig("one", svnConfig, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-1", one);

        P4MaterialConfig p4One = p4("server_and_port", "outside_the_window");
        p4One.setPassword("abcdef");
        PipelineConfig two = PipelineConfigMother.pipelineConfig("two", p4One, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-2", two);

        P4MaterialConfig p4Two = p4("port_and_server", "inside_yourself");
        p4Two.setPassword("fedcba");
        PipelineConfig three = PipelineConfigMother.pipelineConfig("three", p4Two, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-3", three);

        assertThat(cruiseConfig.materialConfigFor(svnConfig.getFingerprint())).isEqualTo(svnConfig);
        assertThat(cruiseConfig.materialConfigFor(p4One.getFingerprint())).isEqualTo(p4One);
        assertThat(cruiseConfig.materialConfigFor(p4Two.getFingerprint())).isEqualTo(p4Two);
        assertThat(cruiseConfig.materialConfigFor("some_crazy_fingerprint")).isNull();
    }

    @Test
    public void canFindMaterialConfigForUnderGivenPipelineWithMaterialFingerprint() {
        MaterialConfig fullClone = git("url", "master", false);
        PipelineConfig one = PipelineConfigMother.pipelineConfig("one", fullClone, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-1", one);

        MaterialConfig shallowClone = git("url", "master", true);
        PipelineConfig two = PipelineConfigMother.pipelineConfig("two", shallowClone, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-2", two);

        MaterialConfig others = git("bar", "master", true);
        PipelineConfig three = PipelineConfigMother.pipelineConfig("three", others, new JobConfigs(new JobConfig("job")));
        cruiseConfig.addPipeline("group-3", three);

        String fingerprint = git("url", "master").getFingerprint();

        assertThat(((GitMaterialConfig) cruiseConfig.materialConfigFor(one.name(), fingerprint)).isShallowClone()).isFalse();
        assertThat(((GitMaterialConfig) cruiseConfig.materialConfigFor(two.name(), fingerprint)).isShallowClone()).isTrue();
        assertThat(cruiseConfig.materialConfigFor(three.name(), fingerprint)).isNull();
    }

    @Test
    public void shouldFindBuildPlanWithStages() {
        try {
            cruiseConfig.jobConfigByName("cetaceans", "whales", "right whale", true);
            fail("Expected not to find right whale in stage whales in pipeline cetaceans");
        } catch (RuntimeException ex) {
            // ignore
        }

        addPipeline("cetaceans", "whales", jobConfig("whale"));

        try {
            cruiseConfig.jobConfigByName("cetaceans", "whales", "dolphin", true);
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("Job [dolphin] is not found in pipeline [cetaceans] stage [whales].");
        }

        try {
            cruiseConfig.jobConfigByName("primates", "whales", "dolphin", true);
            fail("Expected not to find primates in stage whales in pipeline cetaceans");
        } catch (RuntimeException ex) {
            // ignore
        }
        JobConfig plan = jobConfig("baboon");
        addPipeline("primates", "apes", plan);
        assertThat(cruiseConfig.jobConfigByName("primates", "apes", "baboon", true)).isEqualTo(plan);
    }

    @Test
    public void shouldFindNextStage() {
        addPipelineWithStages("mingle", "dev", jobConfig("ut"), jobConfig("ft"));
        assertThat(cruiseConfig.hasNextStage(new CaseInsensitiveString("mingle"), new CaseInsensitiveString("dev"))).isTrue();
        StageConfig nextStage = cruiseConfig.nextStage(new CaseInsensitiveString("mingle"), new CaseInsensitiveString("dev"));
        assertThat(nextStage.name()).isEqualTo(new CaseInsensitiveString("dev2"));
        assertThat(cruiseConfig.hasNextStage(new CaseInsensitiveString("mingle"), nextStage.name())).isFalse();
    }

    @Test
    public void shouldFindPreviousStage() {
        addPipelineWithStages("mingle", "dev", jobConfig("ut"), jobConfig("ft"));
        assertThat(cruiseConfig.hasPreviousStage(new CaseInsensitiveString("mingle"), new CaseInsensitiveString("dev2"))).isTrue();
        StageConfig previousStage = cruiseConfig.previousStage(new CaseInsensitiveString("mingle"), new CaseInsensitiveString("dev2"));
        assertThat(previousStage.name()).isEqualTo(new CaseInsensitiveString("dev"));
        assertThat(cruiseConfig.hasPreviousStage(new CaseInsensitiveString("mingle"), previousStage.name())).isFalse();
    }

    @Test
    public void shouldKnowWhenBuildPlanNotInConfigFile() {
        pipelines.add(createPipelineConfig("pipeline", "stage", "build1", "build2"));
        assertThat(cruiseConfig.hasBuildPlan(new CaseInsensitiveString("pipeline"), new CaseInsensitiveString("stage"), "build1", true)).isTrue();
        assertThat(cruiseConfig.hasBuildPlan(new CaseInsensitiveString("pipeline"), new CaseInsensitiveString("stage"), "build2", true)).isTrue();
        assertThat(cruiseConfig.hasBuildPlan(new CaseInsensitiveString("pipeline"), new CaseInsensitiveString("stage"), "build3", true)).isFalse();
    }

    @Test
    public void shouldTellIfSMTPIsEnabled() {
        assertThat(cruiseConfig.isSmtpEnabled()).isFalse();

        MailHost mailHost = new MailHost("abc", 12, "admin", "p", true, true, "anc@mail.com", "anc@mail.com");
        cruiseConfig.setServerConfig(new ServerConfig(null, mailHost, null, null));

        cruiseConfig.server().updateMailHost(mailHost);
        assertThat(cruiseConfig.isSmtpEnabled()).isTrue();
    }

    @Test
    public void shouldReturnAMapOfAllTemplateNamesWithAssociatedPipelines() {
        PipelineTemplateConfig template = template("first_template");
        PipelineConfig pipelineConfig1 = PipelineConfigMother.pipelineConfig("first");
        pipelineConfig1.clear();
        pipelineConfig1.setTemplateName(new CaseInsensitiveString("first_template"));
        pipelineConfig1.usingTemplate(template);

        PipelineConfig pipelineConfig2 = PipelineConfigMother.pipelineConfig("second");
        pipelineConfig2.clear();
        pipelineConfig2.setTemplateName(new CaseInsensitiveString("FIRST_template"));
        pipelineConfig2.usingTemplate(template);

        PipelineConfig pipelineConfigWithoutTemplate = PipelineConfigMother.pipelineConfig("third");

        BasicPipelineConfigs pipelineConfigs = new BasicPipelineConfigs(pipelineConfig1, pipelineConfig2, pipelineConfigWithoutTemplate);
        pipelineConfigs.setOrigin(new FileConfigOrigin());
        CruiseConfig cruiseConfig = createCruiseConfig(pipelineConfigs);

        cruiseConfig.addTemplate(template);
        SecurityConfig securityConfig = new SecurityConfig();
        securityConfig.adminsConfig().add(new AdminUser(new CaseInsensitiveString("root")));
        cruiseConfig.server().useSecurity(securityConfig);

        Map<CaseInsensitiveString, Map<CaseInsensitiveString, Authorization>> allTemplatesWithAssociatedPipelines = cruiseConfig.templatesWithAssociatedPipelines();

        assertThat(allTemplatesWithAssociatedPipelines.size()).isEqualTo(1);
        Map<CaseInsensitiveString, Map<CaseInsensitiveString, Authorization>> expectedTemplatesMap = new HashMap<>();
        expectedTemplatesMap.put(new CaseInsensitiveString("first_template"), new HashMap<>());
        expectedTemplatesMap.get(new CaseInsensitiveString("first_template")).put(new CaseInsensitiveString("first"), new Authorization());
        expectedTemplatesMap.get(new CaseInsensitiveString("first_template")).put(new CaseInsensitiveString("second"), new Authorization());
        assertThat(allTemplatesWithAssociatedPipelines).isEqualTo(expectedTemplatesMap);
    }

    private PipelineTemplateConfig template(final String name) {
        return new PipelineTemplateConfig(new CaseInsensitiveString(name), StageConfigMother.stageConfig("some_stage"));
    }

    @Test
    public void shouldThrowExceptionWhenThereIsNoGroup() {
        CruiseConfig config = createCruiseConfig();
        try {
            config.isInFirstGroup(new CaseInsensitiveString("any-pipeline"));
            fail("should throw exception when there is no group");
        } catch (Exception e) {
            assertThat(e.getMessage()).isEqualTo("No pipeline group defined yet!");
        }
    }

    @Test
    public void shouldOfferAllTasksToVisitors() {
        CruiseConfig config = createCruiseConfig();
        Task task1 = new ExecTask("ls", "-a", "");
        Task task2 = new AntTask();
        setupJobWithTasks(config, task1, task2);

        final List<Task> tasksVisited = new ArrayList<>();
        config.accept((pipelineConfig, stageConfig, jobConfig, task) -> tasksVisited.add(task));

        assertThat(tasksVisited.size()).isEqualTo(3);
        assertThat(tasksVisited.get(0)).isEqualTo(task2);
        assertThat(tasksVisited.get(1)).isEqualTo(task1);
        assertThat(tasksVisited.get(2)).isEqualTo(task2);
    }

    @Test
    public void shouldReturnTrueIfThereAreTwoPipelineGroups() {
        CruiseConfig config = goConfigMother.cruiseConfigWithTwoPipelineGroups();
        assertThat(config.hasMultiplePipelineGroups()).isTrue();
    }

    @Test
    public void shouldReturnFalseIfThereIsOnePipelineGroup() {
        CruiseConfig config = goConfigMother.cruiseConfigWithOnePipelineGroup();
        assertThat(config.hasMultiplePipelineGroups()).isFalse();
    }

    @Test
    public void shouldFindDownstreamPipelines() {
        CruiseConfig config = GoConfigMother.defaultCruiseConfig();
        goConfigMother.addPipeline(config, "pipeline-1", "stage-1", "job-1");
        PipelineConfig pipeline2 = goConfigMother.addPipeline(config, "pipeline-2", "stage-2", "job-2");
        PipelineConfig pipeline3 = goConfigMother.addPipeline(config, "pipeline-3", "stage-3", "job-3");
        goConfigMother.setDependencyOn(config, pipeline2, "pipeline-1", "stage-1");
        goConfigMother.setDependencyOn(config, pipeline3, "pipeline-1", "stage-1");
        Iterable<PipelineConfig> downstream = config.getDownstreamPipelines("pipeline-1");
        assertThat(downstream).contains(pipeline2);
        assertThat(downstream).contains(pipeline3);
    }

    @Test
    public void shouldReturnFalseForEmptyCruiseConfig() {
        CruiseConfig config = createCruiseConfig();
        assertThat(config.hasMultiplePipelineGroups()).isFalse();
    }

    @Test
    public void shouldReturnFalseIfNoMailHost() {
        assertThat(createCruiseConfig().isMailHostConfigured()).isFalse();
    }

    @Test
    public void shouldReturnTrueIfMailHostIsConfigured() {
        MailHost mailHost = new MailHost("hostName", 1234, "user", "pass", true, true, "from", "admin@local.com");
        assertThat(GoConfigMother.cruiseConfigWithMailHost(mailHost).isMailHostConfigured()).isTrue();
    }

    @Test
    public void shouldNotLockAPipelineWhenItIsAddedToAnEnvironment() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");
        EnvironmentConfig env = config.addEnvironment("environment");
        env.addPipeline(new CaseInsensitiveString("pipeline-1"));
        assertThat(config.isPipelineLockable("pipeline-1")).isFalse();
    }

    @Test
    public void shouldBeAbleToExplicitlyLockAPipeline() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");
        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        pipelineConfig.lockExplicitly();
        assertThat(config.isPipelineLockable("pipeline-1")).isTrue();
    }

    @Test
    public void shouldCollectAllTheErrorsInTheChildren() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");

        shouldCollectAllTheErrorsInTheChildrenHelper(config);
    }

    protected void shouldCollectAllTheErrorsInTheChildrenHelper(CruiseConfig config) {
        SecurityAuthConfig ldapConfig = new SecurityAuthConfig("ldap", "cd.go.authorization.ldap");
        ldapConfig.errors().add("uri", "invalid ldap uri");
        ldapConfig.errors().add("searchBase", "invalid search base");
        config.server().security().securityAuthConfigs().add(ldapConfig);

        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        pipelineConfig.errors().add("base", "Some base errors");

        P4MaterialConfig p4MaterialConfig = p4("localhost:1999", "view");
        p4MaterialConfig.setConfigAttributes(Map.of(ScmMaterialConfig.FOLDER, "p4_folder"));
        pipelineConfig.addMaterialConfig(p4MaterialConfig);
        p4MaterialConfig.errors().add("materialName", "material name does not follow pattern");

        StageConfig stage = pipelineConfig.first();
        stage.errors().add("role", "Roles must be proper");

        List<ConfigErrors> allErrors = config.validateAfterPreprocess();
        assertThat(allErrors.size()).isEqualTo(5);
        assertThat(allErrors.get(0).on("uri")).isEqualTo("invalid ldap uri");
        assertThat(allErrors.get(0).on("searchBase")).isEqualTo("invalid search base");
        assertThat(allErrors.get(1).on("base")).isEqualTo("Some base errors");
        assertThat(allErrors.get(2).on("role")).isEqualTo("Roles must be proper");
        assertThat(allErrors.get(3).on(ScmMaterialConfig.FOLDER)).isEqualTo("Destination directory is required when a pipeline has multiple SCM materials.");
        assertThat(allErrors.get(4).on("materialName")).isEqualTo("material name does not follow pattern");
    }


    @Test
    public void getAllErrors_shouldCollectAllErrorsInTheChildren() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");

        SecurityAuthConfig ldapConfig = new SecurityAuthConfig("ldap", "cd.go.authorization.ldap");
        ldapConfig.errors().add("uri", "invalid ldap uri");
        ldapConfig.errors().add("searchBase", "invalid search base");
        config.server().security().securityAuthConfigs().add(ldapConfig);

        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        pipelineConfig.errors().add("base", "Some base errors");

        P4MaterialConfig p4MaterialConfig = p4("localhost:1999", "view");
        pipelineConfig.addMaterialConfig(p4MaterialConfig);
        p4MaterialConfig.errors().add("materialName", "material name does not follow pattern");

        StageConfig stage = pipelineConfig.first();
        stage.errors().add("role", "Roles must be proper");

        List<ConfigErrors> allErrors = config.getAllErrors();
        assertThat(allErrors.size()).isEqualTo(4);
        assertThat(allErrors.get(0).on("uri")).isEqualTo("invalid ldap uri");
        assertThat(allErrors.get(0).on("searchBase")).isEqualTo("invalid search base");
        assertThat(allErrors.get(1).on("base")).isEqualTo("Some base errors");
        assertThat(allErrors.get(2).on("role")).isEqualTo("Roles must be proper");
        assertThat(allErrors.get(3).on("materialName")).isEqualTo("material name does not follow pattern");
    }

    @Test
    public void getAllErrors_shouldIgnoreErrorsOnElementToBeSkipped() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");

        SecurityAuthConfig ldapConfig = new SecurityAuthConfig("ldap", "cd.go.authorization.ldap");
        ldapConfig.errors().add("uri", "invalid ldap uri");
        ldapConfig.errors().add("searchBase", "invalid search base");
        config.server().security().securityAuthConfigs().add(ldapConfig);

        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        pipelineConfig.errors().add("base", "Some base errors");

        P4MaterialConfig p4MaterialConfig = p4("localhost:1999", "view");
        pipelineConfig.addMaterialConfig(p4MaterialConfig);
        p4MaterialConfig.errors().add("materialName", "material name does not follow pattern");

        StageConfig stage = pipelineConfig.first();
        stage.errors().add("role", "Roles must be proper");

        List<ConfigErrors> allErrors = config.getAllErrorsExceptFor(p4MaterialConfig);
        assertThat(allErrors.size()).isEqualTo(3);
        assertThat(allErrors.get(0).on("uri")).isEqualTo("invalid ldap uri");
        assertThat(allErrors.get(0).on("searchBase")).isEqualTo("invalid search base");
        assertThat(allErrors.get(1).on("base")).isEqualTo("Some base errors");
        assertThat(allErrors.get(2).on("role")).isEqualTo("Roles must be proper");
    }

    @Test
    public void getAllErrors_shouldRetainAllErrorsWhenNoSubjectGiven() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");

        SecurityAuthConfig ldapConfig = new SecurityAuthConfig("ldap", "cd.go.authorization.ldap");
        ldapConfig.errors().add("uri", "invalid ldap uri");
        ldapConfig.errors().add("searchBase", "invalid search base");
        config.server().security().securityAuthConfigs().add(ldapConfig);

        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        pipelineConfig.errors().add("base", "Some base errors");

        P4MaterialConfig p4MaterialConfig = p4("localhost:1999", "view");
        pipelineConfig.addMaterialConfig(p4MaterialConfig);
        p4MaterialConfig.errors().add("materialName", "material name does not follow pattern");

        StageConfig stage = pipelineConfig.first();
        stage.errors().add("role", "Roles must be proper");

        List<ConfigErrors> allErrors = config.getAllErrorsExceptFor(null);
        assertThat(allErrors.size()).isEqualTo(4);
    }

    @Test
    public void shouldBuildTheValidationContextForAnOnCancelTask() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");
        PipelineConfig pipelineConfig = config.pipelineConfigByName(new CaseInsensitiveString("pipeline-1"));
        StageConfig stageConfig = pipelineConfig.get(0);
        JobConfig jobConfig = stageConfig.getJobs().get(0);
        ExecTask execTask = new ExecTask("ls", "-la", "dir");
        Task mockTask = mock(Task.class);
        when(mockTask.errors()).thenReturn(new ConfigErrors());
        execTask.setCancelTask(mockTask);
        jobConfig.addTask(execTask);

        config.validateAfterPreprocess();

        verify(mockTask).validate(ConfigSaveValidationContext.forChain(
                config,
                config.getGroups(),
                config.getGroups().findGroup("defaultGroup"),
                pipelineConfig,
                stageConfig,
                stageConfig.getJobs(),
                jobConfig,
                jobConfig.getTasks(),
                execTask,
                execTask.onCancelConfig()));
    }

    @Test
    public void shouldNotConsiderEqualObjectsAsSame() {
        MyValidatable foo = new AlwaysEqualMyValidatable();
        MyValidatable bar = new AlwaysEqualMyValidatable();
        foo.innerValidatable = bar;

        GoConfigGraphWalker.Handler handler = mock(GoConfigGraphWalker.Handler.class);

        new GoConfigGraphWalker(foo).walk(handler);

        verify(handler).handle(same(foo), any(ValidationContext.class));
        verify(handler).handle(same(bar), any(ValidationContext.class));
    }

    @Test
    public void shouldIgnoreConstantFieldsWhileCollectingErrorsToAvoidPotentialCycles() {
        CruiseConfig config = GoConfigMother.configWithPipelines("pipeline-1");
        List<ConfigErrors> allErrors = config.validateAfterPreprocess();
        assertThat(allErrors.size()).isEqualTo(0);
    }

    @Test
    public void shouldErrorOutWhenDependsOnItself() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        PipelineConfig pipelineConfig = goConfigMother.addPipeline(cruiseConfig, "pipeline1", "stage", "build");
        goConfigMother.addStageToPipeline(cruiseConfig, "pipeline1", "ft", "build");
        goConfigMother.setDependencyOn(cruiseConfig, pipelineConfig, "pipeline1", "ft");
        cruiseConfig.validate(null);
        ConfigErrors errors = pipelineConfig.materialConfigs().errors();
        assertThat(errors.on("base")).isEqualTo("Circular dependency: pipeline1 <- pipeline1");
    }

    @Test
    public void shouldNotDuplicateErrorWhenPipelineDoesNotExist() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        PipelineConfig pipelineConfig = goConfigMother.addPipeline(cruiseConfig, "pipeline1", "stage", "build");
        PipelineConfig pipelineConfig2 = goConfigMother.addPipeline(cruiseConfig, "pipeline2", "stage", "build");
        goConfigMother.addStageToPipeline(cruiseConfig, "pipeline1", "ft", "build");
        goConfigMother.setDependencyOn(cruiseConfig, pipelineConfig2, "pipeline1", "ft");
        goConfigMother.setDependencyOn(cruiseConfig, pipelineConfig, "invalid", "invalid");
        cruiseConfig.validate(null);
        List<ConfigErrors> allErrors = cruiseConfig.getAllErrors();
        List<String> errors = new ArrayList<>();
        for (ConfigErrors allError : allErrors) {
            errors.addAll(allError.getAllOn("base"));
        }
        assertThat(errors.size()).isEqualTo(1);
        assertThat(errors.get(0)).isEqualTo("Pipeline 'invalid' does not exist. It is used from pipeline 'pipeline1'.");
    }

    @Test
    public void shouldErrorOutWhenTwoPipelinesDependsOnEachOther() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        PipelineConfig pipeline1 = goConfigMother.addPipeline(cruiseConfig, "pipeline1", "stage", "build");
        PipelineConfig pipeline2 = goConfigMother.addPipeline(cruiseConfig, "pipeline2", "stage", "build");
        goConfigMother.setDependencyOn(cruiseConfig, pipeline2, "pipeline1", "stage");
        goConfigMother.setDependencyOn(cruiseConfig, pipeline1, "pipeline2", "stage");

        cruiseConfig.validate(null);

        assertThat(pipeline1.materialConfigs().errors().isEmpty()).isFalse();
        assertThat(pipeline2.materialConfigs().errors().isEmpty()).isFalse();
    }

    @Test
    public void shouldAddPipelineWithoutValidationInAnExistingGroup() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        PipelineConfig pipeline1 = PipelineConfigMother.pipelineConfig("first");
        PipelineConfig pipeline2 = PipelineConfigMother.pipelineConfig("first");
        cruiseConfig.addPipelineWithoutValidation("first-group", pipeline1);

        assertThat(cruiseConfig.getGroups().size()).isEqualTo(1);
        assertThat(cruiseConfig.findGroup("first-group").get(0)).isEqualTo(pipeline1);

        cruiseConfig.addPipelineWithoutValidation("first-group", pipeline2);
        assertThat(cruiseConfig.findGroup("first-group").get(0)).isEqualTo(pipeline1);
        assertThat(cruiseConfig.findGroup("first-group").get(1)).isEqualTo(pipeline2);
    }

    @Test
    public void shouldErrorOutWhenThreePipelinesFormACycle() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        PipelineConfig pipeline1 = goConfigMother.addPipeline(cruiseConfig, "pipeline1", "stage", "build");
        SvnMaterialConfig material = (SvnMaterialConfig) pipeline1.materialConfigs().get(0);
        material.setConfigAttributes(Map.of(ScmMaterialConfig.FOLDER, "svn_dir"));
        P4MaterialConfig p4MaterialConfig = p4("localhost:1999", "view");
        p4MaterialConfig.setConfigAttributes(Map.of(ScmMaterialConfig.FOLDER, "p4_folder"));
        pipeline1.addMaterialConfig(p4MaterialConfig);
        PipelineConfig pipeline2 = goConfigMother.addPipeline(cruiseConfig, "pipeline3", "stage", "build");
        PipelineConfig pipeline3 = goConfigMother.addPipeline(cruiseConfig, "pipeline2", "stage", "build");
        goConfigMother.setDependencyOn(cruiseConfig, pipeline3, "pipeline3", "stage");
        goConfigMother.setDependencyOn(cruiseConfig, pipeline2, "pipeline1", "stage");
        goConfigMother.setDependencyOn(cruiseConfig, pipeline1, "pipeline2", "stage");
        cruiseConfig.validate(null);
        assertThat(pipeline1.materialConfigs().errors().isEmpty()).isFalse();
        assertThat(pipeline2.materialConfigs().errors().isEmpty()).isFalse();
        assertThat(pipeline3.materialConfigs().errors().isEmpty()).isFalse();
    }

    @Test
    public void shouldAllowCleanupOfNonExistentStages() {
        CruiseConfig cruiseConfig = createCruiseConfig();
        assertThat(cruiseConfig.isArtifactCleanupProhibited("foo", "bar")).isFalse();

        PipelineConfig pipelineConfig = PipelineConfigMother.createPipelineConfig("foo-pipeline", "bar-stage", "baz-job");
        cruiseConfig.addPipeline("defaultGrp", pipelineConfig);
        assertThat(cruiseConfig.isArtifactCleanupProhibited("foo-pipeline", "baz-stage")).isFalse();
        assertThat(cruiseConfig.isArtifactCleanupProhibited("foo-pipeline", "bar-stage")).isFalse();

        ReflectionUtil.setField(pipelineConfig.getFirstStageConfig(), "artifactCleanupProhibited", true);
        assertThat(cruiseConfig.isArtifactCleanupProhibited("foo-pipeline", "bar-stage")).isTrue();
        assertThat(cruiseConfig.isArtifactCleanupProhibited("fOO-pipeLINE", "BaR-StagE")).isTrue();
    }

    @Test
    public void shouldReturnDefaultGroupNameIfNoGroupNameIsSpecified() {
        CruiseConfig cfg = createCruiseConfig();
        assertThat(cfg.sanitizedGroupName(null)).isEqualTo(BasicPipelineConfigs.DEFAULT_GROUP);
        cfg.addPipeline("grp1", PipelineConfigMother.pipelineConfig("foo"));
        assertThat(cfg.sanitizedGroupName(null)).isEqualTo(BasicPipelineConfigs.DEFAULT_GROUP);
    }

    @Test
    public void shouldReturnSelectedGroupNameIfGroupNameIsSpecified() {
        CruiseConfig cfg = createCruiseConfig();
        cfg.addPipeline("grp1", PipelineConfigMother.pipelineConfig("foo"));
        assertThat(cfg.sanitizedGroupName("gr1")).isEqualTo("gr1");
    }

    @Test
    public void shouldAddPackageRepository() {
        PackageRepository packageRepository = new PackageRepository();
        cruiseConfig.savePackageRepository(packageRepository);
        assertThat(cruiseConfig.getPackageRepositories().size()).isEqualTo(1);
        assertThat(cruiseConfig.getPackageRepositories().get(0)).isEqualTo(packageRepository);
        assertThat(cruiseConfig.getPackageRepositories().get(0).getId()).isNotNull();
    }

    @Test
    public void shouldUpdatePackageRepository() {
        PackageRepository packageRepository = new PackageRepository();
        packageRepository.setName("old");
        cruiseConfig.savePackageRepository(packageRepository);

        packageRepository.setName("new");
        cruiseConfig.savePackageRepository(packageRepository);

        assertThat(cruiseConfig.getPackageRepositories().size()).isEqualTo(1);
        assertThat(cruiseConfig.getPackageRepositories().get(0)).isEqualTo(packageRepository);
        assertThat(cruiseConfig.getPackageRepositories().get(0).getId()).isNotNull();
        assertThat(cruiseConfig.getPackageRepositories().get(0).getName()).isEqualTo("new");
    }

    @Test
    public void shouldAddPackageDefinitionToGivenRepository() {
        String repoId = "repo-id";
        PackageRepository packageRepository = PackageRepositoryMother.create(repoId, "repo-name", "plugin-id", "1.0", new Configuration());
        PackageDefinition existing = PackageDefinitionMother.create("pkg-1", "pkg1-name", new Configuration(), packageRepository);

        packageRepository.setPackages(new Packages(existing));
        cruiseConfig.setPackageRepositories(new PackageRepositories(packageRepository));

        Configuration configuration = new Configuration();
        configuration.add(new ConfigurationProperty(new ConfigurationKey("key"), new ConfigurationValue("value")));
        configuration.add(new ConfigurationProperty(new ConfigurationKey("key-with-no-value"), new ConfigurationValue("")));
        PackageDefinition packageDefinition = PackageDefinitionMother.create(null, "pkg2-name", configuration, packageRepository);
        cruiseConfig.savePackageDefinition(packageDefinition);

        assertThat(cruiseConfig.getPackageRepositories().size()).isEqualTo(1);
        assertThat(cruiseConfig.getPackageRepositories().get(0).getId()).isEqualTo(repoId);

        assertThat(cruiseConfig.getPackageRepositories().get(0).getPackages().size()).isEqualTo(2);
        assertThat(cruiseConfig.getPackageRepositories().get(0).getPackages().get(0).getId()).isEqualTo(existing.getId());
        PackageDefinition createdPkgDef = cruiseConfig.getPackageRepositories().get(0).getPackages().get(1);
        assertThat(createdPkgDef.getId()).isNotNull();
        assertThat(createdPkgDef.getConfiguration().getProperty("key")).isNotNull();
        assertThat(createdPkgDef.getConfiguration().getProperty("key-with-no-value")).isNull();
    }

    @Test
    public void shouldClearPackageRepositoryConfigurationsWhichAreEmptyWithNoErrors() {
        PackageRepository packageRepository = mock(PackageRepository.class);
        when(packageRepository.isNew()).thenReturn(true);
        cruiseConfig.savePackageRepository(packageRepository);
        verify(packageRepository).clearEmptyConfigurations();
    }

    @Test
    public void shouldRemovePackageRepositoryById() {
        PackageRepository packageRepository = PackageRepositoryMother.create(null, "repo", "pid", "1.3", new Configuration());
        cruiseConfig.savePackageRepository(packageRepository);
        cruiseConfig.removePackageRepository(packageRepository.getId());
        assertThat(cruiseConfig.getPackageRepositories().find(packageRepository.getId())).isNull();
    }

    @Test
    public void shouldDecideIfRepoCanBeDeleted_BasedOnPackageRepositoryBeingUsedByPipelines() {
        PackageRepository repo1 = PackageRepositoryMother.create(null, "repo1", "plugin", "1.3", new Configuration());
        PackageRepository repo2 = PackageRepositoryMother.create(null, "repo2", "plugin", "1.3", new Configuration());
        PackageDefinition packageDefinition = PackageDefinitionMother.create("package", repo2);
        repo2.addPackage(packageDefinition);
        PipelineConfig pipeline = PipelineConfigMother.pipelineConfig("pipeline");
        pipeline.addMaterialConfig(new PackageMaterialConfig(new CaseInsensitiveString("p1"), packageDefinition.getId(), packageDefinition));
        cruiseConfig.addPipeline("existing_group", pipeline);
        cruiseConfig.savePackageRepository(repo1);
        cruiseConfig.savePackageRepository(repo2);
        assertThat(cruiseConfig.canDeletePackageRepository(repo1)).isTrue();
        assertThat(cruiseConfig.canDeletePackageRepository(repo2)).isFalse();
    }

    @Test
    public void shouldDecideIfPluggableSCMMaterialCanBeDeleted_BasedOnPluggableSCMMaterialBeingUsedByPipelines() {
        SCM scmConfigOne = SCMMother.create("scm-id-1");
        SCM scmConfigTwo = SCMMother.create("scm-id-2");
        cruiseConfig.getSCMs().addAll(List.of(scmConfigOne, scmConfigTwo));
        PipelineConfig pipeline = PipelineConfigMother.pipelineConfig("pipeline");
        pipeline.addMaterialConfig(new PluggableSCMMaterialConfig(null, scmConfigOne, null, null, false));
        cruiseConfig.addPipeline("existing_group", pipeline);

        assertThat(cruiseConfig.canDeletePluggableSCMMaterial(scmConfigOne)).isFalse();
        assertThat(cruiseConfig.canDeletePluggableSCMMaterial(scmConfigTwo)).isTrue();
    }

    @Test
    public void shouldReturnConfigRepos() {
        assertNotNull(cruiseConfig.getConfigRepos());
    }

    @Test
    public void shouldReturnTrueWhenHasGroup() {
        assertThat(cruiseConfig.hasPipelineGroup("existing_group")).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenDoesNotHaveGroup() {
        assertThat(cruiseConfig.hasPipelineGroup("non_existing_group")).isFalse();
    }

    @Test
    public void getAllLocalPipelines_shouldReturnPipelinesOnlyFromMainPart() {
        PipelineConfig pipe1 = PipelineConfigMother.pipelineConfig("pipe1");
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), pipe1);
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipeline("pipe2"));

        assertThat(cruiseConfig.getAllLocalPipelineConfigs(false).size()).isEqualTo(1);
        assertThat(cruiseConfig.getAllLocalPipelineConfigs(false)).contains(pipe1);
    }

    @Test
    public void shouldReturnTrueHasPipelinesFrom2Parts() {
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), PipelineConfigMother.pipelineConfig("pipe1"));
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipeline("pipe2"));

        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe1"))).isTrue();
        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe2"))).isTrue();
    }

    @Test
    public void shouldReturnFalseWhenHasNotPipelinesFrom2Parts() {
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), PipelineConfigMother.pipelineConfig("pipe1"));
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipeline("pipe2"));

        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe3"))).isFalse();
    }

    @Test
    public void shouldReturnGroupsFrom2Parts() {
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), PipelineConfigMother.pipelineConfig("pipe1"));
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipelineInGroup("pipe2", "g2"));

        assertThat(cruiseConfig.hasPipelineGroup("g2")).isTrue();
    }

    @Test
    public void shouldAddPipelineToMain() {
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), PipelineConfigMother.pipelineConfig("pipe1"));
        pipelines.setOrigin(new FileConfigOrigin());
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipeline("pipe2"));
        cruiseConfig.addPipeline("group_main", PipelineConfigMother.pipelineConfig("pipe3"));

        assertThat(mainCruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe3"))).isTrue();
        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe3"))).isTrue();

    }

    @Test
    public void shouldGetAllPipelineNamesFromAllParts() {
        pipelines = new BasicPipelineConfigs("group_main", new Authorization(), PipelineConfigMother.pipelineConfig("pipe1"));
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig,
                PartialConfigMother.withPipelineInGroup("pipe2", "g2"), PartialConfigMother.withPipelineInGroup("pipe3", "g3"));

        assertThat(cruiseConfig.getAllPipelineNames()).contains(new CaseInsensitiveString("pipe1"));
        assertThat(cruiseConfig.getAllPipelineNames()).contains(new CaseInsensitiveString("pipe2"));
        assertThat(cruiseConfig.getAllPipelineNames()).contains(new CaseInsensitiveString("pipe3"));
    }

    @Test
    public void shouldGetJobConfigByName() {
        goConfigMother.addPipeline(cruiseConfig, "cruise", "dev", "linux-firefox");
        JobConfig job = cruiseConfig.jobConfigByName("cruise", "dev", "linux-firefox", true);
        assertNotNull(job);
    }

    @Test
    public void shouldReturnAllUniqueSchedulableScmMaterials() {
        final MaterialConfig svnMaterialConfig = svn("http://svn_url_1", "username", "password", false);
        svnMaterialConfig.setAutoUpdate(false);
        final MaterialConfig svnMaterialConfigWithAutoUpdate = svn("http://svn_url_2", "username", "password", false);
        svnMaterialConfigWithAutoUpdate.setAutoUpdate(true);
        final MaterialConfig hgMaterialConfig = hg("http://hg_url", null);
        hgMaterialConfig.setAutoUpdate(false);
        final MaterialConfig gitMaterialConfig = git("http://git_url");
        gitMaterialConfig.setAutoUpdate(false);
        final MaterialConfig tfsMaterialConfig = tfs(mock(GoCipher.class), new UrlArgument("http://tfs_url"), "username", "domain", "password", "project_path");
        tfsMaterialConfig.setAutoUpdate(false);
        final MaterialConfig p4MaterialConfig = p4("http://p4_url", "view", "username");
        p4MaterialConfig.setAutoUpdate(false);
        final MaterialConfig dependencyMaterialConfig = MaterialConfigsMother.dependencyMaterialConfig();
        final PluggableSCMMaterialConfig pluggableSCMMaterialConfig = MaterialConfigsMother.pluggableSCMMaterialConfig("scm-id-1", null, null);
        pluggableSCMMaterialConfig.getSCMConfig().setAutoUpdate(false);

        final PipelineConfig p1 = PipelineConfigMother.pipelineConfig("pipeline1", new MaterialConfigs(svnMaterialConfig), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p2 = PipelineConfigMother.pipelineConfig("pipeline2", new MaterialConfigs(svnMaterialConfig, gitMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p3 = PipelineConfigMother.pipelineConfig("pipeline3", new MaterialConfigs(hgMaterialConfig, dependencyMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p4 = PipelineConfigMother.pipelineConfig("pipeline4", new MaterialConfigs(p4MaterialConfig, pluggableSCMMaterialConfig), new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        final PipelineConfig p5 = PipelineConfigMother.pipelineConfig("pipeline5", new MaterialConfigs(svnMaterialConfigWithAutoUpdate, tfsMaterialConfig),
                new JobConfigs(new JobConfig(new CaseInsensitiveString("jobName"))));
        cruiseConfig.getGroups().add(new BasicPipelineConfigs(p1, p2, p3, p4, p5));
        final Set<MaterialConfig> materials = cruiseConfig.getAllUniquePostCommitSchedulableMaterials();

        assertThat(materials.size()).isEqualTo(6);
        assertThat(materials).contains(svnMaterialConfig, hgMaterialConfig, gitMaterialConfig, tfsMaterialConfig, p4MaterialConfig, pluggableSCMMaterialConfig);
        assertThat(materials).doesNotContain(svnMaterialConfigWithAutoUpdate);
    }

    @Test
    public void getAllUniquePostCommitSchedulableMaterials_shouldReturnMaterialsWithAutoUpdateFalse() {
        GitMaterialConfig gitAutoMaterial = MaterialConfigsMother.gitMaterialConfig("url");
        PipelineConfig pipelineAuto = pipelineConfig("pipelineAuto", new MaterialConfigs(gitAutoMaterial));
        GitMaterialConfig gitNonAutoMaterial = git(new UrlArgument("other-url"), null, null, "master", "dest", false, null, false, null, new CaseInsensitiveString("git"), false);
        PipelineConfig pipelineTriggerable = pipelineConfig("pipelineTriggerable", new MaterialConfigs(gitNonAutoMaterial));
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipelineAuto, pipelineTriggerable);
        cruiseConfig.getGroups().add(defaultGroup);
        Set<MaterialConfig> materials = cruiseConfig.getAllUniquePostCommitSchedulableMaterials();
        assertThat(materials.size()).isEqualTo(1);
        assertThat(materials).contains(gitNonAutoMaterial);
    }

    @Test
    public void getAllUniquePostCommitSchedulableMaterials_shouldReturnMaterialsAndConfigReposWithAutoUpdateFalse() {
        GitMaterialConfig gitMaterialAuto = MaterialConfigsMother.gitMaterialConfig("url");
        PipelineConfig pipelineAuto = pipelineConfig("pipelineAuto", new MaterialConfigs(gitMaterialAuto));
        GitMaterialConfig gitMaterialManual = git(new UrlArgument("other-url"), null, null, "master", "dest", false, null, false, null, new CaseInsensitiveString("git"), false);
        PipelineConfig pipelineTriggerable = pipelineConfig("pipelineTriggerable", new MaterialConfigs(gitMaterialManual));
        PipelineConfigs defaultGroup = createGroup("defaultGroup", pipelineAuto, pipelineTriggerable);

        cruiseConfig = new BasicCruiseConfig(defaultGroup);
        ConfigReposConfig reposConfig = new ConfigReposConfig();
        GitMaterialConfig configRepoMaterialAutoUpdate = git("http://git");
        GitMaterialConfig configRepoMaterialManual = tap(git("http://git2"), g -> g.setAutoUpdate(false));
        reposConfig.add(ConfigRepoConfig.createConfigRepoConfig(configRepoMaterialAutoUpdate, "myplug", "exclude"));
        reposConfig.add(ConfigRepoConfig.createConfigRepoConfig(configRepoMaterialManual, "myplug", "include"));
        cruiseConfig.setConfigRepos(reposConfig);


        Set<MaterialConfig> materials = cruiseConfig.getAllUniquePostCommitSchedulableMaterials();
        assertThat(materials.size()).isEqualTo(2);
        assertThat(materials).contains(gitMaterialManual);
        assertThat(materials).contains(configRepoMaterialManual);
        assertThat(materials).doesNotContain(configRepoMaterialAutoUpdate);
    }

    @Test
    public void shouldCheckCyclicDependency() {
        PipelineConfig p1 = createPipelineConfig("p1", "s1", "j1");
        PipelineConfig p2 = createPipelineConfig("p2", "s2", "j1");
        p2.addMaterialConfig(new DependencyMaterialConfig(new CaseInsensitiveString("p1"), new CaseInsensitiveString("s1")));
        PipelineConfig p3 = createPipelineConfig("p3", "s3", "j1");
        p3.addMaterialConfig(new DependencyMaterialConfig(new CaseInsensitiveString("p2"), new CaseInsensitiveString("s2")));
        p1.addMaterialConfig(new DependencyMaterialConfig(new CaseInsensitiveString("p3"), new CaseInsensitiveString("s3")));
        pipelines.addAll(List.of(p1, p2, p3));
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        ConfigReposConfig reposConfig = new ConfigReposConfig();
        GitMaterialConfig configRepo = git("http://git");
        reposConfig.add(ConfigRepoConfig.createConfigRepoConfig(configRepo, "myplug", "id"));
        mainCruiseConfig.setConfigRepos(reposConfig);

        PartialConfig partialConfig = PartialConfigMother.withPipeline("pipe2");
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, partialConfig);

        cruiseConfig.validate(mock(ValidationContext.class));
        List<ConfigErrors> allErrors = cruiseConfig.getAllErrors();
        assertThat((allErrors.get(0).on("base"))).isEqualTo("Circular dependency: p1 <- p2 <- p3 <- p1");

    }

    // UI-like scenarios
    @Test
    public void shouldCreateEmptyEnvironmentConfigForEditsWithUIOrigin_WhenFileHasNoEnvironment_AndForEdit() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        PartialConfig partialConfig = PartialConfigMother.withEnvironment("remoteEnv");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig);

        assertThat(cruiseConfig.getEnvironments().size()).isEqualTo(1);
        assertThat(cruiseConfig.getEnvironments().get(0) instanceof MergeEnvironmentConfig).isTrue();
        assertThat(cruiseConfig.getEnvironments().get(0).name()).isEqualTo(new CaseInsensitiveString("remoteEnv"));
        MergeEnvironmentConfig mergedEnv = (MergeEnvironmentConfig) cruiseConfig.getEnvironments().get(0);
        assertThat(mergedEnv.size()).isEqualTo(2);
    }

    @Test
    public void shouldCreateEmptyEnvironmentConfigForEditsWithUIOrigin_WhenFileHasNoEnvironmentAnd2RemoteParts_AndForEdit() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        PartialConfig partialConfig1 = PartialConfigMother.withEnvironment("remoteEnv");
        partialConfig1.setOrigins(new RepoConfigOrigin());
        PartialConfig partialConfig2 = PartialConfigMother.withEnvironment("remoteEnv");
        partialConfig2.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig1, partialConfig2);

        assertThat(cruiseConfig.getEnvironments().size()).isEqualTo(1);
        assertThat(cruiseConfig.getEnvironments().get(0) instanceof MergeEnvironmentConfig).isTrue();
        assertThat(cruiseConfig.getEnvironments().get(0).name()).isEqualTo(new CaseInsensitiveString("remoteEnv"));
        MergeEnvironmentConfig mergedEnv = (MergeEnvironmentConfig) cruiseConfig.getEnvironments().get(0);
        assertThat(mergedEnv.size()).isEqualTo(3);
    }

    @Test
    public void shouldNotCreateMergeEnvironmentConfig_WhenFileHasNoEnvironment_AndNotForEdit() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        PartialConfig partialConfig = PartialConfigMother.withEnvironment("remoteEnv");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, false, partialConfig);

        assertThat(cruiseConfig.getEnvironments().size()).isEqualTo(1);
        assertThat(cruiseConfig.getEnvironments().get(0) instanceof MergeEnvironmentConfig).isFalse();
        assertThat(cruiseConfig.getEnvironments().get(0).name()).isEqualTo(new CaseInsensitiveString("remoteEnv"));
        assertThat(cruiseConfig.getEnvironments().get(0).isLocal()).isFalse();
    }

    @Test
    public void shouldNotCreateEmptyEnvironmentConfigForEditsWithUIOrigin_WhenFileHasEnvironment_AndForEdit() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        mainCruiseConfig.addEnvironment(new BasicEnvironmentConfig(new CaseInsensitiveString("Env")));
        mainCruiseConfig.setOrigins(new FileConfigOrigin());
        PartialConfig partialConfig = PartialConfigMother.withEnvironment("Env");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig);

        assertThat(cruiseConfig.getEnvironments().size()).isEqualTo(1);
        assertThat(cruiseConfig.getEnvironments().get(0) instanceof MergeEnvironmentConfig).isTrue();
        assertThat(cruiseConfig.getEnvironments().get(0).name()).isEqualTo(new CaseInsensitiveString("Env"));

        MergeEnvironmentConfig mergedEnv = (MergeEnvironmentConfig) cruiseConfig.getEnvironments().get(0);
        assertThat(mergedEnv.size()).isEqualTo(2);
        assertThat(mergedEnv.get(0).isLocal()).isTrue();
        assertThat(mergedEnv.get(1).isLocal()).isFalse();

    }

    @Test
    public void shouldModifyEmptyEnvironmentConfigWithUIOrigin() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        PartialConfig partialConfig = PartialConfigMother.withEnvironment("remoteEnv");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig);

        cruiseConfig.getEnvironments().get(0).addAgent("agent");
        MergeEnvironmentConfig mergedEnv = (MergeEnvironmentConfig) cruiseConfig.getEnvironments().get(0);
        assertThat(mergedEnv.getFirstEditablePart().getAgents()).contains(new EnvironmentAgentConfig("agent"));
    }

    @Test
    public void shouldModifyEnvironmentConfigWithFileOrigin() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig(pipelines);
        BasicEnvironmentConfig envInFile = new BasicEnvironmentConfig(new CaseInsensitiveString("Env"));
        mainCruiseConfig.addEnvironment(envInFile);
        mainCruiseConfig.setOrigins(new FileConfigOrigin());
        PartialConfig partialConfig = PartialConfigMother.withEnvironment("Env");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig);

        cruiseConfig.getEnvironments().get(0).addAgent("agent");

        assertThat(envInFile.getAgents()).contains(new EnvironmentAgentConfig("agent"));
    }

    @Test
    public void shouldAddAuthorizationToPipelinesConfigForEditsWithUIOrigin_WhenFileHasNoPipelineGroupYet_AndForEdit() {
        BasicCruiseConfig mainCruiseConfig = new BasicCruiseConfig();
        // only remotely defined group
        PartialConfig partialConfig = PartialConfigMother.withPipelineInGroup("pipe1", "group1");
        partialConfig.setOrigins(new RepoConfigOrigin());
        cruiseConfig = new BasicCruiseConfig(mainCruiseConfig, true, partialConfig);

        assertThat(cruiseConfig.getGroups().size()).isEqualTo(1);
        assertThat(cruiseConfig.getGroups().get(0) instanceof MergePipelineConfigs).isTrue();
        assertThat(cruiseConfig.getGroups().get(0).getGroup()).isEqualTo("group1");

        MergePipelineConfigs mergedEnv = (MergePipelineConfigs) cruiseConfig.getGroups().get(0);
        assertThat(mergedEnv.getLocal().getOrigin()).isEqualTo(new UIConfigOrigin());

        Authorization authorization = new Authorization(new AdminsConfig(
                new AdminUser(new CaseInsensitiveString("firstTemplate-admin"))));
        cruiseConfig.getGroups().get(0).setAuthorization(authorization);

        assertThat(mergedEnv.getLocal().getAuthorization()).isEqualTo(authorization);
    }

    private void setupJobWithTasks(CruiseConfig config, Task... tasks) {
        goConfigMother.addPipeline(config, "cruise", "dev", "linux-firefox");
        JobConfig job = config.jobConfigByName("cruise", "dev", "linux-firefox", true);

        for (Task task : tasks) {
            job.addTask(task);
        }
    }

    private JobConfig jobConfig(String jobConfigName) {
        return new JobConfig(new CaseInsensitiveString(jobConfigName), null, null);
    }

    private PipelineConfig addPipeline(String pipelineName, String stageName, JobConfig... jobConfigs) {
        PipelineConfig pipeline = new PipelineConfig(new CaseInsensitiveString(pipelineName), new MaterialConfigs());
        pipeline.add(new StageConfig(new CaseInsensitiveString(stageName), new JobConfigs(jobConfigs)));
        pipelines.add(pipeline);
        return pipeline;
    }

    private void addPipelineWithStages(String pipelineName, String stageName, JobConfig... jobConfigs) {
        PipelineConfig pipeline = new PipelineConfig(new CaseInsensitiveString(pipelineName), null);
        pipeline.add(new StageConfig(new CaseInsensitiveString(stageName), new JobConfigs(jobConfigs)));
        pipeline.add(new StageConfig(new CaseInsensitiveString(stageName + "2"), new JobConfigs(jobConfigs)));
        pipelines.add(pipeline);
    }

    private static class MyValidatable implements Validatable {
        public Validatable innerValidatable;

        @Override
        public void validate(ValidationContext validationContext) {
        }

        @Override
        public ConfigErrors errors() {
            return new ConfigErrors();
        }

        @Override
        public void addError(String fieldName, String message) {
        }

    }

    private static class AlwaysEqualMyValidatable extends MyValidatable {
        @Override
        public final int hashCode() {
            return 42;
        }

        @Override
        public final boolean equals(Object obj) {
            return true;
        }
    }
}
