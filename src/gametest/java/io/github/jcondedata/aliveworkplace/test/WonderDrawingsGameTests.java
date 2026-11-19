package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderDrawings;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * ROADMAP 52.6's prerequisite, the Wonders' old-drawing fallback. A Wonder is begun with its drawing of the day and keeps
 * to it: when the Wonder is redrawn, a Wonder begun with the older drawing (shipped in {@code blueprint_history/wonder/})
 * is counted, repaired and continued with that drawing's stages, a new one takes today's, and one from a save made before
 * the record is matched by best fit. A redraw is simulated with an older drawing of the test folly added by hand (the
 * way a shipped one is read): two stages unlike today's three (a lapis floor with quartz corners, then a gold top), so
 * which drawing a Wonder goes by shows in every block.
 */
public class WonderDrawingsGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The older drawing's key (a shipped one is {@code drawn_} and 9 hex digits). */
	private static final String OLDER = "drawn_test_older";
	private static final ResourceLocation PART_1 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0");
	private static final ResourceLocation PART_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_2_0");
	private static final Set<Block> OLDER_BLOCKS = Set.of(Blocks.LAPIS_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.GOLD_BLOCK);
	private static final BlockPos HALL = new BlockPos(2, 2, 27);
	private static final BlockPos BENCH = new BlockPos(5, 2, 28);
	private static final BlockPos MIDDLE = new BlockPos(14, 2, 14);

	private record Village(ServerLevel level, ServerPlayer owner, BlockPos hall, Villager builder) {
	}

	// ---- the older drawing -------------------------------------------------------------------------------------------

	/** Stage 1 of the older drawing: a 9 x 9 lapis floor with a quartz post two high at each corner. */
	private static Blueprint olderStage1() {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				blocks.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.LAPIS_BLOCK.defaultBlockState(), null));
			}
		}
		for (int[] c : new int[][] {{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
			for (int y = 1; y <= 2; y++) {
				blocks.add(new Blueprint.Entry(new BlockPos(c[0], y, c[1]), Blocks.QUARTZ_BLOCK.defaultBlockState(), null));
			}
		}
		return new Blueprint(PART_1, new Vec3i(9, 3, 9), blocks, List.of());
	}

	/** Stage 2 of the older drawing: a 5 x 5 gold top, laid at (2, 3, 2). */
	private static Blueprint olderStage2() {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				blocks.add(new Blueprint.Entry(new BlockPos(x, 0, z), Blocks.GOLD_BLOCK.defaultBlockState(), null));
			}
		}
		return new Blueprint(PART_2, new Vec3i(5, 1, 5), blocks, List.of());
	}

	/**
	 * Adds the older drawing {@link #OLDER} of the folly as a shipped one is read: its file's stages (today's first two
	 * stages' names and goods, its own parts and offsets) and its parts' blueprints in the history. Taken away after the test.
	 */
	private static void addOlderDrawing(GameTestHelper helper) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		helper.assertTrue(!Wonders.drawing(FOLLY).isEmpty() && !Wonders.drawing(FOLLY).equals(OLDER), "the folly's file names no drawing: " + Wonders.drawing(FOLLY));
		List<Wonder.Stage> stages = List.of(
			new Wonder.Stage(folly.stages().get(0).name(), List.of(new Wonder.Part(PART_1, BlockPos.ZERO)), folly.stages().get(0).goods()),
			new Wonder.Stage(folly.stages().get(1).name(), List.of(new Wonder.Part(PART_2, new BlockPos(2, 3, 2))), folly.stages().get(1).goods()));
		Refreshes.addHistory(PART_1, OLDER, olderStage1());
		Refreshes.addHistory(PART_2, OLDER, olderStage2());
		WonderDrawings.addHistory(FOLLY, OLDER, stages);
		Leftovers.after(helper, () -> {
			WonderDrawings.removeHistory(FOLLY);
			Refreshes.removeHistory(PART_1);
			Refreshes.removeHistory(PART_2);
		});
	}

	// ---- a village and its Wonder (as WonderFinishedGameTests raises one) -------------------------------------------

	/** A Town owned by a player, with one builder employed at a bench by the hall; free materials. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "drawing-owner");
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 26));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		return new Village(level, player, helper.absolutePos(HALL), builder);
	}

	/** The folly's plan placed with its middle at {@link #MIDDLE} and handed to the village's builder, its goods in the stockpile. */
	private static WonderSite begin(GameTestHelper helper, Village v) {
		return begin(helper, v, true);
	}

	/**
	 * The folly's plan placed with its middle at {@link #MIDDLE} and handed to the village's builder, as a player does;
	 * with {@code stocked}, every stage's goods in the stockpile first (stage 1 starts at once), else none (it waits).
	 */
	private static WonderSite begin(GameTestHelper helper, Village v, boolean stocked) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		ItemStack plan = WonderPlanItem.of(v.level(), v.hall(), folly, Component.literal("Drawholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(v.level().dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		BlueprintData.Placement where = BlueprintItem.data(plan).flatMap(BlueprintData::placement).orElseThrow();
		if (stocked) {
			WonderGoodsKit.stockAll(v.level(), FOLLY, where); // every stage's goods in the stockpile
		}
		helper.assertTrue(WonderStages.handOver(v.owner(), v.builder(), plan) == InteractionResult.SUCCESS, "the plan wasn't handed over");
		WonderSite site = WonderManager.get(v.level()).in(v.hall()).get(0);
		UUID id = site.id();
		Leftovers.after(helper, () -> forget(v.level(), id));
		return site;
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		WonderManager.get(level).remove(wonder);
	}

	/**
	 * Puts up the Wonder {@code id}'s stages, each part's blocks where its build site's plan has them, its site done and
	 * gone as a builder's finished part is, and moves the Wonder on, for at most {@code stages} stages (or until it's finished).
	 */
	private static WonderSite raise(ServerLevel level, UUID id, int stages) {
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		int built = 0;
		for (int round = 0; round < 12 && built < stages && manager.site(id).map(WonderSite::goingUp).orElse(false); round++) {
			boolean any = false;
			for (UUID partId : manager.site(id).orElseThrow().parts()) {
				BuildSite part = sites.get(partId);
				if (part == null || part.isDone()) {
					continue;
				}
				BuildPlan plan = part.plan(level);
				for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
					for (BuildPlan.Step s : plan.steps(stage)) {
						level.setBlock(s.pos(), s.state(), 2 | 16);
					}
				}
				for (int i = 0; i < 20 && !part.isDone(); i++) {
					part.finishList();
				}
				sites.remove(partId);
				any = true;
			}
			if (any) {
				built++;
			}
			WonderStages.tick(level);
		}
		return manager.site(id).orElseThrow();
	}

	/** The blocks the site's part build sites would put up (their plans' structure and decoration). */
	private static Set<Block> partBlocks(ServerLevel level, WonderSite site) {
		Set<Block> out = new HashSet<>();
		for (UUID partId : site.parts()) {
			BuildSite part = BuildSiteManager.get(level).get(partId);
			if (part == null) {
				continue;
			}
			BuildPlan plan = part.plan(level);
			for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
				for (BuildPlan.Step s : plan.steps(stage)) {
					if (!s.state().isAir()) {
						out.add(s.state().getBlock());
					}
				}
			}
		}
		return out;
	}

	private static Set<Block> kinds(List<WonderUpkeep.Block> blocks) {
		Set<Block> out = new HashSet<>();
		for (WonderUpkeep.Block b : blocks) {
			out.add(b.state().getBlock());
		}
		return out;
	}

	// ---- the tests ---------------------------------------------------------------------------------------------------

	/**
	 * A Wonder begun with the older drawing (waiting for its first goods when the "redraw" came) is raised from that
	 * drawing's two stages, its parts named as that drawing's; finished, it reads as standing whole, its powers on, with
	 * nothing to repair. Broken, its repairs are of the older drawing's parts.
	 */
	//$ gametest_ticks_batch TALL '2000' '"wonderDrawingsFinishedOlder"'
	@GameTest(template = TALL, timeoutTicks = 2000, batch = "wonderDrawingsFinishedOlder")
	public void aFinishedWonderOfAnOlderDrawingStandsWholeAndIsRepairedByIt(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		addOlderDrawing(helper);
		WonderSite begun = begin(helper, v, false);
		UUID id = begun.id();
		WonderManager manager = WonderManager.get(level);
		helper.assertTrue(begun.parts().isEmpty(), "stage 1 started without its goods");
		manager.setDrawing(id, OLDER); // begun before the redraw
		WonderGoodsKit.stockAll(level, FOLLY, begun.placement().orElseThrow());
		WonderStages.tick(level);
		WonderSite first = manager.site(id).orElseThrow();
		helper.assertTrue(!first.parts().isEmpty(), "stage 1 didn't start with its goods in");
		for (UUID partId : first.parts()) {
			BuildSite part = BuildSiteManager.get(level).get(partId);
			helper.assertTrue(part != null && WonderDrawings.parse(BlueprintStyles.base(part.structure())).map(o -> o.part().equals(PART_1)).orElse(false),
				"stage 1's part is " + (part == null ? null : part.structure()));
		}
		WonderSite done = raise(level, id, 99);
		helper.assertTrue(done.done(), "the folly wasn't finished from the older drawing's stages: " + done);
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, done);
		helper.assertTrue(blocks.size() == 9 * 9 + 8 + 25 && OLDER_BLOCKS.containsAll(kinds(blocks)), "counted against today's drawing: "
			+ blocks.size() + " blocks of " + kinds(blocks));
		helper.assertTrue(level.getBlockState(helper.absolutePos(MIDDLE)).is(Blocks.LAPIS_BLOCK)
			&& level.getBlockState(helper.absolutePos(MIDDLE.offset(0, 3, 0))).is(Blocks.GOLD_BLOCK), "not built from the older drawing");
		helper.assertTrue(WonderUpkeep.countNow(level, id) == 100, "the older folly doesn't stand whole: " + manager.site(id).orElseThrow().kept());
		helper.assertTrue(manager.site(id).orElseThrow().empowered() && Wonders.power(level, v.hall(), MoodPower.TYPE).isPresent(), "its powers are off");
		helper.assertTrue(WonderUpkeep.repair(level, v.builder(), helper.absolutePos(BENCH)).isEmpty(), "a repair of a Wonder standing whole");

		// broken: its builders repair the older drawing's parts, not today's
		List<WonderUpkeep.Block> pick = new ArrayList<>(blocks);
		net.minecraft.Util.shuffle(pick, RandomSource.create(5206L));
		for (WonderUpkeep.Block b : pick.subList(0, 30)) {
			level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		int left = WonderUpkeep.countNow(level, id);
		helper.assertTrue(left < WonderUpkeep.STANDING && manager.site(id).orElseThrow().kept().damaged(), "30 blocks broken: " + left + "%");
		List<BuildSite> repairs = WonderUpkeep.repair(level, v.builder(), helper.absolutePos(BENCH));
		helper.assertTrue(!repairs.isEmpty(), "no repair of the broken older folly");
		for (BuildSite r : repairs) {
			Optional<WonderDrawings.Older> older = WonderDrawings.parse(BlueprintStyles.base(r.structure()));
			helper.assertTrue(older.isPresent() && older.get().key().equals(OLDER), "a repair of today's part: " + r.structure());
			Set<Block> placing = new HashSet<>();
			for (BuildPlan.Step s : r.plan(level).steps(BuildPlan.Stage.STRUCTURE)) {
				placing.add(s.state().getBlock());
			}
			helper.assertTrue(!placing.isEmpty() && OLDER_BLOCKS.containsAll(placing), "the repair puts up " + placing);
		}
		helper.succeed();
	}

	/**
	 * A Wonder begun with the older drawing, its stage 1 under way when the "redraw" came (its part sites made then, named
	 * by today's ids): that stage is built from the older drawing all the same, and its next stage is the older drawing's
	 * second (the gold top at (2, 3, 2)), its parts named as that drawing's, of two stages, not today's three.
	 */
	//$ gametest_ticks_batch TALL '2000' '"wonderDrawingsHalfBuilt"'
	@GameTest(template = TALL, timeoutTicks = 2000, batch = "wonderDrawingsHalfBuilt")
	public void aHalfBuiltWonderOfAnOlderDrawingTakesItsNextStageFromIt(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		addOlderDrawing(helper);
		WonderSite begun = begin(helper, v);
		UUID id = begun.id();
		WonderManager manager = WonderManager.get(level);
		helper.assertTrue(!begun.parts().isEmpty() && begun.parts().stream().map(p -> BuildSiteManager.get(level).get(p))
			.allMatch(p -> p != null && WonderDrawings.parse(BlueprintStyles.base(p.structure())).isEmpty()), "stage 1 wasn't under way by today's ids");
		manager.setDrawing(id, OLDER); // begun before the redraw
		raise(level, id, 1);
		WonderSite site = manager.site(id).orElseThrow();
		for (int i = 0; i < 10 && site.parts().isEmpty(); i++) {
			WonderStages.tick(level); // (stage 2's goods are in: dedicated, then its parts set out)
			site = manager.site(id).orElseThrow();
		}
		helper.assertTrue(site.goingUp() && site.stage() == 2 && !site.parts().isEmpty(), "stage 2 didn't start: " + site);
		helper.assertTrue(level.getBlockState(helper.absolutePos(MIDDLE)).is(Blocks.LAPIS_BLOCK), "stage 1 wasn't the older drawing's");
		Wonder drawn = WonderDrawings.of(level, site);
		helper.assertTrue(drawn != null && drawn.stages().size() == 2, "the Wonder it goes by: " + (drawn == null ? null : drawn.stages().size()) + " stages");
		for (UUID partId : site.parts()) {
			BuildSite part = BuildSiteManager.get(level).get(partId);
			helper.assertTrue(part != null, "a part site is gone");
			Optional<WonderDrawings.Older> older = WonderDrawings.parse(BlueprintStyles.base(part.structure()));
			helper.assertTrue(older.isPresent() && older.get().key().equals(OLDER) && older.get().part().equals(PART_2),
				"stage 2's part is " + part.structure());
			helper.assertTrue(part.placement().origin().equals(site.placement().orElseThrow().origin().offset(2, 3, 2)), "stage 2's part at " + part.placement().origin());
		}
		Set<Block> next = partBlocks(level, site);
		helper.assertTrue(next.equals(Set.of(Blocks.GOLD_BLOCK)), "stage 2 puts up " + next + ", not the older drawing's gold top");
		helper.succeed();
	}

	/**
	 * A Wonder begun now takes today's drawing (recorded on it), with today's parts; finished, it stands whole by today's
	 * drawing. Its record forgotten (a save from before the record), it is matched back to today's drawing by best fit.
	 */
	//$ gametest_ticks_batch TALL '2000' '"wonderDrawingsFresh"'
	@GameTest(template = TALL, timeoutTicks = 2000, batch = "wonderDrawingsFresh")
	public void aFreshWonderTakesTodaysDrawingAndAnUnrecordedOneIsMatchedToIt(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		addOlderDrawing(helper);
		WonderSite begun = begin(helper, v);
		UUID id = begun.id();
		WonderManager manager = WonderManager.get(level);
		helper.assertTrue(begun.drawing().equals(Optional.of(Wonders.drawing(FOLLY))), "the drawing it was begun with: " + begun.drawing());
		helper.assertTrue(WonderDrawings.of(level, begun) == Wonders.loaded().get(FOLLY), "a fresh Wonder goes by an older drawing");
		WonderSite done = raise(level, id, 99);
		helper.assertTrue(done.done() && done.stage() == 3, "the folly wasn't finished in today's three stages: " + done);
		List<WonderUpkeep.Block> blocks = WonderUpkeep.blocks(level, done);
		helper.assertTrue(blocks.size() > 100 && kinds(blocks).contains(Blocks.STONE_BRICKS) && !kinds(blocks).contains(Blocks.LAPIS_BLOCK),
			"today's folly counted as " + kinds(blocks));
		helper.assertTrue(WonderUpkeep.countNow(level, id) == 100, "today's folly doesn't stand whole");

		manager.setDrawing(id, null); // as a save from before the record has it
		helper.assertTrue(manager.site(id).orElseThrow().drawing().isEmpty(), "the record wasn't forgotten");
		Wonder matched = WonderDrawings.of(level, manager.site(id).orElseThrow());
		helper.assertTrue(matched == Wonders.loaded().get(FOLLY), "today's folly matched to an older drawing");
		helper.assertTrue(manager.site(id).orElseThrow().drawing().equals(Optional.of(Wonders.drawing(FOLLY))),
			"the match wasn't recorded: " + manager.site(id).orElseThrow().drawing());
		helper.succeed();
	}

	/**
	 * A Wonder standing as the older drawing has it, from a save made before the record (none on it): matched by best fit
	 * to the older drawing, recorded, and counted standing whole; its powers stay on.
	 */
	//$ gametest_ticks_batch TALL '2000' '"wonderDrawingsMatched"'
	@GameTest(template = TALL, timeoutTicks = 2000, batch = "wonderDrawingsMatched")
	public void anUnrecordedWonderOfAnOlderDrawingIsMatchedByBestFit(GameTestHelper helper) {
		Village v = village(helper);
		ServerLevel level = v.level();
		addOlderDrawing(helper);
		WonderSite begun = begin(helper, v);
		UUID id = begun.id();
		WonderManager manager = WonderManager.get(level);
		manager.setDrawing(id, OLDER);
		helper.assertTrue(raise(level, id, 99).done(), "the older folly wasn't finished");
		manager.setDrawing(id, null); // as a save from before the record has it
		WonderSite unrecorded = manager.site(id).orElseThrow();
		helper.assertTrue(unrecorded.drawing().isEmpty(), "the record wasn't forgotten");
		Wonder matched = WonderDrawings.of(level, unrecorded);
		helper.assertTrue(matched != null && matched != Wonders.loaded().get(FOLLY) && matched.stages().size() == 2, "not matched to the older drawing");
		helper.assertTrue(manager.site(id).orElseThrow().drawing().equals(Optional.of(OLDER)), "the match: " + manager.site(id).orElseThrow().drawing());
		helper.assertTrue(WonderUpkeep.countNow(level, id) == 100 && manager.site(id).orElseThrow().empowered(),
			"the matched folly: " + manager.site(id).orElseThrow().kept());
		helper.succeed();
	}

	/**
	 * The history holds today's drawing of every Wonder the mod ships (loaded in this run): its file names a key, the
	 * history has that key's stages with today's parts and offsets, and each part's blocks as today's part has them. So
	 * the Wonders standing today keep their drawing when the next one ships.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"wonderDrawingsShipped"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wonderDrawingsShipped")
	public void everyShippedWondersDrawingOfTodayIsInTheHistory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Wonder wonder : Wonders.loaded().values()) {
			if (!wonder.id().getNamespace().equals("aliveworkplace")) {
				continue;
			}
			checked++;
			String key = Wonders.drawing(wonder.id());
			Optional<List<Wonder.Stage>> stages = WonderDrawings.stages(level.getServer(), wonder.id(), key);
			if (key.isEmpty() || stages.isEmpty()) {
				wrong.add(wonder.id() + ": no drawing '" + key + "' in the history");
				continue;
			}
			List<Wonder.Part> today = wonder.stages().stream().flatMap(s -> s.parts().stream()).toList();
			List<Wonder.Part> kept = stages.get().stream().flatMap(s -> s.parts().stream()).toList();
			if (!today.equals(kept) || stages.get().size() != wonder.stages().size()) {
				wrong.add(wonder.id() + ": the history's " + key + " has other parts or offsets than today's");
				continue;
			}
			for (Wonder.Part part : today) {
				Optional<Blueprint> now = BlueprintLibrary.get(level, part.part());
				Optional<Blueprint> old = BlueprintLibrary.get(level, WonderDrawings.olderId(part.part(), key));
				if (now.isEmpty() || old.isEmpty() || !now.get().size().equals(old.get().size())
					|| !new HashSet<>(now.get().blocks()).equals(new HashSet<>(old.get().blocks()))) {
					wrong.add(part.part() + "@" + key + ": not today's part");
				}
			}
		}
		helper.assertTrue(checked >= 7, "only " + checked + " of the mod's Wonders loaded");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " Wonders' drawings of today aren't kept: " + wrong
			+ " (run python3 tools/blueprints/wonder_history.py)");
		helper.succeed();
	}

	/**
	 * 52.6 shipped the rebuilt Observatory: one begun with the 35.17 drawing (the key its file named until then) is still
	 * counted, repaired and continued by that drawing, read from the shipped history. Finished, it is counted against
	 * the old drawing's blocks (8,433 counted), not today's (8,501); half-built at stage 2, its next stage is the
	 * old stage 2 part at its old offset, its blocks as the old part had them; a Wonder recorded with today's key goes
	 * by today's drawing.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"wonderDrawingsObservatory"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wonderDrawingsObservatory")
	public void anObservatoryOfThe3517DrawingKeepsItsOldStages(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation observatory = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "observatory");
		String old = "drawn_863ce8da8"; // the 35.17 drawing, the one standing on servers before 52.6
		Wonder today = Wonders.loaded().get(observatory);
		helper.assertTrue(today != null, "the Observatory isn't loaded: " + Wonders.problems());
		helper.assertTrue(!Wonders.drawing(observatory).equals(old), "the Observatory's file still names the 35.17 drawing");
		helper.assertTrue(WonderDrawings.olderKeys(level.getServer(), observatory).contains(old), "the 35.17 drawing isn't in the history: "
			+ WonderDrawings.olderKeys(level.getServer(), observatory));
		List<Wonder.Stage> oldStages = WonderDrawings.stages(level.getServer(), observatory, old).orElseThrow();
		List<BlockPos> offsets = oldStages.stream().map(st -> st.parts().get(0).offset()).toList();
		helper.assertTrue(oldStages.size() == 4 && offsets.equals(List.of(BlockPos.ZERO, new BlockPos(7, 4, 12), new BlockPos(10, 34, 15), new BlockPos(14, 4, 4))),
			"the 35.17 drawing's stages: " + oldStages.size() + " at " + offsets);

		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE);
		WonderSite finishedOld = new WonderSite(UUID.randomUUID(), observatory, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 4, 1L,
			Optional.of(2L), Optional.of(at), Optional.empty(), List.of(), Optional.empty(), false, true, List.of(), WonderSite.Kept.WHOLE, Optional.of(old));
		WonderSite finishedNew = new WonderSite(UUID.randomUUID(), observatory, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 4, 1L,
			Optional.of(2L), Optional.of(at), Optional.empty(), List.of(), Optional.empty(), false, true, List.of(), WonderSite.Kept.WHOLE,
			Optional.of(Wonders.drawing(observatory)));
		helper.assertTrue(WonderDrawings.older(level, finishedOld) && !WonderDrawings.older(level, finishedNew), "which drawing each goes by");
		int oldCount = WonderUpkeep.blocks(level, finishedOld).size();
		int newCount = WonderUpkeep.blocks(level, finishedNew).size();
		// the counts WonderUpkeep keeps (structure and decoration steps; a few of each drawing's 8,438 and 8,504 blocks are
		// the plan's foundation or landscape steps, which the count leaves out for every Wonder)
		helper.assertTrue(oldCount == 8433 && newCount == 8501, "an old Observatory is counted against " + oldCount + " blocks (the 35.17 drawing: 8433), "
			+ "today's against " + newCount + " (8501)");
		for (WonderUpkeep.Part part : WonderUpkeep.parts(level, finishedOld)) {
			Optional<WonderDrawings.Older> o = WonderDrawings.parse(BlueprintStyles.base(part.structure()));
			helper.assertTrue(o.isPresent() && o.get().key().equals(old), "an old Observatory's part (repaired by it) is today's: " + part.structure());
		}

		WonderSite halfOld = new WonderSite(UUID.randomUUID(), observatory, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 2, 1L,
			Optional.empty(), Optional.of(at), Optional.empty(), List.of(), Optional.empty(), false, false, List.of(), WonderSite.Kept.WHOLE, Optional.of(old));
		Wonder.Part next = WonderDrawings.of(level, halfOld).stages().get(1).parts().get(0);
		ResourceLocation todaysPart = today.stages().get(1).parts().get(0).part();
		helper.assertTrue(next.part().equals(WonderDrawings.olderId(todaysPart, old)) && next.offset().equals(new BlockPos(7, 4, 12)),
			"a half-built old Observatory's stage 2 is " + next);
		Optional<Blueprint> oldPart = BlueprintLibrary.get(level, next.part());
		Optional<Blueprint> newPart = BlueprintLibrary.get(level, todaysPart);
		helper.assertTrue(oldPart.isPresent() && !oldPart.get().blocks().isEmpty() && newPart.isPresent()
			&& !new HashSet<>(oldPart.get().blocks()).equals(new HashSet<>(newPart.get().blocks())), "the old stage 2 part didn't load as the old one");
		helper.succeed();
	}

	/**
	 * The record is saved: a site and the whole manager written and read back keep it; a site saved before it reads with
	 * none (and everything else as it was); a record naming a drawing that isn't shipped goes by today's; an older
	 * drawing's part is read from the history under its own id, and today's parts as before.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"wonderDrawingsSaved"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wonderDrawingsSaved")
	public void theDrawingRecordSurvivesSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		addOlderDrawing(helper);
		BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE);
		WonderSite site = new WonderSite(UUID.randomUUID(), FOLLY, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 2, 3L, Optional.empty(),
			Optional.of(at), Optional.empty(), List.of(), Optional.empty(), false, true, List.of(), new WonderSite.Kept(80, 4L, true, 0L), Optional.of(OLDER));
		Tag saved = WonderSite.CODEC.encodeStart(NbtOps.INSTANCE, site).getOrThrow();
		WonderSite read = WonderSite.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(read.equals(site), "read back as " + read);
		CompoundTag before = ((CompoundTag) saved).copy();
		before.remove("drawing");
		WonderSite old = WonderSite.CODEC.parse(NbtOps.INSTANCE, before).getOrThrow();
		helper.assertTrue(old.drawing().isEmpty() && old.kept().equals(site.kept()) && old.stage() == 2 && old.dedicated(), "a save from before: " + old);

		// the whole manager, as the world saves it
		WonderManager manager = WonderManager.get(level);
		WonderSite recorded = manager.begin(FOLLY, helper.absolutePos(BlockPos.ZERO), null, "Owner", 1L);
		Leftovers.after(helper, () -> manager.remove(recorded.id()));
		helper.assertTrue(recorded.drawing().equals(Optional.of(Wonders.drawing(FOLLY))), "begun without today's drawing: " + recorded.drawing());
		manager.setDrawing(recorded.id(), OLDER);
		WonderManager reloaded = WonderManager.load(manager.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(reloaded.site(recorded.id()).map(WonderSite::drawing).equals(Optional.of(Optional.of(OLDER))),
			"after a reload: " + reloaded.site(recorded.id()).map(WonderSite::drawing));

		// a drawing that isn't shipped: today's stands in
		WonderSite gone = new WonderSite(UUID.randomUUID(), FOLLY, helper.absolutePos(BlockPos.ZERO), Optional.empty(), "Owner", 1, 1L, Optional.empty(),
			Optional.of(at), Optional.empty(), List.of(), Optional.empty(), false, false, List.of(), WonderSite.Kept.WHOLE, Optional.of("drawn_not_shipped"));
		helper.assertTrue(WonderDrawings.of(level, gone) == Wonders.loaded().get(FOLLY), "a drawing that isn't shipped wasn't today's");
		helper.assertTrue(WonderDrawings.of(level, site).stages().get(0).parts().get(0).part().equals(WonderDrawings.olderId(PART_1, OLDER)),
			"the older drawing's part id");

		// the older drawing's parts read from the history; today's as drawn
		Optional<Blueprint> olderPart = BlueprintLibrary.get(level, WonderDrawings.olderId(PART_1, OLDER));
		helper.assertTrue(olderPart.isPresent() && olderPart.get().blocks().size() == 9 * 9 + 8
			&& olderPart.get().id().equals(WonderDrawings.olderId(PART_1, OLDER)), "the older part read as " + olderPart.map(b -> b.blocks().size()));
		helper.assertTrue(BlueprintLibrary.get(level, PART_1).map(b -> b.size().getY()).orElse(0) == 19, "today's stage 1 part");
		helper.assertTrue(WonderDrawings.parse(PART_1).isEmpty() && BlueprintLibrary.get(level, WonderDrawings.olderId(PART_1, "drawn_nowhere")).isEmpty(),
			"an id that isn't an older drawing's part");
		helper.succeed();
	}
}
