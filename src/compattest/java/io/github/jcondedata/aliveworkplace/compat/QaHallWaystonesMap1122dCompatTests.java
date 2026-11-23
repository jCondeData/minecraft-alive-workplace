package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA for B1414 (the hall_waystones showcase scene failing nightly), with the real Waystones mod: the scene's own set-up
 * rebuilt as a GameTest, checked against ROADMAP 41.2's text ("the hall's Map shows the waystone network": the player's
 * villages with a waystone, this one first, each with its distance and direction, discovered / not discovered yet / its
 * waystone broken). Three villages of the player's, each with its Waystone Plaza as the builder leaves it, at the scene's
 * offsets: Ashford here (its stone used by the player), Brookmere 140 east and 110 north (not used), Coldwater 180 west
 * (its stone broken). Then the scene's last check: the hall's screen opened from where the scene's first step leaves the
 * player stays open (the scene's "ok" test). Run five times, each in its own batch.
 */
public class QaHallWaystonesMap1122dCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	//$ gametest_batch AREA '"qaHallWaystonesMap1"'
	@GameTest(template = AREA, batch = "qaHallWaystonesMap1")
	public void theScenesNetworkReadsAsTheSpecSays1(GameTestHelper helper) {
		run(helper);
	}

	//$ gametest_batch AREA '"qaHallWaystonesMap2"'
	@GameTest(template = AREA, batch = "qaHallWaystonesMap2")
	public void theScenesNetworkReadsAsTheSpecSays2(GameTestHelper helper) {
		run(helper);
	}

	//$ gametest_batch AREA '"qaHallWaystonesMap3"'
	@GameTest(template = AREA, batch = "qaHallWaystonesMap3")
	public void theScenesNetworkReadsAsTheSpecSays3(GameTestHelper helper) {
		run(helper);
	}

	//$ gametest_batch AREA '"qaHallWaystonesMap4"'
	@GameTest(template = AREA, batch = "qaHallWaystonesMap4")
	public void theScenesNetworkReadsAsTheSpecSays4(GameTestHelper helper) {
		run(helper);
	}

	//$ gametest_batch AREA '"qaHallWaystonesMap5"'
	@GameTest(template = AREA, batch = "qaHallWaystonesMap5")
	public void theScenesNetworkReadsAsTheSpecSays5(GameTestHelper helper) {
		run(helper);
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	/** The scene's waystoneVillage: a hall of the player's, known to the realm, and its plaza as the builder leaves it. */
	private static List<BuildPlan.Step> village(ServerLevel level, ServerPlayer player, BlockPos hall, String name, BlockPos plaza) {
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			throw new GameTestAssertException("setup: no hall at " + hall);
		}
		entity.setCustomName(Component.literal(name));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		if (level.getPoiManager().getType(hall).isEmpty()) { // as the scene does (B534)
			net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(level.getBlockState(hall)).ifPresent(type -> level.getPoiManager().add(hall.immutable(), type));
		}
		VillageHalls.forgetNearest();
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), hall), Component.literal(name),
			Optional.of(player.getUUID()), player.getGameProfile().getName(), "hamlet", 0, 0, level.getGameTime()));
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("setup: no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(level, plaza));
		List<BuildPlan.Step> steps = new ArrayList<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
				steps.add(step);
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(level, plaza), player.getUUID());
		VillageWaystones.round(level, hall);
		return steps;
	}

	private static void run(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // halls and stones earlier batches left nearby
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos ashford = helper.absolutePos(new BlockPos(15, 2, 15));
		BlockPos brookmere = ashford.offset(140, 0, -110);
		BlockPos coldwater = ashford.offset(-180, 0, 10);
		List<BlockPos> halls = List.of(ashford, brookmere, coldwater);
		List<BlockPos> plazas = List.of(ashford.offset(-12, 0, -12), brookmere.offset(-12, 0, -12), coldwater.offset(-12, 0, -12));
		List<BuildPlan.Step> placed = new ArrayList<>();
		try {
			placed.addAll(village(level, player, ashford, "Ashford", plazas.get(0)));
			placed.addAll(village(level, player, brookmere, "Brookmere", plazas.get(1)));
			placed.addAll(village(level, player, coldwater, "Coldwater", plazas.get(2)));
			BlockPos stone = VillageWaystones.stoneOf(level, ashford).orElse(null);
			helper.assertTrue(stone != null && VillageWaystones.stoneOf(level, brookmere).isPresent() && VillageWaystones.stoneOf(level, coldwater).isPresent(),
				"each village raised its waystone: " + VillageWaystones.stoneOf(level, ashford) + ", " + VillageWaystones.stoneOf(level, brookmere)
					+ ", " + VillageWaystones.stoneOf(level, coldwater));
			helper.assertTrue(VillageWaystones.standing(level, ashford), "Ashford's plaza stone stands as a working waystone (" + stone + ")");
			// The player uses Ashford's stone (as the scene's ShowcaseWaystones.discover does); Coldwater's is broken.
			WaystonesAPI.getWaystoneAt(level, stone).ifPresent(w -> WaystonesAPI.activateWaystone(player, w));
			BlockPos cold = VillageWaystones.stoneOf(level, coldwater).orElseThrow();
			level.setBlockAndUpdate(cold.above(), Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(cold, Blocks.AIR.defaultBlockState());

			// The network, as the scene's own check reads it.
			List<VillageWaystones.Village> network = VillageWaystones.network(level, ashford, player);
			helper.assertTrue(network.size() == 3, "three villages on the network: " + network);
			helper.assertTrue(network.get(0).here() && network.get(0).hall().equals(ashford) && network.get(0).state() == VillageWaystones.State.DISCOVERED,
				"Ashford first, here, discovered: " + network);
			helper.assertTrue(network.get(1).hall().equals(brookmere) && network.get(1).state() == VillageWaystones.State.UNDISCOVERED,
				"Brookmere (nearer) second, not discovered yet: " + network);
			helper.assertTrue(network.get(2).hall().equals(coldwater) && network.get(2).state() == VillageWaystones.State.BROKEN,
				"Coldwater third, broken: " + network);

			// The Map's lines, as the player reads them (en_us.json).
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, ashford), LINE);
			helper.assertTrue(lines.size() == 4, "a heading and three lines: " + lines);
			helper.assertTrue(lines.get(0).equals("Waystone network (3):"), "the heading: " + lines);
			helper.assertTrue(lines.get(1).equals("Ashford, this village: discovered"), "Ashford's line: " + lines);
			helper.assertTrue(lines.get(2).startsWith("Brookmere, ") && lines.get(2).contains("north-east") && lines.get(2).endsWith(": not discovered yet"),
				"Brookmere's line: " + lines);
			helper.assertTrue(lines.get(3).startsWith("Coldwater, ") && lines.get(3).contains(" west") && !lines.get(3).contains("north")
				&& !lines.get(3).contains("south") && lines.get(3).endsWith(": its waystone is broken"), "Coldwater's line: " + lines);
			for (String line : lines) {
				helper.assertFalse(line.contains("aliveworkplace."), "a raw key on the Map: " + lines);
			}

			// The scene's last check (its "ok"): the hall's screen opened from where step 01 leaves the player (beside
			// Ashford's stone, +3.5 x, +4.5 z, eyes 1.6 up) is the hall's menu and stays open there.
			Vec3 eye = Vec3.atBottomCenterOf(stone).add(3.5, 1.6, 4.5);
			player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, 0, 0);
			VillageHallScreen.open(player, ashford);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.VILLAGE_HALL_MENU,
				"the hall's screen opened from the scene's camera spot (" + player.position() + "): " + player.containerMenu);
			helper.assertTrue(player.containerMenu.stillValid(player), "the hall's screen stays open at the scene's camera spot ("
				+ player.position() + ", " + Math.sqrt(player.position().distanceToSqr(Vec3.atCenterOf(ashford))) + " blocks from the hall)");
		} finally {
			player.closeContainer();
			for (int i = placed.size() - 1; i >= 0; i--) {
				BuildPlan.Step step = placed.get(i);
				if (step.secondaryPos() != null) {
					level.setBlock(step.secondaryPos(), Blocks.AIR.defaultBlockState(), 3);
				}
				level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), 3);
			}
			RealmData realms = RealmData.get(level.getServer());
			for (int i = 0; i < halls.size(); i++) {
				level.setBlock(halls.get(i), Blocks.AIR.defaultBlockState(), 3);
				BuildSiteManager.get(level).forgetFinished(placement(level, plazas.get(i)));
				VillageWaystones.Data.get(level).forget(halls.get(i));
				realms.forgetKnown(GlobalPos.of(level.dimension(), halls.get(i)));
			}
			VillageHalls.forgetNearest();
		}
		helper.succeed();
	}
}
