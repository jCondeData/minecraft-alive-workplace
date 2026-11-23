package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B1527: a vanilla raid's raider pays the raid's reputation by the raid it was in when it died ({@code Raider.die} takes
 * it out of the raid before the after-death hook, so the raid is taken just before). These are the edges round that:
 * pillagers that never joined the raid, a raider saved by a totem and out of the raid since, and reputation turned off.
 * The happy path is {@link QaVanillaRaid1123dGameTests#qaTheThirdVanillaRaidPillagerKilledEarnsTheRaidsTwenty}.
 */
public class RaidKillReputationGameTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;

	/** A raid at a bell near the hall, started at tick 30 with {@code members} of {@code p} joined; then {@code then}. */
	private static void withRaid(GameTestHelper helper, ServerPlayer player, List<Mob> p, int members, java.util.function.Consumer<Raid> then) {
		VillageRaids.forget();
		Leftovers.after(helper, VillageRaids::forget);
		BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
		Raid[] raid = new Raid[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
		});
		helper.runAfterDelay(30, () -> {
			raid[0] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, player, p.subList(0, members).toArray(new Mob[0]));
			helper.assertTrue(VillageRaids.active(helper.absolutePos(ReputationGameTests.HALL)).isEmpty(), "(setup) a village raid of ours is on too");
			then.accept(raid[0]);
		});
	}

	private static List<Mob> pillagers(GameTestHelper helper, int n) {
		List<Mob> p = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			p.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + i, 2, 3)));
		}
		return p;
	}

	private static void kill(GameTestHelper helper, Mob m, ServerPlayer by) {
		m.hurt(helper.getLevel().damageSources().playerAttack(by), 1000f);
	}

	/** Pillagers that never joined the raid pay nothing, even standing in an active raid's reach (the control). */
	//$ gametest_ticks_batch AREA '200' '"raidKillRepOutsiders"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "raidKillRepOutsiders")
	public void pillagersOutsideAnActiveVanillaRaidPayNothing(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 4);
			withRaid(helper, me, p, 1, raid -> {
				for (int i = 1; i < 4; i++) {
					helper.assertTrue(((Raider) p.get(i)).getCurrentRaid() == null, "(setup) an outsider is in the raid");
					helper.assertTrue(level.getRaidAt(p.get(i).blockPosition()) == raid, "(setup) an outsider isn't in the raid's reach");
					kill(helper, p.get(i), me);
				}
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "three pillagers outside the raid paid " + ReputationGameTests.points(helper, me));
				helper.succeed();
			});
		});
	}

	/**
	 * A raider a totem saved (its death was looked at, then called off) and taken out of the raid since is no raider of it
	 * when it dies for good: it doesn't make the third kill.
	 */
	//$ gametest_ticks_batch AREA '200' '"raidKillRepTotem"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "raidKillRepTotem")
	public void aRaiderSavedByATotemAndOutOfTheRaidSinceDoesNotCount(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 4);
			withRaid(helper, me, p, 4, raid -> {
				kill(helper, p.get(0), me);
				kill(helper, p.get(1), me);
				Mob lucky = p.get(2);
				lucky.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
				kill(helper, lucky, me);
				helper.assertTrue(lucky.isAlive(), "(setup) the totem didn't save the pillager");
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "a pillager saved by a totem counted as a kill: " + ReputationGameTests.points(helper, me));
				raid.removeFromRaid((Raider) lucky, false);
				helper.assertTrue(((Raider) lucky).getCurrentRaid() == null, "(setup) the saved pillager is still in the raid");
				lucky.invulnerableTime = 0; // (the hurt cooldown after the first blow)
				kill(helper, lucky, me);
				helper.assertTrue(!lucky.isAlive(), "(setup) the saved pillager didn't die the second time");
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "a pillager out of the raid made the third kill: " + ReputationGameTests.points(helper, me));
				kill(helper, p.get(3), me);
				helper.assertTrue(ReputationGameTests.points(helper, me) == 20, "the real third raider paid " + ReputationGameTests.points(helper, me) + ", not 20");
				helper.succeed();
			});
		});
	}

	/** With reputation turned off, a vanilla raid's third raider pays nothing. */
	//$ gametest_ticks_batch AREA '200' '"raidKillRepOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "raidKillRepOff")
	public void aVanillaRaidsThirdRaiderPaysNothingWithReputationOff(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3);
			withRaid(helper, me, p, 3, raid -> {
				Reputation.ENABLED = false; // put back by ReputationGameTests.reputation
				for (Mob m : p) {
					kill(helper, m, me);
				}
				Reputation.ENABLED = true;
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "reputation off, a vanilla raid's third raider paid " + ReputationGameTests.points(helper, me));
				helper.succeed();
			});
		});
	}
}
