package it.nexu.forwarding;

import it.nexu.forwarding.config.WindowStateStore;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class WindowStateStoreTest {
    @Test void missingStateIsIgnored() throws Exception {
        Path dir = Files.createTempDirectory("nexu-window-");
        assertTrue(new WindowStateStore(dir.resolve("window.properties")).load().isEmpty());
    }

    @Test void stateRoundTrips() throws Exception {
        Path dir = Files.createTempDirectory("nexu-window-");
        WindowStateStore store = new WindowStateStore(dir.resolve("window.properties"));
        WindowStateStore.State expected = new WindowStateStore.State(120, 80, 1180, 760, true);
        store.save(expected);
        assertEquals(expected, store.load().orElseThrow());
    }

    @Test void corruptOrUnsafeStateIsIgnored() throws Exception {
        Path dir = Files.createTempDirectory("nexu-window-");
        Path file = dir.resolve("window.properties");
        Files.writeString(file, "version=1\nx=NaN\ny=0\nwidth=999999999\nheight=760\nmaximized=true\n");
        assertTrue(new WindowStateStore(file).load().isEmpty());
    }
}
