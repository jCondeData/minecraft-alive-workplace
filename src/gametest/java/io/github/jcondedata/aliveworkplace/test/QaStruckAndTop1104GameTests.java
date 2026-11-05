package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1104-2137), verifying B928/B952 (a worker struck by a REAL lightning bolt loses nothing: bag and one
 * blueprint dropped out of the bolt's reach, the site freed) and B937/B953 (blocks over the world's top or under its
 * bottom are skipped without materials, the build ends, the held blueprint's lists don't ask for them), from the specs.
 * Adversarial cases the fixes' own tests don't cover (two more, failing, in QaStruckAndTop1104BugTests): a struck builder beside lava with his bench covered, two builders
 * under one bolt, a second bolt on the same spot, a real bolt then a save and load and a new builder finishing the hut;
 * a hut whose floor is exactly the world's bottom layer (nothing skipped), the held blueprint's lists exactly at the top
 * and bottom (everything asked for, a shortfall reported), a hut sunk under the bottom saved and loaded mid-build, and
 * a blueprint laid wholly over the top.
 */
public class QaStruckAndTop1104GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-test");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int PLANKS = 20;
	private static final int COBBLE = 7;

	// ------------------------------------------------------------------------------------------------ real bolts

	/**
	 * A builder by a lava pool, his bench out of the bolt's reach but with a carpet on it (nothing can lie on top): a real
	 * bolt turns him; his bag and blueprint lie on open ground out of the bolt's reach, none of it in the lava or burning.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaStruckLava"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaStruckLava")
	public void aRealBoltOnABuilderByLavaWithHisBenchCoveredLeavesEverythingOnSafeGround(GameTestHelper helper) {
		struckInPlace(helper, new BlockPos(22, 2, 15), List.of(new BlockPos(15, 2, 15)), new int[] {0}, h -> {
			for (int x = 17; x <= 19; x++) {
				for (int z = 11; z <= 19; z++) {
					h.setBlock(new BlockPos(x, 1, z), Blocks.LAVA);
				}
			}
			for (int z = 11; z <= 19; z++) {
				h.setBlock(new BlockPos(14, 1, z), Blocks.LAVA); // lava on the bench's far side of him too
			}
			h.setBlock(new BlockPos(22, 3, 15), Blocks.WHITE_CARPET);
		});	}

	/** One real bolt on two builders of the same bench side by side: two blueprints and both bags survive, nothing doubled. */
	//$ gametest_ticks_batch HUGE '400' '"qaStruckPair"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaStruckPair")
	public void oneRealBoltOnTwoBuildersLeavesBothBlueprintsAndBothBags(GameTestHelper helper) {
		struckInPlace(helper, new BlockPos(14, 2, 6), List.of(new BlockPos(6, 2, 6), new BlockPos(7, 2, 6)), new int[] {0}, h -> {
		});
	}

	/** A storm's second bolt on the same spot ten ticks after the first (on the witch): what the builder left survives both. */
	//$ gametest_ticks_batch HUGE '400' '"qaStruckAgain"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaStruckAgain")
	public void aSecondRealBoltOnTheSameSpotLeavesTheBagAndBlueprint(GameTestHelper helper) {
		struckInPlace(helper, new BlockPos(6, 2, 14), List.of(new BlockPos(6, 2, 6)), new int[] {0, 10, 20}, h -> {
		});
	}

	/**
	 * The owner hands the hut over; a REAL bolt turns the builder mid-STRUCTURE; the world is saved and loaded (the build
	 * sites from their save). The site doesn't come back, the owner was told the spot the blueprint lies on, he picks up
	 * what fell (the materials back in the chest), lays the blueprint again and hands it to a second builder, who
	 * finishes the hut with every material accounted for.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaStruckReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaStruckReload")
	public void aRealBoltThenASaveAndLoadAndANewBuilderFinishesTheHutWithNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		QaBuilderEdges1104bGameTests.protectionOff(helper);
		noFireSpread(helper);
		QaBuilderEdges1104bGameTests.Layout l = QaBuilderEdges1104bGameTests.layout(helper, 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaSR"));
		Villager first = QaBuilderEdges1104bGameTests.builder(helper, l, new BlockPos(3, 2, 3));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaBuilderEdges1104bGameTests.placed(placement));
		QaBuilderEdges1104bGameTests.hand(c, first, false);
		BuildSite site = Builders.activeSite(level, first);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over started no build: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
		UUID siteId = site.id();
		QaBuilderTerrain1102GameTests.Run r = QaBuilderEdges1104bGameTests.run(level, first, site, l);
		String hut = Blueprints.displayName(QaBuilderEdges1104bGameTests.TEST_HUT).getString();
		long[] struckAt = {-1};
		Map<Item, Integer> bag = new HashMap<>();
		Villager[] second = {null};
		UUID[] secondSite = {null};
		helper.onEachTick(() -> {
			QaBuilderEdges1104bGameTests.calmWitches(helper);
			if (struckAt[0] < 0) {
				BuildSite s = r.site();
				if (s == null || s.stage() != BuildPlan.Stage.STRUCTURE || s.cursor() < 4 || QaBuilderConverted1104GameTests.bag(first, r.plan()).isEmpty()) {
					return;
				}
				bag.putAll(QaBuilderConverted1104GameTests.bag(first, r.plan()));
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
				bolt.moveTo(Vec3.atBottomCenterOf(first.blockPosition()));
				level.addFreshEntity(bolt);
				struckAt[0] = helper.getTick();
				return;
			}
			if (second[0] != null || helper.getTick() - struckAt[0] < 40) {
				return;
			}
			helper.assertTrue(!first.isAlive(), "setup: the bolt didn't turn the builder");
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolt still flashes");
			// a save and a load of the build sites
			BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			level.getDataStorage().set(BuildSiteManager.NAME, loaded);
			BuildSite back = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(back == null || !first.getUUID().equals(back.builder()), "after the strike and a reload the gone builder still has his site");
			ItemEntity lying = null;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				if (e.getItem().is(ModItems.BLUEPRINT)) {
					helper.assertTrue(lying == null, "two blueprints fell");
					lying = e;
				}
			}
			helper.assertTrue(lying != null, "no blueprint survived the real bolt (his bag was " + bag + ")");
			BlockPos at = lying.blockPosition();
			String told = "Lightning turned your builder into a witch while building " + hut + ". Their tools and the blueprint dropped at "
				+ at.getX() + " " + at.getY() + " " + at.getZ() + ".";
			helper.assertTrue(QaBuilderEdges1104bGameTests.texts(c.seen()).contains(told), "the owner wasn't told \"" + told + "\"; he saw "
				+ QaBuilderEdges1104bGameTests.texts(c.seen()));
			ItemStack fallen = lying.getItem().copy();
			Container chest = helper.getBlockEntity(l.supplies().get(0));
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				if (!e.getItem().is(ModItems.BLUEPRINT)) {
					ItemStack rest = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, chest, e.getItem().copy(), null);
					helper.assertTrue(rest.isEmpty(), "the chest is full: " + rest);
				}
				e.discard();
			}
			fallen.set(ModComponents.BLUEPRINT, BlueprintItem.data(fallen).orElseThrow().withPlacement(Optional.of(placement)));
			second[0] = QaBuilderEdges1104bGameTests.builder(helper, l, new BlockPos(3, 2, 3));
			c.player().setItemInHand(InteractionHand.MAIN_HAND, fallen);
			int seen = c.seen().size();
			QaBuilderEdges1104bGameTests.hand(c, second[0], false);
			BuildSite next = Builders.activeSite(level, second[0]);
			helper.assertTrue(next != null && !next.isDeconstruction(), "the new builder didn't take the fallen blueprint after the reload: "
				+ QaBuilderEdges1104bGameTests.texts(c.seen()).subList(seen, c.seen().size()));
			secondSite[0] = next.id();
			r.builder()[0] = second[0];
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(secondSite[0] != null, "not handed to the new builder yet");
			BuildSite s = BuildSiteManager.get(level).get(secondSite[0]);
			helper.assertTrue(s == null, "the new builder's hut isn't finished: " + (s == null ? "" : "stage=" + s.stage() + " status=" + s.status()));
			List<BlockPos> wrong = r.plan().unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not as drawn after the new builder finished");
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + c.player().getInventory().countItem(e.getKey());
				if (got != QaBuilderTerrain1102GameTests.EXTRA) {
					off.put(e.getKey(), "loose " + got);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost across a real bolt, a reload and a second builder (wanted "
				+ QaBuilderTerrain1102GameTests.EXTRA + " of each left over; the struck bag was " + bag + "): " + off);
			LOGGER.info("qa struck: real bolt (bag {}), reload, second builder finished the hut", bag);
		});
	}

	// ------------------------------------------------------------------------------------------- top and bottom

	/**
	 * A hut whose floor is exactly the world's bottom layer (y -64 in the Overworld), handed over by the player: no
	 * warning of the bottom (or the top), the hut is built whole with nothing skipped, and exactly its list is used.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaBottomFits"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaBottomFits")
	public void aHutWhoseFloorIsTheWorldsBottomLayerIsBuiltWholeWithNoWarning(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.protectionOff(helper);
		int min = level.getMinBuildHeight();
		int g = QaBuilderEdges1104bGameTests.bottom(helper);
		QaBuilderEdges1104bGameTests.Layout l = QaBuilderEdges1104bGameTests.layout(helper, g);
		BlockPos origin = new BlockPos(6, g - 1, 6);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(helper.absolutePos(origin).getY() == min, "setup: the floor isn't on the bottom layer: " + helper.absolutePos(origin));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaBF"));
		Villager v = QaBuilderEdges1104bGameTests.builder(helper, l, new BlockPos(3, g, 3));
		ItemStack bp = QaBuilderEdges1104bGameTests.placed(placement);
		bp.set(ModComponents.BLUEPRINT, BlueprintItem.data(bp).orElseThrow().withLevelGround(false));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, bp);
		QaBuilderEdges1104bGameTests.hand(c, v, false);
		BuildSite site = Builders.activeSite(level, v);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over on the bottom layer started no build: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
		List<String> texts = QaBuilderEdges1104bGameTests.texts(c.seen());
		helper.assertFalse(texts.stream().anyMatch(t -> t.contains("below the bottom of the world") || t.contains("over the world's build limit")),
			"a hut that fits on the bottom layer was warned: " + texts);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderEdges1104bGameTests.run(level, v, site, l);
		QaBuilderTerrain1102GameTests.watchDeath(helper, r);
		BuildSite[] last = {site};
		helper.onEachTick(() -> {
			if (r.site() != null) {
				last[0] = r.site();
			}
		});
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			helper.assertTrue(last[0].skipped() == 0, last[0].skipped() + " block(s) skipped by a hut that fits on the world's bottom layer");
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + c.player().getInventory().countItem(e.getKey());
				if (got != QaBuilderTerrain1102GameTests.EXTRA) {
					off.put(e.getKey(), "loose " + got + ", wanted " + QaBuilderTerrain1102GameTests.EXTRA);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost by the hut on the bottom layer: " + off);
		});
	}

	/**
	 * The held blueprint's lists for a hut that fits exactly (its roof the layer under the top, or its floor the bottom
	 * layer): the Material List book asks for the whole list, and the tooltip's supply report, with the chest one oak
	 * plank short, says exactly 1 oak plank missing; with that plank put in, nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaListsFitTop"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaListsFitTop")
	public void theHeldBlueprintsListsForAHutExactlyUnderTheTopAskForEverything(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		int g = QaBuilderEdges1104bGameTests.top(helper, 4);
		listsFit(helper, new BlockPos(2, g, 2), new BlockPos(2, g, 4), new BlockPos(6, g, 6), "top");
	}

	/** The same for a hut whose floor is the world's bottom layer. */
	//$ gametest_ticks_batch HUGE '100' '"qaListsFitBottom"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaListsFitBottom")
	public void theHeldBlueprintsListsForAHutOnTheBottomLayerAskForEverything(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int min = level.getMinBuildHeight() - helper.absolutePos(BlockPos.ZERO).getY();
		for (int x = -1; x <= 24; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = min; y < min + 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		Leftovers.after(helper, () -> {
			for (int x = -1; x <= 24; x++) {
				for (int z = -1; z <= 16; z++) {
					for (int y = min; y < min + 12; y++) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		});
		listsFit(helper, new BlockPos(2, min, 2), new BlockPos(2, min, 4), new BlockPos(6, min, 6), "bottom");
	}

	/**
	 * The hut sunk with its floor one layer under the world's bottom, handed over by the player, then saved and loaded
	 * (sites and builder) mid-STRUCTURE: the floor is still skipped (counted), every block in the world that can stand is
	 * built, the build ends and nothing is made or lost.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaBottomReload"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaBottomReload")
	public void aHutSunkUnderTheBottomSavedAndLoadedMidBuildEndsWithNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.protectionOff(helper);
		int min = level.getMinBuildHeight();
		int g = QaBuilderEdges1104bGameTests.bottom(helper);
		QaBuilderEdges1104bGameTests.Layout l = QaBuilderEdges1104bGameTests.layout(helper, g);
		BlockPos origin = new BlockPos(6, g - 2, 6);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(helper.absolutePos(origin).getY() == min - 1, "setup: the floor isn't one under the bottom: " + helper.absolutePos(origin));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaBR"));
		Villager v = QaBuilderEdges1104bGameTests.builder(helper, l, new BlockPos(3, g, 3));
		ItemStack bp = QaBuilderEdges1104bGameTests.placed(placement);
		bp.set(ModComponents.BLUEPRINT, BlueprintItem.data(bp).orElseThrow().withLevelGround(false));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, bp);
		QaBuilderEdges1104bGameTests.hand(c, v, false);
		BuildSite site = Builders.activeSite(level, v);
		helper.assertTrue(site != null && !site.isDeconstruction(), "the hand-over under the bottom started no build: " + QaBuilderEdges1104bGameTests.texts(c.seen()));
		QaBuilderTerrain1102GameTests.Run r = QaBuilderEdges1104bGameTests.run(level, v, site, l);
		BuildSite[] last = {site};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s == null) {
				return;
			}
			last[0] = s;
			if (!reloaded[0] && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() > 3) {
				reloaded[0] = true;
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, level, r.builder()[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.site() == null, "the hut under the bottom never ends after a reload: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(reloaded[0], "the build was never saved and loaded mid-structure");
			helper.assertTrue(last[0].skipped() > 0, "the floor under the bottom wasn't counted as skipped");
			List<String> unbuilt = r.plan().unfinished(level).stream().filter(p -> !level.isOutsideBuildHeight(p) && r.plan().targets().get(p).canSurvive(level, p))
				.map(p -> p.subtract(placement.origin()).toShortString()).toList();
			helper.assertTrue(unbuilt.isEmpty(), "blocks in the world left unbuilt after the reload (hut-relative): " + unbuilt);
			Map<Item, Integer> stands = QaBuilderEdges1104bGameTests.standing(level, r.plan());
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
				int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey()) + c.player().getInventory().countItem(e.getKey());
				int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA - stands.getOrDefault(e.getKey(), 0);
				if (got != want) {
					off.put(e.getKey(), "loose " + got + ", wanted " + want);
				}
			}
			helper.assertTrue(off.isEmpty(), "materials made or lost by the hut under the bottom across a reload: " + off);
		});
	}

	/**
	 * A blueprint laid on the very top layer of the world (the clicked block at y 319, so the whole hut would stand from y
	 * 320 up): nothing of it can stand. Whether the hand-over refuses it or the build ends at once, no material leaves the
	 * chests, nothing stands, the builder isn't left on a site, and the player doesn't lose his blueprint (it
	 * comes back to the chests, as at the end of any build). Here the ground
	 * under the footprint is as high as the clicked block, so there is nothing to fill under it either.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaWhollyOver"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaWhollyOver")
	public void aBlueprintLaidWhollyOverTheTopUsesNothingAndKeepsTheBlueprint(GameTestHelper helper) {
		whollyOver(helper, true);
	}

	/**
	 * {@link #aBlueprintLaidWhollyOverTheTopUsesNothingAndKeepsTheBlueprint}: the clicked block at y 319 on top of ground
	 * as high as it under the whole footprint ({@code flush}), or a lone pillar over lower ground.
	 */
	static void whollyOver(GameTestHelper helper, boolean flush) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.protectionOff(helper);
		int max = level.getMaxBuildHeight();
		int g = QaBuilderEdges1104bGameTests.top(helper, 4);
		QaBuilderEdges1104bGameTests.Layout l = QaBuilderEdges1104bGameTests.layout(helper, g);
		int topRel = max - helper.absolutePos(BlockPos.ZERO).getY();
		helper.setBlock(new BlockPos(6, topRel - 1, 6), Blocks.STONE); // the block he clicks, on the world's top layer
		if (flush) {
			// a mountain top as high as the world: ground up to y 319 under the whole footprint, nothing to fill
			for (int x = 6; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					for (int y = g; y < topRel; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, topRel, 6)), Rotation.NONE, Mirror.NONE);
		helper.assertTrue(placement.origin().getY() == max, "setup: the hut isn't wholly over the top: " + placement.origin());
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaWO"));
		Villager v = QaBuilderEdges1104bGameTests.builder(helper, l, new BlockPos(3, g, 3));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaBuilderEdges1104bGameTests.placed(placement));
		QaBuilderEdges1104bGameTests.hand(c, v, false);
		BuildSite site = Builders.activeSite(level, v);
		LOGGER.info("qa wholly over: hand-over {} said {}", site == null ? "refused" : "taken", QaBuilderEdges1104bGameTests.texts(c.seen()));
		QaBuilderTerrain1102GameTests.Run r = site == null ? null : QaBuilderEdges1104bGameTests.run(level, v, site, l);
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, QaBuilderEdges1104bGameTests.TEST_HUT).orElseThrow(), placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(Builders.activeSite(level, v) == null, "the builder is still on the hut wholly over the top: "
				+ (r == null ? "" : QaBuilderTerrain1102GameTests.state(helper, r)));
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
				int got = c.player().getInventory().countItem(e.getKey()) + ModAttachments.BUILDER_BAG.getOrCreate(v).count(e.getKey());
				for (BlockPos p : l.supplies()) {
					got += ((Container) helper.getBlockEntity(p)).countItem(e.getKey());
				}
				for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
					got += ie.getItem().is(e.getKey()) ? ie.getItem().getCount() : 0;
				}
				if (got != e.getValue() + QaBuilderTerrain1102GameTests.EXTRA) {
					off.put(e.getKey(), got + " of " + (e.getValue() + QaBuilderTerrain1102GameTests.EXTRA));
				}
			}
			helper.assertTrue(off.isEmpty(), "materials taken by a hut that can't stand anywhere: " + off + "; he read "
				+ QaBuilderEdges1104bGameTests.texts(c.seen()));
			int blueprints = c.player().getInventory().countItem(ModItems.BLUEPRINT) + QaBuilderEdges1104bGameTests.blueprints(helper)
				+ (v.getMainHandItem().is(ModItems.BLUEPRINT) ? 1 : 0) + v.getInventory().countItem(ModItems.BLUEPRINT);
			for (BlockPos p : l.supplies()) {
				blueprints += ((Container) helper.getBlockEntity(p)).countItem(ModItems.BLUEPRINT); // a finished build's comes back to its chests
			}
			helper.assertTrue(blueprints == 1, blueprints + " blueprint(s) left of the one handed over (player, chests, ground, builder)");
			LOGGER.info("qa wholly over: ended with nothing used; the player read {}", QaBuilderEdges1104bGameTests.texts(c.seen()));
		});
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static void noFireSpread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	/**
	 * Builders of the bench at {@code bench} (each with a hut of his own to build, far off), standing still at {@code
	 * stand} with {@value #PLANKS} oak planks and {@value #COBBLE} cobblestone in his bag, struck by real bolts at the
	 * first one's spot after each delay of {@code bolts}; {@code terrain} shapes the ground first. Once the bolts are gone:
	 * every builder a witch, his site gone, one blueprint each and every bag item on the ground, none in lava or burning.
	 */
	static void struckInPlace(GameTestHelper helper, BlockPos bench, List<BlockPos> stand, int[] bolts, Consumer<GameTestHelper> terrain) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		noFireSpread(helper);
		terrain.accept(helper);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		List<Villager> builders = new ArrayList<>();
		for (int i = 0; i < stand.size(); i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, stand.get(i));
			v.setNoAi(true);
			Builders.employ(level, v, helper.absolutePos(bench));
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(20, 2, 2 + 9 * i)),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = Builders.start(level, v, null, QaBuilderEdges1104bGameTests.TEST_HUT, placement);
			helper.assertTrue(site != null, "setup: builder " + i + "'s hut didn't start");
			UUID id = site.id();
			Leftovers.after(helper, () -> {
				if (BuildSiteManager.get(level).get(id) != null) {
					BuildSiteManager.get(level).remove(id);
				}
			});
			ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.OAK_PLANKS, PLANKS));
			ModAttachments.BUILDER_BAG.getOrCreate(v).add(new ItemStack(Items.COBBLESTONE, COBBLE));
			builders.add(v);
		}
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(stand.get(0)));
		for (int delay : bolts) {
			helper.runAfterDelay(2 + delay, () -> {
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
				bolt.moveTo(at);
				level.addFreshEntity(bolt);
			});
		}
		helper.onEachTick(() -> QaBuilderEdges1104bGameTests.calmWitches(helper));
		int lastBolt = bolts[bolts.length - 1];
		helper.runAfterDelay(lastBolt + 60, () -> {
			int n = builders.size();
			Map<Item, Integer> ground = new HashMap<>();
			List<String> where = new ArrayList<>();
			List<String> bad = new ArrayList<>();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				ground.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
				BlockPos p = e.blockPosition();
				where.add(e.getItem().getCount() + " " + e.getItem().getItem() + " at " + helper.relativePos(p).toShortString());
				if (level.getFluidState(p).is(FluidTags.LAVA) || e.isOnFire() || !level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
					bad.add(e.getItem() + " at " + helper.relativePos(p).toShortString() + " (" + level.getBlockState(p).getBlock() + (e.isOnFire() ? ", burning" : "") + ")");
				}
			}
			LOGGER.info("qa struck: {} builder(s), bolts at {}: on the ground {}", n, java.util.Arrays.toString(bolts), where);
			for (Villager v : builders) {
				helper.assertTrue(!v.isAlive(), "setup: a bolt didn't turn a builder");
				helper.assertTrue(Builders.activeSite(level, v) == null, "a struck builder still has a site");
			}
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolts still flash");
			helper.assertTrue(level.getEntitiesOfClass(Witch.class, helper.getBounds().inflate(16)).size() == n, "not " + n + " witch(es)");
			helper.assertTrue(bad.isEmpty(), "dropped items in lava, burning or inside a block: " + bad);
			int blueprints = ground.getOrDefault(ModItems.BLUEPRINT, 0);
			helper.assertTrue(blueprints == n && ground.getOrDefault(Items.OAK_PLANKS, 0) == PLANKS * n && ground.getOrDefault(Items.COBBLESTONE, 0) == COBBLE * n,
				"after " + bolts.length + " real bolt(s) on " + n + " builder(s) (each with " + PLANKS + " planks, " + COBBLE + " cobblestone and a blueprint) "
					+ "the ground holds " + ground + ": " + where);
			helper.succeed();
		});
	}

	/** {@link #theHeldBlueprintsListsForAHutExactlyUnderTheTopAskForEverything}: the hut at {@code origin} fits; one plank short, then none. */
	private static void listsFit(GameTestHelper helper, BlockPos bench, BlockPos chest, BlockPos origin, String where) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		helper.setBlock(chest, Blocks.CHEST);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, QaBuilderEdges1104bGameTests.TEST_HUT).orElseThrow(), placement);
		int max = level.getMaxBuildHeight();
		int min = level.getMinBuildHeight();
		helper.assertTrue(where.equals("top") ? plan.bounds().maxY() == max - 1 : plan.bounds().minY() == min,
			"setup: the hut doesn't exactly touch the world's " + where + ": " + plan.bounds());
		helper.assertTrue(QaBuilderEdges1104bGameTests.underLimitNeed(level, plan).equals(plan.materials()),
			"setup: not every block fits: " + QaBuilderEdges1104bGameTests.underLimitNeed(level, plan) + " vs " + plan.materials());
		Container c = helper.getBlockEntity(chest);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			int count = e.getValue() - (e.getKey() == Items.OAK_PLANKS ? 1 : 0);
			for (int left = count; left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				c.setItem(slot++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		BlueprintData data = new BlueprintData(QaBuilderEdges1104bGameTests.TEST_HUT, Optional.empty(), Optional.of(placement));
		SupplyReport shortReport = BlueprintSupplies.check(level, data).orElseThrow();
		List<String> missing = shortReport.missing().stream().map(m -> m.count() + " " + m.item()).toList();
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		Map<Item, Integer> listed = new HashMap<>();
		list.lines().forEach(line -> listed.put(line.item(), line.need()));
		helper.assertTrue(listed.equals(plan.materials()), "at the world's " + where + " the Material List book lists " + listed + ", not the whole list " + plan.materials());
		helper.assertTrue(list.toBring() == 1, "at the world's " + where + " one plank short, the book says " + list.toBring() + " still to bring, not 1");
		helper.assertTrue(shortReport.missing().equals(List.of(new SupplyReport.Missing(Items.OAK_PLANKS, 1))),
			"at the world's " + where + " one plank short, the supply report says missing " + missing);
		c.setItem(slot, new ItemStack(Items.OAK_PLANKS));
		SupplyReport full = BlueprintSupplies.check(level, data).orElseThrow();
		helper.assertTrue(full.missing().isEmpty(), "at the world's " + where + " with everything in the chest the report says missing "
			+ full.missing().stream().map(m -> m.count() + " " + m.item()).toList());
		helper.succeed();
	}
}
