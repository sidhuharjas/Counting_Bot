package com.countingbot.game;

import java.util.concurrent.ThreadLocalRandom;

// Fail roasts. 24 handwritten favorites, plus 40 x 25 x 10 = 10,000 generated
// combinations (openers x endings x tags), so 10,024 lines in total.
// Placeholders: {u} = user mention, {w} = what they typed, {e} = the expected number.
public final class FailMessages {

    private FailMessages() {
    }

    // the classic lines, kept verbatim
    private static final String[] FAILS = {
            "{u} really typed {w} when the count wanted {e}. Bad bad beta.",
            "{u} told {e} to go eat curry. {e} did not come back. Wrong number.",
            "Bro really said **{w}** to the count's face. {u} ruined it.",
            "Skill issue, {u}. The count is crying after **{w}**.",
            "{u} has the same energy as a dead calculator. Expected {e}.",
            "L + fell off + count ruined by {u} at **{w}**.",
            "{u} really said 'trust me bro' and typed **{w}**. Expected {e}.",
            "That number is now in witness protection, thanks to {u}. Expected {e}.",
            "{u} fumbled harder than an NFL wide receiver. Expected **{e}**.",
            "The count called. It wants a lawyer after what {u} did. Expected {e}.",
            "{u} counting like pineapple on pizza - just wrong. Expected {e}.",
            "{u}, you absolute unit of chaos. **{w}** was not it.",
            "{u} put the **{w}** in... actually no, {w} doesn't go anywhere. Expected {e}.",
            "ERROR 404: {e} not found. {u} served **{w}** instead.",
            "{u} really said {w} with their whole chest. Expected {e}.",
            "Nice try {u}, but the count was born yesterday. Expected **{e}**.",
            "{u} just speedran losing the count. Expected {e}.",
            "{u}'s fingers said {w} while their brain was on airplane mode. Expected {e}.",
            "The count has left the chat because of {u}. Expected **{e}**.",
            "{u} fumbled {w} like a hot potato. Expected {e}.",
            "{u} said {w}. The count said 'new number who dis'. Expected {e}.",
            "Not it, {u}. **{w}** was a social experiment. Expected {e}.",
            "{u} really hit us with the {w} on THIS count? Expected {e}.",
            "{u} chose chaos and typed **{w}**. Expected {e}."
    };

    private static final String[] OPENERS = {
            "{u} typed {w}",
            "{u} really typed {w}",
            "{u} said {w}",
            "{u} really said {w}",
            "{u} went with {w}",
            "{u} chose {w}",
            "{u} hit us with {w}",
            "{u} offered {w}",
            "{u} sent {w}",
            "{u} dropped {w}",
            "{u} thought {w} was the move",
            "{u} fumbled {w}",
            "{u} rolled with {w}",
            "{u}'s fingers went with {w}",
            "{u} presented {w}",
            "{u} decided {w} was the answer",
            "{u} said {w} like they meant it",
            "{u} risked it all on {w}",
            "{u} went ahead with {w}",
            "{u} really hit send on {w}",
            "{u} put {w} out there",
            "{u} went all in on {w}",
            "{u} answered {w}",
            "{u} locked in {w}",
            "{u} threw {w} at the count",
            "{u} really went with {w}",
            "{u} said '{w}' with their whole chest",
            "{u} confidently typed {w}",
            "{u} offered up {w}",
            "{u} picked {w}",
            "{u} went for {w}",
            "{u} really said '{w}'",
            "{u} shot the count a {w}",
            "{u} went with the classic {w}",
            "{u} trusted the vibes and typed {w}",
            "{u} took one look and typed {w}",
            "{u} sent the count a nice little {w}",
            "{u} rolled out {w}",
            "{u} presented the count with {w}",
            "{u} decided {w} was a good idea"
    };

    private static final String[] ENDINGS = {
            "when the count wanted {e}",
            "instead of {e}",
            "when {e} was right there",
            "when {e} was next",
            "when everyone was waiting for {e}",
            "and {e} was the answer",
            "with {e} right there",
            "when {e} was the plan",
            "instead of the easy {e}",
            "when {e} was sitting right there",
            "and {e} was waiting",
            "when {e} was the whole point",
            "rather than {e}",
            "with {e} so close",
            "when {e} was the assignment",
            "when {e} was literally next",
            "when the count needed {e}",
            "when {e} was the only option",
            "the count asked for {e}",
            "when {e} was coming up",
            "with {e} right around the corner",
            "when {e} was due",
            "when {e} was right here",
            "while {e} was next",
            "when {e} was one message away"
    };

    private static final String[] TAGS = {
            "Bad bad beta.",
            "Skill issue.",
            "The count is crying.",
            "That number is in witness protection.",
            "New number who dis.",
            "ERROR 404: {e} not found.",
            "Fumbled like a hot potato.",
            "Imagine typing that.",
            "The count has left the chat.",
            "Go eat curry."
    };

    private static final String[] NUMBER_ONLY_FAILS = {
            "{u} broke numbers-only mode. Only numbers (or c! commands) are allowed here. Expected **{e}**.",
            "{u} sent a non-number message in a numbers-only counting channel. Expected **{e}**.",
            "That's not a number, {u}. Expected **{e}**."
    };

    public static String random(String mention, String wrong, int expected) {
        if (wrong == null) {
            return fill(pick(NUMBER_ONLY_FAILS), mention, wrong, expected);
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // 1 in 4 uses one of the handwritten favorites
        String line = rng.nextInt(4) == 0
                ? pick(FAILS)
                : pick(OPENERS) + " " + pick(ENDINGS) + ". " + pick(TAGS);
        return fill(line, mention, wrong, expected);
    }

    // Shown after a correct count; empty string for ordinary numbers.
    public static String milestone(int number) {
        if (number % 100 == 0) {
            return "**" + number + "**! The legends keep going.";
        }
        if (number % 50 == 0) {
            return "**" + number + "** - halfway to a hundred. Keep it up.";
        }
        if (number % 25 == 0) {
            return "**" + number + "** - the count is rolling.";
        }
        return "";
    }

    private static String fill(String line, String mention, String wrong, int expected) {
        return line.replace("{u}", mention)
                .replace("{e}", String.valueOf(expected))
                .replace("{w}", wrong == null ? "" : wrong);
    }

    private static String pick(String[] arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }
}
