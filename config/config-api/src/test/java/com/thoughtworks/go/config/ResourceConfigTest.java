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

import com.thoughtworks.go.domain.ConfigErrors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceConfigTest {

    @BeforeEach
    public void setUp() {
    }

    @Test
    public void shouldAllowValidResourceNameForAgentResources() {
        ResourceConfig resourceConfig = resource("- foo|bar baz.quux");
        resourceConfig.validate(ConfigSaveValidationContext.forChain(new BasicCruiseConfig()));
        assertThat(resourceConfig.errors().isEmpty()).isTrue();
    }

    @Test
    public void shouldAllowParamsInsideResourceNameWhenInsideTemplates() {
        ResourceConfig resourceConfig = resource("#{PARAMS}");
        ValidationContext context = ConfigSaveValidationContext.forChain(new BasicCruiseConfig(), new TemplatesConfig());
        resourceConfig.validate(context);
        assertThat(resourceConfig.errors().isEmpty()).isTrue();
    }

    @Test // Note : At the Resource class level there is no way of accurately validating Parameters. This will only be invalidated when template gets used.
    public void validate_shouldAllowAnyCombinationOfHashesAndCurlyBraces() {
        ResourceConfig resourceConfig = resource("}#PARAMS{");
        ValidationContext context = ConfigSaveValidationContext.forChain(new BasicCruiseConfig(), new TemplatesConfig());
        resourceConfig.validate(context);
        assertThat(resourceConfig.errors().isEmpty()).isTrue();
    }

    @Test
    public void shouldNotAllowInvalidResourceNamesWhenInsideTemplates() {
        ResourceConfig resourceConfig = resource("#?{45}");
        ValidationContext context = ConfigSaveValidationContext.forChain(new BasicCruiseConfig(), new TemplatesConfig());
        resourceConfig.validate(context);
        assertThat(resourceConfig.errors().isEmpty()).isFalse();
        assertThat(resourceConfig.errors().on(JobConfig.RESOURCES)).isEqualTo("Resource name '#?{45}' is not valid. Valid names can contain valid parameter syntax or valid alphanumeric with hyphens,dots or pipes");
    }

    @Test
    public void shouldNotAllowParamsInsideResourceNameWhenOutsideTemplates() {
        ResourceConfig resourceConfig = resource("#{PARAMS}");
        ValidationContext context = ConfigSaveValidationContext.forChain(new BasicCruiseConfig(), new PipelineConfig());
        resourceConfig.validate(context);
        assertThat(resourceConfig.errors().isEmpty()).isFalse();
        assertThat(resourceConfig.errors().on(JobConfig.RESOURCES)).isEqualTo(String.format("Resource name '#{PARAMS}' is not valid. Valid names much match '%s'", ResourceConfig.VALID_REGEX));
    }

    @Test
    public void shouldNotAllowInvalidResourceNameForAgentResources() {
        ResourceConfig resourceConfig = resource("foo$bar");
        resourceConfig.validate(ConfigSaveValidationContext.forChain(new BasicCruiseConfig()));
        ConfigErrors configErrors = resourceConfig.errors();
        assertThat(configErrors.isEmpty()).isFalse();
        assertThat(configErrors.on(JobConfig.RESOURCES)).isEqualTo(String.format("Resource name 'foo$bar' is not valid. Valid names much match '%s'", ResourceConfig.VALID_REGEX));
    }

    private ResourceConfig resource(String name) {
        ResourceConfig resourceConfig = new ResourceConfig();
        resourceConfig.setName(name);
        return resourceConfig;
    }

    @Test
    //This is to work around a bug caused by MagicalCruiseConfigLoader,
    // since it uses direct field access
    public void shouldUseTrimmedNameInEquals() throws NoSuchFieldException, IllegalAccessException {
        ResourceConfig resourceConfig = new ResourceConfig();
        Field field = resourceConfig.getClass().getDeclaredField("name");
        field.setAccessible(true);
        field.set(resourceConfig, "resource1   ");
        assertThat(new ResourceConfig("resource1")).isEqualTo(resourceConfig);
    }

    @Test
    public void shouldCompareBasedOnName() {
        ResourceConfig resourceConfigA = new ResourceConfig("aaa");
        ResourceConfig resourceConfigB = new ResourceConfig("bbb");
        assertThat(resourceConfigA.compareTo(resourceConfigB)).isLessThan(0);
        assertThat(resourceConfigB.compareTo(resourceConfigA)).isGreaterThan(0);
        assertThat(resourceConfigA.compareTo(resourceConfigA)).isEqualTo(0);
    }
}
