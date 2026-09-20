package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.TcpForwardingFilter;

/** Permit only the configured remote-forward destination, never arbitrary server-initiated forwarding. */
public final class ProfileForwardingFilter implements TcpForwardingFilter {
    private final TunnelProfile profile;
    private final Cancellation cancellation;
    public ProfileForwardingFilter(TunnelProfile profile, Cancellation cancellation) {
        this.profile = profile; this.cancellation = cancellation;
    }
    @Override public boolean canListen(SshdSocketAddress address, Session session) {
        // The client creates its own local listeners. The SSH peer may not create additional listeners.
        return false;
    }
    @Override public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
        // MINA resolves a forwarded-tcpip channel to the locally registered destination before this check.
        return !cancellation.isCancelled() && profile.mode() == TunnelProfile.Mode.REMOTE
            && type == Type.Forwarded && address != null
            && address.getPort() == profile.targetPort()
            && address.getHostName().equalsIgnoreCase(profile.targetHost());
    }
}
