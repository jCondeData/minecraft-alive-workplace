package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (qa-lane-b-1103-0938, 2026-11-03): adversarial Scan Tool round trips around the fixes of B861 (a bubble column is
 * the water a player poured on soul sand or magma: a water bucket each, the bucket comes back, the bubbles form again)
 * and B865 (a melon's or pumpkin's grown stem costs one seeds and is planted as a stem). Written from the bug texts and
 * docs/wiki/builder.md, with the round trip of {@link QaScanRebuild1102dGameTests}: columns of one and eight blocks, the
 * bubbles' direction and pull on a dropped item, the buckets paid from a chest and returned, a chest one bucket short,
 * a save and reload in the decoration, two stems on one list, and the seeds paid from a chest.
 */
public class QaScanWaterStems1103GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = QaScanRebuild1102dGameTests.SRC;
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	// ------------------------------------------------------------------------------------------------ bubble columns

	/** A glass tube on a stone floor, {@code height} blocks of bubbles over soul sand (or magma) in its middle. */
	private static QaScanRebuild1102dGameTests.Scene tube(Block bottom, int height, boolean down) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				for (int dy = 1; dy <= height + 1; dy++) {
					if (dx != 1 || dz != 1) {
						s.put(dx, dy, dz, Blocks.GLASS);
					}
				}
			}
		}
		s.put(1, 1, 1, bottom);
		for (int dy = 2; dy <= height + 1; dy++) {
			s.put(1, dy, 1, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN, down));
		}
		return s;
	}

	/** Every block of the tube is a bubble column again, pulling {@code down} (sinking) or up; null when it is. */
	private static String bubbles(ServerLevel level, QaScanRebuild1102dGameTests.Built b, int height, boolean down) {
		List<String> wrong = new ArrayList<>();
		for (int dy = 2; dy <= height + 1; dy++) {
			BlockState now = level.getBlockState(b.at(1, dy, 1));
			if (!now.is(Blocks.BUBBLE_COLUMN) || now.getValue(BubbleColumnBlock.DRAG_DOWN) != down) {
				wrong.add("y" + dy + "=" + now);
			}
		}
		return wrong.isEmpty() ? null : String.join("; ", wrong);
	}

	/** Puts what the site's list asks into the chest at SUPPLY (stacks split as a player would). */
	private static Container stock(GameTestHelper helper, Map<Item, Integer> asked) {
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, new ItemStack(e.getKey()).getMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		return chest;
	}

	/** Scans the scene with real clicks, then a builder at the bench builds it back from a chest (not free). */
	private static QaScanRebuild1102dGameTests.Built paidRoundTrip(GameTestHelper helper, QaScanRebuild1102dGameTests.Scene scene) {
		Leftovers.clear(helper);
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			helper.setBlock(SRC.offset(e.getKey()), e.getValue());
		}
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(scene.max()));
		helper.setBlock(SUPPLY, Blocks.CHEST);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		return new QaScanRebuild1102dGameTests.Built(b, scene);
	}

	private static int held(Container chest, Villager builder, Item item) {
		return chest.countItem(item) + (builder == null ? 0 : ModAttachments.BUILDER_BAG.getOrCreate(builder).count(item));
	}

	/**
	 * The soul sand elevator of B861: the list asks three water buckets, and built again each block of the tube is a
	 * rising bubble column (DRAG_DOWN false), not only still water.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103wsRising"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103wsRising")
	public void aScannedSoulSandElevatorRisesAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, tube(Blocks.SOUL_SAND, 3, false),
			Map.of(Items.WATER_BUCKET, 3, Items.SOUL_SAND, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = bubbles(level, b, 3, false);
			helper.assertTrue(wrong == null, "the elevator's tube isn't rising bubbles again: " + wrong);
			helper.assertTrue(b.b().site().skipped() == 0, b.b().site().skipped() + " steps skipped");
		});
	}

	/** The smallest elevator: one bubble block over soul sand costs one water bucket and rises again. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103wsOne"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103wsOne")
	public void aOneBlockBubbleColumnCostsOneBucketAndRisesAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, tube(Blocks.SOUL_SAND, 1, false),
			Map.of(Items.WATER_BUCKET, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = bubbles(level, b, 1, false);
			helper.assertTrue(wrong == null, "the one-block column isn't rising bubbles again: " + wrong);
		});
	}

	/**
	 * A magma column eight blocks tall: eight water buckets on the list, eight sinking bubble blocks built again, and an
	 * item dropped at the top of the tube is pulled down (plain water would float it up), so the column really drags.
	 */
	//$ gametest_ticks_batch HUGE '3600' '"qa1103wsTallMagma"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qa1103wsTallMagma")
	public void anEightTallMagmaColumnSinksAgainAndPullsAnItemDown(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, tube(Blocks.MAGMA_BLOCK, 8, true),
			Map.of(Items.WATER_BUCKET, 8, Items.MAGMA_BLOCK, 1), true);
		ServerLevel level = helper.getLevel();
		ItemEntity[] drop = {null};
		double[] start = {0};
		helper.onEachTick(() -> {
			if (drop[0] == null && BuildSiteManager.get(level).get(b.b().site().id()) == null && bubbles(level, b, 8, true) == null) {
				BlockPos top = b.at(1, 9, 1);
				drop[0] = new ItemEntity(level, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
				drop[0].setPickUpDelay(32767);
				level.addFreshEntity(drop[0]);
				start[0] = drop[0].getY();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = bubbles(level, b, 8, true);
			helper.assertTrue(wrong == null, "the magma column isn't sinking bubbles again: " + wrong);
			helper.assertTrue(drop[0] != null, "no item dropped yet");
			helper.assertTrue(drop[0].getY() < b.at(1, 4, 1).getY(), "an item dropped at the top of the column at y " + start[0] + " is at y "
				+ drop[0].getY() + ", not pulled down below " + b.at(1, 4, 1).getY());
			drop[0].discard();
		});
	}

	/**
	 * Not free: the chest holds exactly what the list asks (three water buckets for the elevator). Built, the tube rises
	 * again, no water bucket is left, and all three empty buckets came back (chest or the builder's bag): none is lost.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103wsPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103wsPaid")
	public void aPaidElevatorUsesThreeWaterBucketsAndGivesTheBucketsBack(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = paidRoundTrip(helper, tube(Blocks.SOUL_SAND, 3, false));
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.WATER_BUCKET, 0) == 3, "the list asks " + asked + ", not 3 water buckets");
		Container chest = stock(helper, asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = bubbles(level, b, 3, false);
			helper.assertTrue(wrong == null, "the paid elevator's tube isn't rising bubbles: " + wrong);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(held(chest, builder, Items.WATER_BUCKET) == 0, held(chest, builder, Items.WATER_BUCKET) + " water buckets left unused");
			helper.assertTrue(held(chest, builder, Items.BUCKET) == 3, "of 3 water buckets poured, " + held(chest, builder, Items.BUCKET)
				+ " empty buckets came back");
		});
	}

	/**
	 * Not free, one water bucket short (two of three): the builder waits for one water bucket and never finishes with a
	 * dry or half-filled tube; topped up, the tube rises again with all three buckets back.
	 */
	//$ gametest_ticks_batch HUGE '4500' '"qa1103wsShort"'
	@GameTest(template = HUGE, timeoutTicks = 4500, batch = "qa1103wsShort")
	public void anElevatorOneBucketShortWaitsThenRises(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = paidRoundTrip(helper, tube(Blocks.SOUL_SAND, 3, false));
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = new java.util.LinkedHashMap<>(b.b().site().plan(level).materials());
		asked.put(Items.WATER_BUCKET, 2);
		Container chest = stock(helper, asked);
		String[] waited = {null};
		helper.onEachTick(() -> {
			BuildSite site = BuildSiteManager.get(level).get(b.b().site().id());
			if (waited[0] == null && site == null) {
				helper.fail("the build finished from a chest with 2 of the 3 water buckets: tube " + bubbles(level, b, 3, false));
			}
			if (waited[0] == null && site != null && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
				Map<Item, Integer> missing = site.missing();
				helper.assertTrue(missing.getOrDefault(Items.WATER_BUCKET, 0) == 1, "with 2 of 3 water buckets the builder waits for " + missing
					+ ", not 1 water bucket");
				waited[0] = "waited for " + missing;
				for (int i = 0; i < chest.getContainerSize(); i++) {
					if (chest.getItem(i).isEmpty()) {
						chest.setItem(i, new ItemStack(Items.WATER_BUCKET)); // the player tops the chest up
						break;
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waited[0] != null, "never waited for the missing water bucket: " + b.b().site().stage() + " " + b.b().site().status());
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building after the top-up (" + waited[0] + "): "
				+ b.b().site().stage() + " " + b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = bubbles(level, b, 3, false);
			helper.assertTrue(wrong == null, "after the top-up the tube isn't rising bubbles: " + wrong);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(held(chest, builder, Items.BUCKET) == 3, held(chest, builder, Items.BUCKET) + " empty buckets came back, not 3");
		});
	}

	/** Saves and loads the server's sites and the builder again the first tick the site is in the decoration. */
	private static void reloadInDecoration(GameTestHelper helper, QaScanRebuild1102dGameTests.Built b, String[] reloaded) {
		ServerLevel level = helper.getLevel();
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
	}

	/** The elevator's site saved and loaded again in the decoration (before the water goes in): the tube still rises. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103wsReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103wsReload")
	public void anElevatorSurvivesASaveAndReloadMidDecoration(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, tube(Blocks.SOUL_SAND, 3, false),
			Map.of(Items.WATER_BUCKET, 3), true);
		ServerLevel level = helper.getLevel();
		String[] reloaded = {null};
		reloadInDecoration(helper, b, reloaded);
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the build never reached the decoration for a reload");
			BuildSite live = BuildSiteManager.get(level).get(b.b().site().id());
			helper.assertTrue(live == null, "still building after the reload: " + (live == null ? "" : live.stage() + " " + live.status()
				+ " missing " + live.missing()));
			String wrong = bubbles(level, b, 3, false);
			helper.assertTrue(wrong == null, "after a reload (" + reloaded[0] + ") the tube isn't rising bubbles: " + wrong);
		});
	}

	// ------------------------------------------------------------------------------------------------ grown stems

	/** Stone floor, a pool, and a melon (or pumpkin) on dirt beside its grown stem on farmland, per row {@code z}. */
	private static void stemRow(QaScanRebuild1102dGameTests.Scene s, int z, Block attached, Block fruit) {
		s.put(0, 0, z, Blocks.FARMLAND);
		s.put(1, 0, z, Blocks.DIRT);
		s.put(0, 1, z, attached.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.EAST));
		s.put(1, 1, z, fruit);
	}

	private static QaScanRebuild1102dGameTests.Scene stemField() {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 5; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
			}
		}
		s.put(0, 0, 1, Blocks.WATER);
		s.put(0, 0, 3, Blocks.WATER);
		stemRow(s, 0, Blocks.ATTACHED_MELON_STEM, Blocks.MELON);
		stemRow(s, 2, Blocks.ATTACHED_MELON_STEM, Blocks.MELON);
		stemRow(s, 4, Blocks.ATTACHED_PUMPKIN_STEM, Blocks.PUMPKIN);
		return s;
	}

	private static String stemsWrong(ServerLevel level, QaScanRebuild1102dGameTests.Built b) {
		List<String> wrong = new ArrayList<>();
		int[][] rows = {{0}, {2}, {4}};
		for (int[] r : rows) {
			int z = r[0];
			boolean melon = z != 4;
			BlockState stem = level.getBlockState(b.at(0, 1, z));
			boolean ok = melon ? stem.is(Blocks.MELON_STEM) || stem.is(Blocks.ATTACHED_MELON_STEM)
				: stem.is(Blocks.PUMPKIN_STEM) || stem.is(Blocks.ATTACHED_PUMPKIN_STEM);
			if (!ok) {
				wrong.add("z" + z + " stem=" + stem);
			}
			BlockState fruit = level.getBlockState(b.at(1, 1, z));
			if (!fruit.is(melon ? Blocks.MELON : Blocks.PUMPKIN)) {
				wrong.add("z" + z + " fruit=" + fruit);
			}
		}
		return wrong.isEmpty() ? null : String.join("; ", wrong);
	}

	/**
	 * Two melons and a pumpkin, each on its grown stem: the list asks exactly one seeds per stem (2 melon seeds, 1
	 * pumpkin seeds), never a melon or pumpkin for a stem (one each for the fruit only), and every stem is built.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103wsStems"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103wsStems")
	public void twoMelonStemsAndAPumpkinStemAskOneSeedsEach(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, stemField(),
			Map.of(Items.MELON_SEEDS, 2, Items.PUMPKIN_SEEDS, 1, Items.MELON, 2, Items.PUMPKIN, 1), true);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = stemsWrong(level, b);
			helper.assertTrue(wrong == null, "the stems or fruit weren't built again: " + wrong);
			helper.assertTrue(b.b().site().skipped() == 0, b.b().site().skipped() + " steps skipped");
		});
	}

	/** Not free: the chest holds exactly the list; built, every stem stands and the chest's seeds are used up. */
	//$ gametest_ticks_batch HUGE '4000' '"qa1103wsStemsPaid"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qa1103wsStemsPaid")
	public void paidStemsUseTheChestsSeeds(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = paidRoundTrip(helper, stemField());
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> asked = b.b().site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.MELON_SEEDS, 0) == 2 && asked.getOrDefault(Items.PUMPKIN_SEEDS, 0) == 1,
			"the list asks " + asked + ", not 2 melon seeds and 1 pumpkin seeds");
		Container chest = stock(helper, asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.b().site().id()) == null, "still building: " + b.b().site().stage() + " "
				+ b.b().site().status() + " missing " + b.b().site().missing());
			String wrong = stemsWrong(level, b);
			helper.assertTrue(wrong == null, "the paid stems or fruit weren't built again: " + wrong);
			Villager builder = level.getEntitiesOfClass(Villager.class, helper.getBounds()).stream().findFirst().orElse(null);
			helper.assertTrue(held(chest, builder, Items.MELON_SEEDS) == 0 && held(chest, builder, Items.PUMPKIN_SEEDS) == 0,
				"seeds left over: " + held(chest, builder, Items.MELON_SEEDS) + " melon, " + held(chest, builder, Items.PUMPKIN_SEEDS) + " pumpkin");
		});
	}

	/** The stems' site saved and loaded again in the decoration: every stem still stands at the end. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1103wsStemsReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1103wsStemsReload")
	public void stemsSurviveASaveAndReloadMidDecoration(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, stemField(), Map.of(Items.MELON_SEEDS, 2), true);
		ServerLevel level = helper.getLevel();
		String[] reloaded = {null};
		reloadInDecoration(helper, b, reloaded);
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] != null, "the build never reached the decoration for a reload");
			BuildSite live = BuildSiteManager.get(level).get(b.b().site().id());
			helper.assertTrue(live == null, "still building after the reload: " + (live == null ? "" : live.stage() + " " + live.status()
				+ " missing " + live.missing()));
			String wrong = stemsWrong(level, b);
			helper.assertTrue(wrong == null, "after a reload (" + reloaded[0] + ") the stems or fruit weren't built: " + wrong);
		});
	}
}
