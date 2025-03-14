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
package com.thoughtworks.go.server.messaging;

import ch.qos.logback.classic.Level;
import com.thoughtworks.go.server.messaging.MultiplexingQueueProcessor.Action;
import com.thoughtworks.go.util.LogFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;

import static com.thoughtworks.go.util.LogFixture.logFixtureFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class MultiplexingQueueProcessorTest {
    private MultiplexingQueueProcessor queueProcessor;

    @BeforeEach
    public void setUp() {
        queueProcessor = new MultiplexingQueueProcessor("queue1");
    }

    @Test
    public void shouldMultiplexActionsFromDifferentThreadsOnToHandlersOnASingleThread() throws Exception {
        ThreadNameAccumulator t1NameAccumulator = new ThreadNameAccumulator();
        Thread t1 = setupNewThreadToAddActionIn(t1NameAccumulator);

        ThreadNameAccumulator t2NameAccumulator = new ThreadNameAccumulator();
        Thread t2 = setupNewThreadToAddActionIn(t2NameAccumulator);

        queueProcessor.start();
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        waitForProcessingToHappen();

        assertThat(t1NameAccumulator.threadOfCall).isNotNull();
        assertThat(t1NameAccumulator.threadOfCall).isNotEqualTo(t1NameAccumulator.threadOfQueueAdd);
        assertThat(t1NameAccumulator.threadOfCall).isNotEqualTo(Thread.currentThread().getName());

        assertThat(t2NameAccumulator.threadOfCall).isNotNull();
        assertThat(t2NameAccumulator.threadOfCall).isNotEqualTo(t2NameAccumulator.threadOfQueueAdd);
        assertThat(t2NameAccumulator.threadOfCall).isNotEqualTo(Thread.currentThread().getName());

        assertThat(t1NameAccumulator.threadOfCall).isEqualTo(t2NameAccumulator.threadOfCall);
    }

    @Test
    public void shouldNotAllowTheQueueProcessorToBeStartedMultipleTimes() {
        queueProcessor.start();

        try {
            queueProcessor.start();
            fail("Should have failed to start queue processor a second time.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).isEqualTo("Cannot start queue processor for queue1 multiple times.");
        }
    }

    @Test
    @Timeout(5)
    public void shouldLogAndIgnoreAnyActionsWhichFail() throws Exception {
        Action successfulAction1 = mock(Action.class);
        Action successfulAction2 = mock(Action.class);

        Action failingAction = mock(Action.class);
        doThrow(new RuntimeException("Ouch. Failed.")).when(failingAction).call();

        try (LogFixture logFixture = logFixtureFor(MultiplexingQueueProcessor.class, Level.WARN)) {
            queueProcessor.add(successfulAction1);
            queueProcessor.add(failingAction);
            queueProcessor.add(successfulAction2);
            queueProcessor.start();
            while (!queueProcessor.queue.isEmpty()) {
                waitForProcessingToHappen(100);
            }

            synchronized (logFixture) {
                assertThat(logFixture.contains(Level.WARN, "Failed to handle action in queue1 queue")).isTrue();
                assertThat(logFixture.getLog()).contains("Ouch. Failed.");
            }
        }

        verify(successfulAction1).call();
        verify(successfulAction2).call();
    }

    @Test
    public void shouldProcessAllActionsInOrderOfThemBeingAdded() throws Exception {
        Action action1 = mock(Action.class);
        Action action2 = mock(Action.class);
        Action action3 = mock(Action.class);

        queueProcessor.add(action1);
        queueProcessor.add(action2);
        queueProcessor.add(action3);

        queueProcessor.start();
        waitForProcessingToHappen();

        InOrder inOrder = inOrder(action1, action2, action3);
        inOrder.verify(action1).call();
        inOrder.verify(action2).call();
        inOrder.verify(action3).call();
    }

    private Thread setupNewThreadToAddActionIn(final ThreadNameAccumulator threadNameAccumulator) {
        return new Thread(() -> {
            threadNameAccumulator.threadOfQueueAdd = Thread.currentThread().getName();

            queueProcessor.add(new Action() {
                @Override
                public void call() {
                    threadNameAccumulator.threadOfCall = Thread.currentThread().getName();
                }

                @Override
                public String description() {
                    return "some-action";
                }
            });
        });
    }

    private void waitForProcessingToHappen() throws InterruptedException {
        waitForProcessingToHappen(1000); /* Prevent potential race, of queue not being processed. Being a little lazy. :( */
    }

    private void waitForProcessingToHappen(int time) throws InterruptedException {
        Thread.sleep(time);
    }

    private class ThreadNameAccumulator {
        String threadOfCall;
        String threadOfQueueAdd;
    }
}
