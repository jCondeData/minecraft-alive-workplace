package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA, from ROADMAP 37.17 and the furniture-and-storage and builder wiki pages, plain Fabric (the vanilla builds): the
 * Storage Hall and the Terminal Nook "are each a Storehouse: it counts as one for the village, porters and builders use
 * it". A finished Nook feeds another builder's build with every item accounted for; the board counts each item of the
 * build's chests once; a chest of a finished Hall broken by a player leaves the rest serving with nothing duplicated; the
 * Nook's build survives a restart mid-build; a builder counts the hall up to the village's reach and not a block beyond.
 */
public class QaStorageHallGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** Where each build's Storehouse block stands in its template (StorageHallGameTests). */
	private static final BlockPos NOOK_STOREHOUSE = new BlockPos(2, 1, 4);
	private static final BlockPos HALL_STOREHOUSE = new BlockPos(6, 1, 5);
	private static final int NOOK_STORES = 8;
	private static final int HALL_STORES = 31;
	/** Each material of the test hut is put in the hall's chests this many over what the hut takes. */
	private static final int EXTRA = 3;

	// ------------------------------------------------------------------------------------------------ the Nook, built

	/**
	 * A builder finishes the Terminal Nook; the hut's materials (3 over of each) then go in the Nook's chests and a
	 * second builder, at a table 24 blocks off with no chest of its own, builds the hut from them. Every item is
	 * accounted for: what's left in the Nook, his bag and on the ground is exactly the 3 over of each.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qaNookFeeds"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qaNookFeeds")
	public void aFinishedNookFeedsASecondBuildersHutWithNothingLostOrDuplicated(GameTestHelper helper) {
		ServerLevel level = setup(helper, 48);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager first = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, first, helper.absolutePos(bench));
		StarterBlueprints.Entry entry = StarterBlueprints.TERMINAL_NOOK;
		BlueprintData.Placement placement = placement(helper, new BlockPos(9, 2, 3));
		BuildSite nook = Builders.start(level, first, null, entry.id(), placement);
		BuildPlan nookPlan = nook.plan(level);
		helper.assertTrue(nookPlan != null, "no blueprint " + entry.id());
		stockBarrels(helper, nookPlan.materials());
		BlockPos board = placement.origin().offset(NOOK_STOREHOUSE);
		BoundingBox box = box(placement, entry);
		Object[] second = new Object[3]; // villager, site, plan
		Map<Item, Integer> hutMaterials = new HashMap<>();
		helper.succeedWhen(() -> {
			if (second[0] == null) {
				helper.assertTrue(BuildSiteManager.get(level).get(nook.id()) == null, "the Nook is still building: " + nook.stage() + " " + nook.status()
					+ " missing " + nook.missing());
				helper.assertTrue(nook.skipped() == 0, "the Nook skipped " + nook.skipped());
				List<BlockPos> store = storeIn(level, board, box);
				helper.assertTrue(store.size() == NOOK_STORES, "the finished Nook's store has " + store.size() + " blocks, wanted " + NOOK_STORES);
				// the second builder, at his own table across the area
				BlockPos table = new BlockPos(26, 2, 26);
				helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
				ensurePoi(level, helper.absolutePos(table));
				Villager builder = helper.spawn(EntityType.VILLAGER, table.west());
				Builders.employ(level, builder, helper.absolutePos(table));
				BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(19, 2, 19)));
				BuildPlan plan = site.plan(level);
				helper.assertTrue(plan != null, "no blueprint " + TEST_HUT);
				hutMaterials.putAll(plan.materials());
				spread(level, store, hutMaterials, EXTRA);
				Village.forget(builder);
				Component status = Builders.statusText(level, site, builder);
				helper.assertTrue(site.missing().isEmpty(), "the second builder doesn't count the finished Nook: needs " + site.missing() + "; " + status.getString());
				second[0] = builder;
				second[1] = site;
				second[2] = plan;
				throw new GameTestAssertException("the hut is just started");
			}
			Villager builder = (Villager) second[0];
			BuildSite site = (BuildSite) second[1];
			BuildPlan plan = (BuildPlan) second[2];
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut is still building: " + site.stage() + " " + site.status()
				+ " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the hut wrong");
			helper.assertTrue(site.skipped() == 0, "the hut skipped " + site.skipped());
			assertAccounted(helper, level, storeIn(level, board, box), builder, hutMaterials, EXTRA);
		});
	}

	// ------------------------------------------------------------------------------------------- the board, once

	/**
	 * The finished Nook (its blocks set as the build sets them) with a different item in each of its 8 chests, a
	 * different number of each: the board's stock line says 8 chests and the true total, and taking all of an item
	 * through the store gives exactly what was put in (a chest read through both halves of a double chest would give it
	 * twice).
	 */
	//$ gametest_batch HUGE_AREA '"qaNookBoard"'
	@GameTest(template = HUGE_AREA, batch = "qaNookBoard")
	public void theNooksBoardCountsEachItemOnce(GameTestHelper helper) {
		ServerLevel level = setup(helper, 0);
		StarterBlueprints.Entry entry = StarterBlueprints.TERMINAL_NOOK;
		BlueprintData.Placement placement = placement(helper, new BlockPos(9, 2, 3));
		paste(helper, entry, placement);
		BlockPos board = placement.origin().offset(NOOK_STOREHOUSE);
		List<BlockPos> store = storeIn(level, board, box(placement, entry));
		helper.assertTrue(store.size() == NOOK_STORES, "the Nook's store has " + store.size() + " blocks, wanted " + NOOK_STORES);
		Item[] items = {Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.WHEAT, Items.PAPER, Items.COAL, Items.BREAD, Items.STRING};
		int total = 0;
		for (int i = 0; i < store.size(); i++) {
			((Container) level.getBlockEntity(store.get(i))).setItem(0, new ItemStack(items[i], i + 2));
			total += i + 2;
		}
		List<BlockPos> all = SupplyContainers.networks(level, board).stores();
		for (int i = 0; i < store.size(); i++) {
			long counted = SupplyContainers.count(level, all, items[i]);
			helper.assertTrue(counted == i + 2, "the Storehouse counts " + counted + " " + items[i] + ", put in " + (i + 2));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Component stock = stockLine(helper, player, board);
		Object[] args = ((TranslatableContents) stock.getContents()).getArgs();
		helper.assertTrue(((Component) args[0]).getString().equals(NOOK_STORES + " chests"), "the board says " + ((Component) args[0]).getString()
			+ ", wanted " + NOOK_STORES + " chests");
		helper.assertTrue(((Component) args[1]).getString().equals(total + " items"), "the board says " + ((Component) args[1]).getString()
			+ ", wanted " + total + " items");
		for (int i = 0; i < store.size(); i++) {
			int taken = SupplyContainers.extract(level, all, items[i], 1000);
			helper.assertTrue(taken == i + 2, "taking all the " + items[i] + " gave " + taken + ", put in " + (i + 2));
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------- a chest of the Hall broken

	/**
	 * The finished Storage Hall: the hut's materials (3 over) in some of its chests and 10 diamonds alone in another. A
	 * player breaks the diamonds' chest: the board then counts one chest and 10 items fewer, exactly 10 diamonds lie on
	 * the ground, and a builder still builds the hut from the hall's other chests with every item accounted for.
	 */
	//$ gametest_ticks_batch HUGE_AREA '6000' '"qaHallBroken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 6000, batch = "qaHallBroken")
	public void aChestOfTheHallBrokenLeavesTheRestServingWithNothingDuplicated(GameTestHelper helper) {
		ServerLevel level = setup(helper, 48);
		StarterBlueprints.Entry entry = StarterBlueprints.STORAGE_HALL;
		BlueprintData.Placement placement = placement(helper, new BlockPos(9, 2, 3));
		paste(helper, entry, placement);
		BlockPos board = placement.origin().offset(HALL_STOREHOUSE);
		BoundingBox box = box(placement, entry);
		List<BlockPos> store = storeIn(level, board, box);
		helper.assertTrue(store.size() == HALL_STORES, "the Hall's store has " + store.size() + " blocks, wanted " + HALL_STORES);
		BlockPos table = new BlockPos(26, 2, 26);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(table));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.west());
		Builders.employ(level, builder, helper.absolutePos(table));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(19, 2, 20)));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + TEST_HUT);
		Map<Item, Integer> hut = plan.materials();
		// the diamonds' chest: the last of the store; the hut's materials in the others
		BlockPos broken = store.get(store.size() - 1);
		List<BlockPos> rest = new ArrayList<>(store.subList(0, store.size() - 1));
		spread(level, rest, hut, EXTRA);
		((Container) level.getBlockEntity(broken)).setItem(0, new ItemStack(Items.DIAMOND, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		long[] before = stockNumbers(helper, player, board);
		helper.assertTrue(level.destroyBlock(broken, true, player), "the chest wasn't broken");
		long[] after = stockNumbers(helper, player, board);
		helper.assertTrue(after[0] == before[0] - 1, "the board's chests went from " + before[0] + " to " + after[0] + ", wanted one fewer");
		helper.assertTrue(after[1] == before[1] - 10, "the board's items went from " + before[1] + " to " + after[1] + ", wanted 10 fewer");
		Village.forget(builder);
		Builders.statusText(level, site, builder);
		helper.assertTrue(site.missing().isEmpty(), "with one chest broken the builder doesn't count the Hall: needs " + site.missing());
		helper.succeedWhen(() -> {
			int diamonds = onGround(helper, Items.DIAMOND);
			helper.assertTrue(diamonds == 10, diamonds + " diamonds on the ground, wanted the 10 the chest held");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut is still building: " + site.stage() + " " + site.status()
				+ " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the hut wrong");
			helper.assertTrue(site.skipped() == 0, "the hut skipped " + site.skipped());
			assertAccounted(helper, level, storeIn(level, board, box), builder, hut, EXTRA);
		});
	}

	// ------------------------------------------------------------------------------------------ a restart mid-build

	/**
	 * The Nook's build saved and loaded mid-structure (every site and the builder, as a server restart reads them): it
	 * still finishes with every block right and nothing skipped, it is a Storehouse with its 8 stores, and the barrels
	 * that held exactly its materials are empty, with nothing in the builder's bag (nothing was carried twice).
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qaNookReload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qaNookReload")
	public void theNookBuildFinishesAfterARestartMidBuild(GameTestHelper helper) {
		ServerLevel level = setup(helper, 0);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3))};
		Builders.employ(level, builder[0], helper.absolutePos(bench));
		StarterBlueprints.Entry entry = StarterBlueprints.TERMINAL_NOOK;
		BlueprintData.Placement placement = placement(helper, new BlockPos(9, 2, 3));
		BuildSite started = Builders.start(level, builder[0], null, entry.id(), placement);
		UUID id = started.id();
		BuildPlan plan = started.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + entry.id());
		List<BlockPos> barrels = stockBarrels(helper, plan.materials());
		int[] structureTicks = {0};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(id);
			if (reloaded[0] || site == null || site.stage() != BuildPlan.Stage.STRUCTURE || ++structureTicks[0] < 300) {
				return;
			}
			reloaded[0] = true;
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			Villager old = builder[0];
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(old.save(tag), "the builder wasn't saved");
			old.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
			level.addFreshEntity(loaded);
			builder[0] = (Villager) loaded;
		});
		BlockPos board = placement.origin().offset(NOOK_STOREHOUSE);
		BoundingBox box = box(placement, entry);
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the build never reached its structure for a restart");
			BuildSite site = BuildSiteManager.get(level).get(id);
			helper.assertTrue(site == null, "the Nook is still building after the restart: " + (site == null ? "" : site.stage() + " " + site.status()
				+ " missing " + site.missing()));
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the Nook wrong after the restart, e.g. " + unfinished.stream().limit(8)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(level.getBlockState(board).is(ModBlocks.STOREHOUSE), "no Storehouse block after the restart");
			List<BlockPos> store = storeIn(level, board, box);
			helper.assertTrue(store.size() == NOOK_STORES, "the Nook's store has " + store.size() + " blocks after the restart, wanted " + NOOK_STORES);
			Map<Item, Long> left = SupplyContainers.contents(level, barrels);
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "the barrels that held exactly the Nook's materials still hold " + left);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(builder[0]).isEmpty(), "the builder still carries "
				+ ModAttachments.BUILDER_BAG.getOrCreate(builder[0]).stacks());
		});
	}

	// --------------------------------------------------------------------------------------------- reach, both sides

	/** A finished Nook's Storehouse exactly 20 blocks from a builder's table with a 20-block village: he counts it. */
	//$ gametest_batch HUGE_AREA '"qaNookReachIn"'
	@GameTest(template = HUGE_AREA, batch = "qaNookReachIn")
	public void aNookExactlyAtTheVillagesReachIsCounted(GameTestHelper helper) {
		Reach r = reach(helper, 20);
		helper.assertTrue(r.site().missing().isEmpty(), "the Nook 20 blocks off with a 20-block village isn't counted: needs " + r.site().missing()
			+ "; " + r.status().getString());
		helper.assertTrue(!has(r.status(), "message.aliveworkplace.status.storehouse_far"), "the status says the Nook 20 blocks off is too far: "
			+ r.status().getString());
		helper.succeed();
	}

	/** The same with a 19-block village: not counted, and the status says it's too far. */
	//$ gametest_batch HUGE_AREA '"qaNookReachOut"'
	@GameTest(template = HUGE_AREA, batch = "qaNookReachOut")
	public void aNookOneBlockPastTheVillagesReachIsNotCounted(GameTestHelper helper) {
		Reach r = reach(helper, 19);
		helper.assertTrue(!r.site().missing().isEmpty(), "the Nook 20 blocks off was counted with a 19-block village");
		helper.assertTrue(has(r.status(), "message.aliveworkplace.status.storehouse_far"), "the status doesn't say the Nook is too far: "
			+ r.status().getString());
		helper.succeed();
	}

	private record Reach(BuildSite site, Component status) {
	}

	/** A pasted Nook with the hut's materials in its chests, and a builder's table exactly 20 blocks from its Storehouse. */
	private static Reach reach(GameTestHelper helper, int radius) {
		ServerLevel level = setup(helper, radius);
		StarterBlueprints.Entry entry = StarterBlueprints.TERMINAL_NOOK;
		BlueprintData.Placement placement = placement(helper, new BlockPos(2, 2, 2));
		paste(helper, entry, placement);
		BlockPos board = placement.origin().offset(NOOK_STOREHOUSE);
		List<BlockPos> store = storeIn(level, board, box(placement, entry));
		helper.assertTrue(store.size() == NOOK_STORES, "the Nook's store has " + store.size() + " blocks, wanted " + NOOK_STORES);
		BlockPos table = new BlockPos(2, 2, 2).offset(NOOK_STOREHOUSE).offset(12, 0, 16); // 20 blocks: 12 east, 16 south, level with it
		helper.assertTrue(helper.absolutePos(table).distSqr(board) == 400, "the table isn't 20 blocks from the Storehouse: "
			+ Math.sqrt(helper.absolutePos(table).distSqr(board)));
		helper.setBlock(table.below(), Blocks.STONE);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(table));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(table.getX() - 1, 2, table.getZ()));
		Builders.employ(level, builder, helper.absolutePos(table));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, new BlockPos(20, 2, 22)));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no blueprint " + TEST_HUT);
		spread(level, store, plan.materials(), 0);
		Village.forget(builder);
		Component status = Builders.statusText(level, site, builder);
		return new Reach(site, status);
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/** A clean area: nothing of an earlier batch, materials not free, village sharing at {@code radius} (0: off). */
	private static ServerLevel setup(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		if (radius > 0) {
			Leftovers.village(helper, radius);
		}
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		return level;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static BoundingBox box(BlueprintData.Placement placement, StarterBlueprints.Entry entry) {
		return BoundingBox.fromCorners(placement.origin(), placement.origin().offset(entry.size().getX() - 1, entry.size().getY() - 1, entry.size().getZ() - 1));
	}

	/** Sets every block of the build as a finished build stands (the vanilla build here), and checks nothing is wrong. */
	private static void paste(GameTestHelper helper, StarterBlueprints.Entry entry, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow(() -> new GameTestAssertException("no blueprint " + entry.id()));
		BuildPlan plan = BuildPlan.create(FurnitureChoice.forNewBuild(entry.id()).apply(blueprint, entry.id()), placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION,
			BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		List<BlockPos> unfinished = plan.unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), "the pasted " + entry.id() + " has " + unfinished.size() + " wrong block(s)");
		BlockPos board = placement.origin().offset(entry == StarterBlueprints.TERMINAL_NOOK ? NOOK_STOREHOUSE : HALL_STOREHOUSE);
		helper.assertTrue(level.getBlockState(board).is(ModBlocks.STOREHOUSE), "no Storehouse block in the pasted " + entry.id());
		ensurePoi(level, board);
	}

	/** Barrels in a row at z = 5 holding exactly {@code materials}; returns their positions (absolute). */
	private static List<BlockPos> stockBarrels(GameTestHelper helper, Map<Item, Integer> materials) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		List<BlockPos> barrels = new ArrayList<>();
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			barrels.add(helper.absolutePos(barrel));
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return barrels;
	}

	/** Puts {@code materials} plus {@code extra} of each in the given chests, one stack per chest in turn. */
	private static void spread(ServerLevel level, List<BlockPos> chests, Map<Item, Integer> materials, int extra) {
		int next = 0;
		int[] slot = new int[chests.size()];
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			for (int left = e.getValue() + extra; left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				int i = next++ % chests.size();
				((Container) level.getBlockEntity(chests.get(i))).setItem(slot[i]++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
	}

	/** The Storehouse's store, only the blocks inside the build. */
	private static List<BlockPos> storeIn(ServerLevel level, BlockPos board, BoundingBox box) {
		return SupplyContainers.find(level, board, null).stream().filter(box::isInside).toList();
	}

	/** Each material: what's in the store, the builder's bag and on the ground is exactly {@code extra}. */
	private static void assertAccounted(GameTestHelper helper, ServerLevel level, List<BlockPos> store, Villager builder, Map<Item, Integer> materials,
		int extra) {
		helper.assertTrue(!materials.isEmpty(), "no materials to account for");
		var bag = ModAttachments.BUILDER_BAG.getOrCreate(builder);
		List<String> wrong = new ArrayList<>();
		for (Item item : materials.keySet()) {
			long left = SupplyContainers.count(level, store, item) + bag.count(item) + onGround(helper, item);
			if (left != extra) {
				wrong.add(item + ": " + left + " left (store " + SupplyContainers.count(level, store, item) + ", bag " + bag.count(item) + ", ground "
					+ onGround(helper, item) + ")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "after the hut, wanted " + extra + " of each left: " + wrong);
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		AABB around = helper.getBounds().inflate(8);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	private static Component stockLine(GameTestHelper helper, ServerPlayer player, BlockPos board) {
		var lore = StorehouseBoard.boardForTest(player, board).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t && t.getKey().equals("screen.aliveworkplace.storehouse.stock"))
			.findFirst().orElseThrow(() -> new GameTestAssertException("the board has no stock line: " + lore.lines()));
	}

	/** The board's chests and items, as numbers read from the line the player reads. */
	private static long[] stockNumbers(GameTestHelper helper, ServerPlayer player, BlockPos board) {
		Object[] args = ((TranslatableContents) stockLine(helper, player, board).getContents()).getArgs();
		return new long[] {leadingNumber(((Component) args[0]).getString()), leadingNumber(((Component) args[1]).getString())};
	}

	private static long leadingNumber(String text) {
		return Long.parseLong(text.split(" ")[0].replace(",", ""));
	}

	private static boolean has(Component text, String key) {
		if (text.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) {
				return true;
			}
			for (Object a : t.getArgs()) {
				if (a instanceof Component c && has(c, key)) {
					return true;
				}
			}
		}
		for (Component sibling : text.getSiblings()) {
			if (has(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}
}
