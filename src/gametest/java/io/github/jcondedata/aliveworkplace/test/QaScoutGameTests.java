package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane D, 2026-10-24) for Scouts (ROADMAP 32.12): "the band ignores a scout more than 8 blocks off".
 * ScoutGameTests proves a scout 10 blocks off is left alone; these prove the other side of the boundary: a lair's
 * bandit does go for a scout 8 blocks off (not more than 8) and one 5 blocks off; and it is any lair's band, not just
 * the bandits': a desert lair's husk leaves a scout 10 blocks off alone.
 */
public class QaScoutGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos POST = new BlockPos(4, 2, 4);

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Scouts.forget(level);
			level.setDayTime(time);
		});
		level.setDayTime(1000);
	}

	/** A scout (a guard with a spyglass in the off hand) held where they stand. */
	private static Villager scout(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		villager.setNoAi(true);
		return villager;
	}

	/** A lair's bandit {@code blocks} from a scout (both on block centres in a line), unable to walk: it goes for the scout. */
	private static void seen(GameTestHelper helper, int blocks) {
		setUp(helper);
		Villager scout = scout(helper, new BlockPos(15, 2, 4));
		helper.assertTrue(Scouts.isScout(scout), "not a scout");
		Vindicator bandit = helper.spawn(EntityType.VINDICATOR, new BlockPos(15, 2, 4 + blocks));
		bandit.addTag(Lairs.TAG);
		bandit.addTag(Lairs.BANDIT_TAG);
		bandit.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); // (held where it stands: the distance is the test)
		helper.assertTrue(Math.abs(bandit.distanceTo(scout) - blocks) < 0.01, "the bandit stands " + bandit.distanceTo(scout) + " off");
		helper.assertFalse(Scouts.unseen(bandit, scout), "a scout " + blocks + " blocks off counts as unseen");
		helper.succeedWhen(() -> helper.assertTrue(bandit.getTarget() == scout,
			"the bandit didn't go for the scout " + blocks + " blocks off: " + bandit.getTarget()));
	}

	//$ gametest_ticks_batch HUGE '300' '"qaScoutEight"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaScoutEight")
	public void aBanditGoesForAScoutExactlyEightBlocksOff(GameTestHelper helper) {
		seen(helper, 8);
	}

	//$ gametest_ticks_batch HUGE '300' '"qaScoutFive"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaScoutFive")
	public void aBanditGoesForAScoutFiveBlocksOff(GameTestHelper helper) {
		seen(helper, 5);
	}

	/**
	 * Any lair's band, not just the bandits': a desert lair's husk (the lair's tag, no bandit tag) goes for a plain guard
	 * 10 blocks off, never for a scout as far the other way.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaScoutHusk"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaScoutHusk")
	public void aDesertLairsHuskDoesntGoForAScoutTenBlocksAway(GameTestHelper helper) {
		setUp(helper);
		Villager scout = scout(helper, new BlockPos(15, 2, 4));
		Villager plain = scout(helper, new BlockPos(15, 2, 24));
		plain.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		helper.assertTrue(Scouts.isScout(scout) && !Scouts.isScout(plain), "who is a scout");
		net.minecraft.world.entity.monster.Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(15, 2, 14));
		husk.addTag(Lairs.TAG);
		husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		husk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		helper.onEachTick(() -> {
			if (husk.getTarget() == scout) {
				helper.fail("the lair's husk went for the scout " + (int) husk.distanceTo(scout) + " blocks off");
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(husk.getTarget() == plain, "the husk went for nobody: " + husk.getTarget()));
	}
}
