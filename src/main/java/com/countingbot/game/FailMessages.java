package com.countingbot.game;

import java.util.concurrent.ThreadLocalRandom;

public final class FailMessages {

    private FailMessages() {
    }

    private static final String[] FAILS = {
            "{u} really typed {w} when the count wanted {e} 💀 Bad bad beta!",
            "{u} told {e} to go eat curry 🍛 ... {e} did not come back. Wrong number!",
            "Bro really said **{w}**... to the count's face. 😭 {u} ruined it!",
            "Skill issue, {u}. The count is crying after **{w}**. 😢",
            "{u} has the same energy as a dead calculator. ☠️ Expected {e}!",
            "L + fell off + count ruined by {u} at **{w}**. 📉",
            "{u} really said 'trust me bro' and typed **{w}**. 🤡 Expected {e}!",
            "That number is now in witness protection, thanks to {u}. 🕵️ Expected {e}!",
            "{u} fumbled harder than an NFL wide receiver. 🏈 Expected **{e}**!",
            "The count called. It wants a lawyer after what {u} did. ⚖️ Expected {e}!",
            "{u} counting like pineapple on pizza - just wrong. 🍕 Expected {e}!",
            "{u} you absolute unit... of chaos. 🧱 **{w}** was not it!",
            "{u} put the **{w}** in... actually no, {w} doesn't go anywhere. Expected {e}!",
            "ERROR404: {e} not found. {u} served **{w}** instead. 📟",
            "{u} really said {w} with their whole chest. 📢 Expected {e}!",
            "Nice try {u}, but the count was born yesterday. Expected **{e}**! 🍼",
            "{u} just speedran losing the count. 🏃 Expected {e}!",
            "{u}'s fingers said {w} while their brain was on airplane mode. ✈️ Expected {e}!",
            "The count has left the chat because of {u}. Expected **{e}**! 🚪",
            "{u} fumbled {w} like it was hot potato. 🥔 Expected {e}!",
            "{u} said {w}... the count said 'new number who dis'. 📵 Expected {e}!",
            "Not it, {u}. **{w}** was a social experiment. Expected {e}! 🧪",
            "{u} really hit us with the {w}... on THIS count? 🫠 Expected {e}!",
            "{u} chose chaos and typed **{w}**. 🌶️ Expected {e}!"
    };

    private static final String[] NUMBER_ONLY_FAILS = {
            "{u} broke numbers-only mode! Only numbers (or c! commands) are allowed here. Expected **{e}**.",
            "{u} sent a non-number message in a NUMBERS-ONLY counting channel 💀 Expected **{e}**!",
            "That's not a number, {u}! Numbers-only mode means numbers only. Expected **{e}**! 🔢"
    };

    public static String random(String mention, String wrong, int expected) {
        if (wrong == null) {
            return pick(NUMBER_ONLY_FAILS)
                    .replace("{u}", mention)
                    .replace("{e}", String.valueOf(expected))
                    .replace("{w}", "");
        }
        return pick(FAILS)
                .replace("{u}", mention)
                .replace("{e}", String.valueOf(expected))
                .replace("{w}", wrong);
    }

    public static String milestone(int number) {
        if (number % 100 == 0) {
            return "🎊 **" + number + "**!" + " The legends keep going!";
        }
        if (number % 50 == 0) {
            return "🌟 **" + number + "** — half a hundred, unstoppable!";
        }
        if (number % 25 == 0) {
            return "🔥 **" + number + "** — the community is rolling!";
        }
        return "";
    }

    private static String pick(String[] arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }
}
