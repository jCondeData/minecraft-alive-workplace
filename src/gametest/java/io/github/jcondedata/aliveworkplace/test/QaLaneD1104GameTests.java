package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA, lane D, 2026-11-04: B133 (the Seer as a source of the day's raid warning), B136 (Legends take M34's luxury goods),
 * B426 (the crafters' shortage line names items in the plural after a count above one). Written from each bug's
 * expected behavior and its item's spec, from both sides of each boundary. The Cobblemon halves (B426's Cup line, B466)
 * are in {@code QaLaneD1104CompatTests}.
 */
public class QaLaneD1104GameTests implements FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1104_raiders");
	private static final long DAY = VillageNeeds.DAY;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	// ---- B133: the Seer ----

	record SeerVillage(ServerLevel level, BlockPos hall, Villager seer, List<Component> heard) {
	}

	/** A hall, a settled Seer (the only Legend loaded), no tower, a player by the hall; everything put back after. */
	static SeerVillage seerVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.setDayTime(time);
			new WorkplaceConfig().apply();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		Legends.setForTest(Map.of(seer.id(), seer));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ThreatData.get(level).forgetClock(hall);
		Villager s = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		s.setNoAi(true);
		Legends.make(level, s, seer, "test");
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "QaSeer1104");
		player.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		return new SeerVillage(level, hall, s, heard);
	}

	private static String hover(SeerVillage v) {
		return DefencePage.next(v.level(), v.hall()).getHoverName().getString();
	}

	static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/**
	 * The Seer sees two days ahead, not three: an attack three days off is not told (and the Defense page still says
	 * none is seen); a day later, two days off, it is told at once in the Seer's words ("A vision of the ... road: ...
	 * will strike ... in 2 days."), once; the page counts it down: in 2 days, tomorrow night, tonight.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104SeerDays"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104SeerDays")
	public void qaB133TheSeerTellsAnAttackTwoDaysOffButNotThree(GameTestHelper helper) {
		SeerVillage v = seerVillage(helper);
		ServerLevel level = v.level();
		BlockPos hall = v.hall();
		ThreatData data = ThreatData.get(level);
		ThreatData.Attack attack = new ThreatData.Attack(33, RAIDERS, -Math.PI / 2, 15000);
		data.rolled(hall, 32, attack);
		at(level, 30, 300);
		helper.assertFalse(Warnings.warn(level, hall, 5, 0), "told three days ahead by a Seer who sees two");
		helper.assertTrue(data.warned(hall) == -1, "warned " + data.warned(hall) + " three days ahead");
		helper.assertTrue(v.heard().stream().noneMatch(c -> c.getString().contains(" will strike ")), "the player heard " + v.heard());
		helper.assertTrue(hover(v).equals("No attack seen coming"), "the Defense page three days ahead: " + hover(v));

		at(level, 31, 300);
		helper.assertTrue(Warnings.warn(level, hall, 5, 0) && data.warned(hall) == 33, "not told two days ahead");
		String want = "A vision of the " + Warnings.side(hall, attack).getString() + " road: " + Warnings.who(level, hall, attack).getString() + " will strike "
			+ VillageHalls.name(level, hall).getString() + " in 2 days.";
		List<String> heard = v.heard().stream().map(Component::getString).filter(s -> s.contains(" will strike ")).toList();
		helper.assertTrue(heard.equals(List.of(want)), "the player heard " + heard + ", want [" + want + "]");
		helper.assertTrue(hover(v).equals("Attack in 2 days"), "the Defense page two days ahead: " + hover(v));
		helper.assertFalse(Warnings.warn(level, hall, 5, 0), "told twice");

		at(level, 32, 300);
		helper.assertTrue(hover(v).equals("Attack tomorrow night"), "the Defense page a day ahead: " + hover(v));
		at(level, 33, 300);
		helper.assertTrue(hover(v).equals("Attack tonight"), "the Defense page on the day: " + hover(v));
		helper.assertTrue(v.heard().stream().filter(c -> c.getString().contains(" will strike ")).count() == 1, "told more than once: " + v.heard());
		helper.succeed();
	}

	/**
	 * A Seer who is gone (dead) is no source: an attack two days off isn't told, and nothing reads as if it had been.
	 * The same village with the Seer alive (the other side) is told: the test can tell the two apart.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104SeerGone"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104SeerGone")
	public void qaB133ASeerWhoIsGoneGivesNoWarning(GameTestHelper helper) {
		SeerVillage v = seerVillage(helper);
		ServerLevel level = v.level();
		BlockPos hall = v.hall();
		ThreatData data = ThreatData.get(level);
		helper.assertTrue(Warnings.source(level, hall, 2).isPresent(), "a living Seer is no source two days ahead");
		v.seer().discard();
		helper.assertTrue(Warnings.source(level, hall, 2).isEmpty(), "a Seer who is gone is still a source: " + Warnings.source(level, hall, 2));
		data.rolled(hall, 42, new ThreatData.Attack(43, RAIDERS, 0, 15000));
		at(level, 41, 300);
		helper.assertFalse(Warnings.warn(level, hall, 5, 0), "told by a Seer who is gone");
		helper.assertTrue(data.warned(hall) == -1 && v.heard().stream().noneMatch(c -> c.getString().contains(" will strike ")),
			"warned " + data.warned(hall) + ", heard " + v.heard());
		helper.assertTrue(hover(v).equals("No lookout"), "the Defense page with nobody watching: " + hover(v));
		helper.succeed();
	}

	// ---- B136: the Legends' luxuries ----

	private static Legend luxuryLegend(String kind) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1104_" + kind), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \"qa1104\", \"lore\": \"l\", \"needs\": {\"luxury\": \"" + kind + "\"},"
				+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"minecraft:farmer\"], \"factor\": 2.0}]}").getAsJsonObject());
	}

	/** A Legend of {@code legend} made from a new villager (no home), settled in the test's village. */
	private static Villager legendary(GameTestHelper helper, Legend legend, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(2));
		v.setCustomName(Component.literal("Ada"));
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, new LegendData(data.id(), data.name(), false, Optional.of(helper.absolutePos(HALL)), 0, -1, Map.of(), -1, -1, "test"));
		return v;
	}

	/**
	 * The hall, the store (a chest by a smoker) and a cleanup; {@code body} runs with Legend needs on and the roster put
	 * back before the tick ends, the store chest emptied.
	 */
	private static void luxuryVillage(GameTestHelper helper, java.util.function.BiConsumer<BlockPos, Container> body) {
		Leftovers.clear(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos smoker = helper.absolutePos(new BlockPos(16, 2, 16));
			BlockPos storePos = helper.absolutePos(new BlockPos(17, 2, 16));
			level.setBlockAndUpdate(smoker, Blocks.SMOKER.defaultBlockState());
			level.setBlockAndUpdate(storePos, Blocks.CHEST.defaultBlockState());
			boolean moods = Moods.ENABLED;
			boolean needs = LegendNeeds.ENABLED;
			try {
				LegendNeeds.ENABLED = true;
				helper.assertTrue(VillageNeeds.store(level, hall).contains(storePos), "the chest by the smoker isn't the store");
				body.accept(hall, (Container) level.getBlockEntity(storePos));
			} finally {
				Moods.ENABLED = moods;
				LegendNeeds.ENABLED = needs;
				Moods.forget();
				Legends.reload(level.getServer().getResourceManager());
				LegendPowers.forget();
				((Container) level.getBlockEntity(storePos)).clearContent();
				level.setBlockAndUpdate(storePos, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(smoker, Blocks.AIR.defaultBlockState());
			}
			helper.succeed();
		});
	}

	/**
	 * Every one of 1.8's luxury goods is taken by the Legend who likes its kind, from the village store, and the good of
	 * another kind beside it is left; while it lasts the Legend is "enjoying their wine / jewels / books / fine clothing".
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104Luxuries"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104Luxuries")
	public void qaB136EveryLuxuryGoodIsTakenByItsOwnKindOnly(GameTestHelper helper) {
		record Case(String kind, Item good, Item other, String word) {
		}
		List<Case> cases = List.of(
			new Case("wine", ModItems.CIDER, ModItems.NOBLE_ROBES, "wine"),
			new Case("wine", ModItems.BERRY_WINE, ModItems.GAZETTE, "wine"),
			new Case("wine", ModItems.VINTAGE_WINE, ModItems.GOLD_CIRCLET, "wine"),
			new Case("jewels", ModItems.AMETHYST_RING, ModItems.CIDER, "jewels"),
			new Case("jewels", ModItems.EMERALD_BROOCH, ModItems.FINE_CLOTHES, "jewels"),
			new Case("jewels", ModItems.GOLD_CIRCLET, ModItems.ILLUMINATED_BOOK, "jewels"),
			new Case("books", ModItems.GAZETTE, ModItems.AMETHYST_RING, "books"),
			new Case("books", ModItems.ILLUMINATED_BOOK, ModItems.VINTAGE_WINE, "books"),
			new Case("clothes", ModItems.WORK_CLOTHES, ModItems.EMERALD_BROOCH, "fine clothing"),
			new Case("clothes", ModItems.FINE_CLOTHES, ModItems.BERRY_WINE, "fine clothing"),
			new Case("clothes", ModItems.NOBLE_ROBES, ModItems.GOLD_CIRCLET, "fine clothing"));
		luxuryVillage(helper, (hall, store) -> {
			ServerLevel level = helper.getLevel();
			List<String> wrong = new ArrayList<>();
			int i = 0;
			for (Case c : cases) {
				Legend legend = luxuryLegend(c.kind());
				Legends.setForTest(Map.of(legend.id(), legend));
				LegendPowers.forget();
				store.clearContent();
				store.setItem(0, new ItemStack(c.other(), 3));
				store.setItem(1, new ItemStack(c.good(), 1));
				Villager ada = legendary(helper, legend, new BlockPos(6 + (i % 5) * 2, 2, 10 + (i / 5) * 2));
				i++;
				long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
				LegendData data = LegendNeeds.check(level, hall, ada, today);
				String what = c.kind() + "/" + c.good().getDescription().getString();
				if (data.lastLuxury() != today || store.countItem(c.good()) != 0) {
					wrong.add(what + ": not taken (lastLuxury " + data.lastLuxury() + ", left " + store.countItem(c.good()) + ")");
				}
				if (store.countItem(c.other()) != 3) {
					wrong.add(what + ": took the " + c.other().getDescription().getString());
				}
				var mood = LegendNeeds.luxuryMood(level, ada);
				String said = mood == null ? "none" : mood.reason().getString();
				if (!said.equals("enjoying their " + c.word())) {
					wrong.add(what + ": mood reads \"" + said + "\"");
				}
				LegendRecord.get(level).forget(ada.getUUID());
				ada.discard();
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		});
	}

	/**
	 * Both sides: a wine-loving Legend with only the other kinds' 1.8 goods in the store takes none of them and her luxury
	 * goes unmet ("wine once a week" is what she wants); a single Cider put in, she takes it the next day.
	 */
	//$ gametest_ticks_batch BIG '100' '"qa1104LuxuryUnmet"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qa1104LuxuryUnmet")
	public void qaB136OnlyOtherKindsLeaveHerLuxuryUnmetUntilACiderComes(GameTestHelper helper) {
		luxuryVillage(helper, (hall, store) -> {
			ServerLevel level = helper.getLevel();
			Legend legend = luxuryLegend("wine");
			Legends.setForTest(Map.of(legend.id(), legend));
			LegendPowers.forget();
			Item[] others = {ModItems.AMETHYST_RING, ModItems.EMERALD_BROOCH, ModItems.GOLD_CIRCLET, ModItems.GAZETTE, ModItems.ILLUMINATED_BOOK,
				ModItems.WORK_CLOTHES, ModItems.FINE_CLOTHES, ModItems.NOBLE_ROBES};
			for (int k = 0; k < others.length; k++) {
				store.setItem(k, new ItemStack(others[k], 1));
			}
			Villager ada = legendary(helper, legend, new BlockPos(8, 2, 8));
			LegendData data = LegendNeeds.check(level, hall, ada, 10);
			helper.assertTrue(data.lastLuxury() == -1 && data.unmet().getOrDefault(io.github.jcondedata.aliveworkplace.legend.LegendText.LUXURY, 0) == 1,
				"a wine-lover's luxury met with no wine: " + data);
			for (int k = 0; k < others.length; k++) {
				helper.assertTrue(store.countItem(others[k]) == 1, "she took the " + others[k].getDescription().getString());
			}
			String wants = LegendNeeds.wants(legend, data).getString();
			helper.assertTrue(wants.contains("wine once a week"), "what she wants: " + wants);
			store.setItem(others.length, new ItemStack(ModItems.CIDER, 1));
			data = LegendNeeds.check(level, hall, ada, 11);
			helper.assertTrue(data.lastLuxury() == 11 && store.countItem(ModItems.CIDER) == 0, "day 11: the Cider wasn't taken: " + data);
		});
	}

	// ---- B426: the crafters' shortage line ----

	/** The line as a builder reads it, from the real lack of a chain: one log in the singular, two in the plural, one Barrel. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB426TheRealLackReadsOneAndTwoLogsRight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, Integer> four = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 4, Map.of());
		check(wrong, JobShortages.lackLine(four, 4, Items.OAK_PLANKS).getString(), "short of 1 Oak Log to make 4 Oak Planks");
		Map<Item, Integer> eight = Crafting.lacking(level, Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 8, Map.of());
		check(wrong, JobShortages.lackLine(eight, 8, Items.OAK_PLANKS).getString(), "short of 2 Oak Logs to make 8 Oak Planks");
		Map<Item, Integer> one = new LinkedHashMap<>();
		one.put(Items.OAK_LOG, 2);
		check(wrong, JobShortages.lackLine(one, 1, Items.BARREL).getString(), "short of 2 Oak Logs to make 1 Barrel");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B920 (B426 kin): other woods than oak: a crafter short of two logs of jungle, cherry, crimson or warped wood names
	 * them in the plural, as B426 expects ("plural item names after counts above one"), the same as spruce, birch and the rest.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB426EveryWoodsLogsAreNamedInThePlural(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		Map<Item, String> planks = new LinkedHashMap<>();
		planks.put(Items.SPRUCE_PLANKS, "short of 2 Spruce Logs to make 8 Spruce Planks");
		planks.put(Items.BIRCH_PLANKS, "short of 2 Birch Logs to make 8 Birch Planks");
		planks.put(Items.JUNGLE_PLANKS, "short of 2 Jungle Logs to make 8 Jungle Planks");
		planks.put(Items.ACACIA_PLANKS, "short of 2 Acacia Logs to make 8 Acacia Planks");
		planks.put(Items.DARK_OAK_PLANKS, "short of 2 Dark Oak Logs to make 8 Dark Oak Planks");
		planks.put(Items.MANGROVE_PLANKS, "short of 2 Mangrove Logs to make 8 Mangrove Planks");
		planks.put(Items.CHERRY_PLANKS, "short of 2 Cherry Logs to make 8 Cherry Planks");
		planks.put(Items.CRIMSON_PLANKS, "short of 2 Crimson Stems to make 8 Crimson Planks");
		planks.put(Items.WARPED_PLANKS, "short of 2 Warped Stems to make 8 Warped Planks");
		planks.forEach((item, want) -> {
			Map<Item, Integer> lack = Crafting.lacking(level, Crafting.Kind.CRAFTING, item, 8, Map.of());
			check(wrong, JobShortages.lackLine(lack, 8, item).getString(), want);
		});
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	static void check(List<String> wrong, String got, String want) {
		if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
	}
}
