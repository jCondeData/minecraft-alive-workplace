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
		return new Progress(inner.met() ? 0 : 1, 1, Component.translatable("rule.aliveworkplace.not", inner.line()));
	}
}
