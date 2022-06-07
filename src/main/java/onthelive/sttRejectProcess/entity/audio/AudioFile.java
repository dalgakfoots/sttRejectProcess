package onthelive.sttRejectProcess.entity.audio;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AudioFile {

    private String originFileName;
    private String storageFileName;
    private String filePath;
    private String fileType;
    private String fileSize;

}
