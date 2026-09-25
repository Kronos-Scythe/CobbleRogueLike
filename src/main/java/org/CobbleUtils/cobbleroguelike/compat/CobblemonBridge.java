package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    /**
     * Makes the run copy of one of the player's own Pokémon. The copy gets a new UUID and a
     * rogue tag, so the original is never touched. It does not level up, evolve or change.
     * {@code level <= 0} keeps the original level.
     */
    public static Pokemon createRogueCopy(Pokemon original, DynamicRegistryManager registries, int level) {
        Pokemon copy = original.clone(true, registries);
        if (level > 0) {
            copy.setLevel(level);
            copy.initializeMoveset(true);
        }
        copy.heal();
        markRogue(copy);
        return copy;
    }

    /** Finds one of the player's Pokémon by UUID in their party or PC. */
    public static Pokemon findOwned(ServerPlayerEntity player, UUID id) {
        for (Pokemon pokemon : party(player)) {
            if (pokemon.getUuid().equals(id)) {
                return pokemon;
            }
        }
        for (Pokemon pokemon : pc(player)) {
            if (pokemon.getUuid().equals(id)) {
                return pokemon;
            }
        }
        return null;
    }

    /** An item that renders the Pokémon's model, for menu icons. */
    public static ItemStack icon(Pokemon pokemon) {
        return PokemonItem.from(pokemon);
    }

    /** Model icon for a species id, or null if the species doesn't exist. */
    public static ItemStack icon(String speciesName) {
        Species species = PokemonSpecies.INSTANCE.getByName(speciesName);
        return species == null ? null : PokemonItem.from(species, Set.of(), 1, null);
    }

    public static Text displayName(Pokemon pokemon) {
        return pokemon.getDisplayName(false);
    }

    public static Text natureName(Pokemon pokemon) {
        return Text.translatable(pokemon.getNature().getDisplayName());
    }

    public static Text abilityName(Pokemon pokemon) {
        return Text.translatable(pokemon.getAbility().getDisplayName());
    }

    public static void healParty(ServerPlayerEntity player) {
        party(player).heal();
    }

    public static String describe(Pokemon pokemon) {
        return pokemon.getSpecies().getName() + " Lv." + pokemon.getLevel();
    }
}
