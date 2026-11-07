package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.WondersPage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA for the Wonders page and the Wonder Plan (ROADMAP 35.3), from its spec, the corners WonderPlanGameTests leaves:
 * the owner's friends may draw up the plans; a Village (no room for a Wonder) is told so on the card and a click takes
 * nothing; a Wonder in {@code disabledWonders} is off the page; a plan is refused over a finished Wonder, with a corner past 96 blocks, and once
 * its hall is gone. (Over a Wonder waiting for its goods: QaWonderPlanBug1031Tests, B713.)
 */
public class QaWonderPlan1031GameTests {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> out = new ArrayList<>();
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static void villager(GameTestHelper helper, BlockPos at, VillagerProfession job, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
	}

	/** A Town round {@code HALL} owned by the returned player, every requirement of the folly met (Legends stand in). */
	private static ServerPlayer town(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean legends = Wonders.LEGENDS;
		Wonders.LEGENDS = false;
		Leftovers.after(helper, () -> Wonders.LEGENDS = legends);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setTreasury(0);
		villager(helper, new BlockPos(3, 2, 3), ModVillagers.BUILDER, 5);
		villager(helper, new BlockPos(13, 2, 3), VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(8, 2, 13), VillagerProfession.FARMER, 1);
		return player;
	}

	private static Wonder folly(GameTestHelper helper) {
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		return folly;
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item);
	}

	/** placeRefusal's translation key for {@code plan} anchored at {@code at}, unturned. */
	private static String refusal(ServerLevel level, ItemStack plan, BlockPos at) {
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), size, at, Rotation.NONE);
		return WonderPlans.placeRefusal(level, WonderPlanItem.plan(plan).get(), placement, size).map(c -> key(c) + " (" + c.getString() + ")").orElse("none");
	}

	private static void use(ServerPlayer player, BlockPos ground) {
		player.setShiftKeyDown(false);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false)));
	}

	private static Optional<BlueprintData.Placement> placed(ServerPlayer player) {
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	// ---------------------------------------------------------------- who may draw them up

	/** "owner and friends only": a friend of the owner draws up the plans with their own emeralds; a stranger still may not. */
	//$ gametest_batch SMALL '"qaWonderFriend1031"'
	@GameTest(template = SMALL, batch = "qaWonderFriend1031")
	public void qaTheOwnersFriendMayDrawUpThePlans(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Wonder folly = folly(helper);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(level.getServer());
		friends.add(owner.getUUID(), friend.getUUID(), "Pal");
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), friend.getUUID()));
		friend.getInventory().add(new ItemStack(Items.EMERALD, 40));

		// through the hall's own screen, as the friend
		ChoiceMenu menu = VillageHallScreen.forTest(friend, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), friend);
		int index = new ArrayList<>(Wonders.all()).indexOf(folly);
		menu.press(WondersPage.slot(index), friend);
		helper.assertTrue(count(friend, Items.EMERALD) == 8 && count(friend, ModItems.WONDER_PLAN) == 1,
			"the friend's 32 emeralds for the plan: " + count(friend, Items.EMERALD) + " emeralds, " + count(friend, ModItems.WONDER_PLAN) + " plans");

		// the friend struck off the list: refused, nothing taken
		friends.remove(owner.getUUID(), friend.getUUID());
		friend.getInventory().add(new ItemStack(Items.EMERALD, 32));
		WonderPlans.Drawn again = WonderPlans.draw(level, hall, friend, FOLLY);
		helper.assertTrue(!again.drawn() && key(again.refusal()).equals("message.aliveworkplace.wonder_plan.not_yours")
			&& count(friend, Items.EMERALD) == 40 && count(friend, ModItems.WONDER_PLAN) == 1, "no longer a friend: " + key(again.refusal()));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the cap on the card

	/** A Village has room for no Wonder (default caps Hamlet 0, Village 0): the card says so, offers no plans, a click takes nothing. */
	//$ gametest_batch SMALL '"qaWonderVillage1031"'
	@GameTest(template = SMALL, batch = "qaWonderVillage1031")
	public void qaAVillageIsToldItHasNoRoomAndPaysNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Wonder folly = folly(helper);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setRank(VillageRanks.Rank.VILLAGE);
		player.getInventory().add(new ItemStack(Items.EMERALD, 40));

		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		int tab = HallPages.slot(WondersPage.PAGE);
		helper.assertTrue(lore(menu.icon(tab)).contains("Wonders: 0 of 0 for a Village"), "the tab's cap line: " + lore(menu.icon(tab)));
		menu.press(tab, player);
		int index = new ArrayList<>(Wonders.all()).indexOf(folly);
		List<String> lines = lore(menu.icon(WondersPage.slot(index)));
		helper.assertTrue(lines.contains("Wonders: 0 of 0 for a Village") && lines.stream().noneMatch(l -> l.startsWith("Click: Draw up")),
			"the card in a Village: " + lines);
		menu.press(WondersPage.slot(index), player);
		helper.assertTrue(count(player, Items.EMERALD) == 40 && count(player, ModItems.WONDER_PLAN) == 0,
			"a Village drew up plans: " + count(player, Items.EMERALD) + " emeralds left");
		WonderPlans.Drawn drawn = WonderPlans.draw(level, hall, player, FOLLY);
		helper.assertTrue(!drawn.drawn() && key(drawn.refusal()).equals("wonder.aliveworkplace.refused.cap")
			&& drawn.refusal().getString().equals("Wonders: 0 of 0 for a Village."), "the refusal: " + (drawn.refusal() == null ? "none" : drawn.refusal().getString()));
		helper.succeed();
	}

	/** {@code disabledWonders}: the folly is off the page and its plans can't be drawn up; back on, it returns. */
	//$ gametest_batch SMALL '"qaWonderDisabled1031"'
	@GameTest(template = SMALL, batch = "qaWonderDisabled1031")
	public void qaADisabledWonderIsOffThePage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		folly(helper);
		Set<ResourceLocation> before = Wonders.DISABLED;
		Set<ResourceLocation> off = new HashSet<>(before);
		off.add(FOLLY);
		Wonders.DISABLED = Set.copyOf(off);
		Leftovers.after(helper, () -> Wonders.DISABLED = before);
		player.getInventory().add(new ItemStack(Items.EMERALD, 40));

		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), player);
		List<String> names = new ArrayList<>();
		for (int slot = WondersPage.slot(0); slot < ChoiceMenu.SIZE; slot++) {
			names.add(menu.icon(slot).getHoverName().getString());
		}
		helper.assertTrue(!names.contains("Test Folly"), "the disabled folly is on the page: " + names);
		WonderPlans.Drawn drawn = WonderPlans.draw(level, hall, player, FOLLY);
		helper.assertTrue(!drawn.drawn() && count(player, Items.EMERALD) == 40 && count(player, ModItems.WONDER_PLAN) == 0,
			"a disabled Wonder's plans drawn up: " + (drawn.refusal() == null ? "drawn" : drawn.refusal().getString()));

		Wonders.DISABLED = before;
		menu = VillageHallScreen.forTest(player, hall);
		menu.press(HallPages.slot(WondersPage.PAGE), player);
		int index = new ArrayList<>(Wonders.all()).indexOf(Wonders.loaded().get(FOLLY));
		helper.assertTrue(index >= 0 && menu.icon(WondersPage.slot(index)).getHoverName().getString().equals("Test Folly"), "the folly didn't come back");
		helper.succeed();
	}

	// ---------------------------------------------------------------- where the plan may go down

	/** Over a finished Wonder: refused, saying so; a little way off, it goes down. */
	//$ gametest_batch SMALL '"qaWonderOverFinished1031"'
	@GameTest(template = SMALL, batch = "qaWonderOverFinished1031")
	public void qaAPlanIsRefusedOverAFinishedWonder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		Wonder folly = folly(helper);
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlockPos origin = hall.offset(-24, 0, -24);
		WonderManager manager = WonderManager.get(level);
		WonderSite done = manager.begin(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly"), hall, player.getUUID(), "Owner", 1);
		Leftovers.after(helper, () -> manager.remove(done.id()));
		manager.place(done.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		manager.finish(done.id(), 2);

		BlockPos centre = origin.offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).startsWith("message.aliveworkplace.wonder_plan.over_wonder (It would stand over another Wonder)"),
			"over the finished Wonder: " + refusal(level, plan, centre));
		BlockPos clear = origin.offset(size.getX() + size.getX() / 2 + 3, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, clear).equals("none"), "beside it: " + refusal(level, plan, clear));
		helper.succeed();
	}

	/** "all of it within 96 blocks of the hall": with a wider village, a centre in reach but a corner past 96 is refused with that corner's distance. */
	//$ gametest_batch SMALL '"qaWonderCorner1031"'
	@GameTest(template = SMALL, batch = "qaWonderCorner1031")
	public void qaACornerPastNinetySixIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 100;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		Wonder folly = folly(helper);
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		// the centre 94 blocks east: in the hall's (wider) reach, its far corners past 96
		BlockPos far = hall.offset(94, 0, 0);
		String why = refusal(level, plan, far);
		helper.assertTrue(why.startsWith("message.aliveworkplace.wonder_plan.too_far (All of it must be within 96 blocks of the hall of "),
			"a corner past 96 (size " + size.toShortString() + "): " + why);
		// pulled in so every corner is within 96: no distance refusal
		BlockPos in = hall.offset(96 - size.getX() - size.getZ() / 2 - 2, 0, 0);
		String fine = refusal(level, plan, in);
		helper.assertTrue(!fine.startsWith("message.aliveworkplace.wonder_plan.too_far") && !fine.startsWith("message.aliveworkplace.wonder_plan.outside"),
			"every corner within 96: " + fine);
		helper.succeed();
	}

	/** A plan whose Village Hall is broken is refused and says the hall is gone. */
	//$ gametest_batch SMALL '"qaWonderNoHall1031"'
	@GameTest(template = SMALL, batch = "qaWonderNoHall1031")
	public void qaAPlanWhoseHallIsGoneIsRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		Wonder folly = folly(helper);
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		BlockPos ground = hall.offset(-20, 0, -20);
		helper.assertTrue(refusal(level, plan, ground).equals("none"), "set-up: the spot is fine with the hall: " + refusal(level, plan, ground));
		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(refusal(level, plan, ground).equals("message.aliveworkplace.wonder_plan.no_hall (The Village Hall of Testholm is gone)"),
			"with the hall broken: " + refusal(level, plan, ground));
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		use(player, ground.below());
		helper.assertTrue(placed(player).isEmpty(), "placed with no hall");
		helper.succeed();
	}
}
