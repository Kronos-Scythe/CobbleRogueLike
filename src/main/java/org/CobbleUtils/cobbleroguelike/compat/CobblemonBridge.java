package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Every call into Cobblemon's API goes through this class, so a change in Cobblemon's API
 * only needs fixing here.
 */
public final class CobblemonBridge {

    /** Persistent-data flag carried by every Pokémon created for a run. */
    public static final String ROGUE_TAG = "cobbleroguelike_rogue";

    private CobblemonBridge() {
    }

    public static PlayerPartyStore party(ServerPlayerEntity player) {
        return Cobblemon.INSTANCE.getStorage().getParty(player);
    }

    public static PCStore pc(ServerPlayerEntity player) {
        return Cobblemon.INSTANCE.getStorage().getPC(player);
    }

    public static List<Pokemon> partyMembers(ServerPlayerEntity player) {
        List<Pokemon> members = new ArrayList<>();
        for (Pokemon pokemon : party(player)) {
            members.add(pokemon);
        }
        return members;
    }

    public static List<Pokemon> pcMembers(ServerPlayerEntity player) {
        List<Pokemon> members = new ArrayList<>();
        for (Pokemon pokemon : pc(player)) {
            members.add(pokemon);
        }
        return members;
    }

    public static boolean isInBattle(ServerPlayerEntity player) {
        return BattleRegistry.INSTANCE.getBattleByParticipatingPlayer(player) != null;
    }

    public static boolean isRogue(Pokemon pokemon) {
        return pokemon.getPersistentData().getBoolean(ROGUE_TAG);
    }

    public static void markRogue(Pokemon pokemon) {
        pokemon.getPersistentData().putBoolean(ROGUE_TAG, true);
    }

    public static NbtCompound save(Pokemon pokemon, DynamicRegistryManager registries) {
        return pokemon.saveToNBT(registries, new NbtCompound());
    }

    public static Pokemon load(NbtCompound tag, DynamicRegistryManager registries) {
        return Pokemon.Companion.loadFromNBT(registries, tag);
    }

    /** Despawns the Pokémon's sent-out entity, if any. */
    public static void recall(Pokemon pokemon) {
        PokemonEntity entity = pokemon.getEntity();
        if (entity != null) {
            entity.discard();
        }
    }

    public static boolean speciesExists(String name) {
        return PokemonSpecies.INSTANCE.getByName(name) != null;
    }

    /** Creates a rogue-tagged Pokémon from a property string such as {@code "mudkip level=5"}. */
    public static Pokemon createRogue(String properties) {
        Pokemon pokemon = PokemonProperties.Companion.parse(properties).create();
        markRogue(pokemon);
        return pokemon;
    }

    public static void healParty(ServerPlayerEntity player) {
        party(player).heal();
    }

    public static String describe(Pokemon pokemon) {
        return pokemon.getSpecies().getName() + " Lv." + pokemon.getLevel();
    }
}
