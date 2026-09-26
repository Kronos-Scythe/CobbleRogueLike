package org.CobbleUtils.cobbleroguelike.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.CobbleUtils.cobbleroguelike.ui.RogueNetwork;

public class CobbleroguelikeClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Payload types are registered by the common initializer; these are the client handlers.
        ClientPlayNetworking.registerGlobalReceiver(RogueNetwork.OpenView.ID, (payload, context) ->
                show(context.client(), new RogueScreen(payload.view())));
        ClientPlayNetworking.registerGlobalReceiver(RogueNetwork.CloseView.ID, (payload, context) -> {
            if (context.client().currentScreen instanceof RogueScreen) {
                show(context.client(), null);
            }
        });
    }

    /** Swaps screens without reporting the old run screen as closed by the player. */
    private static void show(MinecraftClient client, Screen screen) {
        RogueScreen.replacing = true;
        try {
            client.setScreen(screen);
        } finally {
            RogueScreen.replacing = false;
        }
    }
}
