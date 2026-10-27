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
	public void cropsArePlantedAndFurnacesLecternsAndJukeboxesBuiltEmpty(GameTestHelper helper) {
		// B493: planted at age 0, no berries; growing since still counts as built
		BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
		BlockState wheat = MaterialRules.forPlacement(ripe);
		helper.assertTrue(wheat.getValue(net.minecraft.world.level.block.CropBlock.AGE) == 0, "ripe wheat is placed as " + wheat);
		helper.assertTrue(MaterialRules.matches(ripe, wheat), "wheat that grew no longer counts as built");
		BlockState beets = MaterialRules.forPlacement(Blocks.BEETROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, 3));
		helper.assertTrue(beets.getValue(net.minecraft.world.level.block.BeetrootBlock.AGE) == 0, "ripe beetroots are placed as " + beets);
		helper.assertTrue(MaterialRules.forPlacement(Blocks.NETHER_WART.defaultBlockState().setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3))
			.getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 0, "ripe nether wart isn't placed young");
		helper.assertTrue(MaterialRules.forPlacement(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3))
			.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) == 0, "a ripe berry bush isn't placed young");
		helper.assertTrue(MaterialRules.forPlacement(Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2)
			.setValue(net.minecraft.world.level.block.CocoaBlock.FACING, Direction.EAST)).getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 0, "ripe cocoa isn't placed young");
		BlockState vines = MaterialRules.forPlacement(Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
		helper.assertTrue(!vines.getValue(BlockStateProperties.BERRIES), "cave vines are placed with a berry");
		helper.assertTrue(MaterialRules.matches(Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true), vines), "vines that fruited no longer count as built");
		// B494: no book or disc shown that the block doesn't hold
		BlockState lectern = MaterialRules.forPlacement(Blocks.LECTERN.defaultBlockState().setValue(BlockStateProperties.HAS_BOOK, true).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		helper.assertTrue(!lectern.getValue(BlockStateProperties.HAS_BOOK) && lectern.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST, "a lectern is placed as " + lectern);
		helper.assertTrue(!MaterialRules.forPlacement(Blocks.JUKEBOX.defaultBlockState().setValue(BlockStateProperties.HAS_RECORD, true)).getValue(BlockStateProperties.HAS_RECORD),
			"a jukebox is placed showing a disc");
		// B495: furnaces built cold; campfires as drawn
		for (BlockState furnace : List.of(Blocks.FURNACE.defaultBlockState(), Blocks.SMOKER.defaultBlockState(), Blocks.BLAST_FURNACE.defaultBlockState())) {
			BlockState lit = furnace.setValue(BlockStateProperties.LIT, true);
			BlockState placed = MaterialRules.forPlacement(lit);
			helper.assertTrue(!placed.getValue(BlockStateProperties.LIT), "a burning " + furnace.getBlock() + " is placed lit");
			helper.assertTrue(MaterialRules.matches(lit, placed), "a " + furnace.getBlock() + " lit since no longer counts as built");
		}
		BlockState campfire = Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
		helper.assertTrue(MaterialRules.forPlacement(campfire).getValue(BlockStateProperties.LIT), "a blueprint's lit campfire is placed out");
		// B496: 1.21.1 saves a hive's bees as 'bees'
		CompoundTag hive = new CompoundTag();
		ListTag bees = new ListTag();
		CompoundTag bee = new CompoundTag();
		bee.putInt("ticks_in_hive", 5);
		bees.add(bee);
		hive.put("bees", bees);
		BlockPos at = new BlockPos(0, 1, 0);
		helper.setBlock(at, Blocks.BEE_NEST);
		CompoundTag out = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(at), helper.getBlockState(at), helper.getBlockEntity(at), hive);
		helper.assertTrue(out == null || !out.contains("bees"), "a nest's bees are copied: " + out);
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
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aFluidHeldInBlockDataIsNotCopied(GameTestHelper helper) {
		// B486: Create's tank as it saves 1000 mB of lava ({TankContent: {Fluid: {id, amount}}}), a Forge-style and a
		// Fabric-style tank, and a list of tanks (Create's spout and basin): every fluid goes, the tank's own setup stays
		BlockPos pos = new BlockPos(0, 1, 0);
		helper.setBlock(pos, Blocks.OAK_SIGN);
		CompoundTag nbt = new CompoundTag();
		nbt.put("TankContent", tankOf(fluid("id", "minecraft:lava")));
		nbt.putInt("Size", 2);
		nbt.putInt("Height", 3);
		nbt.putInt("Luminosity", 15);
		CompoundTag forge = new CompoundTag();
		forge.putString("FluidName", "minecraft:water");
		forge.putInt("Amount", 1000);
		nbt.put("forge", forge);
		CompoundTag fabric = new CompoundTag();
		fabric.put("variant", fluid("fluid", "minecraft:flowing_lava"));
		fabric.putLong("amount", 81000);
		fabric.putLong("capacity", 81000);
		nbt.put("fabric", fabric);
		ListTag tanks = new ListTag();
		CompoundTag segment = new CompoundTag();
		segment.put("TankContent", tankOf(fluid("id", "minecraft:lava")));
		segment.putFloat("Level", 1);
		tanks.add(segment);
		nbt.put("Tanks", tanks);
		CompoundTag empty = fluid("id", "minecraft:empty");
		nbt.put("emptyTank", empty);
		CompoundTag out = BlockEntityData.sanitize(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), helper.getBlockEntity(pos), nbt);
		helper.assertTrue(out != null, "the tank's data was dropped whole");
		helper.assertTrue(out.getCompound("TankContent").isEmpty(), "Create's tank keeps its lava: " + out);
		helper.assertTrue(out.getInt("Size") == 2 && out.getInt("Height") == 3, "the tank's size was lost: " + out);
		helper.assertTrue(!out.contains("Luminosity"), "the emptied tank still glows: " + out);
		helper.assertTrue(!out.contains("forge"), "a Forge-style fluid stack is copied: " + out);
		helper.assertTrue(!out.getCompound("fabric").contains("variant") && !out.getCompound("fabric").contains("amount")
			&& out.getCompound("fabric").getLong("capacity") == 81000, "a Fabric-style tank isn't built empty with its capacity: " + out);
		helper.assertTrue(out.getList("Tanks", 10).size() == 1 && out.getList("Tanks", 10).getCompound(0).getCompound("TankContent").isEmpty()
			&& out.getList("Tanks", 10).getCompound(0).contains("Level"), "a list of tanks isn't emptied in place: " + out);
		helper.assertTrue(out.contains("emptyTank"), "an empty fluid stack was taken for a fluid: " + out);
		helper.succeed();
	}

	private static CompoundTag fluid(String key, String id) {
		CompoundTag fluid = new CompoundTag();
		fluid.putString(key, id);
		fluid.putLong("amount", 81000);
		return fluid;
	}

	/** Create's {@code TankContent}: {@code {Fluid: {...}}}. */
	private static CompoundTag tankOf(CompoundTag fluid) {
		CompoundTag content = new CompoundTag();
		content.put("Fluid", fluid);
		return content;
	}
}
