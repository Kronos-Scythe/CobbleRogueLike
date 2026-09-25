# CobbleRogueLike — Design Draft

A roguelike adventure mode for Cobblemon. It is modelled mostly on **Pokémon Emerald Rogue**, and borrows battle and co-op ideas from **Cobblemon Battle Tower** and **Cobblemon: Coop+**. A run is fully isolated from the player's main save: rewards come out, but nothing that affects gym or level-cap progression does.

---

## 1. Design pillars

1. **You start with one Pokémon.** Every run begins with a single partner, and the team is built during the run.
2. **Isolated runs.** The rogue party, bag, levels and evolutions never touch the main-world party, PC or progression.
3. **Branching adventure path.** Routes lead to a gym. You pick your path between gyms, and gym types, trainers and encounters are procedurally chosen.
4. **Meta progression lives in the mode.** Species you catch in runs unlock things for future runs, the way Emerald Rogue's Safari Zone does. They are not handed to the main world.
5. **Solo first, co-op second.** Co-op multi battles come in v2.

---

## 2. What we take from each inspiration

| Source | Idea we take | How it maps |
|---|---|---|
| **Emerald Rogue** | One partner at start | The starter is picked from 3 rolled options drawn from your unlocked pool |
| | Procedural routes, then a random gym leader of a random type | Adventure map made of nodes, with a boss node every N nodes |
| | Level cap equal to the next boss's cap | Run-local level cap, not the server's level-cap mod |
| | Safari Zone of previously caught species | Hub "Safari" where caught species become future starter options (first evo stage) |
| | Quests and rewards | Quest board in the hub that unlocks starters, modes and cosmetics |
| | Difficulty settings, doubles toggle, Gauntlet and Rainbow modes | Run modifiers chosen at the start |
| **Battle Tower** | Tiered difficulty with gimmicks unlocked by tier | Gym 1–2 has no gimmicks, then Tera, then Dynamax/Mega, then everything |
| | Battle Points plus a shop | Run currency spent in shop nodes, and a meta currency paid out at the end of the run |
| | Random-team mode, custom trainer skins | Trainer generator with a pool of skins |
| **Coop+** | Invite a friend; 2v1 and 2v2 multi battles | Co-op run: each player controls their own Pokémon, and bosses become multi battles |
| | Battle changes last only for that battle; reward bag | Confirms that temporary battle state is workable; reward bag handed out at the end of the run |

---

## 3. The run loop

```
Hub ──► choose modifiers ──► pick 1 of 3 starters ──► Adventure map
                                                          │
      ┌───────────── repeat per "chapter" (8 gyms → E4 → Champion) ─────────────┐
      │  choose next node (2–3 branches)                                         │
      │    Route      : encounter arena, catch ONE Pokémon (limited balls)       │
      │    Trainer    : generated trainer battle, money + item                   │
      │    Rest stop  : heal, or tutor/move-relearner                            │
      │    Shop       : spend run money                                          │
      │    Event      : berry tree, item cache, legendary encounter, wonder trade│
      │  ► Gym node   : random type, team scaled to cap, badge = cap increase    │
      └──────────────────────────────────────────────────────────────────────────┘
Whiteout (or win) ─► results screen ─► meta rewards ─► restore main save ─► Hub
```

### Starting with a single Pokémon

This is the defining constraint, so the early game has to be tuned around it:

- The **first route is guaranteed**: a route node with 3 wild options, at least one of which is super-effective against the first gym's type.
- **First gym at level 12–15.** Trainers before it field at most 2 Pokémon.
- **One free starter reroll** per run, with more unlocked through quests.
- **Optional hardcore modifier:** "Solo" (you can never catch, so it's just you and your partner), which pays out bonus meta currency.
- **Weighted starter pool:** 3 options from different rarity or BST bands, so there's always a safe pick and a greedy pick.

### Catching

Route nodes teleport you into a small biome arena where a few wild Pokémon are spawned by the mod, not by natural spawning. You catch physically with a limited number of run Poké Balls, which keeps the Cobblemon feel. Once you catch one, the rest despawn, so it's **one catch per route**, like Emerald Rogue. Nuzlocke rules are an optional modifier.

### Level cap and scaling

- The run cap is `nextBoss.cap`, for example 15 → 20 → 25 → … → 70 for the Champion, and is configurable.
- EXP is blocked, or carried over to the next level cap (configurable), once a Pokémon reaches the cap. Rare Candies are clamped too.
- Trainer and wild levels are derived from the cap and the node depth.

### Difficulty tiers (Battle Tower–style)

| Chapter | Trainer AI | Gimmicks | Team size |
|---|---|---|---|
| Gyms 1–2 | basic | none | 2–4 |
| Gyms 3–4 | smarter | Tera on bosses | 4–5 |
| Gyms 5–6 | smart | Tera, Mega | 5–6 |
| Gyms 7–8, E4, Champ | best | all gimmicks, legends on Champ | 6 |

Your PokeRogue ability and egg-move data feeds the trainer and wild generators here. Because rogue Pokémon are throwaway, they can use it without affecting main-game balance.

---

## 4. Isolation (the hard part)

### Recommended approach: rogue dimension plus journaled full swap

1. The player enters through a hub NPC or portal.
2. **The swap is written to disk first.** Main party, PC lock flag, inventory, XP, position and dimension go into a `RogueSaveJournal` on the player's persistent data (or a per-player file) *before* anything changes.
3. The main party is moved into a hidden custom store. The player gets an empty party and a run inventory, then is teleported to `cobbleroguelike:rogue`.
4. On exit (win, whiteout or `/rogue quit`), the reverse happens, and the journal is cleared **last**.
5. **On login and on server start**, if a journal exists and the player isn't in an active run, restore from it. This is what makes crashes and disconnects mid-swap safe.

### Rules inside the rogue dimension (enforced server-side)

- PC access, trading, dropping items, containers, ender chests, `/give`-style item transfer, Pokémon release to the world, and daycare/breeding are blocked.
- Battles started there are tagged `rogue`. Level-cap mods, RCT progression hooks and advancement/Pokédex hooks either ignore them or get an explicit allowlist. Pokédex "seen/caught" is the one thing we let leak, and it's configurable.
- There's one active run per player, and a co-op run belongs to its party leader.

### Alternative considered: virtual team, no swap

Cobblemon can start battles from manually built actors, so in principle the rogue team could live only in a custom store and never touch the real party. The catch is that the party HUD, send-out keybind and summary screen all read the real party, so we'd need client-side replacements. The swap approach reuses all of Cobblemon's existing UI, so it wins for v1.

---

## 5. Rewards that don't break gym progression

**Paid out at the end of a run, scaled by badges earned, modifiers and quests:**

- **Rogue Tokens** (meta currency), spent at a main-world vendor on:
  - mints, Ability Capsules and Patches, Bottle Caps, IV/EV items
  - cosmetic Poké Balls, trainer skins, titles
  - held-item *access*, not power (e.g. one Choice item per completed Champion run), configurable
- **Optional:** a *level-1 egg* of one species from a winning run. It keeps the hall-of-fame feeling without skipping gym caps. This is off by default and server-configurable.

**Never paid out:** leveled Pokémon, Rare Candies, EXP items above the player's current main-world cap, or badges and progression flags.

**Kept inside the mode (meta progression):**

- The Safari/starter pool unlocks (first evolution stage of every species you catch)
- Quest-board unlocks: extra rerolls, a bigger starting ball count, modes (Gauntlet, Rainbow, Solo)
- Run history and hall of fame

---

## 6. Co-op (v2, Coop+-inspired)

- The party leader invites 1–2 players. Each player has **their own** single starter and catches on their own route nodes.
- Path choice is decided by vote, or by the leader (configurable).
- Normal trainers are fought 2v1 (both players against one trainer's doubles team). Gyms and the Champion are 2v2, with the leader and a partner NPC.
- Each player controls only their own Pokémon, so the format is multi-battle, not shared-party.
- Whiting out alone doesn't end the run; the run ends only if every player whites out.
- Each player's journal and swap are independent.

---

## 7. Technical notes

- **Target:** Cobblemon 1.6.x / 1.7.x on MC 1.21.1 (Fabric + NeoForge via Architectury, matching Cobblemon). Check exact API names against the target Cobblemon version before building on them.
- **Battles:** build actors and sides programmatically and start them through the battle registry with a singles or doubles format. Trainer teams come from our generator as `PokemonProperties`.
- **Trainers:** our own lightweight NPC entity, or Cobblemon NPCs. Skins come from a config folder, the same pattern Battle Tower uses.
- **Arenas:** prebuilt structure templates (one per biome, plus gym and E4 rooms), pasted into the rogue dimension on a per-run grid so runs never overlap.
- **Wild spawning:** natural Cobblemon spawning is disabled in the rogue dimension, and encounter Pokémon are spawned explicitly.
- **Data-driven:** encounter pools, gym type pools, trainer archetypes, shop tables and reward tables are all datapack JSON, so modpacks can retune them.

---

## 8. Milestones

1. **Skeleton.** Rogue dimension, the journaled swap and restore, block rules, and `/rogue start|quit`.
2. **Minimal loop.** Pick 1 of 3 starters, then a linear list of route and trainer nodes, 1 gym, and a results screen.
3. **Adventure map.** Branching nodes, shop and rest nodes, the full 8 gyms, E4 and Champion, run level cap, and difficulty tiers.
4. **Meta.** Safari/starter unlocks, the quest board, Rogue Tokens and the main-world vendor.
5. **Modifiers.** Nuzlocke, Solo, Doubles, Rainbow and Gauntlet.
6. **Co-op.** Invites, multi battles, shared map voting.
