package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool;
import com.cobblemon.mod.common.pokemon.Species;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;
import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads Cobblemon's world spawn pool to find what spawns in a rogue biome. Includes regional
 * forms and spawns added by datapacks/addons. Pools are cached and rebuilt after /reload.
 */
public final class SpawnData {

    /** {@code properties} has no level, e.g. {@code "vulpix alolan"}. */
    public record Candidate(String properties, String species, int weight, int bst) {
    }

    private static final Map<String, List<Candidate>> CACHE = new ConcurrentHashMap<>();
    private static MinecraftServer server;

    private SpawnData() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            CACHE.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            CACHE.clear();
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((reloaded, resourceManager, success) -> CACHE.clear());
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static List<Candidate> candidates(RogueConfig.RogueBiome biome) {
        return CACHE.computeIfAbsent(biome.id, key -> build(biome));
    }

    private static List<Candidate> build(RogueConfig.RogueBiome biome) {
        if (server == null) {
            return List.of();
        }
        RogueConfig config = RogueConfig.get();
        Registry<Biome> registry = server.getRegistryManager().get(RegistryKeys.BIOME);
        Set<Identifier> targets = resolveBiomes(registry, biome.biomes);
        if (targets.isEmpty()) {
            return List.of();
        }
        int maxSpread = (int) Math.max(1, registry.size() * config.maxBiomeSpread);

        SpawnPool pool;
        try {
            pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        } catch (RuntimeException notLoaded) {
            return List.of();
        }

        Map<String, Candidate> byProperties = new LinkedHashMap<>();
        for (SpawnDetail detail : pool) {
            if (!(detail instanceof PokemonSpawnDetail pokemonDetail)) {
                continue;
            }
            Set<Identifier> valid = detail.getValidBiomes();
            if (valid.isEmpty() || valid.size() > maxSpread || valid.stream().noneMatch(targets::contains)) {
                continue;
            }
            PokemonProperties properties = pokemonDetail.getPokemon();
            Species species = CobblemonBridge.speciesById(properties.getSpecies());
            if (species == null || !species.getImplemented() || CobblemonBridge.isSpecial(species)) {
                continue;
            }
            String cleaned = clean(CobblemonBridge.propertyId(species), properties.asString(" "));
            Candidate candidate = new Candidate(cleaned, CobblemonBridge.propertyId(species),
                    bucketWeight(detail.getBucket()), CobblemonBridge.bst(species));
            byProperties.merge(cleaned, candidate, (a, b) -> a.weight() >= b.weight() ? a : b);
        }
        List<Candidate> result = new ArrayList<>(byProperties.values());
        Cobbleroguelike.LOGGER.info("Rogue biome '{}': {} spawn candidates from {} biomes", biome.id, result.size(), targets.size());
        return result;
    }

    private static Set<Identifier> resolveBiomes(Registry<Biome> registry, List<String> entries) {
        Set<Identifier> result = new HashSet<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId == null) {
                    continue;
                }
                for (RegistryEntry<Biome> biome : registry.iterateEntries(TagKey.of(RegistryKeys.BIOME, tagId))) {
                    biome.getKey().ifPresent(key -> result.add(key.getValue()));
                }
            } else {
                Identifier id = Identifier.tryParse(entry);
                if (id != null && registry.containsId(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    /** Species first, then form/aspect tokens; drops level, shiny and species keys. */
    private static String clean(String speciesId, String properties) {
        StringBuilder builder = new StringBuilder(speciesId);
        for (String token : properties.split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (token.isBlank() || lower.startsWith("species=") || lower.startsWith("level=") || lower.startsWith("lvl=")
                    || lower.startsWith("l=") || lower.startsWith("shiny") || lower.equals(speciesId)
                    || lower.equals("cobblemon:" + speciesId)) {
                continue;
            }
            builder.append(' ').append(token);
        }
        return builder.toString();
    }

    private static int bucketWeight(String bucket) {
        RogueConfig config = RogueConfig.get();
        return switch (bucket == null ? "" : bucket.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> config.uncommonWeight;
            case "rare" -> config.rareWeight;
            case "ultra-rare", "ultra_rare", "ultrarare" -> config.ultraRareWeight;
            default -> config.commonWeight;
        };
    }
}
