package io.github.jcondedata.aliveworkplace.hall;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;

/** A village's colours (ROADMAP 30.13), set at the hall with a Village Banner. {@code villageBanners} in the config. */
public final class VillageBanners {
	/** {@code villageBanners} in the config. Off: no Village Banners can be crafted or set; colours stay saved. */
	public static boolean ENABLED = true;

	/** A banner design: base colour and pattern layers. */
	public record Colours(DyeColor base, BannerPatternLayers patterns) {
	}

	/** The colours of the hall's village, or null when none were set. */
	@Nullable
	public static Colours of(VillageHallBlockEntity hall) {
		return ENABLED ? hall.colours() : null;
	}

	private VillageBanners() {
	}
}
