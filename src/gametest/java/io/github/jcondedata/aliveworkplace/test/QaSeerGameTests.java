package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA for 29.16, the Seer, written from the spec: a Seer on strike (or with Legends off) foretells nothing; a blessed
 * wedding's mood lasts 7 days, gone on the 8th, survives a save, and the early baby comes once; the dawn's lines in
 * English ("today", "tomorrow", "in 2 days", the side raiders come from, no guest); the foretelling's saved state
 * round-trips and an empty one writes nothing.
 */
public class QaSeerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Starfall"), List.of());
		Leftovers.after(helper, () -> {
			Legends.ENABLED = true;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	private static Legend seer(GameTestHelper helper) {
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		Legends.setForTest(Map.of(seer.id(), seer));
		return seer;
	}

	/** A settled Seer foretells; on strike, or with Legends off, nobody foretells and the hall's "What next?" has no Seer lines. */
	//$ gametest_ticks_batch AREA '100' '"qaSeerStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerStrike")
	public void aStrikingSeerForetellsNothing(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Legend legend = seer(helper);
		Villager seer = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 17));
		seer.setNoAi(true);
		Legends.make(level, seer, legend, "test");
		LegendPowers.forget();
		helper.assertTrue(Seer.foreteller(level, hall).isPresent(), "a settled Seer doesn't foretell for the village");
		LegendData d = ModAttachments.LEGEND.get(seer);
		ModAttachments.LEGEND.set(seer, new LegendData(d.id(), d.name(), d.guest(), d.hall(), d.since(), d.lastDay(), d.unmet(), Chronicle.day(level),
			d.lastLuxury(), d.way(), d.checked()));
		LegendPowers.forget();
		helper.assertTrue(Seer.foreteller(level, hall).isEmpty(), "a Seer on strike foretells");
		helper.assertTrue(Seer.tips(level, hall).isEmpty(), "the hall's tips with the Seer on strike");
		ModAttachments.LEGEND.set(seer, d);
		LegendPowers.forget();
		Legends.ENABLED = false;
		try {
			helper.assertTrue(Seer.foreteller(level, hall).isEmpty(), "a Seer foretells with Legends off");
		} finally {
			Legends.ENABLED = true;
		}
		LegendPowers.forget();
		helper.assertTrue(Seer.foreteller(level, hall).isPresent(), "back to work, the Seer doesn't foretell");
		helper.succeed();
	}

	/** The blessing's +10 lasts days 0 to 6 and is gone on day 7; it survives a save; the baby comes early only once. */
	//$ gametest_ticks_batch AREA '100' '"qaSeerBlessing"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeerBlessing")
	public void theBlessingLastsSevenDaysAndTheBabyComesOnce(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager bride = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		bride.setNoAi(true);
		long today = Chronicle.day(level);
		ModAttachments.SEER_BLESSING.set(bride, new Seer.Blessing(today, 10, 7, 2, true));
		LegendPowers.MoodReason mood = Seer.blessedMood(level, bride);
		helper.assertTrue(mood != null && mood.points() == 10, "the wedding day's mood: " + mood);
		helper.assertTrue(!mood.reason().getString().startsWith("mood."), "the mood's reason isn't in English: " + mood.reason().getString());
		ModAttachments.SEER_BLESSING.set(bride, new Seer.Blessing(today - 6, 10, 7, 2, true));
		helper.assertTrue(Seer.blessedMood(level, bride) != null, "the blessing gone on its 7th day");
		ModAttachments.SEER_BLESSING.set(bride, new Seer.Blessing(today - 7, 10, 7, 2, true));
		helper.assertTrue(Seer.blessedMood(level, bride) == null, "the blessing still there on the 8th day");
		Seer.Blessing blessing = new Seer.Blessing(today - 1, 10, 7, 2, true);
		ModAttachments.SEER_BLESSING.set(bride, blessing);
		Villager loaded = EntityType.VILLAGER.create(level);
		loaded.load(bride.saveWithoutId(new CompoundTag()));
		helper.assertTrue(blessing.equals(ModAttachments.SEER_BLESSING.get(loaded)), "the blessing in a save: " + ModAttachments.SEER_BLESSING.get(loaded));
		loaded.discard();
		Seer.babyCame(List.of(bride));
		Seer.Blessing after = ModAttachments.SEER_BLESSING.get(bride);
		helper.assertTrue(after != null && !after.babyDue() && after.mood() == 10 && after.day() == today - 1, "after the baby: " + after);
		helper.assertTrue(Seer.blessedMood(level, bride) != null, "the baby ended the blessing's mood");
		helper.succeed();
	}

	/** The dawn's lines: a calm night, the festival in 2 days, the market tomorrow, no guest; a raid's side; "today" for today. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theDawnLinesInEnglish(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(BlockPos.ZERO);
		long t = 40;
		helper.assertTrue(Seer.when(t, t).getString().equals("today") && Seer.when(t - 1, t).getString().equals("today"), "today: " + Seer.when(t, t).getString());
		helper.assertTrue(Seer.when(t + 1, t).getString().equals("tomorrow"), "tomorrow: " + Seer.when(t + 1, t).getString());
		helper.assertTrue(Seer.when(t + 3, t).getString().equals("in 3 days"), "in 3 days: " + Seer.when(t + 3, t).getString());
		Seer.State calm = new Seer.State(t, false, 0, 0, false, t + 2, t + 1, Map.of());
		List<String> lines = Seer.lines(level, hall, calm).stream().map(c -> c.getString().trim()).toList();
		helper.assertTrue(lines.equals(List.of("No raiders will come tonight.", "The next festival: in 2 days (day 42).", "The next market day: tomorrow (day 41).",
			"No Legend will visit tomorrow.")), "the calm dawn: " + lines);
		Seer.State none = new Seer.State(t, false, 0, 0, false, -1, -1, Map.of());
		List<String> bare = Seer.lines(level, hall, none).stream().map(c -> c.getString().trim()).toList();
		helper.assertTrue(bare.get(1).equals("No festival is coming.") && bare.get(2).startsWith("No market day"), "no festival or market: " + bare);
		Seer.State raid = new Seer.State(t, true, Math.PI / 2, 18000, false, -1, -1, Map.of());
		String side = raid.night().side(hall).getString();
		String first = Seer.lines(level, hall, raid).get(0).getString().trim();
		helper.assertTrue(!side.isEmpty() && first.equals("Raiders will come tonight, from the " + side + "."), "the raid line: " + first);
		helper.succeed();
	}

	/** The hall's foretelling saved and loaded is the same; the empty one writes nothing and an old tag gets the defaults. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theForetellingSurvivesASave(GameTestHelper helper) {
		Seer.State told = new Seer.State(12, true, 1.25, 17500, true, 14, 13,
			Map.of("chapel@13", new Seer.Told(13, java.util.Optional.of(Seer.ID)), "inn@12", new Seer.Told(12, java.util.Optional.empty())));
		CompoundTag tag = new CompoundTag();
		told.save(tag);
		Seer.State back = Seer.State.load(tag);
		helper.assertTrue(told.equals(back), "after a save: " + back);
		helper.assertTrue(back.guestOn(13).map(e -> e.getKey().equals("chapel") && e.getValue().equals(Seer.ID)).orElse(false), "tomorrow's guest: " + back.guestOn(13));
		helper.assertTrue(back.guestOn(12).isEmpty(), "nobody told for today came back as a guest");
		CompoundTag empty = new CompoundTag();
		Seer.State.EMPTY.save(empty);
		helper.assertTrue(!empty.contains("seer"), "the empty foretelling wrote " + empty);
		helper.assertTrue(Seer.State.load(new CompoundTag()).equals(Seer.State.EMPTY), "an old save without it");
		CompoundTag old = new CompoundTag();
		CompoundTag seer = new CompoundTag();
		seer.putLong("told_day", 9);
		old.put("seer", seer);
		Seer.State defaults = Seer.State.load(old);
		helper.assertTrue(defaults.toldDay() == 9 && !defaults.raid() && defaults.festival() == -1 && defaults.market() == -1 && defaults.guests().isEmpty(),
			"an old tag's defaults: " + defaults);
		helper.succeed();
	}
}
