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

import com.thoughtworks.go.config.StageConfig;
import com.thoughtworks.go.domain.*;
import com.thoughtworks.go.presentation.pipelinehistory.JobHistory;
import com.thoughtworks.go.presentation.pipelinehistory.StageInstanceModel;
import com.thoughtworks.go.server.service.InstanceFactory;
import com.thoughtworks.go.util.GoConstants;
import com.thoughtworks.go.util.TimeProvider;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.thoughtworks.go.util.GoConstants.DEFAULT_APPROVED_BY;
import static java.time.temporal.ChronoUnit.HOURS;

public class StageMother {

    private StageMother() {
    }

    public static Stage withOneScheduledBuild(String stageName, String failedBuildplanName,
                                              String successfulBuildplanName, int stageId) {
        JobInstance instance1 = JobInstanceMother.completed(failedBuildplanName, JobResult.Failed);
        JobInstance instance2 = JobInstanceMother.completed(successfulBuildplanName, JobResult.Passed);
        JobInstance instance3 = JobInstanceMother.scheduled("scheduledBuild");
        JobInstances instances = new JobInstances(instance1, instance2, instance3);

        Stage stage = new Stage(stageName, instances, DEFAULT_APPROVED_BY, null, null, new TimeProvider());
        stage.setId(stageId);
        return stage;
    }

    public static Stage completedStageInstanceWithTwoPlans(String stageName) {
        StageConfig scheduledStageConfig = StageConfigMother.twoBuildPlansWithResourcesAndMaterials(stageName);
        Stage completed = scheduleInstance(scheduledStageConfig);
        passBuildInstancesOfStage(completed, Instant.now());
        return completed;
    }

    public static Stage stageWithNBuildsHavingEndState(JobState endState, JobResult result, String stageName,
                                                       String... buildNames) {
        return stageWithNBuildsHavingEndState(endState, result, stageName, Arrays.asList(buildNames));
    }

    public static Stage stageWithNBuildsHavingEndState(JobState endState, JobResult result, String stageName,
                                                       List<String> buildNames) {
        JobInstances builds = new JobInstances();
        for (String buildName : buildNames) {
            builds.add(JobInstanceMother.buildEndingWithState(endState, result, buildName));
        }
        Stage stage = new Stage(stageName, builds, DEFAULT_APPROVED_BY, null, null, new TimeProvider());
        stage.calculateResult();
        stage.setCompletedByTransitionId(stage.getJobInstances().last().getTransitions().latestTransitionId());
        return stage;
    }


    public static Stage passedStageInstance(String stageName, String planName, final String pipelineName) {
        return passedStageInstance(pipelineName, stageName, planName, null);
    }

    public static Stage passedStageInstance(String pipelineName, String stageName, String buildName, Instant completionDate) {
        Stage stage = scheduledStage(pipelineName, 1, stageName, 1, buildName);
        passBuildInstancesOfStage(stage, completionDate);
        stage.calculateResult();
        return stage;
    }

    public static Stage passedStageInstance(String pipelineName, String stageName, int stageCounter, String buildName, Instant completionDate) {
        Stage stage = passedStageInstance(pipelineName, stageName, buildName, completionDate);
        stage.setCounter(stageCounter);
        return stage;
    }

    public static Stage createPassedStage(String pipelineName, int pipelineCounter, String stageName, int stageCounter, String buildName, Instant completionDate) {
        Stage stage = scheduledStage(pipelineName, pipelineCounter, stageName, stageCounter, buildName);
        passBuildInstancesOfStage(stage, completionDate);
        stage.calculateResult();
        return stage;
    }

    @SuppressWarnings("unused") // Used by stages_controller_spec.rb
    public static Stage createPassedStageWithFakeDuration(String pipelineName, int pipelineCounter, String stageName, int stageCounter, String jobName, final Instant scheduleTime,
                                                          Instant completedTime) {
        return createStageWithFakeDuration(pipelineName, pipelineCounter, stageName, stageCounter, jobName, scheduleTime, completedTime, JobResult.Passed);
    }

    @SuppressWarnings("unused") // Used by stages_controller_spec.rb
    public static Stage createFailedStageWithFakeDuration(String pipelineName, int pipelineCounter, String stageName, int stageCounter, String jobName, final Instant scheduleTime,
                                                          Instant completedTime) {
        return createStageWithFakeDuration(pipelineName, pipelineCounter, stageName, stageCounter, jobName, scheduleTime, completedTime, JobResult.Failed);
    }

    private static Stage createStageWithFakeDuration(String pipelineName, int pipelineCounter, String stageName, int stageCounter, String jobName, final Instant scheduleTime, Instant completedTime,
                                                     JobResult jobResult) {
        TimeProvider timeProvider = new TimeProvider() {
            @Override
            public long currentTimeMillis() {
                return scheduleTime.toEpochMilli();
            }
        };

        JobInstance firstJob = new JobInstance(jobName, timeProvider);
        JobInstances jobInstances = new JobInstances(firstJob);
        Stage stage = StageMother.custom(pipelineName, stageName, jobInstances);

        firstJob.assign("AGENT-1", Date.from(completedTime));
        firstJob.completing(jobResult, Date.from(completedTime));
        firstJob.completed(Date.from(completedTime));
        stage.calculateResult();
        stage.setCreatedTime(new Timestamp(timeProvider.currentTimeMillis()));
        stage.setLastTransitionedTime(new Timestamp(completedTime.toEpochMilli()));
        stage.setIdentifier(new StageIdentifier(pipelineName, pipelineCounter, "LABEL-" + pipelineCounter, stageName, String.valueOf(stageCounter)));

        return stage;
    }

    public static Stage scheduledStage(String pipelineName, int pipelineCounter, String stageName, int stageCounter, String buildName) {
        StageConfig stageConfig = StageConfigMother.oneBuildPlanWithResourcesAndMaterials(stageName, buildName);
        Stage stage = scheduleInstance(stageConfig);
        stage.setCounter(stageCounter);
        stage.setIdentifier(new StageIdentifier(pipelineName, pipelineCounter, "LABEL-" + pipelineCounter, stageName, "" + stageCounter));
        stage.setId(fakeId());
        for (JobInstance jobInstance : stage.getJobInstances()) {
            jobInstance.setIdentifier(new JobIdentifier(pipelineName, pipelineCounter, "LABEL-" + pipelineCounter, stageName, "" + stageCounter, buildName, fakeId()));
        }
        return stage;
    }

    private static long fakeId() {
        return (long) (Math.random() * 1000000000);
    }

    public static Stage failingStage(String stageName) {
        StageConfig stageConfig = StageConfigMother.twoBuildPlansWithResourcesAndMaterials(stageName);
        Stage stage = scheduleInstance(stageConfig);
        stage.setId(fakeId());

        stage.getJobInstances().get(0).fail();
        stage.getJobInstances().get(1).completing(JobResult.Passed);

        return stage;
    }

    public static Stage completedFailedStageInstance(String pipelineName, String stageName, String planName) {
        return completedFailedStageInstance(pipelineName, stageName, planName, null);
    }

    public static Stage completedFailedStageInstance(String pipelineName, String stageName, String planName, Instant completionDate) {
        Stage completed = scheduledStage(pipelineName, 1, stageName, 1, planName);
        completeBuildInstancesOfStage(completed, JobResult.Failed, completionDate == null ? Instant.now() : completionDate);
        completed.calculateResult();
        return completed;
    }

    private static void passBuildInstancesOfStage(Stage stage, Instant completionDate) {
        completeBuildInstancesOfStage(stage, JobResult.Passed, completionDate == null ? Instant.now() : completionDate);
    }

    private static void completeBuildInstancesOfStage(Stage stage, JobResult result, Instant completionDate) {
        for (JobInstance job : stage.getJobInstances()) {
            job.assign("uuid", Date.from(completionDate.minus(1, HOURS)));
            job.completing(result, Date.from(completionDate));
            job.completed(Date.from(completionDate));
        }
        stage.setLastTransitionedTime(new Timestamp(completionDate.toEpochMilli()));
    }

    private static Stage scheduleInstance(StageConfig stageConfig) {
        Stage stageInstance = new InstanceFactory().createStageInstance(stageConfig, new DefaultSchedulingContext(DEFAULT_APPROVED_BY), "md5-test", new TimeProvider());
        stageInstance.building();
        return stageInstance;
    }

    public static Stage custom(String stagename) {
        return passedStageInstance(stagename, "dev", "pipeline-name");
    }

    public static Stage custom(String stageName, JobInstance... instances) {
        return custom(stageName, new JobInstances(instances));
    }

    public static Stage custom(String stageName, JobInstances instances) {
        return custom("pipeline", stageName, instances);
    }

    public static Stage custom(String pipelineName, String stageName, JobInstances instances) {
        Stage stage = new Stage(stageName, instances, DEFAULT_APPROVED_BY, null, GoConstants.APPROVAL_SUCCESS, new TimeProvider());
        stage.setIdentifier(new StageIdentifier(pipelineName, 1, "1", stageName, "1"));
        stage.building();
        return stage;
    }

    public static Stage cancelledStage(String stageName, String jobName) {
        JobInstance job = new JobInstance(jobName);
        job.setState(JobState.Completed);
        job.setResult(JobResult.Cancelled);
        Stage stage = custom(stageName, job);
        stage.setCounter(1);
        return stage;
    }

    public static StageInstanceModel toStageInstanceModel(Stage stage) {
        StageInstanceModel stageInstanceModel = new StageInstanceModel(stage.getName(), String.valueOf(stage.getCounter()), stage.getResult(), stage.getIdentifier());
        stageInstanceModel.setApprovalType(stage.getApprovalType());
        stageInstanceModel.setApprovedBy(stage.getApprovedBy());
        stageInstanceModel.setRerunOfCounter(stage.getRerunOfCounter());
        JobHistory jobHistory = new JobHistory();
        for (JobInstance jobInstance : stage.getJobInstances()) {
            jobHistory.addJob(jobInstance.getName(), jobInstance.getState(), jobInstance.getResult(), jobInstance.getScheduledDate());
        }
        stageInstanceModel.setBuildHistory(jobHistory);
        return stageInstanceModel;
    }

    @SuppressWarnings("unused") // Used from value_stream_map_model_spec.rb
    public static Stage unrunStage(String stageName) {
        return new NullStage(stageName, new JobInstances());
    }
}
