package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.fill;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setup;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.notAStorageRoom;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.round;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateLastResort1114eBugCompatTests.storageRoom;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateToolbox1114e2CompatTests.everywhereLoose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateToolbox1114e2CompatTests.looseWithInside;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane e, 2026-11-15), B1261's fix (a369fdc79) from its text: a Create block over a Mover's Crate in a storage room
 * (MoveSite.dropPacked's last resort) drops exactly what was packed; a filter a player set is a ghost, never an item.
 * The bug's tests were a Toolbox with one kind of item and an empty Smart Chute set to diamonds; these take an empty
 * Deployer set to emeralds (another FilteringBehaviour block) and a Basin holding two different stacks with its filter
 * set, each item counted loose, inside every loose item's data, in the barrels and at both spots.
 */
public class QaCreateMoverCrateFixes1115eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * {@code state} (already standing at {@link QaCreateMoverContents1110eCompatTests#SPOT} and prepared by the caller)
	 * moved onto a Mover's Crate in a storage room; afterwards the block itself drops once and each item of {@code want}
	 * exists exactly that many times (old spot, new spot, the barrels, loose or inside a loose item).
	 */
	private static void inAStorageRoom(GameTestHelper helper, String name, BlockState state, String blockItem, Map<Item, Integer> want) {
		ServerLevel level = helper.getLevel();
		Map.Entry<Item, Integer> first = want.entrySet().iterator().next();
		Setup s = setup(helper, name, state, first.getKey(), first.getValue());
		helper.runAfterDelay(2, () -> {
			BlockPos landing = moved(s, SPOT);
			String end;
			Map<Item, Long> after = new LinkedHashMap<>();
			Map<String, Long> looseNow;
			long blocks;
			try {
				set(level, landing, ModBlocks.MOVERS_CRATE.defaultBlockState());
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE || x.skipped() > 0);
				helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && site.skipped() == 0,
					"setup: the put-up didn't get to STRUCTURE before the drop: " + site.stage() + ", skipped " + site.skipped());
				String[] room = {null};
				site = work(level, s, x -> {
					if (x.skipped() > 0) {
						return true;
					}
					storageRoom(level, landing);
					room[0] = notAStorageRoom(level, landing);
					return false;
				});
				helper.assertTrue(site.skipped() == 1, "the " + name + " on the crate was never dropped: " + site.stage() + ", skipped " + site.skipped());
				helper.assertTrue(room[0] == null, "setup: the crate wasn't in a storage room right before the drop: " + room[0]);
				site = work(level, s, x -> false);
				end = site.stage() + ", skipped " + site.skipped();
				looseNow = looseWithInside(helper, first.getKey());
				for (Item item : want.keySet()) {
					long n = everywhereLoose(helper, item) + held(level, helper.absolutePos(SPOT), item) + held(level, landing, item);
					for (BlockPos p : round(landing)) {
						if (level.getBlockEntity(p) instanceof Container c) {
							for (int i = 0; i < c.getContainerSize(); i++) {
								if (c.getItem(i).is(item)) {
									n += c.getItem(i).getCount();
								}
							}
						}
					}
					after.put(item, n);
				}
				blocks = looseNow.entrySet().stream().filter(e -> e.getKey().startsWith(blockItem)).mapToLong(Map.Entry::getValue).sum();
			} finally {
				tidy(helper, s);
			}
			helper.assertTrue(end.startsWith("DONE") && end.contains("skipped 1"), "the move: " + end);
			helper.assertTrue(blocks == 1, "the " + name + " itself: " + blocks + " dropped, not 1; loose " + looseNow);
			for (Map.Entry<Item, Integer> e : want.entrySet()) {
				long n = after.get(e.getKey());
				helper.assertTrue(n == e.getValue(), "a " + name + " over a Mover's Crate in a storage room: " + n + " "
					+ BuiltInRegistries.ITEM.getKey(e.getKey()).getPath() + " exist after, not " + e.getValue() + " ("
					+ (n > e.getValue() ? "made from nothing" : "lost") + "); loose " + looseNow);
			}
			helper.succeed();
		});
	}

	/** An empty Deployer set to emeralds (its filter a ghost): the deployer and no emerald at all. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1115e_crate_deployer_filter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1115e_crate_deployer_filter")
	public void anEmptyDeployerWithAFilterOnACrateAmongContainersMakesNoEmerald(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockState deployer = block("create:deployer").defaultBlockState();
		BlockPos at = helper.absolutePos(SPOT);
		set(level, at, deployer);
		BlockEntity be = level.getBlockEntity(at);
		helper.assertTrue(be != null, "setup: no deployer block entity at " + at);
		CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
		data.put("Filter", new ItemStack(Items.EMERALD).save(level.registryAccess()));
		be.loadWithComponents(data, level.registryAccess());
		be.setChanged();
		helper.assertTrue(be.saveWithoutMetadata(level.registryAccess()).getCompound("Filter").getString("id").equals("minecraft:emerald"),
			"setup: the deployer's filter isn't emeralds: " + be.saveWithoutMetadata(level.registryAccess()));
		inAStorageRoom(helper, "deployer", deployer, "create:deployer", Map.of(Items.EMERALD, 0));
	}

	/**
	 * A Basin holding 8 iron ingots and 5 gold ingots (two stacks) with its output filter set to diamonds: the basin,
	 * exactly 8 iron and 5 gold, and no diamond.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1115e_crate_basin_two"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1115e_crate_basin_two")
	public void aBasinWithTwoStacksAndAFilterOnACrateAmongContainersKeepsExactlyBoth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockState basin = block("create:basin").defaultBlockState();
		BlockPos at = helper.absolutePos(SPOT);
		set(level, at, basin);
		helper.assertTrue(fill(level, at, Items.GOLD_INGOT, 5) != null && held(level, at, Items.GOLD_INGOT) == 5,
			"setup: the basin holds " + held(level, at, Items.GOLD_INGOT) + " gold, not 5");
		BlockEntity be = level.getBlockEntity(at);
		CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
		data.put("Filter", new ItemStack(Items.DIAMOND).save(level.registryAccess()));
		be.loadWithComponents(data, level.registryAccess());
		be.setChanged();
		helper.assertTrue(be.saveWithoutMetadata(level.registryAccess()).getCompound("Filter").getString("id").equals("minecraft:diamond")
			&& held(level, at, Items.GOLD_INGOT) == 5, "setup: the basin's filter isn't diamonds or its gold changed: " + be.saveWithoutMetadata(level.registryAccess()));
		Map<Item, Integer> want = new LinkedHashMap<>();
		want.put(Items.IRON_INGOT, 8);
		want.put(Items.GOLD_INGOT, 5);
		want.put(Items.DIAMOND, 0);
		inAStorageRoom(helper, "basin", basin, "create:basin", want);
	}
}
