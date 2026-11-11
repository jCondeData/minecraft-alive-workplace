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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA verify of B1176 (lane D, 2026-11-11): the Village Hall broken or moved round a warned day (32.14) or a siege
 * (32.6). From the spec: no portcullis stays down in a gate whose warned day or siege is over; a hall broken calls off
 * its raid (raiders leave, the siege lifts at the next dawn) and draws up its braced bars; a hall put back on its spot
 * braces again. These cover what QaWarnedHallGone1111dBugTests and WarnedHallGoneGameTests don't: bars a player set by
 * hand on a quiet day, a second village, live raiders, a hall broken before noon, and break / put back / break again.
 */
public class QaWarnedHallGoneVerify1111dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_hall_gone_verify_besiegers");
	private static final ResourceLocation BAND = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_hall_gone_verify_band");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	/** The neighbour village's hall, in the south-west corner. */
	private static final BlockPos OTHER = new BlockPos(2, 2, 28);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall, a finished Gatehouse and Wall Tower, an archer by the tower; raids on; nothing on the hall's clock yet. */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos other = helper.absolutePos(OTHER);
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject()));
		with.put(BAND, Culture.read(BAND, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}]}").getAsJsonObject()));
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			for (BlockPos h : List.of(hall, other)) {
				Sieges.siege(level, h).ifPresent(s -> Sieges.lift(level, s));
				Sieges.raise(level, h, false);
				VillageRaids.forget(h);
				ThreatData.get(level).end(h);
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
		ThreatData.get(level).forgetClock(other);
		guard(helper, 16, 14);
	}

	/** Dusk of day 11: the clock sets day 12's attack from the north, and the archer by the tower warns the village. */
	private static void warned(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "setup: the village wasn't warned");
	}

	/** Noon of day 12 with the hall's round run: returns the bars it dropped (all down). */
	private static List<BlockPos> braced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		hallRound(level, hall);
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

	/** A husk raider (no AI, so it stays put) standing at {@code at}. */
	private static Mob raider(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Mob husk = EntityType.HUSK.create(level);
		BlockPos pos = helper.absolutePos(at);
		husk.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
		husk.addTag(VillageRaids.TAG);
		husk.setNoAi(true);
		level.addFreshEntity(husk);
		return husk;
	}

	private static int down(ServerLevel level, List<BlockPos> bars) {
		return (int) bars.stream().filter(p -> level.getBlockState(p).is(Blocks.IRON_BARS)).count();
	}

	/** Iron bars anywhere in the Gatehouse's corner of the test area (its own windows included). */
	private static int barsAroundGatehouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 20, 18)))) {
			if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
				n++;
			}
		}
		return n;
	}

	/** What the server does for a standing hall each round: its gates, the raid clock and the warning's round. */
	private static void hallRound(ServerLevel level, BlockPos hall) {
		Gates.round(level, hall, 1);
		VillageRaids.tick(level, hall, 10, 1, -100, d -> {
		});
		Warnings.round(level, hall, 10, 1);
	}

	/**
	 * A quiet day (no attack on the clock, no warning): the player has built a portcullis of their own, iron bars set by
	 * hand in the Gatehouse's gateway. Breaking the Village Hall must leave the player's bars where they are: only bars
	 * the village dropped for a warned day are its to draw up.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallGoneVerifyHandBars"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallGoneVerifyHandBars")
	public void barsAPlayerSetByHandStayWhenTheHallIsBrokenOnAQuietDay(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		// Where the Gatehouse's portcullis goes: let it drop once and draw it up silently, so the clock keeps nothing.
		level.setDayTime(BASE + 3000);
		Sieges.brace(level, hall);
		List<BlockPos> slots = List.copyOf(ThreatData.get(level).braced(hall));
		Sieges.raise(level, hall, false);
		helper.assertTrue(!slots.isEmpty() && down(level, slots) == 0 && ThreatData.get(level).braced(hall).isEmpty(), "setup: no portcullis slots found, or the bars stayed");
		helper.assertTrue(Warnings.warned(level, hall).isEmpty(), "setup: the village is warned on its quiet day");
		for (BlockPos p : slots) {
			level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 3); // the player sets their own portcullis
		}
		helper.setBlock(HALL, Blocks.AIR); // and breaks the hall
		helper.runAfterDelay(60, () -> {
			int left = down(level, slots);
			helper.assertTrue(left == slots.size(), "a quiet day, the player's own iron bars set in the Gatehouse gateway: breaking the Village Hall took "
				+ (slots.size() - left) + " of the " + slots.size() + " away");
			helper.succeed();
		});
	}

	/**
	 * Two villages. This one is warned and braced at noon; a neighbour (its hall in the corner) has a raid of its own
	 * under way, its raider standing by it, and bars of its own dropped (its clock; its hall block isn't placed, so
	 * its own rounds don't run in the test). Breaking this hall draws up this
	 * village's bars only: the neighbour's raid, its raider and its bars stay.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallGoneVerifyNeighbour"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallGoneVerifyNeighbour")
	public void breakingOneHallLeavesTheNeighbourVillagesRaidAndBarsAlone(GameTestHelper helper) {
		village(helper);
		warned(helper);
		List<BlockPos> bars = braced(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos other = helper.absolutePos(OTHER);
		List<BlockPos> theirs = new ArrayList<>();
		for (int x = 1; x <= 3; x++) {
			BlockPos p = helper.absolutePos(new BlockPos(x, 2, 21));
			level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 3);
			theirs.add(p);
		}
		ThreatData.get(level).braced(other, theirs);
		Mob husk = raider(helper, new BlockPos(1, 2, 28));
		VillageRaids.track(level, other, Threats.get(BAND).orElseThrow(), List.of(husk));
		helper.assertTrue(ThreatData.get(level).raid(other).isPresent(), "setup: the neighbour's raid didn't begin");
		helper.setBlock(HALL, Blocks.AIR); // the player breaks this village's hall
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(down(level, bars) == 0, "this village's hall was broken and " + down(level, bars) + " of its " + bars.size() + " bars are still down");
			helper.assertTrue(ThreatData.get(level).raid(other).isPresent(), "breaking this village's hall ended the neighbour village's raid");
			helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "breaking this village's hall sent the neighbour village's raider away");
			helper.assertTrue(down(level, theirs) == theirs.size(), "breaking this village's hall drew up " + (theirs.size() - down(level, theirs))
				+ " of the neighbour village's " + theirs.size() + " bars");
			helper.assertTrue(ThreatData.get(level).braced(other).size() == theirs.size(), "the neighbour's clock lost its bars: " + ThreatData.get(level).braced(other));
			helper.succeed();
		});
	}

	/**
	 * The siege on the warned night, its raider alive, and the village (named "Testford") with a player by it. The
	 * player breaks the hall: the raid is called off at once (the raider leaves, the player is told in Testford's name),
	 * and the morning after the siege is lifted with no bar of its portcullis left down.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHallGoneVerifyLiveSiege"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHallGoneVerifyLiveSiege")
	public void aHallBrokenMidSiegeSendsItsLiveRaidersAwayAndLiftsTheSiegeByMorning(GameTestHelper helper) {
		village(helper);
		warned(helper);
		List<BlockPos> bars = braced(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setCustomName(Component.literal("Testford"));
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "qa-hall-gone");
		player.moveTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5, 0f, 0f);
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK + 1500); // the raiders' hour
		Mob husk = raider(helper, new BlockPos(14, 2, 1));
		VillageRaids.track(level, hall, Threats.get(BESIEGERS).orElseThrow(), List.of(husk));
		ThreatData.Siege siege = Sieges.siege(level, hall).orElse(null);
		helper.assertTrue(siege != null && !siege.portcullis.isEmpty() && Sieges.portcullisDown(level, hall), "setup: the siege didn't begin with the bars down");
		List<BlockPos> held = List.copyOf(siege.portcullis);
		heard.clear();
		helper.setBlock(HALL, Blocks.AIR); // the player breaks the hall, the raider still alive
		helper.assertTrue(husk.isRemoved() || !husk.isAlive(), "the hall was broken during the siege and its live raider is still there");
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the hall was broken during the siege and its raid is still on the clock");
		List<String> said = heard.stream().map(Component::getString).toList();
		helper.assertTrue(said.contains("The raid on Testford is over: the raiders are gone."), "the player by the broken hall heard " + said);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next morning
		helper.runAfterDelay(80, () -> {
			int still = down(level, held) + down(level, bars);
			helper.assertTrue(Sieges.siege(level, hall).isEmpty(), "the morning after the hall was broken mid-siege, the siege still holds the gates");
			helper.assertTrue(still == 0, "the morning after the hall was broken mid-siege, " + still + " portcullis bars are still down");
			helper.succeed();
		});
	}

	/**
	 * The hall broken on the morning of the warned day, before noon, when no bar is down yet. Noon comes and the real
	 * server runs a few seconds: no portcullis drops for a village with no hall. Then the hall is put back on its spot
	 * and its round braces as it would have (the warning is still the village's), proving the Gatehouse can drop.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHallGoneVerifyBeforeNoon"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHallGoneVerifyBeforeNoon")
	public void aHallBrokenBeforeNoonOfTheWarnedDayLeavesNoBarsToDropAtNoon(GameTestHelper helper) {
		village(helper);
		warned(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.setDayTime(BASE + VillageNeeds.DAY + 3000); // the warned day's morning
		hallRound(level, hall);
		int before = barsAroundGatehouse(helper);
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "setup: bars dropped before noon");
		helper.setBlock(HALL, Blocks.AIR); // the player breaks the hall
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100); // noon
		helper.runAfterDelay(100, () -> {
			int now = barsAroundGatehouse(helper);
			helper.assertTrue(now == before, "the hall was broken before noon of the warned day, and at noon " + (now - before) + " iron bars dropped in the Gatehouse anyway");
			helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "a broken hall's clock braced at noon: " + ThreatData.get(level).braced(hall));
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL); // put back on its spot that afternoon
			hallRound(level, hall);
			List<BlockPos> bars = ThreatData.get(level).braced(hall);
			helper.assertTrue(!bars.isEmpty() && down(level, bars) == bars.size(), "the hall put back on its spot on the warned afternoon didn't brace again");
			helper.succeed();
		});
	}

	/**
	 * Broken, put back, broken again, all on the warned afternoon: the bars go up, drop again with the hall back (it braces
	 * again), and go up again when it is broken the second time, with nothing left on the clock. The next morning the
	 * hall is put back once more and its round finds no bar to drop and none down.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHallGoneVerifyTwice"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHallGoneVerifyTwice")
	public void aHallBrokenPutBackAndBrokenAgainLeavesNoBarsDown(GameTestHelper helper) {
		village(helper);
		warned(helper);
		List<BlockPos> bars = braced(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.setDayTime(BASE + VillageNeeds.DAY + 8000); // the afternoon
		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(down(level, bars) == 0, "first break: " + down(level, bars) + " of " + bars.size() + " bars still down");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		hallRound(level, hall);
		List<BlockPos> again = List.copyOf(ThreatData.get(level).braced(hall));
		helper.assertTrue(!again.isEmpty() && down(level, again) == again.size(), "the hall put back on its spot didn't brace again: " + again);
		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(down(level, again) == 0 && down(level, bars) == 0, "second break: " + (down(level, again) + down(level, bars)) + " bars still down");
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the clock still keeps bars after the second break: " + ThreatData.get(level).braced(hall));
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000); // the next morning
		helper.runAfterDelay(40, () -> {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			hallRound(level, hall);
			helper.runAfterDelay(20, () -> {
				hallRound(level, hall);
				int still = down(level, bars) + down(level, again);
				helper.assertTrue(still == 0, "the morning after (hall back), " + still + " portcullis bars are down");
				helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the morning after, the clock braced again: " + ThreatData.get(level).braced(hall));
				helper.succeed();
			});
		});
	}
}
