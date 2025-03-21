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

import com.thoughtworks.go.config.remote.ConfigRepoConfig;
import com.thoughtworks.go.config.rules.Allow;
import com.thoughtworks.go.domain.materials.Modification;
import com.thoughtworks.go.server.service.ConfigRepoService;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.serverhealth.ServerHealthService;
import com.thoughtworks.go.util.GoConfigFileHelper;
import com.thoughtworks.go.util.command.CommandLine;
import com.thoughtworks.go.util.command.ConsoleResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.IOException;

import static com.thoughtworks.go.helper.ConfigFileFixture.DEFAULT_XML_WITH_2_AGENTS;
import static com.thoughtworks.go.helper.MaterialConfigsMother.git;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class GoConfigRepoConfigDataSourceIntegrationTest {

    @Autowired
    private ServerHealthService serverHealthService;
    @Autowired
    private GoConfigWatchList configWatchList;
    @Autowired
    private GoConfigPluginService configPluginService;
    @Autowired
    private GoConfigService goConfigService;
    @Autowired
    private CachedGoPartials cachedGoPartials;
    @Autowired
    private ConfigRepoService configRepoService;
    @Autowired
    private GoConfigDao goConfigDao;
    @Autowired
    private PartialConfigHelper partials;


    @BeforeEach
    public void setUp(@TempDir File templateConfigRepo) throws Exception {
        GoConfigFileHelper configHelper = new GoConfigFileHelper(DEFAULT_XML_WITH_2_AGENTS);
        configHelper.usingCruiseConfigDao(goConfigDao);
        configHelper.onSetUp();

        GoConfigRepoConfigDataSource repoConfigDataSource = new GoConfigRepoConfigDataSource(configWatchList, configPluginService, serverHealthService, configRepoService, goConfigService);
        repoConfigDataSource.registerListener(new PartialConfigService(repoConfigDataSource, configWatchList, goConfigService, cachedGoPartials, serverHealthService, partials));

        configHelper.addTemplate("t1", "param1", "stage");
        String latestRevision = setupExternalConfigRepo(templateConfigRepo, "external_git_config_repo_referencing_template_with_params");
        ConfigRepoConfig configRepoConfig = ConfigRepoConfig.createConfigRepoConfig(git(templateConfigRepo.getAbsolutePath()), "gocd-xml", "config-id");
        configRepoConfig.getRules().add(new Allow("refer", "*", "*"));
        configHelper.addConfigRepo(configRepoConfig);

        goConfigService.forceNotifyListeners();
        Modification modification = new Modification();
        modification.setRevision(latestRevision);
        repoConfigDataSource.onCheckoutComplete(configRepoConfig.getRepo(), templateConfigRepo, modification);

    }

    @AfterEach
    public void tearDown() {
        cachedGoPartials.clear();
    }

    @Test
    public void shouldLoadACRPipelineWithParams() {
        ParamsConfig paramConfigs = new ParamsConfig(new ParamConfig("foo", "foo"));
        CruiseConfig cruiseConfig = goConfigService.getCurrentConfig();

        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe-with-params"))).isTrue();
        assertThat(cruiseConfig.getPipelineConfigByName(new CaseInsensitiveString("pipe-with-params")).getParams()).isEqualTo(paramConfigs);
    }

    @Test
    public void shouldLoadACRPipelineReferencingATemplateWithParams() {
        ParamsConfig paramConfigs = new ParamsConfig(new ParamConfig("param1", "foo"));
        CruiseConfig cruiseConfig = goConfigService.currentCruiseConfig();

        assertThat(cruiseConfig.hasPipelineNamed(new CaseInsensitiveString("pipe-with-template"))).isTrue();
        assertThat(cruiseConfig.getPipelineConfigByName(new CaseInsensitiveString("pipe-with-template")).getParams()).isEqualTo(paramConfigs);
    }

    private String setupExternalConfigRepo(File configRepo, String configRepoTestResource) throws IOException {
        ClassPathResource resource = new ClassPathResource(configRepoTestResource);
        FileUtils.copyDirectory(resource.getFile(), configRepo);
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("init").withArg(configRepo.getAbsolutePath()).runOrBomb(null);
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArgs("config", "commit.gpgSign", "false").withWorkingDir(configRepo.getAbsoluteFile()).runOrBomb(null);
        gitAddDotAndCommit(configRepo);
        ConsoleResult consoleResult = CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("log").withArg("-1").withArg("--pretty=format:%h").withWorkingDir(configRepo).runOrBomb(null);

        return consoleResult.outputAsString();
    }

    private void gitAddDotAndCommit(File configRepo) {
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("add").withArg("-A").withArg(".").withWorkingDir(configRepo).runOrBomb(null);
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("config").withArg("user.email").withArg("go_test@go_test.me").withWorkingDir(configRepo).runOrBomb(null);
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("config").withArg("user.name").withArg("user").withWorkingDir(configRepo).runOrBomb(null);
        CommandLine.createCommandLine("git").withEncoding(UTF_8).withArg("commit").withArg("-m").withArg("initial commit").withWorkingDir(configRepo).runOrBomb(null);
    }

}
