package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * QA (qa-lane-d-1027-0110) for 29.2, the Legend engine, and 29.4's Mythic line, written from the spec: malformed Legend
 * files fail alone, the {@code legend} profession is never reset to a jobless Novice, a pace power's radius from both
 * sides and only for its trades, a dead Legend's aura gone by the next 200-tick refresh, condition numbers at their
 * edges, and the Legends page's Mythic line for each rank in exact English.
 */
public class QaLegendEngineGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_legend");
	private static final ResourceLocation BAD = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bad");

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static void sendLegendsAway(GameTestHelper helper) {
		Leftovers.after(helper, () -> {
			for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				io.github.jcondedata.aliveworkplace.legend.LegendRecord.get(helper.getLevel()).forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
	}

	private static String base(String extra) {
		return "{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\"" + extra + "}";
	}

	/** Every way a server owner's file can be wrong fails that file (never a Legend with no conditions), and a good minimal one loads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void malformedLegendFilesFailAlone(GameTestHelper helper) {
		List<String> bad = List.of(
			"{\"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\"}", // no rarity
			"{\"rarity\": \"epic\", \"job\": \"aliveworkplace:legend\", \"title\": \"t\", \"lore\": \"l\"}", // unknown rarity
			"{\"rarity\": \"rare\", \"title\": \"t\", \"lore\": \"l\"}", // no job
			"{\"rarity\": \"rare\", \"job\": \"aliveworkplace:juggler\", \"title\": \"t\", \"lore\": \"l\"}", // unknown job
			"{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\", \"lore\": \"l\"}", // no title
			base(", \"arrive\": [{\"way\": \"teleport\"}]"),
			base(", \"needs\": {\"luxury\": \"gold\"}"),
			base(", \"powers\": [{\"type\": \"pace\", \"factor\": 0.5}]"), // a pace that slows
			base(", \"arrive\": [{\"way\": \"visit\", \"conditions\": [{\"type\": \"villagerz\", \"count\": 2}]}]"));
		for (String json : bad) {
			JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
			try {
				Legend l = Legends.read(BAD, obj);
				helper.fail("a malformed Legend file loaded: " + json + " -> " + l);
			} catch (RuntimeException expected) {
				// the loader catches it per file and skips only that one
			}
		}
		Legend minimal = Legends.read(BAD, JsonParser.parseString(base("")).getAsJsonObject());
		helper.assertTrue(minimal != null && minimal.conditions().isEmpty() && minimal.job().toString().equals("aliveworkplace:legend"),
			"a minimal Legend file didn't load: " + minimal);
		helper.assertTrue(Legends.get(TEST).isPresent(), "the good test Legend should still be loaded");
		helper.succeed();
	}

	/**
	 * A Legend without a trade of their own (the Old Sage: the {@code legend} profession, no workstation) is never reset to a
	 * jobless Novice while they live with AI on and no job site; a vanilla farmer in the same spot is, as a control.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLegendProfessionKept"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLegendProfessionKept")
	public void legendProfessionNeverResetToNovice(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		helper.setDayTime(2000);
		ResourceLocation sageId = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");
		Villager sage = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 5));
		helper.runAfterDelay(2, () -> {
			Legends.make(helper.getLevel(), sage, Legends.get(sageId).orElseThrow(), "test");
			helper.assertTrue(sage.getVillagerData().getProfession() == ModVillagers.LEGEND && sage.getVillagerData().getLevel() == 5,
				"the Old Sage isn't a Master of the legend trade: " + sage.getVillagerData());
		});
		Villager control = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 5));
		control.setVillagerData(control.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
		control.setVillagerXp(0);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(control.getVillagerData().getProfession() == VillagerProfession.NONE,
				"control: vanilla should have reset the farmer, is " + control.getVillagerData().getProfession());
			helper.assertTrue(sage.getVillagerData().getProfession() == ModVillagers.LEGEND && sage.getVillagerData().getLevel() == 5,
				"the Old Sage lost the legend trade: " + sage.getVillagerData());
			helper.succeed();
		});
	}

	/** The test Legend's pace (builders, radius 8, 1.5x): a builder 8 blocks away is sped up, one 9 away and a farmer next to it aren't. */
	//$ gametest_ticks_batch AREA '100' '"qaLegendPaceEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendPaceEdges")
	public void pacePowerRadiusBothSidesAndOnlyItsTrades(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		sendLegendsAway(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 0;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.runAfterDelay(2, () -> {
			Villager edge = villager(helper, new BlockPos(11, 2, 5), ModVillagers.BUILDER, 1);
			Villager beyond = villager(helper, new BlockPos(12, 2, 5), ModVillagers.BUILDER, 1);
			Villager farmer = villager(helper, new BlockPos(4, 2, 5), VillagerProfession.FARMER, 1);
			int edgeBase = BuilderLevels.delay(1000, edge);
			int beyondBase = BuilderLevels.delay(1000, beyond);
			Villager ada = villager(helper, new BlockPos(3, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(helper.getLevel(), ada, Legends.get(TEST).orElseThrow(), "test");
			helper.assertTrue(LegendPowers.pace(edge) == 1.5f, "a builder exactly 8 blocks away: pace " + LegendPowers.pace(edge));
			helper.assertTrue(BuilderLevels.delay(1000, edge) == Math.round(edgeBase / 1.5f), "edge delay " + BuilderLevels.delay(1000, edge) + " from " + edgeBase);
			helper.assertTrue(LegendPowers.pace(beyond) == 1f && BuilderLevels.delay(1000, beyond) == beyondBase,
				"a builder 9 blocks away was sped up: " + LegendPowers.pace(beyond));
			helper.assertTrue(LegendPowers.pace(farmer) == 1f, "a farmer (not a covered trade) was sped up: " + LegendPowers.pace(farmer));
			helper.succeed();
		});
	}

	/** A Legend who dies takes their aura with them by the next refresh (200 ticks), with no entity scan to notice. */
	//$ gametest_ticks_batch AREA '400' '"qaLegendDies"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLegendDies")
	public void deadLegendsAuraEndsByTheNextRefresh(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		sendLegendsAway(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 0;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Villager[] near = new Villager[1];
		Villager[] ada = new Villager[1];
		helper.runAfterDelay(2, () -> {
			near[0] = villager(helper, new BlockPos(5, 2, 5), ModVillagers.BUILDER, 1);
			ada[0] = villager(helper, new BlockPos(3, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(helper.getLevel(), ada[0], Legends.get(TEST).orElseThrow(), "test");
			helper.assertTrue(LegendPowers.pace(near[0]) == 1.5f, "alive: pace " + LegendPowers.pace(near[0]));
			ada[0].kill();
		});
		helper.runAfterDelay(2 + LegendPowers.REFRESH + 5, () -> {
			helper.assertTrue(!ada[0].isAlive(), "the Legend should be dead");
			helper.assertTrue(LegendPowers.pace(near[0]) == 1f, "a dead Legend still speeds the builder: " + LegendPowers.pace(near[0]));
			helper.assertTrue(LegendPowers.settled(helper.getLevel()).stream().noneMatch(a -> a.villager() == ada[0]), "the dead Legend is still listed");
			helper.succeed();
		});
	}

	/** Condition numbers at their edges: one short is not met, exactly the number is, and a guest (not settled) Legend doesn't count. */
	//$ gametest_ticks_batch AREA '100' '"qaLegendConditionEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLegendConditionEdges")
	public void conditionEdgesAndGuestsDontCount(GameTestHelper helper) {
		Leftovers.clear(helper);
		sendLegendsAway(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		BlockPos hallRel = new BlockPos(15, 2, 15);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(hallRel);
		BlockPos hall = helper.absolutePos(hallRel);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			Condition village = Conditions.parse(JsonParser.parseString("{\"type\": \"rank_at_least\", \"rank\": \"village\"}").getAsJsonObject());
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(!village.met(level, hall), "a Hamlet met rank_at_least village");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(village.met(level, hall), "a Village didn't meet rank_at_least village");
			Condition rich = Conditions.parse(JsonParser.parseString("{\"type\": \"treasury_total\", \"emeralds\": 100}").getAsJsonObject());
			entity.addTreasuryTotal(100 * 100 - 1); // one cent short
			helper.assertTrue(!rich.met(level, hall) && rich.progress(level, hall).have() == 99, "99.99 emeralds: " + rich.progress(level, hall).have());
			entity.addTreasuryTotal(1);
			helper.assertTrue(rich.met(level, hall) && rich.progress(level, hall).have() == 100, "100 emeralds: " + rich.progress(level, hall).have());
			Condition settled = Conditions.parse(JsonParser.parseString("{\"type\": \"legend\", \"id\": \"aliveworkplace_test:test_legend\"}").getAsJsonObject());
			Villager guest = villager(helper, new BlockPos(5, 2, 5), VillagerProfession.NONE, 1);
			Legends.make(level, guest, Legends.get(TEST).orElseThrow(), "test");
			LegendData d = ModAttachments.LEGEND.get(guest);
			ModAttachments.LEGEND.set(guest, new LegendData(d.id(), d.name(), true, d.hall(), d.since(), d.lastDay(), d.unmet(), d.strikeSince(), d.lastLuxury(), d.way(), d.checked()));
			LegendPowers.forget();
			helper.assertTrue(!settled.met(level, hall), "a guest Legend counted as settled");
			helper.succeed();
		});
	}

	/** The Legends page's Mythic line for each rank, in exact English: none as a Village, one as a Town, two as a City. */
	//$ gametest_ticks_batch AREA '60' '"qaLegendMythicLine"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLegendMythicLine")
	public void mythicLinePerRank(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos hallRel = new BlockPos(15, 2, 15);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(hallRel);
		BlockPos hall = helper.absolutePos(hallRel);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			String[][] expect = {{"VILLAGE", "Mythic Legends: 0 of 0, as a Village"}, {"TOWN", "Mythic Legends: 0 of 1, as a Town"},
				{"CITY", "Mythic Legends: 0 of 2, as a City"}};
			for (String[] e : expect) {
				entity.setRank(VillageRanks.Rank.valueOf(e[0]));
				String line = LegendsPage.mythicLine(level, hall).getString();
				helper.assertTrue(line.equals(e[1]), "as a " + e[0] + ": '" + line + "', expected '" + e[1] + "'");
			}
			helper.succeed();
		});
	}
}
