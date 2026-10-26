package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.RenewalLists;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.21, old villages renewed: the gaps RenewalGameTests leaves (written from the spec). The two-day
 * spacing from both sides, the config switch, a job other than the armorer's, the proposal sentence with its real
 * distance, direction and style, the fit inside the zone and the front on the door's side, and data-pack lists that are
 * broken or name a building that doesn't exist. Each test runs alone in its batch; the village is RenewalGameTests'.
 */
public class QaRenewalGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 24);
	private static final BlockPos CHEST = new BlockPos(3, 2, 25);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation TWO_BED_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1");
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final ResourceLocation FLETCHER_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_fletcher_house_1");

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static Village village(GameTestHelper helper, String style, boolean renew) {
		return village(helper, style, renew, null);
	}

	/** RenewalGameTests' village: its one zone painted on {@code cells} (null: x 6-29, z 0-22). */
	private static Village village(GameTestHelper helper, String style, boolean renew, BoundingBox only) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 20; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		if (only == null) {
			for (int x = 6; x <= 29; x++) {
				for (int z = 0; z <= 22; z++) {
					cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
				}
			}
		} else {
			for (int x = only.minX(); x <= only.maxX(); x++) {
				for (int z = only.minZ(); z <= only.maxZ(); z++) {
					cells.set(CityPlan.cellAt(hall, new BlockPos(x, only.minY(), z)));
				}
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", style).paint(0, cells);
		entity.setPlan(plan.editZone(0, "homes", "Homes 1", style, renew));
		OldHouses.forget(level, hall);
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, cityPlan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, cityPlan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward, builder);
	}

	private static OldHouses.House measure(Village v, BlockPos seed) {
		OldHouses.Measure m = new OldHouses.Measure(v.level(), seed);
		while (!m.done()) {
			m.step(1 << 16);
		}
		return m.house(v.hall(), v.entity().plan());
	}

	private static List<BlockPos> beds(ServerLevel level, BoundingBox box) {
		return level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 24, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList();
	}

	private static BlockPos find(ServerLevel level, BoundingBox box, Predicate<BlockState> what, String name) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (what.test(level.getBlockState(p))) {
				return p.immutable();
			}
		}
		throw new AssertionError("no " + name + " in " + box);
	}

	private static void complete(Village v, BuildSite site) {
		ServerLevel level = v.level();
		BuildPlan plan = site.plan(level);
		if (site.isDeconstruction()) {
			for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.DECONSTRUCT)) {
				level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.secondaryPos() != null) {
					level.setBlock(step.secondaryPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		} else {
			for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.CLEAR)) {
				level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (step.secondaryPos() != null && step.secondaryState() != null) {
						level.setBlock(step.secondaryPos(), step.secondaryState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
		Builders.finish(level, v.builder(), site);
	}

	private static BuildSite site(GameTestHelper helper, Village v) {
		List<Renewals.Renewal> active = Renewals.active(v.level(), v.hall());
		helper.assertTrue(active.size() == 1, "expected one renewal under way, found " + active.size());
		BuildSite site = BuildSiteManager.get(v.level()).get(active.get(0).site());
		helper.assertTrue(site != null, "the renewal's site is gone");
		return site;
	}

	/** The Steward's plan, with renewals switched {@code on} for the call (the GameTest run turns the config off). */
	private static void plan(Village v, boolean on) {
		boolean was = Renewals.ENABLED;
		Renewals.ENABLED = on;
		try {
			StewardDesk.plan(v.level(), v.steward(), v.hall());
		} finally {
			Renewals.ENABLED = was;
		}
	}

	private static List<StewardDesk.Proposal> renewals(Village v) {
		return StewardDesk.of(v.level(), v.hall()).proposals().stream().filter(StewardDesk.Proposal::isRenewal).toList();
	}

	private static Blueprint blueprint(ServerLevel level, ResourceLocation id) {
		return BlueprintLibrary.get(level, id).orElseThrow(() -> new AssertionError("no blueprint " + id));
	}

	/** Config {@code stewardRenewal} is on by default (spec: "Config stewardRenewal (true)"). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaStewardRenewalIsOnByDefault(GameTestHelper helper) {
		helper.assertTrue(new WorkplaceConfig().stewardRenewal, "stewardRenewal is off in a fresh config");
		helper.succeed();
	}

	/** Config off: the Steward proposes no renewal, even with the zone's renew switch on; on again, he does. */
	//$ gametest_ticks_batch AREA '600' '"qaRenewConfigOff"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRenewConfigOff")
	public void qaConfigOffProposesNoRenewalInARenewZone(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		helper.assertTrue(OldHouses.request(level, v.hall()).isPresent(), "no survey");
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(level, v.hall()).map(h -> h.size() == 1).orElse(false), "the survey hasn't found the house");
			plan(v, false);
			plan(v, false);
			helper.assertTrue(renewals(v).isEmpty() && Renewals.active(level, v.hall()).isEmpty(), "a renewal with stewardRenewal off: " + renewals(v).size());
			plan(v, true);
			helper.assertTrue(renewals(v).size() == 1, "switched on again, renewal proposals: " + renewals(v).size());
		});
	}

	/**
	 * At most one renewal every 2 days in a village: one approved on day 0 and finished at once, the second old house is
	 * not proposed on day 1, and is on day 2 (the renewed house no longer counts as old).
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaRenewTwoDays"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaRenewTwoDays")
	public void qaASecondOldHouseWaitsTwoDays(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox first = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BoundingBox second = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, new BlockPos(20, 2, 14));
		helper.assertTrue(OldHouses.request(level, v.hall()).isPresent(), "no survey");
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(OldHouses.result(level, v.hall()).map(h -> h.size() == 2).orElse(false), "the survey hasn't found both houses"))
			.thenExecute(() -> {
				helper.assertTrue(StewardWishesDay.of(level) == 0, "setup: not day 0 but " + StewardWishesDay.of(level));
				plan(v, true);
				List<StewardDesk.Proposal> p = renewals(v);
				helper.assertTrue(p.size() == 1, "day 0, renewal proposals: " + p.size());
				helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), p.get(0).id()) == StewardDesk.Outcome.STARTED, "not started");
				BoundingBox renewed = p.get(0).houseBox().orElseThrow();
				complete(v, site(helper, v));
				complete(v, site(helper, v));
				helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "the first renewal is still under way");
				helper.assertTrue(renewed.equals(first) || renewed.equals(second) || renewed.intersects(first) || renewed.intersects(second),
					"setup: renewed neither house: " + renewed);
				OldHouses.forget(level, v.hall());
				helper.assertTrue(OldHouses.request(level, v.hall()).isPresent(), "no second survey");
			})
			.thenWaitUntil(() -> helper.assertTrue(OldHouses.result(level, v.hall()).isPresent(), "the second survey isn't done"))
			.thenExecute(() -> {
				helper.setDayTime(2000 + 24000);
				helper.assertTrue(StewardWishesDay.of(level) == 1, "setup: not day 1");
				plan(v, true);
				plan(v, true);
				helper.assertTrue(renewals(v).isEmpty() && Renewals.active(level, v.hall()).isEmpty(), "a second renewal the next day: " + renewals(v).size());
				helper.setDayTime(2000 + 48000);
				plan(v, true);
				List<StewardDesk.Proposal> p = renewals(v);
				helper.assertTrue(p.size() == 1, "day 2, renewal proposals: " + p.size() + ", survey " + OldHouses.result(level, v.hall()).map(List::size));
			})
			.thenSucceed();
	}

	/** The Steward's day as the spec counts it (StewardWishes.day). */
	private static final class StewardWishesDay {
		static long of(ServerLevel level) {
			return io.github.jcondedata.aliveworkplace.city.StewardWishes.day(level);
		}
	}

	/**
	 * A job other than the armorer's: vanilla's plains fletcher house (a fletching table) becomes that job's building, the
	 * Fletcher's Lodge, within the old footprint plus 3, its front on the side the old door was.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRenewFletcher"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRenewFletcher")
	public void qaAFletchersHouseBecomesAFletchersLodgeFacingTheOldDoor(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, FLETCHER_HOUSE, HOUSE);
		BlockPos table = find(level, old, s -> s.is(Blocks.FLETCHING_TABLE), "fletching table");
		OldHouses.House house = measure(v, table);
		helper.assertTrue(house.renewable(), "setup: the fletcher house isn't renewable: " + house.verdict());
		Optional<Renewals.Replacement> r = Renewals.replacement(level, v.hall(), house);
		helper.assertTrue(r.isPresent(), "nothing replaces the fletcher house " + house.box());
		ResourceLocation lodge = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fletchers_lodge");
		helper.assertTrue(r.get().blueprint().getPath().equals(lodge.getPath()), "the fletcher house becomes " + r.get().blueprint() + ", not the Fletcher's Lodge");
		BoundingBox hb = house.box();
		BoundingBox box = r.get().box();
		helper.assertTrue(box.minX() >= hb.minX() - Renewals.MARGIN && box.maxX() <= hb.maxX() + Renewals.MARGIN
			&& box.minZ() >= hb.minZ() - Renewals.MARGIN && box.maxZ() <= hb.maxZ() + Renewals.MARGIN, "the lodge " + box + " reaches past " + hb + " by more than 3");
		// the old door: the lower half of a door, nearest the house's edge; its side is the old front
		BlockPos door = null;
		int best = Integer.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(hb.minX(), hb.minY(), hb.minZ(), hb.maxX(), hb.maxY(), hb.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.DOORS) && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				int e = Math.min(Math.min(p.getX() - hb.minX(), hb.maxX() - p.getX()), Math.min(p.getZ() - hb.minZ(), hb.maxZ() - p.getZ()));
				if (e < best) {
					best = e;
					door = p.immutable();
				}
			}
		}
		helper.assertTrue(door != null, "setup: the fletcher house has no door");
		int[] d = {door.getZ() - hb.minZ(), hb.maxX() - door.getX(), hb.maxZ() - door.getZ(), door.getX() - hb.minX()};
		Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		Rotation[] turns = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
		int side = 0;
		for (int i = 1; i < 4; i++) {
			if (d[i] < d[side]) {
				side = i;
			}
		}
		helper.assertTrue(r.get().placement().rotation() == turns[side], "the old door faced " + sides[side] + ", the lodge is turned " + r.get().placement().rotation());
		int newEdge = switch (sides[side]) {
			case NORTH -> box.minZ();
			case SOUTH -> box.maxZ();
			case WEST -> box.minX();
			default -> box.maxX();
		};
		int oldEdge = switch (sides[side]) {
			case NORTH -> hb.minZ();
			case SOUTH -> hb.maxZ();
			case WEST -> hb.minX();
			default -> hb.maxX();
		};
		helper.assertTrue(newEdge == oldEdge, "the lodge's front (" + sides[side] + ") is at " + newEdge + ", the old door's side at " + oldEdge);
		helper.succeed();
	}

	/**
	 * The proposal sentence with real numbers: a two-bed house straight east of the hall in a Cherry zone reads "Renew the
	 * old house N blocks east as a Stone House (Cherry)", N the distance from the hall to the house's middle; approved,
	 * the chronicle notes the plan with the same building.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRenewTitle"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRenewTitle")
	public void qaTheProposalReadsTheRealDistanceDirectionAndStyle(GameTestHelper helper) {
		Village v = village(helper, "cherry", true);
		ServerLevel level = v.level();
		int sizeZ = level.getStructureManager().get(TWO_BED_HOUSE).orElseThrow().getSize().getZ();
		BlockPos at = new BlockPos(14, 2, HALL.getZ() - (sizeZ - 1) / 2);
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, TWO_BED_HOUSE, at);
		OldHouses.House house = measure(v, beds(level, old).get(0));
		helper.assertTrue(house.renewable(), "setup: not renewable: " + house.verdict());
		Optional<Renewals.Replacement> r = Renewals.replacement(level, v.hall(), house);
		helper.assertTrue(r.isPresent(), "nothing replaces the two-bed house");
		Optional<StewardDesk.Proposal> p = StewardDesk.offerRenewal(level, v.hall(), house, r.get());
		helper.assertTrue(p.isPresent(), "not proposed");
		BlockPos centre = house.box().getCenter();
		helper.assertTrue(centre.getZ() == v.hall().getZ(), "setup: the house isn't straight east: centre z " + centre.getZ() + ", hall z " + v.hall().getZ());
		int distance = centre.getX() - v.hall().getX();
		String expected = "Renew the old house " + distance + " blocks east as a Stone House (Cherry)";
		String title = p.get().name().getString();
		helper.assertTrue(title.equals(expected), "the proposal reads \"" + title + "\", expected \"" + expected + "\"");
		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), p.get().id()) == StewardDesk.Outcome.STARTED, "not started");
		String line = v.entity().chronicle().stream().map(e -> e.text().getString()).filter(s -> s.contains("planned to renew an old house")).findFirst().orElse("");
		String expectedLine = v.steward().getDisplayName().getString() + " planned to renew an old house as a Stone House (Cherry): "
			+ v.builder().getDisplayName().getString() + " takes it down and builds it again";
		helper.assertTrue(line.equals(expectedLine), "the chronicle reads \"" + line + "\", expected \"" + expectedLine + "\"");
		helper.succeed();
	}

	/**
	 * The fit inside the zone: the renew zone painted only on the old house's own cells. Whatever is proposed stands
	 * wholly in a renew zone; nothing reaches into land outside it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRenewTight"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRenewTight")
	public void qaATightZoneGetsNothingThatReachesOutsideIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int sx = level.getStructureManager().get(TWO_BED_HOUSE).orElseThrow().getSize().getX();
		int sz = level.getStructureManager().get(TWO_BED_HOUSE).orElseThrow().getSize().getZ();
		BlockPos at = new BlockPos(14, 2, 4);
		BlockPos a = helper.absolutePos(at);
		Village v = village(helper, "", true, new BoundingBox(a.getX(), a.getY(), a.getZ(), a.getX() + sx - 1, a.getY(), a.getZ() + sz - 1));
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, TWO_BED_HOUSE, at);
		OldHouses.House house = measure(v, beds(level, old).get(0));
		Optional<Renewals.Replacement> r = Renewals.replacement(level, v.hall(), house);
		if (r.isPresent()) {
			BoundingBox box = r.get().box();
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos p = new BlockPos(x, box.minY(), z);
					helper.assertTrue(v.entity().plan().zoneAt(v.hall(), p).map(CityPlan.Zone::renew).orElse(false),
						"the replacement " + r.get().blueprint() + " " + box + " reaches " + p + ", outside the renew zone");
				}
			}
			helper.assertTrue(StewardSafety_beds(level, r.get().blueprint()) >= 2, "the replacement has fewer beds than the old house's two");
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("qaRenewTight: replacement {}", r.map(x -> x.blueprint() + " " + x.box()).orElse("none"));
		helper.succeed();
	}

	/** Run the village with the zone's renew switch off: the Steward renews nothing by himself. */
	//$ gametest_ticks_batch AREA '600' '"qaRenewRunOff"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRenewRunOff")
	public void qaRunTheVillageLeavesAZoneNotRenewedAlone(GameTestHelper helper) {
		Village v = village(helper, "", false);
		ServerLevel level = v.level();
		v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.RUN));
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		// with no zone to renew there is nothing to survey (the request may be refused); the Steward's own rounds run anyway
		OldHouses.request(level, v.hall());
		helper.runAfterDelay(200, () -> {
			plan(v, true);
			plan(v, true);
			helper.assertTrue(Renewals.active(level, v.hall()).isEmpty() && renewals(v).isEmpty(), "a renewal in a zone whose renew switch is off");
			helper.succeed();
		});
	}

	private static int StewardSafety_beds(ServerLevel level, ResourceLocation id) {
		return io.github.jcondedata.aliveworkplace.city.StewardConditions.beds(level, BlueprintStyles.base(id));
	}

	/**
	 * Data-pack lists: a broken file, one with no kind, one with no buildings and one whose job isn't an id are skipped
	 * without a crash (the others still load); a homes list naming a building that doesn't exist skips it and takes the
	 * next that fits.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRenewData"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRenewData")
	public void qaBrokenListsAndUnknownBuildingsAreSkipped(GameTestHelper helper) throws Exception {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		ResourceManager real = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> files = real.listResources(RenewalLists.FOLDER, p -> p.getPath().endsWith(".json"));
		ResourceLocation homes = ResourceLocation.fromNamespaceAndPath("aliveworkplace", RenewalLists.FOLDER + "/homes.json");
		Resource ours = files.get(homes);
		helper.assertTrue(ours != null, "setup: no " + homes);
		Map<ResourceLocation, Resource> withPack = new HashMap<>(files);
		withPack.put(homes, json(ours.source(), "{\"kind\": \"home\", \"buildings\": [\"aliveworkplace:no_such_building\", \"aliveworkplace:stone_house\"]}"));
		withPack.put(ResourceLocation.fromNamespaceAndPath("qa", RenewalLists.FOLDER + "/broken.json"), json(ours.source(), "{\"kind\": \"home\", \"buildings\": [ "));
		withPack.put(ResourceLocation.fromNamespaceAndPath("qa", RenewalLists.FOLDER + "/no_kind.json"), json(ours.source(), "{\"buildings\": [\"aliveworkplace:stone_house\"]}"));
		withPack.put(ResourceLocation.fromNamespaceAndPath("qa", RenewalLists.FOLDER + "/no_buildings.json"), json(ours.source(), "{\"job\": \"minecraft:mason\", \"buildings\": []}"));
		withPack.put(ResourceLocation.fromNamespaceAndPath("qa", RenewalLists.FOLDER + "/bad_job.json"), json(ours.source(), "{\"job\": \"Not An Id!\", \"buildings\": [\"aliveworkplace:smithy\"]}"));
		withPack.put(ResourceLocation.fromNamespaceAndPath("qa", RenewalLists.FOLDER + "/array.json"), json(ours.source(), "[1, 2]"));
		java.lang.reflect.Constructor<RenewalLists> c = RenewalLists.class.getDeclaredConstructor();
		c.setAccessible(true);
		ResourceManagerReloadListener listener = c.newInstance();
		int before = RenewalLists.all().size();
		try {
			listener.onResourceManagerReload(manager(real, withPack));
			helper.assertTrue(RenewalLists.all().size() == before, "lists loaded " + RenewalLists.all().size() + ", expected the " + before + " good ones");
			helper.assertTrue(RenewalLists.homes().map(l -> l.buildings().get(0).getPath().equals("no_such_building")).orElse(false), "the pack's homes list didn't load");
			BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
			OldHouses.House house = measure(v, beds(level, old).get(0));
			Optional<Renewals.Replacement> r = Renewals.replacement(level, v.hall(), house);
			helper.assertTrue(r.isPresent() && BlueprintStyles.base(r.get().blueprint()).getPath().equals("stone_house"),
				"past the unknown building the next wasn't taken: " + r.map(Renewals.Replacement::blueprint));
		} finally {
			listener.onResourceManagerReload(real);
		}
		helper.assertTrue(RenewalLists.homes().map(l -> l.buildings().get(0).getPath().equals("starter_cottage")).orElse(false), "the shipped homes list isn't back");
		helper.succeed();
	}

	/**
	 * The fit: a building wider and deeper than the old footprint plus 3 on each side can't go there and is passed over
	 * for the list's next (a homes list and the fletcher's list, each starting with an arena), never crashing the Steward.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRenewTooBig"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRenewTooBig")
	public void qaABuildingTooBigForThePlotIsPassedOver(GameTestHelper helper) throws Exception {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		ResourceLocation arena = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "arena_3");
		net.minecraft.core.Vec3i big = blueprint(level, arena).size();
		ResourceManager real = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> files = real.listResources(RenewalLists.FOLDER, p -> p.getPath().endsWith(".json"));
		ResourceLocation homes = ResourceLocation.fromNamespaceAndPath("aliveworkplace", RenewalLists.FOLDER + "/homes.json");
		ResourceLocation fletcher = ResourceLocation.fromNamespaceAndPath("aliveworkplace", RenewalLists.FOLDER + "/fletcher.json");
		Resource ours = files.get(homes);
		Map<ResourceLocation, Resource> withPack = new HashMap<>(files);
		withPack.put(homes, json(ours.source(), "{\"kind\": \"home\", \"buildings\": [\"aliveworkplace:arena_3\", \"aliveworkplace:starter_cottage\"]}"));
		withPack.put(fletcher, json(ours.source(), "{\"job\": \"minecraft:fletcher\", \"buildings\": [\"aliveworkplace:arena_3\", \"aliveworkplace:fletchers_lodge\"]}"));
		java.lang.reflect.Constructor<RenewalLists> c = RenewalLists.class.getDeclaredConstructor();
		c.setAccessible(true);
		ResourceManagerReloadListener listener = c.newInstance();
		try {
			listener.onResourceManagerReload(manager(real, withPack));
			BoundingBox small = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, new BlockPos(8, 2, 2));
			BoundingBox fl = OldHousesGameTests.placeVanilla(helper, FLETCHER_HOUSE, new BlockPos(18, 2, 12));
			OldHouses.House home = measure(v, beds(level, small).get(0));
			OldHouses.House shop = measure(v, find(level, fl, st -> st.is(Blocks.FLETCHING_TABLE), "fletching table"));
			for (OldHouses.House h : List.of(home, shop)) {
				helper.assertTrue(h.renewable(), "setup: not renewable: " + h.verdict());
				helper.assertTrue(Math.min(big.getX(), big.getZ()) > Math.max(h.box().getXSpan(), h.box().getZSpan()) + 2 * Renewals.MARGIN,
					"setup: the arena " + big + " would fit " + h.box());
			}
			Optional<Renewals.Replacement> r1 = Renewals.replacement(level, v.hall(), home);
			Optional<Renewals.Replacement> r2 = Renewals.replacement(level, v.hall(), shop);
			io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("qaRenewTooBig: home {} -> {}, shop {} -> {}", home.box(),
				r1.map(Renewals.Replacement::placement), shop.box(), r2.map(Renewals.Replacement::placement));
			helper.assertTrue(r1.map(r -> r.blueprint().getPath().equals("starter_cottage")).orElse(false), "the home became " + r1.map(Renewals.Replacement::blueprint));
			helper.assertTrue(r2.map(r -> r.blueprint().getPath().equals("fletchers_lodge")).orElse(false), "the fletcher house became " + r2.map(Renewals.Replacement::blueprint));
		} finally {
			listener.onResourceManagerReload(real);
		}
		helper.succeed();
	}

	private static Resource json(PackResources source, String text) {
		return new Resource(source, () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}

	/** {@code real}, with {@code files} for the renewal lists' folder. */
	private static ResourceManager manager(ResourceManager real, Map<ResourceLocation, Resource> files) {
		return new ResourceManager() {
			@Override
			public Set<String> getNamespaces() {
				return real.getNamespaces();
			}

			@Override
			public List<Resource> getResourceStack(ResourceLocation id) {
				return real.getResourceStack(id);
			}

			@Override
			public Map<ResourceLocation, Resource> listResources(String folder, Predicate<ResourceLocation> filter) {
				if (!folder.equals(RenewalLists.FOLDER)) {
					return real.listResources(folder, filter);
				}
				Map<ResourceLocation, Resource> out = new HashMap<>();
				files.forEach((k, r) -> {
					if (filter.test(k)) {
						out.put(k, r);
					}
				});
				return out;
			}

			@Override
			public Map<ResourceLocation, List<Resource>> listResourceStacks(String folder, Predicate<ResourceLocation> filter) {
				return real.listResourceStacks(folder, filter);
			}

			@Override
			public Stream<PackResources> listPacks() {
				return real.listPacks();
			}

			@Override
			public Optional<Resource> getResource(ResourceLocation id) {
				return real.getResource(id);
			}
		};
	}
}
