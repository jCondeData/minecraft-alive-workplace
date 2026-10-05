package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.GrandRebuild;
import io.github.jcondedata.aliveworkplace.legend.GrandRebuildPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PacePower;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.FinishedBuildings;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * 29.12, the Master Architect: the real file (a Legendary Builder who likes jewels, visits the inn), visiting only a Town
 * with finished buildings in 3 styles; builders within 32 blocks twice as fast and at the cap, one outside not; grander
 * buildings: an upgrade first (homes first, never a decoration), the Grand style when there's none, only the changed
 * blocks, never a player's own build, one at a time, every 3 days, paused by a sneak-right-click, stopped by a strike;
 * the Architect's state through a save and reload; and every sentence a player reads.
 */
public class MasterArchitectGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation ID = AliveWorkplace.id("master_architect");

	/** The hall; when the test ends, the villagers, their record entries, the build sites and the buildings recorded go. */
	private static void setUp(GameTestHelper helper, List<BlueprintData.Placement> recorded) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		// A Legendary Legend comes once to each world: an Architect another test left in the server's record holds the slot.
		LegendRecord stale = LegendRecord.get(helper.getLevel());
		stale.entries().stream().filter(e -> e.id().equals(ID)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			recorded.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			LegendRecord record = LegendRecord.get(level);
			Set<UUID> ours = new HashSet<>();
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(64), v -> true)) {
				record.forget(v.getUUID());
				ours.add(v.getUUID());
				v.discard();
			}
			for (BuildSite s : List.copyOf(BuildSiteManager.get(level).all())) {
				if (s.builder() != null && ours.contains(s.builder())) {
					BuildSiteManager.get(level).remove(s.id());
				}
			}
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with only the real Master Architect loaded (and {@code more}), moods off, then puts it all back. */
	private static void staged(GameTestHelper helper, List<Legend> more, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		long time = level.getDayTime();
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend architect = Legends.get(ID).orElseThrow(() -> new AssertionError("master_architect.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, architect);
			more.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.accept(architect);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/** {@code id} recorded as finished by the village's builders at {@code origin} (nothing placed). */
	private static BlueprintData.Placement finished(GameTestHelper helper, ResourceLocation id, BlockPos origin, List<BlueprintData.Placement> recorded) {
		BlueprintData.Placement p = placement(helper, origin);
		BuildSiteManager.get(helper.getLevel()).recordFinished(id, p, UUID.randomUUID());
		recorded.add(p);
		return p;
	}

	/** The Architect, settled in the village. */
	private static Villager architect(GameTestHelper helper, Legend legend, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend, "test");
		return v;
	}

	/** A builder at a Blueprint Table at {@code bench}. */
	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		v.setNoAi(true);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static GrandRebuildPower power(Legend legend) {
		return legend.powers(GrandRebuildPower.class).get(0);
	}

	private static BuildSite round(GameTestHelper helper, Villager architect, Legend legend) {
		return GrandRebuild.round(helper.getLevel(), architect, ModAttachments.LEGEND.get(architect), power(legend));
	}

	private static List<String> chronicle(GameTestHelper helper) {
		return hall(helper).chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LEGEND).map(e -> e.text().getString()).toList();
	}

	private static ResourceLocation grand(ResourceLocation base) {
		return BlueprintStyles.styled(base, GrandRebuild.GRAND);
	}

	/** The real file: a Legendary Builder who likes jewels, comes to the inn, with both powers and the Grand style on the server. */
	//$ gametest_ticks_batch AREA '100' '"architectFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectFile")
	public void theArchitectsFile(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			helper.assertTrue(legend.rarity() == Rarity.LEGENDARY && legend.job().equals(AliveWorkplace.id("builder")), "rarity and trade: " + legend);
			helper.assertTrue(legend.luxury().equals(Optional.of("jewels")), "likes: " + legend.luxury());
			helper.assertTrue(legend.ways("visit").size() == 1 && legend.ways("visit").get(0).get("place").getAsString().equals("inn"), "comes: " + legend.arrive());
			PacePower pace = legend.powers(PacePower.class).get(0);
			helper.assertTrue(pace.radius() == 32 && pace.factor() == 2f && pace.trades().equals(Set.of(AliveWorkplace.id("builder"))), "pace: " + pace);
			helper.assertTrue(power(legend).days() == 3 && power(legend).style().equals("grand"), "grand_rebuild: " + power(legend));
			helper.assertTrue(legend.conditions().stream().anyMatch(c -> c instanceof FinishedBuildings f && f.styles() == 3), "3 styles: " + legend.conditions());
			helper.assertTrue(BlueprintStyles.get("grand").isPresent(), "no Grand style");
			Blueprint grandHouse = BlueprintLibrary.get(helper.getLevel(), grand(StarterBlueprints.STONE_HOUSE.id())).orElseThrow();
			Set<String> blocks = new HashSet<>();
			grandHouse.blocks().forEach(e -> blocks.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getPath()));
			helper.assertTrue(blocks.contains("stone_bricks") && blocks.contains("dark_oak_planks") && blocks.contains("polished_deepslate")
				&& !blocks.contains("cobblestone") && !blocks.contains("spruce_planks"), "the Grand Stone House's blocks: " + blocks);
			// every sentence a player reads
			helper.assertTrue(legend.titleText().getString().equals("Master Architect"), "title: " + legend.titleText().getString());
			helper.assertTrue(legend.loreText().getString().startsWith("Halls for kings"), "lore: " + legend.loreText().getString());
			for (String name : legend.names()) {
				helper.assertTrue(!Component.translatable(name).getString().equals(name), "untranslated name " + name);
			}
			helper.assertTrue(pace.describe().getString().equals("Every Builder within 32 blocks works 2× as fast"), "pace line: " + pace.describe().getString());
			helper.assertTrue(power(legend).describe().getString().equals("Every 3 days, a building the builders finished is upgraded, or redrawn in the Grand style"),
				"grand_rebuild line: " + power(legend).describe().getString());
			helper.assertTrue(BlueprintStyles.get("grand").orElseThrow().title().getString().equals("Grand"), "style name");
			helper.succeed();
		}));
	}

	/** The Architect comes only to a Town with finished buildings in at least 3 styles (the as-drawn one counts). */
	//$ gametest_ticks_batch AREA '100' '"architectVisit"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectVisit")
	public void visitsOnlyAQualifyingTown(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			long day = Chronicle.day(level);
			hall(helper).setRank(VillageRanks.Rank.VILLAGE);
			finished(helper, StarterBlueprints.STONE_HOUSE.id(), new BlockPos(10, 2, 3), recorded);
			finished(helper, BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "cherry"), new BlockPos(10, 2, 12), recorded);
			finished(helper, BlueprintStyles.styled(StarterBlueprints.INN.id(), "stonework"), new BlockPos(20, 2, 3), recorded);
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).isEmpty(), "came to a Village");
			hall(helper).setRank(VillageRanks.Rank.TOWN);
			BuildSiteManager.get(level).forgetFinished(recorded.remove(2));
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).isEmpty(), "came to a Town with buildings in 2 styles");
			finished(helper, BlueprintStyles.styled(StarterBlueprints.INN.id(), "stonework"), new BlockPos(20, 2, 3), recorded);
			helper.assertTrue(LegendGuests.candidates(level, hall, "inn", day).contains(legend), "didn't come to a Town with 3 styles: "
				+ legend.conditions().stream().map(c -> c.type() + " " + c.progress(level, hall).line().getString()).toList()
				+ ", slot: " + io.github.jcondedata.aliveworkplace.legend.LegendSlots.whyNot(level, hall, legend, null).map(Component::getString));
			helper.assertTrue(LegendGuests.candidates(level, hall, "market", day).isEmpty(), "comes to the market too");
			helper.succeed();
		}));
	}

	/** Builders within 32 blocks are twice as fast, at the cap even with another Legend's pace; one outside isn't. */
	//$ gametest_ticks_batch AREA '100' '"architectPace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectPace")
	public void buildersNearbyAreTwiceAsFast(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		Legend other = Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "other_pace"),
			com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"t\", \"lore\": \"l\","
				+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"aliveworkplace:builder\"], \"factor\": 1.5}]}").getAsJsonObject());
		helper.runAfterDelay(2, () -> staged(helper, List.of(other), legend -> {
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 5));
			Villager near = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 25)); // 20 blocks away
			Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 45));  // 40 blocks away
			for (Villager v : List.of(near, far)) {
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER));
			}
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(near) == 2f, "a builder 20 blocks away: " + LegendPowers.pace(near));
			helper.assertTrue(LegendPowers.pace(far) == 1f, "a builder 40 blocks away: " + LegendPowers.pace(far));
			helper.assertTrue(Pace.factor(near) < Pace.factor(far), "the near builder's pace: " + Pace.factor(near) + " vs " + Pace.factor(far));
			Villager mason = architect(helper, other, new BlockPos(6, 2, 24));
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(near) == LegendPowers.PACE_CAP, "past the cap with two Legends: " + LegendPowers.pace(near));
			Legends.clear(mason);
			// on strike: no powers
			LegendData data = ModAttachments.LEGEND.get(arch);
			long today = Chronicle.day(helper.getLevel());
			ModAttachments.LEGEND.set(arch, data.checkedOn(today, Map.of("luxury", 3), today, -1));
			LegendPowers.forget();
			helper.assertTrue(LegendPowers.pace(near) == 1f, "a striking Architect still speeds builders up");
			helper.succeed();
		}));
	}

	/** An upgrade first, homes first, never a decoration; one at a time and every 3 days; the chronicle says so. */
	//$ gametest_ticks_batch AREA '100' '"architectUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectUpgrade")
	public void aRebuildPicksAnUpgrade(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.WELL.id(), new BlockPos(12, 2, 2), recorded);          // a decoration with an upgrade
			finished(helper, StarterBlueprints.MARKET_STALL.id(), new BlockPos(20, 2, 2), recorded);  // no beds
			BlueprintData.Placement cottage = finished(helper, StarterBlueprints.STARTER_COTTAGE.id(), new BlockPos(12, 2, 14), recorded);
			Villager busy = builder(helper, new BlockPos(2, 2, 22));
			Builders.start(level, busy, null, StarterBlueprints.PARK_BENCH.id(), placement(helper, new BlockPos(2, 2, 26)));
			Villager idle = builder(helper, new BlockPos(6, 2, 22));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			BuildSite site = round(helper, arch, legend);
			helper.assertTrue(site != null, "no rebuild");
			helper.assertTrue(site.structure().equals(StarterBlueprints.STARTER_COTTAGE_2.id()) && site.placement().equals(cottage),
				"not the cottage's upgrade on the cottage: " + site.structure() + " at " + site.placement());
			helper.assertTrue(idle.getUUID().equals(site.builder()), "not the least busy builder");
			helper.assertTrue(site.isSteward(), "a rebuild hands a blueprint back");
			helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("asked for the Starter Cottage to be upgraded") && l.contains(idle.getDisplayName().getString())),
				"chronicle: " + chronicle(helper));
			helper.assertTrue(round(helper, arch, legend) == null, "two at once");
			BuildSiteManager.get(level).remove(site.id());
			ModAttachments.BUILDER_JOB.remove(idle);
			helper.assertTrue(round(helper, arch, legend) == null, "another within 3 days");
			level.setDayTime(level.getDayTime() + 3 * 24000L);
			BuildSite next = round(helper, arch, legend);
			helper.assertTrue(next != null && !StarterBlueprints.WELL_2.id().equals(next.structure()), "after 3 days: " + (next == null ? null : next.structure()));
			helper.assertTrue(GrandRebuild.choose(level, helper.absolutePos(HALL), "grand").map(c -> !c.building().structure().equals(StarterBlueprints.WELL.id())).orElse(true),
				"a decoration picked");
			helper.succeed();
		}));
	}

	/** A building with no upgrade is redrawn in the Grand style; one already Grand with none is left alone. */
	//$ gametest_ticks_batch AREA '100' '"architectGrand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectGrand")
	public void theGrandStyleWhenThereIsNoUpgrade(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlueprintData.Placement top = finished(helper, grand(StarterBlueprints.STONE_HOUSE_3.id()), new BlockPos(12, 2, 2), recorded);
			helper.assertTrue(GrandRebuild.choose(level, hall, "grand").isEmpty(), "a Grand building with no upgrade picked");
			BuildSiteManager.get(level).forgetFinished(top);
			BlueprintData.Placement house = finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			BuildSite site = round(helper, arch, legend);
			helper.assertTrue(site != null && site.structure().equals(grand(StarterBlueprints.STONE_HOUSE_3.id())) && site.placement().equals(house),
				"not the Stone House redrawn: " + (site == null ? null : site.structure()));
			helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("redrew the Stone House") && l.contains("in the Grand style")), "chronicle: " + chronicle(helper));
			helper.succeed();
		}));
	}

	/** Redrawn in the Grand style, only the blocks that differ need work; a player's own build is never picked or touched. */
	//$ gametest_ticks_batch AREA '200' '"architectChanged"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "architectChanged")
	public void onlyTheChangedBlocksAndNeverAPlayersBuild(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			// a player's own house, built by hand: the same blueprint's blocks, never recorded as finished
			BlueprintData.Placement mine = placement(helper, new BlockPos(16, 2, 2));
			StructureTemplate house = level.getStructureManager().get(StarterBlueprints.STONE_HOUSE.id()).orElseThrow();
			house.placeInWorld(level, mine.origin(), mine.origin(), new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			builder(helper, new BlockPos(22, 2, 22));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			helper.assertTrue(GrandRebuild.choose(level, hall, "grand").isEmpty(), "a player's build picked");
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild with only a player's build");
			helper.assertTrue(level.getBlockState(firstOf(level, house, mine, Blocks.COBBLESTONE, helper)).is(Blocks.COBBLESTONE), "the player's house was touched");
			// the village's own house, finished
			BlueprintData.Placement theirs = placement(helper, new BlockPos(2, 2, 14));
			house.placeInWorld(level, theirs.origin(), theirs.origin(), new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1L), 2);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.STONE_HOUSE.id(), theirs, UUID.randomUUID());
			recorded.add(theirs);
			// upgrades come first: with the Stone House's own tier 2 on the server, ask for the redrawing directly
			ResourceLocation grandId = grand(StarterBlueprints.STONE_HOUSE.id());
			Blueprint base = BlueprintLibrary.get(level, StarterBlueprints.STONE_HOUSE.id()).orElseThrow();
			Blueprint grandBp = BlueprintLibrary.get(level, grandId).orElseThrow();
			BuildPlan plan = BuildPlan.create(grandBp, theirs);
			Map<BlockPos, BlockState> baseAt = new java.util.HashMap<>();
			BuildPlan.create(base, theirs).steps(BuildPlan.Stage.STRUCTURE).forEach(s -> baseAt.put(s.pos(), s.state()));
			BuildPlan.create(base, theirs).steps(BuildPlan.Stage.DECORATION).forEach(s -> baseAt.put(s.pos(), s.state()));
			int work = 0;
			int changed = 0;
			int total = 0;
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					total++;
					boolean needs = !MaterialRules.matches(level.getBlockState(step.pos()), step.state());
					boolean differs = baseAt.get(step.pos()) == null || baseAt.get(step.pos()).getBlock() != step.state().getBlock();
					work += needs ? 1 : 0;
					changed += differs ? 1 : 0;
					helper.assertTrue(needs == differs, "at " + step.pos() + " the world has " + level.getBlockState(step.pos()) + ", the Grand house wants "
						+ step.state() + (needs ? ": work on an unchanged block" : ": a changed block left alone"));
				}
			}
			helper.assertTrue(work > 0 && work < total, "work " + work + " of " + total + " blocks (changed: " + changed + ")");
			helper.assertTrue(level.getBlockState(firstOf(level, house, mine, Blocks.COBBLESTONE, helper)).is(Blocks.COBBLESTONE), "the player's house was touched");
			helper.succeed();
		}));
	}

	/** The first block of {@code block} in {@code template} placed at {@code at} (a world position). */
	private static BlockPos firstOf(ServerLevel level, StructureTemplate template, BlueprintData.Placement at, net.minecraft.world.level.block.Block block,
									GameTestHelper helper) {
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block)) {
			return at.origin().offset(info.pos());
		}
		throw new AssertionError("no " + block + " in the template");
	}

	/** A strike stops the rebuild under way (placed blocks stay) and starts none; the chronicle says so. */
	//$ gametest_ticks_batch AREA '100' '"architectStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectStrike")
	public void aStrikeStopsTheRebuild(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			BuildSite site = round(helper, arch, legend);
			helper.assertTrue(site != null, "no rebuild");
			long today = Chronicle.day(level);
			LegendData data = ModAttachments.LEGEND.get(arch);
			ModAttachments.LEGEND.set(arch, data.checkedOn(today, Map.of("luxury", 3), today, -1));
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild on strike");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the rebuild went on through the strike");
			helper.assertTrue(GrandRebuild.state(arch).site().isEmpty(), "the Architect still minds the stopped site");
			helper.assertTrue(chronicle(helper).stream().anyMatch(l -> l.contains("went on strike, and the work on the Stone House") && l.contains("stopped")),
				"chronicle: " + chronicle(helper));
			level.setDayTime(level.getDayTime() + 4 * 24000L);
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild on strike days later");
			helper.succeed();
		}));
	}

	/** A sneak-right-click pauses grander buildings and another carries on; the player reads which. */
	//$ gametest_ticks_batch AREA '100' '"architectPause"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectPause")
	public void sneakRightClickPauses(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setShiftKeyDown(true);
			helper.assertTrue(GrandRebuild.isArchitect(arch), "not the Architect");
			GrandRebuild.togglePause(player, arch); // what BuilderEvents does on a sneak-right-click with an empty hand
			helper.assertTrue(GrandRebuild.state(arch).paused(), "not paused");
			helper.assertTrue(round(helper, arch, legend) == null, "a rebuild while paused");
			helper.assertTrue(Component.translatable("message.aliveworkplace.legend.grand_paused", "Ada").getString().equals("Ada will leave the village's buildings as they are"),
				"the paused line");
			GrandRebuild.togglePause(player, arch);
			helper.assertTrue(Component.translatable("message.aliveworkplace.legend.grand_resumed", "Ada").getString().equals("Ada will make the village's buildings grander again"),
				"the resumed line");
			helper.assertTrue(!GrandRebuild.state(arch).paused() && round(helper, arch, legend) != null, "no rebuild once carried on");
			helper.assertTrue(Legends.in(level, helper.absolutePos(HALL), ID).orElse(null) == arch, "Legends.in doesn't find the Architect");
			helper.succeed();
		}));
	}

	/** The Architect's state survives a save and reload; a villager saved before 29.12 loads with none. */
	//$ gametest_ticks_batch AREA '100' '"architectSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "architectSave")
	public void theArchitectSurvivesSaveAndReload(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		helper.runAfterDelay(2, () -> staged(helper, List.of(), legend -> {
			ServerLevel level = helper.getLevel();
			finished(helper, StarterBlueprints.STONE_HOUSE_3.id(), new BlockPos(12, 2, 2), recorded);
			builder(helper, new BlockPos(4, 2, 20));
			Villager arch = architect(helper, legend, new BlockPos(5, 2, 8));
			BuildSite site = round(helper, arch, legend);
			GrandRebuild.State before = GrandRebuild.state(arch);
			helper.assertTrue(site != null && before.site().equals(Optional.of(site.id())) && before.lastDay() == Chronicle.day(level), "state: " + before);
			CompoundTag tag = arch.saveWithoutId(new CompoundTag());
			arch.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(tag);
			helper.assertTrue(before.equals(GrandRebuild.state(loaded)), "after a reload: " + GrandRebuild.state(loaded));
			level.addFreshEntity(loaded);
			helper.assertTrue(round(helper, loaded, legend) == null, "a second rebuild after a reload");
			Villager old = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
			CompoundTag oldTag = old.saveWithoutId(new CompoundTag());
			Villager oldLoaded = EntityType.VILLAGER.create(level);
			oldLoaded.load(oldTag);
			helper.assertTrue(GrandRebuild.state(oldLoaded).equals(GrandRebuild.State.EMPTY), "an old villager: " + GrandRebuild.state(oldLoaded));
			helper.succeed();
		}));
	}
}
