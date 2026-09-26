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

	public static void init() {
	}

	private ModComponents() {
	}
}
