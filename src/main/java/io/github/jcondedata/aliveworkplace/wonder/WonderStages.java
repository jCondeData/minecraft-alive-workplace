package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A Wonder's stages as build sites (35.4). Each stage's parts become ordinary {@link BuildSite}s, placed with the
 * Wonder's turn and mirror at their offsets, pointing back at the Wonder ({@link BuildSite#wonder}) with the Foundation
 * Stone as their bench (so the chests round the stone are the stockpile). Only the last stage levels the ground round
 * it. A stage starts only when every part of the one before is done: the check reads the saved sites alone, so the
 * order holds through a restart at any tick.
 */
public final class WonderStages {
	/** Ticks between checks for a finished stage. */
	static final int INTERVAL = 20;

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % INTERVAL == 0) {
				tick(level);
			}
		});
	}

	/**
	 * Puts the Wonder {@code id} down at {@code placement} with its stone at {@code stone} and starts its current stage
	 * for {@code builder} (may be null: the village's builders pick it up). Returns the stage's build sites.
	 */
	public static List<BuildSite> start(ServerLevel level, UUID id, BlueprintData.Placement placement, BlockPos stone, @Nullable UUID builder) {
		WonderManager manager = WonderManager.get(level);
		manager.place(id, placement, stone);
		return startStage(level, manager.site(id).orElseThrow(), builder);
	}

	/** Creates the build sites of {@code site}'s current stage, and records them on the Wonder. */
	static List<BuildSite> startStage(ServerLevel level, WonderSite site, @Nullable UUID builder) {
		Optional<Wonder> wonder = Optional.ofNullable(Wonders.loaded().get(site.wonder()));
		if (wonder.isEmpty() || site.placement().isEmpty() || site.stage() < 1 || site.stage() > wonder.get().stages().size()) {
			return List.of();
		}
		BlueprintData.Placement at = site.placement().get();
		boolean last = site.stage() == wonder.get().stages().size();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> made = new ArrayList<>();
		UUID owner = site.owner().orElse(new UUID(0L, 0L));
		String colours = wonder.get().villageColours() ? VillageBanners.wonderStyle(VillageBanners.of(level, site.hall())) : null;
		for (Wonder.Part part : wonder.get().stages().get(site.stage() - 1).parts()) {
			BlockPos origin = at.origin().offset(StructureTemplate.transform(part.offset(), at.mirror(), at.rotation(), BlockPos.ZERO));
			BuildSite build = sites.create(owner, site.ownerName(), BlueprintStyles.styled(part.part(), colours),
				new BlueprintData.Placement(at.dimension(), origin, at.rotation(), at.mirror()));
			build.setWonder(site.id());
			site.stone().ifPresent(build::setBench);
			build.setLevelGround(last);
			if (builder != null) {
				build.setBuilder(builder);
				build.setQueued(true); // in the builder's queue: picked up when they finish what they're on (Builders.activeSite)
			}
			made.add(build);
		}
		WonderManager.get(level).setParts(site.id(), made.stream().map(BuildSite::id).toList());
		return made;
	}

	/** Whether every part of {@code site}'s current stage is done (a part whose site is gone counts as done). */
	public static boolean stageDone(ServerLevel level, WonderSite site) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && !build.isDone()) {
				return false;
			}
		}
		return true;
	}

	/** Moves every Wonder of {@code level} whose stage is done on to its next stage, or finishes it. */
	public static void tick(ServerLevel level) {
		WonderManager manager = WonderManager.get(level);
		for (WonderSite site : manager.sites()) {
			if (site.goingUp() && site.placement().isPresent() && !site.parts().isEmpty() && stageDone(level, site)) {
				advance(level, site);
			}
		}
	}

	/** {@code site}'s stage is done: the next one starts (its builders kept), or the Wonder is finished. */
	static void advance(ServerLevel level, WonderSite site) {
		WonderManager manager = WonderManager.get(level);
		Wonder wonder = Wonders.loaded().get(site.wonder());
		BuildSiteManager sites = BuildSiteManager.get(level);
		UUID builder = null;
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && build.builder() != null) {
				builder = build.builder();
			}
		}
		int next = site.stage() + 1;
		if (wonder == null || next > wonder.stages().size()) {
			manager.finish(site.id(), io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
			return;
		}
		manager.setStage(site.id(), next);
		startStage(level, manager.site(site.id()).orElseThrow(), builder);
	}

	/**
	 * Ends the Wonder {@code id}: what's built stays, its open build sites end (their builders let go), it's forgotten,
	 * and a fresh Wonder Plan goes to its owner if online, else drops by the stone. Returns the plan given (empty if
	 * there was no such Wonder or it isn't loaded any more).
	 */
	public static ItemStack cancel(ServerLevel level, UUID id) {
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		Optional<WonderSite> found = manager.site(id);
		if (found.isEmpty()) {
			return ItemStack.EMPTY;
		}
		WonderSite site = found.get();
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && !build.isDone()) {
				Builders.cancel(level, build);
				sites.remove(part);
			}
		}
		manager.remove(id);
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (wonder == null || site.done()) {
			return ItemStack.EMPTY;
		}
		ItemStack plan = WonderPlanItem.of(level, site.hall(), wonder, VillageHalls.name(level, site.hall()));
		ItemStack given = plan.copy();
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			if (!owner.getInventory().add(given)) {
				owner.drop(given, false);
			}
		} else {
			BlockPos at = site.stone().orElse(site.placement().map(BlueprintData.Placement::origin).orElse(site.hall()));
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, given);
		}
		return plan;
	}

	private WonderStages() {
	}
}
