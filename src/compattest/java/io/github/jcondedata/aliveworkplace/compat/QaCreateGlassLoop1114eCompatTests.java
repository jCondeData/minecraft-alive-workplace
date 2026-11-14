package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Law;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-14): B1196 and B1197 verified from the spec (glassblower.md: "a coal or charcoal for every eight
 * fired (nine glass take two coal)"; with Create "eight glass take one coal, not two, and a builder with no sand is told
 * 'short of 4 Sand to make 4 Glass', never Create's Tiled Glass"; the fix: loop-back recipes are "still tried when the
 * others can't be made"), with the compat suite's mods (Create among them). The coal boundaries from both sides (9, 16,
 * 17), the no-sand-no-coal line, only Tiled Glass to hand, sand in the builder's Create Item Vault, and a reload mid-job.
 * Glass panes (a two-step loop through Create's Tiled Glass Pane) fail: B1255, QaCreateGlassLoop1114eBugCompatTests on tests/create-glass-pane-loop-1114e. Set-ups reuse
 * QaCrafterOwnModdedStore1111eCompatTests; each test has its own batch.
 */
public class QaCreateGlassLoop1114eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final int LOOK = 1000;

	private static Item item(String id) {
		Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
		if (i == Items.AIR) {
			throw new GameTestAssertException("setup: no item " + id);
		}
		return i;
	}

	/** n sand and the given coal in the Glassblower's own chest, n glass asked: n sand and {@code coal} coal fired. */
	private static void fire(GameTestHelper helper, String what, int dx, int dz, int sand, int coalGiven, int coalUsed) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, dx, dz, what),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, sand), new ItemStack(Items.COAL, coalGiven));
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, dx * dz);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, coalUsed);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, dx * dz);
			});
	}

	/** Nine glass take two coal (the wiki's own example), with Create's glass recipes loaded. */
	//$ gametest_ticks_batch AREA '5000' '"qaGlassLoop1114eNine"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaGlassLoop1114eNine")
	public void nineGlassBurnTwoCoal(GameTestHelper helper) {
		fire(helper, "nine", 3, 3, 9, 3, 2);
	}

	/** Sixteen glass, two full eights: exactly two coal, not three or four. */
	//$ gametest_ticks_batch AREA '6000' '"qaGlassLoop1114eSixteen"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaGlassLoop1114eSixteen")
	public void sixteenGlassBurnTwoCoal(GameTestHelper helper) {
		fire(helper, "sixteen", 4, 4, 16, 4, 2);
	}

	/** Seventeen glass, one over two eights: three coal. */
	//$ gametest_ticks_batch AREA '6000' '"qaGlassLoop1114eSeventeen"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaGlassLoop1114eSeventeen")
	public void seventeenGlassBurnThreeCoal(GameTestHelper helper) {
		fire(helper, "seventeen", 17, 1, 17, 5, 3);
	}

	/** No sand and no coal anywhere, 4 glass asked: the spec's line word for word, naming sand and coal, never Create's glass. */
	//$ gametest_ticks_batch AREA '1100' '"qaGlassLoop1114eNothing"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGlassLoop1114eNothing")
	public void noSandNoCoalLineNamesSandAndCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 1, "nothing"),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		helper.runAfterDelay(LOOK, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue("Can't be made yet: short of 4 Sand, 1 Coal to make 4 Glass".equals(text), "the builder's line: '" + text
				+ "', the spec's: 'Can't be made yet: short of 4 Sand, 1 Coal to make 4 Glass'");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made something from nothing");
			helper.succeed();
		});
	}

	/**
	 * Only Create's Tiled Glass (4) and a coal in the builder's chest, no sand anywhere, 4 glass asked: the loop-back recipe
	 * is still tried when the others can't be made, so the tiled glass is fired back into 4 glass on one coal and built.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassLoop1114eTiled"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassLoop1114eTiled")
	public void onlyTiledGlassToHandIsFiredBackIntoGlass(GameTestHelper helper) {
		Item tiled = item("create:tiled_glass");
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 1, "tiled"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(tiled, 4), new ItemStack(Items.COAL, 1));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(Law.of("tiled glass and glass", tiled, 1, Items.GLASS, 1)),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, tiled, 4);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 4);
			});
	}

	/** 8 sand and 2 coal in the builder's own Create Item Vault (no chest), the Glassblower has nothing: 8 glass on one coal. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassLoop1114eVault"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassLoop1114eVault")
	public void eightSandInTheBuildersCreateVaultBurnOneCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 2, "vault"),
			QaCrafterOwnModdedStore1111eCompatTests.vaultState(), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.STATION, ModBlocks.GLASS_KILN);
		Villager blower = helper.spawn(EntityType.VILLAGER, QaCrafterOwnModdedStore1111eCompatTests.STATION.south());
		Jobs.employ(helper.getLevel(), blower, helper.absolutePos(QaCrafterOwnModdedStore1111eCompatTests.STATION), ModVillagers.GLASS_KILN_POI,
			ModVillagers.GLASSBLOWER);
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 8);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 8);
			});
	}

	/** 8 sand and 2 coal in his own chest, 8 glass asked, the Glassblower saved and loaded while making and while carrying: one coal. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassLoop1114eReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassLoop1114eReload")
	public void eightGlassReloadedMidJobBurnOneCoal(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.GLASS, 4, 2, "reload"),
			Blocks.CHEST.defaultBlockState());
		helper.setBlock(QaCrafterOwnModdedStore1111eCompatTests.OWN, Blocks.CHEST);
		Villager blower = QaCrafterOwnModdedStore1111eCompatTests.glassblower(helper, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		AtomicBoolean[] seen = reloads(helper, blower.getUUID());
		QaCrafterOwnModdedStore1111eCompatTests.watch(helper, a, blower.getUUID(), List.of(QaCrafterOwnModdedStore1111eCompatTests.SAND_AND_GLASS),
			(start, end) -> {
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.SAND, 8);
				QaCrafterOwnModdedStore1111eCompatTests.used(helper, start, end, Items.COAL, 1);
				QaCrafterOwnModdedStore1111eCompatTests.exactly(helper, end, Items.GLASS, 8);
				helper.assertTrue(seen[0].get(), "never reloaded while making, so the reload wasn't tested");
				helper.assertTrue(seen[1].get(), "never reloaded while carrying, so the reload wasn't tested");
			});
	}

	/** Saves and loads the crafter the first time he is making, and the first time he carries what he made. */
	private static AtomicBoolean[] reloads(GameTestHelper helper, UUID crafter) {
		AtomicBoolean crafting = new AtomicBoolean();
		AtomicBoolean delivering = new AtomicBoolean();
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (!(level.getEntity(crafter) instanceof Villager v)) {
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
		return new AtomicBoolean[] {crafting, delivering};
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
