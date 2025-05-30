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
package com.thoughtworks.go.util.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InMemoryConsumer implements StreamConsumer {
    private final Queue<String> lines = new ConcurrentLinkedQueue<>();
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryConsumer.class);

    @Override
    public void consumeLine(String line) {
        try {
            lines.add(line);
        } catch (RuntimeException e) {
            LOG.error("Problem consuming line [{}]", line, e);
        }
    }

    public List<String> asList() {
        return new ArrayList<>(lines);
    }

    public boolean contains(String message) {
        return toString().contains(message);
    }

    @Override
    public String toString() {
        return String.join("\n", lines);
    }
}
