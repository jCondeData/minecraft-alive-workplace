package io.github.jcondedata.aliveworkplace.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.jcondedata.aliveworkplace.client.ConfigScreen;

/**
 * Mod Menu's "Configure" button opens {@link ConfigScreen}. Mod Menu is optional: it loads this class (the "modmenu"
 * entrypoint in fabric.mod.json) only when it is installed, and the mod's links (source, issues) come from the
 * contact block there.
 */
public class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::new;
	}
}
