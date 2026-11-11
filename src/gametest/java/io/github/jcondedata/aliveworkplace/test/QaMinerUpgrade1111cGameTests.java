package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.PickaxeCrafting;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-11, lane b's 36.11a / B1202, written from the spec): a Miner upgrades his pickaxe as soon as he has the
 * materials, at any level, whenever it is below iron (3 iron ingots + 2 sticks; diamond from 3 diamonds), from his own
 * chests, his bag or the village store; the old pickaxe goes back to his chest; raw iron he mined is smelted; ores left
 * as too hard are remembered (saved, default empty, capped at 256) and dug after the upgrade; the status line says
 * what he needs; {@code minerCraftsPickaxe} off keeps the old behavior.
 */
public class QaMinerUpgrade1111cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos FURNACE = new BlockPos(4, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);

	// --- setup -----------------------------------------------------------------------------------

	private record Unit(ServerLevel level, Villager miner, BlockPos bench, List<BlockPos> own, BuilderBag bag, Container chest) {
	}

	/** A Novice miner at his bench with a chest of {@code chest}, no quarry: for the crafting rules alone. */
	private static Unit unit(GameTestHelper helper, ItemStack... chest) {
		Leftovers.clear(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		villager.setNoAi(true);
		BlockPos bench = helper.absolutePos(BENCH);
		Miners.employ(level, villager, bench);
		helper.assertTrue(villager.getVillagerData().getLevel() == 1, "setup: the miner should be a Novice");
		List<BlockPos> own = SupplyContainers.find(level, bench, null);
		helper.assertTrue(own.contains(helper.absolutePos(CHEST)), "setup: the chest isn't the miner's own: " + own);
		return new Unit(level, villager, bench, own, ModAttachments.BUILDER_BAG.getOrCreate(villager), c);
	}

	private static PickaxeCrafting.Plan upgrade(Unit u, ItemStack hand) {
		return PickaxeCrafting.upgrade(u.level(), u.miner(), u.bench(), u.own(), u.bag(), hand);
	}

	private static ItemStack craft(Unit u, ItemStack hand) {
		return PickaxeCrafting.craft(u.level(), u.miner(), u.bench(), u.own(), u.bag(), hand);
	}

	private static PickaxeCrafting.Upgrade missing(Unit u, ItemStack hand) {
		return PickaxeCrafting.upgradeMissing(u.level(), u.miner(), u.bench(), u.own(), u.bag(), hand);
	}

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	/** A Novice miner holding {@code hand} at his bench, a stone quarry from {@link #MIN} to {@code max}, {@code chest} in his chest. */
	private static Setup setup(GameTestHelper helper, BlockPos max, ItemStack hand, ItemStack... chest) {
		Leftovers.clear(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(MIN, max)) {
			helper.setBlock(p, Blocks.STONE);
		}
		helper.setBlock(BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(BENCH));
		villager.setItemSlot(EquipmentSlot.MAINHAND, hand);
		QuarrySite site = Miners.start(level, villager, null, BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(max)),
			max.getY() - MIN.getY() + 1);
		site.setStairs(false);
		helper.assertTrue(villager.getVillagerData().getLevel() == 1, "setup: the miner should be a Novice");
		return new Setup(level, villager, site);
	}

	private static QuarrySite live(ServerLevel level, QuarrySite site) {
		return QuarrySiteManager.get(level).get(site.id());
	}

	private static String line(Setup s) {
		QuarrySite site = live(s.level(), s.site());
		return site == null ? "finished" : Miners.stateLine(site, ChatFormatting.WHITE).getString();
	}

	private static String state(GameTestHelper helper, Setup s, Villager miner) {
		QuarrySite site = live(s.level(), s.site());
		return site == null ? "finished" : site.status() + ", " + site.mined() + " dug, holding " + miner.getMainHandItem()
			+ ", miner at " + helper.relativePos(miner.blockPosition()) + ", line '" + line(s) + "'";
	}

	/** Every stack of {@code item} in the chest. */
	private static List<ItemStack> stacks(Container chest, Item item) {
		List<ItemStack> out = new java.util.ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(item)) {
				out.add(chest.getItem(i));
			}
		}
		return out;
	}

	/** Dropped {@code item}s lying around the test area (an old pickaxe that didn't make it into the chest). */
	private static int dropped(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(item)).size();
	}

	// --- the rules, both sides of each boundary ------------------------------------------------

	/**
	 * Stone in hand: 2 ingots make nothing (and say 1 more), 3 ingots with 1 stick make nothing (and say 1 Stick), 3 and
	 * 2 make an iron pickaxe taking exactly those. Nothing is taken while something is short.
	 */
	//$ gametest_batch AREA '"qa1111c_iron_bounds"'
	@GameTest(template = AREA, batch = "qa1111c_iron_bounds")
	public void twoIngotsOrOneStickAreNotEnoughThreeAndTwoAre(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.STICK, 2));
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		helper.assertTrue(upgrade(u, stone) == null, "2 ingots: an upgrade planned " + upgrade(u, stone));
		helper.assertTrue(craft(u, stone).isEmpty(), "2 ingots: a pickaxe made");
		PickaxeCrafting.Upgrade need = missing(u, stone);
		helper.assertTrue(need != null && need.item() == Items.IRON_INGOT && need.count() == 1, "2 ingots: missing " + need);
		helper.assertTrue(u.chest().countItem(Items.IRON_INGOT) == 2 && u.chest().countItem(Items.STICK) == 2, "taken while 1 ingot short");
		// 3 ingots, 1 stick, no planks.
		u.chest().setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		u.chest().setItem(1, new ItemStack(Items.STICK, 1));
		helper.assertTrue(upgrade(u, stone) == null, "1 stick: an upgrade planned");
		helper.assertTrue(craft(u, stone).isEmpty(), "1 stick: a pickaxe made");
		need = missing(u, stone);
		helper.assertTrue(need != null && need.item() == Items.STICK && need.count() == 1, "1 stick: missing " + need);
		helper.assertTrue(u.chest().countItem(Items.IRON_INGOT) == 3 && u.chest().countItem(Items.STICK) == 1, "taken while 1 stick short");
		// 3 ingots, 2 sticks (and 2 more of each that must stay).
		u.chest().setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		u.chest().setItem(1, new ItemStack(Items.STICK, 4));
		helper.assertTrue(missing(u, stone) == null, "nothing should be missing: " + missing(u, stone));
		ItemStack made = craft(u, stone);
		helper.assertTrue(made.is(Items.IRON_PICKAXE), "3 ingots + 2 sticks: made " + made);
		helper.assertTrue(u.chest().countItem(Items.IRON_INGOT) == 2 && u.chest().countItem(Items.STICK) == 2,
			"not exactly 3 ingots and 2 sticks taken: " + u.chest().countItem(Items.IRON_INGOT) + " ingots, " + u.chest().countItem(Items.STICK) + " sticks left");
		helper.succeed();
	}

	/**
	 * Iron in hand: 2 diamonds make nothing (ingots don't make another iron one); 3 make a diamond pickaxe. Stone in hand
	 * with 2 diamonds and 3 ingots: iron, the diamonds untouched; with 3 diamonds and no ingots: diamond straight away.
	 */
	//$ gametest_batch AREA '"qa1111c_diamond_bounds"'
	@GameTest(template = AREA, batch = "qa1111c_diamond_bounds")
	public void twoDiamondsAreNotEnoughThreeAre(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.STICK, 8), new ItemStack(Items.IRON_INGOT, 3));
		ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
		helper.assertTrue(upgrade(u, iron) == null, "iron in hand, 2 diamonds: planned " + upgrade(u, iron));
		helper.assertTrue(craft(u, iron).isEmpty(), "iron in hand, 2 diamonds: made one");
		helper.assertTrue(missing(u, iron) == null, "iron in hand should need nothing: " + missing(u, iron));
		helper.assertTrue(u.chest().countItem(Items.DIAMOND) == 2 && u.chest().countItem(Items.IRON_INGOT) == 3, "taken for nothing");
		ItemStack stoneMade = craft(u, new ItemStack(Items.STONE_PICKAXE));
		helper.assertTrue(stoneMade.is(Items.IRON_PICKAXE), "stone in hand, 2 diamonds + 3 ingots: made " + stoneMade);
		helper.assertTrue(u.chest().countItem(Items.DIAMOND) == 2 && u.chest().countItem(Items.IRON_INGOT) == 0, "diamonds used for an iron one");
		u.chest().setItem(0, new ItemStack(Items.DIAMOND, 3));
		PickaxeCrafting.Plan plan = upgrade(u, new ItemStack(Items.STONE_PICKAXE));
		helper.assertTrue(plan != null && plan.tier() == PickaxeCrafting.Tier.DIAMOND, "stone in hand, 3 diamonds, no ingots: " + plan);
		ItemStack made = craft(u, iron);
		helper.assertTrue(made.is(Items.DIAMOND_PICKAXE), "iron in hand, 3 diamonds: made " + made);
		helper.assertTrue(u.chest().countItem(Items.DIAMOND) == 0, "diamonds left: " + u.chest().countItem(Items.DIAMOND));
		helper.succeed();
	}

	/** A golden and a wooden pickaxe are below iron; a diamond or netherite one is never "upgraded"; nothing is taken then. */
	//$ gametest_batch AREA '"qa1111c_tiers"'
	@GameTest(template = AREA, batch = "qa1111c_tiers")
	public void goldenAndWoodenAreBelowIronDiamondIsNeverReplaced(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2), new ItemStack(Items.DIAMOND, 3));
		for (Item top : List.of(Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
			ItemStack hand = new ItemStack(top);
			helper.assertTrue(upgrade(u, hand) == null && craft(u, hand).isEmpty() && missing(u, hand) == null, "something over " + top);
		}
		helper.assertTrue(u.chest().countItem(Items.DIAMOND) == 3 && u.chest().countItem(Items.IRON_INGOT) == 3, "taken over diamond");
		u.chest().setItem(2, ItemStack.EMPTY); // no diamonds: iron is the best on offer
		for (Item low : List.of(Items.GOLDEN_PICKAXE, Items.WOODEN_PICKAXE)) {
			ItemStack hand = new ItemStack(low);
			PickaxeCrafting.Plan plan = upgrade(u, hand);
			helper.assertTrue(plan != null && plan.tier() == PickaxeCrafting.Tier.IRON, low + " in hand, ingots there: " + plan);
		}
		u.chest().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
		PickaxeCrafting.Upgrade need = missing(u, new ItemStack(Items.GOLDEN_PICKAXE));
		helper.assertTrue(need != null && need.item() == Items.IRON_INGOT && need.count() == 2, "golden in hand, 1 ingot: " + need);
		helper.succeed();
	}

	/** Lane b's surviving mutant (PickaxeCrafting ~111): two planks only in the miner's bag make the sticks. */
	//$ gametest_batch AREA '"qa1111c_bag_planks"'
	@GameTest(template = AREA, batch = "qa1111c_bag_planks")
	public void planksOnlyInTheBagMakeTheSticks(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.IRON_INGOT, 3));
		u.bag().add(new ItemStack(Items.OAK_PLANKS, 2));
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		helper.assertTrue(missing(u, stone) == null, "with 2 planks in the bag, says missing " + missing(u, stone));
		ItemStack made = craft(u, stone);
		helper.assertTrue(made.is(Items.IRON_PICKAXE), "2 planks in the bag + 3 ingots: made " + made);
		helper.assertTrue(u.bag().count(Items.OAK_PLANKS) == 0, "the planks weren't used: " + u.bag().count(Items.OAK_PLANKS));
		helper.assertTrue(u.bag().count(Items.STICK) + u.chest().countItem(Items.STICK) == 2, "the 2 spare sticks of 4 went missing: "
			+ u.bag().count(Items.STICK) + " in bag, " + u.chest().countItem(Items.STICK) + " in chest");
		helper.succeed();
	}

	/** Lane b's surviving mutant (PickaxeCrafting ~134): exactly 2 planks and no sticks are enough; 1 plank is not. */
	//$ gametest_batch AREA '"qa1111c_two_planks"'
	@GameTest(template = AREA, batch = "qa1111c_two_planks")
	public void exactlyTwoPlanksAndNoSticksAreEnough(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.SPRUCE_PLANKS, 1));
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		helper.assertTrue(upgrade(u, stone) == null && craft(u, stone).isEmpty(), "1 plank, no sticks: made a pickaxe");
		PickaxeCrafting.Upgrade need = missing(u, stone);
		helper.assertTrue(need != null && need.item() == Items.STICK && need.count() == 2, "1 plank: missing " + need);
		helper.assertTrue(u.chest().countItem(Items.SPRUCE_PLANKS) == 1 && u.chest().countItem(Items.IRON_INGOT) == 3, "taken while short");
		u.chest().setItem(1, new ItemStack(Items.SPRUCE_PLANKS, 2));
		ItemStack made = craft(u, stone);
		helper.assertTrue(made.is(Items.IRON_PICKAXE), "exactly 2 planks + 3 ingots: made " + made);
		helper.assertTrue(u.chest().countItem(Items.SPRUCE_PLANKS) == 0 && u.chest().countItem(Items.IRON_INGOT) == 0, "materials left: "
			+ u.chest().countItem(Items.SPRUCE_PLANKS) + " planks, " + u.chest().countItem(Items.IRON_INGOT) + " ingots");
		helper.succeed();
	}

	/**
	 * Ingots in the village store: his own come first and exactly 3 are taken in all (1 of his, 2 of the store's); the
	 * status line's count adds the store's ingots to his own.
	 */
	//$ gametest_batch AREA '"qa1111c_store"'
	@GameTest(template = AREA, batch = "qa1111c_store")
	public void storeIngotsGoDownByExactlyWhatIsUsed(GameTestHelper helper) {
		Unit u = unit(helper, new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.STICK, 2));
		BlockPos storeChest = new BlockPos(3, 2, 12);
		helper.setBlock(new BlockPos(2, 2, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		Container store = helper.getBlockEntity(storeChest);
		helper.assertTrue(!u.own().contains(helper.absolutePos(storeChest)), "setup: the store's chest is the miner's own");
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 1));
		ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
		PickaxeCrafting.Upgrade need = missing(u, stone);
		helper.assertTrue(need != null && need.item() == Items.IRON_INGOT && need.count() == 1, "1 own + 1 in the store: missing " + need);
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
		ItemStack made = craft(u, stone);
		helper.assertTrue(made.is(Items.IRON_PICKAXE), "1 own + 5 in the store: made " + made);
		helper.assertTrue(u.chest().countItem(Items.IRON_INGOT) == 0, "his own ingot wasn't used first: " + u.chest().countItem(Items.IRON_INGOT));
		helper.assertTrue(store.countItem(Items.IRON_INGOT) == 3, "the store should have 5 - 2 = 3 left, has " + store.countItem(Items.IRON_INGOT));
		// The sticks only in the village store: they count, and exactly 2 are taken from it.
		helper.assertTrue(u.chest().countItem(Items.STICK) == 0, "setup: sticks left in his own chest");
		store.setItem(1, new ItemStack(Items.STICK, 5));
		ItemStack again = craft(u, stone);
		helper.assertTrue(again.is(Items.IRON_PICKAXE), "store ingots + store sticks: made " + again);
		helper.assertTrue(store.countItem(Items.IRON_INGOT) == 0 && store.countItem(Items.STICK) == 3, "store left: "
			+ store.countItem(Items.IRON_INGOT) + " ingots, " + store.countItem(Items.STICK) + " sticks");
		helper.succeed();
	}

	// --- in the world ----------------------------------------------------------------------------

	/**
	 * A golden pickaxe, worn by 10, at a Novice: he makes an iron one; the golden one lands in his chest exactly once
	 * with its wear kept (not repaired, not dropped, not copied).
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa1111c_golden"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1111c_golden")
	public void aWornGoldenPickaxeGoesBackToTheChestAsItWas(GameTestHelper helper) {
		ItemStack golden = new ItemStack(Items.GOLDEN_PICKAXE);
		golden.setDamageValue(10);
		Setup s = setup(helper, new BlockPos(9, 3, 9), golden, new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(s.miner().getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe in hand: " + state(helper, s, s.miner()));
			Container chest = helper.getBlockEntity(CHEST);
			List<ItemStack> back = stacks(chest, Items.GOLDEN_PICKAXE);
			helper.assertTrue(back.size() == 1 && back.get(0).getCount() == 1, "golden pickaxes in the chest: " + back);
			helper.assertTrue(back.get(0).getDamageValue() >= 10, "the golden pickaxe came back repaired: damage " + back.get(0).getDamageValue());
			helper.assertTrue(dropped(helper, Items.GOLDEN_PICKAXE) == 0, "a golden pickaxe was dropped too");
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.STICK) == 0, "materials left");
		});
	}

	/** An iron pickaxe, worn by 100, and 3 diamonds: a diamond pickaxe; the iron one back in the chest worn; the ingots untouched. */
	//$ gametest_ticks_batch AREA '1200' '"qa1111c_iron_to_diamond"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1111c_iron_to_diamond")
	public void anIronPickaxeMinerMakesADiamondOneFromThreeDiamonds(GameTestHelper helper) {
		ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
		iron.setDamageValue(100);
		Setup s = setup(helper, new BlockPos(9, 3, 9), iron, new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 2),
			new ItemStack(Items.IRON_INGOT, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(s.miner().getMainHandItem().is(Items.DIAMOND_PICKAXE), "no diamond pickaxe in hand: " + state(helper, s, s.miner()));
			Container chest = helper.getBlockEntity(CHEST);
			List<ItemStack> back = stacks(chest, Items.IRON_PICKAXE);
			helper.assertTrue(back.size() == 1 && back.get(0).getDamageValue() >= 100, "iron pickaxes in the chest: " + back
				+ (back.isEmpty() ? "" : " damage " + back.get(0).getDamageValue()));
			helper.assertTrue(chest.countItem(Items.DIAMOND) == 0 && chest.countItem(Items.IRON_INGOT) == 3, "diamonds "
				+ chest.countItem(Items.DIAMOND) + ", ingots " + chest.countItem(Items.IRON_INGOT));
		});
	}

	/**
	 * An iron pickaxe and 3 ingots + 2 sticks (no diamonds): nothing to upgrade to. He digs the whole quarry with the same
	 * pickaxe, never stops to make one, never asks for anything, and the ingots and sticks stay.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qa1111c_iron_stays"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa1111c_iron_stays")
	public void anIronPickaxeMinerLeavesTheIngotsAlone(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(8, 2, 8), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_INGOT, 3),
			new ItemStack(Items.STICK, 2));
		boolean[] wrong = {false};
		String[] seen = {""};
		helper.onEachTick(() -> {
			QuarrySite site = live(s.level(), s.site());
			if (site != null && (site.status() == QuarrySite.Status.CRAFTING_PICKAXE || site.upgradeNeed() != null || line(s).contains("Pickaxe"))) {
				wrong[0] = true;
				seen[0] = state(helper, s, s.miner());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(live(s.level(), s.site()) == null, "still going: " + state(helper, s, s.miner()));
			helper.assertFalse(wrong[0], "made or asked for a pickaxe with an iron one in hand: " + seen[0]);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 3 && chest.countItem(Items.STICK) == 2, "materials taken: "
				+ chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.STICK) + " sticks");
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) + (s.miner().getMainHandItem().is(Items.IRON_PICKAXE) ? 1 : 0) == 1,
				"not exactly one iron pickaxe: " + chest.countItem(Items.IRON_PICKAXE) + " in the chest, holding " + s.miner().getMainHandItem());
		});
	}

	/**
	 * The status line, word for word, as the materials turn up one by one (stone pickaxe, no planks): "digging · needs 1
	 * Iron Ingot for an Iron Pickaxe" with 2 ingots, "… needs 2 Sticks …" with 3 and none, "… needs 1 Stick …" with one,
	 * then the iron pickaxe and the need gone from the line.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa1111c_status"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa1111c_status")
	public void theStatusLineCountsDownTheMaterials(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(11, 4, 11), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_INGOT, 2));
		List<String> expected = List.of("digging · needs 1 Iron Ingot for an Iron Pickaxe", "digging · needs 2 Sticks for an Iron Pickaxe",
			"digging · needs 1 Stick for an Iron Pickaxe");
		int[] stage = {0};
		List<String> lines = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			String now = line(s);
			if (lines.isEmpty() || !lines.get(lines.size() - 1).equals(now)) {
				lines.add(now);
			}
			if (stage[0] >= expected.size() || !now.equals(expected.get(stage[0]))) {
				return;
			}
			Container chest = helper.getBlockEntity(CHEST);
			if (stage[0] == 0) {
				chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
			} else if (stage[0] == 1) {
				chest.setItem(1, new ItemStack(Items.STICK, 1));
			} else {
				chest.setItem(1, new ItemStack(Items.STICK, 2));
			}
			stage[0]++;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage[0] == expected.size(), "the line never read '" + (stage[0] < expected.size() ? expected.get(stage[0]) : "")
				+ "'; lines seen: " + lines);
			helper.assertTrue(s.miner().getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe: " + state(helper, s, s.miner()));
			helper.assertFalse(line(s).contains("Pickaxe"), "still asks after the upgrade: " + line(s));
		});
	}

	/**
	 * Raw iron and sticks in his chest but no furnace anywhere near: nothing can make the ingots, so he digs on (no
	 * pointless trips to the chest), the line says "digging · needs 3 Iron Ingots for an Iron Pickaxe", the quarry
	 * finishes and the raw iron is still there.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qa1111c_no_furnace"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa1111c_no_furnace")
	public void withNoFurnaceHeDigsOnAndSaysWhatHeNeeds(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(8, 3, 8), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.RAW_IRON, 3),
			new ItemStack(Items.STICK, 2));
		boolean[] said = {false};
		String[] trip = {null};
		helper.onEachTick(() -> {
			QuarrySite site = live(s.level(), s.site());
			if (site == null) {
				return;
			}
			if (line(s).equals("digging · needs 3 Iron Ingots for an Iron Pickaxe")) {
				said[0] = true;
			}
			if (trip[0] == null && site.status() == QuarrySite.Status.DEPOSITING
				&& ModAttachments.BUILDER_BAG.getOrCreate(s.miner()).freeSlots() >= 2) {
				trip[0] = state(helper, s, s.miner());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(live(s.level(), s.site()) == null, "still going: " + state(helper, s, s.miner()));
			helper.assertTrue(said[0], "the line never said he needs 3 Iron Ingots");
			helper.assertTrue(trip[0] == null, "went to the chest with room in his bag and no furnace to feed: " + trip[0]);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 3 && chest.countItem(Items.STICK) == 2, "raw iron "
				+ chest.countItem(Items.RAW_IRON) + ", sticks " + chest.countItem(Items.STICK));
		});
	}

	/**
	 * Two diamond ores passed with a stone pickaxe; before the upgrade the player mines one (air) and swaps the other
	 * for stone. After the upgrade the miner isn't stuck on them: the quarry finishes and nothing is waiting.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1111c_left_gone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1111c_left_gone")
	public void leftOresThePlayerTookDontStallHim(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(9, 3, 9), new ItemStack(Items.STONE_PICKAXE));
		BlockPos a = new BlockPos(7, 3, 7);
		BlockPos b = new BlockPos(8, 3, 8);
		helper.setBlock(a, Blocks.DIAMOND_ORE);
		helper.setBlock(b, Blocks.DEEPSLATE_DIAMOND_ORE);
		boolean[] taken = {false};
		boolean[] upgraded = {false};
		helper.onEachTick(() -> {
			if (s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				upgraded[0] = true;
			}
			QuarrySite site = live(s.level(), s.site());
			if (taken[0] || site == null || site.leftOres().size() < 2) {
				return;
			}
			taken[0] = true;
			helper.setBlock(a, Blocks.AIR);
			helper.setBlock(b, Blocks.STONE);
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
			chest.setItem(1, new ItemStack(Items.STICK, 2));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(taken[0], "the two diamond ores were never both remembered: " + state(helper, s, s.miner())
				+ (live(s.level(), s.site()) == null ? "" : " left " + live(s.level(), s.site()).leftOres()));
			helper.assertTrue(upgraded[0], "never upgraded: " + state(helper, s, s.miner()));
			helper.assertTrue(live(s.level(), s.site()) == null, "stuck: " + state(helper, s, s.miner())
				+ (live(s.level(), s.site()) == null ? "" : " left " + live(s.level(), s.site()).leftOres()));
		});
	}

	/**
	 * The owner's whole story with nobody's help: a Novice with a stone pickaxe digs iron ore, the raw iron goes to the
	 * plain furnace by his workstation (coal in his chest), the ingots make an iron pickaxe, and the diamond ore he left
	 * before is dug with it.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1111c_whole_story"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1111c_whole_story")
	public void theIronHeDugBecomesThePickaxeForTheDiamondHeLeft(GameTestHelper helper) {
		helper.setBlock(FURNACE, Blocks.FURNACE);
		Setup s = setup(helper, new BlockPos(11, 4, 11), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COAL, 8),
			new ItemStack(Items.STICK, 2));
		BlockPos diamond = new BlockPos(6, 4, 6);
		helper.setBlock(diamond, Blocks.DIAMOND_ORE);
		for (BlockPos ore : List.of(new BlockPos(7, 4, 7), new BlockPos(9, 4, 8), new BlockPos(10, 4, 10))) {
			helper.setBlock(ore, Blocks.IRON_ORE);
		}
		boolean[] left = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(s.level(), s.site());
			if (site != null && site.leftOres().contains(helper.absolutePos(diamond))) {
				left[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			boolean iron = s.miner().getMainHandItem().is(Items.IRON_PICKAXE) || chest.countItem(Items.IRON_PICKAXE) == 1;
			helper.assertTrue(left[0], "the diamond ore was never left for a better pickaxe: " + state(helper, s, s.miner()));
			helper.assertTrue(iron, "no iron pickaxe from the iron he dug: " + state(helper, s, s.miner()) + ", chest raw "
				+ chest.countItem(Items.RAW_IRON) + ", ingots " + chest.countItem(Items.IRON_INGOT));
			helper.assertBlockPresent(Blocks.AIR, diamond);
			helper.assertTrue(chest.countItem(Items.DIAMOND) == 1 || s.miner().getMainHandItem().is(Items.IRON_PICKAXE)
				&& ModAttachments.BUILDER_BAG.getOrCreate(s.miner()).count(Items.DIAMOND) == 1, "the diamond isn't in his chest or bag");
		});
	}

	/**
	 * Restart while he's making the iron pickaxe (the villager saved and loaded again): the new one finishes it, and
	 * exactly 3 ingots and 2 sticks are used, one iron pickaxe made and one stone pickaxe put back.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa1111c_restart"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1111c_restart")
	public void aRestartWhileMakingItNeitherLosesNorDoubles(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(9, 3, 9), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.IRON_INGOT, 6),
			new ItemStack(Items.STICK, 4));
		Villager[] miner = {s.miner()};
		helper.onEachTick(() -> {
			QuarrySite site = live(s.level(), s.site());
			if (miner[0] != s.miner() || site == null || site.status() != QuarrySite.Status.CRAFTING_PICKAXE) {
				return;
			}
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(s.miner().save(tag), "the miner wasn't saved");
			s.miner().discard();
			Entity back = EntityType.loadEntityRecursive(tag, s.level(), e -> e);
			helper.assertTrue(back instanceof Villager, "the miner didn't load");
			s.level().addFreshEntity(back);
			miner[0] = (Villager) back;
			Leftovers.after(helper, back::discard);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(miner[0] != s.miner(), "never seen making the pickaxe: " + state(helper, s, miner[0]));
			helper.assertTrue(miner[0].getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe after the restart: " + state(helper, s, miner[0]));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 3 && chest.countItem(Items.STICK) == 2, "not exactly one pickaxe's worth used: "
				+ chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.STICK) + " sticks left");
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) + dropped(helper, Items.STONE_PICKAXE) == 1, "stone pickaxes: "
				+ chest.countItem(Items.STONE_PICKAXE) + " in the chest, " + dropped(helper, Items.STONE_PICKAXE) + " dropped");
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 0, "a second iron pickaxe in the chest");
		});
	}

	/**
	 * The switch off: a remembered ore (outside the pit, so the normal pass never reaches it) is not gone back for, even
	 * with an iron pickaxe in hand; the quarry finishes and the ore stands.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa1111c_off_memory"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa1111c_off_memory")
	public void switchedOffRememberedOresAreNotGoneBackFor(GameTestHelper helper) {
		PickaxeCrafting.ENABLED = false;
		Leftovers.after(helper, () -> PickaxeCrafting.ENABLED = true);
		Setup s = setup(helper, new BlockPos(7, 2, 7), new ItemStack(Items.IRON_PICKAXE));
		BlockPos ore = new BlockPos(9, 2, 9);
		helper.setBlock(ore, Blocks.DIAMOND_ORE);
		s.site().leaveOre(helper.absolutePos(ore));
		helper.succeedWhen(() -> {
			helper.assertTrue(live(s.level(), s.site()) == null, "still going: " + state(helper, s, s.miner()));
			helper.assertBlockPresent(Blocks.DIAMOND_ORE, ore);
		});
	}

	// --- saved data ------------------------------------------------------------------------------

	/**
	 * The cap of 256 ores left: a repeat counts once, room comes back when one is forgotten, a full list survives a save,
	 * and a save with left_ores of the wrong type loads with none and no crash.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leftOresStayWithinTheCap(GameTestHelper helper) {
		QuarrySite site = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "",
			net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"), new BoundingBox(0, 0, 0, 3, 3, 3), 4);
		site.leaveOre(new BlockPos(1, 1, 1));
		site.leaveOre(new BlockPos(1, 1, 1));
		helper.assertTrue(site.leftOres().size() == 1, "a repeat counted twice: " + site.leftOres());
		for (int i = 0; i < 300; i++) {
			site.leaveOre(new BlockPos(i, 2, 0));
		}
		helper.assertTrue(site.leftOres().size() == 256, "cap: " + site.leftOres().size());
		site.forgetOre(new BlockPos(1, 1, 1));
		site.leaveOre(new BlockPos(0, 5, 0));
		helper.assertTrue(site.leftOres().size() == 256 && site.leftOres().contains(new BlockPos(0, 5, 0)), "no room after one was forgotten");
		CompoundTag tag = site.save();
		QuarrySite back = QuarrySite.load(tag);
		helper.assertTrue(back != null && back.leftOres().equals(site.leftOres()), "a full list changed in a save");
		tag.putInt("left_ores", 5);
		QuarrySite odd = QuarrySite.load(tag);
		helper.assertTrue(odd != null && odd.leftOres().isEmpty(), "left_ores of the wrong type: " + (odd == null ? "null" : odd.leftOres()));
		helper.succeed();
	}
}
