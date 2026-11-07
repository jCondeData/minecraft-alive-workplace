package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-e-1102-1915, Create and the pack): Create's item-moving and block-breaking machines acting on the mod's own
 * blocks, tested from the guide's promises.
 *
 * <p>Funnels and chutes: shop-counter.md says only the owner, friends and operators change a counter's price list, and
 * mailbox.md that only they open a mailbox, so a brass funnel on the side of one, or a chute under it feeding a hopper,
 * takes nothing; the same machine on a chest does (the control). Items a funnel tries to push in are never lost or
 * doubled. A Drop Box is the village's own box, so a funnel may move its items, but none may vanish or double.
 *
 * <p>Drills: each guide page says breaking the block drops it and what's inside (a counter: its price list). A
 * stranger's Mechanical Drill breaking the same block, filled the same way, must leave exactly what a player breaking it
 * gets (each test breaks a twin by a player as its control): nothing vanishes, nothing doubles.
 */
public class QaCreateMachinesOnOurBlocks1102eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	/** The mod's block under test. */
	private static final BlockPos SUBJECT = new BlockPos(6, 4, 8);
	/** The chest (or the player-broken twin) used as the control, 12 blocks away. */
	private static final BlockPos CONTROL = new BlockPos(18, 4, 8);

	private static BlockState create(String id) {
		return MechanicCompatTests.create(id).defaultBlockState();
	}

	private static BlockState with(BlockState state, String name, String value) {
		return MechanicCompatTests.with(state, name, value);
	}

	/** A funnel of {@code kind} facing {@code facing} (away from the block it's on), pulling or pushing. */
	private static BlockState funnel(String kind, String facing, boolean extracting) {
		return with(with(create(kind), "facing", facing), "extracting", String.valueOf(extracting));
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int s = 0; s < c.getContainerSize(); s++) {
			if (c.getItem(s).is(item)) {
				n += c.getItem(s).getCount();
			}
		}
		return n;
	}

	/** {@code item} on the ground within 4 blocks of {@code pos}. */
	private static int ground(GameTestHelper helper, BlockPos pos, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(4), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** {@code item} in any vanilla container block entity within 2 blocks of {@code pos} (the block, a hopper below). */
	private static int stored(GameTestHelper helper, BlockPos pos, Item item) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				n += count(c, item);
			}
		}
		return n;
	}

	private static float speed(GameTestHelper helper, BlockPos pos) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	// ---------------------------------------------------------------- funnels and chutes pulling out

	private static ShopCounterBlockEntity franksCounter(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(at);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		return counter;
	}

	private static MailboxBlockEntity franksMailbox(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(at);
		box.setOwner(UUID.randomUUID(), "Frank");
		helper.assertTrue(box.receive(new ItemStack(Items.DIAMOND, 3)).isEmpty(), "set-up: the post couldn't deliver 3 diamonds");
		return box;
	}

	private static Container chestWith(GameTestHelper helper, BlockPos at, Item item, int count) {
		helper.setBlock(at, Blocks.CHEST);
		Container chest = helper.getBlockEntity(at);
		chest.setItem(0, new ItemStack(item, count));
		return chest;
	}

	private static void counterUntouched(GameTestHelper helper, ShopCounterBlockEntity counter, String by) {
		helper.assertTrue(counter.goods(0).is(Items.COBBLESTONE) && counter.goods(0).getCount() == 16,
			by + " on Frank's counter took his sample: it is " + counter.goods(0) + ", not 16 cobblestone");
		helper.assertTrue(counter.price(0).is(Items.EMERALD) && counter.price(0).getCount() == 2,
			by + " on Frank's counter took his price: it is " + counter.price(0) + ", not 2 emeralds");
		int out = ground(helper, SUBJECT, Items.COBBLESTONE) + ground(helper, SUBJECT, Items.EMERALD);
		helper.assertTrue(out == 0, out + " of Frank's items lie by his counter (" + by + ")");
	}

	/** A brass funnel on the east side of Frank's counter, pulling; the control: the same funnel on a chest's side. */
	//$ gametest_ticks_batch AREA '300' '"qa_cmob_brass_side_counter"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_brass_side_counter")
	public void aBrassFunnelOnACountersSideTakesNoSampleOrPrice(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ShopCounterBlockEntity counter = franksCounter(helper, SUBJECT);
		helper.setBlock(SUBJECT.east(), funnel("brass_funnel", "east", true));
		Container chest = chestWith(helper, CONTROL, Items.EMERALD, 2);
		helper.setBlock(CONTROL.east(), funnel("brass_funnel", "east", true));
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(count(chest, Items.EMERALD) == 0, "the control: the brass funnel on the chest's side pulled nothing, it holds " + chest.getItem(0));
			counterUntouched(helper, counter, "a brass funnel on the side");
			helper.succeed();
		});
	}

	/** A brass funnel on the side of Frank's mailbox takes none of his mail; the control: a chest's side. */
	//$ gametest_ticks_batch AREA '300' '"qa_cmob_brass_side_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_brass_side_mailbox")
	public void aBrassFunnelOnAMailboxsSideTakesNoMail(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MailboxBlockEntity box = franksMailbox(helper, SUBJECT);
		helper.setBlock(SUBJECT.east(), funnel("brass_funnel", "east", true));
		Container chest = chestWith(helper, CONTROL, Items.DIAMOND, 3);
		helper.setBlock(CONTROL.east(), funnel("brass_funnel", "east", true));
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(count(chest, Items.DIAMOND) == 0, "the control: the brass funnel on the chest's side pulled nothing, it holds " + chest.getItem(0));
			int out = ground(helper, SUBJECT, Items.DIAMOND);
			helper.assertTrue(count(box, Items.DIAMOND) == 3, "a brass funnel on the side of Frank's mailbox took his mail: "
				+ count(box, Items.DIAMOND) + " of 3 diamonds left, " + out + " on the ground");
			helper.succeed();
		});
	}

	/** A chute under the block feeding a hopper; the control chest above the same chute and hopper empties into it. */
	private static void chuteAndHopperUnder(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at.below(), with(with(create("chute"), "facing", "down"), "shape", "normal"));
		helper.setBlock(at.below(2), Blocks.HOPPER);
	}

	//$ gametest_ticks_batch AREA '300' '"qa_cmob_chute_counter"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_chute_counter")
	public void aChuteUnderACounterPullsNothingOut(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ShopCounterBlockEntity counter = franksCounter(helper, SUBJECT);
		chuteAndHopperUnder(helper, SUBJECT);
		Container chest = chestWith(helper, CONTROL, Items.EMERALD, 2);
		chuteAndHopperUnder(helper, CONTROL);
		helper.runAfterDelay(200, () -> {
			Container controlHopper = helper.getBlockEntity(CONTROL.below(2));
			helper.assertTrue(count(chest, Items.EMERALD) == 0 && count(controlHopper, Items.EMERALD) == 2,
				"the control: the chute under the chest didn't move its 2 emeralds to the hopper: chest " + chest.getItem(0) + ", hopper " + controlHopper.getItem(0));
			Container hopper = helper.getBlockEntity(SUBJECT.below(2));
			helper.assertTrue(hopper.isEmpty(), "a chute under Frank's counter fed the hopper " + hopper.getItem(0));
			counterUntouched(helper, counter, "a chute underneath");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '300' '"qa_cmob_chute_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_chute_mailbox")
	public void aChuteUnderAMailboxPullsNoMailOut(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MailboxBlockEntity box = franksMailbox(helper, SUBJECT);
		chuteAndHopperUnder(helper, SUBJECT);
		Container chest = chestWith(helper, CONTROL, Items.DIAMOND, 3);
		chuteAndHopperUnder(helper, CONTROL);
		helper.runAfterDelay(200, () -> {
			Container controlHopper = helper.getBlockEntity(CONTROL.below(2));
			helper.assertTrue(count(controlHopper, Items.DIAMOND) == 3,
				"the control: the chute under the chest didn't move its 3 diamonds to the hopper: chest " + chest.getItem(0) + ", hopper " + controlHopper.getItem(0));
			Container hopper = helper.getBlockEntity(SUBJECT.below(2));
			helper.assertTrue(hopper.isEmpty() && count(box, Items.DIAMOND) == 3,
				"a chute under Frank's mailbox took his mail: the hopper holds " + hopper.getItem(0) + ", the mailbox " + count(box, Items.DIAMOND) + " of 3 diamonds");
			helper.succeed();
		});
	}

	/** A Drop Box is the village's: a funnel may pull from it, but the 9 emeralds in it stay 9 in the world. */
	//$ gametest_ticks_batch AREA '300' '"qa_cmob_funnel_dropbox_out"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_funnel_dropbox_out")
	public void aFunnelPullingFromADropBoxNeitherLosesNorDoublesItems(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(SUBJECT, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(SUBJECT);
		box.setItem(0, new ItemStack(Items.EMERALD, 9));
		helper.setBlock(SUBJECT.above(), funnel("andesite_funnel", "up", true));
		helper.runAfterDelay(200, () -> {
			int in = count(box, Items.EMERALD);
			int out = ground(helper, SUBJECT, Items.EMERALD);
			helper.assertTrue(in + out == 9, "a funnel on a Drop Box holding 9 emeralds: " + in + " left in it and " + out + " on the ground");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- funnels pushing in

	/** 4 diamonds dropped onto an inserting funnel on top of {@code at}. */
	private static void dropOntoFunnel(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at.above(), funnel("andesite_funnel", "up", false));
		Vec3 v = Vec3.atCenterOf(helper.absolutePos(at.above()));
		ItemEntity e = new ItemEntity(helper.getLevel(), v.x, v.y, v.z, new ItemStack(Items.DIAMOND, 4));
		e.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(e);
	}

	/** Sets the control: a chest under the same funnel takes the 4 diamonds. */
	private static Container controlChestUnderFunnel(GameTestHelper helper) {
		helper.setBlock(CONTROL, Blocks.CHEST);
		dropOntoFunnel(helper, CONTROL);
		return helper.getBlockEntity(CONTROL);
	}

	//$ gametest_ticks_batch AREA '300' '"qa_cmob_funnel_into_counter"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_funnel_into_counter")
	public void aFunnelPushingIntoACounterChangesNoPriceAndLosesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ShopCounterBlockEntity counter = franksCounter(helper, SUBJECT);
		dropOntoFunnel(helper, SUBJECT);
		Container chest = controlChestUnderFunnel(helper);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(count(chest, Items.DIAMOND) == 4, "the control: the funnel didn't put the 4 diamonds in the chest, it holds " + count(chest, Items.DIAMOND));
			int in = count(counter, Items.DIAMOND);
			int out = ground(helper, SUBJECT, Items.DIAMOND);
			helper.assertTrue(in == 0, "a funnel pushed " + in + " diamonds into Frank's price list");
			helper.assertTrue(out == 4, "4 diamonds dropped on a funnel on Frank's counter: " + in + " in it, " + out + " on the ground (lost or doubled)");
			helper.assertTrue(counter.goods(0).getCount() == 16 && counter.price(0).getCount() == 2 && counter.price(0).is(Items.EMERALD),
				"Frank's price list changed: " + counter.goods(0) + " for " + counter.price(0));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '300' '"qa_cmob_funnel_into_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_funnel_into_mailbox")
	public void aFunnelPushingIntoAMailboxLosesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(SUBJECT, ModBlocks.MAILBOX);
		MailboxBlockEntity box = helper.getBlockEntity(SUBJECT);
		box.setOwner(UUID.randomUUID(), "Frank");
		dropOntoFunnel(helper, SUBJECT);
		Container chest = controlChestUnderFunnel(helper);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(count(chest, Items.DIAMOND) == 4, "the control: the funnel didn't put the 4 diamonds in the chest, it holds " + count(chest, Items.DIAMOND));
			int in = count(box, Items.DIAMOND);
			int out = ground(helper, SUBJECT, Items.DIAMOND);
			helper.assertTrue(in + out == 4, "4 diamonds dropped on a funnel on Frank's mailbox: " + in + " in it, " + out + " on the ground (lost or doubled)");
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '300' '"qa_cmob_funnel_into_dropbox"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_cmob_funnel_into_dropbox")
	public void aFunnelPushingIntoADropBoxLosesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(SUBJECT, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(SUBJECT);
		dropOntoFunnel(helper, SUBJECT);
		Container chest = controlChestUnderFunnel(helper);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(count(chest, Items.DIAMOND) == 4, "the control: the funnel didn't put the 4 diamonds in the chest, it holds " + count(chest, Items.DIAMOND));
			int in = count(box, Items.DIAMOND);
			int out = ground(helper, SUBJECT, Items.DIAMOND);
			helper.assertTrue(in + out == 4, "4 diamonds dropped on a funnel on a Drop Box: " + in + " in it, " + out + " on the ground (lost or doubled)");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- a drill breaking our blocks

	/**
	 * Puts {@code block} at {@link #SUBJECT} and its twin at {@link #CONTROL}, each filled by {@code fill}. A player in
	 * survival breaks the twin with {@code tool} at once (the control: what the guide promises); a stranger's
	 * Mechanical Drill turned by a Creative Motor breaks the subject. Each of {@code items} must come out of the drill's
	 * break exactly as many times as out of the player's.
	 */
	private static void drillBreaksLikeAPlayer(GameTestHelper helper, Block block, Item tool, BiConsumer<BlockEntity, Boolean> fill, Map<Item, Integer> expected) {
		Item[] items = expected.keySet().toArray(new Item[0]);
		PartnerShowsCompatTests.clearLeftovers(helper);
		// both on the floor (y = 2), so the drill sits level with its target
		BlockPos subject = SUBJECT.below(2);
		BlockPos control = CONTROL.below(2);
		helper.setBlock(subject, block);
		helper.setBlock(control, block);
		fill.accept(helper.getLevel().getBlockEntity(helper.absolutePos(subject)), false);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		fill.accept(helper.getLevel().getBlockEntity(helper.absolutePos(control)), true);
		Vec3 stand = Vec3.atCenterOf(helper.absolutePos(control.south(2)));
		player.teleportTo(stand.x, stand.y, stand.z);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
		boolean playerBroke = player.gameMode.destroyBlock(helper.absolutePos(control));
		Map<Item, Integer> byPlayer = new LinkedHashMap<>();
		for (Item item : items) {
			byPlayer.put(item, ground(helper, control, item) + stored(helper, control, item));
		}
		player.discard();
		helper.assertTrue(playerBroke && helper.getBlockState(control).isAir(), "the control: the player couldn't break the " + block.getDescriptionId());
		helper.assertTrue(byPlayer.equals(expected), "the control: a player breaking it should get " + expected + " (the guide), got " + byPlayer);

		BlockPos drill = subject.east();
		helper.setBlock(drill.east(), with(create("creative_motor"), "facing", "west"));
		helper.setBlock(drill, with(create("mechanical_drill"), "facing", "west"));
		helper.succeedWhen(() -> {
			float turning = speed(helper, drill);
			helper.assertTrue(turning != 0, "the drill never turned, so the test proves nothing");
			helper.assertTrue(helper.getBlockState(subject).isAir(), "the drill hasn't broken the " + block.getDescriptionId() + " yet (speed " + turning + ")");
			Map<Item, Integer> byDrill = new LinkedHashMap<>();
			for (Item item : items) {
				byDrill.put(item, ground(helper, subject, item) + stored(helper, subject, item));
			}
			helper.assertTrue(byDrill.equals(byPlayer), "a Mechanical Drill breaking a " + block.getDescriptionId() + " left " + byDrill
				+ " in the world; a player breaking it gets " + byPlayer);
		});
	}

	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_mailbox"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_mailbox")
	public void aDrillBreakingAMailboxDropsItAndItsMailLikeAPlayer(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.MAILBOX, Items.IRON_PICKAXE, (be, byPlayer) -> {
			MailboxBlockEntity box = (MailboxBlockEntity) be;
			box.setOwner(UUID.randomUUID(), "Frank");
			box.receive(new ItemStack(Items.PAPER, 5));
			box.receive(new ItemStack(Items.DIAMOND, 3));
		}, Map.of(ModBlocks.MAILBOX.asItem(), 1, Items.PAPER, 5, Items.DIAMOND, 3));
	}

	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_counter"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_counter")
	public void aDrillBreakingAShopCounterDropsItAndItsPriceListLikeAPlayer(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.SHOP_COUNTER, Items.IRON_AXE, (be, byPlayer) -> {
			ShopCounterBlockEntity counter = (ShopCounterBlockEntity) be;
			counter.setOwner(UUID.randomUUID(), "Frank");
			counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
			counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		}, Map.of(ModBlocks.SHOP_COUNTER.asItem(), 1, Items.COBBLESTONE, 16, Items.EMERALD, 2));
	}

	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_dropbox"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_dropbox")
	public void aDrillBreakingADropBoxDropsItAndEverythingInsideLikeAPlayer(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.DROP_BOX, Items.IRON_AXE, (be, byPlayer) -> {
			Container box = (Container) be;
			box.setItem(0, new ItemStack(Items.EMERALD, 9));
			box.setItem(5, new ItemStack(Items.OAK_LOG, 32));
		}, Map.of(ModBlocks.DROP_BOX.asItem(), 1, Items.EMERALD, 9, Items.OAK_LOG, 32));
	}

	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_bench"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_bench")
	public void aDrillBreakingABuildersBenchDropsItLikeAPlayer(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.BUILDERS_BENCH, Items.IRON_AXE, (be, byPlayer) -> { },
			Map.of(ModBlocks.BUILDERS_BENCH.asItem(), 1));
	}

	/** village-hall.md: "Mine the hall with an axe and it drops itself, keeping a name you gave it." A drill too. */
	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_hall"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_hall")
	public void aDrillBreakingAVillageHallDropsItWithItsName(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.VILLAGE_HALL, Items.IRON_AXE, (be, byPlayer) ->
			((VillageHallBlockEntity) be).setCustomName(Component.literal("Oakvale")), Map.of(ModBlocks.VILLAGE_HALL.asItem(), 1));
	}

	/** blueprint-table.md: "it drops itself, and the blueprints stored in it drop beside it." A drill too. */
	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_table"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_table")
	public void aDrillBreakingABlueprintTableDropsItAndItsBlueprintsLikeAPlayer(GameTestHelper helper) {
		drillBreaksLikeAPlayer(helper, ModBlocks.BLUEPRINT_TABLE, Items.IRON_AXE, (be, byPlayer) -> {
			for (int i = 0; i < 3; i++) {
				((BlueprintTableBlockEntity) be).put(new ItemStack(ModItems.BLANK_BLUEPRINT));
			}
		}, Map.of(ModBlocks.BLUEPRINT_TABLE.asItem(), 1, ModItems.BLANK_BLUEPRINT, 3));
	}

	/** The hall item a drill drops keeps the name the hall was given. */
	//$ gametest_ticks_batch AREA '600' '"qa_cmob_drill_hall_name"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_cmob_drill_hall_name")
	public void aDrillBreakingANamedVillageHallKeepsTheName(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos hallAt = SUBJECT.below(2);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(hallAt)).setCustomName(Component.literal("Oakvale"));
		BlockPos drill = hallAt.east();
		helper.setBlock(drill.east(), with(create("creative_motor"), "facing", "west"));
		helper.setBlock(drill, with(create("mechanical_drill"), "facing", "west"));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(hallAt).isAir(), "the drill hasn't broken the hall yet (speed " + speed(helper, drill) + ")");
			var halls = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(hallAt)).inflate(4),
				e -> e.getItem().is(ModBlocks.VILLAGE_HALL.asItem()));
			helper.assertTrue(halls.size() == 1, halls.size() + " hall items lie by the drilled hall");
			Component name = halls.get(0).getItem().get(DataComponents.CUSTOM_NAME);
			helper.assertTrue(name != null && name.getString().equals("Oakvale"), "the drilled hall came back named " + name + ", not Oakvale");
		});
	}
}
