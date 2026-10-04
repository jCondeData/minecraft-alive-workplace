package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings ({@code config/aliveworkplace.json}) as a vanilla options screen: a switch for every feature and
 * a slider for every distance and number, each with its tooltip. Opened from Mod Menu. Closing the screen saves the
 * file and puts the values into effect in this game (singleplayer and LAN worlds); a dedicated server keeps its own
 * file, which the note under the title says.
 */
public class ConfigScreen extends OptionsSubScreen {
	/** The language keys of an option's label and tooltip. */
	public static String labelKey(String option) {
		return "aliveworkplace.config." + option;
	}

	public static String tooltipKey(String option) {
		return "aliveworkplace.config." + option + ".tooltip";
	}

	private final Path dir;
	private final WorkplaceConfig config;
	private final List<OptionInstance<?>> options = new ArrayList<>();

	public ConfigScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("aliveworkplace.config.title"));
		this.dir = Platform.get().configDir();
		this.config = WorkplaceConfig.load(dir);
	}

	@Override
	protected void addTitle() {
		layout.setHeaderHeight(44);
		LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
		header.defaultCellSetting().alignHorizontallyCenter();
		header.addChild(new StringWidget(title, font));
		header.addChild(new StringWidget(Component.translatable("aliveworkplace.config.note").withStyle(ChatFormatting.GRAY), font));
	}

	@Override
	protected void addOptions() {
		List<OptionInstance<?>> switches = new ArrayList<>();
		List<OptionInstance<?>> numbers = new ArrayList<>();
		for (String name : WorkplaceConfig.optionNames()) {
			if (WorkplaceConfig.isSwitch(name)) {
				switches.add(OptionInstance.createBoolean(labelKey(name), OptionInstance.cachedConstantTooltip(Component.translatable(tooltipKey(name))),
					config.getBoolean(name), value -> config.setBoolean(name, value)));
			} else {
				WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
				numbers.add(new OptionInstance<>(labelKey(name), OptionInstance.cachedConstantTooltip(Component.translatable(tooltipKey(name))),
					(caption, value) -> Options.genericValueLabel(caption, value), new OptionInstance.IntRange(range.min(), range.max()),
					config.getInt(name), value -> config.setInt(name, value)));
			}
		}
		list.addSmall(numbers.toArray(OptionInstance[]::new));
		list.addSmall(switches.toArray(OptionInstance[]::new));
		options.clear();
		options.addAll(numbers);
		options.addAll(switches);
	}

	/** The option buttons and sliders, numbers first (the screenshot scene checks their labels fit). */
	public List<AbstractWidget> optionWidgets() {
		return options.stream().map(list::findOption).toList();
	}

	/** The settings being edited, saved when the screen closes. */
	public WorkplaceConfig config() {
		return config;
	}

	public void scrollToEnd() {
		list.setScrollAmount(list.getMaxScroll());
	}

	@Override
	public void removed() {
		super.removed();
		config.save(dir);
		config.apply();
	}
}
