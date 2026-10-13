package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The goods of a Wonder's stages (ROADMAP 35.7) for the tests that raise one: since 35.7 a stage starts only once its
 * goods are in, so a test that is about the building puts them in the stockpile first.
 */
public final class WonderGoodsKit {
	/** Where the goods chest goes for a stone at {@code stone} of a Wonder at {@code at}: four out and two along, away from the Wonder. */
	public static BlockPos goodsChest(BlueprintData.Placement at, BlockPos stone) {
		int dx = Integer.signum(stone.getX() - at.origin().getX());
		int dz = Integer.signum(stone.getZ() - at.origin().getZ());
		return stone.offset(4 * (dx == 0 ? -1 : dx), 0, 2 * (dz == 0 ? -1 : dz));
	}

	/**
	 * A chest by where the stone of the Wonder at {@code at} goes, holding every stage's goods of {@code wonder}: each stage
	 * is dedicated as soon as it may start (by day). Returns the chest.
	 */
	public static BlockPos stockAll(ServerLevel level, ResourceLocation wonder, BlueprintData.Placement at) {
		BlockPos stone = WonderStages.stoneSpot(level, at).orElseThrow();
		return stockAll(level, wonder, goodsChest(at, stone));
	}

	/** A chest at {@code chest} holding every stage's goods of {@code wonder}. */
	public static BlockPos stockAll(ServerLevel level, ResourceLocation wonder, BlockPos chest) {
		Wonder w = Wonders.loaded().get(wonder);
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		Container box = (Container) level.getBlockEntity(chest);
		int slot = 0;
		for (Wonder.Stage stage : w.stages()) {
			for (Wonder.Good good : stage.goods()) {
				slot = put(box, slot, good, good.count());
			}
		}
		return chest;
	}

	/** Puts {@code count} of what {@code good} takes into {@code box} from {@code slot} on; returns the next free slot. */
	public static int put(Container box, int slot, Wonder.Good good, int count) {
		ItemStack one = WonderGoods.icon(good);
		for (int left = count; left > 0; ) {
			int n = Math.min(left, one.getMaxStackSize());
			box.setItem(slot++, one.copyWithCount(n));
			left -= n;
		}
		return slot;
	}

	/** Hands the goods of the Wonder {@code id}'s current stage in at its stone, as a villager with no chest would. */
	public static void handInStage(ServerLevel level, UUID id) {
		WonderSite site = WonderManager.get(level).site(id).orElseThrow();
		List<Wonder.Good> goods = WonderGoods.goods(site);
		for (int i = 0; i < goods.size(); i++) {
			ItemStack one = WonderGoods.icon(goods.get(i));
			for (int left = goods.get(i).count(); left > 0; ) {
				int n = Math.min(left, one.getMaxStackSize());
				WonderGoods.deliver(level, id, i, one.copyWithCount(n));
				left -= n;
			}
		}
	}

	private WonderGoodsKit() {
	}
}
