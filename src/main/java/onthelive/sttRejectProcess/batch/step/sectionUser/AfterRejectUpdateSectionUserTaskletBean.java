package onthelive.sttRejectProcess.batch.step.sectionUser;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AfterRejectUpdateSectionUserTaskletBean {

    private final StepBuilderFactory stepBuilderFactory;
    private final AfterRejectUpdateSectionUserTasklet afterRejectUpdateSectionUserTasklet;

    @Bean
    public Step afterRejectUpdateSectionUserStep() throws Exception {
        return stepBuilderFactory.get("afterRejectUpdateSectionUserStep")
                .tasklet(afterRejectUpdateSectionUserTasklet)
                .build();
    }

}
