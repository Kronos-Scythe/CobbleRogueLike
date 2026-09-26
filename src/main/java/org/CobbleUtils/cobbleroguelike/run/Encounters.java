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

        List<Candidate> all = SpawnData.candidates(biome);
        List<Candidate> pool = new ArrayList<>();
        for (Candidate candidate : all) {
            if (candidate.bst() >= Scaling.minBst(level) - 80 && candidate.bst() <= Scaling.maxBst(level) + 20) {
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

        List<String> result = new ArrayList<>();
        for (Candidate picked : pickWeighted(pool, count, random)) {
            result.add(withExtras(picked.properties() + " level=" + level, random));
        }
        return result;
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
            remaining.removeIf(c -> c.species().equals(chosenSpecies));
            if (species.add(chosenSpecies)) {
                picked.add(chosen);
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
