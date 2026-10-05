package io.github.jcondedata.aliveworkplace.legend;

/**
 * One of a Legend's powers, read from its file ({@link Powers#parse}). The shared ones ({@link PacePower},
 * {@link MoodPower}) are read where they act; each Legend item adds its named power.
 */
public interface Power {
	String type();

	/**
	 * The line the hall's list and Legends page show for this power (29.4): {@code legend.<ns>.power.<type>} unless the
	 * power says more. Each Legend item's named power adds its line to the lang file.
	 */
	default net.minecraft.network.chat.Component describe() {
		return net.minecraft.network.chat.Component.translatable("legend.aliveworkplace.power." + type());
	}
}
