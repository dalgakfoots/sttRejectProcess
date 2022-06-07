package onthelive.sttRejectProcess.entity.enums;

public enum NaverLangEnum {
    en("Eng"),
    ja("Jpn"),
    zh("Chn"),
    ko("Kor"); // for test

    private String code;

    NaverLangEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return this.code;
    }
}
