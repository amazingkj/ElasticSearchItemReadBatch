package io.spring.cloud.elastic.job;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersIncrementer;

import java.util.Date;

public class DailyJobTimestamper implements JobParametersIncrementer {
    private static final String RUN_ID = "run.id";

    @Override
    public JobParameters getNext(JobParameters jobParameters) {

        JobParameters params = (jobParameters == null) ? new JobParameters() : jobParameters;
        return new JobParametersBuilder()
                .addDate("currentDate",new Date())
                .addLong(RUN_ID, params.getLong(RUN_ID, 0L) + 1)
                .toJobParameters();
    }
}
