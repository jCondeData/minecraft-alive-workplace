package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b, 2026-11-04), from the spec in docs/wiki/basements.md and cellars.md: a builder digs a basement or cellar
 * "out of the ground under the house, walls and floors it ... and leaves the house above as it was". Here the ground
 * under the Starter Cottage IV's basement is not dry earth: a cave two deep under its floor, and a water source right
 * under its floor; both finish whole and dry. Water or lava in the earth beside the basement's walls (and the same for a
 * Smithy's "Dig the cellar") are bugs today: {@link QaBasementGround1104BugTests}, which shares this class's setup.
 * <p>
 * Every area is earth eight deep over its floor (stone y 2-6, dirt 7-8, grass at y 9), the builds standing on it at
 * y 10, so a basement four deep (y 6-9) still has two blocks of stone under its floor.
 */
public class QaBasementGround1104GameTests implements FabricGameTest {
	static final Logger LOGGER = LoggerFactory.getLogger("QaBasementGround1104");
	static final String TALL_AREA = "aliveworkplace_test:tall_area";
	static final int GROUND = 9;
	static final BlockPos BENCH = new BlockPos(2, GROUND + 1, 2);
	static final BlockPos CHEST = new BlockPos(2, GROUND + 1, 4);
	static final BlockPos VILLAGER = new BlockPos(3, GROUND + 1, 3);
	static final BlockPos ORIGIN = new BlockPos(8, GROUND + 1, 9);

	record Setup(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan, Blueprint blueprint) {
	}

	/** The basement's extent in the blueprint (relative to the origin), from its entries under y = 0. */
	record Extent(int minX, int maxX, int minY, int minZ, int maxZ) {
		static Extent of(Blueprint blueprint) {
			List<Blueprint.Entry> under = blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).toList();
			return new Extent(under.stream().mapToInt(e -> e.pos().getX()).min().orElseThrow(),
				under.stream().mapToInt(e -> e.pos().getX()).max().orElseThrow(),
				under.stream().mapToInt(e -> e.pos().getY()).min().orElseThrow(),
				under.stream().mapToInt(e -> e.pos().getZ()).min().orElseThrow(),
				under.stream().mapToInt(e -> e.pos().getZ()).max().orElseThrow());
		}

		int midX() {
			return (minX + maxX) / 2;
		}

		int midZ() {
			return (minZ + maxZ) / 2;
		}

		/** Spots in the earth just outside the basement's west, east and south walls, and one under the middle of its floor. */
		List<BlockPos> wetSpots() {
			return List.of(new BlockPos(minX - 1, -2, midZ()), new BlockPos(maxX + 1, -3, midZ()), new BlockPos(midX(), -2, maxZ + 1),
				new BlockPos(midX(), minY - 1, midZ()));
		}
	}

	// ----------------------------------------------------------------------------------------------------------- cave

	/**
	 * A cave two deep under the middle of where the basement's floor goes (the floor's 3x3 middle and more, down to the
	 * area's floor): when done, every floor block the drawing has is there and the whole build is as drawn.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaBasementCave"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaBasementCave")
	public void aCaveUnderTheBasementLeavesItsFloorWhole(GameTestHelper helper) {
		int[] caved = {0};
		Setup s = cottageFour(helper, b -> {
			Extent at = Extent.of(b);
			for (int x = at.minX() + 1; x <= at.maxX() - 1; x++) {
				for (int z = at.minZ() + 1; z <= at.maxZ() - 1; z++) {
					for (int dy = 1; dy <= 2; dy++) {
						helper.setBlock(ORIGIN.offset(x, at.minY() - dy, z), Blocks.AIR);
						caved[0]++;
					}
				}
			}
		});
		Extent ex = Extent.of(s.blueprint());
		int cave = caved[0];
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			BlockPos origin = s.site().placement().origin();
			List<String> holes = new ArrayList<>();
			int floor = 0;
			for (Blueprint.Entry e : s.blueprint().blocks()) {
				if (e.pos().getY() == ex.minY() && !e.state().isAir()) {
					floor++;
					BlockState world = s.level().getBlockState(origin.offset(e.pos()));
					if (!world.is(e.state().getBlock())) {
						holes.add(e.pos().toShortString() + "=" + world);
					}
				}
			}
			helper.assertTrue(floor > 0, "setup: the basement has no floor blocks at y " + ex.minY());
			helper.assertTrue(holes.isEmpty(), holes.size() + " of " + floor + " floor block(s) missing over the cave, e.g. " + holes.stream().limit(4).toList());
			LOGGER.info("qa basement cave: {} floor blocks whole over a cave of {} spots", floor, cave);
		});
	}

	// ------------------------------------------------------------------------------------------ water under the floor

	/**
	 * A water source in the stone right under the middle of where the basement's floor goes (and none beside it): the dig
	 * opens the floor over it. When done, the floor stands whole over the water, no water is in the basement, and the
	 * builder is alive.
	 */
	//$ gametest_ticks_batch TALL_AREA '24000' '"qaBasementWaterUnder"'
	@GameTest(template = TALL_AREA, timeoutTicks = 24000, batch = "qaBasementWaterUnder")
	public void waterUnderTheBasementFloorStaysUnderIt(GameTestHelper helper) {
		BlockPos[] under = {null};
		Setup s = cottageFour(helper, b -> {
			Extent at = Extent.of(b);
			under[0] = ORIGIN.offset(at.midX(), at.minY() - 1, at.midZ());
			helper.setBlock(under[0], Blocks.WATER);
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			int checked = assertNoFluidInBasement(helper, s);
			helper.assertTrue(s.villager().isAlive(), "the builder died");
			LOGGER.info("qa basement water under: built at tick {}, {} spot(s) of the basement dry, under the floor {}", helper.getTick(), checked,
				helper.getBlockState(under[0]));
		});
	}


	// -------------------------------------------------------------------------------------------------------- helpers

	/**
	 * Starter Cottage IV clicked onto a finished Cottage III on deep earth, the earth beside and under where its basement
	 * goes shaped by {@code shapeTheGround}, the builder stocked with what the supply report says is missing.
	 */
	static Setup cottageFour(GameTestHelper helper, java.util.function.Consumer<Blueprint> shapeTheGround) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		BlueprintData.Placement placement = placement(helper, ORIGIN);
		level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE_3.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(ORIGIN), helper.absolutePos(ORIGIN), new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE_3.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		ResourceLocation four = StarterBlueprints.STARTER_COTTAGE_4.id();
		Blueprint blueprint = BlueprintLibrary.get(level, four).orElseThrow();
		shapeTheGround.accept(blueprint);
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		var report = BlueprintSupplies.check(level, new BlueprintData(four, Optional.of(StarterBlueprints.STARTER_COTTAGE_4.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> needed = new ArrayList<>();
		report.missing().forEach(m -> needed.addAll(stacks(m.item(), m.count())));
		fill(helper, needed);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, four, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + four);
		}
		Setup s = new Setup(level, villager, site, plan, blueprint);
		watchDeath(helper, s);
		return s;
	}

	/** {@code fluid} sources at the basement's {@link Extent#wetSpots}, each a spot of earth no block of the drawing takes. */
	static List<BlockPos> wet(GameTestHelper helper, Blueprint blueprint, Block fluid) {
		Set<BlockPos> drawn = new HashSet<>();
		blueprint.blocks().forEach(e -> drawn.add(e.pos()));
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos rel : Extent.of(blueprint).wetSpots()) {
			helper.assertFalse(drawn.contains(rel), "setup: " + rel.toShortString() + " is a spot of the build, not the earth beside it");
			BlockPos at = ORIGIN.offset(rel);
			helper.assertTrue(at.getY() >= 2, "setup: " + rel.toShortString() + " is under the area's ground");
			helper.setBlock(at, fluid);
			out.add(rel);
		}
		return out;
	}

	static void watchDeath(GameTestHelper helper, Setup s) {
		helper.onEachTick(() -> {
			if (!s.villager().isAlive()) {
				throw new GameTestAssertException("the builder died at tick " + helper.getTick() + " at " + helper.relativePos(s.villager().blockPosition())
					+ " (stage " + s.site().stage() + ", cursor " + s.site().cursor() + ")");
			}
		});
	}

	static void rules(GameTestHelper helper) {
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

	/** Stone y 2-6, dirt 7-8, grass at {@link #GROUND}, {@code size} each way. */
	static void ground(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				for (int y = 2; y <= GROUND; y++) {
					helper.setBlock(new BlockPos(x, y, z), y < GROUND - 2 ? Blocks.STONE : y < GROUND ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				}
			}
		}
	}

	/** As CellarGameTests.oldBuild: the build finished before 26.15, its cellar still earth and the hatch a block of floor. */
	static BlueprintData.Placement oldBuild(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos originRel) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = placement(helper, originRel);
		BlockPos file = helper.absolutePos(originRel).below(entry.basement());
		level.getStructureManager().get(entry.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
		Set<Long> columns = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos world = helper.absolutePos(originRel).offset(e.pos());
			if (e.pos().getY() < 0) {
				level.removeBlockEntity(world);
				level.setBlock(world, (e.pos().getY() >= -2 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), flags);
			} else if (e.pos().getY() == 0 && columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))
					&& (e.state().is(BlockTags.TRAPDOORS) || e.state().is(Blocks.LADDER) || e.state().isAir())) {
				level.setBlock(world, Blocks.COBBLESTONE.defaultBlockState(), flags);
			}
		}
		BuildSiteManager.get(level).recordFinished(entry.id(), placement, UUID.randomUUID());
		return placement;
	}

	static void fill(GameTestHelper helper, List<ItemStack> stock) {
		List<BlockPos> stores = new ArrayList<>(List.of(CHEST));
		for (int i = 0; i * 27 < Math.max(1, stock.size()) - 27; i++) {
			stores.add(new BlockPos(1 + i % 4, GROUND + 1, 5 + i / 4));
		}
		for (int i = 0; i < stores.size(); i++) {
			helper.setBlock(stores.get(i), i == 0 ? Blocks.CHEST : Blocks.BARREL);
			Container box = helper.getBlockEntity(stores.get(i));
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				box.setItem(slot, stock.get(i * 27 + slot).copy());
			}
		}
	}

	static List<ItemStack> stacks(Item item, int count) {
		List<ItemStack> out = new ArrayList<>();
		for (int left = count; left > 0; left -= item.getDefaultMaxStackSize()) {
			out.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
		}
		return out;
	}

	static BlueprintData.Placement placement(GameTestHelper helper, BlockPos originRel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(originRel), Rotation.NONE, Mirror.NONE);
	}

	static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
			"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " progress=" + Math.round(s.site().progress(s.plan()) * 100)
				+ "% missing=" + s.site().missing() + " builder at " + helper.relativePos(s.villager().blockPosition())
				+ " in " + s.level().getBlockState(s.villager().blockPosition()));
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		if (!unfinished.isEmpty()) {
			String sample = unfinished.stream().limit(5)
				.map(p -> p.subtract(s.site().placement().origin()).toShortString() + "=" + s.level().getBlockState(p))
				.collect(Collectors.joining(", "));
			throw new GameTestAssertException(unfinished.size() + " block(s) wrong after the build, e.g. " + sample);
		}
	}

	/**
	 * No water or lava (source, flowing, or in a waterlogged block the drawing doesn't have waterlogged) anywhere in the
	 * box of the basement's drawing (its walls and floor included). Returns the number of spots checked.
	 */
	static int assertNoFluidInBasement(GameTestHelper helper, Setup s) {
		BlockPos origin = s.site().placement().origin();
		Extent ex = Extent.of(s.blueprint());
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		s.blueprint().blocks().forEach(e -> drawn.put(e.pos(), e.state()));
		List<String> wet = new ArrayList<>();
		int checked = 0;
		for (int x = ex.minX(); x <= ex.maxX(); x++) {
			for (int y = ex.minY(); y < 0; y++) {
				for (int z = ex.minZ(); z <= ex.maxZ(); z++) {
					BlockPos rel = new BlockPos(x, y, z);
					checked++;
					var f = s.level().getFluidState(origin.offset(rel));
					if (f.isEmpty() || !(f.is(FluidTags.WATER) || f.is(FluidTags.LAVA))) {
						continue;
					}
					BlockState want = drawn.get(rel);
					boolean drawnWet = want != null && want.hasProperty(BlockStateProperties.WATERLOGGED) && want.getValue(BlockStateProperties.WATERLOGGED);
					if (!drawnWet) {
						wet.add(rel.toShortString() + "=" + s.level().getBlockState(origin.offset(rel)));
					}
				}
			}
		}
		helper.assertTrue(wet.isEmpty(), wet.size() + " spot(s) of " + checked + " in the finished basement hold water or lava, e.g. " + wet.stream().limit(5).toList());
		return checked;
	}
}
