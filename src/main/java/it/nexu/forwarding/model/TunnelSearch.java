package it.nexu.forwarding.model;

import java.util.List;
import java.util.Locale;

/** Search each term as a case-insensitive substring of any non-secret profile field. */
public final class TunnelSearch {
    public enum ModeFilter { ALL, LOCAL, REMOTE, LOCAL_REMOTE, DYNAMIC }

    private TunnelSearch() { }

    /** Every whitespace-separated term must match at least one field (AND across terms, OR across fields). */
    public static boolean matches(TunnelProfile profile, String resolvedIp, String query) {
        if (profile == null) return false;
        if (query == null || query.isBlank()) return true;

        List<String> fields = List.of(
            profile.id().toString(), profile.name(), profile.notes(), profile.installation(),
            profile.origin().name(), profile.mode().name(), profile.sourceKey(),
            profile.sshHost(), Integer.toString(profile.sshPort()), profile.username(),
            profile.bindHost(), Integer.toString(profile.bindPort()),
            profile.targetHost(), profile.mode() == TunnelProfile.Mode.DYNAMIC ? "" : Integer.toString(profile.targetPort()),
            profile.proxyHost(), profile.usesProxy() ? Integer.toString(profile.proxyPort()) : "",
            profile.proxyUsername(), profile.proxyLabel(), resolvedIp == null ? "" : resolvedIp
        ).stream().map(field -> field.toLowerCase(Locale.ROOT)).toList();

        for (String term : query.strip().toLowerCase(Locale.ROOT).split("\\s+")) {
            if (fields.stream().noneMatch(field -> field.contains(term))) return false;
        }
        return true;
    }

    public static boolean matchesMode(TunnelProfile.Mode mode, ModeFilter filter) {
        if (mode == null) return false;
        if (filter == null) return true;
        return switch (filter) {
            case ALL -> true;
            case LOCAL -> mode == TunnelProfile.Mode.LOCAL;
            case REMOTE -> mode == TunnelProfile.Mode.REMOTE;
            case LOCAL_REMOTE -> mode == TunnelProfile.Mode.LOCAL || mode == TunnelProfile.Mode.REMOTE;
            case DYNAMIC -> mode == TunnelProfile.Mode.DYNAMIC;
        };
    }
}
