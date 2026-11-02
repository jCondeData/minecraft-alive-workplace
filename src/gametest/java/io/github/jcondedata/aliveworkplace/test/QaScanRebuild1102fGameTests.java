package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * QA (2026-11-02): adversarial Scan Tool round trips around the fixes of B816 (sugar cane by water), B817 (sea pickles
 * built waterlogged, glowing) and B818 (tall seagrass built again, as a player grows it), from their Expected and the
 * Scan Tool's promise (docs/wiki/scan-tool.md: the builders can build a scan again). Corners QaScanRebuild1102dBugTests
 * leaves out: cane on each soil and at each height, the cane's water in the world instead of the scan, cane that can't
 * stand (skipped, never floating), a chest short of cane, a save and reload mid-decoration, one and four pickles and
 * their light, a dry pickle on land, living coral, what tall seagrass costs, and plain seagrass one deep.
 */
public class QaScanRebuild1102fGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = QaScanRebuild1102dGameTests.SRC;
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	// ------------------------------------------------------------------------------------------------- sugar cane

	/** Cane 1 and 3 tall on dirt, grass, sand and red sand along one channel of water: every stalk stands again, 8 cane asked. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fCaneSoils"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fCaneSoils")
	public void caneOneAndThreeTallOnEverySoilIsBuiltAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 5; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
			if (dx == 0 || dx == 5) {
				for (int dz = 0; dz <= 2; dz++) {
					s.put(dx, 1, dz, Blocks.STONE);
				}
			} else {
				s.put(dx, 1, 2, Blocks.STONE);
				s.put(dx, 1, 1, Blocks.WATER); // the channel, beside each foot
			}
		}
		Block[] soils = {Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.RED_SAND};
		int[] tall = {1, 3, 1, 3};
		for (int i = 0; i < 4; i++) {
			s.put(1 + i, 1, 0, soils[i]);
			for (int dy = 2; dy < 2 + tall[i]; dy++) {
				s.put(1 + i, dy, 0, Blocks.SUGAR_CANE);
			}
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SUGAR_CANE, 8), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(!helper.getLevel().getBlockState(b.at(1 + i, 2 + tall[i], 0)).is(Blocks.SUGAR_CANE),
					"the cane on " + soils[i] + " grew taller than the scanned " + tall[i]);
			}
		});
	}

	/**
	 * Cane whose only water is a diagonal away can't stand in vanilla (scanned the tick it was set). The build must skip
	 * it, counted, and never leave a floating cane; the rest of the scene stands again.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fCaneDiagonal"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fCaneDiagonal")
	public void caneWithWaterOnlyADiagonalAwayIsSkippedNotFloating(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 3; dx++) {
			for (int dz = 0; dz <= 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				s.put(dx, 1, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.SAND);
		s.put(2, 1, 2, Blocks.WATER); // diagonal to the sand: not beside the foot
		s.put(1, 2, 1, Blocks.SUGAR_CANE);
		s.put(1, 3, 1, Blocks.SUGAR_CANE);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(), true);
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			BlockState foot = level.getBlockState(b.at(1, 2, 1));
			if (foot.is(Blocks.SUGAR_CANE) && !foot.canSurvive(level, b.at(1, 2, 1))) {
				helper.fail("a sugar cane was built with no water beside its foot at " + b.at(1, 2, 1).toShortString());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : s.blocks.entrySet()) {
				BlockState now = level.getBlockState(b.at(e.getKey().getX(), e.getKey().getY(), e.getKey().getZ()));
				if (e.getValue().is(Blocks.SUGAR_CANE)) {
					if (now.is(Blocks.SUGAR_CANE)) {
						wrong.add(e.getKey().toShortString() + " a cane that can't stand was built");
					}
				} else if (!now.is(e.getValue().getBlock())) {
					wrong.add(e.getKey().toShortString() + " " + e.getValue().getBlock() + " built as " + now);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "after the build: " + String.join("; ", wrong));
			helper.assertTrue(b.b().site().skipped() >= 1, "the cane that can't stand was dropped without a word: " + b.b().site().skipped() + " skipped");
		});
	}

	/**
	 * The scan holds only the sand and the cane; the water beside the foot is the world's at the build site (a player
	 * builds the scan by an existing pond). The cane stands three tall.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fCaneWorldWater"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fCaneWorldWater")
	public void caneScannedWithoutItsWaterStandsByTheWorldsWater(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scanCaneColumn(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 5));
		wellBeside(level, origin);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		helper.assertTrue(b.origin().equals(origin), "setup: the build's origin " + b.origin() + " isn't " + origin);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
				+ " missing " + b.site().missing());
			helper.assertTrue(caneHeight(level, origin.above()) == 3, "the cane by the world's water stands " + caneHeight(level, origin.above())
				+ " tall, not 3 (" + b.site().skipped() + " skipped)");
			helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
		});
	}

	/**
	 * Not free: the chest holds 2 of the 3 cane. The builder waits for the third (never a floating or missing stalk),
	 * and once a player adds it, the cane stands three tall and the chest's cane is used up.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1102fCaneShort"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1102fCaneShort")
	public void aChestShortOfCaneWaitsThenBuildsThreeTall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = scanCaneColumn(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 5));
		wellBeside(level, origin);
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		chest.setItem(0, new ItemStack(Items.SUGAR_CANE, 2));
		chest.setItem(1, new ItemStack(Items.SAND, 4));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		String[] waited = {null};
		helper.onEachTick(() -> {
			for (int dy = 2; dy <= 3; dy++) {
				if (level.getBlockState(origin.above(dy)).is(Blocks.SUGAR_CANE) && !level.getBlockState(origin.above(dy - 1)).is(Blocks.SUGAR_CANE)) {
					helper.fail("a floating sugar cane at " + dy + " above the sand");
				}
			}
			BuildSite site = BuildSiteManager.get(level).get(b.site().id());
			if (waited[0] == null && site != null && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				Map<Item, Integer> missing = site.missing();
				helper.assertTrue(missing.getOrDefault(Items.SUGAR_CANE, 0) == 1, "with 2 of 3 cane the builder waits for " + missing + ", not 1 sugar cane");
				waited[0] = "waited for " + missing + " with " + caneHeight(level, origin.above()) + " cane up";
				chest.setItem(2, new ItemStack(Items.SUGAR_CANE, 1)); // the player tops the chest up
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] != null || BuildSiteManager.get(level).get(b.site().id()) == null,
				"still building, never waiting: " + b.site().stage() + " " + b.site().status());
			helper.assertTrue(waited[0] != null, "the build finished from a chest with 2 of the 3 cane (" + caneHeight(level, origin.above()) + " tall)");
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building after the top-up: " + b.site().stage() + " "
				+ b.site().status() + " missing " + b.site().missing() + " (" + waited[0] + ")");
			helper.assertTrue(caneHeight(level, origin.above()) == 3, "the cane stands " + caneHeight(level, origin.above()) + " tall, not 3 (" + waited[0] + ")");
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 0, chest.countItem(Items.SUGAR_CANE) + " cane left in the chest for 3 built");
		});
	}

	/**
	 * B816's scene (cane three tall on sand beside a walled pool), the server's sites and the builder saved and loaded
	 * again in the decoration, the first tick the pool's water is in: the cane still stands three tall.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fCaneReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fCaneReload")
	public void caneByAPoolSurvivesASaveAndReloadMidDecoration(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				s.put(dx, 1, dz, Blocks.STONE);
			}
		}
		s.put(1, 1, 1, Blocks.SAND);
		s.put(1, 1, 2, Blocks.WATER);
		for (int dy = 2; dy <= 4; dy++) {
			s.put(1, dy, 1, Blocks.SUGAR_CANE);
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SUGAR_CANE, 3), true);
		ServerLevel level = helper.getLevel();
		String[] reloaded = {null};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(b.b().site().id());
			if (reloaded[0] != null || site == null || site.stage() != BuildPlan.Stage.DECORATION
				|| !level.getBlockState(b.at(1, 1, 2)).is(Blocks.WATER)) {
				return;
			}
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds())) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(v.save(tag), "the builder wasn't saved");
				v.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				level.addFreshEntity(loaded);
			}
			BuildSite back = BuildSiteManager.get(level).get(b.b().site().id());
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.DECORATION, "after the reload the site is "
				+ (back == null ? "gone" : back.stage()));
			reloaded[0] = "reloaded at cursor " + site.cursor() + " with " + caneHeight(level, b.at(1, 2, 1)) + " cane up";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the build never reached the decoration with the pool's water in for a reload");
			BuildSite live = BuildSiteManager.get(level).get(b.b().site().id());
			helper.assertTrue(live == null, "still building after the reload: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
			helper.assertTrue(caneHeight(level, b.at(1, 2, 1)) == 3, "after a reload (" + reloaded[0] + ") the cane stands "
				+ caneHeight(level, b.at(1, 2, 1)) + " tall, not 3");
		});
	}

	// ----------------------------------------------------------------------------------------------- sea pickles

	/**
	 * One pickle and four pickles at the bottom of a pool two deep: asked as 5, built waterlogged with the scanned count,
	 * glowing as vanilla's (light 6 and 15), and the water above them is water (no dry cell under the pool).
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fPickles"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fPickles")
	public void oneAndFourPicklesInAPoolGlowAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 3; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 2; dy++) {
					if (dz != 1 || dx == 0 || dx == 3) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		BlockState pickle = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.WATERLOGGED, true);
		s.put(1, 1, 1, pickle.setValue(SeaPickleBlock.PICKLES, 1));
		s.put(2, 1, 1, pickle.setValue(SeaPickleBlock.PICKLES, 4));
		s.put(1, 2, 1, Blocks.WATER);
		s.put(2, 2, 1, Blocks.WATER);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SEA_PICKLE, 5), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			int[] counts = {1, 4};
			int[] glow = {6, 15};
			for (int i = 0; i < 2; i++) {
				BlockPos at = b.at(1 + i, 1, 1);
				BlockState p = level.getBlockState(at);
				helper.assertTrue(p.is(Blocks.SEA_PICKLE) && p.getValue(SeaPickleBlock.PICKLES) == counts[i] && p.getValue(SeaPickleBlock.WATERLOGGED),
					counts[i] + " pickle(s) in water were built as " + p);
				int light = level.getBrightness(LightLayer.BLOCK, at);
				helper.assertTrue(light >= glow[i], counts[i] + " pickle(s) give block light " + light + ", not " + glow[i]);
				BlockState above = level.getBlockState(at.above());
				helper.assertTrue(above.getFluidState().isSource() && above.is(Blocks.WATER), "above " + counts[i] + " pickle(s) is " + above + ", not the pool's water");
			}
		});
	}

	/** Two pickles scanned dry on sand stay dry: no water poured, no water bucket asked. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102fDryPickles"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102fDryPickles")
	public void picklesScannedDryOnLandStayDry(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.SAND);
		s.put(0, 1, 0, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 2).setValue(SeaPickleBlock.WATERLOGGED, false));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SEA_PICKLE, 2, Items.WATER_BUCKET, 0), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = level.getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.SEA_PICKLE) && p.getValue(SeaPickleBlock.PICKLES) == 2 && !p.getValue(SeaPickleBlock.WATERLOGGED),
				"two dry pickles were built as " + p);
			helper.assertTrue(level.getFluidState(b.at(0, 1, 0)).isEmpty(), "water at the dry pickles: " + level.getFluidState(b.at(0, 1, 0)));
		});
	}

	/**
	 * Living tube coral and a tube coral fan at the bottom of a pool (the fix's other "lives in water" blocks) are built
	 * waterlogged and are still alive 200 ticks after the build, not dead coral.
	 */
	//$ gametest_ticks_batch HUGE '3400' '"qa1102fCoral"'
	@GameTest(template = HUGE, timeoutTicks = 3400, batch = "qa1102fCoral")
	public void livingCoralInAPoolIsStillAliveAfterTheBuild(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 3; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 2; dy++) {
					if (dz != 1 || dx == 0 || dx == 3) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		s.put(1, 1, 1, Blocks.TUBE_CORAL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		s.put(2, 1, 1, Blocks.TUBE_CORAL_FAN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		s.put(1, 2, 1, Blocks.WATER);
		s.put(2, 2, 1, Blocks.WATER);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.TUBE_CORAL, 1, Items.TUBE_CORAL_FAN, 1), true);
		ServerLevel level = helper.getLevel();
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			b.done(helper);
			if (doneAt[0] < 0) {
				doneAt[0] = level.getGameTime();
			}
			for (int i = 0; i < 2; i++) {
				BlockState c = level.getBlockState(b.at(1 + i, 1, 1));
				helper.assertTrue(c.is(i == 0 ? Blocks.TUBE_CORAL : Blocks.TUBE_CORAL_FAN) && c.getValue(BlockStateProperties.WATERLOGGED),
					"the living coral in the pool is " + c + " " + (level.getGameTime() - doneAt[0]) + " ticks after the build");
			}
			helper.assertTrue(level.getGameTime() - doneAt[0] >= 200, "waiting 200 ticks for the coral to live or die");
		});
	}

	// ------------------------------------------------------------------------------------------------ seagrass

	/**
	 * Two tall seagrass filling a pool exactly two deep (no water above): both halves of each stand again, and each
	 * plant costs what a player uses, one seagrass and one bone meal (2 and 2), not one per half.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102fTallSeagrass"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102fTallSeagrass")
	public void twoTallSeagrassTwoDeepCostOneSeagrassAndOneBoneMealEach(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 3; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.SAND);
				for (int dy = 1; dy <= 2; dy++) {
					if (dz != 1 || dx == 0 || dx == 3) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		BlockState lower = Blocks.TALL_SEAGRASS.defaultBlockState();
		BlockState upper = lower.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
		for (int dx = 1; dx <= 2; dx++) {
			s.put(dx, 1, 1, lower);
			s.put(dx, 2, 1, upper);
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SEAGRASS, 2, Items.BONE_MEAL, 2), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			for (int dx = 1; dx <= 2; dx++) {
				BlockState lo = level.getBlockState(b.at(dx, 1, 1));
				BlockState up = level.getBlockState(b.at(dx, 2, 1));
				helper.assertTrue(lo.is(Blocks.TALL_SEAGRASS) && lo.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.LOWER
					&& up.is(Blocks.TALL_SEAGRASS) && up.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.UPPER,
					"tall seagrass " + dx + " was built as " + lo + " under " + up);
			}
		});
	}

	/** Plain seagrass in water one deep stands again as plain seagrass: one seagrass asked, no bone meal. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102fSeagrass"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102fSeagrass")
	public void seagrassOneDeepIsBuiltAsPlainSeagrass(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.SAND);
				if (dx != 1 || dz != 1) {
					s.put(dx, 1, dz, Blocks.STONE);
				}
			}
		}
		s.put(1, 1, 1, Blocks.SEAGRASS);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SEAGRASS, 1, Items.BONE_MEAL, 0), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState g = level.getBlockState(b.at(1, 1, 1));
			helper.assertTrue(g.is(Blocks.SEAGRASS) && level.getFluidState(b.at(1, 1, 1)).isSource(), "the seagrass one deep was built as " + g);
		});
	}

	// ------------------------------------------------------------------------------------------------- helpers

	/** Sand with a pool beside it and cane three tall at SRC; scans only the sand and the cane (not the water). */
	private static ResourceLocation scanCaneColumn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		wellBeside(level, helper.absolutePos(SRC));
		for (int dy = 1; dy <= 3; dy++) {
			helper.setBlock(SRC.above(dy), Blocks.SUGAR_CANE);
		}
		helper.assertTrue(caneHeight(level, helper.absolutePos(SRC.above())) == 3, "setup: the cane isn't three tall");
		return QaBuilders1102bGameTests.scan(helper, SRC, SRC.above(3));
	}

	/** Sand at {@code at} on stone, and a water source east of it walled in on its other three sides. */
	private static void wellBeside(ServerLevel level, BlockPos at) {
		level.setBlock(at.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.east().below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.east(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.east().north(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.east().south(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(at.east(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** How many sugar cane stand in a column from {@code foot} up. */
	private static int caneHeight(ServerLevel level, BlockPos foot) {
		int n = 0;
		while (n < 8 && level.getBlockState(foot.above(n)).is(Blocks.SUGAR_CANE)) {
			n++;
		}
		return n;
	}
}
