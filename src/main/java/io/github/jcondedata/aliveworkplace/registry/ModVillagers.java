package io.github.jcondedata.aliveworkplace.registry;

import com.google.common.collect.ImmutableSet;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

public final class ModVillagers {
	public static final ResourceLocation BENCH_ID = AliveWorkplace.id("builders_bench");
	public static final ResourceKey<PoiType> BUILDERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, BENCH_ID);
	public static final PoiType BUILDERS_BENCH_POI_TYPE = PointOfInterestHelper.register(BENCH_ID, 1, 1, ModBlocks.BUILDERS_BENCH);

	public static final VillagerProfession BUILDER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("builder"),
		new VillagerProfession(
			"builder",
			holder -> holder.is(BUILDERS_BENCH_POI),
			holder -> holder.is(BUILDERS_BENCH_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_MASON
		)
	);

	public static final ResourceLocation MINERS_BENCH_ID = AliveWorkplace.id("miners_bench");
	public static final ResourceKey<PoiType> MINERS_BENCH_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MINERS_BENCH_ID);
	public static final PoiType MINERS_BENCH_POI_TYPE = PointOfInterestHelper.register(MINERS_BENCH_ID, 1, 1, ModBlocks.MINERS_BENCH);

	public static final VillagerProfession MINER = Registry.register(
		BuiltInRegistries.VILLAGER_PROFESSION,
		AliveWorkplace.id("miner"),
		new VillagerProfession(
			"miner",
			holder -> holder.is(MINERS_BENCH_POI),
			holder -> holder.is(MINERS_BENCH_POI),
			ImmutableSet.of(),
			ImmutableSet.of(),
			SoundEvents.VILLAGER_WORK_TOOLSMITH
		)
	);

	/** Villagers whose work this mod runs: they get the long shift and our WORK package. */
	public static boolean isWorker(VillagerProfession profession) {
		return profession == BUILDER || profession == MINER;
	}

	/**
	 * Builders put in a longer shift than vanilla villagers (who only WORK 2000-9000 and then
	 * gossip): they work from early morning to dusk, then go to bed like everyone else.
	 */
	public static final Schedule BUILDER_SCHEDULE = Registry.register(
		BuiltInRegistries.SCHEDULE,
		AliveWorkplace.id("builder"),
		new ScheduleBuilder(new Schedule())
			.changeActivityAt(10, Activity.IDLE)
			.changeActivityAt(1000, Activity.WORK)
			.changeActivityAt(11000, Activity.IDLE)
			.changeActivityAt(12000, Activity.REST)
			.build()
	);

	public static void init() {
	}

	private ModVillagers() {
	}
}
