package io.spring.cloud.elastic.job;

import io.spring.cloud.elastic.elasticsearch.domain.LogData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Criteria;

import java.text.SimpleDateFormat;
import java.util.*;


@Slf4j
@RequiredArgsConstructor
@Configuration
public class ElasticJobConfig {
    private static final int CHUNK_SIZE = 50000;

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final ElasticsearchOperations operations;
    private final MakeCSVStatisticsTasklet makeCSVStatisticsTasklet;

    @Autowired
    private final CustomJobParameter jobParameter;

    @Bean
    public Job StatisticsJob() throws Exception {
        return jobBuilderFactory.get("StatisticsJob")
                .incrementer(new DailyJobTimestamper())
                .start(startStep())
                .next(betweenGreaterThanStep())
                .next(makeCSVStatisticsStep())
                .next(MultiFTP_tasklet_buildStep())
                .build();
    }

    @Bean
    public Step startStep() {
        return stepBuilderFactory.get("startStep")
                .tasklet((contribution, chunkContext) -> {
                    log.info(">>>>> Start!");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step betweenGreaterThanStep() throws Exception {
        log.info("betweenGreaterThanStep 진입");
        return stepBuilderFactory.get("betweenGreaterThanStep")
                .<LogData, LogData>chunk(CHUNK_SIZE)
                .reader(betweenGreaterThanItemReader())
                .writer(addStatisticsItemWriter())
                .listener(new ElasticStepExecutionListener())
                .build();
    }

    @Bean
    public Step makeCSVStatisticsStep() {
        return this.stepBuilderFactory.get("makeCSVStatisticsStep")
                .tasklet(makeCSVStatisticsTasklet)
                .build();
    }

    @Bean
    public ElasticsearchItemReader<LogData> betweenGreaterThanItemReader() {

        String FromDate = jobParameter.getFROM();
        String ToDate = jobParameter.getTO();
        String greaterThan = jobParameter.getGreaterThan();


        log.info( "***** jobParameter FromDate : {}, ToDate : {}, greaterThan : {}",FromDate,ToDate,greaterThan);

        Criteria criteria=null;

        if("NONE".equals(FromDate)&&"NONE".equals(ToDate)&&"NONE".equals(greaterThan)){
            log.info("all parameter is null");
        } else if("NONE".equals(FromDate)||"NONE".equals(ToDate)) {
            Date greaterThanDate = new Date(System.currentTimeMillis() - 60 * Integer.parseInt(greaterThan) * 1000);
            criteria = Criteria.where("engine").exists()
                    .and(Criteria.where("@timestamp").greaterThan(greaterThanDate));

        } else if ("NONE".equals(greaterThan)) {
            criteria = Criteria.where("engine").exists()
                    .and(Criteria.where("@timestamp").between(FromDate,ToDate));
        }

        log.info( "criteria! {}",criteria);
        Sort sort = Sort.by(List.of(Sort.Order.desc("@timestamp")));

        return ElasticsearchItemReader.<LogData>builder()
                .name("greaterThanItemReader")
                .pageSize(CHUNK_SIZE)
                .operations(operations)
                .criteria(criteria)
                .sortOptions(sort)
                .targetType(LogData.class)
                .build();
    }

    /*Writer*/
    @Bean
    public ItemWriter<LogData> addStatisticsItemWriter() {
        return new ItemWriter<LogData>() {

            @Override
            public void write(List<? extends LogData> list) throws Exception {

                makeCSVStatisticsTasklet.aggregate(list);
                log.info("list.get(0).getTimestamp() : {}",list.get(0).getTimestamp().toString());

            }
        };
    }

    @Bean
    public Step MultiFTP_tasklet_buildStep() {
        return stepBuilderFactory.get("MultiFTP_tasklet_buildStep")
                .tasklet(taskletConfig()).build();

    }

    @Bean
    public FTpTasklet taskletConfig() {
        FTpTasklet taskletConfig = new FTpTasklet(jobParameter);
        return taskletConfig;
    }



}
