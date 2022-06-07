package onthelive.sttRejectProcess.entity.enums;

public enum GoogleLangEnum {

    en("en-US"),
    ja("ja-JP"),
    zh("zh"), // 북경어(중국)
    es("es-ES"),
    ru("ru-RU"),
    de("de-DE"),
    fr("fr-FR"),
    ko("ko-KR"); // for test

    private String code;

    GoogleLangEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }


}
