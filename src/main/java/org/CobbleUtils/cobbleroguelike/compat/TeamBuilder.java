package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Turns a plain property string (e.g. {@code "garchomp level=40"}) into a competitive set:
 * chosen moves (STAB, coverage, a support move for doubles), a fitting held item, a nature,
 * and IVs/EVs, depending on the tier.
 *
 * <ul>
 *     <li>Tier 0: the best damaging level-up moves (smarter than Cobblemon's default "last 4 learned").</li>
 *     <li>Tier 1: adds TM/tutor/egg moves, a support move, a held item, a fitting nature and perfect IVs.</li>
 *     <li>Tier 2: adds full EV spreads (252/252/4).</li>
 * </ul>
 */
public final class TeamBuilder {

    /** Moves that are bad without setup, recharge, self-KO, or fail too often for an AI. */
    private static final Set<String> BANNED = Set.of(
            "hyperbeam", "gigaimpact", "blastburn", "frenzyplant", "hydrocannon", "rockwrecker", "roaroftime",
            "prismaticlaser", "eternabeam", "meteorassault", "explosion", "selfdestruct", "mistyexplosion", "memento",
            "finalgambit", "healingwish", "lunardance", "focuspunch", "dreameater", "synchronoise", "lastresort",
            "futuresight", "doomdesire", "solarbeam", "solarblade", "skyattack", "razorwind", "skullbash", "meteorbeam",
            "belch", "steelbeam", "mindblown", "struggle", "fling", "naturalgift", "spitup", "dynamaxcannon",
            "chatter", "shelltrap", "beakblast", "burnup", "doubleironbash", "snore", "sleeptalk", "counter",
            "mirrorcoat", "metalburst", "bide", "present", "magnitude", "hiddenpower", "round", "echoedvoice",
            "fakeout", "firstimpression", "suckerpunch", "thief", "covet", "outrage", "thrash", "petaldance",
            "uproar", "rollout", "iceball", "furycutter", "hyperspacefury", "clangoroussoul");
    private static final List<String> DOUBLES_SUPPORT = List.of(
            "protect", "fakeout", "tailwind", "followme", "ragepowder", "trickroom", "helpinghand", "wideguard",
            "icywind", "electroweb", "snarl", "willowisp", "thunderwave", "spore", "sleeppowder", "detect");
    private static final List<String> SINGLES_SUPPORT = List.of(
            "swordsdance", "nastyplot", "dragondance", "quiverdance", "calmmind", "bulkup", "shellsmash", "shiftgear",
            "recover", "roost", "softboiled", "slackoff", "moonlight", "synthesis", "willowisp", "thunderwave",
            "spore", "toxic", "stealthrock", "protect");

    private TeamBuilder() {
    }

    /**
     * @param usedItems held items already on this team (item clause); updated in place
     * @param giveItem  whether this member gets a held item
     */
    public static String build(String properties, int tier, boolean doubles, boolean giveItem,
                               Set<String> usedItems, Random random) {
        Pokemon pokemon;
        try {
            pokemon = PokemonProperties.Companion.parse(properties).create();
        } catch (RuntimeException e) {
            return properties;
        }
        FormData form = pokemon.getForm();
        int level = pokemon.getLevel();
        Map<Stat, Integer> stats = form.getBaseStats();
        int atk = stats.getOrDefault(Stats.ATTACK, 50);
        int spa = stats.getOrDefault(Stats.SPECIAL_ATTACK, 50);
        int spe = stats.getOrDefault(Stats.SPEED, 50);
        int bulk = stats.getOrDefault(Stats.HP, 50) + stats.getOrDefault(Stats.DEFENCE, 50) + stats.getOrDefault(Stats.SPECIAL_DEFENCE, 50);
        boolean physical = atk >= spa;
        boolean bulky = bulk > atk + spa + spe;
        Set<String> types = new HashSet<>();
        for (ElementalType type : form.getTypes()) {
            types.add(type.getName().toLowerCase(Locale.ROOT));
        }

        Learnset learnset = form.getMoves();
        Set<MoveTemplate> candidates = new LinkedHashSet<>(learnset.getLevelUpMovesUpTo(level));
        if (tier >= 1) {
            candidates.addAll(learnset.getTmMoves());
            candidates.addAll(learnset.getTutorMoves());
            candidates.addAll(learnset.getEggMoves());
            candidates.addAll(learnset.getLegacyMoves());
        }

        String item = giveItem ? pickItem(physical, bulky, spe, doubles, !form.getEvolutions().isEmpty(), usedItems, random) : null;
        boolean choiceLocked = item != null && item.contains("choice_");

        List<String> moves = pickMoves(candidates, types, physical, doubles, tier, choiceLocked);
        StringBuilder result = new StringBuilder(properties);
        if (!moves.isEmpty()) {
            result.append(" moves=").append(String.join(",", moves));
        }
        if (item != null) {
            result.append(" held_item=").append(item);
            usedItems.add(item);
        }
        if (tier >= 1) {
            result.append(" nature=").append(physical ? (spe >= 80 && !bulky ? "jolly" : "adamant") : (spe >= 80 && !bulky ? "timid" : "modest"));
            result.append(" hp_iv=31 attack_iv=31 defence_iv=31 special_attack_iv=31 special_defence_iv=31 speed_iv=31");
        }
        if (tier >= 2) {
            String attackEv = physical ? "attack_ev=252" : "special_attack_ev=252";
            result.append(bulky ? " hp_ev=252 " + attackEv + " defence_ev=4" : " speed_ev=252 " + attackEv + " hp_ev=4");
        }
        return result.toString();
    }

    private static List<String> pickMoves(Set<MoveTemplate> candidates, Set<String> types, boolean physical,
                                          boolean doubles, int tier, boolean choiceLocked) {
        List<MoveTemplate> attacks = new ArrayList<>();
        for (MoveTemplate move : candidates) {
            if (!move.getDamageCategory().getName().equals("status") && move.getPower() > 0 && !BANNED.contains(move.getName())) {
                attacks.add(move);
            }
        }
        List<String> chosen = new ArrayList<>();
        Set<String> chosenTypes = new HashSet<>();
        int attackSlots = tier >= 1 && !choiceLocked ? 3 : 4;
        while (chosen.size() < attackSlots && !attacks.isEmpty()) {
            MoveTemplate best = null;
            double bestScore = -1;
            for (MoveTemplate move : attacks) {
                String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
                double accuracy = move.getAccuracy() <= 0 ? 1.0 : move.getAccuracy() / 100.0;
                double score = move.getPower() * accuracy
                        * (types.contains(type) ? 1.5 : 1.0)
                        * (move.getDamageCategory().getName().equals(physical ? "physical" : "special") ? 1.0 : 0.55)
                        * (chosenTypes.contains(type) ? 0.45 : 1.0)
                        * (move.getPriority() > 0 ? 1.1 : 1.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = move;
                }
            }
            if (best == null) {
                break;
            }
            attacks.remove(best);
            chosen.add(best.getName());
            chosenTypes.add(best.getElementalType().getName().toLowerCase(Locale.ROOT));
        }
        if (chosen.size() < 4 && tier >= 1 && !choiceLocked) {
            Set<String> names = new HashSet<>();
            candidates.forEach(move -> names.add(move.getName()));
            for (String support : doubles ? DOUBLES_SUPPORT : SINGLES_SUPPORT) {
                if (names.contains(support)) {
                    chosen.add(support);
                    break;
                }
            }
        }
        // Fill any remaining slot with the next best attack.
        while (chosen.size() < 4 && !attacks.isEmpty()) {
            chosen.add(attacks.remove(0).getName());
        }
        return chosen;
    }

    private static String pickItem(boolean physical, boolean bulky, int speed, boolean doubles, boolean notFullyEvolved,
                                   Set<String> usedItems, Random random) {
        List<String> options = new ArrayList<>();
        if (notFullyEvolved) {
            options.add("cobblemon:eviolite");
        }
        if (bulky) {
            options.addAll(List.of("cobblemon:leftovers", "cobblemon:sitrus_berry", "cobblemon:assault_vest", "cobblemon:rocky_helmet"));
        } else if (physical) {
            options.addAll(List.of("cobblemon:life_orb", "cobblemon:choice_band", "cobblemon:expert_belt", "cobblemon:muscle_band"));
            if (speed >= 70 && speed <= 100) {
                options.add("cobblemon:choice_scarf");
            }
        } else {
            options.addAll(List.of("cobblemon:life_orb", "cobblemon:choice_specs", "cobblemon:expert_belt", "cobblemon:wise_glasses"));
            if (speed >= 70 && speed <= 100) {
                options.add("cobblemon:choice_scarf");
            }
        }
        if (doubles) {
            options.addAll(List.of("cobblemon:focus_sash", "cobblemon:sitrus_berry", "cobblemon:safety_goggles",
                    "cobblemon:weakness_policy", "cobblemon:clear_amulet", "cobblemon:covert_cloak"));
        }
        options.add("cobblemon:leftovers");
        options.add("cobblemon:sitrus_berry");
        options.removeIf(item -> usedItems.contains(item) || !ItemBridge.exists(item));
        if (options.isEmpty()) {
            return null;
        }
        // Earlier entries fit the role best; weight toward them.
        int index = Math.min(options.size() - 1, (int) Math.floor(Math.abs(random.nextGaussian()) * 1.5));
        return options.get(index);
    }
}
