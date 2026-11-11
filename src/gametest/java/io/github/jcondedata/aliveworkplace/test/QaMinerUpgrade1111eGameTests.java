package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.PickaxeCrafting;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
 * QA for B1202 / ROADMAP 36.11a (lane-e-1111-1912), written from the spec: the ores left for a better pickaxe through a
 * whole quarry-file save and reload (before and after the upgrade, order and count kept, an emptied list writes nothing),
 * the 256 cap at its edge, an ore a player broke meanwhile not waited on, the diamond tier in the work loop (the old
 * pickaxe kept once, never lost or duplicated; diamonds beat iron, the ingots stay), the ingots from the village store,
 * and the status line from both sides (shown while he lacks the ingots or sticks, gone once he has them, never with an
 * iron pickaxe in hand, never while {@code minerCraftsPickaxe} is off and back when it's on again).
 */
public class QaMinerUpgrade1111eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	/** A Novice miner holding {@code hand} at his bench, a stone quarry from {@link #MIN} to {@code max}, {@code chest} in his chest. */
	private static Setup setup(GameTestHelper helper, BlockPos max, ItemStack hand, ItemStack... chest) {
		Leftovers.clear(helper);
		Leftovers.storehouses(helper); // B1207: a Storehouse or hall left nearby would be his village store
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
		return site == null ? "finished" : site.status() + ", " + site.mined() + " dug, left " + site.leftOres() + ", holding "
			+ s.miner().getMainHandItem() + ", miner at " + helper.relativePos(s.miner().blockPosition());
	}

	/** The quarry file's own loader (private, as the game calls it on a world load). */
	private static QuarrySiteManager loadFile(CompoundTag tag, HolderLookup.Provider registries) {
		try {
			Method load = QuarrySiteManager.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			load.setAccessible(true);
			return (QuarrySiteManager) load.invoke(null, tag, registries);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't load the quarry file: " + e);
		}
	}

	/**
	 * A whole restart of the quarry file: every quarry written as the game writes {@code aliveworkplace_quarries}, read
	 * back by its loader, and the live quarry swapped for the one read back. Returns the one read back.
	 */
	private static QuarrySite restart(GameTestHelper helper, Setup s) {
		ServerLevel level = s.level();
		CompoundTag file = QuarrySiteManager.get(level).save(new CompoundTag(), level.registryAccess());
		QuarrySite loaded = loadFile(file, level.registryAccess()).get(s.site().id());
		helper.assertTrue(loaded != null, "the quarry isn't in the saved file");
		QuarrySiteManager.get(level).remove(s.site().id());
		QuarrySiteManager.get(level).restore(loaded);
		return loaded;
	}

	/**
	 * Gold, diamond and redstone ore in the top layer, dug with a stone pickaxe: all three left. The whole quarry file is
	 * saved and loaded (a restart): the three come back in the same order. Then the ingots turn up; the moment he holds
	 * the iron pickaxe, before he has gone back for them, the file is saved and loaded again: still the same three. He
	 * digs them all, each counted as dug, and once none is left the quarry writes no {@code left_ores} at all.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa_upgrade_1111e_reload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa_upgrade_1111e_reload")
	public void leftOresSurviveTwoWholeRestartsAroundTheUpgrade(GameTestHelper helper) {
		BlockPos max = new BlockPos(9, 4, 9);
		Setup s = setup(helper, max, new ItemStack(Items.STONE_PICKAXE));
		List<BlockPos> ores = List.of(new BlockPos(9, 4, 8), new BlockPos(6, 4, 6), new BlockPos(7, 4, 9));
		List<Block> kinds = List.of(Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.REDSTONE_ORE);
		for (int i = 0; i < ores.size(); i++) {
			helper.setBlock(ores.get(i), kinds.get(i));
		}
		int[] phase = {0};
		List<BlockPos> order = new ArrayList<>();
		int[] dugBefore = {0};
		boolean[] emptyChecked = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(s);
			if (site == null) {
				return;
			}
			if (phase[0] == 0 && site.leftOres().size() >= 3) {
				phase[0] = 1;
				order.addAll(site.leftOres());
				helper.assertTrue(order.size() == 3, "more than the three ores left: " + order);
				QuarrySite back = restart(helper, s);
				helper.assertTrue(new ArrayList<>(back.leftOres()).equals(order), "the ores left came back as " + back.leftOres() + ", not " + order);
				// The quarry read back is the live one and still reports its changes to the file.
				QuarrySiteManager.get(s.level()).setDirty(false);
				back.leaveOre(order.get(0));
				helper.assertFalse(QuarrySiteManager.get(s.level()).isDirty(), "leaving an ore already left changed the file");
				Container chest = helper.getBlockEntity(CHEST);
				chest.setItem(20, new ItemStack(Items.IRON_INGOT, 3));
				chest.setItem(21, new ItemStack(Items.STICK, 2));
			} else if (phase[0] == 1 && s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				phase[0] = 2;
				helper.assertTrue(site.leftOres().size() == 3, "an ore was dug before the iron pickaxe was in hand: " + site.leftOres());
				dugBefore[0] = site.mined();
				QuarrySite back = restart(helper, s);
				helper.assertTrue(new ArrayList<>(back.leftOres()).equals(order), "after the upgrade the ores left came back as " + back.leftOres());
			} else if (phase[0] == 2 && site.leftOres().isEmpty() && !emptyChecked[0]) {
				emptyChecked[0] = true;
				helper.assertTrue(site.mined() >= dugBefore[0] + 3, "the three ores weren't counted as dug: " + dugBefore[0] + " then " + site.mined());
				CompoundTag saved = site.save();
				helper.assertFalse(saved.contains("left_ores"), "an emptied list is still written: " + saved.get("left_ores"));
				QuarrySite back = QuarrySite.load(saved);
				helper.assertTrue(back != null && back.leftOres().isEmpty(), "an emptied list loads with ores");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "never got to the upgrade (phase " + phase[0] + "): " + state(helper, s));
			helper.assertTrue(emptyChecked[0], "the ores left never all got dug: " + state(helper, s));
			helper.assertTrue(live(s) == null, "still going: " + state(helper, s));
			for (BlockPos ore : ores) {
				helper.assertBlockPresent(Blocks.AIR, ore);
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.RAW_GOLD) == 1 && chest.countItem(Items.DIAMOND) == 1 && chest.countItem(Items.REDSTONE) >= 4,
				"finds: " + chest.countItem(Items.RAW_GOLD) + " raw gold, " + chest.countItem(Items.DIAMOND) + " diamonds, " + chest.countItem(Items.REDSTONE) + " redstone");
		});
	}

	/**
	 * The cap at its edge: 255 left, the 256th is kept (and changes the file), the 257th is not (and changes nothing); an
	 * ore already left doesn't move or change anything; one dug after all makes room again, counted as dug. A full list
	 * goes through the quarry file in its order, and the quarry read back still reports its changes to its file.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qa_upgrade_1111e_cap"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qa_upgrade_1111e_cap")
	public void theLeftOreCapIsExactlyTwoHundredFiftySix(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		QuarrySiteManager file = loadFile(new CompoundTag(), registries); // a quarry file of its own, not the level's
		QuarrySite site = file.create(UUID.randomUUID(), "", ResourceLocation.withDefaultNamespace("overworld"), new BoundingBox(0, -60, 0, 300, 0, 3), 61);
		helper.assertTrue(QuarrySite.MAX_LEFT_ORES == 256, "the cap moved: " + QuarrySite.MAX_LEFT_ORES);
		for (int i = 0; i < 255; i++) {
			site.leaveOre(new BlockPos(i, -10, 1));
		}
		helper.assertTrue(site.leftOres().size() == 255, "255 left: " + site.leftOres().size());
		file.setDirty(false);
		BlockPos last = new BlockPos(255, -10, 1);
		site.leaveOre(last);
		helper.assertTrue(site.leftOres().size() == 256 && site.leftOres().contains(last), "the 256th wasn't kept: " + site.leftOres().size());
		helper.assertTrue(file.isDirty(), "the 256th didn't change the file");
		file.setDirty(false);
		BlockPos over = new BlockPos(256, -10, 1);
		site.leaveOre(over);
		helper.assertTrue(site.leftOres().size() == 256 && !site.leftOres().contains(over), "the 257th was kept: " + site.leftOres().size());
		helper.assertFalse(file.isDirty(), "the 257th changed the file");
		List<BlockPos> before = new ArrayList<>(site.leftOres());
		site.leaveOre(new BlockPos(0, -10, 1));
		site.forgetOre(new BlockPos(999, -10, 1));
		helper.assertTrue(new ArrayList<>(site.leftOres()).equals(before), "the order changed");
		helper.assertFalse(file.isDirty(), "leaving an ore twice, or forgetting one never left, changed the file");
		// The full list through the file, in order.
		QuarrySite back = loadFile(file.save(new CompoundTag(), registries), registries).get(site.id());
		helper.assertTrue(back != null && new ArrayList<>(back.leftOres()).equals(before), "the 256 changed in the file: "
			+ (back == null ? "gone" : back.leftOres().size() + " back"));
		// One dug after all: room for the next, counted as dug.
		int mined = site.mined();
		site.dugLeftOre(new BlockPos(17, -10, 1));
		helper.assertTrue(site.mined() == mined + 1 && site.leftOres().size() == 255, "dug: " + site.mined() + " dug, " + site.leftOres().size() + " left");
		helper.assertTrue(file.isDirty(), "an ore dug didn't change the file");
		site.leaveOre(over);
		helper.assertTrue(site.leftOres().size() == 256 && site.leftOres().contains(over), "no room after one was dug");
		// The one read back reports to its own file.
		QuarrySiteManager file2 = loadFile(file.save(new CompoundTag(), registries), registries);
		QuarrySite back2 = file2.get(site.id());
		helper.assertFalse(file2.isDirty(), "a file just loaded is dirty");
		back2.forgetOre(over);
		helper.assertTrue(file2.isDirty() && back2.leftOres().size() == 255, "forgetting one in a loaded quarry didn't change its file");
		helper.succeed();
	}

	/**
	 * Gold, diamond and redstone ore left with a stone pickaxe; then a player mines the gold himself and puts cobblestone
	 * where the redstone was. Still with the stone pickaxe the miner stops waiting for both (only the diamond is left),
	 * and after the upgrade he digs the diamond, leaves the player's cobblestone alone and finishes the quarry.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qa_upgrade_1111e_broken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_upgrade_1111e_broken")
	public void anOreAPlayerTookIsNotWaitedOn(GameTestHelper helper) {
		BlockPos max = new BlockPos(9, 4, 9);
		Setup s = setup(helper, max, new ItemStack(Items.STONE_PICKAXE));
		BlockPos gold = new BlockPos(6, 4, 7);
		BlockPos diamond = new BlockPos(8, 4, 6);
		BlockPos redstone = new BlockPos(9, 4, 9);
		helper.setBlock(gold, Blocks.GOLD_ORE);
		helper.setBlock(diamond, Blocks.DIAMOND_ORE);
		helper.setBlock(redstone, Blocks.REDSTONE_ORE);
		int[] phase = {0};
		boolean[] onlyDiamondWithStone = {false};
		helper.onEachTick(() -> {
			QuarrySite site = live(s);
			if (site == null) {
				return;
			}
			if (phase[0] == 0 && site.leftOres().size() >= 3) {
				phase[0] = 1;
				helper.getLevel().destroyBlock(helper.absolutePos(gold), false);
				helper.setBlock(redstone, Blocks.COBBLESTONE);
				Container chest = helper.getBlockEntity(CHEST);
				chest.setItem(20, new ItemStack(Items.IRON_INGOT, 3));
				chest.setItem(21, new ItemStack(Items.STICK, 2));
			} else if (phase[0] == 1 && s.miner().getMainHandItem().is(Items.STONE_PICKAXE)
				&& site.leftOres().equals(java.util.Set.of(helper.absolutePos(diamond)))) {
				onlyDiamondWithStone[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 1, "the three ores were never all left: " + state(helper, s));
			helper.assertTrue(onlyDiamondWithStone[0], "with the stone pickaxe he never stopped waiting for the ores the player took");
			helper.assertTrue(live(s) == null, "still going: " + state(helper, s));
			helper.assertBlockPresent(Blocks.AIR, diamond);
			helper.assertBlockPresent(Blocks.COBBLESTONE, redstone);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.DIAMOND) == 1 && chest.countItem(Items.RAW_GOLD) == 0 && chest.countItem(Items.REDSTONE) == 0,
				"finds: " + chest.countItem(Items.DIAMOND) + " diamonds, " + chest.countItem(Items.RAW_GOLD) + " raw gold, " + chest.countItem(Items.REDSTONE) + " redstone");
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 1 && chest.countItem(Items.STONE_PICKAXE) == 1, "pickaxes put away: "
				+ chest.countItem(Items.IRON_PICKAXE) + " iron, " + chest.countItem(Items.STONE_PICKAXE) + " stone");
		});
	}

	/**
	 * A Novice with a worn iron pickaxe and 3 diamonds and 2 sticks in his chest makes a diamond pickaxe at his
	 * workstation. The iron pickaxe goes back in his chest exactly once, as worn as it was: never lost, never doubled.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_upgrade_1111e_diamond"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_upgrade_1111e_diamond")
	public void threeDiamondsMakeADiamondPickaxeAndTheIronOneIsKeptOnce(GameTestHelper helper) {
		ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
		worn.setDamageValue(100);
		Setup s = setup(helper, new BlockPos(10, 3, 10), worn, new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(s.miner().getMainHandItem().is(Items.DIAMOND_PICKAXE), "no diamond pickaxe in hand: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			int irons = 0;
			int damage = -1;
			int pickaxes = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack st = chest.getItem(i);
				if (st.is(Items.IRON_PICKAXE)) {
					irons += st.getCount();
					damage = st.getDamageValue();
				}
				if (st.is(net.minecraft.tags.ItemTags.PICKAXES)) {
					pickaxes += st.getCount();
				}
			}
			helper.assertTrue(irons == 1 && damage == 100, "the old iron pickaxe in the chest: " + irons + ", damage " + damage);
			helper.assertTrue(pickaxes == 1, "pickaxes in the chest: " + pickaxes);
			helper.assertTrue(chest.countItem(Items.DIAMOND) == 0 && chest.countItem(Items.STICK) == 0, "materials left: "
				+ chest.countItem(Items.DIAMOND) + " diamonds, " + chest.countItem(Items.STICK) + " sticks");
			helper.assertTrue(s.miner().getVillagerData().getLevel() == 1, "he levelled up for it");
		});
	}

	/**
	 * Stone in hand, 3 ingots, 3 diamonds and 4 sticks: he makes the diamond pickaxe straight away and never an iron one
	 * on the way, and the ingots and 2 sticks are still there afterwards (nothing better than diamond to make from them).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa_upgrade_1111e_best"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_upgrade_1111e_best")
	public void diamondsBeatIronAndTheIngotsStay(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(10, 3, 10), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.STICK, 4));
		boolean[] iron = {false};
		int[] diamondAt = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			if (s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				iron[0] = true;
			}
			if (diamondAt[0] < 0 && s.miner().getMainHandItem().is(Items.DIAMOND_PICKAXE)) {
				diamondAt[0] = tick[0];
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(diamondAt[0] >= 0 && tick[0] >= diamondAt[0] + 2 * 100 + 20, "no diamond pickaxe yet, or not long enough after it: " + state(helper, s));
			helper.assertFalse(iron[0], "made an iron pickaxe on the way");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 3 && chest.countItem(Items.STICK) == 2 && chest.countItem(Items.DIAMOND) == 0,
				"left: " + chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.STICK) + " sticks, " + chest.countItem(Items.DIAMOND) + " diamonds");
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1 && chest.countItem(Items.IRON_PICKAXE) == 0, "pickaxes: "
				+ chest.countItem(Items.STONE_PICKAXE) + " stone, " + chest.countItem(Items.IRON_PICKAXE) + " iron");
		});
	}

	/**
	 * The ingots only in the village store (a Storehouse's chest, not his): through his own work loop he makes the iron
	 * pickaxe from them, the store's ingots are used, and the stone pickaxe goes back in his own chest.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qa_upgrade_1111e_store"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa_upgrade_1111e_store")
	public void theVillageStoresIngotsMakeTheIronPickaxe(GameTestHelper helper) {
		// setup() first: it clears every Storehouse left nearby (B1207), which would take this one too
		Setup s = setup(helper, new BlockPos(10, 3, 10), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STICK, 2));
		BlockPos storehouse = new BlockPos(2, 2, 12);
		BlockPos storeChest = new BlockPos(3, 2, 12);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		Leftovers.after(helper, () -> helper.setBlock(storehouse, Blocks.AIR)); // not a village store for later batches (B1207)
		helper.setBlock(storeChest, Blocks.CHEST);
		Container store = helper.getBlockEntity(storeChest);
		store.setItem(0, new ItemStack(Items.IRON_INGOT, 3));
		helper.succeedWhen(() -> {
			helper.assertTrue(s.miner().getMainHandItem().is(Items.IRON_PICKAXE), "no iron pickaxe from the store's ingots: " + state(helper, s) + " | " + status(s));
			helper.assertTrue(store.countItem(Items.IRON_INGOT) == 0, "the store's ingots weren't used: " + store.countItem(Items.IRON_INGOT));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1 && store.countItem(Items.STONE_PICKAXE) == 0,
				"the stone pickaxe: " + chest.countItem(Items.STONE_PICKAXE) + " in his chest, " + store.countItem(Items.STONE_PICKAXE) + " in the store");
		});
	}

	/**
	 * The status line from both sides: 1 ingot and 1 stick, "needs 2 Iron Ingots for an Iron Pickaxe" (never 3); two more
	 * ingots, "needs 1 Stick for an Iron Pickaxe"; the stick, and the line asks for nothing any more while he digs on with
	 * the iron pickaxe he made.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_upgrade_1111e_status"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_upgrade_1111e_status")
	public void theStatusLineAsksForWhatsMissingUntilItsThere(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(11, 5, 11), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.STICK, 1));
		int[] phase = {0};
		List<String> wrong = new ArrayList<>();
		int[] afterIron = {0};
		helper.onEachTick(() -> {
			String line = status(s);
			if (phase[0] == 0) {
				if (line.contains("needs 3 Iron Ingot") || line.contains("Stick")) {
					wrong.add("before the ingots: " + line);
				}
				if (line.contains("needs 2 Iron Ingots for an Iron Pickaxe")) {
					phase[0] = 1;
					Container chest = helper.getBlockEntity(CHEST);
					chest.setItem(20, new ItemStack(Items.IRON_INGOT, 2));
				}
			} else if (phase[0] == 1) {
				if (line.contains("needs 1 Stick for an Iron Pickaxe")) {
					phase[0] = 2;
					Container chest = helper.getBlockEntity(CHEST);
					chest.setItem(21, new ItemStack(Items.STICK, 1));
				}
			} else if (s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				afterIron[0]++;
				if (line.contains("Iron Pickaxe") || line.contains("needs")) {
					wrong.add("with the iron pickaxe: " + line);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wrong.isEmpty(), "wrong lines: " + wrong);
			helper.assertTrue(phase[0] == 2, "phase " + phase[0] + ", the line: " + status(s) + " | " + state(helper, s));
			helper.assertTrue(afterIron[0] >= 200, "not long enough with the iron pickaxe yet (" + afterIron[0] + "): " + state(helper, s));
			helper.assertTrue(live(s) != null && live(s).status() == QuarrySite.Status.WORKING, "not digging on: " + state(helper, s));
		});
	}

	/** An iron pickaxe in hand and no ingots anywhere: the line never asks for anything, the whole quarry through. */
	//$ gametest_ticks_batch AREA '2400' '"qa_upgrade_1111e_iron_quiet"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_upgrade_1111e_iron_quiet")
	public void anIronPickaxeNeverAsksForIngots(GameTestHelper helper) {
		Setup s = setup(helper, new BlockPos(9, 3, 9), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.STICK, 2));
		List<String> asked = new ArrayList<>();
		helper.onEachTick(() -> {
			String line = status(s);
			if (line.contains("needs") && asked.size() < 3) {
				asked.add(line);
			}
			ItemStack hand = s.miner().getMainHandItem();
			if (!hand.isEmpty() && !hand.is(Items.IRON_PICKAXE) && asked.size() < 3) {
				asked.add("holding " + hand);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(asked.isEmpty(), "asked with an iron pickaxe: " + asked);
			helper.assertTrue(live(s) == null, "still going: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.IRON_PICKAXE) == 1 && chest.countItem(Items.STICK) == 2, "put away: "
				+ chest.countItem(Items.IRON_PICKAXE) + " iron pickaxes, " + chest.countItem(Items.STICK) + " sticks");
		});
	}

	/**
	 * {@code minerCraftsPickaxe} off with a stone pickaxe and 1 ingot: the line never asks, though he lacks 2. Turned on,
	 * it asks for the 2; turned off again, it stops asking at once, and he never makes the pickaxe meanwhile.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_upgrade_1111e_switch"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_upgrade_1111e_switch")
	public void theSwitchHidesTheLineAndBringsItBack(GameTestHelper helper) {
		PickaxeCrafting.ENABLED = false;
		Leftovers.after(helper, () -> PickaxeCrafting.ENABLED = true);
		Setup s = setup(helper, new BlockPos(11, 5, 11), new ItemStack(Items.STONE_PICKAXE),
			new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.STICK, 2));
		int[] tick = {0};
		int[] phase = {0};
		List<String> wrong = new ArrayList<>();
		helper.onEachTick(() -> {
			tick[0]++;
			String line = status(s);
			if (phase[0] == 0) {
				if (line.contains("needs")) {
					wrong.add("while off at tick " + tick[0] + ": " + line);
				}
				if (tick[0] >= 300) {
					phase[0] = 1;
					PickaxeCrafting.ENABLED = true;
				}
			} else if (phase[0] == 1) {
				if (line.contains("needs 2 Iron Ingots for an Iron Pickaxe")) {
					phase[0] = 2;
					PickaxeCrafting.ENABLED = false;
					String now = status(s);
					if (now.contains("needs")) {
						wrong.add("still asking the moment it's off: " + now);
					}
				}
			} else if (phase[0] >= 2 && phase[0] < 300) {
				phase[0]++;
				if (line.contains("needs")) {
					wrong.add("off again: " + line);
				}
			}
			if (s.miner().getMainHandItem().is(Items.IRON_PICKAXE)) {
				wrong.add("made an iron pickaxe from 1 ingot");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wrong.isEmpty(), "wrong: " + wrong.subList(0, Math.min(3, wrong.size())));
			helper.assertTrue(phase[0] >= 300, "phase " + phase[0] + " (1 = turned on, never asked): " + status(s) + " | " + state(helper, s));
		});
	}
}
