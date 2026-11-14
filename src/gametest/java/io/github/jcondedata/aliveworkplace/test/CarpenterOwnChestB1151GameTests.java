package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B1151 and the owner's B1171, in the village the {@code carpenter} showcase scene films: one builder with his own
 * chests by his Blueprint Table (no Storehouse, no Porter), a Market Stall to build with spruce logs in place of its
 * woodwork, and a Carpenter hired at a crafting table.
 *
 * <p>The scene made "0 pieces" every night since 46.1 because its chest held 20 spruce logs and the stall's own posts
 * take 21: the Carpenter never saws the logs a build still needs as logs (B33, B696), so he had none. Until 46.1 he
 * wove the carpets from the spare wool, which is the Shepherd's list now, and the scene passed on those. With logs to
 * spare he makes all of it, once, into the chests the builder takes from, and the stall gets built; that is also the
 * owner's B1171 village in its plainest form (a builder's own chests in his house), reloaded mid-delivery too.
 */
public class CarpenterOwnChestB1151GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	static final BlockPos BENCH = new BlockPos(3, 2, 3);
	/** The builder's own chests, by his table: the stock in the first, the second empty for what's made. */
	static final BlockPos STOCK = new BlockPos(1, 2, 3);
	static final BlockPos ROOM = new BlockPos(1, 2, 5);
	static final BlockPos TABLE = new BlockPos(16, 2, 3);
	static final BlockPos SITE = new BlockPos(9, 2, 9);
	/** Spruce logs in the chest on top of the stall's own. */
	static final int SPARE_LOGS = 64;
	/** A request is made in whole crafts (4 planks, 6 slabs, 3 fences, 2 trapdoors), a bagful at a time: this many over at most. */
	static final int ROUNDING = 24;
	/** Spare logs for the test whose builder carries the stall's own: enough for its woodwork (about 28), not that and 21 more. */
	static final int CARRIED_SPARE = 32;
	/** The Market Stall's pieces the Carpenter makes from spruce logs. */
	static final List<Item> WOODWORK = List.of(Items.SPRUCE_PLANKS, Items.SPRUCE_SLAB, Items.BARREL, Items.SPRUCE_TRAPDOOR, Items.SPRUCE_FENCE,
		Items.COMPOSTER);

	record Run(ServerLevel level, BuildSite site, BuildPlan plan, Villager builder, AtomicReference<Villager> carpenter, int woodwork, int ownLogs) {
	}

	/**
	 * Logs to spare: every piece of the stall's woodwork (planks, slabs, barrels, trapdoors, fences, the composter) is
	 * made from the logs in the builder's own chest and put back there, the stall is built to the last block, and the
	 * Carpenter never makes more than the stall asks for (a loop would saw all 64 spare logs).
	 */
	//$ gametest_ticks_batch BIG '9000' '"b1151Stall"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "b1151Stall")
	public void theStallsWoodworkIsMadeOnceFromTheBuildersOwnLogsAndBuilt(GameTestHelper helper) {
		Run run = setUp(helper, SPARE_LOGS, false);
		madeOnceAndBuilt(helper, run, null, SPARE_LOGS / 4);
	}

	/**
	 * The same, saved and loaded mid-delivery: the Carpenter is reloaded the first time his job turns to carrying what
	 * he made to the builder's chest. It still arrives, nothing is made twice, and the stall is built.
	 */
	//$ gametest_ticks_batch BIG '9000' '"b1151StallReload"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "b1151StallReload")
	public void reloadedMidDeliveryTheWoodworkStillArrivesOnce(GameTestHelper helper) {
		Run run = setUp(helper, SPARE_LOGS, false);
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			Villager carpenter = run.carpenter().get();
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (!reloaded.get() && job != null && job.phase() == CrafterJob.Phase.DELIVERING) {
				reloaded.set(true);
				run.carpenter().set(QaCarpenterStorehouse1101GameTests.reload(run.level(), carpenter));
			}
		});
		madeOnceAndBuilt(helper, run, reloaded, SPARE_LOGS / 4);
	}

	/**
	 * What stalled the scene's stall once it had logs (B1151): the builder carries the stall's own 21 logs, and the chest
	 * holds 32 more, enough for all the woodwork. The Carpenter held the stall's 21 back in the chest as well, as if the
	 * builder carried none, so he had 11 to saw, ran out part-way, and the builder waited for the rest beside a chest of
	 * logs. Expected: what the builder (or his crew) carries counts toward the build, the rest is spare, and the stall
	 * is built.
	 */
	//$ gametest_ticks_batch BIG '9000' '"b1151StallCarried"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "b1151StallCarried")
	public void logsTheBuilderCarriesAreNotHeldBackInHisChestToo(GameTestHelper helper) {
		Run run = setUp(helper, CARRIED_SPARE, true);
		madeOnceAndBuilt(helper, run, null, 0);
	}

	/**
	 * The scene as it was staged (B1151): one log short of the stall's own posts and no woodwork. Those logs are the
	 * build's, so the Carpenter makes nothing of them, and the builder's line says what's short: spruce logs.
	 */
	//$ gametest_ticks_batch BIG '1600' '"b1151StallShort"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "b1151StallShort")
	public void theBuildsOwnLogsAreNeverSawnAndTheLineNamesTheLogs(GameTestHelper helper) {
		Run run = setUp(helper, -1, false);
		helper.onEachTick(() -> {
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(run.carpenter().get(), 0);
			if (made > 0) {
				throw new GameTestAssertException("the Carpenter made " + made + " from logs the stall needs as logs" + state(helper, run));
			}
		});
		helper.runAfterDelay(400, () -> helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
			if (live == null) {
				throw new GameTestAssertException("the stall was built without its woodwork");
			}
			Component line = JobShortages.line(run.level(), live.id(), live.missing(), helper.absolutePos(BENCH));
			String text = line == null ? "" : line.getString();
			if (!text.contains("Spruce Log")) {
				throw new GameTestAssertException("the builder's line doesn't name the spruce logs short: \"" + text + "\"" + state(helper, run));
			}
			int logs = count(helper, Items.SPRUCE_LOG) + bag(run.builder()).count(Items.SPRUCE_LOG);
			if (logs != run.ownLogs() - 1) {
				throw new GameTestAssertException(logs + " of the " + (run.ownLogs() - 1) + " spruce logs left" + state(helper, run));
			}
		}));
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/**
	 * The scene's village: the Market Stall's list in the builder's first chest with its woodwork left out, and
	 * {@code spareLogs} spruce logs more than the stall's own (-1: one fewer, as the scene had it). {@code carried}: the
	 * stall's own logs are in the builder's bag already, and only the spare ones in the chest.
	 */
	static Run setUp(GameTestHelper helper, int spareLogs, boolean carried) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
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
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.MARKET_STALL.id())
			.orElseThrow(() -> new GameTestAssertException("no Market Stall blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SITE), Rotation.NONE, Mirror.NONE);
		Map<Item, Integer> materials = BuildPlan.create(blueprint, placement).materials();
		int ownLogs = materials.getOrDefault(Items.SPRUCE_LOG, 0);
		if (ownLogs == 0) {
			throw new GameTestAssertException("the Market Stall takes no spruce logs any more: " + materials);
		}
		List<ItemStack> stock = new ArrayList<>();
		int woodwork = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			if (e.getKey() == Items.SPRUCE_LOG) {
				continue;
			}
			if (WOODWORK.contains(e.getKey())) {
				if (!CraftJobs.makes(level, CraftJobs.Job.CARPENTER, e.getKey())) {
					throw new GameTestAssertException(e.getKey() + " isn't on the Carpenter's list");
				}
				woodwork += e.getValue(); // left out: his to make
				continue;
			}
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int left = (carried ? 0 : ownLogs) + spareLogs; left > 0; left -= 64) {
			stock.add(new ItemStack(Items.SPRUCE_LOG, Math.min(left, 64)));
		}
		if (woodwork < 20) {
			throw new GameTestAssertException("the Market Stall has only " + woodwork + " pieces of the Carpenter's: " + materials);
		}
		helper.setBlock(STOCK, Blocks.BARREL);
		helper.setBlock(ROOM, Blocks.BARREL);
		Container chest = helper.getBlockEntity(STOCK);
		if (stock.size() > chest.getContainerSize()) {
			throw new GameTestAssertException("the stock takes " + stock.size() + " slots");
		}
		for (int i = 0; i < stock.size(); i++) {
			chest.setItem(i, stock.get(i));
		}
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(BENCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		if (carried && bag(builder).addAll(Items.SPRUCE_LOG, ownLogs) != 0) {
			throw new GameTestAssertException("the builder's bag doesn't take " + ownLogs + " logs");
		}
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.MARKET_STALL.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the Market Stall");
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		// as the scene hires him: at a plain crafting table
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(TABLE));
		Villager carpenter = helper.spawn(EntityType.VILLAGER, TABLE.north());
		Jobs.employ(level, carpenter, helper.absolutePos(TABLE), ModVillagers.CRAFTING_TABLE_POI, ModVillagers.CARPENTER);
		return new Run(level, site, plan, builder, new AtomicReference<>(carpenter), woodwork, ownLogs);
	}

	/** Never more made than the stall's woodwork (and the rounding of whole crafts); passes once the stall stands. */
	static void madeOnceAndBuilt(GameTestHelper helper, Run run, AtomicBoolean reloaded, int logsLeft) {
		int most = run.woodwork() + ROUNDING;
		helper.onEachTick(() -> {
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(run.carpenter().get(), 0);
			if (made > most) {
				throw new GameTestAssertException("the Carpenter made " + made + " pieces for a stall that takes " + run.woodwork()
					+ ": made again after it was delivered" + state(helper, run));
			}
		});
		helper.succeedWhen(() -> {
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(run.carpenter().get(), 0);
			if (BuildSiteManager.get(run.level()).get(run.site().id()) != null) {
				throw new GameTestAssertException("still building" + state(helper, run));
			}
			List<BlockPos> unfinished = run.plan().unfinished(run.level());
			if (!unfinished.isEmpty()) {
				throw new GameTestAssertException(unfinished.size() + " blocks of the stall wrong, e.g. " + helper.relativePos(unfinished.get(0)));
			}
			if (made < run.woodwork()) {
				throw new GameTestAssertException("the stall stands, but the Carpenter made only " + made + " of its " + run.woodwork() + " pieces");
			}
			if (reloaded != null && !reloaded.get()) {
				throw new GameTestAssertException("the Carpenter was never caught carrying, so the reload showed nothing");
			}
			// every log sawn went into the stall: no more of the spare ones are gone than its woodwork takes
			int logs = count(helper, Items.SPRUCE_LOG);
			if (logs < logsLeft) {
				throw new GameTestAssertException("only " + logs + " of the spare spruce logs left, " + logsLeft + " expected at least" + state(helper, run));
			}
		});
	}

	static BuilderBag bag(Villager villager) {
		return ModAttachments.BUILDER_BAG.getOrCreate(villager);
	}

	/** How many of {@code item} the builder's two chests hold. */
	static int count(GameTestHelper helper, Item item) {
		int n = 0;
		for (BlockPos pos : List.of(STOCK, ROOM)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		return n;
	}

	/** The site's list, the Carpenter's count, job and bag, and the logs left, for a failure's message. */
	static String state(GameTestHelper helper, Run run) {
		BuildSite live = BuildSiteManager.get(run.level()).get(run.site().id());
		Villager carpenter = run.carpenter().get();
		return " [site: " + (live == null ? "done" : live.stage() + " " + live.status() + " missing=" + live.missing())
			+ "; carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + ", job=" + ModAttachments.CRAFTER_JOB.get(carpenter)
			+ ", bag empty=" + bag(carpenter).isEmpty() + "; spruce logs in the chests: " + count(helper, Items.SPRUCE_LOG) + "]";
	}
}
