package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-21, independent of the author) for ROADMAP 50.13's three rebuilt Create builds, from their briefs:
 * the Pump House (18 x 17 x 13), the Train Station (19 x 18 x 17) and the Terminal Nook (11 x 15 x 12),. The tests that fail
 * because the mod is wrong are in QaRebuiltCreate1121eBugCompatTests (on tests/rebuilt-create-1121e).
 */
public class QaRebuiltCreate1121eCompatTests implements FabricGameTest {
	static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	static final String TOMS = "toms_storage";


	// ---- 2. The Pump House ---------------------------------------------------------------------------------------------

	/**
	 * The Pump House with a save and load of the world while its structure goes up (the build sites and the builder from
	 * their saves): it goes on to the end, every block as drawn, nothing skipped, the cauldron and the two chests at their
	 * spots, and the wheel's cogwheel turns the pump.
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e_pump_house_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e_pump_house_reload")
	public void aPumpHouseReloadedMidBuildIsFinishedAndPumps(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.PUMP_HOUSE, List.of());
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(b.level).get(b.id);
			if (!reloaded[0] && now != null && now.stage() == BuildPlan.Stage.STRUCTURE && now.cursor() > 40) {
				reloaded[0] = true;
				b.builder = reload(helper, b.level, b.builder);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-structure");
			assertDone(helper, b);
			for (BlockPos chest : List.of(new BlockPos(5, 1, 10), new BlockPos(9, 1, 10))) {
				helper.assertTrue(b.level.getBlockState(b.at(chest)).is(Blocks.CHEST), "no chest at " + chest.toShortString() + ": "
					+ b.level.getBlockState(b.at(chest)));
			}
			helper.assertTrue(b.level.getBlockState(b.at(new BlockPos(7, 1, 10))).is(Blocks.CAULDRON), "no empty cauldron at 7,1,10");
			for (BlockPos part : List.of(new BlockPos(13, 2, 3), new BlockPos(13, 2, 4))) {
				float speed = speed(b.level, b.at(part));
				helper.assertTrue(Math.abs(speed) > 0, "the " + id(b.level, b.at(part)) + " at " + part.toShortString() + " doesn't turn (Speed " + speed + ")");
			}
		});
	}


	// ---- 3. The Train Station ------------------------------------------------------------------------------------------

	/**
	 * The Train Station, a track block broken by a player once it's laid while the build still goes on: the build ends
	 * with the track whole x 0-18 at z 12, the station at (9, 1, 11), and nothing solid within 5 above the line (the
	 * train's room; the brief asks 4, 50.13's note claims 6, but tie beams stand at 6).
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e_station_track_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e_station_track_broken")
	public void aTrainStationTrackBrokenMidBuildIsRelaidWithRoomAbove(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Build b = start(helper, StarterBlueprints.TRAIN_STATION, List.of(new ItemStack(BuiltInRegistries.ITEM.get(
			ResourceLocation.fromNamespaceAndPath("create", "track")), 2)));
		BlockPos broken = new BlockPos(4, 1, 12);
		boolean[] done = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(b.level).get(b.id);
			if (!done[0] && now != null && CaravanTrains.isTrack(b.level.getBlockState(b.at(broken)))) {
				done[0] = true;
				b.level.setBlock(b.at(broken), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // a player breaks it
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "the track at " + broken.toShortString() + " was never broken mid-build");
			assertDone(helper, b);
			helper.assertTrue(CaravanTrains.isStation(b.level.getBlockState(b.at(new BlockPos(9, 1, 11)))), "no station at 9,1,11: "
				+ b.level.getBlockState(b.at(new BlockPos(9, 1, 11))));
			List<String> blocked = new ArrayList<>();
			for (int x = 0; x < 19; x++) {
				BlockPos track = b.at(new BlockPos(x, 1, 12));
				helper.assertTrue(CaravanTrains.isTrack(b.level.getBlockState(track)), "no track at " + x + ",1,12: " + b.level.getBlockState(track));
				for (int up = 1; up <= 5; up++) { // 50.13's note says "nothing within 6"; the brief asks 4 for the train: 5 checked
					BlockPos p = track.above(up);
					if (!b.level.getBlockState(p).getCollisionShape(b.level, p).isEmpty()) {
						blocked.add(x + "," + (1 + up) + ",12=" + id(b.level, p));
					}
				}
			}
			helper.assertTrue(blocked.isEmpty(), "solid within 5 above the line: " + blocked);
		});
	}

	// ---- 4. The Terminal Nook with Tom's ---------------------------------------------------------------------------------

	/**
	 * The Terminal Nook with Tom's: a different item in each of the 8 chests the connector (5, 3, 9) joins; the terminal
	 * (5, 3, 8) lists all 8 and the Storehouse (2, 3, 4) counts each once; the Storehouse reads every chest and barrel of the
	 * build (the 8 on the network, the 2 barrels on their own; the vanilla copy's ninth chest is the connector's spot).
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e_nook_each_chest"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e_nook_each_chest")
	public void theTerminalNookSeesEveryChestOnItsNetwork(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Build b = start(helper, StarterBlueprints.TERMINAL_NOOK, List.of());
		Item[] kinds = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.SAND, Items.GRAVEL, Items.ANDESITE, Items.DIORITE, Items.GRANITE};
		boolean[] filled = {false};
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			BlockPos head = b.at(new BlockPos(5, 3, 9));
			BlockPos board = b.at(new BlockPos(2, 3, 4));
			BlockPos terminal = b.at(new BlockPos(5, 3, 8));
			helper.assertTrue(id(b.level, head).equals(TOMS + ":inventory_connector"), "no connector at 5,3,9: " + b.level.getBlockState(head));
			helper.assertTrue(id(b.level, terminal).equals(TOMS + ":storage_terminal"), "no terminal at 5,3,8: " + b.level.getBlockState(terminal));
			helper.assertTrue(b.level.getBlockState(board).is(ModBlocks.STOREHOUSE), "no Storehouse at 2,3,4: " + b.level.getBlockState(board));
			Set<BlockPos> joined = StorageNetworks.members(b.level, head);
			List<BlockPos> chests = joined.stream().filter(p -> b.level.getBlockState(p).is(Blocks.CHEST)).sorted().toList();
			helper.assertTrue(chests.size() == 8, "the connector joins " + chests.size() + " chests (of " + joined.size() + " inventories), wanted 8");
			List<BlockPos> store = SupplyContainers.find(b.level, board, null);
			net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
				b.placement.origin(), b.placement.origin().offset(b.entry.size().getX() - 1, b.entry.size().getY() - 1, b.entry.size().getZ() - 1));
			List<BlockPos> ownStores = store.stream().filter(p -> !p.equals(head) && box.isInside(p)).toList();
			helper.assertTrue(store.contains(head), "the Storehouse doesn't read the connector");
			List<String> all = new ArrayList<>();
			List<String> outside = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState s = b.level.getBlockState(p);
				if (s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST)) {
					String at = p.subtract(b.placement.origin()).toShortString() + "=" + id(b.level, p);
					all.add(at);
					if (!joined.contains(p) && !store.contains(p)) {
						outside.add(at);
					}
				}
			}
			helper.assertTrue(outside.isEmpty(), "chests or barrels of the build the Storehouse doesn't read: " + outside);
			// The vanilla Nook has 11 stores (9 chests, 2 barrels); the Tom's copy draws the connector in one chest's place
			// (the brief), so 8 chests on the network and the 2 barrels on their own: 10
			helper.assertTrue(all.size() == 10 && joined.size() + ownStores.size() == 10, "in the build: " + all + "; " + "the Storehouse's store in the build is " + joined.size() + " on the network + "
				+ ownStores.size() + " on their own (" + ownStores.stream().map(p -> id(b.level, p)).toList() + "), wanted 10 stores");
			if (!filled[0]) {
				for (int i = 0; i < 8; i++) {
					helper.assertTrue(SupplyContainers.insert(b.level, List.of(chests.get(i)), new ItemStack(kinds[i], 3 + i)).isEmpty(), "no room in chest " + i);
				}
				filled[0] = true;
			}
			for (int i = 0; i < 8; i++) {
				long counted = SupplyContainers.count(b.level, store, kinds[i]);
				helper.assertTrue(counted == 3 + i, "the Storehouse counts " + counted + " " + kinds[i] + ", wanted " + (3 + i) + " (once)");
			}
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			try {
				player.setGameMode(GameType.SURVIVAL);
				Direction screen = Direction.NORTH; // the Nook's terminal faces north (StorageHallsCompatTests)
				BlockPos feet = terminal.relative(screen);
				player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				player.gameMode.useItemOn(player, b.level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(terminal).relative(screen, 0.5), screen, terminal, false));
				helper.assertTrue(player.containerMenu.getClass().getName().contains("TerminalMenu"), "the terminal opened " + player.containerMenu.getClass().getName());
				Map<Item, Long> listed = stacks(b.level, terminal);
				for (int i = 0; i < 8; i++) {
					helper.assertTrue(listed.getOrDefault(kinds[i], 0L) == 3 + i, "the terminal lists " + listed.getOrDefault(kinds[i], 0L) + " " + kinds[i]
						+ " (put in chest " + chests.get(i).subtract(b.placement.origin()).toShortString() + "), wanted " + (3 + i));
				}
			} finally {
				b.level.getServer().getPlayerList().remove(player);
			}
		});
	}

	// ---- helpers -------------------------------------------------------------------------------------------------------

	/** A starter build under way. */
	static final class Build {
		ServerLevel level;
		StarterBlueprints.Entry entry;
		UUID id;
		BuildPlan plan;
		BlueprintData.Placement placement;
		Villager builder;

		BlockPos at(BlockPos spot) {
			return placement.origin().offset(spot);
		}
	}

	/** A builder starts {@code entry} by (9, 2, 3) (moved in for a wide one) from barrels of exactly its materials plus {@code extra}. */
	static Build start(GameTestHelper helper, StarterBlueprints.Entry entry, List<ItemStack> extra) {
		Build b = new Build();
		ServerLevel level = helper.getLevel();
		b.level = level;
		b.entry = entry;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		b.builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, b.builder, helper.absolutePos(bench));
		BlockPos origin = new BlockPos(Math.min(9, 28 - entry.size().getX()), 2, Math.min(3, 28 - entry.size().getZ()));
		b.placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, b.builder, null, entry.id(), b.placement);
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		b.id = site.id();
		b.plan = site.plan(level);
		List<ItemStack> stock = materials(b.plan);
		stock.addAll(extra);
		fill(helper, stock, 0);
		return b;
	}

	static List<ItemStack> materials(BuildPlan plan) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		return stock;
	}

	static void barrels(GameTestHelper helper, BuildPlan plan, int z) {
		fill(helper, materials(plan), z);
	}

	static void fill(GameTestHelper helper, List<ItemStack> stock, int z) {
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, z);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	/** The build is over: every block as drawn and nothing skipped (the site as it was last saved). */
	static void assertDone(GameTestHelper helper, Build b) {
		BuildSite now = BuildSiteManager.get(b.level).get(b.id);
		helper.assertTrue(now == null, "still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()
			+ " skipped " + now.skipped()));
		List<BlockPos> unfinished = b.plan.unfinished(b.level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
			.map(p -> p.subtract(b.placement.origin()).toShortString() + "=" + b.level.getBlockState(p)).toList());
	}

	/** As a save and load of the world: every build site and the builder from their saves. Returns the loaded builder. */
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

	static float speed(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	static String tag(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be == null ? "(no block entity: " + level.getBlockState(pos) + ")" : be.saveWithoutMetadata(level.registryAccess()).toString();
	}

	static String id(ServerLevel level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
	}

	/** What Tom's terminal lists, by item (by reflection: never compiled against Tom's classes). */
	static Map<Item, Long> stacks(ServerLevel level, BlockPos terminal) {
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
