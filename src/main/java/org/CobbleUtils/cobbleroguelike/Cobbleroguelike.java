package org.CobbleUtils.cobbleroguelike;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.CobbleUtils.cobbleroguelike.command.RogueCommand;
import org.CobbleUtils.cobbleroguelike.guard.RogueGuards;
import org.CobbleUtils.cobbleroguelike.run.RunManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cobbleroguelike implements ModInitializer {

    public static final String MOD_ID = "cobbleroguelike";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        RogueConfig.load();

        ServerLifecycleEvents.SERVER_STARTED.register(RunManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RunManager.onServerStopped());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RunManager.get().onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RunManager.get().onDisconnect(handler.getPlayer()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> RogueCommand.register(dispatcher));

        RogueGuards.register();
    }
}
