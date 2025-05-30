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
package com.thoughtworks.go.domain;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class FileHandlerTest {

    @TempDir
    File tempDir;

    private File artifact;
    private ArtifactMd5Checksums checksums;
    private FileHandler fileHandler;
    private StubGoPublisher goPublisher;

    @BeforeEach
    public void setup() {
        artifact = new File("foo");
        checksums = mock(ArtifactMd5Checksums.class);
        fileHandler = new FileHandler(artifact, "src/file/path");
        goPublisher = new StubGoPublisher();
    }

    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(artifact.toPath());
    }

    @Test
    public void shouldCheckTheMD5OfTheFile() throws IOException {
        when(checksums.md5For("src/file/path")).thenReturn(DigestUtils.md5Hex(new ByteArrayInputStream("Hello world".getBytes())));
        fileHandler.useArtifactMd5Checksums(checksums);

        fileHandler.handle(new ByteArrayInputStream("Hello world".getBytes()));
        fileHandler.handleResult(200, goPublisher);

        assertThat(Files.readString(artifact.toPath(), UTF_8)).isEqualTo("Hello world");
        assertThat(goPublisher.getMessage()).contains("Saved artifact to [foo] after verifying the integrity of its contents.");
        verify(checksums).md5For("src/file/path");
        verifyNoMoreInteractions(checksums);
    }

    @Test
    public void shouldWarnWhenChecksumsFileIsNotPresent() throws IOException {
        fileHandler.handle(new ByteArrayInputStream("Hello world".getBytes()));

        fileHandler.handleResult(200, goPublisher);

        assertThat(goPublisher.getMessage()).contains("Saved artifact to [foo] without verifying the integrity of its contents.");
        assertThat(goPublisher.getMessage()).doesNotContain("[WARN] The md5checksum value of the artifact [src/file/path] was not found on the server. Hence, Go could not verify the integrity of its contents.");
        assertThat(Files.readString(artifact.toPath(), UTF_8)).isEqualTo("Hello world");
    }

    @Test
    public void shouldWarnWhenChecksumsFileIsPresentButMD5DoesNotExist() throws IOException {
        when(checksums.md5For("src/file/path")).thenReturn(null);
        fileHandler.useArtifactMd5Checksums(checksums);

        fileHandler.handle(new ByteArrayInputStream("Hello world".getBytes()));

        fileHandler.handleResult(200, goPublisher);

        assertThat(goPublisher.getMessage()).contains("[WARN] The md5checksum value of the artifact [src/file/path] was not found on the server. Hence, Go could not verify the integrity of its contents.");
        assertThat(goPublisher.getMessage()).contains("Saved artifact to [foo] without verifying the integrity of its contents");
        assertThat(Files.readString(artifact.toPath(), UTF_8)).isEqualTo("Hello world");
    }

    @Test
    public void shouldThrowExceptionWhenChecksumsDoNotMatch() throws IOException {
        when(checksums.md5For("src/file/path")).thenReturn("wrong_md5");
        fileHandler.useArtifactMd5Checksums(checksums);

        try {
            fileHandler.handle(new ByteArrayInputStream("Hello world".getBytes()));
            fileHandler.handleResult(200, goPublisher);
            fail("Should throw exception when checksums do not match.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Artifact download failed for [src/file/path]");
            assertThat(goPublisher.getMessage()).contains("[ERROR] Verification of the integrity of the artifact [src/file/path] failed. The artifact file on the server may have changed since its original upload.");
        }
    }

    @Test
    public void shouldNotDisplayArtifactMultipleTimesWhenRetriesCalled() throws IOException {
        when(checksums.md5For("src/file/path")).thenReturn("wrong_md5");
        fileHandler.useArtifactMd5Checksums(checksums);

        int retryCount = 0;

        while (retryCount < 2) {
            retryCount++;
            try {
                fileHandler.handle(new ByteArrayInputStream("Hello world".getBytes()));
                fileHandler.handleResult(200, goPublisher);
                fail("Should throw exception when checksums do not match.");
            } catch (RuntimeException e) {
                assertThat(e.getMessage()).isEqualTo("Artifact download failed for [src/file/path]");
                assertThat(goPublisher.getMessage()).contains("[ERROR] Verification of the integrity of the artifact [src/file/path] failed. The artifact file on the server may have changed since its original upload.");
            }
        }
    }

    @Test
    void shouldCalculateSha1Digest() throws IOException {
        Path tempFile = tempDir.toPath().resolve("testFile.txt");
        Files.writeString(tempFile, "12345", UTF_8);
        assertThat(FileHandler.sha1Digest(tempFile.toFile())).isEqualTo("jLIjfQZ5yojbZGTqxg2pY0VROWQ=");
    }

}
