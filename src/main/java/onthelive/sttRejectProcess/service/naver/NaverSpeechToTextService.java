package onthelive.sttRejectProcess.service.naver;

import lombok.extern.slf4j.Slf4j;
import onthelive.sttRejectProcess.entity.enums.NaverLangEnum;
import onthelive.sttRejectProcess.entity.naver.NaverSpeechToTextRequestBody;
import onthelive.sttRejectProcess.entity.naver.NaverSpeechToTextResponseBody;
import onthelive.sttRejectProcess.service.SpeechToTextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class NaverSpeechToTextService implements SpeechToTextService {

    @Value("${naver.clientId}")
    private String clientId;

    @Value("${naver.clientSecret}")
    private String clientSecret;

    @Value("${naver.apiURL}")
    private String apiURL;

    @Override
    public String speechToText(String filePath, String toLang) throws Exception {
        Path path = Paths.get(filePath);
        byte[] data = Files.readAllBytes(path);

        NaverSpeechToTextRequestBody body = new NaverSpeechToTextRequestBody(data);

        WebClient client = WebClient.builder()
                .baseUrl(apiURL+ NaverLangEnum.valueOf(toLang).getCode())
                .build();

        NaverSpeechToTextResponseBody response = client.post()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-NCP-APIGW-API-KEY-ID", clientId)
                .header("X-NCP-APIGW-API-KEY", clientSecret)
                .bodyValue(body.getImage())
                .retrieve()
                .onStatus(HttpStatus::is4xxClientError, e -> e.bodyToMono(String.class).map(Exception::new))
                .bodyToMono(NaverSpeechToTextResponseBody.class)
                .block();

        return response.getText();
    }
}
