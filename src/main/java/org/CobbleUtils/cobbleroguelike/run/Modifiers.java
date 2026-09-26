package org.CobbleUtils.cobbleroguelike.run;

import org.CobbleUtils.cobbleroguelike.RogueConfig;

import java.util.List;
import java.util.Set;

/** Optional run rules, chosen before a run, each adding a Rogue Token bonus. */
public final class Modifiers {

    public static final String NUZLOCKE = "nuzlocke";
    public static final String SOLO = "solo";
    public static final String HARD = "hard";
    public static final String NO_SHOP = "noshop";
    public static final String ALL_DOUBLES = "alldoubles";

    public record Info(String id, String name, String icon, String description) {
    }

    public static final List<Info> ALL = List.of(
            new Info(NUZLOCKE, "Nuzlocke", "minecraft:skeleton_skull", "Pokémon that faint are gone for good."),
            new Info(SOLO, "Solo", "minecraft:totem_of_undying", "Only your partner: routes give no Pokémon, and legendaries pay coins instead of joining."),
            new Info(HARD, "Hard", "minecraft:netherite_sword", "Every trainer is 2 levels higher (on top of the config difficulty)."),
            new Info(NO_SHOP, "No Shop", "minecraft:barrier", "The run shop is closed."),
            new Info(ALL_DOUBLES, "All Doubles", "minecraft:iron_sword", "Every trainer battle is a double battle."));

    private Modifiers() {
    }

    public static boolean has(RunState state, String id) {
        return state.modifiers.contains(id);
    }

    /** Token bonus as a fraction, e.g. 0.5 for +50%. */
    public static double bonus(String id) {
        RogueConfig config = RogueConfig.get();
        return switch (id) {
            case NUZLOCKE -> config.nuzlockeTokenBonus;
            case SOLO -> config.soloTokenBonus;
            case HARD -> config.hardTokenBonus;
            case NO_SHOP -> config.noShopTokenBonus;
            case ALL_DOUBLES -> config.allDoublesTokenBonus;
            default -> 0.0;
        };
    }

    public static double totalBonus(Set<String> modifiers) {
        return modifiers.stream().mapToDouble(Modifiers::bonus).sum();
    }
}
