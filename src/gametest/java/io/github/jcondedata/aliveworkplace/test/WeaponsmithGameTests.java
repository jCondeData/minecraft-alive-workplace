package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Weaponsmiths mend worn gear and make the guards' swords. */
public class WeaponsmithGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos GRINDSTONE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos POST = new BlockPos(19, 2, 19);
	private static final BlockPos POST_CHEST = new BlockPos(19, 2, 17);

	/** A worn pickaxe left in the chest by the grindstone comes back mended, three iron ingots' worth. */
	//$ gametest_ticks_batch AREA '1200' '"weaponsmith_mends"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "weaponsmith_mends")
	public void weaponsmithMendsAWornPickaxe(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Villager smith = weaponsmith(helper);
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		pickaxe.setDamageValue(200);
		chest.setItem(0, pickaxe);
		chest.setItem(1, new ItemStack(Items.IRON_INGOT, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.ITEMS_MENDED.getOrElse(smith, 0) == 1, "mended " + ModAttachments.ITEMS_MENDED.getOrElse(smith, 0));
			int damage = -1;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.IRON_PICKAXE)) {
					damage = chest.getItem(i).getDamageValue();
				}
			}
			helper.assertTrue(damage >= 0 && damage <= 200 - 3 * 62, "the pickaxe's damage is " + damage);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0, "iron left: " + chest.countItem(Items.IRON_INGOT));
		});
	}

	/** A guard with no weapon gets an iron sword, made from the iron in the weaponsmith's chest. */
	//$ gametest_ticks_batch AREA '1600' '"weaponsmith_sword"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "weaponsmith_sword")
	public void weaponsmithMakesASwordForTheGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		weaponsmith(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
		chest.setItem(1, new ItemStack(Items.STICK, 1));
		Villager guard = guard(helper);
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		helper.succeedWhen(() -> {
			boolean got = guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD) || guardsChest.countItem(Items.IRON_SWORD) == 1;
			helper.assertTrue(got, "no sword for the guard");
			Village.RADIUS = 0;
		});
	}

	/** A worn helmet in a guard's chest is fetched, mended with the weaponsmith's iron and brought back. */
	//$ gametest_ticks_batch AREA '2000' '"weaponsmith_guard_gear"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "weaponsmith_guard_gear")
	public void weaponsmithMendsTheGuardsWornHelmet(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		weaponsmith(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
		Villager guard = guard(helper);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		ItemStack helmet = new ItemStack(Items.IRON_HELMET);
		helmet.setDamageValue(120);
		guardsChest.setItem(0, helmet);
		helper.succeedWhen(() -> {
			int damage = -1;
			for (int i = 0; i < guardsChest.getContainerSize(); i++) {
				if (guardsChest.getItem(i).is(Items.IRON_HELMET)) {
					damage = guardsChest.getItem(i).getDamageValue();
				}
			}
			helper.assertTrue(damage >= 0 && damage < 60, "the helmet in the guard's chest has damage " + damage);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0, "iron left: " + chest.countItem(Items.IRON_INGOT));
			Village.RADIUS = 0;
		});
	}

	/**
	 * B970's neighbour: the grindstone and the chest the worn sword came from both broken while the smith carries it. With
	 * nowhere to put it back, the sword lands at the smith's feet and the iron goes back to its chest; nothing stays in the
	 * bag and the job isn't kept.
	 */
	//$ gametest_ticks_batch AREA '1800' '"weaponsmith_nowhere_back"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "weaponsmith_nowhere_back")
	public void weaponsmithWithTheGrindstoneAndTheChestGoneDropsThePiece(GameTestHelper helper) {
		Villager smith = QaSmithScribe1105cGameTests.weaponsmith(helper);
		Container stock = QaSmithScribe1105cGameTests.chest(helper, QaSmithScribe1105cGameTests.STOCK);
		Container drop = QaSmithScribe1105cGameTests.chest(helper, QaSmithScribe1105cGameTests.DROP);
		drop.setItem(0, QaSmithScribe1105cGameTests.worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && drop.countItem(Items.IRON_SWORD) == 0) {
				helper.destroyBlock(QaSmithScribe1105cGameTests.GRINDSTONE);
				helper.destroyBlock(QaSmithScribe1105cGameTests.DROP);
				broken[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the smith never took the sword");
			int swords = QaSmithScribe1105cGameTests.onGround(helper, Items.IRON_SWORD);
			int iron = stock.countItem(Items.IRON_INGOT) + QaSmithScribe1105cGameTests.onGround(helper, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, "swords on the ground " + swords + ", iron " + iron + " of 10; the bag holds "
				+ QaSmithScribe1105cGameTests.bag(smith).stacks());
			helper.assertTrue(!ModAttachments.MENDING_JOB.has(smith), "the mending job is still kept");
		});
	}

	/** The smith killed while carrying a player's worn sword: the sword (and the iron) fall where he was, not lost. */
	//$ gametest_ticks_batch AREA '1200' '"weaponsmith_killed_mid_job"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "weaponsmith_killed_mid_job")
	public void weaponsmithKilledMidJobDropsThePiece(GameTestHelper helper) {
		Villager smith = QaSmithScribe1105cGameTests.weaponsmith(helper);
		Container stock = QaSmithScribe1105cGameTests.chest(helper, QaSmithScribe1105cGameTests.STOCK);
		Container drop = QaSmithScribe1105cGameTests.chest(helper, QaSmithScribe1105cGameTests.DROP);
		drop.setItem(0, QaSmithScribe1105cGameTests.worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		boolean[] killed = {false};
		helper.onEachTick(() -> {
			if (!killed[0] && drop.countItem(Items.IRON_SWORD) == 0) {
				smith.kill();
				killed[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(killed[0], "the smith never took the sword");
			int swords = drop.countItem(Items.IRON_SWORD) + QaSmithScribe1105cGameTests.onGround(helper, Items.IRON_SWORD);
			int iron = stock.countItem(Items.IRON_INGOT) + QaSmithScribe1105cGameTests.onGround(helper, Items.IRON_INGOT);
			helper.assertTrue(swords == 1 && iron == 10, "the smith killed mid-job: swords " + swords + ", iron " + iron + " of 10");
		});
	}

	private static Villager weaponsmith(GameTestHelper helper) {
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
		return smith;
	}

	private static Villager guard(GameTestHelper helper) {
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return guard;
	}
}
