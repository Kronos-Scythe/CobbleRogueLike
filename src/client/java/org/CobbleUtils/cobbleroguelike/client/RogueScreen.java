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

import java.util.ArrayList;
import java.util.List;

/**
 * The run screen (Battle Tower style). In a run: party on the left, the options in the middle,
 * and the progress (stats and the floor tower) on the right. Outside a run the options take the whole width. Close sits
 * in the bottom-left corner, Back (when the screen has one) in the bottom-right, and footer
 * buttons in between. Everything is drawn from a {@link RogueView}; clicks go back to the server.
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
    private static final int MUTED = 0xFFA8AEC2;
    private static final int BUTTON_BG = 0xF01C2440;
    private static final int BUTTON_BORDER = 0xFF4A5680;
    private static final int CARD_BG = 0xE0202A48;
    private static final int CARD_BORDER = 0xFF5A4A9A;
    private static final int INFO_BG = 0x90000000;
    private static final int INFO_BORDER = 0xFF3A4570;

    // Menu.Layout ordinals
    private static final int CARDS = 1;
    private static final int GRID = 2;
    private static final int PAGE = 3;

    private static final int ROW_H = 22;
    private static final int GAP = 4;
    private static final int PARTY_W = 56;
    private static final int CENTER_W = 124;
    private static final int FOOTER_H = 22;
    private static final int PAGE_STEP = 12;

    /** True while the server swaps this screen for another, so that isn't reported as a close. */
    static boolean replacing;

    private final RogueView view;
    /** Layout size in screen units; the panel is drawn at {@link #scale} times this. */
    private static final int BASE_W = 460;
    private static final int BASE_H = 240;

    /** Panel bounds in layout units (x0, y0 are 0: the matrix moves the panel into place). */
    private int x0, y0, w, h;
    /** Layout-to-GUI scale and the panel's top-left corner in GUI coordinates. */
    private float scale = 1F;
    private float originX, originY;
    private int scroll;
    private int maxScroll;
    /** Clickable areas from the last frame. */
    private final List<Hit> hits = new ArrayList<>();

    /** A clickable area: a menu slot, or the Close button (slot = -1). */
    private record Hit(int x, int y, int w, int h, int slot) {
    }

    public RogueScreen(RogueView view) {
        super(view.title);
        this.view = view;
    }

    @Override
    protected void init() {
        // Fill most of the window whatever the GUI scale is, in whole screen-pixel steps so the
        // font stays crisp: at GUI scale 2 the panel is drawn 1.5x or 2x, at scale 4 it's 1x.
        double guiScale = client != null ? client.getWindow().getScaleFactor() : 1.0;
        double fit = Math.min(width * 0.9 / BASE_W, height * 0.9 / BASE_H);
        double pixels = Math.floor(fit * guiScale);
        scale = (float) Math.max(1.0, pixels / guiScale);
        w = scale > 1F ? BASE_W : Math.min(BASE_W, width - 8);
        h = scale > 1F ? BASE_H : Math.min(BASE_H, height - 8);
        x0 = 0;
        y0 = 0;
        originX = (width - w * scale) / 2F;
        originY = (height - h * scale) / 2F;
    }

    /** GUI mouse coordinates to layout units. */
    private double layoutX(double mouseX) {
        return (mouseX - originX) / scale;
    }

    private double layoutY(double mouseY) {
        return (mouseY - originY) / scale;
    }

    /** Scissor rectangles are in GUI coordinates (the matrix doesn't apply to them). */
    private void scissor(DrawContext context, int x1, int y1, int x2, int y2) {
        context.enableScissor((int) Math.floor(originX + x1 * scale), (int) Math.floor(originY + y1 * scale),
                (int) Math.ceil(originX + x2 * scale), (int) Math.ceil(originY + y2 * scale));
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
        return y0 + h - FOOTER_H - 5;
    }

    private int bodyBottom() {
        return footerTop() - 5;
    }

    /** Options in the middle (between the party and the progress panel in a run). */
    private int contentX() {
        return view.run ? x0 + 6 + PARTY_W + GAP : x0 + 6;
    }

    private int contentW() {
        return view.run ? w - 12 - PARTY_W - CENTER_W - 2 * GAP : w - 12;
    }

    /** The progress panel (stats and floor tower) on the right. */
    private int progressX() {
        return x0 + w - 6 - CENTER_W;
    }

    private int[] memberRect(int index) {
        int top = bodyTop();
        int tileH = (bodyBottom() - top - 5 * 2) / 6;
        return new int[]{x0 + 6, top + index * (tileH + 2), PARTY_W, tileH};
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext context, int guiMouseX, int guiMouseY, float delta) {
        super.render(context, guiMouseX, guiMouseY, delta);
        hits.clear();
        int mouseX = (int) Math.floor(layoutX(guiMouseX));
        int mouseY = (int) Math.floor(layoutY(guiMouseY));
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(originX, originY, 0);
        matrices.scale(scale, scale, 1F);

        context.fill(x0, y0, x0 + w, y0 + h, FRAME_BG);
        context.drawBorder(x0, y0, w, h, FRAME_OUTER);
        context.drawBorder(x0 + 1, y0 + 1, w - 2, h - 2, 0xFF2A2060);

        // Header: title tab and badge
        int badgeW = view.badge.getString().isEmpty() ? 0 : textRenderer.getWidth(view.badge) + 12;
        int titleW = Math.min(textRenderer.getWidth(view.title) + 12, w - 20 - badgeW);
        panel(context, x0 + 6, y0 + 5, titleW, 16, CENTER);
        context.drawText(textRenderer, trim(view.title, titleW - 12), x0 + 12, y0 + 9, TEXT, true);
        if (badgeW > 0) {
            panel(context, x0 + w - 6 - badgeW, y0 + 5, badgeW, 16, GOLD);
            context.drawText(textRenderer, view.badge, x0 + w - badgeW, y0 + 9, TEXT, true);
        }
        context.fill(x0 + 6, y0 + 25, x0 + w - 6, y0 + 26, FRAME_OUTER);

        Tooltip tooltip = new Tooltip();
        if (view.run) {
            renderParty(context, mouseX, mouseY, tooltip);
            renderProgress(context);
        }
        renderContent(context, mouseX, mouseY, tooltip);
        renderFooter(context, mouseX, mouseY, tooltip);
        matrices.pop();

        // Tooltips at normal size, at the real mouse position.
        if (tooltip.stack != null) {
            context.drawItemTooltip(textRenderer, tooltip.stack, guiMouseX, guiMouseY);
        }
    }

    /** The first hovered stack wins. */
    private static final class Tooltip {
        ItemStack stack;

        void offer(ItemStack candidate) {
            if (stack == null) {
                stack = candidate;
            }
        }
    }

    private void renderParty(DrawContext context, int mouseX, int mouseY, Tooltip tooltip) {
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
            context.drawItem(member.icon(), r[0] + 3, r[1] + (r[3] - 16) / 2);
            context.drawText(textRenderer, "Lv" + member.level(), r[0] + 22, r[1] + 3, 0xFFFFE070, true);
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
            if (inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
                context.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, HOVER);
                tooltip.offer(member.icon());
            }
        }
    }

    private void renderProgress(DrawContext context) {
        int x = progressX();
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

    private void renderContent(DrawContext context, int mouseX, int mouseY, Tooltip tooltip) {
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

        int y = top + GAP;
        if (!view.info.isEmpty()) {
            y = renderInfo(context, x + GAP, y, cw - 2 * GAP, mouseX, mouseY, tooltip) + GAP;
        }
        int ax = x + GAP;
        int aw = cw - 2 * GAP;
        int ah = bottom - GAP - y;
        if (ah <= 0 || view.content.isEmpty()) {
            maxScroll = 0;
            return;
        }
        scissor(context, ax, y, ax + aw, y + ah);
        switch (view.layout) {
            case CARDS -> renderCards(context, ax, y, aw, ah, mouseX, mouseY, tooltip);
            case GRID -> renderGrid(context, ax, y, aw, ah, mouseX, mouseY, tooltip);
            case PAGE -> renderPage(context, ax, y, aw, ah);
            default -> renderList(context, ax, y, aw, ah, mouseX, mouseY, tooltip);
        }
        context.disableScissor();
        renderScrollbar(context, x + cw - 3, y, ah);
    }

    /** Description boxes side by side; returns the bottom edge. */
    private int renderInfo(DrawContext context, int x, int y, int width, int mouseX, int mouseY, Tooltip tooltip) {
        int n = Math.min(3, view.info.size());
        int boxW = (width - (n - 1) * GAP) / n;
        int maxLines = n == 1 ? 5 : 4;
        int boxH = 22;
        List<List<OrderedText>> bodies = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<OrderedText> lines = wrapLore(view.info.get(i).stack(), boxW - 26);
            bodies.add(lines);
            boxH = Math.max(boxH, 16 + Math.min(lines.size(), maxLines) * 9 + 3);
        }
        for (int i = 0; i < n; i++) {
            RogueView.Entry entry = view.info.get(i);
            int bx = x + i * (boxW + GAP);
            context.fill(bx, y, bx + boxW, y + boxH, INFO_BG);
            context.drawBorder(bx, y, boxW, boxH, INFO_BORDER);
            context.drawItem(entry.stack(), bx + 3, y + 3);
            context.drawText(textRenderer, trim(entry.stack().getName(), boxW - 26), bx + 23, y + 4, TEXT, true);
            List<OrderedText> lines = bodies.get(i);
            for (int l = 0; l < lines.size() && l < maxLines; l++) {
                context.drawText(textRenderer, lines.get(l), bx + 23, y + 15 + l * 9, MUTED, false);
            }
            if (lines.size() > maxLines && inside(mouseX, mouseY, bx, y, boxW, boxH)) {
                tooltip.offer(entry.stack());
            }
        }
        return y + boxH;
    }

    private void renderList(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, Tooltip tooltip) {
        int visible = Math.max(1, (height + 2) / (ROW_H + 2));
        maxScroll = Math.max(0, view.content.size() - visible);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int rowW = width - (maxScroll > 0 ? 6 : 0);
        for (int i = scroll; i < view.content.size() && i < scroll + visible; i++) {
            RogueView.Entry entry = view.content.get(i);
            int ry = y + (i - scroll) * (ROW_H + 2);
            boolean hovered = drawBox(context, entry, x, ry, rowW, ROW_H, mouseX, mouseY);
            context.drawItem(entry.stack(), x + 3, ry + 3);
            Text subtitle = firstLore(entry.stack());
            int textW = rowW - 26;
            if (subtitle == null) {
                context.drawText(textRenderer, trim(entry.stack().getName(), textW), x + 23, ry + 7, TEXT, true);
            } else {
                context.drawText(textRenderer, trim(entry.stack().getName(), textW), x + 23, ry + 2, TEXT, true);
                context.drawText(textRenderer, trim(subtitle, textW), x + 23, ry + 12, MUTED, false);
            }
            if (hovered) {
                tooltip.offer(entry.stack());
            }
        }
    }

    /** A few big cards, centered: up to 4 in a row (5 or more wrap into rows of 3-5). */
    private void renderCards(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, Tooltip tooltip) {
        maxScroll = 0;
        int n = view.content.size();
        int perRow = n <= 4 ? n : n <= 6 ? 3 : n <= 8 ? 4 : 5;
        int rows = (n + perRow - 1) / perRow;
        int cardW = Math.min(150, (width - (perRow - 1) * 6) / perRow);
        int cardH = Math.min(120, (height - (rows - 1) * 6) / rows);
        if (cardH < 40) {
            renderList(context, x, y, width, height, mouseX, mouseY, tooltip);
            return;
        }
        int blockH = rows * cardH + (rows - 1) * 6;
        int startY = y + Math.max(0, (height - blockH) / 2);
        boolean bigIcon = cardH >= 80;
        for (int i = 0; i < n; i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, n - row * perRow);
            int rowW = inRow * cardW + (inRow - 1) * 6;
            int cx = x + (width - rowW) / 2 + (i % perRow) * (cardW + 6);
            int cy = startY + row * (cardH + 6);
            RogueView.Entry entry = view.content.get(i);
            boolean hovered = drawBox(context, entry, cx, cy, cardW, cardH, mouseX, mouseY);

            int ty;
            if (bigIcon) {
                drawScaledItem(context, entry.stack(), cx + (cardW - 32) / 2F, cy + 6, 2F);
                ty = cy + 42;
            } else {
                context.drawItem(entry.stack(), cx + (cardW - 16) / 2, cy + 4);
                ty = cy + 23;
            }
            List<OrderedText> name = textRenderer.wrapLines(entry.stack().getName(), cardW - 8);
            for (int l = 0; l < name.size() && l < 2; l++) {
                OrderedText line = name.get(l);
                context.drawText(textRenderer, line, cx + (cardW - textRenderer.getWidth(line)) / 2, ty, TEXT, true);
                ty += 10;
            }
            ty += 2;
            List<OrderedText> lore = wrapLore(entry.stack(), cardW - 10);
            boolean truncated = name.size() > 2;
            for (OrderedText line : lore) {
                if (ty + 9 > cy + cardH - 2) {
                    truncated = true;
                    break;
                }
                context.drawText(textRenderer, line, cx + (cardW - textRenderer.getWidth(line)) / 2, ty, MUTED, false);
                ty += 9;
            }
            if (hovered && truncated) {
                tooltip.offer(entry.stack());
            }
        }
    }

    /** Icon tiles at their chest positions (9 wide), details on hover. */
    private void renderGrid(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, Tooltip tooltip) {
        maxScroll = 0;
        int minRow = Integer.MAX_VALUE;
        int maxRow = 0;
        for (RogueView.Entry entry : view.content) {
            minRow = Math.min(minRow, entry.slot() / 9);
            maxRow = Math.max(maxRow, entry.slot() / 9);
        }
        int rows = maxRow - minRow + 1;
        int tile = Math.max(18, Math.min(40, Math.min(width / 9, height / rows)));
        int startX = x + (width - tile * 9) / 2;
        int startY = y + Math.max(0, (height - tile * rows) / 2);
        float scale = tile >= 38 ? 2F : tile >= 28 ? 1.5F : 1F;
        int icon = Math.round(16 * scale);
        for (RogueView.Entry entry : view.content) {
            int tx = startX + (entry.slot() % 9) * tile;
            int ty = startY + (entry.slot() / 9 - minRow) * tile;
            boolean hovered = drawBox(context, entry, tx + 1, ty + 1, tile - 2, tile - 2, mouseX, mouseY);
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(tx + (tile - icon) / 2F, ty + (tile - icon) / 2F, 0);
            matrices.scale(scale, scale, 1F);
            context.drawItem(entry.stack(), 0, 0);
            context.drawItemInSlot(textRenderer, entry.stack(), 0, 0);
            matrices.pop();
            if (hovered) {
                tooltip.offer(entry.stack());
            }
        }
    }

    /** Text sections: icon and heading, then the full lore. Scrolls. */
    private void renderPage(DrawContext context, int x, int y, int width, int height) {
        int total = 0;
        List<List<OrderedText>> bodies = new ArrayList<>();
        for (RogueView.Entry entry : view.content) {
            List<OrderedText> lines = wrapLore(entry.stack(), width - 32);
            bodies.add(lines);
            total += 18 + lines.size() * 10 + 8;
        }
        maxScroll = Math.max(0, (total - height + PAGE_STEP - 1) / PAGE_STEP);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int cy = y - scroll * PAGE_STEP;
        for (int i = 0; i < view.content.size(); i++) {
            RogueView.Entry entry = view.content.get(i);
            List<OrderedText> lines = bodies.get(i);
            int sectionH = 18 + lines.size() * 10;
            context.fill(x, cy, x + width - 6, cy + sectionH + 2, INFO_BG);
            context.drawItem(entry.stack(), x + 3, cy + 1);
            context.drawText(textRenderer, trim(entry.stack().getName(), width - 32), x + 23, cy + 5, TEXT, true);
            for (int l = 0; l < lines.size(); l++) {
                context.drawText(textRenderer, lines.get(l), x + 23, cy + 18 + l * 10, MUTED, false);
            }
            cy += sectionH + 8;
        }
    }

    private void renderScrollbar(DrawContext context, int x, int top, int height) {
        if (maxScroll <= 0) {
            return;
        }
        context.fill(x - 4, top, x, top + height, 0xFF1A2038);
        int thumbH = Math.max(12, height / (maxScroll + 1));
        int thumbY = top + (height - thumbH) * scroll / maxScroll;
        context.fill(x - 4, thumbY, x, thumbY + thumbH, 0xFF7B5AC8);
    }

    /** Close in the bottom-left, Back in the bottom-right, footer buttons centered between. */
    private void renderFooter(DrawContext context, int mouseX, int mouseY, Tooltip tooltip) {
        int y = footerTop();
        int left = x0 + 6;
        int right = x0 + w - 6;

        Text close = Text.literal("Close");
        int closeW = textRenderer.getWidth(close) + 20;
        textButton(context, close, left, y, closeW, mouseX, mouseY, 0xFF6A4A5A);
        hits.add(new Hit(left, y, closeW, FOOTER_H, -1));
        left += closeW + GAP;

        if (!view.back.isEmpty()) {
            RogueView.Entry back = view.back.get(0);
            Text label = Text.literal("◀ ").append(back.stack().getName());
            int backW = textRenderer.getWidth(label) + 16;
            right -= backW;
            textButton(context, label, right, y, backW, mouseX, mouseY, 0xFF4A6A9A);
            hits.add(new Hit(right, y, backW, FOOTER_H, back.slot()));
            right -= GAP;
        }

        int n = view.actions.size();
        if (n == 0) {
            return;
        }
        int space = right - left;
        int labeled = 0;
        for (RogueView.Entry entry : view.actions) {
            labeled += Math.min(110, textRenderer.getWidth(entry.stack().getName()) + 26);
        }
        labeled += (n - 1) * GAP;
        boolean withLabels = labeled <= space;
        int total = withLabels ? labeled : n * FOOTER_H + (n - 1) * GAP;
        int bx = left + Math.max(0, (space - total) / 2);
        for (RogueView.Entry entry : view.actions) {
            int bw = withLabels ? Math.min(110, textRenderer.getWidth(entry.stack().getName()) + 26) : FOOTER_H;
            boolean hovered = inside(mouseX, mouseY, bx, y, bw, FOOTER_H);
            context.fill(bx, y, bx + bw, y + FOOTER_H, entry.clickable() ? BUTTON_BG : 0xC0101420);
            context.drawBorder(bx, y, bw, FOOTER_H, hovered && entry.clickable() ? 0xFFFFFFFF : entry.clickable() ? BUTTON_BORDER : 0xFF2A3050);
            context.drawItem(entry.stack(), bx + 3, y + 3);
            if (withLabels) {
                context.drawText(textRenderer, trim(entry.stack().getName(), bw - 24), bx + 21, y + 7, TEXT, true);
            }
            if (entry.clickable()) {
                hits.add(new Hit(bx, y, bw, FOOTER_H, entry.slot()));
            }
            if (hovered) {
                tooltip.offer(entry.stack());
            }
            bx += bw + GAP;
        }
    }

    private void textButton(DrawContext context, Text label, int x, int y, int width, int mouseX, int mouseY, int border) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, FOOTER_H);
        context.fill(x, y, x + width, y + FOOTER_H, BUTTON_BG);
        context.drawBorder(x, y, width, FOOTER_H, hovered ? 0xFFFFFFFF : border);
        context.drawText(textRenderer, label, x + (width - textRenderer.getWidth(label)) / 2, y + 7, TEXT, true);
    }

    /** Background and border for an entry; registers clickable ones. Returns whether it's hovered. */
    private boolean drawBox(DrawContext context, RogueView.Entry entry, int x, int y, int width, int height, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        if (entry.clickable()) {
            context.fill(x, y, x + width, y + height, CARD_BG);
            context.drawBorder(x, y, width, height, hovered ? 0xFFFFFFFF : CARD_BORDER);
            hits.add(new Hit(x, y, width, height, entry.slot()));
        } else {
            context.fill(x, y, x + width, y + height, INFO_BG);
        }
        if (hovered) {
            context.fill(x + 1, y + 1, x + width - 1, y + height - 1, HOVER);
        }
        return hovered;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double guiMouseX, double guiMouseY, int button) {
        double mouseX = layoutX(guiMouseX);
        double mouseY = layoutY(guiMouseY);
        if (button == 0 || button == 1) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    playClick();
                    if (hit.slot() < 0) {
                        close();
                    } else {
                        send(hit.slot(), button);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(guiMouseX, guiMouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(verticalAmount)));
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
            scissor(context, x, y, x + width, y + height);
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

    private void drawScaledItem(DrawContext context, ItemStack stack, float x, float y, float scale) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
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

    /** All non-blank lore lines, wrapped to a width. */
    private List<OrderedText> wrapLore(ItemStack stack, int width) {
        List<OrderedText> lines = new ArrayList<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return lines;
        }
        for (Text line : lore.lines()) {
            if (!line.getString().isBlank()) {
                lines.addAll(textRenderer.wrapLines(line, Math.max(20, width)));
            }
        }
        return lines;
    }

    private static Text firstLore(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return null;
        }
        for (Text line : lore.lines()) {
            if (!line.getString().isBlank()) {
                return line;
            }
        }
        return null;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
