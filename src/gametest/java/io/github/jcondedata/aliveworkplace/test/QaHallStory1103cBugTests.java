package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1103-2153), bug tests (not registered: they fail until the mod is fixed): Doctor Wren, the Sickness
 * story's visiting physician, is a Cleric with her own AI, so a free brewing stand by the hall can become her
 * workstation as it would any Cleric's (vanilla's job-site claim, here set as it leaves her). village-hall.md (B889): "a story's visitor such as Doctor Wren [isn't] the village's to
 * employ: their row shows their own job, they count as working, and Job ▾ offers nothing". With the stand, her row
 * offers Change job and the hall retrains her (B895's cause: HallRetraining.canRetrain doesn't ask HallPeople.visitor).
 */
public class QaHallStory1103cBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos STAND = new BlockPos(19, 2, 17);
	private static final BlockPos CUTTER = new BlockPos(9, 2, 9);

	/** Doctor Wren at the hall, a free brewing stand two blocks off; {@code then} runs once she has made it her workstation. */
	private static void wrenAtAStand(GameTestHelper helper, int emeralds, TriConsumer then) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		QaSicknessGameTests.settings(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		QaSicknessGameTests.village(helper, 8);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, emeralds);
		helper.runAfterDelay(3, () -> {
			long day = Chronicle.day(level) + 1;
			QaSicknessGameTests.setDay(level, day, 1000);
			ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
			helper.assertTrue(s != null, "setup: the arc didn't start");
			QaSicknessGameTests.setDay(level, day + 1, 1000);
			SicknessStory.tick(level);
			Villager wren = SicknessStory.healer(level, s);
			helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "setup: no Doctor Wren");
			helper.setBlock(STAND, Blocks.BREWING_STAND);
			claim(helper, wren, () -> then.accept(new Ctx(level, hall, s, wren, player, seen)));
		});
	}

	record Ctx(ServerLevel level, BlockPos hall, ArcState s, Villager wren, ServerPlayer player, List<String> seen) {
	}

	interface TriConsumer extends Consumer<Ctx> {
	}

	/**
	 * Doctor Wren takes the brewing stand as her workstation, left as vanilla's AssignProfessionFromJobSite leaves a
	 * Cleric who claims a free one: its ticket taken and her job-site memory set (her own AI in this test area didn't
	 * get there: she arrives on the heightmap above the floor). She's kept still from then on.
	 */
	private static void claim(GameTestHelper helper, Villager wren, Runnable then) {
		ServerLevel level = helper.getLevel();
		BlockPos stand = helper.absolutePos(STAND);
		helper.runAfterDelay(2, () -> {
			wren.setNoAi(true);
			wren.teleportTo(stand.getX() + 1.5, stand.getY(), stand.getZ() + 0.5);
			level.getPoiManager().take(h -> h.is(PoiTypes.CLERIC), (h, p) -> p.equals(stand), stand, 1);
			wren.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), stand));
			helper.assertTrue(level.getPoiManager().getFreeTickets(stand) == 0 && wren.getVillagerData().getProfession() == VillagerProfession.CLERIC,
				"setup: the stand isn't Wren's (free " + level.getPoiManager().getFreeTickets(stand) + ")");
			then.run();
		});
	}

	/**
	 * Doctor Wren has taken the free brewing stand by the hall as her workstation (her own AI, as any Cleric does). Her
	 * People row should still offer no change of job, and Job ▾ then Mason twice should neither retrain her nor take the
	 * player's emeralds; she stays the arc's Cleric healer.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3bWrenStand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3bWrenStand")
	public void doctorWrenAtABrewingStandIsNotRetrained(GameTestHelper helper) {
		wrenAtAStand(helper, 64, c -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, c.player());
			CompoundTag row = HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, c.wren())));
			int money = c.player().getInventory().countItem(Items.EMERALD);
			c.seen().clear();
			menu.clickMenuButton(c.player(), HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, c.wren()));
			QaHallScreenGameTests.pickJob(helper, c.player(), menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, c.player(), menu, VillagerProfession.MASON);
			int paid = money - c.player().getInventory().countItem(Items.EMERALD);
			VillagerProfession now = c.wren().getVillagerData().getProfession();
			if (!row.getString("job_action").isEmpty() || now != VillagerProfession.CLERIC || paid != 0) {
				helper.fail("Doctor Wren at a brewing stand: row action '" + row.getString("job_action") + "' (kind " + row.getString("kind")
					+ "); Job ▾ then Mason twice made her " + now + " at " + QaHallScreenGameTests.site(c.wren()) + " for " + paid
					+ " emeralds; still the arc's healer: " + (SicknessStory.healer(c.level(), c.s()) == c.wren()) + "; told " + c.seen());
			}
			helper.succeed();
		});
	}

	/**
	 * The same Doctor Wren at her brewing stand given No job from the table (Job ▾, then No job twice): it should change
	 * nothing (her job "can't be changed here"), not take her Cleric's job away mid-story.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3bWrenNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3bWrenNoJob")
	public void doctorWrenAtABrewingStandIsNotGivenNoJob(GameTestHelper helper) {
		wrenAtAStand(helper, 0, c -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, c.player());
			c.seen().clear();
			menu.clickMenuButton(c.player(), HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, c.wren()));
			QaHallScreenGameTests.pickJob(helper, c.player(), menu, VillagerProfession.NONE);
			QaHallScreenGameTests.pickJob(helper, c.player(), menu, VillagerProfession.NONE);
			VillagerProfession now = c.wren().getVillagerData().getProfession();
			boolean site = c.wren().getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent();
			if (now != VillagerProfession.CLERIC || !site) {
				helper.fail("Doctor Wren at a brewing stand: Job ▾ then No job twice made her " + now + " (job site kept: " + site + "); told " + c.seen());
			}
			helper.succeed();
		});
	}
}
