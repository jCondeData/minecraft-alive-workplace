package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.jeweller.Jewellers;
import io.github.jcondedata.aliveworkplace.jeweller.MegaStones;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA for ROADMAP 38.6a, written from the owner's words: "the Jeweller cuts Mega Stones only from materials the player
 * provides through the Storehouse storage, never on his own initiative from nothing". Adversarial cases the builder's
 * {@link MegaStoneCompatTests} leaves out: a long shift with nothing anywhere, both sides of the makings' counts
 * (one emerald short, then exactly enough; a surplus leaves exactly the rest), a chest by his workstation that isn't
 * the store, the meteorite recipe's four-ore boundary, and the switches over a real shift.
 */
public class QaJewellerMegaCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos TRAINER = new BlockPos(9, 2, 9);
	/** A plain chest near the stonecutter (4,2,2) but more than 8 blocks from the Storehouse (13,2,11): not the store. */
	private static final BlockPos STRAY = new BlockPos(1, 2, 8);
	private static final TagKey<Item> STONES = TagKey.create(Registries.ITEM, AliveWorkplace.id("mega_stones"));

	private static Item ms(String path) {
		return MegaStoneCompatTests.ms(path);
	}

	/** Every Mega item anywhere the Jeweller could have put one: his chest, the store, his own inventory, dropped items. */
	private static String megaAnywhere(GameTestHelper helper, Container own, Villager v) {
		StringBuilder out = new StringBuilder();
		List<Item> watched = List.of(ms("mega_stone_crystal"), ms("mega_stone"), ms("keystone"), ms("keystone_ore"));
		Container store = MegaStoneCompatTests.store(helper);
		for (Container c : List.of(own, store, v.getInventory())) {
			for (Item item : watched) {
				if (c.countItem(item) > 0) {
					out.append(c.countItem(item)).append(' ').append(item).append("; ");
				}
			}
			if (!MegaStoneCompatTests.stonesIn(c).isEmpty()) {
				out.append(MegaStoneCompatTests.stonesIn(c)).append("; ");
			}
		}
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			ItemStack s = e.getItem();
			if (watched.contains(s.getItem()) || s.is(STONES)) {
				out.append("dropped ").append(s).append("; ");
			}
		}
		return out.toString();
	}

	private static String counts(Container c) {
		return c.countItem(ms("mega_stone_crystal")) + " crystal, " + c.countItem(Items.GOLD_INGOT) + " gold, " + c.countItem(Items.EMERALD) + " emeralds";
	}

	/**
	 * Never from nothing: a Master Jeweller (every Mega recipe, the meteorite one too) with a Master trainer in the village
	 * and nothing Mega in the store, his chest or his pockets works 1200 ticks and no crystal, Mega Stone, Key Stone or
	 * ore appears anywhere.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaJewellerNothing"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaJewellerNothing")
	public void neverCutsFromNothingOverALongShift(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(Items.GOLD_INGOT, 8), new ItemStack(Items.EMERALD, 16));
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		helper.assertTrue(!MegaStones.stonesOf(master).isEmpty(), "the Master's team has no Mega Stone to cut");
		Villager v = MegaStoneCompatTests.jeweller(helper, 5);
		helper.assertTrue(megaAnywhere(helper, own, v).isEmpty(), "Mega items before the shift: " + megaAnywhere(helper, own, v));
		MegaStoneCompatTests.work(helper, v);
		helper.runAfterDelay(1200, () -> {
			String found = megaAnywhere(helper, own, v);
			helper.assertTrue(found.isEmpty(), "made from nothing in 1200 ticks: " + found + " says " + MegaStoneCompatTests.line(helper, v));
			Container store = MegaStoneCompatTests.store(helper);
			helper.assertTrue(store.countItem(Items.GOLD_INGOT) == 8 && store.countItem(Items.EMERALD) == 16,
				"the store's gold and emeralds were spent with no crystal: " + counts(store));
			master.discard();
			helper.succeed();
		});
	}

	/**
	 * Both sides of the emeralds' count: crystal, 2 gold and 3 emeralds in the store (one short) cut nothing and take
	 * nothing in 400 ticks; with the fourth emerald put in the store, exactly one stone is cut and the makings are gone.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaJewellerOneShort"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaJewellerOneShort")
	public void oneEmeraldShortThenExactlyEnough(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2),
			new ItemStack(Items.EMERALD, 3));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		MegaStoneCompatTests.work(helper, v);
		boolean[] topped = {false};
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(MegaStoneCompatTests.stonesIn(own).isEmpty() && MegaStoneCompatTests.stonesIn(store).isEmpty(),
				"cut " + MegaStoneCompatTests.stonesIn(own) + " one emerald short");
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(store, 1, 2, 3), "makings taken while one emerald short: " + counts(store) + " left");
			store.setItem(10, new ItemStack(Items.EMERALD, 1));
			topped[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(topped[0], "not yet topped up");
			List<Item> made = MegaStoneCompatTests.stonesIn(own);
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "stones " + made + ", wanted one of " + wanted + "; says "
				+ MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(store, 0, 0, 0), "store makings left: " + counts(store));
		});
	}

	/**
	 * A surplus leaves exactly the rest: 1 crystal, 5 gold and 9 emeralds in the store make one stone (one crystal, one
	 * stone) and leave exactly 3 gold and 5 emeralds; long after, still one stone.
	 */
	//$ gametest_ticks_batch AREA '2200' '"qaJewellerSurplus"'
	@GameTest(template = AREA, timeoutTicks = 2200, batch = "qaJewellerSurplus")
	public void aSurplusLeavesExactlyTheRest(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 5),
			new ItemStack(Items.EMERALD, 9));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		helper.assertTrue(MegaStones.stonesOf(master).size() >= 1, "the Master's team has no Mega Stone");
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		MegaStoneCompatTests.work(helper, v);
		helper.runAfterDelay(2000, () -> {
			List<Item> made = MegaStoneCompatTests.stonesIn(own);
			made.addAll(MegaStoneCompatTests.stonesIn(store));
			helper.assertTrue(made.size() == 1, "stones " + made + " from one crystal; says " + MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(store, 0, 3, 5), "store left " + counts(store) + ", expected 0 crystal, 3 gold, 5 emeralds");
			master.discard();
			helper.succeed();
		});
	}

	/**
	 * Only the Storehouse's storage: the full makings in a plain chest beside his workstation that is not the store (more
	 * than 8 blocks from any Storehouse) are never used, and nothing is cut.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaJewellerStray"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaJewellerStray")
	public void notFromAChestThatIsntTheStore(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper);
		helper.setBlock(STRAY, Blocks.CHEST);
		Container stray = helper.getBlockEntity(STRAY);
		stray.setItem(0, new ItemStack(ms("mega_stone_crystal")));
		stray.setItem(1, new ItemStack(Items.GOLD_INGOT, 2));
		stray.setItem(2, new ItemStack(Items.EMERALD, 4));
		stray.setItem(3, new ItemStack(ms("keystone_ore")));
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		MegaStoneCompatTests.work(helper, v);
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(stray, 1, 2, 4) && stray.countItem(ms("keystone_ore")) == 1,
				"makings taken from a chest that isn't the store: " + counts(stray) + ", " + stray.countItem(ms("keystone_ore")) + " ore left");
			helper.assertTrue(MegaStoneCompatTests.stonesIn(own).isEmpty() && own.countItem(ms("keystone")) == 0,
				"cut " + MegaStoneCompatTests.stonesIn(own) + " / " + own.countItem(ms("keystone")) + " Key Stones without the store");
			master.discard();
			helper.succeed();
		});
	}

	/**
	 * Not from his own pockets: a crystal the Jeweller carries (not put in the store) with the gold and emeralds in the
	 * store is not cut, and the crystal and the store's makings stay put.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaJewellerPockets"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaJewellerPockets")
	public void notFromACrystalInHisPockets(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		v.getInventory().addItem(new ItemStack(ms("mega_stone_crystal")));
		MegaStoneCompatTests.work(helper, v);
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(MegaStoneCompatTests.stonesIn(own).isEmpty() && MegaStoneCompatTests.stonesIn(store).isEmpty()
				&& MegaStoneCompatTests.stonesIn(v.getInventory()).isEmpty(), "cut a stone from the crystal in his pockets: " + MegaStoneCompatTests.stonesIn(own));
			helper.assertTrue(v.getInventory().countItem(ms("mega_stone_crystal")) == 1 && MegaStoneCompatTests.noMakingsTaken(store, 0, 2, 4),
				"taken: his pockets hold " + v.getInventory().countItem(ms("mega_stone_crystal")) + " crystal, the store " + counts(store));
			master.discard();
			helper.succeed();
		});
	}

	/**
	 * The meteorite recipe's boundary (a Miner's finds reach the store too): 3 Mega meteorite ores with the gold and
	 * emeralds cut nothing; with the fourth ore in the store, exactly one stone, and all four ores are gone.
	 */
	//$ gametest_ticks_batch AREA '2200' '"qaJewellerMeteorite"'
	@GameTest(template = AREA, timeoutTicks = 2200, batch = "qaJewellerMeteorite")
	public void threeMeteoriteOresAreNotEnoughFourAre(GameTestHelper helper) {
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("mega_meteorid_fire_ore"), 2), new ItemStack(ms("mega_meteorid_water_ore")),
			new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 4));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		Villager v = MegaStoneCompatTests.jeweller(helper, 4);
		MegaStoneCompatTests.work(helper, v);
		boolean[] topped = {false};
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(MegaStoneCompatTests.stonesIn(own).isEmpty(), "cut " + MegaStoneCompatTests.stonesIn(own) + " from 3 meteorite ores");
			int ores = store.countItem(ms("mega_meteorid_fire_ore")) + store.countItem(ms("mega_meteorid_water_ore"));
			helper.assertTrue(ores == 3 && MegaStoneCompatTests.noMakingsTaken(store, 0, 2, 4), "taken while short: " + ores + " ores, " + counts(store));
			store.setItem(10, new ItemStack(ms("mega_meteorid_dawn_ore")));
			topped[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(topped[0], "not yet topped up");
			List<Item> made = MegaStoneCompatTests.stonesIn(own);
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "stones " + made + ", wanted one of " + wanted + "; says "
				+ MegaStoneCompatTests.line(helper, v));
			int ores = store.countItem(ms("mega_meteorid_fire_ore")) + store.countItem(ms("mega_meteorid_water_ore"))
				+ store.countItem(ms("mega_meteorid_dawn_ore"));
			helper.assertTrue(ores == 0 && MegaStoneCompatTests.noMakingsTaken(store, 0, 0, 0), "left " + ores + " ores, " + counts(store));
		});
	}

	/**
	 * The switches over a real shift: with the Jeweller off (M34) for 300 ticks, then {@code villageMegas} off for 300
	 * more, the stocked store is untouched and nothing is cut; switched back on, the same store makes the stone (so the
	 * setup could cut one all along).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaJewellerSwitches"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaJewellerSwitches")
	public void switchedOffOverAShiftThenOn(GameTestHelper helper) {
		boolean megas = MegaBracelets.ENABLED;
		boolean jewellers = Jewellers.ENABLED;
		PartnerShowsCompatTests.after(helper, () -> {
			MegaBracelets.ENABLED = megas;
			Jewellers.ENABLED = jewellers;
		});
		Container own = MegaStoneCompatTests.shop(helper, new ItemStack(ms("mega_stone_crystal")), new ItemStack(Items.GOLD_INGOT, 2),
			new ItemStack(Items.EMERALD, 4));
		Container store = MegaStoneCompatTests.store(helper);
		Villager master = MegaStoneCompatTests.villager(helper, TRAINER, ModVillagers.TRAINER, 5);
		List<Item> wanted = MegaStones.stonesOf(master);
		Villager v = MegaStoneCompatTests.jeweller(helper, 3);
		Jewellers.ENABLED = false;
		MegaStoneCompatTests.work(helper, v);
		boolean[] on = {false};
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(store, 1, 2, 4) && MegaStoneCompatTests.stonesIn(own).isEmpty(),
				"Jewellers off: store " + counts(store) + ", cut " + MegaStoneCompatTests.stonesIn(own));
			Jewellers.ENABLED = jewellers;
			MegaBracelets.ENABLED = false;
		});
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(MegaStoneCompatTests.store(helper).countItem(ms("mega_stone_crystal")) == 1 && MegaStoneCompatTests.stonesIn(own).isEmpty(),
				"villageMegas off: store " + counts(store) + ", cut " + MegaStoneCompatTests.stonesIn(own));
			MegaBracelets.ENABLED = megas;
			MegaStones.forget();
			on[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(on[0], "still switched off");
			List<Item> made = MegaStoneCompatTests.stonesIn(own);
			helper.assertTrue(made.size() == 1 && wanted.contains(made.get(0)), "back on: stones " + made + ", wanted one of " + wanted + "; says "
				+ MegaStoneCompatTests.line(helper, v));
			helper.assertTrue(MegaStoneCompatTests.noMakingsTaken(store, 0, 0, 0), "back on: store left " + counts(store));
		});
	}
}
