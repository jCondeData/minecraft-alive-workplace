package io.github.jcondedata.aliveworkplace.mc;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Ids. Change: {@code ResourceKey.location()} is renamed {@code identifier()} in 1.21.11 (porting.md). */
public final class Ids {
	/** The id a registry key points at ("minecraft:plains"). */
	public static ResourceLocation of(ResourceKey<?> key) {
		return key.location();
	}

	private Ids() {
	}
}
