package org.CobbleUtils.cobbleroguelike.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;


/** Container screen that never moves items and turns clicks into menu actions. */
public final class MenuScreenHandler extends GenericContainerScreenHandler {

    private final Menu menu;
    /** Only the first click counts, so double clicks or packet spam can't fire an action twice. */
    private boolean clicked = false;

    public MenuScreenHandler(int syncId, PlayerInventory playerInventory, Menu menu) {
        super(typeFor(menu.rows()), syncId, playerInventory, menu.inventory(), menu.rows());
        this.menu = menu;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!clicked && actionType == SlotActionType.PICKUP && player instanceof ServerPlayerEntity serverPlayer
                && slotIndex >= 0 && slotIndex < menu.inventory().size()) {
            Menu.ClickAction action = menu.action(slotIndex);
            if (action != null) {
                clicked = true;
                boolean rightClick = button == 1;
                // Run after the click packet is handled, since actions usually open another screen.
                serverPlayer.getServer().execute(() -> action.click(serverPlayer, rightClick));
            }
        }
        // Undo whatever the client predicted.
        syncState();
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeFor(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            case 6 -> ScreenHandlerType.GENERIC_9X6;
            default -> ScreenHandlerType.GENERIC_9X3;
        };
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
