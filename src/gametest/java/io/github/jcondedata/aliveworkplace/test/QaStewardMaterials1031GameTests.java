package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-31) for B685: the showcase city_timelapse ends with one wall piece WAITING_FOR_MATERIALS for spruce logs
 * that the soak stocked for it ("spruce_log want 8 list 63 done 55 given 63 store 0", every bag and chest empty, items
 * off none). The soak stocks each build's exact list into one storehouse every builder shares, so a log can only go
 * missing if something turns it into something else (the ledger counts crafts as fair, B655) or a build lays more than
 * its list. The village has a Carpenter (B655 saw him craft in this scene), and he works out what he may use from the
 * waiting builder's own remaining need only ({@code CrafterWork.choose}: {@code Builders.remainingNeed} of that one site),
 * so another open build's logs in the same chests are "spare" to him.
 *
 * <p>Spec: docs/guide/carpenter.md "They only use what the rest of that build doesn't need"; ROADMAP 23.1 "every build
 * finishes; no item is duplicated or lost (count them before and after)"; B33/23.5: from shared or another builder's
 * chests, only what the builds they serve can spare. Each test has its own batch (the crafters' claims, the ledger and
 * the chests are shared state).
 */
public class QaStewardMaterials1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH_B = new BlockPos(5, 2, 2);
	/** The chest both builders share, within reach of both benches (as the storehouse's chests are in the city). */
	private static final BlockPos CHEST = new BlockPos(3, 2, 4);
	private static final BlockPos STATION = new BlockPos(13, 2, 2);
	private static final BlockPos ROW_A = new BlockPos(6, 2, 8);
	private static final BlockPos ROW_B = new BlockPos(6, 2, 11);
	/** The tick the cobblestone builder B waits for arrives (as a porter or the soak would bring it). */
	private static final int COBBLE_AT = 1600;

	// ---------------------------------------------------------------------------------------------------- the bug

	/**
	 * Two builders share a chest. A builds a row of 3 spruce planks and the chest has none; B builds a row of 3
	 * cobblestone with 3 spruce logs on it, and the chest holds exactly those 3 logs (no cobblestone yet: B waits for it, as
	 * the city's pieces wait their turn). A Carpenter works nearby. B's logs are not spare: the Carpenter must not craft
	 * them into A's planks. Then the cobblestone comes and B finishes. On the current code the Carpenter turns one of B's
	 * logs into 4 planks for A, and B waits for 1 spruce log for ever: the city time-lapse's stuck wall piece.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaMat1031Carpenter"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaMat1031Carpenter")
	public void aCarpenterNeverCraftsAwayLogsAnotherOpenBuildNeeds(GameTestHelper helper) {
		prepare(helper, true);
		ServerLevel level = helper.getLevel();
		ResourceLocation planksRow = blueprint(helper, "planks", ROW_A, new Vec3i(3, 1, 1), at -> Blocks.SPRUCE_PLANKS.defaultBlockState());
		ResourceLocation logRow = blueprint(helper, "logs", ROW_B, new Vec3i(3, 2, 1),
			at -> at.getY() == ROW_B.getY() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 3));
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		BuildSite siteA = start(helper, a, planksRow, ROW_A);
		BuildSite siteB = start(helper, b, logRow, ROW_B);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		long start = level.getGameTime();
		boolean[] cobble = {false};
		helper.onEachTick(() -> {
			long t = level.getGameTime() - start;
			if (!cobble[0] && t >= COBBLE_AT) {
				cobble[0] = true;
				ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, List.of(helper.absolutePos(CHEST)),
					new ItemStack(Items.COBBLESTONE, 3));
				helper.assertTrue(rest.isEmpty(), "setup: no room for the cobblestone");
			}
			// Conservation of B's logs: in the chest, in anyone's bag, lying about or standing in B's row.
			int logs = inChest(helper, Items.SPRUCE_LOG) + inBags(helper, Items.SPRUCE_LOG) + onGround(helper, Items.SPRUCE_LOG)
				+ placed(helper, ROW_B.above(), 3, Blocks.SPRUCE_LOG);
			if (logs < 3) {
				throw new GameTestAssertException("at tick " + t + " only " + logs + " of the 3 spruce logs stocked for B's build are left (chest "
					+ inChest(helper, Items.SPRUCE_LOG) + ", bags " + inBags(helper, Items.SPRUCE_LOG) + ", built " + placed(helper, ROW_B.above(), 3, Blocks.SPRUCE_LOG)
					+ "): the Carpenter made " + (inChest(helper, Items.SPRUCE_PLANKS) + inBags(helper, Items.SPRUCE_PLANKS)
					+ placed(helper, ROW_A, 3, Blocks.SPRUCE_PLANKS)) + " spruce planks for A out of the logs B's open build still needs (A "
					+ (BuildSiteManager.get(level).get(siteA.id()) == null ? "finished" : siteA.status()) + ", B " + siteB.status()
					+ " missing " + siteB.missing() + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cobble[0], "the cobblestone isn't there yet");
			helper.assertTrue(BuildSiteManager.get(level).get(siteB.id()) == null, "B's build isn't finished: " + siteB.status() + " missing " + siteB.missing()
				+ ", chest " + chestContents(helper));
		});
	}

	/**
	 * B696's other half: the same two builds, but the chest holds one spruce log more than B needs. That log is spare, so
	 * the Carpenter still crafts it into A's planks: A finishes, and B (once its cobblestone comes) finishes too.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaMat1031CarpenterSpare"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaMat1031CarpenterSpare")
	public void aCarpenterStillCraftsFromALogNoOpenBuildNeeds(GameTestHelper helper) {
		prepare(helper, true);
		ServerLevel level = helper.getLevel();
		ResourceLocation planksRow = blueprint(helper, "planks_spare", ROW_A, new Vec3i(3, 1, 1), at -> Blocks.SPRUCE_PLANKS.defaultBlockState());
		ResourceLocation logRow = blueprint(helper, "logs_spare", ROW_B, new Vec3i(3, 2, 1),
			at -> at.getY() == ROW_B.getY() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 4));
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		BuildSite siteA = start(helper, a, planksRow, ROW_A);
		BuildSite siteB = start(helper, b, logRow, ROW_B);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		long start = level.getGameTime();
		boolean[] cobble = {false};
		helper.onEachTick(() -> {
			if (!cobble[0] && level.getGameTime() - start >= COBBLE_AT) {
				cobble[0] = true;
				ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, List.of(helper.absolutePos(CHEST)),
					new ItemStack(Items.COBBLESTONE, 3));
				helper.assertTrue(rest.isEmpty(), "setup: no room for the cobblestone");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cobble[0], "the cobblestone isn't there yet");
			helper.assertTrue(BuildSiteManager.get(level).get(siteA.id()) == null, "A's planks row isn't finished from the spare log: " + siteA.status()
				+ " missing " + siteA.missing() + ", chest " + chestContents(helper));
			helper.assertTrue(BuildSiteManager.get(level).get(siteB.id()) == null, "B's build isn't finished: " + siteB.status() + " missing " + siteB.missing()
				+ ", chest " + chestContents(helper));
		});
	}

	// ----------------------------------------------------------------------------------------------- happy paths

	/**
	 * One builder, the real Palisade from the wall kit, exactly its list in the chest: it finishes, and every item of the
	 * list is either built into the palisade, in the chest or in the builder's bag (nothing over, nothing missing).
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaMat1031Palisade"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaMat1031Palisade")
	public void aPalisadeBuiltFromExactlyItsListUsesExactlyItsList(GameTestHelper helper) {
		prepare(helper, false);
		exactBuild(helper, AliveWorkplace.id("palisade"), new BlockPos(6, 2, 9));
	}

	/** The same for the Palisade Tower (the piece stuck in the time-lapse): 63 spruce logs in, 63 built. */
	//$ gametest_ticks_batch BIG '5000' '"qaMat1031Tower"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaMat1031Tower")
	public void aPalisadeTowerBuiltFromExactlyItsListUsesExactlyItsList(GameTestHelper helper) {
		prepare(helper, false);
		exactBuild(helper, AliveWorkplace.id("palisade_tower"), new BlockPos(6, 2, 9));
	}

	/**
	 * Two builders sharing one chest, each building a Palisade: two pieces side by side, as the wall line lays its
	 * segments out on a stretch that isn't a round number of them (Walls.run: the second starts 5 along, so they share
	 * two columns). The chest holds exactly both lists. Both finish, and no spruce log or fence goes missing: each one is
	 * standing in the wall, in the chest or in a bag (the shared columns are counted on both lists, so some are left over).
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaMat1031Overlap"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaMat1031Overlap")
	public void twoOverlappingPalisadesFromOneSharedChestLoseNothing(GameTestHelper helper) {
		prepare(helper, false);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CHEST.east(2), Blocks.CHEST); // a gap between: never one double chest
		Villager a = builder(helper, BENCH_A);
		Villager b = builder(helper, BENCH_B);
		BuildSite one = start(helper, a, AliveWorkplace.id("palisade"), new BlockPos(3, 2, 9));
		BuildSite two = start(helper, b, AliveWorkplace.id("palisade"), new BlockPos(8, 2, 9));
		Map<Item, Integer> stocked = new TreeMap<>(QaStewardMaterials1031GameTests::byId);
		for (BuildSite s : List.of(one, two)) {
			s.plan(level).materials().forEach((item, n) -> stocked.merge(item, n, Integer::sum));
		}
		stock(helper, stocked, List.of(CHEST, CHEST.east(2)));
		BoundingBox union = new BoundingBox(Math.min(one.plan(level).bounds().minX(), two.plan(level).bounds().minX()), one.plan(level).bounds().minY(),
			Math.min(one.plan(level).bounds().minZ(), two.plan(level).bounds().minZ()), Math.max(one.plan(level).bounds().maxX(), two.plan(level).bounds().maxX()),
			Math.max(one.plan(level).bounds().maxY(), two.plan(level).bounds().maxY()), Math.max(one.plan(level).bounds().maxZ(), two.plan(level).bounds().maxZ()));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(one.id()) == null && BuildSiteManager.get(level).get(two.id()) == null,
				"not both finished: first " + one.status() + " missing " + one.missing() + ", second " + two.status() + " missing " + two.missing()
				+ ", chests " + chestContents(helper));
			for (Item item : List.of(Items.SPRUCE_LOG, Items.SPRUCE_FENCE)) {
				int standing = standing(level, union, item);
				int chests = inChest(helper, item, CHEST) + inChest(helper, item, CHEST.east(2));
				int bags = inBags(helper, item);
				int ground = onGround(helper, item);
				int total = standing + chests + bags + ground;
				helper.assertTrue(total == stocked.getOrDefault(item, 0), BuiltInRegistries.ITEM.getKey(item).getPath() + ": " + stocked.get(item)
					+ " stocked, but " + standing + " standing in the wall, " + chests + " in the chests, " + bags + " in bags and " + ground
					+ " lying about = " + total);
			}
		});
	}

	// --------------------------------------------------------------------------------------------------- helpers

	private static void prepare(GameTestHelper helper, boolean village) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		if (village) {
			Leftovers.village(helper, 48);
		}
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
	}

	/** One builder at a Blueprint Table, a chest beside it with exactly {@code id}'s list, building it at {@code origin}. */
	private static void exactBuild(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CHEST.east(2), Blocks.CHEST);
		Villager builder = builder(helper, BENCH_A);
		BuildSite site = start(helper, builder, id, origin);
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> stocked = new TreeMap<>(QaStewardMaterials1031GameTests::byId);
		stocked.putAll(plan.materials());
		stock(helper, stocked, List.of(CHEST, CHEST.east(2)));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the " + id.getPath() + " isn't finished: " + site.stage() + " "
				+ site.status() + " missing " + site.missing() + ", chests " + chestContents(helper));
			Map<Item, Integer> built = new HashMap<>();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
						for (MaterialRules.Requirement r : step.requirements()) {
							built.merge(r.item(), r.count(), Integer::sum);
						}
					}
				}
			}
			List<String> off = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : stocked.entrySet()) {
				Item item = e.getKey();
				int b = built.getOrDefault(item, 0);
				int c = inChest(helper, item, CHEST) + inChest(helper, item, CHEST.east(2));
				int g = inBags(helper, item);
				int d = onGround(helper, item);
				if (b + c + g + d != e.getValue()) {
					off.add(BuiltInRegistries.ITEM.getKey(item).getPath() + " stocked " + e.getValue() + ", built " + b + ", chests " + c + ", bag " + g
						+ ", lying about " + d);
				}
			}
			helper.assertTrue(off.isEmpty(), "the " + id.getPath() + "'s list doesn't add up: " + String.join("; ", off));
		});
	}

	private static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	private static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A small blueprint drawn in the world at {@code origin} ({@code size}), saved and the spot cleared again. */
	private static ResourceLocation blueprint(GameTestHelper helper, String name, BlockPos origin, Vec3i size,
											  java.util.function.Function<BlockPos, BlockState> block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					BlockPos at = origin.offset(x, y, z);
					helper.setBlock(at, block.apply(at));
				}
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1031_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
				}
			}
		}
		return id;
	}

	private static void stock(GameTestHelper helper, Map<Item, Integer> items, List<BlockPos> chests) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> at = chests.stream().map(helper::absolutePos).toList();
		items.forEach((item, n) -> {
			int left = n;
			while (left > 0) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				ItemStack rest = io.github.jcondedata.aliveworkplace.build.SupplyContainers.insert(level, at, new ItemStack(item, take));
				if (!rest.isEmpty()) {
					throw new GameTestAssertException("setup: the chests are full");
				}
				left -= take;
			}
		});
	}

	private static int inChest(GameTestHelper helper, Item item) {
		return inChest(helper, item, CHEST);
	}

	private static int inChest(GameTestHelper helper, Item item, BlockPos pos) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c)) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(item)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static String chestContents(GameTestHelper helper) {
		Map<String, Integer> out = new TreeMap<>();
		for (BlockPos pos : List.of(CHEST, CHEST.east(2))) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (!s.isEmpty()) {
						out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
					}
				}
			}
		}
		return out.toString();
	}

	private static int inBags(GameTestHelper helper, Item item) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** How many of the row's {@code length} blocks from {@code from} along x are {@code block}. */
	private static int placed(GameTestHelper helper, BlockPos from, int length, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (int x = 0; x < length; x++) {
			if (helper.getBlockState(from.offset(x, 0, 0)).is(block)) {
				n++;
			}
		}
		return n;
	}

	/** Blocks in {@code box} (world) that are {@code item}'s block. */
	private static int standing(ServerLevel level, BoundingBox box, Item item) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).getBlock().asItem() == item) {
				n++;
			}
		}
		return n;
	}

	private static int byId(Item a, Item b) {
		return BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b));
	}
}
