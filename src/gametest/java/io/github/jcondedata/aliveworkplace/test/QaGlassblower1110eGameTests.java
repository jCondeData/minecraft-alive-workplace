package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA, lane e (2026-11-10), ROADMAP 46.6 the Glassblower, written from the spec: saves (reloaded at every phase of a job,
 * the sand, coal and glass counted to the block), the Glass Kiln broken mid-job (put back, or gone for good: nothing lost),
 * Milestone 37 closed (the owner's live server today: the jobs plan without chains), seven sand for eight glass, red sand,
 * a player's furnace with coal in it never used as his fuel or his kiln, and the 46.6 sentences as a player reads them.
 */
public class QaGlassblower1110eGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan) {
	}

	/**
	 * Nine glass from nine sand on three coal, the Glassblower saved and loaded once in each phase of the job he reaches
	 * (fetching, firing, delivering): the build finishes with exactly one coal left (two burned, not four), no sand, and
	 * exactly nine glass in the whole test (none doubled by a reload, none lost).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassReloadEveryPhase"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassReloadEveryPhase")
	public void reloadedInEveryPhaseHeNeitherDoublesNorLosesGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS, 9, "everyphase"), new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 3));
		AtomicReference<Villager> blower = new AtomicReference<>(glassblower(helper));
		AtomicReference<CrafterJob.Phase> last = new AtomicReference<>();
		AtomicInteger reloads = new AtomicInteger();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(blower.get());
			if (job == null || job.phase() == last.get()) {
				return;
			}
			last.set(job.phase());
			int before = total(helper, Items.GLASS) + total(helper, Items.SAND);
			Villager copy = reload(helper.getLevel(), blower.get());
			blower.set(copy);
			reloads.incrementAndGet();
			int after = total(helper, Items.GLASS) + total(helper, Items.SAND);
			helper.assertTrue(before == after, "sand and glass changed across the reload in " + job.phase() + ": " + before + " -> " + after);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Glassblower in " + job.phase());
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(reloads.get() >= 2, "reloaded only " + reloads.get() + " time(s); last phase " + last.get());
			helper.assertTrue(total(helper, Items.SAND) == 0, total(helper, Items.SAND) + " sand left somewhere");
			helper.assertTrue(total(helper, Items.COAL) == 1, "coal left (9 fired, 2 burned): " + total(helper, Items.COAL));
			helper.assertTrue(total(helper, Items.GLASS) == 9, "glass in the whole test (9 placed, none spare): " + total(helper, Items.GLASS));
		});
	}

	/**
	 * The Glass Kiln broken while he fires, then put back a moment later (before he gives up on the job): he carries on and
	 * the panes are built from the same six sand and one coal, nothing doubled.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaGlassKilnBackSoon"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaGlassKilnBackSoon")
	public void theKilnBrokenAndPutBackHeStillMakesThePanes(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "kilnback"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		AtomicBoolean broken = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(blower);
			if (broken.get() || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			broken.set(true);
			helper.setBlock(STATION, Blocks.AIR);
			helper.runAfterDelay(100, () -> helper.setBlock(STATION, ModBlocks.GLASS_KILN));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.get(), "he never started firing");
			assertBuilt(helper, a);
			helper.assertTrue(total(helper, Items.SAND) == 0 && total(helper, Items.COAL) == 0,
				"the makings weren't used: " + total(helper, Items.SAND) + " sand, " + total(helper, Items.COAL) + " coal");
			helper.assertTrue(total(helper, Items.GLASS_PANE) == 16, "panes in the whole test (1 placed + 15): " + total(helper, Items.GLASS_PANE));
		});
	}

	/**
	 * The Glass Kiln broken while he fires and not put back: once he gives the job up, the sand and coal in his bag are back
	 * in the builder's chest (or at his feet), nothing vanished and no glass came out of a kiln that isn't there.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaGlassKilnGone"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaGlassKilnGone")
	public void theKilnBrokenForGoodLosesNoSand(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS, 1, "kilngone"), new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		AtomicBoolean broken = new AtomicBoolean();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(blower);
			if (broken.get() || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			broken.set(true);
			helper.setBlock(STATION, Blocks.AIR);
			helper.runAfterDelay(1500, () -> {
				helper.assertTrue(ModAttachments.CRAFTER_JOB.get(blower) == null, "the job outlived the kiln by 1500 ticks");
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(blower).isEmpty(), "his bag still holds " + ModAttachments.BUILDER_BAG.getOrCreate(blower).stacks());
				int sand = total(helper, Items.SAND);
				int glass = total(helper, Items.GLASS);
				helper.assertTrue(sand + glass == 8, "sand and glass in the whole test: " + sand + " + " + glass + " (8 given)");
				helper.assertTrue(total(helper, Items.COAL) <= 1, total(helper, Items.COAL) + " coal from 1");
				helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null || glass > 0, "the build finished without glass");
				helper.succeed();
			});
		});
	}

	/** Seven sand for eight glass: whatever he fires, no glass is made from sand that isn't there, and the line names the sand. */
	//$ gametest_ticks_batch AREA '2600' '"qaGlassSevenSand"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaGlassSevenSand")
	public void sevenSandNeverMakesEightGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS, 8, "sevensand"), new ItemStack(Items.SAND, 7), new ItemStack(Items.COAL, 1));
		glassblower(helper);
		helper.runAfterDelay(2300, () -> {
			int sand = total(helper, Items.SAND);
			int glass = total(helper, Items.GLASS);
			helper.assertTrue(sand + glass == 7, "sand and glass in the whole test: " + sand + " + " + glass + " (7 sand given)");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "eight glass built from seven sand");
			String text = line(helper, a);
			helper.assertTrue(text != null && text.contains("Sand"), "the builder's line doesn't name the sand: " + text);
			helper.succeed();
		});
	}

	/** Red sand fires to glass as well as sand does (any sand a furnace takes). */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassRedSand"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassRedSand")
	public void redSandMakesGlassToo(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS, 1, "redsand"), new ItemStack(Items.RED_SAND, 1), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(total(helper, Items.RED_SAND) == 0, total(helper, Items.RED_SAND) + " red sand left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/**
	 * A player's furnaces, one by the builder's bench and one by the kiln, with coal in their fuel slots and sand in their
	 * input: he never takes their coal or sand (nor fires in them); with no coal in the chests the line names the coal.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaGlassPlayersFurnace"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGlassPlayersFurnace")
	public void aPlayersFurnaceAndItsCoalAreNeverHis(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "furnace"), new ItemStack(Items.SAND, 6));
		Villager blower = glassblower(helper);
		BlockPos byBench = BENCH.east();
		BlockPos byKiln = STATION.west();
		// Each furnace holds coal or sand but not both, so it never burns by itself: whatever leaves it, he took.
		for (BlockPos p : List.of(byBench, byKiln)) {
			helper.setBlock(p, Blocks.FURNACE);
			helper.setBlock(p.above(), Blocks.FURNACE);
			((Container) helper.getBlockEntity(p)).setItem(1, new ItemStack(Items.COAL, 2));
			((Container) helper.getBlockEntity(p.above())).setItem(0, new ItemStack(Items.SAND, 4));
		}
		helper.runAfterDelay(1100, () -> {
			for (BlockPos p : List.of(byBench, byKiln)) {
				Container fuel = helper.getBlockEntity(p);
				Container input = helper.getBlockEntity(p.above());
				helper.assertTrue(fuel.getItem(1).is(Items.COAL) && fuel.getItem(1).getCount() == 2, "the furnace's coal at " + p + ": " + fuel.getItem(1));
				helper.assertTrue(input.getItem(0).is(Items.SAND) && input.getItem(0).getCount() == 4, "the furnace's sand at " + p.above() + ": " + input.getItem(0));
				helper.assertTrue(fuel.getItem(0).isEmpty() && fuel.getItem(2).isEmpty() && input.getItem(1).isEmpty() && input.getItem(2).isEmpty(),
					"something was put in a player's furnace at " + p);
			}
			helper.assertTrue(blower.getVillagerData().getProfession() == ModVillagers.GLASSBLOWER, "he's a " + blower.getVillagerData().getProfession());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			String text = line(helper, a);
			helper.assertTrue("Can't be made yet: short of 1 Coal to make 1 Glass Pane".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * Milestone 37 closed, as on the owner's live server today (no saved chains): a builder's glass pane is still made from
	 * six sand and a coal, the glass fired and then the panes.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaGlassM37Closed"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGlassM37Closed")
	public void withMilestone37ClosedPanesAreStillMadeFromSand(GameTestHelper helper) {
		prepare(helper);
		closeM37(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "m37"), new ItemStack(Items.SAND, 6), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			helper.assertFalse(Expansions.isOpen("M37"), "Milestone 37 opened again mid-test");
			assertBuilt(helper, a);
			helper.assertTrue(total(helper, Items.SAND) == 0 && total(helper, Items.COAL) == 0,
				"the panes weren't made from the sand and coal: " + total(helper, Items.SAND) + " sand, " + total(helper, Items.COAL) + " coal left");
			helper.assertTrue(total(helper, Items.GLASS_PANE) == 16, "panes in the whole test (1 placed + 15): " + total(helper, Items.GLASS_PANE));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/** Milestone 37 closed: sand but no coal, he takes nothing and makes nothing, and the builder's line is never a wrong one. */
	//$ gametest_ticks_batch AREA '1400' '"qaGlassM37ClosedNoFuel"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGlassM37ClosedNoFuel")
	public void withMilestone37ClosedNoFuelTakesNothing(GameTestHelper helper) {
		prepare(helper);
		closeM37(helper);
		Asker a = asker(helper, blueprint(helper, Blocks.GLASS_PANE, 1, "m37nofuel"), new ItemStack(Items.SAND, 6));
		Villager blower = glassblower(helper);
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(((Container) helper.getBlockEntity(CHEST)).countItem(Items.SAND) == 6, "the sand went from the chest");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) == 0, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
			// The shortage lines are Milestone 37's (37.14a, noted only by chain planning): closed, there's no line, and
			// never a wrong one ("Hire a Glassblower" with one at work, or "Nobody here makes").
			String text = line(helper, a);
			helper.assertTrue(text == null || "Can't be made yet: short of 1 Coal to make 1 Glass Pane".equals(text), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/**
	 * The 46.6 sentences as a player reads them, with their real arguments: the kiln and the job named, the status title,
	 * the Steward's reason, the workshop's name; every one translated and written in American English.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGlassblowersSentencesReadRight(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String[][] want = {
			{"block.aliveworkplace.glass_kiln", "Glass Kiln"},
			{"entity.minecraft.villager.glassblower", "Glassblower"},
			{"entity.minecraft.zombie_villager.glassblower", "Zombie Glassblower"},
			{"plural.aliveworkplace.profession.aliveworkplace.glassblower", "Glassblowers"},
			{"plural.aliveworkplace.aliveworkplace.glass_kiln", "Glass Kilns"},
			{"blueprint.aliveworkplace.glassblowers_workshop", "Glassblower's Workshop"},
		};
		for (String[] w : want) {
			helper.assertTrue(lang.has(w[0]), "no text for " + w[0]);
			helper.assertTrue(w[1].equals(lang.getOrDefault(w[0])), w[0] + " reads " + lang.getOrDefault(w[0]));
		}
		String title = Component.translatable("message.aliveworkplace.glassblower.title", 12).getString();
		helper.assertTrue("Glassblower · 12 made".equals(title), "the status title reads " + title);
		String why = Component.translatable("steward.aliveworkplace.why.glassblower", "Oakvale", 14).getString();
		helper.assertTrue("14 villagers and nobody to make glass for the builders".equals(why), "the Steward's reason reads " + why);
		for (String key : List.of("message.aliveworkplace.glassblower.title", "steward.aliveworkplace.why.glassblower",
				"blueprint.aliveworkplace.glassblowers_workshop", "block.aliveworkplace.glass_kiln")) {
			String text = lang.getOrDefault(key).toLowerCase(java.util.Locale.ROOT);
			for (String british : List.of("colour", "defence", "armour", "jewell", "cancelled", "grey", "centre", "metre")) {
				helper.assertFalse(text.contains(british), key + " is British English: " + text);
			}
		}
		helper.succeed();
	}

	// ---- helpers ----

	/** Closes Milestone 37 for this test (the owner's server), opened again when the test ends whatever happens. */
	private static void closeM37(GameTestHelper helper) {
		boolean was = Expansions.openForTests;
		Leftovers.after(helper, () -> Expansions.openForTests = was);
		Expansions.openForTests = false;
		helper.assertFalse(Expansions.isOpen("M37"), "Milestone 37 is still open with the test switch off");
	}

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/**
	 * Every {@code item} in the test: in its containers, the villagers' bags and inventories, on the ground, and placed as a
	 * block (glass and panes in the blueprint's square).
	 */
	private static int total(GameTestHelper helper, Item item) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(2);
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			if (level.getBlockEntity(p) instanceof Container c) {
				n += c.countItem(item);
			}
			if (item instanceof net.minecraft.world.item.BlockItem bi && level.getBlockState(p).is(bi.getBlock())) {
				n++;
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(8), Villager::isAlive)) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			n += v.getInventory().countItem(item);
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** A blueprint of {@code n} (1 to 9) {@code block}s on the floor, in a 3 x 3 square (one block alone for 1). */
	private static ResourceLocation blueprint(GameTestHelper helper, Block block, int n, String what) {
		int side = n == 1 ? 1 : 3;
		int placed = 0;
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				if (placed++ < n) {
					helper.setBlock(ROW.offset(x, 0, z), block);
				}
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa_glass_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(side, 1, side), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	private static Asker asker(GameTestHelper helper, ResourceLocation id, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan);
	}

	/** A Glassblower at his Glass Kiln, with a chest by it. */
	private static Villager glassblower(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.GLASS_KILN);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.GLASS_KILN_POI, ModVillagers.GLASSBLOWER);
		return v;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
