package com.countingbot.listener;

import com.countingbot.game.FunnyRegistry;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.ReactionAddEvent;
import discord4j.core.object.emoji.UnicodeEmoji;
import discord4j.core.object.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public final class ReactionListener {

    private static final Logger log = LoggerFactory.getLogger(ReactionListener.class);

    private final FunnyRegistry registry;

    public ReactionListener(FunnyRegistry registry) {
        this.registry = registry;
    }

    public void register(GatewayDiscordClient client) {
        client.on(ReactionAddEvent.class)
                .flatMap(event -> Mono.defer(() -> handle(event))
                        .onErrorResume(err -> {
                            log.error("reaction handler failed", err);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    private Mono<Void> handle(ReactionAddEvent event) {
        Integer rating = keycapRating(event.getEmoji().asUnicodeEmoji().map(UnicodeEmoji::getRaw).orElse(""));
        if (rating == null) {
            return Mono.empty();
        }
        long messageId = event.getMessageId().asLong();
        Long counterId = registry.counterOf(messageId);
        if (counterId == null) {
            return Mono.empty();
        }
        long raterId = event.getUserId().asLong();
        if (raterId == counterId) {
            return Mono.empty();
        }
        return event.getUser()
                .map(User::isBot)
                .defaultIfEmpty(false)
                .flatMap(isBot -> {
                    if (!isBot) {
                        registry.rate(messageId, raterId, rating);
                    }
                    return Mono.empty();
                })
                .then();
    }

    static Integer keycapRating(String raw) {
        String s = raw.replace("\ufe0f", "");
        if (s.length() == 2 && s.charAt(1) == '\u20e3' && s.charAt(0) >= '1' && s.charAt(0) <= '5') {
            return s.charAt(0) - '0';
        }
        return null;
    }
}
