package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Boss teams built around a plan, in the style of Radical Red, Run & Bun and the Kaizo hacks:
 * a <b>setter</b> creates the condition (rain, sun, sand, snow, Trick Room, Tailwind, a terrain),
 * <b>abusers</b> exploit it (Swift Swim, Chlorophyll, slow hard hitters under Trick Room...), and in
 * doubles a <b>support</b> slot brings Fake Out, redirection or Intimidate. Setters lead.
 *
 * <p>Roles are found by ability and learnset, not by hard-coded species, so it works at every
 * level, for every type and with addon Pokémon. If an archetype can't be filled from the pool
 * (no setter), the next preferred one is tried; {@code null} means "use a plain competitive team".
 */
public final class ArchetypeBuilder {

    public enum Speed { ANY, FAST, SLOW }

    /** One team style. {@code setterAbilities} or {@code setterMove} make a setter. */
    public record Def(String id, String name, List<String> setterAbilities, String setterMove, String setterItem,
                      List<String> abuserAbilities, Map<String, Double> typeMultipliers, Set<String> boostedMoves,
                      Set<String> unbanned, Speed speed) {
    }

    public record Result(List<String> team, String archetype) {
    }

    private record Candidate(String properties, String species, Set<String> abilities, Set<String> moves,
                             Set<String> types, int hp, int atk, int def, int spa, int spd, int spe) {
        int bst() {
            return hp + atk + def + spa + spd + spe;
        }

        boolean physical() {
            return atk >= spa;
        }
    }

    public static final Map<String, Def> DEFS = new LinkedHashMap<>();

    static {
        add(new Def("rain", "Rain", List.of("drizzle"), "raindance", "cobblemon:damp_rock",
                List.of("swiftswim", "raindish", "dryskin", "hydration"),
                Map.of("water", 1.5, "fire", 0.5, "electric", 1.1), Set.of("thunder", "hurricane", "weatherball", "electroshot"),
                Set.of(), Speed.ANY));
        add(new Def("sun", "Sun", List.of("drought", "orichalcumpulse"), "sunnyday", "cobblemon:heat_rock",
                List.of("chlorophyll", "solarpower", "flowergift", "protosynthesis", "harvest", "leafguard"),
                Map.of("fire", 1.5, "water", 0.5, "grass", 1.1), Set.of("solarbeam", "solarblade", "weatherball", "morningsun", "synthesis"),
                Set.of("solarbeam", "solarblade"), Speed.ANY));
        add(new Def("sand", "Sandstorm", List.of("sandstream"), "sandstorm", "cobblemon:smooth_rock",
                List.of("sandrush", "sandforce", "sandveil"),
                Map.of("rock", 1.3, "ground", 1.3, "steel", 1.3), Set.of("weatherball", "shoreup"),
                Set.of(), Speed.ANY));
        add(new Def("snow", "Snow", List.of("snowwarning"), "snowscape", "cobblemon:icy_rock",
                List.of("slushrush", "icebody", "snowcloak", "iceface"),
                Map.of("ice", 1.5), Set.of("blizzard", "auroraveil", "weatherball"),
                Set.of(), Speed.ANY));
        add(new Def("trickroom", "Trick Room", List.of(), "trickroom", null,
                List.of(), Map.of(), Set.of(), Set.of(), Speed.SLOW));
        add(new Def("tailwind", "Tailwind", List.of(), "tailwind", "cobblemon:focus_sash",
                List.of(), Map.of("flying", 1.2), Set.of(), Set.of(), Speed.FAST));
        add(new Def("electricterrain", "Electric Terrain", List.of("electricsurge", "hadronengine"), "electricterrain",
                "cobblemon:terrain_extender", List.of("surgesurfer", "quarkdrive"),
                Map.of("electric", 1.5), Set.of("risingvoltage"), Set.of(), Speed.ANY));
        add(new Def("psychicterrain", "Psychic Terrain", List.of("psychicsurge"), "psychicterrain",
                "cobblemon:terrain_extender", List.of(),
                Map.of("psychic", 1.5), Set.of("expandingforce"), Set.of(), Speed.ANY));
        add(new Def("grassyterrain", "Grassy Terrain", List.of("grassysurge"), "grassyterrain",
                "cobblemon:terrain_extender", List.of("grasspelt"),
                Map.of("grass", 1.4), Set.of("grassyglide"), Set.of(), Speed.ANY));
    }

    private static void add(Def def) {
        DEFS.put(def.id(), def);
    }

    /** Doubles support moves, in order of preference. */
    private static final List<String> SUPPORT_MOVES = List.of("fakeout", "followme", "ragepowder", "helpinghand");
    private static final List<String> SUPPORT_ABILITIES = List.of("intimidate", "friendguard");
    private static final int MAX_ANALYZED = 70;

    private ArchetypeBuilder() {
    }

    /** Archetypes that suit a type, best first. A null or empty type means anything goes. */
    public static List<String> preferredFor(String type, Random random) {
        List<String> order = new ArrayList<>(switch (type == null ? "" : type) {
            case "water" -> List.of("rain", "trickroom", "tailwind");
            case "fire" -> List.of("sun", "tailwind", "trickroom");
            case "grass" -> List.of("sun", "grassyterrain", "trickroom");
            case "rock", "ground" -> List.of("sand", "trickroom", "tailwind");
            case "steel" -> List.of("sand", "trickroom");
            case "ice" -> List.of("snow", "trickroom", "tailwind");
            case "electric" -> List.of("electricterrain", "rain", "tailwind");
            case "psychic" -> List.of("psychicterrain", "trickroom");
            case "ghost", "fairy" -> List.of("trickroom", "tailwind");
            case "flying" -> List.of("tailwind", "rain");
            case "bug" -> List.of("tailwind", "sun", "trickroom");
            case "dragon" -> List.of("tailwind", "sand", "rain", "sun");
            case "dark", "fighting", "poison", "normal" -> List.of("tailwind", "trickroom");
            default -> List.of();
        });
        if (order.isEmpty()) {
            order.addAll(DEFS.keySet());
            Collections.shuffle(order, random);
        } else if (order.size() > 1 && random.nextDouble() < 0.3) {
            // Keep leaders a little unpredictable: sometimes lead with the second choice.
            Collections.swap(order, 0, 1);
        }
        return order;
    }

    /**
     * Builds a team of {@code size} from {@code pool} (property strings without level) at
     * {@code level} (the ace; others 1-3 levels lower). Returns null if no archetype fits.
     */
    public static Result build(List<String> pool, int size, int level, List<String> archetypes, boolean doubles,
                               int tier, Random random) {
        if (pool.isEmpty() || size < 2) {
            return null;
        }
        List<String> sample = new ArrayList<>(pool);
        Collections.shuffle(sample, random);
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String properties : sample) {
            if (candidates.size() >= MAX_ANALYZED) {
                break;
            }
            Candidate candidate = analyze(properties, level, tier);
            if (candidate != null && seen.add(candidate.species())) {
                candidates.add(candidate);
            }
        }
        if (candidates.size() < size) {
            return null;
        }
        for (String id : archetypes) {
            Def def = DEFS.get(id);
            if (def != null) {
                Result result = tryBuild(def, candidates, size, level, doubles, tier, random);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Result tryBuild(Def def, List<Candidate> candidates, int size, int level, boolean doubles,
                                   int tier, Random random) {
        // 1. Setter: an ability setter is best (no turn spent), else a move setter.
        Candidate setter = null;
        String setterAbility = null;
        for (Candidate candidate : candidates) {
            for (String ability : def.setterAbilities()) {
                if (candidate.abilities().contains(ability) && (setter == null || candidate.bst() > setter.bst())) {
                    setter = candidate;
                    setterAbility = ability;
                }
            }
        }
        if (setter == null && def.setterMove() != null) {
            Comparator<Candidate> preference = switch (def.speed()) {
                // Trick Room setters want to be slow and bulky; Tailwind setters fast.
                case SLOW -> Comparator.comparingInt(c -> c.hp() + c.def() + c.spd() - c.spe() * 2);
                case FAST -> Comparator.comparingInt(Candidate::spe);
                default -> Comparator.comparingInt(Candidate::bst);
            };
            setter = candidates.stream().filter(c -> c.moves().contains(def.setterMove())).max(preference).orElse(null);
        }
        if (setter == null) {
            return null;
        }
        Candidate chosenSetter = setter;

        // 2. Doubles support: Fake Out / redirection / Intimidate.
        Candidate support = null;
        if (doubles && size >= 3) {
            support = candidates.stream()
                    .filter(c -> c != chosenSetter)
                    .filter(c -> SUPPORT_MOVES.stream().anyMatch(c.moves()::contains)
                            || SUPPORT_ABILITIES.stream().anyMatch(c.abilities()::contains))
                    .max(Comparator.comparingInt(Candidate::bst)).orElse(null);
        }
        Candidate chosenSupport = support;

        // 3. Abusers fill the rest, best last (the ace).
        int abuserCount = size - 1 - (support == null ? 0 : 1);
        List<Candidate> abusers = candidates.stream()
                .filter(c -> c != chosenSetter && c != chosenSupport)
                .sorted(Comparator.comparingDouble((Candidate c) -> abuserScore(def, c)).reversed())
                .limit(abuserCount)
                .sorted(Comparator.comparingDouble((Candidate c) -> abuserScore(def, c)))
                .toList();
        if (abusers.size() < abuserCount) {
            return null;
        }

        List<String> team = new ArrayList<>();
        Set<String> usedItems = new HashSet<>();
        List<Candidate> order = new ArrayList<>();
        order.add(setter);
        if (support != null) {
            order.add(support);
        }
        order.addAll(abusers);
        for (int i = 0; i < order.size(); i++) {
            Candidate member = order.get(i);
            boolean ace = i == order.size() - 1;
            int memberLevel = Math.max(2, ace ? level : level - 1 - random.nextInt(3));
            TeamBuilder.SetPlan plan;
            if (member == setter) {
                plan = setterPlan(def, member, setterAbility, doubles);
            } else if (member == support) {
                plan = supportPlan(def, member);
            } else {
                plan = abuserPlan(def, member);
            }
            boolean giveItem = tier >= 1 || ace || member == setter;
            team.add(TeamBuilder.build(member.properties() + " level=" + memberLevel, tier, doubles, giveItem, usedItems, random, plan));
        }
        return new Result(team, def.name());
    }

    private static double abuserScore(Def def, Candidate c) {
        double score = c.bst();
        if (def.abuserAbilities().stream().anyMatch(c.abilities()::contains)) {
            score += 220;
        }
        for (String type : c.types()) {
            if (def.typeMultipliers().getOrDefault(type, 1.0) > 1.0) {
                score += 90;
            } else if (def.typeMultipliers().getOrDefault(type, 1.0) < 1.0) {
                score -= 120;
            }
        }
        int offense = Math.max(c.atk(), c.spa());
        switch (def.speed()) {
            case SLOW -> score += (c.spe() <= 55 ? 180 : c.spe() >= 90 ? -220 : 0) + offense * 0.5;
            case FAST -> score += (c.spe() >= 90 ? 130 : c.spe() <= 50 ? -150 : 0) + offense * 0.3;
            default -> score += offense * 0.2;
        }
        return score;
    }

    private static TeamBuilder.SetPlan setterPlan(Def def, Candidate setter, String setterAbility, boolean doubles) {
        List<String> required = new ArrayList<>();
        String item = null;
        if (setterAbility == null) {
            required.add(def.setterMove());
            item = def.setterItem();
        } else if (doubles) {
            item = "cobblemon:sitrus_berry";
        }
        if (doubles) {
            required.add("protect");
        }
        if (def.speed() == Speed.SLOW) {
            item = "cobblemon:mental_herb"; // Trick Room setters get taunted otherwise
        }
        return new TeamBuilder.SetPlan(setterAbility, required, item, null, def.speed() == Speed.SLOW,
                def.typeMultipliers(), def.boostedMoves(), def.unbanned());
    }

    private static TeamBuilder.SetPlan supportPlan(Def def, Candidate support) {
        List<String> required = new ArrayList<>();
        for (String move : SUPPORT_MOVES) {
            if (support.moves().contains(move)) {
                required.add(move);
                break;
            }
        }
        required.add("protect");
        String ability = SUPPORT_ABILITIES.stream().filter(support.abilities()::contains).findFirst().orElse(null);
        return new TeamBuilder.SetPlan(ability, required, "cobblemon:sitrus_berry", null, def.speed() == Speed.SLOW,
                def.typeMultipliers(), def.boostedMoves(), def.unbanned());
    }

    private static TeamBuilder.SetPlan abuserPlan(Def def, Candidate abuser) {
        String ability = def.abuserAbilities().stream().filter(abuser.abilities()::contains).findFirst().orElse(null);
        String nature = null;
        if (ability != null && def.speed() == Speed.ANY && !def.abuserAbilities().isEmpty()) {
            // Speed-doubling abilities: go all in on power.
            nature = abuser.physical() ? "adamant" : "modest";
        }
        return new TeamBuilder.SetPlan(ability, List.of(), null, nature, def.speed() == Speed.SLOW,
                def.typeMultipliers(), def.boostedMoves(), def.unbanned());
    }

    private static Candidate analyze(String properties, int level, int tier) {
        try {
            Pokemon pokemon = PokemonProperties.Companion.parse(properties + " level=" + level).create();
            FormData form = pokemon.getForm();
            Set<String> abilities = new HashSet<>();
            for (PotentialAbility ability : form.getAbilities()) {
                abilities.add(ability.getTemplate().getName().toLowerCase(Locale.ROOT));
            }
            Learnset learnset = form.getMoves();
            Set<String> moves = new HashSet<>();
            for (MoveTemplate move : learnset.getLevelUpMovesUpTo(level)) {
                moves.add(move.getName());
            }
            if (tier >= 1) {
                learnset.getTmMoves().forEach(move -> moves.add(move.getName()));
                learnset.getTutorMoves().forEach(move -> moves.add(move.getName()));
                learnset.getEggMoves().forEach(move -> moves.add(move.getName()));
                learnset.getLegacyMoves().forEach(move -> moves.add(move.getName()));
            }
            Set<String> types = new HashSet<>();
            for (ElementalType type : form.getTypes()) {
                types.add(type.getName().toLowerCase(Locale.ROOT));
            }
            Map<Stat, Integer> stats = form.getBaseStats();
            return new Candidate(properties, CobblemonBridge.propertyId(pokemon.getSpecies()), abilities, moves, types,
                    stats.getOrDefault(Stats.HP, 50), stats.getOrDefault(Stats.ATTACK, 50), stats.getOrDefault(Stats.DEFENCE, 50),
                    stats.getOrDefault(Stats.SPECIAL_ATTACK, 50), stats.getOrDefault(Stats.SPECIAL_DEFENCE, 50),
                    stats.getOrDefault(Stats.SPEED, 50));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
