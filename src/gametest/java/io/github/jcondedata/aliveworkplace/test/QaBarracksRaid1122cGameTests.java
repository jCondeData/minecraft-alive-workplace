package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane C (2026-11-22, lane-c-1122-1853): B1475 checked from its spec, not the code. raids.md: "A guard turns out to a
 * raid's raider (ours, or a vanilla raid's) within {@code guardRadius} of his post whether he sees it or not, so walls no
 * longer keep a Barracks' guards at their grindstones while raiders stand outside; any other monster, and anything outside
 * a raid, he still has to see (or have within 4 blocks). A guard who ends up outside his patrol's square (14 blocks by day,
 * 6 at night, without a patrol route) walks straight back to his post before patrolling on." config-switches.md:
 * {@code guardRadius} 24, "how far from their workstation guards patrol and fight".
 *
 * <p>The finished Barracks I at (8, 2, 14) of a huge area (its grindstones end up at about (10, 3, 16) and (14, 3, 16), just
 * behind the front wall with its glass), a guard employed at each, by night, no bell. What {@link QaBarracksTurnOut1122dGameTests}
 * and its bug tests already cover (two still husks 8 to 10 out in front, the bell, the way back after) isn't repeated: here a
 * raider walking past, a crossbow pillager standing off at the corner, a vanilla raid's pillager, the {@code guardRadius}
 * boundary from both sides (at 12, since 24 reaches past the whole test area), and what keeps the sight rule: a pillager with
 * no raid, and a zombie that isn't a raider in a raid. A foe that must stay unseen stands in a small closed stone box, so only
 * the through-wall rule could pick it; a box at the same spot inside the radius proves the box doesn't stop that rule.
 * Last, a guard left outside his square (by day and by night, saved and reloaded on the way) walks back to his post.
 */
public class QaBarracksRaid1122cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaBarracksRaid1122c");
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_barracks_raid_1122c");
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos AT = new BlockPos(8, 2, 14);
	private static final int NIGHT = 14000;
	private static final int DAY = 6000;
	/** Night square (6) plus a step, as in QaBarracksTurnOut1122dGameTests. */
	private static final int HOME = 7;
	/** Day square (14) plus a step. */
	private static final int DAY_HOME = 15;
	/**
	 * "Back at his post": within this many blocks of the grindstone on each axis, on its floor and indoors (the grindstones
	 * stand in the row just behind the front wall, so indoors is a z no smaller than the grindstone's; the doorstep outside
	 * is within 2 blocks of a grindstone through the wall and doesn't count).
	 */
	private static final int AT_POST = 3;
	/** Ticks a guard has to come out (the bug test's 900). */
	private static final int TURN_OUT = 900;
	/** Ticks guards must stay home while a foe they mustn't pick waits outside. */
	private static final int STAY = 600;

	private record Scene(ServerLevel level, BlockPos hall, Culture culture, List<BlockPos> posts, List<Villager> guards) {
	}

	/** The village hall, the finished Barracks I with a sworded guard at each grindstone, at {@code dayTime}; {@code radius} sets guardRadius (0: the default). */
	private static Scene scene(GameTestHelper helper, int dayTime, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int hallRadius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 32;
		helper.setDayTime(dayTime);
		if (radius > 0) {
			WorkplaceConfig config = new WorkplaceConfig();
			config.guardRadius = radius;
			config.apply();
		}
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(RAIDERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": []}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(RAIDERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = hallRadius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		StarterBlueprints.Entry build = StarterBlueprints.BARRACKS;
		BlockPos origin = helper.absolutePos(AT);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		List<BlockPos> posts = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(build.size().getX() - 1, build.size().getY() - 1, build.size().getZ() - 1))) {
			if (level.getBlockState(p).is(Blocks.GRINDSTONE)) {
				posts.add(p.immutable());
			}
		}
		helper.assertTrue(posts.size() == 2, "(setup) Barracks I draws " + posts.size() + " grindstones, not 2");
		List<Villager> guards = new ArrayList<>();
		for (BlockPos g : posts) {
			BlockPos stand = null;
			for (BlockPos n : List.of(g.north(), g.south(), g.east(), g.west())) {
				if (level.getBlockState(n).isAir() && level.getBlockState(n.above()).isAir() && level.getBlockState(n.below()).isFaceSturdy(level, n.below(), Direction.UP)) {
					stand = n;
					break;
				}
			}
			helper.assertTrue(stand != null, "(setup) no free cell beside the grindstone at " + helper.relativePos(g).toShortString());
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
			level.addFreshEntity(v);
			Jobs.employ(level, v, g, PoiTypes.WEAPONSMITH, ModVillagers.GUARD);
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			guards.add(v);
			Leftovers.after(helper, () -> guards.forEach(Villager::discard));
		}
		return new Scene(level, hall, culture, posts, guards);
	}

	/** A tough mob at {@code rel} (y = 2), our raid's tag when {@code raider}, without AI unless {@code ai}. */
	private static Mob mob(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos rel, boolean raider, boolean ai) {
		ServerLevel level = helper.getLevel();
		Mob m = type.create(level);
		BlockPos p = helper.absolutePos(rel);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		if (raider) {
			m.addTag(VillageRaids.TAG);
		}
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		m.setHealth(200);
		level.addFreshEntity(m);
		Leftovers.after(helper, m::discard);
		return m;
	}

	/** A closed stone box round {@code rel} (3 by 3, 3 tall, the two cells a mob stands in left empty): nothing sees in or out. */
	private static void box(GameTestHelper helper, BlockPos rel) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 2; dy++) {
					boolean inside = dx == 0 && dz == 0 && dy < 2;
					helper.setBlock(rel.offset(dx, dy, dz), inside ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
	}

	private static void raid(Scene s, Mob... raiders) {
		VillageRaids.track(s.level(), s.hall(), s.culture(), new ArrayList<>(List.of(raiders)));
	}

	private static double flat(Villager v, BlockPos p) {
		return Math.max(Math.abs(v.getX() - (p.getX() + 0.5)), Math.abs(v.getZ() - (p.getZ() + 0.5)));
	}

	private static double horizontal(Villager v, Mob m) {
		double dx = v.getX() - m.getX();
		double dz = v.getZ() - m.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean hurt(Mob m) {
		return !m.isAlive() || m.getHealth() < m.getMaxHealth();
	}

	/** Euclidean distance from block {@code a} to block {@code b} (both absolute). */
	private static double dist(BlockPos a, BlockPos b) {
		return Math.sqrt(a.distSqr(b));
	}

	private static String where(GameTestHelper helper, Scene s) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < s.guards().size(); i++) {
			Villager g = s.guards().get(i);
			out.add("guard " + i + " at " + helper.relativeVec(g.position()) + " (" + String.format("%.1f", flat(g, s.posts().get(i))) + " from his grindstone "
				+ s.posts().get(i).subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ", alive " + g.isAlive() + ", sleeping " + g.isSleeping() + ")");
		}
		return out.toString();
	}

	/**
	 * Every guard must come within 3.5 blocks (across) of {@code foe} within {@link #TURN_OUT} ticks, and, unless it is boxed
	 * in, hurt it. {@code each} runs every tick first (a walking raider's step).
	 */
	private static void expectTurnOut(GameTestHelper helper, Scene s, Mob foe, boolean boxed, String what, Runnable each) {
		boolean[] came = new boolean[s.guards().size()];
		helper.onEachTick(() -> {
			each.run();
			for (int i = 0; i < came.length; i++) {
				Villager g = s.guards().get(i);
				helper.assertTrue(g.isAlive(), "(setup) guard " + i + " died");
				if (horizontal(g, foe) < 3.5) {
					came[i] = true;
				}
			}
			boolean all = true;
			for (boolean c : came) {
				all &= c;
			}
			if (all && (boxed || hurt(foe))) {
				LOG.info("[qa1122c] {}: every guard came out by tick {}, foe hurt {}", what, helper.getTick(), hurt(foe));
				helper.succeed();
			} else if (helper.getTick() > TURN_OUT) {
				List<Boolean> list = new ArrayList<>();
				for (boolean c : came) {
					list.add(c);
				}
				helper.fail(what + ": the guards didn't turn out within " + TURN_OUT + " ticks (no bell): came " + list + ", " + where(helper, s) + "; foe at "
					+ helper.relativeVec(foe.position()) + ", hurt " + hurt(foe) + "; raid on " + VillageRaids.active(s.hall()).isPresent());
			}
		});
	}

	/** For {@link #STAY} ticks no guard comes within 6 blocks of {@code foe}, it isn't hurt, and every guard keeps within his night square of his grindstone. */
	private static void expectStay(GameTestHelper helper, Scene s, Mob foe, String what) {
		helper.onEachTick(() -> {
			for (int i = 0; i < s.guards().size(); i++) {
				Villager g = s.guards().get(i);
				BlockPos post = s.posts().get(i);
				helper.assertTrue(g.isAlive(), "(setup) guard " + i + " died");
				if (horizontal(g, foe) < 6 || hurt(foe) || flat(g, post) > HOME || Math.abs(g.getY() - post.getY()) > 4) {
					helper.fail(what + ": at tick " + helper.getTick() + " guard " + i + " left for a foe he mustn't pick: " + where(helper, s) + "; foe at "
						+ helper.relativeVec(foe.position()) + " (" + String.format("%.1f", horizontal(g, foe)) + " from him), hurt " + hurt(foe));
				}
			}
			if (helper.getTick() >= STAY) {
				LOG.info("[qa1122c] {}: nobody left for it in {} ticks; {}", what, helper.getTick(), where(helper, s));
				helper.succeed();
			}
		});
	}

	/** A raider walking along the front (never nearer than 9 blocks to a grindstone, unseen behind the wall): both guards come out to it. */
	//$ gametest_ticks_batch AREA '1200' '"qaBarracksRaid1122cWalker"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBarracksRaid1122cWalker")
	public void qaGuardsComeOutToARaiderWalkingPastTheBarracks(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 0);
		Mob walker = mob(helper, EntityType.HUSK, new BlockPos(3, 2, 6), true, false);
		raid(s, walker);
		helper.assertTrue(VillageRaids.active(s.hall()).isPresent(), "(setup) no raid on the village");
		double[] x = {3.5};
		double[] step = {0.12};
		BlockPos base = helper.absolutePos(new BlockPos(0, 2, 6));
		expectTurnOut(helper, s, walker, false, "a raider walking along the front at z = 6", () -> {
			// It walks to and fro between x = 3 and x = 26 until a guard is on it (then it stands and fights).
			boolean engaged = s.guards().stream().anyMatch(g -> horizontal(g, walker) < 3);
			if (!engaged && walker.isAlive()) {
				x[0] += step[0];
				if (x[0] > 26.5 || x[0] < 3.5) {
					step[0] = -step[0];
				}
				walker.teleportTo(base.getX() + x[0], base.getY(), base.getZ() + 0.5);
			}
		});
	}

	/** A crossbow pillager of the raid standing off at the front corner, 16 to 19 blocks from the grindstones (inside guardRadius 24), behind the wall: fought. */
	//$ gametest_ticks_batch AREA '1200' '"qaBarracksRaid1122cCrossbow"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBarracksRaid1122cCrossbow")
	public void qaGuardsComeOutToACrossbowPillagerStandingOffBehindTheWall(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 0);
		BlockPos at = new BlockPos(24, 2, 3);
		for (BlockPos post : s.posts()) {
			helper.assertTrue(dist(post, helper.absolutePos(at)) < 22, "(setup) the pillager stands " + dist(post, helper.absolutePos(at)) + " from a grindstone, not inside 24");
		}
		Mob pillager = mob(helper, EntityType.PILLAGER, at, true, false);
		pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		raid(s, pillager);
		helper.assertTrue(VillageRaids.active(s.hall()).isPresent(), "(setup) no raid on the village");
		expectTurnOut(helper, s, pillager, false, "a crossbow pillager standing off at the corner", () -> {
		});
	}

	/** A vanilla raid's pillager (no tag of ours, no village raid of ours) standing 9 to 11 blocks out in front: the guards come out to it too. */
	//$ gametest_ticks_batch AREA '1300' '"qaBarracksRaid1122cVanilla"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaBarracksRaid1122cVanilla")
	public void qaGuardsComeOutToAVanillaRaidsPillagerBehindTheWall(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 0);
		ServerLevel level = s.level();
		BlockPos bell = helper.absolutePos(new BlockPos(5, 2, 9));
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		Raid[] raid = new Raid[1];
		ServerPlayer[] player = new ServerPlayer[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
			if (player[0] != null) {
				player[0].discard();
			}
		});
		Mob pillager = mob(helper, EntityType.PILLAGER, new BlockPos(13, 2, 6), false, false);
		pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, bell, 2));
		// One tick loop from the start (registering another from inside a delayed task breaks the test's tick map).
		expectTurnOut(helper, s, pillager, false, "a vanilla raid's pillager in front", () -> {
			if (helper.getTick() == 30) {
				helper.assertTrue(level.isVillage(bell), "(setup) the Barracks and bell don't make a village");
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = level.getRaids().createOrExtendRaid(player[0], bell);
				helper.assertTrue(raid[0] != null, "(setup) no vanilla raid");
				raid[0].setRaidOmenLevel(1);
				raid[0].joinRaid(1, (Raider) pillager, null, false);
				helper.assertTrue(((Raider) pillager).hasActiveRaid(), "(setup) the pillager isn't in the vanilla raid");
				helper.assertTrue(VillageRaids.active(s.hall()).isEmpty(), "(setup) a village raid of ours is on too");
				player[0].discard();
			} else if (helper.getTick() > 30) {
				helper.assertTrue(raid[0].isActive() || hurt(pillager), "(setup) the vanilla raid ended before the guards came, " + where(helper, s));
			}
		});
	}

	/** guardRadius 12: a raider 10.3 blocks from each grindstone (just inside), out in front behind the wall: fought. */
	//$ gametest_ticks_batch AREA '1200' '"qaBarracksRaid1122cInside"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBarracksRaid1122cInside")
	public void qaGuardsComeOutToARaiderJustInsideGuardRadius(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 12);
		BlockPos at = new BlockPos(12, 2, 6);
		for (BlockPos post : s.posts()) {
			double d = dist(post, helper.absolutePos(at));
			helper.assertTrue(d < 11 && d > 9, "(setup) the raider stands " + d + " from a grindstone, not just inside 12");
		}
		Mob husk = mob(helper, EntityType.HUSK, at, true, false);
		raid(s, husk);
		expectTurnOut(helper, s, husk, false, "guardRadius 12, a raider 10.3 out", () -> {
		});
	}

	/** guardRadius 12: a raider of the raid 14.2 blocks from each grindstone (just outside), unseen in a stone box: nobody leaves for it. */
	//$ gametest_ticks_batch AREA '900' '"qaBarracksRaid1122cOutside"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaBarracksRaid1122cOutside")
	public void qaGuardsDontGoThroughWallsForARaiderJustOutsideGuardRadius(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 12);
		BlockPos at = new BlockPos(12, 2, 2);
		for (BlockPos post : s.posts()) {
			double d = dist(post, helper.absolutePos(at));
			helper.assertTrue(d > 13.5 && d < 15, "(setup) the raider stands " + d + " from a grindstone, not just outside 12");
		}
		box(helper, at);
		Mob husk = mob(helper, EntityType.HUSK, at, true, false);
		raid(s, husk);
		helper.assertTrue(VillageRaids.active(s.hall()).isPresent(), "(setup) no raid on the village");
		expectStay(helper, s, husk, "guardRadius 12, a boxed raider 14.2 out");
	}

	/** The same boxed raider 14.2 out with guardRadius at its default 24: the guards come to its box (so the box isn't what kept them in above). */
	//$ gametest_ticks_batch AREA '1200' '"qaBarracksRaid1122cBoxed"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBarracksRaid1122cBoxed")
	public void qaGuardsComeToABoxedRaiderInsideTheDefaultGuardRadius(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 0);
		BlockPos at = new BlockPos(12, 2, 2);
		box(helper, at);
		Mob husk = mob(helper, EntityType.HUSK, at, true, false);
		raid(s, husk);
		expectTurnOut(helper, s, husk, true, "guardRadius 24, a boxed raider 14.2 out", () -> {
		});
	}

	/** No raid: a pillager 10 blocks out in front, unseen in a stone box, inside guardRadius 24: the guards keep the sight rule and stay. */
	//$ gametest_ticks_batch AREA '900' '"qaBarracksRaid1122cNoRaid"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaBarracksRaid1122cNoRaid")
	public void qaGuardsDontGoThroughWallsForAPillagerWithNoRaid(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 0);
		BlockPos at = new BlockPos(12, 2, 6);
		box(helper, at);
		Mob pillager = mob(helper, EntityType.PILLAGER, at, false, false);
		pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		helper.assertTrue(VillageRaids.active(s.hall()).isEmpty(), "(setup) a raid is on");
		helper.assertTrue(s.level().getRaidAt(s.posts().get(0)) == null, "(setup) a vanilla raid is on");
		expectStay(helper, s, pillager, "no raid, a boxed pillager 10 out");
	}

	/**
	 * In a raid (its only raider boxed 20 to 23 blocks off, outside guardRadius 12), a plain zombie with no raider tag 9 blocks
	 * out in front, unseen in a stone box, inside the radius: not picked through the walls.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaBarracksRaid1122cZombie"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaBarracksRaid1122cZombie")
	public void qaGuardsDontGoThroughWallsForAPlainZombieInARaid(GameTestHelper helper) {
		Scene s = scene(helper, NIGHT, 12);
		BlockPos far = new BlockPos(27, 2, 1);
		box(helper, far);
		Mob raider = mob(helper, EntityType.HUSK, far, true, false);
		for (BlockPos post : s.posts()) {
			helper.assertTrue(dist(post, helper.absolutePos(far)) > 15, "(setup) the raid's raider is within 15 of a grindstone");
		}
		BlockPos at = new BlockPos(12, 2, 7);
		box(helper, at);
		Mob zombie = mob(helper, EntityType.ZOMBIE, at, false, false);
		for (BlockPos post : s.posts()) {
			helper.assertTrue(dist(post, helper.absolutePos(at)) < 11, "(setup) the zombie isn't inside guardRadius 12");
		}
		raid(s, raider);
		helper.assertTrue(VillageRaids.active(s.hall()).isPresent(), "(setup) no raid on the village");
		expectStay(helper, s, zombie, "in a raid, a boxed plain zombie 9 out");
	}

	/**
	 * By night with no raid, both guards left outside their night square (16 blocks out in front, no patrol route) and saved
	 * and reloaded on the way: each walks back to his grindstone (indoors, within 3 blocks).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaBarracksRaid1122cBackNight"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBarracksRaid1122cBackNight")
	public void qaAGuardLeftOutsideHisNightSquareWalksBackToHisPostAfterAReload(GameTestHelper helper) {
		back(helper, NIGHT, HOME, true);
	}

	/** By day, both guards left 16 blocks out in front (outside the day square of 14): each walks back to his grindstone (indoors, within 3 blocks), not just into his square. */
	//$ gametest_ticks_batch AREA '1600' '"qaBarracksRaid1122cBackDay"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaBarracksRaid1122cBackDay")
	public void qaAGuardLeftOutsideHisDaySquareWalksBackToHisPost(GameTestHelper helper) {
		back(helper, DAY, DAY_HOME, false);
	}

	private static void back(GameTestHelper helper, int dayTime, int home, boolean reload) {
		Scene s = scene(helper, dayTime, 0);
		ServerLevel level = s.level();
		List<Villager> guards = new ArrayList<>(s.guards());
		for (int i = 0; i < guards.size(); i++) {
			BlockPos post = s.posts().get(i);
			BlockPos out = new BlockPos(post.getX(), helper.absolutePos(new BlockPos(0, 2, 0)).getY(), post.getZ() - 16);
			guards.get(i).teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
		}
		Leftovers.after(helper, () -> guards.forEach(Villager::discard));
		long limit = 1200;
		boolean[] reached = new boolean[guards.size()];
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (reload && tick == 20) {
				for (int i = 0; i < guards.size(); i++) {
					Villager g = guards.get(i);
					helper.assertTrue(flat(g, s.posts().get(i)) > home, "(setup) guard " + i + " was back before the reload (" + flat(g, s.posts().get(i)) + ")");
					CompoundTag tag = new CompoundTag();
					g.saveWithoutId(tag);
					g.discard();
					Villager copy = EntityType.VILLAGER.create(level);
					copy.load(tag);
					level.addFreshEntity(copy);
					io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
					helper.assertTrue(Guards.isGuard(copy), "guard " + i + " isn't a guard after the reload");
					guards.set(i, copy);
				}
			}
			if (tick < 5) {
				for (int i = 0; i < guards.size(); i++) {
					helper.assertTrue(flat(guards.get(i), s.posts().get(i)) > home, "(setup) guard " + i + " isn't outside his square");
				}
				return;
			}
			List<String> away = new ArrayList<>();
			for (int i = 0; i < guards.size(); i++) {
				Villager g = guards.get(i);
				BlockPos post = s.posts().get(i);
				helper.assertTrue(g.isAlive(), "(setup) guard " + i + " died");
				// "walks straight back to his post": he must reach it (indoors, within 3 blocks), not just step over his square's edge.
				if (flat(g, post) <= AT_POST && Math.abs(g.getY() - post.getY()) < 0.5 && g.getZ() >= post.getZ()) {
					reached[i] = true;
				}
				if (!reached[i]) {
					away.add("guard " + i + " at " + helper.relativeVec(g.position()) + ", " + String.format("%.1f", flat(g, post)) + " from his grindstone "
						+ post.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
				}
			}
			if (away.isEmpty() && (!reload || tick > 20)) {
				LOG.info("[qa1122c] {} back at tick {}: {}", dayTime == DAY ? "by day" : "by night", tick, guards.stream().map(g -> helper.relativeVec(g.position()).toString()).toList());
				helper.succeed();
			} else if (tick > limit) {
				helper.fail((dayTime == DAY ? "by day" : "by night") + (reload ? ", reloaded at tick 20" : "") + ": " + limit + " ticks after being left outside their square (" + home
					+ ") the guards haven't walked back to their grindstones (within " + AT_POST + "): " + away);
			}
		});
	}
}
