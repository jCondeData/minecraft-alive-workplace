package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (2026-10-31): the edge of a builder's vertical reach for B696's reservations. Last night's mutation run left
 * {@code SupplyContainers.inReach}'s {@code |dy| <= 4} turned into {@code < 4} alive. A chest exactly 4 blocks above or
 * below a Builder's Bench is that bench's supply ({@code SupplyContainers.find} lists it), so what that bench's open build
 * still needs in it is kept back from the other builds and from the Carpenter ({@code Builders.reservedIn}); a chest 5
 * blocks away is not, and holds nothing back.
 *
 * <p>Set-up, from the player's side: a stone deck over part of the area makes two floors. Build A's bench and the shared
 * chest stand on one floor with a Carpenter, build B's bench on the other, exactly 4 (or 5) blocks above or below the
 * chest. A builds a row of 3 spruce planks (none in the chest: the Carpenter has to make them); B builds 3 cobblestone with
 * 3 spruce logs on top and the chest holds exactly those logs (B waits for its cobblestone, which never comes). Each test
 * has its own batch (the crafters' claims and the build sites are shared state).
 */
public class QaSupplyReachEdge1031GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final int LOW = 2;
	/** The deck's stone layer is at {@code deckTop - 1}; the upper floor stands on it at {@code deckTop}. */
	private static final int DECK_MAX_X = 13;
	private static final int DECK_MAX_Z = 10;
	/** When the spare log comes (the Carpenter has had well over a thousand ticks to touch B's logs by then). */
	private static final int SPARE_AT = 1200;

	/**
	 * The shared chest stands exactly 4 blocks above B's bench, so it is B's supply and B's 3 logs in it are reserved:
	 * the Carpenter, working for A on the chest's floor, never crafts them into planks. Then one spare log arrives; the
	 * Carpenter crafts that one for A, A finishes, and B's 3 logs are all still there.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaReach1031Above4"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaReach1031Above4")
	public void logsInAChestFourBlocksAboveAnotherBuildsBenchStayItsOwn(GameTestHelper helper) {
		reserved(helper, LOW + 4, LOW);
	}

	/** The same with the shared chest exactly 4 blocks below B's bench (B on the deck, the chest and A underneath). */
	//$ gametest_ticks_batch BIG '4000' '"qaReach1031Below4"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaReach1031Below4")
	public void logsInAChestFourBlocksBelowAnotherBuildsBenchStayItsOwn(GameTestHelper helper) {
		reserved(helper, LOW, LOW + 4);
	}

	/**
	 * The other side of the edge: the chest stands 5 blocks above B's bench, out of B's reach, so it is not B's supply
	 * and B's need doesn't hold its logs back. The Carpenter crafts one into planks for A, and A finishes.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaReach1031Above5"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaReach1031Above5")
	public void logsInAChestFiveBlocksAboveAnotherBuildsBenchAreSpare(GameTestHelper helper) {
		Setup s = setUp(helper, LOW + 5, LOW);
		helper.assertFalse(SupplyContainers.find(helper.getLevel(), helper.absolutePos(s.benchB), null).contains(helper.absolutePos(s.chest)),
			"setup: a chest 5 blocks above B's bench is listed as B's supply");
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> helper.assertTrue(BuildSiteManager.get(level).get(s.siteA.id()) == null,
			"A's planks row isn't finished though the chest's logs are 5 blocks above B's bench (out of its reach, so not B's to keep): A "
				+ s.siteA.status() + " missing " + s.siteA.missing() + ", chest " + contents(helper, s.chest) + ", planks made "
				+ planks(helper, s)));
	}

	// ------------------------------------------------------------------------------------------------- set-up

	private record Setup(BlockPos chest, BlockPos benchB, BuildSite siteA, BuildSite siteB, BlockPos rowA, BlockPos rowB) {
	}

	private static void reserved(GameTestHelper helper, int chestY, int benchBY) {
		Setup s = setUp(helper, chestY, benchBY);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(SupplyContainers.find(level, helper.absolutePos(s.benchB), null).contains(helper.absolutePos(s.chest)),
			"setup: a chest " + Math.abs(chestY - benchBY) + " blocks from B's bench isn't listed as B's supply");
		long start = level.getGameTime();
		boolean[] spare = {false};
		helper.onEachTick(() -> {
			long t = level.getGameTime() - start;
			if (!spare[0] && t >= SPARE_AT) {
				spare[0] = true;
				ItemStack rest = SupplyContainers.insert(level, List.of(helper.absolutePos(s.chest)), new ItemStack(Items.SPRUCE_LOG, 1));
				helper.assertTrue(rest.isEmpty(), "setup: no room for the spare log");
			}
			int logs = inChest(helper, s.chest, Items.SPRUCE_LOG) + inBags(helper, Items.SPRUCE_LOG) + onGround(helper, Items.SPRUCE_LOG)
				+ placed(helper, s.rowB.above(), Blocks.SPRUCE_LOG);
			if (logs < 3) {
				throw new GameTestAssertException("at tick " + t + " only " + logs + " of the 3 spruce logs B's open build needs are left, in a chest "
					+ Math.abs(chestY - benchBY) + " blocks " + (chestY > benchBY ? "above" : "below") + " B's bench (B's supply): the Carpenter made "
					+ planks(helper, s) + " spruce planks for A out of them (B " + s.siteB.status() + " missing " + s.siteB.missing() + ", chest "
					+ contents(helper, s.chest) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(spare[0], "the spare log isn't there yet");
			helper.assertTrue(BuildSiteManager.get(level).get(s.siteA.id()) == null, "A's planks row isn't finished from the spare log: A "
				+ s.siteA.status() + " missing " + s.siteA.missing() + ", chest " + contents(helper, s.chest) + ", planks made " + planks(helper, s));
		});
	}

	/**
	 * Two floors: the ground ({@link #LOW}) and a stone deck. The chest, A's bench, A's row and the Carpenter are on the
	 * chest's floor; B's bench on {@code benchBY}. Each floor's row stands where that floor is open to the sky (the upper
	 * row on the deck, the lower one past its edge).
	 */
	private static Setup setUp(GameTestHelper helper, int chestY, int benchBY) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		int deckTop = Math.max(chestY, benchBY);
		for (int x = 0; x <= DECK_MAX_X; x++) {
			for (int z = 0; z <= DECK_MAX_Z; z++) {
				helper.setBlock(new BlockPos(x, deckTop - 1, z), Blocks.STONE);
			}
		}
		BlockPos upperRow = new BlockPos(6, deckTop, 8);
		BlockPos lowerRow = new BlockPos(6, LOW, 15);
		BlockPos rowA = chestY == deckTop ? upperRow : lowerRow;
		BlockPos rowB = benchBY == deckTop ? upperRow : lowerRow;
		BlockPos chest = new BlockPos(3, chestY, 4);
		BlockPos benchA = new BlockPos(2, chestY, 2);
		BlockPos benchB = new BlockPos(5, benchBY, 2);
		BlockPos station = new BlockPos(10, chestY, 2);
		ResourceLocation planksRow = blueprint(helper, "reach_planks", rowA, new Vec3i(3, 1, 1), at -> Blocks.SPRUCE_PLANKS.defaultBlockState());
		ResourceLocation logRow = blueprint(helper, "reach_logs", rowB, new Vec3i(3, 2, 1),
			at -> at.getY() == rowB.getY() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
		helper.setBlock(chest, Blocks.CHEST);
		Container box = helper.getBlockEntity(chest);
		box.setItem(0, new ItemStack(Items.SPRUCE_LOG, 3));
		Villager a = builder(helper, benchA);
		Villager b = builder(helper, benchB);
		BuildSite siteA = start(helper, a, planksRow, rowA);
		BuildSite siteB = start(helper, b, logRow, rowB);
		helper.setBlock(station, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(level, carpenter, helper.absolutePos(station), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return new Setup(chest, benchB, siteA, siteB, rowA, rowB);
	}

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
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

	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A small blueprint drawn in the world at {@code origin} ({@code size}), saved and the spot cleared again. */
	private static ResourceLocation blueprint(GameTestHelper helper, String name, BlockPos origin, Vec3i size,
											  java.util.function.Function<BlockPos, BlockState> block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					BlockPos at = origin.offset(x, y, z);
					helper.setBlock(at, block.apply(at));
				}
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1031_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
				}
			}
		}
		return id;
	}

	// ------------------------------------------------------------------------------------------------- counting

	private static int inChest(GameTestHelper helper, BlockPos pos, Item item) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c)) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static String contents(GameTestHelper helper, BlockPos pos) {
		Map<String, Integer> out = new TreeMap<>();
		if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty()) {
					out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
				}
			}
		}
		return out.toString();
	}

	private static int planks(GameTestHelper helper, Setup s) {
		return inChest(helper, s.chest, Items.SPRUCE_PLANKS) + inBags(helper, Items.SPRUCE_PLANKS) + onGround(helper, Items.SPRUCE_PLANKS)
			+ placed(helper, s.rowA, Blocks.SPRUCE_PLANKS);
	}

	private static int inBags(GameTestHelper helper, Item item) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** How many of the 3 blocks along x from {@code from} are {@code block}. */
	private static int placed(GameTestHelper helper, BlockPos from, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (int x = 0; x < 3; x++) {
			if (helper.getBlockState(from.offset(x, 0, 0)).is(block)) {
				n++;
			}
		}
		return n;
	}
}
