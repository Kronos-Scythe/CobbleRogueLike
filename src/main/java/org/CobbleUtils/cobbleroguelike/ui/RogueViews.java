package org.CobbleUtils.cobbleroguelike.ui;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.run.Biomes;
import org.CobbleUtils.cobbleroguelike.run.Modifiers;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.CobbleUtils.cobbleroguelike.run.RunState;
import org.CobbleUtils.cobbleroguelike.run.Scaling;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Turns a {@link Menu} (plus the player's run, if any) into a {@link RogueView}. */
final class RogueViews {

    private RogueViews() {
    }

    static RogueView build(Menu menu, ServerPlayerEntity player, int id) {
        RogueView view = new RogueView();
        view.id = id;
        view.title = menu.viewTitle();
        view.theme = menu.themeBlock();
        view.themedContent = menu.themedContent();

        SimpleInventory inventory = menu.inventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            // Skip empty slots, fillers (blank names) and chest-only extras.
            if (stack.isEmpty() || stack.getName().getString().isBlank() || menu.isChestOnly(slot)) {
                continue;
            }
            RogueView.Entry entry = new RogueView.Entry(slot, stack, menu.action(slot) != null);
            switch (menu.roleOf(slot)) {
                case CONTENT -> view.content.add(entry);
                case INFO -> view.info.add(entry);
                case FOOTER -> view.actions.add(entry);
                case BACK -> view.back.add(entry);
            }
        }
        view.layout = menu.layoutType().ordinal();

        RunState state = RunManager.isInRun(player) ? RunManager.get().state(player) : null;
        if (state == null) {
            view.badge = Text.literal(RunManager.get().tokens(player) + " Rogue Tokens").formatted(Formatting.LIGHT_PURPLE);
            return view;
        }
        addRun(view, state, player);
        return view;
    }

    private static void addRun(RogueView view, RunState state, ServerPlayerEntity player) {
        RogueConfig config = RogueConfig.get();
        view.run = true;
        if (view.theme.isEmpty()) {
            view.theme = Biomes.block(state.biome);
        }
        view.badge = Text.literal(state.money + " coins").formatted(Formatting.GOLD);

        view.stats.add(Text.literal("Floor: " + state.floor).formatted(Formatting.AQUA));
        view.stats.add(Text.literal("Badges: " + state.badges + "/" + config.gymCount).formatted(Formatting.YELLOW));
        if (Scaling.championUnlocked(state.badges) && config.eliteCount > 0) {
            view.stats.add(Text.literal("Elite Four: " + state.eliteWins + "/" + config.eliteCount).formatted(Formatting.LIGHT_PURPLE));
        }
        view.stats.add(Text.literal("Level cap: " + Scaling.levelCap(state.badges)).formatted(Formatting.GREEN));
        view.stats.add(Text.literal("Biome: " + Biomes.get(state.biome).name).formatted(Formatting.WHITE));
        view.stats.add(state.isCoop()
                ? Text.literal("Co-op: " + RunManager.get().partnerName(state, player.getUuid())).formatted(Formatting.AQUA)
                : Text.literal("Mode: Solo").formatted(Formatting.AQUA));
        if (!state.modifiers.isEmpty()) {
            view.stats.add(Text.literal(String.join(", ", state.modifiers.stream()
                    .map(mod -> Modifiers.ALL.stream().filter(m -> m.id().equals(mod)).map(Modifiers.Info::name).findFirst().orElse(mod))
                    .toList())).formatted(Formatting.DARK_PURPLE));
        }

        // The tower: this segment's floors with the gym on top, or the Elite Four and the Champion.
        if (Scaling.championUnlocked(state.badges)) {
            for (int i = 0; i < config.eliteCount; i++) {
                view.tower.add("Elite " + (i + 1));
            }
            view.tower.add("Champion");
            view.towerCurrent = Math.min(state.eliteWins, view.tower.size() - 1);
        } else {
            int every = Math.max(1, config.gymEvery);
            int position = Scaling.floorInSegment(state.floor);
            int start = state.floor - position + 1;
            for (int i = 0; i < every; i++) {
                view.tower.add(i == every - 1 ? "Gym " + (state.badges + 1) : String.valueOf(start + i));
            }
            view.towerCurrent = position - 1;
        }

        view.party = members(CobblemonBridge.partyMembers(player));
        UUID other = state.other(player.getUuid());
        ServerPlayerEntity partner = other == null ? null : player.getServer().getPlayerManager().getPlayer(other);
        if (partner != null) {
            view.partnerParty = members(CobblemonBridge.partyMembers(partner));
        }
    }

    private static List<RogueView.Member> members(List<Pokemon> party) {
        List<RogueView.Member> members = new ArrayList<>();
        for (Pokemon pokemon : party) {
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("Lv. " + CobblemonBridge.level(pokemon)).formatted(Formatting.YELLOW));
            lore.add(Text.literal("HP " + CobblemonBridge.healthText(pokemon)).formatted(Formatting.GREEN));
            ItemStack held = CobblemonBridge.heldItem(pokemon);
            if (!held.isEmpty()) {
                lore.add(Text.literal("Held: ").append(held.getName()));
            }
            lore.add(Text.literal("Nature: ").append(CobblemonBridge.natureName(pokemon)));
            lore.add(Text.literal("Ability: ").append(CobblemonBridge.abilityName(pokemon)));
            ItemStack icon = Menu.stack(CobblemonBridge.icon(pokemon), CobblemonBridge.displayName(pokemon).copy().formatted(Formatting.AQUA), lore);
            members.add(new RogueView.Member(icon, CobblemonBridge.level(pokemon), CobblemonBridge.healthFraction(pokemon), held.copy()));
        }
        return members;
    }
}
