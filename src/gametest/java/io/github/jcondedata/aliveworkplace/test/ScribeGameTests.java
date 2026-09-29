package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
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

/** Librarians with an enchanting table enchant the village's gear. */
public class ScribeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A guard's plain iron sword gets enchanted with lapis from the librarian's chest. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "scribe_enchants")
	public void librarianEnchantsTheGuardsSword(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		helper.setDayTime(2000);
		BlockPos lectern = new BlockPos(2, 2, 2);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.setBlock(new BlockPos(5, 2, 2), Blocks.ENCHANTING_TABLE);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(Items.LAPIS_LAZULI, 3));
		Villager librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), librarian, helper.absolutePos(lectern), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		helper.setBlock(new BlockPos(19, 2, 19), ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(new BlockPos(19, 2, 19)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		guard.setNoAi(true); // standing still (on patrol, a guard by the area's edge can wander out of reach)
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).isEnchanted(), "the sword isn't enchanted");
			helper.assertTrue(chest.countItem(Items.LAPIS_LAZULI) < 3, "no lapis used");
			helper.assertTrue(librarian.getAttachedOrElse(ModAttachments.ITEMS_ENCHANTED, 0) >= 1, "nothing enchanted");
			Village.RADIUS = 0;
		});
	}
}
