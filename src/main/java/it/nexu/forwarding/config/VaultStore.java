package it.nexu.forwarding.config;

import it.nexu.forwarding.model.TunnelProfile;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local vault. Secrets are bound to SSH identity, not just to a row UUID. */
public final class VaultStore implements AutoCloseable {
    public static final class Entry implements AutoCloseable {
        private final String identity; private final char[] secret;
        Entry(String identity, char[] secret) { this.identity=identity; this.secret=secret.clone(); }
        Entry copy() { return new Entry(identity,secret); }
        @Override public void close() { Arrays.fill(secret,'\0'); }
    }
    private final Path file;
    private Map<UUID,Entry> entries = new LinkedHashMap<>();
    private char[] master;
    public VaultStore(Path file) { this.file=file; }
    public synchronized boolean exists() { return Files.exists(file,LinkOption.NOFOLLOW_LINKS); }
    public synchronized boolean unlocked() { return master != null; }
    public synchronized void unlock(char[] password) throws IOException {
        if (unlocked()) throw new IOException("Archivio già sbloccato.");
        Map<UUID,Entry> next;
        if (exists()) {
            byte[] plain = CryptoBox.decrypt(SafeFiles.readBytes(file,CryptoBox.LIMIT+100),password,CryptoBox.VAULT);
            try { next=decode(plain); } finally { Arrays.fill(plain,(byte)0); }
        } else {
            next=new LinkedHashMap<>();
            byte[] plain=encode(next);
            try { SafeFiles.writeBytes(file,CryptoBox.encrypt(plain,password,CryptoBox.VAULT)); }
            finally { Arrays.fill(plain,(byte)0); }
        }
        entries=next; master=password.clone();
    }
    public static String identity(TunnelProfile p) {
        // Length framing avoids collisions involving delimiters in account/key names.
        StringBuilder b=new StringBuilder();
        for (String s: List.of(p.hostKeyId(),p.username(),p.auth().name(),p.privateKey())) b.append(s.length()).append(':').append(s);
        return b.toString();
    }
    public synchronized boolean contains(TunnelProfile p) {
        Entry e=entries.get(p.id()); return e!=null && e.identity.equals(identity(p));
    }
    public synchronized char[] copy(TunnelProfile p) {
        return contains(p) ? entries.get(p.id()).secret.clone() : new char[0];
    }
    public synchronized void put(TunnelProfile p,char[] secret) throws IOException {
        requireOpen(); Map<UUID,Entry> next=snapshot();
        Entry old=next.put(p.id(),new Entry(identity(p),secret)); if(old!=null) old.close();
        commit(next);
    }
    public synchronized void forget(UUID id) throws IOException {
        requireOpen(); Map<UUID,Entry> next=snapshot(); Entry old=next.remove(id); if(old!=null) old.close(); commit(next);
    }
    public synchronized void merge(Map<UUID,Entry> added) throws IOException {
        requireOpen(); Map<UUID,Entry> next=snapshot();
        for (UUID id:added.keySet()) if (next.containsKey(id)) { wipe(next); throw new IOException("ID credenziale già presente."); }
        added.forEach((id,e)->next.put(id,e.copy())); commit(next);
    }
    public synchronized void changePassword(char[] password) throws IOException {
        requireOpen(); byte[] plain=encode(entries);
        try {
            SafeFiles.writeBytes(file,CryptoBox.encrypt(plain,password,CryptoBox.VAULT));
            Arrays.fill(master,'\0'); master=password.clone();
        } finally { Arrays.fill(plain,(byte)0); }
    }
    public synchronized Map<UUID,Entry> snapshot() { Map<UUID,Entry> out=new LinkedHashMap<>(); entries.forEach((id,e)->out.put(id,e.copy())); return out; }
    private void requireOpen() throws IOException { if(!unlocked()) throw new IOException("Sbloccare prima l'archivio password."); }
    private void commit(Map<UUID,Entry> next) throws IOException {
        byte[] plain=null; boolean success=false;
        try { plain=encode(next); SafeFiles.writeBytes(file,CryptoBox.encrypt(plain,master,CryptoBox.VAULT)); success=true; }
        finally { if(plain!=null) Arrays.fill(plain,(byte)0); if(!success) wipe(next); }
        wipe(entries); entries=next;
    }
    static byte[] encode(Map<UUID,Entry> values) throws IOException {
        if(values.size()>1000) throw new IOException("Massimo 1000 credenziali.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream d=new DataOutputStream(bytes)) {
            d.writeInt(1); d.writeInt(values.size());
            for(var v:values.entrySet()) {
                d.writeLong(v.getKey().getMostSignificantBits()); d.writeLong(v.getKey().getLeastSignificantBits());
                d.writeUTF(v.getValue().identity); char[] s=v.getValue().secret;
                if(s.length>8192) throw new IOException("Credenziale troppo lunga.");
                d.writeInt(s.length); for(char c:s) d.writeChar(c);
            }
        }
        return bytes.toByteArray();
    }
    static Map<UUID,Entry> decode(byte[] bytes) throws IOException {
        Map<UUID,Entry> out=new LinkedHashMap<>();
        try(DataInputStream d=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(d.readInt()!=1) throw new IOException("Versione credenziali non supportata.");
            int count=d.readInt(); if(count<0||count>1000) throw new IOException("Numero credenziali non valido.");
            for(int i=0;i<count;i++) {
                UUID id=new UUID(d.readLong(),d.readLong()); String identity=d.readUTF(); int n=d.readInt();
                if(out.containsKey(id)||n<0||n>8192) throw new IOException("Credenziale non valida.");
                char[] secret=new char[n];
                try { for(int j=0;j<n;j++) secret[j]=d.readChar(); out.put(id,new Entry(identity,secret)); }
                finally { Arrays.fill(secret,'\0'); }
            }
            if(d.read()!=-1) throw new IOException("Dati inattesi nell'archivio."); return out;
        } catch(IOException|RuntimeException e) { wipe(out); throw new IOException("Archivio credenziali non valido.",e); }
    }
    public static void wipe(Map<UUID,Entry> values) { values.values().forEach(Entry::close); values.clear(); }
    @Override public synchronized void close() { wipe(entries); if(master!=null) Arrays.fill(master,'\0'); master=null; }
}
