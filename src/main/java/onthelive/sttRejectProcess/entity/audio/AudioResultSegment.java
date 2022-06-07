package onthelive.sttRejectProcess.entity.audio;

import lombok.Data;
import lombok.ToString;

@ToString
@Data
public class AudioResultSegment{
    private int index;
    private AudioFile audioFile;
}
