package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.scribe.EnchantWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-05) of two old professions, written from README ("Armorers, toolsmiths, weaponsmiths and fletchers",
 * "Librarians: enchanted gear") and the CHANGELOG lines that brought them in.
 * <p>
 * Weaponsmiths "mend the village's worn gear the way an anvil does — each ingot, plank, diamond or leather puts back a
 * quarter of a tool's, weapon's or armor piece's durability ... and put each piece back where it was." Covered here:
 * the piece goes back to the very chest it came from and the anvil's rule takes no more units than it needs; each piece
 * takes its own material (planks for wood, leather for leather); a piece that isn't worn is left alone; no material
 * means nothing taken and nothing lost; enchantments and name survive. The grindstone broken mid-job and a save and
 * reload mid-job fail on main: QaSmithScribe1105cBugTests.
 * <p>
 * Librarians "walk up to a guard and enchant the weapon, armor or bow they wear, then the tools the other workers have
 * in hand ... with lapis from their chest ... like an enchanting table at level 10 for a Novice up to level 30 for a
 * Master, 1 to 3 lapis a piece. Bookshelves round the table (two blocks out with nothing in between, as for your own
 * table) add a level for every three". Covered: no lapis, nothing; enchanted gear skipped; the lapis a Novice, a Master
 * and a Novice with a full ring spend; guards first; the table broken on the way; a save and reload on the way; where
 * a bookshelf counts.
 */
public class QaSmithScribe1105cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos GRINDSTONE = new BlockPos(2, 2, 2);
	/** The chest by the grindstone the smith keeps materials in. */
	static final BlockPos STOCK = new BlockPos(2, 2, 4);
	/** A second chest by the grindstone: the player's drop-off for worn gear. */
	static final BlockPos DROP = new BlockPos(5, 2, 2);
	static final BlockPos POST = new BlockPos(19, 2, 19);
	static final BlockPos POST_CHEST = new BlockPos(19, 2, 17);

	// --- Helpers ---------------------------------------------------------------------------------------------------

	static Villager weaponsmith(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
		helper.setBlock(STOCK, Blocks.CHEST);
		helper.setBlock(DROP, Blocks.CHEST);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
		return smith;
	}

	static Villager guard(GameTestHelper helper, BlockPos at) {
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setNoAi(true); // standing still where the test put them
		return guard;
	}

	static ItemStack worn(Item item, int damage) {
		ItemStack stack = new ItemStack(item);
		stack.setDamageValue(damage);
		return stack;
	}

	static Container chest(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockEntity(pos);
	}

	/** The damage of the only {@code item} in {@code chest}; -1 if none, -2 if more than one. */
	static int damageIn(Container chest, Item item) {
		int damage = -1;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				damage = damage == -1 ? chest.getItem(i).getDamageValue() : -2;
			}
		}
		return damage;
	}

	static ItemStack find(Container chest, Item item) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				return chest.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	static int onGround(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	static BuilderBag bag(Villager v) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v);
	}

	static int mended(Villager smith) {
		return ModAttachments.ITEMS_MENDED.getOrElse(smith, 0);
	}

	static Villager reload(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(copy != null, "a villager to load into");
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	private static Holder<Enchantment> enchantment(GameTestHelper helper, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	// --- Weaponsmith -----------------------------------------------------------------------------------------------

	/**
	 * A worn iron sword (200 of 250 used) dropped in the drop-off chest by the grindstone, ten ingots in the other chest:
	 * like an anvil, four ingots bring it to full (a quarter each; no more than it needs), and the sword goes back to the
	 * drop-off chest, not the one the iron came from.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa1105cMendBack"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1105cMendBack")
	public void qaAWornSwordIsMendedToFullWithFourIngotsAndGoesBackToItsChest(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
		helper.succeedWhen(() -> {
			helper.assertTrue(mended(smith) == 1, "mended " + mended(smith));
			helper.assertTrue(damageIn(drop, Items.IRON_SWORD) == 0, "the sword in the drop-off chest has damage " + damageIn(drop, Items.IRON_SWORD)
				+ " (-1: not there; in the stock chest: " + damageIn(stock, Items.IRON_SWORD) + ")");
			helper.assertTrue(damageIn(stock, Items.IRON_SWORD) == -1, "a sword went into the stock chest");
			helper.assertTrue(stock.countItem(Items.IRON_INGOT) == 6, "four ingots should mend 200 of 250 (62 each): " + stock.countItem(Items.IRON_INGOT) + " of 10 left");
			helper.assertTrue(bag(smith).isEmpty(), "the smith still carries " + bag(smith).stacks());
		});
	}

	/**
	 * Each piece is mended with its own material, as at an anvil: a worn wooden sword with planks and a worn leather
	 * tunic with leather, while the iron beside them is left alone. Both go back to the drop-off chest, whole.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cMendOwnMaterial"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cMendOwnMaterial")
	public void qaWoodIsMendedWithPlanksAndLeatherWithLeatherNotIron(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.WOODEN_SWORD, 50)); // of 59: a quarter is 14, four planks
		drop.setItem(1, worn(Items.LEATHER_CHESTPLATE, 60)); // of 80: a quarter is 20, three leather
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		stock.setItem(1, new ItemStack(Items.OAK_PLANKS, 10));
		stock.setItem(2, new ItemStack(Items.LEATHER, 10));
		helper.succeedWhen(() -> {
			helper.assertTrue(mended(smith) == 2, "mended " + mended(smith));
			helper.assertTrue(damageIn(drop, Items.WOODEN_SWORD) == 0, "wooden sword damage " + damageIn(drop, Items.WOODEN_SWORD));
			helper.assertTrue(damageIn(drop, Items.LEATHER_CHESTPLATE) == 0, "leather tunic damage " + damageIn(drop, Items.LEATHER_CHESTPLATE));
			helper.assertTrue(stock.countItem(Items.OAK_PLANKS) == 6, "planks left " + stock.countItem(Items.OAK_PLANKS) + " of 10 (4 needed)");
			helper.assertTrue(stock.countItem(Items.LEATHER) == 7, "leather left " + stock.countItem(Items.LEATHER) + " of 10 (3 needed)");
			helper.assertTrue(stock.countItem(Items.IRON_INGOT) == 5, "iron was spent on wood or leather: " + stock.countItem(Items.IRON_INGOT) + " of 5 left");
			helper.assertTrue(bag(smith).isEmpty(), "the smith still carries " + bag(smith).stacks());
		});
	}

	/**
	 * A barely-used sword (10 of 250) beside a worn pickaxe: the pickaxe is mended, the sword is left as it is for good
	 * (watched 300 ticks after the pickaxe is done), and no iron is spent on it.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cMendNotWorn"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cMendNotWorn")
	public void qaAPieceThatIsNotWornIsLeftAlone(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 10));
		drop.setItem(1, worn(Items.IRON_PICKAXE, 200));
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 8));
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			if (doneAt[0] < 0 && damageIn(drop, Items.IRON_PICKAXE) == 0) {
				doneAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(doneAt[0] >= 0, "the worn pickaxe isn't mended: damage " + damageIn(drop, Items.IRON_PICKAXE));
			helper.assertTrue(helper.getTick() >= doneAt[0] + 300, "watching the sword");
			helper.assertTrue(damageIn(drop, Items.IRON_SWORD) == 10, "the barely-used sword has damage " + damageIn(drop, Items.IRON_SWORD) + " (was 10)");
			helper.assertTrue(stock.countItem(Items.IRON_INGOT) == 4, "iron left " + stock.countItem(Items.IRON_INGOT) + " of 8 (4 for the pickaxe)");
			helper.assertTrue(mended(smith) == 1, "mended " + mended(smith));
		});
	}

	/**
	 * A worn iron sword and nothing that mends it (gold, a diamond and leather, but no iron): after 1000 ticks the sword
	 * is still in its chest as it was, every other item where it was, and the smith carries nothing.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qa1105cMendNoMaterial"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa1105cMendNoMaterial")
	public void qaWithNothingToMendItWithNothingIsTakenOrLost(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		drop.setItem(0, worn(Items.IRON_SWORD, 200));
		stock.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		stock.setItem(1, new ItemStack(Items.DIAMOND, 1));
		stock.setItem(2, new ItemStack(Items.LEATHER, 4));
		helper.runAtTickTime(1000, () -> {
			helper.assertTrue(damageIn(drop, Items.IRON_SWORD) == 200, "the sword: damage " + damageIn(drop, Items.IRON_SWORD) + " (-1: gone from its chest)");
			helper.assertTrue(stock.countItem(Items.GOLD_INGOT) == 5 && stock.countItem(Items.DIAMOND) == 1 && stock.countItem(Items.LEATHER) == 4,
				"the stock changed: gold " + stock.countItem(Items.GOLD_INGOT) + ", diamond " + stock.countItem(Items.DIAMOND) + ", leather " + stock.countItem(Items.LEATHER));
			helper.assertTrue(bag(smith).isEmpty(), "the smith carries " + bag(smith).stacks());
			helper.assertTrue(mended(smith) == 0, "mended " + mended(smith));
			helper.succeed();
		});
	}

	/**
	 * A worn sword with Sharpness III and the name "Oathkeeper", one ingot to hand: it comes back a quarter mended
	 * (62 of 250) with its enchantment and name as they were.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa1105cMendKeepsEnchant"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa1105cMendKeepsEnchant")
	public void qaAnEnchantedNamedSwordKeepsItsEnchantmentAndName(GameTestHelper helper) {
		Villager smith = weaponsmith(helper);
		Container stock = chest(helper, STOCK);
		Container drop = chest(helper, DROP);
		Holder<Enchantment> sharpness = enchantment(helper, Enchantments.SHARPNESS);
		ItemStack sword = worn(Items.IRON_SWORD, 200);
		sword.enchant(sharpness, 3);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Oathkeeper"));
		drop.setItem(0, sword);
		stock.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(mended(smith) == 1, "mended " + mended(smith));
			ItemStack back = find(drop, Items.IRON_SWORD);
			helper.assertTrue(!back.isEmpty(), "the sword isn't back in the drop-off chest");
			helper.assertTrue(back.getDamageValue() == 138, "one ingot should put back 62 of 250: damage " + back.getDamageValue());
			helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(sharpness, back) == 3, "Sharpness is now " + EnchantmentHelper.getItemEnchantmentLevel(sharpness, back));
			helper.assertTrue("Oathkeeper".equals(back.getHoverName().getString()), "the name is now " + back.getHoverName().getString());
		});
	}

	// --- Librarian -------------------------------------------------------------------------------------------------

	private static final BlockPos LECTERN = new BlockPos(2, 2, 2);
	private static final BlockPos TABLE = new BlockPos(5, 2, 2);
	private static final BlockPos LAPIS_CHEST = new BlockPos(2, 2, 4);

	private static Villager librarian(GameTestHelper helper, BlockPos lectern, BlockPos table, BlockPos chest, int villagerLevel) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.setBlock(table, Blocks.ENCHANTING_TABLE);
		helper.setBlock(chest, Blocks.CHEST);
		Villager librarian = helper.spawn(EntityType.VILLAGER, lectern.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), librarian, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		librarian.setVillagerData(librarian.getVillagerData().setLevel(villagerLevel));
		return librarian;
	}

	private static Villager librarian(GameTestHelper helper, int villagerLevel) {
		return librarian(helper, LECTERN, TABLE, LAPIS_CHEST, villagerLevel);
	}

	private static int enchanted(Villager librarian) {
		return ModAttachments.ITEMS_ENCHANTED.getOrElse(librarian, 0);
	}

	/** Lapis the librarian still has: in the chest and in hand. */
	private static int lapis(Container chest, Villager librarian) {
		return chest.countItem(Items.LAPIS_LAZULI) + bag(librarian).count(Items.LAPIS_LAZULI);
	}

	/** A guard with a plain iron sword (and no other gear): the librarian enchants it, and {@code cost} lapis is spent. */
	private static void costRun(GameTestHelper helper, Villager librarian, BlockPos chestPos, int cost, String who) {
		Container chest = chest(helper, chestPos);
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 10));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.succeedWhen(() -> {
			helper.assertTrue(enchanted(librarian) == 1, who + ": enchanted " + enchanted(librarian));
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), who + ": the sword isn't enchanted");
			helper.assertTrue(lapis(chest, librarian) == 10 - cost, who + ": spent " + (10 - lapis(chest, librarian)) + " lapis on one piece, expected " + cost);
		});
	}

	/** A Novice (enchanting like a table at level 10) spends one lapis on a piece. */
	//$ gametest_ticks_batch AREA '1600' '"qa1105cScribeNovice"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1105cScribeNovice")
	public void qaANoviceLibrarianSpendsOneLapisAPiece(GameTestHelper helper) {
		costRun(helper, librarian(helper, 1), LAPIS_CHEST, 1, "a Novice, no bookshelves");
	}

	/** A Master (like a full enchanting table, level 30) spends three lapis on a piece. */
	//$ gametest_ticks_batch AREA '1600' '"qa1105cScribeMaster"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1105cScribeMaster")
	public void qaAMasterLibrarianSpendsThreeLapisAPiece(GameTestHelper helper) {
		costRun(helper, librarian(helper, 5), LAPIS_CHEST, 3, "a Master");
	}

	/** A Novice at a table ringed by fifteen bookshelves enchants at level 15, an enchanting table's middle slot: two lapis. */
	//$ gametest_ticks_batch AREA '1600' '"qa1105cScribeRing"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1105cScribeRing")
	public void qaANoviceWithAFullRingOfShelvesSpendsTwoLapis(GameTestHelper helper) {
		BlockPos table = new BlockPos(8, 2, 8);
		BlockPos lectern = new BlockPos(8, 2, 3);
		BlockPos chest = new BlockPos(5, 2, 3);
		Villager librarian = librarian(helper, lectern, table, chest, 1);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !(dx == 0 && dz == -2)) {
					helper.setBlock(table.offset(dx, 0, dz), Blocks.BOOKSHELF);
				}
			}
		}
		helper.assertTrue(EnchantWork.shelves(helper.getLevel(), helper.absolutePos(table)) == 15, "setup: the ring");
		costRun(helper, librarian, chest, 2, "a Novice at a full ring");
	}

	/** No lapis anywhere (gold and a book in the chest): the guard's sword stays plain and nothing is enchanted. */
	//$ gametest_ticks_batch AREA '1100' '"qa1105cScribeNoLapis"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qa1105cScribeNoLapis")
	public void qaWithoutLapisNothingIsEnchanted(GameTestHelper helper) {
		Villager librarian = librarian(helper, 1);
		Container chest = chest(helper, LAPIS_CHEST);
		chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		chest.setItem(1, new ItemStack(Items.BOOK, 2));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.runAtTickTime(1000, () -> {
			helper.assertFalse(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "the sword was enchanted without lapis");
			helper.assertTrue(enchanted(librarian) == 0, "enchanted " + enchanted(librarian));
			helper.assertTrue(chest.countItem(Items.GOLD_INGOT) == 5 && chest.countItem(Items.BOOK) == 2, "the chest changed");
			helper.succeed();
		});
	}

	/**
	 * A guard with a sword already enchanted (Sharpness I) and a plain helmet: the helmet is enchanted, the sword keeps
	 * exactly the enchantment it had, and only the helmet's lapis is spent.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa1105cScribeSkipsEnchanted"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1105cScribeSkipsEnchanted")
	public void qaGearAlreadyEnchantedIsSkipped(GameTestHelper helper) {
		Villager librarian = librarian(helper, 1);
		Container chest = chest(helper, LAPIS_CHEST);
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 5));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.enchant(enchantment(helper, Enchantments.SHARPNESS), 1);
		ItemEnchantments before = sword.getEnchantments();
		guard.setItemSlot(EquipmentSlot.MAINHAND, sword);
		guard.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		long[] firstAt = {-1};
		helper.onEachTick(() -> {
			if (firstAt[0] < 0 && enchanted(librarian) >= 1) {
				firstAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(firstAt[0] >= 0 && helper.getTick() >= firstAt[0] + 300, "waiting for the helmet, then 300 ticks more");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.HEAD).isEnchanted(), "the plain helmet isn't enchanted");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).getEnchantments().equals(before),
				"the already-enchanted sword was changed: " + guard.getItemBySlot(EquipmentSlot.MAINHAND).getEnchantments() + " (was " + before + ")");
			helper.assertTrue(enchanted(librarian) == 1, "enchanted " + enchanted(librarian) + " pieces (only the helmet needed it)");
			helper.assertTrue(lapis(chest, librarian) == 4, "lapis left " + lapis(chest, librarian) + " of 5");
		});
	}

	/**
	 * Guards first: a miner-like worker (an Armorer) with a plain pickaxe stands close by, a guard with a plain sword far
	 * off, and there is lapis for only one piece: the guard's sword gets it, the pickaxe stays plain.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cScribeGuardsFirst"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cScribeGuardsFirst")
	public void qaTheGuardIsEnchantedBeforeTheWorkerNearby(GameTestHelper helper) {
		Villager librarian = librarian(helper, 1);
		Container chest = chest(helper, LAPIS_CHEST);
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 1));
		helper.setBlock(new BlockPos(6, 2, 7), Blocks.BLAST_FURNACE);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
		Jobs.employ(helper.getLevel(), worker, helper.absolutePos(new BlockPos(6, 2, 7)), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		worker.setNoAi(true);
		worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.succeedWhen(() -> {
			helper.assertTrue(enchanted(librarian) == 1, "enchanted " + enchanted(librarian));
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "the guard's sword isn't enchanted (the worker's pickaxe is: "
				+ worker.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted() + ")");
			helper.assertFalse(worker.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "the worker's pickaxe got the only lapis");
		});
	}

	/**
	 * The Enchanting Table broken while the librarian walks to the guard with the lapis: nothing is enchanted while it's
	 * gone (300 ticks) and no lapis is spent; with a table back, the sword is enchanted for one lapis in all.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1105cScribeTableGone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1105cScribeTableGone")
	public void qaTheTableBrokenOnTheWayEnchantsNothingAndSpendsNoLapis(GameTestHelper helper) {
		Villager librarian = librarian(helper, 1);
		Container chest = chest(helper, LAPIS_CHEST);
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 3));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		long[] brokenAt = {-1};
		boolean[] back = {false};
		helper.onEachTick(() -> {
			if (brokenAt[0] < 0 && bag(librarian).count(Items.LAPIS_LAZULI) >= 1) {
				helper.destroyBlock(TABLE);
				brokenAt[0] = helper.getTick();
			}
			if (brokenAt[0] >= 0 && !back[0]) {
				if (guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted() || enchanted(librarian) > 0) {
					helper.fail("enchanted with the table gone, " + (helper.getTick() - brokenAt[0]) + " ticks after it was broken");
				}
				if (helper.getTick() >= brokenAt[0] + 300) {
					helper.assertTrue(lapis(chest, librarian) == 3, "lapis spent with the table gone: " + lapis(chest, librarian) + " of 3 left");
					helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
					back[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the table isn't back yet (broken at " + brokenAt[0] + ")");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "with the table back, the sword isn't enchanted");
			helper.assertTrue(lapis(chest, librarian) == 2, "lapis left " + lapis(chest, librarian) + " of 3 (one piece, a Novice)");
		});
	}

	/**
	 * A save and reload of the librarian on the way to the guard, lapis in hand: the sword is still enchanted, and for
	 * one lapis in all (the lapis in hand isn't lost or fetched twice).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qa1105cScribeReload"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa1105cScribeReload")
	public void qaALibrarianReloadedOnTheWayStillEnchantsForOneLapis(GameTestHelper helper) {
		Villager first = librarian(helper, 1);
		Container chest = chest(helper, LAPIS_CHEST);
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 3));
		Villager guard = guard(helper, new BlockPos(18, 2, 18));
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Villager[] librarian = {first};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (!reloaded[0] && bag(librarian[0]).count(Items.LAPIS_LAZULI) >= 1 && enchanted(librarian[0]) == 0) {
				librarian[0] = reload(helper, librarian[0]);
				reloaded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the librarian never fetched the lapis");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "the sword isn't enchanted after the reload");
			helper.assertTrue(lapis(chest, librarian[0]) == 2, "lapis left " + lapis(chest, librarian[0]) + " of 3 (one piece, a Novice)");
			helper.assertTrue(enchanted(librarian[0]) == 1, "enchanted " + enchanted(librarian[0]));
		});
	}

	/**
	 * Where a bookshelf counts, as at a player's table: two blocks out, on the table's level or one above, with air
	 * between. Touching the table, three out or two above, it doesn't. A two-high ring (thirty) still lifts a Novice only
	 * to 15, and of two tables by the lectern the one with more shelves is used.
	 */
	//$ gametest_batch AREA '"qa1105cScribeShelfPlaces"'
	@GameTest(template = AREA, batch = "qa1105cScribeShelfPlaces")
	public void qaABookshelfCountsOnlyWhereAPlayersTableCountsIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		var level = helper.getLevel();
		BlockPos table = new BlockPos(10, 2, 10);
		helper.setBlock(table, Blocks.ENCHANTING_TABLE);
		BlockPos abs = helper.absolutePos(table);
		helper.setBlock(table.offset(1, 0, 0), Blocks.BOOKSHELF); // touching
		helper.setBlock(table.offset(-3, 0, 0), Blocks.BOOKSHELF); // three out
		helper.setBlock(table.offset(0, 2, 2), Blocks.BOOKSHELF); // two out, two up
		helper.assertTrue(EnchantWork.shelves(level, abs) == 0, "shelves touching, three out or two up count: " + EnchantWork.shelves(level, abs));
		helper.setBlock(table.offset(0, 0, 2), Blocks.BOOKSHELF);
		helper.setBlock(table.offset(0, 1, -2), Blocks.BOOKSHELF);
		helper.assertTrue(EnchantWork.shelves(level, abs) == 2, "two out on its level and one up should count 2: " + EnchantWork.shelves(level, abs));
		helper.setBlock(table.offset(1, 0, 0), Blocks.AIR);
		helper.setBlock(table.offset(-3, 0, 0), Blocks.AIR);
		helper.setBlock(table.offset(0, 2, 2), Blocks.AIR);
		for (int dy = 0; dy <= 1; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
						helper.setBlock(table.offset(dx, dy, dz), Blocks.BOOKSHELF);
					}
				}
			}
		}
		int shelves = EnchantWork.shelves(level, abs);
		Villager novice = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		helper.assertTrue(EnchantWork.strength(novice, shelves) == 15, "a Novice at a two-high ring (" + shelves + " shelves): strength "
			+ EnchantWork.strength(novice, shelves) + ", a player's table stops at 15 shelves");
		BlockPos lectern = new BlockPos(10, 2, 5);
		helper.setBlock(lectern, Blocks.LECTERN);
		BlockPos bare = new BlockPos(13, 2, 5);
		helper.setBlock(bare, Blocks.ENCHANTING_TABLE);
		helper.assertTrue(abs.equals(EnchantWork.table(level, helper.absolutePos(lectern))), "the lectern picked the table without shelves: "
			+ EnchantWork.table(level, helper.absolutePos(lectern)));
		helper.succeed();
	}
}
