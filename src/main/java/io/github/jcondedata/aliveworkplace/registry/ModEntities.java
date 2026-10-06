package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.mc.Reg;
import io.github.jcondedata.aliveworkplace.fish.FishingBobber;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entities: the fishermen's bobbers and the cradles' seats. */
public final class ModEntities {
	/** A fisherman villager's bobber on the water (never saved, can't be summoned). */
	public static final EntityType<FishingBobber> FISHING_BOBBER = Reg.entity("fishing_bobber", EntityType.Builder.<FishingBobber>of(FishingBobber::new, MobCategory.MISC).noSave().noSummon().sized(0.25f, 0.25f)
			.clientTrackingRange(4).updateInterval(10));

	/** What a child sits in when asleep in a cradle (30.12); saved, so the child is saved with it. */
	public static final EntityType<io.github.jcondedata.aliveworkplace.hall.CradleSeat> CRADLE_SEAT = Reg.entity("cradle_seat",
		EntityType.Builder.<io.github.jcondedata.aliveworkplace.hall.CradleSeat>of(io.github.jcondedata.aliveworkplace.hall.CradleSeat::new, MobCategory.MISC)
			.noSummon().sized(0.5f, 0.2f).clientTrackingRange(8).updateInterval(20));

	/** What a villager sits on in a Cup's stands (28.19); saved, so the villager is saved with it. */
	public static final EntityType<io.github.jcondedata.aliveworkplace.cup.StandSeat> STAND_SEAT = Reg.entity("stand_seat",
		EntityType.Builder.<io.github.jcondedata.aliveworkplace.cup.StandSeat>of(io.github.jcondedata.aliveworkplace.cup.StandSeat::new, MobCategory.MISC)
			.noSummon().sized(0.5f, 0.2f).clientTrackingRange(8).updateInterval(20));

	public static void init() {
		// Loading the class registers the entities.
	}

	private ModEntities() {
	}
}
