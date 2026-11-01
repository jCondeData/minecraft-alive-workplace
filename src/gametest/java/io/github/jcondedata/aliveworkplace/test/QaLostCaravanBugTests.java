package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import java.util.ArrayList;
import java.util.List;
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

/** The Lost Caravan (31.20): spec mismatches found by QA. Each fails until the mod is fixed. */
public class QaLostCaravanBugTests implements FabricGameTest {
	private static final String AREA = QaLostCaravanGameTests.AREA;

	/**
	 * "After 7 days without a rescue, Tobin finds his own way home with nothing": a player who opened the cage on day 6
	 * and is walking him home has rescued him. On day 7 he must keep following; instead he vanishes, the goods are lost
	 * and the rescuer is told "Nobody came for Tobin".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanBugDaySeven"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanBugDaySeven")
	public void aFreedTobinIsNotLostOnTheSeventhDay(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer me = QaLostCaravanGameTests.listener(helper, seen, a.getX() + 0.5, a.getY(), a.getZ() + 6.5, "QaRescuer");
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS + 1; // day 6
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		Leftovers.after(helper, tobin::discard);
		s.beganDay = Chronicle.day(level) - LostCaravanStory.RESCUE_DAYS; // day 7, on the way home
		LostCaravanStory.tick(level);
		List<String> chat = QaLostCaravanGameTests.texts(seen, false);
		helper.assertTrue(Arcs.running(level, a) == s && tobin.isAlive() && me.getUUID().equals(LostCaravanStory.leader(s)),
			"Tobin, freed and following, was lost on day 7 (arc running: " + (Arcs.running(level, a) != null) + ", Tobin alive: " + tobin.isAlive()
				+ "); the rescuer was told: " + chat.stream().filter(t -> t.startsWith("Nobody came")).findFirst().orElse("-"));
		helper.succeed();
	}

	/**
	 * "This chapter is done when Tobin reaches either village": with the receiving village's hall broken mid-arc there is
	 * no village there, so walking Tobin to where it stood mustn't end the story (he'd "stay in" a village that's gone).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanBugBrokenHallHome"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanBugBrokenHallHome")
	public void tobinDoesNotComeHomeWhereTheHallWasBroken(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		VillageHalls.RADIUS = 8; // (begin puts it back)
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		// the receiving village, moved next to the test cage, then its hall broken
		BlockPos there = helper.absolutePos(new BlockPos(27, 2, 27));
		helper.setBlock(new BlockPos(27, 2, 27), ModBlocks.VILLAGE_HALL);
		LostCaravanStory.hold(s, there, LostCaravanStory.goods(s));
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		helper.setBlock(new BlockPos(27, 2, 27), Blocks.AIR);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		Leftovers.after(helper, () -> {
			tobin.discard();
			me.discard();
		});
		helper.assertTrue(tobin.distanceToSqr(there.getCenter()) < 24 * 24, "the test cage isn't by the broken hall");
		helper.assertTrue(Arcs.running(level, a) == s, "Tobin came home to a broken hall: the arc ended with him " + Math.round(tobin.distanceTo(me))
			+ " blocks from the player, " + Math.round(Math.sqrt(tobin.distanceToSqr(there.getCenter()))) + " from where the hall stood");
		helper.succeed();
	}

	/** The delivery quest pays "at the receiving hall": with that hall broken, carrying the goods to where it stood pays nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanBugBrokenHallGoods"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanBugBrokenHallGoods")
	public void theGoodsAreNotDeliveredToABrokenHall(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		level.setBlock(b, Blocks.AIR.defaultBlockState(), 3); // the receiving hall broken
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, me::discard);
		me.getInventory().add(new ItemStack(Items.OAK_LOG, 32));
		me.getInventory().add(new ItemStack(Items.BREAD, 12));
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 0.5);
		LostCaravanStory.check(me);
		helper.assertTrue(me.getInventory().countItem(Items.OAK_LOG) == 32 && Reputation.points(level, b, me.getUUID()) == 0,
			"the goods were delivered where the receiving hall was broken: " + (32 - me.getInventory().countItem(Items.OAK_LOG)) + " logs taken, standing "
				+ Reputation.points(level, b, me.getUUID()) + " in a village that's gone");
		helper.succeed();
	}
}
