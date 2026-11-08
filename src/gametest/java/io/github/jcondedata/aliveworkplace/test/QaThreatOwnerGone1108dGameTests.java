package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA 2026-11-08 (B1064's kin): what a threat's shot or blast may break once the raider behind it is gone. The spec
 * (docs/wiki/sieges.md, "What threats may damage"): a threat is a raider (tag {@code aliveworkplace_raider}) or a
 * projectile one shot; nothing a threat does breaks a builder's block; players and non-raider mobs follow the game's
 * rules. These are the cases that hold today: the controls (a player's shot, a shot nobody fired, a player's own block)
 * and the blasts whose raider is still known. The failing "owner gone" cases are in QaThreatOwnerGone1108dBugTests.
 */
public class QaThreatOwnerGone1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** Turns mobGriefing on for the test, put back when it ends. */
	static ServerLevel griefing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(was, level.getServer()));
		return level;
	}

	/** An arrow shot by {@code owner} (or by nobody), not added to the level, above {@code pos} (helper coordinates). */
	static Arrow arrow(GameTestHelper helper, Entity owner, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		helper.assertTrue(arrow != null, "setup: no arrow");
		if (owner != null) {
			arrow.setOwner(owner);
		}
		arrow.moveTo(at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 0f, 90f);
		arrow.setDeltaMovement(0, -2.0, 0);
		return arrow;
	}

	/** {@code projectile} hits the top of the block at {@code pos} through the block's own projectile-hit entry point. */
	static void hit(GameTestHelper helper, Projectile projectile, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(pos);
		BlockState state = level.getBlockState(at);
		state.onProjectileHit(level, state, new BlockHitResult(at.getCenter().add(0, 0.5, 0), Direction.UP, at, false), projectile);
	}

	/** A pillager carrying the raider tag, not added to the level. */
	static Pillager raider(GameTestHelper helper) {
		Pillager raider = EntityType.PILLAGER.create(helper.getLevel());
		helper.assertTrue(raider != null, "setup: no pillager");
		raider.addTag(Threats.RAIDER_TAG);
		return raider;
	}

	/** A chorus flower on end stone at {@code pos} (helper coordinates, the flower's spot). */
	static void chorus(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos.below(), Blocks.END_STONE);
		helper.setBlock(pos, Blocks.CHORUS_FLOWER);
		helper.assertTrue(helper.getBlockState(pos).is(Blocks.CHORUS_FLOWER), "setup: the chorus flower didn't stand on its end stone");
	}

	/** A ghast carrying the raider tag, not added to the level. */
	static Ghast ghast(GameTestHelper helper) {
		Ghast ghast = EntityType.GHAST.create(helper.getLevel());
		helper.assertTrue(ghast != null, "setup: no ghast");
		ghast.addTag(Threats.RAIDER_TAG);
		return ghast;
	}

	/** A ghast's fireball shot by {@code owner}, at the block {@code pos} (helper coordinates), not added to the level. */
	static LargeFireball fireball(GameTestHelper helper, LivingEntity owner, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		LargeFireball ball = new LargeFireball(helper.getLevel(), owner, new Vec3(0, -1, 0), 1);
		ball.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0f, 0f);
		return ball;
	}

	/** Bursts {@code source} at {@code pos} (helper coordinates), as a ghast's fireball or a raider's TNT does on impact. */
	static void blast(GameTestHelper helper, Entity source, BlockPos pos, float power, Level.ExplosionInteraction interaction) {
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(pos));
		helper.getLevel().explode(source, at.x, at.y, at.z, power, false, interaction);
	}

	/**
	 * Control for the chorus flower (vanilla breaks it with {@code destroyBlock(pos, true, projectile)}): a raider's arrow
	 * whose shooter is still there leaves the builders' flower standing; a player's own arrow breaks it (the game's rules).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneChorusControl1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneChorusControl1108d")
	public void aRaidersArrowSparesTheBuildersChorusButAPlayersBreaksIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		griefing(helper);
		BlockPos flower = new BlockPos(6, 3, 6);
		chorus(helper, flower);
		QaSiegeDamage1108dGameTests.finished(helper, flower, "qa_gone_chorus_1108d_");
		hit(helper, arrow(helper, raider(helper), flower), flower);
		helper.assertTrue(helper.getBlockState(flower).is(Blocks.CHORUS_FLOWER),
			"a raider's arrow broke the chorus flower on a builder's finished build: " + helper.getBlockState(flower));
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		hit(helper, arrow(helper, player, flower), flower);
		helper.assertTrue(helper.getBlockState(flower).isAir(),
			"a player's own arrow didn't break the builders' chorus flower, but players follow the game's rules: " + helper.getBlockState(flower));
		helper.succeed();
	}

	/**
	 * An arrow nobody shot (a dispenser's) is no threat's: it breaks the builders' decorated pot as the game does. And a
	 * raider's arrow whose shooter left breaks a player's own pot as the game does (players' blocks follow the game).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneNoShooter1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneNoShooter1108d")
	public void anArrowNobodyShotStillBreaksPotsAsTheGameDoes(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		griefing(helper);
		BlockPos pot = new BlockPos(6, 2, 6);
		BlockPos playersPot = new BlockPos(10, 2, 6);
		helper.setBlock(pot, Blocks.DECORATED_POT);
		helper.setBlock(playersPot, Blocks.DECORATED_POT);
		QaSiegeDamage1108dGameTests.finished(helper, pot, "qa_gone_nobody_1108d_");
		hit(helper, arrow(helper, null, pot), pot);
		helper.assertTrue(helper.getBlockState(pot).isAir(),
			"an arrow nobody shot (a dispenser's) didn't break the builders' pot, but it is no threat's and follows the game's rules: " + helper.getBlockState(pot));
		Pillager raider = raider(helper);
		Arrow shot = arrow(helper, raider, playersPot);
		raider.discard();
		hit(helper, shot, playersPot);
		helper.assertTrue(helper.getBlockState(playersPot).isAir(),
			"a raider's arrow didn't break a player's own pot, but players' blocks follow the game's rules: " + helper.getBlockState(playersPot));
		helper.succeed();
	}

	/**
	 * A raider ghast's fireball bursting beside a finished build, its ghast still there: the builders' wool stands and a
	 * player's own wool on the other side is blown away (the game's rules, so the test sees the blast).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneFireballControl1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneFireballControl1108d")
	public void aRaidersFireballSparesTheBuildersBlockWhileItsGhastIsThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		griefing(helper);
		BlockPos wool = new BlockPos(6, 2, 6);
		BlockPos playersWool = new BlockPos(8, 2, 6);
		BlockPos burst = new BlockPos(7, 2, 6);
		helper.setBlock(wool, Blocks.WHITE_WOOL);
		helper.setBlock(playersWool, Blocks.WHITE_WOOL);
		QaSiegeDamage1108dGameTests.finished(helper, wool, "qa_gone_ball_ok_1108d_");
		blast(helper, fireball(helper, ghast(helper), burst), burst, 2f, Level.ExplosionInteraction.MOB);
		helper.assertTrue(helper.getBlockState(wool).is(Blocks.WHITE_WOOL),
			"a raider ghast's fireball blew away the wool of a builder's finished build: " + helper.getBlockState(wool));
		helper.assertTrue(helper.getBlockState(playersWool).isAir(),
			"the fireball didn't blow away a player's own wool beside it, so the test can't see the blast: " + helper.getBlockState(playersWool));
		helper.succeed();
	}

	/**
	 * A raider lit TNT and then left (discarded) before it went off: the TNT is still a threat's, so the builders' wool
	 * beside it stands, while a player's own wool on the other side is blown away.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaOwnerGoneTnt1108d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaOwnerGoneTnt1108d")
	public void aRaidersTntStillSparesTheBuildersBlockAfterTheRaiderLeft(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		griefing(helper);
		BlockPos wool = new BlockPos(6, 2, 6);
		BlockPos playersWool = new BlockPos(8, 2, 6);
		BlockPos burst = new BlockPos(7, 2, 6);
		helper.setBlock(wool, Blocks.WHITE_WOOL);
		helper.setBlock(playersWool, Blocks.WHITE_WOOL);
		QaSiegeDamage1108dGameTests.finished(helper, wool, "qa_gone_tnt_1108d_");
		Pillager raider = raider(helper);
		Vec3 at = Vec3.atCenterOf(helper.absolutePos(burst));
		PrimedTnt tnt = new PrimedTnt(helper.getLevel(), at.x, at.y, at.z, raider);
		raider.discard();
		blast(helper, tnt, burst, 2f, Level.ExplosionInteraction.TNT);
		helper.assertTrue(helper.getBlockState(wool).is(Blocks.WHITE_WOOL),
			"TNT a raider lit blew away the wool of a builder's finished build after the raider left: " + helper.getBlockState(wool));
		helper.assertTrue(helper.getBlockState(playersWool).isAir(),
			"the TNT didn't blow away a player's own wool beside it, so the test can't see the blast: " + helper.getBlockState(playersWool));
		helper.succeed();
	}
}
