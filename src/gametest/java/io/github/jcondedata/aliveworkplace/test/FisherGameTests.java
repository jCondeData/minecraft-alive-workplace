package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Hired fishermen on a real (headless) server. */
public class FisherGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BARREL = new BlockPos(2, 2, 2);

	private static Villager fisherman(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BARREL, Blocks.BARREL);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(11, 1, 11))) {
			helper.setBlock(p, Blocks.WATER);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(BARREL), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
		return villager;
	}

	private static int stored(Container container) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			n += container.getItem(i).getCount();
		}
		return n;
	}

	/** Casts into the pond, reels in fish and junk, and brings every fifth catch home to the barrel. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fishermanFillsTheBarrel(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		Fishers.start(helper.getLevel(), villager, new ItemStack(Items.FISHING_ROD));
		helper.succeedWhen(() -> {
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
			helper.assertTrue(caught >= 5, "only " + caught + " caught");
			Container barrel = helper.getBlockEntity(BARREL);
			helper.assertTrue(stored(barrel) >= 5, "only " + stored(barrel) + " items in the barrel");
		});
	}

	/** While fishing, a bobber floats on the pond on the end of the fisherman's line; it's gone once they stop. */
	@GameTest(template = AREA, timeoutTicks = 1200)
	public void fishermanCastsABobber(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		Fishers.start(helper.getLevel(), villager, new ItemStack(Items.FISHING_ROD));
		helper.succeedWhen(() -> {
			var bobbers = helper.getLevel().getEntitiesOfClass(io.github.jcondedata.aliveworkplace.fish.FishingBobber.class, helper.getBounds());
			helper.assertTrue(bobbers.size() == 1, bobbers.size() + " bobbers out");
			var bobber = bobbers.get(0);
			helper.assertTrue(bobber.owner() == villager, "the bobber isn't on the fisherman's line");
			helper.assertTrue(helper.getLevel().getBlockState(bobber.blockPosition()).is(Blocks.WATER),
				"the bobber isn't on the water: " + helper.relativePos(bobber.blockPosition()));
		});
	}

	/** A smoker next to the barrel: the raw cod and salmon go into it with charcoal from the barrel, not into the barrel. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fishermanSmokesTheCatch(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		BlockPos smokerPos = new BlockPos(2, 2, 4);
		helper.setBlock(smokerPos, Blocks.SMOKER);
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, new ItemStack(Items.CHARCOAL, 8));
		barrel.setItem(1, new ItemStack(Items.COOKED_SALMON, 3)); // already cooked: stays put
		Fishers.start(helper.getLevel(), villager, new ItemStack(Items.FISHING_ROD));
		helper.succeedWhen(() -> {
			int caught = ModAttachments.FISH_CAUGHT.getOrElse(villager, 0);
			helper.assertTrue(caught >= 5, "only " + caught + " caught");
			helper.assertTrue(barrel.countItem(Items.COD) + barrel.countItem(Items.SALMON) == 0, "raw fish left in the barrel");
			helper.assertTrue(barrel.countItem(Items.COOKED_SALMON) >= 3, "the cooked salmon went into the smoker");
			net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity smoker = helper.getBlockEntity(smokerPos);
			boolean loaded = !smoker.getItem(0).isEmpty() || !smoker.getItem(2).isEmpty();
			helper.assertTrue(!loaded || smoker.getItem(1).is(Items.CHARCOAL) || helper.getBlockState(smokerPos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT), "fish in the smoker but no fuel");
		});
	}

	/** When the rod breaks, the next one comes out of the barrel. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void fishermanTakesASpareRod(GameTestHelper helper) {
		Villager villager = fisherman(helper);
		Container barrel = helper.getBlockEntity(BARREL);
		barrel.setItem(0, new ItemStack(Items.FISHING_ROD));
		ItemStack worn = new ItemStack(Items.FISHING_ROD);
		worn.setDamageValue(worn.getMaxDamage() - 1); // breaks on the first catch
		Fishers.start(helper.getLevel(), villager, worn);
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.FISH_CAUGHT.getOrElse(villager, 0) >= 2, "no second catch yet");
			helper.assertTrue(villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.FISHING_ROD), "no rod in hand");
			helper.assertTrue(barrel.countItem(Items.FISHING_ROD) == 0, "the spare rod is still in the barrel");
		});
	}
}
