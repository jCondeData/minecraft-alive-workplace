package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-14, verifying B1196 and B1197 with the pack's mods, Create among them): the Glassblower's shortage line
 * names the Sand for other counts than 4, and his coal is "a coal for every eight" for 9 and 16 glass, across a reload,
 * and Create's Tiled Glass is never part of a glass -> tiled -> glass loop. Spec: glassblower.md (46.6).
 */
public class QaGlassblowerCreateVerify1114fCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	/** No sand anywhere, a coal in the builder's chest, one glass asked: "short of 1 Sand to make 1 Glass". */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassCv1114fOne"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassCv1114fOne")
	public void oneGlassLineNamesOneSand(GameTestHelper helper) {
		lineIs(helper, 1, 1, "one", "Can't be made yet: short of 1 Sand to make 1 Glass");
	}

	/** No sand anywhere, two coal in the builder's chest, nine glass asked: "short of 9 Sand to make 9 Glass" (no coal short, no Create glass). */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassCv1114fNine"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassCv1114fNine")
	public void nineGlassLineNamesNineSand(GameTestHelper helper) {
		lineIs(helper, 3, 3, "nine", "Can't be made yet: short of 9 Sand to make 9 Glass", new ItemStack(Items.COAL, 2));
	}

	/** Nine sand and three coal in his own chest, nine glass asked: nine sand and exactly two coal fired ("nine glass take two coal"). */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassCv1114fNineCoal"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassCv1114fNineCoal")
	public void nineGlassBurnTwoCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 3, 3, "ninecoal"), Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 3));
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 9);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 2);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 9);
			});
	}

	/**
	 * Sixteen sand and three coal in his own chest, sixteen glass asked, and he is saved and loaded while firing and while
	 * carrying: sixteen sand and exactly two coal used, sixteen glass built.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaGlassCv1114fSixteen"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaGlassCv1114fSixteen")
	public void sixteenGlassBurnTwoCoalAcrossAReload(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 4, "sixteen"), Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 16), new ItemStack(Items.COAL, 3));
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		ServerLevel level = helper.getLevel();
		java.util.UUID id = blower.getUUID();
		helper.onEachTick(() -> {
			if (!(level.getEntity(id) instanceof Villager v)) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(v);
			if (job == null) {
				return;
			}
			if (!crafting.get() && job.phase() == CrafterJob.Phase.CRAFTING) {
				crafting.set(true);
				reload(level, v);
			} else if (!delivering.get() && job.phase() == CrafterJob.Phase.DELIVERING) {
				delivering.set(true);
				reload(level, v);
			}
		});
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, id, List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 16);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 2);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 16);
				helper.assertTrue(crafting.get() && delivering.get(), "the reloads never happened (firing " + crafting.get() + ", carrying "
					+ delivering.get() + "), so the reload wasn't tested");
			});
	}

	/**
	 * Eight of Create's Tiled Glass and one coal in the builder's chest, no sand anywhere, eight glass asked. Whatever he
	 * does, there is no loop: either the line names the sand and the tiled glass and coal are untouched, or the tiled glass
	 * was fired once into the eight glass on one coal, and no glass or tiled glass appears beyond the eight there were.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaGlassCv1114fTiled"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaGlassCv1114fTiled")
	public void tiledGlassOnlyNeverLoops(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Item tiled = block("create:tiled_glass").asItem();
		helper.assertTrue(tiled != Items.AIR, "setup: Create's tiled glass isn't loaded");
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 2, "tiled"), Blocks.CHEST.defaultBlockState(),
			new ItemStack(tiled, 8), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.onEachTick(() -> {
			int glassAndTiled = count(helper, Items.GLASS) + count(helper, tiled) + placedGlass(helper);
			if (glassAndTiled > 8) {
				throw new GameTestAssertException("glass and tiled glass add up to " + glassAndTiled + ", more than the 8 tiled glass there were: a loop");
			}
		});
		helper.runAfterDelay(3000, () -> {
			boolean done = BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null;
			int coal = count(helper, Items.COAL);
			int tiledLeft = count(helper, tiled);
			if (done) {
				helper.assertTrue(placedGlass(helper) == 8, "build done but " + placedGlass(helper) + " glass placed, expected 8");
				helper.assertTrue(tiledLeft == 0 && coal == 0, "build done from tiled glass: " + tiledLeft + " tiled glass and " + coal
					+ " coal left, expected 0 and 0 (8 fired once on one coal)");
			} else {
				String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
				helper.assertTrue(text != null && text.contains("Sand") && !text.contains("Tiled") && !text.contains("Framed"),
					"the builder's line: '" + text + "', expected it to name the Sand");
				helper.assertTrue(tiledLeft + placedGlass(helper) + count(helper, Items.GLASS) == 8 && coal <= 1,
					"not done, yet tiled glass " + tiledLeft + ", glass " + count(helper, Items.GLASS) + " + placed " + placedGlass(helper)
					+ ", coal " + coal + ": something was fired in a loop");
			}
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	private static void lineIs(GameTestHelper helper, int dx, int dz, String what, String expected, ItemStack... builderHas) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		ItemStack[] items = builderHas.length == 0 ? new ItemStack[] {new ItemStack(Items.COAL, 1)} : builderHas;
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper,
			QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, dx, dz, "cv" + what), Blocks.CHEST.defaultBlockState(), items);
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(1000, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', the spec's: '" + expected + "'");
			helper.succeed();
		});
	}

	/** {@code item} in the builder's chest, the Glassblower's chest, every villager's inventory and loose on the ground in the area. */
	private static int count(GameTestHelper helper, Item item) {
		int n = 0;
		for (BlockPos rel : List.of(CHEST, QaCrafterOwnModdedStore1111eCompatTests.OWN)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(rel)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += v.getInventory().countItem(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int placedGlass(GameTestHelper helper) {
		int n = 0;
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 2; z++) {
				if (helper.getBlockState(ROW.offset(x, 0, z)).is(Blocks.GLASS)) {
					n++;
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
