package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Edicts and civic items in the village's life (ROADMAP 30.21): the "What next?" tips (a free edict slot, a reform step
 * waiting, Festival Season with too small a treasury, a guild without its Guildhall, Large Families without a Cradle,
 * harvest season without an idol) each shown when it applies and gone when it doesn't, through the hall's own
 * {@link VillageAdvice#tips}; and the chatter of edicts in force and reformed, with every line in the lang file.
 */
public class CivicTalkGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final ResourceLocation LONG_SHIFTS = AliveWorkplace.id("long_shifts");

	//$ gametest_ticks_batch AREA '100' '"civicTipEdictSlot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipEdictSlot")
	public void aFreeEdictSlotIsAdvisedUntilItIsFilled(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			Tip tip = tip(level, hall, "edict_slot");
			helper.assertTrue(tip != null && tip.title.equals("Edict slots free: 1 (a Hamlet)"), "a Hamlet with no edict: " + tip);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "Long Shifts proclaimed");
			helper.assertTrue(tip(level, hall, "edict_slot") == null, "the Hamlet's one slot is full, yet: " + tip(level, hall, "edict_slot"));
			Edicts.setEnabled(false);
			entity.setEdicts(List.of());
			helper.assertTrue(tip(level, hall, "edict_slot") == null, "edicts off, yet a slot is advised");
		});
	}

	//$ gametest_ticks_batch AREA '100' '"civicTipReformStep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipReformStep")
	public void aReformStepWaitingIsAdvised(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			helper.assertTrue(tip(level, hall, "reform_step") == null, "a reform step with no edict in force");
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LONG_SHIFTS).orElseThrow()).done(), "Long Shifts proclaimed");
			Tip tip = tip(level, hall, "reform_step");
			helper.assertTrue(tip != null && tip.title.startsWith("The Shift Bell, step 1 of 3: ") && tip.how.contains("Quests page"),
				"the Shift Bell's first step: " + tip);
			entity.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS.toString(), 3, true, 0)));
			helper.assertTrue(tip(level, hall, "reform_step") == null, "reformed, yet a step is advised: " + tip(level, hall, "reform_step"));
		});
	}

	//$ gametest_ticks_batch AREA '100' '"civicTipFestivalFund"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipFestivalFund")
	public void festivalSeasonWithAnEmptyTreasuryIsAdvised(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			entity.setTreasury(100);
			helper.assertTrue(tip(level, hall, "festival_fund") == null, "no Festival Season, yet the fund is advised");
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("festival_season").toString(), 0)));
			CivicEffects.forget();
			Tip tip = tip(level, hall, "festival_fund");
			helper.assertTrue(tip != null && tip.title.equals("The treasury can't pay for the next festival (1 of 3 emeralds)"), "1 emerald of 3: " + tip);
			entity.setTreasury(300);
			helper.assertTrue(tip(level, hall, "festival_fund") == null, "3 emeralds pay for it, yet: " + tip(level, hall, "festival_fund"));
		});
	}

	//$ gametest_ticks_batch AREA '100' '"civicTipGuildhall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipGuildhall")
	public void aCharteredGuildWithoutItsGuildhallIsAdvised(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			helper.assertTrue(tip(level, hall, "guildhall") == null, "no guild, yet a Guildhall is advised");
			entity.setGuilds(List.of(new Guilds.Charter(AliveWorkplace.id("miners"), UUID.randomUUID(), "Ada", 0, Optional.empty())));
			Guilds.round(level, hall, entity);
			Tip tip = tip(level, hall, "guildhall");
			helper.assertTrue(tip != null && tip.title.equals(Guilds.get(AliveWorkplace.id("miners")).name().getString() + " has no Guildhall"),
				"the miners without a Guildhall: " + tip);
			boolean on = Guilds.ENABLED;
			Guilds.ENABLED = false;
			Leftovers.after(helper, () -> Guilds.ENABLED = on);
			helper.assertTrue(tip(level, hall, "guildhall") == null, "guilds off, yet a Guildhall is advised");
		});
	}

	//$ gametest_ticks_batch AREA '100' '"civicTipCradle"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipCradle")
	public void largeFamiliesWithoutACradleIsAdvised(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			helper.assertTrue(tip(level, hall, "cradle") == null, "no Large Families, yet a Cradle is advised");
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("large_families").toString(), 0)));
			Tip tip = tip(level, hall, "cradle");
			helper.assertTrue(tip != null && tip.title.equals("Large Families without a Cradle") && tip.how.contains("near a bed"), "Large Families: " + tip);
		});
	}

	//$ gametest_ticks_batch AREA '100' '"civicTipHarvestIdol"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicTipHarvestIdol")
	public void harvestSeasonWithoutAnIdolIsAdvised(GameTestHelper helper) {
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time));
		hall(helper, (level, hall, entity) -> {
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
			season(level, Seasons.Season.SUMMER);
			helper.assertTrue(tip(level, hall, "harvest_idol") == null, "summer, yet an idol is advised");
			season(level, Seasons.Season.AUTUMN);
			Tip tip = tip(level, hall, "harvest_idol");
			helper.assertTrue(tip != null && tip.how.contains("32 blocks"), "autumn with a farmer and no idol: " + tip);
			farmer.discard();
			helper.assertTrue(tip(level, hall, "harvest_idol") == null, "no farmer, yet an idol is advised");
		});
	}

	/** Long Shifts in force: "edict_long_shifts"; reformed: "reformed_long_shifts", with lines in the lang file. */
	//$ gametest_ticks_batch AREA '100' '"civicChatter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicChatter")
	public void villagersTalkOfTheEdictsInForce(GameTestHelper helper) {
		hall(helper, (level, hall, entity) -> {
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			helper.assertTrue(Chatter.civicTopics(level, villager, hall).isEmpty(), "civic talk with nothing in force: " + Chatter.civicTopics(level, villager, hall));
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS.toString(), 0)));
			helper.assertTrue(Chatter.civicTopics(level, villager, hall).equals(List.of("edict_long_shifts")), "Long Shifts: " + Chatter.civicTopics(level, villager, hall));
			helper.assertTrue(Chatter.topics(level, villager, hall).contains("edict_long_shifts"), "not among the topics: " + Chatter.topics(level, villager, hall));
			entity.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS.toString(), 3, true, 0)));
			helper.assertTrue(Chatter.civicTopics(level, villager, hall).equals(List.of("reformed_long_shifts")), "reformed: " + Chatter.civicTopics(level, villager, hall));
			entity.setReforms(List.of());
			// Every edict we ship has its lines, in force and reformed
			for (Edicts.Edict edict : Edicts.all()) {
				if (edict.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
					for (String prefix : List.of("edict_", "reformed_")) {
						String key = "chatter.aliveworkplace." + prefix + edict.id().getPath() + ".1";
						String line = Component.translatable(key).getString();
						helper.assertTrue(!line.equals(key), "no line " + key);
					}
				}
			}
			helper.assertTrue(Component.translatable("chatter.aliveworkplace.reformed_long_shifts.0").getString().equals("The shift bell's rung. Home we go."),
				"the Shift Bell's line");
			villager.discard();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private record Tip(String title, String how) {
	}

	private interface Body {
		void run(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity);
	}

	/** A hall in a village of radius 16, a Hamlet with nothing in force, then {@code body}; everything reset after. */
	private static void hall(GameTestHelper helper, Body body) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean edicts = Edicts.ENABLED;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Edicts.setEnabled(edicts);
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
		Edicts.setEnabled(true);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			entity.setReforms(List.of());
			entity.setQuests(List.of());
			entity.setGuilds(List.of());
			CivicEffects.forget();
			body.run(level, hall, entity);
			helper.succeed();
		});
	}

	/** The tip {@code key} the hall gives now, as the player reads it, or null. */
	private static Tip tip(ServerLevel level, BlockPos hall, String key) {
		CivicEffects.forget();
		for (VillageAdvice.Tip t : VillageAdvice.tips(level, hall)) {
			if (t.key().equals(key)) {
				return new Tip(t.title().getString(), t.how().getString());
			}
		}
		return null;
	}

	private static void season(ServerLevel level, Seasons.Season season) {
		long day = (long) season.ordinal() * Seasons.DAYS + 1;
		level.setDayTime((day - 1) * VillageNeeds.DAY + 6000);
	}
}
