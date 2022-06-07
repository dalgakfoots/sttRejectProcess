package onthelive.sttRejectProcess.entity.audio;

import lombok.Data;

import java.util.List;

@Data
public class OctopusSoundRecordInfo {
    private AudioFile audioFile;
    private String recordTime;
    private List<AudioResultSegment> audioResultBySegment;
}
