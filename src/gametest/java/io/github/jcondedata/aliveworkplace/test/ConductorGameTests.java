package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * ROADMAP 39.7d without Create (this suite's runtime has no Create; {@code ConductorCompatTests} has it): there's no
 * Conductor profession and no station job site, a villager saved as a Conductor in a world Create was removed from loads
 * jobless, and the train's seat still goes to the nearest jobless villager, never to someone with a job.
 */
public class ConductorGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Without Create the job isn't registered at all: no profession, no POI. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutCreateThereIsNoConductor(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"), "Create is loaded in the plain suite");
		helper.assertTrue(ModVillagers.CONDUCTOR == null, "a Conductor profession without Create");
		helper.assertTrue(!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(AliveWorkplace.id("conductor")), "aliveworkplace:conductor is registered without Create");
		helper.assertTrue(!helper.getLevel().registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).containsKey(ModVillagers.TRACK_STATION_ID),
			"the station POI is registered without Create");
		helper.assertTrue(!ModVillagers.isConductor(VillagerProfession.NONE) && !ModVillagers.isWorker(VillagerProfession.NONE), "NONE counts as a Conductor");
		helper.succeed();
	}

	/** A villager saved as a Conductor (Create since removed) loads as a jobless villager, not a broken one. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSavedConductorLoadsJoblessWithoutCreate(GameTestHelper helper) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		CompoundTag saved = new CompoundTag();
		villager.saveWithoutId(saved);
		CompoundTag data = saved.getCompound("VillagerData");
		data.put("profession", StringTag.valueOf("aliveworkplace:conductor"));
		saved.put("VillagerData", data);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.NONE,
			"a saved Conductor loads as " + copy.getVillagerData().getProfession());
		helper.assertTrue(!CaravanTrains.isConductor(copy), "the loaded villager counts as a Conductor");
		copy.discard();
		villager.discard();
		helper.succeed();
	}

	/** No Conductor: the seat goes to the nearest jobless grown-up, never to a villager with a job, a baby or a rider. */
	//$ gametest_batch AREA '"conductorSeatFallback"'
	@GameTest(template = AREA, batch = "conductorSeatFallback")
	public void withoutAConductorTheNearestJoblessVillagerTakesTheSeat(GameTestHelper helper) {
		Leftovers.clear(helper); // no villager of another test within the seat's reach
		BlockPos station = helper.absolutePos(new BlockPos(1, 2, 1));
		Villager librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1)); // nearest, but has a job
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		librarian.setNoAi(true);
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 2));
		baby.setAge(-24000);
		baby.setNoAi(true);
		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		far.setVillagerData(far.getVillagerData().setProfession(VillagerProfession.NONE));
		far.setNoAi(true);
		Villager near = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		near.setVillagerData(near.getVillagerData().setProfession(VillagerProfession.NONE));
		near.setNoAi(true);
		Villager seat = CaravanTrains.conductorFor(helper.getLevel(), station);
		helper.assertTrue(seat == near, "the seat went to " + (seat == null ? "nobody" : seat.getVillagerData().getProfession() + " at "
			+ helper.relativePos(seat.blockPosition())));
		near.discard();
		far.discard();
		helper.assertTrue(CaravanTrains.conductorFor(helper.getLevel(), station) == null, "the seat went to a villager with a job or a baby");
		helper.succeed();
	}
}
