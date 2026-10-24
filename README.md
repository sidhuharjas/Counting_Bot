# counting — Discord Counting Bot 🧮

A Discord counting-game bot written in **Java 21** with **[Discord4J](https://discord4j.com)** (the Discord library for Java), inspired by the classic *counting* bot — plus a twist: **points and funny fail roasts**.

Count up together, one number per person, no repeats. Land a number → **+1 point**. Land it *with something funny* → the server rates you 1️⃣–5️⃣ for **up to +5 bonus points**. Ruin it → a random roast **and −2 points**.

## Features

**The game**
- Per-server counting channel (default: any channel named `#counting`, or set with `c!channel`)
- One person can't count two numbers in a row
- Maths counts! `2^7+1` = 129 (can be disabled with `c!disablemaths on`)
- Numbers-only mode: any non-number message ruins the count (`c!numbersonly on`)
- Milestone callouts every 25/50/100

**Points & funniness**
- **+1 point** for every correct number
- Funny message attached (extra text or a maths expression)? The bot drops a 🎭 prompt and everyone rates it **1️⃣–5️⃣** → average rating, rounded, up to **+5 points** (ratings can be changed until the next number)
- Wrong number → **random funny roast** (24 of them: *"bad bad beta"*, *"go eat curry"*, …) **and −2 points**
- Points are tracked globally and per server, with run/high/total scoreboards

**Saves** (protect the count)
- `c!vote` grants **+0.5 saves** (12-hour cooldown, max 3) — link points at your top.gg page
- When someone ruins the count, a save is spent automatically (user save first, then the server save bank) and the count survives; with no saves it resets to **1**
- `c!donatesave` — donate 1 save to the server bank (+0.2)
- `c!transfersave @user` — gift a save (0.5 tax)

**Leaderboards & stats**
- `c!cs` current run scores · `c!lb` high scores · `c!tlb` totals · `c!ulb` global points · `c!slb` server points (all paginated)
- `c!user [@user]`, `c!server`, `c!stats`, `c!calc`, `c!vote`, `c!help`, `c!ping`

**Admin commands** (need Administrator)
- `c!channel [#channel|off]` — set/clear counting channel
- `c!failrole @role [seconds]` — tag count-ruiners with a role (auto-removed after timeout)
- `c!disablemaths on|off` · `c!numbersonly on|off` · `c!language <code>` · `c!givesave @user [amount]`

## Setup

### 1. Create the bot on Discord
1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) → **New Application**.
2. **Bot** tab → add a bot → copy the **token**.
3. On the same **Bot** tab, enable **MESSAGE CONTENT INTENT** under *Privileged Gateway Intents*.
4. Invite it to your server with the **OAuth2 → URL Generator** (`bot` scope; permissions: *Send Messages*, *Embed Links*, *Add Reactions*, *Manage Roles* if you want failroles).

### 2. Configure
```powershell
copy config.properties.example config.properties
# edit config.properties and paste your token
```
Or skip the file and set an environment variable:
```powershell
$env:DISCORD_TOKEN = "your-token-here"
```

### 3. Build & run
Requires **JDK 21+** and **Maven**.
```powershell
mvn package
java -jar target/counting-bot.jar
```
The SQLite database (`counting.db`) is created next to the jar — no external database needed.

### 4. Play
```
# in your counting channel (admin):
c!channel
# then anyone starts:
1
2
3
```

## Notes
- **Prefix** is `c!` (change with `prefix=` in `config.properties`).
- **top.gg voting** is wired as a 12-hour self-serve cooldown with your vote URL — swap in the real top.gg API when you have a bot listing (the integration point is `Database.claimVote`).
- Everything lives in one jar; states survive restarts.
- Missing something? Open an issue or PR — the codebase is deliberately plain Java so the community can extend it.

## Project layout
```
src/main/java/com/countingbot/
├── CountingBot.java            # entrypoint, gateway + intents
├── Config.java                 # config.properties / DISCORD_TOKEN
├── db/Database.java            # SQLite: guilds, users, scores, saves
├── listener/CountingListener   # the game: count, saves, roasts, points
├── listener/ReactionListener   # 1️⃣–5️⃣ funny ratings
├── command/CommandDispatcher   # c! commands (user + admin)
├── game/FailMessages           # 24 funny fail roasts + milestones
├── game/FunnyRegistry          # joke rating sessions
├── game/MathEvaluator          # safe expression parser (2^7+1 etc.)
└── util/Format                 # numbers, uptime, cooldowns
```

Built with [Discord4J](https://discord4j.com) • SQLite • Maven
