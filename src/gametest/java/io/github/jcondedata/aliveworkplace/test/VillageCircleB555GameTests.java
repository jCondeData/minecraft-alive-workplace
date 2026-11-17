package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Banquets;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.BanquetPower;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * B555: the lookups that mean "this village's villagers" take the village's circle ({@link VillageHalls#villagers}), not
 * the square box round it ({@link VillageHalls#area}). In a 16-block village a villager 14 blocks out on both axes stands
 * in the square's corner but outside the circle (19.8 blocks across): the festival, the banquet, a birth's parents, a
 * friend found by name and the cough's villagers leave him out, while one by the hall is counted. Each test runs alone in
 * its batch; the villagers have no AI, so they stay where they are put.
 */
public class VillageCircleB555GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A Village Hall at {@link #HALL} with a 16-block village, everything near it cleared and put back afterwards. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	/** A villager by the hall, inside the village. */
	private static Villager inside(GameTestHelper helper, String name) {
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = hall.offset(2, 0, 2);
		helper.assertTrue(VillageHalls.contains(hall, at), "staging: " + name + " isn't in the village");
		return QaVillageCircleGameTests.at(helper, at, name);
	}

	/** A villager in the square's corner: inside {@link VillageHalls#area}, outside the village's circle. */
	private static Villager corner(GameTestHelper helper, String name) {
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = hall.offset(14, 0, 14);
		helper.assertTrue(VillageHalls.area(hall).contains(at.getCenter()), "staging: " + name + " isn't in the square");
		helper.assertTrue(!VillageHalls.contains(hall, at), "staging: " + name + " is in the village");
		return QaVillageCircleGameTests.at(helper, at, name);
	}

	/** A festival's feast remembers the villager by the hall as having come, not the one in the square's corner. */
	//$ gametest_ticks_batch AREA '100' '"b555Festival"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b555Festival")
	public void aFestivalLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Villager in = inside(helper, "Guest");
		Villager out = corner(helper, "Stranger");
		Festivals.feast(level, helper.absolutePos(HALL));
		long today = Chronicle.day(level);
		Long inDay = ModAttachments.FESTIVAL_DAY.get(in);
		Long outDay = ModAttachments.FESTIVAL_DAY.get(out);
		helper.assertTrue(inDay != null && inDay == today, "the villager by the hall didn't come to the festival: " + inDay);
		helper.assertTrue(outDay == null, "the villager in the square's corner, outside the village, came to its festival");
		in.discard();
		out.discard();
		helper.succeed();
	}

	/** A banquet feeds and remembers the villager by the hall, not the one in the square's corner. */
	//$ gametest_ticks_batch AREA '100' '"b555Banquet"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b555Banquet")
	public void aBanquetLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = village(helper);
		Villager in = inside(helper, "Diner");
		Villager out = corner(helper, "Stranger");
		Banquets.Feast feast = Banquets.feast(level, helper.absolutePos(HALL), entity, new BanquetPower(7, 1, 2, 3, 3));
		helper.assertTrue(ModAttachments.BANQUET.has(in), "the villager by the hall didn't come to the banquet");
		helper.assertTrue(!ModAttachments.BANQUET.has(out), "the villager in the square's corner, outside the village, came to its banquet");
		helper.assertTrue(feast.came() == 1, "came to the banquet: " + feast.came() + ", not the 1 villager in the village");
		in.discard();
		out.discard();
		helper.succeed();
	}

	/**
	 * With a free bed, a full store and a happy village, a villager by the hall and one in the square's corner have no
	 * baby (the village has one grown villager); a second villager by the hall makes the two parents.
	 */
	//$ gametest_ticks_batch AREA '100' '"b555Growth"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b555Growth")
	public void growthLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(17, 2, 19));
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
		helper.setBlock(new BlockPos(5, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(5, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		Villager in = inside(helper, "Parent");
		Villager out = corner(helper, "Stranger");
		helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.NONE,
			"staging: the village can't grow: " + VillageGrowth.blocker(level, hall, happy, 0));
		Villager baby = VillageGrowth.grow(level, hall, happy, 0);
		helper.assertTrue(baby == null, "a villager in the square's corner, outside the village, was a parent of its baby");
		Villager second = QaVillageCircleGameTests.at(helper, hall.offset(-2, 0, 2), "Other parent");
		baby = VillageGrowth.grow(level, hall, happy, 0);
		helper.assertTrue(baby != null && baby.isBaby(), "two grown villagers by the hall had no baby");
		baby.discard();
		in.discard();
		out.discard();
		second.discard();
		helper.succeed();
	}

	/** A friend asked for by name is found by the hall, not in the square's corner. */
	//$ gametest_ticks_batch AREA '100' '"b555Friendship"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b555Friendship")
	public void aFriendByNameLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager in = inside(helper, "Alda");
		Villager out = corner(helper, "Brom");
		helper.assertTrue(Friendship.named(level, hall, "Alda") == in, "the villager by the hall wasn't found by name");
		helper.assertTrue(Friendship.named(level, hall, "Brom") == null, "the villager in the square's corner, outside the village, was found as one of its people");
		in.discard();
		out.discard();
		helper.succeed();
	}

	/** The village a cough can spread through holds the villager by the hall, not the one in the square's corner. */
	//$ gametest_ticks_batch AREA '100' '"b555Sickness"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b555Sickness")
	public void theCoughsVillageLeavesOutTheSquaresCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Villager in = inside(helper, "Neighbor");
		Villager out = corner(helper, "Stranger");
		List<Villager> who = SicknessStory.villagers(level, helper.absolutePos(HALL));
		helper.assertTrue(who.contains(in), "the villager by the hall isn't one of the village's: " + who);
		helper.assertTrue(!who.contains(out), "the villager in the square's corner, outside the village, is one of its villagers");
		in.discard();
		out.discard();
		helper.succeed();
	}
}
