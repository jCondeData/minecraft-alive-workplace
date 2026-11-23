package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * QA (lane c, 2026-11-22), from the spec, not the code: README "Beekeepers" and "Clerics: potions for the guards", the
 * wiki's villager-jobs table and the lang lines (message.aliveworkplace.beekeeper.* / alchemist.*). The happy paths
 * through the player's own entry point (a sneak-right-click with a glass bottle), the boundaries the spec names (a full
 * hive is honey level 5; honeycomb once the chests hold 16 honey bottles; three potions of each kind), missing
 * materials, a hive broken and the worker reloaded mid-job, and every status line these jobs show.
 */
public class QaBeekeeperAlchemist1122cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos APIARY = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HIVE = new BlockPos(9, 3, 9);
	private static final BlockPos STAND = new BlockPos(2, 2, 2);

	// --- Beekeeper ---------------------------------------------------------------------------------------------------

	/**
	 * README: "Stand a villager by a beehive ... and sneak-right-click them with a glass bottle: they become a Beekeeper
	 * ... a full hive is harvested with a glass bottle from the chests by their hive (a honey bottle)". Hired by the
	 * player at a full hive over a lit campfire, with one glass bottle in the chest beside it: one honey bottle there.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaBee1122cHired"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBee1122cHired")
	public void aBeekeeperHiredAtAHiveBottlesItsHoney(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos hive = new BlockPos(4, 3, 4);
		helper.setBlock(hive.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(hive, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		BlockPos chestPos = new BlockPos(6, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
		StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), keeper, new ItemStack(Items.GLASS_BOTTLE), true);
		helper.assertTrue(BeekeeperWork.isBeekeeper(keeper), "the sneak-right-click with a glass bottle by a hive didn't make a Beekeeper: "
			+ StationsSpecGameTests.name(StationsSpecGameTests.job(keeper)));
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1 && chest.countItem(Items.GLASS_BOTTLE) == 0,
				"chest: honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass bottles " + chest.countItem(Items.GLASS_BOTTLE));
			helper.assertTrue(helper.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is still full");
		});
	}

	/**
	 * Boundary of "a full hive is harvested": honey level 4 is not full. With bottles to hand the hive is left alone
	 * (nothing taken from the chest) for 500 ticks; once it reaches level 5 it is harvested.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaBee1122cLevelFour"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaBee1122cLevelFour")
	public void aHiveAtHoneyLevelFourWaitsUntilFull(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		hive(helper, 4);
		Container chest = helper.getBlockEntity(CHEST);
		AtomicBoolean filled = new AtomicBoolean();
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(helper.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 4, "a level-4 hive was harvested");
			helper.assertTrue(chest.countItem(Items.GLASS_BOTTLE) + ModAttachments.BUILDER_BAG.getOrCreate(keeper).count(Items.GLASS_BOTTLE) == 2
				&& chest.countItem(Items.HONEY_BOTTLE) == 0, "bottles used at level 4: glass " + chest.countItem(Items.GLASS_BOTTLE)
				+ ", honey " + chest.countItem(Items.HONEY_BOTTLE));
			helper.setBlock(HIVE, helper.getBlockState(HIVE).setValue(BeehiveBlock.HONEY_LEVEL, 5));
			filled.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filled.get(), "still waiting at level 4");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1 && chest.countItem(Items.GLASS_BOTTLE) == 1,
				"after filling: honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass " + chest.countItem(Items.GLASS_BOTTLE));
		});
	}

	/**
	 * Missing materials: a full hive and no glass bottle or shears anywhere. After 600 ticks the hive is still full,
	 * the chest's other things are untouched, nothing is counted, and the line over his head reads
	 * "Beekeeper · 0 harvests" / "watching the bees".
	 */
	//$ gametest_ticks_batch AREA '800' '"qaBee1122cNoTools"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaBee1122cNoTools")
	public void withoutBottlesOrShearsAFullHiveWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager keeper = beekeeper(helper, new ItemStack(Items.STICK, 5), new ItemStack(Items.HONEY_BOTTLE, 2));
		hive(helper, 5);
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(helper.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 5, "harvested with no tool");
			helper.assertTrue(chest.countItem(Items.STICK) == 5 && chest.countItem(Items.HONEY_BOTTLE) == 2 && chest.countItem(Items.HONEYCOMB) == 0,
				"chest changed: sticks " + chest.countItem(Items.STICK) + ", honey " + chest.countItem(Items.HONEY_BOTTLE));
			helper.assertTrue(ModAttachments.HIVES_HARVESTED.getOrElse(keeper, 0) == 0, "a harvest counted");
			assertStatus(helper, keeper, "Beekeeper · 0 harvests", "watching the bees");
			helper.succeed();
		});
	}

	/**
	 * README: "they switch to honeycomb once the chests have 16 honey bottles". With exactly 16 honey bottles, glass
	 * bottles and shears in the chest: three honeycomb, the glass bottles untouched and still 16 honey bottles.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaBee1122cSixteen"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBee1122cSixteen")
	public void sixteenHoneyBottlesSwitchToHoneycomb(GameTestHelper helper) {
		Leftovers.clear(helper);
		beekeeper(helper, new ItemStack(Items.HONEY_BOTTLE, 16), new ItemStack(Items.GLASS_BOTTLE, 4), new ItemStack(Items.SHEARS));
		hive(helper, 5);
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.HONEYCOMB) == 3, "honeycomb " + chest.countItem(Items.HONEYCOMB)
				+ " (honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass " + chest.countItem(Items.GLASS_BOTTLE) + ")");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 16 && chest.countItem(Items.GLASS_BOTTLE) == 4,
				"bottled at 16: honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass " + chest.countItem(Items.GLASS_BOTTLE));
		});
	}

	/** The other side of 16: with 15 honey bottles (and shears) the hive is still bottled, making the 16th. */
	//$ gametest_ticks_batch AREA '1600' '"qaBee1122cFifteen"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBee1122cFifteen")
	public void fifteenHoneyBottlesStillBottle(GameTestHelper helper) {
		Leftovers.clear(helper);
		beekeeper(helper, new ItemStack(Items.HONEY_BOTTLE, 15), new ItemStack(Items.GLASS_BOTTLE, 4), new ItemStack(Items.SHEARS));
		hive(helper, 5);
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 16 && chest.countItem(Items.GLASS_BOTTLE) == 3 && chest.countItem(Items.HONEYCOMB) == 0,
				"honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass " + chest.countItem(Items.GLASS_BOTTLE) + ", honeycomb " + chest.countItem(Items.HONEYCOMB));
		});
	}

	/**
	 * The hive broken mid-job (while he is on his way to harvest it): no honey from nowhere, the glass bottle not lost
	 * (in the chest or his bag), and the line says "no beehives within 16 blocks".
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaBee1122cBroken"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBee1122cBroken")
	public void aHiveBrokenMidHarvestCostsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager keeper = beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		hive(helper, 5);
		Container chest = helper.getBlockEntity(CHEST);
		AtomicReference<Long> brokenAt = new AtomicReference<>();
		helper.onEachTick(() -> {
			if (brokenAt.get() == null && statusLine(helper, keeper).equals("harvesting a hive")) {
				helper.setBlock(HIVE, Blocks.AIR);
				brokenAt.set(helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() != null, "he never set out to harvest");
			helper.assertTrue(helper.getTick() > brokenAt.get() + 200, "waiting after the break");
			int glass = chest.countItem(Items.GLASS_BOTTLE) + ModAttachments.BUILDER_BAG.getOrCreate(keeper).count(Items.GLASS_BOTTLE);
			int honey = chest.countItem(Items.HONEY_BOTTLE) + ModAttachments.BUILDER_BAG.getOrCreate(keeper).count(Items.HONEY_BOTTLE);
			helper.assertTrue(glass == 2 && honey == 0, "after the break: glass " + glass + ", honey " + honey);
			helper.assertTrue(ModAttachments.HIVES_HARVESTED.getOrElse(keeper, 0) == 0, "a harvest counted from a broken hive");
			assertStatus(helper, keeper, "Beekeeper · 0 harvests", "no beehives within 16 blocks");
		});
	}

	/**
	 * Save and reload mid-job: the Beekeeper is saved and loaded again while harvesting (bottle maybe in his bag). The
	 * job finishes: one honey bottle in the chest, one glass bottle left, the hive emptied, one harvest counted
	 * ("Beekeeper · 1 harvest").
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaBee1122cReload"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaBee1122cReload")
	public void aBeekeeperReloadedMidHarvestFinishesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		AtomicReference<Villager> keeper = new AtomicReference<>(beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2)));
		hive(helper, 5);
		Container chest = helper.getBlockEntity(CHEST);
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (!reloaded.get() && chest.countItem(Items.GLASS_BOTTLE) == 1) { // the bottle is in his bag
				keeper.set(QaSmithScribe1105cGameTests.reload(helper, keeper.get()));
				reloaded.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "never took the bottle");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 1 && chest.countItem(Items.GLASS_BOTTLE) == 1,
				"after the reload: honey " + chest.countItem(Items.HONEY_BOTTLE) + ", glass " + chest.countItem(Items.GLASS_BOTTLE)
				+ ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(keeper.get()).stacks());
			helper.assertTrue(helper.getBlockState(HIVE).getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the hive is still full");
			helper.assertTrue(ModAttachments.HIVES_HARVESTED.getOrElse(keeper.get(), 0) == 1, "harvests " + ModAttachments.HIVES_HARVESTED.getOrElse(keeper.get(), 0));
			WorkerStatus.Entry e = WorkerStatus.get(keeper.get(), helper.getLevel().getGameTime());
			helper.assertTrue(e != null && e.title().getString().equals("Beekeeper · 1 harvest"), "title: " + (e == null ? null : e.title().getString()));
		});
	}

	// --- Cleric (alchemist) ------------------------------------------------------------------------------------------

	/**
	 * README: potions of regeneration "from the chest by the stand: nether wart, ... a ghast tear ..., with blaze powder
	 * as fuel, and water bottles". Water bottles straight from the chest (no water nearby): three potions of regeneration,
	 * counted in "Cleric · 3 potions brewed".
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaAlch1122cRegen"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaAlch1122cRegen")
	public void aClericBrewsRegenerationFromWaterBottles(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager cleric = cleric(helper, water(), water(), water(), new ItemStack(Items.NETHER_WART), new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.BLAZE_POWDER));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(potions(chest, Potions.REGENERATION) == 3, "regeneration potions: " + potions(chest, Potions.REGENERATION));
			assertStatus(helper, cleric, "Cleric · 3 potions brewed", null);
		});
	}

	/**
	 * Strength takes blaze powder twice over: as the fuel and as the ingredient. With two blaze powder, nether wart and
	 * three water bottles: three potions of strength.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaAlch1122cStrength"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaAlch1122cStrength")
	public void aClericBrewsStrengthWithBlazePowderAsFuelAndIngredient(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleric(helper, water(), water(), water(), new ItemStack(Items.NETHER_WART), new ItemStack(Items.BLAZE_POWDER, 2));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> helper.assertTrue(potions(chest, Potions.STRENGTH) == 3,
			"strength potions: " + potions(chest, Potions.STRENGTH) + ", stand: " + standItems(helper)));
	}

	/**
	 * README: "three of each". With three potions of healing already in the chest and the makings of three more, nothing
	 * is brewed: the makings stay in the chest, the stand stays empty, and the line reads "nothing to brew". The blaze
	 * powder (the fuel) is also the strength ingredient, so three potions of strength are stocked too: otherwise strength
	 * is short and rightly started.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaAlch1122cKeep"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaAlch1122cKeep")
	public void threeHealingPotionsInStockBrewNoMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
		ItemStack strength = PotionContents.createItemStack(Items.POTION, Potions.STRENGTH);
		Villager cleric = cleric(helper, healing.copy(), healing.copy(), healing.copy(), strength.copy(), strength.copy(), strength.copy(),
			water(), water(), water(), new ItemStack(Items.NETHER_WART),
			new ItemStack(Items.GLISTERING_MELON_SLICE), new ItemStack(Items.BLAZE_POWDER));
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(800, () -> {
			helper.assertTrue(potions(chest, Potions.HEALING) == 3, "healing potions: " + potions(chest, Potions.HEALING));
			helper.assertTrue(potions(chest, Potions.WATER) == 3 && chest.countItem(Items.NETHER_WART) == 1 && chest.countItem(Items.GLISTERING_MELON_SLICE) == 1,
				"makings used with three in stock: water " + potions(chest, Potions.WATER) + ", wart " + chest.countItem(Items.NETHER_WART)
				+ ", melon " + chest.countItem(Items.GLISTERING_MELON_SLICE) + ", stand " + standItems(helper));
			assertStatus(helper, cleric, "Cleric · 0 potions brewed", "nothing to brew");
			helper.succeed();
		});
	}

	/**
	 * Missing fuel: no blaze powder. Nothing is brewed and nothing is lost in 800 ticks (the makings are in the chest or
	 * the stand). (What follows once blaze powder arrives is B-filed: QaBeekeeperAlchemist1122cBugTests on the branch
	 * tests/beekeeper-alchemist-1123c.)
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaAlch1122cNoFuel"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaAlch1122cNoFuel")
	public void withoutBlazePowderNothingIsBrewedOrLost(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleric(helper, water(), water(), water(), new ItemStack(Items.NETHER_WART), new ItemStack(Items.GLISTERING_MELON_SLICE));
		Container chest = helper.getBlockEntity(CHEST);
		helper.runAfterDelay(800, () -> {
			BrewingStandBlockEntity stand = helper.getBlockEntity(STAND);
			int water = potions(chest, Potions.WATER) + potions(stand, Potions.WATER);
			int awkward = potions(chest, Potions.AWKWARD) + potions(stand, Potions.AWKWARD);
			helper.assertTrue(potions(chest, Potions.HEALING) + potions(stand, Potions.HEALING) == 0 && awkward == 0, "brewed without fuel");
			helper.assertTrue(water == 3 && chest.countItem(Items.NETHER_WART) + stand.countItem(Items.NETHER_WART) == 1
				&& chest.countItem(Items.GLISTERING_MELON_SLICE) + stand.countItem(Items.GLISTERING_MELON_SLICE) == 1,
				"makings lost without fuel: water " + water + ", chest " + chestItems(chest) + ", stand " + standItems(helper));
			helper.succeed();
		});
	}

	/** Missing chest: the line reads "needs a chest by the brewing stand", and the stand is left empty. */
	//$ gametest_ticks_batch AREA '400' '"qaAlch1122cNoChest"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaAlch1122cNoChest")
	public void aClericWithNoChestSaysSo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		helper.succeedWhen(() -> assertStatus(helper, cleric, "Cleric · 0 potions brewed", "needs a chest by the brewing stand"));
	}

	/**
	 * README: "Sneak-right-click a cleric with a glass bottle to hire them for your own guards." The player is told
	 * "<name> brews for your guards now." and the cleric works for them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaAlch1122cHire"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaAlch1122cHire")
	public void sneakRightClickWithABottleHiresTheCleric(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager cleric = cleric(helper);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		StationsSpecGameTests.rightClick(player, cleric, new ItemStack(Items.GLASS_BOTTLE), true);
		String expected = cleric.getDisplayName().getString() + " brews for your guards now.";
		helper.assertTrue(client.seen().stream().map(Component::getString).anyMatch(expected::equals),
			"told " + client.seen().stream().map(Component::getString).toList() + ", expected '" + expected + "'");
		var employer = ModAttachments.BUILDER_EMPLOYER.get(cleric);
		helper.assertTrue(employer != null && employer.name().equals(player.getGameProfile().getName()), "employer: " + employer);
		helper.assertTrue(cleric.getVillagerData().getProfession() == VillagerProfession.CLERIC, "no longer a cleric");
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	private static Villager beekeeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(APIARY, ModBlocks.APIARY);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(APIARY), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		helper.assertTrue(BeekeeperWork.isBeekeeper(villager), "setup: not a beekeeper");
		return villager;
	}

	private static void hive(GameTestHelper helper, int honey) {
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(HIVE, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, honey));
	}

	private static Villager cleric(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return cleric;
	}

	/** One water bottle (potions don't stack, so three are three slots, as a player's chest holds them). */
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

	private static String standItems(GameTestHelper helper) {
		return chestItems(helper.getBlockEntity(STAND));
	}

	private static String chestItems(Container c) {
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				out.append(i).append('=').append(c.getItem(i).getCount()).append(' ').append(c.getItem(i).getHoverName().getString()).append(", ");
			}
		}
		return out.append(']').toString();
	}

	private static String statusLine(GameTestHelper helper, Villager v) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		return e == null ? "" : e.line().getString();
	}

	/** The line over {@code v}'s head is {@code title} / {@code line} (line null: any). */
	private static void assertStatus(GameTestHelper helper, Villager v, String title, String line) {
		WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
		helper.assertTrue(e != null, "no status line over " + v.getName().getString());
		helper.assertTrue(e.title().getString().equals(title), "title '" + e.title().getString() + "', expected '" + title + "'");
		helper.assertTrue(line == null || e.line().getString().equals(line), "line '" + e.line().getString() + "', expected '" + line + "'");
	}
}
