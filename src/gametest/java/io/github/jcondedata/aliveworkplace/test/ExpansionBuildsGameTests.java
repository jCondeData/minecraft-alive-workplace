package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP B119: the Blueprint Table offers an expansion's builds (the Arena and the Pokémon job builds of M28, the
 * Guildhall of M30, the answering houses of M32, the luxury workshops of M34) only once that expansion is open, so a
 * player can't build a workplace whose job can't start. The gates are closed for a moment, synchronously
 * ({@link ExpansionGateGameTests#asPlayer}), and reopened after.
 */
public class ExpansionBuildsGameTests implements FabricGameTest {
	private static final BlockPos TABLE = new BlockPos(1, 2, 1);

	/** Every M28 build: the Arena's tiers and every Pokémon job build. */
	private static List<ResourceLocation> m28Builds() {
		return Stream.concat(StarterBlueprints.ARENAS.stream(), StarterBlueprints.JOB_BUILDS.stream()).map(StarterBlueprints.Entry::id).toList();
	}

	/** The builds of the other closed expansions: the Guildhall (M30), the answering houses (M32), the luxury workshops (M34). */
	private static List<ResourceLocation> laterBuilds() {
		return Stream.of(StarterBlueprints.GUILDHALL, StarterBlueprints.GUILDHALL_2, StarterBlueprints.FIRE_STATION, StarterBlueprints.WELL_HOUSE,
			StarterBlueprints.PHYSICIANS_HOUSE, StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.TAILORS_SHOP,
			StarterBlueprints.TAILORS_SHOP_2, StarterBlueprints.PRINT_SHOP, StarterBlueprints.PRINT_SHOP_2, StarterBlueprints.JEWELLERS_WORKSHOP,
			StarterBlueprints.JEWELLERS_WORKSHOP_2, StarterBlueprints.CRAFTERS_WORKSHOP).map(StarterBlueprints.Entry::id).toList();
	}

	private static List<ResourceLocation> table(MinecraftServer server) {
		return TableServer.listing(server).stream().map(e -> e.id()).toList();
	}

	/** With M28 closed the Table holds none of its builds; opened again, the Gem Grotto (its build without Cobblemon) is back. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void tableHoldsNoM28BuildWhileM28IsOff(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertFalse(Expansions.M28, "M28 is complete: this test's closed half no longer applies");
		List<String> problems = new ArrayList<>();
		ExpansionGateGameTests.asPlayer(() -> {
			List<ResourceLocation> closed = table(server);
			m28Builds().stream().filter(closed::contains).forEach(id -> problems.add(id + " is on the Table with M28 off"));
			if (!closed.contains(StarterBlueprints.STARTER_COTTAGE.id()) || !closed.contains(StarterBlueprints.MARKET_STALL.id())) {
				problems.add("a build of no expansion left the Table");
			}
			// Still loads by id: a blueprint already bought and a build site keep working (operators' list too).
			if (BlueprintLibrary.get(server, StarterBlueprints.GEM_GROTTO.id()).isEmpty()
				|| !BlueprintLibrary.list(server, true).contains(StarterBlueprints.GEM_GROTTO.id())) {
				problems.add("the Gem Grotto no longer loads by id with M28 off");
			}
		});
		List<ResourceLocation> open = table(server);
		helper.assertTrue(Expansions.openForTests, "the gates weren't reopened");
		if (!open.contains(StarterBlueprints.GEM_GROTTO.id()) || !open.contains(StarterBlueprints.GEM_GROTTO_2.id())) {
			problems.add("the Gem Grotto isn't on the Table with M28 on");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The other closed expansions' builds (M30, M32, M34) are gated the same way, by their jobs' switches. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void laterExpansionsBuildsWaitForTheirSwitch(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertFalse(Expansions.M30 || Expansions.M32 || Expansions.M34, "M30, M32 or M34 is complete: move its builds out of this test");
		List<String> problems = new ArrayList<>();
		ExpansionGateGameTests.asPlayer(() -> {
			List<ResourceLocation> closed = table(server);
			laterBuilds().stream().filter(closed::contains).forEach(id -> problems.add(id + " is on the Table with its expansion off"));
		});
		List<ResourceLocation> open = table(server);
		laterBuilds().stream().filter(id -> !open.contains(id)).forEach(id -> problems.add(id + " isn't on the Table with its expansion on"));
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Asking the Table for a closed expansion's build by id is refused, and nothing is handed out. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void tableRefusesAClosedExpansionsBuildById(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.CREATIVE);
		BlockPos table = helper.absolutePos(TABLE);
		player.moveTo(new Vec3(table.getX() + 1.5, table.getY(), table.getZ() + 0.5));
		boolean[] took = new boolean[2];
		try {
			ExpansionGateGameTests.asPlayer(() -> took[0] = TableServer.take(player, table, StarterBlueprints.GEM_GROTTO.id()));
			took[1] = TableServer.take(player, table, StarterBlueprints.GEM_GROTTO.id());
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.assertFalse(took[0], "the Table handed out the Gem Grotto with M28 off");
		helper.assertTrue(took[1], "the Table refused the Gem Grotto with M28 on");
		helper.succeed();
	}
}
