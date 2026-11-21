package io.github.jcondedata.aliveworkplace.test;

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
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1121-0653), from ROADMAP 27.8 and 27.10 (showcase scene {@code steward_homes} failing since 2026-11-19): a
 * Village with no beds, a Homes zone painted east of its hall, a Builder at his table and a seasoned Steward appointed
 * with the City Plan. With the mode set to Run the village through the desk, the Steward's own planning (the planner his
 * work calls at the hall) starts a home build by itself, in the Homes zone, even with no materials in store. In Ask me
 * first he only proposes it; with a Homes zone too small for any home nothing starts.
 */
public class QaStewardHomes1121cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	/** The home blueprints of 27.10's rules (any tier, any look). */
	private static final List<String> HOMES = List.of("starter_cottage", "stone_house", "terrace", "townhouse");

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** The village, its Homes zone x 6..maxX, z 2..maxZ of the test area (relative), and {@code homeless} villagers more. */
	private static Village village(GameTestHelper helper, int maxX, int maxZ, int homeless) {
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
				for (int y = 2; y < 20; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		// B1429: the village reaches 64 blocks round the hall, far past this area, and beds an earlier batch left there
		// (in the full suite) were counted as this village's: nobody was homeless, no home was wished for. No beds here.
		for (BlockPos bed : io.github.jcondedata.aliveworkplace.hall.VillageHalls.beds(level, hall).toList()) {
			level.setBlock(bed, Blocks.AIR.defaultBlockState(), 3);
		}
		Leftovers.after(helper, () -> {
			for (BuildSite site : new ArrayList<>(manager.all())) {
				if (near.isInside(site.placement().origin())) {
					manager.remove(site.id());
				}
			}
			level.removeBlock(hall, false);
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.VILLAGE);
		BitSet cells = new BitSet();
		for (int x = 6; x <= maxX; x++) {
			for (int z = 2; z <= maxZ; z++) {
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
		level.setDayTime(level.getDayTime() / 24000L * 24000L + 1000); // morning
		return new Village(level, hall, owner, steward);
	}

	private static boolean home(BuildSite site) {
		String path = BlueprintLooks.base(site.structure()).getPath();
		return HOMES.stream().anyMatch(path::startsWith);
	}

	private static Optional<BuildSite> homeSite(Village v) {
		return StewardDesk.openSites(v.level(), v.hall()).stream().filter(QaStewardHomes1121cGameTests::home).findFirst();
	}

	private static String what(Village v) {
		return "open " + StewardDesk.openSites(v.level(), v.hall()).stream().map(s -> s.structure().toString()).toList()
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

	/** Run the village, chosen on the desk: the Steward starts a home build himself, inside the Homes zone. */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121cRun"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121cRun")
	public void runTheVillageStartsAHomeInTheHomesZone(GameTestHelper helper) {
		Village v = village(helper, 29, 25, 3);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run the village refused for the owner");
		planning(helper, v);
		helper.succeedWhen(() -> {
			Optional<BuildSite> site = homeSite(v);
			helper.assertTrue(site.isPresent(), "no home build started by the Steward: " + what(v));
			Optional<CityPlan.Zone> zone = v.entity().plan().zoneAt(v.hall(), site.get().placement().origin());
			helper.assertTrue(zone.map(z -> z.kind().equals("homes")).orElse(false),
				"the home " + site.get().structure() + " at " + site.get().placement().origin() + " isn't in the Homes zone: " + zone);
		});
	}

	/** No free materials and nothing in store: Run the village still starts the home (the builder waits for materials). */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121cNoMaterials"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121cNoMaterials")
	public void runTheVillageStartsAHomeWithNoMaterials(GameTestHelper helper) {
		Village v = village(helper, 29, 25, 3);
		var rule = v.level().getGameRules().getRule(ModGameRules.FREE_MATERIALS);
		boolean before = rule.get();
		rule.set(false, v.level().getServer());
		Leftovers.after(helper, () -> rule.set(before, v.level().getServer()));
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run the village refused for the owner");
		planning(helper, v);
		helper.succeedWhen(() -> helper.assertTrue(homeSite(v).isPresent(), "no home build started without materials: " + what(v)));
	}

	/** Ask me first: the home is proposed on the desk, and nothing starts without the owner's click. */
	//$ gametest_ticks_batch AREA '600' '"qaStewardHomes1121cAsk"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStewardHomes1121cAsk")
	public void askMeFirstOnlyProposesTheHome(GameTestHelper helper) {
		Village v = village(helper, 29, 25, 3);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.ASK), "Ask me first refused");
		planning(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(StewardDesk.openSites(v.level(), v.hall()).isEmpty(), "a build started in Ask me first: " + what(v));
			boolean proposed = StewardDesk.of(v.level(), v.hall()).proposals().stream()
				.anyMatch(p -> HOMES.stream().anyMatch(BlueprintLooks.base(p.blueprint()).getPath()::startsWith));
			helper.assertTrue(proposed, "no home proposed: " + what(v));
		});
	}

	/** A Homes zone of 5 by 5 fits no home: in Run the village nothing starts (and nothing outside the zone). */
	//$ gametest_ticks_batch AREA '400' '"qaStewardHomes1121cTiny"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaStewardHomes1121cTiny")
	public void aHomesZoneTooSmallStartsNoHome(GameTestHelper helper) {
		Village v = village(helper, 10, 6, 3);
		helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run the village refused for the owner");
		planning(helper, v);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(homeSite(v).isEmpty(), "a home started in a zone too small for it: " + what(v));
			helper.succeed();
		});
	}
}
