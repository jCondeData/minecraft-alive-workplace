package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
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
	/** The village's rank at the last round (see {@link VillageRanks}). */
	private VillageRanks.Rank rank = VillageRanks.Rank.HAMLET;
	/** The day of the last raid on the village (see {@code guard/VillageRaids}). */
	private long lastRaidDay = -100;
	/** The day of the village's next (or last) festival, when it last feasted, when a player last called one. */
	private long festivalDay = -1;
	private long feastDay = -1;
	private long festivalCalled = -100;
	/** The treasury, in hundredths of an emerald, and the day it last took the village's takings (see {@link Treasury}). */
	private int treasury;
	@Nullable
	private java.util.UUID owner;
	private String ownerName = "";
	private boolean protectedVillage;
	private long lastTaxDay = -1;
	/** The berries the village has found (ROADMAP 28.9, the Berry Breeder's book): none by default. */
	private final java.util.Set<net.minecraft.resources.ResourceLocation> berriesFound = new java.util.LinkedHashSet<>();
	/** The day of the last market (see {@link MarketDays}). */
	private long lastMarketDay = -1;
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
			MarketDays.tick(server, pos, hall);
			VillageHalls.Census census = VillageHalls.census(server, pos);
			VillageRanks.round(server, pos, hall, census.villagers());
			Caravans.round(server, pos, census);
			io.github.jcondedata.aliveworkplace.guard.Gates.round(server, pos, census.guards());
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.round(server, pos);
			Festivals.round(server, pos, hall, census.villagers());
			io.github.jcondedata.aliveworkplace.people.Couples.round(server, pos);
			if (Treasury.ENABLED) {
				Treasury.round(server, pos, hall, census.workers().size());
			}
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.tick(server, pos, census.villagers(), census.guards(), hall.lastRaidDay, day -> {
				hall.lastRaidDay = day;
				hall.setChanged();
			});
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

	public VillageRanks.Rank rank() {
		return rank;
	}

	void setRank(VillageRanks.Rank rank) {
		this.rank = rank;
		setChanged();
	}

	public long lastMarketDay() {
		return lastMarketDay;
	}

	public void setLastMarketDay(long day) {
		lastMarketDay = day;
		setChanged();
	}

	public int questsDone() {
		return questsDone;
	}

	void questDone() {
		questsDone++;
		setChanged();
	}

	public long festivalDay() {
		return festivalDay;
	}

	public void setFestivalDay(long day) {
		festivalDay = day;
		setChanged();
	}

	public long feastDay() {
		return feastDay;
	}

	public void setFeastDay(long day) {
		feastDay = day;
		setChanged();
	}

	public long festivalCalled() {
		return festivalCalled;
	}

	public void setFestivalCalled(long day) {
		festivalCalled = day;
		setChanged();
	}

	/** Whoever placed the hall (or claimed it first); null for a hall nobody has claimed. See {@link VillageProtection}. */
	@Nullable
	public java.util.UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public void setOwner(@Nullable java.util.UUID owner, String name) {
		this.owner = owner;
		this.ownerName = name;
		setChanged();
	}

	/** Whether the owner keeps other players from changing things in the village (off unless they turn it on). */
	public boolean isProtected() {
		return protectedVillage;
	}

	public void setProtected(boolean on) {
		protectedVillage = on;
		setChanged();
		if (level != null) {
			VillageProtection.mark(level, worldPosition, on);
		}
	}

	@Override
	public void setLevel(net.minecraft.world.level.Level level) {
		super.setLevel(level);
		VillageProtection.mark(level, worldPosition, protectedVillage);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null) {
			VillageProtection.mark(level, worldPosition, false);
		}
	}

	public int treasury() {
		return treasury;
	}

	public void setTreasury(int cents) {
		treasury = Math.max(0, cents);
		setChanged();
	}

	public long lastTaxDay() {
		return lastTaxDay;
	}

	public void setLastTaxDay(long day) {
		lastTaxDay = day;
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
		name = Nbt.has(tag, "CustomName", 8) ? parseCustomNameSafe(Nbt.getString(tag, "CustomName"), registries) : null;
		lastBirth = Nbt.getLong(tag, "lastBirth");
		births = Nbt.getInt(tag, "births");
		quests = VillageQuests.Quest.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("quests"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		lastQuestDay = tag.contains("lastQuestDay") ? Nbt.getLong(tag, "lastQuestDay") : -1;
		questsDone = Nbt.getInt(tag, "questsDone");
		lastMarketDay = tag.contains("lastMarketDay") ? Nbt.getLong(tag, "lastMarketDay") : -1;
		lastRaidDay = tag.contains("lastRaidDay") ? Nbt.getLong(tag, "lastRaidDay") : -100;
		festivalDay = tag.contains("festivalDay") ? Nbt.getLong(tag, "festivalDay") : -1;
		feastDay = tag.contains("feastDay") ? Nbt.getLong(tag, "feastDay") : -1;
		festivalCalled = tag.contains("festivalCalled") ? Nbt.getLong(tag, "festivalCalled") : -100;
		treasury = Nbt.getInt(tag, "treasury");
		owner = Nbt.hasUuid(tag, "owner") ? Nbt.getUuid(tag, "owner") : null;
		ownerName = Nbt.getString(tag, "ownerName");
		protectedVillage = Nbt.getBoolean(tag, "protected");
		lastTaxDay = tag.contains("lastTaxDay") ? Nbt.getLong(tag, "lastTaxDay") : -1;
		int r = Nbt.getInt(tag, "rank");
		rank = VillageRanks.Rank.values()[Math.max(0, Math.min(VillageRanks.Rank.values().length - 1, r))];
		research = io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("research"))
			.result().orElse(io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY);
		berriesFound.clear();
		net.minecraft.nbt.ListTag berries = Nbt.getList(tag, "berriesFound", net.minecraft.nbt.Tag.TAG_STRING);
		for (int i = 0; i < berries.size(); i++) {
			net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(Nbt.stringAt(berries, i));
			if (id != null) {
				berriesFound.add(id);
			}
		}
		chronicle = new java.util.ArrayList<>();
		net.minecraft.nbt.ListTag lines = Nbt.getList(tag, "chronicle", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < lines.size(); i++) {
			CompoundTag line = Nbt.compoundAt(lines, i);
			Component text = parseCustomNameSafe(Nbt.getString(line, "text"), registries);
			if (text != null) {
				chronicle.add(new Chronicle.Entry(Nbt.getLong(line, "day"), Chronicle.Kind.parse(Nbt.getString(line, "kind")), text));
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
		tag.putLong("lastMarketDay", lastMarketDay);
		tag.putLong("lastRaidDay", lastRaidDay);
		tag.putLong("festivalDay", festivalDay);
		tag.putLong("feastDay", feastDay);
		tag.putLong("festivalCalled", festivalCalled);
		tag.putInt("treasury", treasury);
		if (owner != null) {
			Nbt.putUuid(tag, "owner", owner);
			tag.putString("ownerName", ownerName);
		}
		tag.putBoolean("protected", protectedVillage);
		tag.putLong("lastTaxDay", lastTaxDay);
		tag.putInt("rank", rank.ordinal());
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
		net.minecraft.nbt.ListTag berries = new net.minecraft.nbt.ListTag();
		berriesFound.forEach(id -> berries.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
		tag.put("berriesFound", berries);
	}

	/** The berries the village has found (ROADMAP 28.9), in the order found. */
	public java.util.Set<net.minecraft.resources.ResourceLocation> berriesFound() {
		return java.util.Collections.unmodifiableSet(berriesFound);
	}

	/** Notes a berry the village found; whether it's new. */
	public boolean findBerry(net.minecraft.resources.ResourceLocation berry) {
		boolean added = berriesFound.add(berry);
		if (added) {
			setChanged();
		}
		return added;
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
