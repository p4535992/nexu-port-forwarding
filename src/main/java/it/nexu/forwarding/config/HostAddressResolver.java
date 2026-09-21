package it.nexu.forwarding.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** DNS-only host resolution used for display/filtering. Never sends ICMP echo packets. */
public final class HostAddressResolver {
    private HostAddressResolver() { }

    public static String resolve(String host) throws UnknownHostException {
        String value = host == null ? "" : host.trim();
        if (value.isEmpty()) return "";
        if (isIpv4Literal(value) || isIpv6Literal(value)) return stripBrackets(value);
        InetAddress[] addresses = InetAddress.getAllByName(value);
        if (addresses.length == 0) return "";
        for (InetAddress address : addresses) if (address instanceof Inet4Address) return address.getHostAddress();
        return addresses[0].getHostAddress();
    }

    public static boolean isIpLiteral(String host) {
        if (host == null) return false;
        String value = stripBrackets(host.trim());
        return isIpv4Literal(value) || isIpv6Literal(value);
    }
    private static String stripBrackets(String value) {
        return value.startsWith("[") && value.endsWith("]") ? value.substring(1,value.length()-1) : value;
    }
    private static boolean isIpv4Literal(String value) {
        String[] parts=value.split("\\.",-1); if(parts.length!=4) return false;
        for(String part:parts) {
            if(part.isEmpty()||part.length()>3||!part.chars().allMatch(Character::isDigit)) return false;
            int n=Integer.parseInt(part); if(n<0||n>255) return false;
        }
        return true;
    }
    private static boolean isIpv6Literal(String value) {
        if (!value.contains(":")) return false;
        // InetAddress parsing is safe here because a colon rules out a DNS hostname.
        try { return InetAddress.getByName(value).getHostAddress().contains(":"); }
        catch (Exception ignored) { return false; }
    }
}
