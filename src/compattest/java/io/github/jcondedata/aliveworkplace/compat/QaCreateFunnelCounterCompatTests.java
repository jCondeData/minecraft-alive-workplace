package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * QA (lane-e-1026-1343), with Create: B349's promise (docs/guide/shop-counter.md: only the owner, friends and operators
 * change the price list) against a real Create funnel. An extracting andesite funnel on top of a chest pulls its items
 * out and drops them (the control); the same funnel on someone's Shop Counter takes no sample and no price.
 */
public class QaCreateFunnelCounterCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '200' '"qa_create_funnel_counter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_create_funnel_counter")
	public void aCreateFunnelOnACounterTakesNoSampleOrPrice(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos counterAt = new BlockPos(4, 2, 4);
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(counterAt.above(), extractingFunnel());

		BlockPos chestAt = new BlockPos(12, 2, 4);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(chestAt.above(), extractingFunnel());

		helper.runAfterDelay(120, () -> {
			helper.assertTrue(chest.isEmpty(), "the control: the funnel on the chest should have pulled its 2 emeralds out, it holds " + chest.getItem(0));
			helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
				"a Create funnel on Frank's counter took his sample: it is " + counter.goods(0) + ", not 16 cobblestone");
			helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2,
				"a Create funnel on Frank's counter took his price: it is " + counter.price(0) + ", not 2 emeralds");
			int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(counterAt)).inflate(2)).size();
			helper.assertTrue(dropped == 0, dropped + " items lie by Frank's counter");
			helper.succeed();
		});
	}

	/** An andesite funnel on top of a block, set to pull out of it. */
	static BlockState extractingFunnel() {
		BlockState funnel = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:andesite_funnel")).defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.UP);
		for (var property : funnel.getProperties()) {
			if (property.getName().equals("extracting") && property instanceof BooleanProperty extracting) {
				return funnel.setValue(extracting, true);
			}
		}
		throw new IllegalStateException("create:andesite_funnel has no 'extracting' property");
	}
}
