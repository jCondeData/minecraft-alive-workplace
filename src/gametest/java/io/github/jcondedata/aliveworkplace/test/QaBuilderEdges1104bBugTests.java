package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-04, the tester of B928 and B937), bugs: see the report. Not registered on main (they fail on the mod as it
 * is); the builder registers each with its fix. Helpers in {@link QaBuilderEdges1104bGameTests}.
 */
public class QaBuilderEdges1104bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B928 with a real bolt (a thunderstorm's, or /summon lightning_bolt), not only its hit: one bolt lands on the builder
	 * mid-STRUCTURE with materials in his bag; fire spread is off, so only the bolt acts. As a death: exactly one blueprint
	 * lies where he was and every material of his bag is still there.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaEdgesRealBolt"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaEdgesRealBolt")
	public void aRealBoltOnABuilderLeavesTheBlueprintAndHisWholeBag(GameTestHelper helper) {
		bolts(helper, 1);
	}

	/** The same with two bolts on him in the same tick (/summon twice): nothing dropped twice, nothing lost, one blueprint. */
	//$ gametest_ticks_batch HUGE '6000' '"qaEdgesRealBolts"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaEdgesRealBolts")
	public void twoRealBoltsOnABuilderLeaveOneBlueprintAndHisWholeBag(GameTestHelper helper) {
		bolts(helper, 2);
	}

	private static void bolts(GameTestHelper helper, int n) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		Villager v = r.builder()[0];
		Map<Item, Integer> before = new HashMap<>();
		Map<Item, Integer> bag = new HashMap<>();
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			QaBuilderEdges1104bGameTests.calmWitches(helper);
			if (struckAt[0] >= 0 && helper.getTick() - struckAt[0] <= 8) {
				List<String> ground = new ArrayList<>();
				for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(16))) {
					ground.add(e.getItem().getCount() + " " + e.getItem().getItem() + (e.isOnFire() ? " (burning)" : ""));
				}
				QaBuilderEdges1104bGameTests.LOGGER.info("qa edges bolt: {} tick(s) after {} bolt(s): builder alive {}, bolts {}, on the ground {}", helper.getTick() - struckAt[0], n,
					v.isAlive(), level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).size(), ground);
			}
			BuildSite s = r.site();
			if (struckAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.STRUCTURE || s.cursor() < 4
				|| QaBuilderConverted1104GameTests.bag(v, r.plan()).isEmpty()) {
				return;
			}
			for (Item item : r.plan().materials().keySet()) {
				before.put(item, QaBuilderTerrain1102GameTests.loose(helper, r, item));
			}
			bag.putAll(QaBuilderConverted1104GameTests.bag(v, r.plan()));
			for (int i = 0; i < n; i++) {
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
				bolt.moveTo(Vec3.atBottomCenterOf(v.blockPosition()));
				level.addFreshEntity(bolt);
			}
			struckAt[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(struckAt[0] >= 0 && helper.getTick() - struckAt[0] >= 60, "not struck yet (or the bolts still flash)");
			helper.assertTrue(!v.isAlive(), "setup: the bolts didn't turn the builder");
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolts still flash");
			int blueprints = QaBuilderEdges1104bGameTests.blueprints(helper);
			Map<Item, String> off = new HashMap<>();
			for (Map.Entry<Item, Integer> e : before.entrySet()) {
				int now = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
				if (now != e.getValue()) {
					off.put(e.getKey(), e.getValue() + " -> " + now);
				}
			}
			helper.assertTrue(blueprints == 1 && off.isEmpty(), n + " real bolt(s) on the builder (his bag " + bag + "): " + blueprints
				+ " blueprint(s) on the ground, not 1; materials (chests, bags, ground) before -> after: " + off);
		});
	}

	/**
	 * B937, the held blueprint's tooltip (its supply report): the hut laid across the world's top (two layers under it, two
	 * over), the chest by the bench holding exactly what can stand under the top. The hand-over promises "those blocks
	 * will be left out and need no materials", so the report must say nothing is missing; and the Material List book
	 * (Book and Quill on the placed blueprint) must list only those blocks, nothing still to bring.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaEdgesTopTooltip"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaEdgesTopTooltip")
	public void theHeldBlueprintsListsAcrossTheTopAskOnlyForWhatCanStand(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int g = QaBuilderEdges1104bGameTests.top(helper, 2);
		BlockPos bench = new BlockPos(2, g, 2);
		BlockPos chest = new BlockPos(2, g, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(bench));
		helper.setBlock(chest, Blocks.CHEST);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, g, 6)), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, QaBuilderEdges1104bGameTests.TEST_HUT).orElseThrow(), placement);
		Map<Item, Integer> under = QaBuilderEdges1104bGameTests.underLimitNeed(level, plan);
		Container c = helper.getBlockEntity(chest);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : under.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				c.setItem(slot++, new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		BlueprintData data = new BlueprintData(QaBuilderEdges1104bGameTests.TEST_HUT, Optional.empty(), Optional.of(placement));
		List<String> wrong = new ArrayList<>();
		SupplyReport report = BlueprintSupplies.check(level, data).orElseThrow();
		if (!report.missing().isEmpty()) {
			wrong.add("the tooltip's supply report says missing " + report.missing().stream().map(m -> m.count() + " " + m.item()).toList());
		}
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		Map<Item, Integer> listed = new HashMap<>();
		list.lines().forEach(l -> listed.put(l.item(), l.need()));
		if (!listed.equals(under) || list.toBring() != 0) {
			wrong.add("the Material List book lists " + listed + " with " + list.toBring() + " still to bring");
		}
		helper.assertTrue(wrong.isEmpty(), "with the chest holding exactly what can stand under the top " + under + " (the whole list "
			+ plan.materials() + "): " + String.join("; ", wrong));
		helper.succeed();
	}
}
