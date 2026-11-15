package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Law;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane b, 2026-11-15), checking B1254/B1255 with Create loaded, from the spec (glassblower.md, 46.6): "Glass from
 * sand, a coal or charcoal for every eight fired (nine glass take two coal)", "Glass panes from sand, the glass fired
 * first", "Nine sand on one coal is a coal short too", and with Create "Create's panes fired back into plain panes come
 * last, so 6 sand and a coal make 16 panes on that one coal". The boundaries on both sides, the lines word for word,
 * Create's Tiled Glass in the chest left alone while there is sand, and a reload while firing and carrying with nothing
 * made again 1200 ticks after the build.
 */
public class QaGlassPanesCreate1115bCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final int LOOK = 1000;
	private static final int AFTER = 1200;
	/** 8 sand fire 8 glass, 6 glass cut 16 panes: 6 x 8 = 16 x 3. */
	private static final Law PANES = Law.of("sand, glass and panes", Items.SAND, 8, Items.GLASS, 8, Items.GLASS_PANE, 3);

	/** Eight sand and one coal in the builder's chest, eight glass asked: all eight fired on that one coal, no coal left. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassPanes1115bEight"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassPanes1115bEight")
	public void eightSandOnOneCoalFireAllEightGlass(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 2, "qa1115beight"), Blocks.CHEST.defaultBlockState(),
			new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 8);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 8);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.COAL, 0);
			});
	}

	/** Nine sand and one coal, nine glass asked: the line is exactly one coal short and nothing is fired or lost meanwhile. */
	//$ gametest_ticks_batch AREA '1300' '"qaGlassPanes1115bNine"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaGlassPanes1115bNine")
	public void nineSandOnOneCoalAreExactlyOneCoalShort(GameTestHelper helper) {
		shortLine(helper, Blocks.GLASS, 3, 3, "qa1115bnine", "Can't be made yet: short of 1 Coal to make 9 Glass", 9, 1,
			new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 1));
	}

	/** Five sand and a coal, sixteen panes asked (six glass): the line is exactly one sand short, nothing used meanwhile. */
	//$ gametest_ticks_batch AREA '1300' '"qaGlassPanes1115bFive"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaGlassPanes1115bFive")
	public void fiveSandForSixteenPanesAreExactlyOneSandShort(GameTestHelper helper) {
		shortLine(helper, Blocks.GLASS_PANE, 4, 4, "qa1115bfive", "Can't be made yet: short of 1 Sand to make 16 Glass Panes", 5, 1,
			new ItemStack(Items.SAND, 5), new ItemStack(Items.COAL, 1));
	}

	/** No sand, two coal, thirty-two panes asked (twelve glass): the line names the twelve sand, never Create's glass. */
	//$ gametest_ticks_batch AREA '1300' '"qaGlassPanes1115bNoSand"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaGlassPanes1115bNoSand")
	public void withoutSandThirtyTwoPanesNameTwelveSand(GameTestHelper helper) {
		shortLine(helper, Blocks.GLASS_PANE, 4, 8, "qa1115bnosand", "Can't be made yet: short of 12 Sand to make 32 Glass Panes", 0, 2,
			new ItemStack(Items.COAL, 2));
	}

	/**
	 * Twelve sand and three coal in the builder's chest, thirty-two panes asked: twelve glass fired on exactly two coal
	 * (ceil(12 / 8)), the spare coal left, 32 panes and no glass left over.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaGlassPanes1115bTwelve"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaGlassPanes1115bTwelve")
	public void thirtyTwoPanesFromTwelveSandBurnTwoCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 8, "qa1115btwelve"), Blocks.CHEST.defaultBlockState(),
			new ItemStack(Items.SAND, 12), new ItemStack(Items.COAL, 3));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(PANES), (start, end) -> {
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 12);
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 2);
			QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 32);
			QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 0);
		});
	}

	/**
	 * Twelve sand and one coal, thirty-two panes asked: the twelve glass need a second coal, so the builder's line names one
	 * coal (whether he made the first sixteen panes or not), never sand or Create's glass, and the build isn't finished.
	 */
	//$ gametest_ticks_batch AREA '2300' '"qaGlassPanes1115bCoalShort"'
	@GameTest(template = AREA, timeoutTicks = 2300, batch = "qaGlassPanes1115bCoalShort")
	public void thirtyTwoPanesOnOneCoalAreOneCoalShort(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 8, "qa1115bcoalshort"), Blocks.CHEST.defaultBlockState(),
			new ItemStack(Items.SAND, 12), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.onEachTick(() -> {
			int sand = count(helper, Items.SAND, Blocks.AIR);
			int glass = count(helper, Items.GLASS, Blocks.GLASS);
			int panes = count(helper, Items.GLASS_PANE, Blocks.GLASS_PANE);
			if (sand * 8 + glass * 8 + panes * 3 > 12 * 8) {
				throw new GameTestAssertException("sand " + sand + ", glass " + glass + ", panes " + panes + " add up to more than the 12 sand there were: doubled");
			}
		});
		helper.runAfterDelay(2200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.matches("Can't be made yet: short of 1 Coal to make (16|32) Glass Panes"),
				"the builder's line: '" + text + "', expected 'Can't be made yet: short of 1 Coal to make 32 Glass Panes' (or 16, once the first 16 were made)");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "32 panes were built from 12 sand on one coal");
			int coal = count(helper, Items.COAL, Blocks.AIR);
			helper.assertTrue(coal <= 1, coal + " coal, more than the one there was");
			helper.succeed();
		});
	}

	/**
	 * Eight of Create's Tiled Glass beside six sand and a coal in the builder's chest, sixteen panes asked. Create's ways back
	 * to plain panes "come last", so the panes are fired from the sand on the one coal and the tiled glass is still all there:
	 * not melted down nor counted as sand.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassPanes1115bTiled"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassPanes1115bTiled")
	public void tiledGlassInTheChestIsLeftAloneWhileThereIsSand(GameTestHelper helper) {
		Item tiled = block("create:tiled_glass").asItem();
		Item tiledPane = block("create:tiled_glass_pane").asItem();
		helper.assertTrue(tiled != Items.AIR && tiledPane != Items.AIR, "setup: Create's tiled glass isn't loaded");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 4, "qa1115btiled"), Blocks.CHEST.defaultBlockState(),
			new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1), new ItemStack(tiled, 8));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		Law all = Law.of("sand, glass, tiled glass and panes", Items.SAND, 8, Items.GLASS, 8, tiled, 8, Items.GLASS_PANE, 3, tiledPane, 3);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(all, Law.of("tiled glass", tiled, 1)), (start, end) -> {
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 6);
			QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
			QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS_PANE, 16);
			QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, tiled, 8);
			QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, tiledPane, 0);
		});
	}

	/**
	 * Twelve sand and three coal in his own chest, thirty-two panes asked; he is saved and loaded while firing and while
	 * carrying. The 32 panes are built once on exactly two coal, and 1200 ticks after the build nothing more is made: the
	 * pane count, his made count and the coal stay put.
	 */
	//$ gametest_ticks_batch AREA '8000' '"qaGlassPanes1115bReload"'
	@GameTest(template = AREA, timeoutTicks = 8000, batch = "qaGlassPanes1115bReload")
	public void reloadedWhileFiringThePanesComeOnceOnTwoCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS_PANE, 4, 8, "qa1115breload"), Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 12), new ItemStack(Items.COAL, 3));
		ServerLevel level = helper.getLevel();
		UUID id = blower.getUUID();
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		AtomicLong doneAt = new AtomicLong(-1);
		AtomicInteger madeAtDone = new AtomicInteger();
		long t0 = level.getGameTime();
		helper.onEachTick(() -> {
			long t = level.getGameTime() - t0;
			if (level.getEntity(id) instanceof Villager v) {
				CrafterJob job = ModAttachments.CRAFTER_JOB.get(v);
				if (job != null && !crafting.get() && job.phase() == CrafterJob.Phase.CRAFTING) {
					crafting.set(true);
					reload(level, v);
					return;
				} else if (job != null && !delivering.get() && job.phase() == CrafterJob.Phase.DELIVERING) {
					delivering.set(true);
					reload(level, v);
					return;
				}
			}
			int sand = count(helper, Items.SAND, Blocks.AIR);
			int glass = count(helper, Items.GLASS, Blocks.GLASS);
			int panes = count(helper, Items.GLASS_PANE, Blocks.GLASS_PANE);
			if (sand * 8 + glass * 8 + panes * 3 > 12 * 8) {
				throw new GameTestAssertException("at tick " + t + " sand " + sand + ", glass " + glass + ", panes " + panes
					+ " add up to more than the 12 sand there were: doubled");
			}
			if (BuildSiteManager.get(level).get(a.site().id()) != null) {
				return;
			}
			int made = level.getEntity(id) instanceof Villager v ? ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0) : -1;
			if (doneAt.get() < 0) {
				doneAt.set(t);
				madeAtDone.set(made);
				return;
			}
			helper.assertTrue(made == madeAtDone.get(), "his made count went from " + madeAtDone.get() + " to " + made + ", " + (t - doneAt.get())
				+ " ticks after the build: he made more");
			if (t - doneAt.get() < AFTER) {
				return;
			}
			helper.assertTrue(crafting.get() && delivering.get(), "the reloads never happened (firing " + crafting.get() + ", carrying "
				+ delivering.get() + "), so the reload wasn't tested");
			helper.assertTrue(panes == 32, panes + " glass panes in the area " + AFTER + " ticks after the build, expected 32");
			helper.assertTrue(glass == 0 && sand == 0, "glass " + glass + " and sand " + sand + " left, expected 0 and 0 (12 sand fired once)");
			int coal = count(helper, Items.COAL, Blocks.AIR);
			helper.assertTrue(coal == 1, coal + " coal left of 3, expected 1 (12 glass fired take ceil(12 / 8) = 2)");
			helper.assertTrue(made >= 1, "his made count says he made nothing");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	/**
	 * A builder whose chest holds {@code has}, asking for {@code dx} x {@code dz} of {@code block}: after LOOK ticks his line is
	 * {@code expected} word for word, the build isn't done, and {@code sand} sand and {@code coal} coal are all still there.
	 */
	private static void shortLine(GameTestHelper helper, Block block, int dx, int dz, String what, String expected, int sand, int coal,
		ItemStack... has) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, block, dx, dz, what),
			Blocks.CHEST.defaultBlockState(), has);
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(LOOK, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', the spec's: '" + expected + "'");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "the build was finished though it was short");
			int sandNow = count(helper, Items.SAND, Blocks.AIR);
			int coalNow = count(helper, Items.COAL, Blocks.AIR);
			helper.assertTrue(sandNow == sand && coalNow == coal, "sand " + sandNow + " and coal " + coalNow + " while waiting, expected " + sand + " and "
				+ coal + ": something was used for a job that can't be finished");
			helper.succeed();
		});
	}

	/**
	 * {@code item} in the builder's chest, the Glassblower's chest, every villager's inventory and bag, loose on the ground,
	 * and {@code placed} blocks in the blueprint's rows.
	 */
	private static int count(GameTestHelper helper, Item item, Block placed) {
		int n = 0;
		for (BlockPos rel : List.of(CHEST, QaCrafterOwnModdedStore1111eCompatTests.OWN)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16))) {
			n += v.getInventory().countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		if (placed != Blocks.AIR) {
			for (int x = 0; x < 4; x++) {
				for (int z = 0; z < 8; z++) {
					if (helper.getBlockState(ROW.offset(x, 0, z)).is(placed)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	private static void reload(ServerLevel level, Villager v) {
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		Village.forget(copy);
	}
}
