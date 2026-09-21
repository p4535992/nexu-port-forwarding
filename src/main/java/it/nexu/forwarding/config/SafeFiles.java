package it.nexu.forwarding.config;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

public final class SafeFiles {
    private SafeFiles() { }
    public static Path appDirectory() {
        try { return StorageLocations.current().dataDirectory(); }
        catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    public static void directory(Path dir) throws IOException {
        Files.createDirectories(dir);
        if(Files.isSymbolicLink(dir)) throw new IOException("La cartella dati non può essere un link simbolico.");
        restrict(dir,true);
    }
    public static void restrict(Path file,boolean directory) throws IOException {
        if(Files.getFileStore(file).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(file,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
        else {
            AclFileAttributeView acl=Files.getFileAttributeView(file,AclFileAttributeView.class);
            if(acl!=null) {
                AclEntry.Builder entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
                if(directory) entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
                acl.setAcl(List.of(entry.build()));
            }
        }
    }
    public static byte[] readBytes(Path file,int limit) throws IOException {
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("File locale non valido.");
        // Read cap also protects against a file growing after the size check.
        try(InputStream in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] data=in.readNBytes(limit+1); if(data.length>limit) throw new IOException("File troppo grande."); return data;
        }
    }
    public static Properties read(Path file) throws IOException {
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) return new Properties();
        return decode(readBytes(file,2_000_000));
    }
    public static Properties decode(byte[] bytes) throws IOException {
        if(bytes.length>2_000_000) throw new IOException("Configurazione troppo grande.");
        Properties p=new Properties();
        try(Reader r=new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }
    public static byte[] encode(Properties p,String description) throws IOException {
        StringWriter writer=new StringWriter(); p.store(writer,description); return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static void write(Path file,Properties p,String description) throws IOException { writeBytes(file,encode(p,description)); }
    public static void writeBytes(Path file,byte[] bytes) throws IOException {
        Path parent=file.toAbsolutePath().getParent(); Files.createDirectories(parent);
        if(Files.isSymbolicLink(file)) throw new IOException("Non sovrascrivo un link simbolico.");
        // Only restrict the temporary FILE, never an arbitrary export folder such as Documents.
        Path tmp=Files.createTempFile(parent,".nexu-",".tmp");
        try {
            restrict(tmp,false); Files.write(tmp,bytes);
            try(FileChannel ch=FileChannel.open(tmp,StandardOpenOption.WRITE)) { ch.force(true); }
            try { Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
