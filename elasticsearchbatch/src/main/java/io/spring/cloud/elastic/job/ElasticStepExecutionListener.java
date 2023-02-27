package io.spring.cloud.elastic.job;


import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

@Slf4j
public class ElasticStepExecutionListener implements StepExecutionListener {


    @Override
    public void beforeStep(StepExecution stepExecution) {

        String stepName = stepExecution.getStepName();
        log.info("{} start", stepName);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        int getCommitCount = stepExecution.getCommitCount();
        int getWriteCount = stepExecution.getWriteCount();
        int getReadCount = stepExecution.getReadCount();
        ExitStatus exitStatus = stepExecution.getExitStatus();

        log.info("Step done");
        log.info("Step WriteCount {}건, getReadCount {}건, CommitCount {}건,", getWriteCount, getReadCount, getCommitCount);

        return exitStatus;
    }



}
