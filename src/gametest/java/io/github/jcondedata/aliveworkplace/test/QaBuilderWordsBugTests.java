package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-24, lane-b-1024-1837): builder lines that count without a singular, after B377 and B395 gave the
 * Storehouse board's stock line its own. The board's "For the Builder, N blocks away" reads "1 blocks away" to a player
 * standing on the builder's bench; the builder's list in chat (sneak-click with an empty hand) reads "Still to bring
 * for the Test Hut: 1 items of 1 kinds" when only the torch is missing. Expected: "1 block away", "1 item of 1 kind"
 * (as the written list's first page already says). And the owner of a finished build with one block knocked out
 * reads "<builder> is repairing the <build> (1 blocks missing)" (the repair question's own line has its singular).
 */
public class QaBuilderWordsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * The Storehouse two blocks west of the builder's bench: the player who clicks it stands on top of the bench, one
	 * block from it. The cobblestone request's line says "1 block away"; from the usual Storehouse 14 blocks off the
	 * same line says "14 blocks away".
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaBoardWords"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBoardWords")
	public void aWorkerOneBlockAwayReadsOneBlockAway(GameTestHelper helper) {
		QaStageSixGameTests.Waiting w = QaStageSixGameTests.waitingBuilder(helper);
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabw"));
			ServerPlayer p = c.player();
			String far = forLine(helper, p, w.storehouse());
			helper.assertTrue(far.endsWith(", 14 blocks away"), "from the Storehouse 14 blocks off: " + far);

			helper.setBlock(w.storehouse(), Blocks.AIR);
			BlockPos near = new BlockPos(0, 2, 2);
			helper.setBlock(near, ModBlocks.STOREHOUSE);
			String one = forLine(helper, p, near);
			helper.assertTrue(p.blockPosition().equals(helper.absolutePos(new BlockPos(2, 3, 2))), "the player isn't on the bench: " + p.blockPosition());
			helper.assertTrue(one.endsWith(", 1 block away"), "one block from the bench the board reads '" + one + "' (want '..., 1 block away')");
			helper.succeed();
		});
	}

	/**
	 * The test hut's builder, his chest by the bench holding everything but the torch (25 cobblestone, 55 oak planks,
	 * the oak door). A sneak-click with an empty hand on him puts his list in chat: its heading must read "Still to
	 * bring for the Test Hut: 1 item of 1 kind".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaBuilderWordsChat"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaBuilderWordsChat")
	public void theChatListWithOnlyTheTorchMissingSaysOneItemOfOneKind(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabwC"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setShiftKeyDown(true);
		QaStageFourGameTests.pressOn(p, builder, c.seen());
		p.setShiftKeyDown(false);
		List<String> seen = c.seen().stream().map(Component::getString).toList();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		String heading = seen.stream().filter(t -> t.startsWith("Still to bring for the " + hut)).findFirst().orElse(null);
		helper.assertTrue(heading != null, "no list in chat: " + seen);
		helper.assertTrue(heading.contains("☐ 1 × Torch") || heading.contains("Torch"), "the list doesn't name the torch: " + heading);
		helper.assertTrue(heading.startsWith("Still to bring for the " + hut + ": 1 item of 1 kind"),
			"with only the torch to bring the chat reads '" + heading.lines().findFirst().orElse("") + "' (want 'Still to bring for the " + hut + ": 1 item of 1 kind')");
		helper.succeed();
	}

	/**
	 * Upkeep: a finished four-plank wall owned by a player who employs the builder, one plank knocked out, planks in
	 * the chest, repairs without asking. The owner is told the builder is repairing it, "(1 block missing)".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBuilderWordsRepair"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBuilderWordsRepair")
	public void aRepairOfOneBlockSaysOneBlockMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean was = io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED;
		boolean asks = io.github.jcondedata.aliveworkplace.build.Upkeep.ASK;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = was;
			io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = asks;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qabwR"));
		ServerPlayer owner = c.player();
		BlockPos src = new BlockPos(6, 2, 6);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_words_wall_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(src), new net.minecraft.core.Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(src.offset(1, 0, 0), Blocks.AIR);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(builder,
			new io.github.jcondedata.aliveworkplace.build.Employer(owner.getUUID(), owner.getGameProfile().getName()));
		var placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(src), Rotation.NONE, Mirror.NONE);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(wall, placement, owner.getUUID());
		helper.runAfterDelay(2, () -> {
			// (the builder may have looked already; otherwise his look for repairs, as BuilderGameTests' upkeep test does)
			if (!io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.has(builder)) {
				BuildSite site = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.isRepair(), "no repair started");
			}
		});
		helper.succeedWhen(() -> {
			List<String> seen = c.seen().stream().map(Component::getString).toList();
			String line = seen.stream().filter(t -> t.contains(" is repairing the ")).findFirst().orElse(null);
			helper.assertTrue(line != null, "the owner wasn't told of the repair: " + seen);
			helper.assertTrue(line.endsWith("(1 block missing)"), "a repair of one block reads '" + line + "' (want '... (1 block missing)')");
		});
	}

	/**
	 * A porter's status title after his first trip: one iron sword put in a Drop Box by the Storehouse, which he takes to
	 * the store. His title then counts what he has carried: "Porter · 1 item carried".
	 */
	//$ gametest_ticks_batch BIG '1200' '"qaBuilderWordsPorter"'
	@GameTest(template = BIG, timeoutTicks = 1200, batch = "qaBuilderWordsPorter")
	public void aPorterWhoCarriedOneItemSaysOneItemCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos boxPos = new BlockPos(8, 2, 8);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		box.setItem(0, new ItemStack(Items.IRON_SWORD));
		BlockPos storehouse = new BlockPos(19, 2, 19);
		BlockPos storeChest = new BlockPos(19, 2, 17);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		io.github.jcondedata.aliveworkplace.store.Porters.employ(helper.getLevel(), porter, helper.absolutePos(storehouse));
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(storeChest);
			helper.assertTrue(store.countItem(Items.IRON_SWORD) == 1, "the sword isn't in the store yet");
			int carried = io.github.jcondedata.aliveworkplace.registry.ModAttachments.ITEMS_CARRIED.getOrElse(porter, 0);
			helper.assertTrue(carried == 1, "carried " + carried);
			var entry = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(porter, helper.getLevel().getGameTime());
			helper.assertTrue(entry != null, "no status for the porter");
			String title = entry.title().getString();
			helper.assertTrue(title.contains("1 item carried"), "a porter who carried one item reads '" + title + "' (want 'Porter · 1 item carried')");
		});
	}

	/** The "For the ..." line of the board's cobblestone request, the board opened by a click on {@code storehouse}. */
	private static String forLine(GameTestHelper helper, ServerPlayer p, BlockPos storehouse) {
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		ChoiceMenu board = (ChoiceMenu) p.containerMenu;
		int slot = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
		helper.assertTrue(slot >= 0, "the board doesn't ask for cobblestone");
		List<String> lore = QaStageSixGameTests.lore(board.icon(slot));
		p.closeContainer();
		return lore.stream().filter(l -> l.startsWith("For the ")).findFirst().orElse("(no 'For the' line in " + lore + ")");
	}
}
