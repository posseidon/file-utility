package io.github.posseidon.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostnameServiceTest {

    @Test
    void shouldReturnHostnameFromEnvWhenAvailable() {
        HostnameService service = new HostnameService(() -> "k8s-pod-123");
        assertThat(service.getMachineName()).isEqualTo("k8s-pod-123");
    }

    @Test
    void shouldReturnDefaultWhenAllLookupsFail() {
        HostnameService service = new HostnameService(() -> null);
        assertThat(service.getMachineName()).isNull();
    }

    @Test
    void shouldHandleEmptyStringsGracefully() {
        HostnameService service = new HostnameService(() -> "");
        assertThat(service.getMachineName()).isEmpty();
    }

    @Test
    void shouldHandleWhitespaceHostnames() {
        HostnameService service = new HostnameService(() -> "   ");
        assertThat(service.getMachineName()).isEqualTo("   ");
    }

    @Test
    void shouldHandleExceptionInDnsResolution() {
        HostnameService service = new HostnameService(() -> {
            throw new RuntimeException("DNS Down");
        });

        assertThatThrownBy(service::getMachineName).isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReturnCurrentMachineName() {
        HostnameService service = new HostnameService();
        String hostName = service.getMachineName();

        assertThat(hostName).isNotBlank();
    }
}
