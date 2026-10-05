package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/**
 * {@code keeps_to} (the Merchant Prince, 29.17): by day the Legend keeps to the Village Hall and the village's Market
 * Square, turns about between them, and walks back when they stray more than {@code radius} blocks from the one they
 * keep to ({@link Legends#tick}, every 10 seconds).
 */
public record KeepsToPower(int radius) implements Power {
	/** How long they stay at one before going to the other: half a morning. */
	static final int TURN = 3000;

	static KeepsToPower read(JsonObject json) {
		int radius = json.has("radius") ? json.get("radius").getAsInt() : 10;
		if (radius < 2) {
			throw new IllegalArgumentException("'radius' below 2");
		}
		return new KeepsToPower(radius);
	}

	@Override
	public String type() {
		return "keeps_to";
	}

	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.keeps_to");
	}

	/** Where {@code villager}, a settled Legend with this power, should be now: the hall or the square; empty at night. */
	public static Optional<BlockPos> place(ServerLevel level, Villager villager, LegendData data) {
		long time = level.getDayTime() % 24000L;
		if (time >= 12000L) {
			return Optional.empty();
		}
		Optional<BlockPos> hall = data.hall().isPresent() ? data.hall() : VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isEmpty()) {
			return Optional.empty();
		}
		Optional<BlockPos> square = MarketDays.square(level, hall.get());
		return square.isPresent() && (time / TURN) % 2 == 1 ? square : hall;
	}

	/** Sends a straying Legend back to the place they keep to. */
	public static void tick(Villager villager) {
		LegendData data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(villager);
		if (data == null || !data.settled() || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Legends.get(data.id()).flatMap(l -> l.powers(KeepsToPower.class).stream().findFirst()).ifPresent(p -> place(level, villager, data).ifPresent(at -> {
			if (villager.blockPosition().distSqr(at) > (double) p.radius() * p.radius()) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(at, 0.5f, 3));
			}
		}));
	}
}
