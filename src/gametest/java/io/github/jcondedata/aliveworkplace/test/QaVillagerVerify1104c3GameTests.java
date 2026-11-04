package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.wonder.LighthousePowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane C, 2026-11-04, round 3): independent checks of seven landed fixes, written from their specs and going past
 * the reported case. B878/B916 (crafter-family lines name many things in the plural, one in the singular), B906/B911
 * (a hall visitor handed a job item is refused whatever blocks are near, and keeps the item), B914 (one pair of quotes
 * round an asker's line), B917 (a reform step's part-progress carries over the story switched off and on) and B919 (the
 * Defense page's idle watch line fits its source).
 */
public class QaVillagerVerify1104c3GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- B878 / B916: counted crafter lines ---------------------------------------------------------------------------

	/** What a worker shows above the head, every line seen, collected each tick. */
	static Set<String> lines(GameTestHelper helper, Villager v) {
		Set<String> lines = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(v, helper.getLevel().getGameTime());
			if (e != null && e.line() != null) {
				lines.add(e.line().getString());
			}
		});
		return lines;
	}

	/**
	 * A Chef with the makings for two pumpkin pies (and no more): "fetching what 2 Pumpkin Pies take", "cooking 2
	 * Pumpkin Pies", and any putting-away line in the plural too; never the singular after 2.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaVv1104c3ChefPies"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaVv1104c3ChefPies")
	public void aChefCookingTwoPiesNamesThemInThePlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos stove = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.PUMPKIN, 2));
		chest.setItem(1, new ItemStack(Items.SUGAR, 2));
		chest.setItem(2, new ItemStack(Items.EGG, 2));
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		Set<String> seen = lines(helper, chef);
		String pies = Words.itemName(Items.PUMPKIN_PIE, 2).getString();
		helper.succeedWhen(() -> {
			helper.assertTrue(chest.countItem(Items.PUMPKIN_PIE) == 2, "setup: " + chest.countItem(Items.PUMPKIN_PIE) + " pies cooked, expected 2");
			helper.assertTrue(pies.equals("Pumpkin Pies"), "the plural is '" + pies + "'");
			helper.assertTrue(seen.contains("fetching what 2 Pumpkin Pies take"), "want 'fetching what 2 Pumpkin Pies take'; lines seen: " + seen);
			helper.assertTrue(seen.contains("cooking 2 Pumpkin Pies"), "want 'cooking 2 Pumpkin Pies'; lines seen: " + seen);
			for (String l : seen) {
				helper.assertTrue(!l.contains("Pumpkin Pie ") && !l.endsWith("Pumpkin Pie") && !l.contains("aliveworkplace."), "two pies named as one: \"" + l + "\"");
				if (l.startsWith("putting") && !l.equals("putting things away")) {
					helper.assertTrue(l.equals("putting 2 Pumpkin Pies away"), "the putting-away line: \"" + l + "\"");
				}
			}
		});
	}

	/** A Weaponsmith making the one sword a guard needs: every line names one Iron Sword, never "Iron Swords". */
	//$ gametest_ticks_batch BIG '1600' '"qaVv1104c3OneSword"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qaVv1104c3OneSword")
	public void aWeaponsmithMakingOneSwordReadsInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		BlockPos grindstone = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		BlockPos post = new BlockPos(19, 2, 19);
		BlockPos postChest = new BlockPos(19, 2, 17);
		helper.setBlock(grindstone, Blocks.GRINDSTONE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(grindstone), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.IRON_INGOT, 2));
		chest.setItem(1, new ItemStack(Items.STICK, 1));
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(postChest, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		Container guardsChest = helper.getBlockEntity(postChest);
		Set<String> seen = lines(helper, smith);
		helper.succeedWhen(() -> {
			boolean got = guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD) || guardsChest.countItem(Items.IRON_SWORD) == 1;
			helper.assertTrue(got, "setup: no sword for the guard");
			helper.assertTrue(seen.contains("fetching what 1 Iron Sword takes"), "want 'fetching what 1 Iron Sword takes'; lines seen: " + seen);
			helper.assertTrue(seen.contains("making 1 Iron Sword"), "want 'making 1 Iron Sword'; lines seen: " + seen);
			for (String l : seen) {
				helper.assertTrue(!l.contains("Iron Swords") && !l.endsWith(" take") && !l.contains("aliveworkplace."), "one sword read as many: \"" + l + "\"");
				if (l.startsWith("taking")) {
					helper.assertTrue(l.equals("taking 1 Iron Sword to the guard"), "the delivering line: \"" + l + "\"");
				}
			}
		});
	}

	/**
	 * Data-driven: every luxury recipe's own output (made in batches, "making 2 Amethyst Rings"), the Fletcher's arrows
	 * (always 8) and every tool and sword the Toolsmith and Weaponsmith make has a plural for a count above one that
	 * differs from its one-form, or is a word that is already plural ("Fine Clothes", "Noble Robes"); 1 stays the name.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyCrafterOutputHasAPluralForMany(GameTestHelper helper) {
		Set<Item> items = new LinkedHashSet<>();
		for (LuxuryRecipes.Recipe r : LuxuryRecipes.all().values()) {
			if (r.id().getNamespace().equals(AliveWorkplace.MOD_ID)) { // the mod's own recipes, not the GameTests' data pack
				items.add(r.output());
			}
		}
		helper.assertTrue(items.contains(ModItems.AMETHYST_RING) && items.size() >= 10, "setup: luxury outputs " + items);
		items.addAll(List.of(Items.ARROW, Items.SPECTRAL_ARROW,
			Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.IRON_AXE, Items.STONE_AXE, Items.IRON_SHOVEL, Items.STONE_SHOVEL,
			Items.IRON_HOE, Items.STONE_HOE, Items.SHEARS, Items.FISHING_ROD,
			Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE,
			Items.IRON_SWORD, Items.STONE_SWORD, Items.BOW, Items.CROSSBOW));
		List<String> wrong = new ArrayList<>();
		for (Item item : items) {
			String one = item.getDescription().getString();
			String many = Words.itemName(item, 2).getString();
			String zero = Words.itemName(item, 0).getString();
			if (!Words.itemName(item, 1).getString().equals(one)) {
				wrong.add(one + ": the one-form reads '" + Words.itemName(item, 1).getString() + "'");
			}
			boolean alreadyPlural = one.endsWith("Clothes") || one.endsWith("Robes");
			if (many.equals(one) && !alreadyPlural) {
				wrong.add(BuiltInRegistries.ITEM.getKey(item) + ": '2 " + many + "'");
			}
			if (!zero.equals(many)) {
				wrong.add(one + ": 0 reads '" + zero + "', 2 reads '" + many + "'");
			}
			if (many.contains("aliveworkplace.")) {
				wrong.add("raw key: " + many);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "named as one after a count above one: " + wrong);
		helper.succeed();
	}

	// ---- B906 / B911: a visitor handed a job item --------------------------------------------------------------------

	private static final BlockPos ITEM_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos SPOT = new BlockPos(13, 2, 14);
	private static final BlockPos CUTTER = new BlockPos(14, 2, 13);

	private static String cant(Villager v) {
		return Component.translatable("message.aliveworkplace.hall.jobs.cant", v.getDisplayName()).getString();
	}

	private static boolean sentAway(List<String> seen) {
		return seen.stream().anyMatch(s -> s.contains(" job needs a "));
	}

	private static void stand(GameTestHelper helper, Villager v, BlockPos rel) {
		v.setNoAi(true);
		BlockPos at = helper.absolutePos(rel);
		v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	/**
	 * A relief rider with no Stonecutter anywhere near, sneak-clicked (the real packet) with a stack of five clay: told his
	 * job can't be changed here (not sent to find a Stonecutter), still a Guard, and the five clay still in the hand.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVv1104c3RiderNoBlock"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVv1104c3RiderNoBlock")
	public void aReliefRiderWithNoBlockNearIsRefusedAndKeepsTheClay(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		Villager rider = QaHallScreenGameTests.villager(helper, "Rider", SPOT);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			rider.setVillagerData(rider.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
			rider.setVillagerXp(70);
			BlockPos hall = helper.absolutePos(ITEM_HALL);
			ModAttachments.RELIEF_RIDER.set(rider, new Relief.Rider(hall, GlobalPos.of(helper.getLevel().dimension(), hall)));
			seen.clear();
			StationsSpecGameTests.rightClick(player, rider, new ItemStack(Items.CLAY_BALL, 5), true);
			helper.assertTrue(rider.getVillagerData().getProfession() == ModVillagers.GUARD, "the rider's job changed: " + rider.getVillagerData());
			helper.assertTrue(seen.contains(cant(rider)), "with no Stonecutter near, the rider handed clay was told: " + seen);
			helper.assertFalse(sentAway(seen), "the player was sent to fetch a block: " + seen);
			ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
			helper.assertTrue(hand.is(Items.CLAY_BALL) && hand.getCount() == 5, "the clay was used up on a refusal: " + hand);
			helper.succeed();
		});
	}

	/**
	 * Doctor Wren (the Sickness story's Cleric, at her brewing stand) handed clay beside a Stonecutter a Mason already
	 * works at: refused as a visitor, not told the Stonecutter is taken; she stays the Cleric and the Mason keeps it.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVv1104c3WrenTaken"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVv1104c3WrenTaken")
	public void doctorWrenByATakenStonecutterIsRefusedNotToldItIsTaken(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos stand = new BlockPos(12, 2, 15);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager wren = QaHallScreenGameTests.villager(helper, "Doctor Wren", SPOT);
		wren.addTag(Arcs.MOB_TAG);
		Villager mason = QaHallScreenGameTests.villager(helper, "Mo", new BlockPos(15, 2, 12));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Jobs.employ(level, wren, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
			wren.setVillagerData(wren.getVillagerData().setLevel(3));
			Jobs.employ(level, mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(CUTTER)) == 0, "setup: the Stonecutter isn't taken");
			seen.clear();
			StationsSpecGameTests.rightClick(player, wren, new ItemStack(Items.CLAY_BALL, 3), true);
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC, "Doctor Wren's job changed: " + wren.getVillagerData());
			boolean atStand = wren.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(stand))).orElse(false);
			helper.assertTrue(atStand, "Doctor Wren lost her brewing stand");
			helper.assertTrue(seen.contains(cant(wren)), "by a taken Stonecutter, Doctor Wren handed clay was told: " + seen);
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("someone already works")), "the player was told the block is taken: " + seen);
			helper.assertTrue(mason.getVillagerData().getProfession() == VillagerProfession.MASON
				&& mason.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(CUTTER))).orElse(false),
				"the Mason lost his Stonecutter");
			ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
			helper.assertTrue(hand.is(Items.CLAY_BALL) && hand.getCount() == 3, "the clay was used up on a refusal: " + hand);
			helper.succeed();
		});
	}

	/**
	 * The Builder's item: a hired mercenary handed a Blank Blueprint is refused with no Blueprint Table near and again
	 * beside a free one, keeps his job, pays nothing and keeps the blueprint; the table stays free. The control: an
	 * ordinary jobless villager handed one by the same table becomes its Builder.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVv1104c3MercBlueprint"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVv1104c3MercBlueprint")
	public void aMercenaryHandedABlueprintIsRefusedWithOrWithoutATable(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(ITEM_HALL);
			Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			Leftovers.after(helper, () -> band.forEach(Villager::discard));
			helper.assertTrue(band.size() == Mercenaries.BAND, "setup: band " + band.size());
			band.forEach(v -> v.setNoAi(true));
			Villager merc = band.get(0);
			stand(helper, merc, SPOT);
			VillagerProfession was = merc.getVillagerData().getProfession();
			int money = player.getInventory().countItem(Items.EMERALD);
			seen.clear();
			StationsSpecGameTests.rightClick(player, merc, new ItemStack(ModItems.BLANK_BLUEPRINT), true);
			helper.assertTrue(seen.contains(cant(merc)), "no table near: the mercenary handed a Blank Blueprint was told " + seen);
			helper.assertFalse(sentAway(seen), "no table near: the player was sent to fetch a Blueprint Table: " + seen);
			helper.setBlock(CUTTER, ModBlocks.BLUEPRINT_TABLE);
			seen.clear();
			StationsSpecGameTests.rightClick(player, merc, new ItemStack(ModItems.BLANK_BLUEPRINT), true);
			helper.assertTrue(seen.contains(cant(merc)), "by a free table: the mercenary handed a Blank Blueprint was told " + seen);
			helper.assertTrue(merc.getVillagerData().getProfession() == was && Mercenaries.isMercenary(merc), "the mercenary became a " + merc.getVillagerData().getProfession());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == money, "paid " + (money - player.getInventory().countItem(Items.EMERALD)) + " emeralds");
			ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
			helper.assertTrue(hand.is(ModItems.BLANK_BLUEPRINT) && hand.getCount() == 1, "the blueprint was used up on a refusal: " + hand);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(CUTTER)) == 1, "the free Blueprint Table was taken");
			// the control: an ordinary jobless villager by the same table takes it
			Villager ada = QaHallScreenGameTests.villager(helper, "Ada", new BlockPos(15, 2, 12));
			seen.clear();
			StationsSpecGameTests.rightClick(player, ada, new ItemStack(ModItems.BLANK_BLUEPRINT), true);
			helper.assertTrue(ada.getVillagerData().getProfession() == ModVillagers.BUILDER,
				"an ordinary villager by a free Blueprint Table handed a Blank Blueprint is a " + ada.getVillagerData().getProfession() + "; told " + seen);
			helper.succeed();
		});
	}

	// ---- B914: one pair of quotes ------------------------------------------------------------------------------------

	private static final BlockPos REFORM_HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	/**
	 * Every line a story's villager says in the "%s: "%s"" format (all eight reform stories' asks, and any other
	 * {@code says} line in the lang) reads with exactly one pair of quotes; and the Common Granary's Farmer, talked to
	 * through the story's own path, says his line once in one pair of quotes.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3Quotes"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3Quotes")
	public void everyAskersLineHasExactlyOnePairOfQuotes(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager farmer = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.FARMER);
		helper.runAfterDelay(2, () -> {
			List<String> wrong = new ArrayList<>();
			int checked = 0;
			for (String key : enUs().keySet()) {
				if (!key.startsWith("arc.aliveworkplace.") || !(key.endsWith(".says") || key.endsWith("_says"))) {
					continue;
				}
				checked++;
				String line = Component.translatable("message.aliveworkplace.arc.says", "Ada", Component.translatable(key)).getString();
				long quotes = line.chars().filter(c -> c == '"').count();
				if (quotes != 2 || !line.startsWith("Ada: \"") || !line.endsWith("\"") || line.startsWith("Ada: \"\"")) {
					wrong.add(key + " -> " + line);
				}
			}
			for (String arc : List.of("shift_bell", "common_granary", "midwives", "watchful_gate", "festival_fund", "fair_ledger", "lamplighters", "militia_drill")) {
				if (!enUs().containsKey("arc.aliveworkplace." + arc + ".says")) {
					wrong.add("no says line for " + arc);
				}
			}
			helper.assertTrue(checked >= 9 && wrong.isEmpty(), checked + " says lines; with other than one pair of quotes: " + wrong);
			// the Common Granary's ask, through the player's talk
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(REFORM_HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			List<String> heard = new ArrayList<>(); // each chat line once (displayClientMessage, as the player's screen shows it)
			ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, heard);
			Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("free_bread").toString(), day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			helper.assertTrue(QaReformStoryGameTests.asks(farmer), "setup: the Farmer didn't ask");
			heard.clear();
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, farmer);
			String who = farmer.getDisplayName().getString();
			List<String> said = heard.stream().filter(s -> s.startsWith(who + ":")).toList();
			String expected = who + ": \"" + Component.translatable("arc.aliveworkplace.common_granary.says").getString() + "\"";
			helper.assertTrue(said.equals(List.of(expected)), "the Farmer said " + said + ", expected [" + expected + "]");
			helper.succeed();
		});
	}

	// ---- B917: a step's part-progress carries over -------------------------------------------------------------------

	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final String FREE_BREAD = AliveWorkplace.id("free_bread").toString();

	private static List<VillageQuests.Quest> steps(VillageHallBlockEntity entity, String edict) {
		return entity.quests().stream().filter(q -> q.reform().map(r -> r.edict().equals(edict)).orElse(false)).toList();
	}

	private static List<Quest> story(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static long startDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long before = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(before));
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
		return day;
	}

	private static ServerPlayer reformPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(REFORM_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	private static void give(ServerPlayer player, Item item, int n) {
		while (n > 0) {
			int stack = Math.min(n, item.getDefaultMaxStackSize());
			player.getInventory().add(new ItemStack(item, stack));
			n -= stack;
		}
	}

	/** Two days of the hall's rounds (M30's and the stories'), from {@code day}. */
	private static void rounds(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day) {
		for (int r = 0; r < 2; r++) {
			level.setDayTime((day + r) * VillageNeeds.DAY + 1000);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			Arcs.round(level, hall, entity);
		}
	}

	/**
	 * The edge: 23 of the Shift Bell's 24 clocks handed to the story, then storyArcs off (the game rule's switch, not
	 * disabledArcs): M30's step 1 holds the 23 and asks one more; that one clock finishes the step, and step 2 (the gold)
	 * starts from nothing, the extra not carried into it.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3CarryAllButOne"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3CarryAllButOne")
	public void allButOneClockGivenToTheStoryCarryOverAndOneMoreFinishesTheStep(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		boolean enabled = Arcs.ENABLED;
		Leftovers.after(helper, () -> Arcs.ENABLED = enabled);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(REFORM_HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = reformPlayer(helper);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			List<Quest> told = story(level, hall);
			helper.assertTrue(told.size() == 1, "setup: " + told.size() + " story quests up after the ask");
			give(player, Items.CLOCK, 23);
			helper.assertTrue(Stories.handIn(player, hall, told.get(0).id) == 23, "setup: 23 clocks not taken by the story");
			Arcs.ENABLED = false;
			rounds(level, hall, entity, day);
			List<VillageQuests.Quest> m30 = steps(entity, LONG_SHIFTS);
			helper.assertTrue(m30.size() == 1 && m30.get(0).reform().get().step() == 0, "setup: M30's step 1 isn't up with storyArcs off: " + m30);
			helper.assertTrue(m30.get(0).count() == 24 && m30.get(0).progress() == 23,
				"M30's step 1 after 23 clocks given to the story: " + m30.get(0).progress() + " of " + m30.get(0).count());
			give(player, Items.CLOCK, 5);
			helper.assertTrue(VillageQuests.handIn(player, hall, m30.get(0).id()) == 1, "the last clock: the step took more or less than the one it lacked");
			helper.assertTrue(player.getInventory().countItem(Items.CLOCK) == 4, "clocks left with the player: " + player.getInventory().countItem(Items.CLOCK));
			rounds(level, hall, entity, day + 2);
			helper.assertTrue(Reforms.progress(entity, LONG_SHIFTS).step() >= 1, "step 1 not done after its 24th clock: " + Reforms.progress(entity, LONG_SHIFTS));
			List<VillageQuests.Quest> next = steps(entity, LONG_SHIFTS);
			helper.assertTrue(next.size() == 1 && next.get(0).reform().get().step() == 1 && next.get(0).progress() == 0,
				"step 2 should start from nothing: " + next);
			helper.succeed();
		});
	}

	/**
	 * Toggled twice: 10 clocks to the story, the story switched off by name (M30's step holds 10), 4 more handed on
	 * M30's step (14), then the story switched on again: step 1, whichever way it is up, holds 14 and asks the 10 left.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3CarryTwice"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3CarryTwice")
	public void halfAStepCarriesOverTheStorySwitchedOffAndOnAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager smith = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.TOOLSMITH);
		Set<String> off = Arcs.DISABLED;
		Leftovers.after(helper, () -> Arcs.DISABLED = off);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(REFORM_HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = reformPlayer(helper);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			List<Quest> told = story(level, hall);
			helper.assertTrue(told.size() == 1, "setup: " + told.size() + " story quests up after the ask");
			give(player, Items.CLOCK, 10);
			helper.assertTrue(Stories.handIn(player, hall, told.get(0).id) == 10, "setup: 10 clocks not taken by the story");
			Arcs.DISABLED = Set.of("shift_bell");
			rounds(level, hall, entity, day);
			List<VillageQuests.Quest> m30 = steps(entity, LONG_SHIFTS);
			helper.assertTrue(m30.size() == 1 && m30.get(0).progress() == 10, "story off: M30's step 1 should hold the 10 clocks: " + m30);
			give(player, Items.CLOCK, 4);
			helper.assertTrue(VillageQuests.handIn(player, hall, m30.get(0).id()) == 4, "setup: 4 more not handed in on M30's step");
			Arcs.DISABLED = Set.of();
			level.setDayTime((day + 2) * VillageNeeds.DAY + 1000);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, smith);
			Arcs.round(level, hall, entity);
			List<Quest> again = story(level, hall);
			List<VillageQuests.Quest> left = steps(entity, LONG_SHIFTS);
			helper.assertTrue(again.size() + left.size() == 1, again.size() + " story quests and " + left.size() + " M30 steps up for step 1");
			int held = again.isEmpty() ? left.get(0).progress() : again.get(0).progress[0];
			helper.assertTrue(held == 14, "story on again: step 1 holds " + held + " of the 14 clocks handed in ("
				+ (again.isEmpty() ? "M30's step" : "the story's step") + ")");
			helper.succeed();
		});
	}

	/**
	 * Another reform both ways: the Free Bread's 512 wheat. 200 handed on M30's step with storyArcs off; switched on, the
	 * Common Granary's step 1 holds the 200; switched off again, M30's step 1 still holds them.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3CarryGranary"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3CarryGranary")
	public void theFreeBreadsWheatCarriesOverBothWays(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager farmer = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.FARMER);
		boolean enabled = Arcs.ENABLED;
		Leftovers.after(helper, () -> Arcs.ENABLED = enabled);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(REFORM_HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer player = reformPlayer(helper);
			long day = startDay(helper);
			entity.setEdicts(List.of(new Edicts.InForce(FREE_BREAD, day)));
			Arcs.ENABLED = false;
			QaReformStoryGameTests.autoRound(level, hall, entity);
			List<VillageQuests.Quest> m30 = steps(entity, FREE_BREAD);
			helper.assertTrue(m30.size() == 1 && m30.get(0).count() == 512, "setup: M30's step 1 isn't up with storyArcs off: " + m30);
			give(player, Items.WHEAT, 200);
			helper.assertTrue(VillageQuests.handIn(player, hall, m30.get(0).id()) == 200, "setup: 200 wheat not handed in on M30's step");
			Arcs.ENABLED = true;
			level.setDayTime(day * VillageNeeds.DAY + 1000);
			QaReformStoryGameTests.autoRound(level, hall, entity);
			ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, farmer);
			Arcs.round(level, hall, entity);
			List<Quest> told = story(level, hall);
			List<VillageQuests.Quest> left = steps(entity, FREE_BREAD);
			helper.assertTrue(told.size() + left.size() == 1, "story on: " + told.size() + " story quests and " + left.size() + " M30 steps up for step 1");
			int held = told.isEmpty() ? left.get(0).progress() : told.get(0).progress[0];
			helper.assertTrue(held == 200, "story on: step 1 holds " + held + " of the 200 wheat");
			Arcs.ENABLED = false;
			rounds(level, hall, entity, day + 1);
			List<VillageQuests.Quest> back = steps(entity, FREE_BREAD);
			helper.assertTrue(back.size() == 1 && back.get(0).reform().get().step() == 0 && back.get(0).progress() == 200,
				"story off again: M30's step 1 should hold the 200 wheat: " + back);
			helper.succeed();
		});
	}

	// ---- B919: the Defense page's idle watch line --------------------------------------------------------------------

	private static List<String> lore(ItemStack icon) {
		return QaLaneD1104GameTests.lore(icon);
	}

	/** A Seer alone: "<Seer> sees raids in visions, 2 days before they come", word for word, and no tower. */
	//$ gametest_ticks_batch BIG '100' '"qaVv1104c3SeerLine"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaVv1104c3SeerLine")
	public void theDefensePageGivesTheSeerTheirVisionsTwoDaysAhead(GameTestHelper helper) {
		QaLaneD1104GameTests.SeerVillage v = QaLaneD1104GameTests.seerVillage(helper);
		ItemStack next = DefencePage.next(v.level(), v.hall());
		List<String> lines = lore(next);
		String want = Component.translatable("screen.aliveworkplace.defence.next_watch_seer", v.seer().getName(), 2).getString();
		helper.assertTrue(next.getHoverName().getString().equals("No attack seen coming"), "the page: " + next.getHoverName().getString());
		helper.assertTrue(want.equals(v.seer().getName().getString() + " sees raids in visions, 2 days before they come"), "the lang line: " + want);
		helper.assertTrue(lines.contains(want), "want '" + want + "'; the page says " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("tower") || l.contains("aliveworkplace.")), "the page: " + lines);
		helper.succeed();
	}

	/**
	 * A guard posted by a finished Wall Tower (no Seer, no Lighthouse): the default line, "<guard> keeps watch from a tower:
	 * a raid is seen a day ahead". With the guard gone, the page says nobody watches.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3TowerLine"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3TowerLine")
	public void theDefensePageKeepsTheTowerLineForATowerGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 24;
		BlockPos hallRel = new BlockPos(15, 2, 26);
		BlockPos hall = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
		});
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ThreatData.get(level).forgetClock(hall);
		BlockPos origin = helper.absolutePos(new BlockPos(21, 2, 5));
		level.getStructureManager().get(StarterBlueprints.WALL_TOWER.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.WALL_TOWER.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		BlockPos post = new BlockPos(16, 2, 16);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 14));
		guard.setNoAi(true);
		Jobs.employ(level, guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ItemStack next = DefencePage.next(level, hall);
		List<String> lines = lore(next);
		String want = Component.translatable("screen.aliveworkplace.defence.next_watch", guard.getName()).getString();
		helper.assertTrue(want.equals(guard.getName().getString() + " keeps watch from a tower: a raid is seen a day ahead"), "the lang line: " + want);
		helper.assertTrue(next.getHoverName().getString().equals("No attack seen coming"), "the page: " + next.getHoverName().getString());
		helper.assertTrue(lines.contains(want), "want '" + want + "'; the page says " + lines);
		guard.discard();
		helper.assertTrue(DefencePage.next(level, hall).getHoverName().getString().equals("No lookout"),
			"with the guard gone: " + DefencePage.next(level, hall).getHoverName().getString());
		helper.succeed();
	}

	/**
	 * A village with a Lighthouse (early warning) and pirates on the clock, not yet warned of: the page names the keeper in
	 * the Lighthouse's own line, not on a tower. With a raid by land on the clock instead, the Lighthouse watches nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaVv1104c3LighthouseLine"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaVv1104c3LighthouseLine")
	public void theDefensePageGivesTheLighthouseKeeperTheirOwnLine(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hallRel = new BlockPos(15, 2, 15);
		BlockPos hall = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(AliveWorkplace.id("lighthouse"), hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(begun.id(), 7);
		Leftovers.after(helper, () -> {
			manager.remove(begun.id());
			Wonders.forgetPowers();
		});
		WonderSite site = manager.site(begun.id()).orElseThrow();
		ResourceLocation pirates = AliveWorkplace.id("drowned_pirates");
		helper.assertTrue(Threats.get(pirates).map(c -> c.arrival() == Culture.Arrival.SHORE).orElse(false), "setup: no drowned pirates");
		ThreatData data = ThreatData.get(level);
		long base = 50 * VillageNeeds.DAY;
		level.setDayTime(base + 3000); // morning of day 51
		data.forgetClock(hall);
		data.rolled(hall, 51, new ThreatData.Attack(53, pirates, Math.PI / 2, 13600));
		ItemStack next = DefencePage.next(level, hall);
		List<String> lines = lore(next);
		Component keeper = Component.translatable("message.aliveworkplace.lighthouse.keeper", LighthousePowers.postName(level, site));
		String want = Component.translatable("screen.aliveworkplace.defence.next_watch_lighthouse", keeper).getString();
		helper.assertTrue(next.getHoverName().getString().equals("No attack seen coming"), "the page: " + next.getHoverName().getString());
		helper.assertTrue(lines.contains(want), "want '" + want + "'; the page says " + lines);
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("from a tower") || l.contains("aliveworkplace.")), "the page: " + lines);
		Culture land = Threats.all().stream().filter(c -> c.arrival() != Culture.Arrival.SHORE).findFirst().orElseThrow();
		data.forgetClock(hall);
		data.rolled(hall, 51, new ThreatData.Attack(53, land.id(), Math.PI / 2, 13600));
		helper.assertTrue(DefencePage.next(level, hall).getHoverName().getString().equals("No lookout"),
			"a raid by land with only the Lighthouse: " + lore(DefencePage.next(level, hall)) + " / " + DefencePage.next(level, hall).getHoverName().getString());
		helper.succeed();
	}

	/** The mod's en_us.json as read from the jar. */
	private static java.util.Map<String, String> enUs() {
		try (var in = QaVillagerVerify1104c3GameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
			json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
			return out;
		} catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
