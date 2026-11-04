package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1103-2153): the Village Hall's People table (40.3) acting on villagers a story is using, written from the
 * spec (40.3's Done when, village-hall.md's Job ▾, 31.18 The Sickness, 31.7 heart events), through the hall's own menu
 * clicks: the Sickness story's healer (the village's Nurse) retrained or given No job while she brews the cure or takes
 * it round, with a save and reload between; Doctor Wren mid-brew; and a heart event being told while the teller's job
 * changes. What must hold: the cure is neither lost nor doubled, a change of job is paid at its price (No job free), the
 * story finishes or ends cleanly with nothing stuck, and the villager keeps the new job.
 */
public class QaHallStory1103cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos STAND = new BlockPos(20, 2, 20);
	private static final BlockPos CUTTER = new BlockPos(9, 2, 9);
	/** A day time in the villagers' evening meeting hours (9000 to 11000): off work, awake. */
	private static final long OFF_WORK = 9500;
	private static final ResourceLocation LEARNING = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "work_learning");

	// --- helpers ------------------------------------------------------------------------------------------------------

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static int free(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(relative));
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static void twoClicks(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, VillagerProfession job) {
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
	}

	/** Every {@code item} anywhere within 48 blocks of the test: in hands and inventories of mobs and players, and dropped. */
	static int everywhere(GameTestHelper helper, Item item) {
		AABB box = helper.getBounds().inflate(48);
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, box)) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (v.getItemBySlot(slot).is(item)) {
					n += v.getItemBySlot(slot).getCount();
				}
			}
			n += v.getInventory().countItem(item);
		}
		for (Player p : helper.getLevel().getEntitiesOfClass(Player.class, box)) {
			n += p.getInventory().countItem(item);
		}
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	/** The Sickness's village (8 by the hall, a Nurse at her brewing stand), a free stonecutter in it, and a paying player. */
	private record Sick(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, List<Villager> people, Villager nurse, ServerPlayer player, List<String> seen) {
	}

	private static Sick sick(GameTestHelper helper, boolean nurse, int emeralds) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		QaSicknessGameTests.settings(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		List<Villager> people = QaSicknessGameTests.village(helper, 8);
		Villager n = nurse ? QaSicknessGameTests.nurse(helper, STAND) : null;
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, emeralds);
		return new Sick(level, helper.absolutePos(HALL), entity, people, n, player, seen);
	}

	/** The arc started today, the healer told and handed the herbs: the brew has begun. */
	private static ArcState brewing(GameTestHelper helper, Sick k, Villager healer) {
		ArcState s = Arcs.running(k.level, k.hall);
		helper.assertTrue(s != null, "setup: the arc didn't start");
		QaSicknessGameTests.gather(helper, k.level, s, k.player, healer);
		helper.assertTrue(SicknessStory.stamp(s, "brew_from") >= 0 && !s.flags.containsKey(SicknessStory.BREWED), "setup: no brew under way");
		return s;
	}

	private static ArcState start(GameTestHelper helper, Sick k) {
		QaSicknessGameTests.setDay(k.level, Chronicle.day(k.level) + 1, 1000);
		ArcState s = Arcs.start(k.level, k.hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "setup: the arc didn't start");
		return s;
	}

	/** The cure went round: the arc is over as cured, nobody coughs, and no Three-Herb Tonic is left anywhere. */
	private static void curedOnce(GameTestHelper helper, Sick k, ArcState s, String when) {
		helper.assertTrue(Arcs.running(k.level, k.hall) == null && s.flags.containsKey(SicknessStory.CURED),
			when + ": the cure didn't go round (arc " + (Arcs.running(k.level, k.hall) == null ? "over" : "chapter " + s.chapter) + ", cured "
				+ s.flags.containsKey(SicknessStory.CURED) + ", course " + SicknessStory.stamp(s, "course") + ")");
		helper.assertTrue(QaSicknessGameTests.grey(k.people) == 0, when + ": still coughing: " + QaSicknessGameTests.grey(k.people));
		int tonics = everywhere(helper, ModItems.THREE_HERB_TONIC);
		helper.assertTrue(tonics == 0, when + ": " + tonics + " Three-Herb Tonic left in the world after the cure went round");
		long cured = QaSicknessGameTests.chronicle(k.entity).stream().filter(l -> l.startsWith("The Three-Herb Tonic went round")).count();
		helper.assertTrue(cured == 1, when + ": the chronicle says the tonic went round " + cured + " times: " + QaSicknessGameTests.chronicle(k.entity));
	}

	// --- The Sickness's healer, the village's Nurse ---------------------------------------------------------------------

	/**
	 * Nurse Ada is brewing the Three-Herb Tonic (a quarter done) when the player opens the People table: she's an
	 * ordinary worker of the village, listed working with Change job offered. Two clicks on Mason retrain her at the
	 * stonecutter for her price (not for free), the brew isn't started over, and the cure still goes round once: everyone
	 * well, the arc over as cured, no tonic left in the world, one chronicle line.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3RetrainBrew"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3RetrainBrew")
	public void theHealerRetrainedMidBrewPaysAndTheCureStillComesOnce(GameTestHelper helper) {
		Sick k = sick(helper, true, 64);
		helper.runAfterDelay(3, () -> {
			ArcState s = start(helper, k);
			brewing(helper, k, k.nurse);
			long from = SicknessStory.stamp(s, "brew_from");
			k.level.setDayTime(from + SicknessStory.BREW / 4);
			SicknessStory.tick(k.level);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, k.player);
			CompoundTag r = row(helper, menu, k.nurse);
			helper.assertTrue(r.getString("kind").equals("worker") && r.getString("job_action").equals("retrain"),
				"the brewing Nurse's row isn't a worker's with Change job: " + r);
			int price = Retraining.price(k.nurse);
			int money = emeralds(k.player);
			twoClicks(helper, k.player, menu, k.nurse, VillagerProfession.MASON);
			helper.assertTrue(k.nurse.getVillagerData().getProfession() == VillagerProfession.MASON
					&& QaHallScreenGameTests.site(k.nurse).equals(java.util.Optional.of(helper.absolutePos(CUTTER))),
				"two clicks on Mason: she's " + k.nurse.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(k.nurse) + "; told " + k.seen);
			helper.assertTrue(price > 0 && emeralds(k.player) == money - price,
				"the healer's retraining cost " + (money - emeralds(k.player)) + " emeralds, her price is " + price);
			helper.assertTrue(free(helper, STAND) == 1, "her brewing stand isn't free after the retraining: " + free(helper, STAND));
			helper.assertTrue(SicknessStory.stamp(s, "brew_from") == from, "the brew started over: from " + from + " to " + SicknessStory.stamp(s, "brew_from"));
			k.level.setDayTime(from + SicknessStory.BREW);
			QaSicknessGameTests.runRound(k.level, k.hall, s, 60);
			curedOnce(helper, k, s, "retrained mid-brew");
			helper.assertTrue(k.nurse.getVillagerData().getProfession() == VillagerProfession.MASON && k.nurse.getMainHandItem().isEmpty(),
				"after the round she's " + k.nurse.getVillagerData().getProfession() + " holding " + k.nurse.getMainHandItem());
			helper.succeed();
		});
	}

	/**
	 * The tonic is brewed and Nurse Ada is taking it round, the bottle in her hand, when the player gives her No job from
	 * the table (two clicks, free). The bottle isn't dropped for anyone to pick up, she's jobless with her stand free,
	 * and the round still finishes: everyone well, no tonic left anywhere.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3NoJobRound"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3NoJobRound")
	public void theHealerGivenNoJobMidRoundDropsNoTonicAndTheRoundEnds(GameTestHelper helper) {
		Sick k = sick(helper, true, 0);
		helper.runAfterDelay(3, () -> {
			ArcState s = start(helper, k);
			brewing(helper, k, k.nurse);
			k.level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW);
			SicknessStory.tick(k.level);
			helper.assertTrue(s.flags.containsKey(SicknessStory.BREWED) && k.nurse.getMainHandItem().is(ModItems.THREE_HERB_TONIC),
				"setup: not on the round (brewed " + s.flags.containsKey(SicknessStory.BREWED) + ", holding " + k.nurse.getMainHandItem() + ")");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, k.player);
			twoClicks(helper, k.player, menu, k.nurse, VillagerProfession.NONE);
			helper.assertTrue(k.nurse.getVillagerData().getProfession() == VillagerProfession.NONE && free(helper, STAND) == 1,
				"No job twice: she's " + k.nurse.getVillagerData().getProfession() + ", stand free " + free(helper, STAND) + "; told " + k.seen);
			int dropped = 0;
			for (ItemEntity drop : k.level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48))) {
				if (drop.getItem().is(ModItems.THREE_HERB_TONIC)) {
					dropped += drop.getItem().getCount();
				}
			}
			helper.assertTrue(dropped == 0, "No job mid-round dropped " + dropped + " Three-Herb Tonic on the ground");
			helper.assertTrue(everywhere(helper, ModItems.THREE_HERB_TONIC) <= 1, "more than one tonic after No job: " + everywhere(helper, ModItems.THREE_HERB_TONIC));
			QaSicknessGameTests.runRound(k.level, k.hall, s, 60);
			curedOnce(helper, k, s, "No job mid-round");
			helper.succeed();
		});
	}

	/**
	 * Retrained mid-brew, then the world saved and loaded (the Nurse and the stories read back from their saves): she
	 * keeps the Mason job and its block, the brew carries on from where it was, and the cure goes round once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3RetrainReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3RetrainReload")
	public void theHealerRetrainedMidBrewThenReloadedStillCures(GameTestHelper helper) {
		Sick k = sick(helper, true, 64);
		helper.runAfterDelay(3, () -> {
			start(helper, k);
			ArcState s0 = Arcs.running(k.level, k.hall);
			brewing(helper, k, k.nurse);
			long from = SicknessStory.stamp(s0, "brew_from");
			k.level.setDayTime(from + SicknessStory.BREW / 3);
			SicknessStory.tick(k.level);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, k.player);
			twoClicks(helper, k.player, menu, k.nurse, VillagerProfession.MASON);
			k.player.closeContainer();
			helper.assertTrue(k.nurse.getVillagerData().getProfession() == VillagerProfession.MASON, "setup: not retrained; told " + k.seen);
			// Save and load: the stories and the Nurse
			Stories.Data loaded = Stories.Data.load(Stories.Data.get(k.level).save(new CompoundTag(), k.level.registryAccess()), k.level.registryAccess());
			k.level.getDataStorage().set("aliveworkplace_stories", loaded);
			CompoundTag tag = k.nurse.saveWithoutId(new CompoundTag());
			k.nurse.discard();
			Villager back = EntityType.VILLAGER.create(k.level);
			back.load(tag);
			k.level.addFreshEntity(back);
			ArcState s = Arcs.running(k.level, k.hall);
			helper.assertTrue(s != null && SicknessStory.stamp(s, "brew_from") == from && !s.flags.containsKey(SicknessStory.BREWED),
				"after the reload the brew isn't where it was: " + (s == null ? "no arc" : "from " + SicknessStory.stamp(s, "brew_from")));
			helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.MASON
					&& QaHallScreenGameTests.site(back).equals(java.util.Optional.of(helper.absolutePos(CUTTER))),
				"after the reload she's " + back.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(back));
			helper.assertTrue(SicknessStory.healer(k.level, s) == back || SicknessStory.healer(k.level, s) == null,
				"after the reload the healer is someone else: " + SicknessStory.healer(k.level, s));
			k.level.setDayTime(from + SicknessStory.BREW);
			QaSicknessGameTests.runRound(k.level, k.hall, s, 60);
			curedOnce(helper, new Sick(k.level, k.hall, k.entity, k.people, back, k.player, k.seen), s, "retrained mid-brew and reloaded");
			helper.succeed();
		});
	}

	// --- Doctor Wren mid-brew ---------------------------------------------------------------------------------------

	/**
	 * No healer in the village: Doctor Wren comes and is brewing by the hall (half done) when the player opens the
	 * table. Her row says Cleric with her level, she's counted working (not jobless), and her Job ▾ offers nothing; a
	 * stale screen's No job and Mason picks change nothing and cost nothing; the brew isn't set back, and her cure goes
	 * round once.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3WrenBrew"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3WrenBrew")
	public void doctorWrenMidBrewCantBeRetrainedAndHerCureComes(GameTestHelper helper) {
		Sick k = sick(helper, false, 64);
		helper.runAfterDelay(3, () -> {
			long day = Chronicle.day(k.level) + 1;
			QaSicknessGameTests.setDay(k.level, day, 1000);
			ArcState s = Arcs.start(k.level, k.hall, QaSicknessGameTests.arc(helper));
			helper.assertTrue(s != null, "setup: the arc didn't start");
			QaSicknessGameTests.setDay(k.level, day + 1, 1000);
			SicknessStory.tick(k.level);
			Villager wren = SicknessStory.healer(k.level, s);
			helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "setup: no Doctor Wren");
			wren.setNoAi(true); // she stays by the hall (the round steps over to each patient)
			QaSicknessGameTests.gather(helper, k.level, s, k.player, wren);
			long from = SicknessStory.stamp(s, "brew_from");
			k.level.setDayTime(from + SicknessStory.BREW / 2);
			SicknessStory.tick(k.level);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, k.player);
			VillageHalls.Census census = VillageHalls.census(k.level, k.hall);
			CompoundTag r = row(helper, menu, wren);
			helper.assertTrue(r.getString("kind").equals("worker") && r.getString("job").equals("entity.minecraft.villager.cleric") && r.getInt("level") > 0
					&& r.getString("job_action").isEmpty(), "Doctor Wren's row mid-brew: " + r);
			helper.assertTrue(census.workers().contains(wren) && !census.jobless().contains(wren), "Doctor Wren isn't counted working mid-brew");
			int money = emeralds(k.player);
			k.seen.clear();
			menu.clickMenuButton(k.player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, wren));
			helper.assertTrue(k.seen.contains("Doctor Wren's job can't be changed here."), "Job ▾ on Doctor Wren mid-brew told the player: " + k.seen);
			for (int i = 0; i < 2; i++) {
				QaHallScreenGameTests.pickJob(helper, k.player, menu, VillagerProfession.NONE);
				QaHallScreenGameTests.pickJob(helper, k.player, menu, VillagerProfession.MASON);
			}
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC && free(helper, CUTTER) == 1 && emeralds(k.player) == money,
				"after stale picks Doctor Wren is " + wren.getVillagerData().getProfession() + ", stonecutter free " + free(helper, CUTTER) + ", emeralds " + emeralds(k.player));
			helper.assertTrue(SicknessStory.stamp(s, "brew_from") == from && SicknessStory.healer(k.level, s) == wren,
				"the brew or the healer changed: from " + SicknessStory.stamp(s, "brew_from") + ", healer " + SicknessStory.healer(k.level, s));
			k.level.setDayTime(from + SicknessStory.BREW);
			QaSicknessGameTests.runRound(k.level, k.hall, s, 60);
			curedOnce(helper, k, s, "Wren after stale picks");
			helper.succeed();
		});
	}

	// --- A heart event told while the job changes -----------------------------------------------------------------------

	/** A named Librarian, Dara, at four hearts with the player (her two-heart story told), standing by the hall's door. */
	private record Heart(ServerLevel level, BlockPos hall, Villager dara, ServerPlayer player, List<String> seen) {
	}

	private static Heart heart(GameTestHelper helper, int emeralds) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		HeartEvents.forget();
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean friendship = Friendship.ENABLED;
		boolean events = HeartEvents.ENABLED;
		Map<ResourceLocation, JsonElement> files = HeartEvents.files(level.getServer().getResourceManager());
		HeartEvents.load(files);
		VillageHalls.RADIUS = 14;
		Friendship.ENABLED = true;
		HeartEvents.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Friendship.ENABLED = friendship;
			HeartEvents.ENABLED = events;
			HeartEvents.forget();
			CivicEffects.forget();
		});
		// Off work (31.7: a heart event starts only then): the evening's meeting hours, whatever time an earlier batch left.
		// An employed villager's brain takes its activity from the schedule when it's set up, so at work hours (2000 to
		// 9000) Dara would be at work and never begin.
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		level.setDayTime(Math.floorDiv(time, 24000L) * 24000L + OFF_WORK);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos lectern = new BlockPos(13, 2, 19);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(16, 2, 17));
		Jobs.employ(level, dara, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		dara.setVillagerXp(5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, emeralds);
		return new Heart(level, helper.absolutePos(HALL), dara, player, seen);
	}

	/** Dara begins "My work" (the learning trade's four-heart story) to the player by the hall: one line said. */
	private static long begin(GameTestHelper helper, Heart h) {
		CivicEffects.forget();
		BlockPos at = h.hall;
		h.player.teleportTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		Friendship.add(h.dara, h.player, 400);
		Friendship.told(h.dara, h.player, "aliveworkplace:before_hall");
		HeartEvents.Event pending = HeartEvents.pending(h.level, h.dara, h.player.getUUID());
		helper.assertTrue(pending != null && pending.id().equals(LEARNING), "setup: Dara's pending story is " + (pending == null ? null : pending.id()));
		helper.assertTrue(!Chatter.busy(h.dara), "setup: Dara is at work (" + h.dara.getBrain().getActiveNonCoreActivity() + " at day time "
			+ h.level.getDayTime() % 24000 + ")");
		long start = HeartEventGameTests.later(h.level);
		HeartEvents.tick(h.level, start);
		HeartEvents.Telling telling = HeartEvents.telling(h.dara);
		helper.assertTrue(telling != null && telling.said == 1 && telling.event.id().equals(LEARNING), "setup: Dara didn't begin her story: " + telling
			+ " (activity " + h.dara.getBrain().getActiveNonCoreActivity() + ", offer " + PersonalRequests.offer(h.dara) + ")");
		return start;
	}

	/**
	 * Ticks the heart engine on for a whole story's time after the change; the telling must have finished or ended, the
	 * four-heart story kept at most once (+20 at most once, one chronicle line at most), and nothing left half told.
	 */
	private static void endsCleanly(GameTestHelper helper, Heart h, long start, String change) {
		for (int i = 1; i <= 8; i++) {
			HeartEvents.tick(h.level, start + (long) i * HeartEvents.LINE_EVERY);
		}
		HeartEvents.Telling telling = HeartEvents.telling(h.dara);
		List<String> told = Friendship.of(h.dara).bond(h.player.getUUID()).told();
		int points = Friendship.points(h.dara, h.player.getUUID());
		helper.assertTrue(telling == null || telling.event.id().equals(LEARNING) == false && telling.said >= 1,
			change + ": Dara is stuck telling her old story: " + (telling == null ? null : telling.event.id() + " said " + telling.said));
		long fours = told.stream().filter(id -> !id.equals("aliveworkplace:before_hall")).count();
		helper.assertTrue(fours <= 1 && points == 400 + 20 * (int) fours,
			change + ": told " + told + " with " + points + " points (400 before)");
		List<String> lines = HeartEventGameTests.friendLines(h.level, h.hall);
		helper.assertTrue(lines.size() == fours, change + ": the chronicle's friend lines " + lines + " for told " + told);
		// Whatever was told, nothing more is waiting at four hearts once a four-heart story was told
		if (fours == 1) {
			HeartEvents.Event next = HeartEvents.pending(h.level, h.dara, h.player.getUUID());
			helper.assertTrue(next == null, change + ": after one four-heart story, another is pending: " + (next == null ? null : next.id()));
		}
	}

	/**
	 * Dara, a Librarian, is telling her four-heart "My work" story when the player retrains her as a Mason from the
	 * People table (two clicks, her price paid). No crash; the telling finishes or ends with nothing half kept (no stuck
	 * telling, +20 at most once, one chronicle line at most), and she keeps the Mason job at the stonecutter.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3HeartRetrain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3HeartRetrain")
	public void aHeartEventToldWhileRetrainedEndsCleanlyAndTheJobStays(GameTestHelper helper) {
		Heart h = heart(helper, 64);
		helper.runAfterDelay(3, () -> {
			long start = begin(helper, h);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, h.player);
			int money = emeralds(h.player);
			int price = Retraining.price(h.dara);
			twoClicks(helper, h.player, menu, h.dara, VillagerProfession.MASON);
			helper.assertTrue(h.dara.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(h.player) == money - price,
				"mid-story retraining: she's " + h.dara.getVillagerData().getProfession() + ", paid " + (money - emeralds(h.player)) + " of " + price + "; told " + h.seen);
			endsCleanly(helper, h, start, "retrained mid-story");
			helper.assertTrue(h.dara.getVillagerData().getProfession() == VillagerProfession.MASON
					&& QaHallScreenGameTests.site(h.dara).equals(java.util.Optional.of(helper.absolutePos(CUTTER))),
				"after her story she's " + h.dara.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(h.dara));
			helper.succeed();
		});
	}

	/**
	 * As above, with No job: the telling finishes or ends cleanly, and she stays jobless (her lectern free), not put back
	 * to the Librarian her story was about.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3HeartNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3HeartNoJob")
	public void aHeartEventToldWhileGivenNoJobEndsCleanly(GameTestHelper helper) {
		Heart h = heart(helper, 0);
		helper.runAfterDelay(3, () -> {
			long start = begin(helper, h);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, h.player);
			twoClicks(helper, h.player, menu, h.dara, VillagerProfession.NONE);
			helper.assertTrue(h.dara.getVillagerData().getProfession() == VillagerProfession.NONE, "No job mid-story: she's " + h.dara.getVillagerData().getProfession() + "; told " + h.seen);
			endsCleanly(helper, h, start, "No job mid-story");
			helper.assertTrue(h.dara.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(h.dara).isEmpty()
					&& free(helper, new BlockPos(13, 2, 19)) == 1,
				"after her story she's " + h.dara.getVillagerData().getProfession() + " at " + QaHallScreenGameTests.site(h.dara) + ", lectern free " + free(helper, new BlockPos(13, 2, 19)));
			helper.succeed();
		});
	}

	/**
	 * The player retrains Dara before she begins: at four hearts she then tells the Mason's story ("My work" for the
	 * building trades), not the Librarian's, as the event's job condition says.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHs3HeartAfter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHs3HeartAfter")
	public void retrainedBeforeHerStorySheTellsTheNewJobs(GameTestHelper helper) {
		Heart h = heart(helper, 64);
		helper.runAfterDelay(3, () -> {
			CivicEffects.forget();
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, h.player);
			twoClicks(helper, h.player, menu, h.dara, VillagerProfession.MASON);
			h.player.closeContainer();
			helper.assertTrue(h.dara.getVillagerData().getProfession() == VillagerProfession.MASON, "setup: not retrained; told " + h.seen);
			Friendship.add(h.dara, h.player, 400);
			Friendship.told(h.dara, h.player, "aliveworkplace:before_hall");
			HeartEvents.Event pending = HeartEvents.pending(h.level, h.dara, h.player.getUUID());
			helper.assertTrue(pending != null && pending.id().getPath().equals("work_building"),
				"a Mason at four hearts has " + (pending == null ? null : pending.id()) + " to tell, not the building trades' story");
			helper.succeed();
		});
	}
}
