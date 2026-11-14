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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 46.2, the Shepherd weaves: at his loom he makes wool from string, dyes it and makes carpets, beds, banners,
 * banner patterns and paintings for builders, the whole chain in steps (the wool first, then the bed). Through a
 * builder's request: two blue beds from white wool, planks and blue dye; a banner spun from string; a missing dye named
 * on the builder's line; a Shepherd saved and loaded mid-job carrying on; and with no Shepherd, the old dyer leaving the
 * beds alone and the line saying "Hire a Shepherd".
 */
public class ShepherdWeavesGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** The Shepherd's list is his own now: no stand-in makes it, and the lists' other jobs keep theirs. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theShepherdsListIsHisOwn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CraftJobs.Job.SHEPHERD.craftsNow(), "the Shepherd still has a stand-in");
		for (Item item : List.of(Items.WHITE_WOOL, Items.BLUE_WOOL, Items.RED_CARPET, Items.BLUE_BED, Items.WHITE_BANNER, Items.PAINTING,
				Items.FLOWER_BANNER_PATTERN, Items.CREEPER_BANNER_PATTERN)) {
			helper.assertTrue(CraftJobs.maker(level, item) == CraftJobs.Job.SHEPHERD, item + " is made by " + CraftJobs.maker(level, item));
			helper.assertFalse(CraftJobs.makes(level, CraftJobs.Job.LEATHERWORKER, item), "the Leatherworker still makes " + item);
		}
		// the dyes stay the Florist's (her own since 46.3), leather goods the Leatherworker's
		helper.assertTrue(CraftJobs.maker(level, Items.BLUE_DYE) == CraftJobs.Job.FLORIST, "blue dye: " + CraftJobs.maker(level, Items.BLUE_DYE));
		helper.assertTrue(CraftJobs.maker(level, Items.ITEM_FRAME) == CraftJobs.Job.LEATHERWORKER, "item frame: " + CraftJobs.maker(level, Items.ITEM_FRAME));
		helper.succeed();
	}

	/**
	 * Two blue beds for a builder from white wool, planks and blue dye in his chest: the Shepherd dyes the wool (or the
	 * beds) and makes them at his loom, step by step, into the builder's chest; the build is finished with them.
	 */
	//$ gametest_ticks_batch AREA '3000' '"weaveBlueBeds"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "weaveBlueBeds")
	public void theShepherdMakesTwoBlueBedsFromWhiteWool(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 6));
		Villager shepherd = shepherd(helper);
		AtomicInteger steps = new AtomicInteger();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd);
			if (job != null) {
				steps.accumulateAndGet(job.steps().size(), Math::max);
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 2, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
			helper.assertTrue(steps.get() >= 2, "the beds weren't made in steps (the dyeing, then the beds): " + steps.get());
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 0, chest.countItem(Items.WHITE_WOOL) + " white wool left: the beds weren't made from it");
		});
	}

	/** A white banner for a builder from string and a stick: the wool spun from the string first, then the banner. */
	//$ gametest_ticks_batch AREA '3000' '"weaveBanner"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "weaveBanner")
	public void theShepherdSpinsWoolFromStringForABanner(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(ROW, Blocks.WHITE_BANNER);
		ResourceLocation id = capture(helper, ROW, new Vec3i(1, 1, 1), "banner");
		Asker a = asker(helper, id, new ItemStack(Items.STRING, 24), new ItemStack(Items.STICK, 1));
		Villager shepherd = shepherd(helper);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.STRING) == 0, chest.countItem(Items.STRING) + " string left: the wool wasn't spun from it");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) >= 1, "the Shepherd made nothing");
		});
	}

	/**
	 * White wool and planks but no blue dye: the Shepherd makes nothing, takes nothing, and the builder's line names the
	 * dye ("short of ... Blue Dye to make 2 Blue Beds"), not the lapis or cornflowers that make it.
	 */
	//$ gametest_ticks_batch AREA '1400' '"weaveNoDye"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "weaveNoDye")
	public void aMissingDyeIsNamedOnTheShortageLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6));
		Villager shepherd = shepherd(helper);
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 6 && chest.countItem(Items.OAK_PLANKS) == 6, "the makings were taken");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0) == 0, "the Shepherd made " + ModAttachments.ITEMS_CRAFTED.getOrElse(shepherd, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue(text != null && text.contains("Blue Dye") && text.contains("to make 2 Blue Beds") && !text.contains("Lapis") && !text.contains("Cornflower"),
				"the builder's line: " + text);
			helper.succeed();
		});
	}

	/** Saved and loaded just as he starts weaving, the Shepherd carries on with the makings in his bag and the beds arrive. */
	//$ gametest_ticks_batch AREA '3600' '"weaveReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "weaveReload")
	public void aShepherdLoadedMidJobFinishesTheBeds(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 6));
		AtomicReference<Villager> shepherd = new AtomicReference<>(shepherd(helper));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(shepherd.get());
			if (saved.get() != null || job == null || job.phase() != CrafterJob.Phase.CRAFTING) {
				return;
			}
			saved.set(job);
			Villager copy = reload(helper.getLevel(), shepherd.get());
			helper.assertTrue(!ModAttachments.BUILDER_BAG.getOrCreate(copy).isEmpty(), "the makings should be in the bag across the reload");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(copy) != null, "the job wasn't saved on the Shepherd");
			shepherd.set(copy);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the Shepherd never started weaving");
			assertBuilt(helper, a);
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(shepherd.get()) == null, "the finished job is still saved on the Shepherd");
		});
	}

	/**
	 * No Shepherd, only the Leatherworker who wove before 46.2: he leaves the beds alone (the makings stay in the chest)
	 * and the builder's line says "Hire a Shepherd to make Blue Beds".
	 */
	//$ gametest_ticks_batch AREA '1400' '"weaveNoShepherd"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "weaveNoShepherd")
	public void withNoShepherdTheLineSaysHireOne(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, beds(helper, Blocks.BLUE_BED), new ItemStack(Items.WHITE_WOOL, 6), new ItemStack(Items.OAK_PLANKS, 6),
			new ItemStack(Items.BLUE_DYE, 6));
		helper.setBlock(STATION, Blocks.CAULDRON);
		Villager dyer = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), dyer, helper.absolutePos(STATION), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
		helper.runAfterDelay(200, () -> JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH)));
		helper.runAfterDelay(1100, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) == 6 && chest.countItem(Items.BLUE_DYE) == 6, "the Leatherworker took the makings");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0) == 0, "the Leatherworker made " + ModAttachments.ITEMS_CRAFTED.getOrElse(dyer, 0));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Hire a Shepherd to make Blue Beds".equals(text), "the builder's line: " + text);
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

	/** A blueprint of two {@code bed}s side by side, heads to the south. */
	private static ResourceLocation beds(GameTestHelper helper, net.minecraft.world.level.block.Block bed) {
		for (int x = 0; x < 2; x++) {
			BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
			helper.setBlock(ROW.offset(x, 0, 0), foot);
			helper.setBlock(ROW.offset(x, 0, 1), foot.setValue(BedBlock.PART, BedPart.HEAD));
		}
		return capture(helper, ROW, new Vec3i(2, 1, 2), "beds");
	}

	/** The blocks at {@code origin} ({@code size}) as a test blueprint, then cleared. */
	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"weave_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < size.getX(); x++) {
			for (int y = 0; y < size.getY(); y++) {
				for (int z = 0; z < size.getZ(); z++) {
					helper.setBlock(origin.offset(x, y, z), Blocks.AIR);
				}
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
		return new Asker(builder, site, plan, CHEST);
	}

	/** A Shepherd at his loom, with a chest by it (where the flock's wool goes). */
	private static Villager shepherd(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.LOOM);
		helper.setBlock(STATION.east(), Blocks.CHEST);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		return shepherd;
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
