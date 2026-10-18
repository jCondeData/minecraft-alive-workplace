package io.github.jcondedata.aliveworkplace.mechanic;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Builders build contraptions (ROADMAP 39.5, design note M39, "Contraptions"). With Create installed and config
 * {@code createBuilds} on, a new build site puts its kinetic parts in after what holds them up, by group (supports, then
 * shafts and gearboxes, then cogwheels, then belts: {@link KineticParts}, from data); each block keeps its axis and
 * facing as every block does, and the data Create needs comes with it ({@link #blockData}: a belt's length, index and
 * controller, the controller moved with the build). A part broken while the build is still going is put back before
 * the builder calls it done ({@link #goBack}). Once it's finished, its parts' speeds are read from their saved data by
 * name ({@code Speed}) and the owner is told whether it all turns, and what doesn't. No Create class is used.
 */
public final class Contraptions {
	/** How long after a build is finished its turning is checked, and checked again while something doesn't turn yet. */
	static final int CHECK_AFTER = 100;
	/** How many times the check looks before it lists what doesn't turn (a wheel's water takes a moment to fall). */
	static final int CHECK_TRIES = 3;
	/** How many times a builder goes back over the kinetic parts for ones broken since they went in. */
	static final int MOST_PASSES = 2;
	/** Most parts named in the owner's line; the rest are counted. */
	static final int NAMED = 4;

	/** The data a kinetic block's speed and network are kept in: worked out again in the new place, never copied. */
	private static final List<String> RUNNING = List.of("Speed", "Source", "Network", "NeedsSpeedUpdate");
	/** What of a belt segment's data is copied: its place on the belt and its looks (never the items on it). */
	private static final List<String> BELT_PLACE = List.of("Index", "Length", "IsController");
	private static final List<String> BELT_LOOKS = List.of("Casing", "Covered", "Dye");

	private Contraptions() {
	}

	public static void init() {
		KineticParts.init();
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % 20 == 0) {
				check(level);
			}
		});
	}

	/** Whether builds put contraptions together (Create's parts known, {@code createBuilds} on). */
	public static boolean active() {
		return CreateBuilds.ENABLED && KineticParts.any();
	}

	// ---- the data Create needs ----------------------------------------------------------------------------------------

	/**
	 * Whether a kinetic block's data is made right for where it's built: with Create's parts known and the expansion
	 * open, whatever {@code createBuilds} says, since a belt copied with its old controller breaks itself (a fix, not an
	 * option).
	 */
	static boolean fixesData() {
		return KineticParts.any() && io.github.jcondedata.aliveworkplace.Expansions.on(io.github.jcondedata.aliveworkplace.Expansions.M39);
	}

	/**
	 * Where the blueprint's corner stood in the world it was saved from, read off a belt's controller segment (its
	 * {@code Controller} is its own position there); null without one, or when no contraption is built.
	 */
	@Nullable
	public static BlockPos savedOrigin(Blueprint blueprint) {
		if (!fixesData()) {
			return null;
		}
		for (Blueprint.Entry entry : blueprint.blocks()) {
			if (KineticParts.isBelt(entry.state()) && entry.nbt() != null && Nbt.getBoolean(entry.nbt(), "IsController")) {
				BlockPos controller = MechanicWork.readPos(entry.nbt().get("Controller"));
				if (controller != null) {
					return controller.subtract(entry.pos());
				}
			}
		}
		return null;
	}

	/**
	 * The block-entity data to give {@code state} where the build stands: a kinetic block's data without its speed and
	 * network (Create works them out again once it's in place); a belt segment's place on its belt and its looks, with
	 * {@code Controller} moved from where the blueprint was saved by the build's offset and rotation. When the controller
	 * can't be moved (no controller segment in the blueprint, or a mirrored build, which turns a belt end for end) the
	 * place is left out too, and Create works the belt out from its blocks. Any other block's data as it is.
	 */
	@Nullable
	public static CompoundTag blockData(BlockState state, @Nullable CompoundTag nbt, @Nullable BlockPos savedOrigin, BlueprintData.Placement placement) {
		if (nbt == null || !fixesData()) {
			return nbt;
		}
		if (KineticParts.isBelt(state)) {
			CompoundTag out = new CompoundTag();
			for (String key : BELT_LOOKS) {
				copy(nbt, out, key);
			}
			BlockPos at = MechanicWork.readPos(nbt.get("Controller"));
			if (at != null && savedOrigin != null && placement.mirror() == Mirror.NONE) {
				for (String key : BELT_PLACE) {
					copy(nbt, out, key);
				}
				BlockPos world = placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
					.transform(at.subtract(savedOrigin), placement.mirror(), placement.rotation(), BlockPos.ZERO));
				out.put("Controller", nbt.get("Controller") instanceof CompoundTag ? xyz(world) : new IntArrayTag(new int[] {world.getX(), world.getY(), world.getZ()}));
			}
			return out;
		}
		if (KineticParts.ofKineticMod(state.getBlock()) && nbt.contains("Speed")) {
			CompoundTag out = nbt.copy();
			RUNNING.forEach(out::remove);
			return out;
		}
		return nbt;
	}

	private static void copy(CompoundTag from, CompoundTag to, String key) {
		Tag value = from.get(key);
		if (value != null) {
			to.put(key, value.copy());
		}
	}

	private static CompoundTag xyz(BlockPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("X", pos.getX());
		tag.putInt("Y", pos.getY());
		tag.putInt("Z", pos.getZ());
		return tag;
	}

	// ---- the order ----------------------------------------------------------------------------------------------------

	/**
	 * Whether a crew member leaves {@code step} for now: it's a kinetic part of a group the lead hasn't reached yet, so
	 * nothing goes in before what it sits on or drives it.
	 */
	public static boolean notYet(BuildSite site, BuildPlan plan, BuildPlan.Step step) {
		if (!site.kineticOrder() || site.stage() != BuildPlan.Stage.STRUCTURE || site.isRetrying()) {
			return false;
		}
		int group = KineticParts.group(step.state());
		return group >= 0 && site.cursor() < plan.kineticStart(group);
	}

	/**
	 * The structure or decoration list has run out: if a kinetic part put in earlier is gone (broken mid-build) and its
	 * spot is free, the builder goes back to the first kinetic part and over them again, putting back what's missing
	 * (at most {@link #MOST_PASSES} times a build). True if it went back.
	 */
	public static boolean goBack(ServerLevel level, BuildSite site, BuildPlan plan) {
		if (!site.kineticOrder() || site.kineticPasses() >= MOST_PASSES
			|| site.stage() != BuildPlan.Stage.STRUCTURE && site.stage() != BuildPlan.Stage.DECORATION) {
			return false;
		}
		int first = plan.kineticStart(0);
		List<BuildPlan.Step> structure = plan.steps(BuildPlan.Stage.STRUCTURE);
		if (first < 0 || first >= structure.size()) {
			return false;
		}
		for (BuildPlan.Step step : structure.subList(first, structure.size())) {
			BlockState there = level.getBlockState(step.pos());
			if (!MaterialRules.matches(there, step.state()) && there.canBeReplaced() && !site.gaveUp(step.pos())) {
				site.backToKineticParts(first);
				return true;
			}
		}
		return false;
	}

	// ---- the turning check --------------------------------------------------------------------------------------------

	/** A build is finished: its turning is checked in a moment, if it has parts that turn. */
	public static void finished(ServerLevel level, BuildSite site, @Nullable BuildPlan plan, Villager builder) {
		if (plan == null || !site.kineticOrder() || !active()) {
			return;
		}
		List<BlockPos> parts = new ArrayList<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (turns(level, step.pos(), step.state())) {
					parts.add(step.pos().immutable());
				}
			}
		}
		if (parts.isEmpty()) {
			return;
		}
		BuildSiteManager.get(level).scheduleTurnCheck(new BuildSiteManager.TurnCheck(site.structure(), site.placement(), site.owner(),
			builder.getDisplayName().getString(), parts, level.getGameTime() + CHECK_AFTER, 0));
	}

	/**
	 * Whether the block planned at {@code pos} is a kinetic one: of a kinetic mod, with a turning speed in its saved data
	 * (a wheel, a machine, a part), or a kinetic part that's gone already (it's listed).
	 */
	private static boolean turns(ServerLevel level, BlockPos pos, BlockState planned) {
		if (!KineticParts.ofKineticMod(planned.getBlock()) || !planned.hasBlockEntity()) {
			return false;
		}
		CompoundTag data = saved(level, pos);
		return data == null ? KineticParts.group(planned) >= 0 : data.contains("Speed");
	}

	@Nullable
	private static CompoundTag saved(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be == null ? null : be.saveWithoutMetadata(level.registryAccess());
	}

	/** The parts among {@code parts} that don't turn now: gone, or standing still. */
	public static List<BlockPos> stillParts(ServerLevel level, List<BlockPos> parts) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : parts) {
			CompoundTag data = saved(level, pos);
			if (data == null || Math.abs(Nbt.getFloat(data, "Speed")) <= 0) {
				out.add(pos);
			}
		}
		return out;
	}

	/** The checks that are due: all turning, the owner is told so; not yet, looked at again a little later, then listed. */
	static void check(ServerLevel level) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		if (manager.turnChecks().isEmpty()) {
			return;
		}
		for (BuildSiteManager.TurnCheck c : manager.dueTurnChecks(level.getGameTime())) {
			if (c.parts().stream().anyMatch(p -> !level.isLoaded(p))) {
				manager.scheduleTurnCheck(new BuildSiteManager.TurnCheck(c.structure(), c.placement(), c.owner(), c.builder(), c.parts(),
					level.getGameTime() + CHECK_AFTER, c.tries())); // nobody near: looked at when it's loaded
				continue;
			}
			List<BlockPos> still = stillParts(level, c.parts());
			if (!still.isEmpty() && c.tries() + 1 < CHECK_TRIES) {
				manager.scheduleTurnCheck(new BuildSiteManager.TurnCheck(c.structure(), c.placement(), c.owner(), c.builder(), c.parts(),
					level.getGameTime() + CHECK_AFTER, c.tries() + 1));
				continue;
			}
			manager.setNotTurning(c.placement(), still);
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(c.owner());
			if (owner != null) {
				owner.sendSystemMessage(line(level, c, still).withStyle(still.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
			}
		}
	}

	/**
	 * What the owner reads: who checked (the village's Mechanic when there's one nearby, who leads on contraptions, else
	 * the builder), and either that all of it turns or what doesn't, the first few by name and place.
	 */
	public static MutableComponent line(ServerLevel level, BuildSiteManager.TurnCheck c, List<BlockPos> still) {
		Optional<Villager> mechanic = MechanicWork.crewLead(level, c.placement().origin());
		Component who = mechanic.<Component>map(Villager::getDisplayName).orElse(Component.literal(c.builder()));
		Component build = Blueprints.displayName(c.structure());
		if (still.isEmpty()) {
			return Words.counted("message.aliveworkplace.contraption_turns", c.parts().size(), who, build, c.parts().size());
		}
		MutableComponent list = Component.empty();
		for (int i = 0; i < Math.min(NAMED, still.size()); i++) {
			BlockPos p = still.get(i);
			BlockState state = level.getBlockState(p);
			Component part = state.isAir() ? Component.translatable("message.aliveworkplace.contraption_gone")
				: state.getBlock().getName();
			if (i > 0) {
				list.append(", ");
			}
			list.append(Component.translatable("message.aliveworkplace.contraption_part", part, p.getX() + " " + p.getY() + " " + p.getZ()));
		}
		if (still.size() > NAMED) {
			list.append(", ").append(Component.translatable("message.aliveworkplace.contraption_more", still.size() - NAMED));
		}
		return Words.counted("message.aliveworkplace.contraption_stuck", still.size(), who, build, still.size(), list);
	}
}
