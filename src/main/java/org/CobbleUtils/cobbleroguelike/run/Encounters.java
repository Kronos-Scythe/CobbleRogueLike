package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;
import org.CobbleUtils.cobbleroguelike.compat.CobblemonBridge;
import org.CobbleUtils.cobbleroguelike.compat.SpawnData;
import org.CobbleUtils.cobbleroguelike.compat.SpawnData.Candidate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Route encounters and biome-themed trainer pools. It tries, in order: the biome's real spawn
 * data within a strength window for the level, then all of the biome's spawn data, then species
 * of the biome's types, then the configured encounter pool.
 */
public final class Encounters {

    private Encounters() {
    }

    /** Route options as property strings with level (and maybe shiny / hidden ability). */
    public static List<String> roll(RunState state, Random random) {
        RogueConfig config = RogueConfig.get();
        RogueConfig.RogueBiome biome = Biomes.get(state.biome);
        int level = Scaling.encounterLevel(state);
        int count = config.encounterOptions;

        List<Candidate> all = flattenRarity(SpawnData.candidates(biome), state.badges);
        // With evolveEncounters, weaker basics are fine late on: they come evolved.
        int minBst = Scaling.minBst(level) - 80 - (config.evolveEncounters ? 120 : 0);
        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : all) {
            if (candidate.bst() >= minBst && candidate.bst() <= Scaling.maxBst(level) + 20) {
                pool.add(candidate);
            }
        }
        if (distinctSpecies(pool) < count) {
            pool = new ArrayList<>(all);
        }
        if (distinctSpecies(pool) < count) {
            for (String type : biome.types) {
                for (String species : CobblemonBridge.speciesPool(type, Scaling.minBst(level) - 80, Scaling.maxBst(level) + 20, false)) {
                    pool.add(new Candidate(species, species, config.commonWeight, 0));
                }
            }
        }
        if (distinctSpecies(pool) < count) {
            for (String species : config.encounterPool) {
                if (CobblemonBridge.speciesExists(species)) {
                    pool.add(new Candidate(species, species, config.commonWeight, 0));
                }
            }
        }

        List<Candidate> picks = pickWeighted(pool, count, random);
        // Variety: sometimes one option wanders in from another biome.
        if (picks.size() >= 2 && random.nextDouble() < config.wanderingChance) {
            Candidate wanderer = wanderer(state, level, minBst, picks, random);
            if (wanderer != null) {
                picks.set(picks.size() - 1, wanderer);
            }
        }
        List<String> result = new ArrayList<>();
        for (Candidate picked : picks) {
            String properties = picked.properties() + " level=" + level;
            if (config.evolveEncounters) {
                properties = CobblemonBridge.evolvedForLevel(properties, level, config.itemEvolutionLevel,
                        config.otherEvolutionLevel, random);
            }
            result.add(withExtras(properties, random));
        }
        return result;
    }

    /** A spawn from a random other biome, within the same strength window, not already offered. */
    private static Candidate wanderer(RunState state, int level, int minBst, List<Candidate> offered, Random random) {
        List<RogueConfig.RogueBiome> biomes = new ArrayList<>(RogueConfig.get().biomes);
        biomes.removeIf(b -> b.id.equals(state.biome));
        if (biomes.isEmpty()) {
            return null;
        }
        RogueConfig.RogueBiome other = biomes.get(random.nextInt(biomes.size()));
        Set<String> taken = new HashSet<>();
        offered.forEach(c -> taken.add(c.species()));
        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : flattenRarity(SpawnData.candidates(other), state.badges)) {
            if (!taken.contains(candidate.species()) && candidate.bst() >= minBst && candidate.bst() <= Scaling.maxBst(level) + 20) {
                pool.add(candidate);
            }
        }
        List<Candidate> picked = pickWeighted(pool, 1, random);
        return picked.isEmpty() ? null : picked.get(0);
    }

    /**
     * Rare spawns get more common with each badge: weights are flattened toward each other
     * (weight^(1 - t), t = encounterRarityPerBadge * badges, at most 0.6).
     */
    private static List<Candidate> flattenRarity(List<Candidate> candidates, int badges) {
        double t = Math.max(0, Math.min(0.6, RogueConfig.get().encounterRarityPerBadge * badges));
        if (t <= 0) {
            return candidates;
        }
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int weight = (int) Math.max(1, Math.round(100 * Math.pow(Math.max(1, candidate.weight()), 1 - t)));
            result.add(new Candidate(candidate.properties(), candidate.species(), weight, candidate.bst()));
        }
        return result;
    }

    /**
     * Rare encounter: 3 strong competitive Pokémon (the config meta pools) at route level. The top
     * pool joins in from {@code metaTopFromBadge} badges and the paradox pool from
     * {@code metaEliteFromBadge}.
     */
    public static List<String> rollMeta(RunState state, Random random) {
        RogueConfig config = RogueConfig.get();
        int level = Scaling.encounterLevel(state);
        List<String> pool = new ArrayList<>();
        for (String species : config.metaPoolStrong) {
            addIfExists(pool, species);
        }
        if (state.badges >= config.metaTopFromBadge) {
            for (String species : config.metaPoolTop) {
                addIfExists(pool, species);
                addIfExists(pool, species); // top picks count twice
            }
        }
        if (state.badges >= config.metaEliteFromBadge) {
            for (String species : config.metaPoolElite) {
                addIfExists(pool, species);
                addIfExists(pool, species);
            }
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (result.size() < config.encounterOptions && !pool.isEmpty()) {
            String picked = pool.remove(random.nextInt(pool.size()));
            if (!seen.add(picked)) {
                continue;
            }
            String properties = picked + " level=" + level;
            if (random.nextDouble() < config.metaHiddenAbilityChance) {
                properties += " hiddenability=yes";
            }
            if (random.nextDouble() < config.shinyChance) {
                properties += " shiny=yes";
            }
            result.add(properties);
        }
        return result.isEmpty() ? roll(state, random) : result;
    }

    private static void addIfExists(List<String> pool, String properties) {
        String species = properties.split(" ")[0];
        if (CobblemonBridge.speciesById(species) != null) {
            pool.add(properties);
        }
    }

    /**
     * Property strings (no level) for a normal trainer in the current biome: the biome's spawns
     * plus species of its types, all within the strength window for {@code level}.
     */
    public static List<String> trainerPool(RunState state, int level) {
        RogueConfig.RogueBiome biome = Biomes.get(state.biome);
        Set<String> pool = new HashSet<>();
        for (Candidate candidate : SpawnData.candidates(biome)) {
            if (candidate.bst() >= Scaling.minBst(level) && candidate.bst() <= Scaling.maxBst(level)) {
                pool.add(candidate.properties());
            }
        }
        for (String type : biome.types) {
            pool.addAll(CobblemonBridge.speciesPool(type, Scaling.minBst(level), Scaling.maxBst(level), false));
        }
        List<String> sorted = new ArrayList<>(pool);
        sorted.sort(String::compareTo); // stable order so seeded rolls stay reproducible
        return sorted;
    }

    /** Adds the shiny / hidden-ability rolls to a route or legendary Pokémon. */
    public static String withExtras(String properties, Random random) {
        RogueConfig config = RogueConfig.get();
        String result = properties;
        if (random.nextDouble() < config.shinyChance) {
            result += " shiny=yes";
        }
        if (random.nextDouble() < config.hiddenAbilityChance) {
            result += " hiddenability=yes";
        }
        return result;
    }

    private static List<Candidate> pickWeighted(List<Candidate> pool, int count, Random random) {
        List<Candidate> remaining = new ArrayList<>(pool);
        List<Candidate> picked = new ArrayList<>();
        Set<String> species = new HashSet<>();
        Set<String> types = new HashSet<>();
        int rerolls = 0;
        while (picked.size() < count && !remaining.isEmpty()) {
            int total = remaining.stream().mapToInt(c -> Math.max(1, c.weight())).sum();
            int roll = random.nextInt(total);
            Candidate chosen = remaining.get(remaining.size() - 1);
            for (Candidate candidate : remaining) {
                roll -= Math.max(1, candidate.weight());
                if (roll < 0) {
                    chosen = candidate;
                    break;
                }
            }
            String chosenSpecies = chosen.species();
            // Variety: skip a second option of the same main type while others are left.
            String type = CobblemonBridge.primaryType(chosenSpecies);
            boolean repeat = type != null && types.contains(type)
                    && remaining.stream().anyMatch(c -> !type.equals(CobblemonBridge.primaryType(c.species())));
            if (repeat && rerolls++ < 8) {
                continue;
            }
            remaining.removeIf(c -> c.species().equals(chosenSpecies));
            if (species.add(chosenSpecies)) {
                picked.add(chosen);
                if (type != null) {
                    types.add(type);
                }
            }
        }
        return picked;
    }

    private static int distinctSpecies(List<Candidate> pool) {
        Set<String> species = new HashSet<>();
        pool.forEach(c -> species.add(c.species()));
        return species.size();
    }
}
