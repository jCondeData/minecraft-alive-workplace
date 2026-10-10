package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
	public static final DataComponentType<BlueprintData> BLUEPRINT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("blueprint"),
		DataComponentType.<BlueprintData>builder().persistent(BlueprintData.CODEC).networkSynchronized(BlueprintData.STREAM_CODEC).build()
	);

	/** A Village Banner's base colour (30.13); its patterns are vanilla's {@code banner_patterns}. */
	public static final DataComponentType<net.minecraft.world.item.DyeColor> VILLAGE_BANNER = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("village_banner"),
		DataComponentType.<net.minecraft.world.item.DyeColor>builder().persistent(net.minecraft.world.item.DyeColor.CODEC).networkSynchronized(net.minecraft.world.item.DyeColor.STREAM_CODEC).build()
	);

	/** A Quarry Marker's corners and depth. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.mine.QuarryData> QUARRY = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("quarry"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.mine.QuarryData>builder()
			.persistent(io.github.jcondedata.aliveworkplace.mine.QuarryData.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.mine.QuarryData.STREAM_CODEC).build()
	);

	/** A Field Marker's corners. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.farm.FieldData> FIELD = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("field"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.farm.FieldData>builder()
			.persistent(io.github.jcondedata.aliveworkplace.farm.FieldData.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.farm.FieldData.STREAM_CODEC).build()
	);

	/** A Scan Tool's corners. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.farm.FieldData> SCAN = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("scan"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.farm.FieldData>builder()
			.persistent(io.github.jcondedata.aliveworkplace.farm.FieldData.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.farm.FieldData.STREAM_CODEC).build()
	);

	/** A Patrol Map's route. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route> PATROL = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("patrol"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route>builder()
			.persistent(io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route.STREAM_CODEC).build()
	);

	/** What a Shape Planner is set to draw. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.blueprint.Shapes.Settings> SHAPE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("shape"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.blueprint.Shapes.Settings>builder()
			.persistent(io.github.jcondedata.aliveworkplace.blueprint.Shapes.Settings.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.blueprint.Shapes.Settings.STREAM_CODEC).build()
	);

	/** The Village Hall a Village Ledger is bound to. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger> LEDGER = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("ledger"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger>builder()
			.persistent(io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger.STREAM_CODEC).build()
	);

	/** The Village Hall a City Plan is bound to (27.2). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger> CITY_PLAN_HALL = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("city_plan_hall"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger>builder()
			.persistent(io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem.Ledger.STREAM_CODEC).build()
	);

	/** A Colony Charter's mother village and the spot chosen for its colony (33.8). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem.Charter> COLONY_CHARTER = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("colony_charter"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem.Charter>builder()
			.persistent(io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem.Charter.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem.Charter.STREAM_CODEC).build()
	);

	/** A village's plan, on the Village Hall item when the hall is broken (27.2). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.city.CityPlan> CITY_PLAN = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("city_plan"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.city.CityPlan>builder()
			.persistent(io.github.jcondedata.aliveworkplace.city.CityPlan.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.city.CityPlan.STREAM_CODEC).build()
	);

	/** A Rally Banner's guards and whether it's raised. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.Rally> RALLY = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("rally"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.Rally>builder()
			.persistent(io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.Rally.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.guard.RallyBannerItem.Rally.STREAM_CODEC).build()
	);

	/** Where a Travel Ticket goes. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.travel.TicketData> TICKET = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("ticket"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.travel.TicketData>builder()
			.persistent(io.github.jcondedata.aliveworkplace.travel.TicketData.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.travel.TicketData.STREAM_CODEC).build()
	);

	/** A Delivery Note's courier route. */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.mail.RouteData> ROUTE = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("route"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.mail.RouteData>builder()
			.persistent(io.github.jcondedata.aliveworkplace.mail.RouteData.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.mail.RouteData.STREAM_CODEC).build()
	);

	/** What a placed blueprint still needs from the builder's chests (kept fresh by the server, never saved). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.blueprint.SupplyReport> SUPPLY_REPORT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("supply_report"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.blueprint.SupplyReport>builder()
			.networkSynchronized(io.github.jcondedata.aliveworkplace.blueprint.SupplyReport.STREAM_CODEC).build()
	);

	/** The day a luxury good that ages was made, and how long it takes to be vintage (34.5). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay> MADE_DAY = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("made_day"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay>builder()
			.persistent(io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay.STREAM_CODEC).build()
	);

	/** What a Gift holds: the wrapped item and who wrapped it (31.6). */
	public static final DataComponentType<io.github.jcondedata.aliveworkplace.story.Gifts.Wrapped> GIFT = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		AliveWorkplace.id("gift"),
		DataComponentType.<io.github.jcondedata.aliveworkplace.story.Gifts.Wrapped>builder()
			.persistent(io.github.jcondedata.aliveworkplace.story.Gifts.Wrapped.CODEC)
			.networkSynchronized(io.github.jcondedata.aliveworkplace.story.Gifts.Wrapped.STREAM_CODEC).build()
	);

	public static void init() {
	}

	private ModComponents() {
	}
}
