package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 37.17 with Tom's Simple Storage: the Storage Hall, the Terminal Nook and the Warehouse are built from their
 * Tom's copies (the modded library, 37.12). A builder finishes each with nothing skipped; its inventory connector joins
 * exactly the chests, barrels and filing cabinets drawn round it or on its cables, the Storehouse reads them through the
 * connector (37.10) so each item counts once, and the build's terminal opens Tom's own screen listing them. Without
 * Tom's: {@code StorageHallGameTests}.
 */
public class StorageHallsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String TOMS = "toms_storage";

	/** With Tom's the three are at the Blueprint Table and every new one is built from its Tom's copy. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withTomsTheStorageBuildsUseTheirTomsCopies(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.STORAGE_HALLS) {
			helper.assertTrue(listed.contains(entry.id()), entry.id() + " isn't in the Blueprint Table");
			helper.assertTrue(ModdedLibrary.complete(entry.id()), entry.id() + "'s Tom's copy misses " + ModdedLibrary.missing(entry.id()));
			helper.assertTrue(FurnitureChoice.forNewBuild(entry.id()).modded(), entry.id() + " isn't built from its Tom's copy");
		}
		helper.succeed();
	}

	/** The Terminal Nook: six chests round the connector, the storage terminal before it. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"toms_nook_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "toms_nook_build")
	public void aBuilderBuildsTheTerminalNookOnTomsNetwork(GameTestHelper helper) {
		build(helper, StarterBlueprints.TERMINAL_NOOK, new BlockPos(2, 1, 4), new BlockPos(4, 1, 6), 6,
			new BlockPos(4, 1, 5), Direction.NORTH, "storage_terminal");
	}

	/** The Storage Hall: 27 chests and barrels round the connector, two filing cabinets on its cable, terminals on the counter. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"toms_hall_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "toms_hall_build")
	public void aBuilderBuildsTheStorageHallOnTomsNetwork(GameTestHelper helper) {
		build(helper, StarterBlueprints.STORAGE_HALL, new BlockPos(6, 1, 5), new BlockPos(6, 1, 8), 29,
			new BlockPos(5, 1, 5), Direction.UP, "storage_terminal");
	}

	/** The Warehouse: 60 chests and barrels on two floors, joined through the trims, the crafting terminal at the counter. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"toms_warehouse_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "toms_warehouse_build")
	public void aBuilderBuildsTheWarehouseOnTomsNetwork(GameTestHelper helper) {
		build(helper, StarterBlueprints.WAREHOUSE, new BlockPos(8, 2, 6), new BlockPos(9, 1, 6), 60,
			new BlockPos(8, 1, 6), Direction.WEST, "crafting_terminal");
	}

	/**
	 * Builds {@code entry} by (9, 2, 3) from barrels holding exactly its materials. When it's done: every block is right
	 * and nothing was skipped; the connector (at {@code connector} in the template) joins {@code members} inventories, all
	 * inside the build; the Storehouse at {@code storehouse} lists the connector and none of them; cobblestone put in the
	 * first and the last of them counts once through the store; the terminal (its screen on its {@code screen} side)
	 * opens Tom's menu and lists the same.
	 */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos storehouse, BlockPos connector, int members,
		BlockPos terminal, Direction screen, String terminalBlock) {
		PartnerShowsCompatTests.clearLeftovers(helper);
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
		helper.assertTrue(plan.materials().keySet().stream().anyMatch(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(TOMS)),
			entry.id() + " is planned without Tom's blocks: " + plan.materials().keySet());
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
		BlockPos head = placement.origin().offset(connector);
		BlockPos board = placement.origin().offset(storehouse);
		BlockPos screenAt = placement.origin().offset(terminal);
		boolean[] filled = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			helper.assertTrue(id(level, head).equals(TOMS + ":inventory_connector"), "no connector at " + connector.toShortString() + ": " + level.getBlockState(head));
			helper.assertTrue(id(level, screenAt).equals(TOMS + ":" + terminalBlock), "no " + terminalBlock + " at " + terminal.toShortString());
			Set<BlockPos> joined = StorageNetworks.members(level, head);
			helper.assertTrue(joined.size() == members, "the connector joins " + joined.size() + " inventories, wanted " + members);
			net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
				placement.origin(), placement.origin().offset(entry.size().getX() - 1, entry.size().getY() - 1, entry.size().getZ() - 1));
			helper.assertTrue(joined.stream().allMatch(box::isInside), "the connector joins inventories outside the build");
			List<BlockPos> store = SupplyContainers.find(level, board, null);
			helper.assertTrue(store.contains(head), "the Storehouse doesn't read the connector: " + store.size() + " stores");
			helper.assertTrue(store.stream().noneMatch(joined::contains), "a joined inventory is also its own store");
			if (!filled[0]) { // once: cobblestone in the first and last chest or barrel the connector joins
				// (chests and barrels: a filing cabinet takes only what doesn't stack)
				List<BlockPos> list = joined.stream().filter(p -> level.getBlockState(p).is(Blocks.CHEST) || level.getBlockState(p).is(Blocks.BARREL)).toList();
				helper.assertTrue(SupplyContainers.insert(level, List.of(list.get(0)), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "no room");
				helper.assertTrue(SupplyContainers.insert(level, List.of(list.get(list.size() - 1)), new ItemStack(Items.COBBLESTONE, 5)).isEmpty(), "no room");
				filled[0] = true;
			}
			long counted = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(counted == 15, "the Storehouse counts " + counted + " cobblestone, wanted 15 (each once)");
			// The terminal: Tom's own menu, listing the network
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				player.setGameMode(GameType.SURVIVAL);
				Vec3 face = Vec3.atCenterOf(screenAt).relative(screen, 0.5);
				// Standing in front of the screen: beside it, or (a screen facing up, set in a counter) on the aisle north of it
				BlockPos feet = screen.getAxis().isVertical() ? screenAt.north() : screenAt.relative(screen);
				player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(face, screen, screenAt, false));
				String menu = player.containerMenu.getClass().getName();
				helper.assertTrue(menu.contains("TerminalMenu"), "using the " + terminalBlock + " opened " + menu + ", not Tom's terminal");
				long listed = stacks(screenAt, level).getOrDefault(Items.COBBLESTONE, 0L);
				helper.assertTrue(listed == 15, "the terminal lists " + listed + " cobblestone, wanted 15");
			} finally {
				level.getServer().getPlayerList().remove(player);
			}
		});
	}

	private static String id(ServerLevel level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
	}

	/** What Tom's terminal lists, by item (read by reflection: never compiled against Tom's classes). */
	private static Map<Item, Long> stacks(BlockPos terminal, ServerLevel level) {
		Map<Item, Long> out = new HashMap<>();
		try {
			Object be = level.getBlockEntity(terminal);
			if (be == null) {
				throw new GameTestAssertException("the terminal has no block entity");
			}
			Map<?, ?> stacks = (Map<?, ?>) be.getClass().getMethod("getStacks").invoke(be);
			for (Object stored : stacks.values()) {
				ItemStack stack = (ItemStack) stored.getClass().getMethod("getStack").invoke(stored);
				long n = (Long) stored.getClass().getMethod("getQuantity").invoke(stored);
				out.merge(stack.getItem(), n, Long::sum);
			}
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't read Tom's terminal: " + e);
		}
		return out;
	}
}
