package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-10-24): B373's fix says a threat's mob never goes through a Nether portal ("a lair's band and
 * captain, a bandit camp's, and every raider"). Players build portals in and near their villages, so these put the
 * threat mobs the fix's own test doesn't cover (a lair's captain, a bandit camp's chief and men) into a lit portal built
 * by hand, beside a plain pillager that must leave (proving the portal works). The mobs it misses (a raider's mount,
 * the Bandit King's fort's men) are the bug tests {@code QaPortalThreatBugTests} on tests/portal-threats.
 */
public class QaPortalThreatsGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/** A lit two-wide, three-tall Nether portal (along x) with its bottom portal blocks at {@code bottom}; its blocks. */
	static List<BlockPos> portal(GameTestHelper helper, BlockPos bottom) {
		for (int x = -1; x <= 2; x++) {
			for (int y = -1; y <= 3; y++) {
				if (x == -1 || x == 2 || y == -1 || y == 3) {
					helper.setBlock(bottom.offset(x, y, 0), Blocks.OBSIDIAN);
				}
			}
		}
		// A floor on both sides of the frame, so whatever stands in the portal stays put.
		for (int x = -1; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (z != 0) {
					helper.setBlock(bottom.offset(x, -1, z), Blocks.OBSIDIAN);
				}
			}
		}
		BlockState lit = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
		List<BlockPos> blocks = new ArrayList<>();
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 2; x++) {
				helper.setBlock(bottom.offset(x, y, 0), lit);
				blocks.add(helper.absolutePos(bottom.offset(x, y, 0)));
			}
		}
		for (BlockPos p : blocks) {
			helper.assertTrue(helper.getLevel().getBlockState(p).is(Blocks.NETHER_PORTAL), "the portal didn't stay lit at " + p);
		}
		return blocks;
	}

	/** Anything that went to the Nether is gone from there when the test ends. */
	static List<UUID> strays(GameTestHelper helper) {
		List<UUID> strays = new ArrayList<>();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			if (nether != null) {
				strays.forEach(id -> {
					Entity e = nether.getEntity(id);
					if (e != null) {
						e.discard();
					}
				});
			}
		});
		return strays;
	}

	/**
	 * Puts {@code mobs} into the portal at {@code in} every tick for 20 ticks, then checks after 40 that the plain one
	 * left for the Nether and the threat's are still here.
	 */
	static void throughPortal(GameTestHelper helper, BlockPos in, Entity plain, List<Entity> threats, String what) {
		ServerLevel level = helper.getLevel();
		List<Entity> all = new ArrayList<>(threats);
		all.add(plain);
		for (int t = 0; t < 20; t++) {
			helper.runAfterDelay(t, () -> {
				for (Entity e : all) {
					if (!e.isRemoved() && e.getVehicle() == null) {
						e.teleportTo(in.getX() + 0.5, in.getY(), in.getZ() + 0.5);
					}
				}
			});
		}
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(plain.isRemoved() && plain.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION,
				"a plain " + plain.getType().toShortString() + " in the portal didn't leave, so it proves nothing: " + plain.getRemovalReason());
			for (Entity e : threats) {
				helper.assertTrue(!e.isRemoved() && e.level() == level,
					what + " went through a player's portal: " + e + " " + e.getRemovalReason());
			}
			AliveWorkplace.LOG.info("QaPortalThreats: {} stayed out of the portal while a plain {} went through", what, plain.getType().toShortString());
			helper.succeed();
		});
	}


	/** A town-ranked hall (village radius 20) for a lair, put back when the test ends. */
	private static BlockPos town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = 20;
		Lairs.ENABLED = true;
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> !(m instanceof Villager)
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))).forEach(Mob::discard);
			ThreatData.get(level).end(hall);
			Lairs.forget(level);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = was;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		level.setDayTime(44 * 24000L + 2000);
		return hall;
	}

	/**
	 * A lair of {@code culture}: its captain and one of its band are put in a player's portal by the village and stay in
	 * the world; a plain pillager beside them leaves.
	 */
	private static void lairStays(GameTestHelper helper, ResourceLocation culture) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		List<UUID> strays = strays(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), culture);
			helper.assertTrue(lair != null && lair.culture().equals(culture), culture + ": no lair: " + lair);
			Entity captain = level.getEntity(lair.captain());
			helper.assertTrue(captain instanceof Mob, culture + ": no captain: " + captain);
			Mob member = Lairs.band(level, lair).stream().filter(m -> !m.getUUID().equals(lair.captain())).findFirst().orElse(null);
			helper.assertTrue(member != null, culture + ": the lair has no band");
			strays.add(captain.getUUID());
			strays.add(member.getUUID());
			List<BlockPos> blocks = portal(helper, new BlockPos(2, 2, 27));
			BlockPos in = blocks.get(0);
			Mob plain = EntityType.PILLAGER.create(level);
			plain.moveTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5, 0f, 0f);
			plain.setPersistenceRequired();
			level.addFreshEntity(plain);
			Leftovers.after(helper, plain::discard);
			strays.add(plain.getUUID());
			throughPortal(helper, in, plain, List.of(captain, member), culture + "'s lair captain and band");
		});
	}

	/** B373's spec: a bandit camp's chief and his men keep out of portals. */
	//$ gametest_ticks_batch HUGE '100' '"qaPortalBandits"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPortalBandits")
	public void aBanditCampsChiefAndManStayOutOfAPortal(GameTestHelper helper) {
		lairStays(helper, AliveWorkplace.id("bandits"));
	}

	/** B373's spec: a lair's captain (B373's own test puts only one of the band in) keeps out of portals too. */
	//$ gametest_ticks_batch HUGE '100' '"qaPortalLair"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaPortalLair")
	public void aDesertLairsCaptainAndBandStayOutOfAPortal(GameTestHelper helper) {
		lairStays(helper, DESERT);
	}
}
