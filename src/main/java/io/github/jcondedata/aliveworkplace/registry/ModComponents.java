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

	public static void init() {
	}

	private ModComponents() {
	}
}
