package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane B, 2026-11-09 (qa-lane-b-1109-0339): B1066, B1071 and B1079, from docs/wiki/sieges.md ("a threat is a raider
 * or a projectile one shot ... a raider's shot stays a raider's until it lands; a bounce with no owner keeps it, a new
 * owner drops it") and docs/wiki/village-fire.md (burning arrows). Covers what ThreatDamageGameTests,
 * QaThreatSpare1109bGameTests, QaThreatOwnerGone1108dBugTests, FireGameTests and QaConvertedThreats1109dGameTests leave
 * out: B1079 through a real bounce off the builders' frame (shooter discarded, killed, unloaded), the controls (a player's
 * and a plain skeleton's arrow still knock the item out of that frame as vanilla does), the mark through a bounce, a
 * save and a take-over in every order, and a burning raider's arrow bounced with its shooter gone, with village fires
 * switched off and against a player's burning arrow.
 */
public class QaRaiderArrows1109GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	// ---- B1079 through a real bounce ---------------------------------------------------------------------------------

	/**
	 * A burning-free arrow of {@code owner}, flying west at the builders' frame A from 1.5 blocks off (no gravity, the same
	 * path every run, the way QaThreatSpare1109bGameTests aims it); {@code gone} then takes the shooter away mid-flight.
	 */
	private static Arrow shoot(GameTestHelper helper, Entity owner, Consumer<Entity> gone) {
		ServerLevel level = helper.getLevel();
		Arrow arrow = EntityType.ARROW.create(level);
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(owner);
		var a = helper.absolutePos(QaThreatSpare1109bGameTests.FRAME_A);
		arrow.moveTo(a.getX() + 1.5, a.getY() + 0.45, a.getZ() + 0.5, 90f, 0f);
		arrow.setNoGravity(true);
		arrow.setDeltaMovement(-1.5, 0, 0);
		level.addFreshEntity(arrow);
		gone.accept(owner);
		return arrow;
	}

	private static Pillager raider(GameTestHelper helper) {
		Pillager raider = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 2, 2));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		return raider;
	}

	/** Both builders' frames keep their diamonds and the bounced arrow is still a raider's shot. */
	private static void bothFramesStand(GameTestHelper helper, Arrow arrow, String how) {
		helper.runAfterDelay(120, () -> {
			helper.assertTrue(arrow.getOwner() == null, "setup: the " + how + " raider still reads as the arrow's owner");
			List<ItemFrame> a = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_A);
			helper.assertTrue(!a.isEmpty() && a.get(0).getItem().is(Items.DIAMOND),
				"the " + how + " raider's arrow knocked the diamond out of the builders' frame A");
			List<ItemFrame> b = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_B);
			helper.assertTrue(!b.isEmpty() && b.get(0).getItem().is(Items.DIAMOND),
				"the " + how + " raider's arrow, bounced off the builders' frame A, knocked the diamond out of their frame B (arrow tags "
					+ arrow.getTags() + ")");
			helper.assertTrue(ThreatDamage.isThreat(arrow), "the " + how + " raider's arrow is nobody's after the bounce (tags " + arrow.getTags() + ")");
			helper.getLevel().getEntitiesOfClass(Arrow.class, helper.getBounds()).forEach(Arrow::discard);
			helper.succeed();
		});
	}

	/** B1079: the raider left (dawn, a call-off) while his arrow flew; it bounces off frame A and spares frame B too. */
	//$ gametest_ticks_batch AREA '200' '"qaRaiderArrows1109Discarded"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaiderArrows1109Discarded")
	public void aDiscardedRaidersArrowBouncedOffTheBuildersFrameSparesTheOtherFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, true);
		bothFramesStand(helper, shoot(helper, raider(helper), Entity::discard), "discarded");
	}

	/** B1079: a guard killed the raider while his arrow flew (the body still lying there as it falls). */
	//$ gametest_ticks_batch AREA '200' '"qaRaiderArrows1109Killed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaiderArrows1109Killed")
	public void aKilledRaidersArrowBouncedOffTheBuildersFrameSparesTheOtherFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, true);
		Arrow arrow = shoot(helper, raider(helper), Entity::kill);
		helper.runAfterDelay(1, () -> helper.assertTrue(ThreatDamage.isThreat(arrow), "a dying raider's arrow isn't a threat's"));
		bothFramesStand(helper, arrow, "killed");
	}

	/** B1079: the raider's chunk unloaded while his arrow flew. */
	//$ gametest_ticks_batch AREA '200' '"qaRaiderArrows1109Unloaded"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaiderArrows1109Unloaded")
	public void anUnloadedRaidersArrowBouncedOffTheBuildersFrameSparesTheOtherFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, true);
		bothFramesStand(helper, shoot(helper, raider(helper), e -> e.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK)), "unloaded");
	}

	/** Control: a player's arrow into the builders' frame knocks its diamond out, as the game does (only threats are refused). */
	//$ gametest_ticks_batch AREA '200' '"qaRaiderArrows1109Player"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaiderArrows1109Player")
	public void aPlayersArrowStillKnocksTheItemOutOfTheBuildersFrame(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, false);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		Arrow arrow = shoot(helper, player, e -> {
		});
		helper.assertTrue(!ThreatDamage.isThreat(arrow), "a player's arrow counts as a threat's");
		helper.runAfterDelay(40, () -> {
			List<ItemFrame> a = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_A);
			helper.assertTrue(a.isEmpty() || a.get(0).getItem().isEmpty(),
				"a player's arrow left the diamond in the builders' frame (the raiders' rule reached a player's shot), or it missed");
			helper.getLevel().getEntitiesOfClass(Arrow.class, helper.getBounds()).forEach(Arrow::discard);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(ItemEntity::discard);
			helper.succeed();
		});
	}

	/** Control: a skeleton that isn't a raider, killed while its arrow flew: the arrow is nobody's and knocks the diamond out. */
	//$ gametest_ticks_batch AREA '200' '"qaRaiderArrows1109Plain"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRaiderArrows1109Plain")
	public void aPlainSkeletonsArrowWhoseShooterWasKilledKnocksTheItemOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaThreatSpare1109bGameTests.hut(helper, true, false);
		Skeleton plain = helper.spawn(EntityType.SKELETON, new BlockPos(2, 2, 2));
		plain.setNoAi(true);
		Arrow arrow = shoot(helper, plain, e -> {
			e.kill();
			e.discard();
		});
		helper.assertTrue(!ThreatDamage.isThreat(arrow), "a plain skeleton's arrow, its shooter gone, counts as a threat's");
		helper.runAfterDelay(40, () -> {
			List<ItemFrame> a = QaThreatSpare1109bGameTests.frames(helper, QaThreatSpare1109bGameTests.FRAME_A);
			helper.assertTrue(a.isEmpty() || a.get(0).getItem().isEmpty(),
				"a plain skeleton's arrow left the diamond in the builders' frame (the fix reached a shot that was never a raider's), or it missed");
			helper.getLevel().getEntitiesOfClass(Arrow.class, helper.getBounds()).forEach(Arrow::discard);
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(ItemEntity::discard);
			helper.succeed();
		});
	}

	// ---- The mark in every order ----------------------------------------------------------------------------------------

	private static Arrow reload(GameTestHelper helper, Arrow arrow) {
		CompoundTag nbt = new CompoundTag();
		helper.assertTrue(arrow.save(nbt), "setup: the arrow didn't save");
		Arrow loaded = (Arrow) EntityType.create(nbt, helper.getLevel()).orElse(null);
		helper.assertTrue(loaded != null, "setup: the arrow didn't load");
		return loaded;
	}

	/**
	 * B1066/B1079 by the spec's words: a raider's shot, its shooter gone, stays a raider's through two bounces (owner
	 * null) and a save and reload in between; a bounce never makes a plain mob's or nobody's arrow a threat's; a real new
	 * owner (a player knocking it back) drops the mark, and a later bounce or reload doesn't bring it back; a raider's shot
	 * handed to another raider stays a threat's.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aRaidersMarkSurvivesBouncesAndReloadsButNotANewOwner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pillager raider = EntityType.PILLAGER.create(level);
		Pillager other = EntityType.PILLAGER.create(level);
		Skeleton plain = EntityType.SKELETON.create(level);
		helper.assertTrue(raider != null && other != null && plain != null, "setup: no mobs");
		raider.addTag(Threats.RAIDER_TAG);
		other.addTag(Threats.RAIDER_TAG);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));

		Arrow shot = EntityType.ARROW.create(level);
		Arrow plainShot = EntityType.ARROW.create(level);
		Arrow nobodys = EntityType.ARROW.create(level);
		Arrow handed = EntityType.ARROW.create(level);
		helper.assertTrue(shot != null && plainShot != null && nobodys != null && handed != null, "setup: no arrows");
		shot.setOwner(raider);
		plainShot.setOwner(plain);
		handed.setOwner(raider);
		raider.discard();
		plain.discard();
		helper.assertTrue(shot.getOwner() == null, "setup: the gone raider still reads as the owner");

		shot.setOwner(null);
		helper.assertTrue(ThreatDamage.isThreat(shot), "a gone raider's arrow lost its mark at the first bounce");
		Arrow loaded = reload(helper, shot);
		helper.assertTrue(ThreatDamage.isThreat(loaded), "a gone raider's bounced arrow lost its mark through a save and reload");
		loaded.setOwner(null);
		helper.assertTrue(ThreatDamage.isThreat(loaded), "a gone raider's arrow lost its mark at a second bounce after a reload");

		plainShot.setOwner(null);
		nobodys.setOwner(null);
		helper.assertTrue(!ThreatDamage.isThreat(plainShot), "a plain skeleton's arrow became a threat's when it bounced");
		helper.assertTrue(!ThreatDamage.isThreat(nobodys), "an arrow nobody shot became a threat's when it bounced");
		helper.assertTrue(!ThreatDamage.isThreat(reload(helper, plainShot)), "a plain skeleton's bounced arrow became a threat's after a reload");

		loaded.setOwner(player);
		helper.assertTrue(!ThreatDamage.isThreat(loaded), "a raider's arrow a player knocked back still counts as a threat's");
		loaded.setOwner(null);
		helper.assertTrue(!ThreatDamage.isThreat(loaded), "a player's knocked-back arrow became a threat's again when it bounced");
		helper.assertTrue(!ThreatDamage.isThreat(reload(helper, loaded)), "a player's knocked-back arrow became a threat's again after a reload");

		handed.setOwner(other);
		helper.assertTrue(ThreatDamage.isThreat(handed), "a raider's arrow taken over by another raider stopped counting as a threat's");
		helper.succeed();
	}

	// ---- B1071: the burning arrow, bounced ----------------------------------------------------------------------------

	/** A village hall and a 5 x 5 log hut the builders "finished" (as FireGameTests' hut); everything put back afterwards. */
	private static ServerLevel village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		Set<String> disabled = Disasters.DISABLED;
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		VillageFire.reset();
		level.setWeatherParameters(6000, 0, false, false);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Disasters.DISABLED = disabled;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
				if (level.getBlockState(p).is(BlockTags.FIRE)) {
					level.removeBlock(p, false);
				}
			}
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(8), m -> !(m instanceof Villager)).forEach(Mob::discard);
			VillageFire.reset();
		});
		BlockPos corner = new BlockPos(12, 2, 12);
		helper.setBlock(new BlockPos(19, 2, 2), ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(corner.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(corner.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(corner.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_arrows_1109_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(corner), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(corner), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return level;
	}

	/** How often in 40 hits on the hut's roof {@code arrow} set it alight (each fire put out again), with a fixed random. */
	private static int lit(GameTestHelper helper, ServerLevel level, Entity arrow, long seed) {
		BlockPos roof = helper.absolutePos(new BlockPos(14, 4, 14));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(roof).add(0, 0.5, 0), Direction.UP, roof, false);
		RandomSource random = RandomSource.create(seed);
		int lit = 0;
		for (int i = 0; i < 40; i++) {
			Optional<BlockPos> f = VillageFire.arrow(level, arrow, hit, random);
			if (f.isPresent()) {
				helper.assertTrue(f.get().equals(roof.above()), "the arrow's fire isn't where it hit: " + f.get());
				lit++;
				level.removeBlock(f.get(), false);
			}
		}
		return lit;
	}

	/**
	 * B1071 with B1079: a raider's burning arrow whose shooter was discarded mid-flight, then bounced (owner null) and
	 * saved and loaded, still lights the builders' roof about 1 in 4; a player's burning arrow (an arrow through lava,
	 * a Flame bow) never does, nor one a player knocked back from the raider; with village fires switched off in the
	 * config's {@code disasters} map, the raider's arrow lights nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaiderArrows1109Fire"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaiderArrows1109Fire")
	public void aGoneRaidersBurningArrowStillLightsTheRoofAfterABounceButNotWithFiresOff(GameTestHelper helper) {
		ServerLevel level = village(helper);
		helper.runAfterDelay(2, () -> {
			Skeleton raider = helper.spawn(EntityType.SKELETON, new BlockPos(5, 2, 18));
			raider.setNoAi(true);
			raider.addTag(Threats.RAIDER_TAG);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
			Arrow shot = burning(helper, raider);
			Arrow knocked = burning(helper, raider);
			Arrow players = burning(helper, player);
			raider.discard();
			shot.setOwner(null); // it bounced off something that refused the hit
			Arrow loaded = reload(helper, shot);
			helper.assertTrue(loaded.getOwner() == null && loaded.isOnFire(), "setup: the reloaded arrow: " + loaded.getOwner() + " " + loaded.isOnFire());
			knocked.setOwner(player);
			helper.assertTrue(lit(helper, level, players, 7L) == 0, "a player's burning arrow lit the builders' roof");
			helper.assertTrue(lit(helper, level, knocked, 7L) == 0, "a raider's burning arrow a player knocked back lit the roof as a raider's");
			int bounced = lit(helper, level, shot, 42L);
			int reloaded = lit(helper, level, loaded, 42L);
			helper.assertTrue(bounced > 3 && bounced < 37, "a gone raider's burning arrow lit the roof " + bounced + " times in 40 after a bounce, not about 1 in 4 (tags "
				+ shot.getTags() + ")");
			helper.assertTrue(reloaded > 3 && reloaded < 37, "a gone raider's bounced burning arrow lit the roof " + reloaded + " times in 40 after a reload");
			Disasters.DISABLED = Set.of("village_fire");
			helper.assertTrue(lit(helper, level, loaded, 42L) == 0, "a raider's burning arrow lit the roof with village fires switched off");
			helper.succeed();
		});
	}

	private static Arrow burning(GameTestHelper helper, Entity owner) {
		Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(owner);
		arrow.igniteForSeconds(5f);
		return arrow;
	}
}
