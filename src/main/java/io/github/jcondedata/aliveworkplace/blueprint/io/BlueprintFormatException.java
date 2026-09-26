package io.github.jcondedata.aliveworkplace.blueprint.io;

import net.minecraft.network.chat.Component;

/** A blueprint file that could not be read. The message is a translation key suffix plus arguments. */
public class BlueprintFormatException extends Exception {
	private final String key;
	private final Object[] args;

	public BlueprintFormatException(String key, Object... args) {
		super(key);
		this.key = key;
		this.args = args;
	}

	public Component toComponent() {
		return Component.translatable("message.aliveworkplace.import.error." + key, args);
	}
}
