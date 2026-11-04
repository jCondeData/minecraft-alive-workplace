package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * B952: a REAL lightning bolt (a thunderstorm's, /summon lightning_bolt) keeps striking every entity in its reach for
 * several ticks, so what B928 dropped where the struck worker stood burned the next tick. Now it's put down out of the
 * bolt's reach. These summon real bolts (not the visual-only hit B928GameTests calls), from QA's
 * QaBuilderEdges1104bBugTests on tests/builders-1104b.
 */
public class B952GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** One bolt on the builder mid-STRUCTURE with materials in his bag: one blueprint and his whole bag survive it. */
	//$ gametest_ticks_batch HUGE '6000' '"b952Bolt"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "b952Bolt")
	public void aRealBoltOnABuilderLeavesTheBlueprintAndHisWholeBag(GameTestHelper helper) {
		bolts(helper, 1);
	}

	/** Two bolts on him in the same tick: nothing dropped twice, nothing lost, one blueprint. */
	//$ gametest_ticks_batch HUGE '6000' '"b952Bolts"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "b952Bolts")
	public void twoRealBoltsOnABuilderLeaveOneBlueprintAndHisWholeBag(GameTestHelper helper) {
		bolts(helper, 2);
	}

	/** Any worker (no Blueprint Table to put it on): a real bolt leaves their bag and the owner's tool whole. */
	//$ gametest_ticks_batch HUGE '200' '"b952Worker"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "b952Worker")
	public void aRealBoltOnAnyWorkerLeavesTheirBagAndTool(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		noFireSpread(helper);
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		worker.setNoAi(true);
		worker.setVillagerData(worker.getVillagerData().setProfession(VillagerProfession.ARMORER));
		ModAttachments.BUILDER_BAG.getOrCreate(worker).add(new ItemStack(Items.IRON_INGOT, 12));
		ModAttachments.BUILDER_BAG.getOrCreate(worker).add(new ItemStack(Items.COAL, 5));
		worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
		worker.setDropChance(EquipmentSlot.MAINHAND, 2f); // the owner's: dropped, not lost
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		bolt.moveTo(Vec3.atBottomCenterOf(worker.blockPosition()));
		level.addFreshEntity(bolt);
		helper.onEachTick(() -> QaBuilderEdges1104bGameTests.calmWitches(helper));
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(!worker.isAlive(), "setup: the bolt didn't turn the worker");
			helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, helper.getBounds().inflate(16)).isEmpty(), "the bolt still flashes");
			Map<Item, Integer> ground = new HashMap<>();
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
				ground.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
			helper.assertTrue(ground.getOrDefault(Items.IRON_INGOT, 0) == 12 && ground.getOrDefault(Items.COAL, 0) == 5
				&& ground.getOrDefault(Items.IRON_AXE, 0) == 1, "a real bolt on the worker (12 iron, 5 coal, the owner's axe) left on the ground: " + ground);
			helper.succeed();
		});
	}

	private static void noFireSpread(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
	}

	private static void bolts(GameTestHelper helper, int n) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		QaBuilderEdges1104bGameTests.difficulty(helper, Difficulty.NORMAL);
		noFireSpread(helper);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderTerrain1102GameTests.start(helper, 2, new BlockPos(6, 2, 6));
		Villager v = r.builder()[0];
		Map<Item, Integer> before = new HashMap<>();
		Map<Item, Integer> bag = new HashMap<>();
		long[] struckAt = {-1};
		helper.onEachTick(() -> {
			QaBuilderEdges1104bGameTests.calmWitches(helper);
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
}
