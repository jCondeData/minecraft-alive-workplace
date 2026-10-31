package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA, written from ROADMAP 34.13 and 34.14 (the Winery, the Tailor's Shop, the Print Shop and the Jeweler's Workshop):
 * every tier's one job block is its own worker's job site and no other job's (no barrel in a Winery, no lectern in a
 * Print Shop, III tiers too); the village pieces in all five styles weigh 2, keep the forbidden blocks out and come with
 * a worker whose job takes the piece's block; the blueprint a Journeyman sells is the Table's own and reads in English;
 * each Steward rule holds for its own job only; and every name a player reads is American English.
 */
public class QaLuxuryWorkshopsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A workshop: its house name, its job, the job block, the block that would make another job, its tiers and its English name. */
	private record Shop(String house, VillagerProfession job, Block block, Block forbidden, List<StarterBlueprints.Entry> tiers, String name) {
	}

	private static List<Shop> shops() {
		return List.of(
			new Shop("winery", ModVillagers.VINTNER, Blocks.CAULDRON, Blocks.BARREL,
				List.of(StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.WINERY_3), "Winery"),
			new Shop("tailors_shop", ModVillagers.TAILOR, Blocks.LOOM, Blocks.COMPOSTER,
				List.of(StarterBlueprints.TAILORS_SHOP, StarterBlueprints.TAILORS_SHOP_2), "Tailor's Shop"),
			new Shop("print_shop", ModVillagers.PRINTER, Blocks.CARTOGRAPHY_TABLE, Blocks.LECTERN,
				List.of(StarterBlueprints.PRINT_SHOP, StarterBlueprints.PRINT_SHOP_2), "Print Shop"),
			new Shop("jewellers_workshop", ModVillagers.JEWELLER, Blocks.STONECUTTER, Blocks.SMITHING_TABLE,
				List.of(StarterBlueprints.JEWELLERS_WORKSHOP, StarterBlueprints.JEWELLERS_WORKSHOP_2, StarterBlueprints.JEWELLERS_WORKSHOP_3), "Jeweler's Workshop"));
	}

	/** Whether {@code state} is some job's job site (any job but none), and which jobs take it. */
	private static List<VillagerProfession> jobsTaking(BlockState state) {
		Optional<Holder<PoiType>> poi = PoiTypes.forState(state);
		if (poi.isEmpty()) {
			return List.of();
		}
		return BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(p -> p != VillagerProfession.NONE && p.heldJobSite().test(poi.get())).toList();
	}

	/**
	 * Every tier of the four, the III ones too: exactly one job block, it is the shop's own block, and the shop's worker
	 * takes it as his job site; so no second block draws a vanilla job (a barrel's fisherman, a lectern's librarian).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyTierHasOneJobBlockAndItIsItsWorkersOwn(GameTestHelper helper) {
		for (Shop s : shops()) {
			for (StarterBlueprints.Entry e : s.tiers()) {
				Blueprint b = BlueprintLibrary.get(helper.getLevel(), e.id()).orElseThrow(() -> new GameTestAssertException("no blueprint " + e.id()));
				List<BlockState> sites = b.blocks().stream().map(x -> x.state()).filter(st -> !jobsTaking(st).isEmpty()).toList();
				helper.assertTrue(sites.size() == 1, e.id() + " has " + sites.size() + " job blocks: " + sites);
				helper.assertTrue(sites.get(0).is(s.block()), e.id() + "'s job block is " + sites.get(0) + ", not " + s.block());
				helper.assertTrue(jobsTaking(sites.get(0)).contains(s.job()), e.id() + ": a " + s.job() + " doesn't take " + sites.get(0));
			}
		}
		helper.succeed();
	}

	/** The item's two "no": no barrel in any tier of the Winery (the fisherman's), no lectern in any Print Shop (the librarian's). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void noWineryHasABarrelAndNoPrintShopALectern(GameTestHelper helper) {
		for (Shop s : shops()) {
			for (StarterBlueprints.Entry e : s.tiers()) {
				Blueprint b = BlueprintLibrary.get(helper.getLevel(), e.id()).orElseThrow();
				for (Block bad : List.of(Blocks.BARREL, Blocks.LECTERN)) {
					long n = b.blocks().stream().filter(x -> x.state().is(bad)).count();
					helper.assertTrue(n == 0, e.id() + " has " + n + " " + BuiltInRegistries.BLOCK.getKey(bad));
				}
			}
		}
		helper.succeed();
	}

	/** Each of the four grows in all five styles' house pools at weight 2 (34.13: "weight 2"), while its job is on. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void eachVillagePieceWeighsTwoInEveryStyle(GameTestHelper helper) {
		var pools = helper.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		for (String style : VillageHouses.STYLES) {
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			helper.assertTrue(pool != null, "no house pool for " + style);
			for (Shop s : shops()) {
				String id = AliveWorkplace.id("village/" + style + "_" + s.house()).toString();
				int weight = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
					.filter(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id) + "(?![a-z_0-9]).*"))
					.mapToInt(p -> p.getSecond()).sum();
				helper.assertTrue(weight == 2, style + " " + s.house() + " weighs " + weight);
			}
		}
		helper.succeed();
	}

	/**
	 * The four village pieces in all five styles: one of the shop's block, none of the block that would make another job,
	 * and one villager already of the shop's job, whose job takes the piece's block.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyStylesPieceHasItsBlockAndAWorkerWhoTakesIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String style : VillageHouses.STYLES) {
			for (Shop s : shops()) {
				ResourceLocation id = AliveWorkplace.id("village/" + style + "_" + s.house());
				StructureTemplate t = level.getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("missing " + id));
				CompoundTag tag = t.save(new CompoundTag());
				ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
				List<BlockState> states = new ArrayList<>();
				for (int i = 0; i < palette.size(); i++) {
					states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
				}
				ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
				int own = 0;
				int forbidden = 0;
				int sites = 0;
				for (int i = 0; i < blocks.size(); i++) {
					BlockState st = states.get(blocks.getCompound(i).getInt("state"));
					own += st.is(s.block()) ? 1 : 0;
					forbidden += st.is(s.forbidden()) || st.is(Blocks.BARREL) || st.is(Blocks.LECTERN) ? 1 : 0;
					sites += jobsTaking(st).isEmpty() ? 0 : 1;
				}
				helper.assertTrue(own == 1 && sites == 1, id + ": " + own + " of its block, " + sites + " job blocks");
				helper.assertTrue(forbidden == 0, id + " has " + forbidden + " blocks that would make another job");
				helper.assertTrue(jobsTaking(s.block().defaultBlockState()).contains(s.job()), s.job() + " doesn't take " + s.block());
				ListTag entities = tag.getList("entities", Tag.TAG_COMPOUND);
				String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(s.job()).toString();
				long workers = 0;
				for (int i = 0; i < entities.size(); i++) {
					CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
					if (nbt.getString("id").equals("minecraft:villager") && nbt.getCompound("VillagerData").getString("profession").equals(job)) {
						workers++;
					}
				}
				helper.assertTrue(workers == 1 && entities.size() == 1, id + " comes with " + workers + " " + job + " among " + entities.size() + " entities");
			}
		}
		helper.succeed();
	}

	/**
	 * The blueprint a Journeyman Vintner, Tailor, Printer and Jeweler sells is the Blueprint Table's own (the same
	 * structure, the Table can open it) and its name reads in English ("Winery", not a key); one level below, nothing.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theJourneymansBlueprintIsTheTablesOwnAndReadsInEnglish(GameTestHelper helper) {
		int x = 0;
		for (Shop s : shops()) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x++, 2, 0));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(s.job()).setLevel(1));
			v.setVillagerXp(1);
			((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
			helper.assertTrue(blueprintOffers(v).isEmpty(), "an Apprentice " + s.job() + " sells a blueprint");
			((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
			List<MerchantOffer> offers = blueprintOffers(v);
			helper.assertTrue(offers.size() == 1, "a Journeyman " + s.job() + " sells " + offers.size() + " blueprints");
			ResourceLocation sold = BlueprintItem.data(offers.get(0).getResult()).map(d -> d.structure()).orElseThrow();
			helper.assertTrue(sold.equals(s.tiers().get(0).id()), s.job() + " sells " + sold);
			helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), sold).isPresent(), "the Table has no " + sold);
			String name = offers.get(0).getResult().getHoverName().getString();
			helper.assertTrue(name.contains(s.name()) && !name.contains("aliveworkplace."), s.job() + "'s blueprint is called \"" + name + "\"");
			v.discard();
		}
		helper.succeed();
	}

	private static List<MerchantOffer> blueprintOffers(Villager v) {
		return v.getOffers().stream().filter(o -> o.getResult().is(ModItems.BLUEPRINT)).toList();
	}

	/**
	 * Each workshop's Steward rule holds for its own job only: with only the other three jobs (each without a
	 * workstation) in the village it doesn't; its own worker added, it does.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLuxuryStewardRules"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLuxuryStewardRules")
	public void eachStewardRuleHoldsForItsOwnJobOnly(GameTestHelper helper) {
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
		long day = StewardWishes.day(level);
		List<String> rules = List.of("workplace_winery", "workplace_tailors_shop", "workplace_print_shop", "workplace_jewellers_workshop");
		List<Shop> shops = shops();
		for (int i = 0; i < shops.size(); i++) {
			String ruleId = rules.get(i);
			StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id(ruleId))).findFirst()
				.orElseThrow(() -> new GameTestAssertException("rule " + ruleId + " didn't load"));
			helper.assertTrue(rule.effect().blueprint().equals(Optional.of(AliveWorkplace.id(shops.get(i).house()))), ruleId + " builds " + rule.effect().blueprint());
			List<Villager> others = new ArrayList<>();
			int x = 2;
			for (int j = 0; j < shops.size(); j++) {
				if (j != i) {
					others.add(worker(helper, new BlockPos(x, 2, 18), shops.get(j).job()));
					x += 2;
				}
			}
			StewardWishes.Verdict without = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
			helper.assertTrue(without.status() != StewardWishes.Status.HELD, ruleId + " holds with only the other three jobs: " + without.checks());
			Villager own = worker(helper, new BlockPos(x, 2, 18), shops.get(i).job());
			StewardWishes.Verdict with = StewardWishes.judge(rule, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
			helper.assertTrue(with.status() == StewardWishes.Status.HELD, ruleId + " doesn't hold with its own worker: " + with.status() + " " + with.checks());
			others.forEach(Villager::discard);
			own.discard();
		}
		helper.succeed();
	}

	private static Villager worker(GameTestHelper helper, BlockPos pos, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.setVillagerXp(1);
		return v;
	}

	/**
	 * Every name a player reads for the four (each tier's blueprint, the village piece, the job and its zombie, the config
	 * switch) is there, in English, and spelled the American way (owner, 2026-10-26: jeweler, color), keys unchanged.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyNameAPlayerReadsIsAmericanEnglish(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = new ArrayList<>();
		for (Shop s : shops()) {
			for (StarterBlueprints.Entry e : s.tiers()) {
				keys.add("blueprint.aliveworkplace." + e.id().getPath());
			}
			keys.add("village_piece.aliveworkplace." + s.house());
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(s.job()).getPath();
			keys.add("entity.minecraft.villager." + job);
			keys.add("entity.minecraft.zombie_villager." + job);
		}
		for (String config : List.of("vintners", "tailors", "printers", "jewellers")) {
			keys.add("aliveworkplace.config." + config);
			keys.add("aliveworkplace.config." + config + ".tooltip");
		}
		java.util.regex.Pattern british = java.util.regex.Pattern.compile("(?i)jewell|colour|grey|centre|armour|defence|cancell|favour|metre");
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "no text for " + key);
			String text = lang.getOrDefault(key);
			helper.assertTrue(!text.isBlank() && !british.matcher(text).find(), key + " reads \"" + text + "\"");
		}
		helper.assertTrue(lang.getOrDefault("blueprint.aliveworkplace.jewellers_workshop").equals("Jeweler's Workshop"),
			"the Jeweler's Workshop is called " + lang.getOrDefault("blueprint.aliveworkplace.jewellers_workshop"));
		helper.assertTrue(lang.getOrDefault("entity.minecraft.villager.jeweller").equals("Jeweler"), "the job is called " + lang.getOrDefault("entity.minecraft.villager.jeweller"));
		helper.succeed();
	}
}
