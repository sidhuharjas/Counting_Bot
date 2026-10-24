package com.countingbot;

import com.countingbot.command.CommandDispatcher;
import com.countingbot.db.Database;
import com.countingbot.game.FunnyRegistry;
import com.countingbot.listener.CountingListener;
import com.countingbot.listener.ReactionListener;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.gateway.intent.Intent;
import discord4j.gateway.intent.IntentSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class CountingBot {

    private static final Logger log = LoggerFactory.getLogger(CountingBot.class);

    public static volatile long startedAt = System.currentTimeMillis();

    private CountingBot() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.load();
        String token = config.token();
        if (token.isBlank() || token.equals("PUT_YOUR_BOT_TOKEN_HERE")) {
            System.err.println("No bot token found.");
            System.err.println("Set the DISCORD_TOKEN environment variable, or copy config.properties.example");
            System.err.println("to config.properties and fill in your token.");
            return;
        }

        Database db = new Database(Path.of(config.database()));
        FunnyRegistry funny = new FunnyRegistry(db);
        CommandDispatcher commands = new CommandDispatcher(db, config);
        CountingListener counting = new CountingListener(db, config, commands, funny);
        ReactionListener reactions = new ReactionListener(funny);

        log.info("Starting counting bot...");

        GatewayDiscordClient client = DiscordClient.create(token)
                .gateway()
                .setEnabledIntents(IntentSet.nonPrivileged().or(IntentSet.of(Intent.MESSAGE_CONTENT)))
                .login()
                .block();

        client.on(ReadyEvent.class)
                .subscribe(ready -> log.info("Logged in as {} — counting is live!",
                        ready.getSelf().getUsername()));

        counting.register(client);
        reactions.register(client);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            try {
                db.close();
            } catch (Exception e) {
                log.warn("Error closing database", e);
            }
        }));

        client.onDisconnect().block();
    }
}
