package io.github.jcondedata.aliveworkplace.mc;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * Registry lookups. Changes: {@code registry.get(id)} becomes {@code getValue(id)} by 1.21.4 (porting.md); in 1.21.2
 * registries become their own lookups ({@code asLookup}, {@code getHolder}, {@code getTag}, {@code registryOrThrow}
 * change with them).
 */
public final class Lookup {
	/** The entry with {@code id}: the registry's default (air...) or null if there's none. */
	public static <T> T value(Registry<T> registry, ResourceLocation id) {
		return registry.get(id);
	}

	public static <T> T value(Registry<T> registry, ResourceKey<T> key) {
		return registry.get(key);
	}

	public static <T> Optional<Holder.Reference<T>> holder(Registry<T> registry, ResourceKey<T> key) {
		return registry.getHolder(key);
	}

	public static <T> Holder.Reference<T> holderOrThrow(Registry<T> registry, ResourceKey<T> key) {
		return registry.getHolderOrThrow(key);
	}

	public static <T> Optional<HolderSet.Named<T>> tag(Registry<T> registry, TagKey<T> tag) {
		return registry.getTag(tag);
	}

	/** A registry of the world (data-driven ones too: enchantments, structures...). */
	public static <T> Registry<T> registry(RegistryAccess access, ResourceKey<? extends Registry<? extends T>> key) {
		return access.registryOrThrow(key);
	}

	/** The registry as a holder lookup (to read block states from saved data...). */
	public static <T> HolderLookup.RegistryLookup<T> lookup(Registry<T> registry) {
		return registry.asLookup();
	}

	private Lookup() {
	}
}
