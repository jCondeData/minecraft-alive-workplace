package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.command.SeasonSoak;
import io.github.jcondedata.aliveworkplace.command.SeasonSoak.Day;
import io.github.jcondedata.aliveworkplace.command.SeasonSoak.Part;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * QA (lane-c-1114-0053) for ROADMAP 30.1 (the design note: its config switches and defaults, its data format, the new
 * saved fields' defaults), 30.22 (a season under the edicts: how its result line judges the boundaries of "no pace
 * beyond the cap", "the costs show" and "the village never shrinks", and its command only on benchmark servers) and
 * B1212 (no quest check loads a hall's chunk), written from the spec. Nothing here is random.
 */
public class QaEdicts1114cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** The edicts of Milestone 30, each with a reform (the design note's list). */
	private static final List<String> M30 = List.of("long_shifts", "free_bread", "large_families", "open_gates", "festival_season",
		"tithe", "curfew", "conscription");

	// --- 30.1: the design note's switches, data and saved fields --------------------------------------------------

	/**
	 * The design note's switches and numbers: a config file without any of them (a server from before 1.4) reads them at
	 * the note's defaults (edictMinDays 3, maxWorkPace 200, guildsPerRank 1) and each civic switch at the same value as a
	 * fresh config; the numbers reach the systems that read them, and at those defaults the cap is half the usual time.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAConfigFromBefore14ReadsTheNotesDefaults(GameTestHelper helper) {
		WorkplaceConfig old = WorkplaceConfig.parse("{}");
		WorkplaceConfig fresh = new WorkplaceConfig();
		helper.assertTrue(old.edictMinDays == 3 && old.maxWorkPace == 200 && old.guildsPerRank == 1,
			"numbers: edictMinDays " + old.edictMinDays + ", maxWorkPace " + old.maxWorkPace + ", guildsPerRank " + old.guildsPerRank);
		helper.assertTrue(old.villageEdicts == fresh.villageEdicts && old.workHorns == fresh.workHorns && old.cradles == fresh.cradles
			&& old.villageBanners == fresh.villageBanners && old.harvestIdols == fresh.harvestIdols && old.tonics == fresh.tonics
			&& old.guilds == fresh.guilds, "a switch read from an old file differs from a fresh config's");
		helper.assertTrue(old.seasonDays >= 1, "seasonDays: " + old.seasonDays);
		int pace = Pace.MAX_PERCENT;
		int days = Edicts.MIN_DAYS;
		try {
			Pace.MAX_PERCENT = old.maxWorkPace;
			helper.assertTrue(Pace.cap() == 0.5f, "the cap at maxWorkPace 200: " + Pace.cap());
			// "Bonuses multiply and stop at 100/maxWorkPace; penalties multiply after the cap": an ill worker at the cap is at the usual time.
			helper.assertTrue(Pace.combine(0.25f, 2f, Pace.cap()) == 1f && Pace.combine(0.6f, 1f, Pace.cap()) == 0.6f,
				"combine: " + Pace.combine(0.25f, 2f, Pace.cap()) + " " + Pace.combine(0.6f, 1f, Pace.cap()));
		} finally {
			Pace.MAX_PERCENT = pace;
			Edicts.MIN_DAYS = days;
		}
		helper.succeed();
	}

	/**
	 * The data format (icon, name and texts, boost and cost as lists of effects, the edicts it excludes, the reform's
	 * name, line and steps): every Milestone 30 edict has a real item for an icon, its name and description read as words
	 * (not lang keys), a boost and a cost, a reform with a name, a line and steps, and each edict it excludes is one that
	 * exists (a typo there would let both be in force at once).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryEdictFileIsWhole(GameTestHelper helper) {
		for (String id : M30) {
			Edicts.Edict e = Edicts.find(id).orElse(null);
			helper.assertTrue(e != null, "no edict " + id);
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(e.icon()) && BuiltInRegistries.ITEM.get(e.icon()) != Items.AIR, id + " icon: " + e.icon());
			String name = e.name().getString();
			String text = e.description().getString();
			helper.assertTrue(!name.isBlank() && !name.contains("aliveworkplace") && !text.isBlank() && !text.contains("aliveworkplace"),
				id + " texts: '" + name + "' / '" + text + "'");
			helper.assertTrue(!e.boost().isEmpty() && !e.cost().isEmpty(), id + " boost " + e.boost() + " cost " + e.cost());
			helper.assertTrue(e.reform().isPresent(), id + " has no reform");
			var reform = e.reform().get();
			String rName = reform.name().getString();
			String rLine = reform.line().getString();
			helper.assertTrue(!rName.isBlank() && !rName.contains("aliveworkplace") && !rLine.isBlank() && !rLine.contains("aliveworkplace"),
				id + " reform texts: '" + rName + "' / '" + rLine + "'");
			helper.assertTrue(!reform.steps().isEmpty(), id + " reform has no steps");
			for (ResourceLocation other : e.excludes()) {
				helper.assertTrue(!other.equals(e.id()), id + " excludes itself");
				helper.assertTrue(Edicts.get(other).isPresent(), id + " excludes an edict that doesn't exist: " + other);
				helper.assertTrue(Edicts.get(other).get().excludes(e), other + " doesn't exclude " + id + " back");
			}
		}
		helper.succeed();
	}

	/**
	 * Every new saved field has a default: a Village Hall saved before 1.4 (no edicts, reforms, colours, horn day or
	 * guilds in its save) loads with nothing in force, no colours, the horn never blown and no guild; and once something
	 * is in force, it survives a save and reload.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEdicts1114cOldHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEdicts1114cOldHall")
	public void qaAHallSavedBefore14LoadsWithEveryDefault(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), 2)));
		entity.startRush(7, level.getGameTime() + 100);
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		helper.assertTrue(tag.contains("edicts") && tag.contains("hornDay"), "the new fields aren't saved: " + tag.getAllKeys());

		// Reloaded as it is: kept.
		entity.loadWithComponents(tag.copy(), level.registryAccess());
		helper.assertTrue(entity.edicts().size() == 1 && entity.edicts().get(0).id().equals(AliveWorkplace.id("long_shifts").toString())
			&& entity.hornDay() == 7, "reloaded: " + entity.edicts() + " horn " + entity.hornDay());

		// The same hall as a pre-1.4 save had it.
		for (String key : List.of("edicts", "reforms", "guilds", "hornDay", "bannerBase", "bannerPatterns")) {
			tag.remove(key);
		}
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.edicts().isEmpty() && entity.reforms().isEmpty() && entity.guilds().isEmpty(),
			"old hall: edicts " + entity.edicts() + " reforms " + entity.reforms() + " guilds " + entity.guilds());
		helper.assertTrue(entity.colours() == null && entity.hornDay() == -1, "old hall: colours " + entity.colours() + " horn " + entity.hornDay());
		helper.succeed();
	}

	// --- 30.22: a season under the edicts -----------------------------------------------------------------------------

	private static Day day(int number, boolean reformed, int taken, int paid, int villagers, float slowest) {
		return new Day(number, reformed, taken, 200, false, 1, villagers, 0, 1, 60f, 50f, 20, paid, paid > 0, false,
			List.of(20, 20, 20, 20, 20), List.of(80, 80, 80, 80, 80), 100, slowest, 4.0, 40.0, 0, 90, 120);
	}

	private static List<Day> season(int takenEdicts, int takenReformed, int villagers, float slowest) {
		List<Day> days = new ArrayList<>();
		for (int d = 1; d <= 8; d++) {
			boolean reformed = d > 4;
			days.add(day(d, reformed, reformed ? takenReformed : takenEdicts, d == 2 ? 11 : 0, villagers, slowest));
		}
		return days;
	}

	/**
	 * "No pace beyond the cap", from both sides: work at exactly the cap's share of the usual time is not running away,
	 * a thousandth of the usual time less is; the cap follows maxWorkPace (at 400 a quarter of the time is allowed).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaASeasonAtExactlyTheCapPassesAndAHairBeyondFails(GameTestHelper helper) {
		String at = SeasonSoak.result(season(80, 60, 35, Pace.cap()), Part.BOTH, 35);
		helper.assertTrue(SeasonSoak.passed(at) && at.contains("ran away: nothing") && !at.contains("BEYOND THE CAP"), "at the cap: " + at);
		String beyond = SeasonSoak.result(season(80, 60, 35, Pace.cap() - 0.001f), Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(beyond) && beyond.contains("BEYOND THE CAP") && beyond.contains("ran away: YES"), "beyond: " + beyond);
		int was = Pace.MAX_PERCENT;
		try {
			Pace.MAX_PERCENT = 400;
			String quarter = SeasonSoak.result(season(80, 60, 35, 0.25f), Part.BOTH, 35);
			helper.assertTrue(SeasonSoak.passed(quarter) && quarter.contains("cap 0.250"), "at 400: " + quarter);
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * "The store falls faster under Free Bread and Large Families than reformed", from both sides: one meal more a day
	 * under the edicts shows the cost, one fewer doesn't. A village that stays the same size never shrank.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaOneMealMoreADayShowsTheCostAndOneFewerDoesnt(GameTestHelper helper) {
		String more = SeasonSoak.result(season(61, 60, 35, Pace.cap()), Part.BOTH, 35);
		helper.assertTrue(SeasonSoak.passed(more) && more.contains("costs show"), "one more: " + more);
		helper.assertTrue(more.contains("villagers 35 to 35, never fewer"), "same size: " + more);
		String fewer = SeasonSoak.result(season(59, 60, 35, Pace.cap()), Part.BOTH, 35);
		helper.assertTrue(!SeasonSoak.passed(fewer) && fewer.contains("the store gave out no more under the edicts"), "one fewer: " + fewer);
		helper.succeed();
	}

	/**
	 * The season runs only on benchmark servers: on a server started without {@code -Daliveworkplace.benchmark=true} (as
	 * this one is) there is no {@code /workplace season} for anyone to run, so no player's world is laid out by it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheSeasonCommandIsOnlyOnBenchmarkServers(GameTestHelper helper) {
		var root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot();
		var workplace = root.getChild("workplace");
		boolean has = workplace != null && workplace.getChild("season") != null;
		helper.assertTrue(has == Boolean.getBoolean("aliveworkplace.benchmark"), "/workplace season registered: " + has
			+ " (benchmark " + Boolean.getBoolean("aliveworkplace.benchmark") + ")");
		helper.assertTrue(SeasonSoak.EDICTS.equals(List.of("long_shifts", "free_bread", "large_families", "festival_season")),
			"the spec's four edicts: " + SeasonSoak.EDICTS);
		helper.succeed();
	}

	// --- B1212: no quest check loads a hall's chunk ---------------------------------------------------------------------

	/** A spot far from every test area and player, so its chunk is never loaded unless something loads it. */
	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static Quest quest(ServerLevel level, BlockPos hall, String giver, @org.jetbrains.annotations.Nullable UUID villager, Objectives.Objective objective) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114c"), giver, Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(objective), new int[1], List.of());
		q.villager = villager;
		Stories.post(level, hall, q);
		return q;
	}

	/**
	 * Three halls in three unloaded chunks, each with a villager's request: through more than two rounds of the level's
	 * own personal-request ticks (every 40) none of their chunks loads, and each still lists its request.
	 */
	//$ gametest_ticks_batch AREA '140' '"qaEdicts1114cThreeHalls"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaEdicts1114cThreeHalls")
	public void qaThreeUnloadedHallsStayUnloadedThroughTheErrandTicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<BlockPos> halls = List.of(far(helper, 7000, 7000), far(helper, -7000, 7000), far(helper, 7000, -7000));
		List<Quest> quests = new ArrayList<>();
		for (BlockPos hall : halls) {
			Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
			helper.assertTrue(!loaded(level, hall), "setup: " + hall + " is loaded");
			quests.add(quest(level, hall, "villager", UUID.randomUUID(), new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty())));
		}
		helper.runAfterDelay(100, () -> {
			for (int i = 0; i < halls.size(); i++) {
				BlockPos hall = halls.get(i);
				helper.assertTrue(!loaded(level, hall), "the level's ticks loaded " + hall);
				helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(quests.get(i))), hall + " lists " + PersonalRequests.open(level, hall));
				helper.assertTrue(!loaded(level, hall), "listing loaded " + hall);
			}
			helper.succeed();
		});
	}

	/**
	 * {@code /workplace quests track <id>} looks the quest up in every hall of the player's dimension: with a hall in an
	 * unloaded chunk, tracking a quest it holds finds it and tracking an unknown id finds nothing, and neither loads it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEdicts1114cTrack"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEdicts1114cTrack")
	public void qaTrackingAQuestByIdLoadsNoUnloadedHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7400, -7400);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		Quest q = quest(level, hall, "hall", null, new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty()));
		var player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.hall.QuestJournal.trackById(player, UUID.randomUUID()), "an unknown id was tracked");
		helper.assertTrue(!loaded(level, hall), "tracking an unknown id loaded the hall's chunk");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.QuestJournal.trackById(player, q.id), "the unloaded hall's quest wasn't found");
		helper.assertTrue(!loaded(level, hall), "tracking its quest loaded the hall's chunk");
		helper.succeed();
	}

	/**
	 * The other side of B1212: a hall that is loaded still has its quests checked (the errand tick and Stories.open load
	 * nothing new and list what's there), so the fix didn't make loaded halls look empty.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaEdicts1114cLoadedHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaEdicts1114cLoadedHall")
	public void qaALoadedHallStillListsItsQuests(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		Quest q = quest(level, hall, "villager", UUID.randomUUID(), new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty()));
		RequestErrands.placeHeirlooms(level);
		helper.assertTrue(loaded(level, hall), "the test's own hall isn't loaded");
		helper.assertTrue(Stories.open(level, hall).contains(q) && PersonalRequests.open(level, hall).contains(q),
			"loaded hall: " + Stories.open(level, hall) + " / " + PersonalRequests.open(level, hall));
		helper.succeed();
	}
}
