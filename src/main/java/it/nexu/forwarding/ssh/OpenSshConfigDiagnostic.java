package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only comparison of the effective local OpenSSH configuration with and without the user's config file. */
public final class OpenSshConfigDiagnostic {
    private static final long TIMEOUT_SECONDS = 6;
    private static final int OUTPUT_LIMIT = 128 * 1024;
    private static final Set<String> KEYS = Set.of(
        "hostname","user","port","proxyjump","proxycommand","identityfile","identitiesonly"
    );

    public record Result(String summary) { }

    private OpenSshConfigDiagnostic() { }

    public static Result inspect(TunnelProfile profile) throws IOException, InterruptedException {
        Objects.requireNonNull(profile);
        String executable=findExecutable();
        Map<String,List<String>> configured=run(executable,List.of("-G",profile.sshHost()));
        String nullConfig=isWindows()?"NUL":"/dev/null";
        Map<String,List<String>> clean=run(executable,List.of("-F",nullConfig,"-G",profile.sshHost()));

        List<String> changes=new ArrayList<>();
        for(String key:KEYS) {
            List<String> a=configured.getOrDefault(key,List.of());
            List<String> b=clean.getOrDefault(key,List.of());
            if(!a.equals(b)) changes.add(key+"="+display(a)+" (senza config: "+display(b)+")");
        }

        List<String> relevant=new ArrayList<>();
        String proxyJump=first(configured,"proxyjump");
        String proxyCommand=first(configured,"proxycommand");
        if(active(proxyJump)) relevant.add("ProxyJump="+proxyJump);
        if(active(proxyCommand)) relevant.add("ProxyCommand="+proxyCommand);

        String host=first(configured,"hostname");
        if(!host.isBlank()&&!host.equalsIgnoreCase(profile.sshHost())) relevant.add("HostName="+host);
        String user=first(configured,"user");
        if(!user.isBlank()&&!user.equals(profile.username())) relevant.add("User="+user+" (Nexu="+profile.username()+")");
        String port=first(configured,"port");
        if(!port.isBlank()&&!port.equals(Integer.toString(profile.sshPort()))) relevant.add("Port="+port+" (Nexu="+profile.sshPort()+")");

        List<String> identities=configured.getOrDefault("identityfile",List.of());
        List<String> cleanIdentities=clean.getOrDefault("identityfile",List.of());
        if(!identities.equals(cleanIdentities)) relevant.add("IdentityFile="+display(identities));

        StringBuilder out=new StringBuilder();
        out.append("ssh=").append(executable).append(". ");
        if(changes.isEmpty()) {
            out.append("La configurazione OpenSSH utente non modifica i parametri controllati rispetto a -F ")
                .append(nullConfig).append(".");
        } else {
            out.append("La configurazione OpenSSH utente modifica: ").append(String.join("; ",changes)).append(".");
        }
        if(!relevant.isEmpty()) {
            out.append(" Possibili differenze rispetto a Nexu: ").append(String.join("; ",relevant)).append(".");
        } else if(!changes.isEmpty()) {
            out.append(" Nessuna differenza critica ProxyJump/ProxyCommand/HostName/User/Port/IdentityFile rilevata oltre a quelle elencate.");
        }
        return new Result(bound(out.toString(),1100));
    }

    private static Map<String,List<String>> run(String executable,List<String> args) throws IOException,InterruptedException {
        List<String> command=new ArrayList<>(); command.add(executable); command.addAll(args);
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean done=process.waitFor(TIMEOUT_SECONDS,TimeUnit.SECONDS);
        if(!done) {
            process.destroyForcibly();
            process.waitFor(1,TimeUnit.SECONDS);
            throw new IOException("ssh -G non ha terminato entro "+TIMEOUT_SECONDS+" secondi.");
        }
        byte[] bytes=process.getInputStream().readNBytes(OUTPUT_LIMIT+1);
        if(bytes.length>OUTPUT_LIMIT) throw new IOException("Output di ssh -G troppo grande.");
        String output=new String(bytes,StandardCharsets.UTF_8);
        if(process.exitValue()!=0) throw new IOException("ssh -G è terminato con codice "+process.exitValue()+": "+bound(output.replaceAll("[\\p{Cntrl}]"," ").trim(),240));

        Map<String,List<String>> values=new LinkedHashMap<>();
        for(String line:output.split("\\R")) {
            String trimmed=line.trim();
            if(trimmed.isEmpty()) continue;
            int split=trimmed.indexOf(' ');
            if(split<=0) continue;
            String key=trimmed.substring(0,split).toLowerCase(Locale.ROOT);
            if(!KEYS.contains(key)) continue;
            String value=trimmed.substring(split+1).trim();
            values.computeIfAbsent(key,k->new ArrayList<>()).add(value);
        }
        return values;
    }

    private static String findExecutable() {
        if(isWindows()) {
            String windir=System.getenv("WINDIR");
            Path system=Path.of(windir==null||windir.isBlank()?"C:\\Windows":windir,"System32","OpenSSH","ssh.exe");
            if(Files.isRegularFile(system)) return system.toString();
            return "ssh.exe";
        }
        for(String candidate:List.of("/usr/bin/ssh","/bin/ssh")) if(Files.isExecutable(Path.of(candidate))) return candidate;
        return "ssh";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");
    }
    private static String first(Map<String,List<String>> values,String key) {
        List<String> all=values.get(key); return all==null||all.isEmpty()?"":all.getFirst();
    }
    private static boolean active(String value) {
        return value!=null&&!value.isBlank()&&!value.equalsIgnoreCase("none");
    }
    private static String display(List<String> values) {
        if(values==null||values.isEmpty()) return "—";
        return "["+String.join(", ",values)+"]";
    }
    private static String bound(String value,int max) { return value.length()>max?value.substring(0,max):value; }
}
