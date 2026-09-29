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

	public static void init() {
	}

	private ModComponents() {
	}
}
