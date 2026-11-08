package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * QA (lane-b-1108-0638) for the Builder's material list (B279, B394; docs/wiki/builder.md: "a Book and Quill used on its
 * Builder ... becomes the material list of the rest of it", "held in either hand"). Players keep notes in a Book and
 * Quill, often in the off hand. With the main hand empty, a plain right-click on a Builder (to trade with him) uses the
 * off hand's book, so a Book and Quill with the player's own pages in it is turned into the material list and their
 * pages are gone for good. Expected: only a blank Book and Quill becomes the list; one the player has written in is kept
 * as it was. Control (QaBuilderClicksGameTests.aBookAndQuillFromTheOffHandOnTheBuilder): a blank one is written.
 */
public class QaJournalBook1108bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final String NOTE = "Base: 120 64 -340. Nether portal behind the waterfall.";

	//$ gametest_ticks_batch AREA '60' '"qa1108bJournal"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1108bJournal")
	public void aBookAndQuillWithThePlayersOwnPagesIsNotTurnedIntoTheList(GameTestHelper helper) {
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

		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qajB"));
		ServerPlayer p = c.player();
		ItemStack journal = new ItemStack(Items.WRITABLE_BOOK);
		journal.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(List.of(Filterable.passThrough(NOTE))));
		p.setItemInHand(InteractionHand.OFF_HAND, journal);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		ItemStack after = p.getOffhandItem();
		WritableBookContent pages = after.get(DataComponents.WRITABLE_BOOK_CONTENT);
		helper.assertTrue(after.is(Items.WRITABLE_BOOK) && pages != null && !pages.pages().isEmpty() && pages.pages().get(0).raw().equals(NOTE),
			"a right-click on the builder (main hand empty) turned the player's own Book and Quill into " + after
				+ "; their page \"" + NOTE + "\" is gone (saw " + c.seen().stream().map(x -> x.getString()).toList() + ")");
		helper.succeed();
	}
}
