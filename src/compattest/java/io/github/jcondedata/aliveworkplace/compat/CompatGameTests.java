package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * In-game tests with Chipped and Supplementaries installed ({@code ./gradlew runCompatGameTest}).
 * Each test builds something in the test area, saves it as a blueprint, clears the area, stocks a
 * chest and lets a real builder villager rebuild it.
 */
public class CompatGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:build_area";
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	static final BlockPos ORIGIN = new BlockPos(6, 2, 6);
	private static final java.util.concurrent.atomic.AtomicInteger BLUEPRINTS = new java.util.concurrent.atomic.AtomicInteger();

	// --- Chipped ----------------------------------------------------------------------------

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyChippedBlockCanBeBuilt(GameTestHelper helper) {
		List<String> unbuildable = new ArrayList<>();
		int total = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
			if (id.getNamespace().equals("chipped")) {
				total++;
				if (MaterialRules.classify(block.defaultBlockState()) == MaterialRules.Kind.SKIP && !MaterialRules.isSecondaryHalf(block.defaultBlockState())) {
					unbuildable.add(id.toString());
				}
			}
		}
		helper.assertTrue(total > 1000, "Chipped seems not to be loaded (" + total + " blocks)");
		helper.assertTrue(unbuildable.isEmpty(), "Chipped blocks builders cannot place: " + unbuildable);
		Item herringbone = item("chipped:herringbone_oak_planks");
		helper.assertTrue(MaterialFamilies.accepted(herringbone).contains(Items.OAK_PLANKS), "oak planks should stand in for herringbone oak planks");
		helper.assertTrue(MaterialFamilies.key(herringbone) == Items.OAK_PLANKS, "missing-material messages should name Oak Planks");
		helper.succeed();
	}

	/** A hut made of Chipped variants, with only plain oak planks and stone bricks in the chest. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildsChippedVariantsFromPlainBlocks(GameTestHelper helper) {
		List<Item> planks = variants(helper, "oak_planks", 2);
		List<Item> bricks = variants(helper, "cobblestone", 1);
		BlockState floor = ((net.minecraft.world.item.BlockItem) bricks.get(0)).getBlock().defaultBlockState();
		BlockState wallA = ((net.minecraft.world.item.BlockItem) planks.get(0)).getBlock().defaultBlockState();
		BlockState wallB = ((net.minecraft.world.item.BlockItem) planks.get(1)).getBlock().defaultBlockState();
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				design.put(new BlockPos(x, 0, z), floor);
				for (int y = 1; y <= 2; y++) {
					if (x == 0 || x == 4 || z == 0 || z == 4) {
						design.put(new BlockPos(x, y, z), (x + z) % 2 == 0 ? wallA : wallB);
					}
				}
			}
		}
		ResourceLocation id = blueprintFrom(helper, "chipped_hut", design, Map.of());
		Setup s = setup(helper, id, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 32));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			for (Map.Entry<BlockPos, BlockState> e : design.entrySet()) {
				BlockState there = s.level().getBlockState(s.at(e.getKey()));
				helper.assertTrue(there.getBlock() == e.getValue().getBlock(), "expected " + BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()) + ", found " + there);
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.COBBLESTONE) == 0, "all plain blocks should have been used");
		});
	}

	// --- Rechiseled ---------------------------------------------------------------------------

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rechiseledVariantsAcceptThePlainBlock(GameTestHelper helper) {
		Item beams = item("rechiseled:oak_planks_beams");
		Item beamSlab = item("rechiseled:oak_planks_beams_slab");
		helper.assertTrue(MaterialFamilies.accepted(beams).contains(Items.OAK_PLANKS), "oak planks should stand in for rechiseled oak beams");
		helper.assertTrue(MaterialFamilies.accepted(beamSlab).contains(Items.OAK_SLAB), "oak slabs should stand in for rechiseled oak beam slabs");
		helper.assertFalse(MaterialFamilies.accepted(beams).contains(Items.OAK_SLAB), "a slab must not stand in for a full block");
		helper.succeed();
	}

	// --- Supplementaries ----------------------------------------------------------------------

	/**
	 * A little market corner: a way sign on a fence, a potted flower, a jar with cookies and an item
	 * shelf with a diamond. The builder must build all of it from the chest — and must not conjure up
	 * the cookies or the diamond.
	 */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildsSupplementariesBlocksWithoutFreeItems(GameTestHelper helper) {
		supplementariesCorner(helper, Rotation.NONE);
	}

	/** Same corner turned 90°: the way sign's pending rotation must not make the builder redo it forever. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildsTurnedSupplementariesBlocks(GameTestHelper helper) {
		supplementariesCorner(helper, Rotation.CLOCKWISE_90);
	}

	private void supplementariesCorner(GameTestHelper helper, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		// Build the original in the test area with the real items, as a player would.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.setBlock(ORIGIN.offset(0, 0, 0), Blocks.STONE_BRICKS);
		helper.setBlock(ORIGIN.offset(0, 1, 0), Blocks.OAK_FENCE);
		use(helper, player, "supplementaries:way_sign_oak", ORIGIN.offset(0, 1, 0), Direction.NORTH);
		helper.setBlock(ORIGIN.offset(2, 0, 0), Blocks.POTTED_POPPY);
		helper.setBlock(ORIGIN.offset(4, 0, 0), block("supplementaries:jar").defaultBlockState());
		fillContainer(helper, ORIGIN.offset(4, 0, 0), new ItemStack(Items.COOKIE, 8));
		helper.setBlock(ORIGIN.offset(4, 1, 2), Blocks.STONE_BRICKS);
		helper.setBlock(ORIGIN.offset(4, 1, 1), block("supplementaries:item_shelf").defaultBlockState()
			.setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
		fillContainer(helper, ORIGIN.offset(4, 1, 1), new ItemStack(Items.DIAMOND));
		BlockState signState = helper.getBlockState(ORIGIN.offset(0, 1, 0));
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(signState.getBlock()).getPath().equals("way_sign"), "way sign was not placed: " + signState);

		Map<BlockPos, BlockState> design = new HashMap<>();
		Map<BlockPos, CompoundTag> data = new HashMap<>();
		for (BlockPos rel : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(4, 2, 2))) {
			BlockPos p = ORIGIN.offset(rel);
			design.put(rel.immutable(), helper.getBlockState(p));
			BlockEntity be = level.getBlockEntity(helper.absolutePos(p));
			if (be != null) {
				data.put(rel.immutable(), be.saveWithoutMetadata(level.registryAccess()));
			}
		}
		ResourceLocation id = blueprintFrom(helper, "supplementaries_corner", design, data);
		Setup s = setup(helper, id, rotation,
			new ItemStack(Items.STONE_BRICKS, 2), new ItemStack(Items.OAK_FENCE), new ItemStack(item("supplementaries:way_sign_oak")),
			new ItemStack(Items.FLOWER_POT), new ItemStack(Items.POPPY), new ItemStack(item("supplementaries:jar")),
			new ItemStack(item("supplementaries:item_shelf")));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack left = chest.getItem(i);
				helper.assertTrue(left.isEmpty() || left.getItem() == io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT,
					"left over in the chest: " + left);
			}
			BlockEntity sign = level.getBlockEntity(s.at(new BlockPos(0, 1, 0)));
			CompoundTag signData = sign.saveWithoutMetadata(level.registryAccess());
			helper.assertTrue(signData.getCompound("Mimic").getString("Name").equals("minecraft:oak_fence")
				&& signData.getCompound("SignDown").getBoolean("Active"), "way sign lost its fence or its sign: " + signData);
			Container jar = (Container) level.getBlockEntity(s.at(new BlockPos(4, 0, 0)));
			Container shelf = (Container) level.getBlockEntity(s.at(new BlockPos(4, 1, 1)));
			helper.assertTrue(jar.isEmpty(), "the builder filled the jar for free: " + jar.getItem(0));
			helper.assertTrue(shelf.isEmpty(), "the builder put a free diamond on the shelf");
		});
	}

	// --- helpers ------------------------------------------------------------------------------

	record Setup(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan) {
		/** World position of a block of the blueprint (template coordinates). */
		BlockPos at(BlockPos template) {
			BlueprintData.Placement p = site.placement();
			return p.origin().offset(StructureTemplate.transform(template, p.mirror(), p.rotation(), BlockPos.ZERO));
		}
	}

	static Setup setup(GameTestHelper helper, ResourceLocation structure, ItemStack... chestItems) {
		return setup(helper, structure, Rotation.NONE, chestItems);
	}

	static Setup setup(GameTestHelper helper, ResourceLocation structure, Rotation rotation, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer()); // tests run side by side
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), rotation, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, structure, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Setup(level, villager, site, plan);
	}

	static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
			"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " missing=" + s.site().missing());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		if (!unfinished.isEmpty()) {
			throw new GameTestAssertException(unfinished.size() + " block(s) wrong, e.g. " + unfinished.stream().limit(4)
				.map(p -> helper.relativePos(p) + "=" + s.level().getBlockState(p) + " wanted " + wanted(s.plan(), p)).collect(Collectors.joining(", ")));
		}
		helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) skipped");
	}

	private static String wanted(BuildPlan plan, BlockPos pos) {
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (step.pos().equals(pos)) {
					return stage + " " + step.state() + " " + step.requirements();
				}
			}
		}
		return "?";
	}

	/**
	 * Registers a blueprint made of {@code design} (positions relative to its origin) and clears the
	 * test area where it was, so the builder starts from nothing.
	 */
	static ResourceLocation blueprintFrom(GameTestHelper helper, String name, Map<BlockPos, BlockState> design, Map<BlockPos, CompoundTag> data) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("compat_test/" + name + "_" + BLUEPRINTS.incrementAndGet());
		Vec3i size = new Vec3i(1, 1, 1);
		for (BlockPos p : design.keySet()) {
			size = new Vec3i(Math.max(size.getX(), p.getX() + 1), Math.max(size.getY(), p.getY() + 1), Math.max(size.getZ(), p.getZ() + 1));
		}
		// Put the design in place (if it is not already), capture it, then clear it.
		for (Map.Entry<BlockPos, BlockState> e : design.entrySet()) {
			BlockPos p = helper.absolutePos(ORIGIN.offset(e.getKey()));
			if (level.getBlockState(p) != e.getValue()) {
				level.setBlock(p, e.getValue(), Block.UPDATE_CLIENTS);
			}
		}
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(ORIGIN), size, false, Blocks.STRUCTURE_VOID);
		for (BlockPos rel : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			BlockPos p = helper.absolutePos(ORIGIN.offset(rel));
			level.removeBlockEntity(p);
			level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "could not register the test blueprint");
		return id;
	}

	private static void use(GameTestHelper helper, ServerPlayer player, String itemId, BlockPos rel, Direction face) {
		ItemStack stack = new ItemStack(item(itemId));
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false);
		stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	private static void fillContainer(GameTestHelper helper, BlockPos rel, ItemStack stack) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		if (!(be instanceof Container container)) {
			throw new GameTestAssertException("no container at " + rel + ": " + be);
		}
		container.setItem(0, stack);
		be.setChanged();
	}

	/** The first {@code count} Chipped variants of a vanilla block (from Chipped's own tag). */
	private static List<Item> variants(GameTestHelper helper, String family, int count) {
		TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("chipped", family));
		List<Item> out = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> {
			if (BuiltInRegistries.ITEM.getKey(h.value()).getNamespace().equals("chipped") && h.value() instanceof net.minecraft.world.item.BlockItem
				&& ((net.minecraft.world.item.BlockItem) h.value()).getBlock().defaultBlockState().isCollisionShapeFullBlock(helper.getLevel(), BlockPos.ZERO)) {
				out.add(h.value());
			}
		});
		helper.assertTrue(out.size() >= count, "not enough Chipped variants of " + family + ": " + out);
		return out.subList(0, count);
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("no item " + id));
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("no block " + id));
	}
}
