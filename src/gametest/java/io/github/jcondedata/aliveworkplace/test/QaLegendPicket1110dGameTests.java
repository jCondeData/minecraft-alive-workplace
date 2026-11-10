package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Picket;
import io.github.jcondedata.aliveworkplace.legend.StrangeMood;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Gated;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-10, lane d) for the Legends' strike picket (ROADMAP 29.5: "their WORK activity becomes a picket: they stand
 * by the Village Hall by day"; "the day every need is met again they go back to work"; {@code legendNeeds} off) and the
 * strange mood's claim (29.10: "their other work stops, they stand at it"). Written from the spec, through the villagers'
 * real brains: a Legend with no workstation pickets from IDLE, a Legend not on strike doesn't; the picket survives a save
 * and reload of the villager; a picket already by the hall stays there; the strike ending (needs met, or the switch off)
 * sends a Mason back to her stonecutter; a villager in a strange mood stands at the workstation they claimed, not at the hall.
 */
public class QaLegendPicket1110dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos STATION = new BlockPos(18, 2, 18);
	private static final String NS = "aliveworkplace_test";
	/** How near the hall the picket stops walking (Picket's own stop radius, NEAR * 1.5 + 1). */
	private static final double BY_THE_HALL = Picket.NEAR * 1.5 + 1;
	/** A real Legend with a Masterwork, so the strange mood's daily check keeps the mood on outside {@link #staged}. */
	private static final ResourceLocation GOLEM_SMITH = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "golem_smith");

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	/** A Legend of {@code job} with no powers and no luxury. */
	private static Legend legend(String name, String job) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"" + job + "\", \"title\": \"" + name + "\","
			+ " \"lore\": \"l\"}").getAsJsonObject());
	}

	/** The hall, and when the test ends its villagers (with their record entries), its homes, and the switch put back. */
	private static void setUp(GameTestHelper helper, List<BuildSiteManager.Finished> homes) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setDayTime(3000); // mid-morning: work time, and day
		boolean needs = LegendNeeds.ENABLED;
		LegendNeeds.ENABLED = true; // a strike has to hold over the ticks she walks
		Leftovers.after(helper, () -> {
			LegendNeeds.ENABLED = needs;
			ServerLevel level = helper.getLevel();
			BuildSiteManager sites = BuildSiteManager.get(level);
			homes.forEach(f -> sites.forgetFinished(f.placement()));
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	/** Runs {@code body} with needs on and only {@code legends} loaded, all put back within the tick. */
	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			LegendNeeds.ENABLED = true;
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Moods.forget();
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	/** A Mason Legend standing at {@code at}, working at the stonecutter at {@link #STATION}. */
	private static Villager mason(GameTestHelper helper, BlockPos at, Legend legend) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(STATION, Blocks.STONECUTTER);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setCustomName(Component.literal("Ada"));
		Jobs.employ(level, v, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		Legends.make(level, v, legend, "test");
		return v;
	}

	/** Puts {@code v} on strike today: a home unmet three days. */
	private static void strike(ServerLevel level, Villager v) {
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, data.checkedOn(Chronicle.day(level), Map.of(LegendText.HOME, 3), Chronicle.day(level), -1));
	}

	private static boolean picketing(Villager v) {
		return v.getBrain().getRunningBehaviors().stream().anyMatch(b -> b instanceof Picket);
	}

	/** Every behaviour of the trade's work that the picket holds back and is running now (none while picketing). */
	private static List<String> gatedRunning(Villager v) {
		return v.getBrain().getRunningBehaviors().stream().filter(b -> b instanceof Gated<?>).map(b -> b.debugString()).toList();
	}

	private static double dist(Villager v, BlockPos pos) {
		return Math.sqrt(v.blockPosition().distSqr(pos));
	}

	private static String where(GameTestHelper helper, Villager v) {
		return String.format("%.1f from the hall, %.1f from the stonecutter, activity %s, running %s", dist(v, helper.absolutePos(HALL)),
			dist(v, helper.absolutePos(STATION)), v.getBrain().getActiveNonCoreActivity(),
			v.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList());
	}

	/**
	 * A Legend with no workstation (the {@code aliveworkplace:legend} trade) has no WORK activity: on strike she pickets in
	 * IDLE and walks from 21 blocks off to the hall, with no job site. A second such Legend not on strike never pickets.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaPicket1110dIdle"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaPicket1110dIdle")
	public void aLegendWithNoWorkstationPicketsInIdle(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		Legend sage = legend("qa_picket_sage", "aliveworkplace:legend");
		Legend seer = legend("qa_picket_seer", "aliveworkplace:legend");
		Villager[] striker = new Villager[1];
		Villager[] calm = new Villager[1];
		boolean[] calmPicketed = new boolean[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(sage, seer), () -> {
			Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
			a.setCustomName(Component.literal("Sage"));
			Legends.make(level, a, sage, "test");
			strike(level, a);
			Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 10));
			b.setCustomName(Component.literal("Seer"));
			Legends.make(level, b, seer, "test");
			helper.assertTrue(a.getVillagerData().getProfession() == ModVillagers.LEGEND && b.getVillagerData().getProfession() == ModVillagers.LEGEND,
				"not of the legend trade: " + a.getVillagerData().getProfession() + ", " + b.getVillagerData().getProfession());
			helper.assertTrue(LegendNeeds.striking(a) && !LegendNeeds.striking(b), "staged strikes wrong");
			striker[0] = a;
			calm[0] = b;
		}));
		helper.onEachTick(() -> {
			if (calm[0] != null && picketing(calm[0])) {
				calmPicketed[0] = true;
			}
		});
		helper.runAtTickTime(700, () -> {
			helper.assertTrue(striker[0] != null, "not staged");
			Villager a = striker[0];
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(a.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "the Sage has a workstation");
			helper.assertTrue(dist(a, hall) < BY_THE_HALL, "the striking Sage didn't picket the hall: " + where(helper, a));
			helper.assertTrue(picketing(a) && gatedRunning(a).isEmpty(), "the striking Sage isn't picketing in IDLE: " + where(helper, a));
			helper.assertTrue(!calmPicketed[0], "a Legend not on strike picketed");
			helper.assertTrue(!picketing(calm[0]), "the Seer pickets: " + where(helper, calm[0]));
			helper.succeed();
		});
	}

	/**
	 * The strike and the picket survive a save and reload of the villager: the copy loaded from her saved tag (the brain
	 * rebuilt from it) is still on strike, keeps her stonecutter, and walks to the hall.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaPicket1110dReload"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaPicket1110dReload")
	public void thePicketSurvivesASaveAndReload(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		Legend legend = legend("qa_picket_reload", "minecraft:mason");
		Villager[] ada = new Villager[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(legend), () -> {
			Villager v = mason(helper, STATION.north(), legend);
			strike(level, v);
			LegendData before = ModAttachments.LEGEND.get(v);
			CompoundTag saved = v.saveWithoutId(new CompoundTag());
			v.discard();
			Villager loaded = EntityType.VILLAGER.create(level);
			loaded.load(saved);
			helper.assertTrue(level.addFreshEntity(loaded), "the reloaded villager wasn't added");
			helper.assertTrue(before.equals(ModAttachments.LEGEND.get(loaded)), "her Legend data after the reload: " + ModAttachments.LEGEND.get(loaded));
			ada[0] = loaded;
		}));
		helper.succeedWhen(() -> {
			helper.assertTrue(ada[0] != null, "not staged");
			Villager v = ada[0];
			helper.assertTrue(LegendNeeds.striking(v), "the reload ended her strike");
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(), "the reload lost her stonecutter");
			helper.assertTrue(dist(v, helper.absolutePos(HALL)) < BY_THE_HALL && picketing(v), "the reloaded Legend isn't picketing: " + where(helper, v));
		});
	}

	/**
	 * The picket's boundary: a striking Mason already {@link Picket#NEAR} blocks from the hall stays by it (she isn't sent to
	 * her stonecutter), while one 11 blocks away walks in.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaPicket1110dNear"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaPicket1110dNear")
	public void aPicketByTheHallStaysAndOneFartherWalksIn(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		Legend near = legend("qa_picket_near", "minecraft:mason");
		Legend far = legend("qa_picket_far", "minecraft:mason");
		Villager[] ada = new Villager[2];
		double[] farthest = new double[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(near, far), () -> {
			ada[0] = mason(helper, HALL.east(Picket.NEAR), near);
			strike(level, ada[0]);
			ada[1] = mason(helper, HALL.south(11), far);
			strike(level, ada[1]);
		}));
		helper.onEachTick(() -> {
			if (ada[0] != null) {
				farthest[0] = Math.max(farthest[0], dist(ada[0], helper.absolutePos(HALL)));
			}
		});
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(ada[0] != null, "not staged");
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(farthest[0] <= Picket.NEAR + 1.5, "the picket NEAR blocks from the hall wandered " + farthest[0] + " blocks off: " + where(helper, ada[0]));
			helper.assertTrue(picketing(ada[0]) && gatedRunning(ada[0]).isEmpty(), "the near picket isn't picketing: " + where(helper, ada[0]));
			helper.assertTrue(dist(ada[1], hall) < BY_THE_HALL && picketing(ada[1]), "the far picket didn't walk in: " + where(helper, ada[1]));
			helper.succeed();
		});
	}

	/**
	 * The strike ends the day every need is met (a tier III home of her own, a happy village): the picket stops that day and
	 * the Mason goes back to her stonecutter, 21 blocks from the hall.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaPicket1110dMet"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaPicket1110dMet")
	public void needsMetEndTheStrikeAndSheGoesBackToWork(GameTestHelper helper) {
		List<BuildSiteManager.Finished> homes = new ArrayList<>();
		setUp(helper, homes);
		ServerLevel level = helper.getLevel();
		Legend legend = legend("qa_picket_met", "minecraft:mason");
		Villager[] ada = new Villager[1];
		boolean[] ended = new boolean[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(legend), () -> {
			ada[0] = mason(helper, STATION.north(), legend);
			strike(level, ada[0]);
		}));
		helper.onEachTick(() -> {
			Villager v = ada[0];
			if (v == null || ended[0] || !(dist(v, helper.absolutePos(HALL)) < BY_THE_HALL && picketing(v))) {
				return;
			}
			// picketing by the hall: now every need is met
			staged(helper, List.of(legend), () -> {
				Moods.ENABLED = false;
				BlockPos hall = helper.absolutePos(HALL);
				BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2)).above(100);
				ResourceLocation home = id("qa_picket_manor_3");
				level.getStructureManager().getOrCreate(home).fillFromWorld(level, origin.above(40), new Vec3i(8, 5, 8), false, Blocks.AIR);
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
				UUID builder = UUID.randomUUID();
				BuildSiteManager.get(level).recordFinished(home, placement, builder);
				homes.add(new BuildSiteManager.Finished(home, placement, builder));
				Beds.home(v, origin.offset(1, 1, 1));
				Leftovers.after(helper, () -> {
					for (BlockPos p : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(3, 2, 3))) {
						if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
							level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16); // the sky bed, both halves, no drop
						}
					}
				});
				((VillageHallBlockEntity) level.getBlockEntity(hall)).setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 0, 0, 0.9f));
				LegendData data = LegendNeeds.check(level, hall, v, Chronicle.day(level));
				helper.assertTrue(data != null && !data.onStrike() && data.unmet().isEmpty(), "every need met, still on strike: " + data);
			});
			v.getBrain().eraseMemory(MemoryModuleType.HOME); // the sky home was only for the check: she sleeps nowhere now
			ended[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ended[0], "she never picketed the hall to begin with: " + (ada[0] == null ? "not staged" : where(helper, ada[0])));
			Villager v = ada[0];
			helper.assertTrue(!LegendNeeds.striking(v), "back on strike");
			helper.assertTrue(!picketing(v), "still picketing after the strike ended: " + where(helper, v));
			helper.assertTrue(dist(v, helper.absolutePos(STATION)) <= 3, "not back at her stonecutter: " + where(helper, v));
		});
	}

	/**
	 * {@code legendNeeds} switched off mid-strike: at once she isn't striking, the hall's round calls the strike off, the
	 * picket stops and she goes back to her stonecutter.
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaPicket1110dOff"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaPicket1110dOff")
	public void legendNeedsOffMidStrikeStopsThePicket(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		Legend legend = legend("qa_picket_off", "minecraft:mason");
		Villager[] ada = new Villager[1];
		boolean[] off = new boolean[1];
		helper.runAfterDelay(2, () -> staged(helper, List.of(legend), () -> {
			ada[0] = mason(helper, STATION.north(), legend);
			strike(level, ada[0]);
		}));
		helper.onEachTick(() -> {
			Villager v = ada[0];
			if (v == null || off[0] || !(dist(v, helper.absolutePos(HALL)) < BY_THE_HALL && picketing(v))) {
				return;
			}
			// the switch is off for this tick only: tests in other batches may run beside this one
			boolean needs = LegendNeeds.ENABLED;
			try {
				LegendNeeds.ENABLED = false;
				helper.assertTrue(!LegendNeeds.striking(v), "with legendNeeds off she still counts as striking");
				LegendNeeds.round(level, helper.absolutePos(HALL));
			} finally {
				LegendNeeds.ENABLED = needs;
			}
			LegendData data = ModAttachments.LEGEND.get(v);
			helper.assertTrue(!data.onStrike() && data.unmet().isEmpty(), "the round with legendNeeds off left the strike on: " + data);
			off[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "she never picketed the hall to begin with: " + (ada[0] == null ? "not staged" : where(helper, ada[0])));
			Villager v = ada[0];
			helper.assertTrue(!LegendNeeds.striking(v) && !picketing(v), "still picketing with legendNeeds off: " + where(helper, v));
			helper.assertTrue(dist(v, helper.absolutePos(STATION)) <= 3, "not back at her stonecutter: " + where(helper, v));
		});
	}

	/**
	 * A villager in a strange mood (29.10) stands at the workstation they claimed, not at the hall: she starts by the hall
	 * and walks to it, and her trade's other work waits.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaPicket1110dMood"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaPicket1110dMood")
	public void aStrangeMoodStandsAtTheClaimedWorkstation(GameTestHelper helper) {
		setUp(helper, new ArrayList<>());
		ServerLevel level = helper.getLevel();
		// strange moods are off in GameTest runs (the config); on for this test, which runs alone in its batch
		boolean strange = StrangeMoods.ENABLED;
		StrangeMoods.ENABLED = true;
		Leftovers.after(helper, () -> StrangeMoods.ENABLED = strange);
		Villager[] ada = new Villager[1];
		double[] nearestHall = {Double.MAX_VALUE};
		boolean[] looked = new boolean[1];
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Legends.get(GOLEM_SMITH).isPresent(), "the golem smith isn't loaded");
			helper.setBlock(STATION, Blocks.STONECUTTER);
			Villager v = helper.spawn(EntityType.VILLAGER, HALL.offset(2, 0, 2));
			v.setCustomName(Component.literal("Ada"));
			Jobs.employ(level, v, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
			v.setVillagerData(v.getVillagerData().setLevel(5));
			ModAttachments.STRANGE_MOOD.set(v, StrangeMood.begin(GOLEM_SMITH, helper.absolutePos(STATION), Optional.of(helper.absolutePos(HALL)),
				List.of(ResourceLocation.withDefaultNamespace("diamond"), ResourceLocation.withDefaultNamespace("blaze_rod"),
					ResourceLocation.withDefaultNamespace("echo_shard")), Chronicle.day(level)));
			helper.assertTrue(StrangeMoods.claiming(v) && helper.absolutePos(STATION).equals(StrangeMoods.station(v)), "the mood isn't on");
			ada[0] = v;
		});
		helper.onEachTick(() -> {
			if (ada[0] != null && helper.getTick() > 300) {
				nearestHall[0] = Math.min(nearestHall[0], dist(ada[0], helper.absolutePos(HALL)));
				// the picket sets her look every 10 ticks and vanilla's look sink lets it lapse between: seen once is enough
				looked[0] |= ada[0].getBrain().getMemory(MemoryModuleType.LOOK_TARGET).filter(t -> t.currentBlockPosition().equals(helper.absolutePos(STATION))).isPresent();
			}
		});
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(ada[0] != null, "not staged");
			Villager v = ada[0];
			helper.assertTrue(StrangeMoods.claiming(v), "the mood ended");
			helper.assertTrue(v.blockPosition().closerThan(helper.absolutePos(STATION), 2.5), "not standing at her claimed workstation: " + where(helper, v));
			helper.assertTrue(nearestHall[0] > BY_THE_HALL, "she went back to the hall: " + nearestHall[0] + " blocks from it");
			helper.assertTrue(picketing(v) && gatedRunning(v).isEmpty(), "in a mood but her trade's work runs: " + where(helper, v));
			helper.assertTrue(looked[0], "she never looked at her workstation: " + v.getBrain().getMemory(MemoryModuleType.LOOK_TARGET));
			helper.succeed();
		});
	}
}
