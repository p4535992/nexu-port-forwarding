package it.nexu.forwarding;

import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.Cancellation;
import it.nexu.forwarding.ssh.ProfileForwardingFilter;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.TcpForwardingFilter.Type;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ForwardingPolicyTest {
    @Test void dynamicDoesNotPermitUnsolicitedServerChannels() {
        TunnelProfile p = new TunnelProfile(java.util.UUID.randomUUID(),"SOCKS",TunnelProfile.Mode.DYNAMIC,
            "ssh.example.com",22,"demo","127.0.0.1",1080,"",0,TunnelProfile.Auth.PASSWORD,"",15,15,3,false,5,3,"");
        ProfileForwardingFilter filter = new ProfileForwardingFilter(p,new Cancellation());
        SshdSocketAddress target = new SshdSocketAddress("127.0.0.1",8080);
        assertFalse(filter.canConnect(Type.Direct,target,null));
        assertFalse(filter.canConnect(Type.Forwarded,target,null));
        assertFalse(filter.canListen(target,null));
    }

    @Test void remotePolicyAllowsOnlyConfiguredDestinationUntilStopped() {
        TunnelProfile p = TunnelProfile.example();
        Cancellation cancellation = new Cancellation();
        ProfileForwardingFilter filter = new ProfileForwardingFilter(p, cancellation);
        SshdSocketAddress target = new SshdSocketAddress(p.targetHost(), p.targetPort());
        assertTrue(filter.canConnect(Type.Forwarded, target, null));
        assertFalse(filter.canConnect(Type.Direct, target, null));
        assertFalse(filter.canConnect(Type.Forwarded, new SshdSocketAddress("other.invalid", p.targetPort()), null));
        assertFalse(filter.canConnect(Type.Forwarded, new SshdSocketAddress(p.targetHost(), p.targetPort() + 1), null));
        assertFalse(filter.canConnect(Type.Forwarded, null, null));
        assertFalse(filter.canListen(target, null));
        cancellation.cancel();
        assertFalse(filter.canConnect(Type.Forwarded, target, null));
    }
}
