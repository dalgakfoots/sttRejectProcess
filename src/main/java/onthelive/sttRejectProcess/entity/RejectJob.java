package onthelive.sttRejectProcess.entity;

import com.google.gson.Gson;
import lombok.Data;
import onthelive.sttRejectProcess.entity.audio.AudioResultSegment;
import onthelive.sttRejectProcess.entity.audio.OctopusJobResultValue;
import onthelive.sttRejectProcess.entity.audio.OctopusSoundRecordInfo;
import onthelive.sttRejectProcess.entity.enums.SttType;
import onthelive.sttRejectProcess.service.SpeechToTextService;
import onthelive.sttRejectProcess.util.CommonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class RejectJob {

    private Long projectId;
    private Long documentId;
    private Long sectionId;
    private Long segmentId;

    private String value;
    private OctopusSoundRecordInfo recordInfo;

    private SpeechToTextService speechToTextService;
    private String sttResult;
    private String toLang;

    private String processCode;
    private String state;

    private Long jobMasterId;
    private Long jobSubId;
    private Long userId;

    /*job_sub_rejects*/
    private Long jobRejectId;

    /*from segments*/
    private String segmentValue;
    private SttType sttSource;

    private Long historyCnt;


    public void preprocessToSpeechToText() {
        // 1. value 값을 recordInfo 에 담는다.
        OctopusSoundRecordInfo resultValue = new Gson().fromJson(value, OctopusSoundRecordInfo.class);
        setRecordInfo(resultValue);

        // 2. segmentValue 의 값중 sttSource 값을 가지고 온다.
        HashMap temp = new Gson().fromJson(segmentValue, HashMap.class);
        String sttSource = (String) temp.get("sttSource");
        setSttSource(SttType.valueOf(sttSource));
    }

    public void runStt(String fileStore) throws Exception {
        List<AudioResultSegment> segments = recordInfo.getAudioResultBySegment();
        List<OctopusJobResultValue> values = new ArrayList<>();
        try {
            for(AudioResultSegment e : segments) {
                String filePath = e.getAudioFile().getFilePath();

                String fileName = e.getAudioFile().getStorageFileName();
                String destFile = fileStore + fileName;

                CommonUtil.saveFile(filePath, destFile);
                String sttResultValue = speechToTextService.speechToText(destFile, toLang);
                OctopusJobResultValue value = new OctopusJobResultValue(e.getIndex(), sttResultValue);
                values.add(value);

                CommonUtil.deleteFile(destFile);
            }
        } catch (Exception e) {
            throw new Exception();
        }

        setSttResult(new Gson().toJson(values));
    }
}
