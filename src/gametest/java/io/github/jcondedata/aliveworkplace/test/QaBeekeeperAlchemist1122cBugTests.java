package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-23): a Cleric's bug found from the spec. README "Clerics: potions for the guards": they brew
 * healing, regeneration and strength "from the chest by the stand: nether wart, a glistering melon slice ... with blaze
 * powder as fuel, and water bottles". With no blaze powder he loads the stand with water bottles and nether wart anyway;
 * when blaze powder is put in the chest later, he never comes back to fuel the stand, so nothing is ever brewed.
 */
public class QaBeekeeperAlchemist1122cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STAND = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * Water bottles, nether wart and a glistering melon slice, no blaze powder; after 800 ticks blaze powder goes in the
	 * chest: the three potions of healing follow.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaAlch1123cFuelLate"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaAlch1123cFuelLate")
	public void blazePowderAddedLaterStillBrewsTheHealing(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, water());
		chest.setItem(1, water());
		chest.setItem(2, water());
		chest.setItem(3, new ItemStack(Items.NETHER_WART));
		chest.setItem(4, new ItemStack(Items.GLISTERING_MELON_SLICE));
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		AtomicBoolean fueled = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			chest.setItem(20, new ItemStack(Items.BLAZE_POWDER));
			fueled.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fueled.get(), "waiting without fuel");
			helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions after fuel: " + potions(chest, Potions.HEALING)
				+ ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest));
		});
	}

	private static ItemStack water() {
		return PotionContents.createItemStack(Items.POTION, Potions.WATER);
	}

	private static int potions(Container c, Holder<Potion> potion) {
		ItemStack want = PotionContents.createItemStack(Items.POTION, potion);
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (ItemStack.isSameItemSameComponents(c.getItem(i), want)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static String items(Container c) {
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				out.append(i).append('=').append(c.getItem(i).getCount()).append(' ').append(c.getItem(i).getHoverName().getString()).append(", ");
			}
		}
		return out.append(']').toString();
	}
}
