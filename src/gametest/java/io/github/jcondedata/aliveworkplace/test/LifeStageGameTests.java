package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * Life stages: elders (ROADMAP 34.19). A villager is an elder after the configured grown days, with the world's clock
 * moved on day by day, and the chronicle notes it once; an elder walks slower than a younger villager, by the walk
 * target every worker's {@link Walker} sets and over real ground; "a quiet old age" in the mood of an elder who is fed
 * and housed, and the four elder chatter lines; the day they grew up and the chronicle's note kept through a reload; a
 * child who grows up in the hall's own round is grown from today; and {@code villagerAges} off. (The passing and the
 * Evergreen Charm: {@link AgelessElderGameTests}.)
 */
public class LifeStageGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String NOW_AN_ELDER = "Bram is an elder now";
	private static final List<String> ELDER_LINES = List.of("In my day this was all fields.", "I've seen more harvests than you've had hot dinners.",
		"These knees knew every path in the village, once.", "Sit a while. The young ones are always in a hurry.");

	/** With the clock moved on: not an elder on grown day 119, an elder on day 120, said by the hall and noted once by the chronicle. */
	//$ gametest_ticks_batch AREA '100' '"lifeStageElder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lifeStageElder")
	public void aVillagerIsAnElderAfterTheConfiguredDays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 2);
		Villager tom = villager(helper, new BlockPos(7, 2, 6), "Tom", VillagerProfession.FARMER, 2);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", VillagerProfession.FLETCHER, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long clock = level.getDayTime();
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today); // grown up today
			LifeStages.setAdultSince(tom, today + 20); // grows up in 20 days
			LifeStages.setAdultSince(ida, today + 90);
			List<Villager> all = List.of(bram, tom, ida);
			// Everything below happens within this tick, and the clock goes back: no other test sees the days pass.
			try {
				helper.assertTrue(LifeStages.grownDays(bram, today) == 0 && !LifeStages.isElder(bram) && LifeStages.stage(bram, today) == LifeStages.Stage.GROWN, "Bram today");
				level.setDayTime(clock + 119 * VillageNeeds.DAY);
				helper.assertTrue(Chronicle.day(level) == today + 119, "the clock didn't move: day " + Chronicle.day(level) + " from " + today);
				LifeStages.round(level, hallPos, all);
				helper.assertTrue(!LifeStages.isElder(bram) && LifeStages.stage(bram, Chronicle.day(level)) == LifeStages.Stage.GROWN, "an elder after 119 grown days");
				helper.assertTrue(lore(level, hallPos, bram).stream().noneMatch(l -> l.startsWith("Elder")), "the hall on day 119: " + lore(level, hallPos, bram));
				helper.assertTrue(!chronicle(hall).contains(NOW_AN_ELDER) && !ModAttachments.ELDER_NOTED.has(bram), "the chronicle on day 119: " + chronicle(hall));

				level.setDayTime(clock + 120 * VillageNeeds.DAY);
				helper.assertTrue(LifeStages.isElder(bram) && LifeStages.stage(bram, Chronicle.day(level)) == LifeStages.Stage.ELDER
					&& LifeStages.elderDays(bram, Chronicle.day(level)) == 0, "not an elder after 120 grown days");
				helper.assertTrue(lore(level, hallPos, bram).contains("Elder · grown 120 days"), "the hall on day 120: " + lore(level, hallPos, bram));
				LifeStages.round(level, hallPos, all);
				helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LIFE && e.day() == today + 120
					&& e.text().getString().equals(NOW_AN_ELDER)), "the chronicle on the day he became one: " + chronicle(hall));
				// Once: not in the next round, nor the next days.
				LifeStages.round(level, hallPos, all);
				level.setDayTime(clock + 125 * VillageNeeds.DAY);
				LifeStages.round(level, hallPos, all);
				helper.assertTrue(chronicle(hall).stream().filter(NOW_AN_ELDER::equals).count() == 1, "noted once: " + chronicle(hall));
				helper.assertTrue(lore(level, hallPos, bram).contains("Elder · grown 125 days"), "the hall on day 125: " + lore(level, hallPos, bram));
				// Tom (grown 105 days) and Ida (35) are not elders, and nothing is written for them.
				helper.assertTrue(!LifeStages.isElder(tom) && !LifeStages.isElder(ida), "Tom or Ida is an elder too soon");
				helper.assertTrue(chronicle(hall).stream().filter(l -> l.endsWith(" is an elder now")).count() == 1, "others noted: " + chronicle(hall));

				// villagerElderDays 30: Ida is one now, and is noted in her turn.
				LifeStages.ELDER_DAYS = 30;
				helper.assertTrue(LifeStages.isElder(ida) && LifeStages.elderDays(ida, Chronicle.day(level)) == 5, "an elder after 30 configured days");
				LifeStages.round(level, hallPos, List.of(ida));
				helper.assertTrue(chronicle(hall).contains("Ida is an elder now"), "Ida, with 30 days configured: " + chronicle(hall));
				LifeStages.ELDER_DAYS = 120;
				helper.assertTrue(!LifeStages.isElder(ida), "Ida with 120 days again");
			} finally {
				level.setDayTime(clock);
			}
			helper.assertTrue(Chronicle.day(level) == today, "the clock wasn't put back");
			helper.succeed();
		});
	}

	/** An elder's walk target is slower than a younger worker's, an ageless elder's too; and over real ground the elder covers 15% less. */
	//$ gametest_ticks_batch AREA '200' '"lifeStageWalk"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "lifeStageWalk")
	public void anElderWalksSlower(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> LifeStages.AGES = true);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		Villager bram = walker(helper, new BlockPos(4, 2, 2), "Bram");
		Villager tom = walker(helper, new BlockPos(10, 2, 2), "Tom");
		Villager odo = walker(helper, new BlockPos(16, 2, 2), "Odo");
		LifeStages.setAdultSince(bram, today - 130);
		LifeStages.setAdultSince(tom, today - 30);
		LifeStages.setAdultSince(odo, today - 300);
		ModAttachments.AGELESS.set(odo, true);
		BlockPos bramGoal = helper.absolutePos(new BlockPos(4, 2, 20));
		BlockPos tomGoal = helper.absolutePos(new BlockPos(10, 2, 20));
		BlockPos odoGoal = helper.absolutePos(new BlockPos(16, 2, 20));

		// The walk every worker asks for (miners, lumberjacks... through Walker; builders and explorers through requestWalk).
		Walker bramWalk = new Walker(0.5f);
		Walker tomWalk = new Walker(0.5f);
		helper.assertTrue(LifeStages.walk(bram) == LifeStages.ELDER_WALK && LifeStages.walk(tom) == 1f && LifeStages.walk(odo) == LifeStages.ELDER_WALK,
			"walks: " + LifeStages.walk(bram) + ", " + LifeStages.walk(tom) + ", " + LifeStages.walk(odo));
		// With villagerAges off nobody is an elder, so nobody is slowed.
		LifeStages.AGES = false;
		Walker.requestWalk(bram, bramGoal, 0.5f, 1, 0);
		helper.assertTrue(Math.abs(speed(bram) - 0.5f) < 0.001f, "an elder's walk with villagerAges off: " + speed(bram));
		bram.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		LifeStages.AGES = true;
		bramWalk.walkTo(level, bram, bramGoal, 1.5);
		tomWalk.walkTo(level, tom, tomGoal, 1.5);
		Walker.requestWalk(odo, odoGoal, 0.5f, 1, 0);
		walkersOnly(bram, tom);
		helper.assertTrue(Math.abs(speed(tom) - 0.5f) < 0.001f, "the younger villager's walk target: " + speed(tom));
		helper.assertTrue(speed(bram) < 0.49f && Math.abs(speed(bram) - 0.5f * LifeStages.ELDER_WALK) < 0.001f, "the elder's walk target: " + speed(bram));
		helper.assertTrue(Math.abs(speed(odo) - speed(bram)) < 0.001f, "the ageless elder's walk target: " + speed(odo));
		odo.discard();
		Leftovers.after(helper, () -> {
			bram.discard();
			tom.discard();
		});

		// Side by side over real ground, the same 60 ticks at a steady walk: the elder covers 15% less of it.
		int[] ticks = new int[1];
		double[] from = new double[2];
		// What the elder's walk was asked at, tick by tick (a failure says when it changed and what the villager was then).
		StringBuilder asked = new StringBuilder();
		float[] last = {Float.NaN};
		helper.onEachTick(() -> {
			ticks[0]++;
			float now = bram.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getSpeedModifier()).orElse(-1f);
			int near = bram.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getCloseEnoughDist()).orElse(-1);
			if (now != last[0] && asked.length() < 2000) {
				last[0] = now;
				asked.append(" [tick ").append(ticks[0]).append(": ").append(now).append(", elder ").append(LifeStages.isElder(bram))
					.append(", ages ").append(LifeStages.AGES).append('/').append(LifeStages.ELDER_DAYS).append(", grown ").append(LifeStages.grownDays(bram, Chronicle.day(level)))
					.append(", near ").append(near).append(", nav ").append(bram.getNavigation().isDone() ? "idle" : "walking").append(", z ").append(Math.round(bram.getZ() * 100) / 100.0).append(']');
			}
			if (ticks[0] <= 100) {
				bramWalk.walkTo(level, bram, bramGoal, 1.5);
				tomWalk.walkTo(level, tom, tomGoal, 1.5);
				walkersOnly(bram, tom);
			}
			if (ticks[0] == 40) { // (both are well under way by now, whenever their paths were found)
				from[0] = bram.getZ();
				from[1] = tom.getZ();
			}
		});
		helper.runAfterDelay(100, () -> {
			double elder = bram.getZ() - from[0];
			double young = tom.getZ() - from[1];
			String went = "in 60 ticks the elder went " + elder + " blocks, the younger villager " + young + " (" + Math.round(100 * elder / young) + "%); the elder's walk target:" + asked;
			org.slf4j.LoggerFactory.getLogger("LifeStageGameTests").info("[elder walk] {}", went);
			helper.assertTrue(young > 3, "the younger villager hardly walked: " + went);
			// 15% slower: 85% of the ground, give or take the steps of a path.
			helper.assertTrue(elder < young * 0.90, "the elder is less than 15% slower: " + went);
			helper.assertTrue(elder > young * 0.80, "the elder is more than 15% slower: " + went);
			helper.succeed();
		});
	}

	/** "A quiet old age" (+5) for an elder who is fed and has a bed, first on the hall's card; not hungry, not without a bed; and elders talk like elders. */
	//$ gametest_ticks_batch AREA '100' '"lifeStageMood"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lifeStageMood")
	public void aFedAndHousedElderHasAQuietOldAge(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos bramBed = new BlockPos(4, 2, 4);
		BlockPos tomBed = new BlockPos(16, 2, 4);
		helper.setBlock(bramBed, Blocks.RED_BED);
		helper.setBlock(tomBed, Blocks.RED_BED);
		Villager bram = villager(helper, new BlockPos(4, 2, 6), "Bram", VillagerProfession.NONE, 1);
		Villager tom = villager(helper, new BlockPos(16, 2, 6), "Tom", VillagerProfession.NONE, 1);
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			Moods.ENABLED = true; // (off in GameTests otherwise)
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 131);
			LifeStages.setAdultSince(tom, today - 30);
			for (Villager v : List.of(bram, tom)) {
				ModAttachments.LAST_MEAL.set(v, level.getGameTime());
			}
			bram.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bramBed)));
			tom.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(tomBed)));

			// The same day in every way but their age: the elder's mood is 5 higher, for "a quiet old age".
			Moods.Mood young = Moods.work(level, tom);
			Moods.Mood old = Moods.work(level, bram);
			helper.assertTrue(young.score() <= 90, "the staged mood leaves no room: " + young.score() + " " + texts(young.good()));
			helper.assertTrue(texts(old.good()).contains("a quiet old age") && !texts(young.good()).contains("a quiet old age"),
				"the reasons: elder " + texts(old.good()) + ", younger " + texts(young.good()));
			helper.assertTrue(old.score() == young.score() + 5, "moods: elder " + old.score() + ", younger " + young.score());
			helper.assertTrue(texts(old.good()).containsAll(List.of("fed", "a bed of their own")), "the elder is fed and housed: " + texts(old.good()));
			helper.assertTrue(texts(old.good()).get(0).equals("a quiet old age"), "first of the good reasons: " + texts(old.good()));
			// On the hall's card.
			Moods.forget();
			List<String> card = lore(level, hallPos, bram);
			helper.assertTrue(card.contains("Elder · grown 131 days") && card.stream().anyMatch(l -> l.contains("a quiet old age")), "Bram's card: " + card);
			helper.assertTrue(lore(level, hallPos, tom).stream().noneMatch(l -> l.contains("a quiet old age")), "Tom's card: " + lore(level, hallPos, tom));

			// Hungry: no quiet old age (and 20 less for the hunger, 15 less for no longer being fed).
			ModAttachments.LAST_MEAL.set(bram, level.getGameTime() - 5 * VillageNeeds.DAY);
			Moods.Mood hungry = Moods.work(level, bram);
			helper.assertTrue(!texts(hungry.good()).contains("a quiet old age") && texts(hungry.bad()).contains("hungry"), "a hungry elder: " + texts(hungry.good()));
			helper.assertTrue(hungry.score() == old.score() - 5 - 15 - 20, "a hungry elder's mood: " + hungry.score() + " from " + old.score());
			// Fed, but with no bed of their own: none either.
			ModAttachments.LAST_MEAL.set(bram, level.getGameTime());
			bram.getBrain().eraseMemory(MemoryModuleType.HOME);
			Moods.Mood homeless = Moods.work(level, bram);
			helper.assertTrue(!texts(homeless.good()).contains("a quiet old age") && texts(homeless.good()).contains("fed"), "an elder with no bed: " + texts(homeless.good()));
			// Housed again: back. An ageless elder has it too; one whose age nobody knows does not.
			bram.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bramBed)));
			helper.assertTrue(Moods.work(level, bram).score() == old.score(), "fed and housed again: " + Moods.work(level, bram).score());
			ModAttachments.AGELESS.set(bram, true);
			helper.assertTrue(texts(Moods.work(level, bram).good()).contains("a quiet old age"), "an ageless elder's quiet old age");
			ModAttachments.AGELESS.remove(bram);
			ModAttachments.ADULT_SINCE.remove(tom);
			helper.assertTrue(Moods.work(level, tom).score() == young.score(), "a villager whose age nobody knows: " + Moods.work(level, tom).score());

			// Elders have four lines of their own, and talk of them; the others don't.
			List<String> topics = Chatter.topics(level, bram, hallPos);
			helper.assertTrue(topics.contains("elder"), "an elder's topics: " + topics);
			helper.assertTrue(!Chatter.topics(level, tom, hallPos).contains("elder"), "a younger villager's topics: " + Chatter.topics(level, tom, hallPos));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			for (int i = 0; i < ELDER_LINES.size(); i++) {
				String said = Chatter.say(level, bram, player, "elder", i, "").getString();
				helper.assertTrue(said.equals(ELDER_LINES.get(i)), "elder line " + i + ": " + said);
			}
			// Whatever Chatter picks for an elder is a real sentence (never a raw key), and the elder lines do come up.
			java.util.Set<String> heard = new java.util.TreeSet<>();
			for (int i = 0; i < 400; i++) {
				Component line = Chatter.line(level, bram, hallPos, player);
				helper.assertTrue(line != null && !line.getString().startsWith("chatter."), "a line without its sentence: " + line);
				heard.add(line.getString());
			}
			helper.assertTrue(heard.containsAll(ELDER_LINES), "the elder lines heard in 400 tries: " + heard);
			helper.succeed();
		});
	}

	/** The day they grew up and the chronicle's note are saved with the villager: after a reload still an elder, the same age, and not noted twice. */
	//$ gametest_ticks_batch AREA '100' '"lifeStageReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lifeStageReload")
	public void aReloadKeepsTheDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 2);
		Villager odo = villager(helper, new BlockPos(9, 2, 6), "Odo", VillagerProfession.LIBRARIAN, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 127);
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(chronicle(hall).stream().filter(NOW_AN_ELDER::equals).count() == 1, "noted before the reload: " + chronicle(hall));

			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(bram.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Long.valueOf(today - 127).equals(LifeStages.adultSince(copy)), "the day he grew up after a reload: " + LifeStages.adultSince(copy));
			helper.assertTrue(LifeStages.isElder(copy, today) && LifeStages.grownDays(copy, today) == 127 && LifeStages.stage(copy, today) == LifeStages.Stage.ELDER,
				"an elder of 127 grown days after a reload: " + LifeStages.grownDays(copy, today));
			helper.assertTrue(LifeStages.walk(copy) == LifeStages.ELDER_WALK, "the reloaded elder's walk: " + LifeStages.walk(copy));
			helper.assertTrue(Boolean.TRUE.equals(ModAttachments.ELDER_NOTED.get(copy)), "the chronicle's note after a reload: " + ModAttachments.ELDER_NOTED.get(copy));
			LifeStages.round(level, hallPos, List.of(copy));
			helper.assertTrue(chronicle(hall).stream().filter(NOW_AN_ELDER::equals).count() == 1, "noted again after the reload: " + chronicle(hall));
			// A day more, and the count goes on from the saved day.
			helper.assertTrue(LifeStages.grownDays(copy, today + 1) == 128, "the count after a reload: " + LifeStages.grownDays(copy, today + 1));

			// A villager from a save before all this: no age, not an elder, not slowed, and the round writes nothing on them.
			Villager old = EntityType.VILLAGER.create(level);
			old.load(EntityType.VILLAGER.create(level).saveWithoutId(new CompoundTag()));
			int lines = hall.chronicle().size();
			LifeStages.round(level, hallPos, List.of(old));
			helper.assertTrue(LifeStages.adultSince(old) == null && !LifeStages.isElder(old, today + 1000) && LifeStages.stage(old, today) == LifeStages.Stage.GROWN
				&& LifeStages.walk(old) == 1f && !ModAttachments.ELDER_NOTED.has(old) && hall.chronicle().size() == lines, "an older save's villager");
			// An elder made ageless before this version (no note saved): the charm wrote them in already, so no second line.
			LifeStages.setAdultSince(odo, today - 150);
			helper.assertTrue(LifeStages.makeAgeless(level, odo, Component.literal("Tester")).outcome() == LifeStages.Outcome.ACCEPTED, "Odo takes a charm");
			LifeStages.round(level, hallPos, List.of(odo));
			helper.assertTrue(!chronicle(hall).contains("Odo is an elder now") && chronicle(hall).stream().anyMatch(l -> l.startsWith("Odo will never leave us")),
				"an ageless elder's lines: " + chronicle(hall));
			helper.succeed();
		});
	}

	/** A child who grows up is grown from today (the hall's own round writes it), and the same round notes an elder; 120 days on the child is one. */
	//$ gametest_ticks_batch AREA '200' '"lifeStageGrowingUp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "lifeStageGrowingUp")
	public void aChildGrowingUpIsGrownFromToday(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		Villager pip = villager(helper, new BlockPos(5, 2, 6), "Pip", VillagerProfession.NONE, 1);
		pip.setBaby(true);
		ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "", "", false));
		Villager bram = villager(helper, new BlockPos(9, 2, 6), "Bram", VillagerProfession.FARMER, 3);
		LifeStages.setAdultSince(bram, today - 120);
		helper.assertTrue(LifeStages.stage(pip, today) == LifeStages.Stage.CHILD && LifeStages.adultSince(pip) == null && LifeStages.grownDays(pip, today) == -1
			&& LifeStages.walk(pip) == 1f, "Pip as a child");
		LifeStages.setAdultSince(pip, today - 500); // (a child is never an elder, whatever their record says)
		helper.assertTrue(!LifeStages.isElder(pip, today) && LifeStages.stage(pip, today) == LifeStages.Stage.CHILD, "a child is an elder");
		ModAttachments.ADULT_SINCE.remove(pip);
		pip.setAge(0); // grown up
		helper.assertTrue(!pip.isBaby(), "Pip didn't grow up");
		village(helper); // the hall's first tick is a round
		helper.succeedWhen(() -> {
			helper.assertTrue(LifeStages.adultSince(pip) != null, "the hall's round hasn't seen Pip grown up");
			helper.assertTrue(LifeStages.adultSince(pip) == today, "grown up on day " + LifeStages.adultSince(pip) + ", today is " + today);
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			helper.assertTrue(chronicle(hall).stream().anyMatch(l -> l.startsWith("Pip") && l.endsWith("has grown up")), "chronicle: " + chronicle(hall));
			helper.assertTrue(LifeStages.stage(pip, today) == LifeStages.Stage.GROWN && LifeStages.grownDays(pip, today + 3) == 3, "Pip's days");
			helper.assertTrue(!LifeStages.isElder(pip, today + 119) && LifeStages.isElder(pip, today + 120), "Pip, 120 days on");
			helper.assertTrue(!chronicle(hall).contains("Pip is an elder now"), "Pip noted as an elder: " + chronicle(hall));
			// The same round, through the hall's own tick: Bram became an elder today.
			helper.assertTrue(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LIFE && e.day() == today && e.text().getString().equals(NOW_AN_ELDER)),
				"chronicle: " + chronicle(hall));
		});
	}

	/** {@code villagerAges} off: nobody is an elder, so no elder line, slower walk, mood reason, chatter or chronicle note; on again, all of them. */
	//$ gametest_ticks_batch AREA '100' '"lifeStageConfig"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "lifeStageConfig")
	public void withVillagerAgesOffNobodyIsAnElder(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		BlockPos bed = new BlockPos(4, 2, 4);
		helper.setBlock(bed, Blocks.RED_BED);
		Villager bram = villager(helper, new BlockPos(4, 2, 6), "Bram", VillagerProfession.NONE, 1);
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			Moods.ENABLED = true;
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 200);
			ModAttachments.LAST_MEAL.set(bram, level.getGameTime());
			bram.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bed)));
			BlockPos goal = helper.absolutePos(new BlockPos(4, 2, 16));

			LifeStages.AGES = false;
			helper.assertTrue(!LifeStages.isElder(bram) && LifeStages.stage(bram, today) == LifeStages.Stage.GROWN, "an elder with villagerAges off");
			helper.assertTrue(lore(level, hallPos, bram).stream().noneMatch(l -> l.startsWith("Elder")), "the hall with villagerAges off: " + lore(level, hallPos, bram));
			Walker.requestWalk(bram, goal, 0.5f, 1, 0);
			helper.assertTrue(Math.abs(speed(bram) - 0.5f) < 0.001f, "the walk with villagerAges off: " + speed(bram));
			Moods.Mood plain = Moods.work(level, bram);
			helper.assertTrue(!texts(plain.good()).contains("a quiet old age"), "the mood with villagerAges off: " + texts(plain.good()));
			helper.assertTrue(!Chatter.topics(level, bram, hallPos).contains("elder"), "the talk with villagerAges off: " + Chatter.topics(level, bram, hallPos));
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(!chronicle(hall).contains(NOW_AN_ELDER) && !ModAttachments.ELDER_NOTED.has(bram), "the chronicle with villagerAges off: " + chronicle(hall));
			helper.assertTrue(Long.valueOf(today - 200).equals(LifeStages.adultSince(bram)), "the day he grew up was lost");

			// On again: he is the elder he was all along, and the chronicle notes it now.
			LifeStages.AGES = true;
			bram.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			Moods.forget();
			helper.assertTrue(LifeStages.isElder(bram) && lore(level, hallPos, bram).contains("Elder · grown 200 days"), "on again: " + lore(level, hallPos, bram));
			Walker.requestWalk(bram, goal, 0.5f, 1, 0);
			helper.assertTrue(Math.abs(speed(bram) - 0.5f * LifeStages.ELDER_WALK) < 0.001f, "the walk, on again: " + speed(bram));
			Moods.Mood old = Moods.work(level, bram);
			helper.assertTrue(texts(old.good()).contains("a quiet old age") && old.score() == plain.score() + 5, "the mood, on again: " + old.score() + " from " + plain.score());
			helper.assertTrue(Chatter.topics(level, bram, hallPos).contains("elder"), "the talk, on again: " + Chatter.topics(level, bram, hallPos));
			LifeStages.round(level, hallPos, List.of(bram));
			helper.assertTrue(chronicle(hall).stream().filter(NOW_AN_ELDER::equals).count() == 1, "the chronicle, on again: " + chronicle(hall));
			helper.succeed();
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	/** A Village Hall (the hall's radius 16 for the test); the life-stage switches are on again when the test ends. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LifeStages.AGES = true;
			LifeStages.ELDER_DAYS = 120;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	/** A villager who can walk, looking down the area. */
	private static Villager walker(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/**
	 * Keeps the measured walk Walker's own (B95). Walker also sets a look target on its goal, and an idle villager's vanilla
	 * "walk to what you look at" (SetWalkTargetFromLookTarget, speed 0.5, close enough 2) can pick that up in the tick the
	 * walk target was dropped (no path yet for a villager not on the ground): the elder then walks to the same goal at
	 * vanilla's 0.5, and Walker, finding its goal already set, never asks again. Without a look target it can't start.
	 */
	private static void walkersOnly(Villager... villagers) {
		for (Villager villager : villagers) {
			villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		}
	}

	private static float speed(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow().getSpeedModifier();
	}

	/** The hall after its first round, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static List<String> texts(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}
}
