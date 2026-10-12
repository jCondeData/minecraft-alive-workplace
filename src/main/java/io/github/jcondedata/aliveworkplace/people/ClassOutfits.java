package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Dressed by class (34.17): what a villager wears over their job's outfit, bottom to top. Their class's layer (the
 * {@code outfit} of its file in {@code data/<ns>/classes/}, {@code textures/entity/villager/class/<class>.png}; a
 * class with none, the Peasant, keeps the job's outfit as it is), then the elder's face for an elder (34.19), on
 * grown villagers and children alike. The textures are drawn by {@code tools/textures/art/classes.py}: the owner's picks
 * of 2026-10-11 (34.17a), the Artisan's neckerchief and belt pouch, the Burgher's frock coat and pocket square, the
 * Noble's ermine mantle and chain of office, and the elder's grey brows and short beard. Nothing saves which look a
 * villager wears: it follows their saved class and age. 34.18's render layer draws what this lists.
 */
public final class ClassOutfits {
	/** The elder's look: a face layer (brows, beard), drawn under any hat. */
	public static final ResourceLocation ELDER = AliveWorkplace.id("textures/entity/villager/class/elder.png");

	private ClassOutfits() {
	}

	/** The layer a villager of class {@code c} wears over their job's outfit, or null for none (no class, a Peasant). */
	@Nullable
	public static ResourceLocation outfit(@Nullable SocialClasses.SocialClass c) {
		return c == null ? null : c.outfit().orElse(null);
	}

	/** The layers drawn over the job's outfit for a villager of class {@code c} at {@code stage}, bottom to top. */
	public static List<ResourceLocation> layers(@Nullable SocialClasses.SocialClass c, LifeStages.Stage stage) {
		List<ResourceLocation> out = new ArrayList<>(2);
		ResourceLocation outfit = outfit(c);
		if (outfit != null) {
			out.add(outfit);
		}
		if (stage == LifeStages.Stage.ELDER) {
			out.add(ELDER);
		}
		return out;
	}

	/** The layers {@code villager} wears on {@code day} (their class's, then the elder's face). */
	public static List<ResourceLocation> layers(Villager villager, long day) {
		return layers(SocialClasses.of(villager), LifeStages.stage(villager, day));
	}
}
