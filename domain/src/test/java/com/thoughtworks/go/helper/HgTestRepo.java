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
package com.thoughtworks.go.helper;

import com.thoughtworks.go.config.materials.ScmMaterialConfig;
import com.thoughtworks.go.config.materials.mercurial.HgMaterial;
import com.thoughtworks.go.config.materials.mercurial.HgMaterialConfig;
import com.thoughtworks.go.domain.materials.*;
import com.thoughtworks.go.domain.materials.mercurial.HgCommand;
import com.thoughtworks.go.util.TempDirUtils;
import com.thoughtworks.go.util.command.CommandLine;
import com.thoughtworks.go.util.command.InMemoryStreamConsumer;
import com.thoughtworks.go.util.command.ProcessOutputStreamConsumer;
import com.thoughtworks.go.util.command.UrlArgument;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.thoughtworks.go.helper.MaterialConfigsMother.hg;
import static com.thoughtworks.go.util.command.ProcessOutputStreamConsumer.inMemoryConsumer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.fail;

public class HgTestRepo extends TestRepo {
    private File remoteRepo;
    private static final String HG_BUNDLE_FILE = "../domain/src/test/resources/data/repos/hgrepo.hgbundle";

    private final HgCommand hgCommand;

    public HgTestRepo(Path tempDir) throws IOException {
        this("working-copy", tempDir);
    }

    public HgTestRepo(String workingCopyName, Path tempDir) throws IOException {
        super(tempDir);
        File tempFolder = TempDirUtils.createRandomDirectoryIn(tempDir).toFile();

        remoteRepo = new File(tempFolder, "remote-repo");
        remoteRepo.mkdirs();
        //Copy file to work around bug in hg
        File bundleToExtract = new File(tempFolder, "repo.bundle");
        FileUtils.copyFile(new File(HG_BUNDLE_FILE), bundleToExtract);
        setUpServerRepoFromHgBundle(remoteRepo, bundleToExtract);

        File workingCopy = new File(tempFolder, workingCopyName);
        hgCommand = new HgCommand(null, workingCopy, "default", remoteRepo.getAbsolutePath(), null);
        InMemoryStreamConsumer output = inMemoryConsumer();
        if (hgCommand.clone(output, new UrlArgument(remoteRepo.getAbsolutePath())) != 0) {
            fail("Error creating repository\n" + output.getAllOutput());
        }
    }

    @Override
    public String projectRepositoryUrl() {
        return remoteRepo.getAbsolutePath();
    }

    @Override
    public List<Modification> checkInOneFile(String fileName, String comment) throws Exception {
        return commitAndPushFile(fileName, comment);
    }

    @Override
    public List<Modification> latestModification() {
        return latestModifications();
    }

    @Override
    public HgMaterial material() {
        return new HgMaterial(projectRepositoryUrl(), null);
    }

    public HgMaterialConfig materialConfig() {
        return hg(projectRepositoryUrl(), null);
    }

    private void setUpServerRepoFromHgBundle(File serverRepo, File hgBundleFile) {
        String[] input1 = new String[]{};
        hgAt(serverRepo, "init").runOrBomb(null, input1);

        CommandLine hg = hgAt(serverRepo, "pull", "-u", hgBundleFile.getAbsolutePath());
        String[] input = new String[]{};
        hg.runOrBomb(null, input);
    }

    private CommandLine hgAt(File workingFolder, String... arguments) {
        CommandLine hg = CommandLine.createCommandLine("hg").withArgs(arguments).withEncoding(StandardCharsets.UTF_8);
        hg.setWorkingDir(workingFolder);
        return hg;
    }

    public List<Modification> latestModifications() {
        return hgCommand.latestOneModificationAsModifications();
    }

    public HgMaterial createMaterial(String dest) {
        HgMaterial material = material();
        material.setFolder(dest);
        return material;
    }

    public HgMaterialConfig createMaterialConfig(String dest) {
        HgMaterialConfig materialConfig = materialConfig();
        materialConfig.setConfigAttributes(Map.of(ScmMaterialConfig.FOLDER, dest));
        return materialConfig;
    }

    public void commitAndPushFile(String fileName) throws Exception {
        commitAndPushFile(fileName, "checkin SomeDocumentation.txt");
    }

    public List<Modification> commitAndPushFile(String fileName, String comment) throws Exception {
        return commitAndPushFileWithContent(fileName, comment, "");
    }

    public List<Modification> commitAndPushFileWithContent(String fileName, String comment, String content) throws Exception {
        File baseDir = createRandomTempDirectory().toFile();
        HgMaterial material = updateTo(baseDir);

        File file = new File(baseDir, fileName);
        Files.writeString(file.toPath(), content, UTF_8);

        return addCommitPush(material, comment, baseDir, file);
    }

    public List<Modification> addCommitPush(HgMaterial material, String comment, File baseDir, File file) throws Exception {
        material.add(baseDir, ProcessOutputStreamConsumer.inMemoryConsumer(), file);
        material.commit(baseDir, ProcessOutputStreamConsumer.inMemoryConsumer(), comment, "user");
        material.push(baseDir, ProcessOutputStreamConsumer.inMemoryConsumer());
        return material.latestModification(baseDir, new TestSubprocessExecutionContext());
    }

    public HgMaterial updateTo(File baseDir) {
        HgMaterial material = material();
        Revision tip = latestRevision(material, baseDir, new TestSubprocessExecutionContext());
        material.updateTo(ProcessOutputStreamConsumer.inMemoryConsumer(), baseDir, new RevisionContext(tip), new TestSubprocessExecutionContext());
        return material;
    }

    public UrlArgument url() {
        return new UrlArgument(projectRepositoryUrl());
    }

    private Revision latestRevision(HgMaterial material, File workingDir, TestSubprocessExecutionContext execCtx) {
        List<Modification> modifications = material.latestModification(workingDir, execCtx);
        return new Modifications(modifications).latestRevision(material);
    }
}
