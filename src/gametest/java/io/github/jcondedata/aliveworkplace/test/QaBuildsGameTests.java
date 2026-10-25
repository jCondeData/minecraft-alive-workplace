package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-24), written from the specs (B280, B300, B326 and the builder's invariants in CLAUDE.md), not from the
 * code: landscaping round other blueprints than the Terrace, clicked onto plants through the Blueprint item; furnishings
 * whose support goes mid-build; a site saved and loaded back in FOUNDATION and in LANDSCAPE with every item counted; a
 * crew lead taking over a block two members hold at once; and the level-ground switch at 0.
 */
public class QaBuildsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int LAWN = 3;
	private static final BlockPos BENCH = new BlockPos(2, LAWN + 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, LAWN + 1, 3);

	// --- B280: the ground round a finished build --------------------------------------------------------------------

	/**
	 * B280 with the Starter Cottage, through the player's own click: the Blueprint is clicked onto a poppy on a bump one
	 * above the lawn, so the cottage's floor stands two above the lawn over most of its footprint (the owner's case).
	 * Once built and landscaped, no column round it stands above the ground it had, nothing granular is left, and the
	 * two pits in the ring (beside the bump and on the far side) are filled to the lawn.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaBuildsCottageGrade"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaBuildsCottageGrade")
	public void aCottageClickedOntoAFlowerOnABumpLeavesTheLawnLevel(GameTestHelper helper) {
		ServerLevel level = start(helper, true);
		int[][] oldTop = lawn(helper, RandomSource.create(2801L));
		// the bump: one above the lawn, under the front middle of the cottage, a poppy on it where the player clicks
		BlockPos click = new BlockPos(15, LAWN + 2, 8);
		for (int x = 13; x <= 17; x++) {
			for (int z = 8; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, LAWN + 1, z), Blocks.GRASS_BLOCK);
				helper.setBlock(new BlockPos(x, LAWN + 2, z), Blocks.AIR);
				oldTop[x][z] = LAWN + 1;
			}
		}
		helper.setBlock(click, Blocks.POPPY);
		ServerPlayer player = player(helper);
		ItemStack stack = BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE.id(), StarterBlueprints.STARTER_COTTAGE.size());
		BlueprintData.Placement placement = clickOnto(helper, player, stack, click);
		helper.assertValueEqual(placement.origin().getY(), helper.absolutePos(click).getY(), "the cottage's floor clicked onto the poppy on the bump");
		BoundingBox footprint = footprint(helper, placement, StarterBlueprints.STARTER_COTTAGE.size());
		helper.assertTrue(footprint.isInside(13, 0, 8) && footprint.isInside(17, 0, 11) && footprint.minX() > 6 && footprint.maxX() < 26
			&& footprint.maxZ() < 26, "the cottage isn't over the bump and inside the area: " + footprint + ", origin " + placement.origin().subtract(helper.absolutePos(BlockPos.ZERO)) + " " + placement.rotation());
		// the pits: one beside the bump in the ring in front, one on the far side (both two deep, below the lawn)
		List<BlockPos> pits = List.of(new BlockPos(16, LAWN, footprint.minZ() - 1), new BlockPos(footprint.maxX() + 1, LAWN, footprint.minZ() + 5));
		for (BlockPos pit : pits) {
			helper.setBlock(pit, Blocks.AIR);
			helper.setBlock(pit.below(), Blocks.AIR);
			helper.setBlock(pit.above(), Blocks.AIR);
			oldTop[pit.getX()][pit.getZ()] = LAWN; // filled to the lawn, no higher
		}
		Villager builder = builder(helper);
		helper.assertTrue(Builders.assign(player, builder, player.getMainHandItem()).consumesAction(), "the builder didn't take the placed blueprint");
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "handing over the blueprint started no build");
		UUID id = site.id();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the cottage isn't finished: " + site.stage() + ", " + site.status());
			assertGrade(helper, footprint, oldTop, 2);
			for (BlockPos pit : pits) {
				helper.assertTrue(solid(helper, pit) && solid(helper, pit.below()), "the pit at " + pit.toShortString() + " in the ring wasn't filled to the lawn: "
					+ helper.getBlockState(pit) + " over " + helper.getBlockState(pit.below()));
			}
		});
	}

	/**
	 * B280 with the Market Stall, clicked onto the top half of tall grass: the land east of it is a terrace one lower than
	 * the lawn the stall stands on. The ring on that side stays at the low terrace (the old rule filled it up to the
	 * stall's ground, a raised band); a pit on the west side, below the lawn, is filled; nothing granular is left.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaBuildsStallGrade"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaBuildsStallGrade")
	public void aStallClickedOntoTallGrassLeavesTheLowerTerraceBesideItLow(GameTestHelper helper) {
		ServerLevel level = start(helper, true);
		int[][] oldTop = lawn(helper, RandomSource.create(2802L));
		BlockPos click = new BlockPos(14, LAWN + 2, 9); // the tall grass's top half
		helper.setBlock(click.below(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(click, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		ServerPlayer player = player(helper);
		ItemStack stack = BlueprintItem.create(StarterBlueprints.MARKET_STALL.id(), StarterBlueprints.MARKET_STALL.size());
		BlueprintData.Placement placement = clickOnto(helper, player, stack, click);
		helper.assertValueEqual(placement.origin().getY(), helper.absolutePos(new BlockPos(0, LAWN + 1, 0)).getY(),
			"the stall's floor clicked onto tall grass's top half (on the grass under the plant)");
		BoundingBox footprint = footprint(helper, placement, StarterBlueprints.MARKET_STALL.size());
		helper.assertTrue(footprint.minX() > 6 && footprint.maxX() < 22 && footprint.maxZ() < 24, "the stall isn't inside the area: " + footprint);
		// east of the stall, from the ring on: the land is one lower
		for (int x = footprint.maxX() + 1; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, LAWN + 1, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, LAWN, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, LAWN - 1, z), Blocks.GRASS_BLOCK);
				oldTop[x][z] = LAWN - 1;
			}
		}
		BlockPos pit = new BlockPos(footprint.minX() - 1, LAWN, footprint.minZ() + 2);
		helper.setBlock(pit.above(), Blocks.AIR);
		helper.setBlock(pit, Blocks.AIR);
		helper.setBlock(pit.below(), Blocks.AIR);
		oldTop[pit.getX()][pit.getZ()] = LAWN; // filled to the lawn, no higher
		Villager builder = builder(helper);
		helper.assertTrue(Builders.assign(player, builder, player.getMainHandItem()).consumesAction(), "the builder didn't take the placed blueprint");
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "handing over the blueprint started no build");
		UUID id = site.id();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the stall isn't finished: " + site.stage() + ", " + site.status());
			assertGrade(helper, footprint, oldTop, 2);
			helper.assertTrue(solid(helper, pit) && solid(helper, pit.below()), "the pit west of the stall wasn't filled to the lawn");
		});
	}

	// --- B300: nothing floats ------------------------------------------------------------------------------------------

	/**
	 * B300: mid-DECORATION a player breaks the block under a flower pot the builder has just set down (a flower pot
	 * stays in the air in the game), and mid-LANDSCAPE the block under a second one. The finished Market Stall has no
	 * pot, candle, lantern or carpet resting on nothing, and each undermined pot was set down below or dropped.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaBuildsPotUndermined"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaBuildsPotUndermined")
	public void potsWhoseSupportIsBrokenMidBuildAreSetDown(GameTestHelper helper) {
		undermined(helper, false);
	}

	/** B300 as above, with a save and reload of the site and its builder straight after the first pot's support goes. */
	//$ gametest_ticks_batch HUGE '9000' '"qaBuildsPotReload"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaBuildsPotReload")
	public void aPotUnderminedThenAReloadMidDecorationIsStillSetDown(GameTestHelper helper) {
		undermined(helper, true);
	}

	private static void undermined(GameTestHelper helper, boolean reload) {
		ServerLevel level = start(helper, true);
		lawn(helper, RandomSource.create(3001L));
		for (BlockPos pit : List.of(new BlockPos(9, LAWN, 12), new BlockPos(14, LAWN, 9), new BlockPos(19, LAWN, 13), new BlockPos(13, LAWN, 17))) {
			helper.setBlock(pit, Blocks.AIR); // a few holes round it, so the landscaping has work
			helper.setBlock(pit.below(), Blocks.AIR);
		}
		Villager[] builder = {builder(helper)};
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(10, LAWN + 1, 10)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder[0], null, StarterBlueprints.MARKET_STALL.id(), placement);
		helper.assertTrue(site != null, "the stall didn't start");
		UUID id = site.id();
		BuildPlan plan = site.plan(level);
		List<BlockPos> pots = new ArrayList<>();
		for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.DECORATION)) {
			if (s.state().getBlock() instanceof FlowerPotBlock && !level.getBlockState(s.pos().above()).is(Blocks.CHAIN)
				&& plan.solidPlannedAt(s.pos().below())) {
				pots.add(s.pos());
			}
		}
		helper.assertTrue(pots.size() >= 2, "the Market Stall has " + pots.size() + " flower pots standing on blocks, not 2");
		Map<BlockPos, BlockState> broken = new HashMap<>();
		Map<BlockPos, Block> potBlock = new HashMap<>();
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(id);
			if (now == null) {
				return;
			}
			if (broken.isEmpty() && now.stage() == BuildPlan.Stage.DECORATION) {
				for (BlockPos pot : pots) {
					BlockState there = level.getBlockState(pot);
					if (there.getBlock() instanceof FlowerPotBlock && !level.getBlockState(pot.below()).isAir()
						&& !level.getBlockState(pot.below()).hasBlockEntity()) {
						broken.put(pot.below(), level.getBlockState(pot.below()));
						potBlock.put(pot, there.getBlock());
						level.destroyBlock(pot.below(), false); // a player mines the shelf under it
						helper.assertTrue(level.getBlockState(pot).getBlock() instanceof FlowerPotBlock, "the pot fell by itself: the test isn't testing B300");
						if (reload) {
							builder[0] = reload(helper, level, builder[0]);
						}
						break;
					}
				}
			} else if (broken.size() == 1 && now.stage() == BuildPlan.Stage.LANDSCAPE) {
				for (BlockPos pot : pots) {
					if (!potBlock.containsKey(pot) && level.getBlockState(pot).getBlock() instanceof FlowerPotBlock && !level.getBlockState(pot.below()).isAir()
						&& !level.getBlockState(pot.below()).hasBlockEntity()) {
						broken.put(pot.below(), level.getBlockState(pot.below()));
						potBlock.put(pot, level.getBlockState(pot).getBlock());
						level.destroyBlock(pot.below(), false);
						break;
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the stall isn't finished");
			helper.assertTrue(potBlock.size() == 2, "only " + potBlock.size() + " pot(s) had their support broken (one in DECORATION, one in LANDSCAPE)");
			List<String> floating = floating(level, plan.bounds().inflatedBy(3));
			helper.assertTrue(floating.isEmpty(), "resting on nothing after the build: " + floating);
			for (Map.Entry<BlockPos, Block> e : potBlock.entrySet()) {
				BlockPos pot = e.getKey();
				boolean setDown = false;
				for (int dy = 0; dy <= 8 && !setDown; dy++) {
					setDown = level.getBlockState(pot.below(dy)).is(e.getValue()) && solidUnder(level, pot.below(dy));
				}
				boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(pot).inflate(4),
					i -> i.getItem().is(Items.FLOWER_POT) || i.getItem().is(e.getValue().asItem())).isEmpty();
				helper.assertTrue(setDown || dropped, "the pot at " + pot.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " was neither set down nor dropped");
			}
		});
	}

	// --- save and reload mid-job --------------------------------------------------------------------------------------

	/**
	 * The test hut over a dip, with pits in the ring round it, built from a chest (free materials off), its site and
	 * builder saved and loaded back midway through FOUNDATION: it finishes as drawn, the pits filled, and every item the
	 * chest held is either in the build, back in the chest or in the builder's bag: nothing lost, nothing made.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaBuildsReloadFoundation"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaBuildsReloadFoundation")
	public void aReloadMidFoundationLosesAndMakesNothing(GameTestHelper helper) {
		reloadMid(helper, BuildPlan.Stage.FOUNDATION);
	}

	/** As above, the reload midway through LANDSCAPE (its list started again from the ground as it stands). */
	//$ gametest_ticks_batch HUGE '6000' '"qaBuildsReloadLandscape"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaBuildsReloadLandscape")
	public void aReloadMidLandscapeLosesAndMakesNothing(GameTestHelper helper) {
		reloadMid(helper, BuildPlan.Stage.LANDSCAPE);
	}

	private static final Item[] COUNTED = {Items.COBBLESTONE, Items.OAK_PLANKS, Items.TORCH, Items.OAK_DOOR, Items.DIRT};

	private static void reloadMid(GameTestHelper helper, BuildPlan.Stage at) {
		ServerLevel level = start(helper, false);
		int[][] oldTop = lawn(helper, RandomSource.create(3003L));
		BlockPos origin = new BlockPos(12, LAWN + 1, 12);
		// the dip: three columns under the hut's floor, two of them two deep
		Set<BlockPos> wasAir = new HashSet<>();
		for (BlockPos p : List.of(new BlockPos(12, LAWN, 12), new BlockPos(13, LAWN, 12), new BlockPos(14, LAWN, 12), new BlockPos(12, LAWN - 1, 12),
			new BlockPos(13, LAWN - 1, 12))) {
			helper.setBlock(p, Blocks.AIR);
			wasAir.add(helper.absolutePos(p));
		}
		// the pits in the ring, below the lawn
		List<BlockPos> pits = List.of(new BlockPos(11, LAWN, 14), new BlockPos(17, LAWN, 13), new BlockPos(14, LAWN, 17), new BlockPos(18, LAWN, 15));
		for (BlockPos pit : pits) {
			helper.setBlock(pit.above(), Blocks.AIR);
			helper.setBlock(pit, Blocks.AIR);
			helper.setBlock(pit.below(), Blocks.AIR);
			wasAir.add(helper.absolutePos(pit));
			wasAir.add(helper.absolutePos(pit.below()));
			oldTop[pit.getX()][pit.getZ()] = LAWN; // filled to the lawn, no higher
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack[] stock = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 10),
			new ItemStack(Items.TORCH, 4), new ItemStack(Items.OAK_DOOR, 2), new ItemStack(Items.DIRT, 64)};
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i].copy());
		}
		Map<Item, Integer> before = new HashMap<>();
		for (Item item : COUNTED) {
			before.put(item, chest.countItem(item));
		}
		Villager[] builder = {builder(helper)};
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder[0], null, TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start");
		UUID id = site.id();
		BuildPlan plan = site.plan(level);
		List<BuildPlan.Step> stageSteps = new ArrayList<>(plan.steps(at));
		stageSteps.removeIf(s -> s.state().isAir() || !wasAir.contains(s.pos()));
		helper.assertTrue(stageSteps.size() >= 3, "the hut's " + at + " list has " + stageSteps.size() + " blocks to place, too few to reload in the middle of");
		Set<BlockPos> stepsAt = new HashSet<>();
		stageSteps.forEach(s -> stepsAt.add(s.pos()));
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(level).get(id);
			if (reloaded[0] || now == null || now.stage() != at) {
				return;
			}
			long done = stepsAt.stream().filter(p -> !level.getBlockState(p).isAir()).count();
			if (done >= 1 && done < stepsAt.size()) {
				reloaded[0] = true;
				builder[0] = reload(helper, level, builder[0]);
				BuildSite back = BuildSiteManager.get(level).get(id);
				helper.assertTrue(back != null && back != now && back.stage() == at, "the site didn't come back in " + at + ": " + (back == null ? "gone" : back.stage()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the build was never reloaded in the middle of " + at);
			BuildSite now = BuildSiteManager.get(level).get(id);
			helper.assertTrue(now == null, "the hut isn't finished: " + (now == null ? "-" : now.stage() + ", " + now.status() + ", missing " + now.missing()));
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the hut not as drawn, e.g. "
				+ unfinished.stream().limit(4).map(p -> p.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + "=" + level.getBlockState(p)).toList());
			for (BlockPos pit : pits) {
				helper.assertTrue(solid(helper, pit) && solid(helper, pit.below()), "the pit at " + pit.toShortString() + " wasn't filled");
			}
			assertGrade(helper, footprint(helper, placement, new net.minecraft.core.Vec3i(5, 4, 5)), oldTop, 2);
			// every item accounted for
			Map<Item, Integer> placed = new HashMap<>();
			Set<BlockPos> counted = new HashSet<>();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step s : plan.steps(stage)) {
					BlockState world = level.getBlockState(s.pos());
					if (!s.state().isAir() && world.is(s.state().getBlock()) && counted.add(s.pos()) && !(s.state().is(Blocks.DIRT) || s.state().is(Blocks.GRASS_BLOCK))) {
						placed.merge(s.state().getBlock().asItem(), 1, Integer::sum);
					}
				}
			}
			for (BlockPos p : wasAir) { // the dip and the pits: filled with dirt from the chest (grass may have spread over it)
				BlockState world = level.getBlockState(p);
				if (world.is(Blocks.DIRT) || world.is(Blocks.GRASS_BLOCK)) {
					placed.merge(Items.DIRT, 1, Integer::sum);
				}
			}
			Villager b = builder[0];
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(b);
			List<ItemEntity> loose = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30));
			List<String> wrong = new ArrayList<>();
			for (Item item : COUNTED) {
				int inChest = ((Container) helper.getBlockEntity(CHEST)).countItem(item);
				int inBag = bag.count(item) + b.getInventory().countItem(item) + (b.getMainHandItem().is(item) ? b.getMainHandItem().getCount() : 0);
				int lying = loose.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
				int inBuild = placed.getOrDefault(item, 0);
				if (inChest + inBag + lying + inBuild != before.get(item)) {
					wrong.add(item + ": " + before.get(item) + " in the chest before, now " + inChest + " in it, " + inBag + " carried, " + lying + " lying about, "
						+ inBuild + " in the build");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "after a reload in " + at + " items were lost or made: " + wrong);
		});
	}

	// --- B326: a crew's lead takes over a block two members hold ---------------------------------------------------

	/**
	 * B326 (the spec: "the member on its column or block lets it go, the lead takes the column ... and lays the block
	 * itself"), with two members on the one block: one holds its column, the other claims the block itself. Neither ever
	 * lays it. The floor's middle, earlier in the lead's list, waits for good on the dug-out block under it, so the lead's
	 * pass doesn't come back round to the hole in the tick it takes it over: in the tick both let go, the hole is in the
	 * lead's hand, and the column is the lead's, not left to a member to take back.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaBuildsTwoHolders"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaBuildsTwoHolders")
	public void theLeadTakesABlockOverFromTwoMembersHoldingItAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos dip = new BlockPos(8, 1, 8);
		helper.setBlock(dip, Blocks.AIR);
		helper.setDayTime(2000);
		rules(helper, level, true);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager lead = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, lead, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, lead, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		site.setWonder(UUID.randomUUID()); // a Wonder's part: with members about, its builders work as a crew
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "the test hut's plan isn't there");
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				level.setBlock(s.pos(), s.state(), Block.UPDATE_CLIENTS);
				if (s.secondaryPos() != null && s.secondaryState() != null) {
					level.setBlock(s.secondaryPos(), s.secondaryState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		Set<BlockPos> walls = new HashSet<>();
		plan.steps(BuildPlan.Stage.STRUCTURE).forEach(s -> walls.add(s.pos()));
		// the hole: a wall block on the first course with a wall block over it; the floor's middle, over the dip, waits
		BlockPos stuck = helper.absolutePos(dip.above());
		helper.assertTrue(plan.steps(BuildPlan.Stage.FOUNDATION).stream().anyMatch(s -> s.pos().equals(stuck.below())), "no foundation block in the dip");
		BlockPos hole = plan.steps(BuildPlan.Stage.STRUCTURE).stream().map(BuildPlan.Step::pos)
			.filter(p -> p.getY() == site.placement().origin().getY() + 1 && walls.contains(p.above()) && plan.solidPlannedAt(p.below())
				&& plan.column(p) != plan.column(stuck)).findFirst().orElseThrow();
		BlockPos over = hole.above();
		level.setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(over, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(stuck, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		Item holeItem = plan.steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.pos().equals(hole)).findFirst().orElseThrow().state().getBlock().asItem();
		BlockPos by = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = hole.below().relative(d);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).canBeReplaced()) {
				by = p;
				break;
			}
		}
		helper.assertTrue(by != null, "nowhere to stand by the hole");
		BlockPos stand = by;
		UUID siteId = site.id();
		UUID columnHolder = UUID.randomUUID(); // holds the hole's column, never lays it
		UUID claimer = UUID.randomUUID(); // claims the hole itself, never lays it
		UUID floorHolder = UUID.randomUUID(); // holds the floor's middle through the first pass, then leaves
		long column = plan.column(hole);
		long stuckColumn = plan.column(stuck);
		boolean[] state = {false, false}; // the retries began (floor's middle left for good), both holds in place
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(now != null, "the hut was finished with its floor's middle over a dug-out dip");
			long t = level.getGameTime();
			helper.assertTrue(level.getBlockState(over).isAir(), "the block over the hole went up, over air");
			if (state[1] && now.claim(claimer) == null && now.crewColumn(columnHolder) == null) {
				// both let go in the tick just gone: the lead took the hole over
				helper.assertTrue(state[0] && now.isRetrying(), "the members' holds went before the lead's retries");
				helper.assertTrue(now.crewColumn(lead.getUUID()) != null && now.crewColumn(lead.getUUID()) == column,
					"the members let go, but the hole's column isn't the lead's: " + now.crewColumn(lead.getUUID()));
				helper.assertTrue(lead.getMainHandItem().is(holeItem), "the lead took the hole over from two members but isn't on it that tick: holding "
					+ lead.getMainHandItem());
				helper.succeed();
				return;
			}
			helper.assertTrue(!state[1] || now.claim(claimer) != null && now.crewColumn(columnHolder) != null,
				"only one of the two members let go: claim " + now.claim(claimer) + ", column " + now.crewColumn(columnHolder));
			if (!state[0]) {
				if (now.stage() == BuildPlan.Stage.STRUCTURE && now.isRetrying()) {
					state[0] = true;
					now.release(floorHolder);
					level.setBlock(stuck.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else {
					now.seen(floorHolder, t);
					if (!now.columnTakenByOther(floorHolder, stuckColumn)) {
						now.takeColumn(floorHolder, stuckColumn);
						now.claim(floorHolder, stuck, t);
					}
				}
			}
			now.seen(columnHolder, t);
			now.seen(claimer, t);
			lead.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			lead.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			if (!now.columnTakenByOther(columnHolder, column) && now.crewColumn(columnHolder) == null && !state[1]) {
				now.takeColumn(columnHolder, column);
			}
			if (now.claim(claimer) == null && !state[1]) {
				now.claim(claimer, hole, t);
			}
			if (!state[1] && now.claim(claimer) != null && now.crewColumn(columnHolder) != null) {
				state[1] = true;
			}
		});
	}

	/**
	 * B326 again, with a third member working a column of its own elsewhere: the spec lets go only "the member on its
	 * column or block", so when the lead takes the hole over, that third member keeps its block and its column (kills the
	 * nightly survivor BuilderWork.takeOver's `!m.equals(me) && claim != null` to `||`, which let every member go). As
	 * below, with two members on the one block: one holds its column, the other claims the block itself. Neither ever
	 * lays it. The floor's middle, earlier in the lead's list, waits for good on the dug-out block under it, so the lead's
	 * pass doesn't come back round to the hole in the tick it takes it over: in the tick both let go, the hole is in the
	 * lead's hand, and the column is the lead's, not left to a member to take back.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaBuildsBystander"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaBuildsBystander")
	public void aMemberOnAnotherColumnKeepsItWhenTheLeadTakesAHoleOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos dip = new BlockPos(8, 1, 8);
		helper.setBlock(dip, Blocks.AIR);
		helper.setDayTime(2000);
		rules(helper, level, true);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager lead = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, lead, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, lead, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		site.setWonder(UUID.randomUUID()); // a Wonder's part: with members about, its builders work as a crew
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "the test hut's plan isn't there");
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				level.setBlock(s.pos(), s.state(), Block.UPDATE_CLIENTS);
				if (s.secondaryPos() != null && s.secondaryState() != null) {
					level.setBlock(s.secondaryPos(), s.secondaryState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		Set<BlockPos> walls = new HashSet<>();
		plan.steps(BuildPlan.Stage.STRUCTURE).forEach(s -> walls.add(s.pos()));
		// the hole: a wall block on the first course with a wall block over it; the floor's middle, over the dip, waits
		BlockPos stuck = helper.absolutePos(dip.above());
		helper.assertTrue(plan.steps(BuildPlan.Stage.FOUNDATION).stream().anyMatch(s -> s.pos().equals(stuck.below())), "no foundation block in the dip");
		BlockPos hole = plan.steps(BuildPlan.Stage.STRUCTURE).stream().map(BuildPlan.Step::pos)
			.filter(p -> p.getY() == site.placement().origin().getY() + 1 && walls.contains(p.above()) && plan.solidPlannedAt(p.below())
				&& plan.column(p) != plan.column(stuck)).findFirst().orElseThrow();
		BlockPos over = hole.above();
		level.setBlock(hole, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(over, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(stuck, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		Item holeItem = plan.steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.pos().equals(hole)).findFirst().orElseThrow().state().getBlock().asItem();
		BlockPos by = null;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = hole.below().relative(d);
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && !level.getBlockState(p.below()).canBeReplaced()) {
				by = p;
				break;
			}
		}
		helper.assertTrue(by != null, "nowhere to stand by the hole");
		BlockPos stand = by;
		UUID siteId = site.id();
		UUID columnHolder = UUID.randomUUID(); // holds the hole's column, never lays it
		UUID claimer = UUID.randomUUID(); // claims the hole itself, never lays it
		UUID floorHolder = UUID.randomUUID(); // holds the floor's middle through the first pass, then leaves
		UUID bystander = UUID.randomUUID(); // works a column of its own, away from the hole and the floor's middle
		long column = plan.column(hole);
		long stuckColumn = plan.column(stuck);
		BlockPos elsewhere = plan.steps(BuildPlan.Stage.STRUCTURE).stream().map(BuildPlan.Step::pos)
			.filter(p -> plan.column(p) != plan.column(hole) && plan.column(p) != plan.column(stuck)).reduce((x, y) -> y).orElseThrow();
		long elsewhereColumn = plan.column(elsewhere);
		boolean[] state = {false, false}; // the retries began (floor's middle left for good), both holds in place
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			BuildSite now = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(now != null, "the hut was finished with its floor's middle over a dug-out dip");
			long t = level.getGameTime();
			helper.assertTrue(level.getBlockState(over).isAir(), "the block over the hole went up, over air");
			if (state[1] && now.claim(claimer) == null && now.crewColumn(columnHolder) == null) {
				// both let go in the tick just gone: the lead took the hole over
				helper.assertTrue(state[0] && now.isRetrying(), "the members' holds went before the lead's retries");
				helper.assertTrue(now.crewColumn(lead.getUUID()) != null && now.crewColumn(lead.getUUID()) == column,
					"the members let go, but the hole's column isn't the lead's: " + now.crewColumn(lead.getUUID()));
				helper.assertTrue(elsewhere.equals(now.claim(bystander)) && now.crewColumn(bystander) != null && now.crewColumn(bystander) == elsewhereColumn,
					"taking the hole over, the lead let go the member on another column too: claim " + now.claim(bystander) + ", column " + now.crewColumn(bystander));
				org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa").info("qa crew bystander: the lead took the hole over and the member at {} kept its block and column", elsewhere.toShortString());
				helper.succeed();
				return;
			}
			helper.assertTrue(!state[1] || now.claim(claimer) != null && now.crewColumn(columnHolder) != null,
				"only one of the two members let go: claim " + now.claim(claimer) + ", column " + now.crewColumn(columnHolder));
			if (!state[0]) {
				if (now.stage() == BuildPlan.Stage.STRUCTURE && now.isRetrying()) {
					state[0] = true;
					now.release(floorHolder);
					level.setBlock(stuck.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else {
					now.seen(floorHolder, t);
					if (!now.columnTakenByOther(floorHolder, stuckColumn)) {
						now.takeColumn(floorHolder, stuckColumn);
						now.claim(floorHolder, stuck, t);
					}
				}
			}
			now.seen(columnHolder, t);
			now.seen(claimer, t);
			if (now.claim(bystander) == null) {
				now.takeColumn(bystander, elsewhereColumn);
				now.claim(bystander, elsewhere, t);
			}
			now.seen(bystander, t);
			lead.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			lead.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			if (!now.columnTakenByOther(columnHolder, column) && now.crewColumn(columnHolder) == null && !state[1]) {
				now.takeColumn(columnHolder, column);
			}
			if (now.claim(claimer) == null && !state[1]) {
				now.claim(claimer, hole, t);
			}
			if (!state[1] && now.claim(claimer) != null && now.crewColumn(columnHolder) != null) {
				state[1] = true;
			}
		});
	}

	// --- a config switch off --------------------------------------------------------------------------------------

	/**
	 * workplaceLevelGround at 0 ("leave the ground alone"): the hut is still built, with no crash, and the ground round it
	 * is left as it was: the pits in the ring stay open and the mound beside it, above the hut's floor, stays.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaBuildsLevelGroundOff"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaBuildsLevelGroundOff")
	public void levelGroundZeroLeavesTheGroundAlone(GameTestHelper helper) {
		ServerLevel level = start(helper, true);
		int margin = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(margin, level.getServer()));
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(0, level.getServer());
		lawn(helper, RandomSource.create(3005L));
		BlockPos origin = new BlockPos(12, LAWN + 1, 12);
		List<BlockPos> pits = List.of(new BlockPos(11, LAWN, 14), new BlockPos(17, LAWN, 13));
		for (BlockPos pit : pits) {
			helper.setBlock(pit.above(), Blocks.AIR);
			helper.setBlock(pit, Blocks.AIR);
			helper.setBlock(pit.below(), Blocks.AIR);
		}
		List<BlockPos> mound = List.of(new BlockPos(14, LAWN + 1, 17), new BlockPos(14, LAWN + 2, 17), new BlockPos(15, LAWN + 1, 17));
		for (BlockPos p : mound) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Villager builder = builder(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start");
		UUID id = site.id();
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan.steps(BuildPlan.Stage.LANDSCAPE).stream().noneMatch(s -> !s.state().isAir()),
			"with levelGround 0 the plan still fills " + plan.steps(BuildPlan.Stage.LANDSCAPE).stream().filter(s -> !s.state().isAir()).count() + " spot(s)");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the hut isn't finished: " + site.stage() + ", " + site.status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the hut isn't as drawn");
			for (BlockPos pit : pits) {
				helper.assertTrue(helper.getBlockState(pit).isAir() && helper.getBlockState(pit.below()).isAir(), "the pit at " + pit.toShortString()
					+ " was filled with levelGround 0: " + helper.getBlockState(pit) + " over " + helper.getBlockState(pit.below()));
			}
			for (BlockPos p : mound) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.DIRT), "the mound at " + p.toShortString() + " was dug with levelGround 0");
			}
		});
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static ServerLevel start(GameTestHelper helper, boolean free) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper, level, free);
		return level;
	}

	private static void rules(GameTestHelper helper, ServerLevel level, boolean free) {
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
	}

	/** A lawn over the whole area (dirt, grass on top at {@link #LAWN}), flowers here and there; returns each column's ground top. */
	private static int[][] lawn(GameTestHelper helper, RandomSource random) {
		int[][] top = new int[30][30];
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, LAWN - 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, LAWN, z), Blocks.GRASS_BLOCK);
				boolean byBench = x <= 4 && z <= 6;
				helper.setBlock(new BlockPos(x, LAWN + 1, z), !byBench && random.nextInt(5) == 0 ? (random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION) : Blocks.AIR);
				top[x][z] = LAWN;
			}
		}
		return top;
	}

	private static Villager builder(GameTestHelper helper) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, LAWN + 1, 3));
		Builders.employ(helper.getLevel(), villager, helper.absolutePos(BENCH));
		return villager;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setYRot(0); // looking south: the build goes away from the player, +z
		return player;
	}

	/** The player clicks {@code stack} onto the top of the block at {@code clicked}; returns the placement it got. */
	private static BlueprintData.Placement clickOnto(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos clicked) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos at = helper.absolutePos(clicked);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
		Optional<BlueprintData.Placement> placed = BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
		if (placed.isEmpty()) {
			throw new GameTestAssertException("clicking " + helper.getBlockState(clicked).getBlock() + " placed nothing");
		}
		return placed.get();
	}

	/** The footprint, relative to the test's corner (y 0). */
	private static BoundingBox footprint(GameTestHelper helper, BlueprintData.Placement placement, net.minecraft.core.Vec3i size) {
		BoundingBox b = BlueprintOutline.bounds(placement, size);
		BlockPos lo = new BlockPos(b.minX(), b.minY(), b.minZ()).subtract(helper.absolutePos(BlockPos.ZERO));
		return new BoundingBox(lo.getX(), 0, lo.getZ(), lo.getX() + b.getXSpan() - 1, 0, lo.getZ() + b.getZSpan() - 1);
	}

	/**
	 * B280: within {@code ring} + 2 blocks of the footprint (the bench's corner left out), no ground stands above the ground the
	 * column had (plants don't count, nor where they stood), and nothing that falls (sand, gravel, concrete powder) is there.
	 */
	private static void assertGrade(GameTestHelper helper, BoundingBox footprint, int[][] oldTop, int ring) {
		int reach = ring + 2;
		for (int x = Math.max(0, footprint.minX() - reach); x <= Math.min(29, footprint.maxX() + reach); x++) {
			for (int z = Math.max(0, footprint.minZ() - reach); z <= Math.min(29, footprint.maxZ() + reach); z++) {
				if (footprint.isInside(x, 0, z) || x <= 4 && z <= 6) {
					continue;
				}
				for (int y = 1; y < 14; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState state = helper.getBlockState(p);
					helper.assertTrue(!(state.getBlock() instanceof FallingBlock), state.getBlock() + " left at " + p.toShortString() + " beside the build");
					boolean plant = state.getBlock() instanceof BushBlock || state.canBeReplaced();
					helper.assertTrue(y <= oldTop[x][z] || state.isAir() || plant && y == oldTop[x][z] + 1,
						state.getBlock() + " at " + p.toShortString() + " beside the build, above the ground there (y " + oldTop[x][z] + ")");
				}
			}
		}
	}

	private static boolean solid(GameTestHelper helper, BlockPos p) {
		BlockState s = helper.getBlockState(p);
		return !s.isAir() && !s.canBeReplaced() && !(s.getBlock() instanceof FallingBlock);
	}

	private static boolean solidUnder(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
	}

	/**
	 * B300, from the spec ("every pot, lamp, picture and decoration ... rests on or is attached to a block"): every flower
	 * pot, candle, standing lantern, carpet or decorated pot in {@code box} with nothing to stand on under it (a pot hung
	 * from a chain is fine), and anything else that is not solid and can't stay where it is.
	 */
	private static List<String> floating(ServerLevel level, BoundingBox box) {
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (s.isAir()) {
				continue;
			}
			Block b = s.getBlock();
			boolean small = b instanceof FlowerPotBlock || b instanceof CandleBlock || b instanceof LanternBlock && !s.getValue(LanternBlock.HANGING)
				|| b instanceof net.minecraft.world.level.block.CarpetBlock || b instanceof net.minecraft.world.level.block.DecoratedPotBlock;
			if (small ? !solidUnder(level, p) && !(level.getBlockState(p.above()).getBlock() instanceof ChainBlock) : !s.isSolid() && !s.canSurvive(level, p)) {
				out.add(b.getDescriptionId() + " at " + p.toShortString());
			}
		}
		return out;
	}

	/**
	 * As a save and a load of the world would: every build site from its save (the manager replaced, so each site's plan
	 * is worked out again from the ground as it stands), and the builder from its save. Returns the loaded builder.
	 */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
