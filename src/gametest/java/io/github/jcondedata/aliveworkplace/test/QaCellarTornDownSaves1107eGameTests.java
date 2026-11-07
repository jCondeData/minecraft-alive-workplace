package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-e-1107-1912, saves; the verification of B974). Written from docs/wiki/cellars.md: "Dig the cellar" goes once
 * "the building [is] gone" ("under a quarter of its blocks on and above the ground still standing (solid, whatever the
 * block)"), the hall's advice no longer lists it, and one bought before "starts nothing ("There's no cellar to dig under
 * the Smithy any more"), and the player keeps it". QaBuilderGaps1105bBugTests checks it within one session; these add a
 * server restart: the build-site data (the finished record), the builder (his saved trades) and the bought job item are
 * saved and loaded again, before or after the Smithy is torn down by hand, and check the tear-down is still told apart
 * from a building that stands. Boundaries: all but one block gone (with grass grown over the floor) is gone, a fifth
 * standing is gone, three tenths standing is still there and dug.
 */
public class QaCellarTornDownSaves1107eGameTests implements FabricGameTest {
	private static final String NO_DIG = "message.aliveworkplace.cellar_no_dig";

	/**
	 * Torn down by hand, then the server restarts (sites, builder and the bought job saved and loaded): opening the
	 * builder's trades drops his saved "Dig the cellar", the hall's advice lists no dig, and the reloaded job, given to
	 * the reloaded builder, starts nothing, tells the player the no-cellar sentence and stays with him.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qa1107eCellarTornThenReload"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qa1107eCellarTornThenReload")
	public void qaASmithyTornDownThenReloadedIsNotSoldAdvisedOrDug1107e(GameTestHelper helper) {
		Setup s = setup(helper);
		tearDown(helper, s, 0);
		Villager builder = restart(helper, s.builder);
		ItemStack job = roundTrip(helper, s.job);
		ModTrades.builderHomes(builder); // the player opens his trades after the restart
		helper.assertTrue(sold(builder).isEmpty(), "torn down then reloaded, the builder still sells " + sold(builder));
		helper.assertTrue(advised(helper).isEmpty(), "torn down then reloaded, the hall still advises " + advised(helper));
		refused(helper, s, builder, job, "qaTE1");
		helper.succeed();
	}

	/**
	 * The dig is bought, then the server restarts (the job, the builder and the sites saved and loaded) while the Smithy
	 * still stands; then the Smithy is torn down by hand. The job, given to the builder, starts nothing and says why.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qa1107eCellarReloadThenTorn"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qa1107eCellarReloadThenTorn")
	public void qaADigBoughtAndReloadedBeforeTheTearDownStartsNothing1107e(GameTestHelper helper) {
		Setup s = setup(helper);
		Villager builder = restart(helper, s.builder);
		ItemStack job = roundTrip(helper, s.job);
		ModTrades.builderHomes(builder);
		helper.assertTrue(!sold(builder).isEmpty(), "setup: the standing Smithy, reloaded, is no longer sold its dig");
		tearDown(helper, s, 0);
		refused(helper, s, builder, job, "qaTE2");
		ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder).isEmpty(), "reloaded then torn down, the builder still sells " + sold(builder));
		helper.succeed();
	}

	/**
	 * All but one block of the Smithy on and above the ground broken, and grass grown on the empty floor (not solid), then
	 * a restart: it is gone (well under a quarter), so it is not sold, not advised and the job bought before starts
	 * nothing.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qa1107eCellarOneBlockLeft"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qa1107eCellarOneBlockLeft")
	public void qaASmithyWithOneBlockLeftAndGrassOverItIsGoneAfterAReload1107e(GameTestHelper helper) {
		Setup s = setup(helper);
		tearDown(helper, s, -1);
		int[] share = standing(helper, s);
		helper.assertTrue(share[0] == 1, "setup: " + share[0] + " blocks of " + share[1] + " left standing, not 1");
		Villager builder = restart(helper, s.builder);
		ItemStack job = roundTrip(helper, s.job);
		ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder).isEmpty(), "one block of " + share[1] + " left, the builder still sells " + sold(builder));
		helper.assertTrue(advised(helper).isEmpty(), "one block left, the hall still advises " + advised(helper));
		refused(helper, s, builder, job, "qaTE3");
		helper.succeed();
	}

	/** A fifth of the Smithy left standing (under the wiki's quarter), across a restart: gone, not sold, not dug. */
	//$ gametest_ticks_batch TALL_AREA '200' '"qa1107eCellarAFifthLeft"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qa1107eCellarAFifthLeft")
	public void qaASmithyWithAFifthStandingIsGoneAfterAReload1107e(GameTestHelper helper) {
		Setup s = setup(helper);
		tearDown(helper, s, 5);
		int[] share = standing(helper, s);
		helper.assertTrue(share[0] * 100 >= share[1] * 15 && share[0] * 100 < share[1] * 22,
			"setup: " + share[0] + " of " + share[1] + " standing, not about a fifth");
		Villager builder = restart(helper, s.builder);
		ItemStack job = roundTrip(helper, s.job);
		ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder).isEmpty(), share[0] + " of " + share[1] + " standing, the builder still sells " + sold(builder));
		helper.assertTrue(advised(helper).isEmpty(), "a fifth standing, the hall still advises " + advised(helper));
		refused(helper, s, builder, job, "qaTE4");
		helper.succeed();
	}

	/**
	 * Control: three tenths of the Smithy left standing (over the quarter: a wreck, but there), across a restart: the
	 * builder still sells "Dig the cellar: Smithy", the hall still lists "Dig the cellar under the Smithy", and the job
	 * bought before the restart starts the cellar dig.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qa1107eCellarThreeTenthsLeft"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qa1107eCellarThreeTenthsLeft")
	public void qaASmithyWithThreeTenthsStandingIsStillDugAfterAReload1107e(GameTestHelper helper) {
		Setup s = setup(helper);
		tearDown(helper, s, 10);
		int[] share = standing(helper, s);
		helper.assertTrue(share[0] * 100 >= share[1] * 28 && share[0] * 100 < share[1] * 36,
			"setup: " + share[0] + " of " + share[1] + " standing, not about three tenths");
		Villager builder = restart(helper, s.builder);
		ItemStack job = roundTrip(helper, s.job);
		ModTrades.builderHomes(builder);
		List<String> sold = sold(builder);
		helper.assertTrue(sold.equals(List.of("Dig the cellar: Smithy")), share[0] + " of " + share[1] + " standing, sold " + sold);
		List<String> advice = advised(helper);
		helper.assertTrue(advice.equals(List.of("Dig the cellar under the Smithy")), "three tenths standing, the hall advises " + advice);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaTE5"));
		Builders.assign(c.player(), builder, job);
		ServerLevel level = helper.getLevel();
		List<BuildSite> sites = BuildSiteManager.get(level).all().stream().filter(x -> x.placement().equals(s.placement)).toList();
		boolean dig = sites.stream().anyMatch(BuildSite::isCellarOnly);
		sites.forEach(x -> BuildSiteManager.get(level).remove(x.id()));
		helper.assertTrue(dig, "three tenths standing, the reloaded job started no cellar dig: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
		helper.succeed();
	}

	// --- helpers -----------------------------------------------------------------------------------------------------

	private record Setup(BlueprintData.Placement placement, Blueprint blueprint, Villager builder, ItemStack job) {
	}

	/** An old Smithy (no cellar dug), a builder at his bench, and the "Dig the cellar" he sells for it, bought. */
	private static Setup setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ModTrades.builderHomes(villager);
		ItemStack job = villager.getOffers().stream().map(MerchantOffer::getResult).filter(r -> Cellars.isJob(r)
				&& BlueprintItem.data(r).flatMap(BlueprintData::placement).equals(Optional.of(placement)))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no \"Dig the cellar\" for the old Smithy")).copy();
		return new Setup(placement, BlueprintLibrary.get(level, StarterBlueprints.SMITHY.id()).orElseThrow(), villager, job);
	}

	/**
	 * Breaks the Smithy's blocks on and above the ground by hand (no builder's take-down). {@code keepEvery} 0: all of
	 * them; -1: all but the first drawn solid block, with short grass over the emptied floor; n: keeps 3 of every n
	 * (n = 10) or 1 of every n (otherwise).
	 */
	private static void tearDown(GameTestHelper helper, Setup s, int keepEvery) {
		ServerLevel level = helper.getLevel();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		int i = 0;
		boolean keptOne = false;
		for (Blueprint.Entry e : s.blueprint.blocks()) {
			if (e.pos().getY() < 0 || e.state().isAir()) {
				continue;
			}
			BlockPos p = s.placement.origin().offset(e.pos());
			int n = i++;
			boolean keep = switch (keepEvery) {
				case 0 -> false;
				case -1 -> !keptOne && solid(level.getBlockState(p));
				case 10 -> n % 10 < 3;
				default -> n % keepEvery == 0;
			};
			if (keep) {
				keptOne = true;
				continue;
			}
			level.removeBlockEntity(p);
			BlockState after = keepEvery == -1 && e.pos().getY() == 0 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
			level.setBlock(p, after, flags);
		}
	}

	/** As the wiki counts it: {standing, drawn}, the drawn blocks on and above the ground that are solid in the world. */
	private static int[] standing(GameTestHelper helper, Setup s) {
		int drawn = 0;
		int standing = 0;
		for (Blueprint.Entry e : s.blueprint.blocks()) {
			if (e.pos().getY() < 0 || e.state().isAir()) {
				continue;
			}
			drawn++;
			if (solid(helper.getLevel().getBlockState(s.placement.origin().offset(e.pos())))) {
				standing++;
			}
		}
		return new int[] {standing, drawn};
	}

	private static boolean solid(BlockState state) {
		return !state.isAir() && !state.canBeReplaced();
	}

	/** A server restart: the build-site data (finished records) and the builder saved and loaded again. */
	private static Villager restart(GameTestHelper helper, Villager builder) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		builder.saveWithoutId(tag);
		builder.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	/** The bought job as it comes back from the player's saved inventory. */
	private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		Tag saved = stack.save(registries);
		ItemStack back = ItemStack.parse(registries, saved).orElseThrow(() -> new GameTestAssertException("the job didn't load"));
		if (!Cellars.isJob(back)) {
			throw new GameTestAssertException("the reloaded job is no \"Dig the cellar\" any more: " + back.getHoverName().getString());
		}
		return back;
	}

	/** The builder's "Dig the cellar" trades, as the player reads them. */
	private static List<String> sold(Villager builder) {
		List<String> out = new ArrayList<>();
		builder.getOffers().stream().filter(o -> Cellars.isJob(o.getResult())).forEach(o -> out.add(o.getResult().getHoverName().getString()));
		return out;
	}

	/** The Village Hall's "Dig the cellar" advice for a hall by the bench, as the player reads it. */
	private static List<String> advised(GameTestHelper helper) {
		List<String> out = new ArrayList<>();
		VillageAdvice.tips(helper.getLevel(), helper.absolutePos(BENCH)).stream().filter(t -> t.key().equals("cellar"))
			.forEach(t -> out.add(t.title().getString()));
		return out;
	}

	/**
	 * The job given to the builder starts no site at the Smithy, the player is told the lang key's sentence for the Smithy
	 * (and its words are the wiki's), and he keeps the job; given a second time, the same.
	 */
	private static void refused(GameTestHelper helper, Setup s, Villager builder, ItemStack job, String who) {
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name(who));
		InteractionResult first = Builders.assign(c.player(), builder, job);
		InteractionResult second = Builders.assign(c.player(), builder, job);
		List<BuildSite> sites = BuildSiteManager.get(level).all().stream().filter(x -> x.placement().equals(s.placement)).toList();
		sites.forEach(x -> BuildSiteManager.get(level).remove(x.id()));
		List<String> told = QaBuilderEdges1104bGameTests.texts(c.seen());
		String line = Component.translatable(NO_DIG, "Smithy").getString();
		LOGGER.info("qa 1107e torn down {}: given {} / {}, sites {}, told {}", who, first, second, sites.size(), told);
		helper.assertTrue(sites.isEmpty(), "the job given to the builder started " + sites.size() + " site(s) under the torn-down Smithy (" + first + ")");
		helper.assertTrue(line.startsWith("There's no cellar to dig under the Smithy any more"), "the lang sentence reads: " + line);
		helper.assertTrue(told.stream().filter(line::equals).count() == 2, "the player wasn't told \"" + line + "\" each time: " + told);
		helper.assertTrue(!job.isEmpty() && Cellars.isJob(job), "the refused job was taken from the player");
	}
}
