package io.github.posseidon.core.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.function.Supplier;

public final class HostnameService {
    private final Supplier<String> hostnameProvider;

    public HostnameService() {
        // Default Strategy: Env -> System Prop -> DNS
        this.hostnameProvider = () ->
                Optional.ofNullable(System.getenv("HOSTNAME"))
                        .or(() -> Optional.ofNullable(System.getProperty("hostname")))
                        .orElseGet(this::resolveDnsHostname);
    }

    private String resolveDnsHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }

    public HostnameService(Supplier<String> customProvider) {
        this.hostnameProvider = customProvider;
    }

    public String getMachineName() {
        return hostnameProvider.get();
    }
}
