package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-e-1109-0722, saves), the failing half of {@link QaCuredVillagerEdges1109e2GameTests}: since B1081's fix a
 * villager a zombie turns no longer widows their partner at the bite, only when the zombie villager is killed. A zombie
 * villager that leaves the world for good without dying (vanilla discards it on Peaceful, and one that never traded
 * despawns with no player near) leaves the partner married to someone who no longer exists, for good.
 */
public class QaCuredVillagerEdges1109e2BugTests implements FabricGameTest {
	private static final String AREA = QaCuredVillager1109eGameTests.AREA;

	/**
	 * Ada, married to Bea, is bitten; then the world is set to Peaceful, which takes every zombie villager away for good
	 * (vanilla discards monsters on Peaceful): Ada can never be cured, so Bea is a widow as after a death, not married to
	 * someone who no longer exists (she would never court again).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109e2Peaceful"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109e2Peaceful")
	public void aZombieVillagerGoneOnPeacefulLeavesHerHusbandAWidower(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		QaCuredVillagerEdges1109e2GameTests.bite(helper, ada, zv -> {
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zv.isRemoved(), "setup: the zombie villager is still here on Peaceful");
				Couples.Partner his = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(his == null && ModAttachments.WIDOWED_DAY.has(bea),
					"Ada's zombie villager is gone for good (Peaceful) and Bea is still married to it: Bea's partner " + his
						+ ", widowed " + ModAttachments.WIDOWED_DAY.get(bea));
				helper.succeed();
			});
		});
	}

	/**
	 * Ada, an unemployed villager born in the village who never traded (vanilla: such a zombie villager despawns when no
	 * player is within 128 blocks), married to Bea, is bitten; every player is far away and the zombie villager despawns. Bea is a widow, not
	 * married to someone who no longer exists.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109e2Despawn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109e2Despawn")
	public void aZombieVillagerThatDespawnsLeavesHerHusbandAWidower(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		ada.setNoAi(true);
		// born in the village (bred villagers are never marked persistent; the test helper marks every mob it spawns)
		try {
			java.lang.reflect.Field persistent = net.minecraft.world.entity.Mob.class.getDeclaredField("persistenceRequired");
			persistent.setAccessible(true);
			persistent.setBoolean(ada, false);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("setup: can't make Ada a villager born in the village: " + e);
		}
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		QaCuredVillagerEdges1109e2GameTests.bite(helper, ada, zv -> {
			net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
			BlockPos far = helper.absolutePos(new BlockPos(2, 2, 2)).offset(300, 0, 0);
			player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(zv.isRemoved(), "setup: the zombie villager didn't despawn with the only player 300 blocks off (xp "
					+ zv.getVillagerXp() + ", persistent " + zv.isPersistenceRequired() + ")");
				Couples.Partner his = ModAttachments.PARTNER.get(bea);
				helper.assertTrue(his == null && ModAttachments.WIDOWED_DAY.has(bea),
					"Ada's zombie villager despawned and Bea is still married to it: Bea's partner " + his + ", widowed " + ModAttachments.WIDOWED_DAY.get(bea));
				helper.succeed();
			});
		});
	}

}
