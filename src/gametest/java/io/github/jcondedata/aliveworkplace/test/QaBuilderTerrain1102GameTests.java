package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-02, lane-b-1102-1838's tester) of a Builder building the test hut where the terrain fights back, written
 * from the spec (CLAUDE.md "How the builder works"; docs/wiki/builder.md "Stages": CLEAR takes away what is in the way,
 * FOUNDATION fills the gap under the bottom layer down to solid ground, LANDSCAPE never waits for materials; ROADMAP
 * 23.1 "every build finishes; no item is duplicated or lost"), not from the code:
 * <ul>
 * <li>a site full of sand to the top of the hut's box (with sand above it too: QaBuilderTerrain1102BugTests);</li>
 * <li>a pond beside the doorway that floods the site while it is built;</li>
 * <li>lava pools in the footprint with fire spread on, beside a wooden hut;</li>
 * <li>a crack in a stone plateau under the floor and past it, with no dirt for LANDSCAPE;</li>
 * <li>an oak growing through the footprint, leaves over and round the box;</li>
 * <li>snow, grass, tall grass and flowers over the footprint, never counted as missing materials.</li>
 * </ul>
 * Every test builds the test hut (5x4x5: a cobblestone floor, plank walls and roof, a door and a torch) from the chest and
 * barrels by the bench, free materials off, holding exactly the hut's list plus {@link #EXTRA} of each.
 */
public class QaBuilderTerrain1102GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	static final int EXTRA = 2;

	record Run(ServerLevel level, Villager[] builder, UUID id, BuildPlan plan, int ground, List<BlockPos> supplies, String[] died) {
		BuildSite site() {
			return BuildSiteManager.get(level).get(id);
		}
	}

	// ------------------------------------------------------------------------------------------------ sand and gravel

	/**
	 * The site is full of sand, the hut's whole 5x4x5 box and no higher (nothing above it to fall in). CLEAR takes it all
	 * away top-down: the finished hut is as drawn with no sand inside, and every material adds up. (With sand standing
	 * above the box too, see QaBuilderTerrain1102BugTests.)
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainSandBox"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainSandBox")
	public void aSiteFullOfSandToTheTopOfTheBoxIsAllDugOut(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				for (int y = 2; y <= 5; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.SAND);
				}
			}
		}
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoForeign(helper, r, s -> s.getBlock() instanceof FallingBlock);
			assertNothingFalling(helper, r);
			assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------------ water and lava

	/**
	 * A pond's source just outside the doorway (walled with glass so it can only run into the site) floods the site while
	 * the hut goes up. Once the hut is done no water is left inside it, every block (the torch too, which water washes
	 * off a wall) stands as drawn, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainPond"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainPond")
	public void aPondBesideTheDoorwayLeavesNoWaterInTheFinishedHut(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		// the door is the hut's (2, 1, 0): (8, 3, 6); the source is in front of it
		BlockPos source = new BlockPos(8, 3, 5);
		helper.setBlock(source.below(), Blocks.STONE);
		helper.setBlock(source.west(), Blocks.GLASS);
		helper.setBlock(source.east(), Blocks.GLASS);
		helper.setBlock(source.north(), Blocks.GLASS);
		helper.setBlock(source.west().below(), Blocks.GLASS);
		helper.setBlock(source.east().below(), Blocks.GLASS);
		helper.setBlock(source, Blocks.WATER);
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(source).is(Blocks.WATER), "setup: the pond's source is gone: " + helper.getBlockState(source));
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			// the stone under the source stands one above the lawn beside the hut: LANDSCAPE may dig it, and stone mined
			// drops cobblestone, the floor's material
			boolean dug = !helper.getBlockState(source.below()).is(Blocks.STONE);
			assertNothingMadeOrLost(helper, r, Map.of(net.minecraft.world.item.Items.COBBLESTONE, dug ? 1 : 0));
		});
	}

	/**
	 * Three lava pools lie where the hut's floor goes, with fire spread on: the builder isn't hurt to death, the
	 * wooden hut ends as drawn with no lava or fire in or round it, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainLava"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainLava")
	public void lavaPoolsInTheFootprintWithFireOnBurnNothingAndKillNoOne(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
		BlockPos origin = new BlockPos(6, 2, 6);
		for (BlockPos p : List.of(new BlockPos(8, 2, 8), new BlockPos(9, 2, 9), new BlockPos(9, 2, 8))) {
			helper.setBlock(p, Blocks.LAVA);
		}
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			BoundingBox box = r.plan().bounds().inflatedBy(3);
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				helper.assertFalse(r.level().getBlockState(p).is(BlockTags.FIRE), "fire at " + helper.relativePos(p) + " by the finished hut");
			}
			assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------ uneven ground below

	/**
	 * The ground is a stone plateau four high (the bench, the chests and the builder on it) with a crack one wide and
	 * four deep running under the middle of the hut's floor and two blocks past it. FOUNDATION ("fill the gap under the
	 * bottom layer down to solid ground") fills the crack under the floor with the floor's block all the way down; the
	 * chest has no dirt, and LANDSCAPE never waits for materials, so the build still ends.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainCrack"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainCrack")
	public void aCrackUnderTheFloorIsFilledToTheBottomAndTheBuildEndsWithNoDirt(GameTestHelper helper) {
		prepare(helper);
		int g = 6;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < g; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 8 && z >= 6 && z <= 12 ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		Run r = start(helper, g, new BlockPos(6, g, 6));
		helper.assertTrue(r.plan().materials().getOrDefault(net.minecraft.world.item.Items.DIRT, 0) == 0, "setup: the hut's list asks for dirt");
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> open = new ArrayList<>();
			for (int z = 6; z <= 10; z++) {
				for (int y = 2; y < g; y++) {
					BlockState s = helper.getBlockState(new BlockPos(8, y, z));
					if (s.isAir() || s.canBeReplaced()) {
						open.add("(8," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
			helper.assertTrue(open.isEmpty(), "the crack under the floor wasn't filled down to solid ground: " + open);
			assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------- plants on the site

	/**
	 * An oak grows through the middle of the footprint, its trunk seven high (three above the hut's box) and leaves
	 * over the top two layers of the box and a block past its sides. CLEAR takes away what is in the way: no log or leaf
	 * is left inside the finished hut, which stands as drawn, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainTree"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainTree")
	public void anOakThroughTheFootprintLeavesNoLogOrLeafInTheHut(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int x = 5; x <= 11; x++) {
			for (int z = 5; z <= 11; z++) {
				for (int y = 4; y <= 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), leaves);
				}
			}
		}
		for (int y = 2; y <= 8; y++) {
			helper.setBlock(new BlockPos(8, y, 8), Blocks.OAK_LOG);
		}
		Run r = start(helper, 2, origin);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoForeign(helper, r, s -> s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES));
			assertNothingMadeOrLost(helper, r);
		});
	}

	/**
	 * The footprint (on a grass lawn) is covered with snow layers one to eight deep, short grass, tall grass (both
	 * halves), ferns, poppies and dandelions. CLEAR takes them away and none is ever asked for: at no tick does the site
	 * list snow, grass, seeds or flowers as missing, nor anything the hut's list doesn't have. The hut ends as drawn with
	 * nothing of them inside, and every material adds up.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaTerrainSnowGrass"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaTerrainSnowGrass")
	public void snowGrassAndFlowersOnTheSiteAreClearedAndNeverAskedFor(GameTestHelper helper) {
		prepare(helper);
		BlockPos origin = new BlockPos(6, 2, 6);
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(110211L);
		for (int x = 5; x <= 11; x++) {
			for (int z = 5; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				BlockPos p = new BlockPos(x, 2, z);
				switch (random.nextInt(6)) {
					case 0 -> helper.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(8)));
					case 1 -> helper.setBlock(p, Blocks.SHORT_GRASS);
					case 2 -> {
						helper.setBlock(p, Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
						helper.setBlock(p.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
					}
					case 3 -> helper.setBlock(p, Blocks.FERN);
					case 4 -> helper.setBlock(p, Blocks.POPPY);
					default -> helper.setBlock(p, Blocks.DANDELION);
				}
			}
		}
		Run r = start(helper, 2, origin);
		Set<Item> hutList = r.plan().materials().keySet();
		List<String> asked = new ArrayList<>();
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (now == null || !asked.isEmpty()) {
				return;
			}
			for (Map.Entry<Item, Integer> e : now.missing().entrySet()) {
				if (!hutList.contains(e.getKey())) {
					asked.add(e.getValue() + " " + e.getKey() + " (stage " + now.stage() + ", tick " + helper.getTick() + ")");
				}
			}
		});
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(asked.isEmpty(), "the site asked for what the hut's list doesn't have: " + asked);
			assertFinished(helper, r);
			assertNoForeign(helper, r, s -> s.is(Blocks.SNOW) || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
				|| s.is(BlockTags.FLOWERS));
			assertNothingMadeOrLost(helper, r);
		});
	}

	// ------------------------------------------------------------------------------------------------ setup helpers

	/** Clears what earlier batches left and sets the build rules (free materials off), put back after the test. */
	static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
	}

	/**
	 * QaBuilderDisruptionGameTests' layout on ground level {@code g}: a Builder's Bench at (2, g, 2), a chest and four
	 * barrels along z = 4, the builder at (3, g, 3), the hut at {@code origin}, the hut's list plus EXTRA of each stocked.
	 */
	static Run start(GameTestHelper helper, int g, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, g, 2);
		BlockPos chest = new BlockPos(2, g, 4);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, g, 4), new BlockPos(3, g, 4), new BlockPos(4, g, 4), new BlockPos(5, g, 4));
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(table));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, g, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement);
		if (site == null) {
			throw new GameTestAssertException("the hut didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + TEST_HUT);
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			int left = n + EXTRA;
			while (left > 0) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		// the first barrels' slots, so the chest keeps room for what CLEAR takes away
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		LOGGER.info("qa terrain: materials {}, {} steps, bounds {}", plan.materials(), plan.placeableCount(), plan.bounds());
		return new Run(level, new Villager[] {builder}, id, plan, g, supplies, new String[1]);
	}

	static void watchDeath(GameTestHelper helper, Run r) {
		helper.onEachTick(() -> {
			Villager b = r.builder()[0];
			if (r.died()[0] == null && !b.isAlive() && b.getRemovalReason() != Entity.RemovalReason.DISCARDED) {
				r.died()[0] = state(helper, r) + " (last hurt by " + b.getLastDamageSource() + ")";
			}
		});
	}

	/** As a save and a load of the world would: the build sites from their save, the builder from its. */
	static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
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

	// ------------------------------------------------------------------------------------------------- assertions

	static String state(GameTestHelper helper, Run r) {
		BuildSite s = r.site();
		Villager b = r.builder()[0];
		return (s == null ? "site done" : "stage=" + s.stage() + " status=" + s.status() + " skipped=" + s.skipped() + " missing=" + s.missing())
			+ " builder alive=" + b.isAlive() + " at " + helper.relativePos(b.blockPosition());
	}

	/** The builder never died, the build is over and every block is as drawn. */
	static void assertFinished(GameTestHelper helper, Run r) {
		helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
		helper.assertTrue(r.site() == null, "still building: " + state(helper, r));
		List<BlockPos> wrong = r.plan().unfinished(r.level());
		if (!wrong.isEmpty()) {
			throw new GameTestAssertException(wrong.size() + " block(s) not as drawn after the build, e.g. " + wrong.stream().limit(20)
				.map(p -> helper.relativePos(p).toShortString() + "=" + r.level().getBlockState(p)).collect(Collectors.joining(", ")));
		}
	}

	/** No block {@code bad} stands anywhere in the hut's box. */
	static void assertNoForeign(GameTestHelper helper, Run r, Predicate<BlockState> bad) {
		List<String> found = new ArrayList<>();
		BoundingBox box = r.plan().bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = r.level().getBlockState(p);
			if (bad.test(s)) {
				found.add(helper.relativePos(p).toShortString() + "=" + s.getBlock().getDescriptionId());
			}
		}
		helper.assertTrue(found.isEmpty(), found.size() + " block(s) of the terrain left in the finished hut: "
			+ found.stream().limit(8).collect(Collectors.joining(", ")));
	}

	static int count(Run r, Predicate<BlockState> what) {
		int n = 0;
		BoundingBox box = r.plan().bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (what.test(r.level().getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	static void assertNothingFalling(GameTestHelper helper, Run r) {
		BoundingBox b = r.plan().bounds();
		List<FallingBlockEntity> falling = r.level().getEntitiesOfClass(FallingBlockEntity.class,
			new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1));
		helper.assertTrue(falling.isEmpty(), falling.size() + " block(s) still falling into the finished hut");
	}

	/** No water or lava stands in the hut's box (a waterlogged block of the drawing aside). */
	static void assertNoFluid(GameTestHelper helper, Run r) {
		BoundingBox box = r.plan().bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			var f = r.level().getFluidState(p);
			BlockState s = r.level().getBlockState(p);
			helper.assertTrue(f.isEmpty() || !(f.is(FluidTags.WATER) || f.is(FluidTags.LAVA)) || s.hasProperty(BlockStateProperties.WATERLOGGED),
				"fluid in the finished hut at " + helper.relativePos(p).toShortString() + ": " + s);
		}
	}

	/** {@code item} in the supplies, in every villager's bag near the test and lying on the ground near it. */
	static int loose(GameTestHelper helper, Run r, Item item) {
		AABB around = helper.getBounds().inflate(16);
		int n = 0;
		for (BlockPos p : r.supplies()) {
			n += ((Container) helper.getBlockEntity(p)).countItem(item);
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, around)) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** After the build every one of the hut's materials left loose is exactly the EXTRA stocked over its list. */
	static void assertNothingMadeOrLost(GameTestHelper helper, Run r) {
		assertNothingMadeOrLost(helper, r, Map.of());
	}

	/** The same, with {@code adjust} more of an item expected loose (what CLEAR or LANDSCAPE dug up that drops as it). */
	static void assertNothingMadeOrLost(GameTestHelper helper, Run r, Map<Item, Integer> adjust) {
		Map<Item, Integer> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			int got = loose(helper, r, e.getKey());
			if (got != EXTRA + adjust.getOrDefault(e.getKey(), 0)) {
				off.put(e.getKey(), got);
			}
		}
		helper.assertTrue(off.isEmpty(), "materials made or lost: left loose (chests, bags, ground) " + off + ", wanted " + EXTRA
			+ " of each over the hut's list " + r.plan().materials());
		LOGGER.info("qa terrain: the hut at {} checked at tick {}", r.plan().bounds(), helper.getTick());
	}
}
