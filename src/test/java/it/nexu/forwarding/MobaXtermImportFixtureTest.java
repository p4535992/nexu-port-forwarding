package it.nexu.forwarding;

import it.nexu.forwarding.importer.MobaXtermImport;
import it.nexu.forwarding.model.TunnelProfile;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class MobaXtermImportFixtureTest {
    @Test void suppliedStyleMobaconfImportsAllNineForwardingsWithoutPasswords() throws Exception {
        String ini;
        try(var in=getClass().getResourceAsStream("/mobaxterm/MobaXterm.example.mobaconf")) {
            assertNotNull(in);
            ini=new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
        var report=MobaXtermImport.parse(ini,"");
        assertEquals(9,report.entries());
        assertEquals(9,report.candidates().size());
        assertEquals(0,report.skipped());
        assertEquals(List.of("demo","demo","demo","developer","service","service","demo","demo","demo"),
            report.candidates().stream().map(c->c.profile().username()).toList());
        assertEquals(List.of(TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE,
            TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE,
            TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.LOCAL,TunnelProfile.Mode.REMOTE),
            report.candidates().stream().map(c->c.profile().mode()).toList());
        var proxied=report.candidates().get(3).profile();
        assertEquals(TunnelProfile.ProxyType.HTTP_CONNECT,proxied.proxyType());
        assertEquals("proxy.example.invalid",proxied.proxyHost());
        assertEquals(3128,proxied.proxyPort());
        assertEquals("",proxied.proxyUsername());
        assertEquals("0.0.0.0",proxied.bindHost());
        assertTrue(report.candidates().get(3).requiresReview());
        assertEquals(TunnelProfile.Auth.PRIVATE_KEY,report.candidates().get(4).profile().auth());
        assertTrue(report.candidates().get(4).profile().privateKey().endsWith("demo.ppk"));
        assertTrue(report.warnings().stream().noneMatch(x->x.toLowerCase().contains("password")));
    }
}
