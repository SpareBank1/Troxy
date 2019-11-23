package no.sb1.troxy.http.common;

import java.net.Inet4Address;
import java.util.Objects;

public class ConnectorAddr {
    private final String protocol;
    private final String address;
    private final int port;

    public ConnectorAddr(String protocol, String address, int port) {
        this.protocol = protocol;
        this.address = address;
        this.port = port;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorAddr that = (ConnectorAddr) o;
        return getPort() == that.getPort() &&
                Objects.equals(getProtocol(), that.getProtocol()) &&
                Objects.equals(getAddress(), that.getAddress());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProtocol(), getAddress(), getPort());
    }
}
