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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A server-side menu. Items are just icons, and clicking a slot runs its action.
 * <p>Players with the mod installed see it in the run screen ({@link RogueView}, built by
 * {@link RogueViews}); everyone else gets a vanilla chest screen, so the mod still works
 * server-side only.
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
    /** Chest filler for empty slots. */
    private String filler = "minecraft:gray_stained_glass_pane";
    /** Run screen hints: the nav bar row, a themed backdrop, and slots only chests show. */
    private int navRow = -1;
    private String theme = "";
    private boolean themedContent;
    private final Set<Integer> chestOnly = new HashSet<>();

    /** The menu each player currently has open in the run screen. */
    private static final Map<UUID, OpenView> VIEWS = new ConcurrentHashMap<>();
    private static int nextViewId = 1;

    private static final class OpenView {
        final int id;
        final Menu menu;
        boolean clicked;

        OpenView(int id, Menu menu) {
            this.id = id;
            this.menu = menu;
        }
    }

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

    /** Chest filler item for empty slots (e.g. the biome's block on an encounter). */
    public Menu filler(String itemId) {
        this.filler = itemId;
        return this;
    }

    /** Run screen: this row is the nav bar, shown as buttons along the bottom. */
    public Menu navRow(int row) {
        this.navRow = row;
        return this;
    }

    /** Run screen: tile this block behind the run panels, and behind the content too if asked. */
    public Menu theme(String blockId, boolean content) {
        this.theme = blockId;
        this.themedContent = content;
        return this;
    }

    /** Run screen: hide this slot (it repeats something the run screen already shows). */
    public Menu chestOnly(int slot) {
        chestOnly.add(slot);
        return this;
    }

    Text title() {
        return title;
    }

    int navRowIndex() {
        return navRow;
    }

    String themeBlock() {
        return theme;
    }

    boolean themedContent() {
        return themedContent;
    }

    boolean isChestOnly(int slot) {
        return chestOnly.contains(slot);
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
        if (RogueConfig.get().clientScreen && RogueNetwork.hasClientScreen(player)) {
            if (player.currentScreenHandler != player.playerScreenHandler) {
                player.closeHandledScreen();
            }
            int id;
            synchronized (VIEWS) {
                id = nextViewId++;
            }
            VIEWS.put(player.getUuid(), new OpenView(id, this));
            ServerPlayNetworking.send(player, new RogueNetwork.OpenView(RogueViews.build(this, player, id)));
            return;
        }
        VIEWS.remove(player.getUuid());
        ItemStack filler = stack(this.filler, Text.literal(" "), List.of());
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) {
                inventory.setStack(i, filler.copy());
            }
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, p) -> new MenuScreenHandler(syncId, playerInventory, this), title));
    }

    /** A click from the run screen. Stale or repeated clicks are ignored. */
    static void handleClick(ServerPlayerEntity player, int viewId, int slot, int button) {
        OpenView open = VIEWS.get(player.getUuid());
        if (open == null || open.id != viewId) {
            return;
        }
        if (button == RogueNetwork.Click.CLOSED) {
            VIEWS.remove(player.getUuid(), open);
            return;
        }
        ClickAction action = open.menu.action(slot);
        if (open.clicked || action == null) {
            return;
        }
        open.clicked = true;
        action.click(player, button == 1);
        // Nothing new was opened (e.g. just a chat message): the same screen stays usable.
        open.clicked = false;
    }

    /** Closes our menu, whichever way the player sees it. */
    public static void close(ServerPlayerEntity player) {
        if (VIEWS.remove(player.getUuid()) != null) {
            ServerPlayNetworking.send(player, new RogueNetwork.CloseView());
        }
        player.closeHandledScreen();
    }

    /** True if the player has one of our menus open. */
    public static boolean isOpen(ServerPlayerEntity player) {
        return player.currentScreenHandler instanceof MenuScreenHandler || VIEWS.containsKey(player.getUuid());
    }

    public static void forget(UUID player) {
        VIEWS.remove(player);
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
