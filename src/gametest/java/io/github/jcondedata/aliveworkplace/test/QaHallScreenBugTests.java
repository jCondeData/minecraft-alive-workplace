package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1026-1254) bug tests for ROADMAP 40.3, failing on main until fixed: Job ▾'s "Mason · 2 free Stonecutter"
 * (no plural) when a village has two or more free blocks of a job.
 */
public class QaHallScreenBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/**
	 * Two free blocks of each of three jobs: Job ▾ for a jobless villager reads "Mason · 2 free Stonecutters",
	 * "Librarian · 2 free Lecterns" and "Farmer · 2 free Composters" (the spec's "Mason · 1 free Stonecutter", counted).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobPlural"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobPlural")
	public void twoFreeBlocksArePlural(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Map<VillagerProfession, Block> jobs = Map.of(VillagerProfession.MASON, Blocks.STONECUTTER, VillagerProfession.LIBRARIAN, Blocks.LECTERN,
			VillagerProfession.FARMER, Blocks.COMPOSTER);
		int x = 6;
		for (Block block : jobs.values()) {
			helper.setBlock(new BlockPos(x, 2, 8), block);
			helper.setBlock(new BlockPos(x, 2, 22), block);
			x += 4;
		}
		Villager pip = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 15));
		pip.setNoAi(true);
		pip.setCustomName(Component.literal("Pip"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			helper.useBlock(HALL, player);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
			int slot = -1;
			for (int s = 0; s < ChoiceMenu.SIZE; s++) {
				CompoundTag row = HallPeople.row(menu.icon(s));
				if (row != null && row.getString("uuid").equals(pip.getStringUUID())) {
					slot = s;
				}
			}
			helper.assertTrue(slot >= 0 && menu.clickMenuButton(player, HallJobs.OPEN + slot), "Job ▾ wasn't taken");
			List<String> wrong = new ArrayList<>();
			Map<VillagerProfession, String> want = Map.of(VillagerProfession.MASON, "Mason · 2 free Stonecutters", VillagerProfession.LIBRARIAN,
				"Librarian · 2 free Lecterns", VillagerProfession.FARMER, "Farmer · 2 free Composters");
			for (ItemStack line : HallJobs.lines(menu.icon(slot))) {
				CompoundTag mark = HallJobs.mark(line);
				for (var e : want.entrySet()) {
					if (mark != null && mark.getInt("id") == BuiltInRegistries.VILLAGER_PROFESSION.getId(e.getKey()) && !mark.getString("state").equals("header")
						&& !line.getHoverName().getString().equals(e.getValue())) {
						wrong.add(line.getHoverName().getString() + " (want " + e.getValue() + ")");
					}
				}
			}
			helper.assertTrue(wrong.isEmpty(), "Job ▾ lines without the plural: " + wrong);
			helper.succeed();
		});
	}
}
