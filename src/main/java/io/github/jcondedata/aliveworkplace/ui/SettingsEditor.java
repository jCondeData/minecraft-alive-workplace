package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import java.util.List;

/**
 * What the Settings screen does with the mouse and keys (ROADMAP 44.4), on the settings being edited: a rail click opens
 * a section; a click on a switch's row turns it on or off; [-] and [+] step a small number; a click or drag on a slider
 * sets a big one; the Left and Right keys step the number under the mouse; "Reset this page" puts the open section's
 * settings back to their defaults. Saving stays the screen's (when it closes). Pure logic over {@link SettingsLayout}'s
 * boxes, so the GameTests change settings the way a player's clicks do.
 */
public final class SettingsEditor {
	private final WorkplaceConfig config;
	private final List<String> names;
	private final List<String> sections;
	private int section;
	private int scroll;
	private String dragging;

	public SettingsEditor(WorkplaceConfig config) {
		this(config, WorkplaceConfig.optionNames());
	}

	public SettingsEditor(WorkplaceConfig config, List<String> names) {
		this.config = config;
		this.names = List.copyOf(names);
		this.sections = SettingsLayout.shownSections(this.names);
	}

	public WorkplaceConfig config() {
		return config;
	}

	/** The sections in the rail. */
	public List<String> sections() {
		return sections;
	}

	public int section() {
		return section;
	}

	public String sectionId() {
		return sections.isEmpty() ? SettingsLayout.OTHER : sections.get(section);
	}

	/** The open section's settings, the numbers first. */
	public List<String> options() {
		return optionsIn(sectionId());
	}

	public List<String> optionsIn(String id) {
		return SettingsLayout.optionsIn(id, names, WorkplaceConfig::isSwitch);
	}

	public List<SettingsLayout.Row> rows() {
		return SettingsLayout.rows(options(), WorkplaceConfig::isSwitch);
	}

	/** Opens the {@code i}th section at its top. */
	public void open(int i) {
		if (i >= 0 && i < sections.size() && i != section) {
			section = i;
			scroll = 0;
			dragging = null;
		}
	}

	public int scroll() {
		return scroll;
	}

	public void scrollTo(SettingsLayout.Screen s, int rows) {
		scroll = Math.max(0, Math.min(rows, SettingsLayout.maxScroll(s, rows().size())));
	}

	public void scrollBy(SettingsLayout.Screen s, int rows) {
		scrollTo(s, scroll + rows);
	}

	/** The setting on the row under the mouse, or null. */
	public String optionAt(SettingsLayout.Screen s, double mx, double my) {
		List<SettingsLayout.Row> rows = rows();
		int i = SettingsLayout.rowAt(s, scroll, rows.size(), mx, my);
		return i < 0 ? null : rows.get(i).option();
	}

	/** The box of the row showing {@code option}, or null when it is scrolled out of sight or not in the open section. */
	public int[] rowOf(SettingsLayout.Screen s, String option) {
		List<SettingsLayout.Row> rows = rows();
		for (int i = scroll; i < Math.min(rows.size(), scroll + s.listRows()); i++) {
			if (option.equals(rows.get(i).option())) {
				return SettingsLayout.row(s, i - scroll);
			}
		}
		return null;
	}

	/** A left click: whether it did something (the close X and Done are the screen's). */
	public boolean click(SettingsLayout.Screen s, double mx, double my) {
		int rail = CopperLayout.at(s.railEntries(), mx, my);
		if (rail >= 0 && rail < sections.size()) {
			open(rail);
			return true;
		}
		if (CopperLayout.inside(s.reset(), mx, my)) {
			resetSection();
			return true;
		}
		String name = optionAt(s, mx, my);
		if (name == null) {
			return false;
		}
		int[] row = rowOf(s, name);
		if (WorkplaceConfig.isSwitch(name)) {
			config.setBoolean(name, !config.getBoolean(name));
			return true;
		}
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
		if (range == null) {
			return false;
		}
		if (SettingsLayout.small(range)) {
			int[][] boxes = SettingsLayout.number(row);
			if (CopperLayout.inside(boxes[0], mx, my)) {
				step(name, -1);
				return true;
			}
			if (CopperLayout.inside(boxes[2], mx, my)) {
				step(name, 1);
				return true;
			}
			return false;
		}
		int[] slider = SettingsLayout.slider(row, s.compact());
		if (mx >= slider[0] && mx < slider[0] + slider[2]) {
			config.setInt(name, SettingsLayout.valueAt(range, slider, mx));
			dragging = name;
			return true;
		}
		return false;
	}

	/** A drag with the left button held: the slider clicked last follows the mouse. */
	public boolean drag(SettingsLayout.Screen s, double mx) {
		if (dragging == null) {
			return false;
		}
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(dragging);
		config.setInt(dragging, SettingsLayout.valueAt(range, SettingsLayout.slider(SettingsLayout.row(s, 0), s.compact()), mx));
		return true;
	}

	public void release() {
		dragging = null;
	}

	public boolean dragging() {
		return dragging != null;
	}

	/** Steps a number by {@code by}, within its range; whether it changed. */
	public boolean step(String name, int by) {
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
		if (range == null || WorkplaceConfig.isSwitch(name)) {
			return false;
		}
		int now = config.getInt(name);
		int next = Math.max(range.min(), Math.min(range.max(), now + by));
		if (next == now) {
			return false;
		}
		config.setInt(name, next);
		return true;
	}

	/** Puts the open section's settings back to their defaults. */
	public void resetSection() {
		WorkplaceConfig defaults = new WorkplaceConfig();
		for (String name : options()) {
			if (WorkplaceConfig.isSwitch(name)) {
				config.setBoolean(name, defaults.getBoolean(name));
			} else {
				config.setInt(name, defaults.getInt(name));
			}
		}
	}
}
