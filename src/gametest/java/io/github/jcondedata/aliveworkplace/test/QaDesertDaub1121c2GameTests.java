package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-c-1121-2153), from B1367 and docs/wiki/daub-house.md ("desert: sandstone on a sandstone plinth, jungle wood,
 * a red sandstone roof"): the desert village's Daub House kept all 113 mud-brick daub panels. DaubHouseGameTests counts
 * the mud bricks left (none); this checks the other side: every daub panel of the plains piece (as drawn) is sandstone
 * in the desert piece, cell for cell (no panel left out as a hole or swapped for something else), and no mud-brick
 * block of any shape (stairs, slab, wall) is left in the desert piece.
 */
public class QaDesertDaub1121c2GameTests implements FabricGameTest {
	private static final String DAUB = "house_t1_b";

	private static Map<BlockPos, BlockState> cells(GameTestHelper helper, String style) {
		ResourceLocation id = VillageHouses.home(style, DAUB);
		StructureTemplate template = helper.getLevel().getStructureManager().get(id)
			.orElseThrow(() -> new GameTestAssertException("missing " + id));
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
		BlockState[] states = new BlockState[palette.size()];
		for (int i = 0; i < palette.size(); i++) {
			states[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i));
		}
		Map<BlockPos, BlockState> out = new HashMap<>();
		ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			ListTag pos = b.getList("pos", Tag.TAG_INT);
			out.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), states[b.getInt("state")]);
		}
		return out;
	}

	private static String name(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	/** Each plains daub panel's cell is a full sandstone block in the desert piece; at least 100 such panels. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyDaubPanelIsSandstoneInTheDesert(GameTestHelper helper) {
		Map<BlockPos, BlockState> plains = cells(helper, "plains");
		Map<BlockPos, BlockState> desert = cells(helper, "desert");
		int panels = 0;
		Map<String, Integer> wrong = new TreeMap<>();
		BlockPos example = null;
		for (Map.Entry<BlockPos, BlockState> e : plains.entrySet()) {
			if (!e.getValue().is(Blocks.MUD_BRICKS)) {
				continue;
			}
			panels++;
			BlockState there = desert.get(e.getKey());
			String n = there == null ? "nothing" : name(there);
			boolean sandstone = there != null && n.contains("sandstone") && !n.contains("stairs") && !n.contains("slab") && !n.contains("wall");
			if (!sandstone) {
				wrong.merge(n, 1, Integer::sum);
				example = example == null ? e.getKey() : example;
			}
		}
		helper.assertTrue(panels >= 100, "the plains Daub House has only " + panels + " mud-brick daub panels (113 drawn)");
		helper.assertTrue(wrong.isEmpty(), "desert daub panels not sandstone: " + wrong + ", e.g. at " + example);
		AliveWorkplace.LOG.info("[qa-desert-daub-1121c2] {} daub panels, all sandstone in the desert piece", panels);
		helper.succeed();
	}

	/** No mud brick of any shape is left in the desert piece (the wiki names none for it). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void noMudBrickOfAnyShapeInTheDesertPiece(GameTestHelper helper) {
		Map<String, Integer> mud = new TreeMap<>();
		for (BlockState s : cells(helper, "desert").values()) {
			String n = name(s);
			if (n.startsWith("mud_brick") || n.equals("packed_mud") || n.equals("mud")) {
				mud.merge(n, 1, Integer::sum);
			}
		}
		helper.assertTrue(mud.isEmpty(), "the desert Daub House still has mud blocks: " + mud);
		helper.succeed();
	}
}
