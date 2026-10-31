package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for 36.20, village-wide work reach, written from the spec: "a spot is in reach when it is inside the village's
 * claimed area (the City Plan zones and the hall's radius), wherever the worker's block stands in it, or within a long
 * reach of the worker's block outside it: 128 for every job, a config key each; the refusal names the village ('outside
 * Mapleridge and 140 blocks from the Blueprint Table — keep it within 128')". Every job goes through the player's own
 * right-click on the worker (the placed Blueprint, the Quarry Marker, the Field Marker for a field, tree farm, orchard
 * and berry plot) and is checked on both sides of every boundary: exactly 128 and 129 out, the reach at its minimum 16
 * (16 and 17) and maximum 256 (256 and 257), one block inside and outside the hall's radius, a painted zone cell beyond
 * the radius, a second village's land, a worker block outside the village whose site is in it, the config keys just past
 * their range, and a far field kept through a save and reload of the farmer.
 */
public class QaVillageReachGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	/** One job's way in: the worker, the player's right-click with an area centred {@code off} from the worker's block. */
	record Kit(String name, String tail, IntSupplier reach, IntConsumer setReach, Offer offer) {
	}

	interface Offer {
		/** True if the spot centred {@code off} from the work block was taken (the job is forgotten again). */
		boolean take(Vec3i off);
	}

	static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
	}

	static void radius(int r) {
		VillageHalls.RADIUS = r;
		VillageHalls.forgetNearest();
	}

	static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** Builds the kit for {@code job}: its block at WORK, its worker at AT, a survival player whose lines go to {@code seen}. */
	static Kit kit(GameTestHelper helper, String job, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		BlockPos work = helper.absolutePos(WORK);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		switch (job) {
			case "builder" -> {
				helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
				Builders.employ(level, v, work);
				Leftovers.after(helper, () -> forgetSites(level, v));
				return new Kit(job, "from the builder's Blueprint Table", () -> Builders.MAX_SITE_DISTANCE, r -> Builders.MAX_SITE_DISTANCE = r, off -> {
					p.setItemInHand(InteractionHand.MAIN_HAND, QaBuilderLimitsGameTests.placedHut(level, work, off));
					QaStageSixGameTests.clickVillager(p, v, seen);
					BuildSite site = Builders.activeSite(level, v);
					forgetSites(level, v);
					return site != null;
				});
			}
			case "miner" -> {
				helper.setBlock(WORK, ModBlocks.MINERS_BENCH);
				Jobs.employ(level, v, work, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
				Leftovers.after(helper, () -> {
					QuarrySite left = Miners.activeSite(level, v);
					if (left != null) {
						Miners.cancel(level, left);
					}
				});
				return new Kit(job, "the miner works at", () -> Miners.MAX_DISTANCE, r -> Miners.MAX_DISTANCE = r, off -> {
					// a 5 × 5 quarry one block deep, so its centre is the spot itself
					BlockPos at = work.offset(off);
					ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
					marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2)), 1));
					p.setItemInHand(InteractionHand.MAIN_HAND, marker);
					QaStageSixGameTests.clickVillager(p, v, seen);
					QuarrySite site = Miners.activeSite(level, v);
					if (site != null) {
						Miners.cancel(level, site);
					}
					return site != null;
				});
			}
			case "farmer" -> {
				return area(helper, p, v, seen, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, ModAttachments.FARM_FIELD, "the farmer works at",
					() -> Fields.MAX_DISTANCE, r -> Fields.MAX_DISTANCE = r);
			}
			case "lumberjack" -> {
				return area(helper, p, v, seen, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK, ModAttachments.TREE_FARM,
					"the lumberjack works at", () -> AreaJobs.TREE_FARM_REACH, r -> AreaJobs.TREE_FARM_REACH = r);
			}
			case "orchard" -> {
				return area(helper, p, v, seen, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER, ModAttachments.ORCHARD,
					"the orchard keeper works at", () -> AreaJobs.ORCHARD_REACH, r -> AreaJobs.ORCHARD_REACH = r);
			}
			case "berry" -> {
				return area(helper, p, v, seen, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER, ModAttachments.BERRY_PLOT,
					"the berry breeder works at", () -> AreaJobs.BERRY_PLOT_REACH, r -> AreaJobs.BERRY_PLOT_REACH = r);
			}
			default -> throw new IllegalArgumentException(job);
		}
	}

	private static Kit area(GameTestHelper helper, ServerPlayer p, Villager v, List<Component> seen, Block block,
							net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi, VillagerProfession profession,
							Attachment<FieldJob> type, String tail, IntSupplier reach, IntConsumer setReach) {
		ServerLevel level = helper.getLevel();
		BlockPos work = helper.absolutePos(WORK);
		helper.setBlock(WORK, block);
		Jobs.employ(level, v, work, poi, profession);
		Leftovers.after(helper, () -> type.remove(v));
		return new Kit(profession.name(), tail, reach, setReach, off -> {
			p.setItemInHand(InteractionHand.MAIN_HAND, fieldMarker(level, work.offset(off)));
			QaStageSixGameTests.clickVillager(p, v, seen);
			boolean taken = type.get(v) != null;
			type.remove(v);
			return taken;
		});
	}

	/** A Field Marker holding a 5 × 5 area centred on {@code at}. */
	static ItemStack fieldMarker(ServerLevel level, BlockPos at) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2))));
		return marker;
	}

	static void forgetSites(ServerLevel level, Villager builder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite site : List.copyOf(sites.all())) {
			if (builder.getUUID().equals(site.builder())) {
				sites.remove(site.id());
			}
		}
	}

	/** Asserts {@code off} is taken. */
	static void taken(GameTestHelper helper, Kit kit, List<Component> seen, Vec3i off, String why) {
		seen.clear();
		helper.assertTrue(kit.offer().take(off), kit.name() + ": " + why + " was refused: " + texts(seen));
	}

	/** Asserts {@code off} is refused with a line that {@code line} accepts. */
	static void refused(GameTestHelper helper, Kit kit, List<Component> seen, Vec3i off, String why, Predicate<String> line, String expected) {
		seen.clear();
		helper.assertTrue(!kit.offer().take(off), kit.name() + ": " + why + " was taken");
		helper.assertTrue(texts(seen).stream().anyMatch(line), kit.name() + ": " + why + ": expected " + expected + ", saw " + texts(seen));
	}

	/**
	 * Every boundary of one job's rule: exactly the long reach and one past it, the reach at 16 and 256 and one past each,
	 * one block inside and outside the hall's radius, a zone painted beyond the radius, and the refusal rendered in full.
	 */
	static void boundaries(GameTestHelper helper, String job) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		Kit kit = kit(helper, job, seen);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos work = helper.absolutePos(WORK);
		String village = VillageHalls.name(level, hall).getString();
		int was = kit.reach().getAsInt();
		Leftovers.after(helper, () -> kit.setReach().accept(was));
		helper.assertTrue(was == 128, kit.name() + "'s long reach isn't 128 but " + was);

		// a small village (radius 16): everything east past 20 is outside it, only the long reach counts
		radius(16);
		taken(helper, kit, seen, new Vec3i(128, 0, 0), "a spot exactly 128 blocks out");
		String full = "That " + (job.equals("farmer") ? "field" : "spot") + " is outside " + village + " and 129 blocks from " + (job.equals("builder") ? "" : "the ");
		refused(helper, kit, seen, new Vec3i(129, 0, 0), "a spot 129 blocks out",
			l -> l.startsWith(full) && l.endsWith(kit.tail() + " — keep it within 128."), "\"" + full + "… " + kit.tail() + " — keep it within 128.\"");
		// diagonally: 90, 0, 90 is 127.3 (in); 91, 0, 91 is 128.7, which reads as 129
		taken(helper, kit, seen, new Vec3i(90, 0, 90), "a spot 127.3 blocks out diagonally");
		refused(helper, kit, seen, new Vec3i(91, 0, 91), "a spot 128.7 blocks out diagonally",
			l -> l.contains(" and 129 blocks from ") && l.endsWith("keep it within 128."), "129 blocks, within 128");

		// the key at its minimum and maximum, each from both sides
		kit.setReach().accept(16);
		radius(4);
		// the hall is 2.8 blocks from the work block: a radius of 4 still holds the block, and nothing 16 east
		taken(helper, kit, seen, new Vec3i(0, 0, 16), "a spot 16 blocks out with the reach at 16");
		refused(helper, kit, seen, new Vec3i(0, 0, 17), "a spot 17 blocks out with the reach at 16",
			l -> l.contains(" and 17 blocks from ") && l.endsWith("keep it within 16."), "17 blocks, within 16");
		kit.setReach().accept(256);
		taken(helper, kit, seen, new Vec3i(256, 0, 0), "a spot 256 blocks out with the reach at 256");
		refused(helper, kit, seen, new Vec3i(257, 0, 0), "a spot 257 blocks out with the reach at 256",
			l -> l.contains(" and 257 blocks from ") && l.endsWith("keep it within 256."), "257 blocks, within 256");

		// the village's edge: the hall's radius 64, the long reach 16 so only the village counts
		kit.setReach().accept(16);
		radius(64);
		Vec3i edge = hall.east(64).subtract(work);
		Vec3i past = hall.east(65).subtract(work);
		taken(helper, kit, seen, edge, "a spot on the village's edge (64 from the hall, radius 64)");
		refused(helper, kit, seen, past, "a spot one block past the village's edge",
			l -> l.contains("outside " + village + " and ") && l.endsWith("keep it within 16."), "outside " + village + ", within 16");
		taken(helper, kit, seen, hall.west(64).subtract(work), "a spot on the village's west edge");
		taken(helper, kit, seen, hall.north(64).subtract(work), "a spot on the village's north edge");

		// a zone painted in a corner cell, past the radius (the claimed area grows with the City Plan)
		BlockPos corner = hall.offset(56, 0, 56); // 79 from the hall, outside the radius 64, on the plan's grid
		refused(helper, kit, seen, corner.subtract(work), "an unpainted corner spot outside the radius",
			l -> l.contains("outside " + village), "outside " + village);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BitSet cells = new BitSet();
		int cell = CityPlan.cellAt(hall, corner);
		helper.assertTrue(cell >= 0, "the corner isn't on the plan's grid");
		cells.set(cell);
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		taken(helper, kit, seen, corner.subtract(work), "a spot in a zone painted past the hall's radius");
		entity.setPlan(CityPlan.EMPTY);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachBuilder")
	public void theBuildersReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "builder");
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachMiner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachMiner")
	public void theMinersReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "miner");
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachFarmer"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachFarmer")
	public void theFarmersReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "farmer");
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachLumberjack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachLumberjack")
	public void theLumberjacksReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "lumberjack");
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachOrchard"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachOrchard")
	public void theOrchardKeepersReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "orchard");
	}

	//$ gametest_ticks_batch AREA '200' '"qaReachBerry"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachBerry")
	public void theBerryBreedersReachHoldsAtEveryBoundary(GameTestHelper helper) {
		boundaries(helper, "berry");
	}

	/**
	 * Two villages: only the worker's own village counts. A spot inside another village 160 blocks off is refused, and the
	 * sentence names the worker's village; with the long reach at 256 the same spot is taken.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReachTwoVillages"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachTwoVillages")
	public void aSpotInAnotherVillageCountsOnlyTheLongReach(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		Kit kit = kit(helper, "farmer", seen);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos other = hall.south(200);
		level.setBlockAndUpdate(other, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Leftovers.after(helper, () -> level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState()));
		radius(64);
		String mine = VillageHalls.name(level, hall).getString();
		helper.assertTrue(VillageHalls.nearest(level, other.north(40)).equals(Optional.of(other)), "the second hall isn't a village");
		// 158 south of the composter: inside village B (about 40 from its hall), outside the farmer's own
		Vec3i inB = new Vec3i(0, 0, 158);
		refused(helper, kit, seen, inB, "a spot in another village 158 blocks off",
			l -> l.equals("That field is outside " + mine + " and 158 blocks from the Composter the farmer works at — keep it within 128."),
			"\"That field is outside " + mine + " and 158 blocks from the Composter the farmer works at — keep it within 128.\"");
		// 118 south, between the villages: the long reach takes it
		taken(helper, kit, seen, new Vec3i(0, 0, 118), "a spot 118 blocks off between the villages");
		int reach = Fields.MAX_DISTANCE;
		Leftovers.after(helper, () -> Fields.MAX_DISTANCE = reach);
		Fields.MAX_DISTANCE = 256;
		taken(helper, kit, seen, inB, "a spot in another village 158 blocks off with the reach at 256");
		helper.succeed();
	}

	/**
	 * The worker's block decides the village: a composter outside the village (radius 16, the hall 140 blocks west) can't
	 * reach the hall's own block, 140 away, and the sentence has no village; when the village grows to hold it, it can.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaReachBlockOutside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachBlockOutside")
	public void aWorkBlockOutsideTheVillageReachesOnlyItsLongReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int was = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			radius(was);
		});
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		Kit kit = kit(helper, "farmer", seen);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos hall = work.west(140);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Leftovers.after(helper, () -> level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState()));
		radius(16);
		Vec3i atHall = hall.north(3).subtract(work);
		refused(helper, kit, seen, atHall, "a spot in the village 140 blocks from a composter outside it",
			l -> l.equals("That field is 141 blocks from the Composter the farmer works at — keep it within 128."),
			"\"That field is 141 blocks from the Composter the farmer works at — keep it within 128.\"");
		// Halls left standing by earlier batches' test areas (GameTest never clears a passed test's structure) can be
		// nearer the composter than this test's own, 140 blocks off, once the radius is 160; then that hall's village
		// would hold the composter and this test would fail by where it happened to be laid out. This test runs in its
		// own batch, so every other hall in range belongs to a finished test: take them away first.
		// B559: a hall's point of interest can outlive its block (left by an earlier batch with no hall standing there),
		// and breaking a block that is already gone doesn't take the point away; then the village search still found it.
		// So the point goes too, and the test checks the composter's village is this test's own before it goes on.
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = level.getPoiManager();
		pois.findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> !p.equals(hall), work, 170,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
			.toList().forEach(other -> {
				level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
				if (pois.getType(other).isPresent()) {
					pois.remove(other);
				}
			});
		radius(160);
		Optional<BlockPos> village = io.github.jcondedata.aliveworkplace.work.Reach.village(level, work);
		helper.assertTrue(village.equals(Optional.of(hall)), "the composter's village isn't this test's hall at " + hall.toShortString() + " but " + village
			+ "; halls by the composter: " + pois.findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), p -> true, work, 200,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).map(p -> p.toShortString() + " (" + (int) Math.sqrt(p.distSqr(work))
				+ " blocks, " + level.getBlockState(p).getBlock() + ")").toList());
		taken(helper, kit, seen, atHall, "a spot 141 blocks off in the village that now holds the composter");
		helper.succeed();
	}

	/** A field 100 blocks outside the village stays the farmer's through a save and reload, and while he works. */
	//$ gametest_ticks_batch AREA '200' '"qaReachFieldReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaReachFieldReload")
	public void aFarFieldSurvivesASaveAndReload(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		radius(16);
		List<Component> seen = new ArrayList<>();
		BlockPos work = helper.absolutePos(WORK);
		helper.setBlock(WORK, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, AT);
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, work, PoiTypes.FARMER, VillagerProfession.FARMER);
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		p.setItemInHand(InteractionHand.MAIN_HAND, fieldMarker(level, work.east(100)));
		QaStageSixGameTests.clickVillager(p, farmer, seen);
		FieldJob field = ModAttachments.FARM_FIELD.get(farmer);
		helper.assertTrue(field != null, "the field 100 blocks outside the village was refused: " + texts(seen));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(farmer.save(tag), "the farmer wasn't saved");
		farmer.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the farmer didn't load");
		level.addFreshEntity(back);
		Villager again = (Villager) back;
		again.setNoAi(false);
		Leftovers.after(helper, () -> ModAttachments.FARM_FIELD.remove(again));
		helper.runAfterDelay(100, () -> {
			FieldJob after = ModAttachments.FARM_FIELD.get(again);
			helper.assertTrue(after != null && after.box().equals(field.box()), "the far field was lost after a reload and 100 ticks of work: " + after);
			helper.succeed();
		});
	}

	/** Each reach key one past its range clamps to the edge, and a value on the edge stays. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theReachKeysClampJustPastTheirRange(GameTestHelper helper) {
		String[] keys = {"maxSiteDistance", "quarryReach", "fieldReach", "treeFarmReach", "orchardAreaReach", "berryPlotReach"};
		for (String key : keys) {
			for (int[] in : new int[][]{{15, 16}, {16, 16}, {17, 17}, {255, 255}, {256, 256}, {257, 256}, {-5, 16}}) {
				WorkplaceConfig c = WorkplaceConfig.parse("{\"configVersion\": 5, \"" + key + "\": " + in[0] + "}");
				int got = switch (key) {
					case "maxSiteDistance" -> c.maxSiteDistance;
					case "quarryReach" -> c.quarryReach;
					case "fieldReach" -> c.fieldReach;
					case "treeFarmReach" -> c.treeFarmReach;
					case "orchardAreaReach" -> c.orchardAreaReach;
					default -> c.berryPlotReach;
				};
				helper.assertTrue(got == in[1], key + " " + in[0] + " read as " + got + ", not " + in[1]);
			}
		}
		// the old format's 48 moves only for the builder's key (the area jobs had no key before); the others stay as written
		WorkplaceConfig old = WorkplaceConfig.parse("{\"configVersion\": 4, \"maxSiteDistance\": 48, \"quarryReach\": 48}");
		helper.assertTrue(old.maxSiteDistance == 128 && old.quarryReach == 48, "an old file read as " + old.maxSiteDistance + "/" + old.quarryReach);
		helper.succeed();
	}
}
