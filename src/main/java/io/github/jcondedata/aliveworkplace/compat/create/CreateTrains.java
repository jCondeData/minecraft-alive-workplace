package io.github.jcondedata.aliveworkplace.compat.create;

import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.ITrackBlock;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Create's own trains for the villages (ROADMAP 39.7a, {@link CaravanTrains.Railway}). A village train is put together
 * the way a player puts one together: a small bogey on the track just past the station, a carriage on it (train casing
 * and the caravan's chest at the bogey's height, a seat between two train controls above, one facing each way, so it can
 * be driven both ways), all of it glued with Create's super glue, the conductor sat in the seat, and the station's own
 * assembly ({@link StationBlockEntity#assemble}). The train then gets a Create schedule: to the other station, wait,
 * back, wait, round and round, and Create's railway drives it. The train's owner is the railway's id, so only the
 * village's own trains are ever looked at.
 */
public final class CreateTrains implements CaravanTrains.Railway {
	/** How long the train waits at each station (seconds, Create's "scheduled delay"). */
	static final int WAIT_SECONDS = 10;

	@Override
	public boolean aim(ServerLevel level, BlockPos station, BlockPos other, String name) {
		if (!(level.getBlockEntity(station) instanceof StationBlockEntity be)) {
			return false;
		}
		if (be.edgePoint.hasValidTrack()) {
			return be.getStation() != null;
		}
		for (BlockPos offset : NEAR) {
			BlockPos track = station.offset(offset);
			BlockState state = level.getBlockState(track);
			if (!(state.getBlock() instanceof ITrackBlock block)) {
				continue;
			}
			List<Vec3> axes = block.getTrackAxes(level, track, state);
			if (axes.size() != 1 || Math.abs(axes.get(0).y) > 1e-3 || Math.abs(axes.get(0).dot(Vec3.atLowerCornerOf(offset))) > 1e-3) {
				continue; // the station stands beside a straight, level track (the Train Station's is a step away), never at its end
			}
			CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			tag.put("TargetTrack", NbtUtils.writeBlockPos(track.subtract(station)));
			// Facing the other station: trains coming from there stop here, and the village train is put together on
			// that side of it (found by trying both in CreateTrainCompatTests: facing away, no path is ever found)
			Vec3 toward = Vec3.atLowerCornerOf(other.subtract(station));
			tag.putBoolean("TargetDirection", axes.get(0).dot(toward) > 0);
			be.loadWithComponents(tag, level.registryAccess());
			be.setChanged();
			be.notifyUpdate();
			pending.put(station.immutable(), name); // named once Create knows it (its next tick)
			return false;
		}
		return false;
	}

	/** Where the track a station looks at may be: beside it, or a step further (the Train Station build's platform edge), nearest first. */
	private static final List<BlockPos> NEAR = java.util.stream.Stream.of(new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1),
		new BlockPos(0, 0, -1), new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(0, 0, 2), new BlockPos(0, 0, -2)).toList();

	/** Stations aimed by the village and the name they get once Create knows them. */
	private final Map<BlockPos, String> pending = new java.util.concurrent.ConcurrentHashMap<>();

	@Nullable
	private GlobalStation station(ServerLevel level, BlockPos pos) {
		if (!(level.getBlockEntity(pos) instanceof StationBlockEntity be)) {
			return null;
		}
		GlobalStation station = be.getStation();
		String name = station == null ? null : pending.remove(pos);
		if (name != null) {
			be.updateName(name);
		}
		return station;
	}

	@Nullable
	private static TrackGraph graphOf(GlobalStation station) {
		for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
			if (graph.getPoint(EdgePointType.STATION, station.id) != null) {
				return graph;
			}
		}
		return null;
	}

	@Override
	public boolean linked(ServerLevel level, BlockPos a, BlockPos b) {
		GlobalStation sa = station(level, a);
		GlobalStation sb = station(level, b);
		if (sa == null || sb == null || sa == sb) {
			return false;
		}
		TrackGraph graph = graphOf(sa);
		return graph != null && graph == graphOf(sb);
	}

	@Override
	@Nullable
	public UUID assemble(ServerLevel level, BlockPos stationPos, BlockPos otherPos, UUID owner, Villager conductor) {
		GlobalStation home = station(level, stationPos);
		GlobalStation away = station(level, otherPos);
		if (home == null || away == null || home.getPresentTrain() != null || !(level.getBlockEntity(stationPos) instanceof StationBlockEntity be)) {
			return null;
		}
		Direction along = be.getAssemblyDirection();
		if (along == null || along.getAxis().isVertical()) {
			return null;
		}
		BlockPos target = be.edgePoint.getGlobalPosition();
		BlockState trackState = be.edgePoint.getTrackBlockState();
		ITrackBlock track = be.edgePoint.getTrack();
		for (int i = 1; i <= 3; i++) {
			BlockPos rail = target.relative(along, i);
			if (!track.trackEquals(trackState, level.getBlockState(rail))
				|| !level.getBlockState(rail.above()).canBeReplaced() || !level.getBlockState(rail.above(2)).canBeReplaced()) {
				AliveWorkplace.LOG.info("Alive Workplace: no room for the village train at the station at {} (the track past it, {} blocks, must be straight and clear)",
					stationPos.toShortString(), 3);
				return null;
			}
		}
		BlockPos bogey = target.above().relative(along);
		BlockPos seat = bogey.above().relative(along);
		Map<BlockPos, BlockState> parts = new java.util.LinkedHashMap<>();
		parts.put(bogey, with(block("small_bogey"), "axis", along.getAxis().getSerializedName()));
		parts.put(bogey.relative(along), block("railway_casing"));
		parts.put(bogey.relative(along, 2), with(Blocks.CHEST.defaultBlockState(), "facing", along.getClockWise().getSerializedName()));
		parts.put(bogey.above(), with(block("controls"), "facing", along.getSerializedName()));
		parts.put(seat, block("brown_seat"));
		parts.put(seat.relative(along), with(block("controls"), "facing", along.getOpposite().getSerializedName()));
		parts.forEach((pos, state) -> level.setBlockAndUpdate(pos, state));
		SuperGlueEntity glue = new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(bogey, seat.relative(along)));
		level.addFreshEntity(glue);
		SeatBlock.sitDown(level, seat, conductor);
		be.enterAssemblyMode(null);
		be.assemble(owner);
		Train train = home.getPresentTrain();
		if (train == null) {
			AliveWorkplace.LOG.warn("Alive Workplace: the village train at {} didn't assemble: {}", stationPos.toShortString(),
				be.saveWithoutMetadata(level.registryAccess()).get("LastException"));
			be.exitAssemblyMode();
			conductor.stopRiding();
			glue.discard();
			parts.keySet().forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
			return null;
		}
		be.exitAssemblyMode();
		train.name = Component.translatable("train.aliveworkplace.caravan", home.name, away.name);
		train.runtime.setSchedule(schedule(away.name, home.name), false);
		AliveWorkplace.LOG.info("Alive Workplace: the village train {} runs between {} and {}", train.id, home.name, away.name);
		return train.id;
	}

	/** Round and round: to {@code there}, wait, back {@code home}, wait. */
	static Schedule schedule(String there, String home) {
		Schedule schedule = new Schedule();
		schedule.entries.add(stop(there));
		schedule.entries.add(stop(home));
		schedule.cyclic = true;
		return schedule;
	}

	private static ScheduleEntry stop(String station) {
		DestinationInstruction to = new DestinationInstruction();
		to.getData().putString("Text", station);
		ScheduledDelay wait = new ScheduledDelay();
		wait.getData().putInt("Value", WAIT_SECONDS);
		List<List<ScheduleWaitCondition>> conditions = new ArrayList<>();
		conditions.add(new ArrayList<>(List.of(wait)));
		return new ScheduleEntry(to, conditions);
	}

	@Nullable
	private static Train train(UUID owner) {
		for (Train train : Create.RAILWAYS.trains.values()) {
			if (owner.equals(train.owner) && !train.invalid) {
				return train;
			}
		}
		return null;
	}

	@Override
	public boolean exists(ServerLevel level, UUID owner) {
		Train train = train(owner);
		return train != null && !train.derailed;
	}

	@Override
	public boolean at(ServerLevel level, UUID owner, BlockPos pos) {
		Train train = train(owner);
		GlobalStation station = station(level, pos);
		return train != null && station != null && station.id.equals(train.currentStation); // by id: Create may rebuild a graph's objects
	}

	@Override
	public String status(ServerLevel level, UUID owner) {
		Train train = train(owner);
		if (train == null) {
			return "no train";
		}
		GlobalStation at = train.getCurrentStation();
		return "speed " + train.speed + ", going to " + (train.navigation.destination == null ? "-" : train.navigation.destination.name)
			+ ", at " + (at == null ? "-" : at.name) + ", schedule " + train.runtime.state + " #" + train.runtime.currentEntry
			+ ", conductors " + train.hasForwardConductor() + "/" + train.hasBackwardConductor() + ", derailed " + train.derailed;
	}

	@Override
	public void scrap(ServerLevel level, UUID owner) {
		Train train = train(owner);
		if (train != null) {
			train.invalid = true; // Create's railway takes it off on its next tick
		}
	}

	private static BlockState block(String path) {
		return Lookup.value(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", path)).defaultBlockState();
	}

	private static BlockState with(BlockState state, String name, String value) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? state : set(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}
}
