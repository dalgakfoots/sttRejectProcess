package onthelive.sttRejectProcess.entity.audio;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OctopusJobResultValue {
    private int index;
    private String sttText;
}
