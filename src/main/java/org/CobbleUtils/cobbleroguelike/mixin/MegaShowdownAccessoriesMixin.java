package org.CobbleUtils.cobbleroguelike.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import org.CobbleUtils.cobbleroguelike.compat.ItemBridge;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional Mega Showdown integration. Mega Showdown looks for key items (Mega Bracelet, Z-Ring,
 * Dynamax Band, Tera Orb) in the player's hands or Accessories slots. During a run, gimmicks
 * bought in the run shop count as equipped, so no real item is ever handed out and nothing
 * can leak into the main world. All of Mega Showdown's own rules (config toggles, one Mega per
 * battle, Power Spots) still apply.
 *
 * <p>{@code @Pseudo} and {@code require = 0} make this a no-op when Mega Showdown isn't installed.
 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.utils.AccessoriesUtils", remap = false)
public abstract class MegaShowdownAccessoriesMixin {

    @Inject(method = "checkTagInAccessories", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void cobbleroguelike$runGimmickKeyItem(LivingEntity entity, TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayerEntity player && RunManager.hasGimmickTag(player, tag.id().getPath())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "findFirstItemWithTag", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void cobbleroguelike$runTeraOrb(LivingEntity entity, TagKey<Item> tag, CallbackInfoReturnable<ItemStack> cir) {
        if (entity instanceof ServerPlayerEntity player && tag.id().getPath().equals("tera_orb")
                && RunManager.hasGimmickTag(player, "tera_orb")) {
            // A fresh, fully charged orb that only exists for this check.
            ItemStack orb = ItemBridge.stack("mega_showdown:tera_orb");
            if (!orb.isEmpty()) {
                cir.setReturnValue(orb);
            }
        }
    }
}
