package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
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
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 29.4 (Legends on the hall), second pass, written from the spec and aimed at what the first tests leave out:
 * the spec's own example condition ("kinds of meal in the store: 5 of 8") on a card at 0, one short, exactly at and
 * over the target, with the "What next?" tip following it; the Mythic line through every rank, at its cap, with a
 * Mythic elsewhere or in a grave; "lives in" for a Legendary in the wilds, in another dimension, and gone again once
 * their place is freed; the hall's list with a Legendary, a Mythic, a guest and a Gifted Legend; and every card and tip
 * the shipped roster shows, read for untranslated keys and raw ids.
 */
public class QaLegendsHallEdgesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final String NS = "aliveworkplace_test";
	/** Nine kinds of meal. */
	private static final List<Item> KINDS = List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.PUMPKIN_PIE, Items.COOKED_SALMON,
		Items.COOKED_CHICKEN, Items.COOKIE, Items.COOKED_MUTTON, Items.COOKED_PORKCHOP);
	private static final String READY = "This village has all they need: they may come";

	private static ResourceLocation id(String name) {
		return ResourceLocation.fromNamespaceAndPath(NS, name);
	}

	private static Legend legend(String name, String rarity, String job, String rest) {
		return titled(name, name, rarity, job, rest);
	}

	private static Legend titled(String name, String title, String rarity, String job, String rest) {
		return Legends.read(id(name), JsonParser.parseString("{\"rarity\": \"" + rarity + "\", \"job\": \"" + job + "\", \"title\": \"" + title
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

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && TextColor.fromLegacyFormat(color).equals(c.getStyle().getColor());
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

	/**
	 * The spec's own example: a Legend needing 8 kinds of meal. The card reads "Kinds of meal in the store: 0 of 8" with a
	 * cross at 0 and at 7, a tick at exactly 8 and over it at 9 (never "9 of 8" crossed, never ready below 8); the
	 * "What next?" tip names the meals while they are the one thing missing, and goes once they are there.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesMeals"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesMeals")
	public void mealKindsCardAroundTheTarget(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18; // the village is its own area: a store an earlier batch left nearby never counts
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos chestAt = new BlockPos(21, 2, 15);
		helper.setBlock(new BlockPos(20, 2, 15), Blocks.SMOKER);
		helper.setBlock(chestAt, Blocks.CHEST);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper, legend("qa_gourmet", "rare", "minecraft:farmer", ", \"conditions\": [{\"type\": \"meal_kinds\", \"count\": 8}]"));
			for (BlockPos store : VillageNeeds.store(level, hall)) {
				if (!helper.getBounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(store)) && level.getBlockEntity(store) instanceof Container leftover) {
					leftover.clearContent();
				}
			}
			Container chest = (Container) helper.getBlockEntity(chestAt);
			chest.clearContent();
			// none
			ItemStack card = card(level, hall, "qa_gourmet");
			has(helper, card, "What the village must have:");
			has(helper, card, "✘ Kinds of meal in the store: 0 of 8");
			lacks(helper, card, READY);
			helper.assertTrue(legendTips(level, hall).equals(List.of("The qa_gourmet could come: Kinds of meal in the store: 0 of 8")),
				"tip at 0 of 8 (the one condition missing): " + legendTips(level, hall));
			// one short: two stacks of one kind count once
			for (int i = 0; i < 7; i++) {
				chest.setItem(i, new ItemStack(KINDS.get(i), 2));
			}
			chest.setItem(7, new ItemStack(Items.BREAD, 5));
			card = card(level, hall, "qa_gourmet");
			has(helper, card, "✘ Kinds of meal in the store: 7 of 8");
			lacks(helper, card, READY);
			helper.assertTrue(legendTips(level, hall).equals(List.of("The qa_gourmet could come: Kinds of meal in the store: 7 of 8")),
				"tip at 7 of 8: " + legendTips(level, hall));
			// exactly
			chest.setItem(8, new ItemStack(KINDS.get(7), 1));
			card = card(level, hall, "qa_gourmet");
			has(helper, card, "✔ Kinds of meal in the store: 8 of 8");
			has(helper, card, READY);
			helper.assertTrue(legendTips(level, hall).isEmpty(), "a tip with nothing missing: " + legendTips(level, hall));
			// over
			chest.setItem(9, new ItemStack(KINDS.get(8), 1));
			card = card(level, hall, "qa_gourmet");
			has(helper, card, "✔ Kinds of meal in the store: 9 of 8");
			has(helper, card, READY);
			helper.assertTrue(legendTips(level, hall).isEmpty(), "a tip over the target: " + legendTips(level, hall));
			// and back down to none: the card follows the store, it doesn't remember
			chest.clearContent();
			has(helper, card(level, hall, "qa_gourmet"), "✘ Kinds of meal in the store: 0 of 8");
			helper.succeed();
		});
	}

	/**
	 * The Mythic line through every rank (none as a Hamlet or Village, 1 as a Town, 2 as a City), counting only Mythic
	 * Legends of this village (one elsewhere doesn't count; one in their grave still does), with neither "ready" nor a
	 * "What next?" tip for a Mythic the village has no room for, and a tip again once there is room.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesMythic"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesMythic")
	public void mythicLineThroughRanksAndCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_myth_short", "mythic", "aliveworkplace:legend", ", \"conditions\": [{\"type\": \"villagers\", \"count\": 2}]"),
				legend("qa_myth_free", "mythic", "aliveworkplace:legend", ""),
				legend("qa_myth_kept", "mythic", "aliveworkplace:legend", ""));
			villager(helper, new BlockPos(12, 2, 12), "Bo");
			LegendRecord record = LegendRecord.get(level);
			Map<VillageRanks.Rank, String> lines = Map.of(VillageRanks.Rank.HAMLET, "Mythic Legends: 0 of 0, as a Hamlet",
				VillageRanks.Rank.VILLAGE, "Mythic Legends: 0 of 0, as a Village", VillageRanks.Rank.TOWN, "Mythic Legends: 0 of 1, as a Town",
				VillageRanks.Rank.CITY, "Mythic Legends: 0 of 2, as a City");
			for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
				entity.setRank(rank);
				has(helper, card(level, hall, "qa_myth_short"), lines.get(rank));
				has(helper, card(level, hall, "qa_myth_free"), lines.get(rank));
				boolean room = rank.ordinal() >= VillageRanks.Rank.TOWN.ordinal();
				// no conditions at all, but no room as a Hamlet or Village: never "ready" there
				helper.assertTrue(lore(card(level, hall, "qa_myth_free")).contains(READY) == room,
					"ready as a " + rank + ": " + lore(card(level, hall, "qa_myth_free")));
				// one villager short: a tip only where there is room
				helper.assertTrue(legendTips(level, hall).contains("The qa_myth_short could come: Villagers: 1 of 2") == room,
					"tip as a " + rank + ": " + legendTips(level, hall));
			}

			// a Mythic of another village: not counted here
			entity.setRank(VillageRanks.Rank.TOWN);
			made.add(uuid("qa_myth_away"));
			record.settled(id("qa_myth_kept"), uuid("qa_myth_away"), level.dimension(), Optional.of(hall.offset(700, 0, 0)), Rarity.MYTHIC, "Orla", 1);
			has(helper, card(level, hall, "qa_myth_free"), "Mythic Legends: 0 of 1, as a Town");
			helper.assertTrue(legendTips(level, hall).contains("The qa_myth_short could come: Villagers: 1 of 2"), "tip with a Mythic elsewhere: " + legendTips(level, hall));

			// one of this village's own, in their grave: still holds the place, so the Town is full
			made.add(uuid("qa_myth_here"));
			record.settled(id("qa_myth_kept"), uuid("qa_myth_here"), level.dimension(), Optional.of(hall), Rarity.MYTHIC, "Pell", 2);
			record.died(uuid("qa_myth_here"), 3, Optional.of(hall.east()));
			ItemStack free = card(level, hall, "qa_myth_free");
			has(helper, free, "Mythic Legends: 1 of 1, as a Town");
			lacks(helper, free, READY);
			helper.assertTrue(legendTips(level, hall).stream().noneMatch(t -> t.startsWith("The qa_myth_short ")),
				"a tip for a Mythic a full Town has no room for: " + legendTips(level, hall));
			// even at the one missing condition met: no room, no "ready"
			villager(helper, new BlockPos(13, 2, 12), "Cy");
			lacks(helper, card(level, hall, "qa_myth_short"), READY);

			// a City has room for a second: ready and counted 1 of 2
			entity.setRank(VillageRanks.Rank.CITY);
			has(helper, card(level, hall, "qa_myth_short"), "Mythic Legends: 1 of 2, as a City");
			has(helper, card(level, hall, "qa_myth_short"), READY);
			helper.succeed();
		});
	}

	/**
	 * "lives in": a Legendary settled outside any village "lives in the wilds", one in another dimension names that
	 * dimension's village, and once the place is freed (the record forgets them) the card is an open gold Legendary again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesLivesIn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesLivesIn")
	public void livesInTheWildsAnotherDimensionAndFreed(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<UUID> made = new ArrayList<>();
		cleanUp(helper, made);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				legend("qa_wild", "legendary", "aliveworkplace:legend", ""),
				legend("qa_nether", "legendary", "aliveworkplace:legend", ""));
			LegendRecord record = LegendRecord.get(level);
			made.add(uuid("qa_wild_wren"));
			made.add(uuid("qa_nether_finn"));
			record.settled(id("qa_wild"), uuid("qa_wild_wren"), level.dimension(), Optional.empty(), Rarity.LEGENDARY, "Wren", 1);
			ServerLevel nether = level.getServer().getLevel(Level.NETHER);
			BlockPos netherHall = new BlockPos(40, 70, 40);
			record.settled(id("qa_nether"), uuid("qa_nether_finn"), Level.NETHER, Optional.of(netherHall), Rarity.LEGENDARY, "Finn", 1);

			ItemStack wild = card(level, hall, "qa_wild");
			has(helper, wild, "Wren lives in the wilds");
			helper.assertTrue(wild.is(Items.GRAY_DYE), "a taken Legendary isn't grayed: " + wild);
			lacks(helper, wild, READY);
			if (nether != null) {
				has(helper, card(level, hall, "qa_nether"), "Finn lives in " + VillageHalls.name(nether, netherHall).getString());
			}

			// the place freed: an open Legendary again, gold, ready (no conditions), no "lives in"
			record.forget(uuid("qa_wild_wren"));
			wild = card(level, hall, "qa_wild");
			helper.assertTrue(wild.is(Items.GOLD_INGOT), "a freed Legendary still grayed: " + wild);
			helper.assertTrue(lore(wild).stream().noneMatch(l -> l.contains(" lives in ")), "\"lives in\" after the place was freed: " + lore(wild));
			has(helper, wild, READY);
			helper.succeed();
		});
	}

	/**
	 * The hall's list with a Legendary, a Mythic, a guest Legend and an ordinary villager nearer the hall: every Legend
	 * first, each name "Name, Title" in gold, each rarity in words, the guest's stay; a Gifted Legend's gift in gold below.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesList")
	public void hallListEveryRarityGuestAndGifted(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper,
				titled("qa_architect", "Master Architect", "legendary", "aliveworkplace:legend", ""),
				titled("qa_founder", "Old Founder", "mythic", "aliveworkplace:legend", ""),
				titled("qa_chef", "Wandering Chef", "rare", "aliveworkplace:legend", ""));
			villager(helper, new BlockPos(15, 2, 13), "Bo");
			villager(helper, new BlockPos(14, 2, 15), "Cy");
			Villager ada = villager(helper, new BlockPos(4, 2, 4), "Ada Stonewright");
			Villager orla = villager(helper, new BlockPos(26, 2, 4), "Orla");
			Villager gil = villager(helper, new BlockPos(26, 2, 26), "Gil");
			Legends.make(level, ada, Legends.get(id("qa_architect")).orElseThrow(), "test");
			Legends.make(level, orla, Legends.get(id("qa_founder")).orElseThrow(), "test");
			ModAttachments.LEGEND.set(gil, new LegendData(id("qa_chef"), "", true, Optional.empty(), 1, 9, Map.of(), -1, -1, "visit"));
			Gifted.set(ada, AliveWorkplace.id("lucky"));
			helper.assertTrue(Gifted.of(ada) != null, "the gift didn't load: " + Gifted.all().keySet());

			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			Map<String, ItemStack> rows = new LinkedHashMap<>();
			for (int s = VillageHallScreen.FIRST_PERSON; s < VillageHallScreen.FIRST_PERSON + 3; s++) {
				ItemStack row = menu.icon(s);
				helper.assertTrue(colored(row.get(DataComponents.CUSTOM_NAME), ChatFormatting.GOLD), "row " + s + " isn't a gold Legend: " + name(row));
				rows.put(name(row).split(",")[0], row);
			}
			helper.assertTrue(rows.keySet().equals(java.util.Set.of("Ada Stonewright", "Orla", "Gil")), "Legends not the first three: " + rows.keySet());
			for (int s = VillageHallScreen.FIRST_PERSON + 3; s < VillageHallScreen.FIRST_PERSON + 5; s++) {
				helper.assertTrue(!colored(menu.icon(s).get(DataComponents.CUSTOM_NAME), ChatFormatting.GOLD), "an ordinary villager in gold: " + name(menu.icon(s)));
			}
			ItemStack adaRow = rows.get("Ada Stonewright");
			helper.assertTrue(name(adaRow).equals("Ada Stonewright, Master Architect"), "the name and title: " + name(adaRow));
			has(helper, adaRow, "Legendary Legend · no trade of their own");
			has(helper, rows.get("Orla"), "Mythic Legend · no trade of their own");
			ItemStack gilRow = rows.get("Gil");
			has(helper, gilRow, "Rare Legend · no trade of their own");
			has(helper, gilRow, "A guest of the village until day 9");
			helper.assertTrue(lore(gilRow).stream().noneMatch(l -> l.startsWith("On strike")), "a guest on strike: " + lore(gilRow));

			// the Gifted Legend: "Gifted: Lucky (...)" in gold, below their traits when they have any
			ItemLore adaLore = adaRow.get(DataComponents.LORE);
			Component gift = adaLore.lines().stream().filter(l -> l.getString().startsWith("Gifted: Lucky (")).findFirst().orElse(null);
			helper.assertTrue(gift != null, "no gift line on a Gifted Legend: " + lore(adaRow));
			helper.assertTrue(colored(gift, ChatFormatting.GOLD), "the gift isn't gold: " + gift);
			int traits = lore(adaRow).indexOf(lore(adaRow).stream().filter(l -> l.startsWith("Traits: ")).findFirst().orElse("?"));
			helper.assertTrue(traits < 0 || traits < lore(adaRow).indexOf(gift.getString()), "the gift above the traits: " + lore(adaRow));
			helper.succeed();
		});
	}

	/**
	 * Legends switched off: the "What next?" tip for a Legend one condition short goes, the hall's nether-star tab and the
	 * page say Legends are off; switched back on, the tip comes back.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesOff")
	public void legendsOffTakesTheTipAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = stage(helper, legend("qa_off", "rare", "minecraft:farmer", ", \"conditions\": [{\"type\": \"villagers\", \"count\": 3}]"));
			villager(helper, new BlockPos(12, 2, 12), "Bo");
			String tip = "The qa_off could come: Villagers: 1 of 3";
			helper.assertTrue(legendTips(level, hall).equals(List.of(tip)), "the tip while on: " + legendTips(level, hall));
			Legends.ENABLED = false;
			helper.assertTrue(legendTips(level, hall).isEmpty(), "a Legend tip while Legends are off: " + legendTips(level, hall));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			int tab = io.github.jcondedata.aliveworkplace.hall.HallPages.slot(LegendsPage.PAGE);
			helper.assertTrue(tab >= 0 && lore(menu.icon(tab)).contains("Legends are switched off (legends in the config)."),
				"the tab while off: " + (tab < 0 ? "none" : lore(menu.icon(tab))));
			Legends.ENABLED = true;
			helper.assertTrue(legendTips(level, hall).equals(List.of(tip)), "the tip back on: " + legendTips(level, hall));
			helper.succeed();
		});
	}

	private static final Pattern RAW_ID = Pattern.compile("\\b[a-z_][a-z0-9_.]*:[a-z_][a-z0-9_/.]*");
	/** A format argument left in ("%s", "%2$s", "%d"); a plain percent ("2% a day") is fine. */
	private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?[sd]");

	/**
	 * Every card and "What next?" tip the shipped roster shows a new Village reads as English: no untranslated key, no
	 * "%s" left in, no raw "namespace:path" id, every card says how they come and what the village must have.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLegendsHallEdgesRoster"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLegendsHallEdgesRoster")
	public void shippedRosterReadsInEnglish(GameTestHelper helper) {
		Leftovers.clear(helper);
		cleanUp(helper, new ArrayList<>());
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall)).forEach(Villager::discard);
			Legends.reload(level.getServer().getResourceManager());
			long shipped = Legends.all().stream().filter(l -> l.id().getNamespace().equals(AliveWorkplace.MOD_ID)).count();
			helper.assertTrue(shipped >= 5, "the shipped roster didn't load: " + shipped);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			villager(helper, new BlockPos(12, 2, 12), "Bo");
			List<String> problems = new ArrayList<>();
			List<ItemStack> cards = LegendsPage.cards(level, hall);
			for (ItemStack c : cards) {
				List<String> text = new ArrayList<>(lore(c));
				text.add(name(c));
				for (String line : text) {
					if (ARG.matcher(line).find() || line.contains("aliveworkplace.") || line.contains("rule.")
						|| RAW_ID.matcher(line).find()) {
						problems.add(name(c) + ": " + line);
					}
				}
				if (!lore(c).contains("How they come:") || !lore(c).contains("What the village must have:")) {
					problems.add(name(c) + " lacks how they come or what the village must have: " + lore(c));
				}
			}
			for (VillageAdvice.Tip tip : VillageAdvice.tips(level, hall)) {
				if (!tip.key().equals("legend")) {
					continue;
				}
				for (String line : List.of(tip.title().getString(), tip.how().getString())) {
					if (ARG.matcher(line).find() || line.contains("aliveworkplace.") || line.contains("rule.") || RAW_ID.matcher(line).find()) {
						problems.add("tip: " + line);
					}
				}
			}
			helper.assertTrue(cards.size() >= shipped, "fewer cards than Legends: " + cards.size() + " of " + shipped);
			helper.assertTrue(problems.isEmpty(), "unreadable lines: " + problems);
			helper.succeed();
		});
	}
}
