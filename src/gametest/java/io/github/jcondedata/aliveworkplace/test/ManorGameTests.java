package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.HomeGrades;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.HashMap;
import java.util.Map;
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
 * The Noble's Manor (ROADMAP 34.16): a grade III home in every tier and style; Manor II's walled garden counts 3 beauty
 * for the people who live there (enough for a Noble's beauty need on its own) and Manor I's forecourt doesn't; builders
 * sell it once the village has a household of Burghers (or higher), never with classes off; the Noble's Ball gathers in
 * Manor III's ballroom, in a Manor I or II's hall when there is no ballroom, at the Village Hall with no Manor. (The
 * builder building each tier is in {@code BuilderGameTests}.)
 */
public class ManorGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(19, 2, 19);
	/** Where the Manor's origin is recorded in these tests. */
	private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);
	/** A bed in the Manor's west bedroom (houses.py: the foot at x 3, y 6, z 10), from the origin. */
	private static final BlockPos BED = ORIGIN.offset(3, 6, 10);

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin, Rotation rotation) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), rotation, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** A hall at {@link #HALL}, classes on with our shipped class files, everything put back afterwards. */
	private static VillageHallBlockEntity village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
			SocialClasses.forget();
			Festivals.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		return hall;
	}

	/**
	 * The Manor is a grade III home in every tier and style; a bed in Manor I counts no garden beauty, in Manor II and III
	 * the garden counts 3, so a Noble's "beauty 3" need holds there with no decoration about; the shipped file names it.
	 */
	//$ gametest_batch AREA '"manorGarden"'
	@GameTest(template = AREA, batch = "manorGarden")
	public void theManorIsGradeIIIAndItsGardenCountsThreeBeauty(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = placement(helper, ORIGIN, Rotation.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Villager lord = villager(helper, new BlockPos(5, 2, 5), "Aldous");
		Beds.home(lord, helper.absolutePos(BED));
		helper.assertTrue(HomeGrades.given(StarterBlueprints.MANOR.id()) == 3, "the shipped homes/manor.json gives the Manor grade III");
		Map<ResourceLocation, Integer> beauty = new java.util.LinkedHashMap<>();
		beauty.put(StarterBlueprints.MANOR.id(), 0);
		beauty.put(StarterBlueprints.MANOR_2.id(), 3);
		beauty.put(StarterBlueprints.MANOR_3.id(), 3);
		beauty.put(BlueprintStyles.styled(StarterBlueprints.MANOR.id(), "sandstone"), 0);
		beauty.put(BlueprintStyles.styled(StarterBlueprints.MANOR_2.id(), "cherry"), 3);
		beauty.put(BlueprintStyles.styled(StarterBlueprints.MANOR_3.id(), "grand"), 3);
		ClassNeeds.Beauty need = new ClassNeeds.Beauty(3);
		for (Map.Entry<ResourceLocation, Integer> e : beauty.entrySet()) {
			sites.recordFinished(e.getKey(), placement, UUID.randomUUID());
			int grade = Homes.of(level, lord).map(Homes.Home::tier).orElse(0);
			helper.assertTrue(grade == 3, "a bed in " + e.getKey() + " should be in a grade III home, is " + Homes.of(level, lord));
			int points = ClassNeeds.beauty(level, lord);
			helper.assertTrue(points == e.getValue(), "the beauty at home in " + e.getKey() + ": " + points + " (expected " + e.getValue() + ")");
			ClassNeeds.Village village = new ClassNeeds.Village(level, helper.absolutePos(HALL), 0);
			helper.assertTrue(ClassNeeds.holds(need, lord, village) == (e.getValue() >= 3), "a Noble's beauty need in " + e.getKey());
		}
		// Someone sleeping outside it gets nothing from its garden
		Villager neighbour = villager(helper, new BlockPos(7, 2, 5), "Wat");
		Beds.home(neighbour, helper.absolutePos(new BlockPos(30, 2, 30)));
		helper.assertTrue(ClassNeeds.beauty(level, neighbour) == 0, "a neighbour outside the Manor counts none of its garden");
		helper.succeed();
	}

	/** A garden in a data file: from its tier on, that much beauty; a broken garden skips the file; the shipped files come back. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"manorGardenFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "manorGardenFiles")
	public void aHomeFileCanGiveItsGardenBeauty(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> HomeGrades.reload(level.getServer().getResourceManager()));
		helper.assertTrue(HomeGrades.gardenBeauty(StarterBlueprints.MANOR_2.id()) == 3 && HomeGrades.gardenBeauty(StarterBlueprints.MANOR.id()) == 0,
			"shipped: Manor II's garden counts 3, Manor I has none");
		Map<ResourceLocation, JsonElement> files = new HashMap<>();
		files.put(ours("homes/stone_house.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:stone_house\", \"grade\": 1, \"garden\": {\"tier\": 3, \"beauty\": 2}}"));
		files.put(ours("homes/terrace.json"), JsonParser.parseString("{\"blueprint\": \"aliveworkplace:terrace\", \"grade\": 2, \"garden\": {\"beauty\": -1}}"));
		HomeGrades.load(files);
		helper.assertTrue(HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE_2.id()) == 0 && HomeGrades.gardenBeauty(StarterBlueprints.STONE_HOUSE_3.id()) == 2,
			"the Stone House's garden from III on");
		helper.assertTrue(HomeGrades.given(StarterBlueprints.TERRACE.id()) == 0 && HomeGrades.gardenBeauty(StarterBlueprints.TERRACE_2.id()) == 0,
			"a garden below 0 skips the whole file");
		helper.assertTrue(HomeGrades.gardenBeauty(StarterBlueprints.MANOR_2.id()) == 0, "the files loaded replace the shipped ones");
		helper.succeed();
	}

	/**
	 * A builder doesn't sell the Manor in a village of Artisans; does once it has a Burgher household (a Noble counts too),
	 * at its price and only once; nor with village classes off.
	 */
	//$ gametest_batch AREA '"manorTrades"'
	@GameTest(template = AREA, batch = "manorTrades")
	public void buildersSellTheManorOnceTheVillageHasBurghers(GameTestHelper helper) {
		village(helper);
		Villager builder = villager(helper, new BlockPos(10, 2, 10), "Builder");
		builder.setVillagerData(new VillagerData(builder.getVillagerData().getType(), ModVillagers.BUILDER, 2));
		builder.getOffers().clear();
		Villager artisan = villager(helper, new BlockPos(12, 2, 10), "Ann");
		SocialClasses.seed(artisan, SocialClasses.get(ours("artisan")));
		ModTrades.builderHomes(builder);
		helper.assertTrue(UpgradeOffers.sells(builder, StarterBlueprints.TOWNHOUSE.id()) && !UpgradeOffers.sells(builder, StarterBlueprints.MANOR.id()),
			"Artisans bring the Townhouse, not the Manor");
		Villager burgher = villager(helper, new BlockPos(14, 2, 10), "Bertram");
		SocialClasses.seed(burgher, SocialClasses.get(ours("burgher")));
		SocialClasses.ENABLED = false;
		ModTrades.builderHomes(builder);
		helper.assertTrue(!UpgradeOffers.sells(builder, StarterBlueprints.MANOR.id()), "no Manor with village classes off");
		SocialClasses.ENABLED = true;
		ModTrades.builderHomes(builder);
		ModTrades.builderHomes(builder);
		helper.assertTrue(UpgradeOffers.sells(builder, StarterBlueprints.MANOR.id()), "the Manor once there are Burghers");
		long manors = builder.getOffers().stream().filter(o -> BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(StarterBlueprints.MANOR.id())).orElse(false)).count();
		helper.assertTrue(manors == 1, "sold once: " + manors);
		helper.assertTrue(builder.getOffers().stream().anyMatch(o -> BlueprintItem.data(o.getResult())
			.map(d -> d.structure().equals(StarterBlueprints.MANOR.id())).orElse(false) && o.getBaseCostA().getCount() == ModTrades.MANOR_PRICE),
			"the Manor costs " + ModTrades.MANOR_PRICE);
		// A Noble household alone (no Burgher) is enough for another builder
		burgher.discard();
		artisan.discard();
		Villager noble = villager(helper, new BlockPos(16, 2, 10), "Lady Ysolde");
		SocialClasses.seed(noble, SocialClasses.get(ours("noble")));
		Villager other = villager(helper, new BlockPos(10, 2, 12), "Builder Two");
		other.setVillagerData(new VillagerData(other.getVillagerData().getType(), ModVillagers.BUILDER, 1));
		other.getOffers().clear();
		ModTrades.builderHomes(other);
		helper.assertTrue(UpgradeOffers.sells(other, StarterBlueprints.MANOR.id()), "a Noble household counts as Burghers or higher");
		// The Steward builds one for a household that wants grade III
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().getPath().equals("homes_class_manor")).findFirst().orElse(null);
		helper.assertTrue(rule != null && rule.effect().blueprint().equals(java.util.Optional.of(StarterBlueprints.MANOR.id()))
			&& rule.effect().zone().equals(java.util.Optional.of("homes")), "homes_class_manor builds the Manor in Homes: " + rule);
		helper.succeed();
	}

	/**
	 * The ball's venue: the Village Hall with no Manor; Manor I's and II's hall (their middle) with no ballroom; Manor III's
	 * ballroom, in any style and turned; and only on a ball day.
	 */
	//$ gametest_batch AREA '"manorBallroom"'
	@GameTest(template = AREA, batch = "manorBallroom")
	public void theBallGathersInTheBallroomWhenThereIsOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = village(helper);
		BlockPos pos = helper.absolutePos(HALL);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement placement = placement(helper, ORIGIN, Rotation.NONE);
		BlueprintData.Placement turned = placement(helper, new BlockPos(26, 2, 2), Rotation.CLOCKWISE_90);
		Leftovers.after(helper, () -> {
			sites.forgetFinished(placement);
			sites.forgetFinished(turned);
		});
		long today = Chronicle.day(level);
		hall.setFestivalDay(today);
		hall.setBallDay(today);
		helper.assertTrue(Festivals.square(level, pos).equals(pos), "no Manor: the ball is at the hall");
		BlockPos origin = helper.absolutePos(ORIGIN);
		sites.recordFinished(StarterBlueprints.MANOR.id(), placement, UUID.randomUUID());
		BlockPos middle1 = origin.offset(StarterBlueprints.MANOR.size().getX() / 2, 1, StarterBlueprints.MANOR.size().getZ() / 2);
		helper.assertTrue(Festivals.square(level, pos).equals(middle1), "Manor I (no ballroom): its hall, " + middle1 + ", not " + Festivals.square(level, pos));
		sites.recordFinished(StarterBlueprints.MANOR_2.id(), placement, UUID.randomUUID());
		BlockPos middle2 = origin.offset(StarterBlueprints.MANOR_2.size().getX() / 2, 1, StarterBlueprints.MANOR_2.size().getZ() / 2);
		helper.assertTrue(Festivals.square(level, pos).equals(middle2), "Manor II (no ballroom): its middle, not " + Festivals.square(level, pos));
		sites.recordFinished(StarterBlueprints.MANOR_3.id(), placement, UUID.randomUUID());
		BlockPos ballroom = origin.offset(20, 1, 11);
		helper.assertTrue(Festivals.square(level, pos).equals(ballroom), "Manor III: the ballroom's middle " + ballroom + ", not " + Festivals.square(level, pos));
		sites.recordFinished(BlueprintStyles.styled(StarterBlueprints.MANOR_3.id(), "cherry"), placement, UUID.randomUUID());
		helper.assertTrue(Festivals.square(level, pos).equals(ballroom), "a cherry Manor III: the ballroom too");
		// The ballroom is inside the wing (x 17-23, z 8-14 of Manor III), whatever way it's turned
		sites.forgetFinished(placement);
		sites.recordFinished(StarterBlueprints.MANOR.id(), placement, UUID.randomUUID());
		sites.recordFinished(StarterBlueprints.MANOR_3.id(), turned, UUID.randomUUID());
		BlockPos turnedBallroom = helper.absolutePos(new BlockPos(26, 2, 2)).offset(-11, 1, 20);
		helper.assertTrue(Festivals.square(level, pos).equals(turnedBallroom),
			"a Manor I and a turned Manor III: the ballroom wins, at " + turnedBallroom + ", not " + Festivals.square(level, pos));
		helper.assertTrue(BlueprintLibrary.get(level, StarterBlueprints.MANOR_3.id()).isPresent(), "Manor III is in the library");
		// Not a ball day: the festival is at the square (the hall here), not the ballroom
		hall.setBallDay(today - 2);
		helper.assertTrue(Festivals.square(level, pos).equals(pos), "an ordinary festival isn't held in the ballroom");
		helper.succeed();
	}

	/**
	 * ROADMAP 34.15b (the owner, 2026-10-13): the Manor is sold by villagers only, like the Artisan's House and the
	 * Townhouse (34.15a). No tier, plain or styled, is in the Blueprint Table's list or handed out by it, and a refused
	 * request keeps the Blank Blueprint; every tier still loads by id. A Manor bought from a builder in a village with
	 * Burghers sets out on the ground; a finished Manor brings its upgrade's trade; the Steward's rule still finds it.
	 */
	//$ gametest_batch AREA '"manorSoldOnly"'
	@GameTest(template = AREA, batch = "manorSoldOnly")
	public void theManorIsSoldNotInTheTable(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		var server = level.getServer();
		java.util.List<ResourceLocation> table = io.github.jcondedata.aliveworkplace.table.TableServer.listing(server).stream()
			.map(io.github.jcondedata.aliveworkplace.table.TablePayloads.Entry::id).toList();
		java.util.List<ResourceLocation> everything = BlueprintLibrary.list(server, true);
		for (StarterBlueprints.Entry e : java.util.List.of(StarterBlueprints.MANOR, StarterBlueprints.MANOR_2, StarterBlueprints.MANOR_3)) {
			helper.assertTrue(StarterBlueprints.SOLD_ONLY.contains(e), e.id() + " isn't sold-only");
			helper.assertFalse(table.contains(e.id()), e.id() + " is in the Blueprint Table");
			helper.assertFalse(table.contains(BlueprintStyles.styled(e.id(), "grand")), e.id() + " in the grand style is in the Blueprint Table");
			helper.assertTrue(everything.contains(e.id()), e.id() + " should still load for operators");
			helper.assertTrue(BlueprintLibrary.get(server, e.id()).isPresent(), e.id() + " no longer loads");
		}
		helper.assertTrue(table.contains(StarterBlueprints.STONE_HOUSE.id()), "the Stone House stays in the Table");

		// Asked for anyway (a request the screen never sends), the Table refuses and keeps the Blank Blueprint.
		BlockPos tablePos = new BlockPos(3, 2, 3);
		helper.setBlock(tablePos, ModBlocks.BLUEPRINT_TABLE);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			BlockPos at = helper.absolutePos(tablePos);
			player.moveTo(new net.minecraft.world.phys.Vec3(at.getX() + 1.5, at.getY(), at.getZ() + 0.5));
			player.getInventory().add(new net.minecraft.world.item.ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT, 2));
			helper.assertFalse(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, StarterBlueprints.MANOR.id()), "the Table handed out a Manor");
			helper.assertFalse(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, BlueprintStyles.styled(StarterBlueprints.MANOR_3.id(), "cherry")),
				"the Table handed out a cherry Manor III");
			helper.assertTrue(player.getInventory().countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT) == 2, "a Blank Blueprint was used up");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.table.TableServer.take(player, at, StarterBlueprints.STONE_HOUSE.id()), "the Stone House still comes from the Table");

			// Bought from a builder once the village has Burghers, the Manor sets out on the ground like any blueprint.
			Villager burgher = villager(helper, new BlockPos(14, 2, 10), "Bertram");
			SocialClasses.seed(burgher, SocialClasses.get(ours("burgher")));
			Villager builder = villager(helper, new BlockPos(10, 2, 10), "Builder");
			builder.setVillagerData(new VillagerData(builder.getVillagerData().getType(), ModVillagers.BUILDER, 1));
			builder.getOffers().clear();
			ModTrades.builderHomes(builder);
			net.minecraft.world.item.ItemStack bought = builder.getOffers().stream().map(o -> o.getResult().copy())
				.filter(r -> BlueprintItem.data(r).map(d -> d.structure().equals(StarterBlueprints.MANOR.id())).orElse(false))
				.findFirst().orElse(null);
			helper.assertTrue(bought != null, "the builder sells no Manor: " + builder.getOffers().size());
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bought);
			BlockPos ground = helper.absolutePos(new BlockPos(8, 1, 4));
			bought.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground), net.minecraft.core.Direction.UP, ground, false)));
			helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement).isPresent(),
				"the bought Manor didn't set out: " + BlueprintItem.data(player.getMainHandItem()));

			// The builder who finishes a Manor sells Manor II from then on, though the Table doesn't list it.
			helper.assertTrue(UpgradeOffers.offer(level, builder, StarterBlueprints.MANOR.id()).equals(java.util.Optional.of(StarterBlueprints.MANOR_2.id())),
				"no Manor II trade after a finished Manor");
			helper.assertTrue(UpgradeOffers.sells(builder, StarterBlueprints.MANOR_2.id()), "the builder doesn't sell Manor II");
		} finally {
			server.getPlayerList().remove(player);
		}

		// The Steward's rule still builds it: its blueprint loads by id.
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().getPath().equals("homes_class_manor")).findFirst().orElse(null);
		helper.assertTrue(rule != null && rule.effect().blueprint().flatMap(id -> BlueprintLibrary.get(server, id)).isPresent(),
			"homes_class_manor's blueprint doesn't load: " + rule);
		helper.succeed();
	}
}
