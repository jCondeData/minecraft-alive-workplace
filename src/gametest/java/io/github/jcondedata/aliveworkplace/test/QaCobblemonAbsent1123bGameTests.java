package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.RenewalLists;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;

/**
 * QA (lane B, 2026-11-23, lane-b-1123-0338): ROADMAP 50.12's "the mod absent, nothing changes" for the rebuilt Cobblemon
 * builds, written from their blind briefs ({@code tools/blueprints/briefs/{pokemon_center,trainers_house,daycare,
 * ball_workshop,habitat_garden,research_lab,arena}.md}), not from the code, in the plain suite (Cobblemon NOT loaded).
 *
 * <p>The briefs: the Pokémon Center (every look and level), the Daycare, the Habitat Garden and the Arena are
 * {@code COBBLEMON_ONLY} ("in the Blueprint Table, library and creative tab only with Cobblemon"); the Trainer's House
 * and the Ball Workshop are "in the Blueprint Table only with Cobblemon" ({@code COBBLEMON_WORKPLACES}: "without
 * Cobblemon nothing about it changes for the player (it is not in the Table and villages don't grow it)"). The Research
 * Lab is the exception: "it is still listed in the Blueprint Table" without Cobblemon (the analyzer's cell is air), but
 * the Steward builds it "only with Cobblemon". Their Steward rules (Pokémon Center, Trainer's House, Ball Workshop,
 * Research Lab), their renewal lists (trainer, ball smith, fossil scientist) and the hall's "What next?" tips (Pokémon
 * Center, Arena) all need Cobblemon; a Journeyman Nurse sells the Pokémon Center only with it.
 *
 * <p>Already covered elsewhere, not repeated: {@code PokemonCenterGameTests.thePokemonCenterNeedsCobblemon} and
 * {@code PokemonBuildsGameTests.thePokemonJobBuildsNeedCobblemon} (COBBLEMON_ONLY off the Table listing),
 * {@code ArenaGameTests.withoutCobblemonThereIsNoArena}, {@code WorkplacesGameTests} (each workplace rule alone is
 * MOD_MISSING), {@code VillageGameTests.everyVillageTypeCanGrowTheOtherHouses} (the village pools).
 */
public class QaCobblemonAbsent1123bGameTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** The families the briefs keep away from a player without Cobblemon (every level, look and style counts as its family). */
	private static final Set<ResourceLocation> HIDDEN = Set.of(StarterBlueprints.POKEMON_CENTER.id(), StarterBlueprints.TRAINERS_HOUSE.id(),
		StarterBlueprints.DAYCARE.id(), StarterBlueprints.BALL_WORKSHOP.id(), StarterBlueprints.HABITAT_GARDEN.id(), StarterBlueprints.ARENA.id());
	/** The seven: the six above and the Research Lab, which stays listed but which no Steward builds without Cobblemon. */
	private static final Set<ResourceLocation> SEVEN = Stream.concat(HIDDEN.stream(), Stream.of(StarterBlueprints.RESEARCH_LAB.id()))
		.collect(java.util.stream.Collectors.toSet());

	/** Every level and look of the six hidden families, by the ids the mod ships. */
	static List<StarterBlueprints.Entry> hiddenEntries() {
		List<StarterBlueprints.Entry> out = new ArrayList<>(StarterBlueprints.POKEMON_CENTER_LOOKS);
		out.addAll(StarterBlueprints.POKEMON_CENTER_2_LOOKS);
		out.addAll(List.of(StarterBlueprints.TRAINERS_HOUSE, StarterBlueprints.BALL_WORKSHOP, StarterBlueprints.DAYCARE, StarterBlueprints.DAYCARE_2,
			StarterBlueprints.HABITAT_GARDEN, StarterBlueprints.HABITAT_GARDEN_2));
		out.addAll(StarterBlueprints.ARENAS);
		return out;
	}

	private static boolean hidden(ResourceLocation id) {
		return HIDDEN.contains(StewardConditions.family(id));
	}

	private static void plainSuite(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon"), "Cobblemon is installed in the plain suite");
	}

	/**
	 * The Blueprint Table as a player opens it (the server-wide library and a village's, unlocks on), the library list and
	 * every creative tab: none of the six families in any level, look or style. Every one of them still loads for an
	 * operator (so the lists really left them out), and the Research Lab, which the brief keeps listed, is still there.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noCobblemonBuildIsInTheTableTheLibraryOrTheCreativeTab(GameTestHelper helper) {
		plainSuite(helper);
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		List<ResourceLocation> everything = BlueprintLibrary.list(level.getServer(), true);
		for (StarterBlueprints.Entry e : hiddenEntries()) {
			if (!everything.contains(e.id())) {
				problems.add("(setup) " + e.id() + " doesn't load even for operators");
			}
		}
		Set<ResourceLocation> shown = new TreeSet<>();
		TableServer.listing(level.getServer()).forEach(e -> shown.add(e.id()));
		TableServer.listing(level, null).forEach(e -> shown.add(e.id()));
		shown.addAll(BlueprintLibrary.list(level.getServer(), false));
		for (ResourceLocation id : shown) {
			if (hidden(id)) {
				problems.add("listed at the Table/library without Cobblemon: " + id);
			}
		}
		if (shown.stream().noneMatch(id -> StewardConditions.family(id).equals(StarterBlueprints.RESEARCH_LAB.id()))) {
			problems.add("the Research Lab, which its brief keeps in the Table without Cobblemon, is gone from it");
		}
		CreativeModeTab.ItemDisplayParameters params = new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess());
		int blueprints = 0;
		for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
				continue;
			}
			tab.buildContents(params);
			for (ItemStack stack : tab.getDisplayItems()) {
				Optional<ResourceLocation> id = BlueprintItem.data(stack).map(d -> d.structure());
				if (id.isPresent()) {
					blueprints++;
					if (hidden(id.get())) {
						problems.add("in the creative tab " + BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab) + " without Cobblemon: " + id.get());
					}
				}
			}
		}
		helper.assertTrue(blueprints > 20, "(setup) only " + blueprints + " blueprints found in the creative tabs");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[QA 1123b] " + shown.size()
			+ " listed ids and " + blueprints + " creative blueprints checked, none of the six Cobblemon families"));
		helper.succeed();
	}

	/**
	 * Every trade of every one of our jobs a villager can have without Cobblemon ({@code Stations.available}: not the
	 * Pokémon jobs, whose job blocks don't exist), at every level, each listing asked six times as random villagers would
	 * be: none sells a Blueprint of the six families. (A Journeyman Nurse sells the Pokémon Center only with Cobblemon; the
	 * Daycare, Habitat Garden and Arena are sold by Cobblemon jobs.)
	 */
	//$ gametest_ticks_batch BIG_AREA '100' '"qa_cobblemon_absent_trades_1123b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 100, batch = "qa_cobblemon_absent_trades_1123b")
	public void noVillagerSellsACobblemonBuild(GameTestHelper helper) {
		plainSuite(helper);
		Leftovers.clear(helper);
		List<String> problems = new ArrayList<>();
		int offers = 0;
		int blueprints = 0;
		for (Map.Entry<VillagerProfession, it.unimi.dsi.fastutil.ints.Int2ObjectMap<VillagerTrades.ItemListing[]>> job : VillagerTrades.TRADES.entrySet()) {
			ResourceLocation jobId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job.getKey());
			// only jobs a villager can have in this game: a Habitat or Daycare Keeper (Cobblemon's Pasture Block) can't be had
			if (jobId == null || !jobId.getNamespace().equals(AliveWorkplace.MOD_ID) || !io.github.jcondedata.aliveworkplace.work.Stations.available(job.getKey())) {
				continue;
			}
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
			villager.setNoAi(true);
			villager.setVillagerData(villager.getVillagerData().setProfession(job.getKey()));
			for (int lvl = 1; lvl <= 5; lvl++) {
				VillagerTrades.ItemListing[] listings = job.getValue().get(lvl);
				if (listings == null) {
					continue;
				}
				villager.setVillagerData(villager.getVillagerData().setLevel(lvl));
				for (VillagerTrades.ItemListing listing : listings) {
					for (long seed = 0; seed < 6; seed++) {
						MerchantOffer offer = listing.getOffer(villager, RandomSource.create(1123L + seed));
						if (offer == null) {
							continue;
						}
						offers++;
						Optional<ResourceLocation> id = BlueprintItem.data(offer.getResult()).map(d -> d.structure());
						if (id.isPresent()) {
							blueprints++;
							if (hidden(id.get())) {
								problems.add(jobId.getPath() + " (level " + lvl + ") sells " + id.get());
							}
						}
					}
				}
			}
			villager.discard();
		}
		helper.assertTrue(blueprints > 0, "(setup) no job sold any blueprint in " + offers + " offers");
		problems = problems.stream().distinct().toList();
		helper.assertTrue(problems.isEmpty(), "without Cobblemon: " + String.join("; ", problems));
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[QA 1123b] " + offers + " offers ("
			+ blueprints + " blueprints) of our jobs checked: no Cobblemon build sold"));
		helper.succeed();
	}

	/**
	 * A City-rank village, without Cobblemon, staged for every Cobblemon building's reason: a Trainer, a Trainer Leader, a
	 * Ball Smith, a Fossil Scientist, a Pokémon Trader, a Tutor, a Nurse, a Habitat and a Daycare Keeper, none with a
	 * workstation, and no Pokémon Center. The Steward's morning (his real ranking on the hall) wishes for none of the seven
	 * buildings, nor would any rule that builds one hold; no renewal list offers one; "What next?" suggests neither a
	 * Pokémon Center nor an Arena. A Postman without a workstation in the same village does get his Post Office rule held,
	 * so the staging is one the Steward plans for.
	 */
	//$ gametest_ticks_batch BIG_AREA '100' '"qa_cobblemon_absent_plan_1123b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 100, batch = "qa_cobblemon_absent_plan_1123b")
	public void aVillagePlannedWithoutCobblemonAsksForNoneOfThem(GameTestHelper helper) {
		plainSuite(helper);
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.CITY);
		List<String> jobs = List.of("trainer", "trainer_leader", "ball_smith", "fossil_scientist", "pokemon_trader", "tutor", "nurse",
			"habitat_keeper", "daycare_keeper", "postman");
		int x = 2;
		for (String job : jobs) {
			Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 18));
			x += 2;
			worker.setNoAi(true);
			VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(AliveWorkplace.id(job));
			helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).equals(AliveWorkplace.id(job)), "(setup) no job " + job);
			worker.setVillagerData(worker.getVillagerData().setProfession(profession).setLevel(4));
			worker.setVillagerXp(150);
		}
		List<String> problems = new ArrayList<>();
		// the Steward's morning: his ranking of every loaded rule, kept on the hall
		helper.assertTrue(StewardWishes.rankIfDue(level, hall), "(setup) the hall's wishes weren't ranked");
		List<StewardWishes.Wish> wishes = entity.stewardWishes().wishes();
		for (StewardWishes.Wish wish : wishes) {
			wish.effect().blueprint().filter(b -> SEVEN.contains(StewardConditions.family(b)))
				.ifPresent(b -> problems.add("the Steward wishes for " + b + " (rule " + wish.rule() + ")"));
		}
		helper.assertTrue(!wishes.isEmpty(), "(setup) the Steward wishes for nothing at all in a City of ten workers without workstations");
		// every rule that would build one, judged on the same village: none holds (his wish list stops at eight)
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		long day = StewardWishes.day(level);
		StewardRules.Rule postOffice = StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id("workplace_post_office"))).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("(setup) no rule workplace_post_office"));
		helper.assertTrue(StewardWishes.judge(postOffice, facts, StewardWishes.State.EMPTY, day).status() == StewardWishes.Status.HELD,
			"(setup) the Postman's Post Office rule doesn't hold, so the staging isn't one the Steward plans for");
		int cobblemonRules = 0;
		for (StewardRules.Rule rule : StewardRules.all()) {
			Optional<ResourceLocation> builds = rule.effect().blueprint();
			if (builds.isEmpty() || !SEVEN.contains(StewardConditions.family(builds.get()))) {
				continue;
			}
			cobblemonRules++;
			StewardWishes.Verdict verdict = StewardWishes.judge(rule, facts, StewardWishes.State.EMPTY, day);
			if (verdict.status() == StewardWishes.Status.HELD) {
				problems.add("rule " + rule.id() + " (builds " + builds.get() + ") holds without Cobblemon");
			}
		}
		helper.assertTrue(cobblemonRules >= 4, "(setup) only " + cobblemonRules + " Steward rules build one of the seven; the briefs name four "
			+ "(Pokémon Center, Trainer's House, Ball Workshop, Research Lab)");
		// renewing an old house: no list offers one of the seven, and none at all for the Cobblemon jobs' old houses
		for (RenewalLists.RenewalList list : RenewalLists.all()) {
			for (ResourceLocation b : list.buildings()) {
				if (SEVEN.contains(StewardConditions.family(b))) {
					problems.add("renewal list " + list.name() + " offers " + b);
				}
			}
		}
		for (String job : List.of("trainer", "ball_smith", "fossil_scientist")) {
			RenewalLists.forJob(AliveWorkplace.id(job)).ifPresent(l -> problems.add("an old " + job + "'s house is renewed by list " + l.name()
				+ " " + l.buildings()));
		}
		// the hall's "What next?"
		for (VillageAdvice.Tip tip : VillageAdvice.tips(level, hall)) {
			if (tip.key().equals("pokemon_center") || tip.key().equals("arena")) {
				problems.add("\"What next?\" suggests the " + tip.key() + " without Cobblemon");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[QA 1123b] a City without Cobblemon: "
			+ wishes.size() + " wishes " + wishes.stream().map(w -> w.rule().getPath()).toList() + ", " + cobblemonRules
			+ " Cobblemon rules held back, no renewal or tip for them"));
		helper.succeed();
	}
}
