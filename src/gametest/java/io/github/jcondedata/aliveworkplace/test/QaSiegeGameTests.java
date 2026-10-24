package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-10-24) for Sieges I and III (ROADMAP 32.4 and 32.6), written from the spec. SiegeGameTests and
 * BattleStationGameTests prove the staged siege; these prove what they leave out: the exact blows ("a ravager's blow
 * does 12 every 40 ticks, an axe 4 every 20"), with a ram held still at the gate so every blow is counted; Ramparts
 * doubling the blows a gate takes, not only its starting hit points; a culture with neither ram nor axe never
 * hurting a gate; a Wall Tower's door (80) breaking whole with nothing dropped; the portcullis waiting for the first
 * dawn after through a save and load, from a siege begun just after a dawn; the report of a broken gate; a siege the
 * raiders walked away from (no heroes, no "we held", the builders still mend defences first, for 2 days and no
 * longer); heroes only for the players who fought, with every chat line they read; and the station's height bonus
 * starting at 3 blocks below, not 2.9.
 */
public class QaSiegeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation RAMS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_siege_rams");
	private static final ResourceLocation AXES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_siege_axes");
	private static final ResourceLocation BOWS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_siege_bows");
	private static final ResourceLocation HUSKS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_siege_husks");
	/** The hall: north of the gate (the village side, a blueprint's front at z = 0 faces the village). */
	private static final BlockPos HALL = new BlockPos(14, 2, 4);
	/** The Palisade Gate's (or Gatehouse's) corner. */
	private static final BlockPos GATE = new BlockPos(10, 2, 10);
	/** The Wall Tower's corner, as in BattleStationGameTests: x 5 to 9, z 13 to 17, its top floor's walk at y 9. */
	private static final BlockPos TOWER = new BlockPos(5, 2, 13);
	private static final BlockPos TOWER_HALL = new BlockPos(7, 2, 19);

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity, BlockPos origin) {
		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	private static Culture culture(ResourceLocation id) {
		String roster = id.equals(RAMS) ? "{\"entity\": \"minecraft:ravager\", \"share\": 100, \"role\": \"ram\"}"
			: id.equals(AXES) ? "{\"entity\": \"minecraft:vindicator\", \"share\": 100, \"role\": \"melee\"}"
			: id.equals(BOWS) ? "{\"entity\": \"minecraft:pillager\", \"share\": 100, \"role\": \"ranged\"}"
			: "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}";
		return Culture.read(id, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": [" + roster
			+ "], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
	}

	/**
	 * A village round a hall at {@code hallAt} (a real Village Hall block) with {@code build} finished at {@code at}, the
	 * besieging culture {@code id} loaded for the test only, and the clock at {@code dayTime}. Everything goes back when
	 * the test ends.
	 */
	private static Village village(GameTestHelper helper, ResourceLocation id, StarterBlueprints.Entry build, BlockPos at, BlockPos hallAt, long dayTime) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		level.setDayTime(dayTime);
		BlockPos hall = helper.absolutePos(hallAt);
		Culture culture = culture(id);
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(id, culture);
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
			Moods.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		return new Village(level, hall, culture, entity, origin);
	}

	/** One of the culture's raiders, as a raid spawns it, at the helper position (x, 2, z); held still when {@code still}. */
	private static Mob raider(GameTestHelper helper, Village village, double x, double z, boolean still) {
		ServerLevel level = village.level();
		Culture.Member member = village.culture().roster().get(0);
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, village.culture(), member);
		mob.addTag(VillageRaids.TAG);
		mob.setPersistenceRequired();
		if (still) {
			mob.setNoAi(true);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A guard with a Guard Post of their own two blocks south: a bow in the off hand makes an archer, a shield a knight. */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	private static void ramparts(Village village) {
		village.entity().setResearch(new Research.State(Map.of("drill", 1, "fortification", 1, "ramparts", 1), Optional.empty(), 0, false));
	}

	/** The raid ends the way the hall's round sees it (its raiders gone), and the clock goes to the siege's dawn. */
	private static void endAndDawn(GameTestHelper helper, Village village) {
		VillageRaids.raiders(village.level(), village.hall()).forEach(Mob::discard);
		VillageRaids.tick(village.level(), village.hall(), 0, 0, -100, day -> { });
		helper.assertTrue(VillageRaids.active(village.hall()).isEmpty(), "the raid isn't over");
	}

	private static List<String> reports(Village village) {
		return village.entity().chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIEGE).map(e -> e.text().getString()).toList();
	}

	/**
	 * Every blow of a ram held at a gate block: [tick, hit points after] for each change, until the block is gone or
	 * {@code limit} ticks pass; then {@code done} with them.
	 */
	private static void countBlows(GameTestHelper helper, Village village, BlockPos gate, int start, long limit, java.util.function.Consumer<List<long[]>> done) {
		List<long[]> blows = new ArrayList<>();
		int[] last = {start};
		boolean[] finished = {false};
		helper.onEachTick(() -> {
			if (finished[0]) {
				return;
			}
			long tick = helper.getTick();
			int hp = Sieges.hitPointsLeft(village.level(), village.hall(), gate);
			if (hp != last[0]) {
				blows.add(new long[] {tick, hp});
				last[0] = hp;
			}
			if (hp == 0 || tick >= limit) {
				finished[0] = true;
				done.accept(blows);
			}
		});
	}

	private static String blowList(List<long[]> blows) {
		return blows.stream().map(b -> b[0] + ":" + b[1]).toList().toString();
	}

	/** The blows on a Palisade Gate's middle fence gate by a ravager ram held at it; with Ramparts if {@code ramparts}. */
	private static void ramAtThePalisade(GameTestHelper helper, boolean ramparts) {
		Village village = village(helper, RAMS, StarterBlueprints.PALISADE_GATE, GATE, HALL, 6000);
		ServerLevel level = village.level();
		if (ramparts) {
			ramparts(village);
		}
		BlockPos gate = village.at(3, 0, 1);
		helper.assertTrue(level.getBlockState(gate).getBlock() instanceof FenceGateBlock, "no fence gate at " + gate);
		// Outside (south), its body 1 block from the gate's face: in reach, not in the build.
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Mob ram = raider(helper, village, gate.getX() - base.getX() + 0.5, gate.getZ() - base.getZ() + 3.5, true);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(ram));
		ThreatData.Siege siege = village.siege(helper);
		int full = Sieges.FENCE_GATE_HP * (ramparts ? 2 : 1);
		helper.assertTrue(gate.equals(siege.breach) && Sieges.hitPointsLeft(level, village.hall(), gate) == full,
			"the breach " + siege.breach + " (wanted " + gate + ") has " + Sieges.hitPointsLeft(level, village.hall(), gate));
		int needed = (full + Sieges.RAM_BLOW - 1) / Sieges.RAM_BLOW; // 5, or 10 with Ramparts
		countBlows(helper, village, gate, full, 60 + (needed + 1) * Sieges.RAM_EVERY, blows -> {
			helper.assertTrue(blows.size() == needed, "the gate went after " + blows.size() + " blows, wanted " + needed + ": " + blowList(blows));
			for (int i = 0; i < blows.size(); i++) {
				long want = Math.max(0, full - (long) (i + 1) * Sieges.RAM_BLOW);
				helper.assertTrue(blows.get(i)[1] == want, "blow " + (i + 1) + " left " + blows.get(i)[1] + ", wanted " + want + ": " + blowList(blows));
				if (i > 0) {
					long gap = blows.get(i)[0] - blows.get(i - 1)[0];
					helper.assertTrue(gap == Sieges.RAM_EVERY, "a ram's blows " + gap + " ticks apart, not 40: " + blowList(blows));
				}
			}
			helper.assertTrue(level.getBlockState(gate).isAir(), "the gate still stands: " + level.getBlockState(gate));
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).isEmpty(), "the broken gate dropped something");
			helper.assertTrue(ThreatData.get(level).brokenGates(village.hall()).contains(gate), "the broken gate isn't recorded for repair");
			helper.succeed();
		});
	}

	/** A ravager ram's blows: 12 each, 40 ticks apart, so a fence gate (60) goes on the fifth; nothing drops; it's listed for a builder. */
	//$ gametest_ticks_batch AREA '400' '"qaSiegeRamBlows"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSiegeRamBlows")
	public void aRamStrikesTwelveEveryFortyTicks(GameTestHelper helper) {
		ramAtThePalisade(helper, false);
	}

	/** With Ramparts the same gate has 120 and takes ten of the same blows, not five. */
	//$ gametest_ticks_batch AREA '600' '"qaSiegeRampartsBlows"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSiegeRampartsBlows")
	public void withRampartsAGateTakesTwiceTheBlows(GameTestHelper helper) {
		ramAtThePalisade(helper, true);
	}

	/**
	 * A culture without a ram: its vindicators, axes in hand, are the rams. Each blow does 4, 20 ticks apart, so the
	 * fence gate goes on the fifteenth.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaSiegeAxeBlows"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaSiegeAxeBlows")
	public void anAxeStrikesFourEveryTwentyTicks(GameTestHelper helper) {
		Village village = village(helper, AXES, StarterBlueprints.PALISADE_GATE, GATE, HALL, 6000);
		ServerLevel level = village.level();
		BlockPos gate = village.at(3, 0, 1);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Mob axe = raider(helper, village, gate.getX() - base.getX() + 0.5, gate.getZ() - base.getZ() + 2.5, true);
		helper.assertTrue(axe.getMainHandItem().getItem() instanceof AxeItem, "the vindicator has no axe: " + axe.getMainHandItem());
		VillageRaids.track(level, village.hall(), village.culture(), List.of(axe));
		helper.assertTrue(gate.equals(village.siege(helper).breach), "the breach: " + village.siege(helper).breach);
		int needed = Sieges.FENCE_GATE_HP / Sieges.AXE_BLOW; // 15
		countBlows(helper, village, gate, Sieges.FENCE_GATE_HP, 60 + (needed + 1) * Sieges.AXE_EVERY, blows -> {
			helper.assertTrue(blows.size() == needed, "the gate went after " + blows.size() + " axe blows, wanted " + needed + ": " + blowList(blows));
			for (int i = 0; i < blows.size(); i++) {
				helper.assertTrue(blows.get(i)[1] == Sieges.FENCE_GATE_HP - (i + 1) * Sieges.AXE_BLOW, "axe blow " + (i + 1) + ": " + blowList(blows));
				if (i > 0) {
					helper.assertTrue(blows.get(i)[0] - blows.get(i - 1)[0] == Sieges.AXE_EVERY, "axe blows not 20 ticks apart: " + blowList(blows));
				}
			}
			helper.assertTrue(level.getBlockState(gate).isAir() && level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).isEmpty(),
				"the gate after the last blow: " + level.getBlockState(gate));
			helper.succeed();
		});
	}

	/**
	 * A besieging culture with neither a ram nor an axe (pillagers with crossbows): it is a siege (the gates shut) but
	 * nobody strikes the gate; after 400 ticks beside it every fence gate is whole.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaSiegeNoRams"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaSiegeNoRams")
	public void raidersWithoutARamOrAnAxeNeverHitTheGate(GameTestHelper helper) {
		Village village = village(helper, BOWS, StarterBlueprints.PALISADE_GATE, GATE, HALL, 6000);
		ServerLevel level = village.level();
		List<BlockPos> gates = List.of(village.at(2, 0, 1), village.at(3, 0, 1), village.at(4, 0, 1));
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Mob pillager = raider(helper, village, gates.get(1).getX() - base.getX() + 0.5, gates.get(1).getZ() - base.getZ() + 2.5, true);
		helper.assertTrue(!(pillager.getMainHandItem().getItem() instanceof AxeItem), "the pillager holds an axe");
		VillageRaids.track(level, village.hall(), village.culture(), List.of(pillager));
		village.siege(helper);
		gates.forEach(g -> helper.assertTrue(!level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the siege didn't shut the gate at " + g));
		helper.runAtTickTime(400, () -> {
			for (BlockPos g : gates) {
				helper.assertTrue(level.getBlockState(g).getBlock() instanceof FenceGateBlock && Sieges.hitPointsLeft(level, village.hall(), g) == Sieges.FENCE_GATE_HP,
					"a raider without ram or axe hurt the gate at " + g + ": " + Sieges.hitPointsLeft(level, village.hall(), g));
			}
			helper.succeed();
		});
	}

	/**
	 * A Wall Tower's door is a gate too (80 hit points): an axe takes it in 20 blows, both halves go at once, nothing
	 * drops, the report says the gate was broken and the chat told the player it was breached.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaSiegeDoor"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaSiegeDoor")
	public void aTowerDoorBreaksWholeWithNothingDropped(GameTestHelper helper) {
		Village village = village(helper, AXES, StarterBlueprints.WALL_TOWER, TOWER, TOWER_HALL, 6000);
		ServerLevel level = village.level();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		player.teleportTo(village.hall().getX() + 0.5, village.hall().getY() + 1, village.hall().getZ() + 3.5);
		player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); // (nobody turns on a creative player)
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos door = null;
		for (BlockPos p : BlockPos.betweenClosed(village.origin(), village.origin().offset(4, 3, 4))) {
			var state = level.getBlockState(p);
			if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				door = p.immutable();
				break;
			}
		}
		helper.assertTrue(door != null, "the Wall Tower has no door");
		BlockPos lower = door;
		BlockPos upper = door.above();
		helper.assertTrue(Sieges.hitPoints(level.getBlockState(lower)) == Sieges.DOOR_HP, "a door's hit points: " + Sieges.hitPoints(level.getBlockState(lower)));
		// The vindicator stands just off the door's outer face (the side away from the tower's middle).
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int side = lower.getZ() <= village.at(0, 0, 2).getZ() ? -1 : 1;
		Mob axe = raider(helper, village, lower.getX() - base.getX() + 0.5, lower.getZ() - base.getZ() + 0.5 + side * 1.5, true);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(axe));
		ThreatData.Siege siege = village.siege(helper);
		helper.assertTrue(lower.equals(siege.breach) && Sieges.hitPointsLeft(level, village.hall(), lower) == Sieges.DOOR_HP,
			"the breach " + siege.breach + " (the door is " + lower + ") with " + Sieges.hitPointsLeft(level, village.hall(), lower));
		helper.assertTrue(Sieges.hitPointsLeft(level, village.hall(), upper) == -1, "the door's top half counts as a gate of its own");
		helper.assertTrue(seen.contains(VillageHalls.name(level, village.hall()).getString() + " is under siege! The gates are shut."),
			"the player wasn't told of the siege (a Wall Tower has no portcullis): " + seen);
		int needed = Sieges.DOOR_HP / Sieges.AXE_BLOW; // 20
		countBlows(helper, village, lower, Sieges.DOOR_HP, 60 + (needed + 1) * Sieges.AXE_EVERY, blows -> {
			helper.assertTrue(blows.size() == needed, "the door went after " + blows.size() + " blows, wanted " + needed + ": " + blowList(blows));
			helper.assertTrue(level.getBlockState(lower).isAir() && level.getBlockState(upper).isAir(),
				"the door's halves after it broke: " + level.getBlockState(lower) + ", " + level.getBlockState(upper));
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).isEmpty(), "the broken door dropped something: "
				+ level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)));
			helper.assertTrue(ThreatData.get(level).brokenGates(village.hall()).contains(lower), "the door isn't recorded for repair");
			String name = VillageHalls.name(level, village.hall()).getString();
			helper.assertTrue(seen.contains("The raiders have broken through a gate of " + name + "!"), "the breach line: " + seen);
			helper.assertTrue(seen.stream().filter(s -> s.startsWith("The raiders have broken through")).count() == 1, "the breach told more than once: " + seen);
			// The morning after: the report says the gate was broken, and no guard made a kill.
			endAndDawn(helper, village);
			level.setDayTime(siege.dawn + 20);
			helper.runAfterDelay(45, () -> {
				List<String> reports = reports(village);
				// (how one raider reads, "1 raiders came", is QaSiegeBugTests')
				helper.assertTrue(reports.size() == 1 && reports.get(0).startsWith("The siege: 1 ") && reports.get(0).endsWith(" came and 0 fell, 0 to the guards and 0 to players. The gate was broken. No guard made a kill."),
					"the report: " + reports);
				helper.succeed();
			});
		});
	}

	/**
	 * The portcullis and the gates wait for the first dawn after, through a save and load: a siege begun just after
	 * a dawn (23600) and fought off at once stays shut all that day, still down at 47480 after a restart, and up at
	 * 47500. The dawn's boundary from both sides: 23499 waits for 23500, 23500 for the next day's.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaSiegeDawn"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaSiegeDawn")
	public void thePortcullisWaitsForTheFirstDawnAfterThroughARestart(GameTestHelper helper) {
		helper.assertTrue(Sieges.nextDawn(23499) == 23500 && Sieges.nextDawn(23500) == 47500 && Sieges.nextDawn(0) == 23500 && Sieges.nextDawn(47499) == 47500,
			"next dawns: " + Sieges.nextDawn(23499) + ", " + Sieges.nextDawn(23500));
		Village village = village(helper, HUSKS, StarterBlueprints.GATEHOUSE, GATE, HALL, 23600);
		ServerLevel level = village.level();
		long day = level.getDayTime() - level.getDayTime() % 24000L;
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Mob husk = raider(helper, village, 15.5, 26.5, true);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(husk));
		ThreatData.Siege siege = village.siege(helper);
		helper.assertTrue(siege.dawn == day + 47500, "a siege begun at 23600 lifts at " + (siege.dawn - day) + ", not the next day's dawn 47500");
		helper.assertTrue(Sieges.portcullisDown(level, village.hall()), "the portcullis didn't drop");
		List<BlockPos> bars = new ArrayList<>(siege.portcullis);
		helper.assertTrue(!bars.isEmpty(), "no bars dropped");
		helper.runAtTickTime(20, () -> {
			endAndDawn(helper, village);
			level.setDayTime(day + 30000); // that day's noon: still shut
		});
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(Sieges.siege(level, village.hall()).isPresent() && Sieges.portcullisDown(level, village.hall()), "the portcullis went up before the dawn after");
			ThreatData.get(level).roundTrip(level.registryAccess()); // a restart
			Sieges.forget();
			level.setDayTime(day + 47450); // (the clock runs on: 47480 at the next check)
		});
		helper.runAtTickTime(90, () -> {
			helper.assertTrue(level.getDayTime() - day < 47500, "the clock passed dawn: " + (level.getDayTime() - day));
			helper.assertTrue(Sieges.siege(level, village.hall()).isPresent(), "the siege was lifted before dawn after a restart");
			bars.forEach(b -> helper.assertTrue(level.getBlockState(b).is(Blocks.IRON_BARS), "a bar went up before dawn at " + b.subtract(base)));
			level.setDayTime(day + 47500);
		});
		helper.runAtTickTime(160, () -> {
			helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty(), "the siege wasn't lifted at the dawn after a restart");
			bars.forEach(b -> helper.assertTrue(level.getBlockState(b).isAir(), "a bar is still down at " + b.subtract(base)));
			helper.assertTrue(!Sieges.portcullisDown(level, village.hall()), "the portcullis is still down");
			helper.succeed();
		});
	}

	/**
	 * A siege the raiders walked away from at dawn: a player who killed one of them is no Hero of the Village and isn't
	 * told he is, the villagers aren't glad, but the builders still mend defences first, for 2 days and no longer.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaSiegeFled"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaSiegeFled")
	public void aSiegeTheRaidersWalkedAwayFromMakesNoHeroes(GameTestHelper helper) {
		Village village = village(helper, HUSKS, StarterBlueprints.PALISADE_GATE, GATE, HALL, 6000);
		ServerLevel level = village.level();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		player.teleportTo(village.hall().getX() + 0.5, village.hall().getY() + 1, village.hall().getZ() + 2.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 4));
		farmer.setNoAi(true);
		List<Mob> raiders = List.of(raider(helper, village, 12.5, 24.5, true), raider(helper, village, 14.5, 24.5, true), raider(helper, village, 16.5, 24.5, true));
		VillageRaids.track(level, village.hall(), village.culture(), raiders);
		ThreatData.Siege siege = village.siege(helper);
		raiders.get(0).hurt(level.damageSources().playerAttack(player), 1000f);
		long[] lifted = {-1, -1}; // lifted, the dawn set
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (lifted[0] >= 0) {
				return;
			}
			if (VillageRaids.active(village.hall()).isPresent()) {
				if (tick > 1260) {
					VillageRaids.tick(level, village.hall(), 0, 0, -100, d -> { }); // the hall's round by day, past 1200 ticks: they flee
					helper.assertTrue(VillageRaids.active(village.hall()).isEmpty(), "the raid didn't end by day");
				}
				return;
			}
			if (lifted[1] < 0) {
				// The raid is over (the hall's own round may have ended it): they walked away.
				helper.assertTrue(siege.fled && siege.over, "the siege: fled " + siege.fled + ", over " + siege.over);
				helper.assertTrue(tick > 1150, "the raid ended at tick " + tick + ", before the raiders could flee");
				lifted[1] = tick;
				level.setDayTime(siege.dawn + 20);
				return;
			}
			if (Sieges.siege(level, village.hall()).isPresent()) {
				if (tick > 1440) {
					helper.fail("the siege wasn't lifted at dawn");
				}
				return;
			}
			lifted[0] = tick;
			List<String> reports = reports(village);
			helper.assertTrue(reports.equals(List.of("The siege: 3 raiders came and 1 fell, 0 to the guards and 1 to players. The gate held. No guard made a kill.")),
				"the report: " + reports);
			helper.assertTrue(player.getEffect(MobEffects.HERO_OF_THE_VILLAGE) == null, "a player is a Hero of the Village after a siege they walked away from");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("Hero of the Village")), "he was told he's a hero: " + seen);
			String name = VillageHalls.name(level, village.hall()).getString();
			helper.assertTrue(seen.contains("Dawn: the raiders have fled " + name), "the fled line: " + seen);
			ThreatData data = ThreatData.get(level);
			long now = level.getGameTime();
			helper.assertTrue(data.heldUntil(village.hall()) <= now, "the village is glad after a siege the raiders walked away from: " + (data.heldUntil(village.hall()) - now));
			helper.assertTrue(Moods.work(level, farmer).good().stream().noneMatch(c -> c.getString().equals("we held against the siege")), "\"we held\" after a fled siege");
			long mending = data.mendingUntil(village.hall()) - now;
			helper.assertTrue(mending > 47000 && mending <= SiegeReport.MENDING_TICKS, "defences first for " + mending + " ticks, not 2 days");
			BlockPos inside = village.hall().offset(3, 0, 3);
			helper.assertTrue(SiegeReport.defencesFirst(level, inside), "defences aren't first after a fled siege");
			helper.assertTrue(!SiegeReport.defencesFirst(level, village.hall().offset(VillageHalls.RADIUS * 3, 0, 0)), "defences first for a bench in no village");
			// Two days on: the mending is over (the same saved field at the moment it runs out).
			data.after(village.hall(), 0, now);
			helper.assertTrue(!SiegeReport.defencesFirst(level, inside), "defences still first once the two days are over");
			helper.succeed();
		});
	}

	/**
	 * A won siege: heroes are only the players who killed a raider (the one who watched gets nothing), each is told so
	 * in the words of the plan; the hero is the guard with the most kills, his one kill "1 kill"; the gate held.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSiegeHeroes"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSiegeHeroes")
	public void onlyThePlayersWhoFoughtAreHeroes(GameTestHelper helper) {
		Village village = village(helper, HUSKS, StarterBlueprints.PALISADE_GATE, GATE, HALL, 6000);
		ServerLevel level = village.level();
		List<String> fought = new ArrayList<>();
		List<String> watched = new ArrayList<>();
		ServerPlayer fighter = StationsSpecGameTests.listeningPlayer(helper, fought);
		ServerPlayer watcher = StationsSpecGameTests.listeningPlayer(helper, watched);
		fighter.teleportTo(village.hall().getX() + 0.5, village.hall().getY() + 1, village.hall().getZ() + 2.5);
		watcher.teleportTo(village.hall().getX() + 2.5, village.hall().getY() + 1, village.hall().getZ() + 2.5);
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(fighter);
			level.getServer().getPlayerList().remove(watcher);
		});
		Villager guard = guard(helper, new ItemStack(Items.SHIELD), 20, 4);
		guard.setNoAi(true);
		guard.setCustomName(net.minecraft.network.chat.Component.literal("Osric"));
		List<Mob> raiders = List.of(raider(helper, village, 12.5, 24.5, true), raider(helper, village, 14.5, 24.5, true), raider(helper, village, 16.5, 24.5, true));
		VillageRaids.track(level, village.hall(), village.culture(), raiders);
		ThreatData.Siege siege = village.siege(helper);
		String name = VillageHalls.name(level, village.hall()).getString();
		helper.assertTrue(fought.contains(name + " is under siege! The gates are shut.") && watched.contains(name + " is under siege! The gates are shut."),
			"the siege's first line: " + fought + " / " + watched);
		raiders.get(0).hurt(level.damageSources().playerAttack(fighter), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(guard), 1000f);
		helper.runAtTickTime(20, () -> {
			endAndDawn(helper, village); // the last one leaves; fought off
			helper.assertTrue(!siege.fled, "fought off, yet fled");
			level.setDayTime(siege.dawn + 20);
		});
		helper.runAtTickTime(80, () -> {
			helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty(), "the siege wasn't lifted at dawn");
			List<String> reports = reports(village);
			helper.assertTrue(reports.equals(List.of("The siege: 3 raiders came and 2 fell, 1 to the guards and 1 to players. The gate held. Hero of the night: Osric, with 1 kill.")),
				"the report: " + reports);
			helper.assertTrue(fighter.getEffect(MobEffects.HERO_OF_THE_VILLAGE) != null, "the player who fought isn't a hero");
			helper.assertTrue(watcher.getEffect(MobEffects.HERO_OF_THE_VILLAGE) == null, "the player who only watched is a hero");
			String line = name + " held! You fought at its walls: you are a Hero of the Village for a day.";
			helper.assertTrue(fought.contains(line), "the hero wasn't told: " + fought);
			helper.assertTrue(watched.stream().noneMatch(s -> s.contains("Hero of the Village")), "the watcher was told he's a hero: " + watched);
			helper.succeed();
		});
	}

	/**
	 * The quarter more damage from a station starts at a foe 3 blocks below the archer, not 2.9; on the ground, at the
	 * same heights, there is none; and his reach is 24 from the station, 28 once the village has Ramparts.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaSiegeHeight"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSiegeHeight")
	public void theHeightBonusStartsThreeBlocksBelowAStation(GameTestHelper helper) {
		Village village = village(helper, HUSKS, StarterBlueprints.WALL_TOWER, TOWER, TOWER_HALL, 6000);
		ServerLevel level = village.level();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 12, 21);
		Mob probe = EntityType.HUSK.create(level);
		probe.setPos(archer.getX(), archer.getY() - 3.0, archer.getZ());
		helper.assertTrue(BattleStations.heightBonus(archer, probe) == 1f && BattleStations.range(archer, 16) == 16, "on the ground before the siege: bonus "
			+ BattleStations.heightBonus(archer, probe) + ", range " + BattleStations.range(archer, 16));
		Mob foe = raider(helper, village, 29.5, 20.5, true); // (south-east: the station at (6, 9, 16) is his)
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		village.siege(helper);
		boolean[] given = {false};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (!BattleStations.onStation(archer)) {
				if (tick > 450) {
					helper.fail("the archer isn't on the tower's top: " + archer.position());
				}
				return;
			}
			if (!given[0]) {
				probe.setPos(archer.getX(), archer.getY() - 3.0, archer.getZ());
				float at3 = BattleStations.heightBonus(archer, probe);
				probe.setPos(archer.getX(), archer.getY() - 2.9, archer.getZ());
				float at29 = BattleStations.heightBonus(archer, probe);
				helper.assertTrue(at3 == BattleStations.HEIGHT_BONUS && at3 == 1.25f && at29 == 1f, "bonus 3 below " + at3 + ", 2.9 below " + at29);
				helper.assertTrue(BattleStations.range(archer, 16) == 24, "from the station he reaches " + BattleStations.range(archer, 16));
				ramparts(village);
				given[0] = true;
				helper.runAfterDelay(45, () -> {
					helper.assertTrue(BattleStations.range(archer, 16) == 28, "with Ramparts he reaches " + BattleStations.range(archer, 16));
					helper.succeed();
				});
			}
		});
	}
}
