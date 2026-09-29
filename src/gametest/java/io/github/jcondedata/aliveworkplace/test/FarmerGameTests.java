package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Farmers looking after a marked field on a real (headless) server. */
public class FarmerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager farmer(GameTestHelper helper, BlockPos corner1, BlockPos corner2, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(corner1), helper.absolutePos(corner2)));
		return villager;
	}

	/**
	 * A blank Field Marker given to a farmer: they take on the farm by their composter — both halves of it, across the
	 * water channel — and not the farmland further off.
	 */
	@GameTest(template = AREA, timeoutTicks = 200)
	public void farmerTakesOnTheFarmByTheComposter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(6, 1, 6), new BlockPos(14, 1, 14))) {
			helper.setBlock(p, p.getX() == 10 ? Blocks.WATER : Blocks.FARMLAND);
		}
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(18, 1, 18), new BlockPos(20, 1, 20))) {
			helper.setBlock(p, Blocks.FARMLAND); // someone else's field
		}
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack blank = new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.FIELD_MARKER);
		helper.assertTrue(Fields.assign(player, villager, blank).consumesAction() && Fields.hasField(villager), "the farmer didn't take the farm on");
		BoundingBox box = villager.getAttached(io.github.jcondedata.aliveworkplace.registry.ModAttachments.FARM_FIELD).box();
		BoundingBox expected = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(6, 1, 6)), helper.absolutePos(new BlockPos(14, 1, 14)));
		helper.assertTrue(box.equals(expected), "took on " + box + " instead of " + expected);
		helper.succeed();
	}

	/**
	 * A village farmer takes on the farm by their composter by themselves once there's a chest by it: the harvest goes
	 * into the chest, but they keep some food (bread from the wheat) to share with the village.
	 */
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "village_farm")
	public void villageFarmerTakesOnTheirFarm(GameTestHelper helper) {
		Leftovers.clear(helper); // a farmer from a neighbouring test would pick these crops too
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(6, 1, 6), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, p.getX() == 8 ? Blocks.WATER.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
			if (p.getX() != 8) {
				helper.setBlock(p.above(), (p.getX() < 8 ? Blocks.WHEAT : Blocks.CARROTS).defaultBlockState().setValue(CropBlock.AGE, 7));
			}
		}
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		// Food to share already in their pockets: without it, an unlucky harvest could all go to the share (CI saw it).
		villager.getInventory().addItem(new ItemStack(Items.BREAD, 9));
		helper.succeedWhen(() -> {
			var job = villager.getAttached(io.github.jcondedata.aliveworkplace.registry.ModAttachments.FARM_FIELD);
			helper.assertTrue(job != null && job.adopted(), "the farmer didn't take the farm on");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.WHEAT) + chest.countItem(Items.CARROT) > 0, "no harvest in the chest; harvested "
				+ villager.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.FARM_HARVESTED, 0) + ", bag "
				+ villager.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG).stacks() + ", pockets "
				+ villager.getInventory().getItems() + ", chest " + chest.getItem(0) + " " + chest.getItem(1));
			var pockets = villager.getInventory();
			helper.assertTrue(pockets.countItem(Items.BREAD) > 0 || pockets.countItem(Items.CARROT) > 0, "the farmer kept no food to share");
		});
	}

	/** Stop a farmer's self-adopted farm and they leave it alone; no chest by the composter, no farm taken on either. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void villageFarmersStayStoppedAndNeedAChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(6, 1, 6), new BlockPos(9, 1, 9))) {
			helper.setBlock(p, Blocks.FARMLAND);
		}
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		helper.runAfterDelay(Fields.ADOPT_EVERY + 20, () -> {
			helper.assertFalse(Fields.hasField(villager), "took the farm on with nowhere to put the harvest");
			helper.setBlock(CHEST, Blocks.CHEST);
		});
		helper.runAfterDelay(2L * Fields.ADOPT_EVERY + 60, () -> {
			helper.assertTrue(Fields.hasField(villager), "didn't take the farm on once there was a chest");
			Fields.release(level, villager, null);
		});
		helper.runAfterDelay(4L * Fields.ADOPT_EVERY + 100, () -> {
			helper.assertFalse(Fields.hasField(villager), "took the farm on again after being stopped");
			helper.succeed();
		});
	}

	/** Ripe wheat is cut, wheat goes back in the ground at once, the harvest ends up in the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerHarvestsAndReplants(GameTestHelper helper) {
		BlockPos water = new BlockPos(10, 1, 10);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 12))) {
			if (p.equals(water)) {
				helper.setBlock(p, Blocks.WATER);
			} else {
				helper.setBlock(p, Blocks.FARMLAND);
				helper.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
			}
		}
		Villager villager = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(12, 1, 12));
		helper.succeedWhen(() -> {
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 2, 8), new BlockPos(12, 2, 12))) {
				if (!p.below().equals(water)) {
					helper.assertBlockPresent(Blocks.WHEAT, p);
				}
			}
			int harvested = villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0);
			helper.assertTrue(harvested >= 24, "only " + harvested + " harvested");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.WHEAT) >= 20, "only " + chest.countItem(Items.WHEAT) + " wheat in the chest");
		});
	}

	/** Sweet berries and cocoa in the field are picked (the bush and the pod stay to grow again), into the chest. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void farmerPicksBerriesAndCocoa(GameTestHelper helper) {
		BlockPos bush = new BlockPos(9, 2, 9);
		BlockPos log = new BlockPos(11, 2, 9);
		BlockPos pod = new BlockPos(11, 2, 10);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 11))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3));
		helper.setBlock(log, Blocks.JUNGLE_LOG);
		helper.setBlock(pod, Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.FACING, net.minecraft.core.Direction.NORTH)
			.setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2));
		// No hoe and no seeds in the chest: the grass stays grass, the farmer only picks.
		farmer(helper, new BlockPos(8, 1, 8), new BlockPos(12, 1, 11));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(bush).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) == 1, "berries not picked");
			helper.assertTrue(helper.getBlockState(pod).is(Blocks.COCOA) && helper.getBlockState(pod).getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 0,
				"cocoa not picked: " + helper.getBlockState(pod));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SWEET_BERRIES) >= 2 && chest.countItem(Items.COCOA_BEANS) >= 2,
				"chest: " + chest.countItem(Items.SWEET_BERRIES) + " berries, " + chest.countItem(Items.COCOA_BEANS) + " cocoa beans");
		});
	}

	/** Once everything's sown, bone meal from the chest brings the crops on: they're harvested long before they'd ripen. */
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void farmerUsesBoneMeal(GameTestHelper helper) {
		BlockPos water = new BlockPos(10, 1, 9);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(12, 1, 8))) {
			helper.setBlock(p, Blocks.FARMLAND);
			helper.setBlock(p.above(), Blocks.WHEAT);
		}
		helper.setBlock(water, Blocks.WATER);
		Villager villager = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(12, 1, 8), new ItemStack(Items.BONE_MEAL, 40));
		helper.succeedWhen(() -> {
			int harvested = villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0);
			helper.assertTrue(harvested >= 5, "only " + harvested + " harvested");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.WHEAT) >= 5, "only " + chest.countItem(Items.WHEAT) + " wheat in the chest");
		});
	}

	/** Bare grass is tilled with the hoe from the chest and sown with the carrots from the chest. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerTillsAndSowsFromTheChest(GameTestHelper helper) {
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(11, 1, 9), Blocks.WATER);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.SHORT_GRASS);
		farmer(helper, new BlockPos(8, 1, 8), new BlockPos(10, 1, 10), new ItemStack(Items.STONE_HOE), new ItemStack(Items.CARROT, 16));
		helper.succeedWhen(() -> {
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
				helper.assertBlockPresent(Blocks.FARMLAND, p);
				helper.assertBlockPresent(Blocks.CARROTS, p.above());
			}
		});
	}

	/** Sugar cane is cut down to its bottom block, which is left to grow back. */
	@GameTest(template = AREA, timeoutTicks = 2000)
	public void farmerCutsSugarCane(GameTestHelper helper) {
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(8, 1, 9), Blocks.WATER);
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(8, y, 8), Blocks.SUGAR_CANE);
		}
		farmer(helper, new BlockPos(7, 1, 7), new BlockPos(9, 1, 9));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SUGAR_CANE, new BlockPos(8, 2, 8));
			helper.assertBlockNotPresent(Blocks.SUGAR_CANE, new BlockPos(8, 3, 8));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 2, chest.countItem(Items.SUGAR_CANE) + " sugar cane in the chest");
		});
	}

	/** Sugar cane from the chest goes on the sand by the water, next to the wheat seeds the farmer also takes along. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerPlantsSugarCaneByTheWater(GameTestHelper helper) {
		for (int x = 8; x <= 10; x++) {
			helper.setBlock(new BlockPos(x, 0, 8), Blocks.STONE); // (sand falls: nothing is under the floor)
			helper.setBlock(new BlockPos(x, 1, 8), Blocks.SAND);
			helper.setBlock(new BlockPos(x, 1, 9), Blocks.WATER);
		}
		farmer(helper, new BlockPos(8, 1, 8), new BlockPos(10, 1, 9), new ItemStack(Items.WHEAT_SEEDS, 8), new ItemStack(Items.SUGAR_CANE, 3));
		helper.succeedWhen(() -> {
			for (int x = 8; x <= 10; x++) {
				helper.assertBlockPresent(Blocks.SUGAR_CANE, new BlockPos(x, 2, 8));
			}
		});
	}

	/** Cactus, bamboo and kelp (in a glass tank) are cut down to their bottom block, like sugar cane. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void farmerCutsCactusBambooAndKelp(GameTestHelper helper) {
		helper.setBlock(new BlockPos(16, 0, 8), Blocks.STONE); // (sand falls: nothing is under the floor)
		helper.setBlock(new BlockPos(16, 1, 8), Blocks.SAND);
		helper.setBlock(new BlockPos(14, 1, 8), Blocks.DIRT);
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(16, y, 8), Blocks.CACTUS);
			helper.setBlock(new BlockPos(14, y, 8), Blocks.BAMBOO);
			for (BlockPos side : new BlockPos[]{new BlockPos(11, y, 8), new BlockPos(13, y, 8), new BlockPos(12, y, 7), new BlockPos(12, y, 9)}) {
				helper.setBlock(side, Blocks.GLASS);
			}
			helper.setBlock(new BlockPos(12, y, 8), y == 4 ? Blocks.KELP : Blocks.KELP_PLANT);
		}
		farmer(helper, new BlockPos(12, 1, 8), new BlockPos(16, 1, 8));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.CACTUS, new BlockPos(16, 2, 8));
			helper.assertBlockNotPresent(Blocks.CACTUS, new BlockPos(16, 3, 8));
			helper.assertBlockPresent(Blocks.BAMBOO, new BlockPos(14, 2, 8));
			helper.assertBlockNotPresent(Blocks.BAMBOO, new BlockPos(14, 3, 8));
			var bottom = helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(12, 2, 8)));
			helper.assertTrue(bottom.is(Blocks.KELP) || bottom.is(Blocks.KELP_PLANT), "the kelp's bottom went too: " + bottom);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.CACTUS) == 2 && chest.countItem(Items.BAMBOO) == 2 && chest.countItem(Items.KELP) == 2,
				"in the chest: cactus " + chest.countItem(Items.CACTUS) + ", bamboo " + chest.countItem(Items.BAMBOO) + ", kelp " + chest.countItem(Items.KELP));
		});
	}

	/** Seeds piling up in the chests go in the composter; the bone meal comes out for the field. */
	@GameTest(template = AREA, timeoutTicks = 4000)
	public void farmerCompostsSpareSeeds(GameTestHelper helper) {
		Villager farmer = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(9, 1, 9), new ItemStack(Items.WHEAT_SEEDS, 64),
			new ItemStack(Items.WHEAT_SEEDS, 64), new ItemStack(Items.WHEAT_SEEDS, 64));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			var bag = farmer.getAttachedOrCreate(io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG);
			helper.assertTrue(chest.countItem(Items.BONE_MEAL) + bag.count(Items.BONE_MEAL) >= 1, "no bone meal yet");
			helper.assertTrue(chest.countItem(Items.WHEAT_SEEDS) + bag.count(Items.WHEAT_SEEDS) >= 64, "composted the seeds it needs");
		});
	}
}
