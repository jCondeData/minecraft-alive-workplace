package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.people.Households;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The class engine (ROADMAP 34.2, docs/design/M34.md): the class files and their exact ladder, each need type on its own,
 * households rising after {@code classRiseDays} dawns and falling after {@code classFallDays}, one step a day, children
 * following their household, a data pack changing a need, save and reload, and the hall round's cost with 60
 * households. Each test that changes the shared ladder, hooks or settings runs in a batch of its own and puts them back.
 */
public class ClassGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	/** How far above the test area recorded buildings stand (clear of the Homes tests' 100). */
	private static final int SKY = 140;

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	/** A Village Hall at {@link #HALL} (reach 16), classes on; everything shared put back when the test ends. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply(); // classes off again (GameTests), rise and fall days back to 2 and 3
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK; // the real list back (34.3)
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK; // the real files back (34.4)
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static void marry(ServerLevel level, Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	private static void sleepsAt(ServerLevel level, Villager villager, BlockPos bed) {
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
	}

	private static void eats(Villager villager, String... meals) {
		ModAttachments.RECENT_MEALS.set(villager, Arrays.stream(meals).map(ResourceLocation::withDefaultNamespace).toList());
	}

	private static BlockPos sky(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(2, 2, 2)).above(SKY);
	}

	/** A blueprint of {@code size} with nothing in it (only its size matters to a home). */
	private static ResourceLocation emptyBlueprint(GameTestHelper helper, String path, Vec3i size) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", path);
		helper.getLevel().getStructureManager().getOrCreate(id).fillFromWorld(helper.getLevel(), sky(helper).above(40), size, false, Blocks.AIR);
		return id;
	}

	/** Records a finished build of {@code id} at {@code origin}, forgotten when the test ends. */
	private static void finished(GameTestHelper helper, ResourceLocation id, BlockPos origin) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	private static ClassNeeds.Village at(GameTestHelper helper, long today) {
		return new ClassNeeds.Village(helper.getLevel(), helper.absolutePos(HALL), today);
	}

	private static ClassNeeds.Need need(String json) {
		return ClassNeeds.read(JsonParser.parseString(json));
	}

	private static String id(Villager v) {
		ResourceLocation c = ModAttachments.SOCIAL_CLASS.get(v);
		return c == null ? "none" : c.getPath();
	}

	/** A ladder of four classes for the dawn tests: Artisan needs a varied diet, Burgher a plain one, Noble a Town. */
	private static void testLadder(String artisanNeed, String burgherNeed, String nobleNeed) {
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("peasant"), JsonParser.parseString("{\"tier\": 0, \"tax\": 1}"));
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 1.5, \"needs\": [" + artisanNeed + "]}"));
		files.put(ours("burgher"), JsonParser.parseString("{\"tier\": 2, \"tax\": 2.5, \"needs\": [" + burgherNeed + "]}"));
		files.put(ours("noble"), JsonParser.parseString("{\"tier\": 3, \"tax\": 4, \"needs\": [" + nobleNeed + "]}"));
		SocialClasses.load(files);
	}

	private static SocialClasses.SocialClass cls(String path) {
		return SocialClasses.get(ours(path));
	}

	// The class files.

	/** Our four files load as the design note's ladder: tiers, taxes, outfits and every need, want and job. */
	//$ gametest_batch AREA '"classFiles"'
	@GameTest(template = AREA, batch = "classFiles")
	public void theFourClassFilesAreTheLadder(GameTestHelper helper) {
		village(helper);
		SocialClasses.load(SocialClasses.files(helper.getLevel().getServer().getResourceManager()));
		List<String> ladder = SocialClasses.ladder().stream().map(c -> c.id().getPath() + " " + c.tier() + " x" + c.tax()).toList();
		helper.assertTrue(ladder.equals(List.of("peasant 0 x1.0", "artisan 1 x1.5", "burgher 2 x2.5", "noble 3 x4.0")), "the ladder: " + ladder);
		SocialClasses.SocialClass peasant = cls("peasant");
		helper.assertTrue(peasant.needs().isEmpty() && peasant.wants().isEmpty() && peasant.jobs().isEmpty() && peasant.outfit().isEmpty(),
			"a Peasant needs nothing: " + peasant);
		helper.assertTrue(peasant.name().getString().equals("Peasant"), "named " + peasant.name().getString());
		SocialClasses.SocialClass artisan = cls("artisan");
		helper.assertTrue(artisan.needs().equals(List.of(new ClassNeeds.Home(1), new ClassNeeds.FedDays(3), new ClassNeeds.DietNeed(Diet.Kind.VARIED),
			new ClassNeeds.Services(1, List.of(ours("chapel"), ours("school"), ours("clinic")), List.of()), new ClassNeeds.Luxury(ours("work_clothes")))),
			"the Artisan's needs: " + artisan.needs());
		helper.assertTrue(artisan.wants().equals(List.of(new ClassNeeds.Luxury(ours("cider")), new ClassNeeds.Services(1, List.of(ours("tavern")), List.of()))),
			"the Artisan's wants: " + artisan.wants());
		helper.assertTrue(artisan.jobs().size() == 12 && artisan.jobs().contains(ours("teacher")) && artisan.effects().size() == 1,
			"the Artisan's jobs and perk: " + artisan.jobs() + " " + artisan.effects());
		SocialClasses.SocialClass burgher = cls("burgher");
		helper.assertTrue(burgher.needs().equals(List.of(new ClassNeeds.Home(2), new ClassNeeds.FedDays(3), new ClassNeeds.DietNeed(Diet.Kind.VARIED),
			new ClassNeeds.Services(0, List.of(), List.of(ours("school"))),
			new ClassNeeds.Services(2, List.of(ours("school"), ours("chapel"), ours("clinic"), ours("library"), ours("tavern")), List.of()),
			new ClassNeeds.Building(StarterBlueprints.MARKET_SQUARE.id(), 1),
			new ClassNeeds.Luxury(ours("fine_clothes")), new ClassNeeds.Luxury(ours("berry_wine")), new ClassNeeds.Luxury(ours("gazette")))),
			"the Burgher's needs: " + burgher.needs());
		helper.assertTrue(burgher.wants().equals(List.of(new ClassNeeds.Luxury(ours("amethyst_ring")), new ClassNeeds.Services(0, List.of(), List.of(ours("library"))),
			new ClassNeeds.Beauty(2))), "the Burgher's wants: " + burgher.wants());
		helper.assertTrue(burgher.jobs().equals(List.of(ours("scholar"), ours("undertaker"), ours("jeweller"), ours("trainer_leader"))), "the Burgher's jobs: " + burgher.jobs());
		SocialClasses.SocialClass noble = cls("noble");
		helper.assertTrue(noble.needs().equals(List.of(new ClassNeeds.Home(3), new ClassNeeds.FedDays(3), new ClassNeeds.DietNeed(Diet.Kind.VARIED),
			new ClassNeeds.Services(0, List.of(), List.of(ours("chapel"), ours("school"), ours("clinic"), ours("library"))),
			new ClassNeeds.VillageRank(VillageRanks.Rank.TOWN), new ClassNeeds.Beauty(3),
			new ClassNeeds.Luxury(ours("noble_robes")), new ClassNeeds.Luxury(ours("vintage_wine")), new ClassNeeds.Luxury(ours("emerald_brooch")))),
			"the Noble's needs: " + noble.needs());
		helper.assertTrue(noble.wants().equals(List.of(new ClassNeeds.Luxury(ours("illuminated_book")), new ClassNeeds.Luxury(ours("gold_circlet")))),
			"the Noble's wants: " + noble.wants());
		helper.assertTrue(noble.jobs().isEmpty() && noble.effects().size() == 2 && noble.outfit().isPresent(), "the Noble's jobs and perks: " + noble);
		helper.succeed();
	}

	/** A data pack's {@code classes/artisan.json} replaces ours: its need changes; {@code "enabled": false} takes a class off. */
	//$ gametest_batch AREA '"classDatapack"'
	@GameTest(template = AREA, batch = "classDatapack")
	public void aDatapackClassFileChangesANeed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Map<ResourceLocation, JsonElement> files = new HashMap<>(SocialClasses.files(level.getServer().getResourceManager()));
		helper.assertTrue(files.keySet().containsAll(List.of(ours("peasant"), ours("artisan"), ours("burgher"), ours("noble"))), "our files: " + files.keySet());
		// The pack's artisan.json, at the same path: fed 5 days, and a plain diet is enough.
		files.put(ours("artisan"), JsonParser.parseString("{\"tier\": 1, \"tax\": 2, \"name\": \"Craftsfolk\", \"needs\": ["
			+ "{\"type\": \"fed_days\", \"days\": 5}, {\"type\": \"diet\", \"kind\": \"plain\"}]}"));
		files.put(ours("noble"), JsonParser.parseString("{\"tier\": 3, \"enabled\": false}"));
		files.put(ResourceLocation.fromNamespaceAndPath("somepack", "broken"), JsonParser.parseString("{\"tier\": 5, \"needs\": [{\"type\": \"home\"}]}"));
		SocialClasses.load(files);
		SocialClasses.SocialClass artisan = cls("artisan");
		helper.assertTrue(artisan.needs().equals(List.of(new ClassNeeds.FedDays(5), new ClassNeeds.DietNeed(Diet.Kind.PLAIN))), "the pack's needs: " + artisan.needs());
		helper.assertTrue(artisan.tax() == 2f && artisan.name().getString().equals("Craftsfolk"), "the pack's tax and name: " + artisan);
		List<String> ladder = SocialClasses.ladder().stream().map(c -> c.id().getPath()).toList();
		helper.assertTrue(ladder.equals(List.of("peasant", "artisan", "burgher")), "noble switched off, the broken file skipped: " + ladder);

		// The new need is the one checked: a household fed 3 days on a plain diet rises now (not before).
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Ada");
		SocialClasses.seed(v, cls("peasant"));
		eats(v, "bread", "bread", "carrot");
		ModAttachments.LAST_MEAL.set(v, level.getGameTime());
		for (long day = 1; day <= 3; day++) {
			SocialClasses.check(level, helper.absolutePos(HALL), List.of(v), day, 8);
		}
		helper.assertTrue(id(v).equals("peasant"), "fed 3 days isn't 5: " + id(v));
		for (long day = 4; day <= 6; day++) {
			SocialClasses.check(level, helper.absolutePos(HALL), List.of(v), day, 8);
		}
		helper.assertTrue(id(v).equals("artisan"), "fed 5 days running and 2 dawns of it: " + id(v) + " " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	// Each need type on its own.

	/** {@code home}: the grade of the building their bed is in (a {@code _2} house is II); no bed or no builder's house is 0. */
	//$ gametest_batch AREA '"classNeedHome"'
	@GameTest(template = AREA, batch = "classNeedHome")
	public void homeNeedGoesByTheirBuildingsGrade(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		ResourceLocation house = emptyBlueprint(helper, "class_house_2", new Vec3i(6, 5, 6));
		finished(helper, house, sky(helper));
		Villager housed = villager(helper, new BlockPos(4, 2, 4), "Bram");
		Villager homeless = villager(helper, new BlockPos(8, 2, 4), "Cora");
		Villager outside = villager(helper, new BlockPos(12, 2, 4), "Dov");
		sleepsAt(level, housed, sky(helper).offset(2, 1, 2));
		sleepsAt(level, outside, sky(helper).offset(20, 1, 20));
		ClassNeeds.Village v = at(helper, 1);
		helper.assertTrue(ClassNeeds.holds(need("{\"type\": \"home\", \"grade\": 2}"), housed, v), "a bed in a grade II house is grade II");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"home\", \"grade\": 3}"), housed, v), "grade II isn't III");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"home\", \"grade\": 1}"), homeless, v), "no bed is no home");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"home\", \"grade\": 1}"), outside, v), "a bed in no builder's house is grade 0");
		helper.assertTrue(ClassNeeds.holds(need("{\"type\": \"home\", \"grade\": 0}"), homeless, v), "grade 0 is anyone's");
		List.of(housed, homeless, outside).forEach(Villager::discard);
		helper.succeed();
	}

	/** {@code fed_days}: each dawn fed (ate within the day) adds one; a hungry dawn starts again. */
	//$ gametest_batch AREA '"classNeedFed"'
	@GameTest(template = AREA, batch = "classNeedFed")
	public void fedDaysCountsDawnsFedRunning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Edda");
		SocialClasses.seed(v, cls("peasant"));
		ClassNeeds.Need fed3 = need("{\"type\": \"fed_days\", \"days\": 3}");
		BlockPos hall = helper.absolutePos(HALL);
		ModAttachments.LAST_MEAL.set(v, level.getGameTime());
		SocialClasses.check(level, hall, List.of(v), 1, 8);
		SocialClasses.check(level, hall, List.of(v), 2, 8);
		helper.assertTrue(!ClassNeeds.holds(fed3, v, at(helper, 2)), "fed 2 dawns isn't 3: " + SocialClasses.progress(v));
		SocialClasses.check(level, hall, List.of(v), 2, 8);
		helper.assertTrue(SocialClasses.progress(v).fed() == 2, "a second check the same day counted again: " + SocialClasses.progress(v));
		SocialClasses.check(level, hall, List.of(v), 3, 8);
		helper.assertTrue(ClassNeeds.holds(fed3, v, at(helper, 3)), "fed 3 dawns running: " + SocialClasses.progress(v));
		ModAttachments.LAST_MEAL.set(v, level.getGameTime() - 2 * VillageNeeds.DAY);
		SocialClasses.check(level, hall, List.of(v), 4, 8);
		helper.assertTrue(SocialClasses.progress(v).fed() == 0 && !ClassNeeds.holds(fed3, v, at(helper, 4)), "a hungry dawn starts again: " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	/** {@code diet}: varied (3 kinds of the last meals) holds for varied and plain; plain only for plain; one food or too few meals for neither. */
	//$ gametest_batch AREA '"classNeedDiet"'
	@GameTest(template = AREA, batch = "classNeedDiet")
	public void dietNeedGoesByWhatTheyAte(GameTestHelper helper) {
		village(helper);
		ClassNeeds.Need varied = need("{\"type\": \"diet\", \"kind\": \"varied\"}");
		ClassNeeds.Need plain = need("{\"type\": \"diet\", \"kind\": \"plain\"}");
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Finn");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Gale");
		Villager c = villager(helper, new BlockPos(8, 2, 4), "Hal");
		Villager d = villager(helper, new BlockPos(10, 2, 4), "Ione");
		eats(a, "bread", "baked_potato", "cooked_cod");
		eats(b, "bread", "bread", "baked_potato");
		eats(c, "bread", "bread", "bread", "bread");
		eats(d, "bread", "cooked_cod");
		ClassNeeds.Village v = at(helper, 1);
		helper.assertTrue(ClassNeeds.holds(varied, a, v) && ClassNeeds.holds(plain, a, v), "a varied diet holds for both");
		helper.assertTrue(!ClassNeeds.holds(varied, b, v) && ClassNeeds.holds(plain, b, v), "a plain diet only for plain");
		helper.assertTrue(!ClassNeeds.holds(varied, c, v) && !ClassNeeds.holds(plain, c, v), "bread alone for neither");
		helper.assertTrue(!ClassNeeds.holds(plain, d, v), "two meals aren't a diet yet");
		List.of(a, b, c, d).forEach(Villager::discard);
		helper.succeed();
	}

	/** {@code beauty}: the decorations finished within 16 blocks of their bed (a well is 2, a fountain 3); farther ones don't count. */
	//$ gametest_batch AREA '"classNeedBeauty"'
	@GameTest(template = AREA, batch = "classNeedBeauty")
	public void beautyNeedCountsDecorationsNearHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BlockPos bed = sky(helper);
		finished(helper, StarterBlueprints.WELL.id(), bed.offset(6, 0, 6));
		finished(helper, StarterBlueprints.FOUNTAIN.id(), bed.offset(30, 0, 0));
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Juno");
		sleepsAt(level, v, bed);
		helper.assertTrue(ClassNeeds.beauty(level, v) == 2, "beauty near home: " + ClassNeeds.beauty(level, v));
		helper.assertTrue(ClassNeeds.holds(need("{\"type\": \"beauty\", \"points\": 2}"), v, at(helper, 1)), "a well makes beauty 2");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"beauty\", \"points\": 3}"), v, at(helper, 1)), "the fountain 30 blocks off doesn't count");
		finished(helper, StarterBlueprints.FOUNTAIN.id(), bed.offset(-8, 0, 4));
		helper.assertTrue(ClassNeeds.holds(need("{\"type\": \"beauty\", \"points\": 5}"), v, at(helper, 1)), "a fountain near home: " + ClassNeeds.beauty(level, v));
		v.discard();
		helper.succeed();
	}

	/** {@code building}: a finished build of the blueprint in the village; a styled or upgraded one counts, and {@code tier} asks for an upgrade. */
	//$ gametest_batch AREA '"classNeedBuilding"'
	@GameTest(template = AREA, batch = "classNeedBuilding")
	public void buildingNeedLooksForAFinishedBuildInTheVillage(GameTestHelper helper) {
		village(helper);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Kit");
		ClassNeeds.Need market = need("{\"type\": \"building\", \"blueprint\": \"aliveworkplace:market_square\"}");
		ClassNeeds.Need well2 = need("{\"type\": \"building\", \"blueprint\": \"aliveworkplace:well\", \"tier\": 2}");
		ClassNeeds.Need well3 = need("{\"type\": \"building\", \"blueprint\": \"aliveworkplace:well\", \"tier\": 3}");
		helper.assertTrue(!ClassNeeds.holds(market, v, at(helper, 1)), "no Market Square yet");
		finished(helper, StarterBlueprints.MARKET_SQUARE.id(), helper.absolutePos(new BlockPos(20, 2, 20)));
		finished(helper, StarterBlueprints.WELL_2.id(), helper.absolutePos(new BlockPos(4, 2, 20)));
		finished(helper, StarterBlueprints.FOUNTAIN.id(), helper.absolutePos(HALL).offset(40, 0, 0));
		helper.assertTrue(ClassNeeds.holds(market, v, at(helper, 1)), "a Market Square in the village");
		helper.assertTrue(ClassNeeds.holds(well2, v, at(helper, 1)) && !ClassNeeds.holds(well3, v, at(helper, 1)), "an upgraded well is tier II, not III");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"building\", \"blueprint\": \"aliveworkplace:fountain\"}"), v, at(helper, 1)),
			"a fountain outside the village's reach doesn't count");
		v.discard();
		helper.succeed();
	}

	/** {@code village_rank}: the hall's rank or higher. */
	//$ gametest_batch AREA '"classNeedRank"'
	@GameTest(template = AREA, batch = "classNeedRank")
	public void villageRankNeedGoesByTheHallsRank(GameTestHelper helper) {
		VillageHallBlockEntity hall = village(helper);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Lio");
		ClassNeeds.Need town = need("{\"type\": \"village_rank\", \"rank\": \"town\"}");
		helper.assertTrue(!ClassNeeds.holds(town, v, at(helper, 1)), "a Hamlet isn't a Town");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(ClassNeeds.holds(town, v, at(helper, 1)), "a Town is a Town");
		hall.setRank(VillageRanks.Rank.CITY);
		helper.assertTrue(ClassNeeds.holds(town, v, at(helper, 1)) && ClassNeeds.holds(need("{\"type\": \"village_rank\", \"rank\": \"city\"}"), v, at(helper, 1)),
			"a City is a Town and a City");
		v.discard();
		helper.succeed();
	}

	/** {@code services}: none reach anyone in a village that has none; then {@code count} of {@code any} and every one of {@code all}. */
	//$ gametest_batch AREA '"classNeedServices"'
	@GameTest(template = AREA, batch = "classNeedServices")
	public void servicesNeedReadsTheHallsServiceList(GameTestHelper helper) {
		village(helper);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Mira");
		ClassNeeds.Need oneOf = need("{\"type\": \"services\", \"count\": 1, \"any\": [\"chapel\", \"school\", \"clinic\"]}");
		ClassNeeds.Need twoOf = need("{\"type\": \"services\", \"count\": 2, \"any\": [\"chapel\", \"clinic\", \"library\"]}");
		ClassNeeds.Need school = need("{\"type\": \"services\", \"all\": [\"school\"]}");
		ClassNeeds.Need library = need("{\"type\": \"services\", \"all\": [\"aliveworkplace:library\"]}");
		helper.assertTrue(!ClassNeeds.holds(oneOf, v, at(helper, 1)) && !ClassNeeds.holds(school, v, at(helper, 1)), "no services in the village yet");
		ClassNeeds.services = (level, hall, home) -> Set.of(ours("school"), ours("chapel"));
		helper.assertTrue(ClassNeeds.holds(oneOf, v, at(helper, 1)), "a school is one of chapel, school, clinic");
		helper.assertTrue(!ClassNeeds.holds(twoOf, v, at(helper, 1)), "a chapel is only one of chapel, clinic, library");
		helper.assertTrue(ClassNeeds.holds(school, v, at(helper, 1)) && !ClassNeeds.holds(library, v, at(helper, 1)), "all: school yes, library no");
		v.discard();
		helper.succeed();
	}

	/** {@code luxury}: never for a luxury with no file (34.4); then one had less than its {@code every_days} ago. */
	//$ gametest_batch AREA '"classNeedLuxury"'
	@GameTest(template = AREA, batch = "classNeedLuxury")
	public void luxuryNeedReadsLuxuriesHad(GameTestHelper helper) {
		village(helper);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Nell");
		ClassNeeds.Need clothes = need("{\"type\": \"luxury\", \"id\": \"aliveworkplace:work_clothes\"}");
		// (Work Clothes have their luxury file since 34.10, so the luxury with no file is one that never gets any)
		net.minecraft.resources.ResourceLocation unfiled = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "no_such_luxury");
		ClassNeeds.Need nothing = need("{\"type\": \"luxury\", \"id\": \"aliveworkplace_test:no_such_luxury\"}");
		ModAttachments.LUXURIES_HAD.set(v, Map.of(ours("work_clothes"), 20L, unfiled, 20L));
		helper.assertTrue(!ClassNeeds.holds(nothing, v, at(helper, 20)), "a luxury with no file held, had today");
		ClassNeeds.luxuryEvery = id -> id.equals(ours("work_clothes")) ? 8 : 0;
		helper.assertTrue(ClassNeeds.holds(clothes, v, at(helper, 27)), "had 7 days ago, every 8");
		helper.assertTrue(!ClassNeeds.holds(clothes, v, at(helper, 28)), "had 8 days ago, every 8: due again");
		ModAttachments.LUXURIES_HAD.remove(v);
		helper.assertTrue(!ClassNeeds.holds(clothes, v, at(helper, 21)), "never had one");
		helper.assertTrue(!ClassNeeds.holds(need("{\"type\": \"no_such_need\"}"), v, at(helper, 21)), "an unknown need never holds");
		v.discard();
		helper.succeed();
	}

	// The dawns.

	/** A married couple rises together after 2 dawns with the next class's needs; a couple where one lacks it doesn't. */
	//$ gametest_batch AREA '"classRise"'
	@GameTest(template = AREA, batch = "classRise")
	public void aCoupleRisesTogetherAfterTwoDawns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper);
		testLadder("{\"type\": \"diet\", \"kind\": \"varied\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}");
		Villager a = villager(helper, new BlockPos(4, 2, 4), "Odo");
		Villager b = villager(helper, new BlockPos(6, 2, 4), "Pia");
		Villager c = villager(helper, new BlockPos(4, 2, 8), "Quin");
		Villager d = villager(helper, new BlockPos(6, 2, 8), "Rue");
		Villager unseeded = villager(helper, new BlockPos(10, 2, 8), "Sol");
		marry(level, a, b);
		marry(level, c, d);
		for (Villager v : List.of(a, b, c, d)) {
			SocialClasses.seed(v, cls("peasant"));
		}
		for (Villager v : List.of(a, b, c, unseeded)) {
			eats(v, "bread", "baked_potato", "cooked_cod");
		}
		eats(d, "bread", "bread", "bread");
		helper.assertTrue(Households.of(List.of(a, b, c, d, unseeded)).size() == 3, "two couples and a single");
		BlockPos at = helper.absolutePos(HALL);
		SocialClasses.Result setup = SocialClasses.round(level, at, hall, 101);
		helper.assertTrue(setup != null && setup.counted() == 0 && SocialClasses.progress(a).day() == 0, "the day's first round only finds who to count: " + setup);
		List<SocialClasses.Change> first = wholeDay(level, at, hall, 101);
		helper.assertTrue(first.isEmpty() && id(a).equals("peasant") && SocialClasses.progress(a).met() == 1
			&& SocialClasses.progress(b).met() == 1, "one dawn isn't enough: " + id(a) + " " + SocialClasses.progress(a));
		List<SocialClasses.Change> second = wholeDay(level, at, hall, 102);
		helper.assertTrue(id(a).equals("artisan") && id(b).equals("artisan"), "after 2 dawns both rise: " + id(a) + ", " + id(b));
		helper.assertTrue(second.size() == 1 && second.get(0).who().size() == 2 && second.get(0).rose(), "one household rose: " + second);
		helper.assertTrue(id(c).equals("peasant") && id(d).equals("peasant") && SocialClasses.progress(c).met() == 0,
			"a need holds for a couple only when it holds for both: " + id(c) + " " + SocialClasses.progress(c));
		helper.assertTrue(!ModAttachments.SOCIAL_CLASS.has(unseeded), "the unseeded are left for seeding (34.22)");
		helper.assertTrue(SocialClasses.round(level, at, hall, 102) == null, "the day's check is done: the next round looks at nobody");

		// Classes off: nothing moves however long the needs hold.
		SocialClasses.ENABLED = false;
		for (long day = 103; day <= 108; day++) {
			wholeDay(level, at, hall, day);
		}
		helper.assertTrue(id(a).equals("artisan") && SocialClasses.progress(a).day() == 102, "villageClasses off moved a household: " + id(a));
		List.of(a, b, c, d, unseeded).forEach(Villager::discard);
		helper.succeed();
	}

	/** The hall's rounds on {@code day} until its check is done; the day's rises and falls. */
	private static List<SocialClasses.Change> wholeDay(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day) {
		List<SocialClasses.Change> changes = new ArrayList<>();
		for (int round = 0; round < 20; round++) {
			SocialClasses.Result r = SocialClasses.round(level, hall, entity, day);
			if (r == null) {
				break;
			}
			changes.addAll(r.changes());
		}
		return changes;
	}

	/** A household lacking a need of its own class for 3 dawns falls one class; a Peasant never falls further; a good dawn resets the count. */
	//$ gametest_batch AREA '"classFall"'
	@GameTest(template = AREA, batch = "classFall")
	public void aHouseholdFallsAfterThreeDawns(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		testLadder("{\"type\": \"diet\", \"kind\": \"varied\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}");
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Tam");
		SocialClasses.seed(v, cls("artisan"));
		eats(v, "bread", "bread", "bread");
		BlockPos hall = helper.absolutePos(HALL);
		SocialClasses.check(level, hall, List.of(v), 11, 8);
		SocialClasses.check(level, hall, List.of(v), 12, 8);
		eats(v, "bread", "carrot", "apple");
		SocialClasses.check(level, hall, List.of(v), 13, 8);
		helper.assertTrue(SocialClasses.progress(v).missed() == 0 && id(v).equals("artisan"), "a good dawn starts the count again: " + SocialClasses.progress(v));
		eats(v, "bread", "bread", "bread");
		SocialClasses.check(level, hall, List.of(v), 14, 8);
		SocialClasses.check(level, hall, List.of(v), 15, 8);
		helper.assertTrue(id(v).equals("artisan") && SocialClasses.progress(v).missed() == 2, "2 dawns aren't 3: " + id(v) + " " + SocialClasses.progress(v));
		SocialClasses.Result fall = SocialClasses.check(level, hall, List.of(v), 16, 8);
		helper.assertTrue(id(v).equals("peasant") && fall.changes().size() == 1 && !fall.changes().get(0).rose(), "3 dawns: falls to Peasant: " + id(v));
		for (long day = 17; day <= 25; day++) {
			SocialClasses.check(level, hall, List.of(v), day, 8);
		}
		helper.assertTrue(id(v).equals("peasant") && SocialClasses.progress(v).missed() == 0, "never below Peasant: " + id(v) + " " + SocialClasses.progress(v));
		v.discard();
		helper.succeed();
	}

	/** With every class's needs met and {@code classRiseDays} 1, a household still climbs one class a dawn, never two. */
	//$ gametest_batch AREA '"classOneStep"'
	@GameTest(template = AREA, batch = "classOneStep")
	public void oneStepADayAtMost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		WorkplaceConfig config = WorkplaceConfig.parse("{\"classRiseDays\": 1}");
		config.apply();
		SocialClasses.ENABLED = true;
		helper.assertTrue(SocialClasses.RISE_DAYS == 1 && SocialClasses.FALL_DAYS == 3, "the config's days: " + SocialClasses.RISE_DAYS + " " + SocialClasses.FALL_DAYS);
		String plain = "{\"type\": \"diet\", \"kind\": \"plain\"}";
		testLadder(plain, plain, plain);
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Uma");
		SocialClasses.seed(v, cls("peasant"));
		eats(v, "bread", "carrot", "apple");
		BlockPos hall = helper.absolutePos(HALL);
		List<String> seen = new ArrayList<>();
		for (long day = 31; day <= 34; day++) {
			SocialClasses.check(level, hall, List.of(v), day, 8);
			SocialClasses.check(level, hall, List.of(v), day, 8); // a second round the same day changes nothing
			seen.add(id(v));
		}
		helper.assertTrue(seen.equals(List.of("artisan", "burgher", "noble", "noble")), "one step a dawn: " + seen);
		v.discard();
		helper.succeed();
	}

	/** Children take the class of the grown-ups who sleep in their building; a child in another building keeps theirs. */
	//$ gametest_batch AREA '"classChildren"'
	@GameTest(template = AREA, batch = "classChildren")
	public void childrenFollowTheirHousehold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		testLadder("{\"type\": \"diet\", \"kind\": \"varied\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}", "{\"type\": \"village_rank\", \"rank\": \"city\"}");
		ResourceLocation house = emptyBlueprint(helper, "class_family_house", new Vec3i(6, 5, 6));
		BlockPos home = sky(helper);
		BlockPos other = sky(helper).offset(12, 0, 0);
		finished(helper, house, home);
		finished(helper, house, other);
		Villager mother = villager(helper, new BlockPos(4, 2, 4), "Vera");
		Villager father = villager(helper, new BlockPos(6, 2, 4), "Wyn");
		Villager child = villager(helper, new BlockPos(5, 2, 6), "Xan");
		Villager neighboursChild = villager(helper, new BlockPos(9, 2, 6), "Yara");
		child.setBaby(true);
		neighboursChild.setBaby(true);
		marry(level, mother, father);
		sleepsAt(level, mother, home.offset(1, 1, 1));
		sleepsAt(level, father, home.offset(2, 1, 1));
		sleepsAt(level, child, home.offset(4, 1, 4));
		sleepsAt(level, neighboursChild, other.offset(2, 1, 2));
		SocialClasses.seed(mother, cls("artisan"));
		SocialClasses.seed(father, cls("artisan"));
		eats(mother, "bread", "carrot", "apple");
		eats(father, "bread", "carrot", "apple");
		List<Villager> all = List.of(mother, father, child, neighboursChild);
		SocialClasses.Result r = SocialClasses.check(level, helper.absolutePos(HALL), all, 41, 8);
		helper.assertTrue(r.finished() && r.counted() == 3, "one household and two children counted: " + r);
		helper.assertTrue(id(child).equals("artisan"), "the child takes the household's class: " + id(child));
		helper.assertTrue(id(neighboursChild).equals("none"), "a child in another building doesn't: " + id(neighboursChild));
		SocialClasses.seed(mother, cls("burgher"));
		SocialClasses.check(level, helper.absolutePos(HALL), all, 42, 8);
		helper.assertTrue(id(child).equals("burgher") && id(father).equals("burgher"), "the next dawn they follow again: " + id(child) + " " + id(father));
		all.forEach(Villager::discard);
		helper.succeed();
	}

	/** A save and reload keeps class, progress and the luxuries had; a villager saved before 1.8 loads with none. */
	//$ gametest_batch AREA '"classSave"'
	@GameTest(template = AREA, batch = "classSave")
	public void saveAndReloadKeepsClassAndProgress(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Villager old = villager(helper, new BlockPos(8, 2, 4), "Zed");
		CompoundTag oldTag = old.saveWithoutId(new CompoundTag());
		old.discard();
		Villager v = villager(helper, new BlockPos(4, 2, 4), "Abel");
		ModAttachments.SOCIAL_CLASS.set(v, ours("burgher"));
		ModAttachments.CLASS_PROGRESS.set(v, new SocialClasses.Progress(1, 2, 3, 57));
		ModAttachments.LUXURIES_HAD.set(v, Map.of(ours("berry_wine"), 55L));
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		helper.assertTrue(SocialClasses.of(copy) == cls("burgher"), "class after a reload: " + ModAttachments.SOCIAL_CLASS.get(copy));
		helper.assertTrue(SocialClasses.progress(copy).equals(new SocialClasses.Progress(1, 2, 3, 57)), "progress after a reload: " + SocialClasses.progress(copy));
		helper.assertTrue(ModAttachments.LUXURIES_HAD.getOrElse(copy, Map.of()).equals(Map.of(ours("berry_wine"), 55L)), "luxuries after a reload");
		Villager before = EntityType.VILLAGER.create(level);
		before.load(oldTag);
		helper.assertTrue(SocialClasses.of(before) == null && SocialClasses.progress(before).equals(SocialClasses.Progress.NONE)
			&& !ModAttachments.LUXURIES_HAD.has(before), "a villager saved before 1.8 has no class yet");
		// A class a data pack took away since: they count as Peasant, and their saved id is kept.
		ModAttachments.SOCIAL_CLASS.set(before, ResourceLocation.fromNamespaceAndPath("somepack", "gone"));
		helper.assertTrue(SocialClasses.of(before) == cls("peasant") && ModAttachments.SOCIAL_CLASS.get(before).getPath().equals("gone"), "a lost class is the floor");
		copy.discard();
		before.discard();
		helper.succeed();
	}

	// The cost.

	/** 60 households (20 couples, 40 singles) round a hall: each hall round of the dawn check, 8 households, takes under 2 ms. */
	//$ gametest_ticks_batch HUGE '400' '"classCost"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "classCost")
	public void sixtyHouseholdsTakeUnderTwoMsARound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassNeeds.services = io.github.jcondedata.aliveworkplace.hall.Services.HOOK; // the real list back (34.3)
			ClassNeeds.luxuryEvery = io.github.jcondedata.aliveworkplace.people.Luxuries.HOOK; // the real files back (34.4)
			SocialClasses.forget();
		});
		BlockPos hallRel = new BlockPos(15, 2, 15);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos hallPos = helper.absolutePos(hallRel);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(hallPos);
		hall.setRank(VillageRanks.Rank.TOWN);
		// Everything a class can ask for is there, so every need is looked at (nothing stops at the first one unmet).
		ClassNeeds.services = (l, h, home) -> Set.of(ours("chapel"), ours("school"), ours("clinic"), ours("library"), ours("market"), ours("tavern"));
		ClassNeeds.luxuryEvery = id -> 8;
		ResourceLocation house = emptyBlueprint(helper, "class_row_house_3", new Vec3i(8, 5, 8));
		List<BlockPos> houses = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			BlockPos origin = sky(helper).offset((i % 5) * 10, 0, (i / 5) * 10);
			finished(helper, house, origin);
			finished(helper, StarterBlueprints.FOUNTAIN.id(), origin.offset(4, 0, 9));
			houses.add(origin);
		}
		finished(helper, StarterBlueprints.MARKET_SQUARE.id(), helper.absolutePos(new BlockPos(20, 2, 20)));
		Map<ResourceLocation, Long> luxuries = new HashMap<>();
		for (String l : List.of("work_clothes", "fine_clothes", "berry_wine", "gazette", "noble_robes", "vintage_wine", "emerald_brooch")) {
			luxuries.put(ours(l), 1000L);
		}
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 80; i++) {
			Villager v = villager(helper, new BlockPos(2 + (i % 9) * 3, 2, 2 + (i / 9) * 3), "V" + i);
			BlockPos origin = houses.get(i % 10);
			sleepsAt(level, v, origin.offset(1 + (i / 10) % 6, 1, 1 + (i / 60)));
			eats(v, "bread", "carrot", "apple");
			ModAttachments.LAST_MEAL.set(v, level.getGameTime());
			ModAttachments.LUXURIES_HAD.set(v, luxuries);
			SocialClasses.seed(v, cls(i % 2 == 0 ? "peasant" : "artisan"));
			people.add(v);
		}
		for (int i = 0; i < 40; i += 2) {
			marry(level, people.get(i), people.get(i + 1));
		}
		long households = Households.of(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hallPos), Villager::isAlive)).size();
		helper.assertTrue(households == 60, "60 households, found " + households);
		// A first day warms up (the first calls load classes and code); then two days are timed, round by round.
		runDay(level, hallPos, hall, 1000, null, null);
		List<Long> wall = new ArrayList<>();
		List<Long> cpu = new ArrayList<>();
		runDay(level, hallPos, hall, 1001, wall, cpu);
		runDay(level, hallPos, hall, 1002, wall, cpu);
		double cpuAverage = cpu.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
		double cpuMax = cpu.stream().mapToLong(Long::longValue).max().orElse(0) / 1e6;
		long[] sorted = wall.stream().mapToLong(Long::longValue).sorted().toArray();
		double wallMedian = sorted[sorted.length / 2] / 1e6;
		double wallMax = sorted[sorted.length - 1] / 1e6;
		AliveWorkplace.LOG.info("[test] classes: 60 households, {} finished builds in the world, {} hall rounds timed: server-thread CPU average {} ms (slowest {} ms); wall clock median {} ms (slowest {} ms)",
			BuildSiteManager.get(level).finishedIn(level).size(), wall.size(), String.format("%.3f", cpuAverage), String.format("%.3f", cpuMax),
			String.format("%.3f", wallMedian), String.format("%.3f", wallMax));
		AliveWorkplace.LOG.info("[test] classes: each round, CPU us {} / wall us {}", cpu.stream().map(t -> t / 1000).toList(), wall.stream().map(t -> t / 1000).toList());
		helper.assertTrue(wall.size() == 18, "a round to find the village and 8 to count 60 households (8 a round) a day, found " + wall.size());
		// The round's own cost is the server thread's CPU time (a GC pause or the container being busy isn't the round's);
		// the wall clock's median is checked too, so a slow round can't hide in it.
		helper.assertTrue(cpuAverage < 2.0, String.format("a hall round takes %.3f ms of CPU on average (slowest %.3f ms)", cpuAverage, cpuMax));
		helper.assertTrue(wallMedian < 2.0, String.format("a hall round's median wall time is %.3f ms (slowest %.3f ms)", wallMedian, wallMax));
		people.forEach(Villager::discard);
		helper.succeed();
	}

	/** Runs the hall's rounds on {@code day} until the day's check is done; with lists, each round's wall and thread CPU nanoseconds. */
	private static void runDay(ServerLevel level, BlockPos hallPos, VillageHallBlockEntity hall, long day, List<Long> wall, List<Long> cpu) {
		java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();
		for (int round = 0; round < 20; round++) {
			long cpuStart = threads.getCurrentThreadCpuTime();
			long start = System.nanoTime();
			SocialClasses.Result r = SocialClasses.round(level, hallPos, hall, day);
			long took = System.nanoTime() - start;
			long cpuTook = threads.getCurrentThreadCpuTime() - cpuStart;
			if (r == null) {
				return;
			}
			if (wall != null) {
				wall.add(took);
				cpu.add(cpuTook);
			}
			if (r.finished()) {
				return;
			}
		}
	}
}
