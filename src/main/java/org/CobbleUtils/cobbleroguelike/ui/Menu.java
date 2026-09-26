package org.CobbleUtils.cobbleroguelike.ui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A server-side chest menu. Items are just icons, and clicking a slot runs its action.
 * It uses the vanilla container screen, so it needs nothing on the client beyond Cobblemon.
 */
public final class Menu {

    private final Text title;
    private final int rows;
    private final SimpleInventory inventory;
    /** A click on a menu slot. {@code rightClick} is true for a right click. */
    @FunctionalInterface
    public interface ClickAction {
        void click(ServerPlayerEntity player, boolean rightClick);
    }

    private final Map<Integer, ClickAction> actions = new HashMap<>();

    public Menu(Text title) {
        this(title, 3);
    }

    /** {@code rows} is 1 to 6. */
    public Menu(Text title, int rows) {
        this.title = title;
        this.rows = Math.max(1, Math.min(6, rows));
        this.inventory = new SimpleInventory(this.rows * 9);
    }

    public Menu button(int slot, ItemStack icon, Consumer<ServerPlayerEntity> action) {
        return clickButton(slot, icon, action == null ? null : (player, rightClick) -> action.accept(player));
    }

    /** Like {@link #button} but the action also learns whether it was a right click. */
    public Menu clickButton(int slot, ItemStack icon, ClickAction action) {
        inventory.setStack(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        }
        return this;
    }

    public Menu icon(int slot, ItemStack icon) {
        return clickButton(slot, icon, null);
    }

    /** Fills a whole row with a separator pane (e.g. a nav bar background). */
    public Menu fillRow(int row, String paneItemId) {
        ItemStack pane = stack(paneItemId, Text.literal(" "), List.of());
        for (int slot = row * 9; slot < row * 9 + 9 && slot < inventory.size(); slot++) {
            inventory.setStack(slot, pane.copy());
            actions.remove(slot);
        }
        return this;
    }

    int rows() {
        return rows;
    }

    SimpleInventory inventory() {
        return inventory;
    }

    ClickAction action(int slot) {
        return actions.get(slot);
    }

    public void open(ServerPlayerEntity player) {
        ItemStack filler = stack("minecraft:gray_stained_glass_pane", Text.literal(" "), List.of());
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, filler.copy());
            }
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new MenuScreenHandler(syncId, playerInventory, this), title));
    }

    /** Builds an icon. {@code itemId} may name a Cobblemon item; unknown ids fall back to paper. */
    public static ItemStack stack(String itemId, Text name, List<Text> lore) {
        Identifier id = Identifier.tryParse(itemId);
        Item item = id == null ? Items.PAPER : Registries.ITEM.get(id);
        if (item == Items.AIR) {
            item = Items.PAPER;
        }
        return stack(new ItemStack(item), name, lore);
    }

    /** Names and describes an existing stack, such as a Pokémon model icon. */
    public static ItemStack stack(ItemStack base, Text name, List<Text> lore) {
        ItemStack stack = base.copy();
        stack.set(DataComponentTypes.CUSTOM_NAME, name.copy().styled(s -> s.withItalic(false)));
        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(lore.stream()
                    // Gray, non-italic by default; a line's own color still wins.
                    .map(line -> (Text) Text.empty().append(line).styled(s -> s.withItalic(false).withColor(Formatting.GRAY)))
                    .toList()));
        }
        return stack;
    }
}
