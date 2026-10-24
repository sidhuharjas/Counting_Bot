# counting

A Discord counting bot written in Java 21 with [Discord4J](https://discord4j.com). Same game as the classic counting bots, plus points and funny fail messages.

Everyone counts up together, one number per person, no repeats. A correct number is +1 point. If you add something funny to the count, the server rates it 1 to 5 for up to +5 extra points. A wrong number gets you a roast and -2 points.

## Features

**The game**
- One counting channel per server (default: any channel named `#counting`, or set with `c!channel`)
- One person can't count two numbers in a row
- Maths works: `2^7+1` is a valid 129 (turn off with `c!disablemaths on`)
- Numbers-only mode: any non-number message ruins the count (`c!numbersonly on`)
- Milestone callouts every 25, 50 and 100

**Points**
- +1 point for every correct number
- Funny extra text or a maths expression on the count? Everyone reacts with 1 to 5; the average (rounded) is up to +5 extra points. Ratings can be changed until the next number
- Wrong number: a random roast (24 of them, like "bad bad beta" and "go eat curry") and -2 points
- Points tracked globally and per server, with run, high score and total leaderboards

**Saves**
- `c!save` trades your points for a save (default 10 points for 1 save, max 3, change with `save_cost`)
- When someone ruins the count, a save is spent automatically (theirs first, then the server bank) and the count stays alive; with no saves left it resets to 1. The ruiner still loses 2 points either way
- `c!donatesave` donates 1 save to the server bank (adds 0.2)
- `c!transfersave @user` gifts a save to someone else (0.5 tax)

**Leaderboards and stats**
- `c!cs` current run, `c!lb` high scores, `c!tlb` totals, `c!ulb` global points, `c!slb` server points (all paginated)
- `c!user [@user]`, `c!server`, `c!stats`, `c!calc`, `c!save`, `c!help`, `c!ping`

**Admin commands** (need Administrator)
- `c!channel [#channel|off]` sets or clears the counting channel
- `c!failrole @role [seconds]` gives a role when someone ruins the count (auto-removed after the timeout)
- `c!disablemaths on|off`, `c!numbersonly on|off`, `c!language <code>`, `c!givesave @user [amount]`

## Setup

### 1. Create the bot on Discord
1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and make a new application.
2. Bot tab, add a bot, copy the token.
3. On the same Bot tab, turn on MESSAGE CONTENT INTENT under Privileged Gateway Intents.
4. Invite it with the OAuth2 URL generator (`bot` scope; permissions: Send Messages, Embed Links, Add Reactions, Manage Roles if you want failroles).

### 2. Configure
```powershell
copy config.properties.example config.properties
# edit config.properties and paste your token
```
Or skip the file and set an environment variable:
```powershell
$env:DISCORD_TOKEN = "your-token-here"
```

### 3. Build and run
Requires JDK 21+ and Maven.
```powershell
mvn package
java -jar target/counting-bot.jar
```
The SQLite database (`counting.db`) is created next to the jar, so there is no external database to set up.

### 4. Play
In your counting channel, as an admin:
```
c!channel
```
Then anyone starts:
```
1
2
3
```

## Notes
- Prefix is `c!` (change with `prefix=` in `config.properties`).
- Saves are bought, not voted for: count well, get jokes rated, then trade points for saves with `c!save`.
- Everything is in one jar and the state survives restarts.
- Missing something? Open an issue or PR. The code is plain Java on purpose.

## Project layout
```
src/main/java/com/countingbot/
  CountingBot.java            entrypoint, gateway and intents
  Config.java                 config.properties / DISCORD_TOKEN
  db/Database.java            SQLite: guilds, users, scores, saves
  listener/CountingListener   the game: count, saves, roasts, points
  listener/ReactionListener   1 to 5 funny-message ratings
  command/CommandDispatcher   c! commands (user and admin)
  game/FailMessages           24 fail roasts and milestones
  game/FunnyRegistry          joke rating sessions
  game/MathEvaluator          expression parser (2^7+1 etc)
  util/Format                 numbers, uptime, cooldowns
```

Built with [Discord4J](https://discord4j.com), SQLite and Maven.
