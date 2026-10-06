package io.github.jcondedata.aliveworkplace.test;

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
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 27.21, old villages renewed. A village on flat grass (a hall, a builder at his table, a Steward, one zone east
 * of the hall with "renew old houses" on) and a vanilla plains house placed from its template as a village places it.
 * The take-down and the rebuild are done at once here (each site's own plan, then {@link Builders#finish}), so each test
 * checks the swap and not the builder's pace; taking a house down block by block into the store is BuilderGameTests'.
 * Each test runs alone in its batch.
 */
public class RenewalGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 24);
	private static final BlockPos CHEST = new BlockPos(3, 2, 25);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation ARMORER_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_armorer_house_1");
	private static final ResourceLocation TWO_BED_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_medium_house_1");
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** The village, its one zone in {@code style} with the renew switch {@code renew}. */
	private static Village village(GameTestHelper helper, String style, boolean renew) {
		Leftovers.clear(helper);
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
		for (int x = 6; x <= 29; x++) {
			for (int z = 0; z <= 22; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
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

	/** The house measured from {@code seed} (absolute) and judged for the village. */
	private static OldHouses.House measure(GameTestHelper helper, Village v, BlockPos seed) {
		OldHouses.Measure m = new OldHouses.Measure(v.level(), seed);
		while (!m.done()) {
			m.step(1 << 16);
		}
		return m.house(v.hall(), v.entity().plan());
	}

	/** The first block of {@code block} in {@code box}. */
	private static BlockPos find(ServerLevel level, BoundingBox box, Block block) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(block)) {
				return p.immutable();
			}
		}
		throw new AssertionError("no " + block + " in " + box);
	}

	/** Beds (head halves) in {@code box}. */
	private static List<BlockPos> beds(ServerLevel level, BoundingBox box) {
		return level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 24, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList();
	}

	/** The renewal proposed on the desk for the house found by {@code seed}. */
	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v, OldHouses.House house) {
		helper.assertTrue(house.renewable(), "setup: the old house isn't renewable: " + house.verdict());
		Optional<Renewals.Replacement> replacement = Renewals.replacement(v.level(), v.hall(), house);
		helper.assertTrue(replacement.isPresent(), "nothing on the list fits the old house " + house.box());
		Optional<StewardDesk.Proposal> p = StewardDesk.offerRenewal(v.level(), v.hall(), house, replacement.get());
		helper.assertTrue(p.isPresent(), "not proposed");
		return p.get();
	}

	/** Does the site's whole plan at once and finishes it as the builder would. */
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

	private static boolean chronicled(Village v, String key) {
		return v.entity().chronicle().stream().anyMatch(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals(key));
	}

	/**
	 * Vanilla's plains armorer house in a Stonework zone is renewed as a Smithy in Stonework: the old house scanned into a
	 * renewal blueprint and taken down, the Smithy built on the plot with its front where the door was, and the level-2
	 * armorer keeps the job and the level and works the new blast furnace.
	 */
	//$ gametest_ticks_batch AREA '400' '"renewArmorer"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "renewArmorer")
	public void anArmorersHouseBecomesASmithyAndTheArmorerWorksIt(GameTestHelper helper) {
		Village v = village(helper, "stonework", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, ARMORER_HOUSE, HOUSE);
		BlockPos furnace = find(level, old, Blocks.BLAST_FURNACE);
		Villager armorer = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		armorer.setVillagerData(armorer.getVillagerData().setProfession(VillagerProfession.ARMORER).setLevel(2));
		armorer.setVillagerXp(20);
		Stations.assign(level, armorer, furnace, VillagerProfession.ARMORER);
		OldHouses.House house = measure(helper, v, furnace);
		StewardDesk.Proposal proposal = propose(helper, v, house);
		ResourceLocation smithy = BlueprintStyles.styled(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "smithy"), "stonework");
		helper.assertTrue(proposal.blueprint().equals(smithy), "proposed " + proposal.blueprint() + ", not the Smithy in Stonework");
		String title = proposal.name().getString();
		helper.assertTrue(title.startsWith("Renew the old house ") && title.contains(" blocks ") && title.endsWith(" as a Smithy (Stonework)"),
			"the proposal reads \"" + title + "\"");
		Blueprint newOne = BlueprintLibrary.get(level, smithy).orElseThrow();
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), newOne.size());
		helper.assertTrue(box.minX() >= old.minX() - Renewals.MARGIN && box.maxX() <= old.maxX() + Renewals.MARGIN
			&& box.minZ() >= old.minZ() - Renewals.MARGIN && box.maxZ() <= old.maxZ() + Renewals.MARGIN, "the Smithy " + box + " reaches past " + old + " by more than 3");

		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()) == StewardDesk.Outcome.STARTED, "not started");
		BuildSite down = site(helper, v);
		helper.assertTrue(down.isDeconstruction() && Renewals.isScan(down.structure())
				&& down.structure().getPath().startsWith("renewal/" + v.hall().getX() + "_" + v.hall().getY() + "_" + v.hall().getZ() + "/"),
			"the first site isn't the old house's scan taken down: " + down.structure());
		Blueprint scan = BlueprintLibrary.get(level, down.structure()).orElseThrow();
		helper.assertTrue(scan.blocks().size() == house.blocks(), "the scan has " + scan.blocks().size() + " blocks, the house " + house.blocks());
		helper.assertTrue(chronicled(v, "chronicle.aliveworkplace.plans_renew"), "the chronicle doesn't note the plan");

		complete(v, down);
		helper.assertBlockNotPresent(Blocks.BLAST_FURNACE, helper.relativePos(furnace));
		BuildSite up = site(helper, v);
		helper.assertTrue(!up.isDeconstruction() && up.structure().equals(smithy) && up.placement().equals(proposal.placement()),
			"the Smithy didn't follow the take-down: " + up.structure());
		complete(v, up);
		helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "the renewal is still under way");
		helper.assertTrue(chronicled(v, "chronicle.aliveworkplace.renewed"), "the chronicle doesn't note the house renewed");
		BlockPos newFurnace = find(level, box, Blocks.BLAST_FURNACE);
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(armorer.getVillagerData().getProfession() == VillagerProfession.ARMORER && armorer.getVillagerData().getLevel() == 2,
				"the armorer lost the job: " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(armorer.getVillagerData().getProfession()) + " level "
					+ armorer.getVillagerData().getLevel());
			helper.assertTrue(armorer.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(GlobalPos.of(level.dimension(), newFurnace)::equals).isPresent(),
				"the armorer doesn't work the new blast furnace: " + armorer.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(level.getPoiManager().getFreeTickets(newFurnace) == 0, "the new blast furnace is free");
			helper.succeed();
		});
	}

	/** A two-bed home becomes a home with at least two beds (a Stone House: the Starter Cottage has one), and both its villagers sleep there. */
	//$ gametest_ticks_batch AREA '6000' '"renewTwoBeds"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "renewTwoBeds")
	public void aTwoBedHomeBecomesAHomeWhereBothSleep(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, TWO_BED_HOUSE, HOUSE);
		List<BlockPos> oldBeds = beds(level, old);
		helper.assertTrue(oldBeds.size() == 2, "setup: the medium house has " + oldBeds.size() + " beds");
		List<Villager> sleepers = new ArrayList<>();
		for (BlockPos bed : oldBeds) {
			Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 2));
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
			villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			sleepers.add(villager);
		}
		OldHouses.House house = measure(helper, v, oldBeds.get(0));
		StewardDesk.Proposal proposal = propose(helper, v, house);
		helper.assertTrue(proposal.blueprint().getPath().equals("stone_house"), "proposed " + proposal.blueprint() + ", not the Stone House");
		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()) == StewardDesk.Outcome.STARTED, "not started");
		complete(v, site(helper, v));
		complete(v, site(helper, v));
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), BlueprintLibrary.get(level, proposal.blueprint()).orElseThrow().size());
		List<BlockPos> newBeds = beds(level, box);
		helper.assertTrue(newBeds.size() >= 2, "the new home has " + newBeds.size() + " beds");
		for (Villager villager : sleepers) {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).map(g -> box.isInside(g.pos())).orElse(false),
				"a villager of the old house has no bed in the new one: " + villager.getBrain().getMemory(MemoryModuleType.HOME));
		}
		// they come home for the night on foot, from where they stood while the house was renewed
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			for (Villager villager : sleepers) {
				helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().map(box::isInside).orElse(false),
					"not asleep in the new home yet: at " + helper.relativePos(villager.blockPosition()) + ", home " + villager.getBrain().getMemory(MemoryModuleType.HOME)
						.map(g -> helper.relativePos(g.pos())) + ", activity " + villager.getBrain().getActiveNonCoreActivity() + ", new home " + box);
			}
		});
	}

	/** Saved and loaded between the take-down and the rebuild: the renewal, its scan and the site come back, and it finishes. */
	//$ gametest_ticks_batch AREA '400' '"renewReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "renewReload")
	public void aRenewalSurvivesASaveBetweenTakeDownAndRebuild(GameTestHelper helper) {
		Village v = village(helper, "cherry", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos bed = beds(level, old).get(0);
		Villager sleeper = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 2));
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
		sleeper.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		StewardDesk.Proposal proposal = propose(helper, v, measure(helper, v, bed));
		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()) == StewardDesk.Outcome.STARTED, "not started");
		BuildSite down = site(helper, v);
		ResourceLocation scan = down.structure();
		complete(v, down);
		StewardDesk.State before = StewardDesk.of(level, v.hall());

		// the hall saved and loaded, the site saved and loaded, the scan read again from its file
		CompoundTag tag = v.entity().saveWithoutMetadata(level.registryAccess());
		v.entity().setStewardDesk(StewardDesk.State.EMPTY);
		v.entity().loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(StewardDesk.of(level, v.hall()).equals(before), "the desk came back different: " + StewardDesk.of(level, v.hall()).renewals());
		BuildSite up = site(helper, v);
		BuildSite loaded = BuildSite.load(up.save());
		helper.assertTrue(loaded.id().equals(up.id()) && loaded.structure().equals(proposal.blueprint()) && !loaded.isDeconstruction()
			&& v.hall().equals(loaded.stewardHall()), "the rebuild's site came back different");
		level.getServer().getStructureManager().remove(scan);
		helper.assertTrue(level.getServer().getStructureManager().get(scan).isPresent(), "the scan " + scan + " wasn't saved to a file");

		complete(v, up);
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), BlueprintLibrary.get(level, proposal.blueprint()).orElseThrow().size());
		helper.assertTrue(sleeper.getBrain().getMemory(MemoryModuleType.HOME).map(g -> box.isInside(g.pos())).orElse(false),
			"after the reload the villager has no bed in the new house");
		helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "the renewal is still under way");
		helper.succeed();
	}

	/** Cancelled after the take-down: the renewal ends, nothing holds the cleared plot, and a build there can be approved again. */
	//$ gametest_ticks_batch AREA '400' '"renewCancel"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "renewCancel")
	public void aRenewalCancelledAfterTheTakeDownLeavesThePlotFree(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos bed = beds(level, old).get(0);
		OldHouses.House house = measure(helper, v, bed);
		StewardDesk.Proposal proposal = propose(helper, v, house);
		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), proposal.id()) == StewardDesk.Outcome.STARTED, "not started");
		complete(v, site(helper, v));
		BuildSite up = site(helper, v);
		helper.assertTrue(StewardDesk.cancel(level, v.hall(), v.owner(), up.id()), "couldn't cancel the rebuild");
		helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "the renewal outlived its cancelled site");
		helper.assertTrue(StewardDesk.openSites(level, v.hall()).isEmpty(), "a site is still open");
		BoundingBox box = BlueprintOutline.bounds(proposal.placement(), BlueprintLibrary.get(level, proposal.blueprint()).orElseThrow().size());
		helper.assertTrue(StewardSafety.check(level, v.hall(), box, false).isEmpty(), "the plot is refused: " + StewardSafety.check(level, v.hall(), box, false));
		Optional<StewardDesk.Proposal> again = StewardDesk.offer(level, v.hall(), new io.github.jcondedata.aliveworkplace.city.StewardWishes.Wish(
				ResourceLocation.fromNamespaceAndPath("aliveworkplace", "test/renew_again"), new io.github.jcondedata.aliveworkplace.city.StewardRules.Effect(
				io.github.jcondedata.aliveworkplace.city.StewardRules.Kind.BUILD, Optional.of(proposal.blueprint()), Optional.of("homes"), Optional.empty(),
				Optional.empty()), 50, "steward.aliveworkplace.why.renew", List.of()),
			proposal.blueprint(), proposal.placement(), "Homes 1", false);
		helper.assertTrue(again.isPresent(), "the plot can't be proposed again");
		StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), v.owner(), again.get().id());
		helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "a build on the cleared plot wasn't started: " + outcome);
		helper.succeed();
	}

	/**
	 * Run the village: the Steward renews an old house in a renew zone by himself (the take-down starts, nothing waits on
	 * the desk), and no second one is proposed within two days.
	 */
	//$ gametest_ticks_batch AREA '600' '"renewRun"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "renewRun")
	public void runTheVillageRenewsByItselfOneHouseAtATime(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.RUN));
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, new BlockPos(20, 2, 14));
		boolean was = Renewals.ENABLED;
		helper.assertTrue(OldHouses.request(level, v.hall()).isPresent(), "no survey");
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(level, v.hall()).map(h -> h.size() == 2).orElse(false), "the survey hasn't found both houses");
			Renewals.ENABLED = true;
			try {
				StewardDesk.plan(level, v.steward(), v.hall());
				StewardDesk.plan(level, v.steward(), v.hall());
			} finally {
				Renewals.ENABLED = was;
			}
			helper.assertTrue(Renewals.active(level, v.hall()).size() == 1, "renewals under way: " + Renewals.active(level, v.hall()).size());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().stream().noneMatch(StewardDesk.Proposal::isRenewal),
				"a renewal waits on the desk in Run the village");
			helper.assertTrue(BuildSiteManager.get(level).get(Renewals.active(level, v.hall()).get(0).site()).isDeconstruction(), "not taking it down");
		});
	}

	/** A home proposal for a well at {@code at} (relative), filed under its own test rule. */
	private static StewardDesk.Proposal home(GameTestHelper helper, Village v, BlockPos at) {
		ResourceLocation well = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "well");
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(v.level(), v.hall(), new io.github.jcondedata.aliveworkplace.city.StewardWishes.Wish(
				ResourceLocation.fromNamespaceAndPath("aliveworkplace", "test/b86_home"), new io.github.jcondedata.aliveworkplace.city.StewardRules.Effect(
				io.github.jcondedata.aliveworkplace.city.StewardRules.Kind.BUILD, Optional.of(well), Optional.of("homes"), Optional.empty(),
				Optional.empty()), 50, "steward.aliveworkplace.why.renew", List.of()),
			well, new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(io.github.jcondedata.aliveworkplace.mc.Ids.of(v.level().dimension()),
				helper.absolutePos(at), net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE), "Homes 1", false);
		helper.assertTrue(p.isPresent(), "setup: the home wasn't proposed");
		return p.get();
	}

	/**
	 * B86: one build open at a time (as a Hamlet allows), a home proposed before the renewal: when the Steward approves
	 * everything himself the renewal takes the free slot (it comes at most every two days), and the home waits for the next.
	 */
	//$ gametest_ticks_batch AREA '400' '"renewTurn"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "renewTurn")
	public void aRenewalTakesTheOneFreeSlotAheadOfTheDaysHomes(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		StewardDesk.Proposal home = home(helper, v, new BlockPos(22, 2, 16));
		StewardDesk.Proposal renewal = propose(helper, v, measure(helper, v, beds(level, old).get(0)));
		helper.assertTrue(home.id() < renewal.id(), "setup: the home isn't ahead of the renewal on the desk");
		int was = Stewards.MAX_OPEN_BUILDS;
		Stewards.MAX_OPEN_BUILDS = 1;
		try {
			helper.assertTrue(Stewards.maxOpenBuilds(level, v.steward()) == 1, "setup: more than one build may be open");
			StewardDesk.approveAll(level, v.hall(), null);
			helper.assertTrue(Renewals.active(level, v.hall()).size() == 1, "the renewal didn't start ahead of the home; open: "
				+ StewardDesk.openSites(level, v.hall()).stream().map(BuildSite::structure).toList());
			helper.assertTrue(StewardDesk.of(level, v.hall()).get(home.id()).isPresent(), "the home took a second slot");
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).size() == 1, "more than one build open");
			// the take-down and the rebuild done, the slot is free again and the home goes next
			complete(v, site(helper, v));
			complete(v, site(helper, v));
			helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "the renewal is still under way");
			StewardDesk.approveAll(level, v.hall(), null);
			helper.assertTrue(StewardDesk.of(level, v.hall()).get(home.id()).isEmpty(), "the home never got its turn");
		} finally {
			Stewards.MAX_OPEN_BUILDS = was;
		}
		helper.succeed();
	}

	/** B86: an old house past every bench's reach (54 blocks out in the City run) is still renewed, by a builder of the village. */
	//$ gametest_ticks_batch AREA '400' '"renewFar"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "renewFar")
	public void anOldHousePastTheBenchesReachIsStillRenewed(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		BoundingBox old = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		StewardDesk.Proposal renewal = propose(helper, v, measure(helper, v, beds(level, old).get(0)));
		int was = Builders.MAX_SITE_DISTANCE;
		Builders.MAX_SITE_DISTANCE = 4;
		try {
			StewardDesk.Outcome outcome = StewardDesk.approve(level, v.hall(), null, renewal.id());
			helper.assertTrue(outcome == StewardDesk.Outcome.STARTED, "the far renewal wasn't started: " + outcome);
			BuildSite down = site(helper, v);
			helper.assertTrue(v.builder().getUUID().equals(down.builder()), "the village's builder isn't taking it down");
		} finally {
			Builders.MAX_SITE_DISTANCE = was;
		}
		helper.succeed();
	}

	/** Ask me first: the house waits on the desk as a proposal; a zone whose renew switch is off gets none. */
	//$ gametest_ticks_batch AREA '600' '"renewAsk"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "renewAsk")
	public void askMeFirstProposesAndAZoneNotRenewedGetsNothing(GameTestHelper helper) {
		Village v = village(helper, "", true);
		ServerLevel level = v.level();
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		boolean was = Renewals.ENABLED;
		helper.assertTrue(OldHouses.request(level, v.hall()).isPresent(), "no survey");
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(level, v.hall()).map(h -> h.size() == 1).orElse(false), "the survey hasn't found the house");
			Renewals.ENABLED = true;
			try {
				StewardDesk.plan(level, v.steward(), v.hall());
			} finally {
				Renewals.ENABLED = was;
			}
			List<StewardDesk.Proposal> renewals = StewardDesk.of(level, v.hall()).proposals().stream().filter(StewardDesk.Proposal::isRenewal).toList();
			helper.assertTrue(renewals.size() == 1, "renewal proposals: " + renewals.size());
			helper.assertTrue(Renewals.active(level, v.hall()).isEmpty(), "started without asking");
			String title = renewals.get(0).name().getString();
			helper.assertTrue(title.matches("Renew the old house \\d+ blocks [a-z-]+ as a Starter Cottage"), "the proposal reads \"" + title + "\"");
			helper.assertTrue(renewals.get(0).reason().getString().equals("An old village house no builder built, in a zone to renew"),
				"the reason reads \"" + renewals.get(0).reason().getString() + "\"");
			// the switch turned off: the survey has nothing to show, and nothing more is proposed
			v.entity().setPlan(v.entity().plan().editZone(0, "homes", "Homes 1", "", false));
			StewardDesk.decline(level, v.hall(), null, renewals.get(0).id());
			StewardDesk.State state = StewardDesk.of(level, v.hall());
			Renewals.ENABLED = true;
			try {
				StewardDesk.plan(level, v.steward(), v.hall());
			} finally {
				Renewals.ENABLED = was;
			}
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().stream().noneMatch(StewardDesk.Proposal::isRenewal), "proposed with renew off");
		});
	}

	/** Every shipped renewal list loads, its buildings exist, and homes and the armorer's are there. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShippedRenewalListsLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(RenewalLists.homes().map(l -> l.buildings().size() == 3).orElse(false), "no homes list of three buildings");
		helper.assertTrue(RenewalLists.forJob(ResourceLocation.withDefaultNamespace("armorer")).map(l -> l.buildings().get(0).getPath().equals("smithy"))
			.orElse(false), "the armorer's list doesn't start with the Smithy");
		for (RenewalLists.RenewalList list : RenewalLists.all()) {
			for (ResourceLocation b : list.buildings()) {
				helper.assertTrue(BlueprintLibrary.get(level, b).isPresent(), list.name() + " lists " + b + ", which doesn't exist");
			}
		}
		helper.assertTrue(RenewalLists.all().size() >= 30, "only " + RenewalLists.all().size() + " lists loaded");
		helper.succeed();
	}
}
