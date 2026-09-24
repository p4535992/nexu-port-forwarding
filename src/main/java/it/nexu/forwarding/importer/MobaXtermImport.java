package it.nexu.forwarding.importer;

import it.nexu.forwarding.config.ProfileStore;
import it.nexu.forwarding.config.SafeFiles;
import it.nexu.forwarding.model.TunnelProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Read-only importer for MobaXterm.ini [PortForwarding]. Password sections are never parsed. */
public final class MobaXtermImport {
    private static final int LIMIT=4_000_000;
    private MobaXtermImport() { }

    public record Candidate(TunnelProfile profile,List<String> warnings) {
        public Candidate { warnings=List.copyOf(warnings); }
        public boolean requiresReview() { return !profile.isLoopbackBind() || !warnings.isEmpty(); }
    }
    public record Report(List<Candidate> candidates,List<String> warnings,int entries,int skipped) {
        public Report { candidates=List.copyOf(candidates); warnings=List.copyOf(warnings); }
    }
    public record Plan(List<TunnelProfile> profiles,List<TunnelProfile> added,int duplicates) {
        public Plan { profiles=List.copyOf(profiles); added=List.copyOf(added); }
    }

    public static Report read(Path file,String installation) throws IOException {
        byte[] bytes=SafeFiles.readBytes(file,LIMIT);
        try {
            String text;
            try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException e) { text=java.nio.charset.Charset.forName("windows-1252").decode(java.nio.ByteBuffer.wrap(bytes)).toString(); }
            return parse(text,installation);
        } finally { Arrays.fill(bytes,(byte)0); }
    }

    public static Report parse(String ini,String installation) throws IOException {
        String label=clean(installation,120);
        List<Candidate> out=new ArrayList<>(); List<String> warnings=new ArrayList<>();
        boolean section=false; int entries=0,skipped=0,lineNo=0;
        for(String raw:ini.split("\\R",-1)) {
            lineNo++; String line=raw.strip();
            if(line.startsWith("\\uFEFF")) line=line.substring(1);
            if(line.startsWith("[")&&line.endsWith("]")) {
                section=line.equalsIgnoreCase("[PortForwarding]");
                continue;
            }
            if(!section||line.isEmpty()||line.startsWith("#")) continue;
            int eq=line.indexOf('=');
            if(eq<=0) { skipped++; warnings.add("Riga "+lineNo+": voce PortForwarding non valida."); continue; }
            entries++;
            String key=clean(line.substring(0,eq),200), value=line.substring(eq+1);
            try { out.add(parseEntry(key,value,label,lineNo)); }
            catch(Invalid ex) { skipped++; warnings.add("Riga "+lineNo+" ("+safeName(key)+"): "+ex.getMessage()); }
            if(entries>1000) throw new IOException("Massimo 1000 inoltri MobaXterm per importazione.");
        }
        if(entries==0) warnings.add("Nessuna voce trovata nella sezione [PortForwarding].");
        return new Report(out,warnings,entries,skipped);
    }

    private static Candidate parseEntry(String key,String value,String label,int lineNo) {
        try {
            return parseEntryInternal(key,value,label,lineNo);
        } catch (IllegalArgumentException ex) {
            String message=ex.getMessage();
            throw new Invalid(message==null||message.isBlank()?"dati del profilo non validi.":message);
        }
    }

    private static Candidate parseEntryInternal(String key,String value,String label,int lineNo) {
        String[] f=value.split(";",-1);
        if(f.length<4) throw new Invalid("formato tunnel incompleto.");
        TunnelProfile.Mode mode=switch(f[0].trim().toLowerCase(Locale.ROOT)) {
            case "local" -> TunnelProfile.Mode.LOCAL;
            case "remote" -> TunnelProfile.Mode.REMOTE;
            case "dynamic" -> TunnelProfile.Mode.DYNAMIC;
            default -> throw new Invalid("tipo non supportato: attesi Local, Remote o Dynamic.");
        };
        Ssh ssh=parseSsh(f[1]);
        Endpoint target=mode==TunnelProfile.Mode.DYNAMIC ? new Endpoint("",0) : parseEndpoint(f[2],"destinazione");
        int listenPort=port(f[3],"porta di ascolto");
        String keyFile=f.length>5?f[5].trim():"";
        TunnelProfile.Auth auth=TunnelProfile.Auth.PASSWORD;
        if(!keyFile.isEmpty()&&!keyFile.equalsIgnoreCase("No SSH key selected")) {
            if(keyFile.contains("\u0000")||keyFile.length()>4096) throw new Invalid("percorso chiave non valido.");
            try { Path.of(keyFile); } catch(Exception e) { throw new Invalid("percorso chiave non valido."); }
            auth=TunnelProfile.Auth.PRIVATE_KEY;
        } else keyFile="";
        String bind=f.length>6?f[6].trim():"";
        if(bind.isEmpty()) bind="127.0.0.1";
        String proxySpec=f.length>7?f[7].trim():"";
        ProxySettings proxy=parseProxy(proxySpec);
        List<String> rowWarnings=new ArrayList<>();
        if(proxy.type()!=TunnelProfile.ProxyType.DIRECT)
            rowWarnings.add("Proxy MobaXterm importato come "+proxyLabel(proxy.type())+"; eventuali username/password proxy non vengono importati.");
        if(bind.equals("0.0.0.0")||bind.equals("::")) rowWarnings.add("Bind non loopback: verificare l'esposizione di rete prima dell'avvio.");
        if(mode==TunnelProfile.Mode.DYNAMIC) rowWarnings.add("SOCKS locale senza autenticazione: usare preferibilmente 127.0.0.1.");
        if(auth==TunnelProfile.Auth.PRIVATE_KEY) rowWarnings.add("Percorso chiave importato; il file non è stato letto né copiato.");
        String install=label.isBlank()?installationFromKey(key):label;
        String display=displayName(key,mode,listenPort);
        TunnelProfile base=new TunnelProfile(UUID.randomUUID(),display,mode,ssh.host,ssh.port,ssh.user,bind,listenPort,
            target.host,target.port,auth,keyFile,15,15,3,false,5,3,
            "Importato da MobaXterm. Password e trust host non importati; il trasporto proxy viene importato solo quando riconosciuto.",install,TunnelProfile.Origin.MOBAXTERM,"",
            proxy.type(),proxy.host(),proxy.port(),"");
        String sourceKey=fingerprint(key,base);
        TunnelProfile p=new TunnelProfile(base.id(),base.name(),base.mode(),base.sshHost(),base.sshPort(),base.username(),base.bindHost(),base.bindPort(),
            base.targetHost(),base.targetPort(),base.auth(),base.privateKey(),base.connectTimeoutSeconds(),base.keepAliveSeconds(),base.keepAliveMisses(),
            base.reconnect(),base.reconnectAttempts(),base.reconnectDelaySeconds(),base.notes(),base.installation(),base.origin(),sourceKey,
            base.proxyType(),base.proxyHost(),base.proxyPort(),base.proxyUsername());
        return new Candidate(p,rowWarnings);
    }

    public static Plan append(List<TunnelProfile> existing,List<TunnelProfile> selected) throws IOException {
        List<TunnelProfile> next=new ArrayList<>(existing),added=new ArrayList<>(); Set<String> keys=new HashSet<>(); Set<UUID> ids=new HashSet<>();
        for(TunnelProfile p:existing){ids.add(p.id());if(!p.sourceKey().isEmpty())keys.add(p.sourceKey());}
        int duplicates=0;
        for(TunnelProfile p:selected){
            if(p.origin()!=TunnelProfile.Origin.MOBAXTERM||p.sourceKey().isEmpty()) throw new IOException("Provenienza MobaXterm non valida.");
            if(!keys.add(p.sourceKey())){duplicates++;continue;}
            if(!ids.add(p.id())){p=p.copyWithNewId();ids.add(p.id());}
            next.add(p);added.add(p);
        }
        ProfileStore.encode(next); return new Plan(next,added,duplicates);
    }

    public static List<Path> suggestedPaths() {
        List<Path> out=new ArrayList<>(); String appdata=System.getenv("APPDATA"), home=System.getProperty("user.home","");
        if(appdata!=null&&!appdata.isBlank()) out.add(Path.of(appdata,"MobaXterm","MobaXterm.ini"));
        if(!home.isBlank()) out.add(Path.of(home,"Documents","MobaXterm","MobaXterm.ini"));
        return List.copyOf(out);
    }

    private static ProxySettings parseProxy(String spec) {
        String s=spec==null?"":spec.trim();
        if(s.isEmpty()||s.equalsIgnoreCase("No proxy selected"))
            return new ProxySettings(TunnelProfile.ProxyType.DIRECT,"",0);
        String[] parts=s.split(",",-1);
        if(parts.length<3) throw new Invalid("proxy MobaXterm non valido.");
        String kind=parts[0].trim().toLowerCase(Locale.ROOT);
        TunnelProfile.ProxyType type=switch(kind) {
            case "web proxy","http proxy","http connect","https proxy" -> TunnelProfile.ProxyType.HTTP_CONNECT;
            case "socks proxy","socks5 proxy","socks 5 proxy" -> TunnelProfile.ProxyType.SOCKS5;
            default -> throw new Invalid("tipo proxy MobaXterm non supportato: "+safeProxyKind(parts[0])+".");
        };
        String host=parts[1].trim();
        int port=port(parts[2],"porta proxy");
        Endpoint endpoint=parseEndpoint((host.contains(":")&&!host.startsWith("[")?"["+host+"]":host)+":"+port,"proxy");
        return new ProxySettings(type,endpoint.host,endpoint.port);
    }
    private static String proxyLabel(TunnelProfile.ProxyType type) {
        return type==TunnelProfile.ProxyType.HTTP_CONNECT?"HTTP CONNECT":type==TunnelProfile.ProxyType.SOCKS5?"SOCKS5":"Diretto";
    }
    private static String safeProxyKind(String value) {
        String s=value==null?"":value.replaceAll("[\\p{Cc}\\p{Cf}]"," ").strip();
        return s.substring(0,Math.min(60,s.length()));
    }

    private static Ssh parseSsh(String spec) {
        String s=spec.trim(); int at=s.lastIndexOf('@'); if(at<=0||at==s.length()-1) throw new Invalid("server SSH non valido.");
        String user=s.substring(0,at).trim(), hp=s.substring(at+1).trim(); Endpoint e=parseEndpoint(hp,"server SSH");
        if(user.isBlank()||user.length()>128||user.chars().anyMatch(Character::isWhitespace)) throw new Invalid("utente SSH non valido.");
        return new Ssh(user,e.host,e.port);
    }
    private static Endpoint parseEndpoint(String spec,String field) {
        String s=spec.trim(); String host; String ps;
        if(s.startsWith("[")) { int close=s.indexOf(']'); if(close<1||close+2>s.length()||s.charAt(close+1)!=':') throw new Invalid(field+" non valida."); host=s.substring(1,close);ps=s.substring(close+2); }
        else { int colon=s.lastIndexOf(':'); if(colon<=0||colon==s.length()-1) throw new Invalid(field+" non valida.");host=s.substring(0,colon);ps=s.substring(colon+1); }
        host=host.trim(); if(host.isEmpty()||host.length()>253||host.chars().anyMatch(Character::isISOControl)) throw new Invalid(field+" non valida.");
        return new Endpoint(host,port(ps,field));
    }
    private static int port(String value,String field) { try { int p=Integer.parseInt(value.trim()); if(p<1||p>65535)throw new NumberFormatException(); return p; } catch(Exception e){throw new Invalid(field+" non valida.");} }
    private static String clean(String s,int max){if(s==null)return "";s=s.strip();if(s.length()>max||s.chars().anyMatch(Character::isISOControl))throw new Invalid("testo non valido.");return s;}
    private static String installationFromKey(String key){int dot=key.indexOf('.');String s=dot>=0&&dot+1<key.length()?key.substring(dot+1):"MobaXterm";s=s.replaceAll("[^A-Za-z0-9._ -]"," ").strip();return s.isEmpty()?"MobaXterm":s.substring(0,Math.min(120,s.length()));}
    private static String displayName(String key,TunnelProfile.Mode mode,int port){String n=installationFromKey(key);return (n+" · "+mode+" :"+port).substring(0,Math.min(120,(n+" · "+mode+" :"+port).length()));}
    private static String safeName(String key){return key.replaceAll("[\\p{Cc}\\p{Cf}]"," ").substring(0,Math.min(80,key.length()));}
    private static String fingerprint(String key,TunnelProfile p){String material=String.join("|","mobaxterm-forward-v1",key,p.sshHost().toLowerCase(Locale.ROOT),Integer.toString(p.sshPort()),p.username(),p.mode().name(),p.bindHost().toLowerCase(Locale.ROOT),Integer.toString(p.bindPort()),p.targetHost().toLowerCase(Locale.ROOT),Integer.toString(p.targetPort()),p.auth().name(),p.privateKey());if(p.usesProxy())material+="|proxy|"+p.proxyType().name()+"|"+p.proxyHost().toLowerCase(Locale.ROOT)+"|"+p.proxyPort();try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record Endpoint(String host,int port){}
    private record Ssh(String user,String host,int port){}
    private record ProxySettings(TunnelProfile.ProxyType type,String host,int port){}
    private static final class Invalid extends RuntimeException { Invalid(String m){super(m);} }
}
