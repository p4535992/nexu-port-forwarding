package it.nexu.forwarding.importer;

import it.nexu.forwarding.config.ProfileStore;
import it.nexu.forwarding.model.TunnelProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Whitelist-only conversion. No credentials, scripts, trust or YAML text are retained. */
public final class TabbyImport {
    private TabbyImport() { }
    public record Candidate(TunnelProfile profile, List<String> warnings) {
        public Candidate { warnings = List.copyOf(warnings); }
        public boolean requiresReview() {
            return !profile.isLoopbackBind() || warnings.stream().anyMatch(w->w.startsWith("Più chiavi")
                || w.startsWith("Bind assente"));
        }
    }
    public record Report(List<Candidate> candidates, List<String> warnings, int sshProfiles,
                         int skippedProfiles, int skippedForwards) {
        public Report { candidates = List.copyOf(candidates); warnings = List.copyOf(warnings); }
    }
    public record Plan(List<TunnelProfile> profiles, List<TunnelProfile> added, int duplicates) {
        public Plan { profiles=List.copyOf(profiles); added=List.copyOf(added); }
    }

    public static Report convert(Object document, String installation) throws IOException {
        try { return convertChecked(document, installation); }
        catch (Invalid e) { throw new IOException(e.getMessage()); }
    }
    private static Report convertChecked(Object document, String installation) {
        String label = text(installation,120,false);
        Map<?,?> root = map(document);
        if (!(root.get("profiles") instanceof List<?>))
            throw new Invalid("Nessun elenco profiles leggibile. Esportare una configurazione Tabby YAML non cifrata.");
        List<?> profiles = list(root.get("profiles"),1000);
        Map<String,Map<?,?>> groups = new HashMap<>();
        for (Object g : listOrEmpty(root.get("groups"),1000)) {
            Map<?,?> group=map(g);
            String id=text(group.get("id"),512,false);
            if (!id.isEmpty() && groups.put(id,group)!=null)
                throw new Invalid("ID di gruppo duplicato nella configurazione Tabby.");
        }
        Map<?,?> globals = nestedOptions(root.get("profileDefaults"));
        List<Candidate> out = new ArrayList<>(); List<String> warnings = new ArrayList<>();
        int ssh=0, skipped=0, forwardsSkipped=0, totalForwards=0;
        for (int i=0;i<profiles.size();i++) {
            Object value=profiles.get(i);
            if (!(value instanceof Map<?,?> profile) || !"ssh".equals(profile.get("type"))) continue;
            if (Boolean.TRUE.equals(profile.get("isTemplate"))) continue;
            ssh++;
            String context="Profilo SSH " + (i+1);
            try {
                Map<String,Object> options=new LinkedHashMap<>();
                options.put("port",22); options.put("user","root");
                options.put("keepaliveInterval",5000); options.put("keepaliveCountMax",10);
                overlay(options,globals);
                String groupId=text(profile.get("group"),512,false);
                Map<?,?> group=groups.getOrDefault(groupId,Map.of());
                overlay(options,nestedOptions(group.get("defaults")));
                overlay(options,mapOrEmpty(profile.get("options")));
                List<?> forwards=listOrEmpty(options.get("forwardedPorts"),1000);
                totalForwards+=forwards.size();
                if (totalForwards>1000) throw new Invalid("Massimo 1000 inoltri per importazione.");
                if (forwards.isEmpty()) continue;
                for (String unsupported : List.of("jumpHost","proxyCommand","socksProxyHost","httpProxyHost")) {
                    if (present(options.get(unsupported)))
                        throw new Invalid("Jump host / proxy non supportato: profilo non importato, senza collegamento diretto alternativo.");
                }
                String sshHost=text(options.get("host"),253,true);
                int sshPort=number(options.get("port"),22,1,65535);
                String user=text(options.get("user"),128,true);
                String profileName=display(profile.get("name"),120);
                if (profileName.isEmpty()) profileName="Profilo SSH " + (i+1);
                String tabbyId=text(profile.get("id"),1024,false);
                if (tabbyId.isEmpty()) tabbyId="name:"+profileName;
                String groupName=display(group.get("name"),120);
                String installationName=label.isEmpty() ? (groupName.isEmpty()?display(groupId,120):groupName) : label;
                List<String> notes=new ArrayList<>();
                String authName=text(options.get("auth"),64,false);
                TunnelProfile.Auth auth; String key="";
                List<?> keys=listOrEmpty(options.get("privateKeys"),100);
                if (authName.equals("agent") || authName.equals("keyboardInteractive"))
                    throw new Invalid("Autenticazione SSH agent / keyboard-interactive non supportata: profilo non importato.");
                if (!authName.isEmpty() && !authName.equals("password") && !authName.equals("publicKey"))
                    throw new Invalid("Metodo di autenticazione Tabby non riconosciuto.");
                if (authName.equals("publicKey") || (authName.isEmpty() && !keys.isEmpty())) {
                    if (keys.isEmpty()) throw new Invalid("Autenticazione a chiave senza un file esplicito: selezionare la chiave in Tabby prima dell'export.");
                    key=keyPath(text(keys.get(0),4096,true),sshHost,user);
                    auth=TunnelProfile.Auth.PRIVATE_KEY;
                    if (keys.size()>1) notes.add("Più chiavi Tabby: viene usato solo il primo percorso. Verificare la scelta.");
                } else auth=TunnelProfile.Auth.PASSWORD;
                if (authName.isEmpty()) notes.add("Metodo di autenticazione non dichiarato da Tabby: profilo importato senza credenziale salvata; Nexu la richiederà all'avvio.");
                int interval=seconds(options.get("keepaliveInterval"),5000,1,3600,notes);
                int misses=numberClamped(options.get("keepaliveCountMax"),10,1,10,notes);
                int timeout=seconds(options.get("readyTimeout"),15000,3,300,notes);
                // Keepalive semantics differ across SSH libraries. Never pretend these are identical.
                notes.add("Keepalive convertito in secondi; soglia MINA basata sul timeout, non sul contatore Tabby.");
                if (present(options.get("scripts")) || Boolean.TRUE.equals(options.get("x11"))
                    || Boolean.TRUE.equals(options.get("agentForward")))
                    notes.add("Script, X11 e agent forwarding non vengono eseguiti o importati.");
                for (int j=0;j<forwards.size();j++) {
                    try {
                        Map<?,?> f=map(forwards.get(j));
                        if (Boolean.FALSE.equals(f.get("enabled"))) { forwardsSkipped++; warnings.add(context+", inoltro "+(j+1)+": disabilitato, non importato."); continue; }
                        TunnelProfile.Mode mode;
                        try { mode=TunnelProfile.Mode.valueOf(text(f.get("type"),16,true).toUpperCase(Locale.ROOT)); }
                        catch (IllegalArgumentException ex) { throw new Invalid("Tipo di inoltro non riconosciuto."); }
                        String host=text(f.get("host"),253,false);
                        List<String> rowNotes=new ArrayList<>(notes);
                        if (host.isEmpty()) { host="127.0.0.1"; rowNotes.add("Bind assente: limitato al loopback per sicurezza."); }
                        int port=number(f.get("port"),-1,1,65535);
                        String target=mode==TunnelProfile.Mode.DYNAMIC?"":text(f.get("targetAddress"),253,true);
                        int targetPort=mode==TunnelProfile.Mode.DYNAMIC?0:number(f.get("targetPort"),-1,1,65535);
                        String description=display(f.get("description"),120);
                        String name=display(profileName+(description.isEmpty()?" · "+mode+" :"+port:" · "+description),120);
                        TunnelProfile base=new TunnelProfile(UUID.randomUUID(),name,mode,sshHost,sshPort,user,host,port,
                            target,targetPort,auth,key,timeout,interval,misses,false,5,3,
                            "Importato da Tabby. Credenziali e fiducia nelle chiavi host non importate.",installationName,TunnelProfile.Origin.TABBY,"");
                        String sourceKey=fingerprint(tabbyId,base);
                        TunnelProfile p=new TunnelProfile(base.id(),name,mode,base.sshHost(),sshPort,user,base.bindHost(),port,
                            base.targetHost(),base.targetPort(),auth,key,timeout,interval,misses,false,5,3,base.notes(),installationName,TunnelProfile.Origin.TABBY,sourceKey);
                        if (!p.isLoopbackBind()) rowNotes.add("Ascolto non loopback: può esporre il servizio alla rete.");
                        if (mode==TunnelProfile.Mode.DYNAMIC) rowNotes.add("Proxy SOCKS senza autenticazione locale: mantenere il bind su loopback.");
                        out.add(new Candidate(p,rowNotes));
                    } catch (RuntimeException e) {
                        forwardsSkipped++; warnings.add(context+", inoltro "+(j+1)+": campi mancanti o non validi (tipo, bind, porte o destinazione).");
                    }
                }
            } catch (Invalid e) { skipped++; warnings.add(context+": "+e.getMessage()); }
            catch (RuntimeException e) { skipped++; warnings.add(context+": configurazione non valida; nessun inoltro importato."); }
        }
        if (totalForwards>1000) throw new Invalid("Massimo 1000 inoltri per importazione.");
        return new Report(out,warnings,ssh,skipped,forwardsSkipped);
    }
    /** Append only; repeated imports never overwrite custom labels, edited rows or credentials. */
    public static Plan append(List<TunnelProfile> existing,List<TunnelProfile> selected) throws IOException {
        List<TunnelProfile> next=new ArrayList<>(existing), added=new ArrayList<>();
        Set<String> keys=new HashSet<>(); Set<UUID> ids=new HashSet<>();
        for(TunnelProfile p:existing) { ids.add(p.id()); if(!p.sourceKey().isEmpty()) keys.add(p.sourceKey()); }
        int duplicates=0;
        for(TunnelProfile p:selected) {
            if(p.origin()!=TunnelProfile.Origin.TABBY || p.sourceKey().isEmpty()) throw new IOException("Provenienza importazione non valida.");
            if(!keys.add(p.sourceKey())) { duplicates++; continue; }
            if(!ids.add(p.id())) { p=p.copyWithNewId(); ids.add(p.id()); }
            next.add(p); added.add(p);
        }
        ProfileStore.encode(next); // Count and total serialized size validated before any write.
        return new Plan(next,added,duplicates);
    }
    private static String fingerprint(String tabbyId,TunnelProfile p) {
        List<String> parts=List.of("tabby-forward-v1",tabbyId,p.sshHost().toLowerCase(Locale.ROOT),""+p.sshPort(),p.username(),
            p.mode().name(),p.bindHost().toLowerCase(Locale.ROOT),""+p.bindPort(),p.targetHost().toLowerCase(Locale.ROOT),
            ""+p.targetPort(),p.auth().name(),p.privateKey());
        StringBuilder material=new StringBuilder();
        for(String part:parts) material.append(part.length()).append(':').append(part);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String keyPath(String key,String host,String user) {
        // Tabby may reference a vault/cloud provider instead of an on-disk file: do not fetch these.
        if (key.contains("://") || key.startsWith("vault:") || key.startsWith("\\\\") || key.startsWith("//"))
            throw new Invalid("Chiave non locale o nel vault Tabby: selezionare un file locale esplicito.");
        key=key.replace("%h",host).replace("%r",user);
        if(key.startsWith("~/") || key.startsWith("~\\")) key=Path.of(System.getProperty("user.home"),key.substring(2)).toString();
        if(key.contains("%") || key.contains("${")) throw new Invalid("Percorso chiave con variabili non supportate.");
        // A foreign absolute path is kept, but is not read during import. Runtime validates it on start.
        if(!Path.of(key).isAbsolute() && !key.matches("^[A-Za-z]:[\\\\/].*"))
            throw new Invalid("Percorso chiave relativo: usare un percorso assoluto.");
        return key;
    }
    private static Map<?,?> nestedOptions(Object defaults) { return mapOrEmpty(mapOrEmpty(mapOrEmpty(defaults).get("ssh")).get("options")); }
    private static void overlay(Map<String,Object> out,Map<?,?> in) {
        for(var e:in.entrySet()) if(e.getKey() instanceof String key) out.put(key,e.getValue());
    }
    private static Map<?,?> map(Object v) { if(v instanceof Map<?,?> m) return m; throw new Invalid("Struttura YAML inattesa."); }
    private static Map<?,?> mapOrEmpty(Object v) { return v==null?Map.of():map(v); }
    private static List<?> list(Object v,int max) {
        if(!(v instanceof List<?> l)||l.size()>max) throw new Invalid("Elenco mancante, non valido o oltre il limite di importazione.");
        return l;
    }
    private static List<?> listOrEmpty(Object v,int max) { return v==null?List.of():list(v,max); }
    private static boolean present(Object v) {
        return v!=null && !(v instanceof String s&&s.isBlank()) && !(v instanceof Collection<?> c&&c.isEmpty()) && !Boolean.FALSE.equals(v);
    }
    private static String text(Object v,int max,boolean required) {
        if(v==null&&!required) return "";
        if(!(v instanceof String s)||s.trim().length()>max||s.chars().anyMatch(Character::isISOControl)||(required&&s.isBlank()))
            throw new Invalid("Campo testuale assente o non valido.");
        return s.trim();
    }
    private static String display(Object v,int max) {
        if(v==null) return "";
        if(!(v instanceof String s)) throw new Invalid("Nome o descrizione non testuale.");
        s=s.replaceAll("[\\p{Cc}\\p{Cf}]"," ").strip();
        return s.substring(0,Math.min(max,s.length()));
    }
    private static int number(Object v,int fallback,int min,int max) {
        int n=fallback;
        if(v!=null) {
            if(!(v instanceof Number)&&!(v instanceof String)) throw new Invalid("Valore numerico non valido.");
            String s=v.toString();
            if(!s.matches("[0-9]{1,9}")) throw new Invalid("Valore numerico non valido.");
            try { n=Integer.parseInt(s); } catch(NumberFormatException e) { throw new Invalid("Valore numerico non valido."); }
        }
        if(n<min||n>max) throw new Invalid("Valore numerico fuori intervallo.");
        return n;
    }
    private static int numberClamped(Object v,int fallback,int min,int max,List<String> notes) {
        int n=number(v,fallback,0,999_999_999),result=Math.max(min,Math.min(max,n));
        if(result!=n) notes.add("Valore avanzato adattato ai limiti di Nexu Port Forwarding."); return result;
    }
    private static int seconds(Object v,int fallback,int min,int max,List<String> notes) {
        int ms=number(v,fallback,0,999_999_999); int seconds=(int)Math.ceil(ms/1000.0);
        int result=Math.max(min,Math.min(max,seconds));
        if(result!=seconds||ms%1000!=0) notes.add("Timeout/keepalive arrotondato o adattato ai limiti supportati.");
        return result;
    }
    private static final class Invalid extends RuntimeException { Invalid(String message) { super(message); } }
}
