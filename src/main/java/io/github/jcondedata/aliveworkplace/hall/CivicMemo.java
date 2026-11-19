package io.github.jcondedata.aliveworkplace.hall;

import org.jetbrains.annotations.Nullable;

/**
 * What the edicts' checks keep on a villager between asks (B455): the hall they live by ({@link CivicEffects}) and the
 * work gate's answer ({@link Conscription#workStopped}). Every villager is one ({@code mixin/VillagerCivicMemoMixin}
 * adds the two fields), so an ask reads a field of the villager it already has; the answers used to be in two shared
 * maps, each looked up under a lock for every villager every tick. Never saved; each value is one unchanging record,
 * swapped whole, so another thread reads the old answer or the new one.
 */
public interface CivicMemo {
	@Nullable
	Object aliveworkplace$hallKept();

	void aliveworkplace$hallKept(@Nullable Object kept);

	@Nullable
	Object aliveworkplace$workGate();

	void aliveworkplace$workGate(@Nullable Object kept);
}
