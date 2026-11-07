package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-01, lane-c-1101-0052) of the village fixes B714, B730, B732, B731 and B737, written from each bug's expected
 * behaviour: the boundaries the fixes' own tests don't reach (an edge column shared or one column clear, a protected
 * village's radius exactly met or missed by one), the legitimate cases that must still work (the owner's own tree farm,
 * a house beside a waiting Wonder), what a refused player keeps, and every sentence the player reads.
 */
public class QaVillageFixes1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// --- B714: a Wonder begun and laid out, waiting for its goods ------------------------------------------------------

	/** The footprint of every part of {@code site} as laid out (the ground stage I will take, and the stages above it). */
	private static BoundingBox footprint(ServerLevel level, WonderSite site) {
		BoundingBox out = null;
		for (WonderUpkeep.Part part : WonderUpkeep.parts(level, site)) {
			Vec3i size = BlueprintLibrary.get(level, part.plain()).orElseThrow().size();
			BoundingBox b = BlueprintOutline.bounds(part.placement(), size);
			out = out == null ? b : BoundingBox.encapsulatingBoxes(List.of(out, b)).orElseThrow();
		}
		return out;
	}

	/** A Wonder of {@code hall} begun and laid out at {@code origin} (its Foundation Stone set), stage I waiting for its goods. */
	private static WonderSite waitingWonder(GameTestHelper helper, BlockPos hall, BlockPos origin, ServerPlayer owner) {
		ServerLevel level = helper.getLevel();
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(FOLLY, hall, owner == null ? null : owner.getUUID(), owner == null ? "" : "Owner", 1);
		Leftovers.after(helper, () -> manager.remove(begun.id()));
		manager.place(begun.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		WonderSite now = manager.site(begun.id()).orElseThrow();
		helper.assertTrue(now.goingUp() && !now.started() && now.parts().isEmpty(), "set-up: a Wonder waiting for its goods, no build sites");
		return now;
	}

	/** A Town at {@code hallAt} owned by the client's player, every requirement of the folly met (Legends stand in). */
	private static BlockPos town(GameTestHelper helper, QaStageFiveGameTests.Client c, BlockPos hallAt) {
		Leftovers.clear(helper);
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = helper.getBlockEntity(hallAt);
		entity.setOwner(c.player().getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setTreasury(0);
		for (BlockPos at : List.of(new BlockPos(3, 2, 3), new BlockPos(13, 2, 3), new BlockPos(8, 2, 13))) {
			Villager v = helper.spawn(EntityType.VILLAGER, at);
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(at.getX() == 3 ? ModVillagers.BUILDER : VillagerProfession.FARMER).setLevel(at.getX() == 3 ? 5 : 1));
		}
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		return hall;
	}

	/** The player places the plan in hand with its front's middle at {@code anchor} (clicking the block under it), facing north. */
	private static Optional<BlueprintData.Placement> placePlan(ServerPlayer player, BlockPos anchor) {
		player.setShiftKeyDown(false);
		player.setYRot(180);
		BlockPos ground = anchor.below();
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	/**
	 * B714, edge from both sides: a Wonder Plan whose footprint shares just the last column with a Wonder waiting for its goods
	 * is refused, saying "It would stand over another Wonder"; the same plan moved one column further goes down.
	 */
	//$ gametest_ticks_batch SMALL '60' '"qaVf1101PlanEdge"'
	@GameTest(template = SMALL, timeoutTicks = 60, batch = "qaVf1101PlanEdge")
	public void qaAPlanSharingOneColumnWithAWaitingWonderIsRefusedAndOneColumnClearGoesDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfPlan"));
		BlockPos hall = town(helper, c, new BlockPos(8, 2, 8));
		Wonder folly = Wonders.loaded().get(FOLLY);
		WonderSite waiting = waitingWonder(helper, hall, hall.offset(-24, 0, -24), c.player());
		BoundingBox wonder = footprint(level, waiting);

		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		// where the plan's footprint lies for an anchor: its west column put on the Wonder's east column (shared), or one past
		BlockPos probe = new BlockPos(wonder.maxX(), hall.getY(), wonder.minZ() + 2);
		BoundingBox atProbe = BlueprintOutline.bounds(BlueprintItem.placementAt(level.dimension().location(), size, probe, Rotation.NONE), size);
		BlockPos shared = probe.offset(wonder.maxX() - atProbe.minX(), 0, 0);
		BlockPos clear = shared.east();
		BoundingBox sharedBox = BlueprintOutline.bounds(BlueprintItem.placementAt(level.dimension().location(), size, shared, Rotation.NONE), size);
		helper.assertTrue(sharedBox.minX() == wonder.maxX() && sharedBox.minZ() <= wonder.maxZ() && sharedBox.maxZ() >= wonder.minZ(),
			"set-up: the plan's footprint " + sharedBox + " doesn't share just the Wonder's east column " + wonder);

		ItemStack held = plan.copy();
		c.player().setItemInHand(InteractionHand.MAIN_HAND, held);
		int before = c.seen().size();
		Optional<BlueprintData.Placement> down = placePlan(c.player(), shared);
		List<String> said = c.seen().subList(before, c.seen().size()).stream().map(Component::getString).toList();
		helper.assertTrue(down.isEmpty(), "a plan sharing the waiting Wonder's edge column went down at " + down.map(p -> p.origin().toShortString()));
		helper.assertTrue(said.contains("It would stand over another Wonder"), "the player wasn't told why: " + said);

		c.player().setItemInHand(InteractionHand.MAIN_HAND, plan.copy());
		before = c.seen().size();
		down = placePlan(c.player(), clear);
		List<String> saidClear = c.seen().subList(before, c.seen().size()).stream().map(Component::getString).toList();
		helper.assertTrue(down.isPresent(), "a plan one column clear of the waiting Wonder was refused: " + saidClear);
		helper.assertTrue(saidClear.stream().noneMatch(s -> s.contains("another Wonder")), "the clear plan was said to stand over a Wonder: " + saidClear);
		helper.succeed();
	}

	/** A builder at a Blueprint Table at (26, 2, 4) and a client; returns the builder. Protection is off for this test. */
	private static Villager builder(GameTestHelper helper) {
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		BlockPos benchAt = new BlockPos(26, 2, 4);
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(benchAt));
		return builder;
	}

	/** The client hands {@code builder} the test hut placed with its origin at {@code origin}; the site he took, if any. */
	private static BuildSite handHut(GameTestHelper helper, QaStageFiveGameTests.Client c, Villager builder, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Vec3i size = BlueprintLibrary.get(level, HUT).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(HUT, size);
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(at)));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite taken = Builders.activeSite(level, builder);
		if (taken != null) {
			Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(taken.id()));
		}
		return taken;
	}

	/**
	 * B714, the builder's side at the edge: a house whose footprint takes in just the waiting Wonder's east column is
	 * refused ("That spot overlaps another build in progress (Test Folly)."); the same house one column clear is taken.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101HouseEdge"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101HouseEdge")
	public void qaAHouseOnAWaitingWondersEdgeIsRefusedAndOneColumnClearIsTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(24, 2, 24), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(24, 2, 24));
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderSite waiting = waitingWonder(helper, hall, helper.absolutePos(new BlockPos(2, 2, 2)), null);
		BoundingBox wonder = footprint(level, waiting);
		Villager builder = builder(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfHut"));

		BlockPos edge = new BlockPos(wonder.maxX(), wonder.minY(), wonder.minZ() + 1);
		BuildSite onEdge = handHut(helper, c, builder, edge);
		List<String> said = QaVillageGuards1031GameTests.said(c);
		helper.assertTrue(onEdge == null, "the builder took a house sharing the waiting Wonder's east column: " + said);
		helper.assertTrue(said.contains("That spot overlaps another build in progress (Test Folly)."), "the player wasn't told why: " + said);

		BuildSite clear = handHut(helper, c, builder, edge.east());
		helper.assertTrue(clear != null, "a house one column clear of the waiting Wonder was refused: " + QaVillageGuards1031GameTests.said(c));
		helper.succeed();
	}

	/**
	 * B714's limit: only a Wonder with a placement counts. A Wonder begun but not yet laid out (no Foundation Stone set) has
	 * no ground yet, so a house where it might one day stand is still taken.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101HouseUnplaced"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101HouseUnplaced")
	public void qaAWonderNotYetLaidOutDoesNotStopAHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(24, 2, 24), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(24, 2, 24));
		Leftovers.wonderGround(helper, hall);
		WonderManager manager = WonderManager.get(level);
		WonderSite begun = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(begun.id()));
		helper.assertTrue(manager.site(begun.id()).map(s -> s.placement().isEmpty()).orElse(false), "set-up: the Wonder isn't laid out");
		Villager builder = builder(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfUnp"));
		BuildSite taken = handHut(helper, c, builder, helper.absolutePos(new BlockPos(4, 2, 4)));
		helper.assertTrue(taken != null, "a house was refused for a Wonder that isn't laid out yet: " + QaVillageGuards1031GameTests.said(c));
		helper.succeed();
	}

	// --- B730, B732: a bed no camp placed stays a home --------------------------------------------------------------

	/**
	 * A cabin's brown and green beds with a campfire, a hay bale and the village builder's Blueprint Table all right beside
	 * them (the closest the look can get), the builder at work, two residents; the hall's round, then the census. Runs
	 * {@code check} with the bed heads and residents after the round.
	 */
	static void cabinRound(GameTestHelper helper, java.util.function.BiConsumer<List<BlockPos>, List<Villager>> check) {
		ServerLevel level = helper.getLevel();
		BlockPos brown = QaVillageGuards1031GameTests.bed(helper, new BlockPos(6, 2, 10), Blocks.BROWN_BED);
		BlockPos green = QaVillageGuards1031GameTests.bed(helper, new BlockPos(8, 2, 10), Blocks.GREEN_BED);
		helper.setBlock(new BlockPos(7, 2, 12), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(5, 2, 12), Blocks.HAY_BLOCK);
		BlockPos table = new BlockPos(9, 2, 12);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 13));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(table));
		List<Villager> residents = List.of(helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8)), helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)));
		residents.forEach(v -> v.setNoAi(true));
		List<BlockPos> beds = List.of(brown, green);
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(CampBeds.claim(level, residents.get(i), beds.get(i)), "set-up: resident " + i + " couldn't take their bed");
			}
			helper.setBlock(new BlockPos(15, 2, 20), ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> check.accept(beds, residents));
		});
	}

	/**
	 * B730/B732 at the look's closest: in this world (its wagon-bed list begun with it), a cabin's brown and green beds with
	 * the campfire, the hay bale and the builder's Blueprint Table all within 2 blocks stay homes and both count in Beds.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaVf1101CabinTight"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaVf1101CabinTight")
	public void qaACabinWithFireHayAndTheBuildersTableRightBesideItStaysAHome(GameTestHelper helper) {
		QaVillageGuards1031GameTests.campArea(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CampBeds.Data.get(level).complete(), "set-up: this world's wagon-bed list isn't the complete one a new world starts");
		cabinRound(helper, (beds, residents) -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(!CampBeds.isWagonBed(level, beds.get(i)), "cabin bed " + i + " (fire, hay and the builder's table within 2) is a wagon bed");
				helper.assertTrue(beds.get(i).equals(QaVillageGuards1031GameTests.home(residents.get(i))), "resident " + i + " lost their home");
			}
			VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(new BlockPos(15, 2, 20)));
			helper.assertTrue(census.beds() == 2 && census.wagonBeds() == 0, "beds " + census.beds() + ", wagon beds " + census.wagonBeds());
			helper.succeed();
		});
	}

	// --- B731, B737: a Field Marker handed to a lumberjack, orchard keeper or berry breeder --------------------------

	/** Protected hall at (11, 2, 11), radius 16 (ProtectionSpecGameTests). A worker at {@code block} (22, 2, 26), outside it. */
	private static Villager worker(GameTestHelper helper, Block block, ResourceKey<PoiType> poi, VillagerProfession job) {
		BlockPos at = new BlockPos(22, 2, 26);
		helper.setBlock(at, block);
		Villager worker = helper.spawn(EntityType.VILLAGER, at.west());
		worker.setNoAi(true);
		Jobs.employ(helper.getLevel(), worker, helper.absolutePos(at), poi, job);
		return worker;
	}

	/** The client hands {@code worker} a Field Marker over columns x {@code x0}..{@code x1}, z 2..20; what he read. */
	private static List<String> hand(GameTestHelper helper, QaStageFiveGameTests.Client c, Villager worker, int x0, int x1) {
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(helper.absolutePos(new BlockPos(x0, 1, 2))),
			Optional.of(helper.absolutePos(new BlockPos(x1, 1, 20)))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		int before = c.seen().size();
		QaStageSixGameTests.clickVillager(c.player(), worker, c.seen());
		return c.seen().subList(before, c.seen().size()).stream().map(Component::getString).toList();
	}

	private static final String DENIED = "Testholm is Owner's village: only they and their friends can change things here";

	/**
	 * B731 at the radius, both sides: a stranger's tree farm whose nearest column is exactly 16 from the protected hall
	 * (x 27..30) is refused, saying whose village it is; one column further (x 28..31, 17 off) it is taken and the
	 * lumberjack at his Chopping Block says so, naming the Chopping Block (B737).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101TreeRadius"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101TreeRadius")
	public void qaAStrangersTreeFarmAtTheRadiusIsRefusedAndOneColumnOutIsTaken(GameTestHelper helper) {
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		hall.setCustomName(Component.literal("Testholm"));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfTr"));
		Villager lumberjack = worker(helper, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		List<String> atRadius = hand(helper, c, lumberjack, 27, 30);
		helper.assertTrue(TreeFarms.farm(lumberjack) == null, "a stranger's tree farm 16 from the protected hall was taken: " + atRadius);
		helper.assertTrue(atRadius.contains(DENIED), "the stranger wasn't told whose village it is: " + atRadius);
		List<String> outside = hand(helper, c, lumberjack, 28, 31);
		helper.assertTrue(TreeFarms.farm(lumberjack) != null, "a stranger's tree farm 17 from the protected hall (outside it) was refused: " + outside);
		String started = lumberjack.getDisplayName().getString() + " will keep the 4 × 19 tree farm planted and fell the trees that grow there."
			+ " Put saplings (and an axe) in a chest within 8 blocks of their Chopping Block.";
		helper.assertTrue(outside.contains(started), "not the started line naming the Chopping Block: " + outside);
		helper.succeed();
	}

	/**
	 * B731: a stranger's orchard keeper already keeping an orchard outside the village is handed a second marker reaching
	 * in: refused, the old orchard kept, the new marker left in the stranger's hand and no old marker handed back.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101OrchardKeep"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101OrchardKeep")
	public void qaARefusedOrchardLeavesTheOldOrchardAndTheMarkerInHand(GameTestHelper helper) {
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		hall.setCustomName(Component.literal("Testholm"));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfOr"));
		Villager keeper = worker(helper, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		List<String> first = hand(helper, c, keeper, 28, 31);
		helper.assertTrue(Orchards.orchard(keeper) != null, "set-up: the orchard outside the village wasn't taken: " + first);
		helper.assertTrue(first.stream().anyMatch(s -> s.endsWith("in a chest within 8 blocks of their Fruit Basket.")), "not the Fruit Basket line: " + first);
		var old = Orchards.orchard(keeper);
		List<String> second = hand(helper, c, keeper, 25, 28);
		helper.assertTrue(second.contains(DENIED), "the stranger wasn't told whose village it is: " + second);
		helper.assertTrue(old.equals(Orchards.orchard(keeper)), "the refused marker changed the keeper's orchard: " + Orchards.orchard(keeper));
		helper.assertTrue(c.player().getMainHandItem().is(ModItems.FIELD_MARKER) && c.player().getMainHandItem().getCount() == 1,
			"the refused marker left the stranger's hand: " + c.player().getMainHandItem());
		helper.assertTrue(c.player().getInventory().countItem(ModItems.FIELD_MARKER) == 1,
			"a refused marker handed the old orchard's marker back as well: " + c.player().getInventory().countItem(ModItems.FIELD_MARKER));
		helper.succeed();
	}

	/**
	 * B731's legitimate case: the village's owner hands his own berry breeder a plot reaching into his protected village;
	 * it's taken, and her line names her composter (B737: "within 8 blocks of her Composter."). Then the orchard keeper at a
	 * composter is told of their Composter too.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101OwnerPlot"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101OwnerPlot")
	public void qaTheOwnersOwnPlotReachingInIsTakenAndNamesTheComposter(GameTestHelper helper) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfOw"));
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, c.player());
		hall.setCustomName(Component.literal("Testholm"));
		Villager breeder = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		List<String> said = hand(helper, c, breeder, 25, 28);
		helper.assertTrue(ModAttachments.BERRY_PLOT.get(breeder) != null, "the owner's own berry plot reaching into his village was refused: " + said);
		String started = breeder.getDisplayName().getString() + " will breed berries in the 4 × 19 plot."
			+ " Put berries (and Growth or Surprise Mulch) in a chest within 8 blocks of her Composter.";
		helper.assertTrue(said.contains(started), "not the berry breeder's started line: " + said);
		breeder.discard();
		helper.setBlock(new BlockPos(22, 2, 26), Blocks.AIR);
		Villager keeper = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.ORCHARD_KEEPER);
		List<String> orchard = hand(helper, c, keeper, 25, 28);
		helper.assertTrue(Orchards.orchard(keeper) != null, "the owner's own orchard reaching into his village was refused: " + orchard);
		String line = keeper.getDisplayName().getString() + " will keep the 4 × 19 orchard planted and pick what grows there."
			+ " Put sweet berries (and, with Cobblemon, apricorn seeds and berries) in a chest within 8 blocks of their Composter.";
		helper.assertTrue(orchard.contains(line), "not the orchard keeper's composter line: " + orchard);
		helper.succeed();
	}

	/** B731 with protection switched off: a stranger's tree farm reaching into the village is taken, no refusal said. */
	//$ gametest_ticks_batch HUGE '60' '"qaVf1101TreeOff"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaVf1101TreeOff")
	public void qaWithProtectionOffAStrangersTreeFarmReachingInIsTaken(GameTestHelper helper) {
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		hall.setCustomName(Component.literal("Testholm"));
		VillageProtection.ENABLED = false; // put back by protectedHall
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvfOff"));
		Villager lumberjack = worker(helper, Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, ModVillagers.LUMBERJACK);
		List<String> said = hand(helper, c, lumberjack, 25, 28);
		helper.assertTrue(TreeFarms.farm(lumberjack) != null, "with protection off a stranger's tree farm reaching in was refused: " + said);
		helper.assertTrue(said.stream().noneMatch(s -> s.contains("Owner's village")), "a refusal was said with protection off: " + said);
		helper.assertTrue(said.stream().anyMatch(s -> s.endsWith("within 8 blocks of their Fletching Table.")), "not the fletching table line: " + said);
		helper.succeed();
	}
}
