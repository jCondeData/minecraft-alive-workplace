package io.github.jcondedata.aliveworkplace.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** {@code not}: the {@code condition} inside doesn't hold. */
public record Not(Condition condition) implements Condition {
	@Override
	public String type() {
		return "not";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		Progress inner = condition.progress(level, hall);
		if (condition instanceof GiverConditions.OnGiver) {
			return new Progress(0, 1, Component.translatable("rule.aliveworkplace.not", inner.line())); // nobody is asking: neither it nor its opposite holds
		}
		return new Progress(inner.met() ? 0 : 1, 1, Component.translatable("rule.aliveworkplace.not", inner.line()));
	}

	@Override
	public boolean met(ServerLevel level, BlockPos hall, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.npc.Villager giver,
					   @org.jetbrains.annotations.Nullable net.minecraft.server.level.ServerPlayer player) {
		// (a condition on the giver never holds without one, and neither does its opposite)
		return (giver != null || !(condition instanceof GiverConditions.OnGiver)) && !condition.met(level, hall, giver, player);
	}
}
