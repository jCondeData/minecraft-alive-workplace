package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BlockEntityData;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * B429, B434, B449: what a block holds as its state or as an entity in its data is never copied by a builder, while
 * shipped blueprints build as before (a water cauldron stays full) and a block placed empty, then filled by a player,
 * still counts as built (no endless rebuilding).
 */
public class BuiltEmptyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void contentsHeldAsStateArePlacedEmpty(GameTestHelper helper) {
		BlockState lava = MaterialRules.forPlacement(Blocks.LAVA_CAULDRON.defaultBlockState());
		helper.assertTrue(lava.is(Blocks.CAULDRON), "a lava cauldron is placed as " + lava);
		helper.assertTrue(MaterialRules.forPlacement(Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)).is(Blocks.CAULDRON),
			"a powder snow cauldron isn't placed empty");
		helper.assertTrue(MaterialRules.requirements(lava, null).equals(List.of(new MaterialRules.Requirement(Items.CAULDRON, 1))),
			"a scanned lava cauldron asks " + MaterialRules.requirements(lava, null));
		BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
		helper.assertTrue(MaterialRules.forPlacement(water) == water, "a shipped water cauldron is no longer built full: " + MaterialRules.forPlacement(water));
		BlockState composter = MaterialRules.forPlacement(Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
		helper.assertTrue(composter.getValue(ComposterBlock.LEVEL) == 0, "a ready composter is placed at level " + composter.getValue(ComposterBlock.LEVEL));
		for (BlockState hive : List.of(Blocks.BEEHIVE.defaultBlockState(), Blocks.BEE_NEST.defaultBlockState())) {
			BlockState placed = MaterialRules.forPlacement(hive.setValue(BeehiveBlock.HONEY_LEVEL, 5).setValue(BeehiveBlock.FACING, Direction.EAST));
			helper.assertTrue(placed.getValue(BeehiveBlock.HONEY_LEVEL) == 0 && placed.getValue(BeehiveBlock.FACING) == Direction.EAST,
				"a full " + hive.getBlock() + " is placed as " + placed);
		}
		BlockState anchor = MaterialRules.forPlacement(Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 4));
		helper.assertTrue(anchor.getValue(RespawnAnchorBlock.CHARGE) == 0, "a charged respawn anchor is placed charged: " + anchor);
		BlockState shelf = MaterialRules.forPlacement(Blocks.CHISELED_BOOKSHELF.defaultBlockState()
			.setValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		helper.assertTrue(!shelf.getValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED) && shelf.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST,
			"a chiseled bookshelf is placed as " + shelf);
		BlockState stand = MaterialRules.forPlacement(Blocks.BREWING_STAND.defaultBlockState().setValue(BlockStateProperties.HAS_BOTTLE_1, true));
		helper.assertTrue(!stand.getValue(BlockStateProperties.HAS_BOTTLE_1), "a brewing stand is placed showing a bottle");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aBlockFilledAfterItWasBuiltStillCountsAsBuilt(GameTestHelper helper) {
		BlockState cauldron = MaterialRules.forPlacement(Blocks.LAVA_CAULDRON.defaultBlockState());
		for (BlockState filled : List.of(Blocks.LAVA_CAULDRON.defaultBlockState(), Blocks.WATER_CAULDRON.defaultBlockState(), Blocks.CAULDRON.defaultBlockState())) {
			helper.assertTrue(MaterialRules.matches(filled, cauldron), filled + " doesn't count as the cauldron built empty");
		}
		helper.assertTrue(!MaterialRules.matches(Blocks.COMPOSTER.defaultBlockState(), cauldron), "a composter counts as a cauldron");
		BlockState composter = MaterialRules.forPlacement(Blocks.COMPOSTER.defaultBlockState());
		helper.assertTrue(MaterialRules.matches(Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 5), composter), "a composter fed since doesn't count");
		BlockState hive = MaterialRules.forPlacement(Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		helper.assertTrue(MaterialRules.matches(Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 3), hive), "a hive with honey since doesn't count");
		helper.assertTrue(!MaterialRules.matches(Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.WEST), hive),
			"a hive facing the wrong way counts as built");
		BlockState shelf = MaterialRules.forPlacement(Blocks.CHISELED_BOOKSHELF.defaultBlockState());
		helper.assertTrue(MaterialRules.matches(shelf.setValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED, true), shelf), "a shelf given a book since doesn't count");
		// a shipped water cauldron is still wanted full, as before
		BlockState water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
		helper.assertTrue(MaterialRules.matches(water, MaterialRules.forPlacement(water)), "a full water cauldron doesn't match itself");
		helper.succeed();
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void anEntitySavedWithoutItsIdIsNotCopied(GameTestHelper helper) {
		BlockPos pos = new BlockPos(0, 1, 0);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		CompoundTag mob = new CompoundTag();
		mob.putUUID("UUID", java.util.UUID.randomUUID());
		ListTag at = new ListTag();
		at.add(DoubleTag.valueOf(1));
		at.add(DoubleTag.valueOf(2));
		at.add(DoubleTag.valueOf(3));
		mob.put("Pos", at);
		mob.putString("CustomName", "\"Odo\"");
		CompoundTag nbt = new CompoundTag();
		nbt.put("held", mob);
		CompoundTag looks = new CompoundTag();
		looks.putString("color", "red");
		nbt.put("looks", looks);
		CompoundTag out = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getBlockEntity(pos), nbt);
		helper.assertTrue(out != null && !out.contains("held"), "a mob saved without its id is copied: " + out);
		helper.assertTrue(out.contains("looks"), "plain data was dropped with it: " + out);
		helper.succeed();
	}
}
