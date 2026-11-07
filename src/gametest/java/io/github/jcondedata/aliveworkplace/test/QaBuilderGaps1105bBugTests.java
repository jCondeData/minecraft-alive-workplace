package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderGaps1105bGameTests.*;
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
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;

/**
 * QA (2026-11-05, second pass): the builder's gaps. Written from docs/wiki/cellars.md ("Dig the cellar" goes once "the
 * building [is] gone"): an old Smithy torn down by hand was still sold and dug (B974); one merely damaged is still
 * offered. Written from docs/wiki/builder.md (a struck builder's bag "on the nearest open ground at least 4 blocks
 * away"): a bag landed in a sealed cave under a wheat field, or shut in a sealed room under a stair roof, where a player
 * had to dig blind to the message's coordinates (B973). Helpers are {@link QaBuilderGaps1105bGameTests}'s.
 */
public class QaBuilderGaps1105bBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * The wiki: "the trade goes again once there is no cellar to dig (... the building gone ...)", and one bought before
	 * starts nothing ("There's no cellar to dig under the Smithy any more"), the player keeping it. An old Smithy torn down
	 * by hand (every block of it above the ground broken): the builder's trade goes when his trades are opened, the hall
	 * lists no dig, and the job bought before, given to him, starts no dig under the empty plot.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qaGapsBTornDown"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qaGapsBTornDown")
	public void aSmithyTornDownByHandIsNoLongerOfferedOrDug(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ModTrades.builderHomes(villager);
		ItemStack job = villager.getOffers().stream().map(MerchantOffer::getResult).filter(r -> Cellars.isJob(r)
				&& BlueprintItem.data(r).flatMap(BlueprintData::placement).equals(Optional.of(placement)))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no \"Dig the cellar\" for the old Smithy")).copy();
		// torn down by hand: every block of it on and above the ground broken (no builder's take-down)
		for (Blueprint.Entry e : BlueprintLibrary.get(level, id).orElseThrow().blocks()) {
			if (e.pos().getY() >= 0) {
				BlockPos p = placement.origin().offset(e.pos());
				level.removeBlockEntity(p);
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		ModTrades.builderHomes(villager); // the player opens his trades again
		List<String> still = new ArrayList<>();
		villager.getOffers().stream().filter(o -> Cellars.isJob(o.getResult())).forEach(o -> still.add(o.getResult().getHoverName().getString()));
		List<String> advice = new ArrayList<>();
		Cellars.toDig(level, placement.origin(), 64).forEach(f -> advice.add(f.structure().toString()));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaGT"));
		InteractionResult given = Builders.assign(c.player(), villager, job);
		boolean started = BuildSiteManager.get(level).all().stream().anyMatch(s -> s.placement().equals(placement));
		BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement)).map(BuildSite::id).toList()
			.forEach(sid -> BuildSiteManager.get(level).remove(sid));
		List<String> told = QaBuilderEdges1104bGameTests.texts(c.seen());
		LOGGER.info("qa gaps b torn down: trades after {}, hall lists {}, the job given: {} started {} told {}", still, advice, given, started, told);
		helper.assertTrue(still.isEmpty(), "the Smithy torn down by hand, the builder still sells " + still);
		helper.assertTrue(advice.isEmpty(), "the Smithy torn down by hand, the hall still advises digging its cellar");
		helper.assertFalse(started, "the job bought before, given to the builder, started a dig under the torn-down Smithy (" + given + ")");
		helper.assertTrue(told.stream().anyMatch(t -> t.startsWith("There's no cellar to dig under the Smithy any more")), "the player wasn't told there's no cellar to dig: " + told);
		helper.assertTrue(!job.isEmpty(), "the refused job was taken from the player");
		helper.succeed();
	}

	/**
	 * B974's other side: an old Smithy merely damaged (two of every five of its blocks above the ground broken, its walls
	 * holed and its roof open) is still there: the builder still sells "Dig the cellar", the hall still lists it, and the
	 * job given to him starts the cellar dig.
	 */
	//$ gametest_ticks_batch TALL_AREA '200' '"qaGapsBDamaged"'
	@GameTest(template = TALL_AREA, timeoutTicks = 200, batch = "qaGapsBDamaged")
	public void aSmithyDamagedButStandingIsStillOfferedAndDug(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		int broken = 0;
		int i = 0;
		for (Blueprint.Entry e : BlueprintLibrary.get(level, id).orElseThrow().blocks()) {
			if (e.pos().getY() >= 0 && !e.state().isAir() && i++ % 5 < 2) {
				BlockPos p = placement.origin().offset(e.pos());
				level.removeBlockEntity(p);
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				broken++;
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ModTrades.builderHomes(villager);
		Optional<ItemStack> job = villager.getOffers().stream().map(MerchantOffer::getResult).filter(r -> Cellars.isJob(r)
				&& BlueprintItem.data(r).flatMap(BlueprintData::placement).equals(Optional.of(placement)))
			.findFirst().map(ItemStack::copy);
		boolean advised = Cellars.toDig(level, placement.origin(), 64).stream().anyMatch(f -> f.placement().equals(placement));
		LOGGER.info("qa gaps b damaged: {} blocks broken, sold {}, advised {}", broken, job.isPresent(), advised);
		helper.assertTrue(broken > 20, "setup: only " + broken + " blocks broken");
		helper.assertTrue(job.isPresent(), "the damaged Smithy (" + broken + " blocks broken) is no longer sold its \"Dig the cellar\"");
		helper.assertTrue(advised, "the damaged Smithy is no longer listed by the hall's advice");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaGU"));
		Builders.assign(c.player(), villager, job.get());
		List<BuildSite> sites = BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement)).toList();
		boolean dig = sites.stream().anyMatch(BuildSite::isCellarOnly);
		sites.forEach(s -> BuildSiteManager.get(level).remove(s.id()));
		helper.assertTrue(dig, "the job given to the builder started no cellar dig under the damaged Smithy: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
		helper.succeed();
	}

	/**
	 * A builder struck in his wheat field (farmland under ripe wheat, 16 by 15), his Table 3 blocks east in the field, a
	 * sealed cave 4 to 6 blocks under the field: nothing can lie on the crops, so it all goes to the nearest open ground
	 * a player can walk to (the grass round the field), not into the cave under it.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBField"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBField")
	public void aBuilderStruckInAWheatFieldOverASealedCaveGetsHisBagWhereAPlayerCanReachIt(GameTestHelper helper) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(13, SURFACE, 10), new BlockPos(10, SURFACE, 10), h -> {
			earth(h, (x, z) -> field(x, z) ? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7) : Blocks.GRASS_BLOCK.defaultBlockState(),
				(x, z) -> field(x, z) ? Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7) : null);
			cave(h);
		}, h -> {
		}, (told, lying) -> reachable(helper, told));
	}
	/**
	 * As the bridge, but the sealed room under its end has a roof of stone-brick stairs (nothing can lie on it): it still
	 * all lies where a player can walk to it, not shut in the room under the roof.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBStairRoof"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBStairRoof")
	public void aBuilderStruckOnABridgeOverAStairRoofedRoomGetsHisBagWhereAPlayerCanReachIt(GameTestHelper helper) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(13, 8, 10), new BlockPos(10, 8, 10), h -> bridgeAndRoom(h, true), h -> {
		}, (told, lying) -> {
			reachable(helper, told);
			helper.assertFalse(inRoom(rel(helper, told)), "the message names " + rel(helper, told).toShortString() + ", inside the sealed room");
		});
	}
}
