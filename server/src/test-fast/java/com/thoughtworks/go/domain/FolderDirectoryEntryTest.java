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

import com.thoughtworks.go.server.presentation.html.HtmlRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FolderDirectoryEntryTest {
    @Test
    public void shouldAddFile() {
        DirectoryEntries subDirectory = new DirectoryEntries();
        FolderDirectoryEntry entry = new FolderDirectoryEntry("file", "url", subDirectory);
        entry.addFile("file", "path");
        assertThat(subDirectory).contains(new FileDirectoryEntry("file", "path"));
    }

    @Test
    public void shouldEscapeFolderName() {
        DirectoryEntries subDirectory = new DirectoryEntries();
        FolderDirectoryEntry entry = new FolderDirectoryEntry("<div>Hello</div>", "url", subDirectory);

        HtmlRenderer renderer = new HtmlRenderer("");
        entry.htmlBody().render(renderer);

        assertThat(renderer.asString()).contains("&lt;div&gt;Hello&lt;/div&gt;");
        assertThat(renderer.asString()).doesNotContain("<div>Hello</div>");
    }
}
