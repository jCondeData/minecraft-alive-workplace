package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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

	private static final BlockPos BENCH = new BlockPos(2, 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, 1, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 1, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 1, 6);

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
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);
				BlockPos plant = new BlockPos(x, 1, z);
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
		// Clockwise 90: template (x, z) -> (-z, x). Origin (12,1,6) puts the hut at x 8..12, z 6..10.
		Setup s = setup(helper, TEST_HUT, new BlockPos(12, 1, 6), Rotation.CLOCKWISE_90, hutMaterials());
		BlockPos doorRel = new BlockPos(12, 2, 8); // template door (2,1,0) -> (0,1,2) + origin
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

	private void buildStarter(GameTestHelper helper, StarterBlueprints.Entry entry) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id())
			.orElseThrow(() -> new GameTestAssertException("missing starter blueprint " + entry.id()));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, new BlockPos(9, 1, 9), Rotation.NONE));
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
		BlockPos[] barrels = {new BlockPos(1, 1, 4), new BlockPos(2, 1, 4), new BlockPos(3, 1, 4), new BlockPos(4, 1, 4)};
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.assertTrue(stock.size() <= barrels.length * 27, "test needs more barrels for " + entry.id());
		Setup s = setup(helper, entry.id(), new BlockPos(9, 1, 9), Rotation.NONE);
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
		BuildPlan plan = site.plan(helper.getLevel().getServer());
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

	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos originRel, Rotation rotation, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(CHEST), chestItems);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, originRel, rotation));
		BuildPlan plan = site.plan(level.getServer());
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
