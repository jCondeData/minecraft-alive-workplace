package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendLook;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * 29.4, Legends on the hall: the Legends page's cards against a staged village (each condition's progress, how each
 * comes, the taken Legendary's "lives in", the Mythic line, the village's own Legends first, the grave and zombie lines,
 * Legends switched off), the hall's list with a Legend first in gold with their needs and strike, the "What next?" tip
 * for a Legend one condition short, the chatter about Legends and guests, the look packet, and every new sentence.
 */
public class LegendsHallGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
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

	/** The staged roster: a Legendary two conditions short, a Rare one short, a Mythic, one that lives here and one taken elsewhere. */
	private static Map<ResourceLocation, Legend> roster() {
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		Legend architect = legend("hall_architect", "legendary", "aliveworkplace:builder",
			", \"conditions\": [{\"type\": \"rank_at_least\", \"rank\": \"town\"}, {\"type\": \"treasury_total\", \"emeralds\": 100}],"
				+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"inn\"}], \"needs\": {\"luxury\": \"jewels\"},"
				+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"aliveworkplace:builder\"], \"radius\": 32, \"factor\": 2.0}]");
		Legend chef = legend("hall_chef", "rare", "minecraft:farmer",
			", \"conditions\": [{\"type\": \"villagers\", \"count\": 5}],"
				+ " \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}, {\"way\": \"born\", \"trades\": [\"minecraft:farmer\"]}]");
		Legend founder = legend("hall_founder", "mythic", "aliveworkplace:legend",
			", \"conditions\": [{\"type\": \"rank_at_least\", \"rank\": \"city\"}], \"arrive\": [{\"way\": \"inspired\", \"founder\": true}]");
		Legend bard = legend("hall_bard", "rare", "aliveworkplace:legend",
			", \"needs\": {\"luxury\": \"books\"}, \"powers\": [{\"type\": \"mood\", \"points\": 7, \"radius\": 0}],"
				+ " \"outfit\": \"aliveworkplace_test:textures/entity/villager/legend/bard_own.png\"");
		Legend taken = legend("hall_taken", "legendary", "aliveworkplace:legend", "");
		for (Legend l : List.of(architect, chef, founder, bard, taken)) {
			map.put(l.id(), l);
		}
		return map;
	}

	private static UUID uuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	/** The test's record entries and Legend villagers go with it, and the real roster comes back. */
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

	/** The card whose name is (or, for the village's own, contains) {@code title}. */
	private static ItemStack card(List<ItemStack> cards, String title) {
		return cards.stream().filter(c -> name(c).equals(title)).findFirst().orElse(ItemStack.EMPTY);
	}

	private static void has(GameTestHelper helper, ItemStack card, String line) {
		helper.assertTrue(lore(card).contains(line), name(card) + " lacks \"" + line + "\": " + lore(card));
	}

	/** The page's cards against a staged village, then the progress moving, the Mythic line, grave and zombie lines, Legends off. */
	//$ gametest_ticks_batch AREA '200' '"legendsHallPage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "legendsHallPage")
	public void legendsPageCards(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard); // leftovers from earlier batches
			Legends.setForTest(roster());
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.addTreasuryTotal(5000); // 50 emeralds, in cents
			Villager ada = villager(helper, new BlockPos(12, 2, 12), "Ada");
			villager(helper, new BlockPos(13, 2, 12), "Bo");
			villager(helper, new BlockPos(14, 2, 12), "Cy");
			Legends.make(level, ada, Legends.get(id("hall_bard")).orElseThrow(), "test");
			made.add(uuid("wren"));
			BlockPos elsewhere = hall.offset(600, 0, 0);
			LegendRecord record = LegendRecord.get(level);
			record.settled(id("hall_taken"), uuid("wren"), level.dimension(), Optional.of(elsewhere), Rarity.LEGENDARY, "Wren", 1);

			List<ItemStack> cards = LegendsPage.cards(level, hall);
			// the village's own first: Ada, in gold, with a glint
			ItemStack first = cards.get(0);
			helper.assertTrue(first.is(Items.NETHER_STAR) && name(first).equals("Ada, hall_bard") && gold(first)
				&& Boolean.TRUE.equals(first.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "first card: " + first + " " + name(first));
			has(helper, first, "Rare Legend · no trade of their own");
			has(helper, first, "★ Everyone in the village is 7 happier");
			has(helper, first, "✔ A home of their own (tier III or better)");
			has(helper, first, "✔ Their luxury once a week: books");
			has(helper, first, "✔ A happy village");
			helper.assertTrue(card(cards, "hall_bard").isEmpty(), "the Rare living here also has a card of the others");

			ItemStack architect = card(cards, "hall_architect");
			helper.assertTrue(architect.is(Items.GOLD_INGOT), "architect card: " + architect);
			has(helper, architect, "Legendary Legend · Builder");
			has(helper, architect, "  • Visits the inn");
			has(helper, architect, "✘ Village rank: Village of Town");
			has(helper, architect, "✘ Emeralds the treasury has taken in: 50 of 100");
			has(helper, architect, "Likes jewels");
			has(helper, architect, "★ Every Builder within 32 blocks works 2× as fast");
			helper.assertTrue(lore(architect).stream().noneMatch(l -> l.startsWith("This village has all")), "architect ready: " + lore(architect));

			ItemStack chef = card(cards, "hall_chef");
			has(helper, chef, "  • Found at a ruined portal");
			has(helper, chef, "  • Born to a Farmer");
			has(helper, chef, "✘ Villagers: 3 of 5");

			ItemStack founder = card(cards, "hall_founder");
			helper.assertTrue(founder.is(Items.END_CRYSTAL), "founder card: " + founder);
			has(helper, founder, "  • Comes at the village's first rise to City");
			has(helper, founder, "Mythic Legends: 0 of 0, as a Village");

			ItemStack taken = card(cards, "hall_taken");
			helper.assertTrue(taken.is(Items.GRAY_DYE), "a taken Legendary isn't greyed: " + taken);
			has(helper, taken, "Wren lives in " + VillageHalls.name(level, elsewhere).getString());
			helper.assertTrue(lore(taken).stream().noneMatch(l -> l.startsWith("This village has all")), "a taken Legendary says it may come");

			// "What next?": the Rare one villager short; not the Legendary two short, nor the Mythic a Village has no room for
			List<VillageAdvice.Tip> tips = VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("legend")).toList();
			helper.assertTrue(tips.size() == 1 && tips.get(0).title().getString().equals("The hall_chef could come: Villagers: 3 of 5"),
				"tips: " + tips.stream().map(t -> t.title().getString()).toList());
			helper.assertTrue(tips.get(0).how().getString().equals("Only one thing is missing for the hall_chef: Villagers: 3 of 5. The Legends page says how they come."),
				"tip how: " + tips.get(0).how().getString());

			// progress moves: 50 more emeralds, a Town
			entity.addTreasuryTotal(5000);
			entity.setRank(VillageRanks.Rank.TOWN);
			cards = LegendsPage.cards(level, hall);
			architect = card(cards, "hall_architect");
			has(helper, architect, "✔ Emeralds the treasury has taken in: 100 of 100");
			has(helper, architect, "✔ Village rank: Town of Town");
			has(helper, architect, "This village has all they need: they may come");
			has(helper, card(cards, "hall_founder"), "Mythic Legends: 0 of 1, as a Town");
			// a Town has room for a Mythic: now the Founder, a City short, gets a tip too; the Architect has all and needs none
			tips = VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("legend")).toList();
			helper.assertTrue(tips.size() == 2 && tips.get(1).title().getString().equals("The hall_founder could come: Village rank: Town of City"),
				"tips as a Town: " + tips.stream().map(t -> t.title().getString()).toList());
			helper.assertTrue(lore(LegendsPage.cards(level, hall).get(0)).size() > 0, "own card");

			// a Mythic settled here: the Mythic line counts them, and the header says so
			made.add(uuid("myth"));
			record.settled(id("hall_founder"), uuid("myth"), level.dimension(), Optional.of(hall), Rarity.MYTHIC, "Orla", 3);
			cards = LegendsPage.cards(level, hall);
			has(helper, card(cards, "hall_founder"), "Mythic Legends: 1 of 1, as a Town");
			ItemStack orla = cards.stream().filter(c -> name(c).equals("Orla, hall_founder")).findFirst().orElse(ItemStack.EMPTY);
			has(helper, orla, "Lives here since day 3");
			record.died(uuid("myth"), 4, Optional.of(hall.east()));
			orla = LegendsPage.cards(level, hall).stream().filter(c -> name(c).equals("Orla, hall_founder")).findFirst().orElse(ItemStack.EMPTY);
			has(helper, orla, "In their grave: an Undertaker can bring them back");
			made.add(uuid("zombie"));
			record.turned(uuid("myth"), uuid("zombie"));
			orla = LegendsPage.cards(level, hall).stream().filter(c -> name(c).equals("Orla, hall_founder")).findFirst().orElse(ItemStack.EMPTY);
			has(helper, orla, "Turned into a zombie villager: cure them to have them back");

			// the page through the hall's screen: a nether-star tab, its header with the Mythic line, the cards below
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			int tab = HallPages.slot(LegendsPage.PAGE);
			helper.assertTrue(tab >= 0 && menu.icon(tab).is(Items.NETHER_STAR), "no nether-star tab: " + (tab < 0 ? "none" : menu.icon(tab)));
			menu.press(tab, player);
			helper.assertTrue(menu.icon(4).is(Items.NETHER_STAR) && lore(menu.icon(4)).contains("Mythic Legends: 1 of 1, as a Town"), "header: " + lore(menu.icon(4)));
			helper.assertTrue(name(menu.icon(VillageHallScreen.FIRST_ROW)).equals("Ada, hall_bard"), "first row: " + name(menu.icon(VillageHallScreen.FIRST_ROW)));

			// Legends off: one card that says so
			Legends.ENABLED = false;
			cards = LegendsPage.cards(level, hall);
			helper.assertTrue(cards.size() == 1 && cards.get(0).get(DataComponents.CUSTOM_NAME).getContents() instanceof TranslatableContents t
				&& t.getKey().equals("message.aliveworkplace.legend.off"), "off: " + cards);
			Legends.ENABLED = true;
			helper.succeed();
		});
	}

	/** The hall's list: the Legend first, in gold, with rarity, powers, needs (a cross for one unmet) and a strike in red. */
	//$ gametest_ticks_batch AREA '200' '"legendsHallList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "legendsHallList")
	public void legendFirstOnTheList(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legends.setForTest(roster());
			// two ordinary villagers nearer the hall, the Legend furthest: still first
			villager(helper, new BlockPos(14, 2, 14), "Bo");
			villager(helper, new BlockPos(16, 2, 16), "Cy");
			Villager ada = villager(helper, new BlockPos(5, 2, 5), "Ada");
			Legends.make(level, ada, Legends.get(id("hall_bard")).orElseThrow(), "test");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			ItemStack first = menu.icon(VillageHallScreen.FIRST_PERSON);
			helper.assertTrue(name(first).equals("Ada, hall_bard") && gold(first), "first on the list: " + name(first));
			has(helper, first, "Rare Legend · no trade of their own");
			has(helper, first, "★ Everyone in the village is 7 happier");
			has(helper, first, "✔ A home of their own (tier III or better)");
			helper.assertTrue(!name(menu.icon(VillageHallScreen.FIRST_PERSON + 1)).contains("hall_bard"), "the Legend twice");

			// a need unmet two days and a strike (as 29.5's round writes them): a cross and a red line
			LegendData data = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(data.id(), data.name(), data.guest(), data.hall(), data.since(), data.lastDay(),
				Map.of("home", 2), 4, data.lastLuxury(), data.way()));
			menu = VillageHallScreen.forTest(player, hall);
			first = menu.icon(VillageHallScreen.FIRST_PERSON);
			has(helper, first, "✘ A home of their own (tier III or better)");
			has(helper, first, "✔ A happy village");
			has(helper, first, "On strike since day 4");
			ItemLore lore = first.get(DataComponents.LORE);
			Component strike = lore.lines().stream().filter(l -> l.getString().equals("On strike since day 4")).findFirst().orElseThrow();
			helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.RED).equals(strike.getStyle().getColor()), "the strike isn't red");
			// clicking a Legend (in the jobless list: no workstation) finds them rather than offering them a job
			menu.press(VillageHallScreen.FIRST_PERSON, player);
			helper.assertTrue(!menu.icon(VillageHallScreen.FIND).is(Items.SPYGLASS), "a Legend was offered a job");

			// Legends off: an ordinary villager on the list again
			Legends.ENABLED = false;
			menu = VillageHallScreen.forTest(player, hall);
			for (int s = VillageHallScreen.FIRST_PERSON; s < VillageHallScreen.FIRST_PERSON + 3; s++) {
				helper.assertTrue(!gold(menu.icon(s)) && !name(menu.icon(s)).contains("hall_bard"), "a Legend while off: " + name(menu.icon(s)));
			}
			Legends.ENABLED = true;
			helper.succeed();
		});
	}

	/** Chatter about the Legend living here, a guest and a strike, the Legend about themself; the look packet. */
	//$ gametest_ticks_batch AREA '200' '"legendsHallChatter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "legendsHallChatter")
	public void legendChatterAndLook(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legends.setForTest(roster());
			Villager bo = villager(helper, new BlockPos(14, 2, 14), "Bo");
			Villager ada = villager(helper, new BlockPos(16, 2, 16), "Ada");
			helper.assertTrue(LegendLook.look(ada) == null, "a look for an ordinary villager");
			helper.assertTrue(Chatter.topics(level, bo, hall).stream().noneMatch(t -> t.startsWith("legend")), "Legend talk with none");
			Legend bard = Legends.get(id("hall_bard")).orElseThrow();
			Legends.make(level, ada, bard, "test");

			LegendLook.Look look = LegendLook.look(ada);
			helper.assertTrue(look != null && look.legend() && look.entityId() == ada.getId()
				&& look.outfit().equals(ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/villager/legend/bard_own.png")), "look: " + look);
			helper.assertTrue(LegendLook.outfit(Legends.get(id("hall_chef")).orElseThrow())
				.equals(ResourceLocation.fromNamespaceAndPath(NS, "textures/entity/villager/legend/hall_chef.png")), "the default outfit path");

			helper.assertTrue(Chatter.topics(level, bo, hall).contains("legend_here"), "no talk of the Legend: " + Chatter.topics(level, bo, hall));
			helper.assertTrue(Chatter.topics(level, ada, hall).contains("legend_self"), "the Legend doesn't speak of themself");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Component said = null;
			for (int i = 0; i < 200 && said == null; i++) {
				Component line = Chatter.line(level, bo, hall, player);
				if (line != null && line.getContents() instanceof TranslatableContents t && t.getKey().startsWith("chatter.aliveworkplace.legend_here.")) {
					said = line;
				}
			}
			helper.assertTrue(said != null && said.getString().contains("Ada, hall_bard"), "the line about Ada: " + (said == null ? "never said" : said.getString()));

			// on strike, and a guest
			LegendData data = ModAttachments.LEGEND.get(ada);
			ModAttachments.LEGEND.set(ada, new LegendData(data.id(), data.name(), false, data.hall(), data.since(), data.lastDay(), Map.of("happy", 2), 6,
				data.lastLuxury(), data.way()));
			helper.assertTrue(Chatter.topics(level, bo, hall).contains("legend_strike"), "no talk of the strike");
			Villager guest = villager(helper, new BlockPos(18, 2, 16), "Gil");
			ModAttachments.LEGEND.set(guest, new LegendData(id("hall_chef"), "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit"));
			helper.assertTrue(Chatter.topics(level, bo, hall).contains("legend_guest"), "no talk of the guest");
			List<ItemStack> cards = LegendsPage.cards(level, hall);
			ItemStack gil = cards.stream().filter(c -> name(c).equals("Gil, hall_chef")).findFirst().orElse(ItemStack.EMPTY);
			has(helper, gil, "A guest of the village until day 9");

			Legends.clear(ada);
			helper.assertTrue(LegendLook.look(ada) == null, "a look after clearing");

			// every new sentence a player reads
			Language lang = Language.getInstance();
			List<String> missing = new ArrayList<>();
			List<String> keys = new ArrayList<>(List.of("legend.aliveworkplace.name", "legend.aliveworkplace.rarity_line", "legend.aliveworkplace.no_trade",
				"legend.aliveworkplace.or", "legend.aliveworkplace.comma", "legend.aliveworkplace.power_line", "legend.aliveworkplace.power.pace",
				"legend.aliveworkplace.power.pace_village", "legend.aliveworkplace.power.pace_everyone", "legend.aliveworkplace.power.mood",
				"legend.aliveworkplace.power.mood_village", "legend.aliveworkplace.tick", "legend.aliveworkplace.cross", "legend.aliveworkplace.need.home",
				"legend.aliveworkplace.need.luxury", "legend.aliveworkplace.need.happy", "legend.aliveworkplace.strike", "legend.aliveworkplace.guest",
				"legend.aliveworkplace.guest_until", "legend.aliveworkplace.way.none", "legend.aliveworkplace.way.visit.festival_crowd",
				"legend.aliveworkplace.way.visit.other", "legend.aliveworkplace.way.found.other", "legend.aliveworkplace.way.born",
				"legend.aliveworkplace.way.inspired", "legend.aliveworkplace.way.inspired.founder", "advice.aliveworkplace.legend",
				"advice.aliveworkplace.legend.how"));
			for (String l : List.of("wine", "jewels", "books", "clothes")) {
				keys.add("legend.aliveworkplace.luxury." + l);
			}
			for (String p : List.of("inn", "market", "festival", "chapel", "hall")) {
				keys.add("legend.aliveworkplace.way.visit." + p);
			}
			for (String s : List.of("ruined_portal", "outpost", "shipwreck", "hermit_hut")) {
				keys.add("legend.aliveworkplace.way.found." + s);
			}
			for (String s : List.of("tab", "here", "hint", "title", "about", "mythic", "none", "since", "zombie", "grave", "dead", "how", "item", "needs",
				"needs_nothing", "likes", "powers", "lives_in", "ready")) {
				keys.add("screen.aliveworkplace.legends." + s);
			}
			for (String c : List.of("legend_here.0", "legend_here.1", "legend_guest.0", "legend_guest.1", "legend_strike.0", "legend_self.0")) {
				keys.add("chatter.aliveworkplace." + c);
			}
			for (String k : keys) {
				if (!lang.has(k)) {
					missing.add(k);
				}
			}
			helper.assertTrue(missing.isEmpty(), "missing sentences: " + missing);
			helper.succeed();
		});
	}
}
