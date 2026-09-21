package it.nexu.forwarding;

import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.ssh.Cancellation;
import it.nexu.forwarding.ssh.ProfileForwardingFilter;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.forward.TcpForwardingFilter.Type;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ForwardingPolicyTest {
    @Test void dynamicAllowsOnlyClientChosenDirectChannelsUntilStopped() {
        TunnelProfile p = new TunnelProfile(java.util.UUID.randomUUID(),"SOCKS",TunnelProfile.Mode.DYNAMIC,
            "ssh.example.com",22,"demo","127.0.0.1",1080,"",0,TunnelProfile.Auth.PASSWORD,"",15,15,3,false,5,3,"");
        Cancellation cancellation = new Cancellation();
        ProfileForwardingFilter filter = new ProfileForwardingFilter(p,cancellation);
        SshdSocketAddress a = new SshdSocketAddress("127.0.0.1",8080);
        SshdSocketAddress b = new SshdSocketAddress("service.example.com",443);
        assertTrue(filter.canConnect(Type.Direct,a,null));
        assertTrue(filter.canConnect(Type.Direct,b,null));
        assertFalse(filter.canConnect(Type.Forwarded,a,null));
        assertFalse(filter.canListen(a,null));
        cancellation.cancel();
        assertFalse(filter.canConnect(Type.Direct,a,null));
    }

    @Test void localPolicyAllowsOnlyConfiguredDirectDestination() {
        TunnelProfile base = TunnelProfile.example();
        TunnelProfile p = new TunnelProfile(base.id(),base.name(),TunnelProfile.Mode.LOCAL,base.sshHost(),base.sshPort(),base.username(),
            base.bindHost(),base.bindPort(),base.targetHost(),base.targetPort(),base.auth(),base.privateKey(),base.connectTimeoutSeconds(),
            base.keepAliveSeconds(),base.keepAliveMisses(),base.reconnect(),base.reconnectAttempts(),base.reconnectDelaySeconds(),base.notes());
        ProfileForwardingFilter filter = new ProfileForwardingFilter(p,new Cancellation());
        SshdSocketAddress target = new SshdSocketAddress(p.targetHost(),p.targetPort());
        assertTrue(filter.canConnect(Type.Direct,target,null));
        assertFalse(filter.canConnect(Type.Forwarded,target,null));
        assertFalse(filter.canConnect(Type.Direct,new SshdSocketAddress("other.invalid",p.targetPort()),null));
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
        assertFalse(filter.canListen(target,null));
        cancellation.cancel();
        assertFalse(filter.canConnect(Type.Forwarded, target, null));
    }
}
