package io.github.jcondedata.aliveworkplace.platform.fabric;

import io.github.jcondedata.aliveworkplace.platform.Attachment;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** An {@link Attachment} kept as a Fabric data attachment. */
record FabricAttachment<T>(AttachmentType<T> type) implements Attachment<T> {
	@Override
	@Nullable
	public T get(Entity holder) {
		return holder.getAttached(type);
	}

	@Override
	public T getOrCreate(Entity holder) {
		return holder.getAttachedOrCreate(type);
	}

	@Override
	public T getOrElse(Entity holder, T fallback) {
		return holder.getAttachedOrElse(type, fallback);
	}

	@Override
	public boolean has(Entity holder) {
		return holder.hasAttached(type);
	}

	@Override
	@Nullable
	public T set(Entity holder, @Nullable T value) {
		return holder.setAttached(type, value);
	}

	@Override
	@Nullable
	public T remove(Entity holder) {
		return holder.removeAttached(type);
	}
}
