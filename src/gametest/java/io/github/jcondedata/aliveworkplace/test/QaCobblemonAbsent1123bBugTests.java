package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (lane B, 2026-11-23, lane-b-1123-0338), BUG TEST (fails on main at the time of writing): ROADMAP 50.12's "the mod
 * absent, nothing changes" and the briefs' "in the Blueprint Table only with Cobblemon". The Table leaves the six
 * Cobblemon families off its listing ({@link QaCobblemonAbsent1123bGameTests}), but a Take request for one by id (the
 * packet a stale or modified client sends) hands the Blueprint out, where the Table already refuses such a request for
 * an expansion's unopened build (B119, {@code TableServer.take}: "Not listed ... a request for it by id is refused").
 */
public class QaCobblemonAbsent1123bBugTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";

	/**
	 * A Table asked for one of them by id (the request a stale or modified client sends, which the Table already refuses
	 * for an expansion's unopened build, B119): no blueprint is handed out. A survival player with Blank Blueprints at a
	 * Table, every level and look asked for in turn.
	 */
	//$ gametest_ticks_batch BIG_AREA '100' '"qa_cobblemon_absent_take_1123b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 100, batch = "qa_cobblemon_absent_take_1123b")
	public void aTableAskedByIdForACobblemonBuildHandsNothingOut(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon"), "Cobblemon is installed in the plain suite");
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(5, 2, 5);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		BlockPos at = helper.absolutePos(table);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
		List<String> handed = new ArrayList<>();
		for (StarterBlueprints.Entry e : QaCobblemonAbsent1123bGameTests.hiddenEntries()) {
			player.getInventory().clearContent();
			player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 4));
			boolean took = TableServer.take(player, at, e.id());
			boolean got = false;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				got |= BlueprintItem.data(player.getInventory().getItem(i)).isPresent();
			}
			if (took || got) {
				handed.add(e.id() + (took ? " (take said yes)" : "") + (got ? " (a Blueprint in the inventory)" : ""));
			}
		}
		// the same Table and player hand out a build the brief lists without Cobblemon, so the refusals above aren't the setup's
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 4));
		helper.assertTrue(TableServer.take(player, at, StarterBlueprints.STARTER_COTTAGE.id()), "(setup) the Table won't hand out a Starter Cottage either");
		helper.assertTrue(handed.isEmpty(), "without Cobblemon the Table handed out " + handed);
		helper.succeed();
	}
}
