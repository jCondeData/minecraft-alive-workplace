package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A Village Hall's name for its village (from a Name Tag, or the hall item's anvil name); none: a made-up one. */
public class VillageHallBlockEntity extends BlockEntity implements Nameable {
	@Nullable
	private Component name;
	/** How the village is doing, from the last round (not saved: the first tick counts again). */
	@Nullable
	private VillageNeeds.Needs needs;
	/** When the last baby was born here (0: never), and how many have been. */
	private long lastBirth;
	private int births;
	/** The village's open quests, the day the last one went up and how many have been done. */
	private java.util.List<VillageQuests.Quest> quests = java.util.List.of();
	private long lastQuestDay = -1;
	private int questsDone;
	/** What happened in the village, oldest first (see {@link Chronicle}). */
	private java.util.List<Chronicle.Entry> chronicle = new java.util.ArrayList<>();
	/** The village's research (see {@code research/Research}). */
	private io.github.jcondedata.aliveworkplace.research.Research.State research = io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY;

	public VillageHallBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.VILLAGE_HALL_ENTITY, pos, state);
	}

	@Nullable
	@Override
	public Component getCustomName() {
		return name;
	}

	@Override
	public Component getName() {
		return name != null ? name : VillageHalls.madeUpName(worldPosition);
	}

	/** How the village was doing at the last round, or null before the first. */
	@Nullable
	public VillageNeeds.Needs needs() {
		return needs;
	}

	/** Every {@link VillageNeeds#CHECK_EVERY} ticks (and on the first): the hungry fed, the village counted. */
	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, VillageHallBlockEntity hall) {
		if (level instanceof net.minecraft.server.level.ServerLevel server
			&& (hall.needs == null || Math.floorMod(level.getGameTime() + pos.hashCode(), VillageNeeds.CHECK_EVERY) == 0)) {
			hall.needs = VillageNeeds.check(server, pos);
			VillageQuests.tick(server, pos, hall);
			if (VillageGrowth.grow(server, pos, hall.needs, hall.lastBirth) != null) {
				hall.lastBirth = level.getGameTime();
				hall.births++;
				hall.setChanged();
			}
		}
	}

	/** The chronicle, oldest first. */
	public java.util.List<Chronicle.Entry> chronicle() {
		return java.util.List.copyOf(chronicle);
	}

	void addToChronicle(Chronicle.Entry entry) {
		chronicle.add(entry);
		while (chronicle.size() > Chronicle.MAX) {
			chronicle.remove(0);
		}
		setChanged();
	}

	public io.github.jcondedata.aliveworkplace.research.Research.State research() {
		return research;
	}

	public void setResearch(io.github.jcondedata.aliveworkplace.research.Research.State research) {
		this.research = research;
		setChanged();
	}

	public java.util.List<VillageQuests.Quest> quests() {
		return quests;
	}

	public void setQuests(java.util.List<VillageQuests.Quest> quests) {
		this.quests = java.util.List.copyOf(quests);
		setChanged();
	}

	public long lastQuestDay() {
		return lastQuestDay;
	}

	public void setLastQuestDay(long day) {
		lastQuestDay = day;
		setChanged();
	}

	public int questsDone() {
		return questsDone;
	}

	void questDone() {
		questsDone++;
		setChanged();
	}

	public long lastBirth() {
		return lastBirth;
	}

	public int births() {
		return births;
	}

	/** When the last baby was born (tests). */
	public void setLastBirth(long time) {
		lastBirth = time;
		setChanged();
	}

	public void setCustomName(@Nullable Component name) {
		this.name = name;
		setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		name = tag.contains("CustomName", 8) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
		lastBirth = tag.getLong("lastBirth");
		births = tag.getInt("births");
		quests = VillageQuests.Quest.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("quests"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		lastQuestDay = tag.contains("lastQuestDay") ? tag.getLong("lastQuestDay") : -1;
		questsDone = tag.getInt("questsDone");
		research = io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("research"))
			.result().orElse(io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY);
		chronicle = new java.util.ArrayList<>();
		net.minecraft.nbt.ListTag lines = tag.getList("chronicle", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < lines.size(); i++) {
			CompoundTag line = lines.getCompound(i);
			Component text = parseCustomNameSafe(line.getString("text"), registries);
			if (text != null) {
				chronicle.add(new Chronicle.Entry(line.getLong("day"), Chronicle.Kind.parse(line.getString("kind")), text));
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (name != null) {
			tag.putString("CustomName", Component.Serializer.toJson(name, registries));
		}
		tag.putLong("lastBirth", lastBirth);
		tag.putInt("births", births);
		VillageQuests.Quest.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, quests).result().ifPresent(t -> tag.put("quests", t));
		tag.putLong("lastQuestDay", lastQuestDay);
		tag.putInt("questsDone", questsDone);
		io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, research).result()
			.ifPresent(t -> tag.put("research", t));
		net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
		for (Chronicle.Entry entry : chronicle) {
			CompoundTag line = new CompoundTag();
			line.putLong("day", entry.day());
			line.putString("kind", entry.kind().name().toLowerCase(java.util.Locale.ROOT));
			line.putString("text", Component.Serializer.toJson(entry.text(), registries));
			lines.add(line);
		}
		tag.put("chronicle", lines);
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
		super.applyImplicitComponents(input);
		name = input.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CUSTOM_NAME, name);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove("CustomName");
	}
}
