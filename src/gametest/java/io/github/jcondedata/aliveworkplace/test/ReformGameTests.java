package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Reforms and The Shift Bell (ROADMAP 30.5): the reform step is on the quest page while Long Shifts is in force (in the
 * row below the daily quests, a book and quill), one step a morning, handing in pays (a quarter more a rank) and moves
 * on, progress survives lifting and a reload, the last step reforms it (no mood loss, the pace kept, fireworks, the
 * chronicle, the Book) for good, and a battle step falls back to its {@code fallback} without Cobblemon or a trainer.
 * The test pack's Duelling Days (a battle step) is in src/gametest/resources/data/aliveworkplace_test/edicts.
 */
public class ReformGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final String DUEL = "aliveworkplace_test:test_duel";
	/** The only reform step's slot on the quest page: the middle of the row below the quests. */
	private static final int STEP_SLOT = VillageHallScreen.reformSlots(1)[0];

	/** Long Shifts carries The Shift Bell exactly as the spec writes it; a broken reform is refused by field; arcs load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShiftBellLoadsFromData(GameTestHelper helper) {
		Reforms.Reform bell = Edicts.find("long_shifts").orElseThrow().reform().orElse(null);
		helper.assertTrue(bell != null, "Long Shifts has no reform");
		helper.assertTrue(bell.name().getString().equals("The Shift Bell"), "name: " + bell.name().getString());
		helper.assertTrue(bell.line().getString().equals("A bell in the yard calls the shifts, so nobody works past their hour."),
			"line: " + bell.line().getString());
		helper.assertTrue(bell.effects().isEmpty(), "reformed, the cost just goes: " + bell.effects());
		List<String> steps = bell.steps().stream().map(s -> s.kind() + " " + s.count() + " " + s.item() + " for " + s.reward()).toList();
		helper.assertTrue(steps.equals(List.of("BRING 24 minecraft:clock for 5", "BRING 128 minecraft:gold_ingot for 5", "BRING 300 minecraft:bread for 4")),
			"steps: " + steps);

		Reforms.Reform ring = Edicts.find(DUEL).orElseThrow().reform().orElseThrow();
		helper.assertTrue(ring.arc().equals(Optional.of("aliveworkplace_test:ring_story")), "the reform's arc: " + ring.arc());
		Reforms.Step battle = ring.steps().get(0);
		helper.assertTrue(battle.kind() == VillageQuests.Kind.BATTLE && battle.arc().isPresent()
			&& battle.fallback().map(f -> f.kind() == VillageQuests.Kind.SLAY && f.count() == 3 && f.reward() == 6).orElse(false), "the battle step: " + battle);
		helper.assertTrue(battle.resolve(true) == battle && battle.resolve(false) == battle.fallback().get(), "resolve");

		String noItem = refusal(helper, "{\"name\": \"X\", \"reform\": {\"name\": \"R\", \"steps\": [{\"kind\": \"bring\", \"count\": 2}]}}");
		helper.assertTrue(noItem.contains("item"), "a bring step without an item: " + noItem);
		String badItem = refusal(helper, "{\"name\": \"X\", \"reform\": {\"name\": \"R\", \"steps\": [{\"kind\": \"bring\", \"item\": \"minecraft:nope\"}]}}");
		helper.assertTrue(badItem.contains("nope"), "an unknown item: " + badItem);
		String kind = refusal(helper, "{\"name\": \"X\", \"reform\": {\"name\": \"R\", \"steps\": [{\"kind\": \"dance\"}]}}");
		helper.assertTrue(kind.contains("dance"), "an unknown kind: " + kind);
		String battleFallback = refusal(helper,
			"{\"name\": \"X\", \"reform\": {\"name\": \"R\", \"steps\": [{\"kind\": \"battle\", \"fallback\": {\"kind\": \"battle\"}}]}}");
		helper.assertTrue(battleFallback.contains("battle"), "a battle falling back to a battle: " + battleFallback);
		String empty = refusal(helper, "{\"name\": \"X\", \"reform\": {\"name\": \"R\", \"steps\": []}}");
		helper.assertTrue(empty.contains("steps"), "no steps: " + empty);
		helper.succeed();
	}

	private static String refusal(GameTestHelper helper, String json) {
		try {
			Edicts.read(AliveWorkplace.id("broken"), JsonParser.parseString(json));
		} catch (RuntimeException e) {
			return String.valueOf(e.getMessage());
		}
		helper.fail("accepted " + json);
		return "";
	}

	/**
	 * Proclaimed in the Book, Long Shifts puts The Shift Bell's first step on the quest page, in the row below the daily
	 * quests with a book and quill, and it never expires; lifted (or with edicts off) it's gone from the page.
	 */
	//$ gametest_ticks_batch AREA '100' '"reformShown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformShown")
	public void theStepIsUpWithLongShiftsInForceAndNotWithout(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			Reforms.round(level, hall, entity);
			ChoiceMenu menu = questPage(player, hall);
			helper.assertTrue(menu.icon(STEP_SLOT).isEmpty() && Reforms.shown(entity).isEmpty(), "a step without an edict: " + menu.icon(STEP_SLOT));

			// Proclaimed by two clicks in the Book.
			ChoiceMenu book = EdictBook.forTest(player, hall);
			int at = find(book, "Long Shifts");
			click(book, at, player);
			click(book, at, player);
			helper.assertTrue(entity.edicts().size() == 1, "not proclaimed: " + entity.edicts());
			helper.assertTrue(lore(book.icon(find(book, "Long Shifts"))).contains("Reform: The Shift Bell, step 1 of 3"), "the Book: " + lore(book.icon(at)));

			menu = questPage(player, hall);
			ItemStack step = menu.icon(STEP_SLOT);
			helper.assertTrue(step.is(Items.WRITABLE_BOOK) && step.getHoverName().getString().equals("The Shift Bell, step 1 of 3"), "the step: " + step);
			List<String> lines = lore(step);
			helper.assertTrue(lines.contains("Bring 24 Clock") && lines.contains("A reform step: it never expires")
				&& lines.contains("A bell in the yard calls the shifts, so nobody works past their hour.") && lines.contains("Reform of the edict Long Shifts")
				&& lines.contains("Done so far: 0 of 24") && lines.contains("You have none on you"), "lore: " + lines);
			if (!Money.cobbleDollars()) {
				helper.assertTrue(lines.contains("Reward: 5 emeralds"), "a Hamlet pays 5: " + lines);
			}
			for (int slot : VillageHallScreen.QUEST_SLOTS) {
				helper.assertTrue(!menu.icon(slot).is(Items.WRITABLE_BOOK), "the step sits among the daily quests, slot " + slot);
			}
			helper.assertTrue(STEP_SLOT / 9 == VillageHallScreen.QUEST_SLOTS[0] / 9 + 1, "not the row below the quests: " + STEP_SLOT);
			// The main page counts it.
			ChoiceMenu main = VillageHallScreen.forTest(player, hall);
			helper.assertTrue(main.icon(VillageHallScreen.QUESTS).getHoverName().getString().equals("Quests: 1"), "count: " + main.icon(VillageHallScreen.QUESTS));

			// It never expires: still up after a daily quest's three days.
			VillageQuests.Quest up = Reforms.shown(entity).get(0);
			entity.setQuests(List.of(new VillageQuests.Quest(up.id(), up.kind(), up.item(), up.count(), up.progress(), up.reward(),
				level.getGameTime() - VillageQuests.LASTS * 3, up.poster(), up.deliverTo(), up.reform())));
			entity.setLastQuestDay(Long.MAX_VALUE / 2);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(Reforms.shown(entity).size() == 1, "the step expired: " + entity.quests());

			// Edicts off: no step; on again: back.
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(Reforms.shown(entity).isEmpty() && questPage(player, hall).icon(STEP_SLOT).isEmpty(), "off");
			} finally {
				Edicts.setEnabled(true);
			}
			helper.assertTrue(Reforms.shown(entity).size() == 1, "back on");

			// Lifted: gone from the page.
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level) - 5)));
			helper.assertTrue(Edicts.lift(level, hall, player, LONG_SHIFTS).done(), "lifted");
			helper.assertTrue(questPage(player, hall).icon(STEP_SLOT).isEmpty() && Reforms.shown(entity).isEmpty(), "still up after lifting");
			helper.succeed();
		});
	}

	/**
	 * Handing in on the quest page pays like a quest (a Village: a quarter more) and moves on, but the next step only
	 * goes up the next morning; a step handed in halfway keeps what was given.
	 */
	//$ gametest_ticks_batch AREA '100' '"reformHandIn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformHandIn")
	public void oneStepAMorningAndHandingInPays(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(Edicts.proclaim(level, hall, player, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed");
			VillageQuests.Quest first = Reforms.shown(entity).get(0);
			helper.assertTrue(first.reward() == 6 && first.reform().equals(Optional.of(new VillageQuests.ReformStep(LONG_SHIFTS, 0))),
				"5 emeralds and a quarter, step 0: " + first);

			// Two clocks first: half done, kept.
			player.getInventory().add(new ItemStack(Items.CLOCK, 2));
			ChoiceMenu menu = questPage(player, hall);
			click(menu, STEP_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.CLOCK) == 0, "the clocks weren't taken");
			helper.assertTrue(lore(menu.icon(STEP_SLOT)).contains("Done so far: 2 of 24"), "progress: " + lore(menu.icon(STEP_SLOT)));
			helper.assertTrue(Reforms.progress(entity, LONG_SHIFTS).step() == 0, "moved on at half");

			// The rest: paid, and nothing more until tomorrow.
			player.getInventory().add(new ItemStack(Items.CLOCK, 23));
			click(menu, STEP_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.CLOCK) == 1, "took more than asked: " + player.getInventory().countItem(Items.CLOCK));
			if (!Money.cobbleDollars()) {
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, "paid: " + player.getInventory().countItem(Items.EMERALD));
			}
			Reforms.Progress progress = Reforms.progress(entity, LONG_SHIFTS);
			helper.assertTrue(progress.step() == 1 && !progress.reformed() && progress.nextDay() == Chronicle.day(level) + 1, "progress: " + progress);
			helper.assertTrue(menu.icon(STEP_SLOT).isEmpty(), "a step still on the page: " + menu.icon(STEP_SLOT));
			Reforms.round(level, hall, entity);
			helper.assertTrue(Reforms.shown(entity).isEmpty(), "the next step went up the same day");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.QUEST
				&& e.text().getString().endsWith("The Shift Bell, step 1 of 3: Bring 24 Clock")), "the chronicle: " + chronicle(entity));

			// The next morning: step two.
			nextMorning(helper);
			Reforms.round(level, hall, entity);
			ItemStack two = questPage(player, hall).icon(STEP_SLOT);
			helper.assertTrue(two.getHoverName().getString().equals("The Shift Bell, step 2 of 3") && lore(two).contains("Bring 128 Gold Ingot"),
				"step two: " + two.getHoverName().getString() + " " + lore(two));
			helper.assertTrue(Reforms.shown(entity).get(0).reward() == 6, "step two pays 5 and a quarter: " + Reforms.shown(entity).get(0));
			helper.assertTrue(lore(EdictBook.forTest(player, hall).icon(find(EdictBook.forTest(player, hall), "Long Shifts")))
				.contains("Reform: The Shift Bell, step 2 of 3"), "the Book's step");
			helper.succeed();
		});
	}

	/**
	 * Progress is kept per edict: a step done and the next one half handed in survive lifting, a save and a reload, and
	 * proclaiming it again; a hall and its quests saved before reforms load with none.
	 */
	//$ gametest_ticks_batch AREA '100' '"reformKept"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformKept")
	public void progressSurvivesLiftingAndAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed");
			player.getInventory().add(new ItemStack(Items.CLOCK, 24));
			click(questPage(player, hall), STEP_SLOT, player);
			nextMorning(helper);
			Reforms.round(level, hall, entity);
			player.getInventory().add(new ItemStack(Items.GOLD_INGOT, 3));
			click(questPage(player, hall), STEP_SLOT, player);
			helper.assertTrue(Reforms.shown(entity).get(0).progress() == 3, "3 gold handed in: " + entity.quests());

			// Lifted: off the page, kept.
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level) - 5)));
			helper.assertTrue(Edicts.lift(level, hall, null, LONG_SHIFTS).done(), "lifted");
			helper.assertTrue(Reforms.shown(entity).isEmpty(), "shown while lifted");
			helper.assertTrue(Reforms.progress(entity, LONG_SHIFTS).step() == 1, "forgotten when lifted: " + entity.reforms());

			// Saved and loaded.
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.reforms().equals(entity.reforms()) && copy.quests().equals(entity.quests()),
				"reloaded: " + (copy == null ? null : copy.reforms() + " " + copy.quests()));

			// Proclaimed again: the same step, with what was handed in.
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed again");
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).progress() == 3 && shown.get(0).reform().orElseThrow().step() == 1,
				"after proclaiming again: " + shown);
			helper.assertTrue(lore(questPage(player, hall).icon(STEP_SLOT)).contains("Done so far: 3 of 128"), "the page");

			// A hall from before reforms: no progress; a quest saved without "reform" is a daily one.
			tag.remove("reforms");
			ListTag quests = tag.getList("quests", 10);
			for (int i = 0; i < quests.size(); i++) {
				quests.getCompound(i).remove("reform");
			}
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.reforms().isEmpty() && old.quests().size() == 1 && old.quests().get(0).daily(),
				"a hall from before: " + (old == null ? null : old.reforms() + " " + old.quests()));
			helper.succeed();
		});
	}

	/**
	 * All three steps reform Long Shifts: still 20% faster, no mood loss, fireworks over the hall, the chronicle (REFORM)
	 * and the Book; lifted and proclaimed again it stays reformed, with no step on the page.
	 */
	//$ gametest_ticks_batch AREA '100' '"reformDone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformDone")
	public void theLastStepReformsItForGood(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = village(helper);
		ServerPlayer player = player(helper);
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			int mood = Moods.work(level, builder).score();
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed");
			Moods.forget();
			helper.assertTrue(Moods.work(level, builder).score() == mood - 10, "Long Shifts' cost first");

			hand(helper, player, Items.CLOCK, 24);
			nextMorning(helper);
			Reforms.round(level, hall, entity);
			hand(helper, player, Items.GOLD_INGOT, 128);
			helper.assertTrue(!Reforms.reformed(entity, LONG_SHIFTS), "reformed before the last step");
			nextMorning(helper);
			Reforms.round(level, hall, entity);
			helper.assertTrue(level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(hall).inflate(8)).isEmpty(), "fireworks too soon");
			hand(helper, player, Items.BREAD, 300);

			helper.assertTrue(Reforms.reformed(entity, LONG_SHIFTS), "not reformed: " + entity.reforms());
			if (!Money.cobbleDollars()) {
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 14, "5 + 5 + 4: " + player.getInventory().countItem(Items.EMERALD));
			}
			helper.assertTrue(!level.getEntitiesOfClass(FireworkRocketEntity.class, new AABB(hall).inflate(8)).isEmpty(), "no fireworks over the hall");
			Chronicle.Entry last = entity.chronicle().get(entity.chronicle().size() - 1);
			helper.assertTrue(last.kind() == Chronicle.Kind.REFORM && last.text().getString().equals("The edict Long Shifts was reformed: The Shift Bell"),
				"the chronicle: " + chronicle(entity));
			Moods.forget();
			helper.assertTrue(Moods.work(level, builder).score() == mood, "the mood loss stayed: " + Moods.work(level, builder).score() + " not " + mood);
			helper.assertTrue(BuilderLevels.delay(1200, builder) == 1000, "the pace went: " + BuilderLevels.delay(1200, builder));
			ChoiceMenu book = EdictBook.forTest(player, hall);
			List<String> lines = lore(book.icon(find(book, "Long Shifts")));
			helper.assertTrue(lines.contains("Reformed: The Shift Bell") && lines.contains("+ everyone works 20% faster")
				&& lines.stream().noneMatch(l -> l.startsWith("- ")), "the Book: " + lines);
			Reforms.round(level, hall, entity);
			helper.assertTrue(Reforms.shown(entity).isEmpty() && questPage(player, hall).icon(STEP_SLOT).isEmpty(), "a step after reforming");

			// Lifted and proclaimed again: still reformed.
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level) - 5)));
			helper.assertTrue(Edicts.lift(level, hall, null, LONG_SHIFTS).done(), "lifted");
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed again");
			Moods.forget();
			CivicEffects.forget();
			helper.assertTrue(Reforms.reformed(entity, LONG_SHIFTS) && Reforms.shown(entity).isEmpty(), "not reformed any more");
			helper.assertTrue(Moods.work(level, builder).score() == mood && BuilderLevels.delay(1200, builder) == 1000,
				"proclaimed again: mood " + Moods.work(level, builder).score() + ", delay " + BuilderLevels.delay(1200, builder));
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && Reforms.reformed(copy, LONG_SHIFTS) && copy.civicEffects().all().size() == 1,
				"reformed after a reload: " + (copy == null ? null : copy.civicEffects().all()));
			helper.succeed();
		});
	}

	/** Without Cobblemon (or a trainer in the village) a battle step is its fallback: clear out 3 monsters, paid 6. */
	//$ gametest_ticks_batch AREA '100' '"reformFallback"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformFallback")
	public void aBattleStepFallsBackWithoutCobblemon(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find(DUEL).orElseThrow()).done(), "proclaimed");
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).kind() == VillageQuests.Kind.SLAY && shown.get(0).count() == 3 && shown.get(0).reward() == 6,
				"the fallback: " + shown);
			ItemStack step = questPage(player, hall).icon(STEP_SLOT);
			helper.assertTrue(step.getHoverName().getString().equals("The Duelling Ring, step 1 of 2") && lore(step).contains("Clear out 3 monsters"),
				"the page: " + step.getHoverName().getString() + " " + lore(step));

			// Monsters killed in the village count.
			for (int i = 0; i < 3; i++) {
				Zombie zombie = EntityType.ZOMBIE.create(level);
				zombie.moveTo(hall.getX() + 3, hall.getY(), hall.getZ() + 3);
				VillageQuests.onKill(level, zombie, level.damageSources().playerAttack(player));
			}
			helper.assertTrue(Reforms.progress(entity, DUEL).step() == 1 && Reforms.shown(entity).isEmpty(), "three monsters: " + entity.reforms() + " " + entity.quests());
			if (!Money.cobbleDollars()) {
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, "paid: " + player.getInventory().countItem(Items.EMERALD));
			}
			helper.succeed();
		});
	}

	/**
	 * A step of hundreds (30.1a: The Shift Bell's 300 bread) is handed in over many trips: a stack, then more than a
	 * stack spread over several slots, then the rest with some over. Each trip keeps what was given, the hint names what
	 * a click will take, nothing is lost (no store here, so it lands by the hall), and the last trip reforms the edict.
	 */
	//$ gametest_ticks_batch AREA '100' '"reformHundreds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reformHundreds")
	public void aStepOfHundredsTakesManyTrips(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.find("long_shifts").orElseThrow()).done(), "proclaimed");
			entity.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS, 2, false, 0)));
			entity.setQuests(List.of());
			Reforms.round(level, hall, entity);
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).item().equals("minecraft:bread") && shown.get(0).count() == 300,
				"the last step: " + shown);
			helper.assertTrue(lore(questPage(player, hall).icon(STEP_SLOT)).contains("Bring 300 Bread"), "the page: " + lore(questPage(player, hall).icon(STEP_SLOT)));

			// Trip one: a full stack.
			give(player, Items.BREAD, 64);
			click(questPage(player, hall), STEP_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 0, "a stack not taken: " + player.getInventory().countItem(Items.BREAD));
			helper.assertTrue(Reforms.shown(entity).get(0).progress() == 64 && Reforms.progress(entity, LONG_SHIFTS).step() == 2, "after a stack: " + entity.quests());

			// Trip two: 200 over four slots, all of it taken in one click; kept through a save and a reload.
			give(player, Items.BREAD, 200);
			helper.assertTrue(lore(questPage(player, hall).icon(STEP_SLOT)).contains("Click: hand in 200"), "the hint: " + lore(questPage(player, hall).icon(STEP_SLOT)));
			click(questPage(player, hall), STEP_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 0, "not all 200 taken: " + player.getInventory().countItem(Items.BREAD));
			helper.assertTrue(lore(questPage(player, hall).icon(STEP_SLOT)).contains("Done so far: 264 of 300"), "progress: " + lore(questPage(player, hall).icon(STEP_SLOT)));
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.quests().equals(entity.quests()), "reloaded: " + (copy == null ? null : copy.quests()));

			// Trip three: 50 carried, 36 wanted: 14 kept, the edict reformed, paid once.
			give(player, Items.BREAD, 50);
			helper.assertTrue(lore(questPage(player, hall).icon(STEP_SLOT)).contains("Click: hand in 36"), "the last hint: " + lore(questPage(player, hall).icon(STEP_SLOT)));
			click(questPage(player, hall), STEP_SLOT, player);
			helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 14, "took more than asked: " + player.getInventory().countItem(Items.BREAD));
			helper.assertTrue(Reforms.reformed(entity, LONG_SHIFTS) && Reforms.shown(entity).isEmpty(), "not reformed: " + entity.reforms());
			if (!Money.cobbleDollars()) {
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 4, "paid: " + player.getInventory().countItem(Items.EMERALD));
			}
			int landed = level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(4), e -> e.getItem().is(Items.BREAD)).stream()
				.mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(landed == 300, "bread by the hall: " + landed + " of 300");
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** Hands in {@code count} of {@code item} at the only reform step on the quest page. */
	private static void hand(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		give(player, item, count);
		click(questPage(player, helper.absolutePos(HALL)), STEP_SLOT, player);
		helper.assertTrue(player.getInventory().countItem(item) == 0, "not handed in: " + item);
	}

	/** Puts {@code count} of {@code item} in {@code player}'s inventory, a full stack a slot. */
	private static void give(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		int max = new ItemStack(item).getMaxStackSize();
		for (int left = count; left > 0; left -= max) {
			player.getInventory().add(new ItemStack(item, Math.min(max, left)));
		}
	}

	/** Moves the clock to the next morning (put back when the test ends). */
	private static void nextMorning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long before = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(before));
		level.setDayTime((before / VillageNeeds.DAY + 1) * VillageNeeds.DAY + 1000);
	}

	/** The hall's quest page as {@code player} sees it. */
	private static ChoiceMenu questPage(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.QUESTS, player);
		return menu;
	}

	/** A Village Hall and a builder at a bench near it (no AI). */
	private static Villager village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bench = new BlockPos(5, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench),
			ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		return builder;
	}

	/** The hall after its first round: a Hamlet, nothing in force, no quests (and none coming today), every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setQuests(List.of());
		// The daily quests live in the quest engine since 31.2: drop any the board posted while the village was set up.
		io.github.jcondedata.aliveworkplace.story.Stories.Data.get(helper.getLevel()).remove(helper.absolutePos(HALL));
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		return player;
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static int find(ChoiceMenu menu, String name) {
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		return -1;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}
}
