package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 27.6: the Steward's rules. Each condition holding and not holding, their numbers matching the hall's "What
 * next?" tips, bad rule files skipped, {@code /workplace steward explain}, and the day's wishes saved and reloaded.
 */
public class StewardRulesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A Village Hall in the area, its village just this area (radius 16) for the test. */
	private static BlockPos hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/** One condition, read the way a rule file is. */
	private static StewardConditions.Condition cond(String json) {
		return rule("t", "{\"when\": [" + json + "], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}").when().get(0);
	}

	private static StewardRules.Rule rule(String name, String json) {
		return StewardRules.read(AliveWorkplace.id(name), JsonParser.parseString(json));
	}

	private static StewardConditions.Check check(GameTestHelper helper, String json) {
		return cond(json).test(StewardConditions.Facts.of(helper.getLevel(), helper.absolutePos(HALL)));
	}

	private static void holds(GameTestHelper helper, String json, long value) {
		StewardConditions.Check c = check(helper, json);
		helper.assertTrue(c.held() && c.value() == value, json + " should hold with " + value + ": " + c);
	}

	private static void fails(GameTestHelper helper, String json, long value) {
		StewardConditions.Check c = check(helper, json);
		helper.assertTrue(!c.held() && c.value() == value, json + " should not hold, with " + value + ": " + c);
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true); // stays put and claims nothing while the test counts
		return v;
	}

	private static void bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.CHEST);
		return (ChestBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleBeds"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleBeds")
	public void bedsShortHoldsUntilEveryoneHasABed(GameTestHelper helper) {
		hall(helper);
		for (int x : new int[] {3, 5, 7}) {
			villager(helper, x, 4);
		}
		holds(helper, "{\"beds_short\": {\"at_least\": 1}}", 3);
		fails(helper, "{\"beds_short\": {\"at_least\": 4}}", 3);
		for (int x : new int[] {3, 5, 7}) {
			bed(helper, x, 16);
		}
		fails(helper, "{\"beds_short\": {}}", 0);
		holds(helper, "{\"not\": {\"beds_short\": {}}}", 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleFood"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleFood")
	public void foodShortHoldsUntilTheStoreHasEnough(GameTestHelper helper) {
		hall(helper);
		villager(helper, 3, 4);
		villager(helper, 5, 4);
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		ChestBlockEntity chest = chest(helper, new BlockPos(17, 2, 4));
		chest.setItem(0, new ItemStack(Items.BREAD, 3));
		holds(helper, "{\"food_short\": {\"meals_per_adult\": 2}}", 1); // 3 of 4
		chest.setItem(1, new ItemStack(Items.BREAD, 1));
		fails(helper, "{\"food_short\": {\"meals_per_adult\": 2}}", 0);
		holds(helper, "{\"food_short\": {\"meals_per_adult\": 3}}", 2); // 4 of 6
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRulePoi"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRulePoi")
	public void missingPoiHoldsUntilOneIsPlaced(GameTestHelper helper) {
		hall(helper);
		String storehouse = "{\"missing_poi\": {\"poi\": \"aliveworkplace:storehouse\"}}";
		holds(helper, storehouse, 0);
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		fails(helper, storehouse, 1);
		holds(helper, "{\"missing_poi\": {\"poi\": \"minecraft:fisherman\"}}", 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleWorkstation"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleWorkstation")
	public void workerWithoutWorkstationHoldsUntilHeHasOne(GameTestHelper helper) {
		hall(helper);
		ServerLevel level = helper.getLevel();
		Villager farmer = villager(helper, 3, 4);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.setVillagerXp(1);
		villager(helper, 5, 4); // jobless, not a worker without a workstation
		holds(helper, "{\"worker_without_workstation\": {}}", 1);
		holds(helper, "{\"worker_without_workstation\": {\"professions\": [\"minecraft:farmer\"]}}", 1);
		fails(helper, "{\"worker_without_workstation\": {\"professions\": [\"minecraft:fisherman\"]}}", 0);
		helper.setBlock(new BlockPos(3, 2, 7), Blocks.COMPOSTER);
		Jobs.employ(level, farmer, helper.absolutePos(new BlockPos(3, 2, 7)), PoiTypes.FARMER, VillagerProfession.FARMER);
		helper.assertTrue(farmer.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(), "setup: no job site");
		fails(helper, "{\"worker_without_workstation\": {}}", 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleJobless"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleJobless")
	public void joblessCountsGrownUpsWithoutWorkButNotNitwits(GameTestHelper helper) {
		hall(helper);
		villager(helper, 3, 4);
		Villager second = villager(helper, 5, 4);
		holds(helper, "{\"jobless\": {\"at_least\": 2}}", 2);
		fails(helper, "{\"jobless\": {\"at_least\": 3}}", 2);
		second.setVillagerData(second.getVillagerData().setProfession(VillagerProfession.NITWIT));
		fails(helper, "{\"jobless\": {\"at_least\": 2}}", 1);
		holds(helper, "{\"jobless\": {}}", 1);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleBuilder"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleBuilder")
	public void noBuilderHoldsUntilSomeoneBuilds(GameTestHelper helper) {
		hall(helper);
		Villager v = villager(helper, 3, 4);
		holds(helper, "{\"no_builder\": {}}", 0);
		helper.setBlock(new BlockPos(3, 2, 7), ModBlocks.BLUEPRINT_TABLE);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(new BlockPos(3, 2, 7)));
		helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.BUILDER, "setup: not a builder");
		fails(helper, "{\"no_builder\": {}}", 1);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleRank"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleRank")
	public void rankAtLeastGoesByTheHallsRank(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		fails(helper, "{\"rank_at_least\": {\"rank\": \"village\"}}", 0);
		holds(helper, "{\"rank_at_least\": {\"rank\": \"hamlet\"}}", 0);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		tag.putInt("rank", VillageRanks.Rank.TOWN.ordinal()); // a Town, as a hall saved with that rank loads
		entity.loadWithComponents(tag, level.registryAccess());
		holds(helper, "{\"rank_at_least\": {\"rank\": \"village\"}}", 2);
		fails(helper, "{\"rank_at_least\": {\"rank\": \"city\"}}", 2);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleVillagers"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleVillagers")
	public void villagersAtLeastCountsChildrenToo(GameTestHelper helper) {
		hall(helper);
		villager(helper, 3, 4);
		villager(helper, 5, 4);
		holds(helper, "{\"villagers_at_least\": {\"n\": 2}}", 2);
		fails(helper, "{\"villagers_at_least\": {\"n\": 3}}", 2);
		villager(helper, 7, 4).setAge(-24000);
		holds(helper, "{\"villagers_at_least\": {\"n\": 3}}", 3);
		helper.succeed();
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos at) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleBuilt"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleBuilt")
	public void builtCountBelowCountsAnyStyleOrTier(GameTestHelper helper) {
		hall(helper);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = placement(helper, new BlockPos(2, 2, 2));
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		String below1 = "{\"built_count_below\": {\"blueprint\": \"aliveworkplace:stone_house\", \"n\": 1}}";
		holds(helper, below1, 0);
		// a Stone House II in cherry wood is a Stone House too
		sites.recordFinished(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "styled/cherry/aliveworkplace/stone_house_2"), placement, UUID.randomUUID());
		fails(helper, below1, 1);
		holds(helper, "{\"built_count_below\": {\"blueprint\": \"aliveworkplace:stone_house_3\", \"n\": 2}}", 1);
		holds(helper, "{\"built_count_below\": {\"blueprint\": \"aliveworkplace:storehouse\", \"n\": 1}}", 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleUpgrade")
	public void upgradeAvailableHoldsWhileANextTierExists(GameTestHelper helper) {
		hall(helper);
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = placement(helper, new BlockPos(2, 2, 2));
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		String stone = "{\"upgrade_available\": {\"blueprint\": \"aliveworkplace:stone_house\"}}";
		fails(helper, stone, 0);
		sites.recordFinished(StarterBlueprints.STONE_HOUSE.id(), placement, UUID.randomUUID());
		holds(helper, stone, 1);
		holds(helper, "{\"upgrade_available\": {}}", 1);
		fails(helper, "{\"upgrade_available\": {\"blueprint\": \"aliveworkplace:storehouse\"}}", 0);
		sites.recordFinished(StarterBlueprints.STONE_HOUSE_3.id(), placement, UUID.randomUUID()); // the top tier
		fails(helper, stone, 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleHomes"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleHomes")
	public void homesTierLowHoldsUntilTheirHomesAreUpgraded(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = placement(helper, new BlockPos(2, 2, 2));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			sites.forgetFinished(placement);
		});
		BlockPos hallAt = new BlockPos(16, 2, 16);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		StewardConditions.Condition low = cond("{\"homes_tier_low\": {\"share\": 0.5}}");
		java.util.function.Supplier<StewardConditions.Check> check = () -> low.test(StewardConditions.Facts.of(level, helper.absolutePos(hallAt)));
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			villagers.add(villager(helper, 4 + 2 * i, 12));
			villagers.get(i).getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(4 + 2 * i, 3, 5))));
		}
		helper.assertTrue(!check.get().held() && check.get().value() == 2, "two grown-ups are too few to ask: " + check.get());
		villagers.add(villager(helper, 8, 12));
		villagers.get(2).getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(8, 3, 5))));
		helper.assertTrue(check.get().held() && check.get().value() == 3, "three in no built home: " + check.get());
		sites.recordFinished(StarterBlueprints.STONE_HOUSE.id(), placement, UUID.randomUUID());
		helper.assertTrue(check.get().held() && check.get().value() == 3, "three in a tier I house: " + check.get());
		sites.recordFinished(StarterBlueprints.STONE_HOUSE_2.id(), placement, UUID.randomUUID());
		helper.assertTrue(!check.get().held() && check.get().value() == 0, "three in a tier II house: " + check.get());
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleStore"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleStore")
	public void storeFullGoesByTheStorehousesChests(GameTestHelper helper) {
		hall(helper);
		String full = "{\"store_full\": {\"share\": 0.9}}";
		fails(helper, full, 0); // no storehouse at all
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		ChestBlockEntity chest = chest(helper, new BlockPos(17, 2, 4));
		for (int i = 0; i < 20; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		fails(helper, full, 74); // 20 of 27
		for (int i = 20; i < 25; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		holds(helper, full, 92); // 25 of 27
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"stewardRuleResearch"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleResearch")
	public void researchConditionsReadTheHallsResearch(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		long open = java.util.Arrays.stream(Research.Topic.values()).filter(Research.State.EMPTY::available).count();
		holds(helper, "{\"research_idle\": {}}", open);
		fails(helper, "{\"research_at_least\": {\"topic\": \"hearth\", \"level\": 1}}", 0);
		holds(helper, "{\"not\": {\"research_at_least\": {\"topic\": \"hearth\"}}}", 0);
		entity.setResearch(Research.State.EMPTY.choose(Research.Topic.HEARTH));
		fails(helper, "{\"research_idle\": {}}", open);
		entity.setResearch(new Research.State(Map.of("hearth", 1), Optional.empty(), 0, false));
		holds(helper, "{\"research_at_least\": {\"topic\": \"hearth\", \"level\": 1}}", 1);
		fails(helper, "{\"research_at_least\": {\"topic\": \"hearth\", \"level\": 2}}", 1);
		holds(helper, "{\"research_idle\": {}}", java.util.Arrays.stream(Research.Topic.values()).filter(entity.research()::available).count());
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void modLoadedHoldsForInstalledModsOnly(GameTestHelper helper) {
		StewardConditions.Facts facts = StewardConditions.Facts.of(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		StewardConditions.Check ours = cond("{\"mod_loaded\": {\"mod\": \"aliveworkplace\"}}").test(facts);
		StewardConditions.Check none = cond("{\"mod_loaded\": {\"mod\": \"no_such_mod_here\"}}").test(facts);
		helper.assertTrue(ours.held() && ours.value() == 1, "our own mod isn't loaded: " + ours);
		helper.assertTrue(!none.held() && none.value() == 0, "a mod that isn't there is loaded: " + none);
		helper.succeed();
	}

	private static Object arg(VillageAdvice.Tip tip, int i) {
		return tip.args()[i];
	}

	/** The conditions read the same numbers as the "What next?" tips, in a village that lacks things and then doesn't. */
	//$ gametest_ticks_batch AREA '40' '"stewardRuleAdvice"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleAdvice")
	public void conditionsMatchTheWhatNextTips(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		for (int x : new int[] {3, 5, 7}) {
			villager(helper, x, 4);
		}
		Villager nitwit = villager(helper, 9, 4);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		bed(helper, 3, 16);
		agree(helper, level, hall);
		helper.assertTrue(check(helper, "{\"beds_short\": {}}").value() == 3, "beds: " + check(helper, "{\"beds_short\": {}}"));
		// now: beds for all, a storehouse with food, a builder
		bed(helper, 5, 16);
		bed(helper, 7, 16);
		bed(helper, 9, 16);
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.STOREHOUSE);
		chest(helper, new BlockPos(17, 2, 4)).setItem(0, new ItemStack(Items.BREAD, 64));
		helper.setBlock(new BlockPos(3, 2, 7), ModBlocks.BLUEPRINT_TABLE);
		Builders.employ(level, level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.getVillagerData().getProfession() == VillagerProfession.NONE).get(0), helper.absolutePos(new BlockPos(3, 2, 7)));
		agree(helper, level, hall);
		helper.succeed();
	}

	private static void agree(GameTestHelper helper, ServerLevel level, BlockPos hall) {
		Map<String, VillageAdvice.Tip> tips = new LinkedHashMap<>();
		VillageAdvice.tips(level, hall).forEach(t -> tips.putIfAbsent(t.key(), t));
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		StewardConditions.Check beds = cond("{\"beds_short\": {}}").test(facts);
		StewardConditions.Check food = cond("{\"food_short\": {\"meals_per_adult\": " + VillageAdvice.MEALS_PER_ADULT + "}}").test(facts);
		StewardConditions.Check jobless = cond("{\"jobless\": {}}").test(facts);
		StewardConditions.Check builder = cond("{\"no_builder\": {}}").test(facts);
		StewardConditions.Check storehouse = cond("{\"missing_poi\": {\"poi\": \"aliveworkplace:storehouse\"}}").test(facts);
		StewardConditions.Check homes = cond("{\"homes_tier_low\": {\"share\": 0.5}}").test(facts);
		String all = tips.keySet() + " vs beds " + beds + ", food " + food + ", jobless " + jobless + ", builder " + builder
			+ ", storehouse " + storehouse + ", homes " + homes;
		helper.assertTrue(beds.held() == tips.containsKey("beds") && (!beds.held() || ((Number) arg(tips.get("beds"), 0)).longValue() == beds.value()), all);
		helper.assertTrue(food.held() == tips.containsKey("food") && (!food.held()
			|| ((Number) arg(tips.get("food"), 1)).longValue() - ((Number) arg(tips.get("food"), 0)).longValue() == food.value()), all);
		helper.assertTrue(jobless.held() == tips.containsKey("jobless") && (!jobless.held() || ((Number) arg(tips.get("jobless"), 0)).longValue() == jobless.value()), all);
		helper.assertTrue(builder.held() == tips.containsKey("builder"), all);
		helper.assertTrue(storehouse.held() == tips.containsKey("storehouse"), all);
		helper.assertTrue(homes.held() == tips.containsKey("homes") && (!homes.held() || ((Number) arg(tips.get("homes"), 0)).longValue() == homes.value()), all);
	}

	/** A rule file with an unknown condition, effect or field, or a bad value, is skipped; the good ones load. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void badRuleFilesAreSkippedNamingTheField(GameTestHelper helper) {
		String good = "{\"when\": [{\"jobless\": {}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\", \"priority\": 70}";
		Map<String, String> bad = new LinkedHashMap<>();
		bad.put("when[0].moon_phase", "{\"when\": [{\"moon_phase\": {}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("when[1].beds_short.at_least", "{\"when\": [{\"jobless\": {}}, {\"beds_short\": {\"at_least\": -1}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("when[0].missing_poi.poi", "{\"when\": [{\"missing_poi\": {\"poi\": \"aliveworkplace:no_such_place\"}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("when[0].no_builder.colour", "{\"when\": [{\"no_builder\": {\"colour\": \"red\"}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("when[0]", "{\"when\": [{\"jobless\": {}, \"no_builder\": {}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("when[0].not.moon_phase", "{\"when\": [{\"not\": {\"moon_phase\": {}}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}");
		bad.put("priorty", "{\"when\": [], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\", \"priorty\": 5}");
		bad.put("priority", "{\"when\": [], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\", \"priority\": 101}");
		bad.put("do.conquer", "{\"when\": [], \"do\": {\"conquer\": {}}, \"why\": \"x\"}");
		bad.put("do.build.zone", "{\"when\": [], \"do\": {\"build\": {\"blueprint\": \"aliveworkplace:stone_house\"}}, \"why\": \"x\"}");
		bad.put("why", "{\"when\": [], \"do\": {\"assign_jobs\": {}}}");
		bad.put("min_rank", "{\"when\": [], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\", \"min_rank\": \"empire\"}");
		Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/good.json"), JsonParser.parseString(good));
		for (Map.Entry<String, String> e : bad.entrySet()) {
			try {
				StewardRules.read(AliveWorkplace.id("bad"), JsonParser.parseString(e.getValue()));
				helper.fail("not refused: " + e.getValue());
			} catch (StewardRules.BadRule refused) {
				helper.assertTrue(refused.field.equals(e.getKey()), "refused on '" + refused.field + "', not '" + e.getKey() + "': " + refused.getMessage());
			}
			files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/bad_" + files.size() + ".json"), JsonParser.parseString(e.getValue()));
		}
		List<StewardRules.Rule> loaded = StewardRules.readAll(files);
		helper.assertTrue(loaded.size() == 1 && loaded.get(0).id().equals(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "good")),
			"loaded: " + loaded.stream().map(StewardRules.Rule::id).toList());
		// The starter rules all load.
		List<String> ids = StewardRules.all().stream().map(r -> r.id().toString()).toList();
		for (String name : List.of("builder", "homes", "storehouse", "food", "jobs", "store_full", "workstations", "better_homes", "scholar",
				"pokemon_center", "hearth", "research", "market")) {
			helper.assertTrue(ids.contains("aliveworkplace:" + name), "starter rule " + name + " didn't load: " + ids);
		}
		helper.succeed();
	}

	/** Every translatable key and literal text in a message, its arguments included, flattened (the server has no lang). */
	private static String flat(Component c) {
		StringBuilder out = new StringBuilder();
		if (c.getContents() instanceof TranslatableContents t) {
			out.append(t.getKey()).append('(');
			for (Object a : t.getArgs()) {
				out.append(a instanceof Component ac ? flat(ac) : String.valueOf(a)).append(',');
			}
			out.append(')');
		} else if (c.getContents() instanceof PlainTextContents p) {
			out.append(p.text());
		}
		c.getSiblings().forEach(s -> out.append(flat(s)));
		return out.toString();
	}

	/** {@code /workplace steward explain} by the hall lists a rule that held and one that didn't, with their conditions. */
	//$ gametest_ticks_batch AREA '40' '"stewardExplain"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardExplain")
	public void explainShowsARuleThatHeldAndOneThatDidnt(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		villager(helper, 5, 4);
		List<String> lines = new ArrayList<>();
		CommandSource capture = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				lines.add(flat(message));
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withSource(capture).withLevel(level)
			.withPosition(Vec3.atCenterOf(hall.east(3))), "workplace steward explain");
		String all = String.join("\n", lines);
		helper.assertTrue(!lines.isEmpty() && lines.get(0).startsWith("command.aliveworkplace.steward.explain.header"), "no header:\n" + all);
		int homes = indexOf(lines, "explain.held(),aliveworkplace:homes,");
		int storehouse = indexOf(lines, "explain.not_held(),aliveworkplace:storehouse,");
		helper.assertTrue(homes >= 0, "the homes rule (2 villagers, no bed) isn't shown held:\n" + all);
		helper.assertTrue(lines.get(homes + 1).contains("✔") && lines.get(homes + 1).contains("condition.beds_short(2,1,)"),
			"its condition isn't shown with its number:\n" + all);
		helper.assertTrue(storehouse >= 0, "the storehouse rule (2 villagers of 3) isn't shown not held:\n" + all);
		helper.assertTrue(lines.get(storehouse + 1).contains("✔") && lines.get(storehouse + 1).contains("condition.missing_poi(aliveworkplace:storehouse,0,)")
			&& lines.get(storehouse + 2).contains("✘") && lines.get(storehouse + 2).contains("condition.villagers_at_least(2,3,)"),
			"its conditions aren't shown with what held:\n" + all);
		helper.succeed();
	}

	private static int indexOf(List<String> lines, String part) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).contains(part)) {
				return i;
			}
		}
		return -1;
	}

	/** Once a morning the rules that hold become the day's wishes, saved on the hall; old halls load with none. */
	//$ gametest_ticks_batch AREA '40' '"stewardWishesSaved"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardWishesSaved")
	public void theDaysWishesAreRankedOnceAndSaved(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Villager v = villager(helper, 3, 4);
		villager(helper, 5, 4);
		helper.assertTrue(entity.stewardWishes().equals(StewardWishes.State.EMPTY), "a new hall has wishes");
		// the Steward's planner, at the hall after his rounds, ranks them
		Component line = StewardWork.PLANNER.plan(level, v, hall);
		StewardWishes.State state = entity.stewardWishes();
		List<String> rules = state.wishes().stream().map(w -> w.rule().getPath()).toList();
		helper.assertTrue(state.day() == StewardWishes.day(level), "not ranked today: " + state.day());
		helper.assertTrue(rules.size() >= 3 && rules.get(0).equals("builder") && rules.get(1).equals("homes") && rules.contains("jobs"), "wishes: " + rules);
		helper.assertTrue(state.wishes().get(1).numbers().equals(List.of(2L)), "the homes wish's numbers: " + state.wishes().get(1));
		helper.assertTrue(line.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.steward.state.wish"),
			"the Steward's line: " + flat(line));
		for (int i = 0; i + 1 < state.wishes().size(); i++) {
			helper.assertTrue(state.wishes().get(i).priority() >= state.wishes().get(i + 1).priority(), "not by priority: " + state.wishes());
		}
		// once a day: the village changes, the wishes don't till tomorrow
		bed(helper, 3, 16);
		bed(helper, 5, 16);
		helper.assertTrue(!StewardWishes.rankIfDue(level, hall) && entity.stewardWishes().equals(state), "ranked twice in a day");
		// saved and loaded
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		entity.setStewardWishes(StewardWishes.State.EMPTY);
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.stewardWishes().equals(state), "lost on reload: " + entity.stewardWishes() + " vs " + state);
		// a hall saved before 27.6 loads with none
		tag.remove("steward");
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.stewardWishes().equals(StewardWishes.State.EMPTY), "an old hall loads with " + entity.stewardWishes());
		helper.succeed();
	}

	/** A rule is wished for only at its rank, with its mods, out of its cooldown and under its max. */
	//$ gametest_ticks_batch AREA '40' '"stewardRuleLimits"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardRuleLimits")
	public void rulesKeepToRankModsCooldownAndMax(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		long day = StewardWishes.day(level);
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		java.util.function.BiFunction<String, StewardWishes.State, StewardWishes.Status> judge = (json, state) ->
			StewardWishes.judge(rule("limits", json), facts, state, day).status();
		String jobs = "{\"when\": [{\"jobless\": {}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"";
		helper.assertTrue(judge.apply(jobs + "}", StewardWishes.State.EMPTY) == StewardWishes.Status.HELD, "plain rule");
		helper.assertTrue(judge.apply(jobs + ", \"min_rank\": \"town\"}", StewardWishes.State.EMPTY) == StewardWishes.Status.RANK, "rank");
		helper.assertTrue(judge.apply(jobs + ", \"requires\": [\"no_such_mod_here\"]}", StewardWishes.State.EMPTY) == StewardWishes.Status.MOD_MISSING, "mods");
		// carried out today: a cooldown of 2 days rests today and tomorrow; a max of 1 is used up
		StewardWishes.rankIfDue(level, hall);
		StewardWishes.carriedOut(level, hall, AliveWorkplace.id("limits"));
		StewardWishes.State state = StewardWishes.of(level, hall);
		helper.assertTrue(state.used(AliveWorkplace.id("limits")).times() == 1, "not counted: " + state.used());
		helper.assertTrue(judge.apply(jobs + ", \"cooldown_days\": 2}", state) == StewardWishes.Status.COOLING, "cooldown");
		helper.assertTrue(StewardWishes.judge(rule("limits", jobs + ", \"cooldown_days\": 2}"), facts, state, day + 2).status() == StewardWishes.Status.HELD,
			"still resting after the cooldown");
		helper.assertTrue(judge.apply(jobs + ", \"max\": 1}", state) == StewardWishes.Status.USED_UP, "max");
		helper.assertTrue(judge.apply(jobs + ", \"max\": 2}", state) == StewardWishes.Status.HELD, "under the max");
		helper.succeed();
	}
}
