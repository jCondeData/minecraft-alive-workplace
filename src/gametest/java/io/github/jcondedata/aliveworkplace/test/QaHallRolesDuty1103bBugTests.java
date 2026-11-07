package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1103-1554, round 2), bug tests (not registered: they fail until the mod is fixed): B889's pattern, a
 * villager with a job but no work block shown as jobless and given a free first job, on two more who aren't the
 * village's to employ: the Sickness story's Doctor Wren (a travelling physician, a Cleric who keeps the robe without a
 * workstation) and a relief rider (a Guard from an allied village, there till dawn).
 */
public class QaHallRolesDuty1103bBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);

	/** What the row says and what one click on Mason does, as a failure message; null when the row is right (no change offered). */
	private static String jobless(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, List<String> seen) {
		String was = v.getVillagerData().getProfession().toString();
		CompoundTag row = HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
		if (!row.getString("kind").equals("jobless") && row.getString("job_action").isEmpty()) {
			return null;
		}
		menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, v));
		QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
		return v.getName().getString() + " (" + was + ") is listed as kind '" + row.getString("kind") + "', job '" + row.getString("job")
			+ "', action '" + row.getString("job_action") + "'; one click on Mason made them " + v.getVillagerData().getProfession()
			+ " at " + QaHallScreenGameTests.site(v) + "; told " + seen;
	}

	/**
	 * The Sickness arc with no healer in the village sends for Doctor Wren, who comes the next morning to brew the cure
	 * by the hall. On the People table she shouldn't be a jobless villager to hand the stonecutter to.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbWren"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbWren")
	public void doctorWrenIsNotOfferedAJob(GameTestHelper helper) {
		Leftovers.clear(helper);
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
		helper.setBlock(STONECUTTER.offset(-6, 0, -4), Blocks.STONECUTTER); // in the 12-block village
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			long day = Chronicle.day(level) + 1;
			QaSicknessGameTests.setDay(level, day, 1000);
			ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
			helper.assertTrue(s != null, "setup: the arc didn't start");
			QaSicknessGameTests.setDay(level, day + 1, 1000);
			SicknessStory.tick(level);
			Villager wren = SicknessStory.healer(level, s);
			helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "setup: no Doctor Wren");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			String wrong = jobless(helper, player, menu, wren, seen);
			if (wrong != null) {
				helper.fail(wrong + "; the arc's healer now " + (SicknessStory.healer(level, s) == wren ? "still Wren" : SicknessStory.healer(level, s)));
			}
			helper.succeed();
		});
	}

	/**
	 * A relief rider from an allied village (a Guard with no post, there till dawn) arrives at the hall. On the People
	 * table he shouldn't be a jobless villager to hand the stonecutter to.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbRelief"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbRelief")
	public void aReliefRiderIsNotOfferedAJob(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int edge = Relief.EDGE;
		Relief.EDGE = 5; // they muster 5 blocks from the hall, inside the 14-block village
		Leftovers.after(helper, () -> {
			Relief.EDGE = edge;
			Relief.forget();
		});
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			Relief.arrive(level, new Relief.Band(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(200, 0, 0)),
				Component.literal("Oakford"), 1, level.getGameTime(), level.getGameTime(), false));
			List<Villager> riders = Relief.riders(level, hall);
			helper.assertTrue(riders.size() == 1, "setup: riders " + riders.size());
			Villager rider = riders.get(0);
			Leftovers.after(helper, () -> {
				if (rider.getVehicle() != null) {
					rider.getVehicle().discard();
				}
				rider.discard();
			});
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			String wrong = jobless(helper, player, menu, rider, seen);
			if (wrong != null) {
				helper.fail(wrong + "; still a relief rider: " + Relief.isRider(rider));
			}
			helper.succeed();
		});
	}

	/** A Guard called {@code name} at a guard post by the hall, a sword in hand ({@code still}: no AI). */
	private static Villager guard(GameTestHelper helper, String name, boolean still) {
		BlockPos post = new BlockPos(12, 2, 15);
		helper.setBlock(post, io.github.jcondedata.aliveworkplace.registry.ModBlocks.GUARD_POST);
		Villager v = QaHallScreenGameTests.villager(helper, name, post.offset(0, 0, 1));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), v, helper.absolutePos(post),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD_POST_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD);
		v.setNoAi(still);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return v;
	}

	/** A bandits' lair {@code dx} blocks east of the hall, on the floor, with no camp built; its captain stands there. Cleared after. */
	private static void lair(GameTestHelper helper, int dx) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		level.setDayTime(1000);
		BlockPos pos = hall.offset(dx, 0, 0);
		net.minecraft.world.entity.monster.Vindicator captain = net.minecraft.world.entity.EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.setNoAi(true);
		captain.setPersistenceRequired();
		captain.setInvulnerable(true);
		captain.addTag(io.github.jcondedata.aliveworkplace.threat.Lairs.TAG);
		captain.addTag(io.github.jcondedata.aliveworkplace.threat.Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		io.github.jcondedata.aliveworkplace.threat.Lairs.putForTest(level, new io.github.jcondedata.aliveworkplace.threat.Lairs.Lair(pos, hall, captain.getUUID(),
			Chronicle.day(level), io.github.jcondedata.aliveworkplace.threat.Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3));
		Leftovers.after(helper, () -> {
			captain.discard();
			io.github.jcondedata.aliveworkplace.threat.Lairs.forget(level);
			Scouts.forget(level);
			level.setDayTime(time);
		});
	}

	private static int spyglasses(Villager v) {
		int n = 0;
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			if (v.getItemBySlot(slot).is(net.minecraft.world.item.Items.SPYGLASS)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A scout at his lookout, the spyglass raised (in his main hand, the sword in the other), retrained as a Mason: the
	 * mission is called off, and the spyglass is put away (not left raised in the Mason's main hand); it isn't lost.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrdbScoutLook"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrdbScoutLook")
	public void aScoutRetrainedAtTheLookoutPutsTheSpyglassAway(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager ash = guard(helper, "Ash", false);
		ash.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			lair(helper, 12);
			helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: not sent");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(Scouts.missionOf(level, ash.getUUID()).map(m -> m.phase() == Scouts.Phase.LOOK).orElse(false)
					&& ash.getMainHandItem().is(Items.SPYGLASS), "setup: not looking: " + Scouts.missionOf(level, ash.getUUID()) + ", holding " + ash.getMainHandItem());
				ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
				QaHallScreenGameTests.jobDropdown(helper, player, menu, ash);
				QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
				QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
				helper.assertTrue(ash.getVillagerData().getProfession() == VillagerProfession.MASON, "not retrained: " + ash.getVillagerData() + "; " + seen);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(!Scouts.out(ash), "the mission goes on: " + Scouts.missionOf(level, ash.getUUID()));
					helper.assertTrue(spyglasses(ash) == 1, "the spyglass: " + spyglasses(ash));
					helper.assertTrue(!ash.getMainHandItem().is(Items.SPYGLASS), "the Mason still holds the spyglass up, main hand " + ash.getMainHandItem()
						+ ", off hand " + ash.getOffhandItem());
					helper.succeed();
				});
			});
		});
	}

}
