package org.CobbleUtils.cobbleroguelike.mixin;

import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.CobbleUtils.cobbleroguelike.guard.RogueGuards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void cobbleroguelike$blockCommandsDuringRun(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        if (RogueGuards.shouldBlockCommand(parseResults.getContext().getSource(), command)) {
            ci.cancel();
        }
    }
}
