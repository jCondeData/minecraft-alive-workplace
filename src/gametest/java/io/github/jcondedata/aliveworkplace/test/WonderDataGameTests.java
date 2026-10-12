package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.PokemonCensus;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.rules.LegendSettled;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Wonder data, requirements and caps (ROADMAP 35.2): the test folly's file loads, a broken file is skipped with its
 * reason, a Wonder from a data pack loads like ours; every requirement type holds and fails; a {@code legend} falls back
 * to its stand-in; the caps by rank, one of a kind, one going up, the server limit and the switches;
 * {@code /workplace wonders}; the config; a world without Wonders loads.
 */
public class WonderDataGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation FOLLY_TWO = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly_two");
	private static final ResourceLocation TEST_LEGEND = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_legend");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** A good Wonder file, as a server owner would write one. */
	static final String GOOD = "{\"name\": \"wonder.owner.arch.name\", \"description\": \"wonder.owner.arch.description\", \"icon\": \"minecraft:bell\","
		+ " \"requirements\": [{\"type\": \"rank_at_least\", \"rank\": \"town\"}],"
		+ " \"stages\": [{\"name\": \"wonder.owner.arch.stage.1\", \"parts\": [{\"part\": \"owner_pack:wonder/arch/stage_1_0\", \"offset\": [0, 0, 0]}],"
		+ " \"goods\": [{\"item\": \"minecraft:bread\", \"count\": 16, \"job\": \"aliveworkplace:chef\"}]}],"
		+ " \"powers\": [{\"type\": \"mood\", \"scope\": \"village\", \"amount\": 4}]}";

	private static Condition condition(String json) {
		return Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
	}

	private static void check(GameTestHelper helper, Condition c, BlockPos hall, boolean met) {
		Condition.Progress p = c.progress(helper.getLevel(), hall);
		helper.assertTrue(p.met() == met && c.met(helper.getLevel(), hall) == met, c.type() + ": expected " + (met ? "met" : "not met") + ", got " + p.have() + " of " + p.need());
		helper.assertTrue(p.line().getContents() instanceof TranslatableContents t && t.getKey().startsWith("rule.aliveworkplace."), c.type() + " line");
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		return v;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	// ---------------------------------------------------------------- the files

	/** The test folly's file (gametest resources only) loads with its stages, goods, powers, crew and the Master Architect's line. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTestFollyLoads(GameTestHelper helper) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.loaded().keySet() + ", skipped: " + Wonders.problems());
		helper.assertTrue(folly.name().equals("wonder.aliveworkplace_test.test_folly.name") && folly.icon().toString().equals("minecraft:stone_bricks")
			&& folly.crew() == 12 && folly.enabled(), "name, icon, crew: " + folly);
		helper.assertTrue(folly.stages().size() == 3 && folly.stages().get(0).parts().get(0).part().toString().equals("aliveworkplace_test:wonder/test_folly/stage_1_0"),
			"stages: " + folly.stages());
		Wonder.Stage two = folly.stages().get(1);
		helper.assertTrue(two.goods().size() == 2 && two.goods().get(0).tag().map(Object::toString).equals(Optional.of("minecraft:small_flowers"))
			&& two.goods().get(1).potion().map(Object::toString).equals(Optional.of("minecraft:healing")) && two.goods().get(1).count() == 2
			&& two.goods().get(1).job().toString().equals("minecraft:cleric"), "stage II's goods: " + two.goods());
		helper.assertTrue(folly.powers().size() == 1 && folly.powers().get(0).type().equals("beauty") && folly.powers().get(0).numbers().get("points").getAsInt() == 2,
			"powers: " + folly.powers());
		helper.assertTrue(folly.requirements().size() == 3 && folly.requirements().get(2) instanceof LegendSettled architect
			&& architect.id().equals(Wonders.MASTER_ARCHITECT) && architect.standIn().size() == 1, "the Master Architect's line: " + folly.requirements());
		helper.assertTrue(Wonders.all().stream().anyMatch(w -> w.id().equals(FOLLY)), "the folly isn't listed");
		helper.succeed();
	}

	/** A file with a mistake is skipped with its path and the reason, the good one beside it loads; a switched-off one is skipped quietly. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"wonderFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wonderFiles")
	public void aBrokenFileIsSkippedWithItsReason(GameTestHelper helper) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		files.put(ResourceLocation.parse("owner_pack:wonders/arch.json"), GOOD);
		files.put(ResourceLocation.parse("owner_pack:wonders/not_json.json"), "{not json");
		files.put(ResourceLocation.parse("owner_pack:wonders/bad_condition.json"), GOOD.replace("rank_at_least", "rank_at_leest"));
		files.put(ResourceLocation.parse("owner_pack:wonders/bad_power.json"), GOOD.replace("\"mood\"", "\"moood\""));
		files.put(ResourceLocation.parse("owner_pack:wonders/bad_count.json"), GOOD.replace("\"count\": 16", "\"count\": 0"));
		files.put(ResourceLocation.parse("owner_pack:wonders/bad_job.json"), GOOD.replace("aliveworkplace:chef", "aliveworkplace:cheff"));
		files.put(ResourceLocation.parse("owner_pack:wonders/no_stages.json"), GOOD.replaceAll("\"stages\": \\[.*\\]}\\],", "\"stages\": [],"));
		files.put(ResourceLocation.parse("owner_pack:wonders/no_parts.json"), GOOD.replaceAll("\"parts\": \\[\\{[^}]*\\}\\]", "\"parts\": []"));
		files.put(ResourceLocation.parse("owner_pack:wonders/bad_icon.json"), GOOD.replace("minecraft:bell", "minecraft:no_such_item"));
		files.put(ResourceLocation.parse("owner_pack:wonders/switched_off.json"), "{\"enabled\": false}");
		files.put(ResourceLocation.parse("owner_pack:wonders/needs_a_mod.json"),
			GOOD.replaceFirst("\\{", "{\"fabric:load_conditions\": [{\"condition\": \"fabric:all_mods_loaded\", \"values\": [\"no_such_mod\"]}], "));
		Map<ResourceLocation, Wonder> loaded = Wonders.load(files);
		Map<ResourceLocation, String> problems = Wonders.problems();
		helper.assertTrue(loaded.keySet().equals(Set.of(ResourceLocation.parse("owner_pack:arch"))), "loaded: " + loaded.keySet() + ", skipped: " + problems);
		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("not_json", "");
		expected.put("bad_condition", "rank_at_leest");
		expected.put("bad_power", "moood");
		expected.put("bad_count", "count");
		expected.put("bad_job", "cheff");
		expected.put("no_stages", "no stages");
		expected.put("no_parts", "no parts");
		expected.put("bad_icon", "no_such_item");
		List<String> wrong = new ArrayList<>();
		expected.forEach((name, reason) -> {
			String why = problems.get(ResourceLocation.parse("owner_pack:wonders/" + name + ".json"));
			if (why == null || !why.contains(reason)) {
				wrong.add(name + ": " + why);
			}
		});
		helper.assertTrue(wrong.isEmpty() && problems.size() == expected.size(), "the reasons logged: " + wrong + " in " + problems);
		helper.assertTrue(!problems.containsKey(ResourceLocation.parse("owner_pack:wonders/switched_off.json"))
			&& !problems.containsKey(ResourceLocation.parse("owner_pack:wonders/needs_a_mod.json")), "a switched-off file was logged as broken");
		helper.succeed();
	}

	/** A server owner's Wonder in a data pack folder loads beside ours (the real reload, through a pack on disk). */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"wonderPack"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "wonderPack")
	public void aWonderFromADataPackLoads(GameTestHelper helper) throws Exception {
		var server = helper.getLevel().getServer();
		Path dir = Files.createTempDirectory("aliveworkplace-wonder-pack");
		Path file = dir.resolve("data/owner_pack/wonders/arch.json");
		Files.createDirectories(file.getParent());
		Files.writeString(dir.resolve("pack.mcmeta"), "{\"pack\": {\"pack_format\": 48, \"description\": \"a server owner's Wonder\"}}", StandardCharsets.UTF_8);
		Files.writeString(file, GOOD, StandardCharsets.UTF_8);
		PathPackResources pack = new PathPackResources(new PackLocationInfo("owner_pack", Component.literal("owner_pack"), PackSource.WORLD, Optional.empty()), dir);
		List<PackResources> packs = new ArrayList<>(server.getResourceManager().listPacks().toList());
		packs.add(pack);
		try {
			// not closed: closing it would close the server's own packs
			MultiPackResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, packs);
			Wonders.reload(manager);
			Wonder arch = Wonders.loaded().get(ResourceLocation.parse("owner_pack:arch"));
			helper.assertTrue(arch != null, "the pack's Wonder isn't loaded: " + Wonders.loaded().keySet() + ", skipped: " + Wonders.problems());
			helper.assertTrue(arch.stages().size() == 1 && arch.powers().get(0).type().equals("mood") && arch.crew() == 12
				&& arch.requirements().size() == 2 && arch.requirements().get(1) instanceof LegendSettled l && l.id().equals(Wonders.MASTER_ARCHITECT),
				"the pack's Wonder: " + arch);
			helper.assertTrue(Wonders.loaded().containsKey(FOLLY), "ours went: " + Wonders.loaded().keySet());
		} finally {
			pack.close();
			Wonders.reload(server.getResourceManager());
			Files.deleteIfExists(file);
		}
		helper.assertTrue(!Wonders.loaded().containsKey(ResourceLocation.parse("owner_pack:arch")) && Wonders.loaded().containsKey(FOLLY),
			"after the pack left: " + Wonders.loaded().keySet());
		helper.succeed();
	}

	// ---------------------------------------------------------------- the requirements

	/** Every requirement type a Wonder uses (but {@code coastal}, below), false and then true on a staged village. */
	//$ gametest_ticks_batch AREA '200' '"wonderConditions"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "wonderConditions")
	public void everyRequirementHoldsAndFails(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), ModAttachments.LEGEND::has)) {
				LegendRecord.get(level).forget(v.getUUID());
				v.discard();
			}
			LegendPowers.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		BlockPos hall = helper.absolutePos(HALL);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.SMOKER);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.CHEST);
		helper.runAfterDelay(5, () -> {
			// rank_at_least
			Condition town = condition("{\"type\": \"rank_at_least\", \"rank\": \"town\"}");
			check(helper, town, hall, false);
			entity.setRank(VillageRanks.Rank.TOWN);
			check(helper, town, hall, true);

			// research_levels
			Condition research = condition("{\"type\": \"research_levels\", \"count\": 20}");
			check(helper, research, hall, false);
			entity.setResearch(new Research.State(Map.of("swift_hands", 10, "hearth", 10), Optional.empty(), 0, false));
			check(helper, research, hall, true);

			// finished: a blueprint in any style
			Condition chapels = condition("{\"type\": \"finished\", \"blueprint\": \"aliveworkplace_test:wonder_chapel\", \"count\": 2}");
			check(helper, chapels, hall, false);
			BuildSiteManager sites = BuildSiteManager.get(level);
			BlueprintData.Placement one = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 20)), Rotation.NONE, Mirror.NONE);
			BlueprintData.Placement two = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(10, 2, 20)), Rotation.NONE, Mirror.NONE);
			Leftovers.after(helper, () -> {
				sites.forgetFinished(one);
				sites.forgetFinished(two);
			});
			sites.recordFinished(ResourceLocation.parse("aliveworkplace_test:wonder_chapel"), one, UUID.randomUUID());
			check(helper, chapels, hall, false);
			sites.recordFinished(ResourceLocation.parse("aliveworkplace:styled/wondertest/aliveworkplace_test/wonder_chapel"), two, UUID.randomUUID());
			check(helper, chapels, hall, true);

			// villagers, job_level, and the Master Architect's stand-in (a Master builder) with Legends off
			Condition villagers = condition("{\"type\": \"villagers\", \"count\": 2}");
			Condition clerics = condition("{\"type\": \"job_level\", \"job\": \"minecraft:cleric\", \"level\": 5, \"count\": 1}");
			Condition architect = Wonders.masterArchitect();
			check(helper, villagers, hall, false);
			check(helper, clerics, hall, false);
			villager(helper, new BlockPos(6, 2, 6), VillagerProfession.CLERIC, 4);
			check(helper, clerics, hall, false);
			villager(helper, new BlockPos(8, 2, 6), VillagerProfession.CLERIC, 5);
			check(helper, villagers, hall, true);
			check(helper, clerics, hall, true);

			// legend: settled; with Legends on, a stand-in doesn't stand in
			Condition seer = condition("{\"type\": \"legend\", \"id\": \"aliveworkplace_test:test_legend\","
				+ " \"stand_in\": [{\"type\": \"job_level\", \"job\": \"minecraft:cleric\", \"level\": 5, \"count\": 1}]}");
			check(helper, seer, hall, false);
			check(helper, architect, hall, false);
			boolean legends = Wonders.LEGENDS;
			try {
				Wonders.LEGENDS = false;
				check(helper, seer, hall, true); // the Master Cleric stands in
				check(helper, architect, hall, false); // no Master builder yet
				Condition.Progress p = architect.progress(level, hall);
				helper.assertTrue(key(p.line()).equals("rule.aliveworkplace.legend_stand_in"), "the stand-in's line: " + p.line().getString());
				villager(helper, new BlockPos(10, 2, 6), ModVillagers.BUILDER, 5);
				check(helper, architect, hall, true);
			} finally {
				Wonders.LEGENDS = legends;
			}
			check(helper, architect, hall, false); // Legends on again: the Master Architect himself is wanted
			check(helper, seer, hall, false);
			Condition unknown = condition("{\"type\": \"legend\", \"id\": \"aliveworkplace_test:no_such_legend\","
				+ " \"stand_in\": [{\"type\": \"job_level\", \"job\": \"aliveworkplace:builder\", \"level\": 5, \"count\": 1}]}");
			check(helper, unknown, hall, true); // a Legend whose file isn't loaded: its stand-in
			Villager sage = villager(helper, new BlockPos(20, 2, 20), VillagerProfession.NONE, 1);
			Legends.make(level, sage, Legends.get(TEST_LEGEND).orElseThrow(), "test");
			check(helper, seer, hall, true);

			// caravan_routes
			Condition routes = condition("{\"type\": \"caravan_routes\", \"count\": 1}");
			check(helper, routes, hall, false);
			Caravans.Data caravans = Caravans.Data.get(level);
			caravans.toggleRoute(hall, hall.offset(500, 0, 0), 3);
			Leftovers.after(helper, () -> caravans.remove(hall));
			check(helper, routes, hall, true);

			// iron_golems
			Condition golems = condition("{\"type\": \"iron_golems\", \"count\": 1}");
			check(helper, golems, hall, false);
			helper.spawn(EntityType.IRON_GOLEM, new BlockPos(20, 2, 4)).setNoAi(true);
			check(helper, golems, hall, true);

			// meal_kinds: the chest by the smoker is the store (stores earlier batches left nearby are emptied first)
			for (BlockPos store : io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(level, hall)) {
				if (!helper.getBounds().contains(Vec3.atCenterOf(store)) && level.getBlockEntity(store) instanceof Container leftover) {
					leftover.clearContent();
				}
			}
			Condition meals = condition("{\"type\": \"meal_kinds\", \"count\": 2}");
			check(helper, meals, hall, false);
			Container chest = (Container) helper.getBlockEntity(new BlockPos(4, 2, 3));
			chest.setItem(0, new ItemStack(Items.BREAD, 3));
			chest.setItem(1, new ItemStack(Items.BAKED_POTATO, 1));
			check(helper, meals, hall, true);

			// pastured_pokemon: false without Cobblemon; true with a census that has them
			Condition pokemon = condition("{\"type\": \"pastured_pokemon\", \"count\": 25, \"types\": 10}");
			if (!PokemonCensus.EXTENSION.present()) {
				check(helper, pokemon, hall, false);
				Set<String> types = Set.of("fire", "water", "grass", "electric", "rock", "ground", "ice", "dragon", "fairy", "ghost");
				PokemonCensus.EXTENSION.register("wonder test", new PokemonCensus() {
					@Override
					public Count census(ServerLevel l, BlockPos h) {
						return new Count(25, types, Set.of());
					}

					@Override
					public Component speciesName(String species) {
						return Component.literal(species);
					}

					@Override
					public void openHints(ServerPlayer player, Villager professor) {
					}
				});
				try {
					check(helper, pokemon, hall, true);
				} finally {
					PokemonCensus.EXTENSION.register("", null);
				}
				helper.assertTrue(!PokemonCensus.EXTENSION.present(), "the test census stayed");
			}
			helper.succeed();
		});
	}

	/** {@code mod_loaded}: Minecraft is, a made-up mod isn't; a file without {@code mod} fails. */
	//$ gametest SMALL
	@GameTest(template = SMALL)
	public void modLoadedHoldsAndFails(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		check(helper, condition("{\"type\": \"mod_loaded\", \"mod\": \"minecraft\"}"), hall, true);
		check(helper, condition("{\"type\": \"mod_loaded\", \"mod\": \"no_such_mod\"}"), hall, false);
		try {
			condition("{\"type\": \"mod_loaded\"}");
			helper.fail("a mod_loaded without a mod loaded");
		} catch (IllegalArgumentException expected) {
			// a typo never makes a Wonder free
		}
		helper.assertTrue(Conditions.types().containsAll(List.of("coastal", "mod_loaded", "legend", "pastured_pokemon")), "types: " + Conditions.types());
		helper.succeed();
	}

	/** {@code coastal}: a plains hall isn't on the coast; a beach by the hall is (M32's check, looked at once a day). */
	//$ gametest_batch SMALL '"wonderCoast"'
	@GameTest(template = SMALL, batch = "wonderCoast")
	public void coastalHoldsAndFails(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		String old = level.getBiome(hall).unwrapKey().map(k -> k.location().toString()).orElse("minecraft:plains");
		Condition coastal = condition("{\"type\": \"coastal\"}");
		io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.threat.Conditions.coast(level, hall), "the test world has a coast here already: " + old);
		check(helper, coastal, hall, false);
		try {
			fillBiome(level, hall, "minecraft:beach");
			io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
			check(helper, coastal, hall, true);
		} finally {
			fillBiome(level, hall, old);
			io.github.jcondedata.aliveworkplace.threat.Conditions.forgetCoasts();
		}
		helper.succeed();
	}

	private static void fillBiome(ServerLevel level, BlockPos at, String biome) {
		String cmd = "fillbiome " + (at.getX() - 6) + " " + (at.getY() - 4) + " " + (at.getZ() - 6) + " " + (at.getX() + 6) + " " + (at.getY() + 4)
			+ " " + (at.getZ() + 6) + " " + biome;
		level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput(), cmd);
	}

	// ---------------------------------------------------------------- the caps

	/**
	 * A Town with one Wonder can't start a second and a City can; the same kind twice is refused; one going up at a time;
	 * {@code wonderServerLimit 1} refuses a second village; a Hamlet may hold none; {@code disabledWonders} and the
	 * switch refuse; with the caps clear, an unmet requirement (no Master builder) still refuses.
	 */
	//$ gametest_batch SMALL '"wonderCaps"'
	@GameTest(template = SMALL, batch = "wonderCaps")
	public void theCapsHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(5, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos other = helper.absolutePos(new BlockPos(5, 2, 1));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
		VillageHallBlockEntity otherEntity = (VillageHallBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 1));
		Map<ResourceLocation, Wonder> before = Wonders.loaded();
		Wonder folly = before.get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded");
		Map<ResourceLocation, Wonder> two = new LinkedHashMap<>(before);
		two.put(FOLLY_TWO, new Wonder(FOLLY_TWO, folly.name(), folly.description(), folly.icon(), true, folly.crew(), folly.requirements(), folly.stages(), folly.powers()));
		WonderManager manager = WonderManager.get(level);
		int limit = Wonders.SERVER_LIMIT;
		Set<ResourceLocation> disabled = Wonders.DISABLED;
		List<UUID> made = new ArrayList<>();
		Wonders.setForTest(two);
		try {
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.cap"), "a Hamlet may hold none");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.cap"), "a Village may hold none");
			entity.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Wonders.capRefusal(level, hall, FOLLY).isEmpty(), "a Town with none may start one: " + Wonders.capRefusal(level, hall, FOLLY));
			// with the caps clear, the requirements still count: no Master builder here
			helper.assertTrue(key(Wonders.startRefusal(level, hall, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.requirement"),
				"started without its requirements");

			WonderSite first = manager.begin(FOLLY, hall, UUID.randomUUID(), "Jesse", 3);
			made.add(first.id());
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY_TWO).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.going_up"),
				"a second while the first is going up");
			manager.finish(first.id(), 9);
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY_TWO).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.cap"),
				"a Town with one Wonder started a second");
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.same_kind"),
				"the same kind twice");
			entity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Wonders.capRefusal(level, hall, FOLLY_TWO).isEmpty(), "a City with one may start a second: " + Wonders.capRefusal(level, hall, FOLLY_TWO));
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.same_kind"),
				"the same kind twice in a City");
			helper.assertTrue(key(Wonders.capLine(level, hall)).equals("wonder.aliveworkplace.cap_line"), "the cap line");

			// the server limit: another village wanting the same kind
			otherEntity.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Wonders.capRefusal(level, other, FOLLY).isEmpty(), "no limit by default: " + Wonders.capRefusal(level, other, FOLLY));
			Wonders.SERVER_LIMIT = 1;
			helper.assertTrue(key(Wonders.capRefusal(level, other, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.server_limit"),
				"wonderServerLimit 1 let a second village build it");
			helper.assertTrue(Wonders.capRefusal(level, other, FOLLY_TWO).isEmpty(), "the limit is per kind");
			Wonders.SERVER_LIMIT = 2;
			helper.assertTrue(Wonders.capRefusal(level, other, FOLLY).isEmpty(), "a limit of 2 refused the second village");

			// disabledWonders, and the switch
			Wonders.DISABLED = Set.of(FOLLY_TWO);
			helper.assertTrue(key(Wonders.capRefusal(level, hall, FOLLY_TWO).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.unknown"), "a disabled Wonder");
			helper.assertTrue(Wonders.all().stream().noneMatch(w -> w.id().equals(FOLLY_TWO)), "a disabled Wonder listed");
			helper.assertTrue(Wonders.get(FOLLY).isPresent(), "the others went too");
			Wonders.DISABLED = disabled;
			boolean on = Wonders.ENABLED;
			Wonders.ENABLED = false;
			try {
				helper.assertTrue(key(Wonders.capRefusal(level, other, FOLLY).orElse(Component.empty())).equals("wonder.aliveworkplace.refused.off")
					&& Wonders.all().isEmpty(), "Wonders off");
			} finally {
				Wonders.ENABLED = on;
			}
		} finally {
			Wonders.SERVER_LIMIT = limit;
			Wonders.DISABLED = disabled;
			Wonders.setForTest(before);
			made.forEach(manager::remove);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the command, the config, the save

	/** {@code /workplace wonders}: none yet, then the folly with its village, owner and stage, then finished; "off" while off. */
	//$ gametest_batch SMALL '"wonderCommand"'
	@GameTest(template = SMALL, batch = "wonderCommand")
	public void theWondersCommandListsThem(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		WonderManager manager = WonderManager.get(level);
		List<WonderManager.Located> earlier = WonderManager.everywhere(level.getServer());
		WonderSite site = manager.begin(FOLLY, hall, UUID.randomUUID(), "Jesse", 2);
		try {
			manager.setStage(site.id(), 2);
			List<Component> said = run(helper, "workplace wonders");
			String text = said.stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);
			helper.assertTrue(said.size() == earlier.size() + 2 && text.contains("Jesse") && text.contains(VillageHalls.name(level, hall).getString())
				&& said.stream().anyMatch(c -> key(c).equals("command.aliveworkplace.wonders.line")), "the list: " + text);
			Component line = said.get(said.size() - 1);
			Object[] args = ((TranslatableContents) line.getContents()).getArgs();
			helper.assertTrue(args.length == 4 && args[3] instanceof Component stage && key(stage).equals("command.aliveworkplace.wonders.stage")
				&& ((TranslatableContents) stage.getContents()).getArgs()[0].equals(2), "the stage: " + line.getString());
			manager.finish(site.id(), 7);
			Component done = run(helper, "workplace wonders").get(said.size() - 1);
			helper.assertTrue(done.getString().contains("7") && ((TranslatableContents) done.getContents()).getArgs()[3] instanceof Component f
				&& key(f).equals("command.aliveworkplace.wonders.finished"), "finished: " + done.getString());
			boolean on = Wonders.ENABLED;
			Wonders.ENABLED = false;
			try {
				List<Component> off = run(helper, "workplace wonders");
				helper.assertTrue(off.size() == 1 && key(off.get(0)).equals("command.aliveworkplace.wonders.off"), "while off: " + off);
			} finally {
				Wonders.ENABLED = on;
			}
		} finally {
			manager.remove(site.id());
		}
		helper.succeed();
	}

	private static List<Component> run(GameTestHelper helper, String command) {
		List<Component> said = new ArrayList<>();
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message);
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
		CommandSourceStack source = new CommandSourceStack(out, Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))), Vec2.ZERO, helper.getLevel(), 4,
			"test", Component.literal("test"), helper.getLevel().getServer(), null);
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
		return said;
	}

	/**
	 * The config: the switches apply (behind M35's gate), {@code wonderCaps} is clamped and a short list filled,
	 * {@code disabledWonders} takes bare names as ours, {@code wonderServerLimit} is clamped; with the gate closed every
	 * switch is off and hidden.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void theWonderSwitches(GameTestHelper helper) {
		try {
			WorkplaceConfig config = WorkplaceConfig.parse("{\"wonderCaps\": [1, 9], \"wonderServerLimit\": 99, \"disabledWonders\": [\"great_cathedral\", \"pack:arch\", \"\"],"
				+ " \"wonders\": true, \"wonderLegends\": false}");
			helper.assertTrue(config.wonderCaps.equals(List.of(1, 8, 1, 2)) && config.wonderServerLimit == 64, "clamped: " + config.wonderCaps + ", " + config.wonderServerLimit);
			config.apply();
			helper.assertTrue(Wonders.ENABLED && !Wonders.LEGENDS && Wonders.CAPS[1] == 8 && Wonders.SERVER_LIMIT == 64
				&& Wonders.DISABLED.equals(Set.of(AliveWorkplace.id("great_cathedral"), ResourceLocation.parse("pack:arch"))), "applied: " + Wonders.DISABLED);
			WorkplaceConfig fresh = new WorkplaceConfig();
			helper.assertTrue(fresh.wonders && fresh.wonderLegends && fresh.wonderCaps.equals(List.of(0, 0, 1, 2)) && fresh.wonderServerLimit == 0
				&& fresh.disabledWonders.isEmpty(), "the defaults in GameTests");
			helper.assertTrue(!WorkplaceConfig.optionNames().contains("wonderCaps") && !WorkplaceConfig.optionNames().contains("disabledWonders")
				&& WorkplaceConfig.optionNames().contains("wonders") && WorkplaceConfig.optionNames().contains("wonderServerLimit"), "on the screen");
		} finally {
			new WorkplaceConfig().apply();
		}
		boolean[] seen = new boolean[3];
		ExpansionGateGameTests.asPlayer(() -> {
			WorkplaceConfig.parse("{\"wonders\": true, \"wonderLegends\": true}").apply();
			seen[0] = Wonders.ENABLED || Wonders.LEGENDS;
			seen[1] = new WorkplaceConfig().wonders;
			seen[2] = WorkplaceConfig.optionNames().contains("wonders") || WorkplaceConfig.optionNames().contains("wonderServerLimit");
		});
		helper.assertTrue(!Expansions.M35, "M35 marked complete: move its switches out of this test");
		helper.assertTrue(!seen[0] && !seen[1] && !seen[2], "with the gate closed: on " + seen[0] + ", default " + seen[1] + ", on the screen " + seen[2]);
		helper.assertTrue(Wonders.ENABLED && Wonders.LEGENDS, "the gate reopened");
		helper.succeed();
	}

	/** A site survives a save and load; a world saved before M35 (no sites) loads empty. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wonderSitesSaveAndLoad(GameTestHelper helper) {
		var registries = helper.getLevel().registryAccess();
		helper.assertTrue(WonderManager.load(new CompoundTag(), registries).sites().isEmpty(), "an old world has Wonders");
		WonderManager manager = new WonderManager();
		WonderSite site = manager.begin(FOLLY, new BlockPos(10, 64, -20), UUID.randomUUID(), "Jesse", 5);
		manager.setStage(site.id(), 3);
		manager.finish(site.id(), 12);
		WonderManager back = WonderManager.load(manager.save(new CompoundTag(), registries), registries);
		WonderSite read = back.site(site.id()).orElse(null);
		helper.assertTrue(read != null && read.wonder().equals(FOLLY) && read.hall().equals(new BlockPos(10, 64, -20)) && read.stage() == 3
			&& read.begun() == 5 && read.finished().equals(Optional.of(12L)) && read.ownerName().equals("Jesse") && read.owner().equals(site.owner()), "read back: " + read);
		CompoundTag bare = new CompoundTag();
		CompoundTag one = new CompoundTag();
		one.putIntArray("id", net.minecraft.core.UUIDUtil.uuidToIntArray(UUID.randomUUID()));
		one.putString("wonder", FOLLY.toString());
		one.putIntArray("hall", new int[] {0, 0, 0});
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		list.add(one);
		bare.put("sites", list);
		WonderSite minimal = WonderManager.load(bare, registries).sites().get(0);
		helper.assertTrue(minimal.stage() == 1 && minimal.goingUp() && minimal.owner().isEmpty() && minimal.ownerName().isEmpty(), "the defaults: " + minimal);
		helper.succeed();
	}
}
