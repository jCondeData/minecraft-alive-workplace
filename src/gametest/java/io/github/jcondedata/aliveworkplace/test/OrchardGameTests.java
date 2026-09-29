package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.orchard.Fruit;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Orchard Keepers picking fruit on a real (headless) server. */
public class OrchardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BASKET = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	static Villager keeper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BASKET, ModBlocks.FRUIT_BASKET);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BASKET), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		return villager;
	}

	static void bush(GameTestHelper helper, BlockPos pos, int age) {
		helper.setBlock(pos.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(pos, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, age));
	}

	/**
	 * An orchard (a Field Marker's area): sweet berries from the chest planted as bushes in a grid, 2 apart. Alone in its
	 * batch: the keeper picks ripe fruit within 16 blocks first, and the next test's bushes are that close.
	 */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "orchard_planting")
	public void orchardKeeperPlantsTheOrchard(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(10, 1, 10), new BlockPos(14, 1, 14))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		Villager villager = keeper(helper);
		net.minecraft.world.Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, 20));
		io.github.jcondedata.aliveworkplace.orchard.Orchards.start(villager,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(10, 1, 10)), helper.absolutePos(new BlockPos(14, 1, 14))));
		helper.succeedWhen(() -> {
			for (int x = 10; x <= 14; x++) {
				for (int z = 10; z <= 14; z++) {
					BlockPos spot = new BlockPos(x, 2, z);
					boolean grid = (x - 10) % 2 == 0 && (z - 10) % 2 == 0;
					if (grid) {
						helper.assertBlockPresent(Blocks.SWEET_BERRY_BUSH, spot);
					} else {
						helper.assertBlockNotPresent(Blocks.SWEET_BERRY_BUSH, spot);
					}
				}
			}
			int planted = villager.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.SAPLINGS_PLANTED, 0);
			helper.assertTrue(planted == 9, "planted " + planted);
		});
	}

	/** Glow berries in the chest: planted hanging from the ceiling over the orchard (a roof here), in the same grid. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "orchard_glow")
	public void orchardKeeperHangsGlowBerries(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(9, 5, 9), new BlockPos(13, 5, 13))) {
			helper.setBlock(p, Blocks.STONE);
		}
		Villager villager = keeper(helper);
		net.minecraft.world.Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_BERRIES, 10));
		io.github.jcondedata.aliveworkplace.orchard.Orchards.start(villager,
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(10, 1, 10)), helper.absolutePos(new BlockPos(12, 1, 12))));
		helper.succeedWhen(() -> {
			for (int x = 10; x <= 12; x += 2) {
				for (int z = 10; z <= 12; z += 2) {
					helper.assertBlockPresent(Blocks.CAVE_VINES, new BlockPos(x, 4, z));
				}
			}
			int planted = villager.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.SAPLINGS_PLANTED, 0);
			helper.assertTrue(planted == 4, "planted " + planted);
			helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.GLOW_BERRIES) + villager.getAttachedOrCreate(
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG).count(net.minecraft.world.item.Items.GLOW_BERRIES) == 6,
				"glow berries used: " + (10 - chest.countItem(net.minecraft.world.item.Items.GLOW_BERRIES)));
		});
	}

	/** Ripe berries, a cocoa pod and glow berries all get picked into the chest; the plants stay to grow again. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void orchardKeeperPicksRipeFruit(GameTestHelper helper) {
		BlockPos[] ripe = {new BlockPos(9, 2, 8), new BlockPos(10, 2, 8), new BlockPos(11, 2, 8)};
		for (BlockPos p : ripe) {
			bush(helper, p, 3);
		}
		BlockPos green = new BlockPos(13, 2, 8);
		bush(helper, green, 1);
		// A cocoa pod on a jungle log.
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(8, y, 13), Blocks.JUNGLE_LOG);
		}
		BlockPos pod = new BlockPos(9, 3, 13);
		helper.setBlock(pod, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
		// Glow berries hanging from a ledge.
		helper.setBlock(new BlockPos(13, 5, 13), Blocks.STONE);
		BlockPos vine = new BlockPos(13, 4, 13);
		helper.setBlock(vine, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVinesBlock.AGE, 25).setValue(BlockStateProperties.BERRIES, true));

		Villager villager = keeper(helper);
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SWEET_BERRIES) >= 6, "only " + chest.countItem(Items.SWEET_BERRIES) + " sweet berries in the chest");
			helper.assertTrue(chest.countItem(Items.COCOA_BEANS) >= 2, "only " + chest.countItem(Items.COCOA_BEANS) + " cocoa beans in the chest");
			helper.assertTrue(chest.countItem(Items.GLOW_BERRIES) >= 1, "no glow berries in the chest");
			for (BlockPos p : ripe) {
				helper.assertBlockPresent(Blocks.SWEET_BERRY_BUSH, p);
			}
			helper.assertBlockPresent(Blocks.COCOA, pod);
			helper.assertBlockPresent(Blocks.CAVE_VINES, vine);
			helper.assertTrue(villager.getAttachedOrElse(ModAttachments.FRUIT_PICKED, 0) >= 5,
				"picked count is " + villager.getAttachedOrElse(ModAttachments.FRUIT_PICKED, 0));
			helper.assertTrue(villager.getHealth() >= villager.getMaxHealth(), "the keeper got scratched by the bushes");
		});
	}

	/** What counts as ripe, and picking puts the plant back to growing. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void fruitRipensAndRegrows(GameTestHelper helper) {
		var bush = Blocks.SWEET_BERRY_BUSH.defaultBlockState();
		helper.assertTrue(!Fruit.isRipe(bush.setValue(SweetBerryBushBlock.AGE, 1)), "a green bush is not ripe");
		helper.assertTrue(Fruit.isRipe(bush.setValue(SweetBerryBushBlock.AGE, 2)), "a bush with berries is ripe");
		helper.assertTrue(!Fruit.isRipe(Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.AGE, 1)), "a green pod is not ripe");
		helper.assertTrue(!Fruit.isRipe(Blocks.CAVE_VINES.defaultBlockState()), "a bare vine is not ripe");
		helper.assertTrue(Fruit.isRipe(Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES, true)), "vine berries are ripe");
		helper.assertTrue(!Fruit.isRipe(Blocks.WHEAT.defaultBlockState()), "wheat is the farmer's");

		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
		BlockPos p = new BlockPos(1, 2, 1);
		bush(helper, p, 3);
		var picked = Fruit.pick(helper.getLevel(), helper.absolutePos(p), villager);
		helper.assertTrue(picked.size() == 1 && picked.get(0).is(Items.SWEET_BERRIES) && picked.get(0).getCount() >= 2, "picked " + picked);
		helper.assertTrue(helper.getBlockState(p).getValue(SweetBerryBushBlock.AGE) == 1, "the bush should be back to green");
		helper.succeed();
	}
}
