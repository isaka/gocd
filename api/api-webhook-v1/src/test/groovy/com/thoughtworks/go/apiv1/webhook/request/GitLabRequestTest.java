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

package com.thoughtworks.go.apiv1.webhook.request;

import com.thoughtworks.go.apiv1.webhook.helpers.WithMockRequests;
import com.thoughtworks.go.apiv1.webhook.request.payload.push.GitLabPush;
import com.thoughtworks.go.junit5.FileSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitLabRequestTest implements WithMockRequests {

    @ParameterizedTest
    @FileSource(files = "/gitlab-push.json")
    void parsePayload(String body) {
        assertPayload(gitlab().body(body).build().parsePayload(GitLabPush.class));
    }

    private void assertPayload(GitLabPush payload) {
        assertEquals(Set.of("release"), payload.branches());
        assertEquals("gocd/spaceship", payload.fullName());
        assertEquals("gitlab.example.com", payload.hostname());
    }
}
