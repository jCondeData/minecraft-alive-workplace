package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Which buildings may be moved at all (ROADMAP 45.6), asked by the Hall's Move page when a building's Move button is
 * clicked and again on Confirm. Refused, each with its reason: everything with config {@code moveBuildings} off; a
 * building that isn't the village's own (not one of the hall's finished buildings); one in another player's protected
 * village ({@link VillageProtection#keeper}, the mod's own claims: operators and the owner's friends are let in); the
 * Village Hall's own building, or one that holds any other village's Hall (B1161: the Mover would carry it away); a
 * Wonder (or a part of one); and a building with a build site still going on it (an upgrade, a repair or a build
 * overlapping it), since the Builder's site would lose its building under it.
 */
public final class MoveRules {
	/** Whether buildings may be moved at all (config {@code moveBuildings}). Off: the Move page refuses every one. */
	public static boolean ENABLED = true;

	/**
	 * Why {@code player} may not move the building at {@code from} in the village round {@code hall}, or null when they
	 * may: a line for the action bar, in plain words.
	 */
	@Nullable
	public static Component refusal(ServerLevel level, BlockPos hall, BlockPos from, ServerPlayer player) {
		if (!ENABLED) {
			return Component.translatable("message.aliveworkplace.move.refused.off");
		}
		BuildSiteManager sites = BuildSiteManager.get(level);
		Optional<BuildSiteManager.Finished> building = sites.finishedInVillage(level, hall).stream()
			.filter(f -> f.placement().origin().equals(from)).findFirst();
		if (building.isEmpty()) {
			return Component.translatable("message.aliveworkplace.move.refused.not_ours");
		}
		BuildSiteManager.Finished f = building.get();
		BoundingBox box = BlueprintLibrary.get(level, f.structure()).map(b -> BlueprintOutline.bounds(f.placement(), b.size()))
			.orElse(new BoundingBox(from));
		Optional<BlockPos> keeper = VillageProtection.keeper(level, player, box);
		if (keeper.isPresent()) {
			return VillageProtection.denied(level, keeper.get()).plainCopy();
		}
		if (box.isInside(hall) || holdsHall(level, box)) {
			return Component.translatable("message.aliveworkplace.move.refused.hall");
		}
		if (WonderPlans.isWonder(f.structure())) {
			return Component.translatable("message.aliveworkplace.move.refused.wonder");
		}
		for (BuildSite site : sites.all()) {
			if (site.isDone() || !site.placement().dimension().equals(Ids.of(level.dimension()))) {
				continue;
			}
			boolean same = site.placement().origin().equals(from);
			boolean over = same || BlueprintLibrary.get(level, site.structure())
				.map(b -> BlueprintOutline.bounds(site.placement(), b.size()).intersects(box)).orElse(false);
			if (over) {
				return Component.translatable("message.aliveworkplace.move.refused.building");
			}
		}
		return null;
	}

	/**
	 * Whether any Village Hall stands inside {@code box} (B1161: another village's, two villages side by side). The
	 * loaded chunks are read by their block entities, without loading any; a chunk that isn't loaded is asked by its
	 * halls' points of interest.
	 */
	static boolean holdsHall(ServerLevel level, BoundingBox box) {
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // loaded only, and without waiting (B103)
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof VillageHallBlockEntity && box.isInside(be.getBlockPos())) {
						return true;
					}
				}
			}
		}
		int reach = Math.max(box.getXSpan(), box.getZSpan()) / 2 + 1;
		return level.getPoiManager().getInSquare(h -> h.is(ModVillagers.VILLAGE_HALL_POI), box.getCenter(), reach, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos)
			.anyMatch(p -> box.isInside(p) && level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(p.getX()),
				SectionPos.blockToSectionCoord(p.getZ())) == null);
	}

	private MoveRules() {
	}
}
