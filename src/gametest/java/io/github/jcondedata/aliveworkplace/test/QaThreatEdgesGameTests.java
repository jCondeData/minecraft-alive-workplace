package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane d, 2026-10-25) on threats, from the specs and the nightly mutation report:
 * <ul>
 * <li>32.16a names "a ghast's fireball" and "TNT it lit" among a raider's blasts that never break a builder's block. A
 * raider's ghast fireball and a raider's lit TNT blow up beside a finished log hut: every log stands. The same blast
 * from a wild ghast's fireball, and from TNT nobody lit, does take logs (so the tests would see the rule broken).</li>
 * <li>A lair whose culture is no longer known (a datapack culture removed from the server) comes to a new dawn: it
 * neither grows nor stops the hall's round (the mutant {@code Lairs.java:402 && to ||} threw here).</li>
 * <li>A lair a lone guard killed at, broken by the player alone after the game saved: the guard's name is forgotten
 * AND that is saved, so after a restart the next war party's chronicle line doesn't name him (the mutant
 * {@code WarParties.java:465 != to ==} left the data unsaved).</li>
 * </ul>
 */
public class QaThreatEdgesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** The hut's corner: logs at its four corners (y 2 and 3), a 5 x 5 roof of logs at y 4. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	/** A log hut, recorded as finished by the builders, with mob griefing on (as in {@code ThreatDamageGameTests}). */
	private static void hut(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : data.damaged()) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
		});
		for (BlockPos p : logs()) {
			helper.setBlock(p, Blocks.OAK_LOG);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_edge_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
	}

	private static List<BlockPos> logs() {
		List<BlockPos> out = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					out.add(HUT.offset(x, 0, z));
					out.add(HUT.offset(x, 1, z));
				}
				out.add(HUT.offset(x, 2, z));
			}
		}
		return out;
	}

	private static long standing(GameTestHelper helper) {
		return logs().stream().filter(p -> helper.getBlockState(p).is(Blocks.OAK_LOG)).count();
	}

	/** Puts every log back (after a control blast took some). */
	private static void rebuild(GameTestHelper helper) {
		for (BlockPos p : logs()) {
			helper.setBlock(p, Blocks.OAK_LOG);
		}
	}

	private static LargeFireball fireball(ServerLevel level, Ghast owner, Vec3 at) {
		LargeFireball ball = new LargeFireball(level, owner, Vec3.ZERO, 1);
		ball.moveTo(at.x, at.y, at.z);
		return ball;
	}

	/**
	 * A raider's ghast's fireball bursts on the hut's roof: every log stands. A wild ghast's fireball in the same spot
	 * takes some of the roof.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaEdgeFireball"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEdgeFireball")
	public void qaARaidersGhastFireballSparesTheBuildersBlocks(GameTestHelper helper) {
		hut(helper, "fireball");
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(HUT.offset(2, 3, 2));
		Vec3 burst = at.getCenter();
		helper.runAfterDelay(2, () -> {
			Ghast raider = EntityType.GHAST.create(level);
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(burst.x, burst.y + 10, burst.z);
			level.explode(fireball(level, raider, burst), burst.x, burst.y, burst.z, 3f, true, Level.ExplosionInteraction.MOB);
			helper.assertTrue(standing(helper) == logs().size(), "the raider's ghast fireball took " + (logs().size() - standing(helper)) + " of the hut's logs");
			Ghast wild = EntityType.GHAST.create(level);
			wild.moveTo(burst.x, burst.y + 10, burst.z);
			level.explode(fireball(level, wild, burst), burst.x, burst.y, burst.z, 3f, true, Level.ExplosionInteraction.MOB);
			helper.assertTrue(standing(helper) < logs().size(), "a wild ghast's fireball took nothing: the test can't see the rule");
			rebuild(helper);
			helper.succeed();
		});
	}

	/**
	 * TNT a raider lit goes off on the hut's roof: every log stands. TNT nobody lit (no owner) in the same spot takes
	 * some of the roof.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaEdgeTnt"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaEdgeTnt")
	public void qaTntARaiderLitSparesTheBuildersBlocks(GameTestHelper helper) {
		hut(helper, "tnt");
		ServerLevel level = helper.getLevel();
		Vec3 burst = helper.absolutePos(HUT.offset(2, 3, 2)).getCenter();
		helper.runAfterDelay(2, () -> {
			Pillager raider = EntityType.PILLAGER.create(level);
			raider.addTag(Threats.RAIDER_TAG);
			raider.moveTo(burst.x + 6, burst.y, burst.z);
			PrimedTnt lit = new PrimedTnt(level, burst.x, burst.y, burst.z, raider);
			level.explode(lit, burst.x, burst.y, burst.z, 4f, Level.ExplosionInteraction.TNT);
			helper.assertTrue(standing(helper) == logs().size(), "the raider's TNT took " + (logs().size() - standing(helper)) + " of the hut's logs");
			PrimedTnt nobodys = new PrimedTnt(level, burst.x, burst.y, burst.z, null);
			level.explode(nobodys, burst.x, burst.y, burst.z, 4f, Level.ExplosionInteraction.TNT);
			helper.assertTrue(standing(helper) < logs().size(), "TNT nobody lit took nothing: the test can't see the rule");
			rebuild(helper);
			helper.succeed();
		});
	}

	/**
	 * A lair of a culture the server no longer knows (a datapack culture taken out) comes to the next dawn: no error,
	 * its strength stays as it was, and the day it last grew moves on.
	 */
	//$ gametest_batch AREA '"qaEdgeLostCulture"'
	@GameTest(template = AREA, batch = "qaEdgeLostCulture")
	public void qaALairOfAnUnknownCultureComesToDawnUnchanged(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		long today = Chronicle.day(level);
		ResourceLocation gone = ResourceLocation.fromNamespaceAndPath("qa_removed_pack", "vanished_raiders");
		helper.assertTrue(Threats.get(gone).isEmpty(), "setup: the culture is known");
		Lairs.putForTest(level, new Lairs.Lair(helper.absolutePos(QaWarPartyGameTests.LAIR), hall, UUID.randomUUID(), today, gone, 4, today, 0, 0, 3));
		try {
			Lairs.dawn(level, hall, today + 3);
		} catch (RuntimeException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("a lair of an unknown culture broke the dawn: " + e);
		}
		Lairs.Lair after = Lairs.near(level, hall).orElse(null);
		helper.assertTrue(after != null, "the lair was dropped at dawn");
		helper.assertTrue(after.strength() == 4, "an unknown culture's lair grew to " + after.strength() + ", not stayed at 4");
		helper.assertTrue(after.grown() == today + 3, "its last growth day is " + after.grown() + ", not " + (today + 3));
		helper.succeed();
	}

	/**
	 * A guard on watch (not marching) kills one of the band at the lair; the game then saves; the player alone breaks
	 * the lair. The guard's kill is forgotten, and the forgetting is marked to be saved, so a restart can't bring his
	 * name back into the next war party's chronicle line.
	 */
	//$ gametest_batch AREA '"qaEdgeForgetSaved"'
	@GameTest(template = AREA, batch = "qaEdgeForgetSaved")
	public void qaABrokenLairsKillersAreForgottenForGood(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager watch = QaWarPartyGameTests.guard(helper, new BlockPos(12, 2, 12));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		Escorts.scan(level.getServer());
		helper.assertFalse(WarParties.marching(watch), "setup: the guard is on the march");
		QaWarPartyGameTests.bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(watch), 1000f);
		helper.assertTrue(WarParties.killers(level, hall).size() == 1, "setup: the guard's kill wasn't counted: " + WarParties.killers(level, hall));
		WarParties.Data data = WarParties.Data.get(level);
		data.setDirty(false); // the game saved: what is on disk names the guard
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(WarParties.killers(level, hall).isEmpty(), "the broken lair still counts " + WarParties.killers(level, hall));
		helper.assertTrue(data.isDirty(), "the broken lair's kills were forgotten but not marked to save: a restart brings the guard's name back");
		helper.succeed();
	}
}
