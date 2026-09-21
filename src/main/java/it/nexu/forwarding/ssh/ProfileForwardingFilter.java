package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.TcpForwardingFilter;

/** Scope TCP channels to the forwarding mode selected by the profile. */
public final class ProfileForwardingFilter implements TcpForwardingFilter {
    private final TunnelProfile profile;
    private final Cancellation cancellation;
    public ProfileForwardingFilter(TunnelProfile profile, Cancellation cancellation) {
        this.profile = profile; this.cancellation = cancellation;
    }
    @Override public boolean canListen(SshdSocketAddress address, Session session) {
        // Listeners are created explicitly by the client APIs; the SSH peer may not create extras.
        return false;
    }
    @Override public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
        if (cancellation.isCancelled() || address == null) return false;
        return switch (profile.mode()) {
            // Server-side -R connections arrive as forwarded-tcpip and are pinned to the configured destination.
            case REMOTE -> type == Type.Forwarded && fixedDestination(address);
            // Client-side -L opens direct-tcpip only to the configured fixed destination.
            case LOCAL -> type == Type.Direct && fixedDestination(address);
            // SOCKS -D is intentionally client-selected. MINA opens direct-tcpip to the address requested by
            // the local SOCKS client. The listener bind still controls who can ask for those destinations.
            case DYNAMIC -> type == Type.Direct;
        };
    }
    private boolean fixedDestination(SshdSocketAddress address) {
        return address.getPort() == profile.targetPort()
            && address.getHostName().equalsIgnoreCase(profile.targetHost());
    }
}
