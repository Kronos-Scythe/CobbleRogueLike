package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;

/** Looks up and rolls rogue biomes (see {@link RogueConfig#biomes}). */
public final class Biomes {

    private static final RogueConfig.RogueBiome FALLBACK = new RogueConfig.RogueBiome(
            "wilds", "The Wilds", "minecraft:grass_block", "minecraft:moss_block", List.of("#minecraft:is_overworld"), List.of("normal"));

    /** Blocks for biomes from configs written before {@code block} existed. */
    private static final Map<String, String> DEFAULT_BLOCKS = Map.ofEntries(
            Map.entry("grasslands", "minecraft:moss_block"), Map.entry("forest", "minecraft:oak_log"),
            Map.entry("jungle", "minecraft:jungle_log"), Map.entry("desert", "minecraft:sand"),
            Map.entry("savanna", "minecraft:acacia_log"), Map.entry("mountains", "minecraft:stone"),
            Map.entry("coast", "minecraft:prismarine"), Map.entry("swamp", "minecraft:mud"),
            Map.entry("tundra", "minecraft:snow_block"), Map.entry("caves", "minecraft:deepslate"),
            Map.entry("volcano", "minecraft:magma_block"), Map.entry("mystic", "minecraft:amethyst_block"));

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

    /** The block that themes a biome's screens and encounters. */
    public static String block(String id) {
        RogueConfig.RogueBiome biome = get(id);
        if (biome.block != null && !biome.block.isBlank()) {
            return biome.block;
        }
        return DEFAULT_BLOCKS.getOrDefault(biome.id, "minecraft:moss_block");
    }

    /**
     * Picks a biome for a route card without marking it used: one not visited yet and not in
     * {@code taken} (other cards on the same floor) if possible.
     */
    public static String pick(RunState state, Set<String> taken, Random random) {
        List<RogueConfig.RogueBiome> biomes = RogueConfig.get().biomes;
        if (biomes.isEmpty()) {
            return FALLBACK.id;
        }
        List<String> fresh = new ArrayList<>();
        List<String> any = new ArrayList<>();
        for (RogueConfig.RogueBiome biome : biomes) {
            if (taken.contains(biome.id)) {
                continue;
            }
            any.add(biome.id);
            if (!state.usedBiomes.contains(biome.id)) {
                fresh.add(biome.id);
            }
        }
        List<String> options = !fresh.isEmpty() ? fresh : !any.isEmpty() ? any : List.of(biomes.get(0).id);
        return options.get(random.nextInt(options.size()));
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
