package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane C, 2026-11-04; B878 follow-up): B878 asks that every crafter-family line ("fetching what %s %s take",
 * "making %s %s", "taking %s %s to the guard") names many things in the plural, the Fletcher's included. Its fix names
 * the item through {@code Words.itemName}, which falls back to the singular when the lang has no
 * {@code plural.aliveworkplace.minecraft.<item>}; there is none for the Spectral Arrow, and a Fletcher always makes 8
 * of them for a guard ({@code FletcherWork.ARROWS}). Expected: "making 8 Spectral Arrows", never "8 Spectral Arrow".
 */
public class QaFletcherWords1104cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos POST = new BlockPos(19, 2, 19);
	private static final BlockPos POST_CHEST = new BlockPos(19, 2, 17);

	/** A Fletcher making 8 spectral arrows for a guard: every line with the count names them in the plural. */
	//$ gametest_ticks_batch AREA '1600' '"qaFw1104cSpectral"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaFw1104cSpectral")
	public void aFletcherMakingEightSpectralArrowsNamesThemInThePlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		helper.setBlock(TABLE, Blocks.FLETCHING_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.GLOWSTONE_DUST, 16));
		chest.setItem(1, new ItemStack(Items.ARROW, 4));
		Villager fletcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), fletcher, helper.absolutePos(TABLE), PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(POST_CHEST, Blocks.CHEST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		Container guardsChest = helper.getBlockEntity(POST_CHEST);
		Set<String> lines = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(fletcher, helper.getLevel().getGameTime());
			if (e != null && e.line() != null) {
				lines.add(e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			int arrows = guardsChest.countItem(Items.SPECTRAL_ARROW) + io.github.jcondedata.aliveworkplace.guard.Guards.quiver(guard);
			helper.assertTrue(arrows == 8, "setup: spectral arrows for the guard: " + arrows);
			helper.assertTrue(lines.contains("making 8 Spectral Arrows"), "want 'making 8 Spectral Arrows'; lines seen: " + lines);
			for (String l : lines) {
				helper.assertTrue(!l.contains("Spectral Arrow ") && !l.endsWith("Spectral Arrow"), "8 spectral arrows named as one: \"" + l + "\"");
				helper.assertTrue(!l.contains("aliveworkplace."), "a raw key: \"" + l + "\"");
			}
		});
	}
}
