package onthelive.sttRejectProcess.batch.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import onthelive.sttRejectProcess.entity.RejectJob;
import onthelive.sttRejectProcess.entity.enums.SttType;
import onthelive.sttRejectProcess.service.azure.AzureSpeechToTextService;
import onthelive.sttRejectProcess.service.gcp.GcpSpeechToTextService;
import onthelive.sttRejectProcess.service.naver.NaverSpeechToTextService;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpeechToTextProcessor implements ItemProcessor<RejectJob, RejectJob> {

    private final GcpSpeechToTextService gcpSpeechToTextService;
    private final AzureSpeechToTextService azureSpeechToTextService;
    private final NaverSpeechToTextService naverSpeechToTextService;

    private final JdbcTemplate jdbcTemplate;

    @Value("${dest-file}")
    private String fileStore;

    @Override
    public RejectJob process(RejectJob item) throws Exception {

        Long historyId = updateTableProcess(item);
        item.setHistoryCnt(historyId);
        item.setProcessCode("STT");

        item.preprocessToSpeechToText();
        SttType sttType = item.getSttSource();
        if(sttType == SttType.GOOGLE) {
            item.setSpeechToTextService(gcpSpeechToTextService);
        } else if (sttType == SttType.AZURE) {
            item.setSpeechToTextService(azureSpeechToTextService);
        } else if (sttType == SttType.NAVER) {
            item.setSpeechToTextService(naverSpeechToTextService);
        }

        try {
            item.runStt(fileStore);
            item.setState("COMPLETE");
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            item.setState("FAIL");
            failProcess(item);
            return item;
        }
    }

    /* PRIVATE METHODS */

    private Long updateTableProcess(RejectJob item) {
        Long jobMasterId = item.getJobMasterId();
        Long jobSubId = item.getJobSubId();
        Long userId = item.getUserId();

        Long historyId = getHistoryId(jobMasterId, jobSubId);

        jdbcTemplate.update("UPDATE job_masters SET current_state = 'PROGRESS', updated_datetime = now() WHERE id = ?", jobMasterId);
        jdbcTemplate.update("UPDATE job_subs SET state = 'PROGRESS', updated_datetime = now() WHERE job_master_id = ? and id = ? ", jobMasterId, jobSubId);

        jdbcTemplate.update("INSERT INTO job_sub_histories (id, job_master_id, job_sub_id, user_id, process_code, state, reject_state) " +
                        "VALUES (?, ? , ? , ? , 'STT', 'PROGRESS' , 'Y')",
                historyId, jobMasterId, jobSubId, userId
        );

        return historyId;
    }

    private Long getHistoryId(Long jobMasterId, Long jobSubId) {
        Long historyId = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_sub_histories WHERE job_master_id = ? AND job_sub_id = ?", Long.class,
                jobMasterId, jobSubId
        );
        return historyId;
    }

    private void failProcess(RejectJob item) {
        log.info("failProcess.....");

        jdbcTemplate.update("UPDATE job_masters SET current_state = 'FAIL', updated_datetime = now() WHERE id = ?", item.getJobMasterId());
        jdbcTemplate.update("UPDATE job_subs SET state = 'FAIL', updated_datetime = now() WHERE job_master_id = ? and id = ? ", item.getJobMasterId(), item.getJobSubId());

        jdbcTemplate.update("INSERT INTO job_sub_histories (id, job_master_id, job_sub_id, user_id, process_code, state, reject_state) " +
                        "VALUES (?, ? , ? , ? , 'STT', 'FAIL' , '0') on duplicate key update state = 'FAIL'",
                item.getHistoryCnt() + 1, item.getJobMasterId(), item.getJobSubId(), item.getUserId()
        );
    }
}
