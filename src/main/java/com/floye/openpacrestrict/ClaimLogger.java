package com.floye.openpacrestrict;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClaimLogger {

    private static final String DEFAULT_FILE = "PKH/logs/claim_logs.csv";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path filePath;

    public ClaimLogger() {
        filePath = Paths.get(DEFAULT_FILE);
        initFile();
    }

    private void initFile() {
        if (!Files.exists(filePath)) {
            try (FileWriter writer = new FileWriter(filePath.toFile(), true)) {
                writer.write("DateHeure,Player,Action,X,Z,Dimension\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void log(String playerName, String action, int x, int z, String dimension) {
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String line = String.format("%s,%s,%s,%d,%d,%s%n", timestamp, playerName, action, x, z, dimension);
        try (FileWriter writer = new FileWriter(filePath.toFile(), true)) {
            writer.write(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}