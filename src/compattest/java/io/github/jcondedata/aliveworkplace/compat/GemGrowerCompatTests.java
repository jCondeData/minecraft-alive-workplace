package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.gem.GemBeds;
import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** ROADMAP 28.11 with the real Cobblemon: tumblestones against magma (1.7.3 and up), Type Gems on a crystal core and Blank TMs (1.8). */
public class GemGrowerCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BED = new BlockPos(8, 2, 2);
	/** Where the first planting goes: on top of the bed's block. */
	private static final BlockPos ON_TOP = BED.above();

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(c(path));
	}

	private static Block block(String path) {
		return BuiltInRegistries.BLOCK.get(c(path));
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, String name, String value) {
		@SuppressWarnings("unchecked")
		Property<T> property = (Property<T>) state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : state.setValue(property, property.getValue(value).orElseThrow());
	}

	static Villager grower(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(CUTTER), PoiTypes.MASON, ModVillagers.GEM_GROWER);
		helper.assertTrue(GemGrowers.isGrower(villager), "not a gem grower");
		return villager;
	}

	private static String said(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(grower.blockPosition()) + ", beds "
			+ GemGrowers.beds(grower) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(grower).stacks();
	}

	/**
	 * The Done when (1.7.3): a tumblestone from her chest planted against magma, as Cobblemon grows them (a small bud on
	 * the magma, facing up); once it's a full cluster (forced here: growing takes hours) she picks it and the magma stays.
	 */
	//$ gametest_ticks_batch AREA '2400' '"gem_tumblestone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "gem_tumblestone")
	public void aTumblestoneIsPlantedAgainstMagmaAndItsClusterPicked(GameTestHelper helper) {
		helper.assertTrue(GemBeds.byName(AliveWorkplace.id("tumblestone")) != null, "the tumblestone bed didn't load with Cobblemon");
		helper.setBlock(BED, Blocks.MAGMA_BLOCK);
		Villager grower = grower(helper, new ItemStack(item("tumblestone"), 1));
		boolean[] forced = {false};
		helper.onEachTick(() -> {
			BlockState top = helper.getBlockState(ON_TOP);
			if (!forced[0] && top.is(block("small_budding_tumblestone"))) {
				helper.assertTrue(top.getValue(net.minecraft.world.level.block.DirectionalBlock.FACING) == Direction.UP, "the bud faces " + top);
				helper.setBlock(ON_TOP, with(block("tumblestone_cluster").defaultBlockState(), "facing", "up"));
				forced[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(forced[0], "no tumblestone planted on the magma; she says " + said(helper, grower));
			helper.assertTrue(ModAttachments.GEMS_PICKED.getOrElse(grower, 0) >= 1, "the cluster wasn't picked; she says " + said(helper, grower));
			helper.assertTrue(!helper.getBlockState(ON_TOP).is(block("tumblestone_cluster")), "the cluster is still there");
			helper.assertTrue(helper.getBlockState(BED).is(Blocks.MAGMA_BLOCK), "the magma was broken");
			Container chest = helper.getBlockEntity(CHEST);
			long tumblestones = chest.countItem(item("tumblestone")) + ModAttachments.BUILDER_BAG.getOrCreate(grower).count(item("tumblestone"));
			boolean replanted = helper.getBlockState(ON_TOP).is(block("small_budding_tumblestone"));
			helper.assertTrue(tumblestones + (replanted ? 1 : 0) >= 2, "the cluster's tumblestones didn't come back: " + tumblestones);
		});
	}

	/**
	 * The Done when (1.8.1): a Fire Gem Block from her chest set against a Deepslate Crystal Core; a stage-3 Fire Gem
	 * cluster on it (forced) is picked and the Gem Block kept; with glass and shards she makes Blank TMs (Cobblemon's
	 * recipe). On 1.7.3 there are no Type Gems: the bed must not load, and the test ends there.
	 */
	//$ gametest_ticks_batch AREA '2400' '"gem_type_gem"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "gem_type_gem")
	public void aFireGemBlockIsSetOnACoreAndItsClusterPicked(GameTestHelper helper) {
		if (!PokemonFeatures.TYPE_GEMS.available()) {
			helper.assertTrue(GemBeds.byName(AliveWorkplace.id("fire_gem")) == null, "the Fire Gem bed loaded without Type Gems");
			helper.succeed();
			return;
		}
		helper.assertTrue(GemBeds.byName(AliveWorkplace.id("fire_gem")) != null, "the Fire Gem bed didn't load on 1.8");
		helper.setBlock(BED, block("deepslate_crystal_core"));
		Villager grower = grower(helper, new ItemStack(item("fire_gem_block"), 1), new ItemStack(Items.GLASS, 2), new ItemStack(Items.AMETHYST_SHARD, 2));
		BlockPos cluster = ON_TOP.above();
		boolean[] forced = {false};
		helper.onEachTick(() -> {
			if (!forced[0] && helper.getBlockState(ON_TOP).is(block("fire_gem_block"))) {
				helper.setBlock(cluster, with(with(block("fire_gem_cluster").defaultBlockState(), "facing", "up"), "stage", "3"));
				forced[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(forced[0], "no Fire Gem Block set on the core; she says " + said(helper, grower));
			helper.assertTrue(!helper.getBlockState(cluster).is(block("fire_gem_cluster")), "the cluster wasn't picked; she says " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(ON_TOP).is(block("fire_gem_block")), "the Gem Block wasn't kept: " + helper.getBlockState(ON_TOP));
			helper.assertTrue(helper.getBlockState(BED).is(block("deepslate_crystal_core")), "the core was broken");
			helper.assertTrue(chest.countItem(item("fire_gem")) >= 1, "no Fire Gems in the chest; she says " + said(helper, grower));
			helper.assertTrue(chest.countItem(item("blank_tm")) >= 2, "no Blank TMs made: " + chest.countItem(item("blank_tm")));
		});
	}

	/** With Cobblemon, the orders screen lists Cobblemon's beds (tumblestones; the Type Gems on 1.8) after amethyst. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cobblemonsBedsLoad(GameTestHelper helper) {
		List<String> names = GemBeds.beds().stream().map(b -> b.name().getPath()).toList();
		helper.assertTrue(names.size() >= 4 && names.get(0).equals("amethyst") && names.subList(1, 4)
			.equals(List.of("tumblestone", "sky_tumblestone", "black_tumblestone")), "beds: " + names);
		helper.assertTrue(names.contains("fire_gem") == PokemonFeatures.TYPE_GEMS.available() && names.size() == (PokemonFeatures.TYPE_GEMS.available() ? 22 : 4),
			"Type Gem beds: " + names);
		helper.succeed();
	}
}
