package it.nexu.forwarding;

import it.nexu.forwarding.config.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static it.nexu.forwarding.config.StorageLocations.Mode.*;

/** Offline checks: no SSH, JavaFX, real user directories or external services. */
public final class PortableStorageSelfTest {
    private static int passed;
    private PortableStorageSelfTest() { }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        passed=0;
        Path temp=Files.createTempDirectory("npf-portable-tests-");
        try {
            boolean unicode = java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"))
                .newEncoder().canEncode("àè");
            Path windows=temp.resolve("Windows portable with spaces"), linux=temp.resolve(unicode ? "Linux portable àè" : "Linux portable spaces");
            marker(windows,"portable"); marker(linux,"portable");
            Path winJar=windows.resolve("app/nexu-port-forwarding.jar");
            Path linJar=linux.resolve("lib/app/nexu-port-forwarding.jar");
            Properties win=system("Windows 11",temp.resolve("win-home"));
            Properties lin=system("Linux",temp.resolve("linux-home"));
            Path appdata=temp.resolve("AppData/Local"), xdg=temp.resolve("xdg");
            var w=StorageLocations.resolve(winJar,win,Map.of("LOCALAPPDATA",appdata.toString()));
            eq(w.mode(),PORTABLE,"Windows portable is the default");
            eq(w.dataDirectory(),windows.resolve("data"),"Windows profiles use adjacent data directory");
            eq(w.logsDirectory(),windows.resolve("logs"),"Windows logs are siblings of data");
            check(!Files.exists(w.dataDirectory())&&!Files.exists(appdata),"resolution alone creates no user files");
            var l=StorageLocations.resolve(linJar,lin,Map.of("XDG_DATA_HOME",xdg.toString()));
            eq(l.dataDirectory(),linux.resolve("data"),"Linux lib/app layout resolves portable root");
            eq(l.logsDirectory(),linux.resolve("logs"),"Linux logs are not placed under bin or lib");
            Path plain=temp.resolve("java-bundle");marker(plain,"portable");
            eq(StorageLocations.resolve(plain.resolve("nexu-port-forwarding.jar"),lin,Map.of()).dataDirectory(),
                plain.resolve("data"),"standalone JAR sidecar uses adjacent data");
            Path namedLib=temp.resolve("lib");marker(namedLib,"portable");
            eq(StorageLocations.resolve(namedLib.resolve("app/nexu-port-forwarding.jar"),win,Map.of()).dataDirectory(),
                namedLib.resolve("data"),"Windows root named lib is not mistaken for Linux package layout");
            Path installed=temp.resolve("installation/app/nexu-port-forwarding.jar");
            var wi=StorageLocations.resolve(installed,win,Map.of("LOCALAPPDATA",appdata.toString()));
            eq(wi.dataDirectory(),appdata.resolve("nexu-port-forwarding/data"),"Windows installer uses AppData parent plus data subdirectory");
            eq(wi.logsDirectory(),appdata.resolve("nexu-port-forwarding/logs"),"installed logs are sibling of data");
            eq(StorageLocations.resolve(installed,lin,Map.of("XDG_DATA_HOME",xdg.toString())).dataDirectory(),
                xdg.resolve("nexu-port-forwarding/data"),"Linux installer respects XDG parent plus data subdirectory");
            eq(StorageLocations.resolve(installed,win,Map.of()).dataDirectory(),
                temp.resolve("win-home/AppData/Local/nexu-port-forwarding/data"),"Windows fallback uses parent plus data subdirectory");
            eq(StorageLocations.resolve(installed,lin,Map.of("XDG_DATA_HOME","relative/path")).dataDirectory(),
                temp.resolve("linux-home/.local/share/nexu-port-forwarding/data"),"relative XDG value is ignored");
            Properties changed=system("Linux",temp.resolve("linux-home"));
            changed.setProperty("user.dir",windows.toString());
            eq(StorageLocations.resolve(temp.resolve("target/classes"),changed,Map.of()).mode(),APPDATA,
                "working directory marker does not enable portable mode");
            changed.setProperty("nexu.home",temp.resolve("explicit-property").toString());
            var custom=StorageLocations.resolve(linJar,changed,Map.of("NEXU_PF_HOME",temp.resolve("explicit-env").toString()));
            eq(custom.dataDirectory(),temp.resolve("explicit-property"),"explicit Java property overrides environment and marker");
            eq(custom.mode(),CUSTOM,"explicit override remains identifiable");
            eq(custom.logsDirectory(),temp.resolve("explicit-property/logs"),"custom override retains legacy logs layout");
            changed.setProperty("nexu.home"," ");
            eq(StorageLocations.resolve(linJar,changed,Map.of("NEXU_PF_HOME",temp.resolve("explicit-env").toString())).dataDirectory(),
                temp.resolve("explicit-env"),"blank property permits explicit environment override");
            expect(()->StorageLocations.savePreference(custom,APPDATA),"UI cannot override externally selected directory");
            expect(()->StorageLocations.savePreference(wi,PORTABLE),"installer preference cannot write into installation");
            // Creation is performed by the normal application components, not by a migration.
            try(AppLock lock=new AppLock(w.dataDirectory()); AppLog log=AppLog.inDirectory(w.logsDirectory())) {
                log.mark("portable-fixture-started");
                check(Files.isDirectory(windows.resolve("data"))&&Files.isDirectory(windows.resolve("logs")),
                    "startup components create separate data and logs directories");
                check(!Files.exists(windows.resolve("profiles.properties")),"profiles are never scattered in application root");
            }
            check(Files.readString(w.logsDirectory().resolve("nexu-0.log")).contains("portable-fixture-started"),
                "persistent log is written to the local logs directory");
            Files.writeString(w.dataDirectory().resolve("profiles.properties"),"test-profile=local\n");
            byte[] vaultBytes={1,2,3,4,5};Files.write(w.dataDirectory().resolve("credentials.npfvault"),vaultBytes);
            StorageLocations.savePreference(w,APPDATA);
            eq(w.dataDirectory(),windows.resolve("data"),"preference change does not alter the active session");
            eq(StorageLocations.resolve(winJar,win,Map.of("LOCALAPPDATA",appdata.toString())).dataDirectory(),
                appdata.resolve("nexu-port-forwarding/data"),"AppData preference applies on next resolution");
            check(!Files.exists(appdata),"saving preference does not copy local data into AppData");
            check(Arrays.equals(vaultBytes,Files.readAllBytes(w.dataDirectory().resolve("credentials.npfvault"))),
                "changing preference leaves saved vault bytes untouched");
            StorageLocations.savePreference(w,PORTABLE);
            eq(StorageLocations.resolve(winJar,win,Map.of()).dataDirectory(),windows.resolve("data"),
                "switching back recovers the previous local directory");
            Path legacyRoot=appdata.resolve("nexu-port-forwarding"); Files.createDirectories(legacyRoot);
            Files.writeString(legacyRoot.resolve("profiles.properties"),"legacy-profile\n");
            var installedSelection=StorageLocations.resolve(installed,win,Map.of("LOCALAPPDATA",appdata.toString()));
            eq(StorageLocations.migrateLegacyUserData(installedSelection),1,"legacy root data copied once into data subdirectory");
            eq(Files.readString(installedSelection.dataDirectory().resolve("profiles.properties")),"legacy-profile\n","legacy profile bytes preserved");
            check(Files.exists(legacyRoot.resolve("profiles.properties")),"legacy source retained for rollback");
            eq(StorageLocations.migrateLegacyUserData(installedSelection),0,"migration is idempotent and never overwrites new data");
            check(Files.readString(w.dataDirectory().resolve("profiles.properties")).equals("test-profile=local\n"),
                "switching back preserves saved profile bytes");
            Path moved=temp.resolve("Moved portable");Files.move(windows,moved);
            var movedSelection=StorageLocations.resolve(moved.resolve("app/nexu-port-forwarding.jar"),win,Map.of());
            check(Files.exists(movedSelection.dataDirectory().resolve("profiles.properties")),
                "moving a portable folder keeps its profiles with the application");
            marker(linux,"invalid");
            expect(()->StorageLocations.resolve(linJar,lin,Map.of()),"invalid preference fails instead of silently using AppData");
            Files.writeString(linux.resolve("portable.properties"),"version=99\nstorage=portable\n");
            expect(()->StorageLocations.resolve(linJar,lin,Map.of()),"future preference format is rejected without migration");
            Files.writeString(linux.resolve("portable.properties"),"version=1\nstorage=\\uBAD!\n");
            expect(()->StorageLocations.resolve(linJar,lin,Map.of()),"corrupt preference is reported as an IO error");
            if(Files.getFileStore(temp).supportsFileAttributeView("posix")) {
                Files.delete(linux.resolve("portable.properties"));
                Files.createSymbolicLink(linux.resolve("portable.properties"),moved.resolve("portable.properties"));
                expect(()->StorageLocations.resolve(linJar,lin,Map.of()),"symbolic link preference is rejected");
                eq(Files.getPosixFilePermissions(moved.resolve("data")),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),"data directory remains owner-only");
                eq(Files.getPosixFilePermissions(moved.resolve("logs")),
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"),"local logs directory remains owner-only");
            }
            System.out.println("PORTABLE STORAGE RESULT: "+passed+" passed, 0 failed");
        } finally {
            try(var paths=Files.walk(temp)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    private static Properties system(String os,Path home) {
        Properties p=new Properties();p.setProperty("os.name",os);p.setProperty("user.home",home.toString());return p;
    }
    private static void marker(Path root,String value) throws IOException {
        Files.createDirectories(root);Files.writeString(root.resolve("portable.properties"),"version=1\nstorage="+value+"\n");
    }
    private static void eq(Object a,Object b,String name) {check(Objects.equals(a,b),name);}
    private static void check(boolean ok,String name) { if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name); }
    private static void expect(Checked body,String name) throws Exception {
        try {body.run();} catch(IOException e) {check(true,name);return;}throw new AssertionError(name);
    }
    @FunctionalInterface private interface Checked {void run() throws Exception;}
}
