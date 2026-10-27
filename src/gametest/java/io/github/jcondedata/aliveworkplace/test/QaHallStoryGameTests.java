package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.FamilyPage;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B547 (the People table's Story button, 40.2), from the bug's Expected: "a Story button on the open row opening
 * the life story (then Family) as shift-click did". The builder's own test covers a named worker and a named jobless
 * villager; these cover the other kinds the table lists (40.2: "Children and builders appear too"), a nitwit, and the
 * row's Find button next to Story, all through the hall's own entry points (a right-click on the block, the sidebar's
 * click, and the clicks the client sends for the row's buttons: Story is a shift-click, Find a plain click).
 */
public class QaHallStoryGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		if (name != null) {
			v.setCustomName(Component.literal(name));
		}
		return v;
	}

	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	/** The slot of {@code who}'s row in the table, -1 if none. */
	private static int slotOf(ChoiceMenu menu, Villager who) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(who.getStringUUID())) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean storyHint(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore != null && lore.lines().stream().anyMatch(l -> l.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("screen.aliveworkplace.hall.life_story_hint"));
	}

	private static boolean lifeStoryOpen(ChoiceMenu menu) {
		return menu.icon(FamilyPage.BUTTON).getHoverName().getString().equals("Family");
	}

	/**
	 * A named child, a named builder and a named nitwit each have Story on their open row, and it opens their life story
	 * with its Family button; an unnamed child's row offers none (the hall writes a life story only for a named villager).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallStoryKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallStoryKinds")
	public void storyOpensForANamedChildBuilderAndNitwit(GameTestHelper helper) {
		village(helper);
		BlockPos table = new BlockPos(8, 2, 8);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = villager(helper, 9, 10, "Bram");
		Villager child = villager(helper, 12, 20, "Pip");
		child.setAge(-24000);
		Villager nitwit = villager(helper, 18, 20, "Nob");
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager plainChild = villager(helper, 20, 12, null);
		plainChild.setAge(-24000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			Builders.employ(helper.getLevel(), builder, helper.absolutePos(table));
			for (Villager who : List.of(builder, child, nitwit)) {
				String name = who.getName().getString();
				ChoiceMenu menu = openPeople(helper, player);
				int slot = slotOf(menu, who);
				helper.assertTrue(slot >= 0, "no row for " + name);
				helper.assertTrue(storyHint(menu.icon(slot)), name + "'s row (" + HallPeople.row(menu.icon(slot)).getString("kind")
					+ ") offers no Story");
				menu.clicked(slot, 0, ClickType.QUICK_MOVE, player); // Story: the client's shift-click on the row
				helper.assertTrue(lifeStoryOpen(menu), name + "'s Story didn't open their life story: " + menu.icon(FamilyPage.BUTTON));
				player.closeContainer();
			}
			ChoiceMenu menu = openPeople(helper, player);
			int slot = slotOf(menu, plainChild);
			helper.assertTrue(slot >= 0, "no row for the unnamed child");
			helper.assertFalse(storyHint(menu.icon(slot)), "an unnamed child's row offers a story");
			helper.succeed();
		});
	}

	/**
	 * Find, beside Story on a named worker's open row, still only makes them glow (a plain click on the row): the table
	 * stays open on People and no life story or job page opens in its place.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallStoryFind"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallStoryFind")
	public void findBesideStoryOnlyMakesThemGlow(GameTestHelper helper) {
		village(helper);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.LECTERN);
		Villager dara = villager(helper, 8, 10, "Dara");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			Jobs.employ(helper.getLevel(), dara, helper.absolutePos(new BlockPos(8, 2, 8)), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			ChoiceMenu menu = openPeople(helper, player);
			int slot = slotOf(menu, dara);
			helper.assertTrue(slot >= 0, "no row for Dara");
			helper.assertTrue(storyHint(menu.icon(slot)), "Dara's row offers no Story");
			helper.assertFalse(dara.hasEffect(MobEffects.GLOWING), "Dara glows before Find");
			menu.clicked(slot, 0, ClickType.PICKUP, player); // Find: the client's plain click on the row
			helper.assertTrue(dara.hasEffect(MobEffects.GLOWING), "Find didn't make Dara glow");
			helper.assertFalse(lifeStoryOpen(menu), "Find opened Dara's life story");
			helper.assertTrue(HallSections.current(menu) == Section.PEOPLE && slotOf(menu, dara) >= 0,
				"Find left the People table: " + HallSections.current(menu));
			helper.succeed();
		});
	}
}
