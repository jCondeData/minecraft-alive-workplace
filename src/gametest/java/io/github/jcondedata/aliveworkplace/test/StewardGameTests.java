package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/** ROADMAP 27.5: the Steward, appointed at his own hall with its City Plan. */
public class StewardGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(24, 2, 24);

	private static ItemStack planFor(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(helper.getLevel(), player, stack, helper.absolutePos(hall));
		return stack;
	}

	/** {@code villager} made a Builder of {@link Stewards#MIN_BUILDER_LEVEL}, as a Steward must be (27.1a). */
	static Villager seasoned(Villager villager) {
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(Stewards.MIN_BUILDER_LEVEL));
		villager.setVillagerXp(70);
		return villager;
	}

	private static boolean stewardAt(GameTestHelper helper, Villager villager, BlockPos hall) {
		return villager.getVillagerData().getProfession() == ModVillagers.STEWARD && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(hall))::equals).isPresent();
	}

	/** The plan appoints a villager standing by its own hall, and not one standing by another hall. */
	//$ gametest_ticks_batch AREA '40' '"stewardOwnHall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardOwnHall")
	public void theCityPlanAppointsAtItsOwnHallOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager byB = helper.spawn(EntityType.VILLAGER, HALL_B.east(2));
		helper.assertTrue(Stewards.appoint(player, byB, plan) == InteractionResult.CONSUME, "refusal not answered");
		helper.assertTrue(byB.getVillagerData().getProfession() == VillagerProfession.NONE, "appointed at another hall");
		Villager byA = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		helper.assertTrue(Stewards.appoint(player, byA, plan) == InteractionResult.SUCCESS, "not appointed");
		helper.assertTrue(stewardAt(helper, byA, HALL_A), "not the Steward of his hall: " + byA.getVillagerData());
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) == 0, "the hall's place is still free");
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(HALL_B)) == 1, "the other hall's place went");
		helper.succeed();
	}

	/** A hall with a Steward can't take a second; a plan with no hall bound appoints nobody; a child can't be one. */
	//$ gametest_ticks_batch AREA '40' '"stewardSecond"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardSecond")
	public void aSecondVillagerCantTakeAHallWithASteward(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager first = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		Villager second = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.south(2)));
		helper.assertTrue(Stewards.appoint(player, first, plan) == InteractionResult.SUCCESS, "first not appointed");
		helper.assertTrue(Stewards.appoint(player, second, plan) == InteractionResult.CONSUME, "second not refused");
		helper.assertTrue(second.getVillagerData().getProfession() == ModVillagers.BUILDER, "the hall took a second Steward");
		helper.assertTrue(Stewards.appoint(player, second, new ItemStack(ModItems.CITY_PLAN)) == InteractionResult.CONSUME
			&& second.getVillagerData().getProfession() == ModVillagers.BUILDER, "an unbound plan appointed");
		Villager child = helper.spawn(EntityType.VILLAGER, HALL_A.west(2));
		child.setAge(-24000);
		helper.assertTrue(Stewards.appoint(player, child, plan) == InteractionResult.CONSUME
			&& child.getVillagerData().getProfession() == VillagerProfession.NONE, "a child was appointed");
		helper.assertTrue(stewardAt(helper, first, HALL_A), "the first lost the job");
		helper.succeed();
	}

	/** A hall saved when it had no place for a Steward (0 free tickets) gets its place back when it loads. */
	//$ gametest_ticks_batch AREA '60' '"stewardOldHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "stewardOldHall")
	public void aHallSavedWithNoFreePlaceGetsAStewardAfterReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL_A);
		// An old save: the record's place taken (as one saved with 0 tickets loads), the hall's data written before 27.5.
		level.getPoiManager().take(h -> h.is(ModVillagers.VILLAGE_HALL_POI), (h, p) -> p.equals(hall), hall, 1);
		helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 0, "setup: the place is still free");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		CompoundTag saved = entity.saveWithoutMetadata(level.registryAccess());
		saved.remove("stewardPlaceChecked");
		entity.loadWithComponents(saved, level.registryAccess());
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 1, "the loaded hall has no free place");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Villager villager = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
			helper.assertTrue(Stewards.appoint(player, villager, planFor(helper, player, HALL_A)) == InteractionResult.SUCCESS, "not appointed");
			helper.assertTrue(stewardAt(helper, villager, HALL_A), "no Steward after reload");
			// Checked once: saved again with its Steward, it isn't registered anew.
			CompoundTag again = entity.saveWithoutMetadata(level.registryAccess());
			helper.assertTrue(again.getBoolean("stewardPlaceChecked"), "the check isn't saved");
			helper.succeed();
		});
	}

	/** A jobless villager standing by the hall never takes it by himself. */
	//$ gametest_ticks_batch AREA '500' '"stewardJobless"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "stewardJobless")
	public void aJoblessVillagerByTheHallStaysJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL_A.east(1));
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "took a job: " + villager.getVillagerData());
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()
				&& villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "eyed the hall as a job site");
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) == 1, "the hall's place was taken");
			helper.succeed();
		});
	}

	/** Breaking the hall ends the job: jobless again. */
	//$ gametest_ticks_batch AREA '200' '"stewardHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "stewardHallBroken")
	public void breakingTheHallEndsTheJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager villager = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		helper.assertTrue(Stewards.appoint(player, villager, planFor(helper, player, HALL_A)) == InteractionResult.SUCCESS, "not appointed");
		helper.destroyBlock(HALL_A);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "still " + villager.getVillagerData());
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "still remembers the hall");
			helper.assertTrue(villager.getVillagerXp() == 0 && !StewardWork.holdsPlan(villager), "kept his xp or the plan");
		});
	}

	/** Config {@code steward} off: the plan appoints nobody. */
	//$ gametest_ticks_batch AREA '40' '"stewardOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardOff")
	public void stewardOffStopsAppointments(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		boolean was = Stewards.ENABLED;
		Stewards.ENABLED = false;
		try {
			helper.assertTrue(Stewards.appoint(player, villager, planFor(helper, player, HALL_A)) == InteractionResult.CONSUME, "not refused");
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "appointed with steward off");
		} finally {
			Stewards.ENABLED = was;
		}
		helper.succeed();
	}

	/** Open builds by level 1, 2, 2, 3, 4, never more than the rank allows or the config's cap. */
	//$ gametest_batch EMPTY_STRUCTURE '"stewardOpenBuilds"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "stewardOpenBuilds")
	public void openBuildsFollowLevelRankAndConfig(GameTestHelper helper) {
		int was = Stewards.MAX_OPEN_BUILDS;
		try {
			Stewards.MAX_OPEN_BUILDS = 4;
			int[] city = new int[5];
			for (int l = 1; l <= 5; l++) {
				city[l - 1] = Stewards.maxOpenBuilds(l, VillageRanks.Rank.CITY);
			}
			helper.assertTrue(java.util.Arrays.equals(city, new int[] {1, 2, 2, 3, 4}), "by level: " + java.util.Arrays.toString(city));
			helper.assertTrue(Stewards.maxOpenBuilds(5, VillageRanks.Rank.HAMLET) == 1 && Stewards.maxOpenBuilds(5, VillageRanks.Rank.VILLAGE) == 2
				&& Stewards.maxOpenBuilds(5, VillageRanks.Rank.TOWN) == 3, "rank caps");
			Stewards.MAX_OPEN_BUILDS = 2;
			helper.assertTrue(Stewards.maxOpenBuilds(5, VillageRanks.Rank.CITY) == 2, "config cap");
		} finally {
			Stewards.MAX_OPEN_BUILDS = was;
		}
		helper.succeed();
	}

	/** His rounds: one stop for each zone of the plan, nearest the hall first, never more than six. */
	//$ gametest_ticks_batch AREA '40' '"stewardStops"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardStops")
	public void hisRoundsHaveAtMostSixStops(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL_A);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager villager = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		Stewards.appoint(player, villager, planFor(helper, player, HALL_A));
		helper.assertTrue(StewardWork.stops(level, villager, hall).isEmpty(), "stops with an empty plan");
		CityPlan plan = CityPlan.EMPTY;
		for (int i = 0; i < 8; i++) {
			BitSet cells = new BitSet();
			cells.set(CityPlan.cellAt(hall, hall.offset(-40 + i * 10, 0, 20)));
			plan = plan.addZone("homes", "Homes " + i, "").paint(i, cells);
		}
		entity.setPlan(plan);
		List<StewardWork.Stop> stops = StewardWork.stops(level, villager, hall);
		helper.assertTrue(stops.size() == StewardWork.MAX_STOPS, "stops: " + stops.size());
		for (int i = 1; i < stops.size(); i++) {
			helper.assertTrue(stops.get(i - 1).pos().distSqr(hall) <= stops.get(i).pos().distSqr(hall), "not nearest first");
		}
		helper.assertTrue(stops.get(0).what().getString().startsWith("the Homes"), "named " + stops.get(0).what().getString());
		helper.succeed();
	}

	/** 27.1a: a jobless villager and an Apprentice Builder are refused, and told a Journeyman Builder is needed. */
	//$ gametest_ticks_batch AREA '40' '"stewardUnseasoned"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardUnseasoned")
	public void onlyASeasonedBuilderCanBeAppointed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		List<String> seen = new java.util.ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		ItemStack plan = planFor(helper, player, HALL_A);
		Villager jobless = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		helper.assertTrue(Stewards.appoint(player, jobless, plan) == InteractionResult.CONSUME, "a jobless villager wasn't refused");
		helper.assertTrue(jobless.getVillagerData().getProfession() == VillagerProfession.NONE, "a jobless villager was appointed");
		helper.assertTrue(seen.stream().anyMatch(m -> m.contains("only a Builder who has reached Journeyman")), "no reason given: " + seen);
		Villager apprentice = helper.spawn(EntityType.VILLAGER, HALL_A.south(2));
		apprentice.setVillagerData(apprentice.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		apprentice.setVillagerXp(20);
		helper.assertTrue(Stewards.appoint(player, apprentice, plan) == InteractionResult.CONSUME
			&& apprentice.getVillagerData().getProfession() == ModVillagers.BUILDER, "an Apprentice Builder was appointed");
		Villager librarian = helper.spawn(EntityType.VILLAGER, HALL_A.west(2));
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(5));
		librarian.setVillagerXp(250);
		helper.assertTrue(Stewards.appoint(player, librarian, plan) == InteractionResult.CONSUME
			&& librarian.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "a Master librarian was appointed");
		helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(HALL_A)) == 1, "a refusal took the hall's place");
		helper.succeed();
	}

	/** 27.1a: a Journeyman Builder is appointed, and starts his new job as a Novice Steward. */
	//$ gametest_ticks_batch AREA '40' '"stewardSeasoned"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "stewardSeasoned")
	public void aJourneymanBuilderIsAppointed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Villager builder = seasoned(helper.spawn(EntityType.VILLAGER, HALL_A.east(2)));
		helper.assertTrue(Stewards.qualifies(builder), "a Journeyman Builder doesn't qualify");
		helper.assertTrue(Stewards.appoint(player, builder, planFor(helper, player, HALL_A)) == InteractionResult.SUCCESS, "not appointed");
		helper.assertTrue(stewardAt(helper, builder, HALL_A), "not the Steward of his hall: " + builder.getVillagerData());
		helper.assertTrue(builder.getVillagerData().getLevel() == 1 && builder.getVillagerXp() == 1, "not a Novice Steward: " + builder.getVillagerData());
		helper.succeed();
	}

	/** 27.1a: a Steward appointed before the rule (a jobless villager then) keeps his job through a save and reload. */
	//$ gametest_ticks_batch AREA '200' '"stewardGrandfathered"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "stewardGrandfathered")
	public void aStewardFromAnOldSaveKeepsHisJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL_A);
		Villager old = helper.spawn(EntityType.VILLAGER, HALL_A.east(2));
		// As 27.5 appointed him: any grown villager, straight onto the hall.
		io.github.jcondedata.aliveworkplace.work.Stations.assign(level, old, hall, ModVillagers.STEWARD);
		helper.assertTrue(stewardAt(helper, old, HALL_A), "setup: not a Steward");
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(old.save(saved), "setup: couldn't save him");
		old.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
		net.minecraft.world.entity.Entity back = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(back instanceof Villager && level.addFreshEntity(back), "setup: couldn't load him again");
		Villager steward = (Villager) back;
		helper.assertTrue(Stewards.qualifies(steward), "an old Steward doesn't qualify");
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(stewardAt(helper, steward, HALL_A), "lost his job after reload: " + steward.getVillagerData());
			helper.assertTrue(Stewards.stewardOf(level, hall) == steward, "the hall doesn't know its Steward");
			helper.succeed();
		});
	}

	/** 27.1a: the City Plan takes a Map, a Blank Blueprint and a Heart of the Sea; without the heart nothing is made. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCityPlanNeedsAHeartOfTheSea(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack map = new ItemStack(net.minecraft.world.item.Items.MAP);
		ItemStack blank = new ItemStack(ModItems.BLANK_BLUEPRINT);
		ItemStack heart = new ItemStack(net.minecraft.world.item.Items.HEART_OF_THE_SEA);
		var full = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, List.of(map, blank, heart));
		ItemStack made = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, full, level)
			.map(r -> r.value().assemble(full, level.registryAccess())).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.is(ModItems.CITY_PLAN), "a map, a blank blueprint and a heart of the sea make " + made);
		var old = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(map.copy(), blank.copy()));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, old, level).isEmpty(),
			"a map and a blank blueprint alone still make something");
		helper.succeed();
	}
}
