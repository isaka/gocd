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
package com.thoughtworks.go.plugin.infra.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import com.thoughtworks.go.util.SystemEnvironment;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DefaultPluginLoggingServiceTest {
    @Test
    void shouldTrimDownLogFileNameToAReasonableSizeIfThePluginIdIsTooBig() {
        assertPluginLogFile("abcd", "plugin-abcd.log");

        String pluginIdWithLengthOf189 = "a".repeat(189);
        assertPluginLogFile(pluginIdWithLengthOf189, "plugin-" + pluginIdWithLengthOf189 + ".log");

        String pluginIdWithLengthOf190 = "a".repeat(190);
        assertPluginLogFile(pluginIdWithLengthOf190, "plugin-" + pluginIdWithLengthOf189 + ".log");

        String pluginIdWithLengthOf200 = "a".repeat(200);
        assertPluginLogFile(pluginIdWithLengthOf200, "plugin-" + pluginIdWithLengthOf189 + ".log");
    }

    private void assertPluginLogFile(String pluginId, String expectedPluginLogFileName) {
        SystemEnvironment systemEnvironment = mock(SystemEnvironment.class);

        DefaultPluginLoggingService loggingService = new DefaultPluginLoggingService(systemEnvironment);
        loggingService.debug(pluginId, "some-logger-name", "message");

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("plugin." + pluginId);
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        logger.iteratorForAppenders().forEachRemaining(appenders::add);

        String loggingDirectory = loggingService.getCurrentLogDirectory();
        assertThat(appenders.size()).isEqualTo(1);
        assertThat(new File(((FileAppender<?>) appenders.get(0)).rawFileProperty())).isEqualTo(new File(loggingDirectory, expectedPluginLogFileName));
    }
}
