package it.nexu.forwarding;

import it.nexu.forwarding.config.HostKeyStore;
import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Launcher {
    private Launcher() { }
    public static void main(String[] args) {
        List<String> values=Arrays.asList(args);
        if(values.contains("--version")) { System.out.println("Nexu Port Forwarding 1.2.0"); return; }
        if(values.contains("--ssh-runtime-smoke-test")) { sshRuntimeSmokeTest(); return; }
        if(values.contains("--smoke-test")) System.setProperty("nexu.smokeTest","true");
        javafx.application.Application.launch(NexuApplication.class,args);
    }

    private static void sshRuntimeSmokeTest() {
        Path temp=null;
        try {
            // Reflectively used by SSH/security dependencies: verify the jpackage runtime really contains java.rmi.
            Class.forName("java.rmi.ServerException");
            temp=Files.createTempDirectory("nexu-ssh-runtime-");
            int closedPort;
            try(ServerSocket socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) {
                closedPort=socket.getLocalPort();
            }
            TunnelProfile p=new TunnelProfile(UUID.randomUUID(),"SSH runtime smoke",TunnelProfile.Mode.REMOTE,
                "127.0.0.1",closedPort,"smoke","127.0.0.1",65000,"127.0.0.1",1,
                TunnelProfile.Auth.PASSWORD,"",3,1,1,false,1,1,"Runtime package smoke test.");
            MinaTunnelBackend backend=new MinaTunnelBackend(new HostKeyStore(temp.resolve("pins.properties")),
                (profile,fingerprint,cancellation)->HostTrustPrompt.Decision.REJECT);
            try {
                try(TunnelBackend.Connection ignored=backend.open(p,"smoke".toCharArray(),new Cancellation())) {
                    throw new IllegalStateException("Unexpected SSH connection success during runtime smoke test.");
                }
            } catch(Throwable failure) {
                if (containsRuntimeLinkageFailure(failure)) throw new IllegalStateException("Packaged SSH runtime is incomplete.",failure);
                // The loopback port was deliberately closed. A normal connection failure proves that the SSH runtime initialized.
            }
            String home=System.getenv("NEXU_PF_HOME");
            if(home!=null&&!home.isBlank()) {
                Path dir=Path.of(home).toAbsolutePath().normalize();
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("ssh-runtime-ready"),"SSH_RUNTIME_READY 1.2.0\n",StandardCharsets.UTF_8);
            }
            System.out.println("SSH_RUNTIME_READY 1.2.0");
        } catch(Throwable error) {
            error.printStackTrace();
            System.exit(2);
        } finally {
            if(temp!=null) try(var walk=Files.walk(temp)) {
                for(Path p:walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch(Exception ignored) { }
        }
    }

    private static boolean containsRuntimeLinkageFailure(Throwable error) {
        for(Throwable t=error;t!=null;t=t.getCause())
            if(t instanceof ClassNotFoundException || t instanceof NoClassDefFoundError || t instanceof LinkageError
                || t instanceof ServiceConfigurationError) return true;
        return false;
    }
}
