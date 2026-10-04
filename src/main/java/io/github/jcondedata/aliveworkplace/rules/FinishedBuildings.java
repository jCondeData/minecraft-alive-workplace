package io.github.jcondedata.aliveworkplace.rules;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * {@code finished}: the village's builders have finished {@code count} buildings (optionally of one {@code blueprint}, in
 * any style), or buildings in {@code styles} different styles (the plain blueprints are one style; -1 here: not by styles).
 */
public record FinishedBuildings(int count, Optional<ResourceLocation> blueprint, int styles) implements Condition {
	static FinishedBuildings read(JsonObject json) {
		if (json.has("styles")) {
			return new FinishedBuildings(0, Optional.empty(), Conditions.count(json, "styles"));
		}
		return new FinishedBuildings(Conditions.count(json, "count"),
			json.has("blueprint") ? Optional.of(Conditions.id(json, "blueprint")) : Optional.empty(), -1);
	}

	@Override
	public String type() {
		return "finished";
	}

	@Override
	public Progress progress(ServerLevel level, BlockPos hall) {
		AABB area = VillageHalls.area(hall);
		int n = 0;
		Set<String> seen = new HashSet<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedIn(level)) {
			if (!area.contains(f.placement().origin().getCenter())) {
				continue;
			}
			Optional<BlueprintStyles.Styled> styled = BlueprintStyles.parse(f.structure());
			ResourceLocation base = styled.map(BlueprintStyles.Styled::base).orElse(f.structure());
			if (blueprint.isPresent() && !blueprint.get().equals(base)) {
				continue;
			}
			n++;
			seen.add(styled.map(BlueprintStyles.Styled::style).orElse(""));
		}
		if (styles >= 0) {
			return Progress.of("finished_styles", seen.size(), styles);
		}
		return blueprint.isPresent() ? Progress.of("finished_blueprint", n, count, blueprint.get().toString()) : Progress.of(type(), n, count);
	}
}
