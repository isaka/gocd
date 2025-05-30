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
package com.thoughtworks.go.agent.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.thoughtworks.go.agent.testhelper.FakeGoServer.TestResource.TEST_AGENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JarUtilTest {

    private static final String PATH_WITH_HASHES = "#hashes#in#path";

    @TempDir
    public File temporaryFolder;

    @BeforeEach
    public void setUp() throws IOException {
        TEST_AGENT.copyTo(new File(PATH_WITH_HASHES, "test-agent.jar"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(new File(PATH_WITH_HASHES, "test-agent.jar").toPath());
        Files.deleteIfExists(Path.of(PATH_WITH_HASHES));
    }

    @Test
    public void shouldGetManifestKey() {
        String manifestKey = JarUtil.getManifestKey(new File(PATH_WITH_HASHES, "test-agent.jar"), "Go-Agent-Bootstrap-Class");
        assertThat(manifestKey).isEqualTo("com.thoughtworks.go.HelloWorldStreamWriter");
    }

    @Test
    public void shouldExtractJars() throws Exception {
        File sourceFile = new File(PATH_WITH_HASHES, "test-agent.jar");
        Set<File> files = new HashSet<>(JarUtil.extractFilesInLibDirAndReturnFiles(sourceFile, jarEntry -> jarEntry.getName().endsWith(".class"), temporaryFolder));

        try (Stream<Path> directoryStream = Files.list(temporaryFolder.toPath())) {
            Set<File> actualFiles = directoryStream.map(Path::toFile).collect(Collectors.toSet());

            assertEquals(files, actualFiles);
            assertEquals(2, files.size());
            Set<String> fileNames = files.stream().map(File::getName).collect(Collectors.toSet());
            assertEquals(fileNames, Set.of("ArgPrintingMain.class", "HelloWorldStreamWriter.class"));
        }
    }
}
