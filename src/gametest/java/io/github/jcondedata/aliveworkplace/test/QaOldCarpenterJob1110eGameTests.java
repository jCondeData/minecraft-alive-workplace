package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-10, fourth run): upgrading a save across Milestone 46. The wiki's promise (job-lists.md, "Saved
 * data"): "Old saves load unchanged; a crafter's job in hand from before carries on." A world saved before 46.1 can hold
 * a Carpenter half way through a job that is another job's now: a brewing stand for a builder (the Cleric's since 46.4),
 * its blaze rod and cobblestone in his bag, his job saved without the profession stamp (saves from before B1009). Loaded
 * with today's jar, nothing he carries may be lost: either he finishes it and the builder gets it, or his makings come
 * back where the player can get them.
 */
public class QaOldCarpenterJob1110eGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	/**
	 * A pre-46.1 Carpenter at his bench with a brewing stand's makings in his bag (one blaze rod, three cobblestone), the
	 * job saved for a builder short of one: loaded today, the brewing stand reaches the builder (it carries on) or the
	 * blaze rod and cobblestone are not lost (in the builder's chest, on the ground, or still in his bag with his job).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_old_carpenter_job_1110e"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_old_carpenter_job_1110e")
	public void aCarpentersBrewingStandJobFromBefore46LosesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.assertTrue(CraftJobs.maker(level, Items.BREWING_STAND) != CraftJobs.Job.CARPENTER,
			"the Carpenter still makes brewing stands today, so this isn't an old job from another list");

		// the builder short of one brewing stand
		helper.setBlock(ROW, Blocks.BREWING_STAND);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_old_stand_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the build didn't start");

		// the Carpenter as saved before 46.1: at the crafting step, the makings in his bag, no profession stamp
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager old = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, old, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		Crafting.Step step = new Crafting.Step(Map.of(Items.BLAZE_ROD, 1, Items.COBBLESTONE, 3), new ItemStack(Items.BREWING_STAND), 1);
		ModAttachments.CRAFTER_JOB.set(old, new CrafterJob(helper.absolutePos(CHEST), Optional.empty(), Items.BREWING_STAND, 1, List.of(step),
			Map.of(Items.BLAZE_ROD, 1, Items.COBBLESTONE, 3), List.of(helper.absolutePos(CHEST)), "", CrafterJob.Phase.CRAFTING, 0));
		ModAttachments.BUILDER_BAG.getOrCreate(old).addAll(Items.BLAZE_ROD, 1);
		ModAttachments.BUILDER_BAG.getOrCreate(old).addAll(Items.COBBLESTONE, 3);
		CompoundTag saved = old.saveWithoutId(new CompoundTag());
		old.discard();
		Villager carpenter = EntityType.VILLAGER.create(level);
		helper.assertTrue(carpenter != null, "a villager to load into");
		carpenter.load(saved);
		level.addFreshEntity(carpenter);
		helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) != null && ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(Items.BLAZE_ROD) == 1,
			"the old job or its blaze rod didn't load");

		helper.runAfterDelay(1400, () -> {
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
			boolean built = BuildSiteManager.get(level).get(site.id()) == null;
			int rods = count(helper, chest, carpenter, Items.BLAZE_ROD);
			int stone = count(helper, chest, carpenter, Items.COBBLESTONE);
			int stands = count(helper, chest, carpenter, Items.BREWING_STAND);
			String where = "built " + built + "; job " + (ModAttachments.CRAFTER_JOB.get(carpenter) == null ? "let go" : ModAttachments.CRAFTER_JOB.get(carpenter).phase())
				+ "; blaze rods " + rods + ", cobblestone " + stone + ", spare stands " + stands + " (chest, bag and ground)";
			org.slf4j.LoggerFactory.getLogger("aliveworkplace_test").info("QA old Carpenter job: {}", where);
			helper.assertTrue(carpenter.isAlive(), "the old Carpenter didn't survive loading");
			if (built) {
				helper.assertBlockPresent(Blocks.BREWING_STAND, ROW);
				helper.assertTrue(rods == 0 && stands == 0, "the brewing stand was built and something doubled: " + where);
			} else {
				helper.assertTrue(rods == 1 && stone == 3, "the old job's makings were lost: " + where);
			}
			helper.succeed();
		});
	}

	/** {@code item} in the builder's chest, the Carpenter's bag and lying loose around the test. */
	private static int count(GameTestHelper helper, Container chest, Villager carpenter, Item item) {
		int loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
		return chest.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(item) + carpenter.getInventory().countItem(item) + loose;
	}
}
