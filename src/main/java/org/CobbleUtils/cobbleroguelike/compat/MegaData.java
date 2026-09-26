package org.CobbleUtils.cobbleroguelike.compat;

import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which species can Mega Evolve and with which stone, read from Mega Showdown's datapack
 * ({@code data/mega_showdown/mega_showdown/mega/<stone>.json}, e.g. {@code "pokemons": ["Charizard"]}).
 * Only stones that exist as items and are tagged {@code mega_showdown:mega_stone} are used. It is
 * empty when Mega Showdown isn't installed.
 */
public final class MegaData {

    private static Map<String, List<String>> stonesBySpecies;
    private static MinecraftServer server;

    private MegaData() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            server = started;
            stonesBySpecies = null;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> {
            server = null;
            stonesBySpecies = null;
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((reloaded, resourceManager, success) -> stonesBySpecies = null);
    }

    /** Species id (as used in property strings) to its mega stone item ids. */
    public static Map<String, List<String>> stonesBySpecies() {
        if (stonesBySpecies == null) {
            stonesBySpecies = load();
        }
        return stonesBySpecies;
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> result = new HashMap<>();
        if (server == null) {
            return result;
        }
        Set<String> megaStones = new HashSet<>(ItemBridge.itemsInTag("mega_showdown:mega_stone"));
        Map<Identifier, Resource> files = server.getResourceManager()
                .findResources("mega_showdown/mega", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            String stone = file.getKey().getNamespace() + ":" + path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            if (!megaStones.contains(stone)) {
                continue;
            }
            try (Reader reader = file.getValue().getReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray pokemons = json.getAsJsonArray("pokemons");
                if (pokemons == null) {
                    continue;
                }
                for (JsonElement element : pokemons) {
                    String name = element.getAsString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    Species species = CobblemonBridge.speciesById(name);
                    if (species != null) {
                        result.computeIfAbsent(CobblemonBridge.propertyId(species), key -> new ArrayList<>()).add(stone);
                    }
                }
            } catch (Exception e) {
                Cobbleroguelike.LOGGER.warn("Couldn't read Mega Showdown mega data {}", file.getKey(), e);
            }
        }
        if (!result.isEmpty()) {
            Cobbleroguelike.LOGGER.info("Loaded {} Mega-capable species from Mega Showdown", result.size());
        }
        return result;
    }
}
