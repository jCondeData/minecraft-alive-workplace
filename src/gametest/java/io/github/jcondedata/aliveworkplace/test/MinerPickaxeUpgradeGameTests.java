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
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B1202 and ROADMAP 36.11a (owner, 2026-11-11): the miner gets an iron pickaxe as soon as he has the iron for one, at
 * any level and while he still has a pickaxe; raw iron he mined is smelted into the ingots by the furnace at his
 * workstation; the ores he passed because his pickaxe was too weak are remembered in the quarry (saved) and dug after the
 * upgrade; the status line says what he lacks for it; and with {@code minerCraftsPickaxe} off nothing of it happens.
 */
public class MinerPickaxeUpgradeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos FURNACE = new BlockPos(4, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	/** A Novice miner holding {@code hand} at his bench, a stone quarry from {@link #MIN} to {@code max}, {@code chest} in his chest. */
	private static Setup setup(GameTestHelper helper, BlockPos max, ItemStack hand, ItemStack... chest) {
		Leftovers.clear(helper);
		// B1207: a Storehouse or hall an earlier batch's test left nearby makes its chests the miner's village store, and
		// he takes the sticks left in them: the worn-out test's miner used anUpgradeIsOnlyEverABetterPickaxe's 2 leftover
		// sticks (39 blocks off) and never cut his own planks
		Leftovers.storehouses(helper);
		Leftovers.halls(helper);
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

	private static QuarrySite live(Setup s) {
		return QuarrySiteManager.get(s.level()).get(s.site().id());
	}

	private static String status(Setup s) {
		QuarrySite site = live(s);
		return site == null ? "finished" : Miners.statusText(s.level(), site, s.miner()).getString().replace('\n', '|');
	}

	private static String state(GameTestHelper helper, Setup s) {
		QuarrySite site = live(s);
		return site == null ? "finished" : site.status() + ", " + site.mined() + " dug, holding " + s.miner().getMainHandItem()
			+ ", miner at " + helper.relativePos(s.miner().blockPosition());
	}

	/**
	 * The owner's case: a Novice digging with a stone pickaxe, 3 iron ingots and 2 sticks in his chest. He makes an iron
	 * pickaxe at his workstation (the status says he's making one), the stone one goes back in his chest, and he digs on.
	 */
	//$ gametest_ticks_batch AREA '1200' '"miner_upgrade_iron"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "miner_upgrade_iron")
	public void aStonePickaxeNoviceMakesAnIronPickaxe(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(9, 3, 9), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2));
		boolean[] making = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(s);
			if (site != null && site.status() == QuarrySite.Status.CRAFTING_PICKAXE) {
				making[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(making[0], "never showed making a pickaxe: " + state(helper, s));
			helper.assertTrue(s.miner().getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe in hand: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "the old stone pickaxe isn't back in the chest");
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.STICK) == 0,
				"materials left: " + chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.STICK) + " sticks");
			helper.assertTrue(live(s) != null && live(s).status() == QuarrySite.Status.WORKING && s.miner().getVillagerData().getLevel() == 1,
				"should be digging on as a Novice: " + state(helper, s));
		});
	}

	/**
	 * Raw iron he mined, coal and sticks in his chest and a blast furnace by his workstation: he takes the raw iron to the
	 * furnace, the ingots come out into his chest, and he makes the iron pickaxe from them, with nobody handing him
	 * anything. Meanwhile the status line says he needs the ingots for it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"miner_upgrade_smelt"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "miner_upgrade_smelt")
	public void rawIronIsSmeltedIntoTheIngotsForAnIronPickaxe(GameTestHelper helper) {
		helper.setBlock(FURNACE, Blocks.BLAST_FURNACE);
		Setup s = setup(helper, new BlockPos(11, 4, 11), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.RAW_IRON, 3), new ItemStack(Items.COAL, 4), new ItemStack(Items.STICK, 2));
		boolean[] said = {false};
		helper.onEachTick(() -> {
			if (status(s).contains("needs 3 Iron Ingots for an Iron Pickaxe") || status(s).contains("needs 2 Iron Ingots for an Iron Pickaxe")
				|| status(s).contains("needs 1 Iron Ingot for an Iron Pickaxe")) {
				said[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(s.miner().getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe from the raw iron: " + state(helper, s)
				+ " | " + status(s));
			helper.assertTrue(said[0], "the status line never said he needs the ingots");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 0 && chest.countItem(Items.IRON_INGOT) == 0, "raw iron or ingots left: "
				+ chest.countItem(Items.RAW_IRON) + " raw, " + chest.countItem(Items.IRON_INGOT) + " ingots");
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "the old pickaxe isn't back in the chest");
			helper.assertFalse(status(s).contains("Iron Pickaxe"), "still says he needs something for it: " + status(s));
		});
	}

	/**
	 * Gold, diamond and redstone ore in the quarry's top layer, dug with a stone pickaxe: he passes them, and they are
	 * remembered in the quarry. The quarry is saved and loaded again; then the iron for a pickaxe turns up and, once he
	 * has made it, he goes back and digs all three: the finds are in his chest and none is counted as left standing.
	 */
	//$ gametest_ticks_batch AREA '3000' '"miner_upgrade_left_ores"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "miner_upgrade_left_ores")
	public void oresPassedWithAStonePickaxeAreDugAfterTheUpgrade(GameTestHelper helper) {
		BlockPos max = new BlockPos(9, 4, 9);
		Setup s = setup(helper, max, new ItemStack(Items.STONE_PICKAXE));
		List<BlockPos> ores = List.of(new BlockPos(6, 4, 7), new BlockPos(8, 4, 6), new BlockPos(9, 4, 9));
		List<Block> kinds = List.of(Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.REDSTONE_ORE);
		for (int i = 0; i < ores.size(); i++) {
			helper.setBlock(ores.get(i), kinds.get(i));
		}
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(s);
			if (reloaded[0] || site == null || site.leftOres().size() < 3) {
				return;
			}
			reloaded[0] = true;
			for (BlockPos ore : ores) {
				helper.assertTrue(site.leftOres().contains(helper.absolutePos(ore)), "not remembered: " + ore + " in " + site.leftOres());
			}
			helper.assertTrue(s.miner().getMainHandItem().is(Items.STONE_PICKAXE), "the stone pickaxe changed early: " + s.miner().getMainHandItem());
			// Save and load the quarry again (a restart).
			CompoundTag saved = site.save();
			helper.assertTrue(saved.contains("left_ores"), "the ores left aren't saved");
			QuarrySite loaded = QuarrySite.load(saved);
			QuarrySiteManager.get(s.level()).remove(site.id());
			QuarrySiteManager.get(s.level()).restore(loaded);
			helper.assertTrue(live(s).leftOres().size() == 3, "the ores left didn't come back after loading: " + live(s).leftOres());
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
			chest.setItem(1, new ItemStack(Items.STICK, 2));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the three ores were never all left for a better pickaxe: " + state(helper, s));
			helper.assertTrue(live(s) == null, "still going: " + state(helper, s) + ", left " + (live(s) == null ? "-" : live(s).leftOres()));
			for (BlockPos ore : ores) {
				helper.assertBlockPresent(Blocks.AIR, ore);
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RAW_GOLD) == 1 && chest.countItem(Items.DIAMOND) == 1 && chest.countItem(Items.REDSTONE) >= 4,
				"finds: " + chest.countItem(Items.RAW_GOLD) + " raw gold, " + chest.countItem(Items.DIAMOND) + " diamonds, " + chest.countItem(Items.REDSTONE) + " redstone");
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 1 && chest.countItem(Items.STONE_PICKAXE) == 1,
				"pickaxes back: " + chest.countItem(Items.IRON_PICKAXE) + " iron, " + chest.countItem(Items.STONE_PICKAXE) + " stone");
		});
	}

	/**
	 * {@code minerCraftsPickaxe} off: the old behavior. The ingots and sticks stay in the chest, he digs on with the stone
	 * pickaxe, the diamond ore he can't mine stays standing, and the status line asks for nothing.
	 */
	//$ gametest_ticks_batch AREA '2400' '"miner_upgrade_off"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "miner_upgrade_off")
	public void switchedOffTheMinerKeepsHisPickaxe(GameTestHelper helper) {
		PickaxeCrafting.ENABLED = false;
		Leftovers.after(helper, () -> PickaxeCrafting.ENABLED = true);
		BlockPos diamond = new BlockPos(7, 2, 7);
		Setup s = setup(helper, new BlockPos(8, 2, 8), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.STICK, 2));
		helper.setBlock(diamond, Blocks.DIAMOND_ORE);
		boolean[] asked = {false};
		helper.onEachTick(() -> {
			if (status(s).contains("Iron Pickaxe") || s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				asked[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(live(s) == null, "still going: " + state(helper, s));
			helper.assertFalse(asked[0], "made or asked for an iron pickaxe with the switch off");
			helper.assertBlockPresent(Blocks.DIAMOND_ORE, diamond);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 3 && chest.countItem(Items.STICK) == 2, "materials were taken with the switch off");
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1 && chest.countItem(Items.IRON_PICKAXE) == 0, "pickaxes: "
				+ chest.countItem(Items.STONE_PICKAXE) + " stone, " + chest.countItem(Items.IRON_PICKAXE) + " iron");
		});
	}

	/**
	 * What he makes, without walking: better than the pickaxe in hand only, at any level; the ingots may come from the
	 * village store; diamond over iron; nothing better than diamond; nothing taken when nothing is better.
	 */
	//$ gametest_batch AREA '"miner_upgrade_rules"'
	@GameTest(template = AREA, batch = "miner_upgrade_rules")
	public void anUpgradeIsOnlyEverABetterPickaxe(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(7, 2, 7), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.COBBLESTONE, 6),
			new ItemStack(Items.STICK, 8));
		ServerLevel level = s.level();
		Villager v = s.miner();
		BlockPos bench = helper.absolutePos(BENCH);
		List<BlockPos> own = SupplyContainers.find(level, bench, s.site().box());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(v);
		helper.assertTrue(PickaxeCrafting.rank(new ItemStack(Items.WOODEN_PICKAXE)) == 0 && PickaxeCrafting.rank(new ItemStack(Items.STONE_PICKAXE)) == 1
			&& PickaxeCrafting.rank(new ItemStack(Items.IRON_PICKAXE)) == 2 && PickaxeCrafting.rank(new ItemStack(Items.DIAMOND_PICKAXE)) == 3
			&& PickaxeCrafting.rank(new ItemStack(Items.NETHERITE_PICKAXE)) == 3 && PickaxeCrafting.rank(ItemStack.EMPTY) == -1
			&& PickaxeCrafting.rank(new ItemStack(Items.IRON_SHOVEL)) == -1, "pickaxe ranks");
		// Stone in hand, only cobblestone: nothing better to make, nothing taken.
		helper.assertTrue(PickaxeCrafting.upgrade(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE)) == null, "stone over stone");
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE)).isEmpty(), "made stone over stone");
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 6 && chest.countItem(Items.STICK) == 8, "taken for nothing");
		// A wooden pickaxe: stone is better.
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag, new ItemStack(Items.WOODEN_PICKAXE)).is(Items.STONE_PICKAXE), "no stone over wood");
		// The ingots in the village store (a Storehouse's chest): an iron pickaxe from them, at Novice.
		BlockPos storehouse = new BlockPos(2, 2, 12);
		BlockPos storeChest = new BlockPos(3, 2, 12);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		Container store = helper.getBlockEntity(storeChest);
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		helper.assertTrue(!own.contains(helper.absolutePos(storeChest)), "setup: the store's chest is the miner's own");
		PickaxeCrafting.Plan plan = PickaxeCrafting.upgrade(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE));
		helper.assertTrue(plan != null && plan.tier() == PickaxeCrafting.Tier.IRON, "no iron pickaxe from the store's ingots: " + plan);
		helper.assertTrue(PickaxeCrafting.upgradeMissing(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE)) == null, "says something is missing");
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE)).is(Items.IRON_PICKAXE), "no iron pickaxe made");
		helper.assertTrue(store.countItem(Items.IRON_INGOT) == 0, "the store's ingots weren't used: " + store.countItem(Items.IRON_INGOT));
		// Diamonds and ingots both there: diamond; with iron in hand, ingots alone make nothing.
		chest.setItem(5, new ItemStack(Items.DIAMOND, 3));
		chest.setItem(6, new ItemStack(Items.IRON_INGOT, 3));
		helper.assertTrue(PickaxeCrafting.upgrade(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE)).tier() == PickaxeCrafting.Tier.DIAMOND, "not diamond first");
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag, new ItemStack(Items.IRON_PICKAXE)).is(Items.DIAMOND_PICKAXE), "no diamond over iron");
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag, new ItemStack(Items.IRON_PICKAXE)).isEmpty(), "iron over iron");
		helper.assertTrue(PickaxeCrafting.upgrade(level, v, bench, own, bag, new ItemStack(Items.NETHERITE_PICKAXE)) == null, "something over netherite");
		helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 3, "ingots taken for nothing");
		// What's missing for an iron one: the ingots, then the sticks; nothing with iron in hand.
		chest.setItem(6, new ItemStack(Items.IRON_INGOT, 1));
		PickaxeCrafting.Upgrade need = PickaxeCrafting.upgradeMissing(level, v, bench, own, bag, new ItemStack(Items.STONE_PICKAXE));
		helper.assertTrue(need != null && need.item() == Items.IRON_INGOT && need.count() == 2, "missing: " + need);
		helper.assertTrue(PickaxeCrafting.upgradeMissing(level, v, bench, own, bag, new ItemStack(Items.IRON_PICKAXE)) == null, "missing with iron in hand");
		// the Storehouse would stay a village store for later batches' workers nearby (B1207)
		helper.setBlock(storehouse, Blocks.AIR);
		helper.succeed();
	}

	/** A quarry saved before 36.11a has no ores left for a better pickaxe: it loads with none, and saves none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldQuarriesLoadWithNoOresLeft(GameTestHelper helper) {
		QuarrySite site = new QuarrySite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"),
			new BoundingBox(0, 0, 0, 3, 3, 3), 4);
		CompoundTag old = site.save();
		helper.assertFalse(old.contains("left_ores"), "an empty list is saved");
		QuarrySite loaded = QuarrySite.load(old);
		helper.assertTrue(loaded != null && loaded.leftOres().isEmpty(), "an old quarry loaded with ores left");
		for (int i = 0; i < QuarrySite.MAX_LEFT_ORES + 5; i++) {
			site.leaveOre(new BlockPos(i, 0, 0));
		}
		helper.assertTrue(site.leftOres().size() == QuarrySite.MAX_LEFT_ORES, "no cap: " + site.leftOres().size());
		QuarrySite back = QuarrySite.load(site.save());
		helper.assertTrue(back.leftOres().equals(site.leftOres()), "the ores left changed in a save");
		helper.succeed();
	}

	/** Every new sentence a player reads: the status line's need, singular and plural, and the setting's description. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void upgradeWordsAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String two = Component.translatable("message.aliveworkplace.quarry.upgrade_needs", 2, Words.itemName(Items.IRON_INGOT, 2)).getString();
		helper.assertTrue(two.equals("needs 2 Iron Ingots for an Iron Pickaxe"), "plural: " + two);
		String one = Component.translatable("message.aliveworkplace.quarry.upgrade_needs", 1, Words.itemName(Items.IRON_INGOT, 1)).getString();
		helper.assertTrue(one.equals("needs 1 Iron Ingot for an Iron Pickaxe"), "singular: " + one);
		String sticks = Component.translatable("message.aliveworkplace.quarry.upgrade_needs", 2, Words.itemName(Items.STICK, 2)).getString();
		helper.assertTrue(sticks.equals("needs 2 Sticks for an Iron Pickaxe"), "sticks: " + sticks);
		String tip = lang.getOrDefault("aliveworkplace.config.minerCraftsPickaxe.tooltip");
		helper.assertTrue(!tip.contains("Journeyman") && tip.contains("at any level") && tip.contains("3 Iron Ingots") && tip.contains("Off:"), "the setting: " + tip);
		helper.succeed();
	}
}
