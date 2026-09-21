package it.nexu.forwarding;

import it.nexu.forwarding.config.*;
import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.TunnelEngine;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class StorageSelfTest {
    private static int passed;
    private static void check(boolean condition,String name) { if(!condition)throw new AssertionError(name);passed++;System.out.println("PASS "+name); }
    private interface Checked { void run() throws Exception; }
    private static void rejects(Checked c,String name) throws Exception { boolean rejected=false;try{c.run();}catch(java.io.IOException e){rejected=true;}check(rejected,name); }
    public static void main(String[] args) throws Exception { run(); }
    public static void run() throws Exception {
        passed=0;Path temp=Files.createTempDirectory("nexu-storage-tests-");
        char[] master="test-only-master-password".toCharArray(),other="test-only-backup-password".toCharArray();
        char[] credential="PRIVATE_SSH_PASSWORD_987".toCharArray();
        try {
            Path home=temp.resolve("data");SafeFiles.directory(home);Path file=home.resolve("credentials.npfvault");
            TunnelProfile profile=TunnelProfile.example();
            try(VaultStore vault=new VaultStore(file)) {
                rejects(()->vault.put(profile,credential),"locked vault refuses a write");
                rejects(()->vault.unlock("short".toCharArray()),"new vault rejects a weak master password");
                check(!Files.exists(file),"failed creation does not create a vault");
                vault.unlock(master);vault.put(profile,credential);
                check(vault.contains(profile),"saved credential is available");
                char[] copied=vault.copy(profile);copied[0]='x';
                check(Arrays.equals(vault.copy(profile),credential),"secret copies are defensive");
                byte[] original=Files.readAllBytes(file);
                check(!new String(original,StandardCharsets.ISO_8859_1).contains(new String(credential)),"password is not stored in plaintext");
                vault.put(profile,credential);
                check(!Arrays.equals(original,Files.readAllBytes(file)),"new salt and nonce are used for every write");
                vault.close();check(!vault.contains(profile),"lock clears decrypted credentials");
                rejects(()->vault.unlock(other),"wrong master password is rejected");
                check(!vault.unlocked(),"wrong password leaves vault locked");
                vault.unlock(master);check(Arrays.equals(vault.copy(profile),credential),"credential survives application restart");
                TunnelProfile changed=new TunnelProfile(profile.id(),profile.name(),profile.mode(),"different.invalid",profile.sshPort(),profile.username(),profile.bindHost(),profile.bindPort(),profile.targetHost(),profile.targetPort(),profile.auth(),profile.privateKey(),profile.connectTimeoutSeconds(),profile.keepAliveSeconds(),profile.keepAliveMisses(),profile.reconnect(),profile.reconnectAttempts(),profile.reconnectDelaySeconds(),profile.notes());
                check(!vault.contains(changed),"credential cannot be reused for a changed SSH identity");
                check(!vault.contains(profile.duplicate()),"duplicate profile does not inherit a secret implicitly");
                Path config=home.resolve("profiles.properties");ProfileStore.save(config,List.of(profile));
                check(!Files.readString(config).contains(new String(credential)),"plain profile export excludes secrets");
                Path backup=temp.resolve("export.npfbackup");BackupService.exportTo(backup,List.of(profile),vault,other);
                rejects(()->BackupService.read(backup,master),"wrong backup password is rejected");
                try(BackupService.Backup decoded=BackupService.read(backup,other)) {
                    check(decoded.profiles().equals(List.of(profile)),"encrypted backup preserves profile settings");
                    Path destination=temp.resolve("other-data");SafeFiles.directory(destination);
                    try(VaultStore imported=new VaultStore(destination.resolve("credentials.npfvault"))) {
                        imported.unlock(master);
                        List<TunnelProfile> result=BackupService.append(decoded,List.of(profile),destination.resolve("profiles.properties"),imported);
                        check(result.size()==2&&result.get(0).equals(profile),"restore appends without overwriting existing profiles");
                        check(!result.get(1).id().equals(profile.id()),"restore allocates a new profile ID");
                        check(Arrays.equals(imported.copy(result.get(1)),credential),"restore remaps the matching credential");
                        imported.close();imported.unlock(master);
                        check(Arrays.equals(imported.copy(result.get(1)),credential),"restored password survives another restart");
                    }
                }
                byte[] corrupt=Files.readAllBytes(backup);corrupt[corrupt.length-1]^=1;Files.write(backup,corrupt);
                rejects(()->BackupService.read(backup,other),"tampered backup fails authentication before import");
                rejects(()->CryptoBox.decrypt(Files.readAllBytes(file),master,CryptoBox.BACKUP),"vault and backup envelopes cannot be confused");
                vault.changePassword(other);vault.close();
                rejects(()->vault.unlock(master),"old master password is invalid after rotation");
                vault.unlock(other);check(Arrays.equals(vault.copy(profile),credential),"master rotation preserves credentials");
                vault.forget(profile.id());vault.close();vault.unlock(other);
                check(!vault.contains(profile),"deletion persists after restart");
                byte[] valid=Files.readAllBytes(file);valid[12]^=1;Files.write(file,valid);vault.close();
                rejects(()->vault.unlock(other),"invalid KDF parameters are rejected");
            }
            try(AppLog log=new AppLog(home)) {
                log.event(new TunnelEngine.Event(profile.id(),TunnelEngine.State.ERROR,new String(credential),Instant.now()));
                log.event(new TunnelEngine.Event(profile.id(),TunnelEngine.State.ERROR,
                    "Porta locale già in uso: 127.0.0.1:8687. Un altro processo (per esempio Tabby o MobaXterm) sta probabilmente già ascoltando su questa porta.",
                    Instant.now()));
            }
            String logText=Files.readString(home.resolve("logs/nexu-0.log"));
            check(logText.contains("state=ERROR")&&logText.contains("application-started"),"local diagnostic log records lifecycle and state");
            check(logText.contains("category=LOCAL_BIND_IN_USE")&&logText.contains("127.0.0.1:8687"),"persistent log records sanitized bind conflict diagnostics");
            check(!logText.contains(new String(credential)),"diagnostic log does not persist arbitrary error text or passwords");
            Path exportDir=temp.resolve("exports");Files.createDirectories(exportDir);
            if(Files.getFileStore(exportDir).supportsFileAttributeView("posix")) {
                var perms=Files.getPosixFilePermissions(exportDir);SafeFiles.writeBytes(exportDir.resolve("file.bin"),new byte[]{1});
                check(Files.getPosixFilePermissions(exportDir).equals(perms),"export does not change permissions on the user's directory");
                check(Files.getPosixFilePermissions(exportDir.resolve("file.bin")).equals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")),"exported sensitive file is owner-only on POSIX");
            }
            System.out.println("STORAGE RESULT: "+passed+" passed, 0 failed");
        } finally {
            Arrays.fill(master,'\0');Arrays.fill(other,'\0');Arrays.fill(credential,'\0');
            try(var paths=Files.walk(temp)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
