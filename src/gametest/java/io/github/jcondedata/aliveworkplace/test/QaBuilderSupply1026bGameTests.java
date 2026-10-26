package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane b, 2026-10-26) of where a Builder's materials go, from the spec, not the code:
 * <ul>
 *   <li>ROADMAP 23.1 "every build finishes; no item is duplicated or lost (count them before and after)"; 23.8 "cancel
 *   with materials returned"; wiki builder.md: {@code /workplace cancel <site>} "the blueprint stays placed; hand it back
 *   to carry on", a builder "empties the bag at the chests when it finishes or stops helping", B22 "a builder's steps need
 *   exactly the build's material list, nothing more".</li>
 *   <li>wiki builder.md "Whichever table" (B290): a Builder "counts and takes the village's stock"; B132: two Builders
 *   sharing a Storehouse each take only what they need.</li>
 *   <li>docs/guide/blueprint-table.md "Breaking it": a builder who hasn't leveled "may go back to having no job".</li>
 *   <li>config-switches.md {@code workplaceFreeMaterials}: "Builders place blocks without needing materials".</li>
 * </ul>
 * Every test counts each material before and after: what stands in the build plus what is in the chests, bags and on the
 * ground must be exactly what was stocked.
 */
public class QaBuilderSupply1026bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** QaBuilderDisruptionGameTests' supplies: the chest by the table and the barrels beside it. */
	private static final List<BlockPos> SUPPLIES = List.of(new BlockPos(2, 2, 4), new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
	/** QaBuilderStockGameTests' layout (its STOREHOUSE and STORE_CHESTS). */
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final List<BlockPos> STORE_CHESTS = List.of(new BlockPos(23, 2, 26), new BlockPos(26, 2, 23));
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1024");

	// ------------------------------------------------------------------------------------------------------ helpers

	private static final BuildPlan.Stage[] BUILT = {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION};

	/** Places of the plan (foundation, structure, decoration) drawn as {@code item}'s block. */
	private static java.util.Set<BlockPos> spotsOf(BuildPlan plan, Item item) {
		java.util.Set<BlockPos> out = new java.util.HashSet<>();
		for (BuildPlan.Stage stage : BUILT) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!s.state().isAir() && s.state().getBlock().asItem() == item) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	/** Places of the plan drawn as {@code item}'s block where that block stands. */
	private static int placedOf(ServerLevel level, BuildPlan plan, Item item) {
		int n = 0;
		for (BlockPos p : spotsOf(plan, item)) {
			if (level.getBlockState(p).getBlock().asItem() == item) {
				n++;
			}
		}
		return n;
	}

	/** The plan's planks and cobblestone, each one block for one item: the ones we can count exactly. */
	private static List<Item> oneForOne(BuildPlan plan) {
		List<Item> out = new ArrayList<>();
		for (Item item : List.of(Items.OAK_PLANKS, Items.COBBLESTONE)) {
			Integer listed = plan.materials().get(item);
			if (listed == null) {
				continue;
			}
			int spots = spotsOf(plan, item).size();
			if (spots != listed) {
				throw new GameTestAssertException("setup: the plan lists " + listed + " " + item + " but draws " + spots + " places of it");
			}
			out.add(item);
		}
		return out;
	}

	/** {@code item} in these containers, every villager's bag and inventory near the test and on the ground near it. */
	private static int loose(GameTestHelper helper, List<BlockPos> containers, Item item) {
		int n = 0;
		for (BlockPos p : containers) {
			if (helper.getBlockEntity(p) instanceof Container c) {
				n += c.countItem(item);
			}
		}
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

	/** A one-for-one material's count: what stands plus what is loose is what was stocked. */
	private static void assertCounted(GameTestHelper helper, ServerLevel level, BuildPlan plan, List<BlockPos> containers, Map<Item, Integer> stocked, String when) {
		for (Item item : oneForOne(plan)) {
			int standing = placedOf(level, plan, item);
			int loose = loose(helper, containers, item);
			helper.assertTrue(standing + loose == stocked.get(item), when + ": " + item + " stocked " + stocked.get(item) + ", now " + standing
				+ " standing + " + loose + " in the chests, bags and on the ground = " + (standing + loose));
		}
	}

	/** Blueprints in these containers, on the ground near the test or in a player's inventory near it. */
	private static int blueprints(GameTestHelper helper, List<BlockPos> containers) {
		int n = 0;
		for (BlockPos p : containers) {
			if (helper.getBlockEntity(p) instanceof Container c) {
				n += c.countItem(ModItems.BLUEPRINT);
			}
		}
		AABB around = helper.getBounds().inflate(16);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
			if (e.getItem().is(ModItems.BLUEPRINT)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** Waits for STRUCTURE with at least 3 blocks placed and the builder carrying at least one material, then runs {@code then} once. */
	private static void midStructureWithABag(GameTestHelper helper, QaBuilderDisruptionGameTests.Run r, Runnable then) {
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || r.site().stage() != BuildPlan.Stage.STRUCTURE || QaBuilderDisruptionGameTests.placed(r).size() < 3) {
				return;
			}
			boolean carries = false;
			for (Item item : oneForOne(r.plan())) {
				carries |= QaBuilderDisruptionGameTests.bag(r.builder(), item) > 0;
			}
			if (!carries) {
				return;
			}
			done[0] = true;
			then.run();
		});
	}

	private static void runCommand(ServerLevel level, String command) {
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
	}

	// ----------------------------------------------------------------------------------------------- cancel mid-job

	/**
	 * 23.8 "cancel with materials returned": mid-STRUCTURE, with planks or cobblestone in the builder's bag, an operator
	 * types {@code /workplace cancel <site>}. The site is gone, his bag is empty, what he placed stands, and every item he
	 * carried is back: standing + loose is exactly what was stocked. The blueprint comes back (nobody hired him, so into the
	 * chest by the table), one, not lost and not doubled.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaSupply1026bCancel"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaSupply1026bCancel")
	public void aBuildCanceledMidStructureGivesBackEveryCarriedItem(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BLUEPRINT_TABLE);
		helper.assertTrue(oneForOne(r.plan()).size() == 2, "setup: the test hut's one-for-one materials are " + oneForOne(r.plan()) + " of " + r.plan().materials());
		midStructureWithABag(helper, r, () -> {
			int carried = bagTotal(r.builder());
			runCommand(r.level(), "workplace cancel " + r.site().id());
			helper.assertTrue(BuildSiteManager.get(r.level()).get(r.site().id()) == null, "the site is still there after /workplace cancel");
			helper.assertTrue(bagTotal(r.builder()) == 0, "after the cancel the builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).stacks()
				+ " (carried " + carried + ")");
			assertCounted(helper, r.level(), r.plan(), SUPPLIES, r.stocked(), "right after the cancel (bag held " + carried + ")");
			helper.assertTrue(blueprints(helper, SUPPLIES) == 1, "after the cancel " + blueprints(helper, SUPPLIES) + " blueprints are in the chests or on the ground, wanted 1");
			LOGGER.info("qa supply 1026b cancel: bag of {} given back, {}", carried, QaBuilderDisruptionGameTests.state(helper, r));
			helper.runAfterDelay(200, () -> {
				assertCounted(helper, r.level(), r.plan(), SUPPLIES, r.stocked(), "200 ticks after the cancel");
				helper.assertTrue(Builders.activeSite(r.level(), r.builder()) == null, "200 ticks after the cancel the builder is on a site again");
				helper.succeed();
			});
		});
	}

	/**
	 * The same cancel with every free slot of the chest and barrels filled with dirt first: what the builder carried has
	 * nowhere to go in his supplies. Nothing is lost: it lies on the ground (or went elsewhere near), standing + loose still
	 * exactly what was stocked, and the dirt is all still there.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaSupply1026bCancelFull"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaSupply1026bCancelFull")
	public void aBuildCanceledWithItsChestsFullLosesNothing(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BLUEPRINT_TABLE);
		midStructureWithABag(helper, r, () -> {
			int dirt = 0;
			for (BlockPos p : SUPPLIES) {
				Container c = helper.getBlockEntity(p);
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (c.getItem(i).isEmpty()) {
						c.setItem(i, new ItemStack(Items.DIRT, 64));
						dirt += 64;
					}
				}
				c.setChanged();
			}
			int carried = bagTotal(r.builder());
			runCommand(r.level(), "workplace cancel " + r.site().id());
			helper.assertTrue(bagTotal(r.builder()) == 0, "after the cancel with full chests the builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).stacks());
			assertCounted(helper, r.level(), r.plan(), SUPPLIES, r.stocked(), "right after the cancel with the chests full (bag held " + carried + ")");
			int dirtNow = loose(helper, SUPPLIES, Items.DIRT);
			helper.assertTrue(dirtNow == dirt, "the chests held " + dirt + " dirt before the cancel and " + dirtNow + " after");
			helper.assertTrue(blueprints(helper, SUPPLIES) == 1, "after the cancel with full chests " + blueprints(helper, SUPPLIES) + " blueprints are around, wanted 1");
			LOGGER.info("qa supply 1026b cancel full: bag of {} given back with the chests full", carried);
			helper.succeed();
		});
	}

	/**
	 * /workplace cancel: "the blueprint stays placed; hand it back to carry on". A player takes the blueprint the cancel put in
	 * the chest and right-clicks the builder with it: the build carries on where it stood, ends exactly as drawn, and the
	 * chest holds exactly the spare (nothing built twice, nothing taken twice).
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaSupply1026bHandBack"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaSupply1026bHandBack")
	public void aCanceledBuildHandedBackFinishesWithNothingDoubled(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHb6"));
		ServerPlayer p = c.player();
		BuildSite[] again = new BuildSite[1];
		midStructureWithABag(helper, r, () -> {
			int placedBefore = r.plan().placeableCount() - r.plan().unfinished(r.level()).size();
			runCommand(r.level(), "workplace cancel " + r.site().id());
			ItemStack blueprint = ItemStack.EMPTY;
			for (BlockPos pos : SUPPLIES) {
				Container chest = helper.getBlockEntity(pos);
				for (int i = 0; i < chest.getContainerSize() && blueprint.isEmpty(); i++) {
					if (chest.getItem(i).is(ModItems.BLUEPRINT)) {
						blueprint = chest.removeItemNoUpdate(i);
						chest.setChanged();
					}
				}
			}
			helper.assertTrue(!blueprint.isEmpty(), "the canceled build's blueprint isn't in the chests");
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			QaStageSixGameTests.clickVillager(p, r.builder(), c.seen());
			again[0] = Builders.activeSite(r.level(), r.builder());
			helper.assertTrue(again[0] != null, "handing the blueprint back started nothing: " + c.seen().stream().map(net.minecraft.network.chat.Component::getString).toList());
			helper.assertTrue(again[0].placement().equals(r.site().placement()), "the blueprint handed back built somewhere else: " + again[0].placement() + " vs " + r.site().placement());
			LOGGER.info("qa supply 1026b hand back: {} blocks stood at the cancel, carrying on", placedBefore);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(again[0] != null, "not handed back yet");
			helper.assertTrue(BuildSiteManager.get(r.level()).get(again[0].id()) == null, "still building the handed-back hut: stage=" + again[0].stage()
				+ " status=" + again[0].status() + " missing=" + again[0].missing());
			List<BlockPos> wrong = r.plan().unfinished(r.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) not as drawn after the handed-back build");
			QaBuilderDisruptionGameTests.assertNothingMadeOrLost(helper, r, Map.of());
		});
	}

	// ------------------------------------------------------------------------------------------ free materials switched

	/**
	 * {@code workplaceFreeMaterials} switched on mid-STRUCTURE (with materials in the bag) and the hut finished: blocks may
	 * now come from nothing, but no item does. On every tick the chest, bags and ground never hold more of a material than
	 * was stocked, and at the end what is loose is at least what was stocked less what stands (nothing taken vanished).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaSupply1026bFree"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaSupply1026bFree")
	public void freeMaterialsSwitchedOnMidBuildMakesNoItemAndLosesNone(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BLUEPRINT_TABLE);
		ServerLevel level = r.level();
		boolean was = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(was, level.getServer()));
		boolean[] on = {false};
		List<String> grew = new ArrayList<>();
		midStructureWithABag(helper, r, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
			on[0] = true;
			LOGGER.info("qa supply 1026b free: switched on, {}", QaBuilderDisruptionGameTests.state(helper, r));
		});
		helper.onEachTick(() -> {
			for (Item item : oneForOne(r.plan())) {
				int loose = loose(helper, SUPPLIES, item);
				if (loose > r.stocked().get(item) && grew.isEmpty()) {
					grew.add("tick " + helper.getTick() + ": " + loose + " " + item + " loose, stocked " + r.stocked().get(item));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(grew.isEmpty(), "an item was made: " + grew);
			helper.assertTrue(on[0], "not switched on yet");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			for (Item item : oneForOne(r.plan())) {
				int loose = loose(helper, SUPPLIES, item);
				int standing = placedOf(level, r.plan(), item);
				helper.assertTrue(loose >= r.stocked().get(item) - standing && loose <= r.stocked().get(item), item + ": " + loose + " loose after the build, "
					+ standing + " standing, stocked " + r.stocked().get(item) + " (wanted between " + (r.stocked().get(item) - standing) + " and " + r.stocked().get(item) + ")");
			}
		});
	}

	// --------------------------------------------------------------------------------- the table broken, unleveled

	/**
	 * Blueprint Table guide: a builder who hasn't leveled "may go back to having no job". A level-I builder mid-STRUCTURE,
	 * materials in his bag, has his table mined. Whatever becomes of his job, nothing is lost: 400 ticks on, what stands
	 * plus what is in the chests, bags, villagers' inventories and on the ground is exactly what was stocked, and the build
	 * is still on or its blueprint is around (in the chests or on the ground), never gone.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaSupply1026bTable"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaSupply1026bTable")
	public void anUnleveledBuildersTableMinedMidBuildLosesNoItem(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BLUEPRINT_TABLE);
		midStructureWithABag(helper, r, () -> {
			int carried = bagTotal(r.builder());
			r.level().destroyBlock(helper.absolutePos(new BlockPos(2, 2, 2)), true);
			LOGGER.info("qa supply 1026b table: mined with {} in the bag, {}", carried, QaBuilderDisruptionGameTests.state(helper, r));
			helper.runAfterDelay(400, () -> {
				assertCounted(helper, r.level(), r.plan(), SUPPLIES, r.stocked(), "400 ticks after the table was mined (bag held " + carried + ")");
				boolean on = BuildSiteManager.get(r.level()).get(r.site().id()) != null;
				int prints = blueprints(helper, SUPPLIES);
				helper.assertTrue(on || prints == 1, "the table was mined and the build is gone with " + prints + " blueprints around; builder "
					+ r.builder().getVillagerData() + " alive=" + r.builder().isAlive());
				LOGGER.info("qa supply 1026b table: 400 ticks on, site kept={}, blueprints around={}, builder {}", on, prints, r.builder().getVillagerData().getProfession());
				helper.succeed();
			});
		});
	}

	// ------------------------------------------------------------------------------------------------- Storehouse

	/** QaBuilderStockGameTests' table and Storehouse, with {@code tableCobble} by the table and {@code storeCobble} in the Storehouse. */
	private static QaBuilderStockGameTests.Builder stockRun(GameTestHelper helper, int tableCobble, int storeCobble) {
		ServerLevel level = QaBuilderStockGameTests.setup(helper, STOREHOUSE, storeCobble, 1);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH),
			new ItemStack(Items.COBBLESTONE, tableCobble));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		return b;
	}

	private static List<BlockPos> stockContainers() {
		List<BlockPos> all = new ArrayList<>(STORE_CHESTS);
		all.add(QaBuilderStockGameTests.TABLE_CHEST);
		return all;
	}

	/**
	 * B22 "exactly the build's material list, nothing more": 10 cobblestone by the table and 40 in the Storehouse, far more
	 * than the hut takes. After the hut, the table's chest and the Storehouse hold between them exactly 50 less the hut's
	 * cobblestone (and 60 less its planks): nothing kept in a bag, nothing dropped, nothing doubled.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaSupply1026bSurplus"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaSupply1026bSurplus")
	public void theStorehousesSurplusIsLeftExactly(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = stockRun(helper, 10, 40);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> stocked = Map.of(Items.COBBLESTONE, 50, Items.OAK_PLANKS, 60);
		helper.assertTrue(b.plan().materials().getOrDefault(Items.COBBLESTONE, 0) > 10, "setup: the hut takes only " + b.plan().materials().get(Items.COBBLESTONE) + " cobblestone");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: stage=" + b.site().stage() + " status=" + b.site().status() + " missing=" + b.site().missing());
			helper.assertTrue(b.plan().unfinished(level).isEmpty(), b.plan().unfinished(level).size() + " block(s) not as drawn");
			helper.assertTrue(bagTotal(b.villager()) == 0, "the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
			assertCounted(helper, level, b.plan(), stockContainers(), stocked, "after the hut");
			int inStore = 0;
			for (BlockPos p : STORE_CHESTS) {
				inStore += ((Container) helper.getBlockEntity(p)).countItem(Items.COBBLESTONE);
			}
			LOGGER.info("qa supply 1026b surplus: hut took {}, {} left in the Storehouse, {} by the table", b.plan().materials().get(Items.COBBLESTONE), inStore,
				((Container) helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST)).countItem(Items.COBBLESTONE));
		});
	}

	/**
	 * All the cobblestone (40) is in the Storehouse. Once the builder has taken some of it, the Storehouse block is mined:
	 * 300 ticks on, every cobblestone is still counted (standing, carried, in a chest or on the ground). Placed back (Jesse's
	 * again), the hut is finished and exactly 40 less its cobblestone is left.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaSupply1026bStoreMined"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaSupply1026bStoreMined")
	public void aStorehouseMinedAfterTheFirstFetchLosesNoCobblestone(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = stockRun(helper, 0, 40);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> stocked = Map.of(Items.COBBLESTONE, 40, Items.OAK_PLANKS, 60);
		long[] minedAt = {-1};
		boolean[] back = {false};
		helper.onEachTick(() -> {
			if (minedAt[0] < 0) {
				int inStore = 0;
				for (BlockPos p : STORE_CHESTS) {
					inStore += ((Container) helper.getBlockEntity(p)).countItem(Items.COBBLESTONE);
				}
				if (inStore < 40) {
					level.destroyBlock(helper.absolutePos(STOREHOUSE), true);
					minedAt[0] = helper.getTick();
					LOGGER.info("qa supply 1026b store mined: at {} with {} left in it, bag {}", minedAt[0], inStore, ModAttachments.BUILDER_BAG.getOrCreate(b.villager()).stacks());
				}
				return;
			}
			if (!back[0] && helper.getTick() - minedAt[0] >= 300) {
				assertCounted(helper, level, b.plan(), stockContainers(), stocked, "300 ticks after the Storehouse was mined");
				helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
				ensurePoi(level, helper.absolutePos(STOREHOUSE));
				((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
				Village.forget(b.villager());
				back[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the Storehouse isn't back yet");
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: stage=" + b.site().stage() + " status=" + b.site().status() + " missing=" + b.site().missing());
			helper.assertTrue(b.plan().unfinished(level).isEmpty(), b.plan().unfinished(level).size() + " block(s) not as drawn");
			assertCounted(helper, level, b.plan(), stockContainers(), stocked, "after the hut");
		});
	}

	/**
	 * B132: two Builders sharing a Storehouse each take what their own build needs. Jesse's two builders at two tables 16
	 * blocks apart, each with planks by him and no cobblestone, build a test hut each from one Storehouse holding both huts'
	 * cobblestone and 3 more: both huts are finished, neither waits forever on what the other carried, and exactly 3 are left.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSupply1026bTwoShare"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSupply1026bTwoShare")
	public void twoBuildersSharingAStorehouseBothFinishAndLeaveTheSpare(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder first = stockRun(helper, 0, 0);
		ServerLevel level = helper.getLevel();
		BlockPos table2 = new BlockPos(18, 2, 2);
		BlockPos chest2 = new BlockPos(18, 2, 3);
		BlockPos hut2 = new BlockPos(15, 2, 12);
		helper.setBlock(table2, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(table2));
		helper.setBlock(chest2, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(chest2), new ItemStack(Items.OAK_PLANKS, 60), new ItemStack(Items.TORCH), new ItemStack(Items.OAK_DOOR));
		Villager v2 = helper.spawn(EntityType.VILLAGER, table2.east());
		Builders.employ(level, v2, helper.absolutePos(table2));
		ModAttachments.BUILDER_EMPLOYER.set(v2, new Employer(JESSE, "Jesse"));
		BuildSite site2 = Builders.start(level, v2, JESSE, "Jesse", TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut2), Rotation.NONE, Mirror.NONE));
		BuildPlan plan2 = site2.plan(level);
		if (plan2 == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		int need = first.plan().materials().get(Items.COBBLESTONE) + plan2.materials().get(Items.COBBLESTONE);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(STORE_CHESTS.get(0)), new ItemStack(Items.COBBLESTONE, need + 3));
		Village.forget(first.villager());
		Village.forget(v2);
		Leftovers.after(helper, () -> {
			for (BuildSite s : List.of(first.site(), site2)) {
				if (BuildSiteManager.get(level).get(s.id()) != null) {
					BuildSiteManager.get(level).remove(s.id());
				}
			}
		});
		LOGGER.info("qa supply 1026b two share: the huts take {} cobblestone, the Storehouse holds {}", need, need + 3);
		List<BlockPos> all = stockContainers();
		all.add(chest2);
		helper.succeedWhen(() -> {
			for (BuildSite s : List.of(first.site(), site2)) {
				helper.assertTrue(BuildSiteManager.get(level).get(s.id()) == null, "still building at " + s.bench() + ": stage=" + s.stage() + " status=" + s.status() + " missing=" + s.missing());
			}
			helper.assertTrue(first.plan().unfinished(level).isEmpty() && plan2.unfinished(level).isEmpty(), "a hut isn't as drawn: "
				+ first.plan().unfinished(level).size() + " and " + plan2.unfinished(level).size() + " blocks wrong");
			int standing = placedOf(level, first.plan(), Items.COBBLESTONE) + placedOf(level, plan2, Items.COBBLESTONE);
			int loose = loose(helper, all, Items.COBBLESTONE);
			helper.assertTrue(loose == 3 && standing == need, "after both huts " + standing + " cobblestone stand (want " + need + ") and " + loose + " are loose (want 3)");
		});
	}
}
