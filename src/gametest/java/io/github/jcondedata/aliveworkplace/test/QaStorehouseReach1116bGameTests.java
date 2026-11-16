package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane b, 2026-11-16, session qa-lane-b-1116-1538) of the village's reach for a Builder's Storehouse, from the spec,
 * not the code: wiki builder.md "A Storehouse with its own table (B297, B298, B299)": "When the status says Needs, it
 * also names the nearest Storehouse he doesn't count and why: more than the village's 48 blocks from his table (the
 * distance it gives is rounded up, so one just past the reach reads past it: 48.3 blocks says 49, B355), someone else's,
 * or no chest round it he can read"; "Whichever table (B290)": a sneak-click on another Blueprint Table moves the
 * Builder and his build there; en_us.json's status lines, rendered and compared word for word.
 *
 * The huge test area is 30 blocks a side, too small for the default 48-block village, so the village here reaches 24
 * (the `villageRadius` setting, which is what the 48 is): the same comparison, both sides of its edge. The table is at
 * (2, 2, 2). Earlier QA covered 20 in / 20.4 out by key and numbers (QaBuilderStockGameTests, QaBuilderStockBugTests)
 * and B297GameTests the three reasons by key only; these add the exact sentences, a whole build from a Storehouse at
 * the edge, a whole block past it, a Storehouse coming into reach mid-job (the table moved, the village grown) and a
 * restart while he waits on one past the reach.
 */
public class QaStorehouseReach1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int REACH = 24;
	private static final BlockPos TABLE = QaBuilderStockGameTests.TABLE_CHEST.north();
	private static final UUID STRANGER = UUID.fromString("5f1b2c3d-0000-4000-8000-0000001116b2");

	private static void log(GameTestHelper helper, String what) {
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa-reach-1116b] " + what));
	}

	/** The village reaching {@link #REACH}, Jesse's Storehouse at {@code storehouse} with the hut's cobblestone and door, the table's chest with the rest. */
	private static ServerLevel setup(GameTestHelper helper, BlockPos storehouse, boolean tableStock) {
		ServerLevel level = QaBuilderStockGameTests.setup(helper, storehouse, 25, 1);
		Leftovers.village(helper, REACH);
		if (tableStock) {
			QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		}
		return level;
	}

	private static QaBuilderStockGameTests.Builder builder(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		UUID id = b.site().id();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(id) != null) {
				BuildSiteManager.get(level).remove(id);
			}
		});
		return b;
	}

	/** Cobblestone in the two chests QaBuilderStockGameTests.setup puts by {@code storehouse} (east and south). */
	private static int cobbleAt(GameTestHelper helper, BlockPos storehouse) {
		int n = 0;
		for (BlockPos p : List.of(storehouse.east(), storehouse.south())) {
			if (helper.getBlockEntity(p) instanceof Container c) {
				n += c.countItem(Items.COBBLESTONE);
			}
		}
		return n;
	}

	private static String at(GameTestHelper helper, BlockPos rel) {
		BlockPos p = helper.absolutePos(rel);
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	/** The status's lines as a player reads them (translated, trimmed). */
	private static List<String> lines(ServerLevel level, BuildSite site, Villager villager) {
		return Builders.statusText(level, site, villager).getString().lines().map(String::trim).toList();
	}

	private static List<String> storeLines(List<String> lines) {
		return lines.stream().filter(l -> l.startsWith("Not counting the Storehouse")).toList();
	}

	private static void assertSays(GameTestHelper helper, List<String> lines, String expected) {
		helper.assertTrue(lines.contains(expected), "the status doesn't say \"" + expected + "\"; it says " + lines);
		helper.assertTrue(storeLines(lines).size() == 1, "the status names more than one Storehouse: " + storeLines(lines));
	}

	private static void assertBuilt(GameTestHelper helper, ServerLevel level, QaBuilderStockGameTests.Builder b, Villager now) {
		BuildSite live = BuildSiteManager.get(level).get(b.site().id());
		helper.assertTrue(live == null, "still building: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()
			+ " bench " + live.bench() + " builder's bench " + Builders.benchPos(now)));
		List<BlockPos> wrong = b.plan().unfinished(level);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not as drawn");
	}

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	// ------------------------------------------------------------------------------------------------------ the tests

	/**
	 * A Storehouse exactly at the reach (24 blocks straight along x, a 24-block village) is counted. The hut's door is
	 * nowhere at first, so the status says "Needs" the door only (the cobblestone counted) and names no Storehouse he
	 * doesn't count (the one at the edge he does). The door put in its chest, he builds the whole hut taking all its
	 * cobblestone.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaReach1116bEdge"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaReach1116bEdge")
	public void aStorehouseExactlyAtTheReachIsCountedAndBuildsTheHut(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(2 + REACH, 2, 2);
		ServerLevel level = setup(helper, storehouse, true);
		((Container) helper.getBlockEntity(storehouse.south())).clearContent(); // no door anywhere yet
		QaBuilderStockGameTests.Builder b = builder(helper);
		List<String> lines = lines(level, b.site(), b.villager());
		helper.assertTrue(b.site().missing().keySet().equals(java.util.Set.of(Items.OAK_DOOR)),
			"a Storehouse exactly 24 blocks off in a 24-block village isn't counted, or the door isn't missing: needs " + b.site().missing());
		helper.assertTrue(storeLines(lines).isEmpty(), "the status says a Storehouse at the reach isn't counted: " + lines);
		((Container) helper.getBlockEntity(storehouse.south())).setItem(0, new ItemStack(Items.OAK_DOOR));
		helper.succeedWhen(() -> {
			assertBuilt(helper, level, b, b.villager());
			helper.assertTrue(cobbleAt(helper, storehouse) == 0, "the Storehouse at the edge still holds " + cobbleAt(helper, storehouse) + " cobblestone");
			log(helper, "storehouse at 24 of 24: counted, needs only the door with no Not counting line, hut built, its 25 cobblestone used");
		});
	}

	/**
	 * One whole block past the reach (25 blocks along x): not counted, never taken, and the status says exactly
	 * "Not counting the Storehouse at X Y Z: it is 25 blocks from his table, and a village reaches 24".
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaReach1116bPast"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaReach1116bPast")
	public void aStorehouseOneBlockPastTheReachIsNamedWithItsDistance(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(2 + REACH + 1, 2, 2);
		ServerLevel level = setup(helper, storehouse, true);
		QaBuilderStockGameTests.Builder b = builder(helper);
		Builders.refreshMissing(level, b.site(), b.villager()); // what the status card and status count
		helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) > 0, "a Storehouse 25 blocks off in a 24-block village was counted: missing " + b.site().missing());
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": it is 25 blocks from his table, and a village reaches 24";
		assertSays(helper, lines(level, b.site(), b.villager()), expected);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(cobbleAt(helper, storehouse) == 25, "a Storehouse past the reach was taken from: " + cobbleAt(helper, storehouse) + " of 25 left");
			assertSays(helper, lines(level, b.site(), b.villager()), expected);
			log(helper, "storehouse at 25 of 24: not counted, untouched after 400 ticks, says: " + expected);
			helper.succeed();
		});
	}

	/**
	 * A fraction past the reach (24.08 blocks: 24 along x, 2 along z): not counted, and the distance is rounded up, so the
	 * status reads 25, never "24 blocks ... a village reaches 24" (B355).
	 */
	//$ gametest_batch HUGE '"qaReach1116bFraction"'
	@GameTest(template = HUGE, batch = "qaReach1116bFraction")
	public void aStorehouseAFractionPastTheReachReadsRoundedUp(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(2 + REACH, 2, 4);
		ServerLevel level = setup(helper, storehouse, true);
		QaBuilderStockGameTests.Builder b = builder(helper);
		Builders.refreshMissing(level, b.site(), b.villager()); // what the status card and status count
		helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) > 0, "a Storehouse 24.08 blocks off in a 24-block village was counted: missing " + b.site().missing());
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": it is 25 blocks from his table, and a village reaches 24";
		assertSays(helper, lines(level, b.site(), b.villager()), expected);
		log(helper, "storehouse at 24.08 of 24: says " + expected);
		helper.succeed();
	}

	/**
	 * A stranger's Storehouse well inside the reach (17 blocks), its chests full: not counted, never taken, and the
	 * status says "Not counting the Storehouse at X Y Z: it isn't his village's (it belongs to Stranger)".
	 */
	//$ gametest_ticks_batch HUGE '600' '"qaReach1116bStranger"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaReach1116bStranger")
	public void aStrangersStorehouseInReachIsNamedWithItsOwner(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(14, 2, 14);
		ServerLevel level = setup(helper, storehouse, true);
		((StorehouseBlockEntity) helper.getBlockEntity(storehouse)).setOwner(STRANGER, "Stranger");
		QaBuilderStockGameTests.Builder b = builder(helper);
		Builders.refreshMissing(level, b.site(), b.villager()); // what the status card and status count
		helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) > 0, "a stranger's Storehouse was counted: missing " + b.site().missing());
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": it isn't his village's (it belongs to Stranger)";
		assertSays(helper, lines(level, b.site(), b.villager()), expected);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(cobbleAt(helper, storehouse) == 25, "a stranger's Storehouse was taken from: " + cobbleAt(helper, storehouse) + " of 25 left");
			log(helper, "stranger's storehouse in reach: untouched after 400 ticks, says: " + expected);
			helper.succeed();
		});
	}

	/**
	 * A Storehouse in reach whose only chest stands 9 blocks from it (out of a Storehouse's chest reach; and nowhere near
	 * the table): the status says "...: no chest within 8 blocks of it that he can read". A chest put 8 blocks off then
	 * (at its edge) holding the cobblestone is counted, and the line goes.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaReach1116bNoChest"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaReach1116bNoChest")
	public void aStorehouseWithNoChestInItsReachIsNamedThenCountedWhenOneIsPlaced(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(14, 2, 14);
		ServerLevel level = setup(helper, storehouse, true);
		for (BlockPos p : List.of(storehouse.east(), storehouse.south())) {
			((Container) helper.getBlockEntity(p)).clearContent();
			helper.setBlock(p, Blocks.AIR);
		}
		BlockPos tooFar = storehouse.east(9);
		helper.setBlock(tooFar, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(tooFar), new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_DOOR));
		QaBuilderStockGameTests.Builder b = builder(helper);
		Builders.refreshMissing(level, b.site(), b.villager()); // what the status card and status count
		helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) > 0, "a chest 9 blocks from the Storehouse was counted: missing " + b.site().missing());
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": no chest within 8 blocks of it that he can read";
		assertSays(helper, lines(level, b.site(), b.villager()), expected);
		BlockPos edge = storehouse.west(8);
		helper.setBlock(edge, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(edge), new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_DOOR));
		helper.runAfterDelay(40, () -> {
			Village.forget(b.villager());
			List<String> after = lines(level, b.site(), b.villager());
			helper.assertTrue(storeLines(after).isEmpty(), "a chest 8 blocks from the Storehouse didn't make it counted: " + after);
			helper.assertTrue(b.site().missing().getOrDefault(Items.COBBLESTONE, 0) == 0, "the chest 8 blocks from the Storehouse wasn't counted: missing " + b.site().missing());
			log(helper, "storehouse with its only chest 9 off: says " + expected + "; a chest 8 off is counted");
			helper.succeed();
		});
	}

	/**
	 * Every material of the hut in a Storehouse 31.6 blocks from his table (past the 24 reach), the table's chest empty:
	 * he waits, naming it. The player sneak-clicks a second Blueprint Table 15.2 blocks from the Storehouse with an
	 * empty hand by the builder: he moves there, counts the Storehouse, and builds the whole hut from it.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaReach1116bMoved"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaReach1116bMoved")
	public void aStorehouseComingIntoReachWhenTheTableMovesFeedsTheWholeBuild(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(28, 2, 20);
		ServerLevel level = setup(helper, storehouse, false);
		((Container) helper.getBlockEntity(storehouse.east())).setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		((Container) helper.getBlockEntity(storehouse.east())).setItem(2, new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = builder(helper);
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": it is 32 blocks from his table, and a village reaches 24";
		assertSays(helper, lines(level, b.site(), b.villager()), expected);
		BlockPos second = new BlockPos(14, 2, 14);
		helper.setBlock(second, ModBlocks.BLUEPRINT_TABLE);
		BlockPos secondAbs = helper.absolutePos(second);
		ensurePoi(level, secondAbs);
		helper.runAfterDelay(200, () -> {
			BuildSite live = BuildSiteManager.get(level).get(b.site().id());
			helper.assertTrue(live != null && !live.missing().isEmpty(), "he didn't wait for the Storehouse past the reach: " + (live == null ? "built" : live.missing()));
			helper.assertTrue(cobbleAt(helper, storehouse) == 25, "he took from the Storehouse past the reach before the move");
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			// the player is Jesse's friend, so he may direct Jesse's builder (wiki: a hired builder takes orders from his employer and friends)
			io.github.jcondedata.aliveworkplace.build.Friends friends = io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer());
			UUID jesse = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.get(b.villager()).id();
			friends.add(jesse, player.getUUID(), player.getGameProfile().getName());
			Leftovers.after(helper, () -> friends.remove(jesse, player.getUUID()));
			Vec3 by = b.villager().position();
			player.moveTo(by.x, by.y, by.z);
			player.setShiftKeyDown(true);
			level.getBlockState(secondAbs).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(secondAbs), Direction.UP, secondAbs, false));
			helper.assertTrue(Builders.benchPos(b.villager()).filter(secondAbs::equals).isPresent(), "the sneak-click didn't move him: he works at " + Builders.benchPos(b.villager()));
			Village.forget(b.villager());
			List<String> moved = lines(level, live, b.villager());
			helper.assertTrue(storeLines(moved).isEmpty(), "after the move the Storehouse 15 blocks off still isn't counted: " + moved);
			helper.succeedWhen(() -> {
				assertBuilt(helper, level, b, b.villager());
				helper.assertTrue(cobbleAt(helper, storehouse) == 0, "the Storehouse still holds " + cobbleAt(helper, storehouse) + " cobblestone");
				log(helper, "storehouse 32 off: waited and named; table moved to 15 off: counted, hut built from it");
			});
		});
	}

	/**
	 * Waiting on a Storehouse one block past the reach, the server restarts (every build site and the builder saved and
	 * loaded): the site keeps its stage, progress and what it is short of, the status still names the Storehouse the same
	 * way, and nothing is taken from it. Then the village grows to 25: the Storehouse is in reach and he finishes.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaReach1116bReload"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaReach1116bReload")
	public void aRestartWhileWaitingOnAStorehousePastTheReachKeepsTheSiteThenItFinishesWhenTheVillageGrows(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(2 + REACH + 1, 2, 2);
		ServerLevel level = setup(helper, storehouse, true);
		QaBuilderStockGameTests.Builder b = builder(helper);
		Villager[] who = {b.villager()};
		String expected = "Not counting the Storehouse at " + at(helper, storehouse) + ": it is 25 blocks from his table, and a village reaches 24";
		helper.runAfterDelay(400, () -> {
			BuildSite before = BuildSiteManager.get(level).get(b.site().id());
			helper.assertTrue(before != null && before.missing().getOrDefault(Items.COBBLESTONE, 0) > 0, "he isn't waiting for cobblestone: " + (before == null ? "built" : before.missing()));
			BuildPlan.Stage stage = before.stage();
			int placed = before.placed();
			String missing = before.missing().toString();
			assertSays(helper, lines(level, before, who[0]), expected);
			// the restart
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(who[0].save(tag), "the builder wasn't saved");
			who[0].discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
			level.addFreshEntity(loaded);
			who[0] = (Villager) loaded;
			Village.forget(who[0]);
			BuildSite after = BuildSiteManager.get(level).get(b.site().id());
			helper.assertTrue(after != null, "the restart lost the site");
			helper.assertTrue(after.stage() == stage && after.placed() == placed, "the restart moved the site from " + stage + " placed " + placed
				+ " to " + after.stage() + " placed " + after.placed());
			List<String> said = lines(level, after, who[0]);
			helper.assertTrue(after.missing().toString().equals(missing), "the restart changed what he is short of from " + missing + " to " + after.missing());
			assertSays(helper, said, expected);
			log(helper, "restarted waiting at " + stage + " placed " + placed + " short of " + missing + "; still says " + expected);
			helper.runAfterDelay(200, () -> {
				helper.assertTrue(cobbleAt(helper, storehouse) == 25, "a Storehouse past the reach was taken from after the restart");
				BuildSite still = BuildSiteManager.get(level).get(b.site().id());
				helper.assertTrue(still != null && still.placed() >= placed, "progress dropped after the restart: " + (still == null ? "gone" : still.placed()) + " < " + placed);
				Leftovers.village(helper, REACH + 1);
				Village.forget(who[0]);
				helper.succeedWhen(() -> {
					assertBuilt(helper, level, b, who[0]);
					helper.assertTrue(cobbleAt(helper, storehouse) == 0, "the Storehouse still holds " + cobbleAt(helper, storehouse) + " cobblestone");
					log(helper, "the village grown to 25: hut built from the Storehouse 25 off");
				});
			});
		});
	}
}
