package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B619: a yes to a repair looked for a free builder only in a box of twice {@code maxSiteDistance} round the building,
 * though a builder takes work anywhere in his village (36.20). With the long reach set low (4, so the old box is 8
 * blocks) a wall 21 blocks from the Blueprint Table in the same village is repaired at once by that builder
 * ({@link Builders#reaching}); with no village round them, the same builder is out of reach and the repair waits.
 */
public class FarSiteAsksGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(2, 2, 15);
	private static final BlockPos WALL = new BlockPos(20, 2, 15);
	private static final int LENGTH = 8;
	private static final int REACH = 4;

	//$ gametest_ticks_batch HUGE '200' '"farSiteAsksVillage"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "farSiteAsksVillage")
	public void aYesToARepairAcrossTheVillageStartsItsBuilderAtOnce(GameTestHelper helper) {
		run(helper, true, "fsaV");
	}

	//$ gametest_ticks_batch HUGE '200' '"farSiteAsksNoVillage"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "farSiteAsksNoVillage")
	public void aYesToARepairPastTheLongReachWithNoVillageWaits(GameTestHelper helper) {
		run(helper, false, "fsaN");
	}

	private static void run(GameTestHelper helper, boolean village, String playerName) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int reachWas = Builders.MAX_SITE_DISTANCE;
		int radiusWas = VillageHalls.RADIUS;
		boolean upkeepWas = Upkeep.ENABLED;
		boolean askWas = Upkeep.ASK;
		boolean protectionWas = VillageProtection.ENABLED;
		Builders.MAX_SITE_DISTANCE = REACH;
		VillageHalls.RADIUS = 48;
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		VillageProtection.ENABLED = false;
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			Builders.MAX_SITE_DISTANCE = reachWas;
			VillageHalls.RADIUS = radiusWas;
			Upkeep.ENABLED = upkeepWas;
			Upkeep.ASK = askWas;
			VillageProtection.ENABLED = protectionWas;
			VillageHalls.forgetNearest();
		});
		helper.setDayTime(2000);
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, playerName);
		if (village) {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHalls.forgetNearest();
		}

		// the wall, standing and recorded as finished; then one plank is knocked out
		for (int x = 0; x < LENGTH; x++) {
			helper.setBlock(WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"far_site_asks_" + playerName.toLowerCase() + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(WALL), new Vec3i(LENGTH, 1, 1), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WALL), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(wall, placement, owner.getUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(placement));
		helper.setBlock(WALL.offset(2, 0, 0), Blocks.AIR);

		Leftovers.benches(helper, helper.absolutePos(WALL), 2 * (VillageHalls.RADIUS + 1) + 16); // his is the only table round the wall
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(BENCH.offset(0, 0, 2), Blocks.CHEST);
		Container chest = helper.getBlockEntity(BENCH.offset(0, 0, 2));
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		Villager builder = QaRepairAsks1103bGameTests.hire(helper, owner, BENCH, BENCH.offset(1, 0, 1));
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(BENCH);
		BlockPos spot = Builders.spot(level, wall, placement);
		double off = Reach.across(bench, spot);
		helper.assertTrue(off > 2 * REACH + 4, "the wall is only " + off + " blocks from the Table: the old box of " + 2 * REACH + " would find the builder");
		helper.assertTrue(Reach.village(level, bench).isPresent() == village, "the Table's village: " + Reach.village(level, bench));
		helper.assertTrue(Builders.inReach(level, bench, wall, placement) == village, "the builder's own rule has the wall " + (village ? "out of" : "in") + " reach");
		helper.assertTrue(Builders.benchFor(level, wall, placement).equals(village ? java.util.Optional.of(bench) : java.util.Optional.empty()),
			"the Table a build there would be built from: " + Builders.benchFor(level, wall, placement));

		helper.runAfterDelay(2, () -> {
			QaRepairAsks1103bGameTests.click(helper, owner, "yes", placement.origin());
			String reply = QaRepairAsks1103bGameTests.last(seen);
			boolean repairing = RepairAsks.repairOf(level, placement) != null;
			if (village) {
				helper.assertTrue(repairing, "the village's builder, " + off + " blocks off, wasn't started on the repair agreed to: " + reply);
				helper.assertTrue(builder.getUUID().equals(RepairAsks.repairOf(level, placement).builder()), "someone else has the repair");
			} else {
				helper.assertFalse(repairing, "a builder " + off + " blocks off with no village (long reach " + REACH + ") took the repair: " + reply);
			}
			helper.succeed();
		});
	}
}
