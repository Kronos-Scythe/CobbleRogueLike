package org.CobbleUtils.cobbleroguelike.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Which attacking types are super effective against a type (Gen 9 chart). */
public final class TypeChart {

    private static final Map<String, List<String>> SUPER_EFFECTIVE = Map.ofEntries(
            Map.entry("fire", List.of("grass", "ice", "bug", "steel")),
            Map.entry("water", List.of("fire", "ground", "rock")),
            Map.entry("electric", List.of("water", "flying")),
            Map.entry("grass", List.of("water", "ground", "rock")),
            Map.entry("ice", List.of("grass", "ground", "flying", "dragon")),
            Map.entry("fighting", List.of("normal", "ice", "rock", "dark", "steel")),
            Map.entry("poison", List.of("grass", "fairy")),
            Map.entry("ground", List.of("fire", "electric", "poison", "rock", "steel")),
            Map.entry("flying", List.of("grass", "fighting", "bug")),
            Map.entry("psychic", List.of("fighting", "poison")),
            Map.entry("bug", List.of("grass", "psychic", "dark")),
            Map.entry("rock", List.of("fire", "ice", "flying", "bug")),
            Map.entry("ghost", List.of("psychic", "ghost")),
            Map.entry("dragon", List.of("dragon")),
            Map.entry("dark", List.of("psychic", "ghost")),
            Map.entry("steel", List.of("ice", "rock", "fairy")),
            Map.entry("fairy", List.of("fighting", "dragon", "dark")));

    private TypeChart() {
    }

    /** Attacking types that hit {@code defending} super effectively. */
    public static List<String> countersTo(String defending) {
        List<String> result = new ArrayList<>();
        SUPER_EFFECTIVE.forEach((attacking, targets) -> {
            if (targets.contains(defending)) {
                result.add(attacking);
            }
        });
        result.sort(String::compareTo);
        return result;
    }
}
