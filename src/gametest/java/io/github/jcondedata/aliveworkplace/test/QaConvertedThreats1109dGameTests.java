package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatDamage;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-09): converted threats, written from the bug texts of B1054 (a raider, or any THREAT_TAGS mob,
 * that converts in vanilla stays that raid's raider: counted, the raid not beaten while it walks, sent away at dawn,
 * builders' blocks still spared), B1071 (a raider's burning arrow lights the roof after its shooter is killed or
 * unloaded, not only discarded) and B1072 (a converted lair captain or bounty band member takes the old one's place).
 * Covers what ConvertedMobsGameTests, QaRaiderDrowns1108dBugTests, FireGameTests and B1072GameTests leave out: a raider
 * skeleton frozen in real powder snow, the raid's own count through the hall's raid tick (night, dawn, a second
 * village's raid), ThreatDamage on a converted raider, every threat tag, a killed / unloaded / saved / converted
 * shooter, a converted captain leading a raid coming home at noon, a restart after the conversion, and a bounty band
 * member that converts leaving with the band.
 */
public class QaConvertedThreats1109dGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final long DAY = 24000L;

	// ---- shared set-up ----------------------------------------------------------------------------------------------

	/**
	 * A hall at {@code local} (village radius {@code radius}, put back once afterwards), raids forgotten and the clock put
	 * back afterwards.
	 */
	private static BlockPos hall(GameTestHelper helper, BlockPos local, int radius) {
		ServerLevel level = helper.getLevel();
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		if (radius != was) {
			VillageHalls.RADIUS = radius;
			Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		}
		helper.setBlock(local, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(local);
		ThreatData.get(level).end(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(32), m -> !(m instanceof Villager) && Threats.isThreatMob(m)).forEach(Mob::discard);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageRaids.forget(hall);
			level.setDayTime(time);
		});
		return hall;
	}

	/** A monsters' raid on {@code hall} that began {@code ago} game ticks back, as the hall's raid tick sees it. */
	private static ThreatData.Under raid(ServerLevel level, BlockPos hall, ResourceLocation culture, int raiders, long ago) {
		ThreatData.Under raid = new ThreatData.Under(hall.immutable(), culture, raiders, level.getGameTime() - ago);
		ThreatData.get(level).begin(raid);
		return raid;
	}

	private static void tick(ServerLevel level, BlockPos hall) {
		VillageRaids.tick(level, hall, 8, 0, Chronicle.day(level) - 10, d -> { });
	}

	private static String lastRaidLine(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return "(no hall)";
		}
		List<Chronicle.Entry> raids = entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).toList();
		return raids.isEmpty() ? "" : raids.get(raids.size() - 1).text().getString();
	}

	private static void atNight(ServerLevel level) {
		level.setDayTime(Chronicle.day(level) * DAY + 18000);
	}

	// ---- B1054: the raid's count ------------------------------------------------------------------------------------

	/**
	 * A raider skeleton stuck in real powder snow (the game's own freeze, not a direct convertTo) freezes into a stray:
	 * the stray carries the raider tag and this raid's stamp, keeps "never despawn", and the hall's raid tick at night
	 * keeps the raid going while it lives; only once it is killed is the raid fought off.
	 */
	//$ gametest_ticks_batch BIG '1200' '"qa1109dRaiderFreezes"'
	@GameTest(template = BIG, timeoutTicks = 1200, batch = "qa1109dRaiderFreezes")
	public void aRaiderSkeletonFrozenInPowderSnowKeepsItsRaidGoing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(2, 2, 2), 16);
		BlockPos pit = new BlockPos(10, 2, 10);
		for (int y = 0; y < 4; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					boolean inside = dx == 0 && dz == 0 && y < 3;
					// a stone lid: no sky over it, so the skeleton doesn't burn by day
					helper.setBlock(pit.offset(dx, y, dz), inside ? Blocks.POWDER_SNOW : y == 3 ? Blocks.STONE : Blocks.GLASS);
				}
			}
		}
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, pit.above());
		skeleton.setPersistenceRequired();
		skeleton.addTag(Threats.RAIDER_TAG);
		VillageRaids.track(level, hall, Threats.get(Threats.MONSTERS).orElseThrow(), List.of(skeleton));
		ThreatData.Under under = ThreatData.get(level).raid(hall).orElse(null);
		helper.assertTrue(under != null, "setup: no raid on the hall");
		String stamp = VillageRaids.stampOf(under);
		helper.assertTrue(skeleton.getTags().contains(stamp), "setup: the skeleton has no raid stamp: " + skeleton.getTags());
		AABB box = new AABB(helper.absolutePos(pit)).inflate(1.5, 3, 1.5);
		long start = level.getGameTime();
		helper.succeedWhen(() -> {
			List<Stray> strays = level.getEntitiesOfClass(Stray.class, box, Stray::isAlive);
			helper.assertTrue(!strays.isEmpty(), "(the skeleton hasn't frozen into a stray yet)");
			Stray stray = strays.get(0);
			helper.assertTrue(!skeleton.isAlive(), "setup: the skeleton is still there beside the stray");
			// the game's freeze: 140 ticks in the snow, then 300 converting (not a stray from elsewhere)
			helper.assertTrue(level.getGameTime() - start >= 400, "setup: a stray after only " + (level.getGameTime() - start) + " ticks isn't the freeze");
			helper.assertTrue(stray.getTags().contains(Threats.RAIDER_TAG) && stray.getTags().contains(stamp),
				"the stray frozen from a raider isn't that raid's raider: " + stray.getTags());
			helper.assertTrue(stray.isPersistenceRequired(), "the stray lost 'never despawn'");
			atNight(level);
			tick(level, hall);
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid ended with its frozen raider still alive: '" + lastRaidLine(level, hall) + "'");
			helper.assertTrue(VillageRaids.raiders(level, hall).contains(stray), "the village doesn't count the stray among the raiders");
			stray.kill();
			tick(level, hall);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid goes on with its last raider dead");
			helper.assertTrue(lastRaidLine(level, hall).contains("fought off"), "the chronicle's raid line: '" + lastRaidLine(level, hall) + "'");
		});
	}

	/**
	 * A raid zombie that drowns into a drowned: the night's raid tick keeps the raid on (not beaten while it walks), and
	 * at dawn the drowned is sent away with the raid ("fled"), not left behind forever.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1109dRaiderDrownsDawn"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1109dRaiderDrownsDawn")
	public void aRaidZombieThatDrownsHoldsTheRaidAndLeavesAtDawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, new BlockPos(2, 2, 2), 16);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 8));
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		zombie.addTag(Threats.RAIDER_TAG);
		ThreatData.Under under = raid(level, hall, Threats.MONSTERS, 1, 3000);
		VillageRaids.stamp(zombie, under);
		Drowned drowned = zombie.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null && !zombie.isAlive(), "setup: the zombie didn't turn into a drowned");
		atNight(level);
		tick(level, hall);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid ended as beaten while its drowned raider walks the village: '"
			+ lastRaidLine(level, hall) + "', drowned tags " + drowned.getTags());
		level.setDayTime((Chronicle.day(level) + 1) * DAY + 1000);
		helper.assertTrue(level.getGameTime() - under.began() > 1200, "setup: the raid is too young to leave");
		tick(level, hall);
		helper.assertTrue(drowned.isRemoved(), "the drowned raider wasn't sent away at dawn");
		helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end at dawn");
		helper.assertTrue(lastRaidLine(level, hall).contains("fled"), "the raid didn't end as fled at dawn: '" + lastRaidLine(level, hall) + "'");
		helper.succeed();
	}

	/**
	 * Two villages raided at once: village A's raider zombie drowns by A's hall. It stays A's (its stamp is A's), it keeps
	 * A's raid on, and it does not hold up B's raid, whose own raiders are all dead.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1109dTwoVillages"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1109dTwoVillages")
	public void aConvertedRaiderCountsForItsOwnVillagesRaidOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = hall(helper, new BlockPos(2, 2, 2), 4);
		BlockPos b = hall(helper, new BlockPos(27, 2, 27), 4);
		ThreatData.Under raidA = raid(level, a, Threats.MONSTERS, 1, 100);
		ThreatData.Under raidB = raid(level, b, Threats.MONSTERS, 1, 100);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		zombie.setNoAi(true);
		zombie.addTag(Threats.RAIDER_TAG);
		VillageRaids.stamp(zombie, raidA);
		Zombie theirs = helper.spawn(EntityType.ZOMBIE, new BlockPos(24, 2, 24));
		theirs.setNoAi(true);
		theirs.addTag(Threats.RAIDER_TAG);
		VillageRaids.stamp(theirs, raidB);
		theirs.discard(); // B's raider is slain
		Drowned drowned = zombie.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null, "setup: the zombie didn't turn into a drowned");
		helper.assertTrue(VillageRaids.stampOn(drowned) != null && VillageRaids.stampOn(drowned).equals(VillageRaids.stampOf(raidA)),
			"the drowned isn't stamped with A's raid: " + drowned.getTags());
		atNight(level);
		tick(level, b);
		tick(level, a);
		helper.assertTrue(VillageRaids.active(a).isPresent(), "A's raid ended with A's drowned raider alive");
		helper.assertTrue(VillageRaids.active(b).isEmpty(), "B's raid is held up by A's drowned raider (B's own are all dead)");
		helper.assertTrue(VillageRaids.stampOn(drowned).equals(VillageRaids.stampOf(raidA)), "A's drowned raider was restamped: " + drowned.getTags());
		helper.succeed();
	}

	// ---- B1054: ThreatDamage and every threat tag -------------------------------------------------------------------

	/**
	 * A raider skeleton frozen into a stray must still leave the builders' blocks standing (ThreatDamage), and an arrow the
	 * stray then shoots is a raider's shot; a plain skeleton's stray is no threat and spares nothing (the control).
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1109dConvertedSpares"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1109dConvertedSpares")
	public void aConvertedRaiderStillSparesTheBuildersBlocks(GameTestHelper helper) {
		Hut hut = hut(helper, "spares");
		helper.runAfterDelay(2, () -> {
			ServerLevel level = hut.level();
			BlockPos roof = helper.absolutePos(new BlockPos(14, 4, 14));
			Skeleton raider = helper.spawn(EntityType.SKELETON, new BlockPos(5, 2, 18));
			raider.setNoAi(true);
			raider.addTag(Threats.RAIDER_TAG);
			helper.assertTrue(ThreatDamage.spares(level, roof, raider), "setup: the raider skeleton doesn't spare the builders' roof");
			Skeleton plain = helper.spawn(EntityType.SKELETON, new BlockPos(7, 2, 18));
			plain.setNoAi(true);
			Stray stray = raider.convertTo(EntityType.STRAY, true);
			Stray plainStray = plain.convertTo(EntityType.STRAY, true);
			helper.assertTrue(stray != null && plainStray != null, "setup: no strays");
			helper.assertTrue(ThreatDamage.isThreat(stray), "the raider's stray is no threat to ThreatDamage: " + stray.getTags());
			helper.assertTrue(ThreatDamage.spares(level, roof, stray), "the raider's stray doesn't spare the builders' roof");
			Arrow shot = burning(helper, stray);
			helper.assertTrue(shot.getTags().contains(ThreatDamage.SHOT_TAG), "an arrow the raider's stray shot isn't a raider's shot: " + shot.getTags());
			helper.assertTrue(!ThreatDamage.spares(level, roof, plainStray), "a plain skeleton's stray is spared from the builders' roof as if a raider");
			stray.discard();
			plainStray.discard();
			helper.succeed();
		});
	}

	/**
	 * Every threat tag (THREAT_TAGS: raiders, lairs, bandits, the Bandit fort and King, bounties, rogues, arc mobs) and the
	 * captains' tags survive a husk drowning into a zombie, one tag at a time; the zombie is still a threat's mob.
	 */
	//$ gametest_ticks_batch BIG '40' '"qa1109dEveryTag"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "qa1109dEveryTag")
	public void everyThreatTagSurvivesAHuskDrowning(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<String> tags = new java.util.ArrayList<>(Threats.THREAT_TAGS);
		tags.add(Lairs.CAPTAIN_TAG);
		tags.add(Lairs.BANDIT_CHIEF_TAG);
		tags.add(Bounties.CAPTAIN_TAG);
		for (String tag : tags) {
			Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
			husk.setNoAi(true);
			husk.addTag(tag);
			Zombie zombie = husk.convertTo(EntityType.ZOMBIE, true);
			helper.assertTrue(zombie != null, "setup: the husk tagged " + tag + " didn't convert");
			helper.assertTrue(zombie.getTags().contains(tag), "a husk tagged " + tag + " drowned into a zombie without it: " + zombie.getTags());
			if (Threats.THREAT_TAGS.contains(tag)) {
				helper.assertTrue(Threats.isThreatMob(zombie), "the zombie from a husk tagged " + tag + " is no threat's mob");
			}
			zombie.discard();
		}
		helper.succeed();
	}

	// ---- B1071: the burning arrow -----------------------------------------------------------------------------------

	private record Hut(ServerLevel level, BlockPos hall) {
	}

	/** A village hall and a 5 x 5 log hut the builders "finished", as FireGameTests' hut, put back afterwards. */
	private static Hut hut(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		VillageFire.reset();
		level.setWeatherParameters(6000, 0, false, false);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
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
		BlockPos hallAt = new BlockPos(19, 2, 2);
		BlockPos corner = new BlockPos(12, 2, 12);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(corner.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(corner.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(corner.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1109d_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(corner), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(corner), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return new Hut(level, helper.absolutePos(hallAt));
	}

	/** A burning arrow shot by {@code owner} (not added to the level). */
	private static Arrow burning(GameTestHelper helper, Entity owner) {
		Arrow arrow = EntityType.ARROW.create(helper.getLevel());
		helper.assertTrue(arrow != null, "setup: no arrow");
		arrow.setOwner(owner);
		arrow.igniteForSeconds(5f);
		return arrow;
	}

	/** How often in 40 hits on the roof {@code arrow} set it alight (each fire put out again), with a fixed random. */
	private static int lit(GameTestHelper helper, ServerLevel level, Entity arrow, long seed) {
		BlockPos roof = helper.absolutePos(new BlockPos(15, 4, 15));
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
	 * Raiders' burning arrows still in flight when their shooters are gone in each other way than a discard: killed (once
	 * the body is removed), unloaded with its chunk, the arrow itself saved and loaded again (a chunk unloaded and
	 * reloaded mid-flight), and the shooter frozen into a stray (B1054's conversion) mid-flight. Each lights the roof by
	 * its chance. Controls: a plain skeleton's arrow whose shooter was killed, and a raider's arrow a player deflected
	 * (now the player's), never do.
	 */
	//$ gametest_ticks_batch BIG '120' '"qa1109dArrowShooterGone"'
	@GameTest(template = BIG, timeoutTicks = 120, batch = "qa1109dArrowShooterGone")
	public void aRaidersBurningArrowLightsTheRoofAfterItsShooterIsKilledUnloadedOrConverted(GameTestHelper helper) {
		Hut hut = hut(helper, "arrow_gone");
		ServerLevel level = hut.level();
		Skeleton killed = raiderSkeleton(helper, new BlockPos(2, 2, 18), true);
		Skeleton unloaded = raiderSkeleton(helper, new BlockPos(4, 2, 18), true);
		Skeleton saved = raiderSkeleton(helper, new BlockPos(6, 2, 18), true);
		Skeleton frozen = raiderSkeleton(helper, new BlockPos(8, 2, 18), true);
		Skeleton plain = raiderSkeleton(helper, new BlockPos(10, 2, 18), false);
		Skeleton deflected = raiderSkeleton(helper, new BlockPos(2, 2, 22), true);
		Arrow killedShot = burning(helper, killed);
		Arrow unloadedShot = burning(helper, unloaded);
		Arrow savedShot = burning(helper, saved);
		Arrow frozenShot = burning(helper, frozen);
		Arrow plainShot = burning(helper, plain);
		Arrow deflectedShot = burning(helper, deflected);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		deflectedShot.setOwner(player); // the player knocks it back: it is the player's now
		killed.kill();
		plain.kill();
		unloaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		CompoundTag nbt = new CompoundTag();
		helper.assertTrue(savedShot.save(nbt), "setup: the arrow didn't save");
		saved.discard();
		Arrow reloaded = (Arrow) EntityType.create(nbt, level).orElse(null);
		Stray stray = frozen.convertTo(EntityType.STRAY, true);
		helper.assertTrue(stray != null, "setup: the raider skeleton didn't freeze into a stray");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(killed.isRemoved() && plain.isRemoved(), "setup: the killed skeletons' bodies are still there");
			helper.assertTrue(killedShot.getOwner() == null && unloadedShot.getOwner() == null && frozenShot.getOwner() == null && plainShot.getOwner() == null,
				"setup: a shooter is still there");
			helper.assertTrue(reloaded != null && reloaded.getOwner() == null && reloaded.isOnFire(), "setup: the reloaded arrow: " + reloaded);
			helper.assertTrue(lit(helper, level, plainShot, 7L) == 0, "a plain skeleton's arrow lit the roof after its shooter was killed");
			helper.assertTrue(lit(helper, level, deflectedShot, 7L) == 0, "a raider's arrow a player deflected lit the roof as a raider's");
			int byKilled = lit(helper, level, killedShot, 42L);
			int byUnloaded = lit(helper, level, unloadedShot, 42L);
			int bySaved = lit(helper, level, reloaded, 42L);
			int byFrozen = lit(helper, level, frozenShot, 42L);
			helper.assertTrue(byKilled > 3 && byKilled < 37, "a raider's arrow whose shooter was killed lit the roof " + byKilled + " times in 40, not about 1 in 4");
			helper.assertTrue(byUnloaded > 3 && byUnloaded < 37, "a raider's arrow whose shooter was unloaded lit the roof " + byUnloaded + " times in 40");
			helper.assertTrue(bySaved > 3 && bySaved < 37, "a raider's arrow saved and loaded mid-flight lit the roof " + bySaved + " times in 40 (tags "
				+ reloaded.getTags() + ")");
			helper.assertTrue(byFrozen > 3 && byFrozen < 37, "a raider's arrow whose shooter froze into a stray lit the roof " + byFrozen + " times in 40");
			stray.discard();
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).forEach(ItemEntity::discard);
			helper.succeed();
		});
	}

	private static Skeleton raiderSkeleton(GameTestHelper helper, BlockPos at, boolean raider) {
		Skeleton s = helper.spawn(EntityType.SKELETON, at);
		s.setNoAi(true);
		if (raider) {
			s.addTag(Threats.RAIDER_TAG);
		}
		return s;
	}

	// ---- B1072: lair captains and bounty bands ----------------------------------------------------------------------

	private static final BlockPos TOWN_HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/** A Town-ranked hall with lairs and bounties on (none posted by chance), as B1072GameTests'; all put back afterwards. */
	private static BlockPos town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		Bounties.forget(level);
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		boolean bounties = Bounties.ENABLED;
		boolean auto = Bounties.AUTO;
		int threat = Threats.LEVEL;
		VillageHalls.RADIUS = 20;
		Lairs.ENABLED = true;
		Bounties.ENABLED = true;
		Bounties.AUTO = false;
		helper.setBlock(TOWN_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(TOWN_HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> !(m instanceof Villager)
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG) || m.getTags().contains(Lairs.CAPTAIN_TAG)
					|| m.getTags().contains(Bounties.TAG))).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16)).forEach(ItemEntity::discard);
			ThreatData.get(level).end(hall);
			Lairs.forget(level);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Bounties.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = was;
			Lairs.ENABLED = lairs;
			Bounties.ENABLED = bounties;
			Bounties.AUTO = auto;
			Threats.LEVEL = threat;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		level.setDayTime(49 * DAY + 1000);
		return hall;
	}

	/**
	 * The Pharaoh leads his raid on the town (a lair captain with the raider tag), drowns into a zombie on the way, and
	 * lives to the raid's end at noon (the desert raiders stay until noon): the zombie still counts for the raid in the
	 * morning, then goes home to his tomb as its captain, as the Pharaoh would, instead of vanishing with the others; the
	 * lair still stands with him at its head.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1109dCaptainLeadsHome"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1109dCaptainLeadsHome")
	public void aLairCaptainWhoDrownsLeadingARaidGoesHomeAsCaptain(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Husk pharaoh = level.getEntity(lair.captain()) instanceof Husk h ? h : null;
		helper.assertTrue(pharaoh != null, "setup: the lair's captain is no husk");
		pharaoh.teleportTo(helper.absolutePos(new BlockPos(6, 2, 6)).getX() + 0.5, helper.absolutePos(new BlockPos(6, 2, 6)).getY(),
			helper.absolutePos(new BlockPos(6, 2, 6)).getZ() + 0.5);
		pharaoh.setNoAi(true);
		pharaoh.addTag(Threats.RAIDER_TAG);
		helper.assertTrue(Lairs.leading(pharaoh), "setup: the Pharaoh isn't leading the raid");
		ThreatData.Under under = raid(level, hall, DESERT, 1, 3000);
		VillageRaids.stamp(pharaoh, under);
		Zombie zombie = pharaoh.convertTo(EntityType.ZOMBIE, true);
		helper.assertTrue(zombie != null && !pharaoh.isAlive(), "setup: the husk didn't turn into a zombie");
		level.setDayTime(49 * DAY + 2000); // morning: the desert raiders are still about
		tick(level, hall);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "the raid ended in the morning with its drowned captain still leading it");
		level.setDayTime(49 * DAY + 7000); // past noon's start: the raid is over
		tick(level, hall);
		helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end at noon");
		helper.assertTrue(!zombie.isRemoved() && zombie.isAlive(), "the drowned captain vanished with the raiders instead of going home: " + zombie.getRemovalReason());
		helper.assertTrue(!zombie.getTags().contains(Threats.RAIDER_TAG), "the drowned captain went home still a raider: " + zombie.getTags());
		Lairs.Lair now = Lairs.near(level, hall).orElse(null);
		helper.assertTrue(now != null && now.captain().equals(zombie.getUUID()), "the lair lost its captain: " + now);
		helper.assertTrue(zombie.blockPosition().distManhattan(now.pos()) < 16, "the drowned captain didn't go back to his tomb: at "
			+ zombie.blockPosition().toShortString() + ", the lair at " + now.pos().toShortString());
		helper.succeed();
	}

	/**
	 * The Pharaoh drowns into a zombie while the lairs switch is off (the server owner had paused lairs), lairs come back
	 * on, the server restarts (lairs and bounties saved and loaded), and the hall's round runs: the zombie is still the
	 * lair's captain and the bounty's, the lair stands, and the player who kills him breaks it up and is paid.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1109dCaptainRestart"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1109dCaptainRestart")
	public void aConvertedLairCaptainIsStillCaptainAfterARestart(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Bounties.round(level, hall);
		Bounties.Bounty b = Bounties.open(level, hall).stream().filter(x -> x.lair).findFirst().orElse(null);
		helper.assertTrue(b != null && lair.captain().equals(b.captain), "setup: no bounty posted on the lair's captain");
		Mob pharaoh = (Mob) level.getEntity(lair.captain());
		Lairs.ENABLED = false;
		Zombie zombie = pharaoh.convertTo(EntityType.ZOMBIE, true);
		Lairs.ENABLED = true;
		helper.assertTrue(zombie != null, "setup: the husk didn't turn into a zombie");
		Lairs.Data.get(level).roundTrip(level.registryAccess());
		CompoundTag saved = Bounties.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Bounties.Bounty loaded = Bounties.Data.load(saved, level.registryAccess()).bounties.get(b.id);
		helper.assertTrue(loaded != null && zombie.getUUID().equals(loaded.captain), "after a save and load the bounty names " + (loaded == null ? null : loaded.captain)
			+ ", not the zombie " + zombie.getUUID());
		helper.assertTrue(Lairs.near(level, hall).map(l -> l.captain().equals(zombie.getUUID())).orElse(false),
			"after a restart the lair's captain is " + Lairs.near(level, hall).map(Lairs.Lair::captain).orElse(null) + ", not the zombie he became");
		Lairs.round(level, hall, false, RandomSource.create(5L));
		helper.assertTrue(Lairs.near(level, hall).isPresent(), "the hall's round after the restart broke the lair up while its drowned captain stood");
		ServerPlayer killer = helper.makeMockServerPlayerInLevel();
		killer.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			killer.getInventory().clearContent();
			level.getServer().getPlayerList().remove(killer);
		});
		for (int i = 0; i < 20 && zombie.isAlive(); i++) {
			zombie.invulnerableTime = 0;
			zombie.hurt(level.damageSources().playerAttack(killer), 1000f);
		}
		helper.assertTrue(!zombie.isAlive(), "setup: the zombie didn't fall");
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) > 0, "killing the drowned captain after a restart didn't break the lair up");
		helper.assertTrue(killer.getInventory().countItem(Items.EMERALD) == b.reward, "his killer was paid " + killer.getInventory().countItem(Items.EMERALD)
			+ " emeralds, not " + b.reward);
		helper.succeed();
	}

	/**
	 * A bounty captain's band member turns into another mob (a stand-in conversion: vanilla's illager bands don't convert,
	 * another mod or a zombie siege can): it takes the old one's place in the band, so when the unclaimed bounty comes down
	 * after its days the converted one leaves with the band instead of staying behind as an orphan.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qa1109dBandMember"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qa1109dBandMember")
	public void aBountyBandMemberThatConvertsLeavesWithTheBand(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		Bounties.forget(level);
		Bounties.ENABLED = true;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> m.getTags().contains(Bounties.TAG)).forEach(Mob::discard);
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Bounties.forget(level);
			level.setDayTime(time);
		});
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(5, 2, 5));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		level.setDayTime(199 * DAY + 1000);
		Bounties.Bounty b = Bounties.post(level, hall, AliveWorkplace.id("warlord"), 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(b != null, "setup: no bounty posted");
		BlockPos camp = helper.absolutePos(new BlockPos(18, 2, 18));
		helper.assertTrue(Bounties.raise(level, b, camp, camp.north(4)), "setup: the captain didn't stand");
		helper.assertTrue(!b.band.isEmpty(), "setup: no band");
		UUID old = b.band.get(0);
		Mob member = level.getEntity(old) instanceof Mob m ? m : null;
		helper.assertTrue(member != null, "setup: the band member isn't in the world");
		Zombie turned = member.convertTo(EntityType.ZOMBIE_VILLAGER, true);
		helper.assertTrue(turned != null && !member.isAlive(), "setup: the band member didn't convert");
		helper.assertTrue(b.band.contains(turned.getUUID()) && !b.band.contains(old), "the band still lists the old member, not the one he turned into: " + b.band);
		helper.assertTrue(Bounties.band(level, b).contains(turned), "the converted member isn't counted in the band");
		level.setDayTime(206 * DAY + 1000);
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "setup: the bounty is still up after 7 days");
		helper.assertTrue(turned.isRemoved(), "the converted band member stayed behind when the bounty came down");
		helper.succeed();
	}
}
