package onthelive.sttRejectProcess.entity.naver;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NaverSpeechToTextRequestBody {
    private byte[] image;
}
