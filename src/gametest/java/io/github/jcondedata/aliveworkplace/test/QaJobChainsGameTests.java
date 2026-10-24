package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-c-1024-2152) for ROADMAP 37.14a, written from the spec and the wiki (docs/wiki/crafter.md), not the code:
 * the boundary of "check every step before taking anything" (a raw material short of the last one by exactly one), the jobs
 * JobChainGameTests doesn't run end to end (the Librarian's bookshelves from sugar cane, the Tinkerer's iron bars from
 * raw ore, a Mason's all-stone chain through the kiln) and a kiln chain saved and loaded mid-way. Each test has its own
 * batch: the jobs share their claims.
 */
public class QaJobChainsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/**
	 * Five oak logs for three barrels (three barrels take six, one barrel three): the Carpenter makes the two it can (the
	 * order is tried whole, then smaller), and for the last one it takes nothing and the builder's line names the logs
	 * still short ("short of N Oak Log to make 1 Barrel"); the leftover log stays in the chest.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaChainOneLogShort"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaChainOneLogShort")
	public void fiveLogsMakeTwoBarrelsAndTheLastIsNamedShort(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 5));
		Villager carpenter = carpenter(helper);
		helper.succeedWhen(() -> {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(a.site().missing().getOrDefault(Items.BARREL, 0) == 1, "two barrels should be built, still missing " + a.site().missing());
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.matches("Can't be made yet: short of [12] Oak Log to make 1 Barrel"), "the builder's shortage line: " + text);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 1, "the leftover log should stay in the chest, " + chest.countItem(Items.OAK_LOG) + " there");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the carpenter is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the carpenter took a job it can't finish");
		});
	}

	/**
	 * The Librarian makes the bookish parts through a whole chain: three bookshelves from sugar cane (paper, books, then
	 * the shelves), leather and planks, and the build finishes with every making used.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainLibrarian"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainLibrarian")
	public void theLibrarianMakesBookshelvesFromSugarCane(GameTestHelper helper) {
		prepare(helper);
		Crafting.Plan plan = Crafting.chain(helper.getLevel(), Crafting.Kind.CRAFTING, Items.BOOKSHELF, 3,
			Map.of(Items.SUGAR_CANE, 27L, Items.LEATHER, 9L, Items.OAK_PLANKS, 18L));
		helper.assertTrue(plan != null && plan.steps().size() == 3, "bookshelves from sugar cane should be three steps: " + plan);
		Asker a = asker(helper, Blocks.BOOKSHELF, new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.LEATHER, 9), new ItemStack(Items.OAK_PLANKS, 18));
		Villager librarian = librarian(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 0 && chest.countItem(Items.LEATHER) == 0 && chest.countItem(Items.OAK_PLANKS) == 0,
				"the makings should be used up: " + chest.countItem(Items.SUGAR_CANE) + " cane, " + chest.countItem(Items.LEATHER) + " leather, "
					+ chest.countItem(Items.OAK_PLANKS) + " planks left");
			helper.assertTrue(chest.countItem(Items.PAPER) == 0 && chest.countItem(Items.BOOK) == 0, "half-made parts came back: "
				+ chest.countItem(Items.PAPER) + " paper, " + chest.countItem(Items.BOOK) + " books");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(librarian) == null, "the finished job is still saved on the librarian");
		});
	}

	/**
	 * The Tinkerer's metal parts (wiki: "raw iron fired into ingots, then bars"): three iron bars for a builder from six
	 * raw iron and a coal; the ore is fired, the bars made, the coal burnt and the thirteen spare bars come back.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainTinkerer"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainTinkerer")
	public void theTinkererFiresRawIronIntoBarsForAWaitingBuilder(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.IRON_BARS, new ItemStack(Items.RAW_IRON, 6), new ItemStack(Items.COAL, 1));
		Villager tinkerer = tinkerer(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 0, "the ore should be used up, " + chest.countItem(Items.RAW_IRON) + " left");
			helper.assertTrue(chest.countItem(Items.COAL) == 0, "the coal should be burnt, " + chest.countItem(Items.COAL) + " left");
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0, "half-made ingots came back: " + chest.countItem(Items.IRON_INGOT));
			helper.assertTrue(chest.countItem(Items.IRON_BARS) == 13, "the thirteen spare bars should be in the chest, not " + chest.countItem(Items.IRON_BARS));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(tinkerer) == null, "the finished job is still saved on the tinkerer");
		});
	}

	/**
	 * A Mason's all-stone chain through the kiln: three smooth stone slabs from two cobblestone (fired to stone, fired
	 * again to smooth stone, cut into slabs), four firings on the one coal; the one spare slab comes back.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainMasonStone"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainMasonStone")
	public void theMasonFiresCobblestoneTwiceAndCutsSmoothStoneSlabs(GameTestHelper helper) {
		prepare(helper);
		Crafting.Plan plan = Crafting.chain(helper.getLevel(), Crafting.Kind.KILN, Items.SMOOTH_STONE_SLAB, 3, Map.of(Items.COBBLESTONE, 2L));
		helper.assertTrue(plan != null && plan.steps().size() == 3 && plan.takes().equals(Map.of(Items.COBBLESTONE, 2)),
			"smooth stone slabs from two cobblestone should be three steps: " + plan);
		Asker a = asker(helper, Blocks.SMOOTH_STONE_SLAB, new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.COAL, 1));
		mason(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			assertStoneChainClean(helper, a);
		});
	}

	/** The same kiln chain saved and loaded once the first firing is done: it carries on, nothing doubled and nothing lost. */
	//$ gametest_ticks_batch AREA '4000' '"qaChainMasonReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainMasonReload")
	public void aMasonsKilnChainReloadedMidWayFinishesWithNothingDoubled(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, Blocks.SMOOTH_STONE_SLAB, new ItemStack(Items.COBBLESTONE, 2), new ItemStack(Items.COAL, 1));
		AtomicReference<Villager> mason = new AtomicReference<>(mason(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			if (saved.get() != null) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(mason.get());
			if (job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				saved.set(job);
				Villager copy = reload(helper.getLevel(), mason.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.step() == job.step() && after.phase() == job.phase() && after.target() == job.target()
					&& after.count() == job.count(), "the job after a reload: " + after + ", saved " + job);
				helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.MASON, "the mason lost the job in the reload");
				mason.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the mason never got past the first step");
			assertBuilt(helper, a);
			assertStoneChainClean(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason.get()) == null, "the finished job is still saved on the mason");
		});
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static void assertStoneChainClean(GameTestHelper helper, Asker a) {
		Container chest = helper.getBlockEntity(a.chest());
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "the cobblestone should be used, " + chest.countItem(Items.COBBLESTONE) + " left");
		helper.assertTrue(chest.countItem(Items.COAL) == 0, "the coal should be burnt, " + chest.countItem(Items.COAL) + " left");
		helper.assertTrue(chest.countItem(Items.STONE) == 0 && chest.countItem(Items.SMOOTH_STONE) == 0, "half-made stone came back: "
			+ chest.countItem(Items.STONE) + " stone, " + chest.countItem(Items.SMOOTH_STONE) + " smooth stone");
		helper.assertTrue(chest.countItem(Items.SMOOTH_STONE_SLAB) == 1, "the one spare slab should be in the chest, not " + chest.countItem(Items.SMOOTH_STONE_SLAB));
	}

	static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	/** A builder at BENCH with {@code items} in the chest at CHEST, building a row of three {@code block} at ROW. */
	static Asker asker(GameTestHelper helper, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = row(helper, ROW, block);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}

	private static ResourceLocation row(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qachain_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static Villager carpenter(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return v;
	}

	static Villager librarian(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.LECTERN);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		return v;
	}

	private static Villager tinkerer(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	private static Villager mason(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		return v;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
