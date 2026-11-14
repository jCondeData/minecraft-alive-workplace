package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 46.4, the Cleric brews and makes the magic blocks: at his brewing stand he makes brewing stands, blaze powder,
 * eyes of ender, ender chests, end rods and beacons for builders, step by step like every crafting job, besides his
 * potions. Through a builder's request: a brewing stand from a blaze rod and cobblestone; an ender chest from a blaze
 * rod, an ender pearl and obsidian (the powder, then the eye, then the chest); the missing blaze rod named on the
 * builder's line; a Cleric saved and loaded mid-job carrying on; his potions brewed alongside; and with no Cleric, the
 * Librarian who made these before leaving them alone and the line saying "Hire a Cleric".
 */
public class ClericCraftsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STAND = new BlockPos(14, 2, 2);
	private static final BlockPos CAULDRON = new BlockPos(12, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The Cleric's list is his own now: no stand-in makes it, and the Librarian keeps only his books and tables. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theClericsListIsHisOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.Job.CLERIC.craftsNow(), "the Cleric still has a stand-in");
		for (Item item : List.of(Items.BREWING_STAND, Items.BLAZE_POWDER, Items.ENDER_EYE, Items.ENDER_CHEST, Items.END_ROD, Items.BEACON)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.CLERIC, item + " is made by " + CraftJobs.maker(level, item));
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.LIBRARIAN, item), "the Librarian still makes " + item);
		}
		// the Librarian keeps his own; the blaze rod is no one's to make
		helper.assertTrue(CraftJobs.maker(level, Items.BOOKSHELF) == CraftJobs.Job.LIBRARIAN, "bookshelf: " + CraftJobs.maker(level, Items.BOOKSHELF));
		helper.assertTrue(CraftJobs.maker(level, Items.BLAZE_ROD) == null, "blaze rod: " + CraftJobs.maker(level, Items.BLAZE_ROD));
		helper.succeed();
	}

	/** A brewing stand for a builder from a blaze rod and three cobblestone in his chest: the Cleric makes it and takes it to him. */
	//$ gametest_ticks_batch AREA '2400' '"clericBrewingStand"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "clericBrewingStand")
	public void theClericMakesABrewingStandFromABlazeRod(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.BREWING_STAND, "stand"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 0 && chest.countItem(Items.COBBLESTONE) == 0,
				"the stand wasn't made from the makings: " + chest.countItem(Items.BLAZE_ROD) + " rod(s), " + chest.countItem(Items.COBBLESTONE) + " cobblestone left");
		});
	}

	/**
	 * An ender chest from a blaze rod, an ender pearl and eight obsidian: the chain of his own list, the rod ground to
	 * blaze powder, the eye of ender made, then the chest.
	 */
	//$ gametest_ticks_batch AREA '3600' '"clericEnderChest"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "clericEnderChest")
	public void theClericMakesAnEnderChestStepByStep(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.ENDER_CHEST, "ender"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.ENDER_PEARL, 1),
			new ItemStack(Items.OBSIDIAN, 8));
		Villager cleric = cleric(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.ENDER_PEARL) == 0 && chest.countItem(Items.OBSIDIAN) == 0,
				"the chest wasn't made from the makings: " + chest.countItem(Items.ENDER_PEARL) + " pearl(s), " + chest.countItem(Items.OBSIDIAN) + " obsidian left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
		});
	}

	/**
	 * Cobblestone but no blaze rod: the Cleric makes nothing, takes nothing, and the builder's line names the blaze rod
	 * ("short of 1 Blaze Rod to make 1 Brewing Stand").
	 */
	//$ gametest_ticks_batch AREA '1400' '"clericNoBlazeRod"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "clericNoBlazeRod")
	public void aMissingBlazeRodIsNamedOnTheShortageLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.BREWING_STAND, "norod"), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 3, "the cobblestone was taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) == 0, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.contains("1 Blaze Rod") && text.contains("to make") && text.contains("Brewing Stand")
				&& !text.contains("Cobblestone") && !text.contains("Hire"), "the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Saved and loaded just as he starts making the stand, the Cleric carries on with the makings in his bag and it arrives. */
	//$ gametest_ticks_batch AREA '3600' '"clericReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "clericReload")
	public void aClericLoadedMidJobFinishesTheStand(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.BREWING_STAND, "reload"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		AtomicReference<Villager> cleric = new AtomicReference<>(cleric(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(cleric.get());
			if (saved.get() != null || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			saved.set(job);
			Villager copy = reload(helper.getLevel(), cleric.get());
			helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the makings should be in the bag across the reload");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Cleric");
			cleric.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the Cleric never started making the stand");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(cleric.get()) == null, "the finished job is still saved on the Cleric");
		});
	}

	/**
	 * His potions still get brewed: with the makings of three healing potions in the chest by his stand and a builder
	 * waiting for a brewing stand, the Cleric does both (the two take turns, neither stalls the other).
	 */
	//$ gametest_ticks_batch AREA '4000' '"clericPotionsToo"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "clericPotionsToo")
	public void theClericStillBrewsHisPotions(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.BREWING_STAND, "potions"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper, new ItemStack(Items.GLASS_BOTTLE, 3), new ItemStack(Items.NETHER_WART), new ItemStack(Items.GLISTERING_MELON_SLICE),
			new ItemStack(Items.BLAZE_POWDER));
		helper.setBlock(CAULDRON, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		ItemStack healing = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) >= 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0));
			Container own = helper.getBlockEntity(STAND.east());
			int potions = 0;
			for (int i = 0; i < own.getContainerSize(); i++) {
				if (ItemStack.isSameItemSameComponents(own.getItem(i), healing)) {
					potions += own.getItem(i).getCount();
				}
			}
			helper.assertTrue(potions == 3, "healing potions in his chest: " + potions);
		});
	}

	/**
	 * No Cleric, only the Librarian who made brewing stands before 46.4: he leaves the stand alone (the makings stay in
	 * the chest) and the builder's line says "Hire a Cleric to make Brewing Stands".
	 */
	//$ gametest_ticks_batch AREA '1400' '"clericAbsent"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "clericAbsent")
	public void withNoClericTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, one(helper, Blocks.BREWING_STAND, "absent"), new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 3));
		helper.setBlock(STAND, Blocks.LECTERN);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Villager librarian = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), librarian, helper.absolutePos(STAND), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BLAZE_ROD) == 1 && chest.countItem(Items.COBBLESTONE) == 3, "the Librarian took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(librarian, 0) == 0, "the Librarian made " + ModAttachments.ITEMS_CRAFTED.getOrElse(librarian, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Cleric to make Brewing Stands".equals(text), "the builder's line: " + text);
			helper.succeed();
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
	}

	/** A blueprint of one {@code block}, standing on the floor. */
	private static ResourceLocation one(GameTestHelper helper, Block block, String what) {
		helper.setBlock(ROW, block);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"cleric_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
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
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, CHEST);
	}

	/** A Cleric at his brewing stand, with a chest by it holding {@code own}. */
	private static Villager cleric(GameTestHelper helper, ItemStack... own) {
		helper.setBlock(STAND, Blocks.BREWING_STAND);
		helper.setBlock(STAND.east(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(STAND.east());
		for (int i = 0; i < own.length; i++) {
			chest.setItem(i, own[i].copy());
		}
		Villager cleric = helper.spawn(EntityType.VILLAGER, STAND.south());
		Jobs.employ(helper.getLevel(), cleric, helper.absolutePos(STAND), PoiTypes.CLERIC, VillagerProfession.CLERIC);
		return cleric;
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
