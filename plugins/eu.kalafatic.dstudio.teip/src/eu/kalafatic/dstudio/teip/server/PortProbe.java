package eu.kalafatic.dstudio.teip.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/** Port availability probing for the interactive port-conflict resolution flow. */
public final class PortProbe {

    private PortProbe() {
    }

    /**
     * Returns the first free port starting at {@code startPort} on the given bind
     * address, or {@code -1} when none of {@code attempts} candidates is free.
     */
    public static int findFreePort(String bindAddress, int startPort, int attempts) {
        for (int port = Math.max(1, startPort); port < startPort + Math.max(1, attempts);
                port++) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new java.net.InetSocketAddress(
                        InetAddress.getByName(bindAddress), port));
                return port;
            } catch (IOException ignored) {
                // occupied: try the next candidate
            }
        }
        return -1;
    }
}
