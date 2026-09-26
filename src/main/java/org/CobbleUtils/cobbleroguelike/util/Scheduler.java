package org.CobbleUtils.cobbleroguelike.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs tasks on the server thread after a number of ticks. */
public final class Scheduler {

    private record Task(long dueTick, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long tick = 0;

    private Scheduler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tick++;
            if (TASKS.isEmpty()) {
                return;
            }
            List<Task> due = new ArrayList<>();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
                Task task = it.next();
                if (task.dueTick() <= tick) {
                    due.add(task);
                    it.remove();
                }
            }
            due.forEach(task -> task.action().run());
        });
    }

    /** Must be called on the server thread. */
    public static void runLater(int ticks, Runnable action) {
        TASKS.add(new Task(tick + Math.max(1, ticks), action));
    }
}
