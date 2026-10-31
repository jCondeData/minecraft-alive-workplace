package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31), written from the specs of three landed fixes, not their code:
 * <ul>
 * <li>B453: a "too far" refusal names the worker's own block (the composter, the miner's bench, the job block), never
 * "the builder's Blueprint Table"; each worker path through the player's right-click, the reach boundary from both
 * sides (exactly at the reach taken, one block past refused) and the whole English sentence.</li>
 * <li>B445: counts of exactly one read in the singular and two in the plural, through the hall's golem page and the
 * bank's own deposit and withdrawal.</li>
 * <li>B340: the preview's per-player memory of blueprint ids stays small, the 100-tick repeat window from both sides,
 * every real id answered once, and one player's requests never suppress another's.</li>
 * </ul>
 */
public class QaBuilderWords1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos WORK = new BlockPos(2, 2, 2);
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");

	// --- B453: the worker's own block ----------------------------------------------------------------------------------

	/** A worker employed at {@code block} with the given POI and profession, and a player who reads what they're told. */
	private record Setup(Villager worker, ServerPlayer player, List<Component> seen, BlockPos work) {
		List<String> lines() {
			return seen.stream().map(Component::getString).toList();
		}
	}

	private static Setup worker(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession profession) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, block);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, v, helper.absolutePos(WORK), poi, profession);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Setup(v, player, seen, helper.absolutePos(WORK));
	}

	/** A Field Marker marking a 5 × 5 area whose middle is {@code centre}. */
	private static ItemStack fieldMarker(ServerLevel level, BlockPos centre) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(centre.offset(-2, 0, -2)),
			Optional.of(centre.offset(2, 0, 2))));
		return marker;
	}

	/** The player right-clicks the worker holding {@code stack}, as the client sends it. */
	private static void click(GameTestHelper helper, Setup s, ItemStack stack) {
		s.player().setItemInHand(InteractionHand.MAIN_HAND, stack);
		s.seen().clear();
		UseEntityCallback.EVENT.invoker().interact(s.player(), helper.getLevel(), InteractionHand.MAIN_HAND, s.worker(), null);
	}

	private static void noBuilderWords(GameTestHelper helper, List<String> lines, String who) {
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("Blueprint Table") || l.contains("builder")),
			who + "'s refusal speaks of the builder's Blueprint Table: " + lines);
	}

	/**
	 * B453, the farmer from both sides of the reach: a field whose middle is one block past the reach is refused with
	 * the whole sentence naming the Composter; one whose middle is exactly at the reach is taken.
	 */
	//$ gametest_batch AREA '"qa1031FarmerEdge"'
	@GameTest(template = AREA, batch = "qa1031FarmerEdge")
	public void aFarmersFieldAtTheReachIsTakenAndOnePastIsRefusedNamingTheComposter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		int reach = Fields.MAX_DISTANCE;
		click(helper, s, fieldMarker(level, s.work().east(reach + 1)));
		List<String> lines = s.lines();
		String wanted = "That field is " + (reach + 1) + " blocks from the Composter the farmer works at — keep it within " + reach + ".";
		helper.assertTrue(lines.contains(wanted), "one block past the reach, wanted '" + wanted + "', read " + lines);
		noBuilderWords(helper, lines, "the farmer");
		helper.assertTrue(!Fields.hasField(s.worker()), "a field one block past the reach was taken");
		click(helper, s, fieldMarker(level, s.work().east(reach)));
		helper.assertTrue(Fields.hasField(s.worker()), "a field exactly at the reach (" + reach + ") was refused: " + s.lines());
		helper.assertTrue(s.lines().stream().noneMatch(l -> l.contains("keep it within")), "the field at the reach read a too-far line: " + s.lines());
		helper.succeed();
	}

	/** B453, the orchard keeper (an AreaJobs worker at a Fruit Basket): refused one past, naming the Fruit Basket; taken at the reach. */
	//$ gametest_batch AREA '"qa1031OrchardEdge"'
	@GameTest(template = AREA, batch = "qa1031OrchardEdge")
	public void anOrchardOnePastTheReachNamesTheFruitBasketAndAtTheReachIsTaken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		int reach = AreaJobs.ORCHARD_REACH;
		click(helper, s, fieldMarker(level, s.work().south(reach + 1)));
		List<String> lines = s.lines();
		String wanted = "That spot is " + (reach + 1) + " blocks from the Fruit Basket the orchard keeper works at — keep it within " + reach + ".";
		helper.assertTrue(lines.contains(wanted), "wanted '" + wanted + "', read " + lines);
		noBuilderWords(helper, lines, "the orchard keeper");
		helper.assertTrue(Orchards.orchard(s.worker()) == null, "an orchard one block past the reach was taken");
		click(helper, s, fieldMarker(level, s.work().south(reach)));
		helper.assertTrue(Orchards.orchard(s.worker()) != null, "an orchard exactly at the reach was refused: " + s.lines());
		helper.succeed();
	}

	/** B453, the berry breeder (an AreaJobs worker at a composter): refused one past, naming the Composter; taken at the reach. */
	//$ gametest_batch AREA '"qa1031BerryEdge"'
	@GameTest(template = AREA, batch = "qa1031BerryEdge")
	public void aBerryPlotOnePastTheReachNamesTheComposterAndAtTheReachIsTaken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		int reach = AreaJobs.BERRY_PLOT_REACH;
		click(helper, s, fieldMarker(level, s.work().west(reach + 1)));
		List<String> lines = s.lines();
		String wanted = "That spot is " + (reach + 1) + " blocks from the Composter the berry breeder works at — keep it within " + reach + ".";
		helper.assertTrue(lines.contains(wanted), "wanted '" + wanted + "', read " + lines);
		noBuilderWords(helper, lines, "the berry breeder");
		helper.assertTrue(ModAttachments.BERRY_PLOT.get(s.worker()) == null, "a berry plot one block past the reach was taken");
		click(helper, s, fieldMarker(level, s.work().west(reach)));
		helper.assertTrue(ModAttachments.BERRY_PLOT.get(s.worker()) != null, "a berry plot exactly at the reach was refused: " + s.lines());
		helper.succeed();
	}

	/** B453, the lumberjack from both sides: one past refused with the whole sentence naming the Chopping Block, at the reach taken. */
	//$ gametest_batch AREA '"qa1031LumberEdge"'
	@GameTest(template = AREA, batch = "qa1031LumberEdge")
	public void aTreeFarmAtTheReachIsTakenAndOnePastNamesTheChoppingBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		int reach = AreaJobs.TREE_FARM_REACH;
		click(helper, s, fieldMarker(level, s.work().north(reach + 1)));
		String wanted = "That spot is " + (reach + 1) + " blocks from the Chopping Block the lumberjack works at — keep it within " + reach + ".";
		helper.assertTrue(s.lines().contains(wanted), "wanted '" + wanted + "', read " + s.lines());
		helper.assertTrue(TreeFarms.farm(s.worker()) == null, "a tree farm one block past the reach was taken");
		click(helper, s, fieldMarker(level, s.work().north(reach)));
		helper.assertTrue(TreeFarms.farm(s.worker()) != null, "a tree farm exactly at the reach was refused: " + s.lines());
		helper.succeed();
	}

	/** B453, the miner through the player's click: the whole sentence names the Miner's Bench and the reach, never the builder. */
	//$ gametest_batch AREA '"qa1031MinerWords"'
	@GameTest(template = AREA, batch = "qa1031MinerWords")
	public void aFarQuarryReadsTheMinersBenchSentenceInFull(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		BlockPos far = s.work().east(Miners.MAX_DISTANCE + 40);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(far), Optional.of(far.offset(3, 0, 3)), 4));
		click(helper, s, marker);
		Pattern wanted = Pattern.compile("That spot is (\\d+) blocks from the Miner's Bench the miner works at — keep it within " + Miners.MAX_DISTANCE + "\\.");
		List<String> lines = s.lines();
		var match = lines.stream().map(wanted::matcher).filter(java.util.regex.Matcher::matches).findFirst();
		helper.assertTrue(match.isPresent(), "no whole Miner's Bench sentence: " + lines);
		int said = Integer.parseInt(match.get().group(1));
		helper.assertTrue(said > Miners.MAX_DISTANCE, "the refusal says " + said + " blocks, within the reach of " + Miners.MAX_DISTANCE);
		noBuilderWords(helper, lines, "the miner");
		helper.assertTrue(Miners.activeSite(level, s.worker()) == null, "the far quarry was started");
		helper.succeed();
	}

	/**
	 * B453 (commit d9bbbda7e: "workstation" if it is gone): a farmer whose composter was broken but who still remembers
	 * the spot is refused naming a "workstation", never a Blueprint Table, never an empty name.
	 */
	//$ gametest_batch AREA '"qa1031GoneBlock"'
	@GameTest(template = AREA, batch = "qa1031GoneBlock")
	public void aFarmerWhoseComposterIsGoneHearsOfAWorkstation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		helper.setBlock(WORK, Blocks.AIR);
		int reach = Fields.MAX_DISTANCE;
		click(helper, s, fieldMarker(level, s.work().east(reach + 10)));
		List<String> lines = s.lines();
		String wanted = "That field is " + (reach + 10) + " blocks from the workstation the farmer works at — keep it within " + reach + ".";
		helper.assertTrue(lines.contains(wanted), "wanted '" + wanted + "', read " + lines);
		noBuilderWords(helper, lines, "the farmer without a composter");
		helper.assertTrue(lines.stream().noneMatch(l -> l.contains("the  the")), "an empty block name: " + lines);
		helper.assertTrue(!Fields.hasField(s.worker()), "the far field was taken");
		helper.succeed();
	}

	/**
	 * B453 in a village (36.20's "_village" form): a farmer whose composter stands near a hall, marking a field outside
	 * the village and past the reach, reads "That field is outside {village} and N blocks from the Composter the farmer
	 * works at — keep it within 128."
	 */
	//$ gametest_batch AREA '"qa1031VillageWords"'
	@GameTest(template = AREA, batch = "qa1031VillageWords")
	public void aFarmerInAVillageHearsTheVillageAndTheComposter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		BlockPos hallRel = new BlockPos(5, 2, 5);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState()));
		int far = Math.max(VillageHalls.RADIUS, Fields.MAX_DISTANCE) + 40;
		click(helper, s, fieldMarker(level, s.work().east(far)));
		String village = VillageHalls.name(level, hall).getString();
		String wanted = "That field is outside " + village + " and " + far + " blocks from the Composter the farmer works at — keep it within "
			+ Fields.MAX_DISTANCE + ".";
		List<String> lines = s.lines();
		helper.assertTrue(lines.contains(wanted), "wanted '" + wanted + "', read " + lines);
		noBuilderWords(helper, lines, "the village farmer");
		helper.assertTrue(!Fields.hasField(s.worker()), "a field outside the village and past the reach was taken");
		helper.succeed();
	}

	// --- B445: one in the singular, two in the plural --------------------------------------------------------------

	private static IronGolem golem(GameTestHelper helper, int x, GolemSmith.Role role) {
		IronGolem g = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 24)), role);
		helper.assertTrue(g != null, "no " + role);
		g.setNoAi(true);
		return g;
	}

	/**
	 * B445 on the hall's golem page: a hauler carrying two single items (one cobblestone, one dirt) reads "carrying 2
	 * items to the Storehouse", one carrying one item "carrying 1 item to the Storehouse"; a farmhand with two wheat
	 * "carrying 2 items of harvest" and with one "carrying 1 item of harvest".
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031GolemCounts"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031GolemCounts")
	public void golemLinesCountOneAndTwoItemsRight(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 15));
		IronGolem two = golem(helper, 5, GolemSmith.Role.HAULER);
		ModAttachments.HAULER_LOAD.set(two, new LinkedHashMap<>(Map.of(Items.COBBLESTONE, 1, Items.DIRT, 1)));
		IronGolem one = golem(helper, 9, GolemSmith.Role.HAULER);
		ModAttachments.HAULER_LOAD.set(one, Map.of(Items.OAK_LOG, 1));
		IronGolem harvestTwo = golem(helper, 13, GolemSmith.Role.FARMHAND);
		ModAttachments.HAULER_LOAD.set(harvestTwo, Map.of(Items.WHEAT, 2));
		IronGolem harvestOne = golem(helper, 17, GolemSmith.Role.FARMHAND);
		ModAttachments.HAULER_LOAD.set(harvestOne, Map.of(Items.CARROT, 1));
		Leftovers.after(helper, () -> List.of(two, one, harvestTwo, harvestOne).forEach(IronGolem::discard));
		List<String> lines = GolemSmith.hallLines(level, hall).stream().map(Component::getString).toList();
		String hauler = Component.translatable("entity.aliveworkplace.golem.hauler").getString();
		String farmhand = Component.translatable("entity.aliveworkplace.golem.farmhand").getString();
		List<String> wanted = List.of(hauler + ": carrying 2 items to the Storehouse", hauler + ": carrying 1 item to the Storehouse",
			farmhand + ": carrying 2 items of harvest", farmhand + ": carrying 1 item of harvest");
		for (String w : wanted) {
			helper.assertTrue(lines.contains(w), "the hall doesn't read '" + w + "': " + lines);
		}
		helper.assertTrue(lines.stream().noneMatch(l -> l.matches(".*\\b1 items\\b.*") || l.matches(".*\\b2 item\\b.*")), "a miscounted golem line: " + lines);
		helper.succeed();
	}

	/**
	 * B445 at the bank, through the bank's own deposit and withdrawal (the Merchant Prince keeps it open): putting in one
	 * emerald reads "You put in 1 emerald. Your account: 1 emerald."; a second, "Your account: 2 emeralds."; taking one
	 * out, "You took out 1 emerald. Your account: 1 emerald."
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1031BankOne"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1031BankOne")
	public void theBanksLinesCountOneEmeraldInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallRel = new BlockPos(15, 2, 15);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallRel);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean legends = Legends.ENABLED;
		Villager prince = null;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.ENABLED = true;
			Legends.reload(level.getServer().getResourceManager());
			Legend legend = Legends.get(PRINCE).orElseThrow(() -> new AssertionError(PRINCE + " didn't load"));
			Map<ResourceLocation, Legend> roster = new LinkedHashMap<>();
			roster.put(PRINCE, legend);
			Legends.setForTest(roster);
			LegendPowers.forget();
			prince = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 15));
			prince.setNoAi(true);
			prince.setVillagerData(prince.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(5));
			Legends.make(level, prince, legend, "test");
			LegendPowers.forget();
			helper.assertTrue(PlayerBank.open(level, hall), "setup: the Merchant Prince didn't open the bank");
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			VillageHallBlockEntity e = (VillageHallBlockEntity) helper.getBlockEntity(hallRel);
			String in1 = PlayerBank.deposit(level, hall, player, 1).getString();
			helper.assertValueEqual(in1, "You put in 1 emerald. Your account: 1 emerald.", "the first deposit");
			String in2 = PlayerBank.deposit(level, hall, player, 1).getString();
			helper.assertValueEqual(in2, "You put in 1 emerald. Your account: 2 emeralds.", "the second deposit");
			helper.assertTrue(e.playerBank().emeralds(player.getUUID()) == 2 && player.getInventory().countItem(Items.EMERALD) == 0,
				"after two deposits: account " + e.playerBank().emeralds(player.getUUID()) + ", carried " + player.getInventory().countItem(Items.EMERALD));
			String out1 = PlayerBank.withdraw(level, hall, player, 1).getString();
			helper.assertValueEqual(out1, "You took out 1 emerald. Your account: 1 emerald.", "the first withdrawal");
			String out2 = PlayerBank.withdraw(level, hall, player, 1).getString();
			helper.assertValueEqual(out2, "You took out 1 emerald. Your account: 0 emeralds.", "the second withdrawal");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 2 && e.playerBank().emeralds(player.getUUID()) == 0,
				"after taking both out: carried " + player.getInventory().countItem(Items.EMERALD));
		} finally {
			VillageHalls.RADIUS = radius;
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = legends;
			if (prince != null) {
				LegendRecord.get(level).forget(prince.getUUID());
				prince.discard();
			}
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
			level.getServer().getPlayerList().remove(player);
			level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}

	// --- B340: the preview's remembered ids ------------------------------------------------------------------------

	/**
	 * B340, the 100-tick window from both sides: the Market Stall and the Starter Cottage are both answered at tick 0;
	 * the Market Stall asked again 99 ticks later is still ignored; the cottage asked again 100 ticks later is answered.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa1031PreviewEdge"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1031PreviewEdge")
	public void aPreviewRepeatAt99TicksIsIgnoredAndAt100IsAnswered(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		UUID uuid = c.player().getUUID();
		ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		var server = helper.getLevel().getServer();
		int[] start = {Integer.MIN_VALUE};
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			if (start[0] == Integer.MIN_VALUE) {
				start[0] = server.getTickCount();
				c.send(new PreviewNetworking.Request(stall));
				c.send(new PreviewNetworking.Request(cottage));
				c.channel().flush();
				List<ResourceLocation> first = c.sent(PreviewNetworking.Data.class).stream().map(PreviewNetworking.Data::id).toList();
				helper.assertTrue(first.size() == 2 && first.contains(stall) && first.contains(cottage), "the first two previews: " + first);
				return;
			}
			int age = server.getTickCount() - start[0];
			if (age == 99) {
				c.send(new PreviewNetworking.Request(stall));
				c.channel().flush();
				List<PreviewNetworking.Data> again = c.sent(PreviewNetworking.Data.class);
				helper.assertTrue(again.isEmpty(), "the Market Stall asked again after 99 ticks was sent again");
			} else if (age == 100) {
				c.send(new PreviewNetworking.Request(cottage));
				c.channel().flush();
				List<PreviewNetworking.Data> again = c.sent(PreviewNetworking.Data.class);
				helper.assertTrue(again.size() == 1 && again.get(0).id().equals(cottage),
					"the cottage asked again after exactly 100 ticks: " + again.size() + " sent");
				helper.assertTrue(PreviewNetworking.rememberedFor(uuid) <= 2, PreviewNetworking.rememberedFor(uuid) + " ids remembered for two blueprints");
				done[0] = true;
				helper.succeed();
			}
		});
	}

	/**
	 * B340: a client asking for every one of 15 real blueprints three times over in one tick gets each preview exactly
	 * once, and the server remembers no more ids than there are blueprints asked for.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1031PreviewReal"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1031PreviewReal")
	public void manyRealIdsAskedThriceAreEachAnsweredOnceAndRememberedOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		List<ResourceLocation> ids = BlueprintLibrary.list(helper.getLevel().getServer(), false).stream().limit(15).toList();
		helper.assertTrue(ids.size() >= 5, "setup: only " + ids.size() + " blueprints");
		for (int round = 0; round < 3; round++) {
			for (ResourceLocation id : ids) {
				c.send(new PreviewNetworking.Request(id));
			}
		}
		c.channel().flush();
		List<ResourceLocation> answered = c.sent(PreviewNetworking.Data.class).stream().map(PreviewNetworking.Data::id).toList();
		helper.assertTrue(answered.size() == ids.size() && answered.containsAll(ids),
			ids.size() + " blueprints asked thrice, " + answered.size() + " previews sent: " + answered);
		int remembered = PreviewNetworking.rememberedFor(c.player().getUUID());
		helper.assertTrue(remembered <= ids.size(), remembered + " ids remembered for " + ids.size() + " blueprints");
		helper.succeed();
	}

	/**
	 * B340, two players: one player's request doesn't silence the same preview for another; one player's thousands of
	 * made-up ids leave the other's memory alone; a player who leaves is forgotten.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1031PreviewTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1031PreviewTwo")
	public void onePlayersPreviewsNeverSilenceAnothers(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client ann = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		QaStageFiveGameTests.Client bo = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ResourceLocation stall = StarterBlueprints.MARKET_STALL.id();
		ann.send(new PreviewNetworking.Request(stall));
		ann.channel().flush();
		bo.send(new PreviewNetworking.Request(stall));
		bo.channel().flush();
		helper.assertTrue(ann.sent(PreviewNetworking.Data.class).size() == 1, "Ann's Market Stall preview wasn't sent");
		helper.assertTrue(bo.sent(PreviewNetworking.Data.class).size() == 1, "Ann's request silenced Bo's Market Stall preview");
		for (int i = 0; i < 3000; i++) {
			bo.send(new PreviewNetworking.Request(ResourceLocation.fromNamespaceAndPath("qa", "nothing_" + i)));
		}
		bo.channel().flush();
		helper.assertTrue(bo.sent(PreviewNetworking.Data.class).isEmpty(), "a made-up id was answered");
		int annRemembered = PreviewNetworking.rememberedFor(ann.player().getUUID());
		int boRemembered = PreviewNetworking.rememberedFor(bo.player().getUUID());
		helper.assertTrue(annRemembered == 1 && boRemembered == 1, "remembered after 3000 made-up ids: Ann " + annRemembered + ", Bo " + boRemembered);
		UUID boId = bo.player().getUUID();
		// The way a real client leaves: the connection closes, then the server handles the disconnection (Fabric's
		// DISCONNECT event fires from there).
		net.minecraft.network.Connection link = bo.channel().pipeline().get(net.minecraft.network.Connection.class);
		helper.assertTrue(link != null, "setup: Bo's connection isn't on the channel");
		bo.channel().close();
		link.handleDisconnection();
		helper.assertTrue(PreviewNetworking.rememberedFor(boId) == 0, "Bo left but " + PreviewNetworking.rememberedFor(boId) + " ids are remembered");
		helper.succeed();
	}
}
