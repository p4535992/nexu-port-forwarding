package it.nexu.forwarding.i18n;

import java.util.Locale;
import java.util.Objects;

public final class I18n {
    public enum Language {
        ENGLISH("en","English"),
        ITALIAN("it","Italiano");
        private final String code,displayName;
        Language(String code,String displayName){this.code=code;this.displayName=displayName;}
        public String code(){return code;}
        public String displayName(){return displayName;}
        public static Language fromCode(String value){
            if(value==null)return ENGLISH;
            String code=value.trim().toLowerCase(Locale.ROOT);
            return "it".equals(code)||"italian".equals(code)||"italiano".equals(code)?ITALIAN:ENGLISH;
        }
    }
    private static volatile Language language=Language.ENGLISH;
    private I18n(){}
    public static Language language(){return language;}
    public static void setLanguage(Language value){language=Objects.requireNonNullElse(value,Language.ENGLISH);}
    public static String t(String english,String italian){return language==Language.ITALIAN?italian:english;}
}
