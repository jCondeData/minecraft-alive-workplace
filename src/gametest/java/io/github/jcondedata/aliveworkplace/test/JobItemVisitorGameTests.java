package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B906 (lane-a-1103-2123): the sneak-right-click job path (a job's item, {@link Stations#choose}) refuses a
 * {@link io.github.jcondedata.aliveworkplace.hall.HallPeople#visitor visitor} as the hall's People table does (B895): a
 * hired mercenary at a guard post he took, and the Sickness story's Doctor Wren at a brewing stand, each sneak-clicked
 * twice with clay by a free Stonecutter keep their jobs, nothing paid, and the player is told "X's job can't be changed
 * here."
 */
public class JobItemVisitorGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos CUTTER = new BlockPos(14, 2, 13);

	/** Two sneak-clicks with clay (the Mason's item) by the free Stonecutter: an offer and its confirmation for a paid change. */
	private static InteractionResult clayTwice(ServerPlayer player, Villager villager) {
		Stations.choose(player, villager, new ItemStack(Items.CLAY_BALL));
		return Stations.choose(player, villager, new ItemStack(Items.CLAY_BALL));
	}

	//$ gametest_ticks_batch AREA '200' '"jobItemMercPost"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "jobItemMercPost")
	public void aMercenaryAtAGuardPostKeepsHisJobUnderAJobItem(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos post = new BlockPos(12, 2, 15);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			Leftovers.after(helper, () -> band.forEach(Villager::discard));
			helper.assertTrue(band.size() == 3, "setup: band " + band.size());
			Villager merc = band.get(0);
			merc.setNoAi(true);
			BlockPos at = helper.absolutePos(new BlockPos(13, 2, 14));
			merc.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			Jobs.employ(level, merc, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD); // the post he took
			int money = player.getInventory().countItem(Items.EMERALD);
			seen.clear();
			clayTwice(player, merc);
			int paid = money - player.getInventory().countItem(Items.EMERALD);
			if (merc.getVillagerData().getProfession() != ModVillagers.GUARD || paid != 0 || !Mercenaries.isMercenary(merc)) {
				helper.fail("a hired Mercenary at a guard post sneak-clicked twice with clay became a " + merc.getVillagerData().getProfession()
					+ " for " + paid + " emeralds; told " + seen);
			}
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(CUTTER)) == 1, "the free Stonecutter was taken");
			String name = merc.getDisplayName().getString();
			helper.assertTrue(seen.contains(name + "'s job can't be changed here."), "the player wasn't told why: " + seen);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '200' '"jobItemWrenStand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "jobItemWrenStand")
	public void doctorWrenAtABrewingStandKeepsHerJobUnderAJobItem(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos stand = new BlockPos(12, 2, 15);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager wren = QaHallScreenGameTests.villager(helper, "Doctor Wren", new BlockPos(13, 2, 14));
		wren.addTag(Arcs.MOB_TAG);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Jobs.employ(level, wren, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
			wren.setVillagerData(wren.getVillagerData().setLevel(3));
			int money = player.getInventory().countItem(Items.EMERALD);
			seen.clear();
			clayTwice(player, wren);
			int paid = money - player.getInventory().countItem(Items.EMERALD);
			boolean site = wren.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(stand))).orElse(false);
			if (wren.getVillagerData().getProfession() != VillagerProfession.CLERIC || paid != 0 || !site) {
				helper.fail("Doctor Wren at a brewing stand sneak-clicked twice with clay became a " + wren.getVillagerData().getProfession()
					+ " for " + paid + " emeralds (still at her stand: " + site + "); told " + seen);
			}
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(CUTTER)) == 1, "the free Stonecutter was taken");
			helper.assertTrue(seen.contains("Doctor Wren's job can't be changed here."), "the player wasn't told why: " + seen);
			helper.succeed();
		});
	}
}
