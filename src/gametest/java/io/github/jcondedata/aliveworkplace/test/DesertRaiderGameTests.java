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
import net.minecraft.world.entity.monster.Husk;
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
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = raider(helper, yard, Culture.Role.CLIMBER, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		ThreatData.Siege first = yard.siege(helper);
		helper.assertTrue(first.sand.isEmpty() && !first.ramped, "sand before anyone piled it");
		long[] seen = {-1, -1, -1, 0}; // the restart, the ramp up, the husk inside, the tick the last block went on
		int[] last = {0};
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
				if (!SandRamps.over(level, yard.hall(), climber.getUUID())) {
					helper.assertTrue(tick < 820, "inside, but not by the ramp");
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
		tag.getList("raids", Tag.TAG_COMPOUND).getCompound(0).putLong("began", level.getGameTime() - 2000);
		data.read(tag);
		helper.runAfterDelay(2, () -> {
			for (long time : new long[] {23999, 24000 + 1000, 24000 + 3000}) {
				level.setDayTime(day * 24000 + time);
				VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
				helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the desert raiders left at " + time % 24000);
				helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> !s.over).orElse(false), "the siege is over at " + time % 24000);
			}
			level.setDayTime(day * 24000 + 24000 + 6100);
			VillageRaids.tick(level, yard.hall(), 8, 0, day, d -> { });
			helper.assertTrue(VillageRaids.active(yard.hall()).isEmpty() && VillageRaids.raiders(level, yard.hall()).isEmpty(), "the desert raiders stayed past noon");
			helper.assertTrue(Sieges.siege(level, yard.hall()).map(s -> s.over).orElse(true), "the siege isn't over at noon");
			helper.succeed();
		});
	}
}
