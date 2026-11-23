package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1123-0709): reputation.md "vanilla's pillagers while their raid is on; a straggler after the raid counts for
 * nothing". A vanilla raid that is lost (its village gone after a wave came: the bell broken, and the raid's status set to
 * LOSS as vanilla's Raid.tick does, since a neighbouring batch's bell could keep the village) is
 * over, its bar reads "Raid - Defeat", but vanilla keeps it "active" for its 600-tick celebration and its living raiders
 * stay joined. Killing the third of them then is a straggler after the raid and should pay nothing.
 */
public class QaRaidKillsLost1123dBugTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;

	private static void set(Raid raid, String name, Object value) {
		try {
			Field f = Raid.class.getDeclaredField(name);
			f.setAccessible(true);
			f.set(raid, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object lossStatus() {
		try {
			return Enum.valueOf((Class) Class.forName("net.minecraft.world.entity.raid.Raid$RaidStatus"), "LOSS");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	//$ gametest_ticks_batch AREA '300' '"qaRaidKills1123dLost"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaRaidKills1123dLost")
	public void qaAStragglerOfALostVanillaRaidPaysNothing(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageRaids.forget();
			Leftovers.after(helper, VillageRaids::forget);
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			List<Mob> p = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				p.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + i, 2, 3)));
			}
			Raid[] raid = new Raid[1];
			Leftovers.after(helper, () -> {
				if (raid[0] != null) {
					raid[0].stop();
				}
			});
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(level.isVillage(bell), "(setup) no village at the bell: " + level.getBlockState(bell) + ", meeting poi "
					+ level.getPoiManager().getType(bell) + ", taken " + level.getPoiManager().exists(bell, h -> true));
				raid[0] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, me, p.toArray(new Mob[0]));
				set(raid[0], "groupsSpawned", 1); // a wave came: losing the village now is a loss, not a call-off
				set(raid[0], "started", true);
				p.get(0).hurt(level.damageSources().playerAttack(me), 1000f);
				p.get(1).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "paid before the third: " + ReputationGameTests.points(helper, me));
				level.setBlockAndUpdate(bell, Blocks.AIR.defaultBlockState()); // the village is gone
				// What Raid.tick does when the village is gone after a wave came (status LOSS). Set here too, since other
				// batches' bells within two sections would let vanilla move the raid's center to them instead.
				set(raid[0], "status", lossStatus());
				QaRaidKills1123dGameTests.poll(helper, 100, () -> raid[0].isLoss(), () -> {
					Raider last = (Raider) p.get(2);
					helper.assertTrue(last.getCurrentRaid() == raid[0], "(setup) the straggler isn't in the lost raid");
					last.hurt(level.damageSources().playerAttack(me), 1000f);
					helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "a straggler of a lost vanilla raid (bar: Raid - Defeat, active " + raid[0].isActive()
						+ ") paid " + ReputationGameTests.points(helper, me) + " as the raid's third kill");
					helper.succeed();
				}, () -> helper.fail("(setup) the raid wasn't lost after its village went: stopped " + raid[0].isStopped()));
			});
		});
	}
}
