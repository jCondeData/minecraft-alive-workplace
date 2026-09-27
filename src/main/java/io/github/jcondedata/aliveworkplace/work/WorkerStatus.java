package io.github.jcondedata.aliveworkplace.work;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;

/**
 * What a worker without a saved job site (lumberjack, ...) is up to, for the line above its head.
 * Set by the worker's behaviour each time it changes; sent to nearby players by BuilderStatusSync
 * while fresh.
 */
public final class WorkerStatus {
	public record Entry(Component title, float progress, Component line, long gameTime) {
	}

	private static final Map<Villager, Entry> STATUS = new WeakHashMap<>();

	public static synchronized void set(Villager villager, Component title, float progress, Component line) {
		STATUS.put(villager, new Entry(title, progress, line, villager.level().getGameTime()));
	}

	public static synchronized Map<Villager, Entry> fresh(long gameTime) {
		STATUS.entrySet().removeIf(e -> e.getKey().isRemoved() || gameTime - e.getValue().gameTime() > 100);
		return Map.copyOf(STATUS);
	}

	private WorkerStatus() {
	}
}
