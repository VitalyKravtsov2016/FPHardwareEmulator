package com.fpemulator;

import com.fpemulator.ui.MainWindow;

import javax.swing.*;
import java.util.logging.*;

/**
 * Application entry point for the FP Hardware Emulator.
 *
 * Launches the Swing GUI on the Event Dispatch Thread.
 */
public class Main {

    public static void main(String[] args) {
        // Configure JUL logging to console
        configureLogging(args);

        // Launch GUI on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Fall back to default L&F
            }
            new MainWindow();
        });
    }

    private static void configureLogging(String[] args) {
        boolean verbose = false;
        for (String arg : args) {
            if ("--verbose".equals(arg) || "-v".equals(arg)) {
                verbose = true;
            }
        }

        Logger root = Logger.getLogger("");
        root.setLevel(verbose ? Level.FINE : Level.INFO);
        for (Handler h : root.getHandlers()) {
            h.setLevel(verbose ? Level.FINE : Level.INFO);
            h.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord r) {
                    return String.format("[%s] %s: %s%n",
                            r.getLevel().getName(),
                            r.getLoggerName().replaceFirst("com\\.fpemulator\\.", ""),
                            r.getMessage());
                }
            });
        }
    }
}
