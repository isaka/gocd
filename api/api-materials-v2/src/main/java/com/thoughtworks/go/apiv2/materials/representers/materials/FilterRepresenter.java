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
package com.thoughtworks.go.apiv2.materials.representers.materials;

import com.thoughtworks.go.api.base.OutputWriter;
import com.thoughtworks.go.config.materials.Filter;
import com.thoughtworks.go.config.materials.IgnoredFiles;

import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FilterRepresenter {
    public static Consumer<OutputWriter> toJSON(Filter filter) {
        return outputWriter -> {
            if (!filter.isEmpty()) {
                outputWriter.addChildList("ignore", filter.stream().map(IgnoredFiles::getPattern).collect(Collectors.toList()));
            }
        };
    }

}
