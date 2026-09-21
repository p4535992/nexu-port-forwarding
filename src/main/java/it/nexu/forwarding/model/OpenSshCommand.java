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
        String option = switch (p.mode()) { case REMOTE -> "-R"; case LOCAL -> "-L"; case DYNAMIC -> "-D"; };
        String forward = TunnelProfile.bracket(p.bindHost()) + ":" + p.bindPort();
        if (p.mode() != TunnelProfile.Mode.DYNAMIC)
            forward += ":" + TunnelProfile.bracket(p.targetHost()) + ":" + p.targetPort();
        a.addAll(List.of(option, forward, "-l", p.username(), p.sshHost()));
        return List.copyOf(a);
    }
    public static String powershell(TunnelProfile p) {
        return "& ssh.exe " + arguments(p).stream().map(OpenSshCommand::psQuote).collect(Collectors.joining(" "));
    }
    public static String cmd(TunnelProfile p) {
        return "ssh.exe " + arguments(p).stream().map(OpenSshCommand::cmdQuote).collect(Collectors.joining(" "));
    }
    public static String posix(TunnelProfile p) {
        return "ssh " + arguments(p).stream().map(s -> "'" + s.replace("'", "'\"'\"'") + "'").collect(Collectors.joining(" "));
    }
    private static String psQuote(String value) { return "'" + value.replace("'", "''") + "'"; }
    private static String cmdQuote(String value) {
        String escaped = value.replace("%", "%%").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
