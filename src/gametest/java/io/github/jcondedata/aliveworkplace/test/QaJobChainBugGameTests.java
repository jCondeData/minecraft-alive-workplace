package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-c-1024-2152), ROADMAP 37.14a "say what is short" (bug tests, failing on the code they were written against).
 * A Tinkerer with all the raw iron for a builder's iron bars
 * but no coal to fire it. Like the Mason's kiln (B352), the fuel is a raw material of the chain: nothing should be
 * taken and the builder's line should name the coal, not claim that nobody makes iron bars.
 * A Librarian one leather short of its last bookshelf should name the leather, not the rabbit hide that makes it
 * (B408, B409).
 */
public class QaJobChainBugGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	//$ gametest_ticks_batch AREA '1400' '"qaChainTinkererNoCoal"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaChainTinkererNoCoal")
	public void aTinkererWithOreButNoCoalNamesTheCoalAndTakesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.IRON_BARS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qachain_nocoal_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(ROW.offset(x, 0, 0), Blocks.AIR);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.RAW_IRON, 6));
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.setBlock(STATION, ModBlocks.TINKERS_BENCH);
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, tinkerer, helper.absolutePos(STATION), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), site.id(), site.missing())); // the builder starts waiting about now
		helper.runAfterDelay(1000, () -> {
			Component line = JobShortages.line(helper.getLevel(), site.id(), site.missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 6, "the ore should stay in the chest, " + chest.countItem(Items.RAW_IRON) + " there");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the tinkerer is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).stacks());
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of") && text.contains("Coal"),
				"the builder's line should name the missing coal (the tinkerer makes iron bars, it just can't fire the ore): " + text);
			helper.succeed();
		});
	}

	/**
	 * Eight leather for three bookshelves (each takes three): the Librarian makes the two it can, and for the last it is
	 * exactly one leather short: it takes none of the cane, leather or planks left, makes no paper or books it can't
	 * finish, and the builder's line says "short of 1 Leather to make 1 Bookshelf" (not "4 Rabbit Hide": two leather are
	 * right there, one more is what's short).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainLibrarianShort"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainLibrarianShort")
	public void aLibrarianOneLeatherShortOfTheLastShelfTakesNothingAndNamesIt(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.BOOKSHELF, new ItemStack(Items.SUGAR_CANE, 27), new ItemStack(Items.LEATHER, 8), new ItemStack(Items.OAK_PLANKS, 18));
		Villager librarian = QaJobChainsGameTests.librarian(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(a.site().missing().getOrDefault(Items.BOOKSHELF, 0) == 1, "two shelves should be built, still missing " + a.site().missing());
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 1 Leather to make 1 Bookshelf"),
				"the builder's shortage line: " + (line == null ? null : line.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SUGAR_CANE) == 9 && chest.countItem(Items.LEATHER) == 2 && chest.countItem(Items.OAK_PLANKS) == 6,
				"the last shelf's makings should be left whole: " + chest.countItem(Items.SUGAR_CANE) + " cane, " + chest.countItem(Items.LEATHER) + " leather, "
					+ chest.countItem(Items.OAK_PLANKS) + " planks");
			helper.assertTrue(chest.countItem(Items.PAPER) == 0 && chest.countItem(Items.BOOK) == 0, "something was half made: "
				+ chest.countItem(Items.PAPER) + " paper, " + chest.countItem(Items.BOOK) + " books");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(librarian).isEmpty(), "the librarian is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(librarian).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(librarian) == null, "the librarian holds a job it can't finish");
		});
	}
}
