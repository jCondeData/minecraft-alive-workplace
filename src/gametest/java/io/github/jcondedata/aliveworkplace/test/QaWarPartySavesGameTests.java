package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, saves; B418 / ROADMAP 32.13 and 36.3): the war parties' save across the B418 change. Before it, a guard's
 * kill at the lair was saved only as a name, and it counted as a war party; the fix saves which villages marched
 * ({@code marched}) and keeps old saves as they were ("old saves count every village with kills, as before"). A world
 * saved before the fix, with a guard's kill at its lair, must still earn the war party's 10 days and cheer; one saved
 * after it with only a lone guard's kill must not.
 */
public class QaWarPartySavesGameTests implements FabricGameTest {
	private static final String AREA = QaWarPartyGameTests.AREA;

	/** The war parties' save as it stands, with {@code name} saved as a guard who killed at the lair of {@code hall}. */
	private static CompoundTag savedWithKill(ServerLevel level, BlockPos hall, String name) {
		CompoundTag tag = WarParties.Data.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag kill = new CompoundTag();
		kill.putLong("hall", hall.asLong());
		ListTag names = new ListTag();
		names.add(StringTag.valueOf(name));
		kill.put("names", names);
		ListTag killers = tag.getList("killers", net.minecraft.nbt.Tag.TAG_COMPOUND);
		killers.add(kill);
		tag.put("killers", killers);
		return tag;
	}

	/**
	 * A world saved before B418 (no {@code marched}) whose guard Bram killed at the lair: after the load the player
	 * breaks the lair and the village gets the war party's 10 days, the cheer, and Bram in the chronicle's kill list.
	 */
	//$ gametest_batch AREA '"qaWpSavesOld"'
	@GameTest(template = AREA, batch = "qaWpSavesOld")
	public void aKillSavedBeforeTheFixStillMakesAWarParty(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		CompoundTag old = savedWithKill(level, hall, "Bram");
		old.remove("marched");
		WarParties.Data.get(level).read(old);
		helper.assertTrue(WarParties.killers(level, hall).contains("Bram"), "the old save's kill wasn't read: " + WarParties.killers(level, hall));
		WarParties.Data.get(level).roundTrip(level.registryAccess()); // and saved again by the new jar
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == WarParties.REST_DAYS, "rest " + Lairs.resting(level, hall)
			+ " days after a kill saved before the fix, not " + WarParties.REST_DAYS + " (old saves count every village with kills)");
		helper.assertTrue(WarParties.mood(level, baker) != null, "no cheer after a kill saved before the fix");
		helper.succeed();
	}

	/**
	 * A world saved after B418 with only a lone guard's kill at the lair (the kill saved, the village not marched):
	 * after the load the player alone breaks it, and it is the usual 5 days with no cheer.
	 */
	//$ gametest_batch AREA '"qaWpSavesNew"'
	@GameTest(template = AREA, batch = "qaWpSavesNew")
	public void aLoneGuardsKillSavedAfterTheFixStaysNoWarParty(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		CompoundTag saved = savedWithKill(level, hall, "Wren");
		helper.assertTrue(saved.contains("marched"), "setup: the new jar's save has no marched list");
		WarParties.Data.get(level).read(saved);
		WarParties.Data.get(level).roundTrip(level.registryAccess());
		helper.assertTrue(WarParties.killers(level, hall).contains("Wren"), "the kill was lost in the save: " + WarParties.killers(level, hall));
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == 5, "rest " + Lairs.resting(level, hall) + " days with no war party, not 5");
		helper.assertTrue(WarParties.mood(level, baker) == null, "cheered with no war party: " + WarParties.mood(level, baker));
		helper.succeed();
	}
}
