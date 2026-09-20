package it.nexu.forwarding.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Export only: the application never launches a shell or executes this text. */
public final class OpenSshCommand {
    private OpenSshCommand() { }
    public static List<String> arguments(TunnelProfile p) {
        List<String> a = new ArrayList<>(List.of("-N", "-T", "-p", String.valueOf(p.sshPort()),
            "-o", "ExitOnForwardFailure=yes", "-o", "ServerAliveInterval=" + p.keepAliveSeconds(),
            "-o", "ServerAliveCountMax=" + p.keepAliveMisses(), "-o", "ConnectTimeout=" + p.connectTimeoutSeconds()));
        if (p.auth() == TunnelProfile.Auth.PRIVATE_KEY) a.addAll(List.of("-i", p.privateKey(), "-o", "IdentitiesOnly=yes"));
        else a.addAll(List.of("-o", "PreferredAuthentications=password"));
        a.addAll(List.of(p.mode() == TunnelProfile.Mode.REMOTE ? "-R" : "-L",
            TunnelProfile.bracket(p.bindHost()) + ":" + p.bindPort() + ":" + TunnelProfile.bracket(p.targetHost()) + ":" + p.targetPort(),
            "-l", p.username(), p.sshHost()));
        return List.copyOf(a);
    }
    public static String powershell(TunnelProfile p) {
        return "& ssh.exe " + arguments(p).stream().map(OpenSshCommand::psQuote).collect(Collectors.joining(" "));
    }
    public static String posix(TunnelProfile p) {
        return "ssh " + arguments(p).stream().map(s -> "'" + s.replace("'", "'\"'\"'") + "'").collect(Collectors.joining(" "));
    }
    private static String psQuote(String value) { return "'" + value.replace("'", "''") + "'"; }
}
