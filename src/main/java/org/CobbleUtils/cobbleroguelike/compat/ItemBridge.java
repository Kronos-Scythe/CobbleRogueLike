package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.item.PokemonSelectingItem;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Run-bag item logic. Items are applied through Cobblemon's own item code, using a temporary
 * stack, so potions, revives, mints, vitamins, stones and Mega Showdown's Tera shards all work
 * as normal without touching the real inventory.
 */
public final class ItemBridge {

    private ItemBridge() {
    }

    /** The item for an id, or null if it doesn't exist. */
    public static Item item(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null || !Registries.ITEM.containsId(identifier)) {
            return null;
        }
        Item item = Registries.ITEM.get(identifier);
        return item == Items.AIR ? null : item;
    }

    public static boolean exists(String id) {
        return item(id) != null;
    }

    public static ItemStack stack(String id) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static String id(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** Whether Cobblemon knows how to use this item on a Pokémon (potions, mints, stones...). */
    public static boolean isUsable(String id) {
        return item(id) instanceof PokemonSelectingItem;
    }

    /**
     * Uses an item on a Pokémon. {@code available} is how many the bag has, since some items need
     * several at once (Tera shards). Returns how many were consumed; 0 means it had no effect.
     */
    public static int useOn(ServerPlayerEntity player, Pokemon pokemon, String id, int available) {
        Item item = item(id);
        if (!(item instanceof PokemonSelectingItem selecting) || available <= 0) {
            return 0;
        }
        ItemStack stack = new ItemStack(item, Math.min(available, item.getMaxCount()));
        if (!selecting.canUseOnPokemon(stack, pokemon)) {
            return 0;
        }
        int before = stack.getCount();
        selecting.applyToPokemon(player, stack, pokemon);
        return Math.max(0, before - stack.getCount());
    }

    /** Gives the Pokémon an item to hold and returns what it held before (possibly empty). */
    public static ItemStack swapHeld(Pokemon pokemon, ItemStack newItem) {
        CobblemonGuards.allowHeldItemChange = true;
        try {
            return pokemon.swapHeldItem(newItem, false, true);
        } finally {
            CobblemonGuards.allowHeldItemChange = false;
        }
    }

    public static ItemStack held(Pokemon pokemon) {
        return pokemon.heldItem();
    }

    /** Item ids in an item tag, e.g. {@code mega_showdown:mega_stone}. */
    public static List<String> itemsInTag(String tagId) {
        List<String> ids = new ArrayList<>();
        Identifier identifier = Identifier.tryParse(tagId);
        if (identifier == null) {
            return ids;
        }
        TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, identifier);
        for (RegistryEntry<Item> entry : Registries.ITEM.iterateEntries(tag)) {
            ids.add(Registries.ITEM.getId(entry.value()).toString());
        }
        ids.sort(String::compareTo);
        return ids;
    }
}
