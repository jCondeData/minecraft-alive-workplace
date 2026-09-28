package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.fish.FishingBobber;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entities: only the fishermen's bobbers so far. */
public final class ModEntities {
	/** A fisherman villager's bobber on the water (never saved, can't be summoned). */
	public static final EntityType<FishingBobber> FISHING_BOBBER = Registry.register(BuiltInRegistries.ENTITY_TYPE, AliveWorkplace.id("fishing_bobber"),
		EntityType.Builder.<FishingBobber>of(FishingBobber::new, MobCategory.MISC).noSave().noSummon().sized(0.25f, 0.25f)
			.clientTrackingRange(4).updateInterval(10).build("fishing_bobber"));

	public static void init() {
		// Loading the class registers the entities.
	}

	private ModEntities() {
	}
}
