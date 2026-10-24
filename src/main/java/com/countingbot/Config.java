package com.countingbot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record Config(String token, String prefix, String database, double saveCost) {

    public static Config load() throws IOException {
        Properties props = new Properties();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        }
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            token = props.getProperty("token", "");
        }
        double saveCost;
        try {
            saveCost = Double.parseDouble(props.getProperty("save_cost", "10").trim());
        } catch (NumberFormatException e) {
            saveCost = 10;
        }
        if (saveCost < 0) {
            saveCost = 0;
        }
        return new Config(
                token.trim(),
                props.getProperty("prefix", "c!").trim(),
                props.getProperty("database", "counting.db").trim(),
                saveCost
        );
    }
}
