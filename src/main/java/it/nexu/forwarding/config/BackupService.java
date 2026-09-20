package it.nexu.forwarding.config;

import it.nexu.forwarding.model.TunnelProfile;
import java.io.*;
import java.nio.file.Path;
import java.util.*;

/** Encrypted, bounded binary backup. No ZIP paths, object deserialization or private-key file contents. */
public final class BackupService {
    private BackupService() { }
    public record Backup(List<TunnelProfile> profiles,Map<UUID,VaultStore.Entry> secrets) implements AutoCloseable {
        @Override public void close() { VaultStore.wipe(secrets); }
    }
    public static void exportTo(Path file,List<TunnelProfile> profiles,VaultStore vault,char[] password) throws IOException {
        if(!vault.unlocked()) throw new IOException("Sbloccare l'archivio prima del backup.");
        Map<UUID,VaultStore.Entry> entries=vault.snapshot();
        Set<UUID> ids=new HashSet<>(); profiles.forEach(p->ids.add(p.id()));
        entries.entrySet().removeIf(e->{ if(ids.contains(e.getKey())) return false; e.getValue().close(); return true; });
        byte[] credentials=null,plain=null;
        try {
            byte[] config=ProfileStore.encode(profiles); credentials=VaultStore.encode(entries);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(bytes)) { out.writeInt(1); out.writeInt(config.length); out.write(config); out.writeInt(credentials.length); out.write(credentials); }
            plain=bytes.toByteArray(); SafeFiles.writeBytes(file,CryptoBox.encrypt(plain,password,CryptoBox.BACKUP));
        } finally { VaultStore.wipe(entries); if(credentials!=null) Arrays.fill(credentials,(byte)0); if(plain!=null) Arrays.fill(plain,(byte)0); }
    }
    public static Backup read(Path file,char[] password) throws IOException {
        byte[] plain=CryptoBox.decrypt(SafeFiles.readBytes(file,CryptoBox.LIMIT+100),password,CryptoBox.BACKUP);
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(plain))) {
            if(in.readInt()!=1) throw new IOException("Versione backup non supportata.");
            List<TunnelProfile> profiles=ProfileStore.decode(section(in,2_000_000));
            byte[] secretBytes=section(in,CryptoBox.LIMIT);
            try {
                if(in.read()!=-1) throw new IOException("Dati inattesi nel backup.");
                Map<UUID,VaultStore.Entry> secrets=VaultStore.decode(secretBytes);
                Set<UUID> ids=new HashSet<>(); profiles.forEach(p->ids.add(p.id()));
                if(!ids.containsAll(secrets.keySet())) { VaultStore.wipe(secrets); throw new IOException("Credenziali senza profilo."); }
                return new Backup(profiles,secrets);
            } finally { Arrays.fill(secretBytes,(byte)0); }
        } finally { Arrays.fill(plain,(byte)0); }
    }
    private static byte[] section(DataInputStream in,int limit) throws IOException {
        int size=in.readInt(); if(size<0||size>limit||size>in.available()) throw new IOException("Sezione backup non valida.");
        return in.readNBytes(size);
    }
    /** Append only. Existing rows and secrets are never overwritten. Vault commits before profiles. */
    public static List<TunnelProfile> append(Backup backup,List<TunnelProfile> existing,Path profilesFile,VaultStore vault) throws IOException {
        if(!vault.unlocked()) throw new IOException("Sbloccare l'archivio destinazione.");
        List<TunnelProfile> next=new ArrayList<>(existing); Map<UUID,VaultStore.Entry> added=new LinkedHashMap<>();
        // Always allocate fresh UUIDs, including when the local vault contains deleted/orphaned entries.
        for(TunnelProfile p:backup.profiles()) {
            TunnelProfile imported=p.duplicate(); next.add(imported);
            VaultStore.Entry e=backup.secrets().get(p.id()); if(e!=null) added.put(imported.id(),e.copy());
        }
        byte[] encoded=ProfileStore.encode(next); // Validate ALL profiles before any local write.
        try {
            vault.merge(added);
            // If the second write fails: old profiles are untouched, imported secrets are encrypted orphans.
            // They cannot be matched by old profiles, and a later import uses fresh UUIDs.
            SafeFiles.writeBytes(profilesFile,encoded); return List.copyOf(next);
        } finally { VaultStore.wipe(added); }
    }
}
