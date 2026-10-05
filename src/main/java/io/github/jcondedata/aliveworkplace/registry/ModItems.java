package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Reg;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModItems {
	public static final BlueprintItem BLUEPRINT = Reg.item("blueprint", BlueprintItem::new, new Item.Properties().stacksTo(1));

	/** Paper + blue dye. Turned into a real blueprint at a Blueprint Table. */
	public static final Item BLANK_BLUEPRINT = Reg.item("blank_blueprint", Item::new, new Item.Properties());

	/** Turns something built in the world into a blueprint. */
	public static final io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem SCAN_TOOL = Reg.item("scan_tool", io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem::new, new Item.Properties().stacksTo(1));

	/** Draws up shapes (walls, towers, domes...) as blueprints for the builders. */
	public static final io.github.jcondedata.aliveworkplace.blueprint.ShapePlannerItem SHAPE_PLANNER = Reg.item("shape_planner", io.github.jcondedata.aliveworkplace.blueprint.ShapePlannerItem::new, new Item.Properties().stacksTo(1));

	/** A route for a guard to patrol. */
	public static final io.github.jcondedata.aliveworkplace.guard.PatrolMapItem PATROL_MAP = Reg.item("patrol_map", io.github.jcondedata.aliveworkplace.guard.PatrolMapItem::new, new Item.Properties().stacksTo(1));
	public static final io.github.jcondedata.aliveworkplace.city.CityPlanItem CITY_PLAN = Reg.item("city_plan", io.github.jcondedata.aliveworkplace.city.CityPlanItem::new, new Item.Properties().stacksTo(1));
	public static final io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem VILLAGE_LEDGER = Reg.item("village_ledger", io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem::new, new Item.Properties().stacksTo(1));
	/** Calls a rush in a village once a day (ROADMAP 30.11). */
	public static final io.github.jcondedata.aliveworkplace.hall.WorkHornItem WORK_HORN = Reg.item("work_horn", io.github.jcondedata.aliveworkplace.hall.WorkHornItem::new, new Item.Properties().stacksTo(1));
	public static final io.github.jcondedata.aliveworkplace.guard.RallyBannerItem RALLY_BANNER = Reg.item("rally_banner", io.github.jcondedata.aliveworkplace.guard.RallyBannerItem::new, new Item.Properties().stacksTo(1));

	/** Marks out a quarry for a Miner. */
	public static final io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem QUARRY_MARKER = Reg.item("quarry_marker", io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem::new, new Item.Properties().stacksTo(1));

	/** Marks out a field for a Farmer. */
	public static final io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem FIELD_MARKER = Reg.item("field_marker", io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem::new, new Item.Properties().stacksTo(1));

	/** A ferry ticket to one travel post, sold by Ferrymen. */
	public static final io.github.jcondedata.aliveworkplace.travel.TravelTicketItem TRAVEL_TICKET = Reg.item("travel_ticket", io.github.jcondedata.aliveworkplace.travel.TravelTicketItem::new, new Item.Properties().stacksTo(16));

	/** Sets up a courier route for a Postman. */
	public static final io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem DELIVERY_NOTE = Reg.item("delivery_note", io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem::new, new Item.Properties().stacksTo(1));

	/** A price in CobbleDollars (the number it's renamed to) for a Shop Counter's price row. */
	public static final io.github.jcondedata.aliveworkplace.shop.PriceTagItem PRICE_TAG = Reg.item("price_tag", io.github.jcondedata.aliveworkplace.shop.PriceTagItem::new, new Item.Properties().stacksTo(16));

	/** Two settlers make camp where it's used: a covered wagon, supplies, a Builder's Bench. */
	public static final io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem SETTLERS_WAGON = Reg.item("settlers_wagon", io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem::new, new Item.Properties().stacksTo(1));

	/** The Guide Book: how the mod works, page by page, with in-game screenshots (ROADMAP 26.2a). */
	public static final io.github.jcondedata.aliveworkplace.guide.GuideBookItem GUIDE_BOOK = Reg.item("guide_book", io.github.jcondedata.aliveworkplace.guide.GuideBookItem::new, new Item.Properties().stacksTo(1));

	/** The Village Banner (30.13): a banner's design on a gilded crossbar; sets a village's colours at its hall. */
	public static final io.github.jcondedata.aliveworkplace.hall.VillageBannerItem VILLAGE_BANNER = Reg.item("village_banner", io.github.jcondedata.aliveworkplace.hall.VillageBannerItem::new, new Item.Properties().stacksTo(16));
	public static final net.minecraft.world.item.crafting.RecipeSerializer<io.github.jcondedata.aliveworkplace.hall.VillageBannerItem.Recipe> VILLAGE_BANNER_RECIPE = Registry.register(
		BuiltInRegistries.RECIPE_SERIALIZER, AliveWorkplace.id("village_banner"), io.github.jcondedata.aliveworkplace.hall.VillageBannerItem.RECIPE);

	public static final CreativeModeTab TAB = Registry.register(
		BuiltInRegistries.CREATIVE_MODE_TAB,
		AliveWorkplace.id("main"),
		Platform.get().creativeTab()
			.title(Component.translatable("itemGroup.aliveworkplace"))
			.icon(() -> new ItemStack(ModBlocks.BLUEPRINT_TABLE))
			// The job blocks that 21.1a replaced with vanilla ones (the Builder's Bench...) stay registered, so worlds
			// keep them, but they're gone from here and can't be crafted.
			.displayItems((params, output) -> {
				output.accept(GUIDE_BOOK);
				output.accept(ModBlocks.BLUEPRINT_TABLE);
				output.accept(BLANK_BLUEPRINT);
				output.accept(SCAN_TOOL);
				output.accept(SHAPE_PLANNER);
				output.accept(PATROL_MAP);
				output.accept(RALLY_BANNER);
				output.accept(VILLAGE_LEDGER);
				output.accept(WORK_HORN);
				output.accept(ModBlocks.CRADLE);
				output.accept(CITY_PLAN);
				output.accept(QUARRY_MARKER);
				output.accept(FIELD_MARKER);
				output.accept(ModBlocks.MAILBOX);
				output.accept(DELIVERY_NOTE);
				output.accept(ModBlocks.SHOP_COUNTER);
				output.accept(PRICE_TAG);
				output.accept(ModBlocks.TRAVEL_POST);
				output.accept(ModBlocks.TRAINING_POST);
				output.accept(ModBlocks.STOREHOUSE);
				output.accept(ModBlocks.TRAINING_DUMMY);
				output.accept(ModBlocks.VILLAGE_HALL);
				output.accept(ModBlocks.DROP_BOX);
				output.accept(SETTLERS_WAGON);
				for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
					output.accept(BlueprintItem.create(entry.id(), entry.size()));
				}
				if (io.github.jcondedata.aliveworkplace.platform.Platform.get().isModLoaded("cobblemon")) {
					for (StarterBlueprints.Entry entry : StarterBlueprints.COBBLEMON_ONLY) {
						output.accept(BlueprintItem.create(entry.id(), entry.size()));
					}
				}
				output.accept(BlueprintItem.create(StarterBlueprints.TOWN_HALL.id(), StarterBlueprints.TOWN_HALL.size()));
				for (StarterBlueprints.Entry entry : StarterBlueprints.DEFENCES) {
					output.accept(BlueprintItem.create(entry.id(), entry.size()));
				}
				for (StarterBlueprints.Entry entry : StarterBlueprints.DECORATIONS) {
					output.accept(BlueprintItem.create(entry.id(), entry.size()));
				}
			})
			.build()
	);

	public static void init() {
	}

	private ModItems() {
	}
}
