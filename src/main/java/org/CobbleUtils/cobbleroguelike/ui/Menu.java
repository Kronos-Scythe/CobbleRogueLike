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

    public static final int ROWS = 3;

    private final Text title;
    private final SimpleInventory inventory = new SimpleInventory(ROWS * 9);
    private final Map<Integer, Consumer<ServerPlayerEntity>> actions = new HashMap<>();

    public Menu(Text title) {
        this.title = title;
    }

    public Menu button(int slot, ItemStack icon, Consumer<ServerPlayerEntity> action) {
        inventory.setStack(slot, icon);
        if (action != null) {
            actions.put(slot, action);
        }
        return this;
    }

    public Menu icon(int slot, ItemStack icon) {
        return button(slot, icon, null);
    }

    SimpleInventory inventory() {
        return inventory;
    }

    Consumer<ServerPlayerEntity> action(int slot) {
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
        ItemStack stack = new ItemStack(item);
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
