package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1108-0337): docs/wiki/furniture-and-storage.md, "Sophisticated Storage's chests, barrels and limited
 * barrels already work as supply chests", and every store counts once (37.10's rule). A vanilla double chest is listed
 * by one half (SupplyContainers.otherHalfFound); a Sophisticated chest is no vanilla ChestBlock, so its double chest
 * counts once only if the two halves hand out the same store. Two Sophisticated chests placed side by side by a player
 * (as a player makes a double one) with 10 planks in them: a builder's bench counts 10, takes exactly 10, and none are
 * left counted after.
 */
public class QaSophisticatedDouble1108CompatTests implements FabricGameTest {
	//$ gametest_batch 'CompatGameTests.AREA' '"qaSophDouble"'
	@GameTest(template = CompatGameTests.AREA, batch = "qaSophDouble")
	public void aSophisticatedDoubleChestCountsItsGoodsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(CompatGameTests.BENCH);
		helper.setBlock(CompatGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		Item chestItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedstorage:chest"));
		helper.assertTrue(chestItem instanceof BlockItem, "setup: no Sophisticated chest item: " + chestItem);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos first = new BlockPos(4, 2, 5);
		BlockPos second = new BlockPos(5, 2, 5);
		place(helper, player, first, new ItemStack(chestItem));
		place(helper, player, second, new ItemStack(chestItem));
		BlockState a = level.getBlockState(helper.absolutePos(first));
		BlockState b = level.getBlockState(helper.absolutePos(second));
		Property<?> type = a.getBlock().getStateDefinition().getProperty("type");
		helper.assertTrue(type != null && !"single".equals(name(a, type)) && !"single".equals(name(b, type)),
			"setup: the two chests didn't join into a double chest: " + a + ", " + b);
		var storage = ItemStorage.SIDED.find(level, helper.absolutePos(first), null);
		helper.assertTrue(storage != null, "the Sophisticated chest has no item storage");
		try (var tx = Transaction.openOuter()) {
			long in = storage.insert(ItemVariant.of(Items.OAK_PLANKS), 10, tx);
			tx.commit();
			helper.assertTrue(in == 10, "setup: put " + in + " planks in the chest");
		}
		List<BlockPos> supplies = SupplyContainers.find(level, bench, null);
		long counted = SupplyContainers.count(level, supplies, Items.OAK_PLANKS);
		helper.assertTrue(counted == 10, "the bench counts " + counted + " planks in a double chest holding 10 (supplies " + supplies + ")");
		long taken = SupplyContainers.extract(level, supplies, Items.OAK_PLANKS, 11);
		helper.assertTrue(taken == 10, "asked for 11 of the 10 planks, took " + taken);
		long left = SupplyContainers.count(level, supplies, Items.OAK_PLANKS);
		helper.assertTrue(left == 0, left + " planks still counted after all 10 were taken");
		helper.succeed();
	}

	/** The chest item placed by {@code player} on top of the floor at {@code pos}, as a player places it. */
	private static void place(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
		BlockPos below = helper.absolutePos(pos.below());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).relative(Direction.UP, 0.5), Direction.UP, below, false);
		((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
	}

	private static <T extends Comparable<T>> String name(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}
}
