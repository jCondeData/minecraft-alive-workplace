package io.github.jcondedata.aliveworkplace.platform;

import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.function.Supplier;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Data we keep on an entity (a villager's build site, a trainer's prize list...), saved with it. The loader stores it
 * (on Fabric a data attachment); declare one with {@link #saved} in {@code registry/ModAttachments}.
 */
public interface Attachment<T> {
	/** Saved data called {@code aliveworkplace:<name>}. */
	static <T> Attachment<T> saved(String name, Codec<T> codec) {
		return Platform.get().attachment(AliveWorkplace.id(name), codec, null);
	}

	/** Saved data that starts as {@code initial} the first time {@link #getOrCreate} asks for it. */
	static <T> Attachment<T> saved(String name, Codec<T> codec, Supplier<T> initial) {
		return Platform.get().attachment(AliveWorkplace.id(name), codec, initial);
	}

	@Nullable
	T get(Entity holder);

	/** The value, or a new one from the initial value (only for attachments that have one). */
	T getOrCreate(Entity holder);

	T getOrElse(Entity holder, T fallback);

	boolean has(Entity holder);

	/** Sets the value ({@code null} removes it); returns the old one. */
	@Nullable
	T set(Entity holder, @Nullable T value);

	/** Removes the value; returns the old one. */
	@Nullable
	T remove(Entity holder);
}
