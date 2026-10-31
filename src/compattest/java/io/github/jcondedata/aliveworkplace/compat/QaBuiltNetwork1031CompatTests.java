package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.SophisticatedControllerCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.SophisticatedControllerCompatTests.placeAsPlayer;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1031-0637) for B238 from its spec (37.9: a controller makes the storage it links one store; breaking it
 * turns them back into stores of their own; nothing counts twice), at the Storehouse, the way a builder adds storage:
 * two barrels built one after the other, the second touching only the first built one; then the controller broken;
 * and with moddedStorageNetworks off.
 */
public class QaBuiltNetwork1031CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private record Net(ServerLevel level, BlockPos storehouse, BlockPos controller, BlockPos first, BlockPos second, BlockPos third) {
	}

	/** A Storehouse; a controller placed by a player with barrel A (10 cobblestone); a builder then builds B (7 planks) touching A, and C (5 sticks) touching only B. */
	private static Net build(GameTestHelper helper, boolean networks) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = networks;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "barrel A took nothing");
		BlockPos second = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(second, block("barrel").defaultBlockState(), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, second, level.getBlockState(second));
		helper.assertTrue(SupplyContainers.insert(level, List.of(second), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "barrel B took nothing");
		BlockPos third = helper.absolutePos(new BlockPos(6, 2, 4));
		level.setBlock(third, block("barrel").defaultBlockState(), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, third, level.getBlockState(third));
		helper.assertTrue(SupplyContainers.insert(level, List.of(third), new ItemStack(Items.STICK, 5)).isEmpty(), "barrel C took nothing");
		return new Net(level, helper.absolutePos(new BlockPos(2, 2, 2)), controller, first, second, third);
	}

	private static String counts(Net n, List<BlockPos> store) {
		return SupplyContainers.count(n.level(), store, Items.COBBLESTONE) + " cobblestone, " + SupplyContainers.count(n.level(), store, Items.OAK_PLANKS)
			+ " planks, " + SupplyContainers.count(n.level(), store, Items.STICK) + " sticks in " + store;
	}

	private static boolean exact(Net n, List<BlockPos> store) {
		return SupplyContainers.count(n.level(), store, Items.COBBLESTONE) == 10 && SupplyContainers.count(n.level(), store, Items.OAK_PLANKS) == 7
			&& SupplyContainers.count(n.level(), store, Items.STICK) == 5;
	}

	/**
	 * Two barrels built in a row onto the network (C touches only B, itself built) are both the controller's: the
	 * Storehouse has one store, the controller, and counts every item once (10, 7 and 5).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaBuiltNetworkChain"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBuiltNetworkChain")
	public void twoBarrelsBuiltInARowAreTheControllersCountedOnce(GameTestHelper helper) {
		Net n = build(helper, true);
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(n.level(), n.storehouse(), null);
			helper.assertTrue(exact(n, store), "the Storehouse counts " + counts(n, store) + ", wanted 10, 7 and 5 once each");
			helper.assertTrue(store.equals(List.of(n.controller())), "the Storehouse's stores are " + store + ", wanted the controller "
				+ n.controller() + " alone (its barrels counted through it)");
		});
	}

	/**
	 * 37.9: breaking the controller turns the barrels it linked, the built ones too, back into stores of their own:
	 * nothing is lost and nothing counted twice.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaBuiltNetworkBroken"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBuiltNetworkBroken")
	public void theControllerBrokenLeavesEveryBuiltBarrelAStoreOfItsOwn(GameTestHelper helper) {
		Net n = build(helper, true);
		helper.runAfterDelay(40, () -> {
			n.level().setBlockAndUpdate(n.controller(), Blocks.AIR.defaultBlockState());
			helper.succeedWhen(() -> {
				List<BlockPos> store = SupplyContainers.find(n.level(), n.storehouse(), null);
				helper.assertTrue(exact(n, store), "with the controller broken the Storehouse counts " + counts(n, store) + ", wanted 10, 7 and 5 once each");
				helper.assertTrue(Set.copyOf(store).equals(Set.of(n.first(), n.second(), n.third())) && store.size() == 3,
					"with the controller broken the stores are " + store + ", wanted the three barrels");
			});
		});
	}

	/** With moddedStorageNetworks off, the controller is ignored: each barrel, built or not, is its own store, counted once. */
	//$ gametest_ticks_batch AREA '400' '"qaBuiltNetworkOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBuiltNetworkOff")
	public void switchedOffEveryBuiltBarrelIsItsOwnStore(GameTestHelper helper) {
		Net n = build(helper, false);
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(n.level(), n.storehouse(), null);
			helper.assertTrue(exact(n, store), "switched off, the Storehouse counts " + counts(n, store) + ", wanted 10, 7 and 5 once each");
			helper.assertTrue(store.containsAll(List.of(n.first(), n.second(), n.third())), "switched off, the stores are " + store + ", wanted every barrel");
		});
	}
}
