package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA for 29.4 (Legends on the hall), written from the spec: each condition's progress on both sides of its boundary
 * (text and tick), the "What next?" tip at exactly one missing condition, "lives in" only for a Legendary taken
 * elsewhere, every way a Legend comes in English, the hall list's needs at the 0-day boundary, and the six Chatter
 * lines rendered through en_us.json.
 */
public class QaLegendsHallGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final String NS = "aliveworkplace_test";

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	private static Legend legend(String name, String rarity, String job, String rest) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"" + rarity + "\", \"job\": \"" + job + "\", \"title\": \"" + name
			+ "\", \"lore\": \"l\"" + rest + "}").getAsJsonObject());
	}

	private static Map<ResourceLocation, Legend> roster(Legend... legends) {
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		for (Legend l : legends) {
			map.put(l.id(), l);
		}
		return map;
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	private static void cleanUp(GameTestHelper helper, List<UUID> extra) {
		Leftovers.after(helper, () -> {
			Legends.ENABLED = true;
			LegendRecord record = LegendRecord.get(helper.getLevel());
			extra.forEach(record::forget);
			for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				record.forget(e.getUUID());
				e.discard();
			}
			Legends.reload(helper.getLevel().getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String name(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name == null ? "" : name.getString();
	}

	private static boolean gold(ItemStack stack) {
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		return name != null && TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(name.getStyle().getColor());
	}

	private static ItemStack card(ServerLevel level, BlockPos hall, String title) {
		return LegendsPage.cards(level, hall).stream().filter(c -> name(c).equals(title)).findFirst().orElse(ItemStack.EMPTY);
	}

	private static void has(GameTestHelper helper, ItemStack card, String line) {
		helper.assertTrue(lore(card).contains(line), "\"" + name(card) + "\" lacks \"" + line + "\": " + lore(card));
	}

	private static void lacks(GameTestHelper helper, ItemStack card, String line) {
		helper.assertTrue(!lore(card).contains(line), "\"" + name(card) + "\" has \"" + line + "\": " + lore(card));
	}

	private static List<String> legendTips(ServerLevel level, BlockPos hall) {
		return VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("legend")).map(t -> t.title().getString()).toList();
	}

	/** Shared start: a hall on a clean area, Legends set to {@code legends}. */
	private static BlockPos stage(GameTestHelper helper, Legend... legends) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
		Legends.setForTest(roster(legends));
		return hall;
	}

	/** Villagers 2 / 3 / 4 of 3 and emeralds 0 / 0.99 / 1 of 1: the number shown, the cross or tick, and the ready line. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallProgress"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallProgress")
	public void conditionProgressBoundaries(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_count", "rare", "minecraft:farmer", ", \"conditions\": [{\"type\": \"villagers\", \"count\": 3}]"),
				legend("qa_coin", "rare", "minecraft:farmer", ", \"conditions\": [{\"type\": \"treasury_total\", \"emeralds\": 1}]"));
			entity.setRank(VillageRanks.Rank.VILLAGE);
			String ready = "This village has all they need: they may come";
			villager(helper, new BlockPos(12, 2, 12), "Bo");
			villager(helper, new BlockPos(13, 2, 12), "Cy");
			// one short
			ItemStack count = card(level, hall, "qa_count");
			has(helper, count, "✘ Villagers: 2 of 3");
			lacks(helper, count, ready);
			ItemStack coin = card(level, hall, "qa_coin");
			has(helper, coin, "✘ Emeralds the treasury has taken in: 0 of 1");
			lacks(helper, coin, ready);
			// 99 cents: not yet one emerald, never shown as met
			entity.addTreasuryTotal(99);
			coin = card(level, hall, "qa_coin");
			has(helper, coin, "✘ Emeralds the treasury has taken in: 0 of 1");
			lacks(helper, coin, ready);
			// exactly met
			villager(helper, new BlockPos(14, 2, 12), "Di");
			entity.addTreasuryTotal(1);
			count = card(level, hall, "qa_count");
			has(helper, count, "✔ Villagers: 3 of 3");
			has(helper, count, ready);
			coin = card(level, hall, "qa_coin");
			has(helper, coin, "✔ Emeralds the treasury has taken in: 1 of 1");
			has(helper, coin, ready);
			// over: still a tick and ready, never a cross
			villager(helper, new BlockPos(12, 2, 13), "Ed");
			count = card(level, hall, "qa_count");
			helper.assertTrue(lore(count).stream().anyMatch(l -> l.startsWith("✔ Villagers: ")) && lore(count).stream().noneMatch(l -> l.startsWith("✘ Villagers")),
				"over the count: " + lore(count));
			has(helper, count, ready);
			helper.succeed();
		});
	}

	/** The "What next?" tip: none at two missing, one at exactly one missing (exact English), none once nothing is missing or with no conditions. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallTip"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallTip")
	public void whatNextTipAtExactlyOneMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_pair", "rare", "minecraft:farmer",
					", \"conditions\": [{\"type\": \"villagers\", \"count\": 3}, {\"type\": \"treasury_total\", \"emeralds\": 1}]"),
				legend("qa_free", "rare", "minecraft:farmer", ""));
			entity.setRank(VillageRanks.Rank.VILLAGE);
			villager(helper, new BlockPos(12, 2, 12), "Bo");
			villager(helper, new BlockPos(13, 2, 12), "Cy");
			// two missing: no tip
			helper.assertTrue(legendTips(level, hall).isEmpty(), "a tip at two missing: " + legendTips(level, hall));
			// exactly one missing: the tip, naming the missing condition with its progress
			entity.addTreasuryTotal(100);
			List<VillageAdvice.Tip> tips = VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("legend")).toList();
			helper.assertTrue(tips.size() == 1 && tips.get(0).title().getString().equals("The qa_pair could come: Villagers: 2 of 3"),
				"one missing: " + legendTips(level, hall));
			helper.assertTrue(tips.get(0).how().getString().equals("Only one thing is missing for the qa_pair: Villagers: 2 of 3. The Legends page says how they come."),
				"how: " + tips.get(0).how().getString());
			// nothing missing: no tip (and the Legend with no conditions never had one)
			villager(helper, new BlockPos(14, 2, 12), "Di");
			helper.assertTrue(legendTips(level, hall).isEmpty(), "a tip with nothing missing: " + legendTips(level, hall));
			has(helper, card(level, hall, "qa_free"), "Nothing: any village may have them");
			helper.succeed();
		});
	}

	/** "lives in": for a Legendary taken elsewhere only; not for a Rare settled elsewhere, never naming the own village. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallLivesIn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallLivesIn")
	public void livesInOnlyForLegendaryTakenElsewhere(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_taken", "legendary", "aliveworkplace:legend", ""),
				legend("qa_rare_away", "rare", "aliveworkplace:legend", ""),
				legend("qa_mine", "legendary", "aliveworkplace:legend", ""));
			LegendRecord record = LegendRecord.get(level);
			BlockPos elsewhere = hall.offset(700, 0, 0);
			made.add(uuid("qa_wren"));
			made.add(uuid("qa_finn"));
			record.settled(id("qa_taken"), uuid("qa_wren"), level.dimension(), Optional.of(elsewhere), Rarity.LEGENDARY, "Wren", 1);
			record.settled(id("qa_rare_away"), uuid("qa_finn"), level.dimension(), Optional.of(elsewhere), Rarity.RARE, "Finn", 1);
			Villager ada = villager(helper, new BlockPos(12, 2, 12), "Ada");
			Legends.make(level, ada, Legends.get(id("qa_mine")).orElseThrow(), "test");

			String away = VillageHalls.name(level, elsewhere).getString();
			String home = VillageHalls.name(level, hall).getString();
			List<ItemStack> cards = LegendsPage.cards(level, hall);
			helper.assertTrue(name(cards.get(0)).equals("Ada, qa_mine") && gold(cards.get(0)), "own Legendary not first: " + name(cards.get(0)));
			has(helper, card(level, hall, "qa_taken"), "Wren lives in " + away);
			ItemStack rare = card(level, hall, "qa_rare_away");
			helper.assertTrue(!rare.isEmpty(), "no card for the Rare settled elsewhere");
			helper.assertTrue(lore(rare).stream().noneMatch(l -> l.contains(" lives in ")), "a Rare settled elsewhere is shown as taken: " + lore(rare));
			helper.assertTrue(card(level, hall, "qa_mine").isEmpty(), "the own Legendary also has a roster card");
			for (ItemStack c : cards) {
				helper.assertTrue(lore(c).stream().noneMatch(l -> l.equals("Ada lives in " + home) || l.endsWith(" lives in " + home)),
					"\"lives in\" the own village on " + name(c) + ": " + lore(c));
			}
			helper.succeed();
		});
	}

	/** A Legend that can't come here (a Legendary taken elsewhere, or one already living here) gets no "could come" tip at one missing. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallTakenTip"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallTakenTip")
	public void noTipForLegendThatCannotCome(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			String one = ", \"conditions\": [{\"type\": \"villagers\", \"count\": 5}]";
			BlockPos hall = stage(helper,
				legend("qa_gone", "legendary", "minecraft:farmer", one),
				legend("qa_home", "legendary", "minecraft:farmer", one),
				legend("qa_open", "rare", "minecraft:farmer", one));
			entity.setRank(VillageRanks.Rank.VILLAGE);
			made.add(uuid("qa_gone_wren"));
			LegendRecord.get(level).settled(id("qa_gone"), uuid("qa_gone_wren"), level.dimension(), Optional.of(hall.offset(700, 0, 0)), Rarity.LEGENDARY, "Wren", 1);
			Villager ada = villager(helper, new BlockPos(12, 2, 12), "Ada");
			villager(helper, new BlockPos(13, 2, 12), "Bo");
			Legends.make(level, ada, Legends.get(id("qa_home")).orElseThrow(), "test");
			List<String> tips = legendTips(level, hall);
			// the open Rare is one condition short: its tip is there (the control)
			helper.assertTrue(tips.stream().anyMatch(t -> t.startsWith("The qa_open could come: Villagers: ")), "no tip for the open Rare: " + tips);
			helper.assertTrue(tips.stream().noneMatch(t -> t.startsWith("The qa_gone ")), "a tip for a Legendary living elsewhere: " + tips);
			helper.assertTrue(tips.stream().noneMatch(t -> t.startsWith("The qa_home ")), "a tip for a Legend already living here: " + tips);
			helper.succeed();
		});
	}

	/** Every way a Legend comes, in exact English, under "How they come:"; a Legend with no ways says it comes only by a command. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallWays"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallWays")
	public void everyArrivalWayInEnglish(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_ways", "rare", "minecraft:farmer", ", \"arrive\": ["
					+ "{\"way\": \"visit\", \"place\": \"market\"}, {\"way\": \"visit\", \"place\": \"festival\"},"
					+ " {\"way\": \"visit\", \"place\": \"festival\", \"crowd\": 6}, {\"way\": \"visit\", \"place\": \"chapel\"},"
					+ " {\"way\": \"visit\", \"place\": \"hall\"}, {\"way\": \"found\", \"site\": \"outpost\"},"
					+ " {\"way\": \"found\", \"site\": \"shipwreck\"}, {\"way\": \"found\", \"site\": \"hermit_hut\"},"
					+ " {\"way\": \"found\", \"site\": \"desert_well\"},"
					+ " {\"way\": \"born\", \"trades\": [\"minecraft:farmer\", \"minecraft:fisherman\"]},"
					+ " {\"way\": \"inspired\", \"trades\": [\"minecraft:librarian\"]}]"),
				legend("qa_noway", "rare", "minecraft:farmer", ""));
			ItemStack ways = card(level, hall, "qa_ways");
			for (String line : List.of("How they come:", "  • Visits on market day", "  • Visits a festival", "  • Visits a festival with a crowd of 6",
				"  • Visits the Chapel at a full-moon midnight", "  • Visits the Village Hall", "  • Found in a pillager outpost",
				"  • Found by a shipwreck", "  • Found in a hermit's hut", "  • Found out in the world", "  • Born to a Farmer or Fisherman",
				"  • A Master Librarian seized by a strange mood")) {
				has(helper, ways, line);
			}
			has(helper, card(level, hall, "qa_noway"), "  • Comes only by a command");
			helper.succeed();
		});
	}

	/** The hall's list: two Legends ahead of everyone, in gold; a need unmet 1 day is a cross, 0 days a tick; no strike line without a strike. */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallList")
	public void hallListNeedsAtTheDayBoundary(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_reader", "rare", "aliveworkplace:legend", ", \"needs\": {\"luxury\": \"books\"}"),
				legend("qa_toper", "rare", "aliveworkplace:legend", ", \"needs\": {\"luxury\": \"wine\"}"));
			villager(helper, new BlockPos(14, 2, 14), "Bo");
			villager(helper, new BlockPos(16, 2, 14), "Cy");
			Villager ada = villager(helper, new BlockPos(4, 2, 4), "Ada");
			Villager eve = villager(helper, new BlockPos(26, 2, 26), "Eve");
			Legends.make(level, ada, Legends.get(id("qa_reader")).orElseThrow(), "test");
			Legends.make(level, eve, Legends.get(id("qa_toper")).orElseThrow(), "test");
			LegendData data = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(data.id(), data.name(), data.guest(), data.hall(), data.since(), data.lastDay(),
				Map.of("luxury", 1, "happy", 0), -1, data.lastLuxury(), data.way()));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			ItemStack first = menu.icon(VillageHallScreen.FIRST_PERSON);
			ItemStack second = menu.icon(VillageHallScreen.FIRST_PERSON + 1);
			Set<String> legends = new LinkedHashSet<>(List.of(name(first), name(second)));
			helper.assertTrue(legends.equals(Set.of("Ada, qa_reader", "Eve, qa_toper")) && gold(first) && gold(second),
				"Legends not first, in gold: " + name(first) + " / " + name(second) + " / " + name(menu.icon(VillageHallScreen.FIRST_PERSON + 2)));
			ItemStack adaRow = name(first).startsWith("Ada") ? first : second;
			has(helper, adaRow, "Rare Legend · no trade of their own");
			has(helper, adaRow, "✘ Their luxury once a week: books");
			has(helper, adaRow, "✔ A happy village");
			has(helper, adaRow, "✔ A home of their own (tier III or better)");
			helper.assertTrue(lore(adaRow).stream().noneMatch(l -> l.startsWith("On strike")), "a strike line with no strike: " + lore(adaRow));
			ItemStack eveRow = adaRow == first ? second : first;
			has(helper, eveRow, "✔ Their luxury once a week: wine");
			helper.succeed();
		});
	}

	/** The six Chatter lines about Legends and guests, each said, read from en_us.json with the Legend's name filled in. */
	//$ gametest_ticks_batch AREA '400' '"qaLegendsHallChatter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLegendsHallChatter")
	public void sixChatterLinesResolve(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_poet", "rare", "aliveworkplace:legend", ""),
				legend("qa_guest", "rare", "aliveworkplace:legend", ""));
			Villager bo = villager(helper, new BlockPos(14, 2, 14), "Bo");
			Villager ada = villager(helper, new BlockPos(16, 2, 16), "Ada");
			Legends.make(level, ada, Legends.get(id("qa_poet")).orElseThrow(), "test");
			Villager gil = villager(helper, new BlockPos(18, 2, 16), "Gil");
			ModAttachments.LEGEND.set(gil, new LegendData(id("qa_guest"), "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit"));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Map<String, String> said = new LinkedHashMap<>();
			collect(level, bo, hall, player, said, 1500);
			LegendData data = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(data.id(), data.name(), false, data.hall(), data.since(), data.lastDay(), Map.of("happy", 2), 3,
				data.lastLuxury(), data.way()));
			collect(level, bo, hall, player, said, 1500);
			collect(level, ada, hall, player, said, 1500);
			List<String> problems = new ArrayList<>();
			for (String k : List.of("legend_here.0", "legend_here.1", "legend_guest.0", "legend_guest.1", "legend_strike.0", "legend_self.0")) {
				String text = said.get("chatter.aliveworkplace." + k);
				if (text == null) {
					problems.add(k + " never said");
				} else if (text.contains("chatter.") || text.contains("%") || text.contains("legend.aliveworkplace")) {
					problems.add(k + " unresolved: " + text);
				} else if (k.startsWith("legend_guest") ? !text.contains("Gil") : k.startsWith("legend_self") ? !text.contains("qa_poet") : !text.contains("Ada")) {
					problems.add(k + " lacks the name: " + text);
				}
			}
			helper.assertTrue(problems.isEmpty(), "chatter: " + problems + " said: " + said);
			helper.succeed();
		});
	}

	private static void collect(ServerLevel level, Villager speaker, BlockPos hall, ServerPlayer player, Map<String, String> said, int tries) {
		for (int i = 0; i < tries; i++) {
			Component line = Chatter.line(level, speaker, hall, player);
			if (line != null && line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chatter.aliveworkplace.legend_")) {
				said.putIfAbsent(t.getKey(), line.getString());
			}
		}
	}
}
