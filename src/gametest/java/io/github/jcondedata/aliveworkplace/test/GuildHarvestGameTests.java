package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.orchard.OrchardWork;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.ranch.HerderWork;
import io.github.jcondedata.aliveworkplace.ranch.RanchWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Harvest, Herders' and Scholars' Guilds (ROADMAP 30.19): their data, and each number with the guild founded and
 * not, through the real work where it can be: a village farmer takes on a farm 20 blocks from the composter only in a
 * founded Harvest Guild (16 -> 24), an orchard keeper picks a bush 20 blocks from the basket only then; a shepherd feeds
 * a pair with 10 sheep about only in a founded Herders' Guild (8 -> 12), a hired butcher keeps 14 chickens (not 10);
 * a scholar pays 12 paper and 3 emeralds for Swift Hands I in a founded Scholars' Guild (16 and 4: a quarter less,
 * rounded up).
 */
public class GuildHarvestGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** In big_area: the hall, a Guildhall's (recorded) origin; in huge_area, their places are {@link #HUGE_HALL} and {@link #HUGE_GUILDHALL}. */
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos GUILDHALL = new BlockPos(2, 2, 14);
	private static final BlockPos HUGE_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos HUGE_GUILDHALL = new BlockPos(10, 2, 24);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeGuildsLoadFromData(GameTestHelper helper) {
		check(helper, "harvest", "Harvest Guild", List.of(mc("farmer"), AliveWorkplace.id("orchard_keeper"), AliveWorkplace.id("florist"),
				AliveWorkplace.id("beekeeper"), AliveWorkplace.id("composter"), AliveWorkplace.id("chef")),
			"Farmers, Orchard Keepers, Florists, Beekeepers, Composters and Chefs work 15% faster; the village's own farms and the orchard keepers' rounds reach 8 blocks further (24, not 16).");
		check(helper, "herders", "Herders' Guild", List.of(mc("shepherd"), mc("butcher"), AliveWorkplace.id("rancher")),
			"Shepherds, Butchers and Ranchers work 15% faster; every herd may be 4 bigger: 12 of a kind bred (not 8), 14 kept by a hired butcher (not 10).");
		check(helper, "scholars", "Scholars' Guild", List.of(AliveWorkplace.id("scholar"), AliveWorkplace.id("teacher"), mc("librarian"), mc("cartographer")),
			"Scholars, Teachers, Librarians and Cartographers work 15% faster; research levels cost a quarter less paper, books and emeralds.");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("harvest")).perks().get(1) instanceof Guilds.WorkReach r && r.blocks() == 8, "harvest reach");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("herders")).perks().get(1) instanceof Guilds.HerdSize h && h.extra() == 4, "herders' herd");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("scholars")).perks().get(1) instanceof Guilds.ResearchCost c && c.percent() == -25, "scholars' cost");
		String bad = "";
		try {
			Guilds.read(AliveWorkplace.id("bad"), com.google.gson.JsonParser.parseString(
				"{\"name\": \"X\", \"trades\": [], \"perks\": [{\"type\": \"aliveworkplace:herd_size\", \"extra\": 500}]}"));
		} catch (IllegalArgumentException e) {
			bad = e.getMessage();
		}
		helper.assertTrue(bad.contains("500") || bad.contains("extra"), "a herd 500 bigger is refused: " + bad);
		helper.succeed();
	}

	/**
	 * A village farmer's composter with a chest by it, and the only farmland 20 blocks away: not taken on (16 blocks);
	 * with the Harvest Guild founded, taken on at the next look (24 blocks).
	 */
	//$ gametest_ticks_batch HUGE '1200' '"guildHarvestFarm"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "guildHarvestFarm")
	public void harvestGuildFarmersReachTwentyFourBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, HUGE_HALL, HUGE_GUILDHALL);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos composter = new BlockPos(3, 2, 15);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(23, 2, 14), new BlockPos(25, 2, 16))) {
			helper.setBlock(p, Blocks.FARMLAND);
		}
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(3, 2, 17), Blocks.CHEST);
		Villager master = villager(helper, new BlockPos(6, 2, 20), "Wren", VillagerProfession.FARMER, 5);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 15));
		Jobs.employ(level, farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		helper.runAfterDelay(5, () -> chartered(helper, master, HUGE_HALL));
		helper.runAfterDelay(Fields.ADOPT_EVERY + 30, () -> {
			helper.assertTrue(level.getGameRules().getBoolean(ModGameRules.VILLAGE_FARMS), "village farms on");
			helper.assertTrue(Fields.farmReach(farmer) == 16, "not founded: 16 blocks, got " + Fields.farmReach(farmer));
			helper.assertFalse(Fields.hasField(farmer), "not founded: took on a farm 20 blocks away");
			found(helper, hallAt(helper, HUGE_HALL), HUGE_HALL, HUGE_GUILDHALL);
			helper.assertTrue(Fields.farmReach(farmer) == 24, "founded: 24 blocks, got " + Fields.farmReach(farmer));
			helper.assertTrue(Fields.farmReach(master) == 24 && Math.abs(Guilds.pace(farmer) * 1.15f - 1f) < 1e-4f, "a member works 15% faster");
		});
		helper.runAfterDelay(2L * Fields.ADOPT_EVERY + 80, () -> {
			var job = ModAttachments.FARM_FIELD.get(farmer);
			helper.assertTrue(job != null && job.adopted(), "founded: didn't take on the farm 20 blocks away");
			BoundingBox box = job.box();
			helper.assertTrue(box.isInside(helper.absolutePos(new BlockPos(24, 2, 15))), "the farm taken on: " + box);
			helper.succeed();
		});
	}

	/** A ripe bush 20 blocks from the Fruit Basket: left alone (16), picked once the Harvest Guild is founded (24). */
	//$ gametest_ticks_batch HUGE '1600' '"guildHarvestOrchard"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "guildHarvestOrchard")
	public void harvestGuildOrchardKeepersReachTwentyFourBlocks(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, HUGE_HALL, HUGE_GUILDHALL);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		BlockPos basket = new BlockPos(3, 2, 15);
		BlockPos bush = new BlockPos(23, 2, 15);
		helper.setBlock(basket, ModBlocks.FRUIT_BASKET);
		helper.setBlock(new BlockPos(3, 2, 17), Blocks.CHEST);
		helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
		Villager master = villager(helper, new BlockPos(6, 2, 20), "Pip", ModVillagers.ORCHARD_KEEPER, 5);
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 15));
		Jobs.employ(level, keeper, helper.absolutePos(basket), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		helper.runAfterDelay(5, () -> chartered(helper, master, HUGE_HALL));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(OrchardWork.radius(keeper) == 16, "not founded: 16 blocks, got " + OrchardWork.radius(keeper));
			helper.assertTrue(OrchardWork.findFruit(level, helper.absolutePos(basket), null, level.getGameTime(), new HashMap<>(), OrchardWork.radius(keeper)).isEmpty(),
				"not founded: the bush 20 blocks away is out of the round");
			helper.assertTrue(helper.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) == 3, "not founded: the bush 20 blocks away was picked");
			found(helper, hallAt(helper, HUGE_HALL), HUGE_HALL, HUGE_GUILDHALL);
			helper.assertTrue(OrchardWork.radius(keeper) == 24, "founded: 24 blocks, got " + OrchardWork.radius(keeper));
			helper.succeedWhen(() -> {
				helper.assertBlockPresent(Blocks.SWEET_BERRY_BUSH, bush);
				helper.assertTrue(helper.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) < 2, "founded: the bush 20 blocks away isn't picked yet");
			});
		});
	}

	/** Breeding caps and herds kept, founded and not: shepherds and ranchers 8 -> 12, a hired butcher 10 -> 14; a shepherd with 10 sheep feeds a pair only when founded. */
	//$ gametest_ticks_batch AREA '1200' '"guildHerdersBreed"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "guildHerdersBreed")
	public void herdersGuildBreedsUpToTwelve(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, HALL, GUILDHALL);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos loom = new BlockPos(2, 2, 2);
		helper.setBlock(loom, Blocks.LOOM);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(Items.WHEAT, 16));
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Ebba", VillagerProfession.SHEPHERD, 5);
		Villager rancher = villager(helper, new BlockPos(17, 2, 4), "Rafe", ModVillagers.RANCHER, 2);
		Villager butcher = villager(helper, new BlockPos(18, 2, 4), "Bo", VillagerProfession.BUTCHER, 2);
		Villager miner = villager(helper, new BlockPos(19, 2, 4), "Ida", ModVillagers.MINER, 2);
		ModAttachments.BUILDER_EMPLOYER.set(butcher, new Employer(UUID.randomUUID(), "Al"));
		Villager shepherd = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, shepherd, helper.absolutePos(loom), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		List<Sheep> sheep = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			Sheep s = helper.spawn(EntityType.SHEEP, new BlockPos(6 + i % 5, 2, 7 + i / 5 * 2));
			s.setNoAi(true);
			s.setSheared(true); // nothing to shear: breeding is all there is to do
			sheep.add(s);
		}
		helper.runAfterDelay(5, () -> {
			chartered(helper, master, HALL);
			helper.assertTrue(RanchWork.cap(shepherd) == 8 && RanchWork.cap(rancher) == 8, "not founded: 8 of a kind bred");
			helper.assertTrue(HerderWork.keeps(butcher) == 10, "not founded: a hired butcher keeps 10, got " + HerderWork.keeps(butcher));
		});
		helper.runAfterDelay(300, () -> {
			long inLove = sheep.stream().filter(Sheep::isInLove).count();
			helper.assertTrue(inLove == 0 && chest.countItem(Items.WHEAT) == 16, "not founded: fed " + inLove + " of 10 sheep (the cap is 8)");
			found(helper, hallAt(helper, HALL), HALL, GUILDHALL);
			helper.assertTrue(RanchWork.cap(shepherd) == 12 && RanchWork.cap(rancher) == 12 && RanchWork.cap(butcher) == 12, "founded: 12 of a kind bred, got "
				+ RanchWork.cap(shepherd) + "/" + RanchWork.cap(rancher) + "/" + RanchWork.cap(butcher));
			helper.assertTrue(HerderWork.keeps(butcher) == 14, "founded: a hired butcher keeps 14, got " + HerderWork.keeps(butcher));
			helper.assertTrue(RanchWork.cap(miner) == 8, "a miner isn't a herder");
			helper.assertTrue(Math.abs(Guilds.pace(rancher) * 1.15f - 1f) < 1e-4f, "a rancher works 15% faster");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(RanchWork.cap(shepherd) == 8 && HerderWork.keeps(butcher) == 10, "guilds off: 8 and 10");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeedWhen(() -> {
				long fed = sheep.stream().filter(Sheep::isInLove).count();
				helper.assertTrue(fed >= 2, "founded: a pair of the 10 sheep fed, got " + fed);
			});
		});
	}

	/** A hired butcher in a founded Herders' Guild with 16 grown chickens takes 2 for meat and keeps 14 (not 10). */
	//$ gametest_ticks_batch AREA '2400' '"guildHerdersCull"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "guildHerdersCull")
	public void herdersGuildButcherKeepsFourteen(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, HALL, GUILDHALL);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos smoker = new BlockPos(2, 2, 2);
		helper.setBlock(smoker, Blocks.SMOKER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Ebba", VillagerProfession.SHEPHERD, 5);
		Villager butcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, butcher, helper.absolutePos(smoker), PoiTypes.BUTCHER, VillagerProfession.BUTCHER);
		ModAttachments.BUILDER_EMPLOYER.set(butcher, new Employer(UUID.randomUUID(), "Al"));
		List<Chicken> chickens = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(6 + i % 4, 2, 6 + i / 4));
			chicken.setNoAi(true);
			chickens.add(chicken);
		}
		long[] fourteenSince = {-1};
		helper.runAfterDelay(2, () -> found(helper, chartered(helper, master, HALL), HALL, GUILDHALL));
		helper.succeedWhen(() -> {
			long alive = chickens.stream().filter(Chicken::isAlive).count();
			helper.assertTrue(alive >= 14, "founded: culled below 14, to " + alive);
			if (alive != 14) {
				fourteenSince[0] = -1;
				helper.assertTrue(false, "chickens left: " + alive);
			}
			if (fourteenSince[0] < 0) {
				fourteenSince[0] = helper.getTick();
			}
			helper.assertTrue(helper.getTick() - fourteenSince[0] >= 400, "14 for " + (helper.getTick() - fourteenSince[0]) + " ticks so far");
		});
	}

	/** Research costs, founded and not (a quarter less, rounded up), and a scholar paying 12 paper and 3 emeralds for Swift Hands I. */
	//$ gametest_ticks_batch AREA '800' '"guildScholars"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "guildScholars")
	public void scholarsGuildResearchCostsAQuarterLess(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper, HALL, GUILDHALL);
		int points = Research.POINTS;
		Research.POINTS = 100;
		Leftovers.after(helper, () -> {
			Research.POINTS = points;
			Research.forget();
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos desk = new BlockPos(6, 2, 6);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		helper.setBlock(new BlockPos(6, 2, 8), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(6, 2, 8));
		Villager master = villager(helper, new BlockPos(16, 2, 4), "Odo", ModVillagers.SCHOLAR, 5);
		Villager librarian = villager(helper, new BlockPos(17, 2, 4), "Lia", VillagerProfession.LIBRARIAN, 2);
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity hall = chartered(helper, master, HALL);
			hall.setResearch(new Research.State(java.util.Map.of(), java.util.Optional.empty(), 0, false));
			cost(helper, hall, Research.Topic.SWIFT_HANDS, 1, 16, 0, 4);
			cost(helper, hall, Research.Topic.SWIFT_HANDS, 2, 32, 2, 8);
			cost(helper, hall, Research.Topic.ARCHITECTURE, 1, 16, 4, 4);
			found(helper, hall, HALL, GUILDHALL);
			cost(helper, hall, Research.Topic.SWIFT_HANDS, 1, 12, 0, 3);
			cost(helper, hall, Research.Topic.SWIFT_HANDS, 2, 24, 2, 6);
			cost(helper, hall, Research.Topic.SWIFT_HANDS, 3, 36, 3, 9);
			cost(helper, hall, Research.Topic.ARCHITECTURE, 1, 12, 3, 3);
			helper.assertTrue(Math.abs(Guilds.pace(librarian) * 1.15f - 1f) < 1e-4f, "a librarian works 15% faster");
			try {
				Guilds.ENABLED = false;
				cost(helper, hall, Research.Topic.SWIFT_HANDS, 1, 16, 0, 4);
			} finally {
				Guilds.ENABLED = true;
			}
			// The scholar's desk: the screen shows the cheaper price, and the scholar pays exactly it.
			chest.setItem(0, new ItemStack(Items.PAPER, 12));
			chest.setItem(1, new ItemStack(Items.EMERALD, 3));
			Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 7));
			Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
			var player = helper.makeMockServerPlayerInLevel();
			ChoiceMenu menu = ResearchScreen.forTest(player, helper.absolutePos(HALL));
			int slot = ResearchScreen.TOPIC_SLOTS[Research.Topic.SWIFT_HANDS.ordinal()];
			ItemLore lore = menu.icon(slot).get(DataComponents.LORE);
			List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("12") && l.contains("Paper") && l.contains("3") && l.contains("Emerald")),
				"the screen shows the cheaper price: " + lines);
			menu.press(slot, player);
			helper.assertTrue(hall.research().currentTopic() == Research.Topic.SWIFT_HANDS, "Swift Hands not chosen");
			helper.succeedWhen(() -> {
				helper.assertTrue(hall.research().level(Research.Topic.SWIFT_HANDS) == 1, "Swift Hands: " + hall.research());
				helper.assertTrue(chest.countItem(Items.PAPER) == 0 && chest.countItem(Items.EMERALD) == 0, "paid: paper left "
					+ chest.countItem(Items.PAPER) + ", emeralds left " + chest.countItem(Items.EMERALD));
			});
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static ResourceLocation mc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}

	private static void check(GameTestHelper helper, String id, String name, List<ResourceLocation> trades, String perk) {
		Guilds.Guild g = Guilds.get(AliveWorkplace.id(id));
		helper.assertTrue(g != null, "the " + name + " didn't load: " + Guilds.all().keySet());
		helper.assertTrue(g.name().getString().equals(name), "name: " + g.name().getString());
		helper.assertTrue(g.trades().equals(trades), name + " trades: " + g.trades());
		helper.assertTrue(g.perk().getString().equals(perk), name + " perk: " + g.perk().getString());
		helper.assertTrue(g.perks().get(0) instanceof CivicEffects.WorkPace pace && pace.percent() == 15, name + " 15% faster: " + g.perks());
	}

	private static void cost(GameTestHelper helper, VillageHallBlockEntity hall, Research.Topic topic, int lvl, int paper, int books, int emeralds) {
		Research.Cost c = topic.cost(lvl, helper.getLevel(), hall);
		helper.assertTrue(c.paper() == paper && c.books() == books && c.emeralds() == emeralds,
			topic.key() + " " + lvl + ": expected " + paper + "/" + books + "/" + emeralds + ", got " + c);
	}

	/** A Village Hall at {@code hall} (the hall's radius 16 for the test); what the test changed is put back when it ends. */
	private static void village(GameTestHelper helper, BlockPos hall, BlockPos guildhall) {
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlueprintData.Placement at = placement(helper, guildhall);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			BuildSiteManager.get(helper.getLevel()).forgetFinished(at);
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos guildhall) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(guildhall), Rotation.NONE, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	private static VillageHallBlockEntity hallAt(GameTestHelper helper, BlockPos hall) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hall));
	}

	/** A City hall whose {@code master} was just chartered (no Guildhall yet: the guild waits). */
	private static VillageHallBlockEntity chartered(GameTestHelper helper, Villager master, BlockPos at) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = hallAt(helper, at);
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		Guilds.Offer offer = Guilds.grant(level, master);
		helper.assertTrue(offer.outcome() == Guilds.Outcome.GRANTED, "chartered: " + offer.message().getString());
		helper.assertTrue(!Guilds.founded(level, hall, hall.guilds().get(0).id()), "not founded without a Guildhall");
		return hall;
	}

	/** A Guildhall is finished: the guild claims it and is founded. */
	private static void found(GameTestHelper helper, VillageHallBlockEntity hall, BlockPos at, BlockPos guildhall) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), placement(helper, guildhall), UUID.randomUUID());
		Guilds.round(level, helper.absolutePos(at), hall);
		helper.assertTrue(Guilds.founded(level, hall, hall.guilds().get(0).id()), "founded: " + hall.guilds());
	}
}
