package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;

/**
 * B819: a courier route whose filter named only items of a mod taken out of the pack must not turn into "carry
 * everything" when it loads. The ids that no longer name an item stay on the route (it carries nothing of them) and are
 * saved back as they were, so the items come back on the route if the mod returns.
 */
public class B819GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	/** An item of a mod taken out of the pack. */
	private static final String GONE = "create:brass_ingot";

	/** {@code route} as it loads after its filter's first id was renamed to {@link #GONE}. */
	private static RouteData withGoneFirst(RouteData route) {
		CompoundTag tag = (CompoundTag) RouteData.CODEC.encodeStart(NbtOps.INSTANCE, route).getOrThrow();
		tag.getList("filter", Tag.TAG_STRING).set(0, StringTag.valueOf(GONE));
		return RouteData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}

	private static List<String> savedFilter(RouteData route) {
		CompoundTag tag = (CompoundTag) RouteData.CODEC.encodeStart(NbtOps.INSTANCE, route).getOrThrow();
		List<String> ids = new ArrayList<>();
		ListTag list = tag.getList("filter", Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			ids.add(list.getString(i));
		}
		return ids;
	}

	/**
	 * A Delivery Note whose route carried only a gone mod's item: after a reload it carries nothing, its tooltip says
	 * so, picking a kind in the other hand adds to that filter, and the gone id is still saved on the note.
	 */
	//$ gametest_batch AREA '"b819Note"'
	@GameTest(template = AREA, batch = "b819Note")
	public void aDeliveryNoteFilteredOnlyToAGoneItemCarriesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		var registries = helper.getLevel().registryAccess();
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.of(new BlockPos(9, 2, 3)), List.of(Items.COPPER_INGOT)));
		CompoundTag saved = (CompoundTag) note.save(registries);
		CompoundTag route = saved.getCompound("components").getCompound("aliveworkplace:route");
		helper.assertTrue(route.getList("filter", Tag.TAG_STRING).size() == 1, "the note's filter didn't save as one id: " + saved);
		route.getList("filter", Tag.TAG_STRING).set(0, StringTag.valueOf(GONE));
		ItemStack back = ItemStack.parseOptional(registries, saved);
		RouteData data = back.get(ModComponents.ROUTE);
		helper.assertTrue(data != null && data.isComplete(), "the note lost its route: " + data);
		helper.assertFalse(data.carries(new ItemStack(Items.COBBLESTONE, 64)) || data.carries(new ItemStack(Items.DIAMOND, 3))
			|| data.carries(new ItemStack(Items.COPPER_INGOT, 3)), "the note that carried only Create's brass now carries other items: " + data);
		helper.assertTrue(data.gone().equals(List.of(GONE)), "the gone id wasn't kept: " + data.gone());

		List<String> tip = new ArrayList<>();
		for (Component line : back.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL)) {
			tip.add(line.getString());
		}
		helper.assertTrue(tip.contains("Carries only: 1 item no longer in the game"), "the note's tooltip: " + tip);
		helper.assertFalse(tip.stream().anyMatch(s -> s.contains("everything")), "the tooltip still says it carries everything: " + tip);

		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, back);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 4));
		QaStageThreeGameTests.clickAir(helper, player);
		RouteData picked = player.getMainHandItem().get(ModComponents.ROUTE);
		helper.assertTrue(picked.carries(new ItemStack(Items.WHEAT, 4)) && !picked.carries(new ItemStack(Items.COBBLESTONE, 4)),
			"after adding wheat the note should carry wheat and nothing else: " + picked);
		helper.assertTrue(savedFilter(picked).equals(List.of("minecraft:wheat", GONE)), "the note's saved filter: " + savedFilter(picked));
		List<String> tip2 = player.getMainHandItem().getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL)
			.stream().map(Component::getString).toList();
		helper.assertTrue(tip2.contains("Carries only: Wheat, 1 item no longer in the game"), "the tooltip with wheat: " + tip2);

		// a reset (sneak-click) clears the ends, not the kinds
		RouteData reset = picked.withEnds(Optional.empty(), Optional.empty(), Optional.empty());
		helper.assertTrue(reset.gone().equals(List.of(GONE)) && reset.filter().equals(List.of(Items.WHEAT)), "a reset changed the kinds: " + reset);
		helper.succeed();
	}

	/** A postman's routes with a gone id save and load again and again unchanged; two gone ids say "2 items". */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aGoneFilterIdSurvivesEverySaveOnAPostman(GameTestHelper helper) {
		RouteData mixed = withGoneFirst(new RouteData(Optional.of(new BlockPos(1, 2, 3)), Optional.of(new BlockPos(10, 2, 3)),
			List.of(Items.COPPER_INGOT, Items.WHEAT)));
		RouteData only = withGoneFirst(new RouteData(Optional.of(new BlockPos(4, 2, 4)), Optional.of(new BlockPos(4, 2, 20)), List.of(Items.COPPER_INGOT)));
		RouteData all = new RouteData(Optional.of(new BlockPos(7, 2, 7)), Optional.of(new BlockPos(30, 2, 7)), List.of());
		Villager postman = EntityType.VILLAGER.create(helper.getLevel());
		ModAttachments.COURIER_ROUTES.set(postman, List.of(mixed, only, all));
		for (int round = 1; round <= 2; round++) {
			CompoundTag saved = postman.saveWithoutId(new CompoundTag());
			postman = EntityType.VILLAGER.create(helper.getLevel());
			postman.load(saved);
			List<RouteData> back = ModAttachments.COURIER_ROUTES.get(postman);
			helper.assertTrue(back != null && back.equals(List.of(mixed, only, all)), "round " + round + ": the routes changed: " + back);
		}
		List<RouteData> back = ModAttachments.COURIER_ROUTES.get(postman);
		helper.assertTrue(savedFilter(back.get(0)).equals(List.of("minecraft:wheat", GONE)), "the mixed filter saves as " + savedFilter(back.get(0)));
		helper.assertTrue(savedFilter(back.get(1)).equals(List.of(GONE)), "the gone-only filter saves as " + savedFilter(back.get(1)));
		helper.assertTrue(back.get(0).carries(new ItemStack(Items.WHEAT)) && !back.get(0).carries(new ItemStack(Items.COBBLESTONE)), "the mixed route's kinds");
		helper.assertFalse(back.get(1).carries(new ItemStack(Items.COBBLESTONE)), "the gone-only route carries cobblestone");
		helper.assertTrue(back.get(2).carries(new ItemStack(Items.COBBLESTONE)) && back.get(2).carriesEverything(), "the route with no filter stopped carrying everything");

		RouteData two = new RouteData(only.from(), only.to(), List.of(), Optional.empty(), List.of(GONE, "create:zinc_ingot"));
		helper.assertTrue(two.filterNames().getString().equals("2 items no longer in the game"), "two gone ids read: " + two.filterNames().getString());
		helper.succeed();
	}

	/**
	 * The postman at work: a route that carried only a gone mod's item leaves its chest alone while he runs his other
	 * route (oak logs) to the end.
	 */
	//$ gametest_ticks_batch AREA '1600' '"b819Postman"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "b819Postman")
	public void aPostmanLeavesTheChestOfAGoneOnlyRouteAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos goneFrom = new BlockPos(12, 2, 4);
		BlockPos goneTo = new BlockPos(4, 2, 12);
		BlockPos logFrom = new BlockPos(12, 2, 12);
		BlockPos logTo = new BlockPos(8, 2, 2);
		for (BlockPos p : List.of(goneFrom, goneTo, logFrom, logTo)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		Container source = helper.getBlockEntity(goneFrom);
		source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		source.setItem(1, new ItemStack(Items.DIAMOND, 3));
		Container logs = helper.getBlockEntity(logFrom);
		logs.setItem(0, new ItemStack(Items.OAK_LOG, 20));
		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		RouteData goneOnly = withGoneFirst(new RouteData(Optional.of(helper.absolutePos(goneFrom)), Optional.of(helper.absolutePos(goneTo)),
			List.of(Items.COPPER_INGOT)));
		RouteData logRoute = new RouteData(Optional.of(helper.absolutePos(logFrom)), Optional.of(helper.absolutePos(logTo)), List.of(Items.OAK_LOG));
		ModAttachments.COURIER_ROUTES.set(postman, List.of(goneOnly, logRoute));
		Container target = helper.getBlockEntity(goneTo);
		Container logTarget = helper.getBlockEntity(logTo);
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(logTarget.countItem(Items.OAK_LOG) == 20, "the log route didn't run: " + logTarget.countItem(Items.OAK_LOG) + " of 20 logs");
			helper.assertTrue(target.isEmpty(), "the gone-only route carried items over: " + target.countItem(Items.COBBLESTONE) + " cobblestone, "
				+ target.countItem(Items.DIAMOND) + " diamonds");
			helper.assertTrue(source.countItem(Items.COBBLESTONE) == 64 && source.countItem(Items.DIAMOND) == 3, "the source chest was touched");
			helper.succeed();
		});
	}
}
