package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-04, lane c, round f) for ROADMAP 27.19 (safe by design) and 31.22 (the eight reforms as stories), at
 * what the earlier tests of both leave untested, written from the spec.
 * <ul>
 * <li>27.19's ledger marks "a block a player places or breaks": a break or a placement that never happened (a
 *     stranger refused in a protected village, a block that can't stand where it was put) marks nothing, a block put
 *     by no player (a sapling an orchard plants, a dispenser) marks nothing, and with {@code villageProtection} off the
 *     stranger's break goes through and marks its section. Each with the other side: the owner's own break or
 *     placement at the same spot marks it.</li>
 * <li>31.22's Fair Ledger is "for the hall's owner": the book goes to the owner, not to whoever finished the story.</li>
 * </ul>
 */
public class QaLaneC1104fGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** Where the ledger tests put and break blocks: 6 blocks from ProtectionSpecGameTests' hall, inside its area of 16. */
	private static final BlockPos SPOT = new BlockPos(5, 2, 5);
	/** QaReformStoryGameTests' hall and asker, for its village helpers. */
	private static final BlockPos HALL = new BlockPos(6, 2, 6);
	private static final BlockPos ASKER = new BlockPos(8, 2, 6);

	// ---- 27.19: the ledger marks only what a player really placed or broke ---------------------------------------------

	/**
	 * A stranger's break refused by the village's protection is no break: the block stays and its section stays unmarked
	 * (else a stranger could shut the Steward out of a village by swinging at it). The owner's own break of the same
	 * block marks it.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1104fRefusedBreak"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1104fRefusedBreak")
	public void qaAStrangersRefusedBreakMarksNoSection(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		VillageHalls.forgetNearest();
		Leftovers.after(helper, VillageHalls::forgetNearest);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(SPOT);
		helper.setBlock(SPOT, Blocks.OAK_PLANKS);
		helper.assertTrue(VillageHalls.nearest(level, at).isPresent(), "setup: the spot isn't in the hall's area");
		helper.assertTrue(!PlayerBuilt.get(level).marked(at), "setup: the spot's section is already marked");

		helper.assertTrue(!stranger.gameMode.destroyBlock(at), "setup: the stranger broke a block in a protected village");
		helper.assertBlockPresent(Blocks.OAK_PLANKS, SPOT);
		helper.assertTrue(!PlayerBuilt.get(level).marked(at), "a stranger's refused break marked the section in the ledger of what players built");

		helper.assertTrue(owner.gameMode.destroyBlock(at), "setup: the owner couldn't break his own village's block");
		helper.assertTrue(PlayerBuilt.get(level).marked(at), "the owner's break left the section unmarked");
		helper.succeed();
	}

	/**
	 * A stranger's placement refused by the protection (a real click with planks on a block) puts nothing and marks
	 * nothing; the owner's same click places the planks and marks the section.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1104fRefusedPlace"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1104fRefusedPlace")
	public void qaAStrangersRefusedPlacementMarksNoSection(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		VillageHalls.forgetNearest();
		Leftovers.after(helper, VillageHalls::forgetNearest);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SPOT, Blocks.STONE);
		helper.setBlock(SPOT.above(), Blocks.AIR);
		BlockPos above = helper.absolutePos(SPOT.above());
		for (ServerPlayer p : List.of(owner, stranger)) {
			p.teleportTo(above.getX() + 3.5, above.getY(), above.getZ() + 0.5);
		}
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "setup: the spot's section is already marked");

		click(helper, stranger, SPOT, new ItemStack(Items.OAK_PLANKS, 4));
		helper.assertBlockPresent(Blocks.AIR, SPOT.above());
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "a stranger's refused placement marked the section in the ledger");

		click(helper, owner, SPOT, new ItemStack(Items.OAK_PLANKS, 4));
		helper.assertBlockPresent(Blocks.OAK_PLANKS, SPOT.above());
		helper.assertTrue(PlayerBuilt.get(level).marked(above), "the owner's placement left the section unmarked");
		helper.succeed();
	}

	/**
	 * With {@code villageProtection: false} in the config, the stranger's break goes through, so it is a player's break
	 * like any other and marks its section.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1104fProtectionOff"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1104fProtectionOff")
	public void qaWithProtectionOffAStrangersBreakMarksItsSection(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // (puts the switch back after)
		VillageHalls.forgetNearest();
		Leftovers.after(helper, VillageHalls::forgetNearest);
		VillageProtection.ENABLED = false;
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(SPOT);
		helper.setBlock(SPOT, Blocks.OAK_PLANKS);
		helper.assertTrue(!PlayerBuilt.get(level).marked(at), "setup: the spot's section is already marked");
		helper.assertTrue(stranger.gameMode.destroyBlock(at), "with protection off the stranger still couldn't break the block");
		helper.assertBlockPresent(Blocks.AIR, SPOT);
		helper.assertTrue(PlayerBuilt.get(level).marked(at), "the stranger's real break left the section unmarked");
		helper.succeed();
	}

	/**
	 * A block placed with no player behind it (an orchard planting a sapling, a dispenser placing a shulker box: the
	 * block item's place with no player) marks nothing; the same placement by a player marks the section.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1104fNoPlayer"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1104fNoPlayer")
	public void qaABlockPlacedByNoPlayerMarksNothing(GameTestHelper helper) {
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		ledgerHall(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SPOT, Blocks.GRASS_BLOCK);
		helper.setBlock(SPOT.above(), Blocks.AIR);
		BlockPos above = helper.absolutePos(SPOT.above());
		helper.assertTrue(VillageHalls.nearest(level, above).isPresent(), "setup: the spot isn't in the hall's area");
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "setup: the spot's section is already marked");

		InteractionResult planted = place(helper, null, SPOT, new ItemStack(Items.OAK_SAPLING));
		helper.assertTrue(planted.consumesAction() && helper.getBlockState(SPOT.above()).is(Blocks.OAK_SAPLING), "setup: the sapling wasn't planted: " + planted);
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "a sapling planted by no player marked the section in the ledger of what players built");

		helper.setBlock(SPOT.above(), Blocks.AIR);
		player.teleportTo(above.getX() + 3.5, above.getY(), above.getZ() + 0.5);
		InteractionResult byHand = place(helper, player, SPOT, new ItemStack(Items.OAK_SAPLING));
		helper.assertTrue(byHand.consumesAction() && helper.getBlockState(SPOT.above()).is(Blocks.OAK_SAPLING), "setup: the player's sapling wasn't planted: " + byHand);
		helper.assertTrue(PlayerBuilt.get(level).marked(above), "a sapling a player planted left the section unmarked");
		helper.succeed();
	}

	/**
	 * A placement that fails (a sapling on stone: it can't stand there) puts nothing, so it marks nothing; the same
	 * player's sapling on grass at the same spot marks the section.
	 */
	//$ gametest_ticks_batch BIG '60' '"qa1104fFailedPlace"'
	@GameTest(template = BIG, timeoutTicks = 60, batch = "qa1104fFailedPlace")
	public void qaAPlacementThatFailsMarksNothing(GameTestHelper helper) {
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		ledgerHall(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SPOT, Blocks.STONE);
		helper.setBlock(SPOT.above(), Blocks.AIR);
		BlockPos above = helper.absolutePos(SPOT.above());
		player.teleportTo(above.getX() + 3.5, above.getY(), above.getZ() + 0.5);
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "setup: the spot's section is already marked");

		InteractionResult failed = place(helper, player, SPOT, new ItemStack(Items.OAK_SAPLING));
		helper.assertTrue(!failed.consumesAction() && helper.getBlockState(SPOT.above()).isAir(), "setup: a sapling stood on stone: " + failed);
		helper.assertTrue(!PlayerBuilt.get(level).marked(above), "a placement that put nothing marked the section in the ledger");

		helper.setBlock(SPOT, Blocks.GRASS_BLOCK);
		InteractionResult planted = place(helper, player, SPOT, new ItemStack(Items.OAK_SAPLING));
		helper.assertTrue(planted.consumesAction() && helper.getBlockState(SPOT.above()).is(Blocks.OAK_SAPLING), "setup: the sapling wasn't planted on grass: " + planted);
		helper.assertTrue(PlayerBuilt.get(level).marked(above), "the player's sapling left the section unmarked");
		helper.succeed();
	}

	// ---- 31.22: the Fair Ledger is for the hall's owner ----------------------------------------------------------------

	/**
	 * The Fair Ledger, finished by a player who isn't the hall's owner while the owner is online elsewhere in the village:
	 * the written book goes to the owner ("for the hall's owner"), not to the finisher, and the edict ends reformed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1104fLedgerOwner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1104fLedgerOwner")
	public void qaTheFairLedgerGoesToTheHallsOwnerNotTheFinisher(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaReformStoryGameTests.village(helper);
		Villager librarian = QaReformStoryGameTests.villager(helper, ASKER, VillagerProfession.LIBRARIAN);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = QaReformStoryGameTests.ready(helper);
			ServerPlayer finisher = ProtectionSpecGameTests.player(helper);
			finisher.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			ServerPlayer owner = ProtectionSpecGameTests.player(helper);
			owner.teleportTo(hall.getX() + 12.5, hall.getY(), hall.getZ() + 12.5);
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			long day = Chronicle.day(level) + 1;
			level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
			String edict = AliveWorkplace.id("tithe").toString();
			entity.setEdicts(List.of(new Edicts.InForce(edict, day)));
			QaReformStoryGameTests.autoRound(level, hall, entity);
			Arcs.round(level, hall, entity);
			helper.assertTrue(QaReformStoryGameTests.asks(librarian), "the Librarian didn't ask");
			helper.assertTrue(ArcEffects.onTalk(finisher, level, InteractionHand.MAIN_HAND, librarian) == InteractionResult.SUCCESS, "talking didn't count");
			runSteps(helper, level, hall, entity, edict, day, finisher);
			helper.assertTrue(Arcs.side(level, hall).isEmpty() && Reforms.reformed(entity, edict), "the story didn't end reformed");
			helper.assertTrue(books(finisher) == 0, "the finisher, not the hall's owner, got the Fair Ledger");
			helper.assertTrue(books(owner) == 1, "the hall's owner didn't get the Fair Ledger: " + books(owner) + " books");
			ItemStack book = ItemStack.EMPTY;
			for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
				if (owner.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
					book = owner.getInventory().getItem(i);
				}
			}
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			String title = "The Fair Ledger of " + VillageHalls.name(level, hall).getString();
			helper.assertTrue(content != null && book.getHoverName().getString().equals(title), "the owner's book: " + book.getHoverName().getString());
			helper.succeed();
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/** An unprotected hall at ProtectionSpecGameTests' spot, its area 16 (put back after). */
	private static void ledgerHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(ProtectionSpecGameTests.HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
	}

	/** A player's real right click with {@code held} on the top of the block at {@code pos}. */
	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
	}

	/** The block item's own place on the top of {@code pos}, by {@code player} or by no one. */
	private static InteractionResult place(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack stack) {
		BlockPos at = helper.absolutePos(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false);
		return ((BlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
	}

	private static int books(ServerPlayer player) {
		return player.getInventory().countItem(Items.WRITTEN_BOOK);
	}

	/** Hands in the reform's three steps (M30's kinds and numbers), one a day, as the finisher. */
	private static void runSteps(GameTestHelper helper, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, String edict, long day,
								 ServerPlayer player) {
		Reforms.Reform reform = Reforms.of(edict).orElseThrow();
		for (int n = 0; n < reform.steps().size(); n++) {
			if (n > 0) {
				level.setDayTime((day + n - 1) * VillageNeeds.DAY + 1000);
				Arcs.round(level, hall, entity);
			}
			Reforms.Step step = reform.steps().get(n).resolve(false);
			List<Quest> quests = Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
			helper.assertTrue(quests.size() == 1, "step " + (n + 1) + ": " + quests.size() + " arc quests");
			Quest q = quests.get(0);
			if (step.kind() == VillageQuests.Kind.BRING) {
				Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(step.item()));
				int max = new ItemStack(item).getMaxStackSize();
				for (int left = step.count(); left > 0; left -= max) {
					player.getInventory().add(new ItemStack(item, Math.min(max, left)));
				}
				helper.assertTrue(Stories.handIn(player, hall, q.id) == step.count(), "not handed in: step " + (n + 1));
			} else {
				helper.assertTrue(q.objectives.get(0) instanceof Objectives.Kill k && k.count() == step.count(), "step " + (n + 1) + " isn't M30's");
				Zombie zombie = helper.spawn(EntityType.ZOMBIE, ASKER.west(4));
				for (int i = 0; i < step.count(); i++) {
					Stories.onKill(level, zombie, player);
				}
				zombie.discard();
			}
		}
	}
}
