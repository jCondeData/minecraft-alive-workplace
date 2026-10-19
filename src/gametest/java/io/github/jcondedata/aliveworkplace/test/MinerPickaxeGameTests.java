package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.PickaxeCrafting;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Miners make their own pickaxe (ROADMAP 36.11): from the cobblestone they mined and two sticks, at their workstation,
 * when they have none or theirs wore out; what they lack goes on the Storehouse board; iron and diamond as they rise;
 * restartable over a save; and with {@code minerCraftsPickaxe} off they wait as before.
 */
public class MinerPickaxeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(7, 2, 7);

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	private static Setup setup(GameTestHelper helper, BlockPos min, BlockPos max, ServerPlayer owner, ItemStack... chest) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
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
		QuarrySite site = Miners.start(level, villager, owner, BoundingBox.fromCorners(helper.absolutePos(min), helper.absolutePos(max)),
			max.getY() - min.getY() + 1);
		site.setStairs(false);
		return new Setup(level, villager, site);
	}

	private static boolean finished(Setup s) {
		return QuarrySiteManager.get(s.level()).get(s.site().id()) == null;
	}

	private static String state(GameTestHelper helper, Setup s) {
		QuarrySite site = QuarrySiteManager.get(s.level()).get(s.site().id());
		return site == null ? "finished" : site.status() + ", " + site.mined() + " dug, holding " + s.miner().getMainHandItem()
			+ ", miner at " + helper.relativePos(s.miner().blockPosition());
	}

	/** Runs {@code check} once, the first tick the quarry shows {@code status}. */
	private static void onceStatus(GameTestHelper helper, Setup s, QuarrySite.Status status, Runnable check) {
		boolean[] seen = {false};
		helper.onEachTick(() -> {
			QuarrySite site = QuarrySiteManager.get(s.level()).get(s.site().id());
			if (!seen[0] && site != null && site.status() == status) {
				seen[0] = true;
				check.run();
			}
		});
	}

	/**
	 * The happy path: no pickaxe, but the cobblestone they mined and two sticks in their chest. The miner makes a stone
	 * pickaxe at the workstation (the status says so, the owner hears of it), digs the quarry out with it, and the
	 * pickaxe comes back with the finds.
	 */
	//$ gametest_ticks_batch AREA '2400' '"miner_pickaxe_made"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "miner_pickaxe_made")
	public void minerMakesAStonePickaxeFromWhatTheyMined(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Setup s = setup(helper, MIN, MAX, owner, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		boolean[] crafting = {false};
		onceStatus(helper, s, QuarrySite.Status.CRAFTING_PICKAXE, () -> {
			crafting[0] = true;
			helper.assertTrue(s.site().mined() == 0, "dug before the pickaxe was made");
			helper.assertTrue(Miners.statusText(s.level(), s.site(), s.miner()).getString().contains("making a pickaxe"),
				"the status doesn't say they're making a pickaxe: " + Miners.statusText(s.level(), s.site(), s.miner()).getString());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(crafting[0], "never showed making a pickaxe");
			helper.assertTrue(finished(s), "still going: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "the made pickaxe isn't back in the chest");
			helper.assertTrue(chest.countItem(Items.STICK) == 0, "the sticks weren't used: " + chest.countItem(Items.STICK));
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 4, "3 cobblestone used, 4 dug: the chest has " + chest.countItem(Items.COBBLESTONE));
		});
	}

	/**
	 * A worn-out pickaxe: three uses left, so it breaks on the third block. The miner makes a new one from the three
	 * cobblestone in their bag, with planks from the chest cut into sticks, and digs on to the end.
	 */
	//$ gametest_ticks_batch AREA '3000' '"miner_pickaxe_worn"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "miner_pickaxe_worn")
	public void aWornOutPickaxeIsMadeAgainFromTheBag(GameTestHelper helper) {
		ItemStack worn = new ItemStack(Items.STONE_PICKAXE);
		worn.setDamageValue(worn.getMaxDamage() - 3);
		BlockPos max = new BlockPos(8, 2, 8);
		Setup s = setup(helper, MIN, max, null, worn, new ItemStack(Items.OAK_PLANKS, 2));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still going: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "no new pickaxe back in the chest: " + chest.countItem(Items.STONE_PICKAXE));
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "the planks weren't cut into sticks");
			helper.assertTrue(chest.countItem(Items.STICK) == 2, "two spare sticks should be back: " + chest.countItem(Items.STICK));
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 6, "9 dug, 3 used: the chest has " + chest.countItem(Items.COBBLESTONE));
		});
	}

	/**
	 * Missing materials: nothing to make a pickaxe from. The Storehouse board asks for the cobblestone, then (with that
	 * in the chest) the sticks, and the miner waits; the player hands the sticks over at the board and the miner makes
	 * the pickaxe and digs.
	 */
	//$ gametest_ticks_batch AREA '2400' '"miner_pickaxe_missing"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "miner_pickaxe_missing")
	public void missingMaterialsGoOnTheStorehouseBoard(GameTestHelper helper) {
		Setup s = setup(helper, MIN, MAX, null);
		Leftovers.village(helper, 48); // the board lists the workers of its village
		BlockPos storehouse = new BlockPos(2, 2, 10);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(s.site().status() == QuarrySite.Status.NEEDS_PICKAXE && s.site().mined() == 0, "not waiting: " + state(helper, s));
			helper.assertTrue(asks(s, Items.COBBLESTONE) == 3, "the board should ask for 3 cobblestone, asks " + asks(s, Items.COBBLESTONE));
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 3));
		});
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(s.site().status() == QuarrySite.Status.NEEDS_PICKAXE && s.site().mined() == 0, "not waiting for sticks: " + state(helper, s));
			helper.assertTrue(asks(s, Items.STICK) == 2, "the board should ask for 2 sticks, asks " + asks(s, Items.STICK));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().add(new ItemStack(Items.STICK, 5));
			ChoiceMenu board = io.github.jcondedata.aliveworkplace.store.StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse));
			int slot = -1;
			for (int i = 0; i < ChoiceMenu.SIZE; i++) {
				if (board.icon(i).is(Items.STICK)) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, "the Storehouse board doesn't show the sticks the miner needs");
			board.press(slot, player);
			helper.assertTrue(player.getInventory().countItem(Items.STICK) == 3, "the board took " + (5 - player.getInventory().countItem(Items.STICK)) + " sticks");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still going: " + state(helper, s));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, "no pickaxe made from the handed-over sticks");
		});
	}

	/** How many of {@code item} the miner's requests ask for (what the Storehouse board lists). */
	private static int asks(Setup s, net.minecraft.world.item.Item item) {
		for (Requests.Request r : Requests.of(s.level(), s.miner())) {
			if (r.item() == item) {
				return r.count();
			}
		}
		return 0;
	}

	/**
	 * Save and reload while making it: the miner is saved and loaded again (an unloading chunk) and the quarry with
	 * it. They start making it again, from the same materials, and make one pickaxe only.
	 */
	//$ gametest_ticks_batch AREA '2400' '"miner_pickaxe_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "miner_pickaxe_reload")
	public void makingAPickaxeSurvivesASaveAndReload(GameTestHelper helper) {
		Setup s = setup(helper, MIN, MAX, null, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		Villager[] miner = {s.miner()};
		onceStatus(helper, s, QuarrySite.Status.CRAFTING_PICKAXE, () -> helper.runAfterDelay(20, () -> {
			ServerLevel level = s.level();
			CompoundTag saved = new CompoundTag();
			helper.assertTrue(miner[0].save(saved), "the miner couldn't be saved");
			miner[0].remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			QuarrySite site = QuarrySiteManager.get(level).get(s.site().id());
			QuarrySite loadedSite = QuarrySite.load(site.save());
			QuarrySiteManager.get(level).remove(site.id());
			QuarrySiteManager.get(level).restore(loadedSite);
			Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
			helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "the miner couldn't be loaded again");
			miner[0] = (Villager) back;
			helper.assertTrue(miner[0].getMainHandItem().isEmpty(), "a pickaxe in hand before it was made");
		}));
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(s.level()).get(s.site().id()) == null, "still going: "
				+ (QuarrySiteManager.get(s.level()).get(s.site().id()) == null ? "-" : QuarrySiteManager.get(s.level()).get(s.site().id()).status()));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.STONE_PICKAXE) == 1, chest.countItem(Items.STONE_PICKAXE) + " pickaxes made");
			helper.assertTrue(chest.countItem(Items.STICK) == 0 && chest.countItem(Items.COBBLESTONE) == 4,
				"materials: " + chest.countItem(Items.STICK) + " sticks, " + chest.countItem(Items.COBBLESTONE) + " cobblestone");
		});
	}

	/** {@code minerCraftsPickaxe} off: the materials stay in the chest and the miner waits for a pickaxe, as before. */
	//$ gametest_ticks_batch AREA '600' '"miner_pickaxe_off"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "miner_pickaxe_off")
	public void switchedOffTheMinerWaitsForAPickaxe(GameTestHelper helper) {
		PickaxeCrafting.ENABLED = false;
		Leftovers.after(helper, () -> PickaxeCrafting.ENABLED = true);
		Setup s = setup(helper, MIN, MAX, null, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.STICK, 2));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(s.site().status() == QuarrySite.Status.NEEDS_PICKAXE && s.site().mined() == 0, "not waiting: " + state(helper, s));
			helper.assertTrue(s.miner().getMainHandItem().isEmpty(), "made a pickaxe with the switch off");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 3 && chest.countItem(Items.STICK) == 2, "materials were taken with the switch off");
			helper.assertTrue(asks(s, Items.STICK) == 0 && asks(s, Items.COBBLESTONE) == 0, "materials asked for with the switch off");
			helper.succeed();
		});
	}

	/** Iron from Journeyman, diamond from Expert, the best they can make; a Novice makes stone even with ingots there. */
	//$ gametest_batch AREA '"miner_pickaxe_tiers"'
	@GameTest(template = AREA, batch = "miner_pickaxe_tiers")
	public void betterPickaxesAsTheMinerRises(GameTestHelper helper) {
		Setup s = setup(helper, MIN, MAX, null, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.DIAMOND, 3),
			new ItemStack(Items.STICK, 8));
		ServerLevel level = s.level();
		Villager v = s.miner();
		BlockPos bench = helper.absolutePos(BENCH);
		List<BlockPos> own = io.github.jcondedata.aliveworkplace.build.SupplyContainers.find(level, bench, s.site().box());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(v);
		int[] levels = {1, 3, 4};
		net.minecraft.world.item.Item[] made = {Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE};
		for (int i = 0; i < levels.length; i++) {
			v.setVillagerData(v.getVillagerData().setLevel(levels[i]));
			ItemStack pick = PickaxeCrafting.craft(level, v, bench, own, bag);
			helper.assertTrue(pick.is(made[i]), "a level " + levels[i] + " miner made " + pick);
		}
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.DIAMOND) == 0
			&& chest.countItem(Items.STICK) == 2, "materials left: " + chest.countItem(Items.STICK) + " sticks");
		// A level-4 miner with only cobblestone left makes stone; with nothing, nothing (and nothing is taken).
		chest.setItem(0, new ItemStack(Items.COBBLED_DEEPSLATE, 3));
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag).is(Items.STONE_PICKAXE), "no stone pickaxe from cobbled deepslate");
		helper.assertTrue(PickaxeCrafting.craft(level, v, bench, own, bag).isEmpty(), "a pickaxe from nothing");
		helper.assertTrue(PickaxeCrafting.missing(level, v, bench, own, bag).item() == Items.COBBLESTONE, "should miss cobblestone first");
		helper.succeed();
	}

	/** Every new sentence a player reads: the status line, the owner's two messages and the setting. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pickaxeWordsAreWritten(GameTestHelper helper) {
		Language lang = Language.getInstance();
		helper.assertTrue(lang.getOrDefault("message.aliveworkplace.quarry.state.crafting_pickaxe").equals("making a pickaxe"), "the status line");
		String needs = lang.getOrDefault("message.aliveworkplace.quarry.needs_pickaxe_or_materials");
		helper.assertTrue(needs.contains("3 Cobblestone and 2 Sticks") && needs.split("%s", -1).length == 3, "the needs message: " + needs);
		String made = lang.getOrDefault("message.aliveworkplace.quarry.crafted_pickaxe");
		helper.assertTrue(made.split("%s", -1).length == 3 && made.contains("from what they mined"), "the made message: " + made);
		helper.assertTrue(lang.has("aliveworkplace.config.minerCraftsPickaxe") && lang.getOrDefault("aliveworkplace.config.minerCraftsPickaxe.tooltip").contains("Off:"),
			"the setting's words");
		helper.assertTrue(new io.github.jcondedata.aliveworkplace.WorkplaceConfig().minerCraftsPickaxe, "the setting should default on");
		helper.succeed();
	}
}
