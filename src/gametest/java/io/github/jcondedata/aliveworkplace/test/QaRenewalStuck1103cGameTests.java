package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.Renewals;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-03) for B789: in the nightly city_timelapse showcase (CitySoak) a Steward renewal take-down
 * ({@code renewal/<hall>/1}, a DECONSTRUCT of the old plains_medium_house_1) stayed at STARTING with 0 placed and 0
 * skipped, its builder by his bench. QaRenewalTakeDown1102GameTests covers the plain case (one builder, a small house
 * standing on the grass, nothing else going on). Here the soak's conditions, as near as a 30x30 area allows: the medium
 * house sunk one block into the ground as the soak places it, three builders at tables in a row, the builder busy with
 * another build when the take-down opens, the builder away from his bench, his bench farther off, a save and load while
 * the take-down is still STARTING, the house partly gone already, and two take-downs queued on one builder. Each one
 * must leave STARTING, take the house down and hand over to the rebuild (the take-down site DONE and gone). Each test
 * runs alone in its batch; the day is kept (the builders work by day).
 */
public class QaRenewalStuck1103cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 26);
	private static final ResourceLocation MEDIUM_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1");
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The medium house (13 x 8 x 11), its floor at helper y 1: sunk one block into the grass as CitySoak places it (y -1). */
	private static final BlockPos SUNK_HOUSE = new BlockPos(14, 1, 3);
	/** Tables in a row south of the zone, three apart as CitySoak lays them. */
	private static final List<BlockPos> TABLES = List.of(new BlockPos(1, 2, 21), new BlockPos(4, 2, 21), new BlockPos(7, 2, 21));

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, List<Villager> builders) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** A village on flat grass: the hall, a Steward, a builder at each of {@code tables} with a chest beside it, one Homes zone renewing. */
	private static Village village(GameTestHelper helper, List<BlockPos> tables) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000);
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
				for (int y = 2; y < 24; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 10; x <= 29; x++) {
			for (int z = 0; z <= 18; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		entity.setPlan(plan.editZone(0, "homes", "Homes 1", "", true));
		OldHouses.forget(level, hall);
		List<Villager> builders = new ArrayList<>();
		for (BlockPos table : tables) {
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			helper.setBlock(table.north(), Blocks.CHEST);
			Villager builder = helper.spawn(EntityType.VILLAGER, table.south());
			Builders.employ(level, builder, helper.absolutePos(table));
			builders.add(builder);
		}
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.east(2)));
		ItemStack cityPlan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, cityPlan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, cityPlan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward, builders);
	}

	/** The blocks the house put where the flat ground was (grass at y 1, air above): what a take-down has to remove. */
	private static Set<BlockPos> houseBlocks(GameTestHelper helper, BoundingBox box) {
		Set<BlockPos> out = new HashSet<>();
		int ground = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = helper.getLevel().getBlockState(p);
			boolean flat = p.getY() == ground ? s.is(Blocks.GRASS_BLOCK) : s.isAir();
			if (!flat && p.getY() >= ground) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static long left(ServerLevel level, Set<BlockPos> blocks) {
		return blocks.stream().filter(p -> !level.getBlockState(p).isAir()).count();
	}

	/** Measures the house from its first bed and puts its renewal on the desk. */
	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v, BoundingBox old) {
		List<BlockPos> beds = RenewalGameTests.beds(v.level(), old);
		helper.assertTrue(!beds.isEmpty(), "setup: no bed in the old house " + old);
		OldHouses.Measure m = new OldHouses.Measure(v.level(), beds.get(0));
		while (!m.done()) {
			m.step(1 << 16);
		}
		OldHouses.House house = m.house(v.hall(), v.entity().plan());
		helper.assertTrue(house.renewable(), "setup: the old house isn't renewable: " + house.verdict());
		Optional<Renewals.Replacement> replacement = Renewals.replacement(v.level(), v.hall(), house);
		helper.assertTrue(replacement.isPresent(), "setup: nothing on the list fits the old house " + house.box());
		Optional<StewardDesk.Proposal> p = StewardDesk.offerRenewal(v.level(), v.hall(), house, replacement.get());
		helper.assertTrue(p.isPresent(), "setup: the renewal wasn't proposed");
		return p.get();
	}

	/** The take-down site of the renewal just started (the newest active one that is not building yet). */
	private static BuildSite takeDownSite(GameTestHelper helper, Village v) {
		List<Renewals.Renewal> active = Renewals.active(v.level(), v.hall());
		helper.assertTrue(!active.isEmpty(), "setup: no renewal under way");
		BuildSite site = BuildSiteManager.get(v.level()).get(active.get(active.size() - 1).site());
		helper.assertTrue(site != null && site.isDeconstruction(), "setup: the renewal's site isn't a take-down: " + site);
		return site;
	}

	/** What a take-down was last seen doing, kept each tick while its site exists. */
	private static final class Seen {
		final UUID id;
		final Set<BlockPos> blocks;
		String state = "never seen";
		int skipped;
		boolean leftStarting;
		long gone = -1;
		long leftAtGone = -1;

		Seen(UUID id, Set<BlockPos> blocks) {
			this.id = id;
			this.blocks = blocks;
		}
	}

	/**
	 * Keeps the day (the builders work by day) and watches each take-down; succeeds once every one has left STARTING,
	 * finished with the old house down (no more of its blocks left than it skipped, at most one in 20) and handed over to
	 * its rebuild. {@code builders} is asked each tick (a reload replaces the villager).
	 */
	private static void finishes(GameTestHelper helper, Village v, List<Seen> watched, Supplier<List<Villager>> builders) {
		ServerLevel level = v.level();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(1000);
			}
			for (Seen s : watched) {
				BuildSite site = BuildSiteManager.get(level).get(s.id);
				if (site != null) {
					s.state = site.status() + (site.isQueued() ? " (queued)" : "") + ", " + site.placed() + " placed, " + site.skipped() + " skipped, stage " + site.stage();
					s.skipped = site.skipped();
					if (site.status() != BuildSite.Status.STARTING || site.placed() > 0) {
						s.leftStarting = true;
					}
				} else if (s.gone < 0) {
					s.gone = level.getGameTime();
					s.leftAtGone = left(level, s.blocks);
				}
			}
		});
		helper.succeedWhen(() -> {
			StringBuilder where = new StringBuilder();
			for (Villager b : builders.get()) {
				where.append(' ').append(helper.relativePos(b.blockPosition()).toShortString());
			}
			for (int i = 0; i < watched.size(); i++) {
				Seen s = watched.get(i);
				helper.assertTrue(s.gone >= 0, "take-down " + (i + 1) + " of " + watched.size() + " isn't finished: " + s.state
					+ (s.leftStarting ? "" : " (never left STARTING)") + "; builders at" + where + "; house blocks left " + left(level, s.blocks)
					+ " of " + s.blocks.size());
				helper.assertTrue(s.leftStarting, "take-down " + (i + 1) + " finished without ever leaving STARTING: " + s.state);
				helper.assertTrue(s.leftAtGone <= Math.max(s.skipped, s.blocks.size() / 20),
					"take-down " + (i + 1) + " finished with " + s.leftAtGone + " of the old house's " + s.blocks.size() + " blocks still standing ("
						+ s.skipped + " skipped)");
			}
			List<Renewals.Renewal> active = Renewals.active(level, v.hall());
			helper.assertTrue(active.size() == watched.size() && active.stream().allMatch(Renewals.Renewal::building),
				"the take-downs didn't all hand over to their rebuilds: " + active.size() + " renewals under way, building "
					+ active.stream().filter(Renewals.Renewal::building).count());
		});
	}

	/**
	 * The soak's set-up: the medium house sunk a block into the grass, three builders at their tables, the Steward
	 * approving it himself (Run the village). The take-down leaves STARTING and the house comes down.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckSoak1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckSoak1103c")
	public void qaTheSunkMediumHouseIsTakenDownWithThreeBuilders(GameTestHelper helper) {
		Village v = village(helper, TABLES);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, MEDIUM_HOUSE, SUNK_HOUSE);
		Set<BlockPos> blocks = houseBlocks(helper, old);
		StewardDesk.Proposal proposal = propose(helper, v, old);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the renewal wasn't started: " + outcome);
		BuildSite down = takeDownSite(helper, v);
		helper.assertTrue(v.builders().stream().anyMatch(b -> b.getUUID().equals(down.builder())), "no builder of the village has the take-down");
		finishes(helper, v, List.of(new Seen(down.id(), blocks)), v::builders);
	}

	/**
	 * The builder is busy with another build when the Steward approves: the take-down is queued behind it. Once that build
	 * is done, the take-down is his next and it comes down.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckQueued1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckQueued1103c")
	public void qaATakeDownQueuedBehindAnotherBuildStartsWhenThatIsDone(GameTestHelper helper) {
		Village v = village(helper, TABLES.subList(0, 1));
		ServerLevel level = v.level();
		Villager builder = v.builders().get(0);
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, MEDIUM_HOUSE, SUNK_HOUSE);
		Set<BlockPos> blocks = houseBlocks(helper, old);
		BuildSite hut = Builders.start(level, builder, (net.minecraft.world.entity.player.Player) null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 2)), Rotation.NONE, Mirror.NONE));
		StewardDesk.Proposal proposal = propose(helper, v, old);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.QUEUED, "with the builder busy the renewal wasn't queued: " + outcome);
		BuildSite down = takeDownSite(helper, v);
		helper.assertTrue(down.isQueued() && builder.getUUID().equals(down.builder()), "setup: the take-down isn't queued for the busy builder");
		UUID hutId = hut.id();
		helper.runAfterDelay(200, () -> {
			BuildSite h = BuildSiteManager.get(level).get(hutId);
			if (h != null) {
				Builders.finish(level, builder, h); // the other build done (as at the end of its DECORATION)
			}
		});
		finishes(helper, v, List.of(new Seen(down.id(), blocks)), v::builders);
	}

	/**
	 * The builder is away when the take-down opens (across the area from both his bench and the house) and his bench is
	 * in the far corner: he walks over, takes it down, and back to the chests with what comes off it.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckAway1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckAway1103c")
	public void qaABuilderAwayFromAFarBenchStillTakesItDown(GameTestHelper helper) {
		Village v = village(helper, List.of(new BlockPos(0, 2, 28)));
		ServerLevel level = v.level();
		Villager builder = v.builders().get(0);
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, MEDIUM_HOUSE, SUNK_HOUSE);
		Set<BlockPos> blocks = houseBlocks(helper, old);
		BlockPos away = helper.absolutePos(new BlockPos(28, 2, 27));
		builder.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		StewardDesk.Proposal proposal = propose(helper, v, old);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the renewal wasn't started: " + outcome);
		BuildSite down = takeDownSite(helper, v);
		finishes(helper, v, List.of(new Seen(down.id(), blocks)), v::builders);
	}

	/**
	 * A save and load while the take-down is still STARTING (the sites and the builder from their saves, the scan read
	 * back from its file rather than the cache): it starts and finishes after the load.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckReload1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckReload1103c")
	public void qaASaveAndLoadWhileStartingDoesNotStrandTheTakeDown(GameTestHelper helper) {
		Village v = village(helper, TABLES.subList(0, 1));
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, MEDIUM_HOUSE, SUNK_HOUSE);
		Set<BlockPos> blocks = houseBlocks(helper, old);
		StewardDesk.Proposal proposal = propose(helper, v, old);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the renewal wasn't started: " + outcome);
		BuildSite down = takeDownSite(helper, v);
		helper.assertTrue(down.status() == BuildSite.Status.STARTING, "setup: the take-down isn't STARTING: " + down.status());
		ResourceLocation scan = down.structure();
		List<Villager> builders = new ArrayList<>(v.builders());
		// as a save and a load would: the build sites from their save, the builder from his, the scan from its file
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		level.getServer().getStructureManager().remove(scan);
		helper.assertTrue(level.getServer().getStructureManager().get(scan).isPresent(), "the old house's scan " + scan + " isn't on disk");
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(builders.get(0).save(tag), "the builder wasn't saved");
		builders.get(0).discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		builders.set(0, (Villager) back);
		BuildSite after = BuildSiteManager.get(level).get(down.id());
		helper.assertTrue(after != null && after.isDeconstruction() && after.stewardHall() != null,
			"after the load the take-down is " + (after == null ? "gone" : "deconstruction " + after.isDeconstruction() + ", hall " + after.stewardHall()));
		finishes(helper, v, List.of(new Seen(down.id(), blocks)), () -> builders);
	}

	/**
	 * Part of the old house is gone before the builder gets there (its roof and a wall broken after the approval, as a
	 * player or a creeper might): the rest comes down and the take-down finishes, the missing blocks no obstacle.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckPartGone1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckPartGone1103c")
	public void qaAHousePartlyGoneAlreadyIsStillTakenDown(GameTestHelper helper) {
		Village v = village(helper, TABLES.subList(0, 1));
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, MEDIUM_HOUSE, SUNK_HOUSE);
		Set<BlockPos> blocks = houseBlocks(helper, old);
		StewardDesk.Proposal proposal = propose(helper, v, old);
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, proposal.id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the renewal wasn't started: " + outcome);
		BuildSite down = takeDownSite(helper, v);
		// the top three layers and the west wall go before he starts
		int broken = 0;
		for (BlockPos p : blocks) {
			if (p.getY() > old.maxY() - 3 || p.getX() == old.minX()) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
				broken++;
			}
		}
		helper.assertTrue(broken > 10, "setup: only " + broken + " blocks broken");
		finishes(helper, v, List.of(new Seen(down.id(), blocks)), v::builders);
	}

	/**
	 * Two old houses, both renewals approved one after the other with one builder (a Village and an Apprentice Steward:
	 * two builds open): the second take-down waits in his queue and comes down after the first.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaRenewStuckTwo1103c"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaRenewStuckTwo1103c")
	public void qaTwoTakeDownsQueuedOnOneBuilderBothFinish(GameTestHelper helper) {
		Village v = village(helper, TABLES.subList(0, 1));
		ServerLevel level = v.level();
		v.entity().setRank(VillageRanks.Rank.VILLAGE);
		v.steward().setVillagerData(v.steward().getVillagerData().setLevel(2));
		helper.assertTrue(Stewards.maxOpenBuilds(level, v.steward()) >= 2, "setup: only " + Stewards.maxOpenBuilds(level, v.steward()) + " builds may be open");
		BoundingBox first = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, new BlockPos(11, 2, 1));
		BoundingBox second = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, new BlockPos(21, 2, 11));
		Set<BlockPos> firstBlocks = houseBlocks(helper, first);
		Set<BlockPos> secondBlocks = houseBlocks(helper, second);
		StewardDesk.Outcome one = StewardDesk.approve(level, v.hall(), null, propose(helper, v, first).id());
		helper.assertTrue(one == StewardDesk.Outcome.STARTED, "the first renewal wasn't started: " + one);
		BuildSite downOne = takeDownSite(helper, v);
		StewardDesk.Outcome two = StewardDesk.approve(level, v.hall(), null, propose(helper, v, second).id());
		helper.assertTrue(two == StewardDesk.Outcome.QUEUED, "the second renewal wasn't queued behind the first: " + two);
		BuildSite downTwo = takeDownSite(helper, v);
		helper.assertTrue(!downTwo.id().equals(downOne.id()) && downTwo.isQueued(), "setup: the second take-down isn't queued");
		finishes(helper, v, List.of(new Seen(downOne.id(), firstBlocks), new Seen(downTwo.id(), secondBlocks)), v::builders);
	}
}
