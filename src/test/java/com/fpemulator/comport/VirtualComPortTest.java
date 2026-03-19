package com.fpemulator.comport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VirtualComPort.
 *
 * Uses a random available port to avoid conflicts in CI environments.
 */
class VirtualComPortTest {

    private static final int TEST_PORT = 17890;
    private VirtualComPort comPort;

    @BeforeEach
    void setUp() throws Exception {
        comPort = new VirtualComPort(TEST_PORT);
        comPort.open();
    }

    @AfterEach
    void tearDown() {
        comPort.close();
    }

    @Test
    void testOpenAndClose() {
        assertTrue(comPort.isOpen());
        comPort.close();
        assertFalse(comPort.isOpen());
    }

    @Test
    void testClientConnect() throws Exception {
        boolean[] connected = {false};
        comPort.setConnectCallback(() -> connected[0] = true);

        try (Socket s = new Socket("localhost", TEST_PORT)) {
            Thread.sleep(200);
            assertTrue(comPort.isClientConnected());
            assertTrue(connected[0]);
        }
    }

    @Test
    void testReceiveData() throws Exception {
        boolean[] received = {false};
        comPort.setReceiveCallback(b -> received[0] = true);

        try (Socket s = new Socket("localhost", TEST_PORT)) {
            Thread.sleep(100);
            s.getOutputStream().write(0x42);
            s.getOutputStream().flush();
            Thread.sleep(200);
            assertTrue(received[0]);
            assertEquals(0x42, comPort.receive());
        }
    }

    @Test
    void testSendData() throws Exception {
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            Thread.sleep(100);
            comPort.send(0xAB);

            byte[] buf = new byte[1];
            s.getInputStream().read(buf);
            assertEquals((byte) 0xAB, buf[0]);
        }
    }

    @Test
    void testSendByteArray() throws Exception {
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            Thread.sleep(100);
            byte[] data = "HELLO\r\n".getBytes(StandardCharsets.UTF_8);
            comPort.send(data);

            byte[] buf = new byte[data.length];
            int total = 0;
            long deadline = System.currentTimeMillis() + 2000;
            while (total < data.length && System.currentTimeMillis() < deadline) {
                int n = s.getInputStream().read(buf, total, buf.length - total);
                if (n > 0) total += n;
            }
            assertEquals(data.length, total);
            assertArrayEquals(data, buf);
        }
    }

    @Test
    void testDisconnectCallback() throws Exception {
        boolean[] disconnected = {false};
        comPort.setDisconnectCallback(() -> disconnected[0] = true);

        Socket s = new Socket("localhost", TEST_PORT);
        Thread.sleep(100);
        s.close();
        Thread.sleep(300);
        assertTrue(disconnected[0]);
    }
}
