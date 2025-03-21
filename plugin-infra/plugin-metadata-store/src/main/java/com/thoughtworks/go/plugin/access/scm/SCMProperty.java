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
package com.thoughtworks.go.plugin.access.scm;

import com.thoughtworks.go.plugin.api.config.Property;

public class SCMProperty extends Property implements Comparable<SCMProperty> {
    public SCMProperty(String key) {
        super(key);
        updateDefaults();
    }

    public SCMProperty(String key, String value) {
        super(key, value);
        updateDefaults();
    }

    private void updateDefaults() {
        with(REQUIRED, true);
        with(PART_OF_IDENTITY, true);
        with(SECURE, false);
        with(DISPLAY_NAME, "");
        with(DISPLAY_ORDER, 0);
    }

    @Override
    public int compareTo(SCMProperty o) {
        return this.getOption(DISPLAY_ORDER) - o.getOption(DISPLAY_ORDER);
    }
}
