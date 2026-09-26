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
      │  Route      : pick ONE of 3 wild Pokémon from the biome (or skip)│
      │  Legendary  : after badges 2/4/6 (and rarely): beat it to recruit│
      │  Rest stop  : full heal                                         │
      │  Trainer    : generated AI trainer battle                       │
      │  Shop & Bag : always available from every run menu              │
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

### Biomes, diversity and legendaries

- **Biomes:** each stretch of floors between gyms takes place in one of 12 configurable biomes, and a new one is rolled after every gym without repeats. The 12 are Grasslands, Forest, Jungle, Desert, Savanna, Mountains, Ocean & Coast, Swamp, Tundra, Caves, Volcano and Mystic Grove.
  - Each biome lists Minecraft/Cobblemon biome ids and tags (e.g. `#cobblemon:is_volcanic`) plus theme types.
- **Wild Pokémon come from Cobblemon's real spawn data:** every `PokemonSpawnDetail` in the world spawn pool whose precomputed `validBiomes` touch the rogue biome.
  - Regional forms and datapack/addon spawns are included automatically.
  - "Spawns everywhere" entries (more than `maxBiomeSpread` of all biomes) and special Pokémon are skipped.
  - Picks are weighted by spawn bucket (common 60 / uncommon 28 / rare 10 / ultra-rare 2) within a strength window for the level, and are distinct by species.
  - Fallbacks, in order: the whole biome pool, then the biome's types, then the configured encounter pool.
  - Pools are cached and rebuilt on `/reload` or `/rogue admin reload`.
- **Extras:** route and legendary Pokémon roll a shiny chance (1/256) and a hidden-ability chance (10%). Menus show form, shiny and hidden ability, with form-accurate model icons.
- **Legendary encounters:**
  - A Legendary card is guaranteed on the first floor after badges 2, 4 and 6, and has a 3% chance on other floors.
  - The legendary spawns in front of the player as an uncatchable, AI-less wild Pokémon at the level cap, and is fought as a real wild battle (`PokemonBattleActor`, `StrongBattleAI`).
  - Beating it recruits a rogue copy, going through the release screen if the party is full. Players can also walk away from the preview.
  - Tiers by badges: BST ≤ 600, then ≤ 680, then any. Box legendaries unlock at 6 badges. Ultra Beasts and paradoxes can be turned off in the config.

### Doubles and competitive teams

- `doubleBattles`: `bosses` (default: gyms and the Champion are **double battles**, plus 20% of normal trainers), `all`, or `none`. If the player has fewer than 2 Pokémon able to fight, the battle falls back to singles.
- Boss teams are upgraded by `TeamBuilder`:
  - **Moves:** scored by power, accuracy, STAB and the matching attack stat, with a type-diversity bonus. Recharge, self-KO and AI-unfriendly moves are excluded.
  - **Support move:** one per Pokémon. Protect, Fake Out, Tailwind, Follow Me or Trick Room in doubles; setup or recovery in singles.
  - **Held items:** by role (attacker, bulky, not fully evolved → Eviolite, doubles → Sash, Sitrus, Safety Goggles...), with no duplicates on a team. Choice items drop the support move.
  - **Nature:** chosen by role.
- Tiers:
  - First gym: best level-up moves, and only the ace holds an item.
  - Gyms 2–4: adds TM, tutor and egg moves, items for everyone, natures and perfect IVs.
  - Gym 5 and later, and the Champion: adds full EV spreads.
  - Normal trainers get the best level-up movesets, and their ace holds an item from 4 badges on.

### Move Tutor

- Open it from any run screen (or `/rogue tutor`). Level-up moves the Pokémon already qualifies for are **free**. TM, tutor, egg and legacy moves cost `moveTutorPrice` coins (800).
- You choose which move to replace, and the replaced move is benched, so the Cobblemon summary screen can swap it back in.

### Battles

- Each battle **spawns a temporary Cobblemon NPC** (class `cobbleroguelike:rogue_trainer`, standard model, no AI, invulnerable) a few blocks in front of the player. It is fought through `NPCBattleActor` with `StrongBattleAI`, at skill 1–5 as badges rise (gyms start one step higher). Cobblemon only sends out a trainer's Pokémon at battle start when the trainer has an entity, so an entity-less `TrainerBattleActor` left the battle stuck forever. The NPC is despawned 3 s after the battle ends. NPCs are tagged `cobbleroguelike_trainer`, and any left behind by a crash are removed when their chunk loads.
- **`/rogue endbattle`** force-stops the current battle and ends the run. Non-ops can only use it during a run; `/rogue admin endbattle <player>` works on anyone.
- The player fights with the run party directly, so **damage and fainting carry over** between battles. Rest stops heal, and so does beating a gym (`healAfterGym`).
- Trainer Pokémon are battle clones and can't be caught. The **Bag Clause** is on, so real-inventory items can't be used.
- Run battles start with `canPreempt = false`. The outside-battle guard and other mods' pre-battle hooks can't cancel them.
- **Losing or forfeiting ends the run.** An interrupted battle (a disconnect or server stop) can be challenged again from `/rogue`, against the same trainer.
- Normal trainers are **themed by the current biome**: its real spawns plus species of its types, within a base-stat-total window that grows with level. Gyms use a random type that isn't repeated within a run, and the Champion draws from every species. Legendaries, mythicals, Ultra Beasts and paradoxes only appear as Legendary encounters.

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

### Economy, shop and bag

- **Coins** exist only for the run. You start with `startingMoney` (1500). Each win pays `trainerRewardBase + strongestLevel × trainerRewardPerLevel`: ×4 for gyms and ×8 for the Champion.
- The **shop** is open from every run menu between battles. Its categories are Healing, Battle Items (competitive held items), Type Boosters, Berries, Training (candies, vitamins, ability capsule/patch, all mints) and Evolution. Left-click buys 1 and right-click buys 5. Categories, items and prices live in the config, and unknown item ids are hidden.
- The **bag** is virtual run state and never touches the real inventory. Clicking an item and then a Pokémon either **uses** it (potions, revives, mints, vitamins, stones: Cobblemon's own item logic runs on a temporary stack) or **gives** it to hold (the old held item goes back to the bag). The party row shows HP and held items, and clicking a Pokémon moves its held item into the bag. That's how items move between Pokémon.
- Held items can only change through the bag during a run (the held-item guard has a bypass just for it). Everything in the bag and on rogue Pokémon disappears when the run ends.
- Candy is refused at the level cap. Bag items can't be used in battle yet: the Bag Clause is on, because Cobblemon hands used battle items back to the real inventory when a battle ends.

### Mega Showdown (optional)

When `mega_showdown` is installed, the shop adds:
- **Gimmick unlocks:** Mega Evolution, Z-Moves, Terastallization and Dynamax.
- **Mega Stones** and **Z-Crystals** as held items.
- **Tera Shards**, used from the bag with Mega Showdown's own logic, which needs its configured number of shards.

These are listed from Mega Showdown's item tags, so there's no compile-time dependency.

Unlocks don't hand out real key items. A `@Pseudo` mixin on Mega Showdown's `AccessoriesUtils` makes the matching key-item check succeed for run players who bought the unlock, and returns a virtual, fully charged Tera Orb. Nothing can leak into the main world, and Mega Showdown's own rules still apply: config toggles, one Mega per battle, and Power Spots for Dynamax if configured.

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

**Rogue Tokens (done):** paid whenever a run ends: 2 per floor cleared, 15 per badge, and +100 for beating the Champion. They're stored in `profiles/<uuid>.dat` along with runs, wins and best floor/badges.

- **`/rogue shop`** (also on the hub; no world vendor) spends tokens on **real items** for the player's inventory: all 21 mints, Ability Capsule/Patch, PP Up/Max, vitamins, Destiny Knot, Everstone, Power items, Exp. Share, Lucky Egg, Soothe Bell and special Poké Balls (Master Ball at 750). The catalog and prices are in `tokenShop` in the config.
- `/rogue admin tokens <player> <amount>` adds or removes tokens.
- **Optional (not built):** a *level-1 egg* of one species from a winning run.

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
3. **Done (untested build):** run coins, an always-open shop, the run bag with held-item management, and optional Mega Showdown gimmicks. Next: using bag items during battles, and gimmicks for gym leaders.
4. **Meta:** Rogue Tokens and `/rogue shop` are done. The quest board is still to come.
5. **Modifiers:** Nuzlocke, Solo, Doubles, Rainbow, Gauntlet.
6. **Co-op:** invites, multi battles.
