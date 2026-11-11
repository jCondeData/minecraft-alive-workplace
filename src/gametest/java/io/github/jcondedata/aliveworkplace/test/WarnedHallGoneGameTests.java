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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * B1176: the portcullis dropped on a warned day (32.14) when the Village Hall is broken or moved that day. The bars are
 * kept on the hall's clock in ThreatData and only that hall's round drew them up, so they stayed down for good. Now a
 * hall taken away draws them up and forgets them ({@link Warnings#hallGone}), also after a restart in between; the hall
 * standing still has them drawn up by its round after the day. (QaWarnedHallGone1111dBugTests has the moved hall, the
 * hall put back and the siege.)
 */
public class WarnedHallGoneGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b1176_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall, Gatehouse, Wall Tower and archer by it; warned at dusk of day 11 of day 12's attack; braced at noon of day 12. */
	private static List<BlockPos> bracedAtNoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			Sieges.raise(level, hall, false);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
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
		guard(helper, 16, 14);
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "setup: the village wasn't warned");
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		List<BlockPos> bars = List.copyOf(ThreatData.get(level).braced(hall));
		helper.assertTrue(!bars.isEmpty() && down(level, bars) == bars.size(), "setup: the portcullis didn't drop at noon of the warned day");
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

	private static void guard(GameTestHelper helper, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
	}

	private static int down(ServerLevel level, List<BlockPos> bars) {
		return (int) bars.stream().filter(p -> level.getBlockState(p).is(Blocks.IRON_BARS)).count();
	}

	/** Braced, the world saved and loaded, then the hall broken: every bar goes up at once and the clock forgets them. */
	//$ gametest_ticks_batch AREA '200' '"b1176HallBrokenAfterReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b1176HallBrokenAfterReload")
	public void aHallBrokenAfterARestartDrawsItsBarsUp(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).roundTrip(level.registryAccess());
		helper.assertTrue(ThreatData.get(level).braced(hall).size() == bars.size(), "setup: the dropped bars weren't kept through the save");
		helper.setBlock(HALL, Blocks.AIR); // the player breaks the hall
		int still = down(level, bars);
		helper.assertTrue(still == 0, "the hall was broken on the warned afternoon and " + still + " of " + bars.size() + " portcullis bars are still down");
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the broken hall's clock still keeps its dropped bars: " + ThreatData.get(level).braced(hall));
		ThreatData.get(level).roundTrip(level.registryAccess());
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the dropped bars came back after a restart");
		helper.succeed();
	}

	/** The normal case: the hall stands, its bars stay down through the warned day and its round draws them up after it. */
	//$ gametest_ticks_batch AREA '200' '"b1176HallStanding"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b1176HallStanding")
	public void aStandingHallsBarsStayDownTheDayAndGoUpAfter(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).roundTrip(level.registryAccess());
		level.setDayTime(BASE + VillageNeeds.DAY + 9000); // the afternoon: still down
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(down(level, bars) == bars.size(), "the portcullis went up on the warned afternoon with the hall standing");
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next morning
		Warnings.round(level, hall, 10, 1);
		int still = down(level, bars);
		helper.assertTrue(still == 0, "the morning after the warned day " + still + " of " + bars.size() + " bars are still down");
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the clock still keeps the bars after drawing them up");
		helper.succeed();
	}
}
