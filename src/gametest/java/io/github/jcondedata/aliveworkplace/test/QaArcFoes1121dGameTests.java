package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-d-1121-1609), B724 (31.4 story arcs, kin of B711), from the bug's Expected: the same arc runs in two
 * villages, each spawning its own foe for a {@code kill role:<foe>} step; only the foe a village's own arc spawned counts
 * for that village. Unlike QaVillage1031VerifyBugTests (which calls Stories.onKill), every kill here is a real one: the
 * player's attack kills the pig, through the mod's own death hook.
 * <ul>
 * <li>B's foe killed first: B's hunt ends, A's stays open; then A's own foe comes and killing it ends A's hunt;</li>
 * <li>both foes about: killing A's ends A's and leaves B's open (the other way round from the bug);</li>
 * <li>a save and reload of the stories between each foe's spawn and its kill: the same holds after the reload.</li>
 * </ul>
 */
public class QaArcFoes1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);

	private record Two(ServerLevel level, BlockPos a, BlockPos b, Arcs.Arc hunt, ServerPlayer player) {
	}

	/** Two village halls with the same boar hunt about to start in each, and a survival player near neither. */
	private static Two setup(GameTestHelper helper, String id) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		VillageHalls.RADIUS = 8;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 50;
		Arcs.NEAR = 8;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.stopAll(level, a);
			Arcs.stopAll(level, b);
			level.getEntitiesOfClass(Pig.class, helper.getBounds().inflate(32), p -> p.getTags().contains(ArcEffects.roleTag("boar"))).forEach(Pig::discard);
		});
		for (BlockPos at : List.of(HALL, OTHER_HALL)) {
			helper.setBlock(at, ModBlocks.VILLAGE_HALL);
			BlockPos abs = helper.absolutePos(at);
			((VillageHallBlockEntity) level.getBlockEntity(abs)).setLastQuestDay(Long.MAX_VALUE / 2);
			Arcs.stopAll(level, abs);
		}
		Arcs.Arc hunt = Arcs.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_1121d/" + id), JsonParser.parseString("""
			{"name": "The Boar Hunt", "trigger": {"chance": 0},
			 "chapters": [{"name": "Hunted",
			   "on_start": [{"type": "spawn", "entity": "minecraft:pig", "key": "boar", "name": {"text": "The Boar"}, "offset": [2, 2], "restrict": 3}],
			   "quests": [{"objectives": [{"type": "kill", "entity": "role:boar", "count": 1}]}]}]}""").getAsJsonObject());
		Arcs.add(hunt);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos mid = helper.absolutePos(new BlockPos(14, 2, 14));
		player.teleportTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5); // half way: 13 blocks from each hall, near neither
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
		helper.assertTrue(Arcs.start(level, a, hunt) != null && Arcs.start(level, b, hunt) != null, "the hunt didn't start in both villages");
		return new Two(level, a, b, hunt, player);
	}

	/** The player stands by {@code hall} and the arcs look round: that village's foe comes. Returns it. */
	private static Pig comeTo(GameTestHelper helper, Two t, BlockPos hall) {
		t.player().teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() - 1.5);
		ArcEffects.check(t.level());
		ArcState s = Arcs.running(t.level(), hall);
		helper.assertTrue(s != null && !s.mobs.isEmpty() && s.mobs.get(0).uuid != null,
			"the foe of the village at " + hall.toShortString() + " didn't come with a player by it");
		List<Pig> mine = boars(helper, t).stream().filter(p -> p.getUUID().equals(s.mobs.get(0).uuid)).toList();
		helper.assertTrue(mine.size() == 1, "the village's foe isn't in the world");
		return mine.get(0);
	}

	private static List<Pig> boars(GameTestHelper helper, Two t) {
		return t.level().getEntitiesOfClass(Pig.class, helper.getBounds().inflate(32), p -> p.isAlive() && p.getTags().contains(ArcEffects.roleTag("boar")));
	}

	/** The player kills {@code pig} with a real attack (the mod's death hook counts it). */
	private static void kill(GameTestHelper helper, Two t, Pig pig) {
		t.player().teleportTo(pig.getX(), pig.getY(), pig.getZ() - 1);
		pig.hurt(t.level().damageSources().playerAttack(t.player()), 1000f);
		helper.assertTrue(!pig.isAlive(), "the boar didn't die");
	}

	private static int openArcQuests(ServerLevel level, BlockPos hall) {
		return (int) Stories.open(level, hall).stream().filter(q -> q.arc != null).count();
	}

	/** Saves the stories and loads them back in their place, as a server restart does. */
	private static void reload(GameTestHelper helper, ServerLevel level) {
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(saved, level.registryAccess()));
		CompoundTag again = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "the stories changed in the reload");
	}

	/**
	 * B724's case end to end, with real kills: a player by B only, B's boar comes, the player kills it. B's hunt ends; A's
	 * hunt and its kill quest stay. Then the player goes to A, A's own boar comes, and killing it ends A's hunt.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcFoes1121dOther"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcFoes1121dOther")
	public void killingTheOtherVillagesFoeLeavesOursOpenUntilOurOwnFoeDies(GameTestHelper helper) {
		Two t = setup(helper, "other_first");
		Pig theirs = comeTo(helper, t, t.b());
		ArcState inA = Arcs.running(t.level(), t.a());
		helper.assertTrue(inA != null && inA.mobs.get(0).uuid == null && boars(helper, t).size() == 1,
			"setup: only B's boar should be about (the player was by B only); boars " + boars(helper, t).size());
		kill(helper, t, theirs);
		helper.assertTrue(Arcs.running(t.level(), t.b()) == null, "killing B's own boar didn't end B's hunt");
		helper.assertTrue(Arcs.running(t.level(), t.a()) != null && openArcQuests(t.level(), t.a()) == 1,
			"killing B's boar ended (or changed) A's hunt, whose own boar hadn't come: A running " + (Arcs.running(t.level(), t.a()) != null)
				+ ", A's open arc quests " + openArcQuests(t.level(), t.a()));
		Pig ours = comeTo(helper, t, t.a());
		helper.assertTrue(Arcs.running(t.level(), t.a()) != null, "A's hunt ended when its boar came");
		kill(helper, t, ours);
		helper.assertTrue(Arcs.running(t.level(), t.a()) == null && openArcQuests(t.level(), t.a()) == 0,
			"killing A's own boar didn't end A's hunt");
		helper.succeed();
	}

	/**
	 * Both boars about (the player went by A, then by B): killing A's ends A's hunt and leaves B's open, B's boar alive;
	 * then killing B's ends B's.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcFoes1121dBoth"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcFoes1121dBoth")
	public void withBothFoesAboutEachKillEndsOnlyItsOwnVillagesHunt(GameTestHelper helper) {
		Two t = setup(helper, "both");
		Pig inA = comeTo(helper, t, t.a());
		Pig inB = comeTo(helper, t, t.b());
		helper.assertTrue(boars(helper, t).size() == 2 && !inA.getUUID().equals(inB.getUUID()), "setup: two boars, one per village");
		kill(helper, t, inA);
		helper.assertTrue(Arcs.running(t.level(), t.a()) == null, "killing A's boar didn't end A's hunt");
		helper.assertTrue(Arcs.running(t.level(), t.b()) != null && openArcQuests(t.level(), t.b()) == 1 && inB.isAlive(),
			"killing A's boar ended (or changed) B's hunt");
		kill(helper, t, inB);
		helper.assertTrue(Arcs.running(t.level(), t.b()) == null, "killing B's boar didn't end B's hunt");
		helper.succeed();
	}

	/**
	 * The bug's case with a save and reload between each foe's spawn and its kill: after B's boar comes the stories are
	 * reloaded, and killing B's boar still ends only B's hunt; A's boar comes, the stories are reloaded again, and killing
	 * it ends A's. A role's UUID that is saved empty (A's boar not come yet) must not turn into "any boar of the arc".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcFoes1121dReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcFoes1121dReload")
	public void aReloadBetweenTheFoesSpawnAndItsKillKeepsEachVillagesFoeItsOwn(GameTestHelper helper) {
		Two t = setup(helper, "reload");
		Pig theirs = comeTo(helper, t, t.b());
		reload(helper, t.level());
		helper.assertTrue(Arcs.running(t.level(), t.a()) != null && Arcs.running(t.level(), t.b()) != null, "a hunt was lost in the reload");
		helper.assertTrue(Arcs.running(t.level(), t.a()).mobs.get(0).uuid == null, "A's boar has a UUID after the reload, though it never came");
		kill(helper, t, theirs);
		helper.assertTrue(Arcs.running(t.level(), t.b()) == null, "after a reload, killing B's boar didn't end B's hunt");
		helper.assertTrue(Arcs.running(t.level(), t.a()) != null && openArcQuests(t.level(), t.a()) == 1,
			"after a reload, killing B's boar ended (or changed) A's hunt, whose own boar hadn't come");
		Pig ours = comeTo(helper, t, t.a());
		reload(helper, t.level());
		helper.assertTrue(boars(helper, t).size() == 1, "after the second reload, A's boar was spawned again (or lost): " + boars(helper, t).size());
		kill(helper, t, ours);
		helper.assertTrue(Arcs.running(t.level(), t.a()) == null, "after a reload, killing A's own boar didn't end A's hunt");
		helper.succeed();
	}
}
