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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-11): 32.14's "after the day, bars no siege took over are drawn up", when the Village Hall is not
 * where it was on the warned day. A village warned of tomorrow night's attack drops its Gatehouse portcullis from noon
 * (the bars are kept on the hall's clock in ThreatData). If the player then breaks the hall that afternoon, or moves it
 * a block over, nothing ever draws those bars up: only the hall's own round raises them, keyed by the old hall's spot.
 * Expected: no portcullis stays down for good in a gate whose warned day is over (as with the hall standing).
 * <p>
 * The control: the same village with the hall put back on the very same spot has its bars drawn up the next morning.
 * The setup is WarningGameTests' (a finished Gatehouse to the north, a Wall Tower, an archer posted by it).
 */
public class QaWarnedHallGone1111dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "warned_hall_gone_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	/** Where the moved hall goes: one block east. */
	private static final BlockPos MOVED = new BlockPos(16, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall, Gatehouse, Wall Tower and archer; warned at dusk of day 11 of day 12's attack; at noon of day 12 braced. Returns the dropped bars. */
	private static List<BlockPos> bracedAtNoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true; // (off in GameTests by default; the after() puts the config back)
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos moved = helper.absolutePos(MOVED);
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
			for (BlockPos h : List.of(hall, moved)) {
				Sieges.siege(level, h).ifPresent(s -> Sieges.lift(level, s));
				Sieges.raise(level, h, false);
				VillageRaids.forget(h);
				ThreatData.get(level).forgetSiege(h);
				ThreatData.get(level).forgetPast(h);
				ThreatData.get(level).forgetClock(h);
			}
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		finished(helper, StarterBlueprints.GATEHOUSE, GATEHOUSE);
		finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "setup: no hall");
		ThreatData.get(level).forgetClock(hall);
		ThreatData.get(level).forgetClock(moved);
		guard(helper, 16, 14);
		// Dusk of day 11: the clock sets day 12's attack from the north, and the archer by the tower sees it.
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "setup: the village wasn't warned");
		// Noon of day 12: the gates shut and the portcullis drops.
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		List<BlockPos> bars = List.copyOf(ThreatData.get(level).braced(hall));
		helper.assertTrue(!bars.isEmpty() && down(level, bars) > 0, "setup: the portcullis didn't drop at noon of the warned day");
		return bars;
	}

	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** An archer with a Guard Post of their own two blocks south of them. */
	private static Villager guard(GameTestHelper helper, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	private static int down(ServerLevel level, List<BlockPos> bars) {
		return (int) bars.stream().filter(p -> level.getBlockState(p).is(Blocks.IRON_BARS)).count();
	}

	/** What the server does for a standing hall each round: its gates, the raid clock and the warning's round. */
	private static void hallRound(ServerLevel level, BlockPos hall) {
		Gates.round(level, hall, 1);
		VillageRaids.tick(level, hall, 10, 1, -100, d -> {
		});
		Warnings.round(level, hall, 10, 1);
	}

	/**
	 * The player breaks the Village Hall on the warned afternoon (no raid ever comes: there's no village left). The next
	 * morning, a hundred ticks of the real server later, the Gatehouse's portcullis is still down.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWarnedHallBroken1111d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWarnedHallBroken1111d")
	public void aHallBrokenOnTheWarnedAfternoonLeavesNoPortcullisDown(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, Blocks.AIR); // the player breaks the hall
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next morning
		helper.runAfterDelay(100, () -> {
			int still = down(level, bars);
			helper.assertTrue(still == 0, "the Village Hall was broken on the warned afternoon, and the next morning " + still + " of the "
				+ bars.size() + " portcullis bars it dropped are still down in the Gatehouse (nothing draws them up without the hall)");
			helper.succeed();
		});
	}

	/**
	 * The player moves the Village Hall one block east on the warned afternoon. The next morning the new hall's rounds
	 * run (gates, raid clock, warning) as the server's do, and the old hall's bars are still down in its Gatehouse.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWarnedHallMoved1111d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWarnedHallMoved1111d")
	public void aHallMovedOnTheWarnedAfternoonHasItsBarsDrawnUpNextMorning(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, Blocks.AIR);
		helper.setBlock(MOVED, ModBlocks.VILLAGE_HALL);
		BlockPos moved = helper.absolutePos(MOVED);
		helper.assertTrue(level.getBlockEntity(moved) instanceof VillageHallBlockEntity, "setup: no moved hall");
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next morning
		helper.runAfterDelay(20, () -> {
			hallRound(level, moved);
			helper.runAfterDelay(20, () -> {
				hallRound(level, moved);
				int still = down(level, bars);
				helper.assertTrue(still == 0, "the Village Hall was moved one block on the warned afternoon, and the next morning (the new hall's rounds run) "
					+ still + " of the " + bars.size() + " portcullis bars are still down in its Gatehouse");
				helper.succeed();
			});
		});
	}

	/** The control: the hall broken and put back on the same spot is the same village; the next morning its bars go up. */
	//$ gametest_ticks_batch AREA '300' '"qaWarnedHallBack1111d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWarnedHallBack1111d")
	public void aHallPutBackOnItsSpotDrawsTheBarsUpNextMorning(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(HALL, Blocks.AIR);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
		helper.runAfterDelay(20, () -> {
			hallRound(level, hall);
			int still = down(level, bars);
			helper.assertTrue(still == 0, "the hall put back on its spot: " + still + " of " + bars.size() + " bars still down the next morning");
			helper.succeed();
		});
	}
}
