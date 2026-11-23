package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * QA (lane-d-1123-0108): a vanilla pillager raid (the Raids one a Bad Omen starts, not ours) against the guards and the
 * village's standing, from the spec (docs/wiki/raids.md "Guards", reputation.md "Fighting in a raid"):
 * <ul>
 * <li>a guard turns out to a vanilla raid's raider within {@code guardRadius} of his post whether he sees it or not, and
 * keeps the sight rule just outside it (both sides of the boundary, the raider unseen in a stone box);</li>
 * <li>when the vanilla raid is over, the guards who went out walk back to their posts;</li>
 * <li>the third vanilla raid pillager a player kills while the raid is on pays the raid's +20, once; a pillager outside
 * any raid, and one killed after the raid stopped, count for nothing.</li>
 * </ul>
 * The raid is started with {@code Raids.createOrExtendRaid} at a bell whose meeting point is taken (as ReliefGameTests
 * does); the pillagers are placed by hand and joined to wave 1 so no random wave spawns while one of them lives. The guard
 * scene is a finished Barracks I with a guard at each grindstone, as in QaBarracksRaid1122cGameTests.
 */
public class QaVanillaRaid1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String REP_AREA = ReputationGameTests.AREA;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaVanillaRaid1123d");
	private static final BlockPos HALL = new BlockPos(2, 2, 2);
	private static final BlockPos AT = new BlockPos(8, 2, 14);
	private static final BlockPos BELL = new BlockPos(5, 2, 9);
	private static final int NIGHT = 14000;
	/** Night square (6) plus a step. */
	private static final int HOME = 7;
	private static final int TURN_OUT = 900;
	private static final int STAY = 600;
	private static final int BACK = 700;

	private record Scene(ServerLevel level, BlockPos hall, List<BlockPos> posts, List<Villager> guards) {
	}

	/** The village hall and a finished Barracks I with a sworded guard at each grindstone, at night; {@code radius} sets guardRadius (0: default). */
	private static Scene scene(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int hallRadius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 32;
		helper.setDayTime(NIGHT);
		if (radius > 0) {
			WorkplaceConfig config = new WorkplaceConfig();
			config.guardRadius = radius;
			config.apply();
		}
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
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
			Leftovers.after(helper, v::discard);
		}
		return new Scene(level, hall, posts, guards);
	}

	/** A tough crossbow pillager at {@code rel} (y = 2) without AI, no tag of ours. */
	static Mob pillager(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		Mob m = EntityType.PILLAGER.create(level);
		BlockPos p = helper.absolutePos(rel);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		m.setHealth(200);
		m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		level.addFreshEntity(m);
		Leftovers.after(helper, m::discard);
		return m;
	}

	/** A closed stone box round {@code rel} (3 by 3, 3 tall, the two cells a mob stands in left empty). */
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

	/** A bell at {@code rel} whose meeting point is taken at tick 5, so the spot counts as a village for a vanilla raid. */
	static BlockPos bell(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(rel);
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, bell, 2));
		return bell;
	}

	/** Starts the vanilla raid at {@code bell} (a mock player's omen) and joins {@code raiders} to its first wave. */
	static Raid startVanillaRaid(GameTestHelper helper, BlockPos bell, ServerPlayer player, Mob... raiders) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(level.isVillage(bell), "(setup) the bell doesn't make a village");
		Raid raid = level.getRaids().createOrExtendRaid(player, bell);
		helper.assertTrue(raid != null, "(setup) no vanilla raid");
		raid.setRaidOmenLevel(1);
		for (Mob m : raiders) {
			raid.joinRaid(1, (Raider) m, null, false);
			helper.assertTrue(((Raider) m).hasActiveRaid(), "(setup) the pillager isn't in the vanilla raid");
		}
		return raid;
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

	private static double dist(BlockPos a, BlockPos b) {
		return Math.sqrt(a.distSqr(b));
	}

	private static String where(GameTestHelper helper, Scene s) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < s.guards().size(); i++) {
			Villager g = s.guards().get(i);
			out.add("guard " + i + " at " + helper.relativeVec(g.position()) + " (" + String.format("%.1f", flat(g, s.posts().get(i))) + " from his grindstone, alive "
				+ g.isAlive() + ", sleeping " + g.isSleeping() + ")");
		}
		return out.toString();
	}

	private static Raid[] raidHolder(GameTestHelper helper, ServerPlayer[] player) {
		Raid[] raid = new Raid[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
			if (player[0] != null) {
				player[0].discard();
			}
		});
		return raid;
	}

	// --- Guards against a vanilla raid -----------------------------------------------------------------------------

	/**
	 * guardRadius 12: a vanilla raid's pillager 14.2 blocks from each grindstone (just outside), unseen in a stone box. The
	 * vanilla raid's area takes in the whole Barracks, but the through-wall rule stops at guardRadius: nobody leaves for it.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaVanillaRaid1123dOutside"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaVanillaRaid1123dOutside")
	public void qaGuardsStayInForAVanillaRaidsPillagerJustOutsideGuardRadius(GameTestHelper helper) {
		Scene s = scene(helper, 12);
		BlockPos at = new BlockPos(12, 2, 2);
		for (BlockPos post : s.posts()) {
			double d = dist(post, helper.absolutePos(at));
			helper.assertTrue(d > 13.5 && d < 15, "(setup) the pillager stands " + d + " from a grindstone, not just outside 12");
		}
		box(helper, at);
		Mob pillager = pillager(helper, at);
		BlockPos bell = bell(helper, BELL);
		ServerPlayer[] player = new ServerPlayer[1];
		Raid[] raid = raidHolder(helper, player);
		helper.onEachTick(() -> {
			int tick = (int) helper.getTick();
			if (tick == 30) {
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = startVanillaRaid(helper, bell, player[0], pillager);
				helper.assertTrue(VillageRaids.active(s.hall()).isEmpty(), "(setup) a village raid of ours is on too");
				helper.assertTrue(VillageRaids.raidArea(s.level(), s.posts().get(0)).isPresent(), "(setup) the guards' post isn't in the vanilla raid's area");
				player[0].discard();
			}
			for (int i = 0; i < s.guards().size(); i++) {
				Villager g = s.guards().get(i);
				BlockPos post = s.posts().get(i);
				helper.assertTrue(g.isAlive(), "(setup) guard " + i + " died");
				if (horizontal(g, pillager) < 6 || hurt(pillager) || flat(g, post) > HOME || Math.abs(g.getY() - post.getY()) > 4) {
					helper.fail("guardRadius 12, a boxed vanilla raid pillager 14.2 out: at tick " + tick + " guard " + i + " left for it: " + where(helper, s)
						+ "; pillager hurt " + hurt(pillager));
				}
			}
			if (tick > 30) {
				helper.assertTrue(raid[0].isActive(), "(setup) the vanilla raid ended early");
			}
			if (tick >= 30 + STAY) {
				LOG.info("[qa1123d] boxed vanilla pillager just outside guardRadius: nobody left in {} ticks; {}", STAY, where(helper, s));
				helper.succeed();
			}
		});
	}

	/**
	 * guardRadius at its default 24: the same boxed vanilla raid pillager 14.2 out (inside it) brings both guards out to its
	 * box. Then the raid is over (stopped, the pillager gone): both guards walk back within their night square of their
	 * grindstones.
	 */
	//$ gametest_ticks_batch AREA '1900' '"qaVanillaRaid1123dInsideAndBack"'
	@GameTest(template = AREA, timeoutTicks = 1900, batch = "qaVanillaRaid1123dInsideAndBack")
	public void qaGuardsComeToABoxedVanillaPillagerAndGoBackToTheirPostsAfter(GameTestHelper helper) {
		Scene s = scene(helper, 0);
		BlockPos at = new BlockPos(12, 2, 2);
		box(helper, at);
		Mob pillager = pillager(helper, at);
		BlockPos bell = bell(helper, BELL);
		ServerPlayer[] player = new ServerPlayer[1];
		Raid[] raid = raidHolder(helper, player);
		boolean[] came = new boolean[s.guards().size()];
		long[] over = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == 30) {
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = startVanillaRaid(helper, bell, player[0], pillager);
				helper.assertTrue(VillageRaids.active(s.hall()).isEmpty(), "(setup) a village raid of ours is on too");
				player[0].discard();
				return;
			}
			if (tick < 30) {
				return;
			}
			for (int i = 0; i < s.guards().size(); i++) {
				helper.assertTrue(s.guards().get(i).isAlive(), "(setup) guard " + i + " died");
			}
			if (over[0] < 0) {
				helper.assertTrue(raid[0].isActive(), "(setup) the vanilla raid ended before the guards came, " + where(helper, s));
				boolean all = true;
				for (int i = 0; i < came.length; i++) {
					came[i] |= horizontal(s.guards().get(i), pillager) < 3.5;
					all &= came[i];
				}
				if (all) {
					LOG.info("[qa1123d] boxed vanilla pillager inside guardRadius: both guards came by tick {}", tick);
					raid[0].stop();
					pillager.discard();
					over[0] = tick;
				} else if (tick > 30 + TURN_OUT) {
					helper.fail("guardRadius 24, a boxed vanilla raid pillager 14.2 out: the guards didn't come within " + TURN_OUT + " ticks: " + where(helper, s));
				}
				return;
			}
			boolean home = true;
			for (int i = 0; i < s.guards().size(); i++) {
				Villager g = s.guards().get(i);
				BlockPos post = s.posts().get(i);
				home &= flat(g, post) <= HOME && Math.abs(g.getY() - post.getY()) <= 2;
			}
			if (home) {
				LOG.info("[qa1123d] after the vanilla raid both guards were back by {} ticks", tick - over[0]);
				helper.succeed();
			} else if (tick - over[0] > BACK) {
				helper.fail("the vanilla raid is over but the guards didn't go back to their posts within " + BACK + " ticks: " + where(helper, s)
					+ "; raid active " + raid[0].isActive() + ", vanilla raid at the post " + (s.level().getRaidAt(s.posts().get(0)) != null));
			}
		});
	}

	// --- Reputation in a vanilla raid -------------------------------------------------------------------------------

	/**
	 * reputation.md: "Fighting in a raid: the third raider you kill in it, +20, once a raid"; raiders are "vanilla's
	 * pillagers while their raid is on; a straggler after the raid counts for nothing". A pillager killed with no raid on
	 * pays nothing; with a vanilla raid on, the third joined pillager pays 20 and a fourth nothing more; another player's
	 * third, killed after the raid stopped, pays nothing.
	 */
	//$ gametest_ticks_batch REP_AREA '200' '"qaVanillaRaid1123dReputation"'
	@GameTest(template = REP_AREA, timeoutTicks = 200, batch = "qaVanillaRaid1123dReputation")
	public void qaTheThirdVanillaRaidPillagerKilledEarnsTheRaidsTwenty(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageRaids.forget();
			Leftovers.after(helper, VillageRaids::forget);
			BlockPos hall = helper.absolutePos(ReputationGameTests.HALL);
			BlockPos bell = bell(helper, ReputationGameTests.HALL.offset(3, 0, 3));
			ServerPlayer me = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(2));
			ServerPlayer other = ReputationGameTests.player(helper, ReputationGameTests.HALL.south(3));
			Raid[] raid = new Raid[1];
			Leftovers.after(helper, () -> {
				if (raid[0] != null) {
					raid[0].stop();
				}
			});
			List<Mob> p = new ArrayList<>();
			for (int n = 0; n < 7; n++) {
				p.add(pillager(helper, new BlockPos(3 + n, 2, 3)));
			}
			// A pillager with no raid on.
			p.get(0).hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(ReputationGameTests.points(helper, me) == 0, "a pillager with no raid on paid " + ReputationGameTests.points(helper, me));
			helper.runAfterDelay(30, () -> {
				raid[0] = startVanillaRaid(helper, bell, me, p.get(1), p.get(2), p.get(3), p.get(4), p.get(5), p.get(6));
				helper.assertTrue(VillageRaids.active(hall).isEmpty(), "(setup) a village raid of ours is on too");
				helper.assertTrue(level.getRaidAt(p.get(1).blockPosition()) == raid[0], "(setup) the pillagers aren't in the raid's reach");
				p.get(1).hurt(level.damageSources().playerAttack(me), 1000f);
				p.get(2).hurt(level.damageSources().playerAttack(me), 1000f);
				p.get(5).hurt(level.damageSources().playerAttack(other), 1000f);
				p.get(6).hurt(level.damageSources().playerAttack(other), 1000f);
				helper.assertTrue(ReputationGameTests.points(helper, me) == 0 && ReputationGameTests.points(helper, other) == 0,
					"paid before the third: " + ReputationGameTests.points(helper, me) + ", " + ReputationGameTests.points(helper, other));
				helper.assertTrue(((Raider) p.get(3)).hasActiveRaid() && raid[0].isActive(), "(setup) the third pillager isn't in the active raid");
				p.get(3).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(ReputationGameTests.points(helper, me) == 20, "the third vanilla raid pillager paid " + ReputationGameTests.points(helper, me)
					+ ", not 20 (the raid still active " + raid[0].isActive() + ", the dead pillager's raid " + ((Raider) p.get(3)).getCurrentRaid() + ")");
				Mob extra = pillager(helper, new BlockPos(3, 2, 5));
				raid[0].joinRaid(1, (Raider) extra, null, false);
				extra.hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(ReputationGameTests.points(helper, me) == 20, "paid twice in one vanilla raid: " + ReputationGameTests.points(helper, me));
				// The raid stops; the other player's third, a straggler of it, counts for nothing.
				Mob straggler = pillager(helper, new BlockPos(4, 2, 5));
				raid[0].joinRaid(1, (Raider) straggler, null, false);
				raid[0].stop();
				straggler.hurt(level.damageSources().playerAttack(other), 1000f);
				helper.assertTrue(ReputationGameTests.points(helper, other) == 0, "a straggler after the vanilla raid paid " + ReputationGameTests.points(helper, other));
				helper.succeed();
			});
		});
	}
}
