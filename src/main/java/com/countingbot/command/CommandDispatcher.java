package com.countingbot.command;

import com.countingbot.CountingBot;
import com.countingbot.Config;
import com.countingbot.db.Database;
import com.countingbot.db.Database.GuildConfig;
import com.countingbot.db.Database.LbEntry;
import com.countingbot.db.Database.UserGlobal;
import com.countingbot.db.Database.UserGuild;
import com.countingbot.db.Database.VoteStatus;
import com.countingbot.game.MathEvaluator;
import com.countingbot.util.Format;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.User;
import discord4j.rest.util.Color;
import discord4j.rest.util.Permission;
import discord4j.core.spec.EmbedCreateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);
    private static final Pattern USER_MENTION = Pattern.compile("^<@!?(\\d+)>");
    private static final Pattern ROLE_MENTION = Pattern.compile("^<@&(\\d+)>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("^<#(\\d+)>");
    private static final Pattern RAW_ID = Pattern.compile("^\\d{17,20}$");
    private static final int PAGE_SIZE = 10;

    private final Database db;
    private final Config config;

    public CommandDispatcher(Database db, Config config) {
        this.db = db;
        this.config = config;
    }

    public Mono<Void> dispatch(MessageCreateEvent event, String raw) {
        String body = raw.substring(config.prefix().length()).trim();
        if (body.isEmpty()) {
            return reply(event, "Try `" + config.prefix() + "help` for the command list.");
        }
        String[] parts = body.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        try {
            return switch (cmd) {
                case "help" -> help(event);
                case "vote" -> vote(event);
                case "calc" -> calc(event, body);
                case "stats" -> stats(event);
                case "cs" -> leaderboard(event, args, "cs");
                case "lb" -> leaderboard(event, args, "lb");
                case "tlb" -> leaderboard(event, args, "tlb");
                case "ulb" -> leaderboard(event, args, "ulb");
                case "slb" -> leaderboard(event, args, "slb");
                case "user" -> user(event, args);
                case "server" -> server(event);
                case "donatesave" -> donateSave(event);
                case "transfersave" -> transferSave(event, args);
                case "ping" -> reply(event, "🏓 Pong! Counting is alive.");
                case "channel" -> admin(event, args, this::channelCommand);
                case "failrole" -> admin(event, args, this::failRoleCommand);
                case "disablemaths" -> admin(event, args, this::disableMathsCommand);
                case "numbersonly" -> admin(event, args, this::numbersOnlyCommand);
                case "language" -> admin(event, args, this::languageCommand);
                case "givesave" -> admin(event, args, this::giveSaveCommand);
                default -> reply(event, "Unknown command. Try `" + config.prefix() + "help`.");
            };
        } catch (Exception e) {
            log.error("command {} failed", cmd, e);
            return reply(event, "Something went wrong running that command. Please try again.");
        }
    }

    private Mono<Void> admin(MessageCreateEvent event, String[] args,
                             java.util.function.BiFunction<MessageCreateEvent, String[], Mono<Void>> handler) {
        return isAdmin(event).flatMap(ok -> {
            if (!ok) {
                return reply(event, "You need the **Administrator** permission to use that command.");
            }
            return handler.apply(event, args);
        });
    }

    private Mono<Boolean> isAdmin(MessageCreateEvent event) {
        return Mono.justOrEmpty(event.getMember())
                .flatMap(Member::getBasePermissions)
                .map(p -> p.contains(Permission.ADMINISTRATOR))
                .defaultIfEmpty(false);
    }

    private Mono<Long> requireGuild(MessageCreateEvent event) {
        java.util.Optional<discord4j.common.util.Snowflake> guildId = event.getGuildId();
        if (guildId.isEmpty()) {
            reply(event, "That command only works inside a server.").subscribe();
            return Mono.empty();
        }
        return Mono.just(guildId.get().asLong());
    }

    // ---------- user commands ----------

    private Mono<Void> help(MessageCreateEvent event) {
        String p = config.prefix();
        String desc = """
                **Setup**
                Create (or pick) a counting channel and type `{p}channel` there with Administrator permission.
                Start counting from **1**. One person can't count two numbers in a row.

                **Counting rules & points**
                • Each correct number = **+1 point**.
                • Post a number *with something funny* → everyone rates it 1️⃣–5️⃣ for up to **+5 bonus points**.
                • Wrong number = funny roast **and −2 points**.
                • Break the count without a save → the count resets to **1**.

                **Saves**
                • `{p}vote` — earn saves (0.5 per vote, max 3).
                • A save is spent automatically when someone ruins the count, so the count survives.

                **User commands** (`?` = optional)
                • `{p}cs ?page` — current run scores
                • `{p}lb ?page` — top high scores (biggest number counted)
                • `{p}tlb ?page` — top total scores (counts landed)
                • `{p}ulb ?page` — top users by points (global)
                • `{p}slb ?page` — top users in this server
                • `{p}user ?@user` — a user's stats
                • `{p}server` — this server's stats
                • `{p}calc <expr>` — evaluate maths (e.g. `2^7+1`)
                • `{p}vote` — vote for saves
                • `{p}donatesave` — donate 1 save to the server bank (adds 0.2)
                • `{p}transfersave @user` — gift a save (0.5 tax)
                • `{p}stats` — bot statistics
                • `{p}ping` — health check

                **Admin commands** (Administrator)
                • `{p}channel ?#channel|off` — set/clear the counting channel
                • `{p}failrole @role ?seconds` — role given on a ruined count (bare = off)
                • `{p}disablemaths on|off` — block maths like `1+1`
                • `{p}numbersonly on|off` — any non-number message ruins the count
                • `{p}language <code>` — bot language (English for now)
                • `{p}givesave @user ?amount` — grant saves
                """.replace("{p}", p);

        return replyEmbed(event, EmbedCreateSpec.builder()
                .title("📖 counting — help")
                .description(desc)
                .color(Color.of(0x5865F2))
                .footer("Join our Discord for questions • Invite the bot to your server", null)
                .build());
    }

    private Mono<Void> vote(MessageCreateEvent event) {
        long userId = authorId(event);
        VoteStatus status = db.claimVote(userId);
        String url = config.topggUrl();

        StringBuilder desc = new StringBuilder();
        desc.append("Vote on top.gg to earn **").append(Format.saves(Database.VOTE_AMOUNT))
                .append("** saves.\n");
        desc.append("You currently have **").append(Format.saves(status.saves()))
                .append('/').append(Format.saves(Database.MAX_SAVES)).append("** saves.\n");

        if (status.granted()) {
            desc.append("✅ Vote counted! **+").append(Format.saves(Database.VOTE_AMOUNT))
                    .append(" saves.** You can vote again in ")
                    .append(Format.cooldown(status.nextInMs())).append(".\n")
                    .append("👉 [Vote here](").append(url).append(')');
        } else if (status.atCap() && status.nextInMs() == 0) {
            desc.append("⚠️ You're maxed out at **").append(Format.saves(Database.MAX_SAVES))
                    .append("** saves — spend some first!");
        } else {
            desc.append("❌ You have already voted. You can vote again in ")
                    .append(Format.cooldown(status.nextInMs())).append(".\n")
                    .append("👉 [Vote here](").append(url).append(')');
        }

        return replyEmbed(event, EmbedCreateSpec.builder()
                .title("🗳️ Vote for counting to earn saves")
                .description(desc.toString())
                .color(status.granted() ? Color.of(0x57F287) : Color.of(0xFEE75C))
                .footer("Saves protect the count when someone ruins it.", null)
                .build());
    }

    private Mono<Void> calc(MessageCreateEvent event, String body) {
        String expr = body.length() > 4 ? body.substring(4).trim() : "";
        if (expr.isEmpty()) {
            return reply(event, "Usage: `" + config.prefix() + "calc 2^7+1`");
        }
        Double value = MathEvaluator.evaluate(expr);
        if (value == null) {
            return reply(event, "❌ `" + expr + "` isn't a valid expression. Supported: `+ - * / % ^ ( )`");
        }
        String rendered = value == Math.rint(value)
                ? String.valueOf((long) (double) value)
                : String.valueOf(value);
        return reply(event, "🧮 `" + expr + "` = **" + rendered + "**");
    }

    private Mono<Void> stats(MessageCreateEvent event) {
        int guilds = db.countGuilds();
        int users = db.countUsers();
        long counts = db.sumTotalCorrect();
        long uptime = System.currentTimeMillis() - CountingBot.startedAt;
        return replyEmbed(event, EmbedCreateSpec.builder()
                .title("📊 counting — statistics")
                .addField("Guilds", Format.commas(guilds), true)
                .addField("Users", Format.commas(users), true)
                .addField("Counts landed", Format.commas(counts), true)
                .addField("Uptime", Format.uptime(uptime), true)
                .addField("Shard", "1 / 1", true)
                .addField("Counted with", "Discord4J (Java)", true)
                .color(Color.of(0x5865F2))
                .build());
    }

    private Mono<Void> leaderboard(MessageCreateEvent event, String[] args, String type) {
        int page = page(args);
        int offset = (page - 1) * PAGE_SIZE;
        String prefix = config.prefix();

        String title;
        String valueSuffix;
        Color color;
        List<LbEntry> entries;

        Optional<Long> guildId = event.getGuildId().map(id -> id.asLong());
        switch (type) {
            case "cs" -> {
                if (guildId.isEmpty()) return reply(event, "Run this in a server.");
                title = "🏁 Current scores";
                valueSuffix = " pts this run";
                color = Color.of(0x57F287);
                entries = db.lbCurrentRun(guildId.get(), PAGE_SIZE, offset);
            }
            case "lb" -> {
                title = "🏆 High scores";
                valueSuffix = " highest counted";
                color = Color.of(0xFEE75C);
                entries = db.lbHigh(PAGE_SIZE, offset);
            }
            case "tlb" -> {
                title = "📈 Total scores";
                valueSuffix = " counts";
                color = Color.of(0x5865F2);
                entries = db.lbTotal(PAGE_SIZE, offset);
            }
            case "ulb" -> {
                title = "👑 Top users";
                valueSuffix = " pts";
                color = Color.of(0xEB459E);
                entries = db.lbPoints(PAGE_SIZE, offset);
            }
            case "slb" -> {
                if (guildId.isEmpty()) return reply(event, "Run this in a server.");
                title = "🏠 Top users in this server";
                valueSuffix = " pts";
                color = Color.of(0x5865F2);
                entries = db.lbGuildPoints(guildId.get(), PAGE_SIZE, offset);
            }
            default -> {
                return reply(event, "Unknown leaderboard.");
            }
        }

        if (entries.isEmpty()) {
            return reply(event, "No scores on page " + page + " yet. Start counting!");
        }

        StringBuilder desc = new StringBuilder();
        int rank = offset + 1;
        for (LbEntry entry : entries) {
            desc.append('`').append(rank++).append(".` <@").append(entry.userId())
                    .append("> — **").append(Format.points(entry.value()))
                    .append("**").append(valueSuffix).append('\n');
        }

        return replyEmbed(event, EmbedCreateSpec.builder()
                .title(title)
                .description(desc.toString())
                .color(color)
                .footer("Page " + page + " • " + prefix + type + " " + (page + 1) + " for next page", null)
                .build());
    }

    private Mono<Void> user(MessageCreateEvent event, String[] args) {
        long targetId = resolveUser(event, args).orElse(authorId(event));
        UserGlobal global = db.getUserGlobal(targetId);
        int high = db.getHighScore(targetId);

        EmbedCreateSpec.Builder embed = EmbedCreateSpec.builder()
                .title("👤 User stats")
                .description("Stats for <@" + targetId + ">")
                .color(Color.of(0x5865F2))
                .addField("Points", Format.points(global.points()), true)
                .addField("Funny bonus", Format.points(global.funnyPoints()), true)
                .addField("Saves", Format.saves(global.saves()) + "/" + Format.saves(Database.MAX_SAVES), true)
                .addField("High score", Format.commas(high) + " counted", true)
                .addField("Counts landed", Format.commas(global.totalCorrect()), true);

        Optional<Long> guildId = event.getGuildId().map(id -> id.asLong());
        if (guildId.isPresent()) {
            UserGuild guildRow = db.getUserGuild(targetId, guildId.get());
            embed.addField("Points here", Format.points(guildRow.guildPoints()), true);
            embed.addField("Counts here", Format.commas(guildRow.guildCorrect()), true);
            embed.addField("This run", Format.points(guildRow.runPoints()), true);
        }
        return replyEmbed(event, embed.build());
    }

    private Mono<Void> server(MessageCreateEvent event) {
        return requireGuild(event).flatMap(guildId -> {
            GuildConfig cfg = db.getOrCreateGuild(guildId);
            String channel = cfg.channelId() == null
                    ? "#counting (default)"
                    : "<#" + cfg.channelId() + ">";
            int next = cfg.currentCount() + 1;
            return replyEmbed(event, EmbedCreateSpec.builder()
                    .title("🏠 Server stats")
                    .color(Color.of(0x5865F2))
                    .addField("Counting channel", channel, true)
                    .addField("Next number", Format.commas(next), true)
                    .addField("Highest count", Format.commas(cfg.highestCount()), true)
                    .addField("Counts landed", Format.commas(cfg.totalCorrect()), true)
                    .addField("Active counters", Format.commas(db.activeCounters(guildId)), true)
                    .addField("Save bank", Format.saves(cfg.guildSaves()) + " saves", true)
                    .addField("Numbers-only", cfg.numbersOnly() ? "On" : "Off", true)
                    .addField("Maths", cfg.disableMaths() ? "Disabled" : "Enabled", true)
                    .build());
        });
    }

    private Mono<Void> donateSave(MessageCreateEvent event) {
        return requireGuild(event).flatMap(guildId -> {
            long userId = authorId(event);
            if (db.donateSave(userId, guildId)) {
                double bank = db.getGuildSaves(guildId);
                return reply(event, "❤️ Thank you! Donated **1 save** — the server save bank now has **"
                        + Format.saves(bank) + "** (each save adds 0.2).");
            }
            return reply(event, "You need at least **1 save** to donate (you have **"
                    + Format.saves(db.getSaves(userId)) + "**). Use `" + config.prefix() + "vote` to earn some.");
        });
    }

    private Mono<Void> transferSave(MessageCreateEvent event, String[] args) {
        Optional<Long> target = resolveUser(event, args);
        if (target.isEmpty()) {
            return reply(event, "Usage: `" + config.prefix() + "transfersave @user`");
        }
        long from = authorId(event);
        long to = target.get();
        if (to == from) {
            return reply(event, "You can't transfer a save to yourself.");
        }
        if (db.transferSave(from, to)) {
            return reply(event, "🎁 Sent a save to <@" + to + "> (0.5 tax — they received 0.5).");
        }
        return reply(event, "You need at least **1 save** to transfer (you have **"
                + Format.saves(db.getSaves(from)) + "**).");
    }

    // ---------- admin commands ----------

    private Mono<Void> channelCommand(MessageCreateEvent event, String[] args) {
        return requireGuild(event).flatMap(guildId -> {
            if (args.length == 0) {
                long channelId = event.getMessage().getChannelId().asLong();
                db.setChannel(guildId, channelId);
                return reply(event, "✅ Counting channel set to <#" + channelId
                        + ">. Start counting at **1**!");
            }
            String arg = args[0];
            if (arg.equalsIgnoreCase("off") || arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("disable")) {
                db.setChannel(guildId, null);
                return reply(event, "✅ Counting channel cleared — I'll listen in a channel named #counting again.");
            }
            Matcher m = CHANNEL_MENTION.matcher(arg);
            if (m.matches()) {
                long channelId = Long.parseLong(m.group(1));
                db.setChannel(guildId, channelId);
                return reply(event, "✅ Counting channel set to <#" + channelId + ">.");
            }
            if (RAW_ID.matcher(arg).matches()) {
                long channelId = Long.parseLong(arg);
                db.setChannel(guildId, channelId);
                return reply(event, "✅ Counting channel set to <#" + channelId + ">.");
            }
            return reply(event, "Usage: type `" + config.prefix() + "channel` in the counting channel, or `"
                    + config.prefix() + "channel off`.");
        });
    }

    private Mono<Void> failRoleCommand(MessageCreateEvent event, String[] args) {
        return requireGuild(event).flatMap(guildId -> {
            if (args.length == 0) {
                db.setFailRole(guildId, null, 0);
                return reply(event, "✅ Fail role disabled.");
            }
            Matcher m = ROLE_MENTION.matcher(args[0]);
            if (!m.matches() && !RAW_ID.matcher(args[0]).matches()) {
                return reply(event, "Usage: `" + config.prefix() + "failrole @role ?seconds` (no arguments disables it)");
            }
            long roleId = Long.parseLong(m.matches() ? m.group(1) : args[0]);
            int timeout = 0;
            if (args.length > 1) {
                try {
                    timeout = Math.max(0, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    return reply(event, "Timeout must be a whole number of seconds.");
                }
            }
            db.setFailRole(guildId, roleId, timeout);
            String timeoutText = timeout == 0
                    ? "permanent until removed"
                    : "removed after " + Format.cooldown(timeout * 1000L);
            return reply(event, "✅ Fail role set to <@&" + roleId + "> (" + timeoutText + ").");
        });
    }

    private Mono<Void> disableMathsCommand(MessageCreateEvent event, String[] args) {
        return toggleCommand(event, args, "disablemaths", "maths counting (e.g. `1+1`)", db::setDisableMaths);
    }

    private Mono<Void> numbersOnlyCommand(MessageCreateEvent event, String[] args) {
        return toggleCommand(event, args, "numbersonly", "numbers-only mode", db::setNumbersOnly);
    }

    private Mono<Void> toggleCommand(MessageCreateEvent event, String[] args, String name, String label,
                                     java.util.function.BiConsumer<Long, Boolean> setter) {
        return requireGuild(event).flatMap(guildId -> {
            Boolean value = parseToggle(args);
            if (value == null) {
                return reply(event, "Usage: `" + config.prefix() + name + " on|off`");
            }
            setter.accept(guildId, value);
            return reply(event, "✅ " + label + " is now **" + (value ? "on" : "off") + "**.");
        });
    }

    private Boolean parseToggle(String[] args) {
        if (args.length == 0) return null;
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "enable", "enabled" -> Boolean.TRUE;
            case "off", "false", "no", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    private Mono<Void> languageCommand(MessageCreateEvent event, String[] args) {
        return requireGuild(event).flatMap(guildId -> {
            if (args.length == 0) {
                return reply(event, "Usage: `" + config.prefix() + "language <code>` e.g. `en`, `es`");
            }
            String code = args[0].toLowerCase(Locale.ROOT);
            db.setLanguage(guildId, code);
            return reply(event, "✅ Language set to `" + code + "`. (English is the only translation for now — contributions welcome!)");
        });
    }

    private Mono<Void> giveSaveCommand(MessageCreateEvent event, String[] args) {
        Optional<Long> target = resolveUser(event, args);
        if (target.isEmpty()) {
            return reply(event, "Usage: `" + config.prefix() + "givesave @user ?amount`");
        }
        double amount = 1;
        if (args.length > 1) {
            try {
                amount = Double.parseDouble(args[args.length - 1]);
            } catch (NumberFormatException ignored) {
                // amount not provided as a number
            }
            if (amount <= 0) amount = 1;
        }
        long userId = target.get();
        db.addSaves(userId, amount);
        return reply(event, "✅ Gave **" + Format.saves(amount) + "** save(s) to <@" + userId
                + "> — they now have **" + Format.saves(db.getSaves(userId)) + "/"
                + Format.saves(Database.MAX_SAVES) + "**.");
    }

    // ---------- helpers ----------

    private int page(String[] args) {
        if (args.length == 0) return 1;
        try {
            return Math.max(1, Integer.parseInt(args[0]));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private long authorId(MessageCreateEvent event) {
        return event.getMessage().getAuthor().map(u -> u.getId().asLong()).orElse(-1L);
    }

    private Optional<Long> resolveUser(MessageCreateEvent event, String[] args) {
        if (args.length > 0) {
            Matcher m = USER_MENTION.matcher(args[0]);
            if (m.matches()) {
                return Optional.of(Long.parseLong(m.group(1)));
            }
            if (RAW_ID.matcher(args[0]).matches()) {
                return Optional.of(Long.parseLong(args[0]));
            }
        }
        return event.getMessage().getUserMentions().stream()
                .findFirst()
                .map(User::getId)
                .map(id -> id.asLong());
    }

    private Mono<Void> reply(MessageCreateEvent event, String text) {
        return event.getMessage().getChannel()
                .flatMap(ch -> ch.createMessage(text))
                .then();
    }

    private Mono<Void> replyEmbed(MessageCreateEvent event, EmbedCreateSpec embed) {
        return event.getMessage().getChannel()
                .flatMap(ch -> ch.createMessage(embed))
                .then();
    }
}
