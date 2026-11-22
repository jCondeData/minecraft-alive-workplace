package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA lane D (2026-11-22): the defence builds rebuilt in 51.8 met LIVE by a threat, written from their briefs
 * ({@code tools/blueprints/briefs/gatehouse.md}, {@code lookout_tower.md}) rather than the code.
 * {@link QaRebuiltDefense1121cGameTests} checks the drawings statically (counts, stations walkable); these stand the
 * finished rebuilt drawing by a Village Hall and run the real rounds:
 * <ul>
 * <li>Gatehouse I at night: the hall's round shuts its three fence gates at dusk, a live zombie outside the yard can't
 * get at the villager inside for 30 seconds, and the round opens them again before dawn;</li>
 * <li>Gatehouse I in a siege: an archer posted behind it is lifted onto its walkway over the gate within 400 ticks and
 * hits the raider coming at the gate;</li>
 * <li>Gatehouse I and a ram raid: the siege shuts its gates and drops its portcullis into every air cell under a drawn
 * bar;</li>
 * <li>Lookout Tower IV (the rebuilt top tier): a guard posted by it gives the village a day's warning and the player in
 * the village is told once.</li>
 * </ul>
 * Left out (B1469, the test saved with the bug): an archer on the rebuilt Lookout Tower's top sees no ground within 24
 * blocks over its fence parapet, so he never shoots from it.
 */
public class QaRebuiltDefenseLive1122dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_live_1122d_besiegers");
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	// ---- set-up ---------------------------------------------------------------------------------------------------

	/** A hall at {@code hallAt} (radius 24) with a ram culture loaded, day time {@code time}; everything put back after. */
	private static BlockPos village(GameTestHelper helper, BlockPos hallAt, long time) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long before = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime((int) time);
		BlockPos hall = helper.absolutePos(hallAt);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(before);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "setup: no hall");
		ThreatData.get(level).forgetClock(hall);
		return hall;
	}

	private static Culture culture() {
		return Threats.all().stream().filter(c -> c.id().equals(BESIEGERS)).findFirst().orElseThrow();
	}

	/** Stands the rebuilt {@code build} at {@code at} as drawn and records it finished; returns its world origin. */
	private static BlockPos finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return origin;
	}

	/** An archer guard (bow in the off hand) with his Guard Post at {@code post}, standing a block east of it. */
	private static Villager archer(GameTestHelper helper, BlockPos post) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, post.east());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	/** A raider that stands still at (x, 2, z): a husk carrying the raid's tag. */
	private static Mob raider(GameTestHelper helper, double x, double z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	private static long arrows(GameTestHelper helper, Villager archer) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(24), a -> a.getOwner() == archer).size();
	}

	private static boolean inBox(BlockPos p, BlockPos origin, StarterBlueprints.Entry build) {
		BlockPos d = p.subtract(origin);
		return d.getX() >= 0 && d.getX() < build.size().getX() && d.getY() >= 0 && d.getY() < build.size().getY() && d.getZ() >= 0 && d.getZ() < build.size().getZ();
	}

	private static List<BlockPos> gates(ServerLevel level, BlockPos origin, StarterBlueprints.Entry build) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(build.size().getX() - 1, build.size().getY() - 1, build.size().getZ() - 1))) {
			if (level.getBlockState(p).getBlock() instanceof FenceGateBlock) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * Watches the archer: within 400 ticks he must be on a station inside {@code onBuild}'s box, never having shot from
	 * the ground, then within 400 more ticks his arrows must hurt the foe.
	 */
	private static void climbsAndHits(GameTestHelper helper, Villager archer, Mob foe, BlockPos onOrigin, StarterBlueprints.Entry onBuild) {
		long[] seen = {-1, -1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (seen[0] < 0) {
				if (BattleStations.onStation(archer)) {
					seen[0] = tick;
					Optional<BlockPos> post = BattleStations.post(archer);
					helper.assertTrue(post.isPresent() && inBox(post.get().below(), onOrigin, onBuild) || post.isPresent() && inBox(post.get(), onOrigin, onBuild),
						"his station " + post.map(p -> p.subtract(onOrigin).toShortString()) + " is not on the " + onBuild.id().getPath());
					helper.assertTrue(archer.getY() >= post.get().getY() - 0.1, "not up on the station: y " + archer.getY() + " for " + post.get().getY());
				} else if (tick > 400) {
					helper.fail("the archer isn't on a station of the rebuilt " + onBuild.id().getPath() + " within 400 ticks of the siege: at "
						+ archer.position().subtract(onOrigin.getX(), onOrigin.getY(), onOrigin.getZ()) + " (from its origin), his place "
						+ BattleStations.post(archer).map(p -> p.subtract(onOrigin).toShortString()));
				}
				return;
			}
			if (seen[1] < 0 && foe.getHealth() < foe.getMaxHealth()) {
				seen[1] = tick;
				helper.assertTrue(BattleStations.onStation(archer), "the archer left his station before the hit");
				helper.succeed();
			} else if (seen[1] < 0 && tick > seen[0] + 400) {
				helper.fail("from the " + onBuild.id().getPath() + "'s station he " + (arrows(helper, archer) == 0 ? "never shot at" : "shot at but never hit")
					+ " the foe " + archer.distanceTo(foe) + " blocks off (station " + BattleStations.post(archer).map(p -> p.subtract(onOrigin).toShortString())
					+ ", in sight " + archer.hasLineOfSight(foe) + ", his target " + archer.getTarget() + ", standing " + archer.position().subtract(onOrigin.getX(), onOrigin.getY(), onOrigin.getZ()) + "; ground he sees: " + sees(helper, archer, onOrigin) + ")");
			}
		});
	}

	/** Diagnostics: the ground cells (relative to {@code origin}) within 24 blocks that the archer's eyes see. */
	private static String sees(GameTestHelper helper, Villager archer, BlockPos origin) {
		return seesFrom(helper.getLevel(), archer.getEyePosition(), origin);
	}

	private static String seesFrom(ServerLevel level, net.minecraft.world.phys.Vec3 eye, BlockPos origin) {
		List<String> out = new ArrayList<>();
		for (int dx = -24; dx <= 24; dx += 3) {
			for (int dz = -24; dz <= 24; dz += 3) {
				net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(eye.x + dx, origin.getY() + 1.5, eye.z + dz);
				if (to.distanceTo(eye) > 24) {
					continue;
				}
				var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
				if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
					out.add((int) (to.x - origin.getX()) + "," + (int) (to.z - origin.getZ()));
				}
			}
		}
		return out.size() + " " + out;
	}

	// ---- Gatehouse I at night ------------------------------------------------------------------------------------

	/**
	 * gatehouse.md, "Night": in a village with a hall and a guard the hall's round shuts every fence gate of the build at
	 * dusk (12,500) and opens them before dawn (23,500); "Sealed everywhere else": with its row z = 3 in a yard's wall 4
	 * high, nothing gets from the outside to the village side. A live zombie 9 blocks out in front, at night, with a
	 * villager in the yard behind the shut gates: for 600 ticks it never gets onto the village side and the villager is
	 * never hurt. Then before dawn the round opens all three gates.
	 */
	//$ gametest_ticks_batch HUGE '700' '"qaLive1122dGateNight"'
	@GameTest(template = HUGE, timeoutTicks = 700, batch = "qaLive1122dGateNight")
	public void qaTheRebuiltGatehouseShutsAtNightAndKeepsAZombieOut(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(3, 2, 3), BASE + 13000);
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry build = StarterBlueprints.GATEHOUSE;
		BlockPos at = new BlockPos(9, 2, 8);
		BlockPos origin = finished(helper, build, at);
		int wallZ = at.getZ() + 3;
		// The yard: its front wall along the build's row z = 3, its sides and back closing the village side.
		for (int y = 2; y <= 5; y++) {
			for (int x = 0; x < 30; x++) {
				if (x < at.getX() || x >= at.getX() + build.size().getX()) {
					helper.setBlock(new BlockPos(x, y, wallZ), Blocks.STONE_BRICKS);
				}
				helper.setBlock(new BlockPos(x, y, 0), Blocks.STONE_BRICKS);
			}
			for (int z = 0; z <= wallZ; z++) {
				helper.setBlock(new BlockPos(0, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(29, y, z), Blocks.STONE_BRICKS);
			}
		}
		List<BlockPos> gates = gates(level, origin, build);
		helper.assertTrue(gates.size() == 3, "(setup) Gatehouse I's fence gates: " + gates.size());
		// Open them, as the morning round leaves them, then the dusk round with one guard.
		gates.forEach(p -> level.setBlock(p, level.getBlockState(p).setValue(FenceGateBlock.OPEN, true), 2));
		Gates.round(level, hall, 1);
		List<BlockPos> open = gates.stream().filter(p -> level.getBlockState(p).getValue(FenceGateBlock.OPEN)).toList();
		helper.assertTrue(open.isEmpty(), "the dusk round left Gatehouse I's gates open: " + open.stream().map(p -> p.subtract(origin).toShortString()).toList());
		Villager inside = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 4));
		inside.setNoAi(true);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, 2, 20));
		zombie.setPersistenceRequired();
		zombie.setTarget(inside);
		Leftovers.after(helper, () -> {
			zombie.discard();
			inside.discard();
		});
		float health = inside.getHealth();
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			helper.assertTrue(inside.isAlive() && inside.getHealth() >= health, "the villager behind the shut gates was hurt: " + inside.getHealth());
			helper.assertTrue(zombie.getZ() - base.getZ() > wallZ - 0.2, "the zombie got onto the village side at "
				+ zombie.position().subtract(origin.getX(), origin.getY(), origin.getZ()) + " (from the Gatehouse's origin)");
			if (helper.getTick() == 600) {
				helper.assertTrue(zombie.isAlive(), "(setup) the zombie died: it didn't test the gates");
				level.setDayTime(BASE + 23600);
				Gates.round(level, hall, 1);
				List<BlockPos> shut = gates.stream().filter(p -> !level.getBlockState(p).getValue(FenceGateBlock.OPEN)).toList();
				helper.assertTrue(shut.isEmpty(), "the round before dawn left Gatehouse I's gates shut: " + shut.stream().map(p -> p.subtract(origin).toShortString()).toList());
				helper.succeed();
			}
		});
	}

	// ---- archers on the rebuilt stations ---------------------------------------------------------------------------

	/**
	 * gatehouse.md, "Battle stations": in a siege the archers stand on the walkway over the gate and shoot the raiders
	 * coming at it. Gatehouse I with the hall behind it, an archer posted behind the gate, a raider 10 blocks out in
	 * front: within 400 ticks of the siege he is on one of the Gatehouse's stations, and from it he hits the raider.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qaLive1122dGateArcher"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaLive1122dGateArcher")
	public void qaAnArcherTakesTheRebuiltGatehousesWalkwayAndHits(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(4, 2, 10), BASE + 6000);
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry build = StarterBlueprints.GATEHOUSE;
		BlockPos origin = finished(helper, build, new BlockPos(9, 2, 14));
		List<BlockPos> stations = BattleStations.stations(level, hall);
		helper.assertTrue(!stations.isEmpty() && stations.stream().allMatch(s -> inBox(s, origin, build) || inBox(s.below(), origin, build)),
			"(setup) Gatehouse I's stations: " + stations.stream().map(s -> s.subtract(origin).toShortString()).toList());
		Villager archer = archer(helper, new BlockPos(14, 2, 9));
		Mob foe = raider(helper, 14.5, 28.5);
		VillageRaids.track(level, hall, culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, hall).isPresent(), "a ram raid on a village with Gatehouse I is no siege");
		climbsAndHits(helper, archer, foe, origin, build);
	}

	/**
	 * gatehouse.md, "Siege": a finished Gatehouse makes the village walled, so a ram raid is a siege, and at once its
	 * gates shut and the portcullis drops: under every iron bar the drawing has, bars fill the air below down to the
	 * first block. Gatehouse I with its gates opened as by day: within 40 ticks of the raid all three gates are shut and
	 * every air cell under a drawn bar is iron bars.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaLive1122dGateSiege"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaLive1122dGateSiege")
	public void qaARamRaidShutsTheRebuiltGatehouseAndDropsItsPortcullis(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(4, 2, 10), BASE + 6000);
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry build = StarterBlueprints.GATEHOUSE;
		BlockPos origin = finished(helper, build, new BlockPos(9, 2, 14));
		List<BlockPos> gates = gates(level, origin, build);
		helper.assertTrue(gates.size() == 3, "(setup) Gatehouse I's fence gates: " + gates.size());
		gates.forEach(p -> level.setBlock(p, level.getBlockState(p).setValue(FenceGateBlock.OPEN, true), 2));
		List<BlockPos> drop = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(build.size().getX() - 1, build.size().getY() - 1, build.size().getZ() - 1))) {
			if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
				for (BlockPos below = p.below(); below.getY() >= origin.getY() && level.getBlockState(below).isAir(); below = below.below()) {
					drop.add(below.immutable());
				}
			}
		}
		helper.assertTrue(!drop.isEmpty(), "(setup) Gatehouse I has no portcullis to drop");
		Mob foe = raider(helper, 14.5, 28.5);
		VillageRaids.track(level, hall, culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, hall).isPresent(), "a ram raid on a village with Gatehouse I is no siege");
		helper.runAfterDelay(40, () -> {
			List<String> open = gates.stream().filter(p -> level.getBlockState(p).getValue(FenceGateBlock.OPEN)).map(p -> p.subtract(origin).toShortString()).toList();
			helper.assertTrue(open.isEmpty(), "Gatehouse I's gates still open in the siege: " + open);
			List<String> up = drop.stream().filter(p -> !level.getBlockState(p).is(Blocks.IRON_BARS)).map(p -> p.subtract(origin).toShortString()).toList();
			helper.assertTrue(up.isEmpty(), "the portcullis didn't drop into " + up.size() + " of " + drop.size() + " cells: " + up);
			helper.succeed();
		});
	}

	// ---- the rebuilt Lookout Tower IV warns ------------------------------------------------------------------------

	/**
	 * lookout_tower.md, "A day's warning", in every tier: a guard whose Guard Post is within 24 blocks (flat) of the
	 * tower's middle sees raids coming a day ahead, and the player in the village is told. The rebuilt Lookout Tower IV
	 * (19 x 9), a guard posted 12 south of its middle: the clock sets tomorrow night's attack from the north, the hall's
	 * round warns of day 12, and the player by the hall hears "Smoke on the north road: ... tomorrow night." once.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaLive1122dTowerWarn"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaLive1122dTowerWarn")
	public void qaTheRebuiltLookoutTowerIVGivesADaysWarning(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(24, 2, 14), BASE + 6000);
		ServerLevel level = helper.getLevel();
		WorkplaceConfig.parse("{\"threatLevel\": 1}").apply();
		VillageRaids.ENABLED = true;
		finished(helper, StarterBlueprints.LOOKOUT_TOWER_4, new BlockPos(0, 2, 0));
		List<BlockPos> towers = Warnings.towers(level, hall);
		helper.assertTrue(towers.size() == 1, "Lookout Tower IV isn't the village's tower: " + towers);
		BlockPos post = new BlockPos(towers.get(0).getX(), helper.absolutePos(BlockPos.ZERO).getY() + 2, towers.get(0).getZ() + 12);
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager guard = EntityType.VILLAGER.create(level);
		guard.moveTo(post.getX() + 0.5, post.getY(), post.getZ() - 1.5, 0f, 0f);
		level.addFreshEntity(guard);
		Leftovers.after(helper, guard::discard);
		Jobs.employ(level, guard, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		helper.assertTrue(Warnings.source(level, hall).isPresent(), "a guard posted 12 blocks from Lookout Tower IV's middle is no source of warning");
		List<Component> heard = new ArrayList<>();
		ServerPlayer near = RealmGameTests.listener(helper, heard, "qa-live-1122d");
		near.setPos(hall.getX() + 3.5, hall.getY(), hall.getZ() + 3.5);
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
		heard.clear();
		VillageRaids.tick(level, hall, 10, 1, -1, d -> {
		});
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "Lookout Tower IV gave no warning of day 12: " + ThreatData.get(level).warned(hall));
		long told = heard.stream().filter(c -> c.getString().startsWith("Smoke on the north road: ") && c.getString().endsWith("tomorrow night.")).count();
		helper.assertTrue(told == 1, "the player was told " + told + " times: " + heard.stream().map(Component::getString).toList());
		helper.succeed();
	}
}
