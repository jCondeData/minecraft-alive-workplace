package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * QA (lane d, 2026-10-26): two survivors of the nightly mutation run of 2026-10-25 (run 40176723122) in the threats.
 * <ul>
 * <li>{@code Lairs.java:402}: a lair saved by a world whose raider culture is gone since (a data pack taken out, an
 * expansion's culture renamed) must still pass its dawns: it keeps its band and is not lost, and the server doesn't
 * trip over it (CLAUDE.md: don't break existing saves).</li>
 * <li>{@code WarParties.java:465}: when a lair breaks, the guards' kills there are forgotten, and that forgetting must
 * reach the save (the saved data marked dirty), or after a restart the old kills come back and count at the village's
 * next lair (32.13, B418).</li>
 * </ul>
 */
public class QaThreatSurvivorsGameTests implements FabricGameTest {
	private static final String AREA = QaWarPartyGameTests.AREA;
	private static final ResourceLocation GONE = AliveWorkplace.id("qa_culture_taken_out");

	/**
	 * A lair of a culture that no longer exists, 9 strong, last grown yesterday: dawn keeps it, 9 strong, grown today.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaLairGoneCulture"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaLairGoneCulture")
	public void aLairOfACultureNoLongerLoadedKeepsItsBandAtDawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos camp = hall.offset(40, 0, 40);
		helper.assertTrue(Threats.get(GONE).isEmpty(), "setup: the test's culture exists");
		long today = Chronicle.day(level);
		try {
			Lairs.putForTest(level, new Lairs.Lair(camp, hall, new UUID(9, 9), today - 3, GONE, 9, today - 1, 0, 0, 0));
			try {
				Lairs.dawn(level, hall, today);
			} catch (RuntimeException e) {
				throw new AssertionError("dawn at a lair whose culture is gone failed: " + e);
			}
			Lairs.Lair after = Lairs.near(level, hall).orElse(null);
			helper.assertTrue(after != null, "the lair whose culture is gone was lost at dawn");
			helper.assertTrue(after.strength() == 9, "the lair whose culture is gone is " + after.strength() + " strong after dawn, not 9");
			helper.assertTrue(after.grown() == today, "the lair's last growth day is " + after.grown() + ", not today (" + today + ")");
		} finally {
			QaLairGrowthGameTests.drop(level, hall);
		}
		helper.succeed();
	}

	/**
	 * A world saved with guard Wren's kill at the lair (a lone guard's, after B418): the player breaks the lair, the kill
	 * is forgotten, and the war parties' saved data is marked to be written, so the restart doesn't bring Wren's kill back.
	 */
	//$ gametest_batch AREA '"qaWpForgetSaved"'
	@GameTest(template = AREA, batch = "qaWpForgetSaved")
	public void theKillsForgottenAtABrokenLairAreSaved(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		WarParties.Data data = WarParties.Data.get(level);
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		CompoundTag kill = new CompoundTag();
		kill.putLong("hall", hall.asLong());
		ListTag names = new ListTag();
		names.add(StringTag.valueOf("Wren"));
		kill.put("names", names);
		ListTag killers = saved.getList("killers", net.minecraft.nbt.Tag.TAG_COMPOUND);
		killers.add(kill);
		saved.put("killers", killers);
		data.read(saved);
		helper.assertTrue(WarParties.killers(level, hall).contains("Wren"), "setup: the saved kill wasn't read: " + WarParties.killers(level, hall));
		data.setDirty(false); // as just after the world was loaded: nothing to write yet
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(WarParties.killers(level, hall).isEmpty(), "the broken lair's kills weren't forgotten: " + WarParties.killers(level, hall));
		helper.assertTrue(data.isDirty(), "the kills were forgotten but the war parties' data isn't marked to be saved: after a restart Wren's kill comes back");
		helper.succeed();
	}
}
