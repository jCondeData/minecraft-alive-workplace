package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1123-0653): B1521 from the spec, README "Clerics: potions for the guards": clerics brew "potions of
 * healing, regeneration and strength ... from the chest by the stand: nether wart, a glistering melon slice, a ghast tear
 * or blaze powder, with blaze powder as fuel, and water bottles". Edges round "the blaze powder comes later": the
 * regeneration brew, the stand and the cleric saved and loaded before the powder comes, powder already in the fuel slot,
 * the stand broken and put back, the powder put in at night, and no ingredient at all (nothing wasted).
 */
public class QaClericFuel1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STAND = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Container scene(GameTestHelper helper, Item... chestItems) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i] == Items.POTION ? water() : new ItemStack(chestItems[i]));
		}
		return chest;
	}

	private static Villager cleric(GameTestHelper helper) {
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return cleric;
	}

	/**
	 * The regeneration brew: water, wart and a ghast tear, no powder; the stand is loaded and waits; blaze powder in the
	 * chest at tick 800: three potions of regeneration.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaClericFuel1123cRegen"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaClericFuel1123cRegen")
	public void qaBlazePowderAddedLaterBrewsTheRegeneration(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.NETHER_WART, Items.GHAST_TEAR);
		cleric(helper);
		AtomicBoolean fueled = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			chest.setItem(20, new ItemStack(Items.BLAZE_POWDER));
			fueled.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fueled.get(), "waiting without fuel");
			helper.assertTrue(potions(chest, Potions.REGENERATION) == 3, "regeneration potions after fuel: " + potions(chest, Potions.REGENERATION)
				+ ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest));
		});
	}

	/**
	 * Water, wart and a melon slice, no powder; at tick 800 the cleric and the brewing stand are both saved and loaded (the
	 * stand keeps what he had loaded), then blaze powder goes in the chest: three potions of healing.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaClericFuel1123cReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaClericFuel1123cReload")
	public void qaBlazePowderAddedAfterASaveAndReloadStillBrewsTheHealing(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.NETHER_WART, Items.GLISTERING_MELON_SLICE);
		Villager cleric = cleric(helper);
		AtomicBoolean fueled = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			ServerLevel level = helper.getLevel();
			BlockEntity old = helper.getBlockEntity(STAND);
			CompoundTag tag = old.saveWithoutMetadata(level.registryAccess());
			helper.setBlock(STAND, Blocks.AIR);
			helper.setBlock(STAND, Blocks.BREWING_STAND);
			discardDrops(helper);
			helper.getBlockEntity(STAND).loadWithComponents(tag, level.registryAccess());
			helper.getBlockEntity(STAND).setChanged();
			reload(level, cleric);
			chest.setItem(20, new ItemStack(Items.BLAZE_POWDER));
			fueled.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fueled.get(), "waiting without fuel");
			helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions after a reload and fuel: " + potions(chest, Potions.HEALING)
				+ ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest));
		});
	}

	/** Blaze powder already in the stand's fuel slot, none in the chest: the stand is fueled, the healing follows. */
	//$ gametest_ticks_batch AREA '3200' '"qaClericFuel1123cInSlot"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaClericFuel1123cInSlot")
	public void qaPowderAlreadyInTheFuelSlotBrewsTheHealing(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.NETHER_WART, Items.GLISTERING_MELON_SLICE);
		Container stand = helper.getBlockEntity(STAND);
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
		cleric(helper);
		helper.succeedWhen(() -> helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions with fuel in the slot: "
			+ potions(chest, Potions.HEALING) + ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest)));
	}

	/**
	 * No powder; the stand he loaded is broken at tick 800 (its contents dropped and gone) and a new one put in its place;
	 * the chest gets three more water bottles, wart, a melon slice and blaze powder: the new stand brews the healing.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaClericFuel1123cReplaced"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaClericFuel1123cReplaced")
	public void qaAStandBrokenAndPutBackWhileWaitingForFuelBrewsOnceFueled(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.NETHER_WART, Items.GLISTERING_MELON_SLICE);
		cleric(helper);
		AtomicBoolean fueled = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			helper.getLevel().destroyBlock(helper.absolutePos(STAND), true);
			discardDrops(helper);
		});
		helper.runAfterDelay(840, () -> {
			helper.setBlock(STAND, Blocks.BREWING_STAND);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				chest.setItem(i, ItemStack.EMPTY);
			}
			chest.setItem(0, water());
			chest.setItem(1, water());
			chest.setItem(2, water());
			chest.setItem(3, new ItemStack(Items.NETHER_WART));
			chest.setItem(4, new ItemStack(Items.GLISTERING_MELON_SLICE));
			chest.setItem(5, new ItemStack(Items.BLAZE_POWDER));
			fueled.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fueled.get(), "waiting for the new stand");
			helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions from the new stand: " + potions(chest, Potions.HEALING)
				+ ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest));
		});
	}

	/**
	 * No powder; at tick 800 night falls and the powder goes in the chest while it's dark; at tick 1400 it's day again: the
	 * healing follows.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaClericFuel1123cNight"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaClericFuel1123cNight")
	public void qaPowderPutInAtNightIsUsedOnceDayComes(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.NETHER_WART, Items.GLISTERING_MELON_SLICE);
		cleric(helper);
		AtomicBoolean day = new AtomicBoolean();
		helper.runAfterDelay(800, () -> {
			helper.setDayTime(14000);
			chest.setItem(20, new ItemStack(Items.BLAZE_POWDER));
		});
		helper.runAfterDelay(1400, () -> {
			helper.setDayTime(2000);
			day.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(day.get(), "waiting for day");
			helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions after a night: " + potions(chest, Potions.HEALING)
				+ ", stand " + items(helper.getBlockEntity(STAND)) + ", chest " + items(chest));
		});
	}

	/**
	 * Water bottles and blaze powder but no nether wart and no ingredient: nothing to brew, so nothing is wasted: after
	 * 1,600 ticks the three water bottles are still water bottles (in the chest or the stand), no mundane or thick potion
	 * is made, and the blaze powder is still there (in the chest, the fuel slot, or burned into fuel only if it's still
	 * all unspent).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaClericFuel1123cNothing"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaClericFuel1123cNothing")
	public void qaNoIngredientWastesNoWaterAndNoPowder(GameTestHelper helper) {
		Container chest = scene(helper, Items.POTION, Items.POTION, Items.POTION, Items.BLAZE_POWDER);
		cleric(helper);
		helper.runAfterDelay(1600, () -> {
			Container stand = helper.getBlockEntity(STAND);
			int water = potions(chest, Potions.WATER) + potions(stand, Potions.WATER);
			int wasted = potions(chest, Potions.MUNDANE) + potions(stand, Potions.MUNDANE) + potions(chest, Potions.THICK) + potions(stand, Potions.THICK);
			int fuel = ((BrewingStandBlockEntity) stand).saveWithoutMetadata(helper.getLevel().registryAccess()).getByte("Fuel");
			int powder = count(chest, Items.BLAZE_POWDER) + count(stand, Items.BLAZE_POWDER);
			String seen = "stand " + items(stand) + " fuel " + fuel + ", chest " + items(chest);
			helper.assertTrue(water == 3, "water bottles left: " + water + " of 3; " + seen);
			helper.assertTrue(wasted == 0, "useless potions made: " + wasted + "; " + seen);
			helper.assertTrue(powder == 1 || powder == 0 && fuel == 20, "the blaze powder was spent with nothing brewed: " + seen);
			helper.succeed();
		});
	}

	private static void discardDrops(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(STAND);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4))) {
			e.discard();
		}
	}

	private static void reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
	}

	private static ItemStack water() {
		return PotionContents.createItemStack(Items.POTION, Potions.WATER);
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
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
