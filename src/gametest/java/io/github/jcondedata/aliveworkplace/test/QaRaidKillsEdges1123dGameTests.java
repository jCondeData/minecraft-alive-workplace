package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/**
 * QA (lane-d-1123-1009), raid reputation edges from the spec (reputation.md: "Fighting in a raid: the third raider you kill
 * in it, +20, once a raid"; "Raiders are ours ... and vanilla's pillagers while their raid is on; a straggler after the
 * raid counts for nothing"; standings are saved per hall in {@code aliveworkplace_stories}). What QaRaidKills1123d left
 * out: the counted kills and the paid raid across a save and reload of the standings (a vanilla raid and one of ours),
 * Peaceful during a raid, and a vanilla raid extended by a second omen (still one raid: pays once).
 */
public class QaRaidKillsEdges1123dGameTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaRaidKillsEdges1123d");
	private static final String STORIES = "aliveworkplace_stories";

	private static List<Mob> pillagers(GameTestHelper helper, int n) {
		List<Mob> p = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			p.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + (i % 6), 2, 3 + 2 * (i / 6))));
		}
		return p;
	}

	private static int points(GameTestHelper helper, ServerPlayer p) {
		return ReputationGameTests.points(helper, p);
	}

	private static void kill(GameTestHelper helper, Mob m, ServerPlayer by) {
		m.hurt(helper.getLevel().damageSources().playerAttack(by), 1000f);
		helper.assertTrue(!m.isAlive(), "(setup) a raider survived the player's blow");
	}

	/** What a restart does to the standings: {@code aliveworkplace_stories} written to NBT and read back as a new instance. */
	private static String reloadStories(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Stories.Data old = Stories.Data.get(level);
		CompoundTag saved = old.save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(STORIES, Stories.Data.load(saved, level.registryAccess()));
		helper.assertTrue(Stories.Data.get(level) != old, "(setup) the stories weren't replaced by the reloaded copy");
		return saved.toString();
	}

	/** A vanilla raid at a bell near the hall, started at tick 30, with {@code raiders} joined to its first wave; then {@code then}. */
	private static void withRaid(GameTestHelper helper, ServerPlayer player, List<Mob> raiders, java.util.function.Consumer<Raid> then) {
		VillageRaids.forget();
		Leftovers.after(helper, VillageRaids::forget);
		BlockPos bell = QaVanillaRaid1123dGameTests.bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
		List<Raid> raids = new ArrayList<>();
		Leftovers.after(helper, () -> raids.forEach(Raid::stop));
		helper.runAfterDelay(30, () -> {
			Raid raid = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, player, raiders.toArray(new Mob[0]));
			raids.add(raid);
			helper.assertTrue(VillageRaids.active(helper.absolutePos(ReputationGameTests.HALL)).isEmpty(), "(setup) a village raid of ours is on too");
			then.accept(raid);
		});
	}

	/**
	 * Save and reload, a vanilla raid: two pillagers killed, then the standings are saved and loaded (a restart) and the third
	 * pillager too (it rejoins its raid by id). Its kill pays exactly 20 (the two counted kills survived); a fourth pays
	 * nothing more, and after a second reload a fifth still pays nothing (the paid raid survived too).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidKillsEdges1123dReloadVanilla"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidKillsEdges1123dReloadVanilla")
	public void qaAVanillaRaidsCountedKillsAndItsPaySurviveASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 5);
			withRaid(helper, me, p, raid -> {
				kill(helper, p.get(0), me);
				kill(helper, p.get(1), me);
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				String saved = reloadStories(helper);
				helper.assertTrue(saved.contains("raid_kills:2"), "the saved standings don't hold the two counted raid kills: " + saved);
				Mob old = p.get(2);
				CompoundTag tag = new CompoundTag();
				old.saveWithoutId(tag);
				old.discard();
				Mob copy = EntityType.PILLAGER.create(level);
				copy.load(tag);
				level.addFreshEntity(copy);
				Leftovers.after(helper, copy::discard);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(((Raider) copy).getCurrentRaid() == raid && raid.isActive(), "(setup) the reloaded pillager isn't back in its active raid");
					kill(helper, copy, me);
					helper.assertTrue(points(helper, me) == 20, "the third kill, after the standings were saved and loaded, paid " + points(helper, me) + ", not 20");
					kill(helper, p.get(3), me);
					helper.assertTrue(points(helper, me) == 20, "a fourth kill after the reload paid again: " + points(helper, me));
					reloadStories(helper);
					helper.assertTrue(points(helper, me) == 20, "the paid 20 didn't survive a second reload: " + points(helper, me));
					kill(helper, p.get(4), me);
					helper.assertTrue(points(helper, me) == 20, "a fifth kill after a second reload paid the raid again: " + points(helper, me));
					LOG.info("[qa1123d-edges] vanilla raid {}: two kills, reload, third paid 20, then nothing more across a second reload", raid.getId());
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Save and reload, one of our village raids: two of its raiders killed, the standings saved and loaded; the third pays
	 * exactly 20, the fourth nothing, and after a second reload a fifth still nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidKillsEdges1123dReloadOurs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidKillsEdges1123dReloadOurs")
	public void qaOurRaidsCountedKillsAndItsPaySurviveASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(ReputationGameTests.HALL);
			VillageRaids.forget();
			Leftovers.after(helper, VillageRaids::forget);
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Zombie> z = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3 + i, 2, 3));
				zombie.addTag(VillageRaids.TAG);
				z.add(zombie);
			}
			helper.assertTrue(VillageRaids.track(level, hall, 5) != null && VillageRaids.active(hall).isPresent(), "(setup) no raid of ours");
			kill(helper, z.get(0), me);
			kill(helper, z.get(1), me);
			helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
			String saved = reloadStories(helper);
			helper.assertTrue(saved.contains("raid_kills:2"), "the saved standings don't hold the two counted raid kills: " + saved);
			kill(helper, z.get(2), me);
			helper.assertTrue(points(helper, me) == 20, "our raid's third kill, after the standings were saved and loaded, paid " + points(helper, me) + ", not 20");
			kill(helper, z.get(3), me);
			helper.assertTrue(points(helper, me) == 20, "a fourth kill after the reload paid again: " + points(helper, me));
			reloadStories(helper);
			kill(helper, z.get(4), me);
			helper.assertTrue(points(helper, me) == 20, "a fifth kill after a second reload paid the raid again: " + points(helper, me));
			helper.succeed();
		});
	}

	/**
	 * Peaceful mid-raid: a vanilla raid with three pillagers, two killed by the player; the world turns Peaceful. The game
	 * takes the last pillager away and stops the raid: no reputation for it, no victory, nothing thrown. Back on Normal, a
	 * new raid at the same bell starts its count afresh: its first kill pays nothing (the old two don't carry over), its
	 * third pays 20.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaRaidKillsEdges1123dPeaceful"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaRaidKillsEdges1123dPeaceful")
	public void qaPeacefulMidRaidPaysNothingAndTheNextRaidCountsAfresh(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was == Difficulty.PEACEFUL ? Difficulty.NORMAL : was, true));
		if (was == Difficulty.PEACEFUL) {
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
		}
		ReputationGameTests.village(helper, () -> {
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 3);
			BlockPos bell = helper.absolutePos(ReputationGameTests.HALL.offset(3, 0, 3));
			withRaid(helper, me, p, raid -> {
				kill(helper, p.get(0), me);
				kill(helper, p.get(1), me);
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				Mob last = p.get(2);
				level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
				QaRaidKills1123dGameTests.poll(helper, 60, () -> raid.isStopped() && last.isRemoved(), () -> {
					level.getServer().setDifficulty(Difficulty.NORMAL, true);
					helper.assertTrue(!raid.isVictory() && !raid.isActive(), "Peaceful ended the vanilla raid as a victory (victory " + raid.isVictory() + ", active " + raid.isActive() + ")");
					helper.assertTrue(points(helper, me) == 0, "the pillager Peaceful took away paid the player " + points(helper, me));
					helper.assertTrue(!me.hasEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE), "Peaceful's stopped raid made the player a Hero of the Village");
					// Back on Normal: a new raid at the same bell (the stopped one is dropped by the next raids tick).
					List<Mob> next = new ArrayList<>();
					for (int i = 0; i < 3; i++) {
						next.add(QaVanillaRaid1123dGameTests.pillager(helper, new BlockPos(3 + i, 2, 6)));
					}
					helper.runAfterDelay(5, () -> {
						Raid two = QaVanillaRaid1123dGameTests.startVanillaRaid(helper, bell, me, next.toArray(new Mob[0]));
						Leftovers.after(helper, two::stop);
						helper.assertTrue(two != raid && two.getId() != raid.getId(), "(setup) the new raid is the stopped one");
						kill(helper, next.get(0), me);
						helper.assertTrue(points(helper, me) == 0, "the new raid's first kill paid " + points(helper, me) + ": the Peaceful raid's two kills carried over");
						kill(helper, next.get(1), me);
						helper.assertTrue(points(helper, me) == 0, "the new raid's second kill paid " + points(helper, me));
						kill(helper, next.get(2), me);
						helper.assertTrue(points(helper, me) == 20, "the new raid's third kill paid " + points(helper, me) + ", not 20");
						LOG.info("[qa1123d-edges] Peaceful stopped raid {} with no pay; raid {} after it paid 20 at its third", raid.getId(), two.getId());
						helper.succeed();
					});
				}, () -> helper.fail("Peaceful didn't stop the vanilla raid or take its pillager away in 60 ticks (stopped "
					+ raid.isStopped() + ", pillager removed " + last.isRemoved() + ")"));
			});
		});
	}

	/**
	 * Once a raid, extended: a second omen at the same bell while the raid is on extends it (vanilla: the same raid, a higher
	 * omen level). Two kills before the extension and one after make the third: +20; three more after it pay nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRaidKillsEdges1123dExtended"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaidKillsEdges1123dExtended")
	public void qaAVanillaRaidExtendedByASecondOmenIsStillOneRaidAndPaysOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			List<Mob> p = pillagers(helper, 6);
			BlockPos bell = helper.absolutePos(ReputationGameTests.HALL.offset(3, 0, 3));
			withRaid(helper, me, p, raid -> {
				kill(helper, p.get(0), me);
				kill(helper, p.get(1), me);
				helper.assertTrue(points(helper, me) == 0, "paid before the third: " + points(helper, me));
				Raid again = level.getRaids().createOrExtendRaid(me, bell);
				helper.assertTrue(again == raid && raid.isActive(), "(setup) a second omen didn't extend the same raid: " + again);
				raid.setRaidOmenLevel(raid.getRaidOmenLevel() + 1);
				kill(helper, p.get(2), me);
				helper.assertTrue(points(helper, me) == 20, "the third kill, after the raid was extended, paid " + points(helper, me) + ", not 20");
				kill(helper, p.get(3), me);
				kill(helper, p.get(4), me);
				kill(helper, p.get(5), me);
				helper.assertTrue(points(helper, me) == 20, "the extended raid paid twice: " + points(helper, me));
				helper.succeed();
			});
		});
	}
}
