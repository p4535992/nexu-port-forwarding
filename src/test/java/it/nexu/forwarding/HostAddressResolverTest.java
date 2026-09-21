package it.nexu.forwarding;
import it.nexu.forwarding.config.HostAddressResolver;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class HostAddressResolverTest {
 @Test void ipv4LiteralIsCopiedWithoutDns() throws Exception { assertEquals("203.0.113.10",HostAddressResolver.resolve("203.0.113.10")); }
 @Test void loopbackNameResolves() throws Exception { assertFalse(HostAddressResolver.resolve("localhost").isBlank()); }
 @Test void literalDetectionHandlesIpv4AndIpv6() { assertTrue(HostAddressResolver.isIpLiteral("127.0.0.1"));assertTrue(HostAddressResolver.isIpLiteral("::1"));assertFalse(HostAddressResolver.isIpLiteral("example.invalid")); }
}
