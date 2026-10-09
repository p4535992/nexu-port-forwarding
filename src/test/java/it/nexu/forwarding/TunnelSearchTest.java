package it.nexu.forwarding;

import it.nexu.forwarding.model.TunnelProfile;
import it.nexu.forwarding.model.TunnelSearch;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class TunnelSearchTest {
    private static final UUID ID = UUID.fromString("12345678-1234-4234-8234-123456789abc");

    private static TunnelProfile profile() {
        return new TunnelProfile(ID, "Prato - app", TunnelProfile.Mode.REMOTE,
            "ssh.example.org", 2222, "alice", "127.0.0.1", 8687,
            "192.0.2.40", 5432, TunnelProfile.Auth.PASSWORD, "", 15, 15, 3,
            false, 5, 3, "Descrizione del servizio aziendale");
    }

    @Test void multipleTermsMustEachMatchInAnyField() {
        assertTrue(TunnelSearch.matches(profile(), "198.51.100.7", "prato 8687"));
        assertTrue(TunnelSearch.matches(profile(), "198.51.100.7", "8687  PRATO"));
        assertFalse(TunnelSearch.matches(profile(), "198.51.100.7", "prato 9999"));
        assertFalse(TunnelSearch.matches(profile(), "198.51.100.7", "prato8687"));
    }

    @Test void searchesIdNameNotesHostsIpsAndAllPorts() {
        TunnelProfile profile = profile();
        for (String query : new String[]{"12345678", "ato", "descriz", "ssh.example", "127.0.0",
                "192.0.2.4", "198.51.100", "2222", "8687", "5432", "alice", "remote"}) {
            assertTrue(TunnelSearch.matches(profile, "198.51.100.7", query), query);
        }
        assertFalse(TunnelSearch.matches(profile, "", "198.51.100"));
    }

    @Test void installationMatchesAnotherTermFromDifferentField() {
        TunnelProfile site = profile().withInstallation("Ufficio di Pistoia");
        assertTrue(TunnelSearch.matches(site, null, "PISTOIA 8687"));
        assertFalse(TunnelSearch.matches(site, null, "PISTOIA 8688"));
    }

    @Test void ignoresEmptyQueryAndHandlesNonmatchingTerm() {
        assertTrue(TunnelSearch.matches(profile(), null, null));
        assertTrue(TunnelSearch.matches(profile(), "", " \t "));
        assertFalse(TunnelSearch.matches(profile(), "", "Milano 8687"));
        assertFalse(TunnelSearch.matches(null, "", "prato"));
    }

    @Test void newModeFilterIncludesLocalAndRemoteButNotDynamic() {
        for (TunnelProfile.Mode mode : TunnelProfile.Mode.values()) {
            assertTrue(TunnelSearch.matchesMode(mode, TunnelSearch.ModeFilter.ALL));
            assertTrue(TunnelSearch.matchesMode(mode, null));
            assertEquals(mode != TunnelProfile.Mode.DYNAMIC,
                TunnelSearch.matchesMode(mode, TunnelSearch.ModeFilter.LOCAL_REMOTE));
            assertEquals(mode == TunnelProfile.Mode.LOCAL,
                TunnelSearch.matchesMode(mode, TunnelSearch.ModeFilter.LOCAL));
            assertEquals(mode == TunnelProfile.Mode.REMOTE,
                TunnelSearch.matchesMode(mode, TunnelSearch.ModeFilter.REMOTE));
            assertEquals(mode == TunnelProfile.Mode.DYNAMIC,
                TunnelSearch.matchesMode(mode, TunnelSearch.ModeFilter.DYNAMIC));
        }
    }
}
