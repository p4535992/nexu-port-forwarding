package it.nexu.forwarding;

import it.nexu.forwarding.config.*;
import it.nexu.forwarding.model.*;
import it.nexu.forwarding.ssh.*;
import java.io.IOException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Dependency-free tests, also invoked by JUnit in the Maven build. No external network. */
public final class CoreSelfTest {
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private static int passed, failed;
    private static final String FP1 = "SHA256:" + "A".repeat(43), FP2 = "SHA256:" + "B".repeat(43);
    public static void main(String[] args) throws Exception { runAll(); }
    public static void runAll() throws Exception {
        passed = failed = 0;
        Path temp = Files.createTempDirectory("nexu-tests-");
        try {
            test("example reproduces the user's remote forwarding", () -> {
                TunnelProfile p = TunnelProfile.example(); eq(p.bindPort(),8989); eq(p.destination(),"maven.example.com:8081"); eq(p.mode(),TunnelProfile.Mode.REMOTE);
            });
            test("custom SSH port accepted", () -> eq(change("sshPort",2222).sshPort(),2222));
            test("port 65535 accepted", () -> eq(change("bindPort",65535).bindPort(),65535));
            test("port zero rejected", () -> expect(IllegalArgumentException.class, () -> change("bindPort",0)));
            test("negative port rejected", () -> expect(IllegalArgumentException.class, () -> change("sshPort",-1)));
            test("port above 65535 rejected", () -> expect(IllegalArgumentException.class, () -> change("targetPort",65536)));
            test("URL rather than hostname rejected", () -> expect(IllegalArgumentException.class, () -> change("sshHost","https://host")));
            test("shell metacharacters in hostname rejected", () -> expect(IllegalArgumentException.class, () -> change("sshHost","host; whoami")));
            test("leading option in hostname rejected", () -> expect(IllegalArgumentException.class, () -> change("sshHost","-oProxyCommand=bad")));
            test("username control character rejected", () -> expect(IllegalArgumentException.class, () -> change("username","u\ncommand")));
            test("empty name rejected", () -> expect(IllegalArgumentException.class, () -> change("name","")));
            test("zero keepalive interval rejected", () -> expect(IllegalArgumentException.class, () -> change("keepAliveSeconds",0)));
            test("retry budget bounded", () -> expect(IllegalArgumentException.class, () -> change("reconnectAttempts",21)));
            test("private key path required for key authentication", () -> expect(IllegalArgumentException.class, () -> change("auth",TunnelProfile.Auth.PRIVATE_KEY)));
            test("bracketed IPv6 normalized", () -> eq(change("sshHost","[::1]").sshHost(),"::1"));
            test("IPv6 command address bracketed", () -> check(OpenSshCommand.powershell(change("targetHost","::1")).contains("[::1]:8081")));
            test("wildcard bind normalized", () -> eq(change("bindHost","*").bindHost(),"0.0.0.0"));
            test("loopback detection", () -> { check(TunnelProfile.example().isLoopbackBind()); check(change("bindHost","::1").isLoopbackBind()); check(!change("bindHost","0.0.0.0").isLoopbackBind()); });
            test("duplicate has independent UUID", () -> { TunnelProfile p=TunnelProfile.example(); check(!p.id().equals(p.duplicate().id())); });
            test("duplicate long name remains valid", () -> check(change("name","n".repeat(120)).duplicate().name().length()<=120));
            test("SSH command has no shell or TTY", () -> { List<String> a=OpenSshCommand.arguments(TunnelProfile.example()); check(a.contains("-N")); check(a.contains("-T")); check(!a.contains("-t")); });
            test("remote command export correct", () -> { List<String> a=OpenSshCommand.arguments(TunnelProfile.example()); check(a.contains("-R")); check(a.contains("127.0.0.1:8989:maven.example.com:8081")); });
            test("local command export correct", () -> { List<String> a=OpenSshCommand.arguments(change("mode",TunnelProfile.Mode.LOCAL)); check(a.contains("-L")); check(!a.contains("-R")); });
            test("PowerShell single quotes escaped", () -> check(OpenSshCommand.powershell(change("username","O'Brien")).contains("'O''Brien'")));
            test("CMD export has no PowerShell call operator or single-quoted options", () -> { String s=OpenSshCommand.cmd(TunnelProfile.example()); check(s.startsWith("ssh.exe \"-N\" \"-T\"")); check(!s.startsWith("&")); check(!s.contains("'-N'")); });
            test("POSIX single quotes escaped", () -> check(OpenSshCommand.posix(change("username","O'Brien")).contains("'O'\"'\"'Brien'")));
            test("forwarding summary identifies remote listener side", () -> check(TunnelProfile.example().forwardingSummary().equals("R · SSH[127.0.0.1:8989] → PC → maven.example.com:8081")));
            test("forwarding summary identifies local listener side", () -> check(change("mode",TunnelProfile.Mode.LOCAL).forwardingSummary().startsWith("L · PC[127.0.0.1:8989] → SSH → ")));
            test("inline rename preserves profile identity", () -> { TunnelProfile p=TunnelProfile.example(), renamed=p.withName("Nuovo nome"); eq(renamed.id(),p.id()); eq(renamed.name(),"Nuovo nome"); });
            test("config round trip", () -> { Path f=temp.resolve("roundtrip.properties"); List<TunnelProfile> p=List.of(TunnelProfile.example(),change("mode",TunnelProfile.Mode.LOCAL)); ProfileStore.save(f,p); eq(ProfileStore.load(f),p); });
            test("empty config round trip", () -> { Path f=temp.resolve("empty.properties"); ProfileStore.save(f,List.of()); check(ProfileStore.load(f).isEmpty()); });
            test("missing config loads as empty", () -> check(ProfileStore.load(temp.resolve("missing.properties")).isEmpty()));
            test("unknown config version rejected", () -> { Path f=temp.resolve("badversion.properties"); Files.writeString(f,"format.version=999\nprofile.count=0\n"); expect(IOException.class,()->ProfileStore.load(f)); });
            test("oversized profile count rejected", () -> { Path f=temp.resolve("badcount.properties"); Files.writeString(f,"format.version=1\nprofile.count=1001\n"); expect(IOException.class,()->ProfileStore.load(f)); });
            test("duplicate UUID rejected on save", () -> { TunnelProfile p=TunnelProfile.example(); expect(IOException.class,()->ProfileStore.save(temp.resolve("duplicate.properties"),List.of(p,p))); });
            test("tampered port rejected on import", () -> {
                Path f=temp.resolve("tampered.properties"); ProfileStore.save(f,List.of(TunnelProfile.example()));
                Properties v=SafeFiles.read(f); v.setProperty("profile.0.bindPort","0"); SafeFiles.write(f,v,""); expect(IOException.class,()->ProfileStore.load(f));
            });
            test("invalid boolean rejected on import", () -> {
                Path f=temp.resolve("bool.properties"); ProfileStore.save(f,List.of(TunnelProfile.example()));
                Properties v=SafeFiles.read(f); v.setProperty("profile.0.reconnect","maybe"); SafeFiles.write(f,v,""); expect(IOException.class,()->ProfileStore.load(f));
            });
            test("saved schema has no secret fields", () -> {
                Path f=temp.resolve("without-secrets.properties"); ProfileStore.save(f,List.of(TunnelProfile.example()));
                for (String k:SafeFiles.read(f).stringPropertyNames()) check(!k.toLowerCase().contains("password")&&!k.toLowerCase().contains("passphrase"));
            });
            test("secrets copied on write and read", () -> {
                try(SecretStore s=new SecretStore()) { UUID id=UUID.randomUUID(); char[] raw="abc".toCharArray(); s.put(id,raw); raw[0]='x'; char[] out=s.copy(id); eq(new String(out),"abc"); out[0]='y'; eq(new String(s.copy(id)),"abc"); }
            });
            test("forget secret", () -> { try(SecretStore s=new SecretStore()) { UUID id=UUID.randomUUID(); s.put(id,"abc".toCharArray()); s.forget(id); check(!s.contains(id)); eq(s.copy(id).length,0); } });
            test("close clears secret store", () -> { SecretStore s=new SecretStore(); UUID id=UUID.randomUUID(); s.put(id,"abc".toCharArray()); s.close(); check(!s.contains(id)); });
            test("unknown host is not trusted", () -> eq(new HostKeyStore(temp.resolve("h1")).check("server:22",FP1),HostKeyStore.Match.UNKNOWN));
            test("persistent fingerprint round trip", () -> { Path f=temp.resolve("h2"); new HostKeyStore(f).accept("server:22",FP1,true); eq(new HostKeyStore(f).check("server:22",FP1),HostKeyStore.Match.MATCH); });
            test("host pin scoped to SSH port", () -> { HostKeyStore s=new HostKeyStore(temp.resolve("h3")); s.accept("server:22",FP1,true); eq(s.check("server:2222",FP1),HostKeyStore.Match.UNKNOWN); });
            test("changed host fingerprint detected", () -> { HostKeyStore s=new HostKeyStore(temp.resolve("h4")); s.accept("server:22",FP1,true); eq(s.check("server:22",FP2),HostKeyStore.Match.CHANGED); });
            test("changed fingerprint cannot silently replace pin", () -> { HostKeyStore s=new HostKeyStore(temp.resolve("h5")); s.accept("server:22",FP1,true); expect(IOException.class,()->s.accept("server:22",FP2,true)); eq(s.expected("server:22"),FP1); });
            test("temporary trust not written to disk", () -> { Path f=temp.resolve("h6"); HostKeyStore s=new HostKeyStore(f); s.accept("server:22",FP1,false); eq(s.check("server:22",FP1),HostKeyStore.Match.MATCH); eq(new HostKeyStore(f).check("server:22",FP1),HostKeyStore.Match.UNKNOWN); });
            test("explicit forget removes persistent pin", () -> { Path f=temp.resolve("h7"); HostKeyStore s=new HostKeyStore(f); s.accept("server:22",FP1,true); s.forget("server:22"); eq(new HostKeyStore(f).check("server:22",FP1),HostKeyStore.Match.UNKNOWN); });
            test("invalid fingerprint rejected", () -> expect(IOException.class,()->new HostKeyStore(temp.resolve("h8")).accept("server:22","MD5:bad",true)));
            test("configuration permissions restricted on POSIX", () -> {
                if(Files.getFileStore(temp).supportsFileAttributeView("posix")) { Path f=temp.resolve("permissions"); SafeFiles.write(f,new Properties(),""); eq(Files.getPosixFilePermissions(f),PosixFilePermissions.fromString("rw-------")); }
            });
            test("export does not chmod parent directory", () -> {
                if(Files.getFileStore(temp).supportsFileAttributeView("posix")) { Path dir=Files.createDirectory(temp.resolve("export")); var perms=PosixFilePermissions.fromString("rwxr-xr-x"); Files.setPosixFilePermissions(dir,perms); SafeFiles.write(dir.resolve("p"),new Properties(),""); eq(Files.getPosixFilePermissions(dir),perms); }
            });
            test("single instance lock prevents second writer", () -> {
                Path dir=temp.resolve("locked"); try(AppLock first=new AppLock(dir)) { expect(OverlappingFileLockException.class,()->new AppLock(dir)); }
                try(AppLock next=new AppLock(dir)) { check(true); }
            });
            test("cancellation callback is idempotent", () -> { Cancellation c=new Cancellation(); AtomicInteger n=new AtomicInteger(); c.onCancel(n::incrementAndGet); c.cancel(); c.cancel(); eq(n.get(),1); });
            test("late cancellation registration still notified", () -> { Cancellation c=new Cancellation(); c.cancel(); AtomicInteger n=new AtomicInteger(); c.onCancel(n::incrementAndGet); eq(n.get(),1); });
            test("no automatic connections on engine construction", () -> {
                AtomicInteger n=new AtomicInteger(); try(TunnelEngine e=new TunnelEngine((p,s,c)->{n.incrementAndGet();return new FakeConnection();},v->{})) { eq(n.get(),0); }
            });
            test("idempotent start and independent stop cleanup", () -> {
                AtomicInteger opened=new AtomicInteger(); FakeConnection conn=new FakeConnection(); var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=TunnelProfile.example();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->{opened.incrementAndGet();return conn;},events::add)) {
                    check(e.start(p,"secret".toCharArray())); check(!e.start(p,"second".toCharArray())); await(()->has(events,TunnelEngine.State.ACTIVE));
                    eq(opened.get(),1); e.stop(p.id()); e.stop(p.id()); check(e.awaitStopped(2000)); eq(conn.closes.get(),1); check(has(events,TunnelEngine.State.STOPPED));
                }
            });
            test("stop cancels a connection in progress", () -> {
                var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=TunnelProfile.example();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->{while(true){c.check();Thread.sleep(20);}},events::add)) {
                    e.start(p,new char[0]); e.stop(p.id()); check(e.awaitStopped(2000)); check(!has(events,TunnelEngine.State.ACTIVE));
                }
            });
            test("authentication-like errors are not retried", () -> {
                AtomicInteger n=new AtomicInteger(); var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=change("reconnect",true);
                try(TunnelEngine e=new TunnelEngine((x,s,c)->{n.incrementAndGet();throw new TunnelBackend.Failure("auth failed",false);},events::add)) {
                    e.start(p,new char[0]); check(e.awaitStopped(3000)); eq(n.get(),1); check(has(events,TunnelEngine.State.ERROR));
                }
            });
            test("retryable failures obey the total budget", () -> {
                AtomicInteger n=new AtomicInteger(); var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=retryProfile();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->{n.incrementAndGet();throw new TunnelBackend.Failure("network",true);},events::add)) {
                    e.start(p,new char[0]); check(e.awaitStopped(4000)); eq(n.get(),2); check(has(events,TunnelEngine.State.RECONNECTING)); check(has(events,TunnelEngine.State.ERROR));
                }
            });
            test("stop cancels reconnect delay", () -> {
                AtomicInteger n=new AtomicInteger(); var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=retryProfile();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->{n.incrementAndGet();throw new TunnelBackend.Failure("network",true);},events::add)) {
                    e.start(p,new char[0]); await(()->has(events,TunnelEngine.State.RECONNECTING)); e.stop(p.id()); check(e.awaitStopped(2000)); eq(n.get(),1);
                }
            });
            test("closed backend connection is never marked active", () -> {
                FakeConnection conn=new FakeConnection(); conn.open.set(false); var events=new CopyOnWriteArrayList<TunnelEngine.Event>();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->conn,events::add)) { e.start(TunnelProfile.example(),new char[0]); check(e.awaitStopped(2000)); check(!has(events,TunnelEngine.State.ACTIVE)); eq(conn.closes.get(),1); }
            });
            test("separate rows have separate connection lifecycles", () -> {
                Map<UUID,FakeConnection> connections=new ConcurrentHashMap<>(); TunnelProfile a=TunnelProfile.example(), b=a.duplicate();
                try(TunnelEngine e=new TunnelEngine((p,s,c)->{FakeConnection conn=new FakeConnection();connections.put(p.id(),conn);return conn;},v->{})) {
                    e.start(a,new char[0]); e.start(b,new char[0]); await(()->connections.size()==2); e.stop(a.id()); await(()->!e.isRunning(a.id()));
                    check(e.isRunning(b.id())); check(connections.get(b.id()).open.get()); e.stopAll(); check(e.awaitStopped(2000));
                }
            });
            test("active transport loss triggers a bounded reconnection", () -> {
                AtomicInteger opened=new AtomicInteger(); FakeConnection first=new FakeConnection(), second=new FakeConnection();
                var events=new CopyOnWriteArrayList<TunnelEngine.Event>(); TunnelProfile p=retryProfile();
                try(TunnelEngine e=new TunnelEngine((x,s,c)->opened.incrementAndGet()==1?first:second,events::add)) {
                    e.start(p,new char[0]); await(()->has(events,TunnelEngine.State.ACTIVE)); first.open.set(false);
                    await(()->opened.get()==2); check(has(events,TunnelEngine.State.RECONNECTING)); e.stopAll(); check(e.awaitStopped(2000));
                    eq(first.closes.get(),1); eq(second.closes.get(),1);
                }
            });
            test("engine owns a defensive secret copy", () -> {
                CountDownLatch gate=new CountDownLatch(1); AtomicReference<String> got=new AtomicReference<>(); char[] secret="keep".toCharArray();
                try(TunnelEngine e=new TunnelEngine((p,s,c)->{gate.await();got.set(new String(s));return new FakeConnection();},v->{})) {
                    e.start(TunnelProfile.example(),secret); Arrays.fill(secret,'x'); gate.countDown(); await(()->got.get()!=null); eq(got.get(),"keep"); e.stopAll(); check(e.awaitStopped(2000));
                }
            });
            test("event error text redacts secret", () -> {
                var events=new CopyOnWriteArrayList<TunnelEngine.Event>();
                try(TunnelEngine e=new TunnelEngine((p,s,c)->{throw new TunnelBackend.Failure("error supersecret",false);},events::add)) {
                    e.start(TunnelProfile.example(),"supersecret".toCharArray()); check(e.awaitStopped(2000)); check(events.stream().noneMatch(v->v.detail().contains("supersecret")));
                }
            });
            test("closed engine refuses new starts", () -> { TunnelEngine e=new TunnelEngine((p,s,c)->new FakeConnection(),v->{}); e.close(); check(!e.start(TunnelProfile.example(),new char[0])); });
            System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
            if (failed != 0) throw new AssertionError("Self-test failures: " + failed);
        } finally {
            try(var files=Files.walk(temp)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    private static final class FakeConnection implements TunnelBackend.Connection {
        final AtomicBoolean open=new AtomicBoolean(true); final AtomicInteger closes=new AtomicInteger();
        public boolean isOpen(){return open.get();} public String closedReason(){return "disconnected";}
        public void close(){open.set(false);closes.incrementAndGet();}
    }
    private static boolean has(List<TunnelEngine.Event> events,TunnelEngine.State state){return events.stream().anyMatch(e->e.state()==state);}
    private static void await(BooleanSupplier condition) throws Exception {
        long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!condition.getAsBoolean()&&System.nanoTime()<limit)Thread.sleep(10);
        check(condition.getAsBoolean());
    }
    private static void test(String name,Checked body) {
        try{body.run();passed++;System.out.println("PASS "+name);}
        catch(Throwable e){failed++;System.out.println("FAIL "+name+" -- "+e);e.printStackTrace(System.out);}
    }
    private static void check(boolean ok){if(!ok)throw new AssertionError("Condition failed");}
    private static void eq(Object actual,Object expected){if(!Objects.equals(actual,expected))throw new AssertionError("Expected "+expected+", got "+actual);}
    private static void expect(Class<? extends Throwable> type,Checked body)throws Exception{
        try{body.run();}catch(Throwable e){if(type.isInstance(e))return;throw new AssertionError("Expected "+type+", got "+e,e);}throw new AssertionError("Expected "+type);
    }
    private static TunnelProfile retryProfile(){return create(Map.of("reconnect",true,"reconnectAttempts",1,"reconnectDelaySeconds",1));}
    private static TunnelProfile change(String key,Object value){return create(Map.of(key,value));}
    private static TunnelProfile create(Map<String,Object> v){
        TunnelProfile p=TunnelProfile.example();
        return new TunnelProfile(p.id(),(String)v.getOrDefault("name",p.name()),(TunnelProfile.Mode)v.getOrDefault("mode",p.mode()),
            (String)v.getOrDefault("sshHost",p.sshHost()),(int)v.getOrDefault("sshPort",p.sshPort()),(String)v.getOrDefault("username",p.username()),
            (String)v.getOrDefault("bindHost",p.bindHost()),(int)v.getOrDefault("bindPort",p.bindPort()),(String)v.getOrDefault("targetHost",p.targetHost()),
            (int)v.getOrDefault("targetPort",p.targetPort()),(TunnelProfile.Auth)v.getOrDefault("auth",p.auth()),(String)v.getOrDefault("privateKey",p.privateKey()),
            (int)v.getOrDefault("connectTimeoutSeconds",p.connectTimeoutSeconds()),(int)v.getOrDefault("keepAliveSeconds",p.keepAliveSeconds()),
            (int)v.getOrDefault("keepAliveMisses",p.keepAliveMisses()),(boolean)v.getOrDefault("reconnect",p.reconnect()),
            (int)v.getOrDefault("reconnectAttempts",p.reconnectAttempts()),(int)v.getOrDefault("reconnectDelaySeconds",p.reconnectDelaySeconds()),p.notes());
    }
}
