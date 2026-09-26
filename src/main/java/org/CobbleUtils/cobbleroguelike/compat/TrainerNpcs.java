package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawns the temporary Cobblemon NPC a run battle is fought against. Cobblemon only sends out
 * a trainer's Pokémon at battle start if the trainer has an entity, so every run trainer
 * gets one. NPCs are tagged, and any tagged NPC that isn't in a live battle (e.g. left behind
 * by a crash) is removed as soon as it loads.
 */
public final class TrainerNpcs {

    public static final String TAG = "cobbleroguelike_trainer";
    private static final Identifier NPC_CLASS = Identifier.of(Cobbleroguelike.MOD_ID, "rogue_trainer");
    private static final Identifier FALLBACK_CLASS = Identifier.of("cobblemon", "standard");
    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();

    private TrainerNpcs() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(TAG) && !LIVE.contains(entity.getUuid())) {
                entity.discard();
            }
        });
    }

    /** Spawns a trainer a few blocks in front of the player, facing them. Returns null on failure. */
    public static NPCEntity spawn(ServerPlayerEntity player, String name, int level) {
        ServerWorld world = player.getServerWorld();
        Vec3d pos = findSpot(world, player);

        NPCEntity npc = new NPCEntity(world);
        npc.setNpc(npcClass());
        npc.initialize(Math.max(1, level));
        float yaw = (float) (MathHelper.atan2(player.getZ() - pos.z, player.getX() - pos.x) * (180.0 / Math.PI)) - 90.0F;
        npc.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        npc.setCustomName(Text.literal(name));
        npc.setCustomNameVisible(true);
        npc.setAiDisabled(true);
        npc.addCommandTag(TAG);

        LIVE.add(npc.getUuid());
        if (!world.spawnEntity(npc)) {
            LIVE.remove(npc.getUuid());
            return null;
        }
        return npc;
    }

    public static void despawn(NPCEntity npc) {
        if (npc == null) {
            return;
        }
        LIVE.remove(npc.getUuid());
        if (!npc.isRemoved()) {
            npc.discard();
        }
    }

    private static NPCClass npcClass() {
        NPCClass npcClass = NPCClasses.getByIdentifier(NPC_CLASS);
        if (npcClass == null) {
            npcClass = NPCClasses.getByIdentifier(FALLBACK_CLASS);
        }
        return npcClass != null ? npcClass : NPCClasses.INSTANCE.dummy();
    }

    /** A standable spot 6, 4 or 2 blocks ahead of the player, falling back to the player's position. */
    private static Vec3d findSpot(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVector();
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        dir = dir.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : dir.normalize();
        for (int distance : new int[]{6, 4, 2}) {
            Vec3d target = player.getPos().add(dir.multiply(distance));
            BlockPos top = BlockPos.ofFloored(target).up(2);
            for (int dy = 0; dy <= 6; dy++) {
                BlockPos feet = top.down(dy);
                BlockPos below = feet.down();
                if (world.getBlockState(below).isSolidBlock(world, below) && world.isAir(feet) && world.isAir(feet.up())) {
                    return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                }
            }
        }
        return player.getPos();
    }
}
