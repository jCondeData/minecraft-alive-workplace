package io.github.jcondedata.aliveworkplace.test;

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
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
import net.minecraft.world.entity.item.ItemEntity;
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
 * QA (qa-lane-b-1110-1837) of B1144, written from its spec: the Cleric uses what is in his own chest (the chest by his
 * brewing stand) for a builder's brewing stand, as the guide (cleric.md, "the cleric's own") and the wiki promise. These
 * go past the bug's own test: he takes only what the stand needs from his chest, and a stand whose every making is in his
 * chest is built with the builder's chest empty.
 */
public class QaClericOwnChest1110bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STAND = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** Two blaze rods in his chest and one stand to make: one rod is used, the other stays in his chest. */
	//$ gametest_ticks_batch AREA '2400' '"qaClericOwnTwoRods"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericOwnTwoRods")
	public void ofTwoRodsInHisChestOnlyOneIsUsed(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "tworods"), new ItemStack(Items.COBBLESTONE, 3));
		Villager cleric = cleric(helper, new ItemStack(Items.BLAZE_ROD, 2));
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container own = helper.getBlockEntity(STAND.east());
			helper.assertTrue(own.countItem(Items.BLAZE_ROD) == 1, "blaze rods left in his chest: " + own.countItem(Items.BLAZE_ROD) + ", not 1");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) == 1, "the Cleric made " + ModAttachments.ITEMS_CRAFTED.getOrElse(cleric, 0) + ", not 1");
		});
	}

	/** The rod and the three cobblestone all in his own chest, the builder's chest empty: the stand is built from his. */
	//$ gametest_ticks_batch AREA '2400' '"qaClericOwnAll"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClericOwnAll")
	public void aStandWhoseEveryMakingIsInHisChestIsBuilt(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, single(helper, Blocks.BREWING_STAND, "ownall"));
		cleric(helper, new ItemStack(Items.BLAZE_ROD, 1), new ItemStack(Items.COBBLESTONE, 5));
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container own = helper.getBlockEntity(STAND.east());
			helper.assertTrue(own.countItem(Items.BLAZE_ROD) == 0, "his blaze rod is still in his chest");
			helper.assertTrue(own.countItem(Items.COBBLESTONE) == 2, "cobblestone left in his chest: " + own.countItem(Items.COBBLESTONE) + ", not 2");
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

	private static String line(GameTestHelper helper, Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	private static ResourceLocation single(GameTestHelper helper, Block block, String what) {
		helper.setBlock(ROW, block);
		return capture(helper, ROW, new Vec3i(1, 1, 1), what);
	}

	private static ResourceLocation capture(GameTestHelper helper, BlockPos origin, Vec3i size, String what) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qacleric_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(origin).getX());
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
