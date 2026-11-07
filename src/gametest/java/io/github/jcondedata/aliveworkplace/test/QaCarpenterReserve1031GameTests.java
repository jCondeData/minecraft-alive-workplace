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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
 * QA (qa-lane-b-1031-1238) for B696, the half its own tests leave out: "with two builds sharing a chest <b>or the
 * Storehouse</b>, the Carpenter turns logs stocked for build B into planks for build A". QaStewardMaterials1031GameTests
 * puts the logs in a chest both benches reach; here they sit in the village's Storehouse, out of both benches' own reach
 * (as the city's storehouse is for most of its builders). Expected (B696): "the Carpenter only crafts from what no open
 * build needs", and still crafts what is spare. Each test has its own batch: the crafters' claims and the chests are
 * shared state.
 */
public class QaCarpenterReserve1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_B = new BlockPos(5, 2, 2);
	static final BlockPos STATION = new BlockPos(13, 2, 2);
	/** The village's Storehouse and its chest: 13 or more blocks from every bench, so neither builder's own chest. */
	private static final BlockPos STOREHOUSE = new BlockPos(18, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(18, 2, 17);
	/** Builder A's own chest by its bench, empty at the start. */
	private static final BlockPos A_CHEST = new BlockPos(0, 2, 2);
	static final BlockPos ROW_A = new BlockPos(6, 2, 8);
	private static final BlockPos ROW_B = new BlockPos(6, 2, 11);
	private static final int COBBLE_AT = 1600;

	/**
	 * A builds 3 spruce planks (none anywhere), B builds 3 cobblestone with 3 spruce logs on them, and the Storehouse
	 * holds exactly B's 3 logs (the cobblestone comes later). The Carpenter must leave B's logs alone: at every tick all
	 * 3 are in the Storehouse, in a bag, on the ground or standing in B's row, and B finishes once its cobblestone comes.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaReserve1031Store"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaReserve1031Store")
	public void aCarpenterLeavesTheStorehouseLogsAnotherBuildNeeds(GameTestHelper helper) {
		Run run = setUp(helper, 3, "store", true);
		helper.onEachTick(() -> {
			long t = run.tick();
			int logs = inChest(helper, Items.SPRUCE_LOG) + inBags(helper, Items.SPRUCE_LOG) + onGround(helper, Items.SPRUCE_LOG)
				+ placed(helper, ROW_B.above(), Blocks.SPRUCE_LOG);
			if (logs < 3) {
				throw new GameTestAssertException("at tick " + t + " only " + logs + " of the 3 spruce logs the Storehouse holds for B's build are left (store "
					+ inChest(helper, Items.SPRUCE_LOG) + ", bags " + inBags(helper, Items.SPRUCE_LOG) + "): the Carpenter made "
					+ (inChest(helper, Items.SPRUCE_PLANKS) + inBags(helper, Items.SPRUCE_PLANKS) + placed(helper, ROW_A, Blocks.SPRUCE_PLANKS))
					+ " spruce planks for A from logs B's open build still needs (B " + run.b().status() + " missing " + run.b().missing() + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.cobble()[0], "the cobblestone isn't there yet");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(run.b().id()) == null, "B's build isn't finished: " + run.b().status() + " missing "
				+ run.b().missing() + ", store " + inChest(helper, Items.SPRUCE_LOG) + " logs");
			helper.assertTrue(placed(helper, ROW_B.above(), Blocks.SPRUCE_LOG) == 3, "B's row has " + placed(helper, ROW_B.above(), Blocks.SPRUCE_LOG) + " of its 3 logs");
		});
	}

	/**
	 * The other side of the boundary: the Storehouse holds one log more than B needs. That log is spare, so the
	 * Carpenter crafts it into A's planks: A's row is finished (it had no other way to get planks), and B finishes too.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaReserve1031StoreSpare"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaReserve1031StoreSpare")
	public void aCarpenterStillCraftsTheStorehousesSpareLog(GameTestHelper helper) {
		Run run = setUp(helper, 4, "spare", true);
		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(run.cobble()[0], "the cobblestone isn't there yet");
			helper.assertTrue(BuildSiteManager.get(level).get(run.a().id()) == null, "A's planks row wasn't finished from the Storehouse's spare log: "
				+ run.a().status() + " missing " + run.a().missing() + ", store " + inChest(helper, Items.SPRUCE_LOG) + " logs");
			helper.assertTrue(BuildSiteManager.get(level).get(run.b().id()) == null, "B's build isn't finished: " + run.b().status() + " missing " + run.b().missing());
			helper.assertTrue(placed(helper, ROW_A, Blocks.SPRUCE_PLANKS) == 3 && placed(helper, ROW_B.above(), Blocks.SPRUCE_LOG) == 3,
				"rows: A " + placed(helper, ROW_A, Blocks.SPRUCE_PLANKS) + "/3 planks, B " + placed(helper, ROW_B.above(), Blocks.SPRUCE_LOG) + "/3 logs");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	record Run(ServerLevel level, long start, BuildSite a, BuildSite b, boolean[] cobble) {
		long tick() {
			return level.getGameTime() - start;
		}
	}

	static Run setUp(GameTestHelper helper, int logs, String name, boolean aChest) {
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
		ResourceLocation planksRow = blueprint(helper, "res_planks_" + name, ROW_A, new Vec3i(3, 1, 1), at -> Blocks.SPRUCE_PLANKS.defaultBlockState());
		ResourceLocation logRow = blueprint(helper, "res_logs_" + name, ROW_B, new Vec3i(3, 2, 1),
			at -> at.getY() == ROW_B.getY() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STORE_CHEST);
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, logs));
		if (aChest) {
			helper.setBlock(A_CHEST, Blocks.CHEST); // A's own (empty) chest, where the Carpenter brings what he makes
		}
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		BuildSite siteA = start(helper, a, planksRow, ROW_A);
		BuildSite siteB = start(helper, b, logRow, ROW_B);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		Run run = new Run(level, level.getGameTime(), siteA, siteB, new boolean[] {false});
		helper.onEachTick(() -> {
			if (!run.cobble()[0] && run.tick() >= COBBLE_AT) {
				run.cobble()[0] = true;
				ItemStack rest = SupplyContainers.insert(level, List.of(helper.absolutePos(STORE_CHEST)), new ItemStack(Items.COBBLESTONE, 3));
				helper.assertTrue(rest.isEmpty(), "setup: no room for the cobblestone");
			}
		});
		return run;
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

	/** A small blueprint drawn in the world at {@code origin}, saved, and the spot cleared again. */
	private static ResourceLocation blueprint(GameTestHelper helper, String name, BlockPos origin, Vec3i size,
											  java.util.function.Function<BlockPos, BlockState> block) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			helper.setBlock(at, block.apply(at));
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1031_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (BlockPos at : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			helper.setBlock(at, Blocks.AIR);
		}
		return id;
	}

	static int inChest(GameTestHelper helper, Item item) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(STORE_CHEST)) instanceof Container c)) {
			return 0;
		}
		return c.countItem(item);
	}

	private static int inBags(GameTestHelper helper, Item item) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** How many of the 3-long row from {@code from} along x are {@code block}. */
	static int placed(GameTestHelper helper, BlockPos from, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (int x = 0; x < 3; x++) {
			if (helper.getBlockState(from.offset(x, 0, 0)).is(block)) {
				n++;
			}
		}
		return n;
	}
}
