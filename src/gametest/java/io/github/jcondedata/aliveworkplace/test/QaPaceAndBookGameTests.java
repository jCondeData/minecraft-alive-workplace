package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA (lane-c-1025-1853) for ROADMAP 30.1a (a reform costs hundreds of items), 30.2 (one pace, one cap), 30.4/30.4a (the
 * Book of Edicts page) and 30.21 (edicts and civic items in the village's life), written from the spec: what the
 * builder's tests ({@code PaceGameTests}, {@code EdictBookGameTests}, {@code CivicTalkGameTests},
 * {@code ReformStoryGameTests}) leave out. A penalty alone (no cap), the cap at its lowest and highest, every source's
 * words; the owner's friends and operators clicking (the spec's "only the owner, friends and operators"), a full Hamlet,
 * edicts switched off, the last day before an edict can be lifted ("tomorrow"), the edicts in force after save and
 * reload; the 300th bread of the Shift Bell from both sides; and the rush, colours and festival fund in the village's
 * talk and tips. Each test has its own batch and clears first. Nothing here is random.
 */
public class QaPaceAndBookGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	/** The edicts of Milestone 30 (each with a reform story); the realm's edicts (33.22) cost by their own rule. */
	private static final List<String> M30 = List.of("long_shifts", "free_bread", "large_families", "open_gates", "festival_season",
		"tithe", "curfew", "conscription");

	// --- 30.2: one pace, one cap ---------------------------------------------------------------------------------------

	/**
	 * A penalty with no bonus is never "at the cap": an ill worker outside any village takes exactly twice the time, says
	 * "50% slower (held back by being ill)" (half the speed), and rests, trips and progress follow (a negative time counts as none).
	 * Well again, nothing changes their pace and the status says nothing.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaIllAloneIsTwiceTheTimeAndNeverAtTheCap(GameTestHelper helper) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5, 200, 5)));
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER));
		helper.assertTrue(VillageHalls.nearest(helper.getLevel(), v.blockPosition()).isEmpty(), "a hall in reach");
		helper.assertTrue(Pace.factor(v) == 1f && Pace.describe(v) == null, "well: " + Pace.factor(v) + " " + Pace.describe(v));
		helper.assertTrue(Pace.ticks(-5, v) == 0, "a negative time: " + Pace.ticks(-5, v));
		ModAttachments.ILL_SINCE.set(v, helper.getLevel().getGameTime());
		Pace.Breakdown pace = Pace.of(v);
		helper.assertTrue(pace.factor() == 2f && !pace.capped() && pace.faster().isEmpty(), "ill: " + pace);
		helper.assertTrue(Pace.ticks(101, v) == 202 && Pace.progress(10, v) == 5, "ill rests/progress: " + Pace.ticks(101, v) + " " + Pace.progress(10, v));
		String line = Pace.describe(v).getString();
		helper.assertTrue(line.equals("50% slower (held back by being ill)"), "ill status: " + line);
		helper.assertTrue(!BuilderLevels.describe(v).getString().contains("at the cap"), "ill alone at the cap: " + BuilderLevels.describe(v).getString());
		helper.succeed();
	}

	/**
	 * {@code maxWorkPace} at its ends, from both sides: at 100 no bonus can speed anyone up (a well-kept village's builder
	 * works at the usual pace, "at the cap"), at 400 the same builder's 20% isn't capped; out of range counts as the end.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPaceEnds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPaceEnds")
	public void qaTheCapAt100LetsNoBonusThroughAnd400LetsItAll(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = keptVillage(helper);
		helper.runAfterDelay(5, () -> {
			keep(helper, 1f);
			int was = Pace.MAX_PERCENT;
			try {
				Pace.MAX_PERCENT = 400;
				helper.assertTrue(Math.abs(Pace.factor(builder) - 0.8f) < 1e-6f && !Pace.of(builder).capped(), "400: " + Pace.of(builder));
				helper.assertTrue(Pace.describe(builder).getString().equals("25% faster (a well-kept village)"), "400 status: " + Pace.describe(builder).getString());
				Pace.MAX_PERCENT = 100;
				helper.assertTrue(Pace.factor(builder) == 1f && Pace.of(builder).capped(), "100: " + Pace.of(builder));
				helper.assertTrue(Pace.describe(builder).getString().equals("at the usual pace: at the cap (a well-kept village)"),
					"100 status: " + Pace.describe(builder).getString());
				Pace.MAX_PERCENT = 99;
				helper.assertTrue(Pace.factor(builder) == 1f, "99 counts as 100: " + Pace.factor(builder));
			} finally {
				Pace.MAX_PERCENT = was;
			}
			helper.succeed();
		});
	}

	/** Every named source of pace reads as words in the status line: its lang key exists (the spec's list, and the later ones). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryPaceSourceHasItsWords(GameTestHelper helper) {
		// The sources whose label is their own lang key (the others name a partner, edict, tonic, guild or class).
		List<String> custom = List.of("partners", "edicts", "tonic", "guild", "class");
		for (Pace.Source s : Pace.sources()) {
			if (custom.contains(s.id())) {
				continue;
			}
			String key = "pace.aliveworkplace.source." + s.id();
			String text = Component.translatable(key).getString();
			helper.assertTrue(!text.equals(key) && !text.isBlank(), "no words for " + key);
		}
		for (String key : List.of("message.aliveworkplace.pace.usual", "message.aliveworkplace.pace.capped", "message.aliveworkplace.pace.held_back",
				"message.aliveworkplace.pace.and_held_back", "pace.aliveworkplace.source.edict", "pace.aliveworkplace.source.tonic.none")) {
			helper.assertTrue(!Component.translatable(key, "x", "y").getString().equals(key), "no words for " + key);
		}
		helper.succeed();
	}

	/** B497: a disaster reads in lower case mid-sentence, "held back by a disaster in the village", like the other pace sources. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaADisasterReadsLowerCaseInTheStatusLine(GameTestHelper helper) {
		Villager villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		String line = Component.translatable("message.aliveworkplace.pace.held_back", Pace.DISASTER.label().of(villager)).getString();
		helper.assertTrue(line.equals("held back by a disaster in the village"), "the line reads: " + line);
		villager.discard();
		helper.succeed();
	}

	// --- 30.4: the Book of Edicts ------------------------------------------------------------------------------------

	/** "Only the hall's owner, friends and operators can click": a friend of the owner proclaims Long Shifts by two clicks, and lifts it in time. */
	//$ gametest_ticks_batch AREA '100' '"qaBookFriend"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBookFriend")
	public void qaTheOwnersFriendProclaimsAndLiftsByClicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer friend = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		UUID owner = UUID.randomUUID();
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner, friend.getUUID(), friend.getGameProfile().getName());
		Leftovers.after(helper, () -> friends.remove(owner, friend.getUUID()));
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			entity.setOwner(owner, "Jesse");
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			ChoiceMenu menu = EdictBook.forTest(friend, hall);
			int at = find(menu, "Long Shifts");
			helper.assertTrue(at >= 0, "Long Shifts isn't listed");
			click(menu, at, friend);
			click(menu, at, friend);
			helper.assertTrue(entity.edicts().size() == 1 && entity.edicts().get(0).id().equals(LONG_SHIFTS), "the friend's clicks: " + entity.edicts());
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(helper.getLevel()) - Edicts.MIN_DAYS)));
			menu = EdictBook.forTest(friend, hall);
			click(menu, EdictBook.SLOTS[0], friend);
			helper.assertTrue(entity.edicts().isEmpty(), "the friend couldn't lift it: " + entity.edicts());
			helper.succeed();
		});
	}

	/** An operator who is neither the owner nor their friend proclaims by clicks. */
	//$ gametest_ticks_batch AREA '100' '"qaBookOperator"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBookOperator")
	public void qaAnOperatorProclaimsByClicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer op = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		var players = helper.getLevel().getServer().getPlayerList();
		helper.runAfterDelay(5, () -> {
			players.getOps().add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
			Leftovers.after(helper, () -> players.deop(op.getGameProfile()));
			entity.setOwner(UUID.randomUUID(), "Jesse");
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			helper.assertTrue(op.hasPermissions(2), "not an operator");
			ChoiceMenu menu = EdictBook.forTest(op, helper.absolutePos(HALL));
			int at = find(menu, "Long Shifts");
			click(menu, at, op);
			click(menu, at, op);
			helper.assertTrue(entity.edicts().size() == 1, "the operator's clicks: " + entity.edicts());
			helper.succeed();
		});
	}

	/**
	 * The days before lifting, from both sides: proclaimed two days ago, the slot says "Can be lifted tomorrow" (one day,
	 * not "1 days") and a click keeps it; three days ago it says "Click to lift it" and a click lifts it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBookLiftDay"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBookLiftDay")
	public void qaTheLastDayBeforeLiftingSaysTomorrow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer owner = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			long today = Chronicle.day(helper.getLevel());
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, today - 2)));
			ChoiceMenu menu = EdictBook.forTest(owner, hall);
			List<String> lore = lore(menu.icon(EdictBook.SLOTS[0]));
			helper.assertTrue(lore.contains("Can be lifted tomorrow, from day " + (today + 1)), "a day left: " + lore);
			helper.assertTrue(lore.contains("In force since day " + (today - 2) + " (2 days)"), "since: " + lore);
			click(menu, EdictBook.SLOTS[0], owner);
			helper.assertTrue(entity.edicts().size() == 1, "lifted a day early");
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, today - 3)));
			menu = EdictBook.forTest(owner, hall);
			helper.assertTrue(lore(menu.icon(EdictBook.SLOTS[0])).contains("Click to lift it"), "on the day: " + lore(menu.icon(EdictBook.SLOTS[0])));
			click(menu, EdictBook.SLOTS[0], owner);
			helper.assertTrue(entity.edicts().isEmpty(), "not lifted on its day");
			helper.succeed();
		});
	}

	/** A Hamlet's one slot full: two clicks on another edict proclaim nothing; with edicts switched off, nothing either. */
	//$ gametest_ticks_batch AREA '100' '"qaBookFullAndOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBookFullAndOff")
	public void qaAFullHamletAndEdictsOffProclaimNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer owner = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		boolean on = Edicts.ENABLED;
		Leftovers.after(helper, () -> Edicts.setEnabled(on));
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(helper.getLevel()))));
			ChoiceMenu menu = EdictBook.forTest(owner, hall);
			int drill = find(menu, "Builders' Drill");
			helper.assertTrue(drill >= 0, "the test pack's edict isn't listed");
			click(menu, drill, owner);
			click(menu, drill, owner);
			helper.assertTrue(entity.edicts().size() == 1, "a Hamlet put a second edict in force: " + entity.edicts());

			Edicts.setEnabled(false);
			entity.setEdicts(List.of());
			menu = EdictBook.forTest(owner, hall);
			int at = find(menu, "Long Shifts");
			if (at >= 0) {
				click(menu, at, owner);
				click(menu, at, owner);
			}
			helper.assertTrue(entity.edicts().isEmpty(), "edicts off, yet proclaimed: " + entity.edicts());
			helper.succeed();
		});
	}

	/** The edicts in force, and a reform's progress, come back after the hall is saved and loaded. */
	//$ gametest_ticks_batch AREA '100' '"qaBookSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBookSave")
	public void qaEdictsInForceSurviveSaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			long day = Chronicle.day(level) - 4;
			entity.setRank(VillageRanks.Rank.TOWN);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, day), new Edicts.InForce(AliveWorkplace.id("curfew").toString(), day + 1)));
			entity.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS, 2, false, 0)));
			CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
			VillageHallBlockEntity copy = new VillageHallBlockEntity(entity.getBlockPos(), entity.getBlockState());
			copy.loadWithComponents(tag, level.registryAccess());
			helper.assertTrue(copy.edicts().equals(entity.edicts()), "edicts after reload: " + copy.edicts());
			helper.assertTrue(Reforms.progress(copy, LONG_SHIFTS).step() == 2 && !Reforms.reformed(copy, LONG_SHIFTS), "reform after reload");
			helper.succeed();
		});
	}

	// --- 30.1a: the reform's cost ------------------------------------------------------------------------------------

	/**
	 * The owner's numbers: the Shift Bell asks 24 clocks, two stacks of gold and 300 bread; no Milestone 30 reform asks
	 * a handful any more (every item step at least 24, every reform at least 64 items brought).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryReformAsksForHundredsNotAHandful(GameTestHelper helper) {
		Reforms.Reform bell = Reforms.of(LONG_SHIFTS).orElseThrow();
		List<String> steps = bell.steps().stream().map(s -> s.count() + " " + s.item()).toList();
		helper.assertTrue(steps.equals(List.of("24 minecraft:clock", "128 minecraft:gold_ingot", "300 minecraft:bread")), "the Shift Bell: " + steps);
		for (String path : M30) {
			String id = AliveWorkplace.id(path).toString();
			Reforms.Reform r = Reforms.of(id).orElse(null);
			helper.assertTrue(r != null, "no reform for " + id);
			int brought = 0;
			for (Reforms.Step s : r.steps()) {
				if (s.kind() == VillageQuests.Kind.BRING) {
					helper.assertTrue(s.count() >= 24, id + " asks only " + s.count() + " " + s.item());
					brought += s.count();
				}
			}
			helper.assertTrue(brought >= 64, id + " brings only " + brought + " items");
		}
		helper.succeed();
	}

	/**
	 * The 300 bread, from both sides (story arcs off, the reform's own quest): 299 handed in leave one to go and the edict
	 * unreformed; the 300th reforms it. Hundreds go in across several stacks.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformBread"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformBread")
	public void qaThe300thBreadReformsLongShifts(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		boolean arcs = Arcs.ENABLED;
		boolean edicts = Edicts.ENABLED;
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			Arcs.ENABLED = arcs;
			Edicts.setEnabled(edicts);
			VillageHalls.RADIUS = radius;
		});
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Arcs.ENABLED = false;
			Edicts.setEnabled(true);
			VillageHalls.RADIUS = 16;
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			entity.setQuests(List.of());
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(level))));
			entity.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS, 2, false, 0)));
			Reforms.round(level, hall, entity);
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).left() == 300, "the bread step: " + shown);
			UUID quest = shown.get(0).id();
			player.getInventory().clearContent();
			for (int n : new int[] {64, 64, 64, 64, 43}) {
				player.getInventory().add(new ItemStack(Items.BREAD, n));
			}
			helper.assertTrue(VillageQuests.handIn(player, hall, quest) == 299, "299 not handed in");
			helper.assertTrue(!Reforms.reformed(entity, LONG_SHIFTS), "reformed one bread short");
			helper.assertTrue(Reforms.shown(entity).size() == 1 && Reforms.shown(entity).get(0).left() == 1, "one to go: " + Reforms.shown(entity));
			player.getInventory().add(new ItemStack(Items.BREAD, 5));
			helper.assertTrue(VillageQuests.handIn(player, hall, quest) == 1, "the 300th didn't go in (or more than it)");
			helper.assertTrue(Reforms.reformed(entity, LONG_SHIFTS), "300 bread, yet not reformed");
			helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 4, "the rest wasn't kept: " + player.getInventory().countItem(Items.BREAD));
			helper.succeed();
		});
	}

	// --- 30.21: the village's life -----------------------------------------------------------------------------------

	/**
	 * The talk of a rush (grown villagers only, while it lasts, and its words), and the festival fund's tip one hundredth
	 * of an emerald short ("2 of 3 emeralds") and gone at the full three.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVillageLife"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVillageLife")
	public void qaARushIsTalkedOfAndTheFestivalFundAdvisedToTheEmerald(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageHallBlockEntity entity = hall(helper);
		boolean edicts = Edicts.ENABLED;
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			Edicts.setEnabled(edicts);
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
		});
		Villager grown = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 5));
		child.setAge(-24000);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Edicts.setEnabled(true);
			VillageHalls.RADIUS = 16;
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			entity.setGuilds(List.of());
			entity.setColours(null);
			helper.assertTrue(!Chatter.civicTopics(level, grown, hall).contains("rush"), "a rush before the horn");
			entity.startRush(Chronicle.day(level), level.getGameTime() + 100);
			helper.assertTrue(Chatter.civicTopics(level, grown, hall).contains("rush"), "no talk of the rush: " + Chatter.civicTopics(level, grown, hall));
			helper.assertTrue(!Chatter.civicTopics(level, child, hall).contains("rush"), "a child talks of the rush");
			for (String key : List.of("chatter.aliveworkplace.rush.0", "chatter.aliveworkplace.rush.1", "chatter.aliveworkplace.colours.0",
					"chatter.aliveworkplace.tonic.0", "chatter.aliveworkplace.guild.1")) {
				helper.assertTrue(!Component.translatable(key).getString().equals(key), "no line " + key);
			}
			entity.startRush(Chronicle.day(level), level.getGameTime());
			helper.assertTrue(!Chatter.civicTopics(level, grown, hall).contains("rush"), "the rush ended, still talked of");

			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("festival_season").toString(), 0)));
			entity.setTreasury(299);
			String fund = tip(level, hall, "festival_fund");
			helper.assertTrue(fund != null && fund.equals("The treasury can't pay for the next festival (2 of 3 emeralds)"), "299 hundredths: " + fund);
			entity.setTreasury(300);
			helper.assertTrue(tip(level, hall, "festival_fund") == null, "3 emeralds, still advised");
			helper.succeed();
		});
	}

	// --- Helpers -----------------------------------------------------------------------------------------------------

	private static String tip(ServerLevel level, BlockPos hall, String key) {
		CivicEffects.forget();
		for (io.github.jcondedata.aliveworkplace.hall.VillageAdvice.Tip t : io.github.jcondedata.aliveworkplace.hall.VillageAdvice.tips(level, hall)) {
			if (t.key().equals(key)) {
				return t.title().getString();
			}
		}
		return null;
	}

	/** A Village Hall with a builder at a bench near it (as {@code PaceGameTests} sets one up). */
	private static Villager keptVillage(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Research.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bench = new BlockPos(5, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench),
			ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.runAfterDelay(1, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
				hall.setResearch(new Research.State(Map.of(), Optional.empty(), 0, false));
			}
		});
		return builder;
	}

	private static void keep(GameTestHelper helper, float wellbeing) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, wellbeing));
		VillageNeeds.forget();
		Research.forget();
		Moods.forget();
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		return player;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			CivicEffects.forget();
			Moods.forget();
		});
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
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
}
