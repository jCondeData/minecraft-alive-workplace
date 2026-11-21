package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWork;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1121-2153), from B1417 and B1429 (the showcase scene {@code steward_homes}: "the Steward started a home
 * build himself", "home builds finished") and ROADMAP 27.10's "Villagers without a bed" rule. The village is staged as
 * in {@link QaStewardHomes1121cGameTests} (a hall, a Homes zone east of it, a Builder at his table, a seasoned Steward
 * with the City Plan, Run the village chosen on the desk), but with what that class doesn't cover: the home the Steward
 * starts is really built to the end; it survives a save of the sites; and the rule from both sides (a bed for everyone:
 * no home starts; beds there but 3 short: a home starts). Stray villagers and beds an earlier batch left
 * within the village's reach (B1429's cause) are cleared first, so the bed count is this test's own.
 */
public class QaStewardHomes1121c2GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final List<String> HOMES = List.of("starter_cottage", "stone_house", "terrace", "townhouse", "house_t1_b");

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, long morning) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static void log(String line) {
		AliveWorkplace.LOG.info("[qa-steward-homes-1121c2] {}", line);
	}

	private static Village village(GameTestHelper helper, VillageRanks.Rank rank, int homeless) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
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
				for (int y = 2; y < 28; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		for (BlockPos bed : VillageHalls.beds(level, hall).toList()) {
			level.setBlock(bed, Blocks.AIR.defaultBlockState(), 3);
		}
		// Villagers an earlier batch left within the village's reach would count as this village's people.
		for (Villager stray : level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(VillageHalls.RADIUS + 2))) {
			stray.discard();
		}
		Leftovers.after(helper, () -> {
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
			level.removeBlock(hall, false);
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		entity.setRank(rank);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		for (int i = 0; i < homeless; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(1 + i, 2, 27)).setNoAi(true);
		}
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		long morning = level.getDayTime() / 24000L * 24000L + 1000;
		level.setDayTime(morning);
		helper.assertTrue(StewardDesk.setMode(level, hall, owner, CityPlan.Mode.RUN), "Run the village refused for the owner");
		return new Village(level, hall, owner, steward, morning);
	}

	private static boolean home(ResourceLocation id) {
		String path = BlueprintLooks.base(id).getPath();
		return HOMES.stream().anyMatch(path::startsWith);
	}

	private static Optional<BuildSite> homeSite(Village v) {
		return StewardDesk.openSites(v.level(), v.hall()).stream().filter(s -> home(s.structure())).findFirst();
	}

	private static String what(Village v) {
		return "open " + StewardDesk.openSites(v.level(), v.hall()).stream().map(s -> s.structure() + " " + s.stage() + "/" + s.status()).toList()
			+ ", desk " + StewardDesk.of(v.level(), v.hall()).proposals().stream().map(p -> p.blueprint().toString()).toList()
			+ ", mode " + StewardDesk.mode(v.level(), v.hall());
	}

	/** The Steward plans at his hall every half second, as his work does. */
	private static void planning(GameTestHelper helper, Village v) {
		int[] t = {0};
		helper.onEachTick(() -> {
			if (t[0]++ % 10 == 0) {
				StewardWork.PLANNER.plan(v.level(), v.steward(), v.hall());
			}
		});
	}

	private static void bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void unbed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.AIR);
		helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
	}

	/**
	 * The scene's promise: in Run the village the home the Steward starts himself is built to the end (free materials,
	 * the clock held in the builder's working hours): the site closes and is recorded as a finished build of that hall.
	 */
	//$ gametest_ticks_batch AREA '16000' '"qaStewardHomes1121c2Finish"'
	@GameTest(template = AREA, timeoutTicks = 16000, batch = "qaStewardHomes1121c2Finish")
	public void runTheVillageFinishesTheHomeItStarted(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.HAMLET, 2);
		var free = v.level().getGameRules().getRule(ModGameRules.FREE_MATERIALS);
		boolean before = free.get();
		free.set(true, v.level().getServer());
		Leftovers.after(helper, () -> free.set(before, v.level().getServer()));
		planning(helper, v);
		UUID[] id = {null};
		BuildSite[] started = {null};
		helper.onEachTick(() -> {
			if (helper.getTick() % 20 == 0) {
				v.level().setDayTime(v.morning() + 1000); // the builder's working hours (1000 to dusk)
			}
			if (id[0] == null) {
				homeSite(v).ifPresent(s -> {
					id[0] = s.id();
					started[0] = s;
					log("home started at tick " + helper.getTick() + ": " + s.structure() + " at " + helper.relativePos(s.placement().origin()));
				});
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(id[0] != null, "no home build started by the Steward: " + what(v));
			BuildSite s = started[0];
			helper.assertTrue(BuildSiteManager.get(v.level()).get(id[0]) == null,
				"the home " + s.structure() + " isn't finished: stage " + s.stage() + ", status " + s.status() + ", skipped " + s.skipped() + "; " + what(v));
			boolean recorded = BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall()).stream()
				.anyMatch(f -> f.placement().origin().equals(s.placement().origin()) && home(f.structure()));
			helper.assertTrue(recorded, "the home " + s.structure() + " closed but isn't recorded as a finished build of the village");
			helper.assertTrue(s.plan(v.level()).unfinished(v.level()).isEmpty(), "the home closed with blocks left: " + s.plan(v.level()).unfinished(v.level()).size());
			log("home " + s.structure() + " finished by tick " + helper.getTick() + ", skipped " + s.skipped());
		});
	}

	/** The home the Steward started survives a save and load of the sites: still open, still the hall's, same blueprint. */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121c2Reload"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121c2Reload")
	public void theStartedHomeSurvivesASave(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.VILLAGE, 3);
		planning(helper, v);
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			Optional<BuildSite> site = homeSite(v);
			if (site.isEmpty()) {
				return;
			}
			done[0] = true;
			UUID id = site.get().id();
			ResourceLocation structure = site.get().structure();
			BlockPos origin = site.get().placement().origin();
			HolderLookup.Provider registries = v.level().registryAccess();
			v.level().getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(v.level()).save(new CompoundTag(), registries), registries));
			BuildSite back = BuildSiteManager.get(v.level()).get(id);
			helper.assertTrue(back != null, "the Steward's home " + structure + " was lost in the save");
			helper.assertTrue(back.structure().equals(structure) && back.placement().origin().equals(origin),
				"the home came back as " + back.structure() + " at " + back.placement().origin());
			helper.assertTrue(StewardDesk.openSites(v.level(), v.hall()).stream().anyMatch(s -> s.id().equals(id)),
				"after the save the home is no longer one of the hall's open builds: " + what(v));
			log("home " + structure + " kept through a save of the sites");
			helper.succeed();
		});
		helper.runAfterDelay(590, () -> helper.assertTrue(done[0], "no home build started by the Steward: " + what(v)));
	}

	/** A bed for everyone (8 beds for the 5 villagers): in Run the village no home starts in 300 ticks. */
	//$ gametest_ticks_batch AREA '400' '"qaStewardHomes1121c2Beds"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaStewardHomes1121c2Beds")
	public void aBedForEveryoneStartsNoHome(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.VILLAGE, 3);
		for (int i = 0; i < 8; i++) {
			bed(helper, 6 + 2 * i, 27);
		}
		planning(helper, v);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(homeSite(v).isEmpty(), "a home started with a bed for every villager: " + what(v));
			log("8 beds, 5 villagers: no home after 300 ticks (" + what(v) + ")");
			helper.succeed();
		});
	}

	/** The other side: beds there, but only 2 for the 5 villagers (3 short): a home starts in the Homes zone. */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121c2FewBeds"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121c2FewBeds")
	public void twoBedsForFiveVillagersStillStartsAHome(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.VILLAGE, 3);
		bed(helper, 6, 27);
		bed(helper, 8, 27);
		planning(helper, v);
		helper.succeedWhen(() -> {
			Optional<BuildSite> site = homeSite(v);
			helper.assertTrue(site.isPresent(), "2 beds for 5 villagers, and no home started: " + what(v));
			helper.assertTrue(v.entity().plan().zoneAt(v.hall(), site.get().placement().origin()).map(z -> z.kind().equals("homes")).orElse(false),
				"the home isn't in the Homes zone: " + site.get().placement().origin());
			log("2 beds, 5 villagers: " + what(v));
		});
	}

	/**
	 * B1429's cause staged on purpose: beds an earlier batch left outside the test area but within the village's reach
	 * (where the probe found them: one at (-3, 3, 27), four at (-49..-43, 8, 21)). QaStewardHomes1121cGameTests' Run the
	 * village test must still start its home (its village() clears them).
	 */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121c2StrayBeds"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121c2StrayBeds")
	public void strayBedsWithinReachDontStopTheRunVillageTest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> heads = new ArrayList<>();
		heads.add(helper.absolutePos(new BlockPos(-3, 3, 27)));
		for (int x = -49; x <= -43; x += 2) {
			heads.add(helper.absolutePos(new BlockPos(x, 8, 21)));
		}
		for (BlockPos head : heads) {
			level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(head.north().below(), Blocks.STONE.defaultBlockState(), 3);
			level.setBlock(head.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 3);
			level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD), 3);
		}
		long planted = heads.stream().filter(h -> level.getBlockState(h).is(Blocks.RED_BED)).count();
		helper.assertTrue(planted == heads.size(), "only " + planted + " stray beds planted");
		Leftovers.after(helper, () -> heads.forEach(h -> {
			level.setBlock(h, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(h.north(), Blocks.AIR.defaultBlockState(), 3);
		}));
		log(planted + " stray beds planted within the village's reach");
		new QaStewardHomes1121cGameTests().runTheVillageStartsAHomeInTheHomesZone(helper);
	}

	/**
	 * Config {@code stewardSelfRun} off (docs/wiki/steward.md: "Run the village asks first"): turned off in a village set
	 * to Run the village, the home is only proposed and nothing starts; Run the village can't be chosen again.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121c2SelfRunOff"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121c2SelfRunOff")
	public void selfRunOffRunTheVillageOnlyProposesTheHome(GameTestHelper helper) {
		Village v = village(helper, VillageRanks.Rank.VILLAGE, 3); // set to Run the village while the switch was on
		boolean was = StewardDesk.SELF_RUN;
		StewardDesk.SELF_RUN = false;
		Leftovers.after(helper, () -> StewardDesk.SELF_RUN = was);
		helper.assertFalse(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run the village could be chosen with stewardSelfRun off");
		planning(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(StewardDesk.openSites(v.level(), v.hall()).isEmpty(), "a build started in Run the village with stewardSelfRun off: " + what(v));
			boolean proposed = StewardDesk.of(v.level(), v.hall()).proposals().stream().anyMatch(p -> home(p.blueprint()));
			helper.assertTrue(proposed, "no home proposed with stewardSelfRun off: " + what(v));
			log("stewardSelfRun off: " + what(v));
		});
	}
}
