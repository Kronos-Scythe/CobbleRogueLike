package org.CobbleUtils.cobbleroguelike.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.CobbleUtils.cobbleroguelike.ui.RogueNetwork;
import org.CobbleUtils.cobbleroguelike.ui.RogueView;

import java.util.List;

/**
 * The run screen (Battle Tower style): party on the left, run stats and the floor tower in the
 * middle, the current menu's options on the right and the nav bar along the bottom. Everything is
 * drawn from a {@link RogueView}; clicks go back to the server, which decides what happens.
 */
public final class RogueScreen extends Screen {

    // Palette: dark navy panels with colored frames.
    private static final int FRAME_BG = 0xF00E1322;
    private static final int FRAME_OUTER = 0xFF7B4DFF;
    private static final int PANEL_BG = 0xE0151C31;
    private static final int PARTY = 0xFF3FB56B;
    private static final int PARTNER = 0xFF3FB5C8;
    private static final int CENTER = 0xFF3D8BFF;
    private static final int CONTENT = 0xFFA13DFF;
    private static final int GOLD = 0xFFE8B53A;
    private static final int HOVER = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C93A8;

    private static final int ROW_H = 22;
    private static final int ROW_GAP = 2;
    private static final int PARTY_W = 56;
    private static final int CENTER_W = 124;
    private static final int FOOTER_H = 22;

    /** True while the server swaps this screen for another, so that isn't reported as a close. */
    static boolean replacing;

    private final RogueView view;
    private int x0, y0, w, h;
    private int scroll;

    public RogueScreen(RogueView view) {
        super(view.title);
        this.view = view;
    }

    @Override
    protected void init() {
        w = Math.min(420, width - 8);
        h = Math.min(240, height - 8);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int bodyTop() {
        return y0 + 30;
    }

    private int footerTop() {
        return y0 + h - FOOTER_H - 4;
    }

    private int bodyBottom() {
        return footerTop() - 4;
    }

    private int contentX() {
        return view.run ? x0 + 6 + PARTY_W + 4 + CENTER_W + 4 : x0 + 6;
    }

    private int contentW() {
        return x0 + w - 6 - contentX();
    }

    private int columns() {
        return !view.run && view.content.size() > 7 && contentW() >= 300 ? 2 : 1;
    }

    private int visibleRows() {
        return Math.max(1, (bodyBottom() - bodyTop() - 8 + ROW_GAP) / (ROW_H + ROW_GAP));
    }

    private int maxScroll() {
        int rows = (view.content.size() + columns() - 1) / columns();
        return Math.max(0, rows - visibleRows());
    }

    /** Screen rectangle of a content entry, or null if it's scrolled out of view. */
    private int[] entryRect(int index) {
        int cols = columns();
        int row = index / cols - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int innerX = contentX() + 4;
        int innerW = contentW() - 8 - (maxScroll() > 0 ? 6 : 0);
        int colW = (innerW - (cols - 1) * 4) / cols;
        int x = innerX + (index % cols) * (colW + 4);
        int y = bodyTop() + 4 + row * (ROW_H + ROW_GAP);
        return new int[]{x, y, colW, ROW_H};
    }

    private int buttonCount() {
        return view.actions.size() + 1; // + Close
    }

    private int[] buttonRect(int index) {
        int n = buttonCount();
        int bw = Math.min(70, (w - 12 - (n - 1) * 4) / n);
        int y = footerTop();
        if (index == n - 1) {
            return new int[]{x0 + w - 6 - bw, y, bw, FOOTER_H}; // Close sits on the right
        }
        return new int[]{x0 + 6 + index * (bw + 4), y, bw, FOOTER_H};
    }

    private int[] memberRect(int index) {
        int top = bodyTop();
        int tileH = (bodyBottom() - top - 5 * 2) / 6;
        return new int[]{x0 + 6, top + index * (tileH + 2), PARTY_W, tileH};
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Frame
        context.fill(x0, y0, x0 + w, y0 + h, FRAME_BG);
        context.drawBorder(x0, y0, w, h, FRAME_OUTER);
        context.drawBorder(x0 + 1, y0 + 1, w - 2, h - 2, 0xFF2A2060);

        // Header: title tab and badge
        int titleW = Math.min(textRenderer.getWidth(view.title) + 12, w / 2 + 40);
        panel(context, x0 + 6, y0 + 5, titleW, 16, CENTER);
        context.drawText(textRenderer, trim(view.title, titleW - 12), x0 + 12, y0 + 9, TEXT, true);
        if (!view.badge.getString().isEmpty()) {
            int badgeW = textRenderer.getWidth(view.badge) + 12;
            panel(context, x0 + w - 6 - badgeW, y0 + 5, badgeW, 16, GOLD);
            context.drawText(textRenderer, view.badge, x0 + w - badgeW, y0 + 9, TEXT, true);
        }
        context.fill(x0 + 6, y0 + 25, x0 + w - 6, y0 + 26, FRAME_OUTER);

        ItemStack tooltip = null;
        if (view.run) {
            tooltip = renderParty(context, mouseX, mouseY);
            renderCenter(context);
        }
        ItemStack contentTip = renderContent(context, mouseX, mouseY);
        tooltip = tooltip != null ? tooltip : contentTip;
        ItemStack footerTip = renderFooter(context, mouseX, mouseY);
        tooltip = tooltip != null ? tooltip : footerTip;

        if (tooltip != null) {
            context.drawItemTooltip(textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private ItemStack renderParty(DrawContext context, int mouseX, int mouseY) {
        ItemStack tooltip = null;
        for (int i = 0; i < 6; i++) {
            RogueView.Member member = null;
            boolean partner = false;
            if (i < view.party.size()) {
                member = view.party.get(i);
            } else if (i - view.party.size() < view.partnerParty.size()) {
                member = view.partnerParty.get(i - view.party.size());
                partner = true;
            }
            int[] r = memberRect(i);
            panel(context, r[0], r[1], r[2], r[3], member == null ? 0xFF2A3350 : partner ? PARTNER : PARTY);
            if (member == null) {
                continue;
            }
            int iconY = r[1] + (r[3] - 16) / 2;
            context.drawItem(member.icon(), r[0] + 3, iconY);
            context.drawText(textRenderer, "Lv" + member.level(), r[0] + 22, r[1] + 3, 0xFFFFE070, true);
            // HP bar
            int barX = r[0] + 22;
            int barW = r[2] - 25;
            int barY = r[1] + 13;
            context.fill(barX, barY, barX + barW, barY + 3, 0xFF1A1A1A);
            float hp = member.health();
            int hpColor = hp > 0.5F ? 0xFF4CD964 : hp > 0.2F ? 0xFFF5C542 : 0xFFE5484D;
            context.fill(barX, barY, barX + Math.round(barW * hp), barY + 3, hpColor);
            if (!member.held().isEmpty()) {
                drawScaledItem(context, member.held(), r[0] + r[2] - 11, r[1] + r[3] - 10, 0.5F);
            }
            if (inside(mouseX, mouseY, r)) {
                context.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, HOVER);
                tooltip = member.icon();
            }
        }
        return tooltip;
    }

    private void renderCenter(DrawContext context) {
        int x = x0 + 6 + PARTY_W + 4;
        int top = bodyTop();
        int bottom = bodyBottom();
        tile(context, view.theme, x, top, CENTER_W, bottom - top, 0xD0101626);
        context.drawBorder(x, top, CENTER_W, bottom - top, CENTER);

        int y = top + 5;
        for (Text line : view.stats) {
            context.drawText(textRenderer, trim(line, CENTER_W - 10), x + 5, y, TEXT, true);
            y += 10;
        }

        // Tower: bottom row widest, the boss on top.
        int n = view.tower.size();
        if (n == 0) {
            return;
        }
        int areaTop = y + 4;
        int areaBottom = bottom - 5;
        int rowH = Math.max(9, Math.min(14, (areaBottom - areaTop) / n));
        int maxW = CENTER_W - 12;
        int minW = CENTER_W - 44;
        for (int i = 0; i < n; i++) {
            int rowW = n == 1 ? maxW : maxW - (maxW - minW) * i / (n - 1);
            int rx = x + (CENTER_W - rowW) / 2;
            int ry = areaBottom - (i + 1) * rowH;
            if (ry < areaTop) {
                break;
            }
            boolean boss = i == n - 1;
            int fill, border, color;
            if (i < view.towerCurrent) {
                fill = 0xFF24452F;
                border = 0xFF4E8A5E;
                color = 0xFFB9F5C8;
            } else if (i == view.towerCurrent) {
                fill = 0xFFD9B93A;
                border = 0xFFFFF08A;
                color = 0xFF2A2140;
            } else if (boss) {
                fill = 0xFF4E2230;
                border = 0xFFC0485E;
                color = 0xFFFFB0C0;
            } else {
                fill = 0xFF4A3F2A;
                border = 0xFF8A7650;
                color = 0xFFD6B8FF;
            }
            context.fill(rx, ry + 1, rx + rowW, ry + rowH - 1, fill);
            context.drawBorder(rx, ry + 1, rowW, rowH - 2, border);
            String label = view.tower.get(i);
            context.drawText(textRenderer, label, rx + (rowW - textRenderer.getWidth(label)) / 2, ry + (rowH - 8) / 2 + 1, color, false);
        }
    }

    private ItemStack renderContent(DrawContext context, int mouseX, int mouseY) {
        int x = contentX();
        int top = bodyTop();
        int bottom = bodyBottom();
        int cw = contentW();
        if (view.themedContent && !view.theme.isEmpty()) {
            tile(context, view.theme, x, top, cw, bottom - top, 0xA0101626);
        } else {
            context.fill(x, top, x + cw, bottom, PANEL_BG);
        }
        context.drawBorder(x, top, cw, bottom - top, CONTENT);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        ItemStack tooltip = null;
        for (int i = 0; i < view.content.size(); i++) {
            int[] r = entryRect(i);
            if (r == null) {
                continue;
            }
            RogueView.Entry entry = view.content.get(i);
            boolean hovered = inside(mouseX, mouseY, r);
            if (entry.clickable()) {
                context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xE0202A48);
                context.drawBorder(r[0], r[1], r[2], r[3], hovered ? 0xFFFFFFFF : 0xFF5A4A9A);
            } else {
                context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x90000000);
            }
            if (hovered) {
                context.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, HOVER);
                tooltip = entry.stack();
            }
            context.drawItem(entry.stack(), r[0] + 3, r[1] + 3);
            int textX = r[0] + 23;
            int textW = r[2] - 26;
            Text subtitle = firstLore(entry.stack());
            if (subtitle == null) {
                context.drawText(textRenderer, trim(entry.stack().getName(), textW), textX, r[1] + 7, TEXT, true);
            } else {
                context.drawText(textRenderer, trim(entry.stack().getName(), textW), textX, r[1] + 2, TEXT, true);
                context.drawText(textRenderer, trim(subtitle, textW), textX, r[1] + 12, MUTED, false);
            }
        }

        // Scrollbar
        int max = maxScroll();
        if (max > 0) {
            int trackX = x + cw - 8;
            int trackTop = top + 4;
            int trackH = bottom - top - 8;
            context.fill(trackX, trackTop, trackX + 4, trackTop + trackH, 0xFF1A2038);
            int thumbH = Math.max(12, trackH * visibleRows() / (visibleRows() + max));
            int thumbY = trackTop + (trackH - thumbH) * scroll / max;
            context.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFF7B5AC8);
        }
        return tooltip;
    }

    private ItemStack renderFooter(DrawContext context, int mouseX, int mouseY) {
        ItemStack tooltip = null;
        int n = buttonCount();
        for (int i = 0; i < n; i++) {
            int[] r = buttonRect(i);
            boolean close = i == n - 1;
            boolean hovered = inside(mouseX, mouseY, r);
            RogueView.Entry entry = close ? null : view.actions.get(i);
            boolean clickable = close || entry.clickable();
            context.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], clickable ? 0xF01C2440 : 0xC0101420);
            context.drawBorder(r[0], r[1], r[2], r[3], hovered && clickable ? 0xFFFFFFFF : clickable ? 0xFF4A5680 : 0xFF2A3050);
            if (close) {
                Text label = Text.literal("Close");
                context.drawText(textRenderer, label, r[0] + (r[2] - textRenderer.getWidth(label)) / 2, r[1] + 7, TEXT, true);
                continue;
            }
            context.drawItem(entry.stack(), r[0] + 3, r[1] + 3);
            if (r[2] >= 40) {
                context.drawText(textRenderer, trim(entry.stack().getName(), r[2] - 24), r[0] + 21, r[1] + 7, TEXT, true);
            }
            if (hovered) {
                tooltip = entry.stack();
            }
        }
        return tooltip;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int i = 0; i < view.content.size(); i++) {
            int[] r = entryRect(i);
            if (r != null && inside(mouseX, mouseY, r) && view.content.get(i).clickable()) {
                click(view.content.get(i).slot(), button);
                return true;
            }
        }
        int n = buttonCount();
        for (int i = 0; i < n; i++) {
            if (!inside(mouseX, mouseY, buttonRect(i))) {
                continue;
            }
            if (i == n - 1) {
                playClick();
                close();
            } else if (view.actions.get(i).clickable()) {
                click(view.actions.get(i).slot(), button);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        // Closed by the player (Esc, Close, another screen opening): tell the server.
        if (!replacing) {
            try {
                send(RogueNetwork.Click.CLOSED, RogueNetwork.Click.CLOSED);
            } catch (RuntimeException ignored) {
                // disconnecting
            }
        }
        super.removed();
    }

    private void click(int slot, int button) {
        playClick();
        send(slot, button);
    }

    private void send(int slot, int button) {
        if (ClientPlayNetworking.canSend(RogueNetwork.Click.ID)) {
            ClientPlayNetworking.send(new RogueNetwork.Click(view.id, slot, button));
        }
    }

    private static void playClick() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ------------------------------------------------------------------ helpers

    private void panel(DrawContext context, int x, int y, int width, int height, int border) {
        context.fill(x, y, x + width, y + height, PANEL_BG);
        context.drawBorder(x, y, width, height, border);
    }

    /** Tiles a block's texture over a rectangle and darkens it so text stays readable. */
    private void tile(DrawContext context, String blockId, int x, int y, int width, int height, int overlay) {
        Identifier id = blockId.isEmpty() ? null : Identifier.tryParse(blockId);
        Block block = id == null ? Blocks.AIR : Registries.BLOCK.get(id);
        if (block != Blocks.AIR && client != null) {
            Sprite sprite = client.getBlockRenderManager().getModels().getModelParticleSprite(block.getDefaultState());
            context.enableScissor(x, y, x + width, y + height);
            for (int tx = x; tx < x + width; tx += 16) {
                for (int ty = y; ty < y + height; ty += 16) {
                    context.drawSprite(tx, ty, 0, 16, 16, sprite);
                }
            }
            context.disableScissor();
            context.fill(x, y, x + width, y + height, overlay);
        } else {
            context.fill(x, y, x + width, y + height, PANEL_BG);
        }
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int x, int y, float scale) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 200);
        matrices.scale(scale, scale, 1F);
        context.drawItem(stack, 0, 0);
        matrices.pop();
    }

    private OrderedText trim(Text text, int width) {
        if (textRenderer.getWidth(text) <= width) {
            return text.asOrderedText();
        }
        StringVisitable cut = textRenderer.trimToWidth(text, Math.max(0, width - textRenderer.getWidth("…")));
        return Language.getInstance().reorder(StringVisitable.concat(cut, StringVisitable.plain("…")));
    }

    private static Text firstLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return null;
        }
        List<Text> lines = lore.lines();
        for (Text line : lines) {
            if (!line.getString().isBlank()) {
                return line;
            }
        }
        return null;
    }

    private static boolean inside(double mouseX, double mouseY, int[] r) {
        return mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
    }
}
