package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0938) on 26.10, from its text: "Imports, uploads and scans are always unlocked". With the switch on in
 * a village whose Table starts from the basics, a player scans a little build with the Scan Tool (two corner clicks
 * and a sneak-use): the scan is on the village's Table, unlocked (not greyed out, not a ???), and its Get Blueprint
 * hands it out.
 */
public class QaScanUnlockedGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos TABLE = new BlockPos(5, 2, 5);
	private static final BlockPos SRC = new BlockPos(8, 2, 2);
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest_ticks_batch AREA '100' '"qaScanUnlocked"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaScanUnlocked")
	public void aPlayersScanIsUnlockedOnTheVillagesTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		boolean was = BlueprintUnlocks.ENABLED;
		int radius = VillageHalls.RADIUS;
		BlueprintUnlocks.ENABLED = true;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			BlueprintUnlocks.ENABLED = was;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		VillageHalls.forgetNearest();
		// a little 3 x 2 x 1 build of planks and a crafting table
		helper.setBlock(SRC, Blocks.OAK_PLANKS);
		helper.setBlock(SRC.east(), Blocks.CRAFTING_TABLE);
		helper.setBlock(SRC.east(2), Blocks.OAK_PLANKS);
		helper.setBlock(SRC.above(), Blocks.OAK_PLANKS);
		helper.setBlock(SRC.east(2).above(), Blocks.OAK_PLANKS);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.forgetNearest();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos table = helper.absolutePos(TABLE);
			helper.assertTrue(hall.equals(BlueprintUnlocks.village(level, table)), "setup: the Table isn't in the test's village");
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
			Leftovers.after(helper, player::discard);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			QaStageThreeGameTests.click(helper, player, SRC.below());
			QaStageThreeGameTests.click(helper, player, SRC.east(2).above());
			player.setShiftKeyDown(true);
			QaStageThreeGameTests.clickAir(helper, player);
			player.setShiftKeyDown(false);
			helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
			ResourceLocation id = null;
			for (ItemStack s : player.getInventory().items) {
				if (s.is(ModItems.BLUEPRINT)) {
					id = BlueprintItem.data(s).orElseThrow().structure();
				}
			}
			helper.assertTrue(id != null, "the scan gave no blueprint");
			ResourceLocation scan = id;
			helper.assertTrue(BlueprintUnlocks.state(level, hall, scan) == BlueprintUnlocks.State.UNLOCKED,
				"the player's scan " + scan + " is " + BlueprintUnlocks.state(level, hall, scan) + " in a village with unlocks on (26.10: scans are always unlocked)");
			TablePayloads.Entry entry = TableServer.listing(level, hall).stream().filter(e -> e.id().equals(scan)).findFirst().orElse(null);
			helper.assertTrue(entry != null, "the player's scan " + scan + " isn't on the village's Table");
			helper.assertTrue(!entry.locked() && !entry.hidden(), "the player's scan is on the Table " + (entry.hidden() ? "as a ???" : "greyed out: " + entry.hint()));
			// Get Blueprint from the Table, standing at it with a blank blueprint, hands the scan out
			player.getInventory().clearContent();
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
			player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
			helper.assertTrue(TableServer.take(player, table, scan), "the Table refused to hand out the player's own scan");
			boolean got = false;
			for (ItemStack s : player.getInventory().items) {
				got |= s.is(ModItems.BLUEPRINT) && BlueprintItem.data(s).map(d -> d.structure().equals(scan)).orElse(false);
			}
			helper.assertTrue(got, "the Table said yes but the player holds no blueprint of the scan");
			LOGGER.info("qa scan unlocked: the scan {} is on the Table unlocked and handed out", scan);
			helper.succeed();
		});
	}
}
