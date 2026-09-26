package org.CobbleUtils.cobbleroguelike.shop;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.Modifiers;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.ui.ShopMenus;

import java.util.List;

/** Buying, using and moving run-bag items. Everything here only touches run state and rogue Pokémon. */
public final class ShopService {

    private ShopService() {
    }

    public static void buy(ServerPlayerEntity player, int categoryIndex, int entryIndex, int quantity, int page) {
        RunState state = requireRun(player);
        if (state == null) {
            return;
        }
        if (Modifiers.has(state, Modifiers.NO_SHOP)) {
            RunManager.message(player, "The shop is closed on No Shop runs.", Formatting.RED);
            return;
        }
        List<ShopCatalog.Category> categories = ShopCatalog.categories();
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            ShopMenus.shop(player);
            return;
        }
        List<ShopCatalog.Entry> entries = categories.get(categoryIndex).items();
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            ShopMenus.category(player, categoryIndex, page);
            return;
        }
        ShopCatalog.Entry entry = entries.get(entryIndex);
        String name = displayName(entry);

        if (entry.action().equals(ShopCatalog.UNLOCK)) {
            String gimmick = entry.item().substring("gimmick:".length());
            if (state.gimmicks.contains(gimmick)) {
                RunManager.message(player, name + " is already unlocked for this run.", Formatting.YELLOW);
            } else if (state.money < entry.price()) {
                RunManager.message(player, "Not enough coins.", Formatting.RED);
            } else {
                state.money -= entry.price();
                state.gimmicks.add(gimmick);
                RunManager.message(player, name + " unlocked for this run!", Formatting.GREEN);
                RunManager.get().persist(player, state);
            }
        } else {
            int affordable = entry.price() <= 0 ? quantity : Math.min(quantity, state.money / entry.price());
            if (affordable <= 0) {
                RunManager.message(player, "Not enough coins.", Formatting.RED);
            } else {
                state.money -= affordable * entry.price();
                state.addToBag(entry.item(), affordable);
                RunManager.message(player, "Bought " + affordable + "x " + name + ".", Formatting.GREEN);
                RunManager.get().persist(player, state);
            }
        }
        ShopMenus.category(player, categoryIndex, page);
    }

    /** Uses or gives a bag item to a party member, depending on the item. */
    public static void apply(ServerPlayerEntity player, String itemId, int partyIndex) {
        RunState state = requireRun(player);
        if (state == null) {
            return;
        }
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex < 0 || partyIndex >= party.size() || state.bagCount(itemId) <= 0) {
            ShopMenus.bag(player, 0);
            return;
        }
        Pokemon pokemon = party.get(partyIndex);
        String itemName = ItemBridge.stack(itemId).getName().getString();
        String pokemonName = pokemon.getSpecies().getName();

        if (ShopCatalog.actionFor(itemId).equals(ShopCatalog.USE)) {
            if (itemId.contains("candy") && pokemon.getLevel() >= RunManager.levelCap(player)) {
                RunManager.message(player, pokemonName + " is already at the level cap.", Formatting.RED);
            } else {
                int used = ItemBridge.useOn(player, pokemon, itemId, state.bagCount(itemId));
                if (used > 0) {
                    state.takeFromBag(itemId, used);
                    RunManager.message(player, "Used " + (used > 1 ? used + "x " : "") + itemName + " on " + pokemonName + ".", Formatting.GREEN);
                } else {
                    RunManager.message(player, "It won't have any effect on " + pokemonName + ".", Formatting.YELLOW);
                }
            }
        } else {
            state.takeFromBag(itemId, 1);
            ItemStack previous = ItemBridge.swapHeld(pokemon, ItemBridge.stack(itemId));
            if (!previous.isEmpty()) {
                state.addToBag(ItemBridge.id(previous), previous.getCount());
            }
            RunManager.message(player, pokemonName + " is now holding " + itemName
                    + (previous.isEmpty() ? "." : " (" + previous.getName().getString() + " went back to the bag)."), Formatting.GREEN);
        }
        RunManager.get().persist(player, state);
        ShopMenus.bag(player, 0);
    }

    /** Moves a party member's held item into the bag. */
    public static void takeHeld(ServerPlayerEntity player, int partyIndex) {
        RunState state = requireRun(player);
        if (state == null) {
            return;
        }
        List<Pokemon> party = CobblemonBridge.partyMembers(player);
        if (partyIndex >= 0 && partyIndex < party.size()) {
            Pokemon pokemon = party.get(partyIndex);
            ItemStack held = ItemBridge.held(pokemon);
            if (held.isEmpty()) {
                RunManager.message(player, pokemon.getSpecies().getName() + " isn't holding anything.", Formatting.YELLOW);
            } else {
                ItemBridge.swapHeld(pokemon, ItemStack.EMPTY);
                state.addToBag(ItemBridge.id(held), held.getCount());
                RunManager.message(player, "Took " + held.getName().getString() + " from "
                        + pokemon.getSpecies().getName() + ".", Formatting.GREEN);
                RunManager.get().persist(player, state);
            }
        }
        ShopMenus.bag(player, 0);
    }

    public static String displayName(ShopCatalog.Entry entry) {
        return entry.name() != null ? entry.name() : ItemBridge.stack(entry.item()).getName().getString();
    }

    private static RunState requireRun(ServerPlayerEntity player) {
        RunState state = RunManager.isInRun(player) ? RunManager.get().state(player) : null;
        if (state == null) {
            RunManager.message(player, "You need an active run to use the shop.", Formatting.RED);
            return null;
        }
        if (CobblemonBridge.isInBattle(player)) {
            RunManager.message(player, "Finish your battle first.", Formatting.RED);
            return null;
        }
        return state;
    }
}
