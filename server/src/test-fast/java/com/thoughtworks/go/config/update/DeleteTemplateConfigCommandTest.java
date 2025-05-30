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
package com.thoughtworks.go.config.update;

import com.thoughtworks.go.config.BasicCruiseConfig;
import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.PipelineTemplateConfig;
import com.thoughtworks.go.config.exceptions.EntityType;
import com.thoughtworks.go.helper.GoConfigMother;
import com.thoughtworks.go.helper.StageConfigMother;
import com.thoughtworks.go.server.domain.Username;
import com.thoughtworks.go.server.service.ExternalArtifactsService;
import com.thoughtworks.go.server.service.SecurityService;
import com.thoughtworks.go.server.service.result.HttpLocalizedOperationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeleteTemplateConfigCommandTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private ExternalArtifactsService externalArtifactsService;

    private HttpLocalizedOperationResult result;
    private Username currentUser;
    private BasicCruiseConfig cruiseConfig;
    private PipelineTemplateConfig pipelineTemplateConfig;

    @BeforeEach
    public void setup() {
        currentUser = new Username(new CaseInsensitiveString("user"));
        cruiseConfig = GoConfigMother.defaultCruiseConfig();
        pipelineTemplateConfig = new PipelineTemplateConfig(new CaseInsensitiveString("template"), StageConfigMother.oneBuildPlanWithResourcesAndMaterials("stage", "job"));
        result = new HttpLocalizedOperationResult();
    }

    @Test
    public void shouldDeleteTemplateFromTheGivenConfig() {
        cruiseConfig.addTemplate(pipelineTemplateConfig);
        DeleteTemplateConfigCommand command = new DeleteTemplateConfigCommand(pipelineTemplateConfig, result, securityService, currentUser, externalArtifactsService);
        assertThat(cruiseConfig.getTemplates().contains(pipelineTemplateConfig)).isTrue();
        command.update(cruiseConfig);
        assertThat(cruiseConfig.getTemplates().contains(pipelineTemplateConfig)).isFalse();
    }

    @Test
    public  void shouldValidateWhetherTemplateIsAssociatedWithPipelines() {
        new GoConfigMother().addPipelineWithTemplate(cruiseConfig, "p1", pipelineTemplateConfig.name().toString(), "s1", "j1");
        DeleteTemplateConfigCommand command = new DeleteTemplateConfigCommand(pipelineTemplateConfig, result, securityService, currentUser, externalArtifactsService);

        assertThatThrownBy(() -> command.isValid(cruiseConfig))
                .hasMessageContaining("The template 'template' is being referenced by pipeline(s): [p1]");
    }

    @Test
    public void shouldNotContinueWithConfigSaveIfUserIsUnauthorized() {
        cruiseConfig.addTemplate(pipelineTemplateConfig);
        when(securityService.isAuthorizedToEditTemplate(new CaseInsensitiveString("template"), currentUser)).thenReturn(false);

        DeleteTemplateConfigCommand command = new DeleteTemplateConfigCommand(pipelineTemplateConfig, result, securityService, currentUser, externalArtifactsService);

        assertThat(command.canContinue(cruiseConfig)).isFalse();
        assertThat(result.message()).isEqualTo(EntityType.Template.forbiddenToDelete(pipelineTemplateConfig.name(), currentUser.getUsername()));
    }

    @Test
    public void shouldContinueWithConfigSaveIfUserIsAuthorized() {
        cruiseConfig.addTemplate(pipelineTemplateConfig);
        when(securityService.isAuthorizedToEditTemplate(new CaseInsensitiveString("template"), currentUser)).thenReturn(true);

        DeleteTemplateConfigCommand command = new DeleteTemplateConfigCommand(pipelineTemplateConfig, result, securityService, currentUser, externalArtifactsService);

        assertThat(command.canContinue(cruiseConfig)).isTrue();
    }

    @Test
    public void shouldNotContinueWhenTemplateNoLongerExists() {
        DeleteTemplateConfigCommand command = new DeleteTemplateConfigCommand(pipelineTemplateConfig, result, securityService, currentUser, externalArtifactsService);

        assertThat(command.canContinue(cruiseConfig)).isFalse();
    }

}
