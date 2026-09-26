package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleRules;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import kotlin.Unit;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.CobbleUtils.cobbleroguelike.util.Scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Starts run battles against AI trainers and reports how they ended. */
public final class CobblemonBattles {

    private CobblemonBattles() {
    }

    /**
     * Starts a singles or doubles battle between the player's (rogue) party and an AI trainer.
     * A temporary Cobblemon NPC is spawned in front of the player to send the trainer's
     * Pokémon out, and it is despawned a few seconds after the battle ends.
     * The player's party is used directly, so damage, fainting and EXP carry over within the run.
     * The trainer's Pokémon are battle clones and can't be caught. The Bag Clause stops
     * real-inventory items from being used.
     *
     * @return the battle, or null if it could not start
     */
    public static PokemonBattle startTrainerBattle(ServerPlayerEntity player, String trainerName,
                                                   List<Pokemon> trainerTeam, int aiSkill, boolean doubles,
                                                   String gimmick) {
        List<BattlePokemon> playerTeam = new ArrayList<>(CobblemonBridge.party(player).toBattleTeam(false, false, null));
        playerTeam.sort(Comparator.comparing(pokemon -> pokemon.getHealth() <= 0));
        if (playerTeam.isEmpty() || playerTeam.get(0).getHealth() <= 0 || trainerTeam.isEmpty()) {
            return null;
        }
        PlayerBattleActor playerActor = new PlayerBattleActor(player.getUuid(), playerTeam);

        List<BattlePokemon> team = new ArrayList<>();
        int topLevel = 1;
        for (Pokemon pokemon : trainerTeam) {
            team.add(BattlePokemon.Companion.safeCopyOf(pokemon));
            topLevel = Math.max(topLevel, pokemon.getLevel());
        }

        NPCEntity npc = RunEntities.spawn(player, trainerName, topLevel);
        if (npc == null) {
            return null;
        }
        int skill = Math.max(0, Math.min(5, aiSkill));
        BattleAI ai = new StrongBattleAI(skill);
        if (gimmick != null && !gimmick.isEmpty() && !trainerTeam.isEmpty()) {
            Pokemon ace = trainerTeam.get(trainerTeam.size() - 1);
            ai = new GimmickAI(ai, gimmick, CobblemonBridge.propertyId(ace.getSpecies()));
            // Purely visual: the leader shows off the key item.
            ItemStack keyItem = ItemBridge.stack(gimmick.equals("mega") ? "mega_showdown:mega_bracelet" : "mega_showdown:tera_orb");
            if (!keyItem.isEmpty()) {
                npc.equipStack(EquipmentSlot.MAINHAND, keyItem);
            }
        }
        NPCBattleActor trainer = new NPCBattleActor(npc, team, skill, ai);

        BattleFormat format = withBagClause(doubles ? BattleFormat.Companion.getGEN_9_DOUBLES() : BattleFormat.Companion.getGEN_9_SINGLES());

        // canPreempt = false: run battles skip BATTLE_STARTED_PRE, so neither our own
        // outside-battle guard nor other mods (e.g. level-cap mods) can cancel them.
        BattleStartResult result = BattleRegistry.startBattle(format, new BattleSide(playerActor), new BattleSide(trainer), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(npc);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        MinecraftServer server = player.getServer();
        onEnd(battle, ended -> server.execute(() ->
                // Give the NPC time to recall its Pokémon before it disappears.
                Scheduler.runLater(60, () -> RunEntities.despawn(npc))));
        return battle;
    }

    /**
     * Starts a wild battle against a Legendary spawned in front of the player. Otherwise it works
     * like {@link #startTrainerBattle}: same party handling and Bag Clause, and the Pokémon is
     * uncatchable and despawned after the battle. {@code properties} is the Pokémon to fight.
     */
    public static PokemonBattle startWildBattle(ServerPlayerEntity player, String properties, int aiSkill) {
        List<BattlePokemon> playerTeam = new ArrayList<>(CobblemonBridge.party(player).toBattleTeam(false, false, null));
        playerTeam.sort(Comparator.comparing(pokemon -> pokemon.getHealth() <= 0));
        if (playerTeam.isEmpty() || playerTeam.get(0).getHealth() <= 0) {
            return null;
        }
        PlayerBattleActor playerActor = new PlayerBattleActor(player.getUuid(), playerTeam);

        PokemonEntity entity = RunEntities.spawnWild(player, properties);
        if (entity == null) {
            return null;
        }
        Pokemon wild = entity.getPokemon();
        BattlePokemon battlePokemon = new BattlePokemon(wild, wild, ignored -> Unit.INSTANCE);
        int skill = Math.max(0, Math.min(5, aiSkill));
        PokemonBattleActor wildActor = new PokemonBattleActor(wild.getUuid(), battlePokemon, 32.0F, new StrongBattleAI(skill));

        BattleStartResult result = BattleRegistry.startBattle(withBagClause(BattleFormat.Companion.getGEN_9_SINGLES()),
                new BattleSide(playerActor), new BattleSide(wildActor), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(entity);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        entity.setBattleId(battle.getBattleId());
        MinecraftServer server = player.getServer();
        onEnd(battle, ended -> server.execute(() -> Scheduler.runLater(40, () -> RunEntities.despawn(entity))));
        return battle;
    }

    private static BattleFormat withBagClause(BattleFormat base) {
        Set<String> rules = new HashSet<>(base.getRuleSet());
        rules.add(BattleRules.BAG_CLAUSE);
        return BattleFormat.Companion.setBattleRules(base, rules);
    }

    // ---------------------------------------------------------------- co-op (multi battles)

    /** Each co-op player's own team, fainted last; null if they have nothing able to fight. */
    private static PlayerBattleActor coopActor(ServerPlayerEntity player) {
        List<BattlePokemon> team = new ArrayList<>(CobblemonBridge.party(player).toBattleTeam(false, false, null));
        team.sort(Comparator.comparing(pokemon -> pokemon.getHealth() <= 0));
        if (team.isEmpty() || team.get(0).getHealth() <= 0) {
            return null;
        }
        return new PlayerBattleActor(player.getUuid(), team);
    }

    /**
     * Co-op trainer battle (Cobblemon's MULTI format: two actors per side, one Pokémon each). The
     * two players fight two NPC trainers, spawned side by side in front of the first player. The
     * gimmick (if any) belongs to the first trainer's ace.
     */
    public static PokemonBattle startCoopTrainerBattle(ServerPlayerEntity first, ServerPlayerEntity second,
                                                       String name1, List<Pokemon> team1,
                                                       String name2, List<Pokemon> team2,
                                                       int aiSkill, String gimmick) {
        PlayerBattleActor actor1 = coopActor(first);
        PlayerBattleActor actor2 = coopActor(second);
        if (actor1 == null || actor2 == null || team1.isEmpty() || team2.isEmpty()) {
            return null;
        }
        int skill = Math.max(0, Math.min(5, aiSkill));
        NPCEntity npc1 = RunEntities.spawn(first, name1, topLevel(team1), -1.5);
        NPCEntity npc2 = RunEntities.spawn(first, name2, topLevel(team2), 1.5);
        if (npc1 == null || npc2 == null) {
            RunEntities.despawn(npc1);
            RunEntities.despawn(npc2);
            return null;
        }
        BattleAI ai1 = new StrongBattleAI(skill);
        if (gimmick != null && !gimmick.isEmpty()) {
            ai1 = new GimmickAI(ai1, gimmick, CobblemonBridge.propertyId(team1.get(team1.size() - 1).getSpecies()));
            ItemStack keyItem = ItemBridge.stack(gimmick.equals("mega") ? "mega_showdown:mega_bracelet" : "mega_showdown:tera_orb");
            if (!keyItem.isEmpty()) {
                npc1.equipStack(EquipmentSlot.MAINHAND, keyItem);
            }
        }
        NPCBattleActor trainer1 = new NPCBattleActor(npc1, battleTeam(team1), skill, ai1);
        NPCBattleActor trainer2 = new NPCBattleActor(npc2, battleTeam(team2), skill, new StrongBattleAI(skill));

        BattleStartResult result = BattleRegistry.startBattle(withBagClause(BattleFormat.Companion.getGEN_9_MULTI()),
                new BattleSide(actor1, actor2), new BattleSide(trainer1, trainer2), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(npc1);
            RunEntities.despawn(npc2);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        MinecraftServer server = first.getServer();
        onEnd(battle, ended -> server.execute(() -> Scheduler.runLater(60, () -> {
            RunEntities.despawn(npc1);
            RunEntities.despawn(npc2);
        })));
        syncAlliesLater(battle);
        return battle;
    }

    /** Co-op legendary battle: the legendary plus a companion Pokémon, both wild and uncatchable. */
    public static PokemonBattle startCoopWildBattle(ServerPlayerEntity first, ServerPlayerEntity second,
                                                    String legendary, String companion, int aiSkill) {
        PlayerBattleActor actor1 = coopActor(first);
        PlayerBattleActor actor2 = coopActor(second);
        if (actor1 == null || actor2 == null) {
            return null;
        }
        PokemonEntity entity1 = RunEntities.spawnWild(first, legendary, -1.5);
        PokemonEntity entity2 = RunEntities.spawnWild(first, companion, 1.5);
        if (entity1 == null || entity2 == null) {
            RunEntities.despawn(entity1);
            RunEntities.despawn(entity2);
            return null;
        }
        int skill = Math.max(0, Math.min(5, aiSkill));
        PokemonBattleActor wild1 = new PokemonBattleActor(entity1.getPokemon().getUuid(),
                new BattlePokemon(entity1.getPokemon(), entity1.getPokemon(), ignored -> Unit.INSTANCE), 64.0F, new StrongBattleAI(skill));
        PokemonBattleActor wild2 = new PokemonBattleActor(entity2.getPokemon().getUuid(),
                new BattlePokemon(entity2.getPokemon(), entity2.getPokemon(), ignored -> Unit.INSTANCE), 64.0F, new StrongBattleAI(skill));

        BattleStartResult result = BattleRegistry.startBattle(withBagClause(BattleFormat.Companion.getGEN_9_MULTI()),
                new BattleSide(actor1, actor2), new BattleSide(wild1, wild2), false);
        if (!(result instanceof SuccessfulBattleStart success)) {
            RunEntities.despawn(entity1);
            RunEntities.despawn(entity2);
            return null;
        }
        PokemonBattle battle = success.getBattle();
        entity1.setBattleId(battle.getBattleId());
        entity2.setBattleId(battle.getBattleId());
        syncAlliesLater(battle);
        MinecraftServer server = first.getServer();
        onEnd(battle, ended -> server.execute(() -> Scheduler.runLater(40, () -> {
            RunEntities.despawn(entity1);
            RunEntities.despawn(entity2);
        })));
        return battle;
    }

    /**
     * Co-op fix: players reported not seeing their partner's Pokémon tile on the left of the battle
     * overlay. Once the opening send-outs are done, re-send each player their ally's active
     * Pokémon (the same packet Cobblemon sends on a switch), so the tile is there either way.
     */
    private static void syncAlliesLater(PokemonBattle battle) {
        Scheduler.runLater(80, () -> {
            if (battle.getEnded()) {
                return;
            }
            for (BattleSide side : List.of(battle.getSide1(), battle.getSide2())) {
                for (BattleActor viewer : side.getActors()) {
                    if (!(viewer instanceof PlayerBattleActor)) {
                        continue;
                    }
                    for (BattleActor ally : side.getActors()) {
                        if (ally == viewer) {
                            continue;
                        }
                        for (ActiveBattlePokemon active : ally.getActivePokemon()) {
                            BattlePokemon pokemon = active.getBattlePokemon();
                            if (pokemon != null) {
                                viewer.sendUpdate(new BattleSwitchPokemonPacket(active.getPNX(), pokemon, true, active.getIllusion()));
                            }
                        }
                    }
                }
            }
        });
    }

    private static List<BattlePokemon> battleTeam(List<Pokemon> team) {
        List<BattlePokemon> result = new ArrayList<>();
        for (Pokemon pokemon : team) {
            result.add(BattlePokemon.Companion.safeCopyOf(pokemon));
        }
        return result;
    }

    private static int topLevel(List<Pokemon> team) {
        int level = 1;
        for (Pokemon pokemon : team) {
            level = Math.max(level, pokemon.getLevel());
        }
        return level;
    }

    /** Force-stops the battle the player is in. Returns false if they aren't in one. */
    public static boolean stopBattle(ServerPlayerEntity player) {
        PokemonBattle battle = BattleRegistry.getBattleByParticipatingPlayer(player);
        if (battle == null) {
            return false;
        }
        battle.stop();
        return true;
    }

    public static void onEnd(PokemonBattle battle, Consumer<PokemonBattle> handler) {
        battle.getOnEndHandlers().add(ended -> {
            handler.accept(ended);
            return Unit.INSTANCE;
        });
    }

    /** TRUE if the player won, FALSE if they lost or forfeited, null if the battle was interrupted. */
    public static Boolean playerWon(PokemonBattle battle, UUID playerId) {
        if (containsActor(battle.getWinners(), playerId)) {
            return Boolean.TRUE;
        }
        if (containsActor(battle.getLosers(), playerId)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static boolean containsActor(List<BattleActor> actors, UUID playerId) {
        for (BattleActor actor : actors) {
            if (actor.getUuid().equals(playerId)) {
                return true;
            }
        }
        return false;
    }
}
