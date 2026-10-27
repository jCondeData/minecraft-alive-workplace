package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA for ROADMAP 31.22 (the eight reforms as stories), written from the spec: who asks when the fitting trade is missing,
 * reform arcs as side arcs (beside a main arc, in its cooldown, never restarting it), the Lamplighters' blueprints only
 * for whoever finished it, a story surviving a reload mid-arc with its asker gone, the Shift Bell only on natural ground,
 * the Fair Ledger's 7 days, and every sentence the stories show rendered in American English.
 */
public class QaReformStoryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	// ---- who asks ---------------------------------------------------------------------------------------------------

	/** The Shift Bell: with no Toolsmith any smith asks (not the farmer); with a Toolsmith beside the smiths, the Toolsmith does. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryAskSmith"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryAskSmith")
	public void theShiftBellFallsBackToAnySmithButPrefersTheToolsmith(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager farmer = villager(helper, ASKER, VillagerProfession.FARMER);
		Villager weaponsmith = villager(helper, ASKER.south(2), VillagerProfession.WEAPONSMITH);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), Chronicle.day(level))));
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1, "no Toolsmith: the story didn't start with a weaponsmith there");
			helper.assertTrue(asks(weaponsmith) && !asks(farmer), "no Toolsmith: the weaponsmith should ask, not the farmer");
			Villager armorer = villager(helper, ASKER.north(2), VillagerProfession.ARMORER);
			Villager toolsmith = villager(helper, ASKER.east(2), VillagerProfession.TOOLSMITH);
			for (int i = 0; i < 5; i++) {
				Arcs.stopAll(level, hall);
				for (Villager v : List.of(farmer, weaponsmith, armorer, toolsmith)) {
					untag(v);
				}
				autoRound(level, hall, entity);
				helper.assertTrue(asks(toolsmith) && !asks(weaponsmith) && !asks(armorer), "round " + i + ": the Toolsmith wasn't the one asking");
			}
			helper.succeed();
		});
	}

	/** The Fair Ledger: with no Librarian the Scholar asks. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryAskScholar"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryAskScholar")
	public void theFairLedgerIsAskedByAScholarWithNoLibrarian(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager farmer = villager(helper, ASKER, VillagerProfession.FARMER);
		Villager scholar = villager(helper, ASKER.south(2), ModVillagers.SCHOLAR);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("tithe").toString(), Chronicle.day(level))));
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1 && asks(scholar) && !asks(farmer), "the Scholar didn't ask");
			helper.succeed();
		});
	}

	/** The Lamplighters: with no Porter anyone asks, and the ask counts when you talk to them. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryAskAnyone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryAskAnyone")
	public void theLamplightersAreAskedByAnyoneWithNoPorter(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager farmer = villager(helper, ASKER, VillagerProfession.FARMER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("curfew").toString(), Chronicle.day(level))));
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).size() == 1 && asks(farmer), "nobody asked with no Porter");
			ServerPlayer player = player(helper, hall);
			helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, farmer) == InteractionResult.SUCCESS, "talking to the farmer didn't count");
			helper.succeed();
		});
	}

	/** The Watchful Gate with no Guard: nobody of the trade to ask, so no story starts, and the reform is not lost. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryNoGuard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryNoGuard")
	public void theWatchfulGateWithNoGuardStartsNoStory(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager farmer = villager(helper, ASKER, VillagerProfession.FARMER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("open_gates").toString(), Chronicle.day(level))));
			autoRound(level, hall, entity);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && !asks(farmer), "a story started with no Guard to ask");
			helper.assertTrue(Reforms.shown(entity).size() == 1, "the reform was lost: no story and no M30 step: " + Reforms.shown(entity));
			helper.succeed();
		});
	}

	/**
	 * storyArcs off, a Toolsmith in the village: the Shift Bell's reform runs as M30's own steps on the quest page, one a
	 * day, handed in from the page, and ends reformed; no story starts, nobody is asked, and no bell is raised.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformStoryOffWhole"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformStoryOffWhole")
	public void withStoryArcsOffM30sStepsRunToTheReform(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager toolsmith = villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		boolean on = Arcs.ENABLED;
		Arcs.ENABLED = false;
		Leftovers.after(helper, () -> Arcs.ENABLED = on);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			ServerPlayer player = player(helper, hall);
			long before = level.getDayTime();
			Leftovers.after(helper, () -> level.setDayTime(before));
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			String edict = AliveWorkplace.id("long_shifts").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			Reforms.Reform reform = Reforms.of(edict).orElseThrow();
			for (int n = 0; n < reform.steps().size(); n++) {
				level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
				autoRound(level, hall, entity);
				helper.assertTrue(Arcs.side(level, hall).isEmpty() && !asks(toolsmith), "a story started with storyArcs off (step " + (n + 1) + ")");
				helper.assertTrue(Reforms.shown(entity).size() == 1, "step " + (n + 1) + " isn't on the page: " + Reforms.shown(entity));
				Reforms.Step step = reform.steps().get(n).resolve(level, hall);
				net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item()));
				int max = new ItemStack(item).getMaxStackSize();
				for (int left = step.count(); left > 0; left -= max) {
					player.getInventory().add(new ItemStack(item, Math.min(max, left)));
				}
				io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.forTest(player, hall);
				menu.press(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.QUESTS, player);
				menu.clicked(io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.reformSlots(1)[0], 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
				helper.assertTrue(player.getInventory().countItem(item) == 0, "step " + (n + 1) + " not handed in from the page: " + step.count() + " " + item);
				helper.assertTrue(n == reform.steps().size() - 1 || Reforms.progress(entity, edict).step() == n + 1, "step " + (n + 1) + " didn't move on");
			}
			helper.assertTrue(Reforms.reformed(entity, edict), "not reformed by M30's steps");
			helper.assertTrue(ReformStories.bell(level, hall) == null, "a Shift Bell was raised with storyArcs off");
			helper.succeed();
		});
	}

	// ---- side arcs --------------------------------------------------------------------------------------------------

	/** A reform story starts beside a running main arc and inside the village's cooldown, and leaves the main arc alone. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryBeside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryBeside")
	public void aReformStoryRunsBesideAMainArcInItsCooldown(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		for (int i = 0; i < 4; i++) {
			villager(helper, new BlockPos(4 + i, 2, 12), VillagerProfession.NONE);
		}
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			ArcState main = null;
			for (Arcs.Arc arc : Arcs.all()) {
				if (!arc.side() && arc.event().isEmpty()) {
					main = Arcs.start(level, hall, arc);
					if (main != null) {
						break;
					}
				}
			}
			helper.assertTrue(main != null && Arcs.running(level, hall) == main, "no main arc could start for the test");
			Villager guard = helper.spawn(EntityType.VILLAGER, ASKER);
			guard.setNoAi(true);
			guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(2));
			guard.setVillagerXp(20);
			int atOnce = Arcs.AT_ONCE;
			int cooldown = Arcs.COOLDOWN_DAYS;
			Arcs.AT_ONCE = 1;
			Arcs.COOLDOWN_DAYS = 1000;
			try {
				entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("conscription").toString(), Chronicle.day(level))));
				autoRound(level, hall, entity);
			} finally {
				Arcs.AT_ONCE = atOnce;
				Arcs.COOLDOWN_DAYS = cooldown;
			}
			helper.assertTrue(Arcs.side(level, hall).stream().anyMatch(s -> s.id.equals("aliveworkplace:militia_drill")),
				"the Militia Drill didn't start beside the main arc: " + Arcs.side(level, hall).stream().map(s -> s.id).toList());
			helper.assertTrue(asks(guard), "the Guard wasn't asked");
			helper.assertTrue(Arcs.running(level, hall) == main, "the main arc was replaced");
			helper.succeed();
		});
	}

	// ---- whole stories ----------------------------------------------------------------------------------------------

	/**
	 * The Lamplighters, from the ask to the end, with a bystander at the hall: two Street Lamp blueprints go to whoever
	 * finished it and none to the bystander; the side arc ending doesn't restart the village's cooldown.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformStoryLampsFinisher"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformStoryLampsFinisher")
	public void theLamplightersBlueprintsGoOnlyToWhoeverFinishedIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager porter = villager(helper, ASKER, ModVillagers.PORTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			ServerPlayer finisher = player(helper, hall);
			ServerPlayer bystander = player(helper, hall.south());
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			String edict = AliveWorkplace.id("curfew").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			autoRound(level, hall, entity);
			Arcs.round(level, hall, entity); // (the village's first round: the engine sees it)
			long cooldownDay = Arcs.lastArcDay(level, hall);
			helper.assertTrue(asks(porter), "the Porter didn't ask");
			ArcEffects.onTalk(finisher, level, InteractionHand.MAIN_HAND, porter);
			runSteps(helper, level, hall, entity, edict, day, finisher, null);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && Reforms.reformed(entity, edict), "the story didn't end reformed");
			helper.assertTrue(blueprints(finisher) == 2, "the finisher's Street Lamp blueprints: " + blueprints(finisher));
			helper.assertTrue(blueprints(bystander) == 0, "the bystander got blueprints: " + blueprints(bystander));
			helper.assertTrue(Arcs.lastArcDay(level, hall) == cooldownDay, "the side arc's end restarted the cooldown: " + cooldownDay + " -> "
				+ Arcs.lastArcDay(level, hall));
			helper.succeed();
		});
	}

	/**
	 * The Common Granary saved and loaded mid-story (after its first step), with the Farmer who asked dead: the next
	 * steps still come up with M30's numbers, one a day, and the edict ends reformed (boost kept, cost gone).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReformStoryReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReformStoryReload")
	public void aStoryReloadedMidArcWithItsAskerDeadStillEnds(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager farmer = villager(helper, ASKER, VillagerProfession.FARMER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			ServerPlayer player = player(helper, hall);
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			String edict = AliveWorkplace.id("free_bread").toString();
			Edicts.Edict file = Edicts.find("free_bread").orElseThrow();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			autoRound(level, hall, entity);
			helper.assertTrue(asks(farmer), "the Farmer didn't ask");
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, farmer);
			runSteps(helper, level, hall, entity, edict, day, player, n -> {
				if (n == 1) {
					farmer.kill();
					CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
					Stories.Data copy = Stories.Data.load(saved, level.registryAccess());
					level.getDataStorage().set("aliveworkplace_stories", copy);
					helper.assertTrue(Arcs.side(level, hall).stream().anyMatch(s -> s.id.equals("aliveworkplace:common_granary")), "the story was lost in the reload");
				}
			});
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && Reforms.reformed(entity, edict), "the story didn't end reformed after the reload");
			CivicEffects.forget();
			List<CivicEffects.Effect> here = CivicEffects.of(entity).all().stream().map(CivicEffects.Active::effect).toList();
			helper.assertTrue(here.containsAll(file.boost()) && file.cost().stream().noneMatch(here::contains), "boost kept, cost gone: " + here);
			helper.succeed();
		});
	}

	// ---- endings ----------------------------------------------------------------------------------------------------

	/** The Shift Bell stands only on natural ground: never over planks round the hall; with no natural ground, no bell and nothing broken. */
	//$ gametest_ticks_batch AREA '100' '"qaReformStoryBellGround"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaReformStoryBellGround")
	public void theShiftBellStandsOnlyOnNaturalGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos hallRel = new BlockPos(15, 2, 15);
		for (int x = 1; x <= 29; x++) {
			for (int z = 1; z <= 29; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
				for (int y = 2; y <= 29; y++) { // (builds left by earlier batches at this spot)
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(hallRel);
		helper.assertTrue(ReformStories.placeBell(level, hall) == null, "a bell was set on planks");
		helper.assertTrue(planks(helper) == 29 * 29 - 0, "planks were broken with no spot for the bell");
		// a grass patch 8 out on the east side
		for (int x = 23; x <= 25; x++) {
			for (int z = 14; z <= 16; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		BlockPos bell = ReformStories.placeBell(level, hall);
		helper.assertTrue(bell != null && level.getBlockState(bell).is(Blocks.BELL), "no bell on the grass: " + bell);
		BlockPos low = helper.absolutePos(new BlockPos(23, 1, 14));
		BlockPos high = helper.absolutePos(new BlockPos(25, 1, 16));
		boolean over = bell.getX() >= Math.min(low.getX(), high.getX()) && bell.getX() <= Math.max(low.getX(), high.getX())
			&& bell.getZ() >= Math.min(low.getZ(), high.getZ()) && bell.getZ() <= Math.max(low.getZ(), high.getZ());
		helper.assertTrue(over, "the bell isn't over the grass: " + bell + " (grass " + low + " to " + high + ")");
		helper.assertTrue(planks(helper) == 29 * 29 - 9, "planks round the hall were built over: " + planks(helper));
		helper.succeed();
	}

	/** The Fair Ledger lists exactly the last 7 days' takings day by day (not the 8th day back), with their total. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFairLedgerListsTheLastSevenDays(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 3000); // a hall of its own, never placed
		long today = Chronicle.day(level);
		ReformStories.takings(level, hall, today - 7, 999); // out of the ledger
		long total = 0;
		for (int i = 0; i < 7; i++) {
			long cents = 101 + i * 111;
			ReformStories.takings(level, hall, today - 6 + i, cents);
			total += cents;
		}
		ReformStories.Entry e = ReformStories.Data.get(level).entry(hall);
		ItemStack book = ReformStories.ledger(level, hall, e);
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, "not a written book");
		String page = content.pages().get(0).raw().getString();
		helper.assertTrue(!page.contains("9.99"), "the 8th day back is in the ledger: " + page);
		for (int i = 0; i < 7; i++) {
			String row = "Day " + (today - 6 + i) + ": " + String.format(java.util.Locale.ROOT, "%.2f", (101 + i * 111) / 100.0) + " emeralds";
			helper.assertTrue(page.contains(row), "missing row '" + row + "': " + page);
		}
		helper.assertTrue(page.contains("In all: " + String.format(java.util.Locale.ROOT, "%.2f", total / 100.0) + " emeralds"), "the total: " + page);
		helper.assertTrue(book.getHoverName().getString().startsWith("The Fair Ledger of "), "the title: " + book.getHoverName().getString());
		helper.succeed();
	}

	// ---- text -------------------------------------------------------------------------------------------------------

	private static final Pattern BRITISH = Pattern.compile("(?i)honour|grey|colour|travell|defence|armour|favour|neighbour|centre|labour|cancelled|jeweller|metre|theatre|plough|whilst");

	/** Every sentence of the eight stories renders with its arguments in American English: no raw keys, no leftover %s. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyReformStorySentenceRendersInAmericanEnglish(GameTestHelper helper) {
		List<String> arcs = List.of("shift_bell", "common_granary", "midwives", "watchful_gate", "festival_fund", "fair_ledger", "lamplighters", "militia_drill");
		List<String> keys = new ArrayList<>();
		for (String a : arcs) {
			Arcs.Arc arc = Arcs.find(a).orElseThrow(() -> new AssertionError("no arc " + a));
			helper.assertTrue(arc.side(), a + " isn't a side arc");
			keys.add("arc.aliveworkplace." + a);
			keys.add("arc.aliveworkplace." + a + ".asker");
			keys.add("arc.aliveworkplace." + a + ".ask");
			keys.add("arc.aliveworkplace." + a + ".says");
			for (int c = 1; c <= 4; c++) {
				keys.add("arc.aliveworkplace." + a + "." + c);
				keys.add("arc.aliveworkplace." + a + "." + c + ".intro.1");
				keys.add("chatter.aliveworkplace." + a + "." + c);
			}
			keys.add("chronicle.aliveworkplace." + a + ".ending");
		}
		for (String k : List.of("shift_bell", "harvest_supper", "midwife", "old_soldier", "grand_festival", "grand_held", "fair_ledger", "lamplit", "drill_day", "drilled")) {
			keys.add("chronicle.aliveworkplace.reform_story." + k);
		}
		keys.addAll(List.of("villager.aliveworkplace.reform_story.midwife", "villager.aliveworkplace.reform_story.old_soldier", "item.aliveworkplace.fair_ledger.title",
			"item.aliveworkplace.fair_ledger.author", "item.aliveworkplace.fair_ledger.row", "item.aliveworkplace.fair_ledger.page"));
		Language lang = Language.getInstance();
		List<String> bad = new ArrayList<>();
		for (String key : keys) {
			if (!lang.has(key)) {
				bad.add("missing " + key);
				continue;
			}
			String text = Component.translatable(key, "Ashford", "Maple", "3", "x", "y").getString();
			if (text.contains("%") || text.contains("aliveworkplace.") || BRITISH.matcher(text).find()) {
				bad.add(key + " -> " + text);
			}
		}
		helper.assertTrue(bad.isEmpty(), "bad sentences: " + bad);
		helper.succeed();
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** Hands in the reform's three steps (M30's kinds and numbers), one a day, checking each; {@code between} runs after each step. */
	private static void runSteps(GameTestHelper helper, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, String edict, long day,
								 ServerPlayer player, java.util.function.IntConsumer between) {
		Reforms.Reform reform = Reforms.of(edict).orElseThrow();
		for (int n = 0; n < reform.steps().size(); n++) {
			if (n > 0) {
				level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
				Arcs.round(level, hall, entity);
			}
			Reforms.Step step = reform.steps().get(n).resolve(false);
			List<Quest> quests = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			helper.assertTrue(quests.size() == 1, "step " + (n + 1) + ": " + quests.size() + " arc quests");
			Quest q = quests.get(0);
			if (step.kind() == io.github.jcondedata.aliveworkplace.hall.VillageQuests.Kind.BRING) {
				helper.assertTrue(q.objectives.get(0) instanceof Objectives.Bring b && b.count() == step.count() && b.item().equals(step.item()),
					"step " + (n + 1) + " isn't M30's: " + q.objectives.get(0).json());
				net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item()));
				int max = new ItemStack(item).getMaxStackSize();
				for (int left = step.count(); left > 0; left -= max) {
					player.getInventory().add(new ItemStack(item, Math.min(max, left)));
				}
				helper.assertTrue(Stories.handIn(player, hall, q.id) == step.count(), "not handed in: step " + (n + 1));
			} else {
				helper.assertTrue(q.objectives.get(0) instanceof Objectives.Kill k && k.count() == step.count(), "step " + (n + 1) + " isn't M30's");
				Zombie zombie = helper.spawn(EntityType.ZOMBIE, ASKER.west(4));
				for (int i = 0; i < step.count(); i++) {
					Stories.onKill(level, zombie, player);
				}
				zombie.discard();
			}
			if (between != null) {
				between.accept(n);
			}
		}
	}

	private static int blueprints(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.getItem() instanceof BlueprintItem) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static int planks(GameTestHelper helper) {
		int n = 0;
		for (int x = 1; x <= 29; x++) {
			for (int z = 1; z <= 29; z++) {
				if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_PLANKS)) {
					n++;
				}
			}
		}
		return n;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	static boolean asks(Villager v) {
		return v.getTags().contains(ArcEffects.roleTag("asker"));
	}

	static void untag(Villager v) {
		for (String t : List.copyOf(v.getTags())) {
			if (t.startsWith("aliveworkplace_")) {
				v.removeTag(t);
			}
		}
	}

	static void autoRound(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		boolean auto = Arcs.AUTO;
		Arcs.AUTO = true;
		try {
			Reforms.round(level, hall, entity);
		} finally {
			Arcs.AUTO = auto;
		}
	}

	static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		v.setVillagerXp(20);
		return v;
	}

	static VillageHallBlockEntity ready(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(abs);
		Arcs.stopAll(level, abs);
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setQuests(List.of());
		Stories.Data.get(level).remove(abs);
		hall.setLastQuestDay(Long.MAX_VALUE / 2);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
