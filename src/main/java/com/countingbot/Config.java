package com.countingbot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record Config(String token, String prefix, String database, String topggUrl) {

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
        return new Config(
                token.trim(),
                props.getProperty("prefix", "c!").trim(),
                props.getProperty("database", "counting.db").trim(),
                props.getProperty("topgg_url", "https://top.gg/").trim()
        );
    }
}
