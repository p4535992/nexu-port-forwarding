package it.nexu.forwarding.ssh;

import it.nexu.forwarding.model.TunnelProfile;

@FunctionalInterface
public interface HostTrustPrompt {
    enum Decision { REJECT, THIS_APP_SESSION, REMEMBER }
    Decision ask(TunnelProfile profile, String fingerprint, Cancellation cancellation) throws Exception;
}
