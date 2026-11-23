package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
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
 * QA (lane-d-1123-0108): a vanilla pillager raid (a Bad Omen's, not ours) across a save and reload, from the spec
 * (docs/wiki/raids.md, "Guards": a guard turns out to a raid's raider, ours or a vanilla raid's, within guardRadius of
 * his post whether he sees it or not, and walks back to his post when he ends up outside his square). The guards and the
 * vanilla raid's pillager are saved to NBT and loaded back (the guards' job, post and memories come from the save), the
 * mod's threat data is saved and loaded, once before the guards turn out and once while they stand at the raider: they
 * still come to it, and still go back to their grindstones once the raid is over. Setup as in QaVanillaRaid1123dGameTests.
 * Also realms.md, "Relief in a raid": a vanilla pillager raid at a realm member calls the realm's riders, and their
 * errand is saved, so after a reload they still leave when the raid is over; while they are there the village doesn't
 * call again (the watch calls only when no riders of its are there).
 * Not tested here: battle stations (sieges.md takes them only in a siege of ours, not a vanilla raid), the bell and
 * hiding (the spec adds nothing to vanilla's for a vanilla raid).
 */
public class QaVanillaRaidMore1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaVanillaRaidMore1123d");
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
	private static Mob pillager(GameTestHelper helper, BlockPos rel) {
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
	private static BlockPos bell(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = helper.absolutePos(rel);
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, bell, 2));
		return bell;
	}

	/** Starts the vanilla raid at {@code bell} (a mock player's omen) and joins {@code raiders} to its first wave. */
	private static Raid startVanillaRaid(GameTestHelper helper, BlockPos bell, ServerPlayer player, Mob... raiders) {
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

	/** Saves {@code old} to NBT, loads a new villager from it, then removes the old one (what a restart does to him). */
	private static Villager reload(ServerLevel level, Villager old) {
		CompoundTag tag = new CompoundTag();
		old.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		old.discard();
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	/** The same for the vanilla raid's pillager: loaded first (it joins its raid again from its RaidId), then the old one goes. */
	private static Mob reload(GameTestHelper helper, Mob old) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		old.saveWithoutId(tag);
		Mob copy = EntityType.PILLAGER.create(level);
		copy.load(tag);
		old.discard();
		level.addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		helper.assertTrue(((Raider) copy).hasActiveRaid(), "(setup) the reloaded pillager isn't in the vanilla raid");
		return copy;
	}

	/** A save and reload of the guards, the pillager and the mod's threat data. */
	private static Mob restart(GameTestHelper helper, Scene s, Mob pillager) {
		for (int i = 0; i < s.guards().size(); i++) {
			Villager copy = reload(s.level(), s.guards().get(i));
			s.guards().set(i, copy);
			Leftovers.after(helper, copy::discard);
		}
		QaRaidCallOff1103cGameTests.reloadThreats(s.level());
		return reload(helper, pillager);
	}

	// --- Guards against a vanilla raid across a reload -----------------------------------------------------------

	/**
	 * guardRadius at its default 24, a boxed vanilla raid pillager 14.2 blocks from each grindstone. The world is saved and
	 * loaded 10 ticks into the raid: both reloaded guards still come to its box. Saved and loaded again while they stand
	 * there; then the raid is over (stopped, the pillager gone): both walk back within their night square of their grindstones.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaVanillaRaidMore1123dReload"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaVanillaRaidMore1123dReload")
	public void qaGuardsTurnOutAndGoBackAcrossASaveAndReloadInAVanillaRaid(GameTestHelper helper) {
		Scene s = scene(helper, 0);
		BlockPos at = new BlockPos(12, 2, 2);
		box(helper, at);
		Mob[] pillager = {pillager(helper, at)};
		BlockPos bell = bell(helper, BELL);
		ServerPlayer[] player = new ServerPlayer[1];
		Raid[] raid = raidHolder(helper, player);
		boolean[] came = new boolean[s.guards().size()];
		long[] over = {-1};
		long[] second = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == 30) {
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = startVanillaRaid(helper, bell, player[0], pillager[0]);
				player[0].discard();
				return;
			}
			if (tick < 30) {
				return;
			}
			if (tick == 40) {
				pillager[0] = restart(helper, s, pillager[0]);
				LOG.info("[qa1123d] saved and loaded 10 ticks into the vanilla raid; our raid on too: {}", VillageRaids.active(s.hall()).isPresent());
			}
			for (int i = 0; i < s.guards().size(); i++) {
				helper.assertTrue(s.guards().get(i).isAlive(), "(setup) guard " + i + " died");
			}
			if (over[0] < 0) {
				helper.assertTrue(raid[0].isActive(), "(setup) the vanilla raid ended before the guards came, " + where(helper, s));
				boolean all = true;
				for (int i = 0; i < came.length; i++) {
					came[i] |= tick > 40 && horizontal(s.guards().get(i), pillager[0]) < 3.5;
					all &= came[i];
				}
				if (all && second[0] < 0) {
					LOG.info("[qa1123d] after the reload both guards came by tick {}", tick);
					pillager[0] = restart(helper, s, pillager[0]);
					second[0] = tick;
				} else if (second[0] >= 0 && tick >= second[0] + 20) {
					raid[0].stop();
					pillager[0].discard();
					over[0] = tick;
				} else if (second[0] < 0 && tick > 40 + TURN_OUT) {
					helper.fail("guardRadius 24, a boxed vanilla raid pillager 14.2 out, the world saved and loaded 10 ticks into the raid: the guards didn't come within "
						+ TURN_OUT + " ticks: " + where(helper, s));
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
				LOG.info("[qa1123d] after a reload at the raider and the vanilla raid's end both guards were back by {} ticks", tick - over[0]);
				helper.succeed();
			} else if (tick - over[0] > BACK) {
				helper.fail("saved and loaded while at a vanilla raid's raider, then the raid over: the guards didn't go back to their posts within " + BACK + " ticks: "
					+ where(helper, s) + "; vanilla raid at the post " + (s.level().getRaidAt(s.posts().get(0)) != null));
			}
		});
	}

	// --- Relief riders in a vanilla raid across a reload ---------------------------------------------------------

	/**
	 * A vanilla raid at hamlet 1 of a realm (its one pillager boxed in stone, so no wave of the game's spawns). The riders
	 * come; then a restart: every rider saved and loaded, the relief's memory (bands, calls) lost. For 200 ticks the raid
	 * goes on: the same number of riders are still there, still riders for hamlet 1, and no second band sets out. Then the
	 * raid is over: they leave (LINGER 40 ticks in the test) and none is left within 400 ticks.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaVanillaRaidMore1123dRelief"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaVanillaRaidMore1123dRelief")
	public void qaReliefRidersStayThroughAReloadInAVanillaRaidAndLeaveWhenItIsOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 0);
		ServerLevel level = land.level();
		long minRide = Relief.MIN_RIDE;
		Relief.MIN_RIDE = 60;
		Leftovers.after(helper, () -> Relief.MIN_RIDE = minRide);
		helper.setDayTime(6000);
		ReliefGameTests.treasury(land, 1000);
		BlockPos rel = helper.relativePos(land.hall(1)).offset(2, 0, 16);
		box(helper, rel);
		helper.setBlock(rel.above(2), Blocks.STONE);
		Mob[] pillager = {pillager(helper, rel)};
		BlockPos bell = bell(helper, helper.relativePos(land.hall(1)).offset(2, 0, 2));
		ServerPlayer[] player = new ServerPlayer[1];
		Raid[] raid = raidHolder(helper, player);
		int[] count = {-1};
		long[] reloaded = {-1};
		long[] over = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == 30) {
				player[0] = helper.makeMockServerPlayerInLevel();
				raid[0] = startVanillaRaid(helper, bell, player[0], pillager[0]);
				player[0].discard();
				return;
			}
			if (tick < 30) {
				return;
			}
			List<Villager> riders = Relief.riders(level, land.hall(1));
			if (count[0] < 0) {
				helper.assertTrue(raid[0].isActive(), "(setup) the vanilla raid ended before the riders came");
				if (riders.isEmpty() || !Relief.onTheRoad(level, land.hall(1)).isEmpty()) {
					if (tick > 30 + 400) {
						helper.fail("(setup) no riders came to the vanilla raid within 400 ticks; on the road " + Relief.onTheRoad(level, land.hall(1)));
					}
					return;
				}
				count[0] = riders.size();
				for (Villager r : riders) {
					Villager copy = reload(level, r);
					Leftovers.after(helper, copy::discard);
				}
				Relief.forget();
				reloaded[0] = tick;
				LOG.info("[qa1123d] {} riders came to the vanilla raid by tick {}; saved and loaded", count[0], tick);
				return;
			}
			if (over[0] < 0) {
				helper.assertTrue(raid[0].isActive(), "(setup) the vanilla raid ended on its own");
				helper.assertTrue(Relief.onTheRoad(level, land.hall(1)).isEmpty(), "after a reload in a vanilla raid, with its riders still there, the village called again at tick "
					+ (tick - reloaded[0]) + ": " + Relief.onTheRoad(level, land.hall(1)));
				helper.assertTrue(riders.size() == count[0], "after a reload in a vanilla raid, " + riders.size() + " riders for hamlet 1 at tick " + (tick - reloaded[0])
					+ ", not the " + count[0] + " who came");
				if (tick >= reloaded[0] + 200) {
					raid[0].stop();
					pillager[0].discard();
					over[0] = tick;
				}
				return;
			}
			if (riders.isEmpty()) {
				LOG.info("[qa1123d] the reloaded riders left {} ticks after the vanilla raid ended", tick - over[0]);
				helper.succeed();
			} else if (tick - over[0] > 400) {
				helper.fail("the vanilla raid is over but " + riders.size() + " reloaded riders are still there after 400 ticks (LINGER " + Relief.LINGER + "): "
					+ riders.stream().map(r -> helper.relativePos(r.blockPosition()).toShortString()).toList());
			}
		});
	}
}
