package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * QA (lane-c-1123-0653): B1527 from the spec (docs/wiki/reputation.md, "Fighting in a raid: the third raider you kill in
 * it, +20, once a raid"; raiders are "vanilla's pillagers while their raid is on; a straggler after the raid counts for
 * nothing"). Edges the fix's own tests leave out: a raid's other raider kinds (vindicator, witch, ravager), a player's
 * arrow, kills by something other than a player (they count for nobody), and a second raid paying again after the first.
 */
public class QaRaidReputation1123cGameTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;

	/** A tough raider of {@code type} at {@code rel} (y = 2), no AI. */
	private static Mob raider(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		Mob m = type.create(level);
		BlockPos p = helper.absolutePos(rel);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		m.setHealth(200);
		level.addFreshEntity(m);
		Leftovers.after(helper, m::discard);
		return m;
	}

	private static List<Mob> pillagers(GameTestHelper helper, int n, int z) {
		List<Mob> p = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			p.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + i, 2, z)));
		}
		return p;
	}

	private static void byPlayer(GameTestHelper helper, Mob m, ServerPlayer by) {
		m.hurt(helper.getLevel().damageSources().playerAttack(by), 1000f);
		helper.assertTrue(!m.isAlive(), "(setup) the raider survived the player's blow");
	}

	private static int points(GameTestHelper helper, ServerPlayer p) {
		return ReputationGameTests.points(helper, p);
	}

	/** Starts the vanilla raid at {@code bell} at tick 30 with {@code members}; stops it when the test ends. */
	private static void withRaid(GameTestHelper helper, BlockPos bell, ServerPlayer player, List<Mob> members, java.util.function.Consumer<Raid> then) {
		Raid[] raid = new Raid[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
		});
		helper.runAfterDelay(30, () -> {
			raid[0] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, player, members.toArray(new Mob[0]));
			helper.assertTrue(VillageRaids.active(helper.absolutePos(ReputationGameTests.HALL)).isEmpty(), "(setup) a village raid of ours is on too");
			then.accept(raid[0]);
		});
	}

	private static void setup(GameTestHelper helper) {
		VillageRaids.forget();
		Leftovers.after(helper, VillageRaids::forget);
	}

	/** A vanilla raid's vindicator, witch and ravager are its raiders too: the third of them killed pays 20. */
	//$ gametest_ticks_batch AREA '200' '"qaRaidRep1123cKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidRep1123cKinds")
	public void qaAVindicatorAWitchAndARavagerOfAVanillaRaidPayTheTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			setup(helper);
			BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> kinds = List.of(raider(helper, EntityType.VINDICATOR, new BlockPos(3, 2, 3)), raider(helper, EntityType.WITCH, new BlockPos(5, 2, 3)),
				raider(helper, EntityType.RAVAGER, new BlockPos(8, 2, 3)));
			withRaid(helper, bell, me, kinds, raid -> {
				byPlayer(helper, kinds.get(0), me);
				byPlayer(helper, kinds.get(1), me);
				helper.assertTrue(points(helper, me) == 0, "paid before the third raider: " + points(helper, me));
				byPlayer(helper, kinds.get(2), me);
				helper.assertTrue(points(helper, me) == 20, "a vindicator, a witch and a ravager of a vanilla raid paid " + points(helper, me) + ", not 20");
				helper.succeed();
			});
		});
	}

	/** Raiders shot dead with the player's arrows are his kills: the third pays 20. */
	//$ gametest_ticks_batch AREA '200' '"qaRaidRep1123cArrow"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidRep1123cArrow")
	public void qaThreeVanillaRaidersShotWithThePlayersArrowsPayTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			setup(helper);
			ServerLevel level = helper.getLevel();
			BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3, 3);
			withRaid(helper, bell, me, p, raid -> {
				for (Mob m : p) {
					Arrow arrow = new Arrow(EntityType.ARROW, level);
					arrow.setOwner(me);
					m.hurt(level.damageSources().arrow(arrow, me), 1000f);
					helper.assertTrue(!m.isAlive(), "(setup) a pillager survived the arrow");
				}
				helper.assertTrue(points(helper, me) == 20, "three vanilla raid pillagers shot by the player paid " + points(helper, me) + ", not 20");
				helper.succeed();
			});
		});
	}

	/**
	 * Raiders killed by something other than a player (a zombie's blow, plain damage) count for nobody: with two of those
	 * and two of his own the player has nothing; his own third pays the 20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidRep1123cNotAPlayer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidRep1123cNotAPlayer")
	public void qaRaidersKilledByOthersThanThePlayerDontMakeHisThird(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			setup(helper);
			ServerLevel level = helper.getLevel();
			BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 5, 3);
			Zombie zombie = (Zombie) raider(helper, EntityType.ZOMBIE, new BlockPos(3, 2, 6));
			withRaid(helper, bell, me, p, raid -> {
				p.get(0).hurt(level.damageSources().mobAttack(zombie), 1000f);
				p.get(1).hurt(level.damageSources().generic(), 1000f);
				helper.assertTrue(!p.get(0).isAlive() && !p.get(1).isAlive(), "(setup) the raiders survived");
				byPlayer(helper, p.get(2), me);
				byPlayer(helper, p.get(3), me);
				helper.assertTrue(points(helper, me) == 0, "two raiders killed by a zombie and plain damage counted towards the player's third: "
					+ points(helper, me));
				helper.assertTrue(raid.isActive(), "(setup) the raid ended early");
				byPlayer(helper, p.get(4), me);
				helper.assertTrue(points(helper, me) == 20, "the player's own third raider paid " + points(helper, me) + ", not 20");
				helper.succeed();
			});
		});
	}

	/**
	 * "Once a raid": two kills in one vanilla raid don't carry over to the next. Raid one: two kills, then it stops; raid
	 * two (a new raid at the same bell): one kill pays nothing, the third of raid two pays 20; then a raid three pays
	 * another 20.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaRaidRep1123cTwoRaids"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaRaidRep1123cTwoRaids")
	public void qaKillsDontCarryOverToTheNextVanillaRaidAndEachRaidPaysOnce(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			setup(helper);
			ServerLevel level = helper.getLevel();
			BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> first = pillagers(helper, 2, 3);
			Raid[] raids = new Raid[3];
			Leftovers.after(helper, () -> {
				for (Raid r : raids) {
					if (r != null) {
						r.stop();
					}
				}
			});
			helper.runAfterDelay(30, () -> {
				raids[0] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, me, first.toArray(new Mob[0]));
				byPlayer(helper, first.get(0), me);
				byPlayer(helper, first.get(1), me);
				raids[0].stop();
			});
			List<Mob> second = new ArrayList<>();
			helper.runAfterDelay(60, () -> {
				second.addAll(pillagers(helper, 3, 5));
				raids[1] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, me, second.toArray(new Mob[0]));
				helper.assertTrue(raids[1] != raids[0] && raids[1].getId() != raids[0].getId(), "(setup) the second raid is the first one again");
				byPlayer(helper, second.get(0), me);
				helper.assertTrue(points(helper, me) == 0, "two kills in the last raid and one in this carried over: " + points(helper, me));
				byPlayer(helper, second.get(1), me);
				helper.assertTrue(points(helper, me) == 0, "paid at the second kill of the second raid: " + points(helper, me));
				byPlayer(helper, second.get(2), me);
				helper.assertTrue(points(helper, me) == 20, "the second raid's third kill paid " + points(helper, me) + " in all, not 20");
				raids[1].stop();
			});
			List<Mob> third = new ArrayList<>();
			helper.runAfterDelay(90, () -> {
				third.addAll(pillagers(helper, 3, 7));
				raids[2] = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, me, third.toArray(new Mob[0]));
				for (Mob m : third) {
					byPlayer(helper, m, me);
				}
				helper.assertTrue(points(helper, me) == 40, "a third raid's third kill brought " + points(helper, me) + " in all, not 40");
				helper.succeed();
			});
		});
	}
}
