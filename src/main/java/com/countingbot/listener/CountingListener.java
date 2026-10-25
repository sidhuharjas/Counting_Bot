package com.countingbot.listener;

import com.countingbot.Config;
import com.countingbot.command.CommandDispatcher;
import com.countingbot.db.Database;
import com.countingbot.db.Database.GuildConfig;
import com.countingbot.game.FailMessages;
import com.countingbot.game.FunnyRegistry;
import com.countingbot.game.MathEvaluator;
import com.countingbot.util.Format;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.emoji.Emoji;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.spec.GuildMemberEditSpec;
import discord4j.common.util.Snowflake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CountingListener {

    private static final Logger log = LoggerFactory.getLogger(CountingListener.class);

    private final Database db;
    private final Config config;
    private final CommandDispatcher commands;
    private final FunnyRegistry funny;
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    public CountingListener(Database db, Config config, CommandDispatcher commands, FunnyRegistry funny) {
        this.db = db;
        this.config = config;
        this.commands = commands;
        this.funny = funny;
    }

    public void register(GatewayDiscordClient client) {
        client.on(MessageCreateEvent.class)
                .flatMap(event -> Mono.defer(() -> handle(event))
                        .onErrorResume(err -> {
                            log.error("message handler failed", err);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    private Mono<Void> handle(MessageCreateEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().map(User::isBot).orElse(true)) {
            return Mono.empty();
        }
        String content = message.getContent() == null ? "" : message.getContent();
        String trimmed = content.trim();
        String prefix = config.prefix();
        if (trimmed.length() >= prefix.length()
                && trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return commands.dispatch(event, trimmed);
        }
        Optional<Snowflake> guildIdOpt = message.getGuildId();
        if (guildIdOpt.isEmpty()) {
            return Mono.empty();
        }
        long guildId = guildIdOpt.get().asLong();
        GuildConfig cfg = db.getOrCreateGuild(guildId);
        if (!inCountingChannel(event, cfg)) {
            return Mono.empty();
        }
        long userId = message.getAuthor().map(u -> u.getId().asLong()).orElse(-1L);
        Parse parse = parseNumber(trimmed, cfg.disableMaths());

        synchronized (lock(guildId)) {
            cfg = db.getOrCreateGuild(guildId);

            if (parse == null) {
                if (cfg.numbersOnly()) {
                    return fail(event, cfg, userId, null);
                }
                return Mono.empty();
            }

            long lastUserId = cfg.lastUserId() == null ? -1L : cfg.lastUserId();
            int expected = cfg.currentCount() + 1;

            if (parse.value() == expected) {
                // right number, but from the same person twice in a row: reject it
                if (lastUserId == userId) {
                    String mention = message.getAuthor().map(User::getMention).orElse("");
                    return message.getChannel()
                            .flatMap(ch -> ch.createMessage(
                                    mention + " wait for someone else to send " + expected + "."))
                            .then();
                }
                return success(event, cfg, userId, parse, expected);
            }
            // wrong number fails the count, even from the last counter
            return fail(event, cfg, userId, parse.wasNumber() ? trimmed : null);
        }
    }

    private Mono<Void> success(MessageCreateEvent event, GuildConfig cfg, long userId,
                               Parse parse, int expected) {
        long guildId = cfg.guildId();
        Message message = event.getMessage();

        double jokeBonus = 0;
        long jokeCounter = -1;
        Optional<FunnyRegistry.Completed> completed = funny.completeForGuild(guildId);
        if (completed.isPresent()) {
            jokeCounter = completed.get().counterId();
            jokeBonus = completed.get().bonus();
        }

        db.advance(guildId, userId, expected);

        // check mark on the correct number
        Mono<Void> react = message.addReaction(Emoji.unicode("✅"))
                .onErrorResume(e -> Mono.empty())
                .then();

        StringBuilder text = new StringBuilder();
        if (jokeCounter > 0 && jokeBonus > 0) {
            text.append("<@").append(jokeCounter).append(">'s joke got rated **+")
                    .append(Format.points(jokeBonus)).append("** points.\n");
        }

        if (parse.funny()) {
            funny.register(message.getId().asLong(), guildId, userId);
            String mention = message.getAuthor().map(User::getMention).orElse("");
            text.append(mention)
                    .append(" added something funny to the count. React with 1 to 5 on their message to rate it, for up to **+5** extra points.\n");
        }

        String milestone = FailMessages.milestone(expected);
        if (!milestone.isEmpty()) {
            text.append(milestone).append('\n');
        }

        if (text.isEmpty()) {
            return react;
        }
        text.append("Next number is **").append(expected + 1).append("**.");
        return react.then(message.getChannel()
                .flatMap(ch -> ch.createMessage(text.toString().trim()))
                .then());
    }

    private Mono<Void> fail(MessageCreateEvent event, GuildConfig cfg, long userId, String typed) {
        long guildId = cfg.guildId();
        Message message = event.getMessage();
        int at = cfg.currentCount();
        int expected = at + 1;
        String mention = message.getAuthor().map(User::getMention).orElse("<@unknown>");

        funny.completeForGuild(guildId);

        String roast = FailMessages.random(mention, typed, expected);
        boolean usedUserSave = db.consumeUserSave(userId);
        String saveLine;
        boolean reset = false;
        if (usedUserSave) {
            saveLine = "A save was used (you have " + Format.saves(db.getSaves(userId))
                    + " left). Next number is still **" + expected + "**.";
            db.setLastUser(guildId, userId);
        } else if (db.consumeGuildSave(guildId)) {
            saveLine = "The server save bank covered it. Next number is still **" + expected + "**.";
            db.setLastUser(guildId, userId);
        } else {
            reset = true;
            db.resetCount(guildId);
            saveLine = "Count reset from **" + at + "**. Next number is **1**.";
        }

        double pointsBefore = db.getUserGlobal(userId).points();
        db.addPoints(userId, guildId, -2, false);
        double pointsAfter = db.getUserGlobal(userId).points();
        boolean crossedZero = pointsBefore >= 0 && pointsAfter < 0;

        StringBuilder text = new StringBuilder(roast).append('\n').append(saveLine)
                .append("\n-2 points (you now have **")
                .append(Format.points(pointsAfter))
                .append("**).");
        if (crossedZero) {
            text.append("\nPoints dropped below 0. You're muted for 5 minutes.");
        }
        if (reset) {
            text.append("\nUse ").append(config.prefix())
                    .append("save to buy a save with your points so the count survives next time.");
        }

        Mono<Void> send = message.getChannel()
                .flatMap(ch -> ch.createMessage(text.toString()))
                .then();
        // X on the wrong number
        Mono<Void> react = message.addReaction(Emoji.unicode("❌"))
                .onErrorResume(e -> Mono.empty())
                .then();
        // timeout only the first time they cross 0
        Mono<Void> mute = crossedZero ? timeout(event) : Mono.empty();
        return react.and(send).and(mute).and(applyFailRole(event, cfg));
    }

    private Mono<Void> timeout(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(member -> member.edit(GuildMemberEditSpec.create()
                        .withCommunicationDisabledUntil(java.time.Instant.now().plus(Duration.ofMinutes(5)))))
                .then()
                .onErrorResume(e -> {
                    log.warn("could not timeout member: {}", e.toString());
                    return Mono.empty();
                });
    }

    private Mono<Void> applyFailRole(MessageCreateEvent event, GuildConfig cfg) {
        if (cfg.failRoleId() == null) {
            return Mono.empty();
        }
        Snowflake roleId = Snowflake.of(cfg.failRoleId());
        int timeout = cfg.failRoleTimeout();
        return Mono.justOrEmpty(event.getMember())
                .flatMap(member -> member.addRole(roleId)
                        .onErrorResume(e -> {
                            log.warn("could not add fail role: {}", e.toString());
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> {
                    if (timeout <= 0) {
                        return Mono.empty();
                    }
                    return Mono.delay(Duration.ofSeconds(timeout))
                            .then(Mono.justOrEmpty(event.getMember()))
                            .flatMap(member -> member.removeRole(roleId)
                                    .onErrorResume(e -> Mono.empty()));
                }))
                .onErrorResume(e -> Mono.empty());
    }

    private boolean inCountingChannel(MessageCreateEvent event, GuildConfig cfg) {
        long channelId = event.getMessage().getChannelId().asLong();
        if (cfg.channelId() != null) {
            return cfg.channelId() == channelId;
        }
        return event.getMessage().getChannel()
                .map(ch -> ch instanceof GuildChannel g && "counting".equalsIgnoreCase(g.getName()))
                .blockOptional()
                .orElse(false);
    }

    private Object lock(long guildId) {
        return locks.computeIfAbsent(guildId, k -> new Object());
    }

    record Parse(int value, boolean funny, boolean wasNumber) {
    }

    static Parse parseNumber(String raw, boolean mathsDisabled) {
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        String firstLine = s.split("\\R", 2)[0].trim();
        boolean multiLineExtra = !firstLine.equals(s);

        if (mathsDisabled) {
            String[] parts = firstLine.split("\\s+", 2);
            String token = parts[0];
            if (token.matches("-?\\d+")) {
                try {
                    int value = Integer.parseInt(token);
                    boolean hasRest = parts.length > 1 && !parts[1].isBlank();
                    return new Parse(value, hasRest || multiLineExtra, true);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }

        Double whole = MathEvaluator.evaluate(firstLine);
        if (whole != null && isInt(whole)) {
            int value = whole.intValue();
            boolean funny = multiLineExtra || !firstLine.matches("-?\\d+");
            return new Parse(value, funny, true);
        }

        String[] parts = firstLine.split("\\s+", 2);
        String token = parts[0];
        Double first = MathEvaluator.evaluate(token);
        if (first != null && isInt(first)) {
            boolean hasRest = parts.length > 1 && !parts[1].isBlank();
            int value = first.intValue();
            boolean funny = hasRest || !token.matches("-?\\d+");
            return new Parse(value, funny, true);
        }
        return null;
    }

    private static boolean isInt(double v) {
        return v == Math.rint(v) && v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE;
    }
}
