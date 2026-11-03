package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-11-03) of the Builder's materials and the Storehouse, from the spec (wiki builder.md "Materials",
 * "Whichever table (B290)", B132, B636; ROADMAP 23.1 "no item is duplicated or lost", 23.8 "cancel with materials
 * returned"), not from the code. Targets what the earlier QA classes leave out: two Builders sharing a Storehouse that
 * holds enough for only one, the stock split exactly across the Storehouse's two chests, a Storehouse double chest, the
 * board and the list's exact numbers when the Storehouse covers part of the need, and a cancel with Storehouse goods in
 * the bag. Every test counts the cobblestone: what stands in the huts plus what is in the chests, bags and on the ground
 * is exactly what was stocked, at every tick.
 */
public class QaBuilderSupplies1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** QaBuilderStockGameTests' layout. */
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final List<BlockPos> STORE_CHESTS = List.of(new BlockPos(23, 2, 26), new BlockPos(26, 2, 23));
	private static final BlockPos TABLE2 = new BlockPos(18, 2, 2);
	private static final BlockPos CHEST2 = new BlockPos(18, 2, 3);
	private static final BlockPos HUT2 = new BlockPos(15, 2, 12);
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1024");

	// ------------------------------------------------------------------------------------------------------ helpers

	private static final BuildPlan.Stage[] BUILT = {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION};

	private static Set<BlockPos> spotsOf(BuildPlan plan, Item item) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : BUILT) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().isAir() && s.state().getBlock().asItem() == item) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	private static int placedOf(ServerLevel level, BuildPlan plan, Item item) {
		int n = 0;
		for (BlockPos p : spotsOf(plan, item)) {
			if (level.getBlockState(p).getBlock().asItem() == item) {
				n++;
			}
		}
		return n;
	}

	private static int inContainers(GameTestHelper helper, List<BlockPos> containers, Item item) {
		int n = 0;
		for (BlockPos p : containers) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) { // a mined chest has none
				n += c.countItem(item);
			}
		}
		return n;
	}

	/** {@code item} in these containers, every villager's bag and inventory near the test and on the ground near it. */
	private static int loose(GameTestHelper helper, List<BlockPos> containers, Item item) {
		int n = inContainers(helper, containers, item);
		AABB around = helper.getBounds().inflate(16);
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, around)) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			n += v.getInventory().countItem(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int bagTotal(Villager v) {
		int n = 0;
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
			n += s.getCount();
		}
		return n;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static int cobbleOf(BuildPlan plan) {
		Integer listed = plan.materials().get(Items.COBBLESTONE);
		if (listed == null || listed != spotsOf(plan, Items.COBBLESTONE).size()) {
			throw new GameTestAssertException("setup: the test hut lists " + listed + " cobblestone but draws " + spotsOf(plan, Items.COBBLESTONE).size());
		}
		return listed;
	}

	/** How many of {@code item} the Storehouse's board asks for (0: none), as a player opening it sees it. */
	private static int asked(GameTestHelper helper, ServerPlayer player, Item item) {
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(STOREHOUSE));
		int n = 0;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(item)) {
				n += board.icon(i).getCount();
			}
		}
		return n;
	}

	private static List<BlockPos> stockContainers() {
		List<BlockPos> all = new ArrayList<>(STORE_CHESTS);
		all.add(QaBuilderStockGameTests.TABLE_CHEST);
		return all;
	}

	/** Jesse's Storehouse (no cobblestone yet, one door), the table with planks and a torch, and his builder on the test hut. */
	private static QaBuilderStockGameTests.Builder firstBuilder(GameTestHelper helper) {
		QaBuilderStockGameTests.setup(helper, STOREHOUSE, 0, 1);
		for (BlockPos p : STORE_CHESTS) {
			((Container) helper.getBlockEntity(p)).setItem(0, ItemStack.EMPTY);
		}
		((Container) helper.getBlockEntity(STORE_CHESTS.get(1))).setItem(0, new ItemStack(Items.OAK_DOOR));
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(b.site().id()) != null) {
				BuildSiteManager.get(level).remove(b.site().id());
			}
		});
		return b;
	}

	/** A second builder of Jesse's at another table 16 blocks off, with planks, a torch and a door, on a second hut. */
	private static QaBuilderStockGameTests.Builder secondBuilder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE2, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(TABLE2));
		helper.setBlock(CHEST2, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(CHEST2), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH), new ItemStack(Items.OAK_DOOR));
		Villager v2 = helper.spawn(EntityType.VILLAGER, TABLE2.east());
		Builders.employ(level, v2, helper.absolutePos(TABLE2));
		ModAttachments.BUILDER_EMPLOYER.set(v2, new Employer(JESSE, "Jesse"));
		BuildSite site2 = Builders.start(level, v2, JESSE, "Jesse", TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT2), Rotation.NONE, Mirror.NONE));
		BuildPlan plan2 = site2.plan(level);
		if (plan2 == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		Village.forget(v2);
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site2.id()) != null) {
				BuildSiteManager.get(level).remove(site2.id());
			}
		});
		return new QaBuilderStockGameTests.Builder(v2, site2, plan2);
	}

	private static boolean finished(ServerLevel level, QaBuilderStockGameTests.Builder b) {
		return BuildSiteManager.get(level).get(b.site().id()) == null;
	}

	private static void assertBuilt(GameTestHelper helper, ServerLevel level, QaBuilderStockGameTests.Builder b) {
		helper.assertTrue(finished(level, b), "still building at " + b.site().bench() + ": stage=" + b.site().stage() + " status=" + b.site().status()
			+ " missing=" + b.site().missing());
		helper.assertTrue(b.plan().unfinished(level).isEmpty(), b.plan().unfinished(level).size() + " block(s) of the hut at " + b.site().bench() + " not as drawn");
		helper.assertTrue(bagTotal(b.villager()) == 0, "the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
	}

	private static void runCommand(ServerLevel level, String command) {
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
	}

	// ------------------------------------------------------------------------------------- two builders, one's worth

	/**
	 * B132 and 23.1: Jesse's two builders on two huts, each with his planks, torch and door, share one Storehouse holding
	 * one hut's cobblestone and 2 more. At every tick the cobblestone standing in both huts plus what is in the chests,
	 * bags and on the ground is exactly what was stocked (nothing counted, taken or placed twice). With 2 short of both huts,
	 * after 2500 ticks the huts aren't both finished, and the Storehouse's board asks for cobblestone, never more than the
	 * real shortfall of the two. Topped up with one hut's worth, both huts finish and exactly 2 are left.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaSup1103TwoShort"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaSup1103TwoShort")
	public void twoBuildersWithOneHutsWorthCountNothingTwiceAndFinishWhenToppedUp(GameTestHelper helper) {
		twoShort(helper, 0);
	}

	/**
	 * The same with the Storehouse holding 30, less than one hut more than the other: both builders may take some and both
	 * wait. At every tick the count is exact and the board never asks for more than the 20 the huts are short of; after
	 * 2500 ticks the huts aren't both finished. Topped up with 20 + 2, both finish (neither stalls on what the other holds)
	 * and exactly 2 are left.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaSup1103BothShort"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaSup1103BothShort")
	public void twoBuildersBothShortNeitherStallsOnTheOthersBagOnceToppedUp(GameTestHelper helper) {
		twoShort(helper, 30);
	}

	/** {@code store} cobblestone in the Storehouse (0: the first hut's and 2 more), then topped up to both huts' and 2 more. */
	private static void twoShort(GameTestHelper helper, int store) {
		QaBuilderStockGameTests.Builder first = firstBuilder(helper);
		QaBuilderStockGameTests.Builder second = secondBuilder(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int need1 = cobbleOf(first.plan());
		int need2 = cobbleOf(second.plan());
		int stock = store > 0 ? store : need1 + 2;
		int topUp = need1 + need2 + 2 - stock;
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, stock));
		List<BlockPos> all = stockContainers();
		all.add(CHEST2);
		int[] stocked = {stock};
		boolean[] toppedUp = {false};
		int[] maxAsked = {0};
		LOGGER.info("qa supplies 1103 two short: huts take {} + {} cobblestone, the Storehouse holds {}", need1, need2, stock);
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int standing = placedOf(level, first.plan(), Items.COBBLESTONE) + placedOf(level, second.plan(), Items.COBBLESTONE);
			int loose = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(standing + loose == stocked[0], "tick " + helper.getTick() + ": stocked " + stocked[0] + " cobblestone, now " + standing
				+ " standing + " + loose + " loose = " + (standing + loose));
			if (toppedUp[0]) {
				return;
			}
			if (helper.getTick() % 20 == 0) {
				int real = Math.max(0, need1 + need2 - standing - loose);
				int a = asked(helper, player, Items.COBBLESTONE);
				maxAsked[0] = Math.max(maxAsked[0], a);
				helper.assertTrue(a <= real, "tick " + helper.getTick() + ": the board asks for " + a + " cobblestone, the two huts are short of only " + real);
			}
			if (helper.getTick() >= 2500) {
				helper.assertTrue(!(finished(level, first) && finished(level, second)), "both huts finished with 2 cobblestone fewer than they take");
				helper.assertTrue(maxAsked[0] > 0, "2500 ticks with the two huts 2 cobblestone short and the board never asked for cobblestone (statuses "
					+ first.site().status() + " " + first.site().missing() + " / " + second.site().status() + " " + second.site().missing() + ")");
				LOGGER.info("qa supplies 1103 two short ({}) at 2500: first {} {} {}, second {} {} {}, board asked at most {}", stock, finished(level, first), first.site().status(),
					first.site().missing(), finished(level, second), second.site().status(), second.site().missing(), maxAsked[0]);
				Container chest = (Container) helper.getBlockEntity(STORE_CHESTS.get(0));
				int slot = 0;
				while (!chest.getItem(slot).isEmpty()) {
					slot++;
				}
				chest.setItem(slot, new ItemStack(Items.COBBLESTONE, topUp));
				chest.setChanged();
				stocked[0] += topUp;
				toppedUp[0] = true;
				Village.forget(first.villager());
				Village.forget(second.villager());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(toppedUp[0], "not topped up yet");
			assertBuilt(helper, level, first);
			assertBuilt(helper, level, second);
			int left = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(left == 2, "after both huts " + left + " cobblestone are left, wanted 2");
		});
	}

	// ------------------------------------------------------------------------------------------- the stock spread out

	/**
	 * The hut's cobblestone exactly, split 12 and the rest across the Storehouse's two chests (the door beside the second
	 * share): neither chest holds enough alone. The hut is built and both Storehouse chests end with no cobblestone, none
	 * on the ground or in the bag: every one used once.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaSup1103Split"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaSup1103Split")
	public void theCobblestoneSplitAcrossTheStorehousesTwoChestsIsAllUsed(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need = cobbleOf(b.plan());
		helper.assertTrue(need > 12, "setup: the hut takes only " + need + " cobblestone");
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
		((Container) helper.getBlockEntity(STORE_CHESTS.get(1))).setItem(1, new ItemStack(Items.COBBLESTONE, need - 12));
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(!b.site().missing().containsKey(Items.COBBLESTONE), "with the cobblestone split across the two Storehouse chests the status still needs "
			+ b.site().missing());
		helper.onEachTick(() -> {
			int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
			int loose = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(standing + loose == need, "tick " + helper.getTick() + ": stocked " + need + ", now " + standing + " standing + " + loose + " loose");
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(loose(helper, stockContainers(), Items.COBBLESTONE) == 0, "cobblestone left over: " + loose(helper, stockContainers(), Items.COBBLESTONE));
			helper.assertTrue(((Container) helper.getBlockEntity(STORE_CHESTS.get(1))).countItem(Items.OAK_DOOR) == 0, "the Storehouse's door wasn't used");
		});
	}

	/**
	 * A Storehouse double chest: the chest by the Storehouse joined with a second half one block further west, all the
	 * cobblestone (40) in that far half. The status counts it, the hut is built from it, the double chest is still whole and
	 * exactly 40 less the hut's cobblestone is left in it.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaSup1103Double"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaSup1103Double")
	public void aStorehouseDoubleChestFeedsTheBuilderFromItsFarHalf(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need = cobbleOf(b.plan());
		BlockPos near = STORE_CHESTS.get(0);
		BlockPos far = near.west();
		// Facing north, a LEFT half joins the block clockwise of north (east): far (west) is LEFT, near is RIGHT.
		BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
		helper.setBlock(far, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(near, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.assertTrue(ChestBlock.getConnectedDirection(helper.getBlockState(far)) == Direction.EAST, "setup: the far half doesn't join east");
		((Container) helper.getBlockEntity(far)).setItem(5, new ItemStack(Items.COBBLESTONE, 40));
		List<BlockPos> containers = new ArrayList<>(stockContainers());
		containers.add(far);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(!b.site().missing().containsKey(Items.COBBLESTONE), "with 40 cobblestone in the Storehouse double chest's far half the status still needs "
			+ b.site().missing());
		helper.onEachTick(() -> {
			int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
			int loose = loose(helper, containers, Items.COBBLESTONE);
			helper.assertTrue(standing + loose == 40, "tick " + helper.getTick() + ": stocked 40, now " + standing + " standing + " + loose + " loose");
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(helper.getBlockState(far).is(Blocks.CHEST) && helper.getBlockState(far).getValue(ChestBlock.TYPE) == ChestType.LEFT
				&& helper.getBlockState(near).is(Blocks.CHEST) && helper.getBlockState(near).getValue(ChestBlock.TYPE) == ChestType.RIGHT, "the double chest isn't whole");
			int inChest = inContainers(helper, List.of(near, far), Items.COBBLESTONE);
			helper.assertTrue(inChest == 40 - need, "the double chest holds " + inChest + " cobblestone, wanted " + (40 - need));
		});
	}

	// ---------------------------------------------------------------------------------- the numbers a player reads

	/**
	 * B636 and the Book and Quill list: the Storehouse holds the hut's cobblestone less 7. The status needs exactly 7, the
	 * list asks exactly 7 to bring, and the Storehouse's board asks exactly 7 while he builds, never more (the Storehouse's
	 * own stock is ticked off, the bag never asked again). Once 7 are put in, the hut is finished and none is left.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaSup1103Seven"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaSup1103Seven")
	public void sevenShortIsSevenOnTheStatusTheListAndTheBoard(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int need = cobbleOf(b.plan());
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, need - 7));
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) == 7, "the status needs " + b.site().missing() + ", wanted 7 cobblestone");
		int toBring = 0;
		for (BlueprintSupplies.Line line : SiteList.checklist(level, b.site(), b.villager()).lines()) {
			if (line.item() == Items.COBBLESTONE) {
				toBring += line.toBring();
			}
		}
		helper.assertTrue(toBring == 7, "the Book and Quill list asks " + toBring + " cobblestone to bring, wanted 7");
		long[] seenAt = {-1};
		boolean[] given = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (given[0] || helper.getTick() % 10 != 0) {
				return;
			}
			int a = asked(helper, player, Items.COBBLESTONE);
			helper.assertTrue(a <= 7, "tick " + helper.getTick() + ": the board asks for " + a + " cobblestone, he is short of 7");
			if (a == 7 && seenAt[0] < 0) {
				seenAt[0] = helper.getTick();
			}
			if (helper.getTick() >= 1500) {
				helper.assertTrue(seenAt[0] >= 0, "in 1500 ticks the board never asked for the 7 cobblestone (status " + b.site().status() + ", missing " + b.site().missing() + ")");
				int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
				int loose = loose(helper, stockContainers(), Items.COBBLESTONE);
				helper.assertTrue(standing + loose == need - 7, "stocked " + (need - 7) + ", now " + standing + " standing + " + loose + " loose");
				Container store = (Container) helper.getBlockEntity(STORE_CHESTS.get(0));
				store.setItem(20, new ItemStack(Items.COBBLESTONE, 7));
				store.setChanged();
				given[0] = true;
				Village.forget(b.villager());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0], "not given yet");
			assertBuilt(helper, level, b);
			helper.assertTrue(loose(helper, stockContainers(), Items.COBBLESTONE) == 0, loose(helper, stockContainers(), Items.COBBLESTONE) + " cobblestone left, wanted 0");
			helper.assertTrue(asked(helper, player, Items.COBBLESTONE) == 0, "the finished build still has cobblestone on the board");
		});
	}

	// ------------------------------------------------------------------------------------------------ cancel mid-job

	/**
	 * 23.8 with the Storehouse: all 40 cobblestone in the Storehouse. Mid-STRUCTURE, with cobblestone from it in his bag,
	 * {@code /workplace cancel <site>}: his bag is empty, every cobblestone is counted (standing, in a chest or on the
	 * ground), the blueprint is back once, and 200 ticks on nothing has changed and he is on no site.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaSup1103Cancel"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaSup1103Cancel")
	public void aBuildCanceledWithStorehouseCobblestoneInTheBagLosesNone(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		boolean[] done = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (done[0] || b.site().stage() != BuildPlan.Stage.STRUCTURE || ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).count(Items.COBBLESTONE) == 0
				|| placedOf(level, b.plan(), Items.COBBLESTONE) == 0) {
				return;
			}
			done[0] = true;
			int carried = ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).count(Items.COBBLESTONE);
			runCommand(level, "workplace cancel " + b.site().id());
			helper.assertTrue(finished(level, b), "the site is still there after /workplace cancel");
			helper.assertTrue(bagTotal(b.villager()) == 0, "after the cancel he still carries " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
			int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
			int loose = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(standing + loose == 40, "right after the cancel (he carried " + carried + "): " + standing + " standing + " + loose + " loose, stocked 40");
			int prints = inContainers(helper, stockContainers(), ModItems.BLUEPRINT);
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				if (e.getItem().is(ModItems.BLUEPRINT)) {
					prints += e.getItem().getCount();
				}
			}
			helper.assertTrue(prints == 1, prints + " blueprints came back after the cancel, wanted 1");
			LOGGER.info("qa supplies 1103 cancel: carried {}, {} standing, {} loose", carried, standing, loose);
			helper.runAfterDelay(200, () -> {
				int s2 = placedOf(level, b.plan(), Items.COBBLESTONE);
				int l2 = loose(helper, stockContainers(), Items.COBBLESTONE);
				helper.assertTrue(s2 + l2 == 40, "200 ticks after the cancel: " + s2 + " standing + " + l2 + " loose, stocked 40");
				helper.assertTrue(Builders.activeSite(level, b.villager()) == null, "200 ticks after the cancel he is on a site again");
				helper.succeed();
			});
		});
	}

	// ------------------------------------------------------------------------------- the Storehouse chest mined

	/**
	 * The Storehouse chest with all 40 cobblestone is mined (its things spill) once he has taken some: at every tick the
	 * count is exact (nothing made from the spilled chest, nothing placed that he didn't carry). A chest put back with the spilled cobblestone in it (the items on the ground picked up), he finishes and
	 * exactly 40 less the hut's is left.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSup1103ChestMined"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSup1103ChestMined")
	public void aStorehouseChestMinedMidJobSpillsButDoublesNothingAndHeFinishesWhenItIsBack(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need = cobbleOf(b.plan());
		BlockPos chest = STORE_CHESTS.get(0);
		((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.COBBLESTONE, 40));
		long[] minedAt = {-1};
		boolean[] back = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
			int loose = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(standing + loose == 40, "tick " + helper.getTick() + ": stocked 40, now " + standing + " standing + " + loose + " loose");
			if (minedAt[0] < 0) {
				if (level.getBlockEntity(helper.absolutePos(chest)) instanceof Container c && c.countItem(Items.COBBLESTONE) < 40) {
					level.destroyBlock(helper.absolutePos(chest), true);
					minedAt[0] = helper.getTick();
					LOGGER.info("qa supplies 1103 chest mined at {}: bag {}", minedAt[0], ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
				}
				return;
			}
			if (!back[0] && helper.getTick() - minedAt[0] >= 600) {
				int left = placedOf(level, b.plan(), Items.COBBLESTONE);
				int spilled = 0;
				for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
					if (e.getItem().is(Items.COBBLESTONE)) {
						spilled += e.getItem().getCount();
						e.discard();
					}
				}
				helper.setBlock(chest, Blocks.CHEST);
				((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.COBBLESTONE, spilled));
				Village.forget(b.villager());
				back[0] = true;
				LOGGER.info("qa supplies 1103 chest back with {} spilled cobblestone, {} standing", spilled, left);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the chest isn't back yet");
			assertBuilt(helper, level, b);
			int left = loose(helper, stockContainers(), Items.COBBLESTONE);
			helper.assertTrue(left == 40 - need, left + " cobblestone left, wanted " + (40 - need));
		});
	}

	// ------------------------------------------------------------------------------------- two Storehouses, split

	/**
	 * "Beyond its bench's chests a Builder counts and takes the village's stock" (B290): Jesse's two Storehouses in the
	 * village, 12 cobblestone by one and the rest of the hut's by the other, neither enough alone. The status needs no
	 * cobblestone, the hut is built, both Storehouses' chests end empty of it, and the count is exact at every tick.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaSup1103TwoStores"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaSup1103TwoStores")
	public void theHutsCobblestoneSplitBetweenTwoStorehousesIsAllUsed(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = firstBuilder(helper);
		ServerLevel level = helper.getLevel();
		int need = cobbleOf(b.plan());
		BlockPos store2 = new BlockPos(26, 2, 4);
		BlockPos chest2 = store2.west();
		helper.setBlock(store2, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(store2));
		((io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity) helper.getBlockEntity(store2)).setOwner(JESSE, "Jesse");
		helper.setBlock(chest2, Blocks.CHEST);
		((Container) helper.getBlockEntity(STORE_CHESTS.get(0))).setItem(0, new ItemStack(Items.COBBLESTONE, 12));
		((Container) helper.getBlockEntity(chest2)).setItem(3, new ItemStack(Items.COBBLESTONE, need - 12));
		List<BlockPos> containers = new ArrayList<>(stockContainers());
		containers.add(chest2);
		Village.forget(b.villager());
		Builders.statusText(level, b.site(), b.villager());
		helper.assertTrue(!b.site().missing().containsKey(Items.COBBLESTONE), "with 12 cobblestone by one Storehouse and " + (need - 12)
			+ " by the other the status still needs " + b.site().missing());
		helper.onEachTick(() -> {
			int standing = placedOf(level, b.plan(), Items.COBBLESTONE);
			int loose = loose(helper, containers, Items.COBBLESTONE);
			helper.assertTrue(standing + loose == need, "tick " + helper.getTick() + ": stocked " + need + ", now " + standing + " standing + " + loose + " loose");
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b);
			helper.assertTrue(inContainers(helper, containers, Items.COBBLESTONE) == 0, inContainers(helper, containers, Items.COBBLESTONE) + " cobblestone left in the chests");
		});
	}
}
