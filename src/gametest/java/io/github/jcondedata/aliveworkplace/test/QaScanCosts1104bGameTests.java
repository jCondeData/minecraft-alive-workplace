package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-11-04, lane-b-1104-1238): what the Builder pays for blocks with no item of their own when a scanned build is
 * built back, written from docs/wiki/builder.md section 1 (B792 lichen and sculk vein per face, B817 pickles and living
 * coral waterlogged with a water bucket that comes back, B818 tall seagrass from seagrass and bone meal, B873 powder snow
 * from its bucket). Gaps left by QaScanRebuild1102f, QaScanItemless1103, QaBuilders1102b and PowderSnowBucket: lichen on
 * five faces and sculk vein on four (paid, one short), three pickles' water bucket, dead next to living coral, one bone
 * meal short, one powder snow bucket short, a reload mid-decoration and free materials taking nothing.
 */
public class QaScanCosts1104bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static final List<net.minecraft.world.level.block.state.properties.BooleanProperty> FACES = List.of(BlockStateProperties.DOWN,
		BlockStateProperties.UP, BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST, BlockStateProperties.WEST);

	// ------------------------------------------------------------------------------------------------ helpers

	private static int faces(BlockState state) {
		int n = 0;
		for (var f : FACES) {
			if (state.hasProperty(f) && state.getValue(f)) {
				n++;
			}
		}
		return n;
	}

	private static Villager builder(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
	}

	private static int held(GameTestHelper helper, Container chest, Item item) {
		return QaScanItemless1103GameTests.held(chest, builder(helper), item);
	}

	/** The asked list, less {@code short_} of {@code item}. */
	private static Map<Item, Integer> less(Map<Item, Integer> asked, Item item, int short_) {
		Map<Item, Integer> out = new LinkedHashMap<>(asked);
		out.put(item, asked.getOrDefault(item, 0) - short_);
		return out;
	}

	/** A floor and walls of stone, x 0..w-1, z 0..2, {@code height} walls tall, with the cells at z=1 of {@code cells} left open. */
	private static QaScanRebuild1102dGameTests.Scene basin(int w, int height, int... cells) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= height; dy++) {
					boolean cell = false;
					for (int c : cells) {
						cell |= dz == 1 && dx == c;
					}
					if (!cell) {
						s.put(dx, dy, dz, Blocks.STONE);
					}
				}
			}
		}
		return s;
	}

	/**
	 * Waits for the site to wait for materials, checks it is short of exactly {@code item} x {@code count} and nothing
	 * else, and tops the chest up; returns what it saw (null until then).
	 */
	private static String[] topUpWhenWaiting(GameTestHelper helper, QaScanRebuild1102dGameTests.Built b, Container chest, Item item, int count) {
		ServerLevel level = helper.getLevel();
		String[] waited = {null};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(b.b().site().id());
			if (waited[0] != null || site == null || site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				return;
			}
			Map<Item, Integer> missing = site.missing();
			helper.assertTrue(missing.getOrDefault(item, 0) == count && missing.size() == 1,
				"one " + item + " short, the builder waits for " + missing + ", not " + count + " " + item);
			waited[0] = "waited for " + missing + " in " + site.stage();
			for (int slot = chest.getContainerSize() - 1; slot >= 0; slot--) {
				if (chest.getItem(slot).isEmpty()) {
					chest.setItem(slot, new ItemStack(item, count));
					break;
				}
			}
		});
		return waited;
	}

	// ------------------------------------------------------------------------------------------------ lichen, sculk vein

	/**
	 * Glow lichen on five faces of a stone niche and sculk vein on four faces of another, scanned and built back paid:
	 * the list asks 5 lichen and 4 vein (one a face, as shears count them). With one lichen short the builder waits for
	 * exactly one glow lichen; topped up, both stand on all their scanned faces, the chest's lichen and vein are used up,
	 * and shears take no more lichen off the build than was paid.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qa1104bLichenFaces"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qa1104bLichenFaces")
	public void lichenOnFiveFacesAndVeinOnFourArePaidAFaceEachAndWaitForTheLastLichen(GameTestHelper helper) {
		// Stone x 0..4, y 0..2, z 0..2; niches at (1,1,1) and (3,1,1), each open to the north through (x,1,0)
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx <= 4; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = 0; dz <= 2; dz++) {
					boolean open = dy == 1 && (dx == 1 || dx == 3) && dz <= 1;
					s.put(dx, dy, dz, open ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
				}
			}
		}
		BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.UP, true)
			.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.SOUTH, true);
		BlockState vein = Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.UP, true)
			.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.SOUTH, true);
		s.put(1, 1, 1, lichen);
		s.put(3, 1, 1, vein);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.GLOW_LICHEN, 0) == 5 && asked.getOrDefault(Items.SCULK_VEIN, 0) == 4,
			"a lichen on 5 faces and a vein on 4 ask " + asked + ", not 5 glow lichen and 4 sculk vein");
		Container chest = QaScanItemless1103GameTests.stock(helper, less(asked, Items.GLOW_LICHEN, 1));
		String[] waited = topUpWhenWaiting(helper, b, chest, Items.GLOW_LICHEN, 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] != null, "the build never waited with 4 of 5 lichen (" + b.b().site().stage() + " " + b.b().site().status() + ")");
			b.done(helper);
			BlockState l = level.getBlockState(b.at(1, 1, 1));
			BlockState v = level.getBlockState(b.at(3, 1, 1));
			helper.assertTrue(faces(l) == 5 && l.getValue(BlockStateProperties.SOUTH) && l.getValue(BlockStateProperties.UP),
				"the 5-faced lichen was built as " + l + " (" + waited[0] + ")");
			helper.assertTrue(faces(v) == 4, "the 4-faced sculk vein was built as " + v);
			helper.assertTrue(held(helper, chest, Items.GLOW_LICHEN) == 0 && held(helper, chest, Items.SCULK_VEIN) == 0,
				"after the build " + held(helper, chest, Items.GLOW_LICHEN) + " lichen and " + held(helper, chest, Items.SCULK_VEIN) + " vein are left of 5 and 4");
			int sheared = Block.getDrops(l, level, b.at(1, 1, 1), null, null, new ItemStack(Items.SHEARS)).stream()
				.filter(st -> st.is(Items.GLOW_LICHEN)).mapToInt(ItemStack::getCount).sum();
			helper.assertTrue(sheared <= 5, "shears take " + sheared + " lichen off a build that paid 5");
			LOGGER.info("qa 1104b lichen: asked {}, {} ; lichen {} faces, vein {} faces, shears {}", asked, waited[0], faces(l), faces(v), sheared);
		});
	}

	/** One glow lichen on the floor (one face) asks exactly one lichen, never zero and never six. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1104bLichenOne"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1104bLichenOne")
	public void aOneFacedLichenAndAOneFacedVeinAskOneEach(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true));
		s.put(1, 1, 0, Blocks.SCULK_VEIN.defaultBlockState().setValue(BlockStateProperties.DOWN, true));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.GLOW_LICHEN, 1, Items.SCULK_VEIN, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			for (int dx = 0; dx <= 1; dx++) {
				BlockState there = level.getBlockState(b.at(dx, 1, 0));
				helper.assertTrue(faces(there) == 1 && there.getValue(BlockStateProperties.DOWN), "a one-faced " + s.blocks.get(new BlockPos(dx, 1, 0)).getBlock()
					+ " was built as " + there);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ sea pickles, coral

	/**
	 * Three sea pickles scanned waterlogged in a walled cell (no other water) and built back paid: the list asks 3 pickles
	 * and one water bucket; the pickles are three again, waterlogged and glowing (block light 12 at least), the chest's
	 * pickles and water bucket are used up and the empty bucket comes back.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1104bPicklesPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1104bPicklesPaid")
	public void threePicklesCostThreeAndOneWaterBucketWhichComesBackEmpty(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = basin(3, 1, 1);
		s.put(1, 1, 1, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3).setValue(SeaPickleBlock.WATERLOGGED, true));
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.SEA_PICKLE, 0) == 3 && asked.getOrDefault(Items.WATER_BUCKET, 0) == 1,
			"three waterlogged pickles ask " + asked + ", not 3 sea pickles and 1 water bucket");
		Container chest = QaScanItemless1103GameTests.stock(helper, asked);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockPos at = b.at(1, 1, 1);
			BlockState p = level.getBlockState(at);
			helper.assertTrue(p.is(Blocks.SEA_PICKLE) && p.getValue(SeaPickleBlock.PICKLES) == 3 && p.getValue(SeaPickleBlock.WATERLOGGED),
				"three pickles in water were built as " + p);
			int light = level.getBrightness(LightLayer.BLOCK, at);
			helper.assertTrue(light >= 12, "three waterlogged pickles give block light " + light + ", not 12");
			helper.assertTrue(held(helper, chest, Items.SEA_PICKLE) == 0, held(helper, chest, Items.SEA_PICKLE) + " pickles left of 3 asked for 3 built");
			helper.assertTrue(held(helper, chest, Items.WATER_BUCKET) == 0, "the water bucket was never poured");
			helper.assertTrue(held(helper, chest, Items.BUCKET) == 1, held(helper, chest, Items.BUCKET) + " empty buckets came back of 1 poured");
		});
	}

	/**
	 * Dead tube coral and living brain coral, both scanned waterlogged in walled cells: only the living coral asks a
	 * water bucket (one in all), the dead coral is built as dead coral (never revived) and the living one is built
	 * waterlogged and still alive 100 ticks after the build.
	 */
	//$ gametest_ticks_batch HUGE '3400' '"qa1104bDeadLiving"'
	@GameTest(template = HUGE, timeoutTicks = 3400, batch = "qa1104bDeadLiving")
	public void deadCoralAsksNoWaterAndLivingCoralAsksOneAndStaysAlive(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = basin(5, 1, 1, 3);
		s.put(1, 1, 1, Blocks.DEAD_TUBE_CORAL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		s.put(3, 1, 1, Blocks.BRAIN_CORAL.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(Items.WATER_BUCKET, 1, Items.DEAD_TUBE_CORAL, 1, Items.BRAIN_CORAL, 1), true);
		ServerLevel level = helper.getLevel();
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			b.done(helper);
			if (doneAt[0] < 0) {
				doneAt[0] = level.getGameTime();
			}
			BlockState dead = level.getBlockState(b.at(1, 1, 1));
			BlockState living = level.getBlockState(b.at(3, 1, 1));
			long after = level.getGameTime() - doneAt[0];
			helper.assertTrue(dead.is(Blocks.DEAD_TUBE_CORAL), "the dead coral is " + dead + " " + after + " ticks after the build");
			helper.assertTrue(living.is(Blocks.BRAIN_CORAL) && living.getValue(BlockStateProperties.WATERLOGGED),
				"the living coral is " + living + " " + after + " ticks after the build");
			helper.assertTrue(after >= 100, "waiting 100 ticks for the coral to live or die");
		});
	}

	// ------------------------------------------------------------------------------------------------ seagrass

	/**
	 * Two tall seagrass in a pool two deep, built back paid from a chest one bone meal short: the builder waits for
	 * exactly one bone meal (not seagrass, not a whole plant), and once topped up both plants stand both halves tall,
	 * with the chest's seagrass and bone meal used up.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qa1104bBoneMealShort"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qa1104bBoneMealShort")
	public void tallSeagrassOneBoneMealShortWaitsThenGrowsBothHalves(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = basin(4, 2, 1, 2);
		BlockState lower = Blocks.TALL_SEAGRASS.defaultBlockState();
		BlockState upper = lower.setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER);
		for (int dx = 1; dx <= 2; dx++) {
			s.put(dx, 1, 1, lower);
			s.put(dx, 2, 1, upper);
		}
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.SEAGRASS, 0) == 2 && asked.getOrDefault(Items.BONE_MEAL, 0) == 2,
			"two tall seagrass ask " + asked + ", not 2 seagrass and 2 bone meal");
		Container chest = QaScanItemless1103GameTests.stock(helper, less(asked, Items.BONE_MEAL, 1));
		String[] waited = topUpWhenWaiting(helper, b, chest, Items.BONE_MEAL, 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] != null, "the build never waited with 1 of 2 bone meal (" + b.b().site().stage() + " " + b.b().site().status() + ")");
			b.done(helper);
			for (int dx = 1; dx <= 2; dx++) {
				BlockState lo = level.getBlockState(b.at(dx, 1, 1));
				BlockState up = level.getBlockState(b.at(dx, 2, 1));
				helper.assertTrue(lo.is(Blocks.TALL_SEAGRASS) && lo.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.LOWER
					&& up.is(Blocks.TALL_SEAGRASS) && up.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.UPPER,
					"tall seagrass " + dx + " was built as " + lo + " under " + up + " (" + waited[0] + ")");
			}
			helper.assertTrue(held(helper, chest, Items.SEAGRASS) == 0 && held(helper, chest, Items.BONE_MEAL) == 0,
				held(helper, chest, Items.SEAGRASS) + " seagrass and " + held(helper, chest, Items.BONE_MEAL) + " bone meal left after 2 plants");
		});
	}

	// ------------------------------------------------------------------------------------------------ powder snow

	/**
	 * Two powder snow blocks built back paid from a chest with one powder snow bucket: the builder waits for exactly one
	 * powder snow bucket (not snow, not an empty bucket); topped up, both stand and both empty buckets come back.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qa1104bPowderShort"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qa1104bPowderShort")
	public void powderSnowOneBucketShortWaitsThenGivesBothBucketsBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POWDER_SNOW);
		s.put(1, 1, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.Built b = QaScanItemless1103GameTests.paidRoundTrip(helper, s);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.POWDER_SNOW_BUCKET, 0) == 2, "two powder snow ask " + asked);
		Container chest = QaScanItemless1103GameTests.stock(helper, less(asked, Items.POWDER_SNOW_BUCKET, 1));
		String[] waited = topUpWhenWaiting(helper, b, chest, Items.POWDER_SNOW_BUCKET, 1);
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] != null, "the build never waited with 1 of 2 powder snow buckets (" + b.b().site().stage() + " "
				+ b.b().site().status() + ")");
			b.done(helper);
			helper.assertTrue(held(helper, chest, Items.POWDER_SNOW_BUCKET) == 0, "a powder snow bucket was never poured");
			helper.assertTrue(held(helper, chest, Items.BUCKET) == 2, held(helper, chest, Items.BUCKET) + " empty buckets came back of 2 poured (" + waited[0] + ")");
		});
	}

	// ------------------------------------------------------------------------------------------------ reload, free

	/** A pool of three pickles, tall seagrass and a living coral fan, x 0..6. */
	private static QaScanRebuild1102dGameTests.Scene reef() {
		QaScanRebuild1102dGameTests.Scene s = basin(7, 2, 1, 3, 5);
		s.put(1, 1, 1, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3).setValue(SeaPickleBlock.WATERLOGGED, true));
		s.put(1, 2, 1, Blocks.WATER);
		s.put(3, 1, 1, Blocks.TALL_SEAGRASS.defaultBlockState());
		s.put(3, 2, 1, Blocks.TALL_SEAGRASS.defaultBlockState().setValue(TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER));
		s.put(5, 1, 1, Blocks.TUBE_CORAL_FAN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
		s.put(5, 2, 1, Blocks.WATER);
		return s;
	}

	private static String reefWrong(ServerLevel level, QaScanRebuild1102dGameTests.Built b) {
		BlockState p = level.getBlockState(b.at(1, 1, 1));
		BlockState lo = level.getBlockState(b.at(3, 1, 1));
		BlockState up = level.getBlockState(b.at(3, 2, 1));
		BlockState fan = level.getBlockState(b.at(5, 1, 1));
		if (!(p.is(Blocks.SEA_PICKLE) && p.getValue(SeaPickleBlock.PICKLES) == 3 && p.getValue(SeaPickleBlock.WATERLOGGED))) {
			return "pickles " + p;
		}
		if (!(lo.is(Blocks.TALL_SEAGRASS) && up.is(Blocks.TALL_SEAGRASS) && up.getValue(TallSeagrassBlock.HALF) == DoubleBlockHalf.UPPER)) {
			return "tall seagrass " + lo + " under " + up;
		}
		if (!(fan.is(Blocks.TUBE_CORAL_FAN) && fan.getValue(BlockStateProperties.WATERLOGGED))) {
			return "coral fan " + fan;
		}
		return null;
	}

	/**
	 * The reef, built back free; the server's sites and the builder are saved and loaded again the first tick of the
	 * decoration: the pickles still glow three waterlogged, the tall seagrass stands both halves and the fan lives.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1104bReefReload"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1104bReefReload")
	public void picklesSeagrassAndCoralSurviveASaveAndReloadMidDecoration(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, reef(),
			Map.of(Items.SEA_PICKLE, 3, Items.SEAGRASS, 1, Items.BONE_MEAL, 1, Items.TUBE_CORAL_FAN, 1), true);
		ServerLevel level = helper.getLevel();
		String[] reloaded = {null};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(b.b().site().id());
			if (reloaded[0] != null || site == null || site.stage() != BuildPlan.Stage.DECORATION) {
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
			reloaded[0] = "reloaded at cursor " + site.cursor();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the build never reached the decoration for a reload");
			b.done(helper);
			String wrong = reefWrong(level, b);
			helper.assertTrue(wrong == null, "after a reload (" + reloaded[0] + ") the reef was built wrong: " + wrong);
		});
	}

	/**
	 * workplaceFreeMaterials on: the reef plus powder snow and a five-faced lichen are built back while a chest at the
	 * bench holds every item they'd cost; the build takes nothing from it and puts no empty bucket in it.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1104bFreeTakesNothing"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1104bFreeTakesNothing")
	public void freeMaterialsTakeNothingFromTheChest(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = reef();
		// powder snow and a lichen in a niche open to the north, east of the reef (x 7..9)
		for (int dx = 7; dx <= 9; dx++) {
			for (int dz = 0; dz <= 2; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= 2; dy++) {
					s.put(dx, dy, dz, Blocks.STONE);
				}
			}
		}
		s.put(8, 1, 0, Blocks.AIR);
		s.put(8, 1, 1, Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.UP, true)
			.setValue(BlockStateProperties.EAST, true).setValue(BlockStateProperties.WEST, true).setValue(BlockStateProperties.SOUTH, true));
		s.put(8, 2, 0, Blocks.POWDER_SNOW);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s,
			Map.of(Items.GLOW_LICHEN, 5, Items.POWDER_SNOW_BUCKET, 1, Items.SEA_PICKLE, 3, Items.BONE_MEAL, 1), true);
		Map<Item, Integer> stocked = new LinkedHashMap<>();
		for (Item i : List.of(Items.GLOW_LICHEN, Items.SEA_PICKLE, Items.SEAGRASS, Items.BONE_MEAL, Items.TUBE_CORAL_FAN, Items.WATER_BUCKET,
			Items.POWDER_SNOW_BUCKET, Items.STONE)) {
			stocked.put(i, i == Items.STONE ? 64 : 5);
		}
		Container chest = QaScanItemless1103GameTests.stock(helper, stocked);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			b.done(helper);
			String wrong = reefWrong(level, b);
			helper.assertTrue(wrong == null, "the free reef was built wrong: " + wrong);
			helper.assertTrue(faces(level.getBlockState(b.at(8, 1, 1))) == 5, "the free lichen is " + level.getBlockState(b.at(8, 1, 1)));
			for (Map.Entry<Item, Integer> e : stocked.entrySet()) {
				helper.assertTrue(chest.countItem(e.getKey()) == e.getValue(), "with free materials the chest's " + e.getKey() + " went from "
					+ e.getValue() + " to " + chest.countItem(e.getKey()));
			}
			helper.assertTrue(chest.countItem(Items.BUCKET) == 0, chest.countItem(Items.BUCKET) + " empty buckets appeared in the chest of a free build");
		});
	}
}
