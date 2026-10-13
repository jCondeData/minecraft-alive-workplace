package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.HomeGrades;
import io.github.jcondedata.aliveworkplace.people.HomeWants;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import java.util.HashMap;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Grander homes (ROADMAP 34.15): a home's grade is its blueprint's tier unless a {@code homes/} data file gives it more
 * (the Townhouse is grade II, its upgrades III); households whose class wants a grander home get a "What next?" tip and
 * the Steward's {@code households_want_grade} counts the same households; builders sell the Artisan's House from
 * Apprentice and the Townhouse once the village has Artisans.
 */
public class HomeGradeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(19, 2, 19);

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void sleepsAt(GameTestHelper helper, Villager v, BlockPos bed) {
		v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bed)));
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static int grade(ServerLevel level, Villager v) {
		return Homes.of(level, v).map(Homes.Home::tier).orElse(0);
	}

	/** A bed in Townhouse I is in a grade II home, in its upgrades grade III; the Artisan's House is I and II; any style counts the same. */
	//$ gametest_batch AREA '"homeGradeTownhouse"'
	@GameTest(template = AREA, batch = "homeGradeTownhouse")
	public void aBedInTheTownhouseCountsIIAndInItsUpgradeIII(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = placement(helper, new BlockPos(2, 2, 2));
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara");
		sleepsAt(helper, dara, new BlockPos(6, 8, 10)); // the first floor's bedroom (x 2-12, y 2-21, z 2-13 here)
		Map<ResourceLocation, Integer> expected = new java.util.LinkedHashMap<>();
		expected.put(StarterBlueprints.TOWNHOUSE.id(), 2);
		expected.put(StarterBlueprints.TOWNHOUSE_2.id(), 3);
		expected.put(StarterBlueprints.TOWNHOUSE_3.id(), 3);
		expected.put(BlueprintStyles.styled(StarterBlueprints.TOWNHOUSE.id(), "cherry"), 2);
		expected.put(BlueprintStyles.styled(StarterBlueprints.TOWNHOUSE_2.id(), "stonework"), 3);
		expected.put(StarterBlueprints.ARTISANS_HOUSE.id(), 1);
		expected.put(StarterBlueprints.ARTISANS_HOUSE_2.id(), 2);
		expected.put(StarterBlueprints.STONE_HOUSE.id(), 1); // no data file: its tier
		for (Map.Entry<ResourceLocation, Integer> e : expected.entrySet()) {
			sites.recordFinished(e.getKey(), placement, UUID.randomUUID());
			helper.assertTrue(grade(level, dara) == e.getValue(), "a bed in " + e.getKey() + " should be in a grade " + e.getValue() + " home, is " + Homes.of(level, dara));
			helper.assertTrue(HomeGrades.grade(e.getKey()) == e.getValue(), "HomeGrades.grade(" + e.getKey() + ") = " + HomeGrades.grade(e.getKey()));
		}
		// The class need reads the same grade: a Burgher's "home grade 2" holds in Townhouse I
		sites.recordFinished(StarterBlueprints.TOWNHOUSE.id(), placement, UUID.randomUUID());
		ClassNeeds.Village village = new ClassNeeds.Village(level, helper.absolutePos(HALL), 0);
		helper.assertTrue(ClassNeeds.holds(new ClassNeeds.Home(2), dara, village) && !ClassNeeds.holds(new ClassNeeds.Home(3), dara, village),
			"a bed in Townhouse I holds a grade II need and not a grade III one");
		helper.succeed();
	}

	/**
	 * A data file overrides a tier: the Stone House given grade 2 is II, its II and III are III; a file never lowers a
	 * grade; a broken file is skipped (with a warning naming it) and the others still load. The shipped file is put back.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"homeGradeFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "homeGradeFiles")
	public void aDataFileOverridesATier(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TOWNHOUSE.id()) == 2, "the shipped homes/townhouse.json gives the Townhouse grade II");
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/stone_house.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:stone_house\", \"grade\": 2}"));
		files.put(ours("homes/starter.json"), JsonParser.parseString("{\"blueprint\": \"starter_cottage_3\", \"grade\": 1}"));
		files.put(ours("homes/broken.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 5}"));
		HomeGrades.load(files);
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()) == 2, "Stone House given grade II: " + HomeGrades.grade(StarterBlueprints.STONE_HOUSE.id()));
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STONE_HOUSE_2.id()) == 3, "its II is one more: III");
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STONE_HOUSE_3.id()) == 3, "its III stays III (no more than III)");
		helper.assertTrue(HomeGrades.grade(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "sandstone")) == 2, "any style of it too");
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STARTER_COTTAGE_3.id()) == 3, "a file naming an upgrade grades its family, and never lowers a tier");
		helper.assertTrue(HomeGrades.grade(StarterBlueprints.STARTER_COTTAGE.id()) == 1, "grade I given to the Starter Cottage: I");
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TERRACE.id()) == 0 && HomeGrades.grade(StarterBlueprints.TERRACE_2.id()) == 2,
			"a broken file (grade 5) is skipped: the Terrace keeps its tiers");
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TOWNHOUSE.id()) == 0, "the files loaded replace the shipped ones");
		helper.succeed();
	}

	/** A Hall, a ladder whose Artisans want a grade I home and Burghers a grade II one, classes on; all put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32; // the house's origin is 24 blocks from the hall
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [{\"type\": \"home\", \"grade\": 1}]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [{\"type\": \"home\", \"grade\": 2}, "
			+ "{\"type\": \"village_rank\", \"rank\": \"city\"}]}"));
		SocialClasses.load(files);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return helper.absolutePos(HALL);
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	private static List<String> homeTips(ServerLevel level, BlockPos hall) {
		return VillageAdvice.tips(level, hall).stream().filter(t -> t.key().startsWith("grand_home"))
			.map(t -> t.title().getString() + " | " + t.how().getString()).toList();
	}

	/**
	 * A Burgher couple in a Stone House (grade I) get "Dara and Tomas are Burghers and want a grade II home"; an Artisan with
	 * no built home "Ann is an Artisan and wants a grade I home"; a Peasant with all an Artisan needs but the home "Bo
	 * could be an Artisan and wants a grade I home". The Steward's condition counts the same households, and the
	 * homes_class upgrade picks the couple's house. Once they sleep in a Townhouse their tip is gone.
	 */
	//$ gametest_batch AREA '"homeGradeTips"'
	@GameTest(template = AREA, batch = "homeGradeTips")
	public void householdsWhoOutgrowTheirHomeAskForAGranderOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement house = placement(helper, new BlockPos(2, 2, 2));
		Leftovers.after(helper, () -> sites.forgetFinished(house));
		sites.recordFinished(StarterBlueprints.STONE_HOUSE.id(), house, UUID.randomUUID());
		Villager dara = villager(helper, new BlockPos(3, 2, 14), "Dara");
		Villager tomas = villager(helper, new BlockPos(5, 2, 14), "Tomas");
		Villager ann = villager(helper, new BlockPos(7, 2, 14), "Ann");
		Villager bo = villager(helper, new BlockPos(9, 2, 14), "Bo");
		marry(dara, tomas);
		sleepsAt(helper, dara, new BlockPos(5, 3, 5));
		sleepsAt(helper, tomas, new BlockPos(7, 3, 5));
		sleepsAt(helper, ann, new BlockPos(14, 2, 4)); // outside any built home
		for (Villager v : List.of(dara, tomas)) {
			SocialClasses.seed(v, SocialClasses.get(ours("burgher")));
		}
		SocialClasses.seed(ann, SocialClasses.get(ours("artisan")));
		SocialClasses.seed(bo, SocialClasses.get(ours("peasant")));

		List<String> tips = homeTips(level, hall);
		String how = "Upgrade the house they sleep in, or have a builder put up a grander one and give them its beds: an Artisan's House is grade I "
			+ "and its upgrade II; a Burgher's Townhouse is grade II and its upgrades III";
		// (a couple is named lead first, the lead being the one with the lower UUID)
		helper.assertTrue(tips.contains("Dara and Tomas are Burghers and want a grade II home | " + how)
			|| tips.contains("Tomas and Dara are Burghers and want a grade II home | " + how), "the couple's tip: " + tips);
		helper.assertTrue(tips.contains("Ann is an Artisan and wants a grade I home | " + how), "Ann's tip: " + tips);
		helper.assertTrue(tips.contains("Bo could be an Artisan and wants a grade I home | They have everything else that class needs. " + how),
			"Bo's tip: " + tips);
		helper.assertTrue(tips.size() == 3, "three tips: " + tips);

		// The Steward's condition counts the same households
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		StewardConditions.Check two = new StewardConditions.HouseholdsWantGrade(2, 1).test(facts);
		StewardConditions.Check one = new StewardConditions.HouseholdsWantGrade(1, 1).test(facts);
		helper.assertTrue(two.held() && two.value() == 1, "one household wants grade II: " + two);
		helper.assertTrue(one.held() && one.value() == 2, "two want grade I and have less (Ann and Bo; the couple have I): " + one);
		helper.assertTrue(!new StewardConditions.HouseholdsWantGrade(2, 2).test(facts).held(), "at_least 2 of grade II doesn't hold with one");
		helper.assertTrue(two.shown().getString().equals("Households wanting a grade II home: 1 (at least 1)"), "explain line: " + two.shown().getString());
		// homes_class: upgrade the couple's Stone House (its II is grade II, more than their I)
		Optional<BuildSiteManager.Finished> up = StewardDesk.upgradeFor(level, hall, Optional.empty(), false, true);
		helper.assertTrue(up.isPresent() && up.get().placement().equals(house), "homes_class upgrades the couple's house: " + up);

		// Living in a Townhouse, the couple want nothing more; the Steward has nothing to upgrade for a class
		sites.recordFinished(StarterBlueprints.TOWNHOUSE.id(), house, UUID.randomUUID());
		List<String> after = homeTips(level, hall);
		helper.assertTrue(after.stream().noneMatch(t -> t.contains("Dara")) && after.size() == 2, "the couple's tip goes in a Townhouse: " + after);
		helper.assertTrue(new StewardConditions.HouseholdsWantGrade(2, 1).test(StewardConditions.Facts.of(level, hall)).value() == 0, "nobody wants grade II now");
		helper.assertTrue(StewardDesk.upgradeFor(level, hall, Optional.empty(), false, true).isEmpty(), "no class upgrade left");

		// With classes off there are no tips at all
		SocialClasses.ENABLED = false;
		helper.assertTrue(homeTips(level, hall).isEmpty(), "tips with classes off: " + homeTips(level, hall));
		helper.succeed();
	}

	/** The shipped rule files load: homes_class upgrades for a class, the two build rules build the Artisan's House and the Townhouse in Homes. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHomesClassRulesLoad(GameTestHelper helper) {
		Map<String, StewardRules.Rule> rules = new HashMap<>();
		for (StewardRules.Rule r : StewardRules.all()) {
			rules.put(r.id().getPath(), r);
		}
		StewardRules.Rule upgrade = rules.get("homes_class");
		helper.assertTrue(upgrade != null && upgrade.effect().kind() == StewardRules.Kind.UPGRADE && upgrade.effect().forClass()
			&& upgrade.when().get(0) instanceof StewardConditions.HouseholdsWantGrade g && g.grade() == 2, "homes_class: " + upgrade);
		StewardRules.Rule artisans = rules.get("homes_class_artisans_house");
		helper.assertTrue(artisans != null && artisans.effect().blueprint().equals(Optional.of(StarterBlueprints.ARTISANS_HOUSE.id()))
			&& artisans.effect().zone().equals(Optional.of("homes")), "homes_class_artisans_house: " + artisans);
		StewardRules.Rule townhouse = rules.get("homes_class_townhouse");
		helper.assertTrue(townhouse != null && townhouse.effect().blueprint().equals(Optional.of(StarterBlueprints.TOWNHOUSE.id()))
			&& townhouse.effect().zone().equals(Optional.of("homes")), "homes_class_townhouse: " + townhouse);
		helper.assertTrue(upgrade.effect().describe().getString().equals("upgrade the home of a household that wants a grander one"),
			"the effect's words: " + upgrade.effect().describe().getString());
		// a saved proposal's effect without for_class still loads (old saves)
		StewardRules.Effect old = StewardRules.Effect.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
			JsonParser.parseString("{\"type\": \"upgrade\", \"adds_beds\": true}")).getOrThrow();
		helper.assertTrue(old.addsBeds() && !old.forClass(), "an old effect loads without for_class");
		helper.succeed();
	}

	/**
	 * A Novice builder sells neither; an Apprentice sells the Artisan's House; the Townhouse only once the village has a
	 * household of Artisans; neither is added twice.
	 */
	//$ gametest_batch AREA '"homeGradeTrades"'
	@GameTest(template = AREA, batch = "homeGradeTrades")
	public void buildersSellTheGranderHomes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Villager builder = villager(helper, new BlockPos(10, 2, 10), "Builder");
		builder.setVillagerData(new VillagerData(builder.getVillagerData().getType(), ModVillagers.BUILDER, 1));
		builder.getOffers().clear();
		ModTrades.builderHomes(builder);
		helper.assertTrue(!UpgradeOffers.sells(builder, StarterBlueprints.ARTISANS_HOUSE.id()) && !UpgradeOffers.sells(builder, StarterBlueprints.TOWNHOUSE.id()),
			"a Novice sells neither");
		builder.setVillagerData(builder.getVillagerData().setLevel(2));
		ModTrades.builderHomes(builder);
		helper.assertTrue(UpgradeOffers.sells(builder, StarterBlueprints.ARTISANS_HOUSE.id()), "an Apprentice sells the Artisan's House");
		helper.assertTrue(!UpgradeOffers.sells(builder, StarterBlueprints.TOWNHOUSE.id()), "no Townhouse without an Artisan household");
		Villager peasant = villager(helper, new BlockPos(12, 2, 10), "Pip");
		SocialClasses.seed(peasant, SocialClasses.get(ours("peasant")));
		ModTrades.builderHomes(builder);
		helper.assertTrue(!UpgradeOffers.sells(builder, StarterBlueprints.TOWNHOUSE.id()), "a Peasant household isn't enough");
		Villager artisan = villager(helper, new BlockPos(14, 2, 10), "Ann");
		SocialClasses.seed(artisan, SocialClasses.get(ours("artisan")));
		ModTrades.builderHomes(builder);
		ModTrades.builderHomes(builder);
		helper.assertTrue(UpgradeOffers.sells(builder, StarterBlueprints.TOWNHOUSE.id()), "the Townhouse once there are Artisans");
		long offers = builder.getOffers().stream().filter(o -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(o.getResult()).isPresent()).count();
		helper.assertTrue(offers == 2, "each sold once: " + offers);
		helper.assertTrue(builder.getOffers().stream().anyMatch(o -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(StarterBlueprints.TOWNHOUSE.id())).orElse(false) && o.getBaseCostA().getCount() == ModTrades.TOWNHOUSE_PRICE),
			"the Townhouse costs " + ModTrades.TOWNHOUSE_PRICE);
		// A builder of another job sells nothing
		Villager mason = villager(helper, new BlockPos(16, 2, 10), "Mason");
		mason.setVillagerData(new VillagerData(mason.getVillagerData().getType(), net.minecraft.world.entity.npc.VillagerProfession.MASON, 3));
		mason.getOffers().clear();
		ModTrades.builderHomes(mason);
		helper.assertTrue(mason.getOffers().isEmpty(), "a Mason sells no homes");
		helper.succeed();
	}

	/**
	 * ROADMAP 34.15a (the owner, 2026-10-12): the Artisan's House and the Townhouse are sold by villagers only. No tier of
	 * either is in the Blueprint Table's list or handed out by it (nor the Manor's since 34.15b, see ManorGameTests), the
	 * cottage still is; a builder's Artisan's House
	 * blueprint, bought, sets out on the ground; the Steward's rules for both still find their blueprints.
	 */
	//$ gametest_batch AREA '"homeGradeSoldOnly"'
	@GameTest(template = AREA, batch = "homeGradeSoldOnly")
	public void theGranderHomesAreSoldNotInTheTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		List<ResourceLocation> table = io.github.jcondedata.aliveworkplace.table.TableServer.listing(server).stream()
			.map(io.github.jcondedata.aliveworkplace.table.TablePayloads.Entry::id).toList();
		List<ResourceLocation> everything = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.list(server, true);
		helper.assertTrue(StarterBlueprints.SOLD_ONLY.size() == 8, "every tier of the three grander homes: " + StarterBlueprints.SOLD_ONLY);
		for (StarterBlueprints.Entry e : StarterBlueprints.SOLD_ONLY) {
			helper.assertFalse(table.contains(e.id()), e.id() + " is in the Blueprint Table");
			helper.assertTrue(everything.contains(e.id()), e.id() + " should still load for operators");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(server, e.id()).isPresent(), e.id() + " no longer loads");
		}
		helper.assertTrue(table.contains(StarterBlueprints.STARTER_COTTAGE.id()), "the cottage stays in the Table");

		// Asked for anyway (a request the screen never sends), the Table refuses and keeps the Blank Blueprint.
		BlockPos tablePos = new BlockPos(3, 2, 3);
		helper.setBlock(tablePos, ModBlocks.BLUEPRINT_TABLE);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			BlockPos at = helper.absolutePos(tablePos);
			player.moveTo(new net.minecraft.world.phys.Vec3(at.getX() + 1.5, at.getY(), at.getZ() + 0.5));
			player.getInventory().add(new net.minecraft.world.item.ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT, 2));
			helper.assertFalse(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, StarterBlueprints.TOWNHOUSE.id()), "the Table handed out a Townhouse");
			helper.assertFalse(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, BlueprintStyles.styled(StarterBlueprints.ARTISANS_HOUSE_2.id(), "stonework")),
				"the Table handed out a styled Artisan's House II");
			helper.assertTrue(player.getInventory().countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT) == 2, "a Blank Blueprint was used up");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, StarterBlueprints.STARTER_COTTAGE.id()), "the cottage still comes from the Table");
			helper.assertTrue(Component.translatable("message.aliveworkplace.table.sold_only", "x").getString().equals("The x isn't in the Blueprint Table: a builder sells it"),
				"the refusal's words: " + Component.translatable("message.aliveworkplace.table.sold_only", "x").getString());

			// Bought from an Apprentice builder, the Artisan's House sets out on the ground like any blueprint.
			Villager builder = villager(helper, new BlockPos(10, 2, 10), "Builder");
			builder.setVillagerData(new VillagerData(builder.getVillagerData().getType(), ModVillagers.BUILDER, 2));
			builder.getOffers().clear();
			ModTrades.builderHomes(builder);
			net.minecraft.world.item.ItemStack bought = builder.getOffers().stream().map(o -> o.getResult().copy())
				.filter(r -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(r).map(d -> d.structure().equals(StarterBlueprints.ARTISANS_HOUSE.id())).orElse(false))
				.findFirst().orElse(null);
			helper.assertTrue(bought != null, "the Apprentice sells no Artisan's House: " + builder.getOffers().size());
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bought);
			BlockPos ground = helper.absolutePos(new BlockPos(14, 1, 4));
			bought.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground), net.minecraft.core.Direction.UP, ground, false)));
			helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement).isPresent(),
				"the bought Artisan's House didn't set out: " + io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(player.getMainHandItem()));
		} finally {
			server.getPlayerList().remove(player);
		}

		// The Steward's rules still build them: their blueprints load by id.
		for (StewardRules.Rule r : StewardRules.all()) {
			String path = r.id().getPath();
			if (path.equals("homes_class_artisans_house") || path.equals("homes_class_townhouse")) {
				helper.assertTrue(r.effect().blueprint().flatMap(id -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(server, id)).isPresent(),
					path + "'s blueprint doesn't load");
			}
		}
		helper.succeed();
	}
}
