# CobbleRogueLike — Design Draft

A roguelike adventure mode for Cobblemon. It is modelled mostly on **Pokémon Emerald Rogue**, and borrows battle and co-op ideas from **Cobblemon Battle Tower** and **Cobblemon: Coop+**. A run is fully isolated from the player's main save: rewards come out, but nothing that affects gym or level-cap progression does.

---

## 1. Design pillars

1. **You start with one Pokémon.** Every run begins with a single partner, and the team is built during the run.
2. **Isolated runs.** The rogue party, bag, levels and evolutions never touch the main-world party, PC or progression.
3. **Branching adventure path.** Routes lead to a gym. You pick your path between gyms, and gym types, trainers and encounters are procedurally chosen.
4. **Menu-driven.** Like Battle Tower, the run is a series of menus and battles, not a world to explore.
5. **Meta progression lives in the mode.** Species you catch in runs unlock things for future runs, the way Emerald Rogue's Safari Zone does. They are not handed to the main world.
6. **Solo first, co-op second.** Co-op multi battles come in v2.

---

## 2. What we take from each inspiration

| Source | Idea we take | How it maps |
|---|---|---|
| **Emerald Rogue** | One partner at start | Pick any Pokémon you own (party or PC). A copy joins the run at the starter level |
| | Procedural routes, then a random gym leader of a random type | Pick-a-path menu cards, with a gym every N floors |
| | Level cap equal to the next boss's cap | Run-local level cap, not the server's level-cap mod |
| | Safari Zone of previously caught species | Your own collection is the pool: anything you have caught can be a partner |
| | Quests and rewards | Quest board in the hub that unlocks modes, modifiers and cosmetics |
| | Difficulty settings, doubles toggle, Gauntlet and Rainbow modes | Run modifiers chosen at the start |
| **Battle Tower** | Tiered difficulty with gimmicks unlocked by tier | Gym 1–2 has no gimmicks, then Tera, then Dynamax/Mega, then everything |
| | Battle Points plus a shop | Run currency spent in shop nodes, and a meta currency paid out at the end of the run |
| | Random-team mode, custom trainer skins | Trainer generator with a pool of skins |
| **Coop+** | Invite a friend; 2v1 and 2v2 multi battles | Co-op run: each player controls their own Pokémon, and bosses become multi battles |
| | Battle changes last only for that battle; reward bag | Confirms that temporary battle state is workable; reward bag handed out at the end of the run |

---

## 3. The run loop (menu-driven, Battle Tower-style)

The whole run is played through server-side chest menus (`/rogue`). There's no dimension, map or arenas, and the player stays wherever they are.

```
/rogue ──► Start ──► pick your partner from your party/PC
                          │
      ┌───────────── each floor: choose 1 of 3 path cards ─────────────┐
      │  Route      : pick ONE of 3 wild Pokémon to join (or skip)      │
      │  Rest stop  : full heal                                         │
      │  Trainer    : generated AI trainer battle                       │
      │  Shop/Event : run currency, items             (later)           │
      │  ► Gym every 5th floor: random type, ace at the cap; badge      │
      │  ► After 8 badges: the Champion. Beat them to win the run       │
      └─────────────────────────────────────────────────────────────────┘
Whiteout / win / "End run" ─► rewards ─► real party restored
```

- Choices are **seeded per floor**, so reopening the menu or relogging can't reroll them.
- A full party (6) opens a "release one?" screen.
- Runs **persist across relogs and restarts**: resume with `/rogue`.

### Starting with a single Pokémon

- Early floors are tuned for one partner: the first gym is at level 12–15, and early trainers bring at most 2 Pokémon.
- **Your partner is one of your own Pokémon.** A PC-style picker lists your party and PC with level, nature, ability and shiny status. The run gets a **copy** with a new UUID and the rogue tag, reset to `starterLevel` (default 5, `resetStarterLevel` in the config). IVs, nature, ability and shininess carry over. The original stays in the journal or PC and never gains EXP, evolves or changes.
- **Optional "Solo" modifier:** routes are disabled, so it's just you and your partner, and it pays bonus rewards.

### Battles

- Each battle **spawns a temporary Cobblemon NPC** (class `cobbleroguelike:rogue_trainer`, standard model, no AI, invulnerable) a few blocks in front of the player. It is fought through `NPCBattleActor` with `StrongBattleAI`, at skill 1–5 as badges rise (gyms start one step higher). Cobblemon only sends out a trainer's Pokémon at battle start when the trainer has an entity, so an entity-less `TrainerBattleActor` left the battle stuck forever. The NPC is despawned 3 s after the battle ends. NPCs are tagged `cobbleroguelike_trainer`, and any left behind by a crash are removed when their chunk loads.
- **`/rogue endbattle`** force-stops the current battle and ends the run. Non-ops can only use it during a run; `/rogue admin endbattle <player>` works on anyone.
- The player fights with the run party directly, so **damage and fainting carry over** between battles. Rest stops heal, and so does beating a gym (`healAfterGym`).
- Trainer Pokémon are battle clones and can't be caught. The **Bag Clause** is on, so real-inventory items can't be used.
- Run battles start with `canPreempt = false`. The outside-battle guard and other mods' pre-battle hooks can't cancel them.
- **Losing or forfeiting ends the run.** An interrupted battle (a disconnect or server stop) can be challenged again from `/rogue`, against the same trainer.
- Teams are drawn from all implemented species within a base-stat-total window that grows with level. Legendaries, mythicals, Ultra Beasts and paradoxes are excluded. Gyms use a random type that isn't repeated within a run.

### Level cap and scaling

- The run cap is `levelCaps[badges]`, default 15 / 21 / 27 / 33 / 40 / 47 / 54 / 61, with 70 for the Champion. Rogue Pokémon can't gain EXP past it.
- Trainer and route levels ramp from the previous cap toward 85% of the next one across the 4 floors before each gym.
- The gym team size is `gymTeamSizes[badges]`. Normal trainers bring 1–2 Pokémon early and more with each pair of badges.

### Difficulty tiers (Battle Tower-style)

| Floors | Trainer AI | Gimmicks | Team size |
|---|---|---|---|
| Gyms 1–2 | basic | none | 2–4 |
| Gyms 3–4 | smarter | Tera on bosses | 4–5 |
| Gyms 5–6 | smart | Tera, Mega | 5–6 |
| Gyms 7–8, E4, Champ | best | all, legends on Champ | 6 |

---

## 4. Isolation: party-only swap

Because the run is played in menus, **only the party is swapped**. The run's bag stays virtual inside the run state, and the player's inventory, position and PC are never touched.

1. **The journal is written first.** The real party goes to `<world>/cobbleroguelike/journals/<uuid>.dat` (temp file, then atomic rename) before anything changes.
2. The real party is removed. Every Pokémon created for the run carries a persistent `cobbleroguelike_rogue` tag.
3. `runs/<uuid>.dat` is written once the swap is complete, and again after every choice.
4. **Ending the run:** delete the rogue-tagged Pokémon, move any *untagged* strangers to the PC (never delete them), then add the journal Pokémon back, skipping any UUID already in the party or PC. The journal is deleted **last**. Because of this, the restore is safe to run twice and never duplicates.
5. **Recovery on join:**
   - A journal with no run file means the swap was interrupted, so the party is restored.
   - A run file whose party contains an untagged Pokémon is corrupt, so the party is restored.
   - A run file with no journal is discarded.
   - A run file that can't be read, or whose party is empty, is also restored. Values from older builds fall back to safe defaults when loading.
6. **`/rogue clean`** (or `/rogue admin clean <player>`) force-runs the same restore at any time outside a battle. It also runs automatically when **Start a run** finds leftover data.

### Guards while a run is active

- **Battles:** Cobblemon battles the mod didn't start are cancelled. This stops free EXP and catching.
- **Catching:** thrown Poké Balls are cancelled, so wild Pokémon can't join and rogue Pokémon can't overflow into the PC.
- **Held items:** changes are cancelled, so items can't move between the rogue party and the real inventory.
- **PC:** the block is locked, and `/pc` and `/trade` are blocked for non-ops. The blocked commands are configurable.
- **Sweep:** every 60 s, any rogue-tagged Pokémon found in a PC (or in a party with no run) is deleted. This catches leaks such as trades done through the interaction wheel.

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

- Quest-board unlocks: modes (Gauntlet, Rainbow, Solo) and modifiers
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

- **Target:** Cobblemon 1.6.x on MC 1.21.1, Fabric (yarn mappings). All Cobblemon API calls live in `compat/CobblemonBridge` and `compat/CobblemonGuards`, so an API change only needs fixing there.
- **Menus:** vanilla `GenericContainerScreenHandler` subclasses (`ui/Menu`, `ui/MenuScreenHandler`). No client code is needed, and clicks never move items.
- **Battles (next):** build trainer actors programmatically and start them through the battle registry. Mark them as sanctioned so the battle guard lets them through.
- **Data-driven:** encounter pools live in `config/cobbleroguelike.json` for now, and move to datapack JSON with trainers, gyms and shops.

---

## 8. Milestones

1. **Done (untested build):** journaled party swap and restore, crash recovery, guards, `/rogue` menus, partner picker (a copy of your own Pokémon), route (pick 1 of 3), rest, and the release screen.
2. **Done (untested build):** trainer nodes, gyms every 5 floors, the Champion, run level cap, losing ends the run.
3. **Economy:** run money, a shop node, items as run state applied in battle.
4. **Meta:** the quest board, Rogue Tokens and the vendor.
5. **Modifiers:** Nuzlocke, Solo, Doubles, Rainbow, Gauntlet.
6. **Co-op:** invites, multi battles.
