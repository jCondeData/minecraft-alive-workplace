package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.mobsbegone.MobsBeGoneBlocklist;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-03), verifying B301 from its text at the boundaries B301GameTests leaves (which force the answer
 * through the test switch): "monsters can come unless Peaceful or a spawn blocklist (filled from
 * config/mobsbegone-blacklist.json, every quoted id) refuses all of zombie, skeleton, creeper and spider; doMobSpawning is
 * not counted"; "Foes = monsters plus our bandit/raider/lair tags"; "reform/story slay steps become 'Bring N Bread' (N =
 * foes, max 64)"; "the hall round swaps quests already up" only where they can't be done.
 * <ul>
 * <li>Read from the real world and the real file: three of the four night monsters blocked, monsters still come; all four,
 * none come and the board offers no clearing-out quest; Peaceful, none; doMobSpawning off, they still do.</li>
 * <li>A clearing-out quest that can still be finished (monsters come, or wild Pokémon count) is left as it is by the
 * hall's round, progress kept; with Pokémon counting, a monster from a spawner still counts.</li>
 * <li>The bread is capped at 64 from both sides: 64 stays 64, 65 becomes 64, 1 stays 1.</li>
 * </ul>
 */
public class QaSlayQuest1103dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall (radius 16) whose own rounds post nothing; the switches put back at the end. */
	private static VillageHallBlockEntity hall(GameTestHelper helper, Boolean hostiles, Boolean wild) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Foes.HOSTILES_FOR_TESTS = hostiles;
		Foes.WILD_FOR_TESTS = wild;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Foes.HOSTILES_FOR_TESTS = null;
			Foes.WILD_FOR_TESTS = null;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	private static Quest slay(ServerLevel level, String giver, int count) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/slay"), giver, Optional.empty(), "Ana", now, now + VillageQuests.LASTS,
			List.of(new Objectives.Kill("monster", count, false)), new int[1], List.of());
	}

	private static boolean clearsMonsters(Quest q) {
		return q != null && q.objectives.stream().anyMatch(o -> o instanceof Objectives.Kill k && k.entity().equals("monster"));
	}

	private static Quest find(ServerLevel level, BlockPos hall, UUID id) {
		return Stories.open(level, hall).stream().filter(q -> q.id.equals(id)).findFirst().orElse(null);
	}

	private static long stamp = System.currentTimeMillis() + 10_000;

	/** Writes MobsBeGone's file with a fresh modified time, so the mod reads it again. */
	private static void blocklist(Path file, String json) {
		try {
			Files.writeString(file, json);
			stamp += 5_000;
			Files.setLastModifiedTime(file, FileTime.fromMillis(stamp));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void noBlocklist(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * The world and MobsBeGone's real file decide whether monsters can come, never the test switch: three of the four
	 * night monsters blocked, they still come; all four (Cobbleverse's layout), none come and the board offers no
	 * clearing-out quest; doMobSpawning off, they still come; Peaceful, none.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1103dSlayWorld"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103dSlayWorld")
	public void whetherMonstersCanComeIsReadFromTheWorldAndTheBlocklistFile(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, null, false);
		BlockPos hall = helper.absolutePos(HALL);
		Path file = Platform.get().configDir().resolve(MobsBeGoneBlocklist.FILE);
		String before;
		try {
			before = Files.isRegularFile(file) ? Files.readString(file) : null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		Difficulty difficulty = level.getDifficulty();
		boolean spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, level.getServer());
			if (before == null) {
				noBlocklist(file);
			} else {
				blocklist(file, before);
			}
		});
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		noBlocklist(file);
		helper.assertTrue(Foes.hostiles(level), "Normal, no blocklist: monsters can't come?");
		helper.assertTrue(Foes.slayable(level), "Normal, no blocklist: a clearing-out quest can't be finished?");

		blocklist(file, "{\n  \"blacklist\": [\n    \"minecraft:zombie\",\n    \"minecraft:skeleton\",\n    \"minecraft:creeper\",\n    \"minecraft:pillager\"\n  ]\n}\n");
		helper.assertTrue(Foes.hostiles(level), "three of the four night monsters blocked (spiders still come), yet monsters can't come");

		blocklist(file, "{\n  \"blacklist\": [\n    \"minecraft:zombie\",\n    \"minecraft:skeleton\",\n    \"minecraft:creeper\",\n    \"minecraft:spider\",\n"
			+ "    \"minecraft:iron_golem\"\n  ]\n}\n");
		helper.assertTrue(!Foes.hostiles(level) && !Foes.slayable(level), "all four night monsters blocked, yet monsters can come");
		List<Quest> offered = new ArrayList<>();
		for (int seed = 0; seed < 40; seed++) {
			Quest made = Stories.make(level, hall, 1f, RandomSource.create(seed));
			if (clearsMonsters(made)) {
				offered.add(made);
			}
		}
		helper.assertTrue(offered.isEmpty(), "the board offers clearing out with every night monster blocked: " + offered);

		noBlocklist(file);
		helper.assertTrue(Foes.hostiles(level), "the blocklist file removed, but monsters still can't come");
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
		helper.assertTrue(Foes.hostiles(level), "doMobSpawning off counted, though spawners, raids and camps still bring foes");
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, level.getServer());

		level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.assertTrue(!Foes.hostiles(level) && !Foes.slayable(level), "Peaceful, yet monsters can come");
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.assertTrue(Foes.hostiles(level), "back on Normal, monsters still can't come");
		helper.succeed();
	}

	/**
	 * A clearing-out quest that can still be finished isn't swapped by the hall's round: with monsters, kept as it was;
	 * with no monsters but wild Pokémon counting, kept (progress too) and worded for Pokémon; a zombie from a spawner a
	 * player kills by the hall still counts towards it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1103dSlayKept"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103dSlayKept")
	public void aClearingOutQuestThatCanBeFinishedIsNotSwapped(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, true, false);
		BlockPos hall = helper.absolutePos(HALL);
		Quest story = slay(level, "arc", 8);
		story.arc = "aliveworkplace:test";
		story.progress[0] = 5;
		Stories.post(level, hall, story);

		VillageQuests.tick(level, hall, entity);
		Quest kept = find(level, hall, story.id);
		helper.assertTrue(clearsMonsters(kept) && kept.progress[0] == 5, "with monsters about the quest was swapped: " + kept);
		helper.assertTrue(kept.title().getString().equals("Clear out 8 monsters"), "words with monsters: " + kept.title().getString());

		Foes.HOSTILES_FOR_TESTS = false;
		Foes.WILD_FOR_TESTS = true;
		VillageQuests.tick(level, hall, entity);
		kept = find(level, hall, story.id);
		helper.assertTrue(clearsMonsters(kept) && kept.progress[0] == 5, "with wild Pokémon counting the quest was swapped: " + kept);
		helper.assertTrue(kept.title().getString().equals("Defeat 8 wild Pokémon"), "words with wild Pokémon counting: " + kept.title().getString());

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		zombie.hurt(level.damageSources().playerAttack(player), 1000f);
		kept = find(level, hall, story.id);
		helper.assertTrue(kept != null && kept.progress[0] == 6, "a monster from a spawner didn't count where wild Pokémon do: " + (kept == null ? null : kept.progress[0]));
		helper.succeed();
	}

	/** The bread a stuck quest becomes is capped at 64, from both sides: 64 stays 64, 65 becomes 64, 1 stays 1 (story and reform). */
	//$ gametest_ticks_batch AREA '60' '"qa1103dSlayBread"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1103dSlayBread")
	public void theBreadAStuckQuestBecomesIsCappedAt64(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, false, false);
		BlockPos hall = helper.absolutePos(HALL);
		int[] counts = {64, 65, 1};
		int[] want = {64, 64, 1};
		List<Quest> stories = new ArrayList<>();
		List<VillageQuests.Quest> reforms = new ArrayList<>();
		for (int i = 0; i < counts.length; i++) {
			Quest q = slay(level, "arc", counts[i]);
			q.arc = "aliveworkplace:test" + i;
			Stories.post(level, hall, q);
			stories.add(q);
			reforms.add(new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.SLAY, "minecraft:air", counts[i], 0, 6, level.getGameTime(), "",
				Optional.empty(), Optional.of(new VillageQuests.ReformStep("aliveworkplace:curfew", i))));
		}
		entity.setQuests(reforms);
		VillageQuests.tick(level, hall, entity);
		for (int i = 0; i < counts.length; i++) {
			Quest swapped = find(level, hall, stories.get(i).id);
			helper.assertTrue(swapped != null && swapped.objectives.get(0) instanceof Objectives.Bring b && b.item().equals("minecraft:bread") && b.count() == want[i],
				"a story step of " + counts[i] + " foes became: " + swapped);
			helper.assertTrue(swapped.title().getString().equals("Bring " + want[i] + " Bread"), "its words: " + swapped.title().getString());
			UUID id = reforms.get(i).id();
			VillageQuests.Quest step = entity.quests().stream().filter(q -> q.id().equals(id)).findFirst().orElse(null);
			helper.assertTrue(step != null && step.kind() == VillageQuests.Kind.BRING && step.item().equals("minecraft:bread") && step.count() == want[i]
				&& step.reward() == 6, "a reform step of " + counts[i] + " foes became: " + step);
		}
		helper.succeed();
	}
}
