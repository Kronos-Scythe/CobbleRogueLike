package org.CobbleUtils.cobbleroguelike.shop;

import net.fabricmc.loader.api.FabricLoader;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Everything the run shop sells: the configured categories plus, when Mega Showdown is
 * installed, gimmick unlocks, mega stones, Z-crystals and Tera shards (read from its item tags).
 */
public final class ShopCatalog {

    public static final String USE = "use";
    public static final String HOLD = "hold";
    public static final String AUTO = "auto";
    public static final String UNLOCK = "unlock";

    /** Gimmick key to the Mega Showdown item tag path that grants it. */
    public static final Map<String, String> GIMMICK_TAGS = Map.of(
            "mega", "mega_bracelet",
            "z", "z_ring",
            "dynamax", "dynamax_band",
            "tera", "tera_orb");

    /** {@code item} is an item id, or {@code gimmick:<key>} for unlocks. */
    public record Entry(String item, String icon, String name, int price, String action) {
    }

    public record Category(String name, String icon, List<Entry> items) {
    }

    private ShopCatalog() {
    }

    public static boolean megaShowdownLoaded() {
        return FabricLoader.getInstance().isModLoaded("mega_showdown");
    }

    public static List<Category> categories() {
        RogueConfig config = RogueConfig.get();
        List<Category> result = new ArrayList<>();
        for (RogueConfig.ShopCategory category : config.shop) {
            List<Entry> entries = new ArrayList<>();
            for (RogueConfig.ShopEntry entry : category.items) {
                if (ItemBridge.exists(entry.item)) {
                    entries.add(new Entry(entry.item, entry.item, null, entry.price, category.action));
                }
            }
            if (!entries.isEmpty()) {
                result.add(new Category(category.name, category.icon, entries));
            }
        }
        if (config.megaShowdownShop && megaShowdownLoaded()) {
            addMegaShowdown(result, config);
        }
        return result;
    }

    private static void addMegaShowdown(List<Category> result, RogueConfig config) {
        List<Entry> unlocks = new ArrayList<>();
        unlocks.add(new Entry("gimmick:mega", "mega_showdown:mega_bracelet", "Mega Evolution", config.gimmickUnlockPrice, UNLOCK));
        unlocks.add(new Entry("gimmick:z", "mega_showdown:z_ring", "Z-Moves", config.gimmickUnlockPrice, UNLOCK));
        unlocks.add(new Entry("gimmick:tera", "mega_showdown:tera_orb", "Terastallization", config.gimmickUnlockPrice, UNLOCK));
        unlocks.add(new Entry("gimmick:dynamax", "mega_showdown:dynamax_band", "Dynamax", config.gimmickUnlockPrice, UNLOCK));
        result.add(new Category("Gimmicks", "mega_showdown:mega_bracelet", unlocks));
        addTagCategory(result, "Mega Stones", "mega_showdown:mega_stone", config.megaStonePrice, HOLD);
        addTagCategory(result, "Z-Crystals", "mega_showdown:z_crystal", config.zCrystalPrice, HOLD);
        addTagCategory(result, "Tera Shards", "mega_showdown:tera_shard", config.teraShardPrice, USE);
    }

    private static void addTagCategory(List<Category> result, String name, String tag, int price, String action) {
        List<Entry> entries = new ArrayList<>();
        for (String id : ItemBridge.itemsInTag(tag)) {
            entries.add(new Entry(id, id, null, price, action));
        }
        if (!entries.isEmpty()) {
            result.add(new Category(name, entries.get(0).icon(), entries));
        }
    }

    /** How a bag item is applied: the shop entry's action, else "use" if usable, else "hold". */
    public static String actionFor(String itemId) {
        for (Category category : categories()) {
            for (Entry entry : category.items()) {
                if (entry.item().equals(itemId) && !entry.action().equals(AUTO)) {
                    return entry.action();
                }
            }
        }
        return ItemBridge.isUsable(itemId) ? USE : HOLD;
    }
}
