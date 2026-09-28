package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * In-world tests: a real villager on a real (headless) server builds real blueprints.
 * Run with {@code ./gradlew runGameTest}. Every test must end in success; CI fails otherwise.
 */
public class BuilderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// Test areas: relative y=1 is the smooth-stone floor layer, so things stand at y=2.
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan) {
	}

	// --- building --------------------------------------------------------------------------

	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildsHutFromChestMaterials(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "planks left in chest: " + chest.countItem(Items.OAK_PLANKS));
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "blueprint was not returned to the chest");
			helper.assertTrue(!s.villager().hasAttached(ModAttachments.BUILDER_JOB), "villager still has a job");
		});
	}

	/** Once the hut is up, the builder levels the ground around it: a mound and a rock dug away, a hole filled. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void builderLevelsTheGroundAroundABuild(GameTestHelper helper) {
		BlockPos mound = new BlockPos(5, 2, 8);
		BlockPos rock = new BlockPos(12, 2, 7);
		BlockPos hole = new BlockPos(8, 1, 11);
		BlockPos crate = new BlockPos(12, 2, 10);
		helper.setBlock(mound, Blocks.DIRT);
		helper.setBlock(mound.above(), Blocks.GRASS_BLOCK);
		helper.setBlock(rock, Blocks.STONE);
		helper.setBlock(hole, Blocks.AIR);
		helper.setBlock(crate, Blocks.OAK_PLANKS); // something built: stays
		ItemStack[] materials = java.util.stream.Stream.concat(java.util.Arrays.stream(hutMaterials()),
			java.util.stream.Stream.of(new ItemStack(Items.DIRT, 4))).toArray(ItemStack[]::new);
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, materials);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertBlockPresent(Blocks.AIR, mound);
			helper.assertBlockPresent(Blocks.AIR, mound.above());
			helper.assertBlockPresent(Blocks.AIR, rock);
			helper.assertBlockPresent(Blocks.DIRT, hole);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, crate);
		});
	}

	/** A blueprint with a pool: the builder pours the water from a bucket and keeps the empty bucket. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void builderPoursWaterFromABucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(MaterialRules.classify(Blocks.WATER.defaultBlockState()) == MaterialRules.Kind.DECORATION, "still water should be poured");
		helper.assertTrue(MaterialRules.classify(Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3))
			== MaterialRules.Kind.SKIP, "flowing water should be left to flow");
		helper.assertTrue(MaterialRules.requirements(Blocks.LAVA.defaultBlockState(), null).equals(List.of(new MaterialRules.Requirement(Items.LAVA_BUCKET, 1))),
			"lava costs a lava bucket");

		// The blueprint: a 3x2x3 cobblestone basin with still water in the middle, captured from the world.
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(src.offset(x, 0, z), Blocks.COBBLESTONE);
				helper.setBlock(src.offset(x, 1, z), x == 1 && z == 1 ? Blocks.WATER : Blocks.COBBLESTONE);
			}
		}
		ResourceLocation pool = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "pool_" + Long.toHexString(level.getGameTime()));
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level.getStructureManager().getOrCreate(pool);
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(src.offset(x, 1, z), Blocks.AIR);
				helper.setBlock(src.offset(x, 0, z), Blocks.AIR);
			}
		}

		Setup s = setup(helper, pool, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.COBBLESTONE, 17), new ItemStack(Items.WATER_BUCKET));
		BlockPos middle = HUT_ORIGIN.offset(1, 1, 1);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(helper.getBlockState(middle).getFluidState().isSource(), "no still water in the middle: " + helper.getBlockState(middle));
			Container chest = helper.getBlockEntity(CHEST);
			int buckets = chest.countItem(Items.BUCKET) + s.villager().getAttachedOrCreate(ModAttachments.BUILDER_BAG).count(Items.BUCKET);
			helper.assertTrue(buckets == 1, "the empty bucket went missing (" + buckets + ")");
			helper.assertTrue(chest.countItem(Items.WATER_BUCKET) == 0, "the water bucket wasn't used");
		});
	}

	/** With levelling turned off, the ground around a build stays as it was. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void levellingCanBeTurnedOff(GameTestHelper helper) {
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(),
			placement(helper, HUT_ORIGIN, Rotation.NONE), helper.getLevel(), 12, 0);
		helper.assertTrue(plan.steps(BuildPlan.Stage.LANDSCAPE).isEmpty(), "landscaping planned with a margin of 0");
		helper.setBlock(new BlockPos(5, 2, 8), Blocks.DIRT);
		BuildPlan levelled = BuildPlan.create(BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(),
			placement(helper, HUT_ORIGIN, Rotation.NONE), helper.getLevel(), 12, 2);
		helper.assertTrue(levelled.steps(BuildPlan.Stage.LANDSCAPE).stream().anyMatch(st -> st.pos().equals(helper.absolutePos(new BlockPos(5, 2, 8)))),
			"the mound isn't in the landscaping plan");
		helper.assertTrue(levelled.steps(BuildPlan.Stage.LANDSCAPE).stream().noneMatch(st -> st.pos().getY() > helper.absolutePos(HUT_ORIGIN).getY() + 3),
			"landscaping above the build's height");
		helper.succeed();
	}

	/** Regression: a builder standing where the floor goes must step aside instead of freezing. */
	@GameTest(template = AREA, timeoutTicks = 700)
	public void builderStandingInTheFootprintStepsAside(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.villager().teleportTo(helper.absolutePos(HUT_ORIGIN.offset(2, 0, 2)).getX() + 0.5,
			helper.absolutePos(HUT_ORIGIN).getY(), helper.absolutePos(HUT_ORIGIN.offset(2, 0, 2)).getZ() + 0.5);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
		});
	}

	/**
	 * Regression for the first playtest (jungle): an overgrown site with ferns, tall grass and a bush,
	 * and a mob that will not move off a floor spot. The builder must clear, shoo and finish.
	 */
	@GameTest(template = AREA, timeoutTicks = 1500)
	public void buildsOnAnOvergrownSiteWithAMobInTheWay(GameTestHelper helper) {
		for (int x = 5; x <= 11; x++) {
			for (int z = 5; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				BlockPos plant = new BlockPos(x, 2, z);
				switch ((x * 7 + z * 3) % 4) {
					case 0 -> helper.setBlock(plant, Blocks.FERN);
					case 1 -> helper.setBlock(plant, Blocks.SHORT_GRASS);
					case 2 -> {
						helper.setBlock(plant, Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
						helper.setBlock(plant.above(), Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
					}
					default -> {
					}
				}
			}
		}
		helper.setBlock(HUT_ORIGIN.offset(2, 1, 2), Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, HUT_ORIGIN.offset(1, 0, 3));
		cow.setNoAi(true); // a Pokémon that just sits there
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
		});
	}

	/**
	 * A build placed two blocks above the ground gets a foundation: every column under its floor is
	 * filled down to the ground — except columns where the ground is already higher.
	 */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void fillsAFoundationUnderAFloatingBuild(GameTestHelper helper) {
		BlockPos origin = new BlockPos(6, 4, 6); // hut floor at y=4, ground (smooth stone) at y=1: y=2..3 to fill
		for (int z = 6; z <= 10; z++) {
			helper.setBlock(new BlockPos(6, 2, z), Blocks.STONE); // the x=6 row already has ground up to y=3
			helper.setBlock(new BlockPos(6, 3, z), Blocks.STONE);
		}
		// floor 25 + 20 columns × 2 + 5 spare = 70 cobblestone (a chest slot holds 64)
		ItemStack[] materials = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 6),
			new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH)};
		Setup s = setup(helper, TEST_HUT, origin, Rotation.NONE, materials);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			for (int x = 7; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					for (int y = 2; y <= 3; y++) {
						BlockPos p = new BlockPos(x, y, z);
						helper.assertTrue(helper.getBlockState(p).is(Blocks.COBBLESTONE), "no foundation at " + p + ": " + helper.getBlockState(p));
					}
				}
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 5, "expected exactly 5 cobblestone left, found " + chest.countItem(Items.COBBLESTONE));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 1500, batch = "foundation_off")
	public void foundationsCanBeTurnedOff(GameTestHelper helper) {
		var rule = helper.getLevel().getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH);
		rule.set(0, helper.getLevel().getServer());
		Setup s = setup(helper, TEST_HUT, new BlockPos(6, 4, 6), Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 8)).isAir(), "foundation built although workplaceFoundationDepth=0");
			rule.set(12, helper.getLevel().getServer());
		});
	}

	@GameTest(template = AREA, timeoutTicks = 3000)
	public void waitsForMaterialsThenBuilds(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		AtomicBoolean supplied = new AtomicBoolean(false);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "expected WAITING_FOR_MATERIALS, was " + s.site().status());
			int planks = s.site().missing().getOrDefault(Items.OAK_PLANKS, 0);
			helper.assertTrue(planks == 55, "expected 55 planks missing, got " + s.site().missing());
			helper.assertTrue(s.plan().unfinished(s.level()).size() == s.plan().placeableCount(), "blocks were placed without materials");
			fill(helper.getBlockEntity(CHEST), hutMaterials());
			supplied.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(supplied.get(), "materials not supplied yet");
			assertBuilt(helper, s);
		});
	}

	@GameTest(template = AREA, timeoutTicks = 2400)
	public void clearsWhateverIsInTheWay(GameTestHelper helper) {
		// Junk inside the future hut: interior (must end up empty), a wall spot and a roof spot.
		helper.setBlock(HUT_ORIGIN.offset(2, 0, 2), Blocks.DIRT); // floor spot (cobblestone wanted)
		helper.setBlock(HUT_ORIGIN.offset(2, 1, 2), Blocks.DIRT); // interior (air wanted)
		helper.setBlock(HUT_ORIGIN.offset(1, 2, 1), Blocks.DIRT); // interior (air wanted)
		helper.setBlock(HUT_ORIGIN.offset(0, 1, 2), Blocks.STONE); // wall (planks wanted)
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.DIRT) == 3, "expected the 3 cleared dirt in the chest, found " + chest.countItem(Items.DIRT));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 2400, batch = "rotation")
	public void buildsRotatedBlueprints(GameTestHelper helper) {
		// Clockwise 90: template (x, z) -> (-z, x). Origin (12,2,6) puts the hut at x 8..12, z 6..10.
		Setup s = setup(helper, TEST_HUT, new BlockPos(12, 2, 6), Rotation.CLOCKWISE_90, hutMaterials());
		BlockPos doorRel = new BlockPos(12, 3, 8); // template door (2,1,0) -> (0,1,2) + origin
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			BlockState door = helper.getBlockState(doorRel);
			helper.assertTrue(door.is(Blocks.OAK_DOOR) && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "no door at " + doorRel + ": " + door);
			helper.assertTrue(door.getValue(DoorBlock.FACING) == Direction.WEST, "door should face west after rotating, faces " + door.getValue(DoorBlock.FACING));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 600)
	public void cancellingReturnsTheBlueprint(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.runAfterDelay(120, () -> Builders.cancel(s.level(), s.site()));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "site still exists");
			helper.assertTrue(!s.villager().hasAttached(ModAttachments.BUILDER_JOB), "villager still has the job");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "blueprint not returned");
		});
	}

	@GameTest(template = AREA, timeoutTicks = 1200, batch = "free_materials")
	public void freeMaterialsGameruleNeedsNoChest(GameTestHelper helper) {
		helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, helper.getLevel().getServer());
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, helper.getLevel().getServer());
		});
	}

	// --- the builds we ship must actually be buildable --------------------------------------

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsStarterCottage(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STARTER_COTTAGE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "starter_builds")
	public void buildsMarketStall(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.MARKET_STALL);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsLookoutTower(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.LOOKOUT_TOWER);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsHealingCenter(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.HEALING_CENTER);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsSupplyShop(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.SUPPLY_SHOP);
	}

	// --- deconstruction ---------------------------------------------------------------------

	/** Sneak-given blueprint: the builder takes the hut down and puts exactly its blocks in the chest. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void takesABuildingDownAndReturnsTheBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate hut = level.getStructureManager().get(TEST_HUT).orElseThrow();
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		hut.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
			level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		BlockPos foreign = HUT_ORIGIN.offset(0, 1, 2);
		helper.setBlock(foreign, Blocks.STONE); // not what the blueprint says: must be left alone
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		s.site().setDeconstruction();
		BuildPlan plan = s.site().plan(level);
		helper.assertTrue(plan.isDeconstruction(), "site should be taking the hut down");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null, "still taking it down: " + s.site().stage()
				+ " " + Math.round(s.site().progress(plan) * 100) + "%");
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) still standing");
			helper.assertBlockPresent(Blocks.STONE, foreign);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25 && chest.countItem(Items.OAK_PLANKS) == 54
					&& chest.countItem(Items.OAK_DOOR) == 1 && chest.countItem(Items.TORCH) == 1 && chest.countItem(ModItems.BLUEPRINT) == 1,
				"chest has " + chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.OAK_PLANKS) + " planks, "
					+ chest.countItem(Items.OAK_DOOR) + " door, " + chest.countItem(Items.TORCH) + " torch, " + chest.countItem(ModItems.BLUEPRINT) + " blueprint");
		});
	}

	// --- permissions -------------------------------------------------------------------------

	/** A builder takes orders from its employer and their friends only (operators aside). */
	@GameTest(template = AREA)
	public void buildersOnlyTakeOrdersFromTheirEmployerAndFriends(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		net.minecraft.server.level.ServerPlayer boss = helper.makeMockServerPlayerInLevel();
		net.minecraft.server.level.ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "an unhired builder takes anyone's orders");
		villager.setAttached(ModAttachments.BUILDER_EMPLOYER, new io.github.jcondedata.aliveworkplace.build.Employer(boss.getUUID(), "boss"));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(boss, villager), "the employer must be obeyed");
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "a stranger must not give orders");

		// A stranger's blueprint is refused and stays in their hand.
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
			BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement(helper, HUT_ORIGIN, Rotation.NONE))));
		Builders.assign(stranger, villager, blueprint);
		helper.assertTrue(Builders.activeSite(level, villager) == null && blueprint.getCount() == 1, "the stranger's blueprint was taken");

		io.github.jcondedata.aliveworkplace.build.Friends friends = io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer());
		friends.add(boss.getUUID(), stranger.getUUID(), "friend");
		try {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "a friend may give orders");
			Builders.assign(stranger, villager, blueprint);
			BuildSite site = Builders.activeSite(level, villager);
			helper.assertTrue(site != null, "the friend's blueprint should start a build");
			helper.assertTrue(Builders.isOwnerOrOp(boss, site), "the employer may cancel a friend's build for their builder");
		} finally {
			friends.remove(boss.getUUID(), stranger.getUUID());
		}
		helper.succeed();
	}

	// --- build queue -----------------------------------------------------------------------

	/** A second blueprint handed to a busy builder waits its turn, then gets built. */
	@GameTest(template = AREA, timeoutTicks = 4800)
	public void buildsQueuedBlueprintsInOrder(GameTestHelper helper) {
		ItemStack[] twoHuts = {new ItemStack(Items.COBBLESTONE, 50), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 46),
			new ItemStack(Items.OAK_DOOR, 2), new ItemStack(Items.TORCH, 2)};
		Setup first = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, twoHuts);
		BuildSite second = Builders.enqueue(first.level(), first.villager(), null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		BuildPlan secondPlan = second.plan(first.level());
		helper.assertTrue(second.isQueued(), "second site should be queued");
		helper.assertTrue(Builders.activeSite(first.level(), first.villager()) == first.site(), "the first site should stay active");
		AtomicBoolean startedEarly = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(first.level()).get(first.site().id()) != null && (second.placed() > 0 || !second.isQueued())) {
				startedEarly.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(startedEarly.get(), "the queued build started before the first one was finished");
			assertBuilt(helper, first);
			assertBuilt(helper, new Setup(first.level(), first.villager(), second, secondPlan));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 2, "both blueprints should be back in the chest");
		});
	}

	@GameTest(template = AREA)
	public void cancellingAQueuedBuildKeepsTheCurrentOne(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		BuildSite queued = Builders.enqueue(s.level(), s.villager(), null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		helper.assertTrue(Builders.queue(s.level(), s.villager()).size() == 1, "queue should hold one site");
		Builders.cancel(s.level(), queued);
		helper.assertTrue(BuildSiteManager.get(s.level()).get(queued.id()) == null, "cancelled site still exists");
		helper.assertTrue(Builders.activeSite(s.level(), s.villager()) == s.site(), "cancelling a queued build must not stop the current one");
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "the queued blueprint should be handed back");
		helper.succeed();
	}

	// --- crews -------------------------------------------------------------------------------

	// Close to the lead's bench (2,2,2): the neighbouring test's bench is ~22 blocks along, and must be further away.
	private static final BlockPos HELPER_BENCH = new BlockPos(5, 2, 2);
	private static final BlockPos HELPER = new BlockPos(5, 2, 3);

	/** An idle builder with a bench nearby pitches in; the build finishes correctly and both earn XP. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "crews")
	public void idleBuildersHelpNearbyBuilds(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, s.level().getServer());
		helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER);
		Builders.employ(s.level(), mate, helper.absolutePos(HELPER_BENCH));
		int mateXp = mate.getVillagerXp();
		AtomicBoolean helped = new AtomicBoolean(false);
		helper.onEachTick(() -> helped.compareAndSet(false, Builders.isHelping(mate)));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
			helper.assertTrue(helped.get(), "the idle builder never joined in");
			int byMate = s.site().placedBy(mate.getUUID(), false);
			int byLead = s.site().placedBy(s.villager().getUUID(), false);
			helper.assertTrue(byMate >= 5, "the helper placed only " + byMate + " block(s), the lead " + byLead);
			helper.assertFalse(Builders.isHelping(mate), "the helper should stop once the build is done");
			helper.assertTrue(mate.getAttachedOrCreate(ModAttachments.BUILDER_BAG).isEmpty(), "the helper kept materials");
		});
	}

	/** Handing a helper its own blueprint takes it off helping. */
	@GameTest(template = AREA, batch = "crews")
	public void aHelperGivenItsOwnBuildLeavesTheCrew(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, s.level().getServer());
		helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER);
		Builders.employ(s.level(), mate, helper.absolutePos(HELPER_BENCH));
		// Sites left over from tests that ran earlier in this spot (their builders are gone) would compete for the helper.
		for (BuildSite other : new java.util.ArrayList<>(io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(s.level()).all())) {
			if (other != s.site() && (other.builder() == null || s.level().getEntity(other.builder()) == null)) {
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(s.level()).remove(other.id());
			}
		}
		BuildSite joined = Builders.recruit(s.level(), mate);
		helper.assertTrue(joined == s.site(), "the idle builder should join the nearby build; joined the one at "
			+ (joined == null ? "none" : joined.bench()));
		BuildSite own = Builders.start(s.level(), mate, null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		helper.assertFalse(Builders.isHelping(mate), "still helping after getting its own build");
		helper.assertTrue(Builders.activeSite(s.level(), mate) == own, "its own build should be active");
		helper.assertTrue(own.bench() != null && own.bench().equals(helper.absolutePos(HELPER_BENCH)), "site should remember the bench");
		helper.succeed();
	}

	// --- keeping work loaded ----------------------------------------------------------------

	/** A build keeps its chunks loaded while the player who ordered it is online, and not otherwise. */
	@GameTest(template = AREA)
	public void workStaysLoadedWhileTheOwnerIsOnline(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		net.minecraft.server.level.ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BuildSite site = Builders.start(level, builder, owner, TEST_HUT, placement(helper, HUT_ORIGIN, Rotation.NONE));
		net.minecraft.world.level.ChunkPos siteChunk = new net.minecraft.world.level.ChunkPos(site.placement().origin());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.KeepLoaded.chunksToKeep(level).contains(siteChunk), "the site's chunk is not kept loaded");
		BuildSite orphan = Builders.start(level, helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3)), null, TEST_HUT,
			placement(helper, new BlockPos(11, 2, 11), Rotation.NONE));
		helper.assertTrue(orphan.owner() != null, "setup");
		helper.assertFalse(level.getServer().getPlayerList().getPlayer(orphan.owner()) != null, "setup: the second site's owner is offline");
		helper.succeed();
	}

	// --- builder levels --------------------------------------------------------------------

	/** Building earns XP; crossing a threshold levels the builder up and unlocks the next blueprint for sale. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildingLevelsTheBuilderUp(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.assertTrue(s.villager().getVillagerData().getLevel() == 1, "builder should start as a novice");
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Villager v = s.villager();
			// 85 blocks placed (/5 = 17 XP) + 10 for finishing, on top of the 1 XP employ() gives.
			helper.assertTrue(v.getVillagerXp() >= 20, "only " + v.getVillagerXp() + " XP after a build");
			helper.assertTrue(v.getVillagerData().getLevel() == 2, "builder is level " + v.getVillagerData().getLevel() + ", expected 2");
			boolean sellsStall = v.getOffers().stream().anyMatch(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(StarterBlueprints.MARKET_STALL.id())).orElse(false));
			helper.assertTrue(sellsStall, "a level 2 builder should sell the Market Stall");
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void higherLevelBuildersWorkFaster(GameTestHelper helper) {
		helper.assertTrue(BuilderLevels.delay(8, 1) == 8, "novice delay " + BuilderLevels.delay(8, 1));
		for (int level = 2; level <= 5; level++) {
			helper.assertTrue(BuilderLevels.delay(8, level) < BuilderLevels.delay(8, level - 1) || BuilderLevels.delay(8, level) == 1,
				"level " + level + " is not faster than level " + (level - 1));
		}
		helper.assertTrue(BuilderLevels.delay(8, 5) == 3, "master delay " + BuilderLevels.delay(8, 5));
		helper.assertTrue(BuilderLevels.delay(0, 5) == 0, "a delay of 0 must stay 0");
		helper.assertTrue(BuilderLevels.speedBonus(1) == 0 && BuilderLevels.speedBonus(5) == 150, "speed bonus " + BuilderLevels.speedBonus(5));
		helper.succeed();
	}

	private void buildStarter(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id())
			.orElseThrow(() -> new GameTestAssertException("missing starter blueprint " + entry.id()));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, new BlockPos(9, 2, 9), Rotation.NONE));
		// Stock barrels with exactly what the plan says it needs.
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4)};
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.assertTrue(stock.size() <= barrels.length * 27, "test needs more barrels for " + entry.id());
		Setup s = setup(helper, entry.id(), new BlockPos(9, 2, 9), Rotation.NONE);
		helper.succeedWhen(() -> assertBuilt(helper, s));
	}

	// --- pure logic ------------------------------------------------------------------------

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable(GameTestHelper helper) {
		for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id());
			helper.assertTrue(blueprint.isPresent(), "missing " + entry.id());
			helper.assertTrue(blueprint.get().size().equals(entry.size()),
				entry.id() + " is " + blueprint.get().size() + " but StarterBlueprints says " + entry.size());
			List<String> unbuildable = blueprint.get().blocks().stream()
				.map(Blueprint.Entry::state)
				.filter(st -> !st.isAir() && !MaterialRules.isSecondaryHalf(st) && MaterialRules.classify(st) == MaterialRules.Kind.SKIP)
				.map(BlockState::toString).distinct().collect(Collectors.toList());
			helper.assertTrue(unbuildable.isEmpty(), entry.id() + " contains blocks builders cannot place: " + unbuildable);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pottedPlantsCostAPotAndThePlant(GameTestHelper helper) {
		List<MaterialRules.Requirement> poppy = MaterialRules.requirements(Blocks.POTTED_POPPY.defaultBlockState(), null);
		helper.assertTrue(poppy.equals(List.of(new MaterialRules.Requirement(Items.FLOWER_POT, 1), new MaterialRules.Requirement(Items.POPPY, 1))),
			"potted poppy costs " + poppy);
		helper.assertTrue(MaterialRules.classify(Blocks.POTTED_CACTUS.defaultBlockState()) == MaterialRules.Kind.DECORATION, "potted plants go in the decoration pass");
		helper.assertTrue(MaterialRules.requirements(Blocks.FLOWER_POT.defaultBlockState(), null).size() == 1, "an empty pot costs just the pot");
		helper.succeed();
	}

	/** Blueprint block data can carry looks (sign text) but never free items or mobs. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintDataNeverHandsOutItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
		helper.setBlock(new BlockPos(1, 1, 0), Blocks.OAK_SIGN);
		BlockPos chestPos = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos signPos = helper.absolutePos(new BlockPos(1, 1, 0));
		CompoundTag chestData = new CompoundTag();
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag diamond = new CompoundTag();
		diamond.putString("id", "minecraft:diamond");
		diamond.putInt("count", 64);
		items.add(diamond);
		chestData.put("Items", items);
		chestData.putString("CustomName", "\"Loot\"");
		CompoundTag chestOut = io.github.jcondedata.aliveworkplace.build.BlockEntityData.sanitize(level, chestPos, level.getBlockState(chestPos),
			level.getBlockEntity(chestPos), chestData);
		helper.assertTrue(chestOut != null && chestOut.getAllKeys().equals(java.util.Set.of("CustomName")), "chest data should keep only its name: " + chestOut);

		CompoundTag signData = new CompoundTag();
		CompoundTag front = new CompoundTag();
		front.putString("color", "black");
		signData.put("front_text", front);
		CompoundTag hidden = new CompoundTag();
		hidden.put("stash", diamond.copy());
		CompoundTag cow = new CompoundTag();
		cow.putString("id", "minecraft:cow");
		hidden.put("pet", cow);
		signData.put("extra", hidden);
		CompoundTag signOut = io.github.jcondedata.aliveworkplace.build.BlockEntityData.sanitize(level, signPos, level.getBlockState(signPos),
			level.getBlockEntity(signPos), signData);
		helper.assertTrue(signOut != null && signOut.contains("front_text"), "sign text should be kept: " + signOut);
		helper.assertTrue(!signOut.getCompound("extra").contains("stash") && !signOut.getCompound("extra").contains("pet"),
			"nested items and mobs should be removed: " + signOut);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintPlacementRoundTrips(GameTestHelper helper) {
		Vec3i size = new Vec3i(9, 10, 7);
		ResourceLocation dim = helper.getLevel().dimension().location();
		BlockPos anchor = new BlockPos(100, 64, -50);
		for (Direction front : Direction.Plane.HORIZONTAL) {
			Rotation rotation = BlueprintItem.rotationFacing(front);
			helper.assertTrue(rotation.rotate(Direction.NORTH) == front, "rotationFacing(" + front + ") is wrong");
			BlueprintData.Placement p = BlueprintItem.placementAt(dim, size, anchor, rotation);
			helper.assertTrue(BlueprintItem.anchorWorld(p, size).equals(anchor), "anchor does not round-trip for " + front);
		}
		// Facing north (player looking south), the build extends away from the player (+Z) and is centred on the click.
		BlueprintData.Placement north = BlueprintItem.placementAt(dim, size, anchor, Rotation.NONE);
		helper.assertTrue(north.origin().equals(anchor.offset(-4, 0, 0)), "unexpected origin " + north.origin());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void buildSitesSurviveSaveAndLoad(GameTestHelper helper) {
		BuildSite site = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Jesse", TEST_HUT,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1, 2, 3), Rotation.CLOCKWISE_180, Mirror.NONE));
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null, "test hut blueprint missing");
		site.advance();
		site.defer();
		site.markPlaced();
		CompoundTag saved = site.save();
		BuildSite loaded = BuildSite.load(saved);
		helper.assertTrue(loaded != null, "failed to load");
		helper.assertValueEqual(loaded.save(), saved, "round-tripped site");
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------

	// --- preview and status --------------------------------------------------------------------

	/** The see-through preview packet carries exactly the blocks a builder places, and survives the network codec. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void previewCarriesTheBlocksToBuild(GameTestHelper helper) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		PreviewNetworking.Data sent = PreviewNetworking.build(blueprint);
		io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
		net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
		PreviewNetworking.Data.CODEC.encode(buf, sent);
		PreviewNetworking.Data data = PreviewNetworking.Data.CODEC.decode(buf);
		helper.assertTrue(data.complete() && data.id().equals(blueprint.id()), "preview header wrong: " + data.id() + " complete=" + data.complete());

		Map<BlockPos, BlockState> wanted = new java.util.HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (MaterialRules.classify(e.state()) != MaterialRules.Kind.SKIP || MaterialRules.isSecondaryHalf(e.state())) {
				wanted.put(e.pos(), e.state());
			}
		}
		int[] b = data.blocks();
		helper.assertTrue(b.length == wanted.size() * 4, "preview has " + b.length / 4 + " blocks, expected " + wanted.size());
		boolean doorTop = false;
		for (int i = 0; i < b.length; i += 4) {
			BlockPos pos = new BlockPos(b[i], b[i + 1], b[i + 2]);
			BlockState state = net.minecraft.world.level.block.Block.stateById(data.palette().get(b[i + 3]));
			helper.assertTrue(state == wanted.get(pos), "preview block at " + pos + " is " + state + ", blueprint has " + wanted.get(pos));
			doorTop |= state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
		}
		helper.assertTrue(doorTop, "the preview should include the top half of the door");
		helper.succeed();
	}

	/** What floats above a builder's head: the build's name, its percentage and progress. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void builderStatusShowsBuildAndProgress(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			BuilderStatusSync.Status status = BuilderStatusSync.status(s.level(), s.site(), s.villager());
			helper.assertTrue(status != null, "no status for a working builder");
			helper.assertTrue(status.entityId() == s.villager().getId(), "status is for the wrong entity");
			helper.assertTrue(status.progress() >= 0.5f, "progress still " + status.progress());
			if (!(status.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title)
				|| !title.getKey().equals("message.aliveworkplace.overhead.title")
				|| !java.util.Objects.equals(title.getArgs()[1], Math.round(status.progress() * 100))) {
				throw new GameTestAssertException("unexpected title: " + status.title());
			}
		});
	}

	// --- what's still missing ----------------------------------------------------------------

	/** A placed blueprint knows what the chests by the nearest bench are short of; blocks already in place don't count. */
	@GameTest(template = AREA)
	public void placedBlueprintKnowsWhatsMissing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		BlueprintData.Placement placement = placement(helper, HUT_ORIGIN, Rotation.NONE);
		BlueprintData data = BlueprintItem.data(BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5))).orElseThrow().withPlacement(Optional.of(placement));
		helper.runAfterDelay(2, () -> { // the bench becomes a point of interest a moment after it's placed
			BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement);
			Map<Item, Integer> need = plan.materials();
			Container chest = helper.getBlockEntity(CHEST);
			int slot = 0;
			for (Map.Entry<Item, Integer> e : need.entrySet()) {
				if (e.getKey() != Items.OAK_PLANKS) {
					slot = stock(chest, slot, e.getKey(), e.getValue());
				}
			}
			var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow();
			helper.assertTrue(report.bench().equals(Optional.of(helper.absolutePos(BENCH))) && report.chests() == 1, "bench and chests: " + report);
			helper.assertTrue(report.missing().size() == 1, "only planks should be missing: " + report.missing());
			int planks = missing(report, Items.OAK_PLANKS);
			helper.assertTrue(planks == need.get(Items.OAK_PLANKS), "planks missing " + planks + " of " + need.get(Items.OAK_PLANKS));

			// A plank already where the blueprint wants one doesn't have to come from the chests.
			BuildPlan.Step plankStep = plan.steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).findFirst().orElseThrow();
			level.setBlockAndUpdate(plankStep.pos(), plankStep.state());
			int after = missing(io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow(), Items.OAK_PLANKS);
			helper.assertTrue(after == planks - 1, "a plank in place still counted: " + after);

			stock(chest, slot, Items.OAK_PLANKS, planks);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow().missing().isEmpty(),
				"everything is in the chest");

			// The item carries the report for its tooltip.
			ItemStack stack = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
			stack.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data);
			io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.tick(level, stack, (int) ((40 - level.getGameTime() % 40) % 40));
			var carried = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.SUPPLY_REPORT);
			helper.assertTrue(carried != null && carried.missing().isEmpty(), "the item has no report: " + carried);
			helper.succeed();
		});
	}

	private static int stock(Container chest, int slot, Item item, int count) {
		while (count > 0) {
			int n = Math.min(count, item.getDefaultMaxStackSize());
			chest.setItem(slot++, new ItemStack(item, n));
			count -= n;
		}
		return slot;
	}

	private static int missing(io.github.jcondedata.aliveworkplace.blueprint.SupplyReport report, Item item) {
		return report.missing().stream().filter(m -> m.item() == item).mapToInt(io.github.jcondedata.aliveworkplace.blueprint.SupplyReport.Missing::count).sum();
	}

	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos originRel, Rotation rotation, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		// Tests in a batch run side by side: keep finished builders from wandering into other tests' builds.
		// The crew tests (their own batch) turn helping back on.
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(CHEST), chestItems);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, originRel, rotation));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Setup(level, villager, site, plan);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos originRel, Rotation rotation) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(originRel), rotation, Mirror.NONE);
	}

	private static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
			"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " progress=" + Math.round(s.site().progress(s.plan()) * 100)
				+ "% missing=" + s.site().missing());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		if (!unfinished.isEmpty()) {
			String sample = unfinished.stream().limit(5)
				.map(p -> helper.relativePos(p) + "=" + s.level().getBlockState(p))
				.collect(Collectors.joining(", "));
			throw new GameTestAssertException(unfinished.size() + " block(s) wrong after the build, e.g. " + sample);
		}
	}

	private static ItemStack[] hutMaterials() {
		return new ItemStack[]{
			new ItemStack(Items.COBBLESTONE, 25),
			new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)
		};
	}

	private static void fill(Container container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
	}
}
