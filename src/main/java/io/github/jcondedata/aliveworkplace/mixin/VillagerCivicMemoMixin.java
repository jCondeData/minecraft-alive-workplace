package io.github.jcondedata.aliveworkplace.mixin;

import io.github.jcondedata.aliveworkplace.hall.CivicMemo;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The edicts' answers kept on the villager ({@code hall/CivicMemo}, B455): two fields, never saved, so the checks every
 * villager's brain makes every tick read them straight from the villager.
 */
@Mixin(Villager.class)
abstract class VillagerCivicMemoMixin implements CivicMemo {
	@Unique
	@Nullable
	private volatile Object aliveworkplace$hallKeptValue;
	@Unique
	@Nullable
	private volatile Object aliveworkplace$workGateValue;

	@Override
	@Nullable
	public Object aliveworkplace$hallKept() {
		return aliveworkplace$hallKeptValue;
	}

	@Override
	public void aliveworkplace$hallKept(@Nullable Object kept) {
		aliveworkplace$hallKeptValue = kept;
	}

	@Override
	@Nullable
	public Object aliveworkplace$workGate() {
		return aliveworkplace$workGateValue;
	}

	@Override
	public void aliveworkplace$workGate(@Nullable Object kept) {
		aliveworkplace$workGateValue = kept;
	}
}
