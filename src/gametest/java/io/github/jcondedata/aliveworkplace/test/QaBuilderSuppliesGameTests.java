package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1026-0338) of a Builder's supplies and his bag, from docs/wiki/builder.md "Materials": "A Builder takes what
 * it needs from the chests and barrels within {@code supplyRadius} of the bench (and 4 blocks up or down), carries up to
 * 27 stacks in its bag, never more of a thing than the stretch of work ahead still needs ..., and empties the bag at the
 * chests when it finishes"; "Each block costs an item (MaterialRules; some blocks you cannot hold in survival use their
 * base material)"; config-switches.md: {@code supplyRadius} 8 (2 to 32), "Chests and barrels within this many blocks of a
 * workstation are that villager's supply chests". The 8-block reach and the 4-up-or-down height are QaBuilderLimitsGameTests';
 * the emptied supplies are QaBuilderDisruptionGameTests'.
 */
public class QaBuilderSuppliesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	/** QaBuilderDisruptionGameTests' supplies: its chest and the four barrels beside it. */
	private static final List<BlockPos> SUPPLIES = List.of(new BlockPos(2, 2, 4), new BlockPos(1, 2, 4), new BlockPos(3, 2, 4),
		new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
	/** Spare planks a player stocked over the hut's list, in the last barrel: more than the hut will ever take. */
	private static final int SPARE_PLANK_STACKS = 4;

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	// ---------------------------------------------------------------------------------------- supplyRadius from the config

	/**
	 * config-switches.md: {@code supplyRadius} "Chests and barrels within this many blocks of a workstation are that
	 * villager's supply chests". The owner sets it to 4 (the config file read and applied, as at a server start): 10 planks
	 * in a chest 4 blocks east and 5 cobblestone in a BARREL 4 blocks north are counted; a torch 5 blocks west and a door 5
	 * east (and 2 north) are not, though both are well inside the default 8. The Book and Quill used on the builder:
	 * "45 × Oak Planks (of 55)", "20 × Cobblestone (of 25)", the torch and the door unticked.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaSuppliesRadius"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaSuppliesRadius")
	public void aSupplyRadiusOfFourFromTheConfigCountsChestsAndBarrelsAtFourAndNotFive(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		WorkplaceConfig.parse("{\"supplyRadius\": 4}").apply();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply(); // the defaults every other test runs with (as ConfigGameTests puts them back)
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		helper.assertTrue(SupplyContainers.RADIUS == 4, "the config's supplyRadius 4 wasn't put into effect: " + SupplyContainers.RADIUS);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(12, 2, 12);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		record Stock(BlockPos at, net.minecraft.world.level.block.Block block, ItemStack items) {
		}
		List<Stock> stocks = List.of(
			new Stock(table.east(4), Blocks.CHEST, new ItemStack(Items.OAK_PLANKS, 10)),
			new Stock(table.north(4), Blocks.BARREL, new ItemStack(Items.COBBLESTONE, 5)),
			new Stock(table.west(5), Blocks.CHEST, new ItemStack(Items.TORCH)),
			new Stock(table.east(5).north(2), Blocks.BARREL, new ItemStack(Items.OAK_DOOR)));
		for (Stock s : stocks) {
			helper.setBlock(s.at(), s.block());
			((Container) helper.getBlockEntity(s.at())).setItem(0, s.items().copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, table.south().east());
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(table);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qasR"));
		ServerPlayer p = c.player();
		ItemStack blueprint = QaBuilderLimitsGameTests.placedHut(level, bench, new Vec3i(0, 2, 7));
		p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "the builder took no build: " + texts(c.seen()));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		var content = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, "no list was written: " + p.getMainHandItem() + ", saw " + texts(c.seen()));
		String pages = String.join("\n", content.pages().stream().map(page -> page.raw().getString()).toList());
		LOGGER.info("qa supplies radius: {}", pages.replace('\n', '|'));
		for (String line : List.of("☐ 45 × Oak Planks (of 55)", "☐ 20 × Cobblestone (of 25)", "☐ 1 × Torch", "☐ 1 × Oak Door")) {
			helper.assertTrue(pages.contains(line), "with supplyRadius 4 the list lacks \"" + line + "\": " + pages);
		}
		helper.succeed();
	}

	private static void forgetSitesOf(GameTestHelper helper, Villager builder) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
			for (var site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- the bag on a build

	/** Puts SPARE_PLANK_STACKS stacks of planks in the last barrel and 3 oak logs where the hut's first planks go. */
	private static List<BlockPos> stockSpareAndLogs(GameTestHelper helper, QaBuilderDisruptionGameTests.Run r) {
		Container barrel = helper.getBlockEntity(SUPPLIES.get(SUPPLIES.size() - 1));
		int slot = 26;
		for (int i = 0; i < SPARE_PLANK_STACKS; i++) {
			helper.assertTrue(barrel.getItem(slot).isEmpty(), "setup: the barrel's slot " + slot + " is taken");
			barrel.setItem(slot--, new ItemStack(Items.OAK_PLANKS, 64));
		}
		List<BlockPos> logs = new ArrayList<>();
		for (BuildPlan.Step s : r.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			if (s.state().is(Blocks.OAK_PLANKS) && logs.size() < 3 && r.level().getBlockState(s.pos()).isAir()) {
				r.level().setBlockAndUpdate(s.pos(), Blocks.OAK_LOG.defaultBlockState());
				logs.add(s.pos().immutable());
			}
		}
		helper.assertTrue(logs.size() == 3, "setup: only " + logs.size() + " plank places for the logs");
		return logs;
	}

	private static int stocked(QaBuilderDisruptionGameTests.Run r, Item item) {
		return r.stocked().getOrDefault(item, 0) + (item == Items.OAK_PLANKS ? SPARE_PLANK_STACKS * 64 : 0);
	}

	private static int inSupplies(GameTestHelper helper, Item item) {
		int n = 0;
		for (BlockPos p : SUPPLIES) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, QaBuilderDisruptionGameTests.around(helper))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** {@code item} in the supplies, in every villager's bag near the test and on the ground. */
	private static int loose(GameTestHelper helper, Item item) {
		int n = inSupplies(helper, item) + onGround(helper, item);
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, QaBuilderDisruptionGameTests.around(helper))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	private static String bagOf(Villager v) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(v);
		return bag.stacks().size() + " stacks " + bag.stacks();
	}

	/**
	 * The wiki: the builder "carries up to 27 stacks in its bag, never more of a thing than the stretch of work ahead still
	 * needs ..., and empties the bag at the chests when it finishes". The hut is built for real from its chest and barrels,
	 * with 4 stacks of planks more than it needs stocked and 3 oak logs standing where planks go (cleared into the bag).
	 * On every tick the bag holds at most 27 stacks, and the planks in it plus the planks standing never pass what the hut
	 * takes (no plank carried that the work ahead won't use). At the end the hut is as drawn, the bag is empty and the spare
	 * planks and the 3 logs are back in the chests (none on the ground, none made or lost).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaSuppliesBag"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaSuppliesBag")
	public void theBagNeverCarriesMoreThanTheWorkAheadNeedsAndIsEmptiedIntoTheChestsAtTheEnd(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		stockSpareAndLogs(helper, r);
		int hutPlanks = r.plan().materials().getOrDefault(Items.OAK_PLANKS, 0);
		List<String> broke = new ArrayList<>();
		int[] most = new int[2];
		helper.onEachTick(() -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(r.builder());
			int stacks = bag.stacks().size();
			int carried = bag.count(Items.OAK_PLANKS);
			int standing = QaBuilderDisruptionGameTests.standing(r, Blocks.OAK_PLANKS);
			most[0] = Math.max(most[0], stacks);
			most[1] = Math.max(most[1], carried);
			if (broke.isEmpty() && stacks > BuilderBag.SLOTS) {
				broke.add("the bag held " + stacks + " stacks: " + bagOf(r.builder()));
			}
			if (broke.isEmpty() && carried + standing > hutPlanks) {
				broke.add("the bag carried " + carried + " planks with " + standing + " standing, the hut takes " + hutPlanks + "; "
					+ QaBuilderDisruptionGameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.isEmpty(), broke.isEmpty() ? "" : broke.get(0));
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).isEmpty(), "the build is done but the bag isn't empty: "
				+ bagOf(r.builder()));
			helper.assertTrue(most[1] > 0, "the bag never carried a plank (most " + most[1] + ")");
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int want = stocked(r, e.getKey()) - e.getValue();
				int got = inSupplies(helper, e.getKey());
				helper.assertTrue(got == want, e.getKey() + ": " + got + " back in the chests, wanted " + want + " (stocked " + stocked(r, e.getKey())
					+ ", the hut takes " + e.getValue() + "), " + onGround(helper, e.getKey()) + " on the ground");
			}
			int logs = inSupplies(helper, Items.OAK_LOG);
			helper.assertTrue(logs == 3, "the 3 cleared logs: " + logs + " in the chests, " + onGround(helper, Items.OAK_LOG) + " on the ground");
			LOGGER.info("qa supplies bag: most {} stacks, most {} planks carried (hut {}), {}", most[0], most[1], hutPlanks,
				QaBuilderDisruptionGameTests.state(helper, r));
		});
	}

	/**
	 * The wiki's "carries up to 27 stacks in its bag" on a build bigger than a bag: the Starter Cottage, built for real
	 * from the chest and barrels by the bench (its whole list plus 2 of each stocked). On no tick does the bag hold more
	 * than 27 stacks; at the end the cottage is as drawn, the bag is empty and the supplies hold exactly the spare.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaSuppliesBigBag"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaSuppliesBigBag")
	public void onABuildBiggerThanTheBagItNeverHoldsMoreThan27Stacks(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH,
			io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.STARTER_COTTAGE.id(), new BlockPos(9, 2, 9));
		int stacksNeeded = 0;
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			stacksNeeded += (e.getValue() + e.getKey().getDefaultMaxStackSize() - 1) / e.getKey().getDefaultMaxStackSize();
		}
		int needed = stacksNeeded;
		List<String> broke = new ArrayList<>();
		int[] most = new int[1];
		helper.onEachTick(() -> {
			int stacks = ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).stacks().size();
			most[0] = Math.max(most[0], stacks);
			if (broke.isEmpty() && stacks > BuilderBag.SLOTS) {
				broke.add("at tick " + helper.getTick() + " the bag held " + stacks + " stacks: " + bagOf(r.builder()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke.isEmpty(), broke.isEmpty() ? "" : broke.get(0));
			helper.assertTrue(needed > BuilderBag.SLOTS, "setup: the cottage takes only " + needed + " stacks, not more than a bag");
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).isEmpty(), "the build is done but the bag isn't empty: "
				+ bagOf(r.builder()));
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = inSupplies(helper, e.getKey());
				helper.assertTrue(got == 2, e.getKey() + ": " + got + " back in the supplies, wanted the spare 2 (" + onGround(helper, e.getKey())
					+ " on the ground)");
			}
			LOGGER.info("qa supplies big bag: the cottage takes {} stacks, the bag held at most {}", needed, most[0]);
		});
	}

	/**
	 * The same build, but a player fills every free slot of the bench's chest and barrels with feathers once the hut's
	 * structure is up (and keeps them full), so when the builder finishes there is no room to empty his bag into. The
	 * wiki doesn't say where it goes then, so this asserts only that nothing vanishes: the build ends, and the 3 cleared
	 * logs and every spare plank are still in the chests, in a bag or on the ground for a player to pick up.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaSuppliesFull"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaSuppliesFull")
	public void whenTheChestsAreFullAtTheEndNothingTheBuilderCarriedVanishes(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		stockSpareAndLogs(helper, r);
		boolean[] filled = new boolean[1];
		helper.onEachTick(() -> {
			BuildPlan.Stage stage = r.site().stage();
			if (stage == BuildPlan.Stage.CLEAR || stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.STRUCTURE) {
				return;
			}
			if (!filled[0]) {
				LOGGER.info("qa supplies full: chests filled at {}, bag {}", stage, bagOf(r.builder()));
			}
			filled[0] = true;
			for (BlockPos p : SUPPLIES) {
				if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
					for (int i = 0; i < c.getContainerSize(); i++) {
						if (c.getItem(i).isEmpty()) {
							c.setItem(i, new ItemStack(Items.FEATHER, 64));
						}
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filled[0], "the chests were never filled: " + QaBuilderDisruptionGameTests.state(helper, r));
			QaBuilderDisruptionGameTests.assertFinished(helper, r, List.of());
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int want = stocked(r, e.getKey()) - e.getValue();
				int got = loose(helper, e.getKey());
				helper.assertTrue(got == want, e.getKey() + ": " + got + " in the chests, bags and on the ground, wanted " + want);
			}
			int logs = loose(helper, Items.OAK_LOG);
			helper.assertTrue(logs == 3, "the 3 cleared logs: " + logs + " left (" + inSupplies(helper, Items.OAK_LOG) + " in the chests, "
				+ onGround(helper, Items.OAK_LOG) + " on the ground), bag " + bagOf(r.builder()));
			LOGGER.info("qa supplies full: logs {} in chests, {} on the ground; bag {}", inSupplies(helper, Items.OAK_LOG),
				onGround(helper, Items.OAK_LOG), bagOf(r.builder()));
		});
	}

	/**
	 * Mid-STRUCTURE, with planks in his bag, a player breaks the bench's chest (its contents spill on the ground, as
	 * vanilla does). What the builder already carried is his: from then on no plank is ever lost (standing + in his bag
	 * + in the barrels + on the ground stays what was stocked), and he goes on placing planks after the chest is gone.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaSuppliesChestBroken"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaSuppliesChestBroken")
	public void aBenchChestBrokenMidFetchLosesNothingTheBuilderCarried(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		int total = r.stocked().get(Items.OAK_PLANKS);
		long[] brokeAt = {-1};
		int[] standingAtBreak = {0};
		int[] carriedAtBreak = {0};
		List<String> lost = new ArrayList<>();
		helper.onEachTick(() -> {
			int carried = QaBuilderDisruptionGameTests.bag(r.builder(), Items.OAK_PLANKS);
			int standing = QaBuilderDisruptionGameTests.standing(r, Blocks.OAK_PLANKS);
			if (brokeAt[0] < 0) {
				if (r.site().stage() == BuildPlan.Stage.STRUCTURE && carried >= 8 && standing >= 1) {
					brokeAt[0] = helper.getTick();
					standingAtBreak[0] = standing;
					carriedAtBreak[0] = carried;
					r.level().destroyBlock(helper.absolutePos(SUPPLIES.get(0)), true); // the chest drops itself and what it held
					LOGGER.info("qa supplies chest broken: carried {}, standing {}, {}", carried, standing, QaBuilderDisruptionGameTests.state(helper, r));
				}
				return;
			}
			int now = standing + loose(helper, Items.OAK_PLANKS);
			if (lost.isEmpty() && now != total) {
				lost.add("at tick " + helper.getTick() + " " + now + " planks are standing or loose, " + total + " were stocked: bag " + carried
					+ ", standing " + standing + ", ground " + onGround(helper, Items.OAK_PLANKS) + "; " + QaBuilderDisruptionGameTests.state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokeAt[0] >= 0, "setup: the chest was never broken: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(lost.isEmpty(), lost.isEmpty() ? "" : lost.get(0));
			helper.assertTrue(helper.getTick() - brokeAt[0] >= 400, "waiting");
			int standing = QaBuilderDisruptionGameTests.standing(r, Blocks.OAK_PLANKS);
			helper.assertTrue(standing > standingAtBreak[0], "no plank went up after the chest was broken (he carried " + carriedAtBreak[0]
				+ "): " + standing + " standing, " + QaBuilderDisruptionGameTests.state(helper, r));
		});
	}

	// ------------------------------------------------------------------------------------- what each block costs to build

	/**
	 * The wiki: "Each block costs an item (MaterialRules; some blocks you cannot hold in survival use their base
	 * material)". Blocks a survival player can't hold cost the item a player uses to make them: a wall torch a torch,
	 * redstone wire redstone dust, farmland and a dirt path dirt, a lit furnace a furnace, an oak wall sign an oak sign,
	 * a carrot crop a carrot, wheat seeds' crop wheat seeds, tripwire string, and a potted cactus a flower pot and a cactus.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blocksNobodyCanHoldCostTheItemTheyAreMadeFrom(GameTestHelper helper) {
		record Cost(BlockState state, List<Item> items) {
		}
		List<Cost> costs = List.of(
			new Cost(Blocks.WALL_TORCH.defaultBlockState(), List.of(Items.TORCH)),
			new Cost(Blocks.REDSTONE_WIRE.defaultBlockState(), List.of(Items.REDSTONE)),
			new Cost(Blocks.FARMLAND.defaultBlockState(), List.of(Items.DIRT)),
			new Cost(Blocks.DIRT_PATH.defaultBlockState(), List.of(Items.DIRT)),
			new Cost(Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), List.of(Items.FURNACE)),
			new Cost(Blocks.OAK_WALL_SIGN.defaultBlockState(), List.of(Items.OAK_SIGN)),
			new Cost(Blocks.CARROTS.defaultBlockState(), List.of(Items.CARROT)),
			new Cost(Blocks.WHEAT.defaultBlockState(), List.of(Items.WHEAT_SEEDS)),
			new Cost(Blocks.TRIPWIRE.defaultBlockState(), List.of(Items.STRING)),
			new Cost(Blocks.POTTED_CACTUS.defaultBlockState(), List.of(Items.FLOWER_POT, Items.CACTUS)));
		List<String> wrong = new ArrayList<>();
		for (Cost c : costs) {
			List<MaterialRules.Requirement> got = MaterialRules.requirements(c.state(), null);
			List<Item> items = got.stream().map(MaterialRules.Requirement::item).sorted(java.util.Comparator.comparing(Item::toString)).toList();
			List<Item> want = c.items().stream().sorted(java.util.Comparator.comparing(Item::toString)).toList();
			if (!items.equals(want) || got.stream().anyMatch(q -> q.count() != 1)) {
				wrong.add(c.state() + " costs " + got + ", wanted one each of " + want);
			}
		}
		if (!wrong.isEmpty()) {
			throw new GameTestAssertException(wrong.size() + " block(s) cost the wrong item: " + String.join("; ", wrong));
		}
		helper.succeed();
	}
}
