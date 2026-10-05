package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 27.11, a workplace for every worker. Every {@code workplace_*} rule, in a village staged with a worker of its
 * trade and no workstation: the Steward wishes for that building in its zone (a Cobblemon one waits for Cobblemon), and
 * not without the worker. And each of the 12 buildable village houses built by a builder, its job block taken by its worker.
 */
public class WorkplacesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** Each rule: the worker staged, the building it asks for, its zone, the hall's rank and whether it needs Cobblemon. */
	record Case(String rule, String job, String blueprint, String zone, VillageRanks.Rank rank, boolean cobblemon) {
	}

	static final List<Case> CASES = List.of(
		new Case("workplace_builders_workshop", "aliveworkplace:builder", "builders_workshop", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_carpenters_workshop", "aliveworkplace:carpenter", "carpenters_workshop", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_kitchen", "aliveworkplace:chef", "kitchen", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_kitchen", "minecraft:butcher", "kitchen", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_post_office", "aliveworkplace:postman", "post_office", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_guard_house", "aliveworkplace:guard", "guard_house", "defences", VillageRanks.Rank.VILLAGE, false),
		new Case("workplace_guard_house", "minecraft:weaponsmith", "guard_house", "defences", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_barracks", "aliveworkplace:guard", "barracks", "defences", VillageRanks.Rank.TOWN, false),
		new Case("workplace_barracks", "minecraft:weaponsmith", "barracks", "defences", VillageRanks.Rank.CITY, false),
		new Case("workplace_clinic", "aliveworkplace:nurse", "clinic", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_clinic", "minecraft:cleric", "clinic", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_healing_center", "aliveworkplace:nurse", "healing_center", "civic", VillageRanks.Rank.VILLAGE, false),
		new Case("workplace_healing_center", "minecraft:cleric", "healing_center", "civic", VillageRanks.Rank.TOWN, false),
		new Case("workplace_graveyard", "aliveworkplace:undertaker", "graveyard", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_schoolhouse", "aliveworkplace:teacher", "schoolhouse", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_library", "minecraft:librarian", "library", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_library", "aliveworkplace:scholar", "library", "civic", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_storehouse", "aliveworkplace:porter", "storehouse", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_berry_farm", "aliveworkplace:orchard_keeper", "berry_farm", "farms", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_flower_shop", "aliveworkplace:florist", "flower_shop", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_compost_yard", "aliveworkplace:composter", "compost_yard", "farms", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_apiary_garden", "aliveworkplace:beekeeper", "apiary_garden", "gardens", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_sifting_shed", "aliveworkplace:sifter", "sifting_shed", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_sifting_shed", "minecraft:leatherworker", "sifting_shed", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_tinkers_workshop", "aliveworkplace:tinkerer", "tinkers_workshop", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_tinkers_workshop", "minecraft:toolsmith", "tinkers_workshop", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_nether_gate", "aliveworkplace:netherworker", "nether_gate", "workshops", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_supply_shop", "aliveworkplace:shopkeeper", "supply_shop", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_inn", "aliveworkplace:innkeeper", "inn", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_ranch", "aliveworkplace:rancher", "ranch", "farms", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_ferry_house", "aliveworkplace:ferryman", "ferry_house", "market", VillageRanks.Rank.HAMLET, false),
		new Case("workplace_research_lab", "aliveworkplace:fossil_scientist", "research_lab", "civic", VillageRanks.Rank.HAMLET, true),
		new Case("workplace_trainers_house", "aliveworkplace:trainer", "trainers_house", "civic", VillageRanks.Rank.HAMLET, true),
		new Case("workplace_leaders_hall", "aliveworkplace:trainer_leader", "leaders_hall", "civic", VillageRanks.Rank.HAMLET, true),
		new Case("workplace_ball_workshop", "aliveworkplace:ball_smith", "ball_workshop", "workshops", VillageRanks.Rank.HAMLET, true),
		new Case("workplace_trade_hall", "aliveworkplace:pokemon_trader", "trade_hall", "market", VillageRanks.Rank.HAMLET, true),
		new Case("workplace_school", "aliveworkplace:tutor", "school", "civic", VillageRanks.Rank.HAMLET, true));

	/** The rule of the other tier for the same workers: the Guard House below a Town, the Barracks from one; Clinic and Healing Center at Village. */
	private static final Map<String, String> OTHER_TIER = Map.of(
		"workplace_guard_house", "workplace_barracks", "workplace_barracks", "workplace_guard_house",
		"workplace_clinic", "workplace_healing_center", "workplace_healing_center", "workplace_clinic");

	@GameTestGenerator
	public Collection<TestFunction> rules() {
		List<TestFunction> out = new ArrayList<>();
		for (Case c : CASES) {
			String name = "workplaceRule_" + c.rule().substring("workplace_".length()) + "_" + c.job().substring(c.job().indexOf(':') + 1);
			out.add(new TestFunction(name, name, AREA, 60, 0, true, helper -> rule(helper, c)));
		}
		return out;
	}

	private static StewardRules.Rule ruleOf(GameTestHelper helper, String id) {
		return StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id(id))).findFirst()
			.orElseThrow(() -> new GameTestAssertException("rule " + id + " didn't load"));
	}

	static void rule(GameTestHelper helper, Case c) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(c.rank());
		StewardRules.Rule rule = ruleOf(helper, c.rule());
		long day = StewardWishes.day(level);
		// No worker of the trade: the rule doesn't hold.
		StewardWishes.Verdict before = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		helper.assertTrue(before.status() != StewardWishes.Status.HELD, c.rule() + " holds with no " + c.job());
		// A worker of a trade the rule isn't for: still not (a fisherman; a mason for the Fisher's Hut's rule, ROADMAP 27.14).
		VillagerProfession otherJob = c.job().equals("minecraft:fisherman") ? VillagerProfession.MASON : VillagerProfession.FISHERMAN;
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 20));
		other.setNoAi(true);
		other.setVillagerData(other.getVillagerData().setProfession(otherJob));
		other.setVillagerXp(1);
		StewardWishes.Verdict wrongJob = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		helper.assertTrue(wrongJob.status() != StewardWishes.Status.HELD, c.rule() + " holds for a " + otherJob);
		// The worker without a workstation.
		Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 20));
		worker.setNoAi(true);
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(c.job()));
		worker.setVillagerData(worker.getVillagerData().setProfession(job));
		worker.setVillagerXp(1);
		StewardWishes.Verdict verdict = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		StewardRules.Effect e = rule.effect();
		helper.assertTrue(e.kind() == StewardRules.Kind.BUILD && e.blueprint().equals(Optional.of(AliveWorkplace.id(c.blueprint())))
			&& e.zone().equals(Optional.of(c.zone())), c.rule() + " doesn't build a " + c.blueprint() + " in " + c.zone() + ": " + e);
		helper.assertTrue(BlueprintLibrary.get(level, AliveWorkplace.id(c.blueprint())).isPresent(), "no blueprint " + c.blueprint());
		helper.assertTrue(verdict.checks().get(0).held() && verdict.checks().get(0).value() == 1,
			c.rule() + ": its worker isn't counted: " + verdict.checks().get(0));
		if (c.cobblemon() && !Platform.get().isModLoaded("cobblemon")) {
			helper.assertTrue(verdict.status() == StewardWishes.Status.MOD_MISSING, c.rule() + " isn't held back without Cobblemon: " + verdict.status());
		} else {
			helper.assertTrue(verdict.status() == StewardWishes.Status.HELD, c.rule() + " doesn't hold: " + verdict.status() + " " + verdict.checks());
			StewardWishes.Wish wish = new StewardWishes.Wish(rule.id(), e, rule.priority(), rule.why(), verdict.numbers());
			helper.assertTrue(StewardWishes.plotFor(wish).map(r -> r.zoneKind().equals(c.zone())).orElse(false), c.rule() + ": no plot asked in " + c.zone());
			helper.assertTrue(wish.numbers().get(0) == 1L && wish.why().equals("steward.aliveworkplace.why.workplace"), "the reason doesn't count the worker: " + wish);
		}
		String otherTier = OTHER_TIER.get(c.rule());
		if (otherTier != null) {
			StewardWishes.Verdict o = StewardWishes.judge(ruleOf(helper, otherTier), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
			helper.assertTrue(o.status() != StewardWishes.Status.HELD, otherTier + " holds too at " + c.rank() + ": " + o.status());
		}
		helper.succeed();
	}

	// --- a job the village wants with no free block --------------------------------------------------------

	/** A hamlet with its builder and one villager waiting for a job, no other workstation; the hall counted within 16 blocks. */
	private static Villager wantingVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.HAMLET);
		helper.setBlock(new BlockPos(3, 2, 18), ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 17));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 18)));
		helper.assertTrue(builder.getVillagerData().getProfession() == ModVillagers.BUILDER, "setup: no builder");
		Villager waiting = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 14));
		waiting.setNoAi(true); // stays jobless: vanilla's own job search mustn't race the Steward
		return waiting;
	}

	/** Today's wishes among the workplace rules alone, as the Steward ranks them each morning. */
	private static List<StewardWishes.Wish> workplaceWishes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<StewardRules.Rule> rules = StewardRules.all().stream().filter(r -> r.id().getPath().startsWith("workplace_")).toList();
		return StewardWishes.rank(rules, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
	}

	private static Optional<StewardWishes.Wish> wish(List<StewardWishes.Wish> wishes, String rule) {
		return wishes.stream().filter(w -> w.rule().equals(AliveWorkplace.id(rule))).findFirst();
	}

	/**
	 * Guards are short and no grindstone or Guard Post is free: the village wants a guard with no free block, so the
	 * Guard House rule holds with nobody yet working as a guard. A wanted builder doesn't ask for a Builder's Workshop
	 * (with no builder nobody could build it: {@code no_builder} asks the player instead), and jobs the village doesn't
	 * want (a carpenter) don't either.
	 */
	//$ gametest_ticks_batch AREA '40' '"workplaceWanted"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "workplaceWanted")
	public void wantedJobWithNoFreeBlockWishesItsBuilding(GameTestHelper helper) {
		wantingVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<VillagerProfession> wanted = StewardJobs.plan(level, hall).wanted();
		helper.assertTrue(wanted.contains(ModVillagers.GUARD), "the village wants: " + wanted);
		List<StewardWishes.Wish> wishes = workplaceWishes(helper);
		Optional<StewardWishes.Wish> guardHouse = wish(wishes, "workplace_guard_house");
		helper.assertTrue(guardHouse.isPresent(), "no Guard House for the wanted guard: " + wishes.stream().map(w -> w.rule().getPath()).toList());
		helper.assertTrue(guardHouse.get().numbers().get(0) == 1L && guardHouse.get().why().equals("steward.aliveworkplace.why.workplace"),
			"the reason doesn't count the wanted job: " + guardHouse.get());
		helper.assertTrue(StewardWishes.plotFor(guardHouse.get()).map(r -> r.zoneKind().equals("defences")).orElse(false), "no plot asked in defences");
		helper.assertTrue(wish(wishes, "workplace_builders_workshop").isEmpty(), "a Builder's Workshop with nobody to build it");
		helper.assertTrue(wish(wishes, "workplace_barracks").isEmpty(), "Barracks wished in a hamlet");
		helper.assertTrue(wish(wishes, "workplace_carpenters_workshop").isEmpty(), "a Carpenter's Workshop nobody wants");
		// What /workplace steward explain says of it.
		String explained = StewardWishes.explain(level, hall, List.of(ruleOf(helper, "workplace_guard_house"))).stream()
			.map(c -> c.getString()).collect(Collectors.joining("\n"));
		helper.assertTrue(explained.contains("Workers without a workstation (aliveworkplace:guard, minecraft:weaponsmith): 0; jobs wanted with no free block: 1")
			&& explained.contains("1 jobs here have no workplace of their trade"),
			"explain: " + explained);
		helper.succeed();
	}

	/** The same village with a free grindstone: the guard's job has a block, so no Guard House. */
	//$ gametest_ticks_batch AREA '40' '"workplaceWantedFree"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "workplaceWantedFree")
	public void wantedJobWithAFreeBlockWishesNothing(GameTestHelper helper) {
		wantingVillage(helper);
		helper.setBlock(new BlockPos(8, 2, 14), Blocks.GRINDSTONE);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		StewardJobs.Plan plan = StewardJobs.plan(level, hall);
		helper.assertTrue(!plan.wanted().contains(ModVillagers.GUARD) && plan.jobs().stream().anyMatch(j -> j.job().orElse(null) == ModVillagers.GUARD),
			"the guard isn't given the free grindstone: " + plan);
		List<StewardWishes.Wish> wishes = workplaceWishes(helper);
		helper.assertTrue(wish(wishes, "workplace_guard_house").isEmpty(), "a Guard House with a free grindstone: " + wish(wishes, "workplace_guard_house"));
		StewardWishes.Verdict v = StewardWishes.judge(ruleOf(helper, "workplace_guard_house"), StewardConditions.Facts.of(level, hall),
			StewardWishes.State.EMPTY, StewardWishes.day(level));
		helper.assertTrue(!v.checks().get(0).held() && v.checks().get(0).value() == 0, "the guard counted: " + v.checks().get(0));
		helper.succeed();
	}

	/** No builder: the guard is wanted, but a Guard House nobody could build isn't wished (no_builder asks first). */
	//$ gametest_ticks_batch AREA '40' '"workplaceWantedNoBuilder"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "workplaceWantedNoBuilder")
	public void wantedJobWaitsForABuilder(GameTestHelper helper) {
		wantingVillage(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(hall).inflate(20),
			v -> v.getVillagerData().getProfession() == ModVillagers.BUILDER).forEach(v -> v.discard());
		helper.setBlock(new BlockPos(3, 2, 18), Blocks.AIR); // and no free table to make another
		List<VillagerProfession> wanted = StewardJobs.plan(level, hall).wanted();
		helper.assertTrue(wanted.contains(ModVillagers.GUARD), "the village wants: " + wanted);
		helper.assertTrue(wish(workplaceWishes(helper), "workplace_guard_house").isEmpty(), "a Guard House with nobody to build it");
		helper.succeed();
	}

	/** Nobody waits for a job (the one villager has traded, only his block lost): nothing is wanted, no workplace wished. */
	//$ gametest_ticks_batch AREA '40' '"workplaceWantedNone"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "workplaceWantedNone")
	public void noWantedJobWithoutAJoblessVillager(GameTestHelper helper) {
		Villager v = wantingVillage(helper);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
		v.setVillagerXp(1);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(StewardConditions.Facts.of(level, hall).wanted().isEmpty(), "wanted with nobody waiting");
		List<StewardWishes.Wish> wishes = workplaceWishes(helper);
		helper.assertTrue(wish(wishes, "workplace_guard_house").isEmpty() && wish(wishes, "workplace_builders_workshop").isEmpty(),
			"workplaces wished: " + wishes.stream().map(w -> w.rule().getPath()).toList());
		helper.succeed();
	}

	// --- each workplace built by a builder, its job block taken by its worker ---------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 10);
	private static final BlockPos WORKER = new BlockPos(16, 2, 25);

	/** Each workplace: its worker's job and its job block. */
	record Workplace(StarterBlueprints.Entry entry, String job, Block block) {
	}

	static List<Workplace> workplaces() {
		return List.of(
			new Workplace(StarterBlueprints.BUILDERS_WORKSHOP, "aliveworkplace:builder", ModBlocks.BLUEPRINT_TABLE),
			new Workplace(StarterBlueprints.CARPENTERS_WORKSHOP, "aliveworkplace:carpenter", Blocks.CRAFTING_TABLE),
			new Workplace(StarterBlueprints.KITCHEN, "aliveworkplace:chef", Blocks.SMOKER),
			new Workplace(StarterBlueprints.POST_OFFICE, "aliveworkplace:postman", ModBlocks.MAILBOX),
			new Workplace(StarterBlueprints.GUARD_HOUSE, "aliveworkplace:guard", Blocks.GRINDSTONE),
			new Workplace(StarterBlueprints.CLINIC, "aliveworkplace:nurse", Blocks.BREWING_STAND),
			new Workplace(StarterBlueprints.FERRY_HOUSE, "aliveworkplace:ferryman", ModBlocks.TRAVEL_POST),
			new Workplace(StarterBlueprints.TRAINERS_HOUSE, "aliveworkplace:trainer", ModBlocks.TRAINING_POST),
			new Workplace(StarterBlueprints.LEADERS_HALL, "aliveworkplace:trainer_leader", ModBlocks.TRAINING_POST),
			new Workplace(StarterBlueprints.BALL_WORKSHOP, "aliveworkplace:ball_smith", Blocks.SMITHING_TABLE),
			new Workplace(StarterBlueprints.TRADE_HALL, "aliveworkplace:pokemon_trader", ModBlocks.SHOP_COUNTER),
			new Workplace(StarterBlueprints.SCHOOL, "aliveworkplace:tutor", ModBlocks.TRAINING_POST));
	}

	@GameTestGenerator
	public Collection<TestFunction> builds() {
		List<TestFunction> out = new ArrayList<>();
		for (Workplace w : workplaces()) {
			String name = "workplaceBuilt_" + w.entry().id().getPath();
			out.add(new TestFunction(name, name, HUGE_AREA, 12000, 0, true, helper -> build(helper, w)));
		}
		return out;
	}

	static void build(GameTestHelper helper, Workplace w) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation id = w.entry().id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("missing " + id));
		helper.assertTrue(blueprint.entities().isEmpty(), id + " comes with a villager");
		helper.assertTrue(blueprint.blocks().stream().noneMatch(b -> b.state().is(Blocks.CALCITE) || b.state().is(Blocks.JIGSAW)
			|| b.state().is(Blocks.STRUCTURE_VOID)), id + " still has calcite, a jigsaw or structure voids");
		helper.assertTrue(blueprint.blocks().stream().filter(b -> b.state().is(w.block())).count() == 1, id + " hasn't one " + w.block());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		var report = BlueprintSupplies.check(level, new BlueprintData(id, Optional.of(blueprint.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stacks.size() <= 27 * CHESTS.length, id + " needs " + stacks.size() + " stacks");
		for (int i = 0; i < stacks.size(); i++) {
			((Container) helper.getBlockEntity(CHESTS[i / 27])).setItem(i % 27, stacks.get(i));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, placement);
		Villager worker = helper.spawn(EntityType.VILLAGER, WORKER);
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(w.job()));
		worker.setVillagerData(worker.getVillagerData().setProfession(job));
		worker.setVillagerXp(1);
		worker.refreshBrain(level); // as when a villager takes a job: its brain looks for that job's blocks
		BlockPos min = helper.absolutePos(HOUSE);
		BlockPos max = min.offset(blueprint.size().getX() - 1, blueprint.size().getY() - 1, blueprint.size().getZ() - 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			List<BlockPos> blocks = BlockPos.betweenClosedStream(min, max).filter(p -> level.getBlockState(p).is(w.block()))
				.map(BlockPos::immutable).toList();
			helper.assertTrue(blocks.size() == 1, "job blocks built: " + blocks);
			helper.assertTrue(worker.isAlive() && worker.getVillagerData().getProfession() == job, "the worker is now " + worker.getVillagerData().getProfession());
			helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(blocks.get(0))).orElse(false),
				"the worker works at " + worker.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		});
	}
}
