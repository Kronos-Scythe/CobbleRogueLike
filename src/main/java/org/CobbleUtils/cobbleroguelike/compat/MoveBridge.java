package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.api.moves.BenchedMove;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Move Tutor support: what a Pokémon can learn and how to teach it. */
public final class MoveBridge {

    /** {@code free} moves are level-up/benched moves it could already swap in from the summary screen. */
    public record Teachable(String name, Text displayName, String type, String category, int power, int accuracy, boolean free) {
    }

    private MoveBridge() {
    }

    public static List<Teachable> teachable(Pokemon pokemon) {
        Set<String> known = new HashSet<>();
        for (Move move : pokemon.getMoveSet().getMoves()) {
            known.add(move.getTemplate().getName());
        }
        Set<MoveTemplate> free = new LinkedHashSet<>(pokemon.getAllAccessibleMoves());
        Learnset learnset = pokemon.getForm().getMoves();
        Set<MoveTemplate> paid = new LinkedHashSet<>();
        paid.addAll(learnset.getTmMoves());
        paid.addAll(learnset.getTutorMoves());
        paid.addAll(learnset.getEggMoves());
        paid.addAll(learnset.getLegacyMoves());
        paid.removeAll(free);

        List<Teachable> result = new ArrayList<>();
        for (MoveTemplate move : free) {
            if (!known.contains(move.getName())) {
                result.add(describe(move, true));
            }
        }
        for (MoveTemplate move : paid) {
            if (!known.contains(move.getName())) {
                result.add(describe(move, false));
            }
        }
        result.sort(Comparator.comparing(Teachable::free).reversed()
                .thenComparing(Teachable::type)
                .thenComparing(Teachable::name));
        return result;
    }

    /** Current moves as display names; null entries are empty slots. */
    public static List<Text> currentMoves(Pokemon pokemon) {
        List<Text> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Move move = pokemon.getMoveSet().get(i);
            result.add(move == null ? null : move.getDisplayName());
        }
        return result;
    }

    /** Puts the move in the slot. The replaced move is benched so it can be swapped back in from the summary. */
    public static boolean teach(Pokemon pokemon, String moveName, int slot) {
        MoveTemplate template = Moves.getByName(moveName);
        if (template == null || slot < 0 || slot > 3) {
            return false;
        }
        Move old = pokemon.getMoveSet().get(slot);
        pokemon.getMoveSet().setMove(slot, template.create());
        if (old != null) {
            pokemon.getBenchedMoves().add(new BenchedMove(old.getTemplate(), old.getRaisedPpStages()));
        }
        return true;
    }

    private static Teachable describe(MoveTemplate move, boolean free) {
        return new Teachable(move.getName(), move.getDisplayName(),
                move.getElementalType().getName().toLowerCase(Locale.ROOT),
                move.getDamageCategory().getName(),
                (int) Math.round(move.getPower()),
                move.getAccuracy() <= 0 ? -1 : (int) Math.round(move.getAccuracy()), free);
    }
}
