package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertBuilt;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.assertNoFluidInBasement;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.cottageFour;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.fill;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.rules;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.stacks;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.wet;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.Setup;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Bugs found by QA (lane-b, 2026-11-04) in digging a basement or cellar into wet or hot ground (setup and helpers in
 * {@link QaBasementGround1104GameTests}). Fail today; each goes in with its fix.
 * <ul>
 * <li>Water sources in the earth beside a basement's walls flood the dug pit; the builder ends up standing in the flowing
 * water on the pit's floor and never gets out: the build stalls in CLEAR for good (Starter Cottage IV, and a Smithy's
 * "Dig the cellar").</li>
 * <li>Lava in the earth beside a basement's wall runs into the pit when the wall's spot is dug, and the builder burns to
 * death in it while still clearing.</li>
 * </ul>
 */
public class QaBasementGround1104BugTests implements FabricGameTest {
	// ---------------------------------------------------------------------------------------------------------- water

	/**
	 * Water sources in the earth just outside three of the Starter Cottage IV's basement walls and one under its floor:
	 * the dig opens the earth beside them. When the build is done no water stands in the basement's room (none is drawn
	 * there), the builder is alive and the whole build is as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaBasementWater"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaBasementWater")
	public void waterInTheEarthBesideTheBasementLeavesNoWaterInIt(GameTestHelper helper) {
		Setup s = cottageFour(helper, b -> wet(helper, b, Blocks.WATER));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int fluids = assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa basement water: built at tick {}, {} fluid spot(s) in the basement box", helper.getTick(), fluids);
		});
	}

	// ----------------------------------------------------------------------------------------------------------- lava

	/**
	 * Lava in the same spots, with fire spread on: when the build is done no lava or fire is in or round the basement
	 * and the house, the builder is alive and not burning, and every drawn block of the whole Cottage IV stands as
	 * drawn (nothing of the house burnt away).
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaBasementLava"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaBasementLava")
	public void lavaInTheEarthBesideTheBasementBurnsNothingAndKillsNoOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
		Setup s = cottageFour(helper, b -> wet(helper, b, Blocks.LAVA));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			BoundingBox box = s.plan().bounds().inflatedBy(2);
			List<String> burning = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (s.level().getBlockState(p).is(BlockTags.FIRE)) {
					burning.add(p.subtract(s.site().placement().origin()).toShortString());
				}
			}
			helper.assertTrue(burning.isEmpty(), burning.size() + " fire block(s) in or round the finished cottage, e.g. " + burning.stream().limit(4).toList());
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			helper.assertFalse(s.villager().isOnFire(), "the builder is burning at " + helper.relativePos(s.villager().blockPosition()));
			LOGGER.info("qa basement lava: built at tick {}, builder health {}", helper.getTick(), s.villager().getHealth());
		});
	}

	// --------------------------------------------------------------------------------------------- the Smithy's cellar

	/**
	 * A Smithy finished before cellars (its coal store still earth), water in the earth beside the coal store and under
	 * it; its builder's "Dig the cellar" job, given to him: when done, no water stands in the cellar, the builder is
	 * alive, and the whole Smithy is as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaCellarWater"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaCellarWater")
	public void waterInTheEarthBesideASmithysCellarDigLeavesNoWaterInIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = StarterBlueprints.SMITHY.id();
		BlueprintData.Placement placement = oldBuild(helper, StarterBlueprints.SMITHY, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		List<BlockPos> wet = wet(helper, blueprint, Blocks.WATER);
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		Cellars.offer(level, villager);
		MerchantOffer trade = villager.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(id) && d.placement().equals(Optional.of(placement))).orElse(false))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no \"Dig the cellar\" trade for the old Smithy"));
		List<ItemStack> stock = new ArrayList<>();
		BuildPlan.create(Cellars.cellarOnly(blueprint), placement).materials().forEach((item, count) -> stock.addAll(stacks(item, count)));
		fill(helper, stock);
		BlueprintData data = BlueprintItem.data(trade.getResult()).orElseThrow();
		BuildSite site = Builders.start(level, villager, null, data.structure(), data.placement().orElseThrow());
		helper.assertTrue(site.isCellarOnly(), "setup: the job's blueprint didn't start a cellar dig");
		BuildPlan plan = site.plan(level);
		Setup s = new Setup(level, villager, site, plan, blueprint);
		watchDeath(helper, s);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			assertNoFluidInBasement(helper, s);
			List<BlockPos> wrong = BuildPlan.create(blueprint, placement).unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the whole Smithy wrong after the dig, e.g. "
				+ wrong.stream().limit(4).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(villager.isAlive(), "the builder died");
			LOGGER.info("qa cellar water: dug at tick {} with water at {}", helper.getTick(), wet);
		});
	}
}
