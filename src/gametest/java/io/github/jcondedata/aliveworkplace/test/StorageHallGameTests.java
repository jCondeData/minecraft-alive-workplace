package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 37.17 without Tom's Simple Storage (plain Fabric): the Storage Hall, the Terminal Nook and the Warehouse are at
 * the Blueprint Table while Milestone 37 is open and hidden while it's closed; each is built as its vanilla build (chests
 * and barrels, no terminal, nothing of Tom's), a builder finishes it with nothing skipped, and it is a Storehouse: its
 * Storehouse block is a job site and every chest and barrel of the build is in its store. With Tom's:
 * {@code StorageHallsCompatTests}.
 */
public class StorageHallGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/** Listed, named, on the Village Map as a trade building, and with a Tom's copy that can't be built here. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStorageBuildsAreAtTheTableAsVanillaBuilds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceLocation> listed = TableServer.listing(level.getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.STORAGE_HALLS) {
			ResourceLocation id = entry.id();
			helper.assertTrue(listed.contains(id), id + " isn't in the Blueprint Table");
			helper.assertTrue(StarterBlueprints.ONE_TIER.contains(entry), id + " isn't a starter build");
			helper.assertTrue(net.minecraft.locale.Language.getInstance().has("blueprint.aliveworkplace." + id.getPath()), id + " has no name");
			helper.assertTrue(VillageMaps.kindOf(id).orElse(null) == VillageMaps.Kind.TRADE, id + " is on the map as " + VillageMaps.kindOf(id));
			// The Tom's copy is there, but its blocks aren't: only Tom's ones are missing, and the build is the vanilla one
			helper.assertTrue(ModdedLibrary.hasCopy(id), id + " has no Tom's copy");
			List<ResourceLocation> missing = ModdedLibrary.missing(id);
			helper.assertTrue(!missing.isEmpty() && missing.stream().allMatch(b -> b.getNamespace().equals("toms_storage")),
				id + "'s copy misses " + missing + ", wanted Tom's blocks only");
			helper.assertTrue(missing.contains(ResourceLocation.fromNamespaceAndPath("toms_storage", "inventory_connector")),
				id + "'s copy has no inventory connector: " + missing);
			FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
			helper.assertTrue(!choice.modded(), id + " chose its Tom's copy without Tom's");
			Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
			Blueprint built = choice.apply(blueprint, id);
			long stores = built.blocks().stream().filter(e -> e.state().is(Blocks.CHEST) || e.state().is(Blocks.BARREL)).count();
			long storehouses = built.blocks().stream().filter(e -> e.state().is(ModBlocks.STOREHOUSE)).count();
			boolean modded = built.blocks().stream().anyMatch(e -> !BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getNamespace().equals("minecraft")
				&& !e.state().is(ModBlocks.STOREHOUSE));
			helper.assertTrue(storehouses == 1, id + " has " + storehouses + " Storehouse blocks");
			helper.assertTrue(!modded, id + " has a modded block in plain Fabric");
			int wanted = entry == StarterBlueprints.TERMINAL_NOOK ? 7 : entry == StarterBlueprints.STORAGE_HALL ? 24 : 48;
			helper.assertTrue(stores >= wanted, id + " has " + stores + " chests and barrels, wanted at least " + wanted);
		}
		helper.succeed();
	}

	/** With Milestone 37 closed the three aren't offered (they still load by id, as every expansion build does). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withMilestone37ClosedTheyAreHidden(GameTestHelper helper) {
		var server = helper.getLevel().getServer();
		boolean was = Expansions.openForTests;
		Expansions.openForTests = false;
		try {
			List<ResourceLocation> listed = BlueprintLibrary.list(server, false);
			for (StarterBlueprints.Entry entry : StarterBlueprints.STORAGE_HALLS) {
				helper.assertTrue(!StarterBlueprints.offered(entry.id()), entry.id() + " is offered with Milestone 37 closed");
				helper.assertTrue(!listed.contains(entry.id()), entry.id() + " is in the Blueprint Table with Milestone 37 closed");
				helper.assertTrue(BlueprintLibrary.get(server, entry.id()).isPresent(), entry.id() + " no longer loads by id");
			}
		} finally {
			Expansions.openForTests = was;
		}
		helper.assertTrue(StarterBlueprints.offered(StarterBlueprints.WAREHOUSE.id()), "the Warehouse isn't offered again");
		helper.succeed();
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"storage_nook_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "storage_nook_build")
	public void aBuilderBuildsTheTerminalNookAsAStorehouse(GameTestHelper helper) {
		build(helper, StarterBlueprints.TERMINAL_NOOK, new BlockPos(2, 1, 4), 8);
	}

	//$ gametest_ticks_batch HUGE_AREA '30000' '"storage_hall_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "storage_hall_build")
	public void aBuilderBuildsTheStorageHallAsAStorehouse(GameTestHelper helper) {
		build(helper, StarterBlueprints.STORAGE_HALL, new BlockPos(6, 1, 5), 31);
	}

	//$ gametest_ticks_batch HUGE_AREA '40000' '"storage_warehouse_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "storage_warehouse_build")
	public void aBuilderBuildsTheWarehouseAsAStorehouse(GameTestHelper helper) {
		build(helper, StarterBlueprints.WAREHOUSE, new BlockPos(8, 2, 6), 61);
	}

	/**
	 * Builds {@code entry} by (9, 2, 3) from barrels holding exactly its materials; when it's done every block is right,
	 * nothing was skipped, the Storehouse block at {@code storehouse} (in the template) is a job site, and the
	 * Storehouse's store holds every one of the build's {@code stores} chests and barrels, each once.
	 */
	static BuildSite build(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos storehouse, int stores) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos origin = new BlockPos(Math.min(9, 28 - entry.size().getX()), 2, Math.min(3, 28 - entry.size().getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		BlockPos board = placement.origin().offset(storehouse);
		BoundingBox box = BoundingBox.fromCorners(placement.origin(), placement.origin().offset(entry.size().getX() - 1, entry.size().getY() - 1,
			entry.size().getZ() - 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			helper.assertTrue(level.getBlockState(board).is(ModBlocks.STOREHOUSE), "no Storehouse at " + storehouse.toShortString() + ": "
				+ level.getBlockState(board));
			helper.assertTrue(level.getPoiManager().getType(board).isPresent(), "the Storehouse is no job site");
			List<BlockPos> store = SupplyContainers.find(level, board, null).stream().filter(box::isInside).toList();
			helper.assertTrue(store.size() == stores, "the Storehouse's store in the build: " + store.size() + " blocks, wanted " + stores);
		});
		return site;
	}
}
