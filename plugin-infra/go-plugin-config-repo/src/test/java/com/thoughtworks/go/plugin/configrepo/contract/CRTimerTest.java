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
package com.thoughtworks.go.plugin.configrepo.contract;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CRTimerTest extends AbstractCRTest<CRTimer> {

    private final CRTimer timer;
    private final CRTimer invalidNoTimerSpec;

    public CRTimerTest() {
        timer = new CRTimer("0 15 10 * * ? *", false);

        invalidNoTimerSpec = new CRTimer();
    }

    @Override
    public void addGoodExamples(Map<String, CRTimer> examples) {
        examples.put("timer",timer);
    }

    @Override
    public void addBadExamples(Map<String, CRTimer> examples) {
        examples.put("invalidNoTimerSpec",invalidNoTimerSpec);
    }

    @Test
    public void shouldDeserializeFromAPILikeObject() {
        String json = """
                {
                    "spec": "0 0 22 ? * MON-FRI",
                    "only_on_changes": true
                  }""";
        CRTimer deserializedValue = gson.fromJson(json,CRTimer.class);

        assertThat(deserializedValue.getSpec()).isEqualTo("0 0 22 ? * MON-FRI");
        assertThat(deserializedValue.isOnlyOnChanges()).isTrue();

        ErrorCollection errors = deserializedValue.getErrors();
        assertTrue(errors.isEmpty());
    }
}
