package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.HomeGrades;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane b, 2026-10-26): adversarial tests for the grander homes (ROADMAP 34.15, 34.15a, 34.15b, 34.16), written from
 * the roadmap and docs/wiki/grander-homes.md. They cover what HomeGradeGameTests and ManorGameTests leave out: home files
 * with every kind of bad grade, a tier above III, a couple at the lower of their two homes, the cap of three tips, the
 * plural "could be" tip, a Noble wanting grade III, the Townhouse for every builder level and only for the village round
 * the nearest hall, and the trades added when a player opens a builder's trades.
 */
public class QaGranderHomesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(19, 2, 19);
	private static final String HOW = "Upgrade the house they sleep in, or have a builder put up a grander one and give them its beds: an Artisan's House "
		+ "is grade I and its upgrade II; a Burgher's Townhouse is grade II and its upgrades III";

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	/**
	 * A hall at {@code hall} (a Hamlet), classes on with a test ladder: Peasant (0), Artisan (1, a grade I home), Burgher
	 * (2, a grade II home and a City) and Noble (3, a grade III home and a Town), so only the home is missing to rise from
	 * Peasant to Artisan. Everything is put back afterwards.
	 */
	private static BlockPos village(GameTestHelper helper, BlockPos hallAt, int radius) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int old = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = old;
			new WorkplaceConfig().apply();
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1, \"name\": {\"translate\": \"class.aliveworkplace.peasant\"}}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"name\": {\"translate\": \"class.aliveworkplace.artisan\"}, "
			+ "\"needs\": [{\"type\": \"home\", \"grade\": 1}]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"name\": {\"translate\": \"class.aliveworkplace.burgher\"}, "
			+ "\"needs\": [{\"type\": \"home\", \"grade\": 2}, {\"type\": \"village_rank\", \"rank\": \"city\"}]}"));
		files.put(ours("noble"), JsonParser.parseString("{\"tier\": 3, \"tax\": 4, \"name\": {\"translate\": \"class.aliveworkplace.noble\"}, "
			+ "\"needs\": [{\"type\": \"home\", \"grade\": 3}, {\"type\": \"village_rank\", \"rank\": \"town\"}]}"));
		SocialClasses.load(files);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return helper.absolutePos(hallAt);
	}

	private static List<String> homeTips(ServerLevel level, BlockPos hall) {
		return VillageAdvice.tips(level, hall).stream().filter(t -> t.key().startsWith("grand_home"))
			.map(t -> t.title().getString() + " | " + t.how().getString()).toList();
	}

	/**
	 * Every way a home file can be wrong is skipped without a crash and the good file beside them still loads: a grade of
	 * 0, -1 or 4 (outside 1 to 3), a grade that isn't a number, no grade, no blueprint, a blueprint that isn't an id, and a
	 * file that isn't an object. The blueprints they name keep their own tiers. A real reload brings the shipped files back.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGrandHomeBadFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGrandHomeBadFiles")
	public void badHomeFilesAreSkippedAndTheGoodOneStillLoads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/a_good.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:stone_house\", \"grade\": 2}"));
		files.put(ours("homes/zero.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 0}"));
		files.put(ours("homes/negative.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:starter_cottage\", \"grade\": -1}"));
		files.put(ours("homes/four.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:artisans_house\", \"grade\": 4}"));
		files.put(ours("homes/words.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:townhouse\", \"grade\": \"two\"}"));
		files.put(ours("homes/no_grade.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:manor\"}"));
		files.put(ours("homes/no_blueprint.json"), JsonParser.parseString("{\"grade\": 2}"));
		files.put(ours("homes/bad_id.json"), JsonParser.parseString("{\"blueprint\": \"Not An Id!\", \"grade\": 2}"));
		files.put(ours("homes/array.json"), JsonParser.parseString("[1, 2, 3]"));
		files.put(ours("homes/unknown.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:no_such_house\", \"grade\": 2}"));
		try {
			HomeGrades.load(files);
		} catch (RuntimeException e) {
			helper.fail("loading bad home files threw " + e);
			return;
		}
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()) == 2, "the good file beside the bad ones: Stone House grade "
			+ HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()));
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.TERRACE, StarterBlueprints.STARTER_COTTAGE, StarterBlueprints.ARTISANS_HOUSE,
				StarterBlueprints.TOWNHOUSE, StarterBlueprints.MANOR)) {
			helper.assertTrue(HomeGrades.given(e.id()) == 0, "a bad file gave " + e.id() + " grade " + HomeGrades.given(e.id()));
			helper.assertTrue(HomeGrades.grade(e.id()) == 1, e.id() + " keeps its own tier I, is " + HomeGrades.grade(e.id()));
		}
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.TERRACE_2.id()) == 2, "Terrace II keeps its tier II");
		// A real reload reads the shipped files again: the Townhouse II, the Manor III
		HomeGrades.reload(level.getServer().getResourceManager());
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.TOWNHOUSE.id()) == 2 && HomeGrades.grade(StarterBlueprints.MANOR.id()) == 3
			&& HomeGrades.given(StarterBlueprints.STONE_HOUSE.id()) == 0, "after a reload the shipped grades are back");
		helper.succeed();
	}

	/**
	 * "Each upgrade one more, up to III", and "a blueprint whose own tier is above III keeps it": a family given grade 2
	 * has its _2 and _3 at III and its _4 at 4; the Townhouse given grade 1 is I, II, III up its tiers (in any style); a
	 * family given III is III from its first tier.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaGrandHomeTiers"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaGrandHomeTiers")
	public void upgradesAddOneUpToIIIAndATierAboveIIIKeepsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/villa.json"), JsonParser.parseString("{\"blueprint\": \"qa:villa\", \"grade\": 2}"));
		files.put(ours("homes/townhouse.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:townhouse\", \"grade\": 1}"));
		files.put(ours("homes/terrace.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 3}"));
		HomeGrades.load(files);
		Map<ResourceLocation, Integer> expected = new java.util.LinkedHashMap<>();
		expected.put(ResourceLocation.fromNamespaceAndPath("qa", "villa"), 2);
		expected.put(ResourceLocation.fromNamespaceAndPath("qa", "villa_2"), 3);
		expected.put(ResourceLocation.fromNamespaceAndPath("qa", "villa_3"), 3);
		expected.put(ResourceLocation.fromNamespaceAndPath("qa", "villa_4"), 4);
		expected.put(StarterBlueprints.TOWNHOUSE.id(), 1);
		expected.put(StarterBlueprints.TOWNHOUSE_2.id(), 2);
		expected.put(StarterBlueprints.TOWNHOUSE_3.id(), 3);
		expected.put(BlueprintStyles.styled(StarterBlueprints.TOWNHOUSE_2.id(), "dark_oak"), 2);
		expected.put(StarterBlueprints.TERRACE.id(), 3);
		expected.put(StarterBlueprints.TERRACE_2.id(), 3);
		for (Map.Entry<ResourceLocation, Integer> e : expected.entrySet()) {
			helper.assertTrue(HomeGrades.grade(e.getKey()) == e.getValue(), e.getKey() + " should be grade " + e.getValue() + ", is " + HomeGrades.grade(e.getKey()));
		}
		helper.succeed();
	}

	/**
	 * A couple counts as one household at the lower of their two homes: Burghers Dara (in a Townhouse, grade II) and Tomas
	 * (in a Stone House, grade I) still want a grade II home, in the plural tip; once Tomas's bed is in the Townhouse too,
	 * the tip is gone.
	 */
	//$ gametest_batch AREA '"qaGrandHomeCouple"'
	@GameTest(template = AREA, batch = "qaGrandHomeCouple")
	public void aCoupleWantsAGranderHomeWhileEitherBedIsInALowerOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, HALL, 32);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement townhouse = placement(helper, new BlockPos(2, 2, 2));
		BlueprintData.Placement stone = placement(helper, new BlockPos(18, 2, 2));
		Leftovers.after(helper, () -> {
			sites.forgetFinished(townhouse);
			sites.forgetFinished(stone);
		});
		sites.recordFinished(StarterBlueprints.TOWNHOUSE.id(), townhouse, UUID.randomUUID());
		sites.recordFinished(StarterBlueprints.STONE_HOUSE.id(), stone, UUID.randomUUID());
		Villager dara = villager(helper, new BlockPos(3, 2, 20), "Dara");
		Villager tomas = villager(helper, new BlockPos(5, 2, 20), "Tomas");
		marry(dara, tomas);
		Beds.home(dara, helper.absolutePos(new BlockPos(6, 8, 10)));
		Beds.home(tomas, helper.absolutePos(new BlockPos(21, 3, 5)));
		SocialClasses.seed(dara, SocialClasses.get(ours("burgher")));
		SocialClasses.seed(tomas, SocialClasses.get(ours("burgher")));
		List<String> tips = homeTips(level, hall);
		helper.assertTrue(tips.size() == 1 && (tips.contains("Dara and Tomas are Burghers and want a grade II home | " + HOW)
			|| tips.contains("Tomas and Dara are Burghers and want a grade II home | " + HOW)), "the couple at the lower of their homes: " + tips);
		Beds.home(tomas, helper.absolutePos(new BlockPos(8, 8, 10)));
		helper.assertTrue(homeTips(level, hall).isEmpty(), "both in the Townhouse, no tip: " + homeTips(level, hall));
		helper.succeed();
	}

	/** Up to three households get a tip: four Artisans with no home give exactly three, each "X is an Artisan and wants a grade I home". */
	//$ gametest_batch AREA '"qaGrandHomeThree"'
	@GameTest(template = AREA, batch = "qaGrandHomeThree")
	public void noMoreThanThreeHouseholdsGetATip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, HALL, 32);
		String[] names = {"Ann", "Bea", "Cal", "Dot"};
		for (int i = 0; i < names.length; i++) {
			Villager v = villager(helper, new BlockPos(4 + 4 * i, 2, 26), names[i]);
			Beds.home(v, helper.absolutePos(new BlockPos(4 + 4 * i, 2, 4)));
			SocialClasses.seed(v, SocialClasses.get(ours("artisan")));
		}
		List<String> tips = homeTips(level, hall);
		helper.assertTrue(tips.size() == 3, "three tips, not more: " + tips);
		for (String t : tips) {
			helper.assertTrue(t.matches("(Ann|Bea|Cal|Dot) is an Artisan and wants a grade I home \\| .*") && t.endsWith(HOW), "a tip: " + t);
		}
		helper.succeed();
	}

	/**
	 * The tip's words read right for each case: a Peasant couple with all an Artisan needs but the home "could be Artisans
	 * and want a grade I home"; a Noble in Townhouse I "is a Noble and wants a grade III home", and none once it's Townhouse
	 * II (grade III); a Burgher in an Artisan's House II (grade II) none, in Artisan's House I "is a Burgher and wants a
	 * grade II home".
	 */
	//$ gametest_batch AREA '"qaGrandHomeWords"'
	@GameTest(template = AREA, batch = "qaGrandHomeWords")
	public void theTipReadsRightForEveryClassAndCount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper, HALL, 32);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement townhouse = placement(helper, new BlockPos(2, 2, 2));
		BlueprintData.Placement artisans = placement(helper, new BlockPos(18, 2, 2));
		Leftovers.after(helper, () -> {
			sites.forgetFinished(townhouse);
			sites.forgetFinished(artisans);
		});
		sites.recordFinished(StarterBlueprints.TOWNHOUSE.id(), townhouse, UUID.randomUUID());
		sites.recordFinished(StarterBlueprints.ARTISANS_HOUSE.id(), artisans, UUID.randomUUID());
		Villager dara = villager(helper, new BlockPos(3, 2, 26), "Dara");
		Villager tomas = villager(helper, new BlockPos(5, 2, 26), "Tomas");
		marry(dara, tomas);
		Beds.home(dara, helper.absolutePos(new BlockPos(4, 2, 28)));
		Beds.home(tomas, helper.absolutePos(new BlockPos(7, 2, 28)));
		SocialClasses.seed(dara, SocialClasses.get(ours("peasant")));
		SocialClasses.seed(tomas, SocialClasses.get(ours("peasant")));
		Villager aldous = villager(helper, new BlockPos(9, 2, 26), "Aldous");
		Beds.home(aldous, helper.absolutePos(new BlockPos(6, 8, 10)));
		SocialClasses.seed(aldous, SocialClasses.get(ours("noble")));
		Villager bertram = villager(helper, new BlockPos(11, 2, 26), "Bertram");
		Beds.home(bertram, helper.absolutePos(new BlockPos(19, 3, 3)));
		SocialClasses.seed(bertram, SocialClasses.get(ours("burgher")));

		List<String> tips = homeTips(level, hall);
		helper.assertTrue(tips.contains("Dara and Tomas could be Artisans and want a grade I home | They have everything else that class needs. " + HOW)
			|| tips.contains("Tomas and Dara could be Artisans and want a grade I home | They have everything else that class needs. " + HOW),
			"the Peasant couple's tip: " + tips);
		helper.assertTrue(tips.contains("Aldous is a Noble and wants a grade III home | " + HOW), "the Noble's tip: " + tips);
		helper.assertTrue(tips.contains("Bertram is a Burgher and wants a grade II home | " + HOW), "the Burgher's tip: " + tips);
		helper.assertTrue(tips.size() == 3, "three tips: " + tips);

		sites.recordFinished(StarterBlueprints.TOWNHOUSE_2.id(), townhouse, UUID.randomUUID());
		sites.recordFinished(StarterBlueprints.ARTISANS_HOUSE_2.id(), artisans, UUID.randomUUID());
		List<String> after = homeTips(level, hall);
		helper.assertTrue(after.stream().noneMatch(t -> t.startsWith("Aldous")), "the Noble in Townhouse II (grade III) wants nothing: " + after);
		helper.assertTrue(after.stream().noneMatch(t -> t.startsWith("Bertram")), "the Burgher in Artisan's House II (grade II) wants nothing: " + after);
		helper.assertTrue(after.size() == 1, "only the Peasant couple left: " + after);
		helper.succeed();
	}

	/** A builder of {@code level} with one ordinary trade (a villager with no offers won't open the trade screen). */
	private static Villager builder(GameTestHelper helper, BlockPos pos, String name, int level) {
		Villager b = villager(helper, pos, name);
		b.setVillagerData(new VillagerData(b.getVillagerData().getType(), ModVillagers.BUILDER, level));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.STICK, 4), 12, 1, 0.05f));
		b.setOffers(offers);
		return b;
	}

	/** {@code player} right-clicks {@code villager}, the way a player opens its trades, and closes the screen again. */
	private static void openTrades(GameTestHelper helper, Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trade screen for " + villager.getName().getString() + ": " + player.containerMenu);
		player.closeContainer();
	}

	private static long sold(Villager v, StarterBlueprints.Entry e) {
		return v.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult()).map(d -> d.structure().equals(e.id())).orElse(false)).count();
	}

	/**
	 * Through the player's own entry point (opening a builder's trades): with an Artisan household in the village, a
	 * Novice sells the Townhouse ("every builder there") but not the Artisan's House; an Apprentice and a Master sell both;
	 * opening the trades again adds neither twice.
	 */
	//$ gametest_batch AREA '"qaGrandHomeOpenTrades"'
	@GameTest(template = AREA, batch = "qaGrandHomeOpenTrades")
	public void openingABuildersTradesAddsTheGranderHomesOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper, HALL, 32);
		Villager ann = villager(helper, new BlockPos(14, 2, 14), "Ann");
		SocialClasses.seed(ann, SocialClasses.get(ours("artisan")));
		Villager novice = builder(helper, new BlockPos(6, 2, 6), "Novice", 1);
		Villager apprentice = builder(helper, new BlockPos(6, 2, 12), "Apprentice", 2);
		Villager master = builder(helper, new BlockPos(6, 2, 18), "Master", 5);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			for (Villager b : List.of(novice, apprentice, master)) {
				openTrades(helper, b, player);
				openTrades(helper, b, player);
			}
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.assertTrue(sold(novice, StarterBlueprints.ARTISANS_HOUSE) == 0, "a Novice sells no Artisan's House");
		helper.assertTrue(sold(novice, StarterBlueprints.TOWNHOUSE) == 1, "a Novice sells the Townhouse once in a village with Artisans: " + sold(novice, StarterBlueprints.TOWNHOUSE));
		for (Villager b : List.of(apprentice, master)) {
			helper.assertTrue(sold(b, StarterBlueprints.ARTISANS_HOUSE) == 1, b.getName().getString() + " sells the Artisan's House once: " + sold(b, StarterBlueprints.ARTISANS_HOUSE));
			helper.assertTrue(sold(b, StarterBlueprints.TOWNHOUSE) == 1, b.getName().getString() + " sells the Townhouse once: " + sold(b, StarterBlueprints.TOWNHOUSE));
			helper.assertTrue(sold(b, StarterBlueprints.MANOR) == 0, b.getName().getString() + " sells no Manor without Burghers");
		}
		helper.succeed();
	}

	/**
	 * "The village round the nearest hall": two halls; Artisans in the far village don't bring the Townhouse to a builder
	 * by the near hall, nor Burghers there the Manor; with village classes off an Artisan beside him doesn't either; with
	 * classes on he does.
	 */
	//$ gametest_batch AREA '"qaGrandHomeNearestHall"'
	@GameTest(template = AREA, batch = "qaGrandHomeNearestHall")
	public void onlyTheNearestHallsVillageBringsTheTownhouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos near = new BlockPos(4, 2, 4);
		BlockPos far = new BlockPos(28, 2, 28);
		village(helper, near, 10);
		helper.setBlock(far, ModBlocks.VILLAGE_HALL);
		Villager builder = builder(helper, new BlockPos(6, 2, 6), "Builder", 2);
		Villager ann = villager(helper, new BlockPos(26, 2, 26), "Ann");
		Villager bertram = villager(helper, new BlockPos(26, 2, 24), "Bertram");
		SocialClasses.seed(ann, SocialClasses.get(ours("artisan")));
		SocialClasses.seed(bertram, SocialClasses.get(ours("burgher")));
		io.github.jcondedata.aliveworkplace.registry.ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder, StarterBlueprints.TOWNHOUSE) == 0 && sold(builder, StarterBlueprints.MANOR) == 0,
			"Artisans and Burghers of another village bring nothing to this builder");
		Villager cal = villager(helper, new BlockPos(8, 2, 8), "Cal");
		SocialClasses.seed(cal, SocialClasses.get(ours("artisan")));
		SocialClasses.ENABLED = false;
		io.github.jcondedata.aliveworkplace.registry.ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder, StarterBlueprints.TOWNHOUSE) == 0, "no Townhouse with village classes off");
		SocialClasses.ENABLED = true;
		io.github.jcondedata.aliveworkplace.registry.ModTrades.builderHomes(builder);
		helper.assertTrue(sold(builder, StarterBlueprints.TOWNHOUSE) == 1 && sold(builder, StarterBlueprints.MANOR) == 0,
			"an Artisan in his own village brings the Townhouse (and not the Manor)");
		helper.succeed();
	}
}
