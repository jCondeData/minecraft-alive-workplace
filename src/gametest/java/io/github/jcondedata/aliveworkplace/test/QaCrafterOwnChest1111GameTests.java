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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * B1170 (B1144/B1156 kin): every crafting job uses the makings kept in the chests by its own workstation for a builder's
 * request. Before, only the Cleric and the Glassblower took part in the village (Village.takesPart), so the Mason's,
 * Tinkerer's, Leatherworker's and Librarian's own chests were never a stash CrafterWork gathers from, and the builder
 * waited "short of" with the makings sitting in that crafter's chest. Each test: the builder's chest holds nothing the
 * crafter needs, the crafter's own chest holds the makings, and the asked thing is made from them and built.
 */
public class QaCrafterOwnChest1111GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	/** Where the Leatherworker's frame hangs: on the plank's south face. */
	private static final BlockPos FRAME = ROW.south();

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** Stone bricks in the chest by the Mason's stonecutter, none in the builder's: he cuts them into the builder's slabs. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnChestMason"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnChestMason")
	public void stoneBricksInTheMasonsOwnChestBecomeTheBuildersSlabs(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.STONE_BRICK_SLAB, 4, 1, "mason"));
		Villager mason = crafter(helper, Blocks.STONECUTTER, PoiTypes.MASON, VillagerProfession.MASON, new ItemStack(Items.STONE_BRICKS, 4));
		helper.succeedWhen(() -> {
			Container own = helper.getBlockEntity(STATION.east());
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "the slabs weren't made; his chest: "
				+ own.countItem(Items.STONE_BRICKS) + " stone bricks; the builder's line: " + line(helper, a));
			assertBuilt(helper, a);
			assertMadeFromOwn(helper, mason, own, Items.STONE_BRICKS, 4);
		});
	}

	/** Iron ingots in the chest by the Tinkerer's bench, none in the builder's: he makes the builder's iron bars from them. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnChestTinkerer"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnChestTinkerer")
	public void ironInTheTinkerersOwnChestBecomesTheBuildersBars(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.IRON_BARS, 3, 1, "tinkerer"));
		Villager tinkerer = crafter(helper, ModBlocks.TINKERS_BENCH, ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER,
			new ItemStack(Items.IRON_INGOT, 6));
		helper.succeedWhen(() -> {
			Container own = helper.getBlockEntity(STATION.east());
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "the bars weren't made; his chest: "
				+ own.countItem(Items.IRON_INGOT) + " iron ingots; the builder's line: " + line(helper, a));
			assertBuilt(helper, a);
			assertMadeFromOwn(helper, tinkerer, own, Items.IRON_INGOT, 6);
		});
	}

	/** Planks and books in the chest by the Librarian's lectern, none in the builder's: he makes the builder's bookshelf. */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnChestLibrarian"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnChestLibrarian")
	public void planksAndBooksInTheLibrariansOwnChestBecomeTheBuildersBookshelf(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, area(helper, Blocks.BOOKSHELF, 1, 1, "librarian"));
		Villager librarian = crafter(helper, Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN,
			new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.BOOK, 3));
		helper.succeedWhen(() -> {
			Container own = helper.getBlockEntity(STATION.east());
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "the bookshelf wasn't made; his chest: "
				+ own.countItem(Items.OAK_PLANKS) + " planks, " + own.countItem(Items.BOOK) + " books; the builder's line: " + line(helper, a));
			assertBuilt(helper, a);
			assertMadeFromOwn(helper, librarian, own, Items.BOOK, 3);
		});
	}

	/**
	 * Leather and sticks in the chest by the Leatherworker's cauldron, only the plank in the builder's: he makes the item
	 * frame the blueprint hangs on the plank (B1148), and it's hung.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaOwnChestLeather"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaOwnChestLeather")
	public void leatherAndSticksInTheLeatherworkersOwnChestBecomeTheBuildersFrame(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = framed(helper);
		Asker a = asker(helper, id, new ItemStack(Items.OAK_PLANKS, 4));
		Villager leatherworker = crafter(helper, Blocks.CAULDRON, PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER,
			new ItemStack(Items.LEATHER, 4), new ItemStack(Items.STICK, 64));
		UUID siteId = a.site().id();
		helper.succeedWhen(() -> {
			Container own = helper.getBlockEntity(STATION.east());
			BuildSite open = BuildSiteManager.get(helper.getLevel()).get(siteId);
			helper.assertTrue(open == null, "the frame wasn't made; his chest: " + own.countItem(Items.LEATHER) + " leather, "
				+ own.countItem(Items.STICK) + " sticks; still building: stage=" + a.site().stage() + " status=" + a.site().status()
				+ " missing=" + a.site().missing());
			helper.assertTrue(frames(helper) == 1, frames(helper) + " item frames hang on the plank");
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.OAK_PLANKS), "the plank isn't there");
			assertMadeFromOwn(helper, leatherworker, own, Items.LEATHER, 4);
		});
	}

	// ---- helpers ----

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	/** A blueprint of {@code dx} x {@code dz} {@code block}s on the floor at ROW. */
	private static ResourceLocation area(GameTestHelper helper, Block block, int dx, int dz, String what) {
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), block);
			}
		}
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaownchest_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(dx, 1, dz), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < dx; x++) {
			for (int z = 0; z < dz; z++) {
				helper.setBlock(ROW.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	/** A blueprint of one oak plank with an empty item frame on its south face, standing on the floor. */
	private static ResourceLocation framed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(ROW, Blocks.OAK_PLANKS);
		ItemFrame frame = new ItemFrame(level, helper.absolutePos(FRAME), Direction.SOUTH);
		helper.assertTrue(frame.survives(), "setup: the frame can't hang on the plank");
		level.addFreshEntity(frame);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qaownchest_frame_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 2), true, Blocks.STRUCTURE_VOID);
		frame.discard();
		helper.setBlock(ROW, Blocks.AIR);
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
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}

	/** A crafter at {@code station}, with a chest by it holding {@code own}. */
	private static Villager crafter(GameTestHelper helper, Block station, ResourceKey<PoiType> poi, VillagerProfession job, ItemStack... own) {
		helper.setBlock(STATION, station);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(STATION.east());
		for (int i = 0; i < own.length; i++) {
			chest.setItem(i, own[i].copy());
		}
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), poi, job);
		return v;
	}

	private static void assertMadeFromOwn(GameTestHelper helper, Villager crafter, Container own, Item making, int had) {
		helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 1, "the crafter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
		helper.assertTrue(own.countItem(making) < had, "his own " + making + " was never used: " + own.countItem(making) + " of " + had + " left");
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static int frames(GameTestHelper helper) {
		List<ItemFrame> hanging = helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FRAME)).inflate(0.5), ItemFrame::isAlive);
		return hanging.size();
	}
}
