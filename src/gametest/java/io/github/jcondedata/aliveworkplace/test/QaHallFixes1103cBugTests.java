package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c, 2026-11-03), bug tests (not registered: they fail until the mod is fixed) found while checking B885 and
 * B889's fixes: other ways the same bugs still reach the player.
 */
public class QaHallFixes1103cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final String RETIRED_LINE = " has retired and takes no job now: they mentor the young instead.";

	private static List<String> lore(ItemStack stack) {
		var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/**
	 * B889's fix keeps a mercenary off the jobless list, but a hired mercenary who stands at a free guard post takes it
	 * (as a Guard does; Mercenaries.tick lets "a bed or a post they took" go when he leaves). He then has a job site, so
	 * his row offers a paid retraining: one Job ▾, two clicks on Mason, and the band's sword becomes the village's Mason,
	 * still hired and gone at dawn. Expected (B889): "a mercenary shows as a guard and his job can't be changed here,
	 * like a Legend's".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfbMercPost"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfbMercPost")
	public void aMercenaryAtAGuardPostCantBeRetrained(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		BlockPos post = new BlockPos(12, 2, 15);
		helper.setBlock(post, ModBlocks.GUARD_POST);
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
			Jobs.employ(level, merc, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD); // the post he took
			int money = player.getInventory().countItem(Items.EMERALD);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			CompoundTag row = HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, merc)));
			menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, merc));
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			if (merc.getVillagerData().getProfession() != ModVillagers.GUARD || !row.getString("job_action").isEmpty()) {
				helper.fail("a hired Mercenary at a guard post has Job ▾ '" + row.getString("job_action") + "' on his row; two clicks on Mason made him a "
					+ merc.getVillagerData().getProfession() + " for " + (money - player.getInventory().countItem(Items.EMERALD)) + " emeralds, still hired: "
					+ Mercenaries.isMercenary(merc) + "; told " + seen);
			}
			helper.succeed();
		});
	}

	/**
	 * B895's other case (lane-a-1103-2123): a story's visitor in his own job, Doctor Wren the Cleric, who takes a free
	 * brewing stand as any Cleric does, is no more the village's to retrain than a mercenary: his row offers no Job ▾,
	 * and two clicks on Mason leave him the Cleric he came as, nothing paid.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfbWrenStand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfbWrenStand")
	public void aStoryVisitorAtABrewingStandCantBeRetrained(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		BlockPos stand = new BlockPos(12, 2, 15);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		ServerLevel level = helper.getLevel();
		Villager wren = QaHallScreenGameTests.villager(helper, "Doctor Wren", new BlockPos(13, 2, 15));
		wren.addTag(Arcs.MOB_TAG);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Jobs.employ(level, wren, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
			wren.setVillagerData(wren.getVillagerData().setLevel(3));
			int money = player.getInventory().countItem(Items.EMERALD);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			CompoundTag row = HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, wren)));
			menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, wren));
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			int paid = money - player.getInventory().countItem(Items.EMERALD);
			if (wren.getVillagerData().getProfession() != VillagerProfession.CLERIC || !row.getString("job_action").isEmpty() || paid != 0) {
				helper.fail("Doctor Wren at a brewing stand has Job ▾ '" + row.getString("job_action") + "' on his row; two clicks on Mason made him a "
					+ wren.getVillagerData().getProfession() + " for " + paid + " emeralds; told " + seen);
			}
			helper.assertTrue(seen.contains("Doctor Wren's job can't be changed here."), "the player wasn't told why: " + seen);
			helper.succeed();
		});
	}

	/**
	 * B885's fix grays a retired elder's Job ▾ list, but his row still opens the jobless villager's job page (a press on
	 * his row, as for any jobless villager): it offers the free stonecutter with "Click: give Bram the Mason job", and the
	 * click answers "Someone took that workstation first", which is untrue; the stonecutter is free. Expected: no job
	 * offered to a retired elder there either, or the pick refused with the line saying why ("Bram has retired ...").
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfbRetiredPage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfbRetiredPage")
	public void aRetiredEldersJobPageSaysWhyNotSomeoneTookIt(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			menu.clicked(QaHallScreenGameTests.slotOf(helper, menu, bram), 0, ClickType.PICKUP, player); // his row's button
			int cutter = -1;
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				if (menu.icon(slot).is(Items.STONECUTTER)) {
					cutter = slot;
				}
			}
			if (cutter < 0) {
				helper.succeed(); // no job offered on his page: right
				return;
			}
			List<String> offer = lore(menu.icon(cutter));
			seen.clear();
			menu.clicked(cutter, 0, ClickType.PICKUP, player);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired Bram got a job from his page");
			helper.assertTrue(seen.contains("Bram" + RETIRED_LINE), "the retired Bram's job page offered the free stonecutter (" + offer
				+ "); a click on it told the player " + seen + " instead of why");
			helper.succeed();
		});
	}

	/**
	 * B885's fix says "a retired elder's Job ▾ list now grays every job with 'Bram has retired ...'", but only a job
	 * with a free block gets the line, and that line's label says the job "needs ... class or better" (a social class
	 * he isn't missing). Every job without a block still says "No Fletching Table in the village is free: place one, and
	 * this job can be picked", which isn't true for him. Expected: every line names his retirement, none promises a job.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfbRetiredLines"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfbRetiredLines")
	public void aRetiredEldersEveryJobLineSaysHeRetired(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			List<ItemStack> lines = HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, bram)));
			List<String> wrong = new ArrayList<>();
			for (ItemStack line : lines) {
				CompoundTag mark = HallJobs.mark(line);
				if (mark == null || mark.getString("state").equals("header")) {
					continue;
				}
				String label = line.getHoverName().getString();
				if (!lore(line).contains("Bram" + RETIRED_LINE) || label.contains("class") || lore(line).stream().anyMatch(s -> s.contains("this job can be picked"))) {
					wrong.add(label + " " + lore(line));
				}
			}
			ItemStack mason = QaHallScreenGameTests.jobLine(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " of the retired Bram's " + (lines.size() - 1) + " job lines don't say he's retired, e.g. "
				+ wrong.subList(0, Math.min(2, wrong.size())) + "; the free stonecutter's line reads '" + mason.getHoverName().getString() + "' " + lore(mason));
			helper.succeed();
		});
	}
}
