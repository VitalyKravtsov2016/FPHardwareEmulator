package com.fpemulator.comport;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Virtual COM port implemented as a TCP server.
 *
 * External software (e.g. the cash register POS application) can connect to
 * this port using a TCP-to-serial bridge such as com0com/VSPE, or directly
 * via a raw TCP socket.
 *
 * The server listens on localhost:{tcpPort}.  When a client connects, bytes
 * written to this port (via {@link #send}) are forwarded to the client, and
 * bytes received from the client are buffered and delivered via a callback.
 *
 * Only one client at a time is supported (suitable for a point-to-point
 * serial link).
 */
public class VirtualComPort {

    private static final Logger LOG = Logger.getLogger(VirtualComPort.class.getName());

    private final int            tcpPort;
    private ServerSocket         serverSocket;
    private Socket               clientSocket;
    private OutputStream         clientOut;

    private volatile boolean     running = false;
    private Thread               acceptThread;
    private Thread               receiveThread;

    // Bytes received from the remote client → delivered to firmware via UART
    private final BlockingQueue<Integer> rxBuffer = new ArrayBlockingQueue<>(4096);

    // Callback invoked on the receive thread for each incoming byte
    private Consumer<Integer> receiveCallback;

    // Callback invoked when a client connects / disconnects
    private Runnable connectCallback;
    private Runnable disconnectCallback;

    public VirtualComPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Start listening for incoming connections. */
    public synchronized void open() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(tcpPort);
        serverSocket.setReuseAddress(true);
        running = true;

        acceptThread = new Thread(this::acceptLoop, "vcom-accept-" + tcpPort);
        acceptThread.setDaemon(true);
        acceptThread.start();
        LOG.info(String.format("Virtual COM port listening on TCP port %d", tcpPort));
    }

    /** Stop the virtual COM port. */
    public synchronized void close() {
        running = false;
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        if (acceptThread  != null) acceptThread.interrupt();
        if (receiveThread != null) receiveThread.interrupt();
        LOG.info("Virtual COM port closed");
    }

    public boolean isOpen() { return running; }
    public boolean isClientConnected() { return clientSocket != null && !clientSocket.isClosed(); }
    public int getTcpPort() { return tcpPort; }

    // ── Send / receive ────────────────────────────────────────────────────────

    /** Send a single byte to the connected client (firmware → external). */
    public synchronized void send(int b) {
        if (clientOut == null) return;
        try {
            clientOut.write(b & 0xFF);
            clientOut.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "COM port send error", e);
            disconnectClient();
        }
    }

    /** Send a byte array to the connected client. */
    public synchronized void send(byte[] data) {
        if (clientOut == null) return;
        try {
            clientOut.write(data);
            clientOut.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "COM port send error", e);
            disconnectClient();
        }
    }

    /** Poll the next byte received from the client (-1 if none). */
    public int receive() {
        Integer b = rxBuffer.poll();
        return b != null ? b : -1;
    }

    public boolean hasReceivedData() { return !rxBuffer.isEmpty(); }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void acceptLoop() {
        while (running) {
            try {
                LOG.fine("Waiting for client connection…");
                Socket s = serverSocket.accept();
                synchronized (this) {
                    clientSocket = s;
                    clientOut    = s.getOutputStream();
                }
                LOG.info(String.format("Client connected: %s", s.getRemoteSocketAddress()));
                if (connectCallback != null) connectCallback.run();
                startReceiveThread(s);
            } catch (SocketException e) {
                if (running) LOG.log(Level.WARNING, "Accept error", e);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Accept error", e);
            }
        }
    }

    private void startReceiveThread(Socket s) {
        if (receiveThread != null) receiveThread.interrupt();
        receiveThread = new Thread(() -> {
            try (InputStream in = s.getInputStream()) {
                int b;
                while ((b = in.read()) != -1) {
                    rxBuffer.offer(b);
                    if (receiveCallback != null) receiveCallback.accept(b);
                }
            } catch (SocketException e) {
                if (running) LOG.log(Level.FINE, "Client disconnected", e);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Receive error", e);
            } finally {
                disconnectClient();
                if (disconnectCallback != null) disconnectCallback.run();
            }
        }, "vcom-receive-" + tcpPort);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    private synchronized void disconnectClient() {
        try { if (clientSocket != null) clientSocket.close(); } catch (IOException ignored) {}
        clientSocket = null;
        clientOut    = null;
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public void setReceiveCallback(Consumer<Integer> cb)  { this.receiveCallback    = cb; }
    public void setConnectCallback(Runnable cb)           { this.connectCallback    = cb; }
    public void setDisconnectCallback(Runnable cb)        { this.disconnectCallback = cb; }
}
