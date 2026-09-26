package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Looks up and rolls rogue biomes (see {@link RogueConfig#biomes}). */
public final class Biomes {

    private static final RogueConfig.RogueBiome FALLBACK = new RogueConfig.RogueBiome(
            "wilds", "The Wilds", "minecraft:grass_block", List.of("#minecraft:is_overworld"), List.of("normal"));

    private Biomes() {
    }

    public static RogueConfig.RogueBiome get(String id) {
        List<RogueConfig.RogueBiome> biomes = RogueConfig.get().biomes;
        for (RogueConfig.RogueBiome biome : biomes) {
            if (biome.id.equals(id)) {
                return biome;
            }
        }
        return biomes.isEmpty() ? FALLBACK : biomes.get(0);
    }

    /** Picks a biome not used yet this run (all of them once every biome has been visited). */
    public static String roll(RunState state, Random random) {
        List<RogueConfig.RogueBiome> biomes = RogueConfig.get().biomes;
        if (biomes.isEmpty()) {
            return FALLBACK.id;
        }
        List<RogueConfig.RogueBiome> options = new ArrayList<>();
        for (RogueConfig.RogueBiome biome : biomes) {
            if (!state.usedBiomes.contains(biome.id)) {
                options.add(biome);
            }
        }
        if (options.isEmpty()) {
            state.usedBiomes.clear();
            options.addAll(biomes);
        }
        String picked = options.get(random.nextInt(options.size())).id;
        state.usedBiomes.add(picked);
        return picked;
    }
}
