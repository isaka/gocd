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
package com.thoughtworks.go.domain.materials.tfs;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.materials.tfs.TfsMaterial;
import com.thoughtworks.go.domain.MaterialInstance;
import com.thoughtworks.go.domain.materials.Material;
import com.thoughtworks.go.server.cache.GoCache;
import com.thoughtworks.go.server.dao.DatabaseAccessHelper;
import com.thoughtworks.go.server.persistence.MaterialRepository;
import com.thoughtworks.go.util.GoConfigFileHelper;
import com.thoughtworks.go.util.command.UrlArgument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
        "classpath:/applicationContext-global.xml",
        "classpath:/applicationContext-dataLocalAccess.xml",
        "classpath:/testPropertyConfigurer.xml",
        "classpath:/spring-all-servlet.xml",
})
public class TfsMaterialPersistenceTest {
    @Autowired private DatabaseAccessHelper dbHelper;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private GoCache goCache;

    private GoConfigFileHelper configHelper = new GoConfigFileHelper();

    @BeforeEach
    public void setUp() throws Exception {
        goCache.clear();
        configHelper.onSetUp();
        dbHelper.onSetUp();
    }

    @AfterEach
    public void teardown() throws Exception {
        dbHelper.onTearDown();
        configHelper.onTearDown();
    }

    @Test
    public void shouldBeAbleToConvertAMaterialInstanceObjectToTfsMaterialObject() {
        TfsMaterial tfsCfg = new TfsMaterial(new UrlArgument("url"), "loser", "CORPORATE", "password", "/dev/null");
        tfsCfg.setFolder("folder");
        tfsCfg.setName(new CaseInsensitiveString("materialName"));
        MaterialInstance tfsInstance = materialRepository.findOrCreateFrom(tfsCfg);

        Material material = tfsInstance.toOldMaterial("materialName", "folder", "password");
        assertThat(material).isEqualTo(tfsCfg);
    }

    @Test
    public void shouldFindOldMaterial() {
        TfsMaterial tfsCfg = new TfsMaterial(new UrlArgument("url"), "loser", "CORPORATE", "foo_bar_baz", "/dev/null");
        MaterialInstance tfsInstance1 = materialRepository.findOrCreateFrom(tfsCfg);
        goCache.clear();
        MaterialInstance tfsInstance2 = materialRepository.findOrCreateFrom(tfsCfg);

        assertThat(tfsInstance1).isEqualTo(tfsInstance2);
    }

    @Test
    public void shouldSaveMaterialInstance() {
        TfsMaterial tfsCfg = new TfsMaterial(new UrlArgument("url"), "loser", "CORPORATE", "foo_bar_baz", "/dev/null");
        MaterialInstance materialInstance = materialRepository.findOrCreateFrom(tfsCfg);
        assertThat(materialRepository.findMaterialInstance(tfsCfg)).isEqualTo(materialInstance);
    }

}
