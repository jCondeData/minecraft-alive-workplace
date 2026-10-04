package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 23.5, self-healing supply: builders use the village storehouse (a porter's chests) without being told, a full bag
 * goes into the storehouse rather than on the ground, the storehouse counts while its porter is out, and what the
 * village can't supply shows on the requests board by itself.
 */
public class SelfHealingSupplyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos STOREHOUSE = new BlockPos(24, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(24, 2, 4);
	private static final BlockPos STORE_CHEST_2 = new BlockPos(26, 2, 4);

	private record Setup(ServerLevel level, Villager builder, Villager porter, BuildSite site, BuildPlan plan) {
	}

	/** Half the hut's materials by the bench, half in the storehouse: the builder finds the rest there and finishes. */
	//$ gametest_ticks_batch HUGE '3000' '"supplySplit"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "supplySplit")
	public void aBuilderFetchesWhatItsChestsLackFromTheStorehouse(GameTestHelper helper) {
		Setup s = setup(helper, true);
		fill(helper.getBlockEntity(CHEST), new ItemStack(Items.COBBLESTONE, 12), new ItemStack(Items.OAK_PLANKS, 25), new ItemStack(Items.OAK_DOOR));
		fill(helper.getBlockEntity(STORE_CHEST), new ItemStack(Items.COBBLESTONE, 13), new ItemStack(Items.OAK_PLANKS, 30), new ItemStack(Items.TORCH));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == 0 && store.countItem(Items.OAK_PLANKS) == 0 && store.countItem(Items.TORCH) == 0,
				"the storehouse still holds cobblestone " + store.countItem(Items.COBBLESTONE) + ", planks " + store.countItem(Items.OAK_PLANKS));
		});
	}

	/**
	 * A builder with no chests of its own and a bag full of rubble: it empties the bag into the storehouse's chests,
	 * none of it on the ground, and builds from the storehouse.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"supplyFullBag"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "supplyFullBag")
	public void aFullBagGoesIntoTheStorehouseNotOnTheGround(GameTestHelper helper) {
		Setup s = setup(helper, false);
		fill(helper.getBlockEntity(STORE_CHEST), hutMaterials());
		helper.setBlock(STORE_CHEST_2, Blocks.CHEST);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		for (int i = 0; i < BuilderBag.SLOTS; i++) {
			bag.add(new ItemStack(Items.DIRT, 64));
		}
		helper.assertTrue(bag.freeSlots() == 0, "the bag isn't full: " + bag.freeSlots() + " free");
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int dirt = ((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.DIRT)
				+ ((Container) helper.getBlockEntity(STORE_CHEST_2)).countItem(Items.DIRT) + bag.count(Items.DIRT);
			helper.assertTrue(dirt == BuilderBag.SLOTS * 64, "dirt in the storehouse and bag: " + dirt + " of " + BuilderBag.SLOTS * 64);
			// (The finished build's blueprint comes back on the ground by the bench: it has no chest to go in.)
			List<ItemEntity> dropped = s.level().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(Items.DIRT));
			helper.assertTrue(dropped.isEmpty(), "dirt on the ground: " + dropped.stream().map(e -> e.getItem().toString()).toList());
		});
	}

	/** Its chest is full when the build is done: what's left in the bag goes into the storehouse, not on the ground. */
	//$ gametest_ticks_batch HUGE '3000' '"supplyLeftovers"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "supplyLeftovers")
	public void leftoversGoToTheStorehouseWhenItsChestIsFull(GameTestHelper helper) {
		Setup s = setup(helper, true);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.STONE, 64));
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		for (ItemStack stack : hutMaterials()) {
			bag.add(stack.copy());
		}
		bag.add(new ItemStack(Items.COBBLESTONE, 40)); // 40 more than the hut needs
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int stored = ((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.COBBLESTONE);
			helper.assertTrue(stored == 40, "the storehouse got " + stored + " of the 40 cobblestone left over");
			List<ItemEntity> dropped = s.level().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(Items.COBBLESTONE));
			helper.assertTrue(dropped.isEmpty(), "cobblestone on the ground: " + dropped.size());
		});
	}

	/**
	 * Its own chest holds all the planks, the door and the torch; the storehouse the cobblestone and planks of its own: at
	 * the storehouse for cobblestone the builder takes no planks (its chest has them), so the storehouse's stay for others.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"supplyFairShare"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "supplyFairShare")
	public void atTheStorehouseABuilderTakesOnlyWhatItsChestsLack(GameTestHelper helper) {
		Setup s = setup(helper, true);
		fill(helper.getBlockEntity(CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		fill(helper.getBlockEntity(STORE_CHEST), new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 40));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int planks = ((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.OAK_PLANKS);
			helper.assertTrue(planks == 40, "the builder took " + (40 - planks) + " planks from the storehouse though its chest had them");
		});
	}

	/**
	 * What a builder carries counts toward its builds: its chest keeps back only the rest, so another builder may take
	 * the surplus (23.5: a builder's storehouse surplus sat locked in its chests while another waited).
	 */
	//$ gametest_ticks_batch HUGE '100' '"supplyReserved"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "supplyReserved")
	public void whatABuilderCarriesIsntKeptBackTwice(GameTestHelper helper) {
		Setup s = setup(helper, true);
		s.builder().setNoAi(true);
		BlockPos bench = helper.absolutePos(BENCH);
		java.util.Map<net.minecraft.world.item.Item, Integer> before = Builders.reservedAt(s.level(), bench, null);
		helper.assertTrue(before.getOrDefault(Items.OAK_PLANKS, 0) == 55, "reserved before: " + before);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(s.builder());
		bag.add(new ItemStack(Items.OAK_PLANKS, 40));
		bag.add(new ItemStack(Items.COBBLESTONE, 30));
		java.util.Map<net.minecraft.world.item.Item, Integer> after = Builders.reservedAt(s.level(), bench, null);
		helper.assertTrue(after.getOrDefault(Items.OAK_PLANKS, 0) == 15, "planks kept back with 40 carried: " + after);
		helper.assertTrue(!after.containsKey(Items.COBBLESTONE), "cobblestone kept back though it carries more than enough: " + after);
		helper.assertTrue(after.getOrDefault(Items.OAK_DOOR, 0) == 1, "the door: " + after);
		helper.succeed();
	}

	/**
	 * Two storehouses: the nearer one holds what a second builder, who can reach only that one, needs; the farther one
	 * what the first builder needs. The first builder leaves the nearer one's to the second, and both builds finish.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"supplyTwoStorehouses"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "supplyTwoStorehouses")
	public void aBuilderLeavesAStorehouseToTheBuilderWhoCanReachOnlyThatOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 16);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		BlockPos near = new BlockPos(3, 2, 14);
		BlockPos far = new BlockPos(26, 2, 14);
		for (BlockPos store : List.of(near, far)) {
			helper.setBlock(store, ModBlocks.STOREHOUSE);
			helper.setBlock(store.south(2), Blocks.CHEST);
			Porters.employ(level, helper.spawn(EntityType.VILLAGER, store.north()), helper.absolutePos(store));
		}
		fill(helper.getBlockEntity(near.south(2)), hutMaterials());
		fill(helper.getBlockEntity(far.south(2)), hutMaterials());
		Villager first = builder(helper, new BlockPos(14, 2, 14), new BlockPos(17, 2, 20));
		Villager second = builder(helper, new BlockPos(2, 2, 2), new BlockPos(6, 2, 2));
		helper.assertTrue(Village.stashes(level, second, helper.absolutePos(new BlockPos(2, 2, 2)), null).size() == 1,
			"the second builder should reach only the near storehouse");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).all().stream().noneMatch(site -> first.getUUID().equals(site.builder())),
				"the first builder isn't done");
			helper.assertTrue(BuildSiteManager.get(level).all().stream().noneMatch(site -> second.getUUID().equals(site.builder())),
				"the second builder isn't done: " + BuildSiteManager.get(level).all().stream().filter(site -> second.getUUID().equals(site.builder()))
					.map(site -> site.status() + " missing " + site.missing()).toList());
		});
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench, BlockPos hut) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(level, builder, helper.absolutePos(bench));
		Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE));
		return builder;
	}

	/** The storehouse still counts as the village's while its porter is out far beyond it (it once dropped out). */
	//$ gametest_ticks_batch HUGE '100' '"supplyPorterAway"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "supplyPorterAway")
	public void theStorehouseCountsWhileItsPorterIsOut(GameTestHelper helper) {
		// A small village (8 blocks) so "far out" fits in the test area: the storehouse 6 blocks from the bench, its
		// porter 31 blocks off (beyond the 8 + 16 the search used to look), all inside the area.
		Leftovers.clear(helper);
		Leftovers.village(helper, 8);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(8, 2, 2);
		BlockPos chest = new BlockPos(12, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(28, 2, 20));
		porter.setNoAi(true);
		Porters.employ(level, porter, helper.absolutePos(storehouse));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(porter.blockPosition().distSqr(helper.absolutePos(BENCH)) > 24 * 24, "the porter isn't far out: "
				+ helper.relativePos(porter.blockPosition()));
			List<Village.Stash> stashes = Village.stashes(level, builder, helper.absolutePos(BENCH), null);
			helper.assertTrue(stashes.stream().anyMatch(st -> st.job() == ModVillagers.PORTER && st.chests().contains(helper.absolutePos(chest))),
				"the storehouse isn't among the builder's village stashes while its porter is out: " + stashes);
			helper.succeed();
		});
	}

	/**
	 * Its chests and the storehouse together hold 15 of the 25 cobblestone: the builder takes all 15, then waits, and the
	 * requests board asks for exactly the 10 nobody has, without anyone posting it.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"supplyRequests"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "supplyRequests")
	public void whatTheVillageLacksGoesOnTheRequestsBoard(GameTestHelper helper) {
		Setup s = setup(helper, true);
		fill(helper.getBlockEntity(CHEST), new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		fill(helper.getBlockEntity(STORE_CHEST), new ItemStack(Items.COBBLESTONE, 5));
		helper.succeedWhen(() -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "not waiting yet: " + s.site().status());
			helper.assertTrue(((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.COBBLESTONE) == 0, "the storehouse's cobblestone wasn't taken");
			List<Requests.Request> requests = Requests.of(s.level(), s.builder());
			Requests.Request cobble = requests.stream().filter(r -> r.item() == Items.COBBLESTONE).findFirst()
				.orElseThrow(() -> new GameTestAssertException("no cobblestone request: " + requests));
			helper.assertTrue(cobble.count() == 10, "the board asks for " + cobble.count() + " cobblestone, not 10");
			helper.assertTrue(requests.size() == 1, "more requests than the cobblestone: " + requests.size());
		});
	}

	/** With the village off (radius 0, the config switch), the storehouse is nobody's: the builder waits by its bench. */
	//$ gametest_ticks_batch HUGE '800' '"supplyVillageOff"'
	@GameTest(template = HUGE, timeoutTicks = 800, batch = "supplyVillageOff")
	public void withTheVillageOffTheStorehouseIsNotUsed(GameTestHelper helper) {
		Setup s = setup(helper, true);
		Village.RADIUS = 0;
		fill(helper.getBlockEntity(STORE_CHEST), hutMaterials());
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should wait, not " + s.site().status());
			helper.assertTrue(((Container) helper.getBlockEntity(STORE_CHEST)).countItem(Items.COBBLESTONE) == 25, "it took from the storehouse");
			helper.succeed();
		});
	}

	private static Setup setup(GameTestHelper helper, boolean ownChest) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		Porters.employ(level, porter, helper.absolutePos(STOREHOUSE));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		if (ownChest) {
			helper.setBlock(CHEST, Blocks.CHEST);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + TEST_HUT);
		}
		return new Setup(level, builder, porter, site, plan);
	}

	private static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage=" + s.site().stage()
			+ " status=" + s.site().status() + " missing=" + s.site().missing());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static ItemStack[] hutMaterials() {
		return new ItemStack[]{new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
	}

	private static void fill(Container container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
	}
}
