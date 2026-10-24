package com.countingbot.game;

import com.countingbot.db.Database;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class FunnyRegistry {

    public record Completed(long counterId, double bonus) {
    }

    private static final class Pending {
        final long guildId;
        final long counterId;
        final long createdAt = System.currentTimeMillis();
        final Map<Long, Integer> ratings = new ConcurrentHashMap<>();
        double applied;

        Pending(long guildId, long counterId) {
            this.guildId = guildId;
            this.counterId = counterId;
        }
    }

    private static final long EVICT_MS = 2L * 60L * 60L * 1000L;
    private static final double MAX_BONUS = 5;

    private final Database db;
    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastFunnyByGuild = new ConcurrentHashMap<>();

    public FunnyRegistry(Database db) {
        this.db = db;
    }

    public void register(long messageId, long guildId, long counterId) {
        evictOld();
        pending.put(messageId, new Pending(guildId, counterId));
        lastFunnyByGuild.put(guildId, messageId);
    }

    public Long counterOf(long messageId) {
        Pending p = pending.get(messageId);
        return p == null ? null : p.counterId;
    }

    public boolean rate(long messageId, long raterId, int rating) {
        Pending p = pending.get(messageId);
        if (p == null) {
            return false;
        }
        p.ratings.put(raterId, rating);
        double avg = p.ratings.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double bonus = Math.min(MAX_BONUS, Math.round(avg));
        double delta = bonus - p.applied;
        if (delta != 0) {
            p.applied = bonus;
            db.addPoints(p.counterId, p.guildId, delta, true);
        }
        return true;
    }

    public Optional<Completed> completeForGuild(long guildId) {
        Long messageId = lastFunnyByGuild.remove(guildId);
        if (messageId == null) {
            return Optional.empty();
        }
        Pending p = pending.remove(messageId);
        if (p == null) {
            return Optional.empty();
        }
        return Optional.of(new Completed(p.counterId, p.applied));
    }

    private void evictOld() {
        long cutoff = System.currentTimeMillis() - EVICT_MS;
        pending.entrySet().removeIf(e -> e.getValue().createdAt < cutoff);
    }
}
