package it.nexu.forwarding.config;

import it.nexu.forwarding.i18n.I18n;
import java.nio.file.*;
import java.util.Properties;

public final class LanguageSettings {
    private static final String VERSION="1";
    private LanguageSettings(){}
    public static I18n.Language load(Path file){
        if(file==null||!Files.exists(file))return I18n.Language.ENGLISH;
        try{
            Properties p=SafeFiles.read(file);
            if(!VERSION.equals(p.getProperty("version")))return I18n.Language.ENGLISH;
            return I18n.Language.fromCode(p.getProperty("language"));
        }catch(Exception ignored){return I18n.Language.ENGLISH;}
    }
    public static void save(Path file,I18n.Language language) throws Exception{
        Properties p=new Properties();
        p.setProperty("version",VERSION);
        p.setProperty("language",(language==null?I18n.Language.ENGLISH:language).code());
        SafeFiles.write(file,p,"Nexu Port Forwarding - UI language");
    }
}
