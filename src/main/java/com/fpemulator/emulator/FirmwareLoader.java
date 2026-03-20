package com.fpemulator.emulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads firmware binary files (.bin) for the fiscal register emulator.
 */
public class FirmwareLoader {

    public static class FirmwareInfo {
        public final byte[] data;
        public final String path;
        public final long sizeBytes;
        public final String checksum;

        public FirmwareInfo(byte[] data, String path) {
            this.data = data;
            this.path = path;
            this.sizeBytes = data.length;
            this.checksum = computeChecksum(data);
        }

        private String computeChecksum(byte[] data) {
            int crc = 0;
            for (byte b : data) {
                crc ^= (b & 0xFF);
                for (int i = 0; i < 8; i++) {
                    if ((crc & 1) != 0) {
                        crc = (crc >>> 1) ^ 0xA001;
                    } else {
                        crc >>>= 1;
                    }
                }
            }
            return String.format("CRC16: 0x%04X", crc & 0xFFFF);
        }

        @Override
        public String toString() {
            return String.format("Firmware[path=%s, size=%d bytes, %s]", path, sizeBytes, checksum);
        }
    }

    /**
     * Load a firmware .bin file from the given path.
     *
     * @param filePath path to the .bin firmware file
     * @return FirmwareInfo with loaded data
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is empty or too large
     */
    public static FirmwareInfo load(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Файл не найден: " + filePath);
        }
        long size = Files.size(path);
        if (size == 0) {
            throw new IllegalArgumentException("Файл прошивки пустой: " + filePath);
        }
        if (size > 16 * 1024 * 1024) {
            throw new IllegalArgumentException("Файл прошивки слишком большой (макс. 16 МБ): " + size + " байт");
        }
        byte[] data = Files.readAllBytes(path);
        return new FirmwareInfo(data, filePath);
    }

    /**
     * Validate firmware header (simple check for known signatures).
     */
    public static boolean isValidFirmware(byte[] data) {
        if (data == null || data.length < 4) return false;
        // Check for common binary file markers
        // 0x7F 'E' 'L' 'F' — ELF binary
        if (data[0] == 0x7F && data[1] == 'E' && data[2] == 'L' && data[3] == 'F') return true;
        // All-zero header is suspicious but not invalid for embedded firmware
        // Accept any non-empty binary
        return data.length >= 4;
    }
}
