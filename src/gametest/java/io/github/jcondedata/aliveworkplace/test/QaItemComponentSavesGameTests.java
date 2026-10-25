package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.travel.TicketData;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, saves): the item data no other test saved and loaded (a Field Marker's corners, a Quarry Marker's corners,
 * depth and strip level, a Shape Planner's settings, a Village Ledger, a Wonder Plan, a Rally Banner's guards, a Travel
 * Ticket, a wrapped Gift, a Village Banner's colour) comes back the same after the item is saved with a chest or a
 * player and after it is sent to a client, with optional parts both set and unset and values at their edges.
 */
public class QaItemComponentSavesGameTests implements FabricGameTest {

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void markersPlannersAndLedgersComeBackTheSame(GameTestHelper helper) {
		ResourceLocation nether = Level.NETHER.location();
		roundTrip(helper, ModItems.FIELD_MARKER, ModComponents.FIELD,
			new FieldData(Optional.of(nether), Optional.of(new BlockPos(-29999999, -64, 29999999)), Optional.of(new BlockPos(5, 319, -5))));
		roundTrip(helper, ModItems.FIELD_MARKER, ModComponents.FIELD, new FieldData(Optional.empty(), Optional.of(new BlockPos(1, 2, 3)), Optional.empty()));
		roundTrip(helper, ModItems.FIELD_MARKER, ModComponents.FIELD, new FieldData(Optional.empty(), Optional.empty(), Optional.empty()));

		roundTrip(helper, ModItems.QUARRY_MARKER, ModComponents.QUARRY,
			new QuarryData(Optional.of(nether), Optional.of(new BlockPos(0, 60, 0)), Optional.of(new BlockPos(15, 60, 15)), 2, Optional.of(-58)));
		roundTrip(helper, ModItems.QUARRY_MARKER, ModComponents.QUARRY,
			new QuarryData(Optional.empty(), Optional.of(new BlockPos(0, 60, 0)), Optional.empty(), 0, Optional.empty()));

		roundTrip(helper, ModItems.SHAPE_PLANNER, ModComponents.SHAPE, Shapes.Settings.DEFAULT);
		roundTrip(helper, ModItems.SHAPE_PLANNER, ModComponents.SHAPE,
			new Shapes.Settings("dome", 31, 1, 2, false, Optional.of(ResourceLocation.withDefaultNamespace("deepslate_bricks"))));

		GlobalPos hall = GlobalPos.of(Level.OVERWORLD, new BlockPos(812, 64, -3301));
		roundTrip(helper, ModItems.VILLAGE_LEDGER, ModComponents.LEDGER, new VillageLedgerItem.Ledger(hall, Component.literal("Brücke — \"Ost\" 東")));
		roundTrip(helper, ModItems.VILLAGE_LEDGER, ModComponents.LEDGER, new VillageLedgerItem.Ledger(hall, Component.literal("")));

		roundTrip(helper, ModItems.WONDER_PLAN, ModComponents.WONDER_PLAN,
			new WonderPlanItem.Plan(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "great_library"), hall, Component.literal("Riverside")));
		roundTrip(helper, ModItems.WONDER_PLAN, ModComponents.WONDER_PLAN,
			new WonderPlanItem.Plan(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "colossus"), GlobalPos.of(Level.END, BlockPos.ZERO),
				Component.translatable("block.aliveworkplace.village_hall")));
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bannersTicketsAndGiftsComeBackTheSame(GameTestHelper helper) {
		List<UUID> twelve = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			twelve.add(new UUID(0xABCDL + i, i * 31L));
		}
		roundTrip(helper, ModItems.RALLY_BANNER, ModComponents.RALLY, new RallyBannerItem.Rally(twelve, true));
		roundTrip(helper, ModItems.RALLY_BANNER, ModComponents.RALLY, RallyBannerItem.Rally.EMPTY);

		roundTrip(helper, ModItems.TRAVEL_TICKET, ModComponents.TICKET,
			new TicketData(new UUID(1L, 2L), GlobalPos.of(Level.NETHER, new BlockPos(-4000, 33, 9000)), "Ölberg — the \"Old\" Post"));
		roundTrip(helper, ModItems.TRAVEL_TICKET, ModComponents.TICKET, new TicketData(new UUID(0L, 0L), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), ""));

		for (DyeColor colour : DyeColor.values()) {
			roundTrip(helper, ModItems.VILLAGE_BANNER, ModComponents.VILLAGE_BANNER, colour);
		}

		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal("Grandad's"));
		sword.set(DataComponents.DAMAGE, 300);
		sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
		gift(helper, new Gifts.Wrapped(sword, "Alex"));
		gift(helper, new Gifts.Wrapped(new ItemStack(Items.POPPY, 64), "Steve the \"Kind\" 東"));
		ItemStack inner = new ItemStack(ModItems.GIFT);
		inner.set(ModComponents.GIFT, new Gifts.Wrapped(new ItemStack(Items.CAKE), "Alex"));
		gift(helper, new Gifts.Wrapped(inner, "Steve"));
		helper.succeed();
	}

	/** A gift's item has no equals, so the wrapped stack is compared with its components. */
	private static void gift(GameTestHelper helper, Gifts.Wrapped value) {
		ItemStack stack = new ItemStack(ModItems.GIFT);
		stack.set(ModComponents.GIFT, value);
		check(helper, value, QaStageThreeGameTests.saveAndLoad(helper, stack, ModComponents.GIFT), "a save");
		check(helper, value, QaStageThreeGameTests.overTheNetwork(helper, stack).get(ModComponents.GIFT), "the network");
	}

	private static void check(GameTestHelper helper, Gifts.Wrapped value, Gifts.Wrapped back, String over) {
		helper.assertTrue(back != null && value.from().equals(back.from()) && ItemStack.matches(value.item(), back.item()),
			"the gift of " + value.item() + " from " + value.from() + " changed over " + over + ": " + (back == null ? null : back.item() + " from " + back.from()));
	}

	private static <T> void roundTrip(GameTestHelper helper, Item item, DataComponentType<T> type, T value) {
		ItemStack stack = new ItemStack(item);
		stack.set(type, value);
		T saved = QaStageThreeGameTests.saveAndLoad(helper, stack, type);
		helper.assertTrue(value.equals(saved), type + " on " + item + " changed over a save: " + value + " -> " + saved);
		T sent = QaStageThreeGameTests.overTheNetwork(helper, stack).get(type);
		helper.assertTrue(value.equals(sent), type + " on " + item + " changed over the network: " + value + " -> " + sent);
	}
}
