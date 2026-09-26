# CobbleRogueLike

A roguelike mode for [Cobblemon](https://modrinth.com/mod/cobblemon), inspired by Pokémon Emerald Rogue and Cobblemon Battle Tower. You pick one of your own Pokémon as your only partner and battle through floors of trainers, gyms, the Elite Four and the Champion. Your real team, levels and gym progress are never touched.

> **Status:** early development. Features are written but still being tested in game.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 (Fabric) |
| Fabric Loader | 0.19.5+ |
| Fabric API | 0.116.17+1.21.1 |
| Fabric Language Kotlin | 1.13.7+kotlin.2.2.21 |
| Cobblemon | 1.8.1+1.21.1 |
| Mega Showdown | *optional*: 1.1.3+1.8+1.21.1 |

## Building

```sh
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev client
./gradlew runServer    # dev server
```

The Gradle daemon runs on Java 25 (see `gradle/gradle-daemon-jvm.properties`). The mod itself targets Java 21.

## How a run works

1. **`/rogue` → Start a run.** Toggle optional **modifiers**, then pick a partner from your party or PC. A copy joins the run at level 5, and your real party is saved safely until the run ends.
2. **Each floor, choose 1 of 3 path cards:**
   - **Route:** each route card is its own biome (Desert, Tundra, Volcano…, shown by its block). Pick one of 3 wild Pokémon from it.
   - **Trainer:** fight an AI trainer (NPC) and earn coins.
   - **Rest stop:** full heal.
   - **Legendary:** fight a legendary and it joins you if you win (after badges 3 and 6, rarely otherwise; co-op after badges 4 and 7).

   The first 2 floors are routes only, so you can build a team before the first trainer.
3. **Every 5th floor is a gym.** It has a random type, a double battle, a competitive team and a badge. Your run level cap rises with each badge.
   Before any boss (gym, Elite Four, Champion) you get free **Boss prep**, once each per boss and per player:
   - **Train to level cap:** one click, no grinding; moves and evolutions happen as usual.
   - **Full heal.**
   - **Draft a counter** (only with the Counter Draft modifier): pick 1 of 3 Pokémon at the cap that are strong against the boss's type.
4. **After 8 badges**, 4 Elite Four battles, then **the Champion**. Beat them to win.
5. **Losing or forfeiting ends the run.** Either way you earn **Rogue Tokens** to spend in the Rogue Shop.
6. **Save & leave** any time between battles. Your real party comes back so you can play normally, and you can continue the run later from `/rogue`.

With the mod installed on your client you get a **run screen** like Battle Tower's: your team (and your partner's in co-op) on the left, the options in the middle as cards, a grid or a list, and your progress (floor, badges and a floor tower up to the next gym) on the right, themed with the biome's blocks. It comes back by itself after every run battle. It scales to fit your window at any GUI scale. Coins sit in the top-right corner, **Close** in the bottom-left and **Back** (when there's somewhere to go back to) in the bottom-right. Without the mod on the client (server-only install) the same menus open as chests.

Every run screen has a **nav bar** along the bottom:

| Button | What it does |
|---|---|
| Floor info | Biome, badges, level cap, coins and modifiers |
| Shop | Spend coins on healing items, competitive held items, berries, mints, evolution items… |
| Bag | Use items, give or take held items, and move items between Pokémon |
| Move Tutor | Level-up moves are free; TM, tutor and egg moves cost coins |
| Save & leave | Put the run away and get your real party back; continue later from `/rogue` |
| End run | Ends the run (asks for confirmation first) |

Outside a run, `/rogue` opens the start page: **Start a run** (or **Continue saved run**) and **Co-op run**, with **How to play** (its own page) and the **Rogue Shop** as buttons along the bottom. The Rogue Shop is only available here, not during a run.

### Features

- **Isolation:** only your party is swapped for the run, and it's written to disk first. Run Pokémon are tagged, and anything leaked is cleaned up. The PC, `/pc`, `/trade`, catching, outside battles and held-item swaps are blocked during a run.
- **Biomes:** 12 biomes (Volcano, Tundra, Caves…), each built from **Cobblemon's real spawn data**, so regional forms and addon species appear. Shiny (1/256) and hidden-ability (10%) chances.
- **Battles:** real Cobblemon NPC trainers with `StrongBattleAI`. Gyms, the Elite Four and the Champion are **double battles** with competitive sets: chosen moves, support moves such as Protect or Fake Out, held items, natures, IVs and EVs.
- **Boss team archetypes** (Radical Red / Run & Bun style):
  - Gym leaders from the 2nd gym, plus the Elite Four and the Champion, build teams around a plan: **Rain, Sun, Sandstorm, Snow, Trick Room, Tailwind** or **Electric / Psychic / Grassy Terrain**.
  - Each team has a setter that leads, abusers (Swift Swim, Chlorophyll, slow hard hitters under Trick Room…) and, in doubles, a Fake Out / Follow Me / Intimidate support.
  - Gyms pick a style that fits their type, and the battle preview shows it.
- **Modifiers:** Nuzlocke, Solo, Hard, No Shop and All Doubles, each with a token bonus, and Counter Draft (a boss prep option to draft a counter Pokémon, -25% tokens).
- **Co-op runs** (inspired by Coop+):
  - Invite a friend from the start page or with `/rogue invite <player>`.
  - Each of you picks your own partner and keeps **up to 3 Pokémon**. Floors, badges and coins are shared.
  - **Every battle is a 2 vs 2** (Cobblemon's multi battle): you each control your own Pokémon against two trainers, or a legendary and its companion.
  - Battles start once you've **both pressed Ready** and are standing together.
  - On routes, each of you gets your own 3 options.
  - Beat a legendary and one of you takes it, the other gets its companion.
  - Log off any time and the run waits for you. Losing ends the run for both of you.
- **Mega Showdown (optional):**
  - Buy **gimmick unlocks** (Mega, Z-Moves, Tera, Dynamax) in the run shop, plus mega stones, Z-crystals and Tera shards. No real key items are handed out.
  - **Bosses Mega Evolve or Terastallize.**

## Commands

| Command | Who | What |
|---|---|---|
| `/rogue` | everyone | Open the run menu (or the hub) |
| `/rogue shop` | outside a run | Spend Rogue Tokens on real items |
| `/rogue save` | in a run | Save & leave the run |
| `/rogue invite <player>` | outside a run | Invite a friend to a co-op run |
| `/rogue accept` / `decline` | invited | Answer a co-op invite |
| `/rogue ready` | co-op run | Ready up for the next battle |
| `/rogue tutor` | in a run | Move Tutor |
| `/rogue end` | in a run | End your run (tokens still paid) |
| `/rogue endbattle` | in a run | Force-stop a stuck battle **and end the run** |
| `/rogue clean` | everyone | Clean up leftover run data and restore your party |
| `/rogue admin end\|endbattle\|clean <player>` | op | Same as above, for another player |
| `/rogue admin tokens <player> <amount>` | op | Add or remove Rogue Tokens |
| `/rogue admin reload` | op | Reload the config and biome spawn pools |

## Configuration

`config/cobbleroguelike.json` is created on first launch. Highlights:

**Defaults are the hardest settings:**
- every trainer uses the smartest AI (`maxTrainerAi`) with fully built sets (`setTierBonus: 2`)
- team archetypes and boss Mega/Tera
- no free heal after gyms or Elite Four fights

The start is gentler: difficulty ramps up to these settings over the first 2 gyms (`rampUntilBadge`), and Pokémon far below the level cap get extra EXP (`catchUpExp`).

Run modifiers (Nuzlocke, Hard…) stay optional. To make the game easier, lower `setTierBonus`, turn off `maxTrainerAi`, or raise the `…FromBadge` options.


- **Levels and pacing:** `starterLevel`, `resetStarterLevel`, `gymEvery`, `gymCount`, `levelCaps`, `gymTeamSizes`, `eliteCount`, `eliteEvery`
- **Co-op:** `coopPartyLimit` (3), `coopMaxDistance`, `coopInviteSeconds`, `coopLegendaryAfterBadges`, `coopLegendaryFromBadge`
- **Balance:** `expMultiplier` (2× EXP for run Pokémon), `catchUpExp`, `rampUntilBadge`, `startRouteFloors`, `prepTrainToCap`, `prepDraft`, `prepHeal`, `prepDraftOptions`
- **Difficulty:** `setTierBonus`, `maxTrainerAi`, `trainerArchetypesFromBadge`, `healAfterGym`, `healAfterElite`
- **Battles:** `bossArchetypes`, `archetypeFromBadge`, `bossTeraFromBadge`, `bossMegaFromBadge`, `doubleBattles` (`bosses` / `all` / `none`), `doubleTrainerChance`, `bossGimmicks`
- **Encounters:** `biomes` (Minecraft/Cobblemon biome ids and `#tags`, plus theme types), spawn bucket weights, `shinyChance`, `hiddenAbilityChance`, and the legendary settings
- **Economy:** `startingMoney`, trainer rewards, the `shop` catalog, `moveTutorPrice`
- **Rewards:** `tokensPerFloor`, `tokensPerBadge`, `championTokenBonus`, the `tokenShop` catalog, modifier bonuses
- **UI:** `clientScreen` (use the run screen for players who have the mod), each biome's `block`
- **Blocked commands** during runs: `blockedCommands`

Unknown item ids are hidden from the shops, so catalog entries for missing mods are safe.

## Data

Stored per world in `<world>/cobbleroguelike/`:
- `journals/`: saved real parties
- `runs/`: run progress (and the run team while a run is saved)
- `profiles/`: Rogue Tokens and stats

## More

- [Design notes](docs/DESIGN.md)
