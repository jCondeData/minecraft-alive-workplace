package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1111-0037) of ROADMAP 46.6, the Glassblower, from the spec ("fires glass from sand, a coal for every
 * eight") and the guide's promises, at the corners the earlier tests leave open: exactly eight sand on exactly one coal
 * (all eight fired, the second coal left), nine sand on one coal (nothing lost, the coal named), charcoal as the fuel
 * (the guide: "Each coal or charcoal fires eight"), and a builder with no chest of his own whose sand and coal are in the
 * village's Storehouse (the guide: "he fetches the makings from the builder's chests (and the village's)").
 */
public class QaGlassblower1111bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(13, 2, 2);
	private static final BlockPos ROW = new BlockPos(4, 2, 8);
	private static final BlockPos STOREHOUSE = new BlockPos(18, 2, 19);
	private static final BlockPos STORE_CHEST = new BlockPos(18, 2, 17);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan) {
	}

	/** Exactly eight sand on one coal (two in the chest) for eight glass: all eight fired on the one coal, the second left. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassEight1111b"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassEight1111b")
	public void eightSandFireOnExactlyOneCoal(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, row(helper, Blocks.GLASS, 8, 1, "eight"), true, new ItemStack(Items.SAND, 8), new ItemStack(Items.COAL, 2));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(total(helper, Items.SAND, blower, a.builder()) == 0, total(helper, Items.SAND, blower, a.builder()) + " sand left");
			helper.assertTrue(total(helper, Items.COAL, blower, a.builder()) == 1,
				"coal left after eight glass (one should burn, one stay): " + total(helper, Items.COAL, blower, a.builder()));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	/**
	 * Nine sand on a single coal for nine glass: the ninth needs a second coal. Nothing is lost on the way (the sand and
	 * the glass made from it always add up to nine, wherever they are), the build isn't finished, no more than the one
	 * coal is burnt, and the builder's line names the coal that's short.
	 */
	//$ gametest_ticks_batch AREA '2600' '"qaGlassNineOne1111b"'
	@GameTest(template = AREA, timeoutTicks = 2600, batch = "qaGlassNineOne1111b")
	public void nineSandOnOneCoalLoseNothingAndNameTheCoal(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, row(helper, Blocks.GLASS, 3, 3, "nineone"), true, new ItemStack(Items.SAND, 9), new ItemStack(Items.COAL, 1));
		Villager blower = glassblower(helper);
		helper.onEachTick(() -> {
			int sand = total(helper, Items.SAND, blower, a.builder());
			int glass = total(helper, Items.GLASS, blower, a.builder()) + placed(helper, Blocks.GLASS);
			if (sand + glass != 9) {
				throw new GameTestAssertException("sand and glass add up to " + (sand + glass) + " (" + sand + " sand, " + glass + " glass), not 9");
			}
			int coal = total(helper, Items.COAL, blower, a.builder());
			if (coal < 0 || coal > 1) {
				throw new GameTestAssertException(coal + " coal about");
			}
		});
		helper.runAfterDelay(2300, () -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "nine glass were built on one coal");
			helper.assertTrue(placed(helper, Blocks.GLASS) < 9, "all nine glass are placed");
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of 1 Coal to make "), "the builder's line: " + text);
			helper.assertTrue(text.endsWith(" Glass"), "the builder's line names something else: " + text);
			helper.succeed();
		});
	}

	/** Charcoal is fuel as coal is: a glass pane for a builder from six sand and one charcoal, the charcoal burnt. */
	//$ gametest_ticks_batch AREA '4000' '"qaGlassCharcoal1111b"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaGlassCharcoal1111b")
	public void charcoalFiresThePanesGlass(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, row(helper, Blocks.GLASS_PANE, 1, 1, "charcoal"), true, new ItemStack(Items.SAND, 6), new ItemStack(Items.CHARCOAL, 1));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(total(helper, Items.SAND, blower, a.builder()) == 0 && total(helper, Items.CHARCOAL, blower, a.builder()) == 0,
				"the panes weren't made from the sand and charcoal: " + total(helper, Items.SAND, blower, a.builder()) + " sand, "
					+ total(helper, Items.CHARCOAL, blower, a.builder()) + " charcoal left");
			helper.assertTrue(total(helper, Items.GLASS_PANE, blower, a.builder()) == 15,
				"the rest of the panes (16 made, 1 placed): " + total(helper, Items.GLASS_PANE, blower, a.builder()));
		});
	}

	/**
	 * A builder with no chest by his table, his sand and coal in the village's Storehouse: the Glassblower takes them from
	 * the Storehouse, the pane is built, and the fifteen panes left over are in the Storehouse, none on the ground.
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaGlassStore1111b"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaGlassStore1111b")
	public void aChestlessBuildersPanesComeFromTheStorehousesSand(GameTestHelper helper) {
		prepare(helper);
		Leftovers.buildSites(helper);
		boolean free = helper.getLevel().getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, helper.getLevel().getServer());
		Leftovers.after(helper, () -> helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, helper.getLevel().getServer()));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		store.setItem(0, new ItemStack(Items.SAND, 6));
		store.setItem(1, new ItemStack(Items.COAL, 1));
		ResourceLocation id = row(helper, Blocks.GLASS_PANE, 1, 1, "store");
		Villager builder = QaCarpenterStorehouse1101GameTests.builder(helper, BENCH);
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, builder, id, ROW);
		Asker a = new Asker(builder, site, site.plan(helper.getLevel()));
		Villager blower = glassblower(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(store.countItem(Items.SAND) == 0 && store.countItem(Items.COAL) == 0,
				"the Storehouse's makings weren't used: " + store.countItem(Items.SAND) + " sand, " + store.countItem(Items.COAL) + " coal left");
			helper.assertTrue(total(helper, Items.GLASS_PANE, blower, builder) == 15, "panes left over (16 made, 1 placed): " + total(helper, Items.GLASS_PANE, blower, builder));
			helper.assertTrue(onGround(helper, Items.GLASS_PANE) == 0 && onGround(helper, Items.GLASS) == 0, "glass lies on the ground");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0) >= 1, "the Glassblower made " + ModAttachments.ITEMS_CRAFTED.getOrElse(blower, 0));
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/** A blueprint of {@code dx} x {@code dz} {@code block}s on the floor at ROW, saved and the spot cleared again. */
	private static ResourceLocation row(GameTestHelper helper, Block block, int dx, int dz, String what) {
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaglass1111b_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(dx, 1, dz), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	private static Asker asker(GameTestHelper helper, ResourceLocation id, boolean chest, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		if (chest) {
			helper.setBlock(CHEST, Blocks.CHEST);
			Container container = helper.getBlockEntity(CHEST);
			for (int i = 0; i < items.length; i++) {
				container.setItem(i, items[i].copy());
			}
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Asker(builder, site, plan);
	}

	/** A Glassblower at his Glass Kiln, with an empty chest by it. */
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
			"still building: status=" + a.site().status() + " missing=" + a.site().missing() + "; line: " + line(helper, a));
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	/** How many {@code item} are in every container of the test area, the villagers' bags and inventories, and on the ground. */
	private static int total(GameTestHelper helper, Item item, Villager... villagers) {
		int n = onGround(helper, item);
		AABB box = helper.getBounds();
		for (BlockPos pos : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX - 1, (int) box.maxY - 1, (int) box.maxZ - 1)) {
			BlockEntity be = helper.getLevel().getBlockEntity(pos);
			if (be instanceof Container c) {
				n += c.countItem(item);
			}
		}
		for (Villager v : villagers) {
			if (v.isRemoved()) {
				continue;
			}
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			n += v.getInventory().countItem(item);
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static int placed(GameTestHelper helper, Block block) {
		int n = 0;
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				n += helper.getBlockState(ROW.offset(x, 0, z)).is(block) ? 1 : 0;
			}
		}
		return n;
	}
}
