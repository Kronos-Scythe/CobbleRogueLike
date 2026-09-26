package org.CobbleUtils.cobbleroguelike.ui;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Packets for the client-side run screen. Players without the mod never get these (see
 * {@link #hasClientScreen}) and keep using chest menus.
 */
public final class RogueNetwork {

    private RogueNetwork() {
    }

    /** Server → client: show (or replace) the run screen. */
    public record OpenView(RogueView view) implements CustomPayload {
        public static final Id<OpenView> ID = new Id<>(Identifier.of("cobbleroguelike", "open_view"));
        public static final PacketCodec<RegistryByteBuf, OpenView> CODEC = PacketCodec.of(
                (payload, buf) -> payload.view().write(buf), buf -> new OpenView(RogueView.read(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server → client: close the run screen (a battle is starting, etc.). */
    public record CloseView() implements CustomPayload {
        public static final Id<CloseView> ID = new Id<>(Identifier.of("cobbleroguelike", "close_view"));
        public static final PacketCodec<RegistryByteBuf, CloseView> CODEC = PacketCodec.unit(new CloseView());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Client → server: a click on a view's entry. {@code button} is 0 (left) or 1 (right);
     * {@link #CLOSED} means the player closed the screen.
     */
    public record Click(int viewId, int slot, int button) implements CustomPayload {
        public static final int CLOSED = -1;
        public static final Id<Click> ID = new Id<>(Identifier.of("cobbleroguelike", "click"));
        public static final PacketCodec<RegistryByteBuf, Click> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeVarInt(payload.viewId());
                    buf.writeVarInt(payload.slot());
                    buf.writeVarInt(payload.button());
                },
                buf -> new Click(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Payload types are registered on both sides; the click handler only on the server. */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(OpenView.ID, OpenView.CODEC);
        PayloadTypeRegistry.playS2C().register(CloseView.ID, CloseView.CODEC);
        PayloadTypeRegistry.playC2S().register(Click.ID, Click.CODEC);
        // Fabric runs play payload handlers on the server thread.
        ServerPlayNetworking.registerGlobalReceiver(Click.ID, (payload, context) ->
                Menu.handleClick(context.player(), payload.viewId(), payload.slot(), payload.button()));
    }

    /** True if the player's client has the mod (and so the run screen). */
    public static boolean hasClientScreen(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, OpenView.ID);
    }
}
