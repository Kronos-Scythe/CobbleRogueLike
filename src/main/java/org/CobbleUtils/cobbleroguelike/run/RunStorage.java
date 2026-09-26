package org.CobbleUtils.cobbleroguelike.run;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import org.CobbleUtils.cobbleroguelike.Cobbleroguelike;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Per-player files under {@code <world>/cobbleroguelike/}:
 * <ul>
 *     <li>{@code journals/<uuid>.dat}: the player's real party, written before the swap and
 *     deleted only after it has been restored.</li>
 *     <li>{@code runs/<uuid>.dat}: run progress, written once the swap has completed.</li>
 *     <li>{@code profiles/<uuid>.dat}: Rogue Tokens and lifetime stats.</li>
 * </ul>
 * Writes go to a temp file first, so a crash mid-write never leaves a truncated file behind.
 */
public final class RunStorage {

    private final Path journals;
    private final Path runs;
    private final Path profiles;

    public RunStorage(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT).resolve(Cobbleroguelike.MOD_ID);
        this.journals = root.resolve("journals");
        this.runs = root.resolve("runs");
        this.profiles = root.resolve("profiles");
    }

    public boolean hasJournal(UUID id) {
        return Files.exists(journalFile(id));
    }

    public NbtCompound readJournal(UUID id) throws IOException {
        return read(journalFile(id));
    }

    public void writeJournal(UUID id, NbtCompound tag) throws IOException {
        write(journalFile(id), tag);
    }

    public void deleteJournal(UUID id) throws IOException {
        Files.deleteIfExists(journalFile(id));
    }

    public boolean hasRun(UUID id) {
        return Files.exists(runFile(id));
    }

    public RunState readRun(UUID id) throws IOException {
        return RunState.fromNbt(read(runFile(id)));
    }

    public void writeRun(RunState state) throws IOException {
        write(runFile(state.playerId), state.toNbt());
    }

    public void deleteRun(UUID id) throws IOException {
        Files.deleteIfExists(runFile(id));
    }

    /** Per-player data that outlives runs: Rogue Tokens and stats. Empty if none yet. */
    public NbtCompound readProfile(UUID id) {
        Path file = profiles.resolve(id + ".dat");
        if (!Files.exists(file)) {
            return new NbtCompound();
        }
        try {
            return read(file);
        } catch (IOException e) {
            Cobbleroguelike.LOGGER.error("Failed to read rogue profile {}", id, e);
            return new NbtCompound();
        }
    }

    public void writeProfile(UUID id, NbtCompound tag) throws IOException {
        write(profiles.resolve(id + ".dat"), tag);
    }

    private Path journalFile(UUID id) {
        return journals.resolve(id + ".dat");
    }

    private Path runFile(UUID id) {
        return runs.resolve(id + ".dat");
    }

    private static NbtCompound read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
    }

    private static void write(Path file, NbtCompound tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, tmp);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
