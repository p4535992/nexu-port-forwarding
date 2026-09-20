package it.nexu.forwarding;

import org.junit.jupiter.api.Test;

final class CoreTest {
    @Test void dependencyFreeCoreTests() throws Exception { CoreSelfTest.runAll(); }
}
