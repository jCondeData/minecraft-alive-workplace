package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.herb.Herbs;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The Sickness's eight far herbs, the Three-Herb Tonic and the arcs' {@code herb_patch} (ROADMAP 31.17). */
public class HerbGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** A patch's middle in huge_area: its 6 blocks round it stay well inside the area. */
	private static final BlockPos MIDDLE = new BlockPos(15, 2, 15);

	/** Takes away the barriers over the area, so the sky is open (shade is then only what a test puts there). */
	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static BlockState leaves() {
		return Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	/**
	 * A herb's life: a player places it on its ground (and can't on obsidian), a hundred random ticks change nothing and
	 * plant nothing, bone meal does nothing, it breaks into its own item, and it pops off when its ground is dug out.
	 */
	private static void herbLife(GameTestHelper helper, HerbBlock herb, Block ground) {
		openSky(helper);
		BlockPos on = new BlockPos(1, 1, 1);
		BlockPos bad = new BlockPos(3, 1, 1);
		BlockPos second = new BlockPos(5, 1, 1);
		helper.setBlock(on, ground);
		helper.setBlock(bad, Blocks.OBSIDIAN);
		helper.setBlock(second, ground);
		if (herb.shaded()) {
			for (int x = 0; x <= 6; x++) {
				helper.setBlock(new BlockPos(x, 5, 1), leaves()); // a canopy
			}
		}
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(herb, 2));
		helper.placeAt(player, player.getMainHandItem(), on, Direction.UP);
		BlockPos plant = on.above();
		helper.assertBlockPresent(herb, plant);
		helper.placeAt(player, player.getMainHandItem(), bad, Direction.UP);
		helper.assertBlockNotPresent(herb, bad.above());

		ServerLevel level = helper.getLevel();
		BlockState before = helper.getBlockState(plant);
		helper.assertFalse(before.isRandomlyTicking(), herb + " takes random ticks");
		for (int i = 0; i < 100; i++) {
			helper.randomTick(plant);
		}
		helper.assertTrue(helper.getBlockState(plant) == before, herb + " changed under random ticks: " + helper.getBlockState(plant));
		helper.assertFalse(BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, helper.absolutePos(plant)), herb + " took bone meal");
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 7, 5, 3)) {
			if (helper.getBlockState(p).is(herb)) {
				count++;
			}
		}
		helper.assertTrue(count == 1, herb + " spread: " + count + " of it round its spot");

		level.destroyBlock(helper.absolutePos(plant), true, player); // broken by the player: it drops itself
		helper.assertBlockNotPresent(herb, plant);
		helper.assertItemEntityPresent(herb.asItem(), plant, 2);

		helper.setBlock(second.above(), herb);
		helper.assertBlockPresent(herb, second.above());
		helper.setBlock(second, Blocks.AIR);
		helper.assertBlockNotPresent(herb, second.above());
		helper.succeed();
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void frostbloomGrowsOnSnowAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.FROSTBLOOM, Blocks.SNOW_BLOCK);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void sunthistleGrowsOnRedSandAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.SUNTHISTLE, Blocks.RED_SAND);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void mirewortGrowsOnMudAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.MIREWORT, Blocks.MUD);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void peakmossGrowsOnStoneAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.PEAKMOSS, Blocks.STONE);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void jungleOrchidGrowsOnGrassAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.JUNGLE_ORCHID, Blocks.GRASS_BLOCK);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void seaLavenderGrowsOnGravelAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.SEA_LAVENDER, Blocks.GRAVEL);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void glowcapGrowsOnPodzolInShadeAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.GLOWCAP, Blocks.PODZOL);
	}

	//$ gametest AREA
	@GameTest(template = AREA)
	public void petalBalmGrowsOnGrassAndStays(GameTestHelper helper) {
		herbLife(helper, ModBlocks.PETAL_BALM, Blocks.GRASS_BLOCK);
	}

	/** Each herb's other ground takes it too, and each refuses a ground that isn't its own. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void eachHerbKeepsToItsGround(GameTestHelper helper) {
		openSky(helper);
		Object[][] cases = {
			{ModBlocks.FROSTBLOOM, Blocks.GRASS_BLOCK, Blocks.SAND},
			{ModBlocks.SUNTHISTLE, Blocks.SAND, Blocks.GRASS_BLOCK},
			{ModBlocks.SUNTHISTLE, Blocks.TERRACOTTA, Blocks.STONE},
			{ModBlocks.MIREWORT, Blocks.GRASS_BLOCK, Blocks.SAND},
			{ModBlocks.PEAKMOSS, Blocks.GRASS_BLOCK, Blocks.SAND},
			{ModBlocks.JUNGLE_ORCHID, Blocks.GRASS_BLOCK, Blocks.STONE},
			{ModBlocks.SEA_LAVENDER, Blocks.SAND, Blocks.GRASS_BLOCK},
			{ModBlocks.PETAL_BALM, Blocks.GRASS_BLOCK, Blocks.SAND},
		};
		ServerLevel level = helper.getLevel();
		int x = 0;
		for (Object[] c : cases) {
			HerbBlock herb = (HerbBlock) c[0];
			BlockPos fit = new BlockPos(x, 1, 1);
			BlockPos unfit = new BlockPos(x, 1, 3);
			helper.setBlock(fit, (Block) c[1]);
			helper.setBlock(unfit, (Block) c[2]);
			helper.assertTrue(herb.defaultBlockState().canSurvive(level, helper.absolutePos(fit.above())), herb + " won't stand on " + c[1]);
			helper.assertFalse(herb.defaultBlockState().canSurvive(level, helper.absolutePos(unfit.above())), herb + " stands on " + c[2]);
			x++;
		}
		helper.succeed();
	}

	/** The Glowcap gives light 5 and needs shade: on podzol under the open sky it won't stand, and a player can't place it. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void glowcapGlowsAndNeedsShade(GameTestHelper helper) {
		openSky(helper);
		helper.assertTrue(ModBlocks.GLOWCAP.defaultBlockState().getLightEmission() == 5, "Glowcap's light isn't 5");
		helper.assertTrue(ModBlocks.FROSTBLOOM.defaultBlockState().getLightEmission() == 0, "Frostbloom glows");
		BlockPos ground = new BlockPos(2, 1, 2);
		helper.setBlock(ground, Blocks.PODZOL);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.GLOWCAP, 2));
		helper.placeAt(player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertBlockNotPresent(ModBlocks.GLOWCAP, ground.above());
		helper.setBlock(ground.above(4), Blocks.STONE); // shade
		helper.placeAt(player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertBlockPresent(ModBlocks.GLOWCAP, ground.above());
		helper.succeed();
	}

	/** Every herb has its biome tag with biomes in it, its item, and its name; the tonic is a stack of 16 with its name. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herbsHaveBiomesItemsAndNames(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
		helper.assertTrue(Herbs.all().size() == 8, "not eight herbs");
		for (HerbBlock herb : Herbs.all()) {
			int n = biomes.getTag(Herbs.biomes(herb)).map(t -> t.size()).orElse(0);
			helper.assertTrue(n > 0, "no biomes in " + Herbs.biomes(herb).location());
			helper.assertTrue(herb.asItem() != Items.AIR, herb + " has no item");
			String name = herb.getName().getString();
			helper.assertFalse(name.startsWith("block."), herb + " has no name: " + name);
		}
		helper.assertTrue(biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.withDefaultNamespace("dark_forest")))
			.is(Herbs.biomes(ModBlocks.GLOWCAP)), "the dark forest isn't Glowcap's");
		helper.assertTrue(biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, net.minecraft.resources.ResourceLocation.withDefaultNamespace("badlands")))
			.is(Herbs.biomes(ModBlocks.SUNTHISTLE)), "the badlands aren't Sunthistle's");
		ItemStack tonic = new ItemStack(ModItems.THREE_HERB_TONIC);
		helper.assertTrue(tonic.getMaxStackSize() == 16, "the tonic stacks to " + tonic.getMaxStackSize());
		helper.assertTrue(tonic.getHoverName().getString().equals("Three-Herb Tonic"), "the tonic is called " + tonic.getHoverName().getString());
		helper.assertTrue(Herbs.byId("frostbloom") == ModBlocks.FROSTBLOOM && Herbs.byId("aliveworkplace:glowcap") == ModBlocks.GLOWCAP
			&& Herbs.byId("minecraft:poppy") == null && Herbs.byId("bad id!") == null, "herbs by id");
		helper.succeed();
	}

	/** The herbs planted round {@code MIDDLE} in the area, by position. */
	private static List<BlockPos> planted(GameTestHelper helper, HerbBlock herb) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(0, 1, 0, 29, 8, 29)) {
			if (helper.getBlockState(p).is(herb)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/**
	 * A patch plants 5 to 9 only on fit ground within 6 blocks: grass on the left, stone on the right, a pool and a
	 * player's planks in the grass. None goes on stone, water or planks, none past 6 blocks, though grass runs on.
	 */
	//$ gametest_ticks_batch HUGE '40' '"herbPatch"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "herbPatch")
	public void aPatchPlantsOnlyOnFitGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), x < 15 ? Blocks.GRASS_BLOCK : Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(13, 1, 15), Blocks.WATER);
		helper.setBlock(new BlockPos(12, 1, 15), Blocks.WATER);
		helper.setBlock(new BlockPos(13, 1, 13), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(12, 2, 17), Blocks.SHORT_GRASS); // grass is replaced
		int planted = Herbs.patch(helper.getLevel(), helper.absolutePos(MIDDLE), ModBlocks.JUNGLE_ORCHID, RandomSource.create(31));
		List<BlockPos> at = planted(helper, ModBlocks.JUNGLE_ORCHID);
		helper.assertTrue(planted >= Herbs.LEAST && planted <= Herbs.MOST, "planted " + planted);
		helper.assertTrue(at.size() == planted, "said " + planted + ", there are " + at.size());
		for (BlockPos p : at) {
			int dx = p.getX() - MIDDLE.getX();
			int dz = p.getZ() - MIDDLE.getZ();
			helper.assertTrue(dx * dx + dz * dz <= 36, "one past 6 blocks at " + p);
			helper.assertTrue(p.getY() == 2 && helper.getBlockState(p.below()).is(Blocks.GRASS_BLOCK), "one off the grass at " + p);
		}
		// Twenty more seeds: never more than 9, never fewer than 5 while there's the ground, always on the grass.
		for (int seed = 0; seed < 20; seed++) {
			for (BlockPos p : planted(helper, ModBlocks.JUNGLE_ORCHID)) {
				helper.setBlock(p, Blocks.AIR);
			}
			int n = Herbs.patch(helper.getLevel(), helper.absolutePos(MIDDLE), ModBlocks.JUNGLE_ORCHID, RandomSource.create(seed));
			helper.assertTrue(n >= Herbs.LEAST && n <= Herbs.MOST && planted(helper, ModBlocks.JUNGLE_ORCHID).size() == n, "seed " + seed + ": planted " + n);
			for (BlockPos p : planted(helper, ModBlocks.JUNGLE_ORCHID)) {
				helper.assertTrue(helper.getBlockState(p.below()).is(Blocks.GRASS_BLOCK), "seed " + seed + ": one off the grass at " + p);
			}
		}
		helper.succeed();
	}

	/** Without enough fit ground a patch plants what fits: three sand blocks in a field of stone take three Sea Lavender. */
	//$ gametest_ticks_batch HUGE '40' '"herbPatchFew"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "herbPatchFew")
	public void aPatchPlantsOnlyWhatFits(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		helper.setBlock(new BlockPos(15, 1, 15), Blocks.SAND);
		helper.setBlock(new BlockPos(18, 1, 17), Blocks.GRAVEL);
		helper.setBlock(new BlockPos(11, 1, 14), Blocks.SAND);
		helper.setBlock(new BlockPos(15, 1, 23), Blocks.SAND); // 8 blocks off: too far
		for (int seed = 0; seed < 5; seed++) {
			for (BlockPos p : planted(helper, ModBlocks.SEA_LAVENDER)) {
				helper.setBlock(p, Blocks.AIR);
			}
			int planted = Herbs.patch(helper.getLevel(), helper.absolutePos(MIDDLE), ModBlocks.SEA_LAVENDER, RandomSource.create(seed));
			helper.assertTrue(planted == 3 && planted(helper, ModBlocks.SEA_LAVENDER).size() == 3, "seed " + seed + ": planted " + planted);
			helper.assertBlockNotPresent(ModBlocks.SEA_LAVENDER, new BlockPos(15, 2, 23));
		}
		helper.succeed();
	}

	/** A Glowcap patch goes only into the shade: half the grass under a leaf canopy, half open; all of it under the leaves. */
	//$ gametest_ticks_batch HUGE '40' '"herbPatchShade"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "herbPatchShade")
	public void aGlowcapPatchKeepsToTheShade(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				if (z < 15) {
					helper.setBlock(new BlockPos(x, 7, z), leaves());
				}
			}
		}
		int planted = Herbs.patch(helper.getLevel(), helper.absolutePos(MIDDLE), ModBlocks.GLOWCAP, RandomSource.create(7));
		List<BlockPos> at = planted(helper, ModBlocks.GLOWCAP);
		helper.assertTrue(planted >= Herbs.LEAST && at.size() == planted, "planted " + planted + ", found " + at.size());
		for (BlockPos p : at) {
			helper.assertTrue(p.getY() == 2 && p.getZ() < 15, "a Glowcap in the sun at " + p);
		}
		helper.succeed();
	}

	/**
	 * The arc's way: a {@code herb_patch} effect notes its place at the rule's spot, and once built (a player near) the
	 * patch is planted there, the place is done and its {@code <key>_done} flag set. An unknown herb is refused.
	 */
	//$ gametest_ticks_batch HUGE '40' '"herbPatchArc"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "herbPatchArc")
	public void theArcPlantsAPatchWhereItsRuleSays(GameTestHelper helper) {
		Leftovers.clear(helper);
		openSky(helper);
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		ServerLevel level = helper.getLevel();
		ArcState s = new ArcState("aliveworkplace:sickness_test");
		JsonObject json = new JsonObject();
		json.addProperty("type", "herb_patch");
		json.addProperty("herb", "aliveworkplace:petal_balm");
		json.addProperty("key", "balm");
		JsonObject rule = new JsonObject();
		JsonArray offset = new JsonArray();
		offset.add(3);
		offset.add(-2);
		rule.add("offset", offset);
		json.add("rule", rule);
		BlockPos hall = helper.absolutePos(new BlockPos(12, 2, 17));
		ArcEffects.herbPatch(level, hall, s, json);
		ArcState.ArcPlace p = s.place("balm");
		helper.assertTrue(p != null && p.waiting() && p.spot.getX() == hall.getX() + 3 && p.spot.getZ() == hall.getZ() - 2, "the patch's place: " + (p == null ? null : p.spot));
		helper.assertTrue(planted(helper, ModBlocks.PETAL_BALM).isEmpty(), "planted before anyone came");
		helper.assertTrue(ArcEffects.build(level, s, p) && !p.waiting(), "the patch wasn't planted");
		int n = planted(helper, ModBlocks.PETAL_BALM).size();
		helper.assertTrue(n >= Herbs.LEAST && n <= Herbs.MOST, "planted " + n);
		helper.assertTrue(s.flags.containsKey("balm_done"), "no balm_done flag");

		JsonObject bad = json.deepCopy();
		bad.addProperty("herb", "minecraft:poppy");
		boolean refused = false;
		try {
			ArcEffects.herbPatch(level, hall, s, bad);
		} catch (IllegalArgumentException e) {
			refused = true;
		}
		helper.assertTrue(refused, "a poppy patch was taken");
		helper.succeed();
	}
}
