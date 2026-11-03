package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (lane-b-1103-1839) for furnished builds without any furniture mod (ROADMAP 37.2-37.4), written from the spec. The
 * test pack's {@code aliveworkplace_test_pieces} stands in for a mod (its chair an upturned stair of the spot's wood, its
 * table a block of planks):
 * <ul>
 * <li>every build with furniture spots, as drawn and in every style it is offered in: the Blueprint's material list
 * (the shopping list) is exactly what its builder's plan uses, furniture pieces included, and a styled build's pieces
 * take the style's wood;</li>
 * <li>the choice saved on the site is kept through a save and reload at every stage, with the order emptied or the
 * switch off in between;</li>
 * <li>a nook begun with {@code furnishedBuilds} off, then switched on with a restart mid-build, finishes vanilla.</li>
 * </ul>
 */
public class QaFurnished1103fGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	static final String PIECES = "aliveworkplace_test_pieces";

	private static Map<String, Integer> keyed(Map<Item, Integer> materials) {
		Map<String, Integer> out = new TreeMap<>();
		materials.forEach((item, n) -> out.merge(BuiltInRegistries.ITEM.getKey(MaterialFamilies.key(item)).toString(), n, Integer::sum));
		return out;
	}

	/**
	 * Every build with furniture spots, as drawn and in every style offered for it, with the test mod first: the
	 * unplaced Blueprint's material list (what a player reads to know what to bring) holds exactly what the builder's
	 * own plan for that build uses, item for item, and the site took the test mod's pieces. In the Dark Oak style the
	 * cottage's chair is a dark oak chair (an upturned dark oak stair), and its cost is listed.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '400' '"qaFurnishedStyles1103f"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "qaFurnishedStyles1103f")
	public void everyFurnishedBuildInEveryStyleListsWhatItsBuilderPlans(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int checked = 0;
		int styled = 0;
		int furnished = 0;
		try {
			Furnishings.MODS = List.of(PIECES);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			for (ResourceLocation base : BlueprintLibrary.list(level.getServer(), false)) {
				if (BlueprintFurniture.of(base).isEmpty()) {
					continue;
				}
				List<ResourceLocation> ids = new ArrayList<>();
				ids.add(base);
				for (BlueprintStyles.Style style : BlueprintStyles.all()) {
					if (style.isFor(base) && style.offered()) {
						ids.add(BlueprintStyles.styled(base, style.name()));
					}
				}
				for (ResourceLocation id : ids) {
					Blueprint drawn = BlueprintLibrary.get(level, id).orElse(null);
					if (drawn == null) {
						continue; // a style that leaves this build as it is isn't offered for it
					}
					BlueprintData data = BlueprintItem.data(BlueprintItem.create(id, drawn.size())).orElseThrow();
					var list = BlueprintSupplies.checklist(level, data);
					if (list.isEmpty()) {
						continue; // too big for a list: the item says so
					}
					checked++;
					if (!id.equals(base)) {
						styled++;
					}
					Map<String, Integer> listed = new TreeMap<>();
					list.get().lines().forEach(l -> listed.merge(BuiltInRegistries.ITEM.getKey(l.item()).toString(), l.need(), Integer::sum));
					BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", id, placement);
					if (site.plan(level) == null) {
						problems.add(id + ": no plan");
						continue;
					}
					FurnitureChoice chosen = site.furniture();
					// The test mod only has a table, a chair and a three-long bed: a build without a table or chair spot stays vanilla
					boolean hasTableOrChair = BlueprintFurniture.of(id).stream().anyMatch(s -> s.kind() == FurnitureSpot.Kind.TABLE || s.kind() == FurnitureSpot.Kind.CHAIR);
					if ((hasTableOrChair && chosen.isVanilla()) || !chosen.spots().values().stream().allMatch(p -> p.mod().equals(PIECES))) {
						problems.add(id + ": the site chose " + chosen.spots().size() + " pieces, not the test mod's");
					}
					if (!chosen.isVanilla()) {
						furnished++;
					}
					// The builder's plan without the site's terrain work: the furnished blueprint as the site builds it
					Map<String, Integer> planned = keyed(BuildPlan.create(chosen.apply(drawn, id), new BlueprintData.Placement(level.dimension().location(),
						helper.absolutePos(BlockPos.ZERO), Rotation.NONE, Mirror.NONE)).materials());
					if (!listed.equals(planned)) {
						problems.add(id + ": the list " + diff(listed, planned));
					}
				}
			}
			// The Dark Oak cottage: its chair spot is a dark oak chair, built as an upturned dark oak stair and listed
			ResourceLocation darkOak = BlueprintStyles.styled(COTTAGE, "dark_oak");
			FurnitureSpot chair = BlueprintFurniture.of(darkOak).stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).findFirst().orElseThrow();
			BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", darkOak, placement);
			BuildPlan plan = site.plan(level);
			BlockState at = null;
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (var step : plan.steps(stage)) {
					if (step.pos().equals(placement.origin().offset(chair.pos()))) {
						at = step.state();
					}
				}
			}
			helper.assertTrue(at != null && at.is(Blocks.DARK_OAK_STAIRS) && at.getValue(StairBlock.HALF) == Half.TOP,
				"the Dark Oak cottage's chair is " + at + ", wanted the test mod's upturned dark oak stair");
			BlueprintData data = BlueprintItem.data(BlueprintItem.create(darkOak, null)).orElseThrow();
			helper.assertTrue(BlueprintSupplies.checklist(level, data).orElseThrow().lines().stream().anyMatch(l -> l.item() == Items.DARK_OAK_STAIRS),
				"the Dark Oak cottage's list doesn't ask for dark oak stairs");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(checked >= 10 && styled >= 10 && furnished >= 10, "only " + checked + " lists checked, " + styled + " styled, " + furnished + " with pieces");
		helper.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		org.slf4j.LoggerFactory.getLogger("QaFurnished1103f").info("{} lists checked, {} of them styled, {} with the test pieces", checked, styled, furnished);
		helper.succeed();
	}

	/**
	 * The Starter Cottage with the test mod first, placed in each of the four turns, plain and mirrored both ways: every
	 * plan puts the test mod's table (spruce planks) on the table spot as the turned build has it and its chair (an
	 * upturned spruce stair) on the chair spot facing the spot's turned way, and costs exactly what the unturned plan
	 * costs.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedTurns1103f"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedTurns1103f")
	public void everyTurnAndMirrorOfTheCottageGetsItsPiecesOnTheirSpots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		try {
			Furnishings.MODS = List.of(PIECES);
			Blueprint drawn = BlueprintLibrary.get(level, COTTAGE).orElseThrow();
			FurnitureSpot table = BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == FurnitureSpot.Kind.TABLE).findFirst().orElseThrow();
			FurnitureSpot chair = BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).findFirst().orElseThrow();
			Map<Item, Integer> straight = null;
			for (Mirror mirror : Mirror.values()) {
				for (Rotation rotation : Rotation.values()) {
					BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), drawn.size(),
						helper.absolutePos(new BlockPos(1, 2, 1)), rotation, mirror);
					BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
					BuildPlan plan = site.plan(level);
					String turn = rotation + "/" + mirror;
					if (plan == null) {
						problems.add(turn + ": no plan");
						continue;
					}
					Map<BlockPos, BlockState> steps = QaFurnishedGameTests.stepStates(plan);
					BlockPos tableAt = placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
						table.pos(), mirror, rotation, BlockPos.ZERO));
					BlockPos chairAt = placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
						chair.pos(), mirror, rotation, BlockPos.ZERO));
					net.minecraft.core.Direction facing = rotation.rotate(mirror.mirror(chair.facing().getOpposite()));
					BlockState atTable = steps.get(tableAt);
					BlockState atChair = steps.get(chairAt);
					if (atTable == null || !atTable.is(Blocks.SPRUCE_PLANKS)) {
						problems.add(turn + ": the table spot has " + atTable);
					}
					if (atChair == null || !atChair.is(Blocks.SPRUCE_STAIRS) || atChair.getValue(StairBlock.HALF) != Half.TOP
						|| atChair.getValue(StairBlock.FACING) != facing) {
						problems.add(turn + ": the chair spot has " + atChair + ", wanted an upturned spruce stair facing " + facing);
					}
					Map<Item, Integer> cost = BuildPlan.create(site.furniture().apply(drawn, COTTAGE), placement).materials();
					if (straight == null) {
						straight = cost;
					} else if (!cost.equals(straight)) {
						problems.add(turn + ": costs " + diff(keyed(cost), keyed(straight)));
					}
				}
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A cottage site planned with the test mod first is saved and loaded at every stage from CLEAR to LANDSCAPE; between
	 * the save and the load the order loses the mod, or the switch is turned off. Each time the loaded site plans the
	 * test mod's table (a block of spruce planks) and chair (an upturned spruce stair), and keeps its choice.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaFurnishedStages1103f"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaFurnishedStages1103f")
	public void theChoiceIsKeptThroughASaveAndReloadAtEveryStage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int reloads = 0;
		try {
			Furnishings.MODS = List.of(PIECES);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
			site.plan(level);
			FurnitureChoice chosen = site.furniture();
			helper.assertTrue(!chosen.isVanilla(), "the cottage didn't take the test mod's pieces: " + chosen);
			FurnitureSpot table = BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == FurnitureSpot.Kind.TABLE).findFirst().orElseThrow();
			FurnitureSpot chair = BlueprintFurniture.of(COTTAGE).stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).findFirst().orElseThrow();
			boolean switchOff = false;
			while (site.stage() != BuildPlan.Stage.DONE && reloads < 12) {
				BuildPlan.Stage stage = site.stage();
				site.advance();
				CompoundTag saved = site.save();
				Furnishings.MODS = List.of(PIECES);
				Furnishings.ENABLED = true;
				if (switchOff) {
					WorkplaceConfig.parse("{\"furnishedBuilds\": false}").apply();
				} else {
					Furnishings.MODS = List.of();
				}
				BuildSite loaded = BuildSite.load(saved);
				reloads++;
				if (loaded == null) {
					problems.add(stage + ": the site didn't load");
					break;
				}
				BuildPlan plan = loaded.plan(level);
				if (plan == null) {
					problems.add(stage + ": no plan after the reload");
				} else {
					Map<BlockPos, BlockState> steps = new java.util.HashMap<>();
					for (BuildPlan.Stage s : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
						plan.steps(s).forEach(st -> steps.put(st.pos(), st.state()));
					}
					BlockState atTable = steps.get(placement.origin().offset(table.pos()));
					BlockState atChair = steps.get(placement.origin().offset(chair.pos()));
					if (atTable == null || !atTable.is(Blocks.SPRUCE_PLANKS)) {
						problems.add(stage + (switchOff ? " (switch off)" : " (order emptied)") + ": the table is " + atTable);
					}
					if (atChair == null || !atChair.is(Blocks.SPRUCE_STAIRS) || atChair.getValue(StairBlock.HALF) != Half.TOP) {
						problems.add(stage + (switchOff ? " (switch off)" : " (order emptied)") + ": the chair is " + atChair);
					}
				}
				if (!loaded.furniture().spots().equals(chosen.spots())) {
					problems.add(stage + ": the loaded site's choice is " + loaded.furniture());
				}
				if (loaded.stage() != stage) {
					problems.add(stage + ": loaded at stage " + loaded.stage());
				}
				site = loaded;
				site.finishList(); // nothing deferred: on to the next stage
				switchOff = !switchOff;
				new WorkplaceConfig().apply();
				Furnishings.MODS = List.of(PIECES);
			}
			helper.assertTrue(site.stage() == BuildPlan.Stage.DONE, "never reached DONE: " + site.stage());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(reloads >= 5, "only " + reloads + " stages reloaded");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The Starter Cottage in the Dark Oak style with the test mod first, built from barrels holding exactly what its plan
	 * costs: it finishes, every block right and nothing skipped; its table is the test mod's (a block of dark oak planks)
	 * and its chair the test mod's (an upturned dark oak stair); and not one of the plan's items is left in the barrels or
	 * the builder's hands (no block came free, none was paid twice).
	 */
	//$ gametest_ticks_batch 'aliveworkplace_test:huge_area' '20000' '"qaFurnishedDarkOakBuild1103f"'
	@GameTest(template = aliveworkplace_test:huge_area, timeoutTicks = 20000, batch = "qaFurnishedDarkOakBuild1103f")
	public void aDarkOakCottageWithThePiecesIsBuiltFromExactlyItsMaterials(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		Furnishings.MODS = List.of(PIECES);
		helper.setDayTime(2000);
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, io.github.jcondedata.aliveworkplace.registry.ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ResourceLocation id = BlueprintStyles.styled(COTTAGE, "dark_oak");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && !site.furniture().isVanilla(), "the Dark Oak cottage took no pieces: " + site.furniture());
		Map<Item, Integer> cost = plan.materials();
		List<ItemStack> stock = new ArrayList<>();
		cost.forEach((item, n) -> {
			for (int left = n; left > 0; left -= item.getDefaultMaxStackSize()) {
				stock.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		List<BlockPos> barrels = new ArrayList<>();
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			barrels.add(barrel);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		FurnitureSpot table = BlueprintFurniture.of(id).stream().filter(s -> s.kind() == FurnitureSpot.Kind.TABLE).findFirst().orElseThrow();
		FurnitureSpot chair = BlueprintFurniture.of(id).stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).findFirst().orElseThrow();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			BlockState atTable = level.getBlockState(placement.origin().offset(table.pos()));
			BlockState atChair = level.getBlockState(placement.origin().offset(chair.pos()));
			helper.assertTrue(atTable.is(Blocks.DARK_OAK_PLANKS), "the table is " + atTable);
			helper.assertTrue(atChair.is(Blocks.DARK_OAK_STAIRS) && atChair.getValue(StairBlock.HALF) == Half.TOP, "the chair is " + atChair);
			Map<Item, Integer> left = new TreeMap<>(java.util.Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
			for (BlockPos b : barrels) {
				Container c = helper.getBlockEntity(b);
				for (int s = 0; s < c.getContainerSize(); s++) {
					ItemStack st = c.getItem(s);
					if (!st.isEmpty() && cost.containsKey(st.getItem())) {
						left.merge(st.getItem(), st.getCount(), Integer::sum);
					}
				}
			}
			builder.getInventory().getItems().forEach(st -> {
				if (!st.isEmpty() && cost.containsKey(st.getItem())) {
					left.merge(st.getItem(), st.getCount(), Integer::sum);
				}
			});
			helper.assertTrue(left.isEmpty(), "left over after the build of exactly its materials (paid less than built?): " + left);
			org.slf4j.LoggerFactory.getLogger("QaFurnished1103f").info("Dark Oak cottage with pieces built by tick {}: {} kinds", helper.getTick(), cost.size());
		});
	}

	/**
	 * After the nook is built with the test mod's chair, a player swaps the chair for a crafting table of his own and a
	 * creeper takes a floor block. Through a save and reload of the sites the builder's repair fills the hole and nothing
	 * else: the crafting table the player put on the chair spot is never planned over, and still stands after the repair
	 * is done (Upkeep: "nothing players put there is touched").
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedPlayerSwap1103f"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedPlayerSwap1103f")
	public void aPlayersBlockOnAChairSpotIsLeftByTheRepair(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of(PIECES);
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && !site.furniture().isVanilla(), "the nook took no piece: " + site.furniture());
		QaFurnishedGameTests.stock(helper, plan);
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		BlockPos hole = placement.origin().offset(2, 0, 0);
		BuildSite[] repair = {null};
		String[] phase = {"building"};
		helper.onEachTick(() -> {
			if (!phase[0].equals("building") || BuildSiteManager.get(level).get(site.id()) != null) {
				return;
			}
			BlockState built = level.getBlockState(chairAt);
			if (!built.is(Blocks.SPRUCE_STAIRS) || built.getValue(StairBlock.HALF) != Half.TOP) {
				phase[0] = "the nook was finished with " + built + " on the chair spot, not the test mod's chair";
				return;
			}
			BlockState floor = level.getBlockState(hole);
			level.setBlockAndUpdate(chairAt, Blocks.CRAFTING_TABLE.defaultBlockState());
			level.setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());
			((Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(21, new ItemStack(floor.getBlock().asItem(), 2));
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
			io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = false;
			repair[0] = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
			if (repair[0] == null) {
				phase[0] = "no repair started for the floor hole";
				return;
			}
			BuildPlan fix = repair[0].plan(level);
			Map<BlockPos, BlockState> steps = QaFurnishedGameTests.stepStates(fix);
			if (steps.containsKey(chairAt)) {
				phase[0] = "the repair plans " + steps.get(chairAt) + " over the player's crafting table on the chair spot";
			} else if (!steps.containsKey(hole)) {
				phase[0] = "the repair doesn't fill the floor hole: " + steps.keySet();
			} else {
				phase[0] = "repairing";
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!phase[0].equals("building"), "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			helper.assertTrue(phase[0].equals("repairing"), phase[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(repair[0].id()) == null, "still repairing: " + repair[0].stage() + " " + repair[0].status()
				+ " missing " + repair[0].missing());
			helper.assertTrue(level.getBlockState(hole).is(Blocks.OAK_PLANKS), "the floor hole is " + level.getBlockState(hole));
			helper.assertTrue(level.getBlockState(chairAt).is(Blocks.CRAFTING_TABLE), "the player's crafting table became " + level.getBlockState(chairAt));
		});
	}

	/**
	 * With no furniture mod at all, a nook placed turned a quarter the other way and mirrored left to right is built with
	 * its vanilla chair. After DONE a player breaks the chair, the sites are saved and loaded, and the test mod is then put
	 * first in the order: the builder's repair puts back exactly the chair that stood (the vanilla stair, the same way up
	 * and facing the same way), asks for one stair and nothing else.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaFurnishedVanillaRepair1103f"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFurnishedVanillaRepair1103f")
	public void aVanillaChairBrokenAfterTheBuildIsPutBackAsItStood(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		ServerLevel level = helper.getLevel();
		Furnishings.MODS = List.of();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		Rotation rotation = Rotation.COUNTERCLOCKWISE_90;
		Mirror mirror = Mirror.LEFT_RIGHT;
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), QaFurnishedGameTests.NOOK_SIZE,
			helper.absolutePos(new BlockPos(9, 2, 9)), rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && site.furniture().isVanilla(), "no vanilla plan for the nook: " + site.furniture());
		QaFurnishedGameTests.stock(helper, plan);
		BlockPos chairAt = placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
			QaFurnishedGameTests.CHAIR, mirror, rotation, BlockPos.ZERO));
		BlockState planned = QaFurnishedGameTests.stepStates(plan).get(chairAt);
		helper.assertTrue(planned != null && planned.is(Blocks.SPRUCE_STAIRS) && planned.getValue(StairBlock.HALF) == Half.BOTTOM,
			"the vanilla nook's chair is planned as " + planned);
		boolean[] checked = {false};
		helper.succeedWhen(() -> {
			if (checked[0]) {
				return;
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			checked[0] = true;
			BlockState built = level.getBlockState(chairAt);
			helper.assertTrue(site.skipped() == 0, site.skipped() + " steps skipped");
			helper.assertTrue(built.is(Blocks.SPRUCE_STAIRS) && built.getValue(StairBlock.HALF) == Half.BOTTOM
				&& built.getValue(StairBlock.FACING) == planned.getValue(StairBlock.FACING), "the built chair is " + built + ", planned " + planned);
			level.destroyBlock(chairAt, false);
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			Furnishings.MODS = List.of(PIECES);
			io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
			io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = false;
			BuildSite repair = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
			helper.assertTrue(repair != null, "no repair started for the broken chair");
			BuildPlan fix = repair.plan(level);
			BlockState back = QaFurnishedGameTests.stepStates(fix).get(chairAt);
			helper.assertTrue(back != null && back.is(Blocks.SPRUCE_STAIRS) && back.getValue(StairBlock.HALF) == Half.BOTTOM
				&& back.getValue(StairBlock.FACING) == built.getValue(StairBlock.FACING),
				"the repair puts back " + back + " where " + built + " stood");
			Map<Item, Integer> cost = fix.materials();
			helper.assertTrue(cost.equals(Map.of(Items.SPRUCE_STAIRS, 1)), "the repair of one chair costs " + cost);
		});
	}

	/**
	 * The nook begun with {@code furnishedBuilds} off (the test mod first in the order all along): once the builder has
	 * placed blocks and before the chair, an admin turns the switch on and the server restarts. The nook still finishes
	 * with the vanilla chair it was begun with (a stair the right way up), nothing skipped, and the finished building
	 * records no furniture.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnishedSwitchOn1103f"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnishedSwitchOn1103f")
	public void aNookBegunWithTheSwitchOffAndSwitchedOnMidBuildStaysVanilla(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
		BuildSite site = Builders.start(level, builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the nook");
		helper.assertTrue(site.furniture().isVanilla(), "the nook chose " + site.furniture() + " with the switch off");
		QaFurnishedGameTests.stock(helper, plan);
		((Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(20, new ItemStack(Items.SPRUCE_STAIRS, 2));
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		String[] restarted = {null};
		helper.onEachTick(() -> {
			if (restarted[0] != null) {
				return;
			}
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live == null) {
				restarted[0] = "missed: the nook finished before the switch could change";
				return;
			}
			if (live.placed() == 0) {
				return;
			}
			if (!level.getBlockState(chairAt).isAir()) {
				restarted[0] = "missed: the chair went down with the first blocks";
				return;
			}
			var registries = level.registryAccess();
			WorkplaceConfig.parse("{\"furnishedBuilds\": true, \"furnitureMods\": [\"" + PIECES + "\"]}").apply();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			restarted[0] = "at " + live.stage() + " cursor " + live.cursor() + ", " + live.placed() + " placed";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "the build never placed a block");
			helper.assertTrue(!restarted[0].startsWith("missed"), restarted[0]);
			BuildSiteManager manager = BuildSiteManager.get(level);
			BuildSite live = manager.get(site.id());
			helper.assertTrue(live == null, "still building (restarted " + restarted[0] + "): " + (live == null ? "" : live.stage() + " "
				+ live.status() + " missing " + live.missing()));
			BlockState chair = level.getBlockState(chairAt);
			helper.assertTrue(chair.is(Blocks.SPRUCE_STAIRS) && chair.getValue(StairBlock.HALF) == Half.BOTTOM,
				"switched on and restarted " + restarted[0] + ": the finished nook's chair is " + chair + ", wanted the vanilla stair it was begun with");
			helper.assertTrue(manager.furniture(placement).isVanilla(), "the finished nook records " + manager.furniture(placement));
			org.slf4j.LoggerFactory.getLogger("QaFurnished1103f").info("switched on, restarted {}: chair {} by tick {}", restarted[0], chair, helper.getTick());
		});
	}

	private static String diff(Map<String, Integer> listed, Map<String, Integer> planned) {
		List<String> out = new ArrayList<>();
		java.util.Set<String> keys = new java.util.TreeSet<>(listed.keySet());
		keys.addAll(planned.keySet());
		for (String k : keys) {
			int a = listed.getOrDefault(k, 0);
			int b = planned.getOrDefault(k, 0);
			if (a != b) {
				out.add(k + " " + a + " (the plan uses " + b + ")");
			}
		}
		return String.join(", ", out);
	}
}
