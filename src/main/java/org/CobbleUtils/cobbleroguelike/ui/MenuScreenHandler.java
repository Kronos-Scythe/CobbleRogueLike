package org.CobbleUtils.cobbleroguelike.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Consumer;

/** Container screen that never moves items and turns clicks into menu actions. */
public final class MenuScreenHandler extends GenericContainerScreenHandler {

    private final Menu menu;
    /** Only the first click counts, so double clicks or packet spam can't fire an action twice. */
    private boolean clicked = false;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory, Menu menu) {
        super(menu.rows() == 6 ? ScreenHandlerType.GENERIC_9X6 : ScreenHandlerType.GENERIC_9X3,
                syncId, playerInventory, menu.inventory(), menu.rows());
        this.menu = menu;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!clicked && actionType == SlotActionType.PICKUP && player instanceof ServerPlayerEntity serverPlayer
                && slotIndex >= 0 && slotIndex < menu.inventory().size()) {
            Consumer<ServerPlayerEntity> action = menu.action(slotIndex);
            if (action != null) {
                clicked = true;
                // Run after the click packet is handled, since actions usually open another screen.
                serverPlayer.getServer().execute(() -> action.accept(serverPlayer));
            }
        }
        // Undo whatever the client predicted.
        syncState();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
