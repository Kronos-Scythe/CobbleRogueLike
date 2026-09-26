package org.CobbleUtils.cobbleroguelike.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.ui.RewardMenus;

import java.util.ArrayList;
import java.util.List;

/**
 * The /rogue shop: spends Rogue Tokens (earned by finishing runs) on real items that go to the
 * player's inventory. The catalog is {@link RogueConfig#tokenShop}; unknown item ids are hidden.
 */
public final class RewardService {

    public record Entry(String item, int price) {
    }

    public record Category(String name, String icon, List<Entry> items) {
    }

    private RewardService() {
    }

    public static List<Category> catalog() {
        List<Category> result = new ArrayList<>();
        for (RogueConfig.ShopCategory category : RogueConfig.get().tokenShop) {
            List<Entry> entries = new ArrayList<>();
            for (RogueConfig.ShopEntry entry : category.items) {
                if (ItemBridge.exists(entry.item) && entry.price > 0) {
                    entries.add(new Entry(entry.item, entry.price));
                }
            }
            if (!entries.isEmpty()) {
                result.add(new Category(category.name, category.icon, entries));
            }
        }
        return result;
    }

    public static void buy(ServerPlayerEntity player, int categoryIndex, int entryIndex, int quantity, int page) {
        List<Category> catalog = catalog();
        if (categoryIndex < 0 || categoryIndex >= catalog.size()
                || entryIndex < 0 || entryIndex >= catalog.get(categoryIndex).items().size()) {
            RewardMenus.shop(player);
            return;
        }
        Entry entry = catalog.get(categoryIndex).items().get(entryIndex);
        int tokens = RunManager.get().tokens(player);
        int amount = Math.min(quantity, tokens / entry.price());
        if (amount <= 0) {
            RunManager.message(player, "Not enough Rogue Tokens (" + entry.price() + " needed).", Formatting.RED);
        } else if (RunManager.get().changeTokens(player, -amount * entry.price())) {
            ItemStack template = ItemBridge.stack(entry.item());
            int remaining = amount;
            while (remaining > 0) {
                ItemStack stack = template.copy();
                int count = Math.min(remaining, stack.getMaxCount());
                stack.setCount(count);
                player.getInventory().offerOrDrop(stack);
                remaining -= count;
            }
            RunManager.message(player, "Bought " + amount + "x " + template.getName().getString() + " for "
                    + amount * entry.price() + " Rogue Tokens.", Formatting.GREEN);
        } else {
            RunManager.message(player, "Couldn't save your tokens; nothing was bought.", Formatting.RED);
        }
        RewardMenus.category(player, categoryIndex, page);
    }
}
