package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-31), the four threat fixes of the morning, tested from their ROADMAP text, the player guide
 * (command-threat.md, command-threat-disaster.md) and the wiki (lairs.md, peace.md), not from the code:
 * <ul>
 * <li>B709 "no camp, no blocks": a refused camp doesn't take its ground, so a camp made there next stands with its chief
 * alive in it, out of every wall (he is now put in the world before the camp is stamped round him), and only one;</li>
 * <li>B704: on Peaceful, {@code lair} and {@code start} say it's Peaceful, leave no camp, raid or block behind, and the
 * same orders work as soon as the difficulty is raised;</li>
 * <li>B705: typed by its path or by its whole id, a name is suggested, nothing else is, and {@code lair} never
 * suggests a culture with no lair;</li>
 * <li>B708: "each player within 96 blocks is told once a day": per player, from both sides of 96, and again the next
 * day.</li>
 * </ul>
 */
public class QaThreatFixes1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final String PEACEFUL = "message.aliveworkplace.threat.peaceful";

	// ---- B709 ---------------------------------------------------------------------------------------------------------

	/**
	 * A camp refused (its chief can't spawn) then allowed at the same ground: the second is made, recorded with its
	 * chief, who stands alive in his camp and out of every wall 40 ticks on; one camp only.
	 */
	//$ gametest_ticks_batch AREA '160' '"qaThreatFixesCampAfterRefusal"'
	@GameTest(template = AREA, timeoutTicks = 160, batch = "qaThreatFixesCampAfterRefusal")
	public void aCampMadeAfterARefusedOneStandsWithItsChiefInIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		HostileSpawns.forget();
		HostileSpawns.FORCE = false;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			BanditCamps.forget(level);
			Lairs.forget(level);
			VillageHalls.RADIUS = radius;
		});
		helper.runAfterDelay(2, () -> {
			BlockPos camp = helper.absolutePos(CAMP);
			helper.assertTrue(BanditCamps.found(level, hall, camp) == null, "setup: the camp was made though its chief was refused");
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a refused camp left a lair on record");
			// the blocklist lifted: the same ground takes the camp
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			BanditCamps.Camp made = BanditCamps.found(level, hall, camp);
			helper.assertTrue(made != null, "after a refused camp, the same ground takes no camp once the chief may spawn");
			Lairs.Lair lair = Lairs.near(level, hall).orElse(null);
			helper.assertTrue(lair != null && lair.captain().equals(made.chief()), "the camp made isn't on record with its chief: " + lair + " / " + made);
			Entity chief = level.getEntity(made.chief());
			helper.assertTrue(chief instanceof Mob && chief.isAlive(), "the camp's chief isn't in the world: " + chief);
			Mob captain = (Mob) chief;
			float health = captain.getHealth();
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(captain.isAlive() && !captain.isRemoved(), "the chief died within 40 ticks of his camp being made");
				helper.assertTrue(!captain.isInWall(), "the chief stands inside a block of his own camp at "
					+ captain.blockPosition().subtract(camp).toShortString() + " (" + level.getBlockState(captain.blockPosition()).getBlock().getName().getString() + ")");
				helper.assertTrue(captain.getHealth() >= health, "the chief lost health in his own camp: " + health + " -> " + captain.getHealth());
				double off = Math.sqrt(captain.position().subtract(Vec3.atBottomCenterOf(camp)).horizontalDistanceSqr());
				helper.assertTrue(off <= 10, "the chief stands " + String.format("%.1f", off) + " blocks from his camp's middle");
				long lairs = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(Lairs.CAPTAIN_TAG)).size();
				helper.assertTrue(lairs == 1, lairs + " lair captains round the village (expected the one camp's)");
				helper.succeed();
			});
		});
	}

	// ---- B704 ---------------------------------------------------------------------------------------------------------

	/**
	 * On Peaceful, {@code lair bandits} and {@code start bandits} each say the world is Peaceful (naming the village),
	 * make no camp, raid or block; raised to Normal, the same {@code start} brings the raid at once (the refusal left
	 * nothing "under way").
	 */
	//$ gametest_ticks_batch AREA '80' '"qaThreatFixesPeacefulOrders"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaThreatFixesPeacefulOrders")
	public void ordersOnPeacefulSayItAndLeaveNothingThenWorkOnNormal(GameTestHelper helper) {
		BlockPos hall = QaThreatOrders1031GameTests.village(helper);
		ServerLevel level = helper.getLevel();
		Difficulty before = level.getDifficulty();
		Lairs.forget(level);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(before, true);
			Lairs.forget(level);
		});
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			QaThreatOrders1031GameTests.at(level, 92, 15000);
			CommandSourceStack op = QaThreatOrders1031GameTests.op(level, hall, said);
			String village = VillageHalls.name(level, hall).getString();
			Map<BlockPos, BlockState> blocks = new HashMap<>();
			for (BlockPos p : BlockPos.betweenClosed(hall.offset(-20, -3, -20), hall.offset(20, 12, 20))) {
				blocks.put(p.immutable(), level.getBlockState(p));
			}
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
			int lair = QaThreatOrders1031GameTests.run(level, op, "workplace threat lair bandits");
			helper.assertTrue(lair == 0, "'lair bandits' on Peaceful returned " + lair + ": " + said);
			helper.assertTrue(said.stream().anyMatch(s -> s.contains("Peaceful") && s.contains(village)),
				"'lair bandits' on Peaceful didn't say Peaceful by " + village + ": " + said);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "'lair bandits' on Peaceful left a lair on record");
			said.clear();
			int start = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			helper.assertTrue(start == 0, "'start bandits' on Peaceful returned " + start + ": " + said);
			helper.assertTrue(said.stream().anyMatch(s -> s.contains("Peaceful") && s.contains(village) && !s.contains("nowhere")),
				"'start bandits' on Peaceful didn't say Peaceful by " + village + ": " + said);
			helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "'start bandits' on Peaceful left a raid under way");
			long changed = blocks.entrySet().stream().filter(e -> !level.getBlockState(e.getKey()).equals(e.getValue())).count();
			helper.assertTrue(changed == 0, "the refused Peaceful orders changed " + changed + " blocks round the village");
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			said.clear();
			int again = QaThreatOrders1031GameTests.run(level, op, "workplace threat start bandits");
			helper.assertTrue(again > 0 && ThreatData.get(level).raid(hall).isPresent() && !VillageRaids.raiders(level, hall).isEmpty(),
				"on Normal right after the Peaceful refusal, 'start bandits' brought no raid (returned " + again + "): " + said);
			helper.assertTrue(said.stream().noneMatch(s -> s.contains("Peaceful")), "on Normal the order still speaks of Peaceful: " + said);
			helper.succeed();
		});
	}

	// ---- B705 ---------------------------------------------------------------------------------------------------------

	/**
	 * Suggestions from both sides: by path ({@code mons}, {@code pla}, {@code vill}) and by whole id
	 * ({@code aliveworkplace:pill}) the name comes; letters that fit nothing suggest nothing; {@code lair} never offers
	 * a culture without a lair (the monsters); and the suggested whole id is accepted as typed.
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaThreatFixesSuggest"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaThreatFixesSuggest")
	public void suggestionsFitByPathOrWholeIdAndNothingElse(GameTestHelper helper) {
		CommandSourceStack op = QaThreatOrders1031GameTests.op(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), new ArrayList<>());
		List<String> wrong = new ArrayList<>();
		String[][] must = {
			{"workplace threat start mons", "aliveworkplace:monsters"},
			{"workplace threat start aliveworkplace:pill", "aliveworkplace:pillager_warband"},
			{"workplace threat start dese", "aliveworkplace:desert_raiders"},
			{"workplace threat lair drow", "aliveworkplace:drowned_pirates"},
			{"workplace threat lair pig", "aliveworkplace:piglin_incursion"},
			{"workplace threat disaster pla", "aliveworkplace:plague"},
			{"workplace threat disaster vill", "aliveworkplace:village_fire"},
		};
		for (String[] typed : must) {
			Set<String> got = QaThreatOrders1031GameTests.suggestions(helper, op, typed[0]);
			if (!got.contains(typed[1])) {
				wrong.add("'" + typed[0] + "' doesn't suggest " + typed[1] + ": " + got);
			}
			if (got.size() != 1) {
				wrong.add("'" + typed[0] + "' suggests more than " + typed[1] + ": " + got);
			}
		}
		for (String typed : new String[] {"workplace threat start zzz", "workplace threat lair mons", "workplace threat disaster band",
				"workplace threat start minecraft:band"}) {
			Set<String> got = QaThreatOrders1031GameTests.suggestions(helper, op, typed);
			if (!got.isEmpty()) {
				wrong.add("'" + typed + "' suggests " + got + " (expected nothing)");
			}
		}
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
		for (String full : new String[] {"workplace threat start aliveworkplace:monsters", "workplace threat lair aliveworkplace:witch_coven",
				"workplace threat disaster aliveworkplace:drought"}) {
			var parse = dispatcher.parse(full, op);
			if (!parse.getExceptions().isEmpty() || parse.getReader().canRead() || parse.getContext().getCommand() == null) {
				wrong.add("'" + full + "' (a suggested id typed in full) doesn't parse: " + parse.getExceptions());
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	// ---- B708 ---------------------------------------------------------------------------------------------------------

	/**
	 * Two players 300 blocks apart: a refusal near each tells only him; a second refusal near the first the same day
	 * doesn't tell him again. The next day a refusal 97 blocks from the first tells him nothing, one 95 blocks off tells
	 * him, and one by the second tells the second once more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaThreatFixesToldPerPlayer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaThreatFixesToldPerPlayer")
	public void eachPlayerWithin96IsToldOnceADay(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		HostileSpawns.forget();
		Leftovers.after(helper, HostileSpawns::forget);
		List<Component> seenA = new ArrayList<>();
		List<Component> seenB = new ArrayList<>();
		ServerPlayer a = RealmGameTests.listener(helper, seenA, "qa-told-a");
		ServerPlayer b = RealmGameTests.listener(helper, seenB, "qa-told-b");
		Vec3 atA = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		Vec3 atB = atA.add(300, 0, 0);
		a.teleportTo(atA.x, atA.y, atA.z);
		b.teleportTo(atB.x, atB.y, atB.z);
		helper.runAfterDelay(2, () -> {
			a.teleportTo(atA.x, atA.y, atA.z);
			b.teleportTo(atB.x, atB.y, atB.z);
			refuse(helper, level, atA.add(3, 0, 0));
			helper.assertTrue(told(seenA) == 1 && told(seenB) == 0, "a refusal by A: A told " + told(seenA) + ", B " + told(seenB) + " (expected 1 and 0)");
			refuse(helper, level, atB.add(0, 0, 3));
			helper.assertTrue(told(seenA) == 1 && told(seenB) == 1, "then one by B: A told " + told(seenA) + ", B " + told(seenB) + " (expected 1 and 1)");
			refuse(helper, level, atA.add(0, 0, -3));
			helper.assertTrue(told(seenA) == 1, "a second refusal by A the same day told him again: " + told(seenA));
			// the next day
			var data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
			data.setGameTime(level.getGameTime() + 24000L);
			level.setDayTime(level.getDayTime() + 24000L);
			refuse(helper, level, atA.add(97, 0, 0));
			helper.assertTrue(told(seenA) == 1, "a refusal 97 blocks from A told him (the word reaches 96): " + told(seenA));
			refuse(helper, level, atA.add(95, 0, 0));
			helper.assertTrue(told(seenA) == 2, "the next day, a refusal 95 blocks from A didn't tell him: told " + told(seenA) + " in all (expected 2)");
			helper.assertTrue(told(seenB) == 1, "the next day's refusals far from B told him: " + told(seenB));
			refuse(helper, level, atB.add(-3, 0, 0));
			helper.assertTrue(told(seenB) == 2, "the next day, a refusal by B didn't tell him: told " + told(seenB) + " in all (expected 2)");
			helper.succeed();
		});
	}

	private static void refuse(GameTestHelper helper, ServerLevel level, Vec3 at) {
		Pillager p = EntityType.PILLAGER.create(level);
		p.moveTo(at.x, at.y, at.z);
		helper.assertTrue(!HostileSpawns.add(level, p, "qa test camp", Difficulty.PEACEFUL), "setup: a pillager spawned on Peaceful at " + at);
	}

	private static long told(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals(PEACEFUL)).count();
	}
}
