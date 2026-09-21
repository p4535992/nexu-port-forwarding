package it.nexu.forwarding;

import it.nexu.forwarding.importer.TabbyImport;
import it.nexu.forwarding.model.*;
import it.nexu.forwarding.config.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Pure conversion, storage and migration tests: no Tabby file or network is accessed. */
public final class TabbyImportSelfTest {
    private static int passed;
    @FunctionalInterface interface Check { void run() throws Exception; }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        passed=0;
        test("local remote dynamic map to three independent stopped profiles",()->{
            var report=convert(ssh("Local","Remote","Dynamic")); eq(report.candidates().size(),3);
            eq(report.candidates().stream().map(c->c.profile().mode()).toList(),List.of(TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE,TunnelProfile.Mode.DYNAMIC));
            eq(report.candidates().stream().map(c->c.profile().id()).distinct().count(),3L);
            check(report.candidates().stream().noneMatch(c->c.profile().reconnect()));
        });
        test("remote keeps listen and destination direction",()->{
            var p=one(ssh("Remote")); eq(p.bindPort(),8989); eq(p.targetPort(),8081); eq(p.targetHost(),"service.example.com");
        });
        test("dynamic has no fixed destination",()->{
            var p=one(ssh("Dynamic")); eq(p.targetHost(),""); eq(p.targetPort(),0); check(p.destination().contains("SOCKS"));
        });
        test("dynamic ignores malformed irrelevant target fields",()->{
            var p=ssh("Dynamic"); forward(p).put("targetPort",Map.of()); forward(p).put("targetAddress",List.of());
            eq(one(p).mode(),TunnelProfile.Mode.DYNAMIC);
        });
        test("custom installation name is carried and searchable",()->{
            var p=TabbyImport.convert(root(ssh("Local")),"Cliente Alfa · Produzione").candidates().getFirst().profile();
            eq(p.installation(),"Cliente Alfa · Produzione"); check(p.searchable().contains("cliente alfa"));
        });
        test("blank installation takes the Tabby group name",()->{
            var p=ssh("Local"); p.put("group","grp-1"); var r=root(p); r.put("groups",List.of(obj("id","grp-1","name","London Office")));
            eq(TabbyImport.convert(r,"").candidates().getFirst().profile().installation(),"London Office");
        });
        test("old string group labels are readable",()->{var p=ssh("Local"); p.put("group","My group");eq(one(p).installation(),"My group");});
        test("global then direct group then profile defaults are applied",()->{
            var p=ssh("Local"); p.put("group","g");opts(p).remove("port");opts(p).remove("user");
            var r=root(p); r.put("profileDefaults",obj("ssh",obj("options",obj("port",2222,"user","global"))));
            r.put("groups",List.of(obj("id","g","name","Group","defaults",obj("ssh",obj("options",obj("user","group-user"))))));
            var result=TabbyImport.convert(r,"").candidates().getFirst().profile();eq(result.sshPort(),2222);eq(result.username(),"group-user");
            opts(p).put("user","specific-user"); eq(TabbyImport.convert(r,"").candidates().getFirst().profile().username(),"specific-user");
        });
        test("explicit empty forward list replaces defaults",()->{
            var p=ssh("Local");opts(p).put("forwardedPorts",List.of());var r=root(p);
            r.put("profileDefaults",obj("ssh",obj("options",obj("forwardedPorts",List.of(fwd("Remote"))))));
            eq(TabbyImport.convert(r,"").candidates().size(),0);
        });
        test("inherited forward list is supported",()->{
            var p=ssh("Local");opts(p).remove("forwardedPorts");var r=root(p);
            r.put("profileDefaults",obj("ssh",obj("options",obj("forwardedPorts",List.of(fwd("Remote"))))));
            eq(TabbyImport.convert(r,"").candidates().getFirst().profile().mode(),TunnelProfile.Mode.REMOTE);
        });
        test("non SSH and template profiles are ignored",()->{
            var p=ssh("Local"); p.put("isTemplate",true);
            var r=obj("profiles",List.of(obj("type","local"),p,ssh("Remote")));
            eq(TabbyImport.convert(r,"").candidates().size(),1);
        });
        test("credentials and unrelated fields are never exported",()->{
            var p=ssh("Local");opts(p).put("password","synthetic-do-not-import");opts(p).put("privateKeyContents","synthetic-do-not-import");
            p.put("secret","synthetic-do-not-import");var r=root(p);r.put("vault",obj("content","synthetic-do-not-import"));
            byte[] b=ProfileStore.encode(TabbyImport.convert(r,"").candidates().stream().map(TabbyImport.Candidate::profile).toList());
            check(!new String(b,StandardCharsets.UTF_8).contains("synthetic-do-not-import"));
        });
        test("reimport skips identical rows and preserves label",()->{
            var p=one(ssh("Local"));var existing=p.withInstallation("My edited label");
            var plan=TabbyImport.append(List.of(existing),List.of(one(ssh("Local"))));
            eq(plan.added().size(),0);eq(plan.duplicates(),1);eq(plan.profiles().getFirst().installation(),"My edited label");
        });
        test("reordering forwards does not duplicate",()->{
            var a=convert(ssh("Remote","Dynamic","Local")).candidates().stream().map(TabbyImport.Candidate::profile).toList();
            var b=convert(ssh("Local","Remote","Dynamic")).candidates().stream().map(TabbyImport.Candidate::profile).toList();
            eq(TabbyImport.append(a,b).added().size(),0);
        });
        test("same YAML duplicate rows import only once",()->{
            var ps=convert(ssh("Local","Local")).candidates().stream().map(TabbyImport.Candidate::profile).toList();
            var plan=TabbyImport.append(List.of(),ps);eq(plan.added().size(),1);eq(plan.duplicates(),1);
        });
        test("changed forwarding appends without overwriting",()->{
            var old=one(ssh("Local"));var p=ssh("Local");forward(p).put("targetPort",8082);
            var plan=TabbyImport.append(List.of(old),List.of(one(p)));eq(plan.profiles().size(),2);eq(plan.profiles().getFirst(),old);
        });
        test("profile rename with stable id is not duplicated",()->{
            var p=ssh("Local");var before=one(p);p.put("name","New name");eq(one(p).sourceKey(),before.sourceKey());
        });
        test("no id uses stable profile fallback",()->{var p=ssh("Local");p.remove("id");eq(one(p).sourceKey(),one(p).sourceKey());});
        test("port 0 is skipped instead of being silently reassigned",()->{var p=ssh("Local");forward(p).put("port",0);eq(convert(p).skippedForwards(),1);});
        test("disabled forwarding is not imported",()->{var p=ssh("Local");forward(p).put("enabled",false);eq(convert(p).candidates().size(),0);eq(convert(p).skippedForwards(),1);});
        test("milliseconds convert without weakening unsupported bounds silently",()->{var p=ssh("Local");opts(p).put("keepaliveInterval",1501);opts(p).put("keepaliveCountMax",100);opts(p).put("readyTimeout",1000);var result=one(p);eq(result.keepAliveSeconds(),2);eq(result.keepAliveMisses(),10);eq(result.connectTimeoutSeconds(),3);check(convert(p).candidates().getFirst().warnings().size()>1);});
        test("invalid type is skipped",()->{eq(convert(ssh("SomethingElse")).skippedForwards(),1);});
        test("invalid target does not discard other forwards",()->{var p=ssh("Local","Dynamic");forward(p).put("targetAddress","https://invalid.example");eq(convert(p).candidates().size(),1);});
        test("invalid numeric value is not interpreted as boolean",()->{var p=ssh("Local");forward(p).put("port",true);eq(convert(p).candidates().size(),0);});
        test("negative or fractional ports are rejected",()->{for(Object v:List.of(-1,22.5,"22.5","999999999999")){var p=ssh("Local");forward(p).put("port",v);eq(convert(p).candidates().size(),0);}});
        test("string ports are accepted",()->{var p=ssh("Local");opts(p).put("port","2222");forward(p).put("port","8989");eq(one(p).sshPort(),2222);});
        test("jump host and connection proxies cannot silently become direct",()->{
            for(String key:List.of("jumpHost","proxyCommand","socksProxyHost","httpProxyHost")){
                var p=ssh("Local");opts(p).put(key,"synthetic-hidden-value");var r=convert(p);
                eq(r.candidates().size(),0);eq(r.skippedProfiles(),1);check(!String.join("",r.warnings()).contains("synthetic-hidden-value"));
            }
        });
        test("agent and MFA require unsupported profile warning",()->{for(String auth:List.of("agent","keyboardInteractive")){var p=ssh("Local");opts(p).put("auth",auth);eq(convert(p).skippedProfiles(),1);}});
        test("unknown authentication is not guessed",()->{var p=ssh("Local");opts(p).put("auth","unknown");eq(convert(p).skippedProfiles(),1);});
        test("auto authentication is marked for review",()->{var p=ssh("Local");opts(p).remove("auth");check(convert(p).candidates().getFirst().requiresReview());});
        test("private key path is imported without reading the file",()->{
            var p=ssh("Local");opts(p).put("auth","publicKey");opts(p).put("privateKeys",List.of("/does-not-exist/example-key"));
            eq(one(p).auth(),TunnelProfile.Auth.PRIVATE_KEY);eq(one(p).privateKey(),"/does-not-exist/example-key");
        });
        test("multiple private keys require review",()->{
            var p=ssh("Local");opts(p).put("auth","publicKey");opts(p).put("privateKeys",List.of("/tmp/a","/tmp/b"));check(convert(p).candidates().getFirst().requiresReview());
        });
        test("vault and URL private key references are skipped",()->{for(String key:List.of("vault:example","https://example.com/key","relative-key")){var p=ssh("Local");opts(p).put("auth","publicKey");opts(p).put("privateKeys",List.of(key));eq(convert(p).skippedProfiles(),1);}});
        test("private key placeholders are expanded",()->{var p=ssh("Local");opts(p).put("auth","publicKey");opts(p).put("privateKeys",List.of("~/.ssh/%h-%r"));check(one(p).privateKey().endsWith(".ssh/ssh.example.com-user"));});
        test("missing bind becomes loopback and requests review",()->{var p=ssh("Local");forward(p).remove("host");var c=convert(p).candidates().getFirst();eq(c.profile().bindHost(),"127.0.0.1");check(c.requiresReview());});
        test("non loopback bind is preserved and requires review",()->{var p=ssh("Dynamic");forward(p).put("host","0.0.0.0");check(convert(p).candidates().getFirst().requiresReview());eq(one(p).bindHost(),"0.0.0.0");});
        test("empty configuration returns zero rows",()->{eq(TabbyImport.convert(obj("profiles",List.of()),"").candidates().size(),0);});
        test("encrypted or unsupported root does not pretend to import",()->{expect(()->TabbyImport.convert(obj("vault","encrypted"),""));});
        test("duplicate groups rejected",()->{var r=root(ssh("Local"));r.put("groups",List.of(obj("id","same"),obj("id","same")));expect(()->TabbyImport.convert(r,""));});
        test("oversized installation rejected",()->{expect(()->TabbyImport.convert(root(ssh("Local")),"a".repeat(121)));});
        test("multiline description is rendered as a single row",()->{var p=ssh("Local");forward(p).put("description","One\nTwo");check(!one(p).name().contains("\n"));});
        test("many profiles are bounded",()->{expect(()->TabbyImport.convert(obj("profiles",Collections.nCopies(1001,ssh("Local"))),""));});
        test("import planning validates total row cap",()->{expect(()->TabbyImport.append(Collections.nCopies(1000,TunnelProfile.example()),List.of(one(ssh("Local")))));});
        test("v2 storage roundtrips installation origin and fingerprint",()->{
            var p=one(ssh("Dynamic")).withInstallation("Test installation");eq(ProfileStore.decode(ProfileStore.encode(List.of(p))),List.of(p));
        });
        test("v1 profiles migrate to Custom without invented provenance",()->{
            var props=SafeFiles.decode(ProfileStore.encode(List.of(one(ssh("Local")))));props.setProperty("format.version","1");
            var p=ProfileStore.decode(SafeFiles.encode(props,"")).getFirst();eq(p.origin(),TunnelProfile.Origin.CUSTOM);eq(p.installation(),"");eq(p.sourceKey(),"");
        });
        test("v1 cannot smuggle dynamic forwarding",()->{var props=SafeFiles.decode(ProfileStore.encode(List.of(one(ssh("Dynamic")))));props.setProperty("format.version","1");expect(()->ProfileStore.decode(SafeFiles.encode(props,"")));});
        test("v2 unknown origins rejected",()->{var props=SafeFiles.decode(ProfileStore.encode(List.of(one(ssh("Local")))));props.setProperty("profile.0.origin","Other");expect(()->ProfileStore.decode(SafeFiles.encode(props,"")));});
        test("duplicate goes to Custom and preserves installation",()->{
            var p=one(ssh("Local")).withInstallation("Office");var copy=p.duplicate();eq(copy.origin(),TunnelProfile.Origin.CUSTOM);eq(copy.sourceKey(),"");eq(copy.installation(),"Office");check(!p.id().equals(copy.id()));
        });
        test("reidentify for backup preserves source metadata",()->{var p=one(ssh("Local"));var copy=p.copyWithNewId();eq(copy.origin(),TunnelProfile.Origin.TABBY);eq(copy.sourceKey(),p.sourceKey());check(!p.id().equals(copy.id()));});
        test("SOCKS command uses -D and no target",()->{
            var p=one(ssh("Dynamic"));var args=OpenSshCommand.arguments(p);check(args.contains("-D"));check(!args.contains("-L")&&!args.contains("-R"));eq(args.get(args.indexOf("-D")+1),"127.0.0.1:8989");
        });
        test("local and remote commands remain unchanged",()->{check(OpenSshCommand.arguments(one(ssh("Local"))).contains("-L"));check(OpenSshCommand.arguments(one(ssh("Remote"))).contains("-R"));});
        test("IPv6 dynamic listener command is bracketed",()->{var p=ssh("Dynamic");forward(p).put("host","::1");check(OpenSshCommand.arguments(one(p)).contains("[::1]:8989"));});
        test("schema total byte cap prevents unreadable future config",()->{
            List<TunnelProfile> ps=new ArrayList<>();var p=TunnelProfile.example();
            for(int i=0;i<1000;i++) ps.add(new TunnelProfile(UUID.randomUUID(),p.name(),p.mode(),p.sshHost(),p.sshPort(),p.username(),p.bindHost(),p.bindPort(),p.targetHost(),p.targetPort(),p.auth(),"",15,15,3,false,5,3,"x".repeat(2000)));
            expect(()->ProfileStore.encode(ps));
        });
        test("encrypted backup retains tabs dynamic mode installation and credentials",()->{
            Path dir=Files.createTempDirectory("tabby-backup-");
            try(VaultStore source=new VaultStore(dir.resolve("source"));VaultStore dest=new VaultStore(dir.resolve("dest"))) {
                char[] master="synthetic-only-master".toCharArray();source.unlock(master);dest.unlock(master);
                var p=one(ssh("Dynamic")).withInstallation("Office");source.put(p,"synthetic-credential".toCharArray());
                Path archive=dir.resolve("backup");BackupService.exportTo(archive,List.of(p),source,master);
                try(var backup=BackupService.read(archive,master)) {
                    var restored=BackupService.append(backup,List.of(),dir.resolve("profiles"),dest).getFirst();
                    eq(restored.origin(),TunnelProfile.Origin.TABBY);eq(restored.mode(),TunnelProfile.Mode.DYNAMIC);
                    eq(restored.installation(),"Office");eq(restored.sourceKey(),p.sourceKey());eq(new String(dest.copy(restored)),"synthetic-credential");
                }
            } finally { try(var paths=Files.walk(dir)){ for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        });
        System.out.println("TABBY CORE RESULT: "+passed+" passed, 0 failed");
    }
    private static TabbyImport.Report convert(Map<String,Object> p) throws IOException { return TabbyImport.convert(root(p),""); }
    private static TunnelProfile one(Map<String,Object> p) throws IOException { return convert(p).candidates().getFirst().profile(); }
    private static Map<String,Object> root(Map<String,Object> p) {return obj("profiles",List.of(p));}
    private static Map<String,Object> ssh(String... modes) {
        var fs=new ArrayList<Map<String,Object>>();for(String m:modes)fs.add(fwd(m));
        return obj("type","ssh","id","ssh:custom:synthetic-1","name","Demo SSH", "options",obj("host","ssh.example.com","port",22,"user","user","auth","password","forwardedPorts",fs));
    }
    private static Map<String,Object> fwd(String type) {return obj("type",type,"host","127.0.0.1","port",8989,"targetAddress","service.example.com","targetPort",8081,"description","Example");}
    @SuppressWarnings("unchecked") private static Map<String,Object> opts(Map<String,Object> p) {return (Map<String,Object>)p.get("options");}
    @SuppressWarnings("unchecked") private static Map<String,Object> forward(Map<String,Object> p) {return ((List<Map<String,Object>>)opts(p).get("forwardedPorts")).getFirst();}
    private static Map<String,Object> obj(Object... kv) {var m=new LinkedHashMap<String,Object>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);return m;}
    private static void test(String name,Check c) throws Exception {c.run();passed++;System.out.println("PASS "+name);}
    private static void check(boolean b){if(!b)throw new AssertionError();}
    private static void eq(Object a,Object b){if(!Objects.equals(a,b))throw new AssertionError("Expected "+b+"; got "+a);}
    private static void expect(Check c) throws Exception {try{c.run();}catch(IOException e){return;}throw new AssertionError("Expected IOException");}
}
