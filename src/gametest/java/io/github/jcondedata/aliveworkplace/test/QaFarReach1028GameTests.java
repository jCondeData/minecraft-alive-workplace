package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-28) for 36.20, "Work anywhere in the village, and far outside it", for what FarSiteGameTests,
 * QaFarSiteGameTests and QaVillageReachGameTests leave:
 * <ul>
 * <li>"a village with no hall (worker-block reach only)": with no hall anywhere near, exactly 128 out is taken and 129 is
 * refused with the plain sentence rendered in full (no village named), for the builder (the placed Blueprint), the
 * miner (the Quarry Marker) and the lumberjack (the Field Marker on a tree farm);</li>
 * <li>a nightly mutant in HallJobs.click (the slot bound {@code slot >= 0} made {@code slot > 0}): Job ▾ on slot 0
 * holding a child's row says "Pip's job can't be changed here." (the bound takes the screen's first slot).</li>
 * </ul>
 */
public class QaFarReach1028GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// --- no hall: the worker's block reaches 128, the sentence names no village ------------------------------------------

	/** Takes away every hall (block and point of interest) within 300 blocks of the work block, left by earlier tests. */
	private static void noHalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos work = helper.absolutePos(new BlockPos(3, 2, 3));
		PoiManager pois = level.getPoiManager();
		pois.findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, work, 300, PoiManager.Occupancy.ANY).toList().forEach(other -> {
			level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
			if (pois.getType(other).isPresent()) {
				pois.remove(other);
			}
		});
		VillageHalls.forgetNearest();
	}

	private static void noHallBoundary(GameTestHelper helper, String job, String expected129) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.forgetNearest();
		});
		noHalls(helper);
		List<Component> seen = new ArrayList<>();
		QaVillageReachGameTests.Kit kit = QaVillageReachGameTests.kit(helper, job, seen);
		BlockPos work = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.assertTrue(Reach.village(helper.getLevel(), work).isEmpty(), "setup: the work block is in a village: " + Reach.village(helper.getLevel(), work));
		helper.assertTrue(kit.reach().getAsInt() == 128, kit.name() + "'s long reach isn't 128 but " + kit.reach().getAsInt());
		QaVillageReachGameTests.taken(helper, kit, seen, new Vec3i(128, 0, 0), "with no hall, a spot exactly 128 blocks out");
		QaVillageReachGameTests.taken(helper, kit, seen, new Vec3i(0, 0, -128), "with no hall, a spot exactly 128 blocks north");
		QaVillageReachGameTests.refused(helper, kit, seen, new Vec3i(129, 0, 0), "with no hall, a spot 129 blocks out", expected129::equals, "\"" + expected129 + "\"");
		QaVillageReachGameTests.refused(helper, kit, seen, new Vec3i(0, 0, -129), "with no hall, a spot 129 blocks north", expected129::equals, "\"" + expected129 + "\"");
		// 100 out (the spec's "a spot 100 blocks outside the village") is in the long reach
		QaVillageReachGameTests.taken(helper, kit, seen, new Vec3i(-100, 0, 0), "with no hall, a spot 100 blocks west");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '200' '"qa1028NoHallBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028NoHallBuilder")
	public void withNoHallTheBuilderReaches128AndSaysSoAt129(GameTestHelper helper) {
		noHallBoundary(helper, "builder", "That spot is 129 blocks from the builder's Blueprint Table — keep it within 128.");
	}

	//$ gametest_ticks_batch AREA '200' '"qa1028NoHallMiner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028NoHallMiner")
	public void withNoHallTheMinerReaches128AndSaysSoAt129(GameTestHelper helper) {
		noHallBoundary(helper, "miner", "That spot is 129 blocks from the Miner's Bench the miner works at — keep it within 128.");
	}

	//$ gametest_ticks_batch AREA '200' '"qa1028NoHallLumberjack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028NoHallLumberjack")
	public void withNoHallTheLumberjackReaches128AndSaysSoAt129(GameTestHelper helper) {
		noHallBoundary(helper, "lumberjack", "That spot is 129 blocks from the Chopping Block the lumberjack works at — keep it within 128.");
	}

	// --- HallJobs.click's slot bound: a child's row on slot 0 ----------------------------------------------------------

	/**
	 * Job ▾ for slot 0 holding a person's row is that person's Job ▾: for a child, whose job can't be changed, the hall
	 * says "Pip's job can't be changed here." and opens no list. The hall's own layout keeps the village's name on slot
	 * 0, so the child's row (her icon with its person mark) is moved there, as a screen laid out from its first slot
	 * would have it. Kills the nightly survivor {@code slot >= 0} made {@code slot > 0} (HallJobs.click).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qa1028HallSlotZeroChild"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qa1028HallSlotZeroChild")
	public void jobOnSlotZeroHoldingAChildSaysHerJobCantChange(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
			VillageHalls.forgetNearest();
			HallJobs.forget();
		});
		BlockPos hallRel = new BlockPos(15, 2, 15);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		BlockPos lectern = new BlockPos(10, 2, 10);
		helper.setBlock(lectern, Blocks.LECTERN);
		Villager dara = helper.spawn(EntityType.VILLAGER, lectern.offset(1, 0, 1));
		dara.setNoAi(true);
		Jobs.employ(helper.getLevel(), dara, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setCustomName(Component.literal("Dara"));
		Villager pip = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		pip.setNoAi(true);
		pip.setAge(-24000);
		pip.setCustomName(Component.literal("Pip"));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(hallRel);
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			helper.useBlock(hallRel, player);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
			int pipSlot = -1;
			for (int s = 0; s < ChoiceMenu.SIZE; s++) {
				CompoundTag row = HallPeople.row(menu.icon(s));
				if (row != null && row.getString("uuid").equals(pip.getStringUUID())) {
					pipSlot = s;
				}
			}
			helper.assertTrue(pipSlot > 0, "setup: no row for Pip in the People table");
			helper.assertTrue(!HallJobs.canChange(helper.getLevel(), pip), "setup: a child's job can be changed from the table");
			// Job ▾ on Pip's own row first: the same words (what slot 0 must match)
			seen.clear();
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + pipSlot), "Job ▾ on Pip's row wasn't taken");
			List<String> onRow = seen.stream().map(Component::getString).toList();
			helper.assertTrue(onRow.contains("Pip's job can't be changed here."), "Job ▾ on Pip's row said " + onRow);
			// her row on slot 0
			menu.button(0, menu.icon(pipSlot).copy(), null);
			seen.clear();
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN), "Job ▾ on slot 0 wasn't taken");
			List<String> said = seen.stream().map(Component::getString).toList();
			helper.assertTrue(said.contains("Pip's job can't be changed here."), "Job ▾ on slot 0, Pip's row there, said " + said);
			helper.assertTrue(!HallJobs.isOpen(menu, pip) && !HallJobs.isOpen(menu, dara), "a list opened");
			player.closeContainer();
			helper.succeed();
		});
	}
}
