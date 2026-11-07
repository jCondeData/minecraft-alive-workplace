package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-31) of B206/B207 from their spec: "the wagon's beds are only for the journey; once a village is founded the
 * settlers claim real beds in its homes". No settlers' camp was ever placed here: a player's own cabin has a brown bed,
 * a campfire 6 blocks off and a hay bale 6 blocks off (an ordinary rustic house). CampBeds.looksLikeCamp takes any brown
 * or green bed with a campfire and a hay bale within 10 blocks for an old save's wagon bedroll, lists it for good, and the
 * hall's round un-homes the villager living there and holds the bed for nobody, and the census leaves it out.
 */
public class QaWagonBedsBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	/** A brown bed in a cabin with a campfire and a hay bale nearby, no camp ever placed: it stays a home the hall counts. */
	//$ gametest_ticks_batch AREA '200' '"qaCabinBrownBed1031"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCabinBrownBed1031")
	public void qaACabinsBrownBedByACampfireAndHayIsStillAHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(level).forget(box));

		BlockPos foot = new BlockPos(6, 2, 10);
		helper.setBlock(foot, Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos head = helper.absolutePos(foot.south());
		helper.setBlock(foot.east(6), Blocks.CAMPFIRE); // the cabin's cooking fire
		helper.setBlock(foot.west(6), Blocks.HAY_BLOCK); // a hay bale by the door
		Villager resident = helper.spawn(EntityType.VILLAGER, foot.north());
		resident.setNoAi(true);

		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, head), "setup: the resident couldn't take the cabin's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, head),
					"a player's brown bed with a campfire and a hay bale 6 blocks off is taken for a settlers' wagon bed (no camp was ever placed)");
				helper.assertTrue(head.equals(home(resident)), "the cabin's resident lost his home at the hall's round: home " + home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 0,
					"the hall counts beds " + census.beds() + ", wagon beds " + census.wagonBeds() + " (expected the cabin's bed as a home)");
				helper.succeed();
			});
		});
	}
}
