package com.emulator.fphardware;

import com.emulator.fphardware.cpu.CortexM3Emulator;
import com.emulator.fphardware.gui.EmulatorGUI;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Главный класс приложения эмулятора фискального регистратора
 * Процессор: LPC1778FBD208 (Cortex-M3)
 */
public class MainApp extends Application {
    
    private EmulatorGUI emulatorGUI;
    
    @Override
    public void start(Stage primaryStage) {
        try {
            emulatorGUI = new EmulatorGUI();
            
            Scene scene = new Scene(emulatorGUI.getRootPane(), 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
            
            primaryStage.setTitle("Эмулятор фискального регистратора - LPC1778FBD208 (Cortex-M3)");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                if (emulatorGUI != null) {
                    emulatorGUI.shutdown();
                }
            });
            
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("Ошибка запуска приложения: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
