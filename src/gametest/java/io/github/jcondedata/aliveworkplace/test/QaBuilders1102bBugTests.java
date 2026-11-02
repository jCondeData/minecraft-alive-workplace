package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-11-02, builders and builds) bug tests: each fails because the mod is wrong, from the spec. Uncommitted:
 * the builder commits each with its fix.
 * <ul>
 *   <li>B434/B536 (builder.md "Builders never copy ... what a block holds in its look"; QaScanLooks1027GameTests "the
 *   material list asks for each piece, so breaking the new build gives back no more than was paid").</li>
 *   <li>docs/guide/scan-tool.md: "The Scan Tool turns it into a blueprint, so the village's builders can build it
 *   again."</li>
 * </ul>
 */
public class QaBuilders1102bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);
	/** Ticks the builder may wait for a plain item before the wait counts as a refusal of the special one. */
	private static final int REFUSED_AFTER = 400;

	/**
	 * B434/B536 kin (builder.md "Builders never copy ... what a block holds in its look"; QaScanLooks1027 "breaking the
	 * new build gives back no more than was paid"): a glow lichen on three faces (three lichen when sheared) is scanned
	 * and built again without free materials from a chest holding exactly the material list (and dirt). The built
	 * lichen may cover no more faces than the lichen the list asked for.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bLichenFaces"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bLichenFaces")
	public void aScannedThreeFacedGlowLichenIsNotBuiltFromOneLichen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lichen = SRC.above();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(lichen.north(), Blocks.STONE);
		helper.setBlock(lichen.east(), Blocks.STONE);
		helper.setBlock(lichen, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true)
			.setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.EAST, true));
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC.north(), lichen.east());
		for (BlockPos p : List.of(lichen, lichen.north(), lichen.east(), SRC)) {
			helper.setBlock(p, Blocks.AIR);
		}
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		chest.setItem(slot, new ItemStack(Items.DIRT, 64));
		int paid = asked.getOrDefault(Items.GLOW_LICHEN, 0);
		// The scan's box starts one block north of the stone, so the lichen is at (0, 1, 1) of the blueprint
		BlockPos builtLichen = b.origin().offset(0, 1, 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
				+ " missing " + b.site().missing());
			BlockState there = level.getBlockState(builtLichen);
			helper.assertTrue(there.is(Blocks.GLOW_LICHEN), "setup: no lichen was built at the lichen's spot: " + there + " (" + b.site().skipped() + " skipped)");
			int faces = 0;
			for (var face : List.of(BlockStateProperties.DOWN, BlockStateProperties.UP, BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
				BlockStateProperties.EAST, BlockStateProperties.WEST)) {
				if (there.getValue(face)) {
					faces++;
				}
			}
			helper.assertTrue(faces <= paid, "the scanned glow lichen was built on " + faces + " faces from " + paid
				+ " glow lichen (material list " + asked + "): shears take " + faces + " lichen off it, " + (faces - paid) + " for free");
			LOGGER.info("qa 1102b lichen: {} faces built from {} lichen", faces, paid);
		});
	}

	/**
	 * The scan guide: "Built something you love? The Scan Tool turns it into a blueprint, so the village's builders can
	 * build it again." A cake with a candle on it, scanned on a block of stone and built again without free materials
	 * from a chest holding the material list plus a cake and a candle: at the end it stands there again.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bCandleCake"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bCandleCake")
	public void aScannedCakeWithACandleIsBuiltAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.CANDLE_CAKE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above());
		helper.setBlock(SRC.above(), Blocks.AIR);
		helper.setBlock(SRC, Blocks.AIR);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		chest.setItem(slot++, new ItemStack(Items.CAKE));
		chest.setItem(slot++, new ItemStack(Items.CANDLE));
		chest.setItem(slot, new ItemStack(Items.DIRT, 64));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
				+ " missing " + b.site().missing());
			helper.assertTrue(level.getBlockState(b.origin()).is(Blocks.STONE), "setup: the stone wasn't built: " + level.getBlockState(b.origin()));
			BlockState top = level.getBlockState(b.origin().above());
			helper.assertTrue(top.is(Blocks.CANDLE_CAKE), "the scanned cake with a candle was not built again (the spot holds " + top + "; material list "
				+ asked + ", " + b.site().skipped() + " skipped) with a cake and a candle in the chest");
			LOGGER.info("qa 1102b candle cake: built again ({})", asked);
		});
	}


	/**
	 * The scan guide's "builders can build it again", on hanging plants: three blocks of cave vines hanging from a
	 * stone ceiling and three of weeping vines hanging from netherrack (each a tip and two stem pieces, which have no
	 * item of their own but are placed with glow berries or weeping vines as a player does), scanned and built again
	 * with free materials: both hang three blocks long again.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bHangingVines"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bHangingVines")
	public void scannedHangingVinesThreeLongAreBuiltThreeLong(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// Two posts, each holding a ceiling block at y + 4 with a vine of three hanging from it
		for (int dx : new int[] {0, 2}) {
			for (int dy = 0; dy < 4; dy++) {
				helper.setBlock(SRC.offset(dx, dy, 1), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
			}
			helper.setBlock(SRC.offset(dx, 4, 1), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
			helper.setBlock(SRC.offset(dx, 4, 0), dx == 0 ? Blocks.STONE : Blocks.NETHERRACK);
		}
		helper.setBlock(SRC.offset(0, 3, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 2, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 1, 0), Blocks.CAVE_VINES);
		helper.setBlock(SRC.offset(2, 3, 0), Blocks.WEEPING_VINES_PLANT);
		helper.setBlock(SRC.offset(2, 2, 0), Blocks.WEEPING_VINES_PLANT);
		helper.setBlock(SRC.offset(2, 1, 0), Blocks.WEEPING_VINES);
		helper.assertTrue(level.getBlockState(helper.absolutePos(SRC.offset(0, 3, 0))).is(Blocks.CAVE_VINES_PLANT)
			&& level.getBlockState(helper.absolutePos(SRC.offset(2, 1, 0))).is(Blocks.WEEPING_VINES), "setup: the vines didn't hang");
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(2, 4, 1));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			int cave = 0;
			int weeping = 0;
			for (int dy = 1; dy <= 3; dy++) {
				BlockState c = level.getBlockState(b.origin().offset(0, dy, 0));
				BlockState w = level.getBlockState(b.origin().offset(2, dy, 0));
				cave += c.is(Blocks.CAVE_VINES) || c.is(Blocks.CAVE_VINES_PLANT) ? 1 : 0;
				weeping += w.is(Blocks.WEEPING_VINES) || w.is(Blocks.WEEPING_VINES_PLANT) ? 1 : 0;
			}
			helper.assertTrue(level.getBlockState(b.origin().offset(0, 4, 0)).is(Blocks.STONE), "setup: the ceiling wasn't built");
			helper.assertTrue(cave == 3 && weeping == 3, "the scanned vines three long were built " + cave + " (cave vines) and " + weeping
				+ " (weeping vines) long (" + b.site().skipped() + " skipped, material list " + b.site().plan(level).materials() + ")");
			LOGGER.info("qa 1102b vines: both built three long");
		});
	}

	/**
	 * The scan guide's "builders can build it again", on a piece of redstone: a sticky piston pushed out by the redstone
	 * block behind it, its head out east, scanned and built again with free materials. The redstone block stands
	 * behind the built piston as it did, so the built piston ends pushed out with its head, not as a pushed-out base with
	 * nothing in front (a block a player can't make).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102bPiston"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102bPiston")
	public void aScannedPushedOutPistonIsBuiltWithItsHead(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.STONE);
		}
		helper.setBlock(SRC.above(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(SRC.east(1).above(), Blocks.STICKY_PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING,
			net.minecraft.core.Direction.EAST));
		QaBuilders1102bGameTests.Built[] b = new QaBuilders1102bGameTests.Built[1];
		helper.startSequence().thenWaitUntil(() -> {
			if (!level.getBlockState(helper.absolutePos(SRC.east(2).above())).is(Blocks.PISTON_HEAD)) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("setup: the piston hasn't pushed out yet");
			}
		}).thenExecute(() -> {
			ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.east(2).above());
			helper.setBlock(SRC.east(2).above(), Blocks.AIR);
			helper.setBlock(SRC.east(1).above(), Blocks.AIR);
			helper.setBlock(SRC.above(), Blocks.AIR);
			b[0] = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		}).thenWaitUntil(() -> {
			if (BuildSiteManager.get(level).get(b[0].site().id()) != null) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("still building: " + b[0].site().stage() + " " + b[0].site().status());
			}
		}).thenIdle(10).thenExecute(() -> {
			BlockState base = level.getBlockState(b[0].origin().offset(1, 1, 0));
			BlockState front = level.getBlockState(b[0].origin().offset(2, 1, 0));
			helper.assertTrue(level.getBlockState(b[0].origin().offset(0, 1, 0)).is(Blocks.REDSTONE_BLOCK), "setup: the redstone block wasn't built");
			helper.assertTrue(base.is(Blocks.STICKY_PISTON) && front.is(Blocks.PISTON_HEAD), "the scanned pushed-out piston was built as " + base
				+ " with " + front + " in front (" + b[0].site().skipped() + " skipped, list " + b[0].site().plan(level).materials() + ")");
			LOGGER.info("qa 1102b piston: built pushed out with its head ({})", base);
		}).thenSucceed();
	}

	/**
	 * The Blueprint Table shows a preview of every build it lists (TablePreviewGameTests: no listed build with blocks
	 * draws 0 cells). A scan of one standing banner (a block drawn by its block entity) is listed with 1 block, but its
	 * preview has no cells, so the Table shows an empty picture.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qa1102bBannerPreview"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qa1102bBannerPreview")
	public void aScannedLoneBannerHasAPreviewOnTheTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(SRC, Blocks.WHITE_BANNER);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC);
		var server = helper.getLevel().getServer();
		var preview = io.github.jcondedata.aliveworkplace.table.TableServer.preview(server, id);
		helper.assertTrue(preview.isPresent(), "the scanned banner has no preview at all");
		helper.assertTrue(preview.get().count() > 0, "the Blueprint Table's preview of a scanned lone banner (" + id + ") has 0 cells: the Table draws an empty picture");
		helper.succeed();
	}
}
