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
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane C, 2026-11-11) verifying the village fixes B1176, B1108, B1109 and B1090 from their bugs' "Expected", for the
 * corners their own tests leave out:
 * <ul>
 * <li>B1176 (32.14, 32.6): the hall moved (not broken) during a siege with its raider still alive; a bar spot the player
 * filled with another block isn't dug out when the hall goes; another village's braced bars stay down; raids switched
 * off after the bars dropped.</li>
 * <li>B1108, B1109: every Storehouse board sentence for the Nurse's, Undertaker's, Physician's and Composter's requests,
 * rendered with real arguments and compared word for word; and a real click on the board.</li>
 * <li>B1090 (42.3): the boundary one level below the top (a click still chooses it), a topic at its top while another is
 * being researched (fully researched, not "busy"), and the Great Library's row below its second tier's top (needs the
 * library, not "fully researched").</li>
 * </ul>
 */
public class QaVillageBugs1111cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_hall_gone_1111c_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 26);
	private static final BlockPos MOVED = new BlockPos(16, 2, 26);
	/** A second village's hall spot (no block: its clock only), for bars that aren't this hall's. */
	private static final BlockPos OTHER = new BlockPos(3, 2, 26);
	private static final BlockPos GATEHOUSE = new BlockPos(9, 2, 5);
	private static final BlockPos TOWER = new BlockPos(21, 2, 5);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	// ---- B1176: the warned day's portcullis when the hall goes ------------------------------------------------------

	/** The warned village (as QaWarnedHallGone1111dBugTests sets it up), braced at noon of the warned day. Returns the bars. */
	private static List<BlockPos> bracedAtNoon(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true;
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos moved = helper.absolutePos(MOVED);
		BlockPos other = helper.absolutePos(OTHER);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			for (BlockPos h : List.of(hall, moved, other)) {
				Sieges.siege(level, h).ifPresent(s -> Sieges.lift(level, s));
				Sieges.raise(level, h, false);
				VillageRaids.forget(h);
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
		ThreatData.get(level).forgetClock(moved);
		guard(helper, 16, 14);
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(Warnings.warned(level, hall).isPresent(), "setup: the village wasn't warned");
		level.setDayTime(BASE + VillageNeeds.DAY + Warnings.NOON + 100);
		Gates.round(level, hall, 1);
		Warnings.round(level, hall, 10, 1);
		List<BlockPos> bars = List.copyOf(ThreatData.get(level).braced(hall));
		helper.assertTrue(bars.size() >= 2 && down(level, bars) == bars.size(), "setup: the portcullis didn't drop at noon of the warned day: " + bars.size());
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

	/**
	 * The other path of the siege: the hall moved one block east (not broken) during the siege, its raider still alive.
	 * The raid ends there and then (the raider leaves, the clock has no raid), and the next morning the siege on the old
	 * hall's spot is lifted and no bar is down, while the moved hall's own clock holds no siege.
	 */
	//$ gametest_ticks_batch HUGE '300' '"qaHallMovedSiege1111c"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "qaHallMovedSiege1111c")
	public void aHallMovedDuringASiegeEndsTheRaidAndLiftsTheSiegeNextMorning(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos moved = helper.absolutePos(MOVED);
		level.setDayTime(BASE + VillageNeeds.DAY + VillageRaids.DUSK + 1500);
		Mob husk = EntityType.HUSK.create(level);
		BlockPos out = helper.absolutePos(new BlockPos(14, 2, 1));
		husk.moveTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5, 0f, 0f);
		husk.addTag(VillageRaids.TAG);
		husk.setNoAi(true);
		level.addFreshEntity(husk);
		VillageRaids.track(level, hall, Threats.get(BESIEGERS).orElseThrow(), List.of(husk));
		ThreatData.Siege siege = Sieges.siege(level, hall).orElse(null);
		helper.assertTrue(siege != null && !siege.portcullis.isEmpty() && Sieges.portcullisDown(level, hall), "setup: the siege didn't begin with the bars down");
		List<BlockPos> held = List.copyOf(siege.portcullis);
		helper.setBlock(HALL, Blocks.AIR); // a Mover picks the hall up ...
		helper.setBlock(MOVED, ModBlocks.VILLAGE_HALL); // ... and sets it down a block east
		helper.assertTrue(ThreatData.get(level).raid(hall).isEmpty(), "the hall was moved during the raid, and the raid is still on the old hall's clock");
		helper.assertTrue(husk.isRemoved(), "the hall was moved during the raid, and its raider is still there");
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
		helper.runAfterDelay(80, () -> {
			int still = down(level, held) + down(level, bars);
			helper.assertTrue(Sieges.siege(level, hall).isEmpty(), "the hall was moved during the siege; the next morning the siege on its old spot still holds");
			helper.assertTrue(still == 0, "the hall was moved during the siege; the next morning " + still + " portcullis bars are still down");
			helper.assertTrue(Sieges.siege(level, moved).isEmpty() && ThreatData.get(level).raid(moved).isEmpty(), "the moved hall took the siege over");
			helper.succeed();
		});
	}

	/**
	 * "Draws up its braced bars": only the iron bars it dropped. A bar spot the player filled with cobblestone (or one
	 * they mined out) stays as the player left it when the hall is broken; the rest go up, and the hall's clock forgets
	 * them (a hall put down again later drops none from the old list).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaHallGoneBarsOnly1111c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaHallGoneBarsOnly1111c")
	public void aHallBrokenRaisesOnlyItsOwnIronBars(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos filled = bars.get(0);
		level.setBlock(filled, Blocks.COBBLESTONE.defaultBlockState(), 3); // the player swaps one bar for cobblestone
		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(level.getBlockState(filled).is(Blocks.COBBLESTONE), "breaking the hall dug out the cobblestone the player put in a bar's spot: "
			+ level.getBlockState(filled));
		int still = down(level, bars);
		helper.assertTrue(still == 0, "the hall was broken and " + still + " of its " + bars.size() + " bars are still down");
		helper.assertTrue(ThreatData.get(level).braced(hall).isEmpty(), "the broken hall's clock still keeps its bars: " + ThreatData.get(level).braced(hall).size());
		helper.succeed();
	}

	/**
	 * Breaking one hall touches only its own clock: a second village's portcullis, dropped for its own warned day and
	 * kept under its own hall, stays down.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaHallGoneOtherVillage1111c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaHallGoneOtherVillage1111c")
	public void anotherVillagesBarsStayDownWhenThisHallIsBroken(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		BlockPos other = helper.absolutePos(OTHER);
		List<BlockPos> theirs = List.of(helper.absolutePos(new BlockPos(2, 2, 20)), helper.absolutePos(new BlockPos(3, 2, 20)), helper.absolutePos(new BlockPos(4, 2, 20)));
		for (BlockPos p : theirs) {
			level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 3);
		}
		ThreatData.get(level).braced(other, theirs);
		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(down(level, bars) == 0, "setup: this hall's own bars didn't go up");
		helper.assertTrue(down(level, theirs) == theirs.size(), "breaking this hall drew up " + (theirs.size() - down(level, theirs)) + " of the other village's bars");
		helper.assertTrue(ThreatData.get(level).braced(other).size() == theirs.size(), "the other village's clock lost its bars");
		helper.succeed();
	}

	/** Raids switched off after the bars dropped (the config changed that afternoon): the hall broken still draws them up. */
	//$ gametest_ticks_batch HUGE '200' '"qaHallGoneRaidsOff1111c"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaHallGoneRaidsOff1111c")
	public void raidsSwitchedOffThenTheHallBrokenStillDrawsTheBarsUp(GameTestHelper helper) {
		List<BlockPos> bars = bracedAtNoon(helper);
		ServerLevel level = helper.getLevel();
		VillageRaids.ENABLED = false;
		helper.setBlock(HALL, Blocks.AIR);
		level.setDayTime(BASE + 2 * VillageNeeds.DAY + 1000);
		helper.runAfterDelay(40, () -> {
			int still = down(level, bars);
			helper.assertTrue(still == 0, "raids off, hall broken: " + still + " of " + bars.size() + " bars still down the next morning");
			helper.succeed();
		});
	}

	// ---- B1108, B1109: the Storehouse board's sentences ------------------------------------------------------------

	private static Requests.Request posted(GameTestHelper helper, VillagerProfession profession, int x, ItemStack icon, int count, String key) {
		BlockPos at = new BlockPos(x, 2, 1);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(2));
		villager.setNoAi(true);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.south())));
		Requests.post(villager, icon, count, Component.translatable(key), s -> s.is(icon.getItem()));
		List<Requests.Request> list = Requests.of(helper.getLevel(), villager);
		Requests.clear(villager);
		helper.assertTrue(list.size() == 1, "setup: the " + key + " request isn't on the worker's list");
		return list.get(0);
	}

	/**
	 * Every sentence the board says for the requests the Nurse, Undertaker, Physician and Composter post under their own
	 * names, with one, two and a stack handed over and with none, word for word, in American English.
	 */
	//$ gametest_batch BUILD '"qaBoardSentences1111c"'
	@GameTest(template = BUILD, batch = "qaBoardSentences1111c")
	public void everyNamedRequestSentenceReadsExactly(GameTestHelper helper) {
		Leftovers.clear(helper);
		Requests.Request nurse = posted(helper, ModVillagers.NURSE, 1, new ItemStack(Items.HONEY_BOTTLE), 1, "request.aliveworkplace.remedy");
		Requests.Request undertaker = posted(helper, ModVillagers.UNDERTAKER, 3, new ItemStack(Items.GOLDEN_APPLE), 1, "request.aliveworkplace.revival");
		Requests.Request physician = posted(helper, ModVillagers.PHYSICIAN, 5, new ItemStack(ModItems.REMEDY), 1, "request.aliveworkplace.remedy_physician");
		Requests.Request composter = posted(helper, ModVillagers.COMPOSTER, 7, new ItemStack(Items.ROTTEN_FLESH), 16, "request.aliveworkplace.compost");
		List<String> wrong = new ArrayList<>();
		Object[][] table = {
			{nurse, 1, "Gave a remedy (honey, milk or a healing potion) to the Nurse's chests"},
			{nurse, 2, "Gave 2 remedies (honey, milk or healing potions) to the Nurse's chests"},
			{nurse, 0, "You don't have a remedy (honey, milk or a healing potion) to give (or their chests are full)"},
			{undertaker, 1, "Gave a golden apple (or a healing potion) to the Undertaker's chests"},
			{undertaker, 2, "Gave 2 golden apples (or healing potions) to the Undertaker's chests"},
			{undertaker, 0, "You don't have a golden apple (or a healing potion) to give (or their chests are full)"},
			{physician, 1, "Gave a Remedy to the Physician's chests"},
			{physician, 3, "Gave 3 Remedies to the Physician's chests"},
			{physician, 0, "You don't have a Remedy to give (or their chests are full)"},
			{composter, 1, "Gave a scrap for composting to the Composter's chests"},
			{composter, 16, "Gave 16 scraps for composting to the Composter's chests"},
			{composter, 0, "You don't have any scraps for composting to give (or their chests are full)"},
		};
		for (Object[] row : table) {
			Requests.Request r = (Requests.Request) row[0];
			int given = (Integer) row[1];
			String text = (given == 0 ? StorehouseBoard.noneMessage(r) : StorehouseBoard.gaveMessage(r, given)).getString();
			if (!text.equals(row[2])) {
				wrong.add("'" + text + "' should read '" + row[2] + "'");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * The player's own path: an Undertaker's request for a golden apple is on the Storehouse board; a click with two
	 * golden apples in hand hands one over and the action bar reads "Gave a golden apple (or a healing potion) to the
	 * Undertaker's chests"; a click with none says "You don't have a golden apple (or a healing potion) to give (or
	 * their chests are full)".
	 */
	//$ gametest_ticks_batch BIG '100' '"qaBoardClick1111c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaBoardClick1111c")
	public void aClickOnTheBoardSaysTheUndertakersSentence(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 10);
		BlockPos table = new BlockPos(8, 2, 8);
		BlockPos chest = new BlockPos(9, 2, 8);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(table, ModBlocks.UNDERTAKERS_TABLE);
		helper.setBlock(chest, Blocks.CHEST);
		Villager undertaker = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 6));
		Jobs.employ(level, undertaker, helper.absolutePos(table), ModVillagers.UNDERTAKERS_TABLE_POI, ModVillagers.UNDERTAKER);
		undertaker.setNoAi(true);
		Requests.post(undertaker, new ItemStack(Items.GOLDEN_APPLE), 1, Component.translatable("request.aliveworkplace.revival"),
			io.github.jcondedata.aliveworkplace.grave.Graves::isRevivalItem);
		Leftovers.after(helper, () -> Requests.clear(undertaker));
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 10));
		ServerPlayer player = QaLostCaravanGameTests.listener(helper, seen, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, "QaBoard1111c");
		player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 2));
		ChoiceMenu board = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse));
		int slot = -1;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(Items.GOLDEN_APPLE)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the Undertaker's request for a golden apple isn't on the Storehouse board");
		board.press(slot, player);
		List<String> bar = QaLostCaravanGameTests.texts(seen, true);
		helper.assertTrue(bar.contains("Gave a golden apple (or a healing potion) to the Undertaker's chests"), "the click with golden apples said: " + bar);
		helper.assertTrue(player.getInventory().countItem(Items.GOLDEN_APPLE) == 1, "the board took " + (2 - player.getInventory().countItem(Items.GOLDEN_APPLE)) + " golden apples for a request of one");
		player.getInventory().clearContent();
		Requests.post(undertaker, new ItemStack(Items.GOLDEN_APPLE), 1, Component.translatable("request.aliveworkplace.revival"),
			io.github.jcondedata.aliveworkplace.grave.Graves::isRevivalItem);
		board = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse));
		slot = -1;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(Items.GOLDEN_APPLE)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the request is gone from the board after one golden apple was handed over and it was posted again");
		seen.clear();
		board.press(slot, player);
		bar = QaLostCaravanGameTests.texts(seen, true);
		helper.assertTrue(bar.contains("You don't have a golden apple (or a healing potion) to give (or their chests are full)"), "the empty-handed click said: " + bar);
		helper.succeed();
	}

	// ---- B1090: a click on a Research list row at and below its top ------------------------------------------------

	private record Desk(VillageHallBlockEntity hall, Villager scholar) {
	}

	private static Desk desk(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(14, 2, 14);
		BlockPos desk = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		scholar.setNoAi(true);
		return new Desk((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt)), scholar);
	}

	private static List<String> clicked(GameTestHelper helper, QaStageFiveGameTests.Client client, ChoiceMenu menu, int slot) {
		client.seen().clear();
		JobListMenusGameTests.click(client.player(), menu, slot);
		// (the client records each line twice: displayClientMessage passes it on to sendSystemMessage)
		return client.seen().stream().map(Component::getString).distinct().toList();
	}

	/**
	 * The boundary from below: Swift Hands at level 2 of 3 with nothing being researched; its row isn't gray and a click
	 * chooses it ("The scholars will research Swift Hands next"), never "fully researched".
	 */
	//$ gametest_ticks_batch BIG '100' '"qaResearchBelowTop1111c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaResearchBelowTop1111c")
	public void aClickOneLevelBelowTheTopChoosesTheTopic(GameTestHelper helper) {
		Desk d = desk(helper);
		helper.runAfterDelay(2, () -> {
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			d.hall().setResearch(new Research.State(Map.of(swift.key(), swift.maxLevel - 1), Optional.empty(), 0, false));
			Research.forget();
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ChoiceMenu menu = JobListMenusGameTests.open(helper, client.player(), d.scholar(), true);
			int slot = ResearchScreen.TOPIC_SLOTS[swift.ordinal()];
			helper.assertFalse(JobListMenusGameTests.row(helper, menu, slot).off(), "Swift Hands at level 2 of 3 is gray");
			List<String> told = clicked(helper, client, menu, slot);
			helper.assertTrue(told.contains("The scholars will research Swift Hands next") && told.stream().noneMatch(s -> s.contains("fully researched")),
				"a click on Swift Hands at level 2 of 3 told the player: " + told);
			helper.assertTrue(d.hall().research().currentTopic() == swift, "the click didn't choose Swift Hands");
			helper.succeed();
		});
	}

	/**
	 * A topic at its top while the scholars are paid and busy with another (Hearth): the click says "Swift Hands is fully
	 * researched." (not "The scholars are busy with Hearth", not "can't be researched yet"), and Hearth stays chosen.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaResearchTopWhileBusy1111c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaResearchTopWhileBusy1111c")
	public void aFullyResearchedTopicClickedWhileTheScholarsAreBusySaysItsFullyResearched(GameTestHelper helper) {
		Desk d = desk(helper);
		helper.runAfterDelay(2, () -> {
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			Research.Topic hearth = Research.Topic.HEARTH;
			d.hall().setResearch(new Research.State(Map.of(swift.key(), swift.maxLevel), Optional.of(hearth.key()), 0, true));
			Research.forget();
			helper.assertTrue(d.hall().research().currentTopic() == hearth, "setup: Hearth isn't being researched");
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ChoiceMenu menu = JobListMenusGameTests.open(helper, client.player(), d.scholar(), true);
			List<String> told = clicked(helper, client, menu, ResearchScreen.TOPIC_SLOTS[swift.ordinal()]);
			helper.assertTrue(told.equals(List.of("Swift Hands is fully researched.")), "a click on Swift Hands at its top, Hearth under way, told the player: " + told);
			helper.assertTrue(d.hall().research().currentTopic() == hearth, "the click changed what the scholars research");
			helper.succeed();
		});
	}

	/**
	 * The Great Library's row below its top, with no Great Library: Swift Hands IV–V at level 3 (the first tier's top)
	 * and at 4 (one below the second tier's top) both say "Swift Hands IV–V needs the Great Library", never "fully
	 * researched"; at 5 it says "Swift Hands IV–V is fully researched." even with the library gone.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaResearchLibraryEdges1111c"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaResearchLibraryEdges1111c")
	public void theLibraryRowBelowItsTopSaysItNeedsTheLibrary(GameTestHelper helper) {
		Desk d = desk(helper);
		helper.runAfterDelay(2, () -> {
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			int slot = ResearchScreen.LIBRARY_SLOTS[ResearchScreen.LIBRARY_TOPICS.indexOf(swift)];
			Object[][] cases = {
				{swift.maxLevel, "Swift Hands IV–V needs the Great Library"},
				{swift.top() - 1, "Swift Hands IV–V needs the Great Library"},
				{swift.top(), "Swift Hands IV–V is fully researched."},
			};
			for (Object[] c : cases) {
				d.hall().setResearch(new Research.State(Map.of(swift.key(), (Integer) c[0]), Optional.empty(), 0, false));
				Research.forget();
				ChoiceMenu menu = JobListMenusGameTests.open(helper, client.player(), d.scholar(), true);
				List<String> told = clicked(helper, client, menu, slot);
				helper.assertTrue(told.equals(List.of((String) c[1])), "the library row at level " + c[0] + " told the player " + told + ", expected '" + c[1] + "'");
				client.player().closeContainer();
			}
			helper.assertTrue(d.hall().research().currentTopic() == null, "a click on the library row chose a topic without the library");
			helper.succeed();
		});
	}
}
