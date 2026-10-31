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
 * A bug found by QA for the Wonder Plan (ROADMAP 35.3, "never over a finished building or another Wonder"): a plan goes
 * down over a Wonder that is begun and laid out but waiting for its goods, because such a Wonder has no build sites yet
 * and isn't finished, so WonderPlans.placeRefusal sees nothing there.
 */
public class QaWonderPlanBug1031Tests {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
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

	/** "never over ... another Wonder": a Wonder begun and laid out but waiting for its goods (no build sites yet) still counts. */
	//$ gametest_batch SMALL '"qaWonderOverWaiting1031"'
	@GameTest(template = SMALL, batch = "qaWonderOverWaiting1031")
	public void qaAPlanIsRefusedOverAWonderWaitingForItsGoods(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = town(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		Wonder folly = folly(helper);
		ItemStack plan = WonderPlanItem.of(level, hall, folly, Component.literal("Testholm"));
		Vec3i size = BlueprintItem.data(plan).get().size().get();
		BlockPos origin = hall.offset(-24, 0, -24);
		BlockPos centre = origin.offset(size.getX() / 2, 0, size.getZ() / 2);
		helper.assertTrue(refusal(level, plan, centre).equals("none"), "the open ground isn't free to begin with: " + refusal(level, plan, centre));

		// another Wonder begun there: its plan laid, the stone set, stage I waiting for its goods
		WonderManager manager = WonderManager.get(level);
		WonderSite waiting = manager.begin(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly"), hall, player.getUUID(), "Owner", 1);
		Leftovers.after(helper, () -> manager.remove(waiting.id()));
		manager.place(waiting.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		WonderSite now = manager.site(waiting.id()).orElseThrow();
		helper.assertTrue(now.goingUp() && !now.started() && now.parts().isEmpty(), "set-up: a Wonder waiting for its goods, no build sites");

		helper.assertTrue(refusal(level, plan, centre).startsWith("message.aliveworkplace.wonder_plan.over_wonder"),
			"a plan placed over a Wonder waiting for its goods: " + refusal(level, plan, centre));
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		player.setYRot(180);
		use(player, centre.below());
		helper.assertTrue(placed(player).isEmpty(), "the plan went down over the waiting Wonder at " + placed(player).map(p -> p.origin().subtract(hall).toShortString()));
		helper.succeed();
	}

	/**
	 * The same hole from a builder's side: a house blueprint placed inside a Wonder that is laid out but waiting for its
	 * goods is taken by a builder (the overlap check in Builders looks only at build sites), so he would build the house
	 * where the Wonder's stage I goes up. Expected: refused, as over any build in progress ("That spot overlaps another
	 * build in progress (Test Folly)").
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderHouseOverWaiting1031"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderHouseOverWaiting1031")
	public void qaABuilderRefusesAHouseInsideAWonderWaitingForItsGoods(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection);
		BlockPos hallAt = new BlockPos(24, 2, 24);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		folly(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
		WonderManager manager = WonderManager.get(level);
		WonderSite waiting = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(waiting.id()));
		manager.place(waiting.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		helper.assertTrue(manager.site(waiting.id()).map(s -> s.goingUp() && !s.started() && s.parts().isEmpty()).orElse(false), "set-up: a waiting Wonder");

		BlockPos benchAt = new BlockPos(26, 2, 4);
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, helper.absolutePos(benchAt));
		ResourceLocation hut = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, hut).orElseThrow().size();
		ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(hut, size);
		BlueprintData.Placement inside = new BlueprintData.Placement(level.dimension().location(), origin.offset(2, 0, 2), Rotation.NONE, Mirror.NONE);
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
			BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(inside)));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, net.minecraft.world.level.GameType.SURVIVAL, QaStageSixGameTests.name("qaWH"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		io.github.jcondedata.aliveworkplace.build.BuildSite taken = io.github.jcondedata.aliveworkplace.build.Builders.activeSite(level, builder);
		if (taken != null) {
			Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).remove(taken.id()));
		}
		helper.assertTrue(taken == null, "the builder took a house inside the waiting Test Folly's footprint; he said: "
			+ c.seen().stream().map(Component::getString).toList());
		helper.succeed();
	}
}
