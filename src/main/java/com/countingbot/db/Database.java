package com.countingbot.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Database implements AutoCloseable {

    public record GuildConfig(long guildId, Long channelId, int currentCount, Long lastUserId,
                              Long failRoleId, int failRoleTimeout, boolean disableMaths,
                              boolean numbersOnly, String language, double guildSaves,
                              int highestCount, int totalCorrect) {
    }

    public record UserGlobal(long userId, double saves, double points, int totalCorrect,
                             double funnyPoints) {
    }

    public record UserGuild(long userId, long guildId, double guildPoints, double runPoints,
                            int highScore, int guildCorrect) {
    }

    public record LbEntry(long userId, double value) {
    }

    public enum BuyResult {
        BOUGHT, AT_CAP, NO_POINTS
    }

    public static final double MAX_SAVES = 3.0;

    private final Connection conn;

    public Database(Path file) throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS guilds (
                        guild_id          TEXT PRIMARY KEY,
                        channel_id        TEXT,
                        current_count     INTEGER NOT NULL DEFAULT 0,
                        last_user_id      TEXT,
                        fail_role_id      TEXT,
                        fail_role_timeout INTEGER NOT NULL DEFAULT 0,
                        disable_maths     INTEGER NOT NULL DEFAULT 0,
                        numbers_only      INTEGER NOT NULL DEFAULT 0,
                        language          TEXT NOT NULL DEFAULT 'en',
                        guild_saves       REAL NOT NULL DEFAULT 0,
                        highest_count     INTEGER NOT NULL DEFAULT 0,
                        total_correct     INTEGER NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS user_global (
                        user_id         TEXT PRIMARY KEY,
                        saves           REAL NOT NULL DEFAULT 0,
                        points          REAL NOT NULL DEFAULT 0,
                        total_correct   INTEGER NOT NULL DEFAULT 0,
                        funny_points    REAL NOT NULL DEFAULT 0
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS user_guild (
                        user_id       TEXT NOT NULL,
                        guild_id      TEXT NOT NULL,
                        guild_points  REAL NOT NULL DEFAULT 0,
                        run_points    REAL NOT NULL DEFAULT 0,
                        high_score    INTEGER NOT NULL DEFAULT 0,
                        guild_correct INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (user_id, guild_id)
                    )""");
        }
    }

    private static String id(long value) {
        return String.valueOf(value);
    }

    private void ensureGuild(long guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO guilds (guild_id) VALUES (?)")) {
            ps.setString(1, id(guildId));
            ps.executeUpdate();
        }
    }

    private void ensureUser(long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO user_global (user_id) VALUES (?)")) {
            ps.setString(1, id(userId));
            ps.executeUpdate();
        }
    }

    private void ensureUserGuild(long userId, long guildId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO user_guild (user_id, guild_id) VALUES (?, ?)")) {
            ps.setString(1, id(userId));
            ps.setString(2, id(guildId));
            ps.executeUpdate();
        }
    }

    private static GuildConfig readGuild(ResultSet rs) throws SQLException {
        long guildId = Long.parseLong(rs.getString("guild_id"));
        String channelId = rs.getString("channel_id");
        String lastUserId = rs.getString("last_user_id");
        String failRoleId = rs.getString("fail_role_id");
        return new GuildConfig(
                guildId,
                channelId == null ? null : Long.parseLong(channelId),
                rs.getInt("current_count"),
                lastUserId == null ? null : Long.parseLong(lastUserId),
                failRoleId == null ? null : Long.parseLong(failRoleId),
                rs.getInt("fail_role_timeout"),
                rs.getInt("disable_maths") != 0,
                rs.getInt("numbers_only") != 0,
                rs.getString("language"),
                rs.getDouble("guild_saves"),
                rs.getInt("highest_count"),
                rs.getInt("total_correct")
        );
    }

    public synchronized GuildConfig getOrCreateGuild(long guildId) {
        try {
            ensureGuild(guildId);
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds WHERE guild_id = ?")) {
                ps.setString(1, id(guildId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return readGuild(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void setChannel(long guildId, Long channelId) {
        update("UPDATE guilds SET channel_id = ? WHERE guild_id = ?",
                channelId == null ? null : id(channelId), id(guildId));
    }

    public synchronized void setFailRole(long guildId, Long roleId, int timeoutSeconds) {
        update("UPDATE guilds SET fail_role_id = ?, fail_role_timeout = ? WHERE guild_id = ?",
                roleId == null ? null : id(roleId), timeoutSeconds, id(guildId));
    }

    public synchronized void setDisableMaths(long guildId, boolean value) {
        update("UPDATE guilds SET disable_maths = ? WHERE guild_id = ?", value ? 1 : 0, id(guildId));
    }

    public synchronized void setNumbersOnly(long guildId, boolean value) {
        update("UPDATE guilds SET numbers_only = ? WHERE guild_id = ?", value ? 1 : 0, id(guildId));
    }

    public synchronized void setLanguage(long guildId, String language) {
        update("UPDATE guilds SET language = ? WHERE guild_id = ?", language, id(guildId));
    }

    public synchronized void advance(long guildId, long userId, int number) {
        try {
            ensureGuild(guildId);
            ensureUser(userId);
            update("UPDATE guilds SET current_count = ?, last_user_id = ?, "
                            + "highest_count = MAX(highest_count, ?), total_correct = total_correct + 1 "
                            + "WHERE guild_id = ?",
                    number, id(userId), number, id(guildId));
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO user_guild (user_id, guild_id, guild_points, run_points, high_score, guild_correct)
                    VALUES (?, ?, 1, 1, ?, 1)
                    ON CONFLICT(user_id, guild_id) DO UPDATE SET
                        guild_points  = guild_points + 1,
                        run_points    = run_points + 1,
                        high_score    = MAX(high_score, excluded.high_score),
                        guild_correct = guild_correct + 1""")) {
                ps.setString(1, id(userId));
                ps.setString(2, id(guildId));
                ps.setInt(3, number);
                ps.executeUpdate();
            }
            update("UPDATE user_global SET points = points + 1, total_correct = total_correct + 1 WHERE user_id = ?",
                    id(userId));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void setLastUser(long guildId, Long userId) {
        update("UPDATE guilds SET last_user_id = ? WHERE guild_id = ?",
                userId == null ? null : id(userId), id(guildId));
    }

    public synchronized void resetCount(long guildId) {
        update("UPDATE guilds SET current_count = 0, last_user_id = NULL WHERE guild_id = ?", id(guildId));
        update("UPDATE user_guild SET run_points = 0 WHERE guild_id = ?", id(guildId));
    }

    public synchronized boolean consumeUserSave(long userId) {
        try {
            ensureUser(userId);
            double saves = getSaves(userId);
            if (saves < 1) {
                return false;
            }
            update("UPDATE user_global SET saves = saves - 1 WHERE user_id = ?", id(userId));
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean consumeGuildSave(long guildId) {
        try {
            ensureGuild(guildId);
            double saves = getGuildSaves(guildId);
            if (saves < 1) {
                return false;
            }
            update("UPDATE guilds SET guild_saves = guild_saves - 1 WHERE guild_id = ?", id(guildId));
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void addPoints(long userId, Long guildId, double delta, boolean funny) {
        try {
            if (guildId != null) {
                ensureUserGuild(userId, guildId);
                update("UPDATE user_guild SET guild_points = MAX(0, guild_points + ?) "
                                + "WHERE user_id = ? AND guild_id = ?",
                        delta, id(userId), id(guildId));
            }
            ensureUser(userId);
            update("UPDATE user_global SET points = MAX(0, points + ?) WHERE user_id = ?",
                    delta, id(userId));
            if (funny) {
                update("UPDATE user_global SET funny_points = MAX(0, funny_points + ?) WHERE user_id = ?",
                        delta, id(userId));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized double getSaves(long userId) {
        return getUserGlobal(userId).saves();
    }

    public synchronized double getGuildSaves(long guildId) {
        try {
            ensureGuild(guildId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT guild_saves FROM guilds WHERE guild_id = ?")) {
                ps.setString(1, id(guildId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void addSaves(long userId, double amount) {
        try {
            ensureUser(userId);
            update("UPDATE user_global SET saves = MIN(?, MAX(0, saves + ?)) WHERE user_id = ?",
                    MAX_SAVES, amount, id(userId));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean donateSave(long userId, long guildId) {
        try {
            ensureUser(userId);
            ensureGuild(guildId);
            if (getSaves(userId) < 1) {
                return false;
            }
            update("UPDATE user_global SET saves = saves - 1 WHERE user_id = ?", id(userId));
            update("UPDATE guilds SET guild_saves = ROUND(guild_saves + 0.2, 2) WHERE guild_id = ?", id(guildId));
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean transferSave(long fromUserId, long toUserId) {
        try {
            ensureUser(fromUserId);
            ensureUser(toUserId);
            if (getSaves(fromUserId) < 1) {
                return false;
            }
            update("UPDATE user_global SET saves = saves - 1 WHERE user_id = ?", id(fromUserId));
            update("UPDATE user_global SET saves = MIN(?, saves + 0.5) WHERE user_id = ?",
                    MAX_SAVES, id(toUserId));
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized BuyResult buySave(long userId, Long guildId, double cost) {
        UserGlobal user = getUserGlobal(userId);
        if (user.saves() >= MAX_SAVES) {
            return BuyResult.AT_CAP;
        }
        if (user.points() < cost) {
            return BuyResult.NO_POINTS;
        }
        addPoints(userId, guildId, -cost, false);
        addSaves(userId, 1);
        return BuyResult.BOUGHT;
    }

    public synchronized UserGlobal getUserGlobal(long userId) {
        try {
            ensureUser(userId);
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM user_global WHERE user_id = ?")) {
                ps.setString(1, id(userId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new UserGlobal(
                            userId,
                            rs.getDouble("saves"),
                            rs.getDouble("points"),
                            rs.getInt("total_correct"),
                            rs.getDouble("funny_points")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized UserGuild getUserGuild(long userId, long guildId) {
        try {
            ensureUserGuild(userId, guildId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM user_guild WHERE user_id = ? AND guild_id = ?")) {
                ps.setString(1, id(userId));
                ps.setString(2, id(guildId));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new UserGuild(
                            userId,
                            guildId,
                            rs.getDouble("guild_points"),
                            rs.getDouble("run_points"),
                            rs.getInt("high_score"),
                            rs.getInt("guild_correct")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized int getHighScore(long userId) {
        return queryInt("SELECT COALESCE(MAX(high_score), 0) FROM user_guild WHERE user_id = ?", id(userId));
    }

    public synchronized List<LbEntry> lbCurrentRun(long guildId, int limit, int offset) {
        return lb("SELECT user_id, run_points AS v FROM user_guild WHERE guild_id = ? AND run_points > 0 "
                + "ORDER BY v DESC, user_id ASC LIMIT ? OFFSET ?", limit, offset, id(guildId));
    }

    public synchronized List<LbEntry> lbHigh(int limit, int offset) {
        return lb("SELECT user_id, MAX(high_score) AS v FROM user_guild GROUP BY user_id "
                + "ORDER BY v DESC, user_id ASC LIMIT ? OFFSET ?", limit, offset);
    }

    public synchronized List<LbEntry> lbTotal(int limit, int offset) {
        return lb("SELECT user_id, total_correct AS v FROM user_global WHERE total_correct > 0 "
                + "ORDER BY v DESC, user_id ASC LIMIT ? OFFSET ?", limit, offset);
    }

    public synchronized List<LbEntry> lbPoints(int limit, int offset) {
        return lb("SELECT user_id, points AS v FROM user_global WHERE points > 0 "
                + "ORDER BY v DESC, user_id ASC LIMIT ? OFFSET ?", limit, offset);
    }

    public synchronized List<LbEntry> lbGuildPoints(long guildId, int limit, int offset) {
        return lb("SELECT user_id, guild_points AS v FROM user_guild WHERE guild_id = ? AND guild_points > 0 "
                + "ORDER BY v DESC, user_id ASC LIMIT ? OFFSET ?", limit, offset, id(guildId));
    }

    public synchronized int countGuilds() {
        return queryInt("SELECT COUNT(*) FROM guilds");
    }

    public synchronized int countUsers() {
        return queryInt("SELECT COUNT(*) FROM user_global");
    }

    public synchronized long sumTotalCorrect() {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(total_correct), 0) FROM user_global");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized int activeCounters(long guildId) {
        return queryInt("SELECT COUNT(DISTINCT user_id) FROM user_guild WHERE guild_id = ?", id(guildId));
    }

    private synchronized void update(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindAll(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void bindAll(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else if (p instanceof Integer n) {
                ps.setInt(i + 1, n);
            } else if (p instanceof Long n) {
                ps.setLong(i + 1, n);
            } else if (p instanceof Double n) {
                ps.setDouble(i + 1, n);
            } else {
                ps.setString(i + 1, String.valueOf(p));
            }
        }
    }

    private synchronized int queryInt(String sql, String... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized List<LbEntry> lb(String sql, int limit, int offset, String... params) {
        List<LbEntry> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String p : params) {
                ps.setString(idx++, p);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LbEntry(Long.parseLong(rs.getString(1)), rs.getDouble(2)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        conn.close();
    }
}
