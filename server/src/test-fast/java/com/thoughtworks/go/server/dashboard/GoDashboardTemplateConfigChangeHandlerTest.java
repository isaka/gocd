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
package com.thoughtworks.go.server.dashboard;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.CruiseConfig;
import com.thoughtworks.go.config.PipelineTemplateConfig;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.GoDashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoDashboardTemplateConfigChangeHandlerTest {
    @Mock
    GoDashboardService cacheUpdateService;
    @Mock
    GoConfigService goConfigService;

    private GoDashboardTemplateConfigChangeHandler handler;

    @BeforeEach
    public void setUp() {

        handler = new GoDashboardTemplateConfigChangeHandler(cacheUpdateService, goConfigService);
    }

    @Test
    public void shouldRefreshAllPipelinesAssociatedWithATemplateInCacheWhenATemplateChanges() {
        CruiseConfig cruiseConfig = mock(CruiseConfig.class);
        PipelineTemplateConfig templateConfig = new PipelineTemplateConfig(new CaseInsensitiveString("template1"));
        CaseInsensitiveString pipeline1 = new CaseInsensitiveString("p1");
        CaseInsensitiveString pipeline2 = new CaseInsensitiveString("p2");

        when(goConfigService.currentCruiseConfig()).thenReturn(cruiseConfig);
        when(cruiseConfig.pipelinesAssociatedWithTemplate(templateConfig.name())).thenReturn(List.of(pipeline1, pipeline2));

        handler.call(templateConfig);

        verify(cacheUpdateService).updateCacheForPipeline(pipeline1);
        verify(cacheUpdateService).updateCacheForPipeline(pipeline2);
    }
}