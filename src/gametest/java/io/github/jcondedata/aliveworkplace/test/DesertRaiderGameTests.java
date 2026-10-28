package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * Desert raiders (ROADMAP 32.9). The culture's file as the item writes it (desert and badlands, Town and up, sand ramps,
 * until noon, the Tomb Camp, the Pharaoh who raises the fallen); a husk killed near the living Pharaoh rises once, at
 * half health, and only once, and none rises after his death; a sand ramp piled by a husk climber against a finished
 * Stone Wall (the yard of the ladder tests) reaches the wall's top a block at a time, comes back after a save and load,
 * gets the husk over, and is gone after the siege with nothing else changed; no sand with {@code mobGriefing} off; and
 * the siege is still on at mid-morning and over at noon.
 */
public class DesertRaiderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_sand_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "desert_raiders.json didn't load");
		return culture.get();
	}

	/** The culture of the ramp tests: husk climbers who pile sand. Loaded for the test only. */
	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
	}

	/** The ladder tests' yard: stone brick walls four high, a finished Stone Wall in the middle of the front one, a villager in a closed cell. */
	private static Yard yard(GameTestHelper helper, Culture culture) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, culture, villager);
	}

	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		// The same raider every run (B372): finalizeSpawn rolls on the level's random (a baby, now and then a chicken
		// jockey), and the mob's own random drives its wandering and pathing; a test that wants a baby or a jockey makes one.
		if (mob instanceof Zombie zombie) {
			zombie.setBaby(false);
		}
		if (mob.getVehicle() != null) {
			net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
			mob.stopRiding();
			vehicle.discard();
		}
		mob.getRandom().setSeed(1L);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static boolean inside(GameTestHelper helper, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		double x = mob.getX() - base.getX();
		double y = mob.getY() - base.getY();
		double z = mob.getZ() - base.getZ();
		return mob.isAlive() && z < FRONT - 1 && z > BACK + 1 && x > WEST + 1 && x < EAST && y < 3.5;
	}

	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	private static List<String> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			if (now != e.getValue()) {
				out.add(e.getKey().toShortString() + ": " + e.getValue() + " -> " + now);
			}
		}
		return out;
	}

	/** The culture's file says what the item says, and its texts are in the lang file. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void theDesertRaidersFileIsAsTheItemSays(GameTestHelper helper) {
		Culture culture = desert(helper);
		helper.assertTrue(culture.hours() == Culture.Hours.UNTIL_NOON && culture.tactics().contains(SandRamps.TACTIC), "hours or tactics: " + culture);
		helper.assertTrue(culture.lair().isPresent() && culture.lair().get().structure().equals(AliveWorkplace.id("camp/tomb_camp")), "the lair");
		helper.assertTrue(culture.loot().map(l -> l.equals(AliveWorkplace.id("chests/pharaohs_tomb"))).orElse(false), "the loot");
		helper.assertTrue(helper.getLevel().getStructureManager().get(AliveWorkplace.id("camp/tomb_camp")).isPresent(), "no tomb camp template");
		Culture.Captain pharaoh = culture.captain().orElseThrow();
		helper.assertTrue(pharaoh.health() == 50 && pharaoh.raises().map(r -> r.radius() == 24 && r.delay() == 60 && r.health() == 0.5).orElse(false),
			"the Pharaoh: " + pharaoh);
		int[] shares = culture.roster().stream().mapToInt(Culture.Member::share).toArray();
		helper.assertTrue(shares.length == 3 && shares[0] == 55 && shares[1] == 25 && shares[2] == 20, "the roster's shares");
		Language lang = Language.getInstance();
		for (String key : new String[] {"message.aliveworkplace.siege.sand_ramps", "threat.aliveworkplace.desert_raiders.title",
			"threat.aliveworkplace.desert_raiders.names.20", "entity.aliveworkplace.desert_raider"}) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		for (String key : culture.messages().own().values()) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		helper.succeed();
	}

	/**
	 * The Pharaoh's curse. With the Pharaoh alive a few blocks off, a husk killed rises after 3 s where it fell, at half
	 * health and in its gear; killed again it stays dead. After the Pharaoh's own death a husk killed near where he stood
	 * stays dead.
	 */
	//$ gametest_ticks_batch AREA '400' '"pharaoh"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "pharaoh")
	public void aHuskRisesOnceNearTheLivingPharaohAndNotAfterHisDeath(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		Culture culture = desert(helper);
		ServerLevel level = helper.getLevel();
		Husk pharaoh = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
		pharaoh.setNoAi(true);
		Risings.mark(pharaoh, culture.captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "the Pharaoh isn't marked");
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(14, 2, 14));
		husk.setNoAi(true);
		husk.setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.GOLDEN_HELMET));
		husk.addTag(VillageRaids.TAG);
		AABB box = helper.getBounds();
		java.util.function.Supplier<List<Husk>> risen = () -> level.getEntitiesOfClass(Husk.class, box, h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Husk.class, box.inflate(8), h -> true).forEach(Mob::discard);
			Risings.forget();
		});
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "the husk isn't waiting to rise");
		helper.runAfterDelay(40, () -> helper.assertTrue(risen.get().isEmpty(), "it rose before 3 s"));
		helper.runAfterDelay(65, () -> {
			List<Husk> up = risen.get();
			helper.assertTrue(up.size() == 1, "risen husks after 3 s: " + up.size());
			Husk again = up.get(0);
			helper.assertTrue(Math.abs(again.getHealth() - again.getMaxHealth() / 2) < 0.01, "its health: " + again.getHealth() + " of " + again.getMaxHealth());
			helper.assertTrue(again.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET) && again.getTags().contains(VillageRaids.TAG),
				"it didn't rise as it was");
			helper.assertTrue(again.blockPosition().closerThan(helper.absolutePos(new BlockPos(14, 2, 14)), 1.5), "it rose at " + again.blockPosition());
			again.kill();
			helper.assertTrue(Risings.pending() == 0, "a husk rose twice");
		});
		helper.runAfterDelay(160, () -> {
			helper.assertTrue(level.getEntitiesOfClass(Husk.class, box, h -> h.isAlive() && h != pharaoh).isEmpty(), "a husk got up a second time");
			pharaoh.kill();
			Husk late = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 4));
			late.setNoAi(true);
			late.kill();
			helper.assertTrue(Risings.pending() == 0, "a husk is waiting to rise after the Pharaoh's death");
		});
		helper.runAfterDelay(240, () -> {
			helper.assertTrue(level.getEntitiesOfClass(Husk.class, box, Mob::isAlive).isEmpty(), "a husk rose after the Pharaoh's death");
			helper.succeed();
		});
	}

	/**
	 * A sand ramp against the Stone Wall. A husk climber piles sand a block at a time, never faster than every
	 * {@link SandRamps#EVERY} ticks, only where the siege records it; a save and load half-way keeps the sand and the
	 * ramp goes on; it reaches the wall's top (the players are told), the husk walks up and over, and when the raid is
	 * over every grain is gone and no other block of the area changed, also when the siege is lifted.
	 */
	//$ gametest_ticks_batch AREA '900' '"sandRamp"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "sandRamp")
	public void aSandRampReachesTheTopOfTheStoneWallAndIsGoneAfterTheSiege(GameTestHelper helper) {
		sandRamp(helper, false);
	}

	/** The same ramp climbed by a chicken jockey: it is let go once down inside, as a husk on foot is (B316). */
	//$ gametest_ticks_batch AREA '900' '"sandRampJockey"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "sandRampJockey")
	public void aChickenJockeyGetsOverTheSandRampToo(GameTestHelper helper) {
		sandRamp(helper, true);
	}

	private static void sandRamp(GameTestHelper helper, boolean jockey) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		if (jockey) {
			// A chicken jockey (a baby husk's 1 in 400 roll in finalizeSpawn): its rider is never on the ground itself (B316).
			Chicken chicken = EntityType.CHICKEN.create(level);
			chicken.moveTo(climber.getX(), climber.getY(), climber.getZ(), 180f, 0f);
			level.addFreshEntity(chicken);
			Leftovers.after(helper, chicken::discard);
			((Zombie) climber).setBaby(true);
			// Forced (B529): when finalizeSpawn rolled a jockey of its own, raider() took the husk off that chicken, and a
			// dismount leaves a 60-tick boarding cooldown that refuses an ordinary startRiding in the same tick.
			helper.assertTrue(climber.startRiding(chicken, true), "the husk didn't mount the chicken");
		}
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		ThreatData.Siege first = yard.siege(helper);
		helper.assertTrue(first.sand.isEmpty() && !first.ramped, "sand before anyone piled it");
		long[] seen = {-1, -1, -1, 0}; // the restart, the ramp up, the husk inside, the tick the last block went on
		int[] last = {0};
		long[] in = {-1}; // the tick the husk was first inside
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (seen[2] >= 0) {
				if (tick == seen[2] + 40) {
					ThreatData.Siege siege = yard.siege(helper);
					helper.assertTrue(siege.over, "the siege's raid isn't over");
					helper.assertTrue(SandRamps.sand(level, yard.hall()).isEmpty(), "sand still recorded");
					helper.assertTrue(changes(helper, before).isEmpty(), "a block changed after the siege: " + changes(helper, before));
					Sieges.lift(level, siege);
					helper.assertTrue(changes(helper, before).isEmpty(), "a block changed when the siege was lifted: " + changes(helper, before));
					helper.succeed();
				}
				return;
			}
			List<BlockPos> sand = SandRamps.sand(level, yard.hall());
			if (sand.size() > last[0]) {
				helper.assertTrue(sand.size() == last[0] + 1, "more than a block at once: " + last[0] + " -> " + sand.size());
				helper.assertTrue(seen[3] == 0 || tick - seen[3] >= SandRamps.EVERY, "a block after " + (tick - seen[3]) + " ticks");
				seen[3] = tick;
			}
			last[0] = sand.size();
			// What changed is the recorded sand and nothing else.
			for (String change : changes(helper, before)) {
				helper.assertTrue(change.endsWith("-> Block{minecraft:sand}"), "something other than sand changed: " + change);
			}
			for (BlockPos pos : sand) {
				helper.assertTrue(level.getBlockState(pos).is(Blocks.SAND), "recorded sand isn't there at " + pos);
			}
			if (seen[0] < 0 && sand.size() >= 6) {
				seen[0] = tick;
				ThreatData.get(level).roundTrip(level.registryAccess());
				Sieges.forget();
				SandRamps.forget();
				List<BlockPos> back = SandRamps.sand(level, yard.hall());
				helper.assertTrue(back.equals(sand), "the sand didn't come back after a load: " + back + " for " + sand);
				last[0] = back.size();
			}
			ThreatData.Siege siege = yard.siege(helper);
			if (seen[1] < 0 && siege.ramped) {
				seen[1] = tick;
				// The ramp's top is level with the wall's: its highest block is one under the top of the wall beside it.
				BlockPos high = sand.stream().max(java.util.Comparator.comparingInt(BlockPos::getY)).orElseThrow();
				BlockPos wall = high.north();
				helper.assertTrue(!level.getBlockState(wall).getCollisionShape(level, wall).isEmpty()
					&& level.getBlockState(wall.above()).getCollisionShape(level, wall.above()).isEmpty(), "the ramp's top isn't level with the wall's at " + high);
				int h = high.getY() - helper.absolutePos(BlockPos.ZERO).getY() - 1;
				helper.assertTrue(sand.size() == h * (h + 1) / 2, "a ramp " + h + " high of " + sand.size() + " blocks");
			}
			if (inside(helper, climber)) {
				helper.assertTrue(seen[1] >= 0, "the husk is inside without a finished ramp");
				if (in[0] < 0) {
					in[0] = tick;
				}
				if (!SandRamps.over(level, yard.hall(), climber.getUUID())) {
					if (tick >= 820) {
						BlockPos base = helper.absolutePos(BlockPos.ZERO);
						helper.fail("inside, but not by the ramp: the husk at " + climber.position().subtract(base.getX(), base.getY(), base.getZ())
							+ (climber.onGround() ? " on the ground" : " in the air") + " at tick " + tick + ", inside since " + in[0] + ", ramp up at " + seen[1]
							+ (climber.isBaby() ? ", a baby" : "") + (climber.getVehicle() != null ? ", riding " + climber.getVehicle() : "")
							+ ", sand " + sand.size());
					}
					return; // (it lets go of the ramp once it is down on its feet)
				}
				seen[2] = tick;
				climber.discard();
				VillageRaids.forget(yard.hall());
			}
			if (tick > 820) {
				helper.fail("not over the wall in time: the husk at " + climber.position() + ", " + sand.size() + " blocks of sand, ramp up at " + seen[1]);
			}
		});
	}

	/**
	 * A husk that comes down inside onto a step half a block up (a bottom slab one above the ramp's foot, its top at the
	 * foot + 1.5) is let go there to fight inside, not walked back up the ramp (B330: that height was neither "down
	 * inside" nor "up on the wall", so behind a wall four high it stuck on the step).
	 */
	//$ gametest_ticks_batch AREA '900' '"sandRampStep"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "sandRampStep")
	public void aHuskComingDownOntoAnInnerStepIsLetGo(GameTestHelper helper) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		long[] stepped = {-1}; // the first tick the husk stood on the step
		double[] foot = {Double.NaN};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			ThreatData.Siege siege = yard.siege(helper);
			if (Double.isNaN(foot[0]) && siege.ramped) {
				// The ramp is up: lay bottom slabs one above its foot where the husk comes down, beyond the wall.
				BlockPos at = siege.rampFeet.get(0);
				net.minecraft.core.Direction into = net.minecraft.core.Direction.from3DDataValue(siege.rampDirs.get(0));
				int height = 0;
				while (!level.getBlockState(at.relative(into).above(height)).getCollisionShape(level, at.relative(into).above(height)).isEmpty()) {
					height++;
				}
				helper.assertTrue(height >= 3, "the wall by the ramp is only " + height + " high");
				int k = 2;
				while (k < 8 && !level.getBlockState(at.relative(into, k).above(height - 1)).isAir()) {
					k++;
				}
				for (int d = k; d <= k + 3; d++) {
					for (int s = -1; s <= 1; s++) {
						BlockPos slab = at.relative(into, d).relative(into.getClockWise(), s).above();
						if (level.getBlockState(slab).isAir() && level.getBlockState(slab.above()).isAir()) {
							level.setBlockAndUpdate(slab, Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState());
							Leftovers.after(helper, () -> level.setBlockAndUpdate(slab, Blocks.AIR.defaultBlockState()));
						}
					}
				}
				foot[0] = at.getY();
			}
			if (!Double.isNaN(foot[0]) && stepped[0] < 0 && climber.onGround() && Math.abs(climber.getY() - (foot[0] + 1.5)) < 1.0E-6) {
				stepped[0] = tick;
			}
			if (SandRamps.over(level, yard.hall(), climber.getUUID())) {
				helper.assertTrue(stepped[0] >= 0, "the husk was let go without standing on the step: at " + climber.position() + ", foot " + foot[0]);
				climber.discard();
				helper.succeed();
				return;
			}
			if (stepped[0] >= 0 && tick - stepped[0] > 100 || tick > 820) {
				BlockPos base = helper.absolutePos(BlockPos.ZERO);
				helper.fail("not let go: the husk at " + climber.position().subtract(base.getX(), base.getY(), base.getZ())
					+ (climber.onGround() ? " on the ground" : " in the air") + " at tick " + tick + ", on the step since " + stepped[0]
					+ ", ramp foot y " + (foot[0] - base.getY()) + "; " + rampState(helper, yard, climber, siege));
			}
		});
	}

	/** What the ramp tests' failure messages tell besides the husk (B372): the siege, its sand, the raid, the clock, the husk. */
	private static String rampState(GameTestHelper helper, Yard yard, Mob climber, ThreatData.Siege siege) {
		ServerLevel level = yard.level();
		return "siege over " + siege.over + ", ramped " + siege.ramped + ", sand " + siege.sand.size() + ", ramp feet " + siege.rampFeet
			+ ", raid " + ThreatData.get(level).raid(yard.hall()).map(r -> r.culture() + " began " + r.began()).orElse("none")
			+ ", game time " + level.getGameTime() + ", day time " + level.getDayTime() + ", mobGriefing " + level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
			+ ", husk alive " + climber.isAlive() + (climber.isBaby() ? ", a baby" : "") + (climber.getVehicle() != null ? ", riding " + climber.getVehicle() : "")
			+ ", target " + climber.getTarget() + ", navigation done " + climber.getNavigation().isDone();
	}

	/**
	 * A husk that comes down inside onto ground two higher than the ramp's foot (a terrace behind the wall, full blocks at
	 * the foot and one above it, with no column free down to the foot within reach) is let go there to fight inside
	 * (B331: it stood higher than a step, so it counted as up on the wall, and it was pushed on to a spot one past the wall
	 * on the same terrace, where it stood for good).
	 */
	//$ gametest_ticks_batch AREA '900' '"sandRampTerrace"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "sandRampTerrace")
	public void aHuskComingDownOntoARaisedTerraceIsLetGo(GameTestHelper helper) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		long[] stood = {-1}; // the first tick the husk stood on the terrace
		double[] foot = {Double.NaN};
		BlockPos[] ramp = {null};
		net.minecraft.core.Direction[] dir = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			ThreatData.Siege siege = yard.siege(helper);
			if (Double.isNaN(foot[0]) && siege.ramped) {
				// The ramp is up: a terrace two high behind the wall, where the husk comes down.
				BlockPos at = siege.rampFeet.get(0);
				net.minecraft.core.Direction into = net.minecraft.core.Direction.from3DDataValue(siege.rampDirs.get(0));
				int height = 0;
				while (!level.getBlockState(at.relative(into).above(height)).getCollisionShape(level, at.relative(into).above(height)).isEmpty()) {
					height++;
				}
				helper.assertTrue(height >= 4, "the wall by the ramp is only " + height + " high");
				for (int d = 2; d <= 10; d++) {
					for (int s = -2; s <= 2; s++) {
						for (int y = 0; y <= 1; y++) {
							BlockPos block = at.relative(into, d).relative(into.getClockWise(), s).above(y);
							if (level.getBlockState(block).isAir()) {
								level.setBlockAndUpdate(block, Blocks.SANDSTONE.defaultBlockState());
								Leftovers.after(helper, () -> level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState()));
							}
						}
					}
				}
				foot[0] = at.getY();
				ramp[0] = at;
				dir[0] = into;
			}
			if (ramp[0] != null && stood[0] < 0 && climber.onGround() && Math.abs(climber.getY() - (foot[0] + 2)) < 1.0E-6) {
				// On the terrace, past the wall (not on the ramp's step at the same height).
				double inward = (climber.getX() - (ramp[0].getX() + 0.5)) * dir[0].getStepX() + (climber.getZ() - (ramp[0].getZ() + 0.5)) * dir[0].getStepZ();
				if (inward >= 1.5) {
					stood[0] = tick;
				}
			}
			if (SandRamps.over(level, yard.hall(), climber.getUUID())) {
				helper.assertTrue(stood[0] >= 0, "the husk was let go without standing on the terrace: at " + climber.position() + ", foot " + foot[0]);
				climber.discard();
				helper.succeed();
				return;
			}
			if (stood[0] >= 0 && tick - stood[0] > 100 || tick > 820) {
				BlockPos base = helper.absolutePos(BlockPos.ZERO);
				helper.fail("not let go: the husk at " + climber.position().subtract(base.getX(), base.getY(), base.getZ())
					+ (climber.onGround() ? " on the ground" : " in the air") + " at tick " + tick + ", on the terrace since " + stood[0]
					+ ", ramp foot y " + (foot[0] - base.getY()) + "; " + rampState(helper, yard, climber, siege));
			}
		});
	}

	/** With {@code mobGriefing} off a husk climber piles no sand; the siege still stands. */
	//$ gametest_ticks_batch AREA '300' '"sandOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "sandOff")
	public void noSandWithMobGriefingOff(GameTestHelper helper) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "no siege");
			helper.assertTrue(SandRamps.sand(level, yard.hall()).isEmpty() && changes(helper, before).isEmpty(), "sand with mobGriefing off: " + changes(helper, before));
			helper.succeed();
		});
	}

	/** The raid, the siege and the clock, for the noon test's failure messages (B370). */
	private static String noonState(ServerLevel level, Yard yard, Mob husk, long day) {
		ThreatData data = ThreatData.get(level);
		return "raid " + data.raid(yard.hall()).map(r -> r.culture() + " began " + r.began()).orElse("none") + " (game time " + level.getGameTime() + ")"
			+ ", raids recorded " + data.save(new CompoundTag(), level.registryAccess()).getList("raids", Tag.TAG_COMPOUND).size()
			+ ", active " + VillageRaids.active(yard.hall()).isPresent() + ", raiders " + VillageRaids.raiders(level, yard.hall()).size()
			+ ", siege " + Sieges.siege(level, yard.hall()).map(s -> "over " + s.over + ", dawn " + s.dawn).orElse("none")
			+ ", clock " + level.getDayTime() + " (time " + level.getDayTime() % 24000 + ", test day " + day + ")"
			+ ", husk alive " + husk.isAlive() + (husk.getVehicle() != null ? ", riding " + husk.getVehicle() : "");
	}

	/**
	 * Until noon. A siege of desert raiders is still on at dawn and at mid-morning (the raid goes on, the siege isn't
	 * over), and at noon the raid is over, the raiders gone and the siege over.
	 */
	//$ gametest_ticks_batch AREA '100' '"desertNoon"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "desertNoon")
	public void theDesertSiegeIsStillOnAtMidMorningAndOverAtNoon(GameTestHelper helper) {
		Yard yard = yard(helper, desert(helper));
		ServerLevel level = yard.level();
		long day = level.getDayTime() / 24000 + 1;
		level.setDayTime(day * 24000 + 15000);
		Mob husk = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		husk.setNoAi(true);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(husk));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "the desert raid laid no siege");
		// The raid began a while ago (as a save of one would have it).
		ThreatData data = ThreatData.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		// This village's raid, by its hall (B370: the first of the list was taken, which is another village's when a raid of
		// an earlier test is still recorded, and this one's then began just now and outlasted noon).
		net.minecraft.nbt.ListTag raids = tag.getList("raids", Tag.TAG_COMPOUND);
		boolean found = false;
		for (int i = 0; i < raids.size(); i++) {
			if (raids.getCompound(i).getLong("hall") == yard.hall().asLong()) {
				raids.getCompound(i).putLong("began", level.getGameTime() - 2000);
				found = true;
			}
		}
		helper.assertTrue(found, "the desert raid isn't saved: " + raids);
		data.read(tag);
		helper.runAfterDelay(2, () -> {
			for (long time : new long[] {23999, 24000 + 1000, 24000 + 3000}) {
				level.setDayTime(day * 24000 + time);
				VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
				helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the desert raiders left at " + time % 24000 + "; " + noonState(level, yard, husk, day));
				helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> !s.over).orElse(false), "the siege is over at " + time % 24000 + "; " + noonState(level, yard, husk, day));
			}
			level.setDayTime(day * 24000 + 24000 + 6100);
			VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
			helper.assertTrue(VillageRaids.active(yard.hall()).isEmpty() && VillageRaids.raiders(level, yard.hall()).isEmpty(), "the desert raiders stayed past noon; " + noonState(level, yard, husk, day));
			helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> s.over).orElse(true), "the siege isn't over at noon; " + noonState(level, yard, husk, day));
			helper.succeed();
		});
	}
}
