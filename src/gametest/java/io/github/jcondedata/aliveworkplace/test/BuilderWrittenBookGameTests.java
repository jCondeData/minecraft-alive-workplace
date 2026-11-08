package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * B1042: only a blank Book and Quill (no pages, or only empty ones) becomes the Builder's material list. One with the
 * player's writing in the main hand is left as it was and the player is told a blank one is needed; one opened and
 * closed unwritten (a single empty page) still becomes the list.
 */
public class BuilderWrittenBookGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	//$ gametest_ticks_batch AREA '60' '"writtenBookMainHand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "writtenBookMainHand")
	public void aWrittenBookInTheMainHandIsKeptAndThePlayerIsTold(GameTestHelper helper) {
		Villager builder = setup(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("wbM"));
		ServerPlayer p = c.player();
		ItemStack notes = new ItemStack(Items.WRITABLE_BOOK);
		notes.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(""), Filterable.passThrough("Iron farm at 40 70 12"))));
		p.setItemInHand(InteractionHand.MAIN_HAND, notes);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		ItemStack after = p.getMainHandItem();
		WritableBookContent pages = after.get(DataComponents.WRITABLE_BOOK_CONTENT);
		helper.assertTrue(after.is(Items.WRITABLE_BOOK) && pages != null && pages.pages().size() == 2
			&& pages.pages().get(1).raw().equals("Iron farm at 40 70 12"), "the player's notes were written over: " + after);
		Component told = QaStageFourGameTests.find(c.seen(), "message.aliveworkplace.site_list.blank_book_needed");
		helper.assertTrue(told != null, "the player wasn't told a blank Book and Quill is needed: " + QaStageFourGameTests.keys(c.seen()));
		helper.assertTrue(told.getString().equals("This Book and Quill has your writing in it. Use a blank one for the material list."),
			"the line reads: " + told.getString());
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '60' '"writtenBookEmptyPage"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "writtenBookEmptyPage")
	public void aBookWithOnlyAnEmptyPageStillBecomesTheList(GameTestHelper helper) {
		Villager builder = setup(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("wbE"));
		ServerPlayer p = c.player();
		ItemStack blank = new ItemStack(Items.WRITABLE_BOOK);
		blank.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(""))));
		p.setItemInHand(InteractionHand.MAIN_HAND, blank);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(p.getMainHandItem().is(Items.WRITTEN_BOOK), "a Book and Quill with only an empty page wasn't written: "
			+ p.getMainHandItem() + ", saw " + QaStageFourGameTests.keys(c.seen()));
		helper.succeed();
	}

	private static Villager setup(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		return builder;
	}
}
