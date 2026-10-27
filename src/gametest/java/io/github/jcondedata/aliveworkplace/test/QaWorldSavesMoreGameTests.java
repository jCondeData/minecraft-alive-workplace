package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.craft.DishRequests;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * QA (lane E, saves, 2026-10-27): world-wide files no test had read back from the disk with something in them
 * (QaEverySavedDataGameTests checks them empty and with keys of the wrong kind; QaWorldSavesGameTests did the post,
 * travel and shops). Each through a real restart: written to the world's data folder, the cache dropped, read back by
 * the game's own loader.
 * <ul>
 * <li>the lairs preying on villages (the hall's threats; a camp's captain, strength, raiders out and lost, the name he
 * goes by) and a village's rest after it broke one up (README: "no new camp comes for a while");</li>
 * <li>the night a coven last hexed a village: a restart in the night mustn't hex it a second time;</li>
 * <li>the day a Stadium last held its Stadium Day: a restart in the afternoon mustn't bring the visitors again;</li>
 * <li>a player's friends (README: "/workplace friend add: let a friend direct your builders"), one way, a removed friend
 * staying removed;</li>
 * <li>the blueprints unlocked at Tables far from any hall (the server-wide library, 26.10);</li>
 * <li>the dishes a village asked its Chef for on the hall's board (oldest first, how many still wanted).</li>
 * </ul>
 */
public class QaWorldSavesMoreGameTests implements FabricGameTest {

	/** Writes the level's data to disk and forgets {@code name}, so the next get() reads it from the file. */
	private static void restart(ServerLevel level, String name) {
		DimensionDataStorage storage = level.getDataStorage();
		storage.save();
		try {
			Field cache = DimensionDataStorage.class.getDeclaredField("cache");
			cache.setAccessible(true);
			((Map<?, ?>) cache.get(storage)).remove(name);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A lair (every field its own value) and another village's rest come back the same after a restart. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesLairs"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesLairs")
	public void aLairAndAVillagesRestComeBackAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1)).offset(4000, 0, 4000);
		BlockPos rested = hall.offset(600, 0, 0);
		Lairs.Lair lair = new Lairs.Lair(hall.offset(90, 3, -70), hall, new UUID(11, 22), today - 3, Threats.BANDITS, 9, today - 1, 2, 4, 5);
		Lairs.Data before = Lairs.Data.get(level);
		try {
			Lairs.putForTest(level, lair);
			Lairs.rest(level, rested, 7);
			int restBefore = Lairs.resting(level, rested);
			helper.assertTrue(restBefore == 7, "a fresh rest of 7 days reads " + restBefore);
			restart(level, Lairs.Data.NAME);
			helper.assertTrue(Lairs.Data.get(level) != before, "the lairs weren't read back from the disk");
			helper.assertTrue(Lairs.near(level, hall).equals(java.util.Optional.of(lair)),
				"the lair after a restart: " + Lairs.near(level, hall) + ", saved " + lair);
			helper.assertTrue(Lairs.of(level, hall, Threats.BANDITS).isPresent(), "the lair isn't the bandits' after a restart");
			helper.assertTrue(Lairs.resting(level, rested) == restBefore, "the village's rest after a restart: "
				+ Lairs.resting(level, rested) + " days, was " + restBefore);
			helper.assertTrue(Lairs.resting(level, hall) == 0, "the village with a lair (never broken up) is resting after a restart");
			helper.assertTrue(Lairs.near(level, rested).isEmpty(), "the resting village has a lair after a restart");
		} finally {
			Lairs.forget(level);
		}
		helper.succeed();
	}

	/** A coven's hex night is remembered over a restart, so the same night can't bring a second hex. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesCovens"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesCovens")
	public void aCovensHexNightIsRememberedAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		// A village far off with nobody in it: the night is laid (and remembered) though no one is hexed.
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1)).offset(-5000, 0, 4500);
		BlockPos other = hall.offset(700, 0, 0);
		Lairs.Lair lair = new Lairs.Lair(hall.offset(60, 0, 60), hall, new UUID(3, 4), today, Threats.BANDITS, 5, today, 0, 0, 0);
		Covens.Data before = Covens.Data.get(level);
		try {
			List<?> hexed = Covens.hexNight(level, lair, RandomSource.create(9L));
			helper.assertTrue(hexed.isEmpty(), "villagers were hexed in an empty village: " + hexed);
			helper.assertTrue(Covens.Data.get(level).lastHexed(hall) == today, "tonight's hex isn't remembered before the restart: "
				+ Covens.Data.get(level).lastHexed(hall));
			restart(level, Covens.Data.NAME);
			Covens.Data back = Covens.Data.get(level);
			helper.assertTrue(back != before, "the covens' file wasn't read back from the disk");
			helper.assertTrue(back.lastHexed(hall) == today, "after a restart the village was last hexed on day " + back.lastHexed(hall)
				+ ", not tonight (day " + today + "): the coven would hex it again tonight");
			helper.assertTrue(back.lastHexed(other) == -1, "a village never hexed reads day " + back.lastHexed(other) + " after a restart");
		} finally {
			Covens.Data.get(level).forget(hall);
		}
		helper.succeed();
	}

	/** The day each Stadium held its Stadium Day comes back after a restart; a Stadium that never held one reads never. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesStadium"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesStadium")
	public void aStadiumDayHeldTodayIsRememberedAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		UUID site = UUID.randomUUID();
		UUID older = UUID.randomUUID();
		UUID never = UUID.randomUUID();
		StadiumDays.Data before = StadiumDays.Data.get(level);
		before.setHeld(site, today);
		before.setHeld(older, today - 12);
		restart(level, StadiumDays.Data.NAME);
		StadiumDays.Data back = StadiumDays.Data.get(level);
		helper.assertTrue(back != before, "the Stadium Days' file wasn't read back from the disk");
		helper.assertTrue(back.held(site) == today, "a Stadium Day held today reads day " + back.held(site)
			+ " after a restart, not " + today + ": the visitors would come again");
		helper.assertTrue(back.held(older) == today - 12, "a Stadium Day 12 days ago reads day " + back.held(older) + " after a restart");
		helper.assertTrue(back.held(never) == -1, "a Stadium that never held one reads day " + back.held(never) + " after a restart");
		helper.succeed();
	}

	/** Alice's friends list after a restart: Bob still a friend with his name, Carol removed stays removed, and it's one way. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesFriends"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesFriends")
	public void aPlayersFriendsComeBackAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID alice = UUID.randomUUID();
		UUID bob = UUID.randomUUID();
		UUID carol = UUID.randomUUID();
		UUID dave = UUID.randomUUID();
		Friends before = Friends.get(level.getServer());
		try {
			before.add(alice, bob, "Bob");
			before.add(alice, carol, "Carol");
			before.add(alice, dave, "Dave_99");
			before.remove(alice, carol);
			restart(level, "aliveworkplace_friends");
			Friends back = Friends.get(level.getServer());
			helper.assertTrue(back != before, "the friends' file wasn't read back from the disk");
			helper.assertTrue(back.of(alice).equals(Map.of(bob, "Bob", dave, "Dave_99")), "Alice's friends after a restart: " + back.of(alice));
			helper.assertTrue(List.copyOf(back.of(alice).keySet()).equals(List.of(bob, dave)), "Alice's friends' order after a restart: " + back.of(alice));
			helper.assertTrue(back.mayDirect(alice, bob), "Bob may no longer direct Alice's builders after a restart");
			helper.assertFalse(back.mayDirect(alice, carol), "Carol, removed, may direct Alice's builders after a restart");
			helper.assertFalse(back.mayDirect(bob, alice), "after a restart Alice may direct Bob's builders, though only she named him");
			helper.assertTrue(back.of(bob).isEmpty(), "Bob has friends after a restart: " + back.of(bob));
		} finally {
			Friends now = Friends.get(level.getServer());
			now.remove(alice, bob);
			now.remove(alice, dave);
		}
		helper.succeed();
	}

	/** A blueprint unlocked at a Table far from any hall is still unlocked there after a restart; one not unlocked stays locked. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesUnlocks"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesUnlocks")
	public void aBlueprintUnlockedFarFromAnyHallStaysUnlockedAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation smithy = StarterBlueprints.SMITHY_2.id();
		ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true; // (off in GameTests unless a test turns it on)
		try {
			unlockedFarOff(helper, level, smithy, stall);
		} finally {
			BlueprintUnlocks.ENABLED = was;
		}
		helper.succeed();
	}

	private static void unlockedFarOff(GameTestHelper helper, ServerLevel level, ResourceLocation smithy, ResourceLocation stall) {
		helper.assertTrue(BlueprintUnlocks.state(level, null, smithy) == BlueprintUnlocks.State.LOCKED
			&& BlueprintUnlocks.state(level, null, stall) == BlueprintUnlocks.State.LOCKED,
			"Smithy II and the Market Stall aren't locked far from any hall to begin with: " + BlueprintUnlocks.state(level, null, smithy)
				+ ", " + BlueprintUnlocks.state(level, null, stall));
		try {
			helper.assertTrue(BlueprintUnlocks.unlock(level, null, smithy, List.of()), "Smithy II wasn't unlocked far from any hall");
			restart(level, "aliveworkplace_blueprint_unlocks");
			helper.assertTrue(BlueprintUnlocks.state(level, null, smithy) == BlueprintUnlocks.State.UNLOCKED,
				"Smithy II, unlocked far from any hall, is " + BlueprintUnlocks.state(level, null, smithy) + " after a restart");
			helper.assertTrue(BlueprintUnlocks.state(level, null, stall) == BlueprintUnlocks.State.LOCKED,
				"the Market Stall, never unlocked, is " + BlueprintUnlocks.state(level, null, stall) + " after a restart");
			helper.assertFalse(BlueprintUnlocks.unlock(level, null, smithy, List.of()), "Smithy II unlocked a second time after a restart");
		} finally {
			forgetWild(level, smithy);
		}
	}

	/** Takes {@code id} out of the server-wide unlocks again (a new hall seeds its library from them: later tests need it locked). */
	private static void forgetWild(ServerLevel level, ResourceLocation id) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", net.minecraft.server.MinecraftServer.class);
			get.setAccessible(true);
			Object data = get.invoke(null, level.getServer());
			Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			((java.util.Set<?>) ids.get(data)).remove(id);
			((net.minecraft.world.level.saveddata.SavedData) data).setDirty();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Two villages' dish requests after a restart: each village's own, oldest first, with what is still wanted (a part
	 * delivered counts less, one taken down is gone); a hand-edited count past the most clamps, a count of none is dropped.
	 */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaWorldSavesDishes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaWorldSavesDishes")
	public void aVillagesDishRequestsComeBackAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos north = helper.absolutePos(new BlockPos(1, 2, 1)).offset(0, 0, -3000);
		BlockPos south = north.offset(0, 0, 6000);
		ResourceLocation stew = ResourceLocation.fromNamespaceAndPath("farmersdelight", "beef_stew");
		ResourceLocation pie = ResourceLocation.fromNamespaceAndPath("farmersdelight", "apple_pie");
		ResourceLocation soup = ResourceLocation.fromNamespaceAndPath("croptopia", "tomato_soup");
		DishRequests before = DishRequests.get(level);
		try {
			before.add(north, stew, 10);
			before.add(north, pie, 3);
			before.add(north, soup, 5);
			before.done(north, stew, 4); // 6 stews still wanted
			before.cancel(north, pie); // taken down
			before.add(south, pie, DishRequests.MAX);
			restart(level, DishRequests.NAME);
			DishRequests back = DishRequests.get(level);
			helper.assertTrue(back != before, "the dish requests weren't read back from the disk");
			helper.assertTrue(List.copyOf(back.of(north).keySet()).equals(List.of(stew, soup)), "the north village's dishes after a restart, oldest first: " + back.of(north));
			helper.assertTrue(back.waiting(north, stew) == 6 && back.waiting(north, soup) == 5 && back.waiting(north, pie) == 0,
				"the north village waits for " + back.of(north) + " after a restart, not 6 stews and 5 soups");
			helper.assertTrue(back.of(south).equals(Map.of(pie, DishRequests.MAX)), "the south village waits for " + back.of(south) + " after a restart");
			helper.assertTrue(back.add(south, pie, 1) == 0, "after a restart the south village can ask for more than " + DishRequests.MAX + " pies");
			// A hand-edited file: a count past the most and a count of none.
			net.minecraft.nbt.CompoundTag tag = back.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
			net.minecraft.nbt.ListTag list = tag.getList("requests", net.minecraft.nbt.Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				net.minecraft.nbt.CompoundTag e = list.getCompound(i);
				if (e.getString("dish").equals(stew.toString())) {
					e.putInt("count", 500);
				} else if (e.getString("dish").equals(soup.toString())) {
					e.putInt("count", 0);
				}
			}
			DishRequests edited = DishRequests.load(tag, level.registryAccess());
			helper.assertTrue(edited.of(north).equals(Map.of(stew, DishRequests.MAX)), "a hand-edited file (500 stews, 0 soups) reads " + edited.of(north));
		} finally {
			DishRequests now = DishRequests.get(level);
			for (ResourceLocation dish : List.of(stew, pie, soup)) {
				now.cancel(north, dish);
				now.cancel(south, dish);
			}
		}
		helper.succeed();
	}
}
