package onthelive.sttRejectProcess.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onthelive.sttRejectProcess.batch.step.SpeechToTextProcessor;
import onthelive.sttRejectProcess.batch.listener.NoWorkFoundStepExecutionListener;
import onthelive.sttRejectProcess.entity.RejectJob;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class BatchConfiguration {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final DataSource dataSource;

    private final SpeechToTextProcessor speechToTextProcessor;
    private final Step afterRejectUpdateSectionUserStep;

    private static final int CHUNK_SIZE = 1;

    // --------------- MultiThread --------------- //

    private static final int DEFAULT_POOL_SIZE = 10;

    public TaskExecutor executor(int poolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setThreadNamePrefix("multi-thread-");
        executor.setWaitForTasksToCompleteOnShutdown(Boolean.TRUE);
        executor.initialize();
        return executor;
    }

    // --------------- MultiThread --------------- //

    // --------------- Job --------------- //
    @Bean
    public Job threeKindOfSttJob() throws Exception {
        return jobBuilderFactory.get("threeKindOfSttJob")
                .start(rejectStep())
                .on("*").to(afterRejectUpdateSectionUserStep)
                .end()
                .incrementer(new RunIdIncrementer()).build();
    }
    // --------------- Job --------------- //

    // STEP //
    @Bean
    public Step rejectStep() throws Exception {
        return stepBuilderFactory.get("rejectStep")
                .<RejectJob, RejectJob>chunk(CHUNK_SIZE)
                .reader(rejectReader())
                .processor(speechToTextProcessor)
                .writer(compositeItemWriter(
                        sttWriter(),
                        updateJobMastersSetStateCompleteStt(),
                        insertIntoJobHistoriesStt(),
                        updateJobSubsSetStateCompleteStt(),
                        updateJobSubRejectsSetStateCompleteStt(),
                        updateJobSubRejectsSetStateWaitStt()
                ))
                .listener(new NoWorkFoundStepExecutionListener())
                .taskExecutor(executor(DEFAULT_POOL_SIZE))
                .throttleLimit(DEFAULT_POOL_SIZE)
                .build();
    }

    // STEP //

    // READER //
    @Bean
    public JdbcPagingItemReader<RejectJob> rejectReader() throws Exception {
        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("to_process_code", "STT");
        parameterValues.put("reject_state" , "WAIT");

        return new JdbcPagingItemReaderBuilder<RejectJob>()
                .pageSize(CHUNK_SIZE)
                .fetchSize(CHUNK_SIZE)
                .dataSource(dataSource)
                .rowMapper(new BeanPropertyRowMapper<>(RejectJob.class))
                .queryProvider(rejectQueryProvider())
                .parameterValues(parameterValues)
                .name("speechToTextReader")
                .saveState(false)
                .build();
    }

    @Bean
    public PagingQueryProvider rejectQueryProvider() throws Exception {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause("select" +
                "a.project_id as projectId, " +
                "a.document_id as documentId, " +
                "a.section_id as sectionId, " +
                "a.segment_id as segmentId, " +
                "c.value , " +
                "a.job_master_id as jobMasterId , " +
                "a.job_sub_id as jobSubId, " +
                "a.to_user_id as userId, " +
                "a.id as jobRejectId, " +
                "d.to_lang as toLang, " +
                "e.segment_value as segmentValue ");
        queryProvider.setFromClause("from " +
                "job_sub_rejects a " +
                "inner join job_masters b on " +
                "a.job_master_id = b.id " +
                "inner join job_sub_results c on " +
                "b.pre_job_id = c.job_master_id " +
                "inner join (select id as pid , project_type_code , to_lang from projects a) d on " +
                "a.project_id = d.pid " +
                "inner join (select id as sid, value as segment_value from segments a) e on " +
                "a.segment_id = e.sid ");
        queryProvider.setWhereClause("where " +
                "a.to_process_code = :to_process_code " +
                "and a.reject_state = :reject_state");
        Map<String, Order> sortKeys = new HashMap<>(1);
        sortKeys.put("id", Order.ASCENDING);

        queryProvider.setSortKeys(sortKeys);

        return queryProvider.getObject();
    }
    // READER //

    // WRITER //
    @Bean
    public JdbcBatchItemWriter<RejectJob> sttWriter() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("insert into job_sub_results (job_master_id , job_sub_id , value) " +
                        "values (:jobMasterId , :jobSubId , :sttResult) " +
                        "on duplicate key update value = :sttResult, updated_datetime = now()")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> insertIntoJobHistoriesStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("INSERT INTO job_sub_histories (id, job_master_id, job_sub_id, user_id, process_code, state, reject_state) " +
                        "values (:historyCnt + 1 , :jobMasterId , :jobSubId , :userId , :processCode , :state , 'Y') " +
                        "on duplicate key update state = :state")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> updateJobMastersSetStateCompleteStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("update job_masters set current_state = :state , updated_datetime = now() where id = :jobMasterId")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> updateJobSubsSetStateCompleteStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("update job_subs set state = :state, updated_datetime = now() where job_master_id = :jobMasterId and id = :jobSubId")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> updateJobSubRejectsSetStateCompleteStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("update job_sub_rejects set reject_state = :state where id = :jobRejectId")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> updateJobSubRejectsSetStateWaitStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("update job_sub_rejects set reject_state = 'WAIT' " +
                        "where pre_job_id = :jobMasterId and state = 'LOCK'")
                .beanMapped()
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<RejectJob> insertIntoJobHistoriesRefineStt() {
        return new JdbcBatchItemWriterBuilder<RejectJob>()
                .dataSource(dataSource)
                .sql("INSERT INTO job_sub_histories (id, job_master_id, job_sub_id, user_id, process_code, state, reject_state) " +
                        "values (1 , :jobMasterId , :jobSubId , :userId , 'STT_refine' , 'WAIT' , 'Y') " +
                        "on duplicate key update state = :state")
                .beanMapped()
                .build();
    }

    @Bean
    public CompositeItemWriter<RejectJob> compositeItemWriter(
            @Qualifier("sttWriter") JdbcBatchItemWriter<RejectJob> sttWriter,
            @Qualifier("updateJobMastersSetStateCompleteStt") JdbcBatchItemWriter<RejectJob> updateJobMastersSetStateCompleteStt,
            @Qualifier("insertIntoJobHistoriesStt") JdbcBatchItemWriter<RejectJob> insertIntoJobHistoriesStt,
            @Qualifier("updateJobSubsSetStateCompleteStt") JdbcBatchItemWriter<RejectJob> updateJobSubsSetStateCompleteStt,
            @Qualifier("updateJobSubRejectsSetStateCompleteStt") JdbcBatchItemWriter<RejectJob> updateJobSubRejectsSetStateCompleteStt,
            @Qualifier("updateJobSubRejectsSetStateWaitStt") JdbcBatchItemWriter<RejectJob> updateJobSubRejectsSetStateWaitStt
    ) {
        CompositeItemWriter<RejectJob> writer = new CompositeItemWriter<>();
        writer.setDelegates(
                Arrays.asList(
                        sttWriter,
                        updateJobMastersSetStateCompleteStt,
                        insertIntoJobHistoriesStt,
                        updateJobSubsSetStateCompleteStt,
                        updateJobSubRejectsSetStateCompleteStt,
                        updateJobSubRejectsSetStateWaitStt
                )
        );

        return writer;
    }
    // WRITER //
}
