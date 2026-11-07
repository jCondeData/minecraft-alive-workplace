package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (lane-e-1104-1613), from VillageHalls.assign's own promise ("if it's still free and they can work (not a
 * nitwit or a child)"): the Hall gives a free block to a jobless grown villager, and never to a nitwit or to a child,
 * each refused on its own (last night's mutation run: {@code isBaby() || nitwit} turned into {@code &&} survived, so no
 * test refused one without the other). The block stays free for the next villager.
 */
public class QaHallAssignWho1104eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos COMPOSTER = new BlockPos(3, 2, 3);

	private static VillageHalls.FreeStation composter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		var farmer = level.registryAccess().registryOrThrow(Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.FARMER);
		return new VillageHalls.FreeStation(helper.absolutePos(COMPOSTER), VillagerProfession.FARMER, farmer);
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession job, boolean baby) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		if (baby) {
			v.setAge(-24000);
		}
		return v;
	}

	/** The control: a grown jobless villager is given the free composter (so the refusals below aren't the setup's). */
	//$ gametest_ticks AREA '60'
	@GameTest(template = AREA, timeoutTicks = 60)
	public void aGrownJoblessVillagerIsGivenTheFreeBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHalls.FreeStation free = composter(helper);
		Villager adult = villager(helper, VillagerProfession.NONE, false);
		helper.assertTrue(VillageHalls.assign(level, adult, free), "the Hall didn't give a grown jobless villager the free composter");
		helper.assertTrue(StationsSpecGameTests.job(adult) == VillagerProfession.FARMER
				&& StationsSpecGameTests.site(adult).equals(Optional.of(free.pos())),
			"the grown villager is a " + StationsSpecGameTests.name(StationsSpecGameTests.job(adult)) + " at " + StationsSpecGameTests.site(adult));
		helper.succeed();
	}

	/** A grown nitwit is refused: no job, no block taken, and the composter still goes to the next villager. */
	//$ gametest_ticks AREA '60'
	@GameTest(template = AREA, timeoutTicks = 60)
	public void aGrownNitwitIsNeverGivenABlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHalls.FreeStation free = composter(helper);
		Villager nitwit = villager(helper, VillagerProfession.NITWIT, false);
		helper.assertTrue(!VillageHalls.assign(level, nitwit, free), "the Hall gave a grown nitwit the free composter");
		helper.assertTrue(StationsSpecGameTests.job(nitwit) == VillagerProfession.NITWIT,
			"the nitwit became a " + StationsSpecGameTests.name(StationsSpecGameTests.job(nitwit)));
		helper.assertTrue(level.getPoiManager().getFreeTickets(free.pos()) == 1,
			"the composter is held (" + level.getPoiManager().getFreeTickets(free.pos()) + " free) after the nitwit was refused");
		Villager next = villager(helper, VillagerProfession.NONE, false);
		helper.assertTrue(VillageHalls.assign(level, next, free), "the composter couldn't be given to the next villager after the nitwit");
		helper.succeed();
	}

	/** A jobless child is refused, though not a nitwit: still jobless, the composter still free. */
	//$ gametest_ticks AREA '60'
	@GameTest(template = AREA, timeoutTicks = 60)
	public void aChildWhoIsNoNitwitIsStillNeverGivenABlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHalls.FreeStation free = composter(helper);
		Villager child = villager(helper, VillagerProfession.NONE, true);
		helper.assertTrue(child.isBaby(), "setup: the child isn't a baby");
		helper.assertTrue(!VillageHalls.assign(level, child, free), "the Hall gave a child the free composter");
		helper.assertTrue(StationsSpecGameTests.job(child) == VillagerProfession.NONE,
			"the child became a " + StationsSpecGameTests.name(StationsSpecGameTests.job(child)));
		helper.assertTrue(level.getPoiManager().getFreeTickets(free.pos()) == 1,
			"the composter is held (" + level.getPoiManager().getFreeTickets(free.pos()) + " free) after the child was refused");
		helper.succeed();
	}
}
