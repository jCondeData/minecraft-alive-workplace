package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (2026-10-31, second pass) of B715 from the B206/B207 spec ("a bed nobody's camp placed stays a home"): a real
 * settlers' camp, then the player's own brown bed put down beside its wagon. The older-save look (campfire, hay bale,
 * Blueprint Table close by) still applies to every brown or green bed that isn't on the list, so the player's bed beside a
 * camp placed since the list existed is taken for a bedroll. Kept as a bug test: it fails until the mod is fixed.
 */
public class QaWagonBeds1031bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);

	/**
	 * A real settlers' camp (SettlersWagonItem.makeCamp: its bedrolls, campfire, hay and Blueprint Table), and the player's
	 * own brown bed put down beside the wagon afterwards, which the camp did not place. Once the hall stands, the camp's two
	 * bedrolls are wagon beds (not counted), but the player's bed is a home: its resident keeps it and the census counts it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaPlayersBedBesideTheWagon1031b"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaPlayersBedBesideTheWagon1031b")
	public void qaAPlayersBedBesideARealCampsWagonStaysAHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaWagonBeds1031bGameTests.radius(helper, 18);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> wagon = QaWagonBeds1031bGameTests.bedHeads(helper);
		helper.assertTrue(wagon.size() == 2 && wagon.stream().allMatch(b -> CampBeds.isWagonBed(level, b)), "setup: wagon beds " + wagon);
		BlockPos foot = QaWagonBeds1031bGameTests.freeSpotBeside(level, wagon.get(0)); // absolute (the area may be rotated)
		helper.assertTrue(foot != null, "setup: no room for a bed beside the wagon at " + wagon.get(0));
		BlockState footState = Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		level.setBlock(foot, footState, Block.UPDATE_ALL); // the player's own bed, not the camp's
		level.setBlock(foot.south(), footState.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
		BlockPos mine = foot.south().immutable();
		Villager resident = EntityType.VILLAGER.create(level);
		resident.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5);
		level.addFreshEntity(resident);
		resident.setNoAi(true);
		Leftovers.after(helper, resident::discard);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, mine), "setup: the resident couldn't take the player's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				for (BlockPos b : wagon) {
					helper.assertTrue(CampBeds.isWagonBed(level, b), "the camp's own bedroll at " + b + " is no longer a wagon bed");
				}
				boolean wagonBed = CampBeds.isWagonBed(level, mine);
				BlockPos home = QaWagonBeds1031bGameTests.home(resident);
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(!wagonBed && mine.equals(home) && census.beds() == 1 && census.wagonBeds() == 2,
					"the player's own brown bed beside the wagon (placed after the camp, by nobody's camp): wagon bed " + wagonBed
						+ " (expected false), resident's home " + home + " (expected " + mine + "), census beds " + census.beds()
						+ " (expected 1), wagon beds " + census.wagonBeds() + " (expected 2)");
				helper.succeed();
			});
		});
	}

}
