package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B624 and B626 (the Lost Caravan, 31.20) from the far side of each fix: only a standing hall is a village, so
 * a receiving hall broken and put back is one again and takes the goods; and a Tobin freed on day 6 still follows on
 * day 7 after a restart (B624 with a reload between).
 */
public class QaLostCaravanHallBack1102GameTests implements FabricGameTest {
	private static final String AREA = QaLostCaravanGameTests.AREA;

	/** The receiving hall broken, then put back where it stood: the goods are delivered there and the standing is given. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanHallBack1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanHallBack1102")
	public void qaTheGoodsAreDeliveredOnceTheHallIsPutBack(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3); // the receiving hall broken
		level.setBlock(b, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3); // and put back
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, me::discard);
		int before = Reputation.points(level, b, me.getUUID());
		me.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		me.getInventory().add(new ItemStack(Items.BREAD, 12));
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 0 && Reputation.points(level, b, me.getUUID()) > before,
			"the receiving hall, put back, didn't take the goods: " + me.getInventory().countItem(Items.OAK_LOG) + " logs still carried, standing "
				+ (Reputation.points(level, b, me.getUUID()) - before) + " more");
		helper.succeed();
	}

	/** Freed on day 6, the world restarted, then day 7: Tobin is still the arc's carter, following the rescuer. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanFreedReload1102"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanFreedReload1102")
	public void qaAFreedTobinStillFollowsOnDaySevenAfterARestart(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS + 1; // day 6
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		Leftovers.after(helper, () -> {
			tobin.discard();
			me.discard();
		});
		QaLostCaravanGameTests.reloadStories(level);
		ArcState back = Arcs.running(level, a);
		helper.assertTrue(back != null && me.getUUID().equals(LostCaravanStory.leader(back)), "setup: the arc didn't read back following: " + back);
		back.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS; // day 7, on the way home
		LostCaravanStory.tick(level);
		helper.assertTrue(Arcs.running(level, a) == back && tobin.isAlive() && me.getUUID().equals(LostCaravanStory.leader(back)),
			"Tobin, freed before a restart, was lost on day 7: arc running " + (Arcs.running(level, a) != null) + ", Tobin alive " + tobin.isAlive());
		helper.succeed();
	}
}
