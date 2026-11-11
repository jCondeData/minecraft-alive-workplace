package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * B1203, B1205: the counters Innkeepers work are remembered ({@link InnCounters}) through a save and reload, and an
 * Innkeeper gone for good (discarded, as a zombie's conversion does) leaves his counter.
 */
public class InnCountersB1203GameTests implements FabricGameTest {

	/** Two counters and their Innkeepers come back from a save; an old save without the file starts with none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void innCountersComeBackAfterASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		InnCounters data = new InnCounters();
		BlockPos a = new BlockPos(-30_001, 64, 12);
		BlockPos b = new BlockPos(-30_009, 70, -4);
		UUID ina = new UUID(0x1203L, 1);
		UUID ole = new UUID(0x1203L, 2);
		data.remember(a, ina);
		data.remember(b, ole);
		data.forget(new BlockPos(1, 2, 3)); // never remembered: nothing changes
		InnCounters read = InnCounters.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(ina.equals(read.keeper(a)) && ole.equals(read.keeper(b)), "the counters' Innkeepers after a save: " + read.keeper(a) + ", " + read.keeper(b));
		read.forget(a);
		InnCounters again = InnCounters.load(read.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(again.keeper(a) == null && ole.equals(again.keeper(b)), "a forgotten counter came back after a save");
		InnCounters old = InnCounters.load(new CompoundTag(), level.registryAccess());
		helper.assertTrue(old.keeper(a) == null, "a save from before B1203 should remember no counters");
		helper.succeed();
	}

	/** An Innkeeper discarded (gone for good) leaves his counter; the counter of one still here stays. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anInnkeeperGoneForGoodLeavesHisCounter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		InnCounters known = InnCounters.get(level);
		Villager gone = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
		Villager stays = helper.spawn(EntityType.VILLAGER, new BlockPos(0, 2, 0));
		BlockPos goneCounter = helper.absolutePos(new BlockPos(0, 2, 1));
		BlockPos staysCounter = helper.absolutePos(new BlockPos(0, 3, 1));
		known.remember(goneCounter, gone.getUUID());
		known.remember(staysCounter, stays.getUUID());
		Leftovers.after(helper, () -> {
			known.forget(goneCounter);
			known.forget(staysCounter);
			stays.discard();
		});
		gone.discard();
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(known.keeper(goneCounter) == null, "a discarded Innkeeper's counter is still remembered as his");
			helper.assertTrue(stays.getUUID().equals(known.keeper(staysCounter)), "another Innkeeper's counter was forgotten");
			helper.succeed();
		});
	}
}
