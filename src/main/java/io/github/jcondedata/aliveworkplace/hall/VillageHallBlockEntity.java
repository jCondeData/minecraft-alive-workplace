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
	private long treasuryTotal;
	private int festivalCrowd;
	private long founderMoodDay;
	/** The day of the last raid on the village (see {@code guard/VillageRaids}). */
	private long lastRaidDay = -100;
	/** The day of the village's next (or last) festival, when it last feasted, when a player last called one. */
	private long festivalDay = -1;
	private long feastDay = -1;
	private long festivalCalled = -100;
	/** The day a festival was due and the treasury couldn't pay for it (Festival Season, 30.8); -1: never. */
	private long festivalMissed = -1;
	/** The treasury, in hundredths of an emerald, and the day it last took the village's takings (see {@link Treasury}). */
	private int treasury;
	@Nullable
	private java.util.UUID owner;
	private String ownerName = "";
	private boolean protectedVillage;
	/** The village's City Plan (27.2): empty until someone paints it. */
	private io.github.jcondedata.aliveworkplace.city.CityPlan plan = io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY;
	private long lastTaxDay = -1;
	/** The berries the village has found (ROADMAP 28.9, the Berry Breeder's book): none by default. */
	private final java.util.Set<net.minecraft.resources.ResourceLocation> berriesFound = new java.util.LinkedHashSet<>();
	/** The day of the last market (see {@link MarketDays}). */
	private long lastMarketDay = -1;
	/** What happened in the village, oldest first (see {@link Chronicle}). */
	private java.util.List<Chronicle.Entry> chronicle = new java.util.ArrayList<>();
	/** The village's research (see {@code research/Research}). */
	private io.github.jcondedata.aliveworkplace.research.Research.State research = io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY;
	/** The edicts in force, oldest first (see {@link Edicts}). */
	private java.util.List<Edicts.InForce> edicts = java.util.List.of();
	/** Each edict's reform progress, also of edicts lifted since (see {@link Reforms}). */
	private java.util.List<Reforms.Progress> reforms = java.util.List.of();
	/** Their effects summed (not saved: summed again after a load, a data pack reload or the switch). */
	@Nullable
	private CivicEffects.Sum civic;
	private int civicGeneration;
	/** Free Bread's running share of an extra meal (30.6), in hundredths (0 to 99); saved as {@code extraMeals}, 0 to 1. */
	private int extraMeals;
	/** Conscription (30.10): the game time work may start again after a raid (noon the next day); saved as {@code raidWorkUntil}, 0: none. */
	private long raidWorkUntil;

	/**
	 * Whether the hall's point-of-interest record was checked for its Steward's place since it loaded (27.5): halls saved
	 * before it had one get it on their first tick. Saved, so a hall whose Steward holds the place isn't checked again.
	 */
	private boolean stewardPlaceChecked;
	/** The Steward's wishes for the day and what each of his rules has done (27.6); empty in halls saved before. */
	private io.github.jcondedata.aliveworkplace.city.StewardWishes.State stewardWishes = io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY;

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

	/** Sets how the village is doing until the next round (tests, and the screens' examples). */
	public void setNeeds(VillageNeeds.Needs needs) {
		this.needs = needs;
	}

	/** Every {@link VillageNeeds#CHECK_EVERY} ticks (and on the first): the hungry fed, the village counted. */
	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, VillageHallBlockEntity hall) {
		if (!hall.stewardPlaceChecked && level instanceof net.minecraft.server.level.ServerLevel server) {
			io.github.jcondedata.aliveworkplace.city.Stewards.fixTicket(server, pos);
			hall.stewardPlaceChecked = true;
			hall.setChanged();
		}
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
			io.github.jcondedata.aliveworkplace.legend.LegendSlots.round(server, pos);
			io.github.jcondedata.aliveworkplace.legend.LegendNeeds.round(server, pos);
			io.github.jcondedata.aliveworkplace.people.Couples.round(server, pos);
			if (Treasury.ENABLED) {
				Treasury.round(server, pos, hall, census.workers().size());
			}
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.tick(server, pos, census.villagers(), census.guards(), hall.lastRaidDay, day -> {
				hall.lastRaidDay = day;
				hall.setChanged();
			});
			Conscription.round(server, pos, hall);
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

	/** The Steward's desk (27.8): proposals and declines; empty in halls saved before. */
	private io.github.jcondedata.aliveworkplace.city.StewardDesk.State stewardDesk = io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY;

	public io.github.jcondedata.aliveworkplace.city.StewardDesk.State stewardDesk() {
		return stewardDesk;
	}

	public void setStewardDesk(io.github.jcondedata.aliveworkplace.city.StewardDesk.State state) {
		stewardDesk = state;
		setChanged();
	}

	public io.github.jcondedata.aliveworkplace.city.StewardWishes.State stewardWishes() {
		return stewardWishes;
	}

	public void setStewardWishes(io.github.jcondedata.aliveworkplace.city.StewardWishes.State state) {
		stewardWishes = state;
		setChanged();
	}

	/** The day (by {@link Chronicle#day}) the village was last raided; -100 if never. */
	public long lastRaidDay() {
		return lastRaidDay;
	}

	public void setLastRaidDay(long day) {
		this.lastRaidDay = day;
		setChanged();
	}

	public io.github.jcondedata.aliveworkplace.research.Research.State research() {
		return research;
	}

	public void setResearch(io.github.jcondedata.aliveworkplace.research.Research.State research) {
		this.research = research;
		setChanged();
	}

	/** The edicts in force, oldest first. */
	public java.util.List<Edicts.InForce> edicts() {
		return edicts;
	}

	/** Sets the edicts in force (proclaiming and lifting go through {@link Edicts}; tests set the day proclaimed). */
	public void setEdicts(java.util.List<Edicts.InForce> edicts) {
		this.edicts = java.util.List.copyOf(edicts);
		civic = null;
		setChanged();
	}

	/** The effects of the edicts in force, summed once and kept until they change. */
	public CivicEffects.Sum civicEffects() {
		CivicEffects.Sum sum = civic;
		int generation = Edicts.generation();
		if (sum == null || civicGeneration != generation) {
			java.util.Set<String> reformed = new java.util.HashSet<>();
			reforms.stream().filter(Reforms.Progress::reformed).forEach(p -> reformed.add(p.id()));
			sum = Edicts.sum(edicts, reformed);
			civic = sum;
			civicGeneration = generation;
		}
		return sum;
	}

	/** Free Bread's running share of an extra meal, in hundredths of a meal (see {@code VillageNeeds}). */
	public int extraMeals() {
		return extraMeals;
	}

	public void setExtraMeals(int hundredths) {
		extraMeals = Math.max(0, Math.min(99, hundredths));
		setChanged();
	}

	/** The game time work may start again after a raid under Conscription (0: no raid kept it). */
	public long raidWorkUntil() {
		return raidWorkUntil;
	}

	/** Keeps work stopped until {@code until} (game time) at least ({@link Conscription}); never shortens it. */
	public void keepWorkStoppedUntil(long until) {
		if (until > raidWorkUntil) {
			raidWorkUntil = until;
			setChanged();
		}
	}

	/** Sets when work may start again after a raid (tests). */
	public void setRaidWorkUntil(long until) {
		raidWorkUntil = Math.max(0, until);
		setChanged();
	}

	/** Each edict's reform progress (see {@link Reforms}). */
	public java.util.List<Reforms.Progress> reforms() {
		return reforms;
	}

	/** Sets the reforms' progress (through {@link Reforms}; tests). */
	public void setReforms(java.util.List<Reforms.Progress> reforms) {
		this.reforms = java.util.List.copyOf(reforms);
		civic = null;
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

	/** Sets the rank until the next round counts it again (the round, and tests). */
	public void setRank(VillageRanks.Rank rank) {
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

	/** The day a festival was due and the treasury couldn't pay for it (-1: never). See {@link Festivals}. */
	public long festivalMissed() {
		return festivalMissed;
	}

	public void setFestivalMissed(long day) {
		festivalMissed = day;
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

	public io.github.jcondedata.aliveworkplace.city.CityPlan plan() {
		return plan;
	}

	public void setPlan(io.github.jcondedata.aliveworkplace.city.CityPlan plan) {
		this.plan = plan;
		setChanged();
	}

	/** The plan before each of the last changes made on the plan screen (27.3), newest first; not saved. */
	private final java.util.Deque<io.github.jcondedata.aliveworkplace.city.CityPlan> planUndo = new java.util.ArrayDeque<>();

	/** Changes the plan, remembering the one before for undo (at most {@code depth} steps). */
	public void changePlan(io.github.jcondedata.aliveworkplace.city.CityPlan next, int depth) {
		planUndo.push(plan);
		while (planUndo.size() > depth) {
			planUndo.removeLast();
		}
		setPlan(next);
	}

	/** Puts back the plan before the last change; false if there is nothing to undo. */
	public boolean undoPlan() {
		if (planUndo.isEmpty()) {
			return false;
		}
		setPlan(planUndo.pop());
		return true;
	}

	public int planUndoSteps() {
		return planUndo.size();
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

	/** Every emerald (in cents) the treasury has ever taken in (0 in halls from before 29.2). */
	public long treasuryTotal() {
		return treasuryTotal;
	}

	public void addTreasuryTotal(long cents) {
		treasuryTotal = Math.max(0, treasuryTotal + Math.max(0, cents));
		setChanged();
	}

	/** How many villagers came to the last festival's fireworks. */
	public int festivalCrowd() {
		return festivalCrowd;
	}

	public void setFestivalCrowd(int crowd) {
		festivalCrowd = Math.max(0, crowd);
		setChanged();
	}

	/** The day the Founder's mood came (0: not yet). */
	public long founderMoodDay() {
		return founderMoodDay;
	}

	public void setFounderMoodDay(long day) {
		founderMoodDay = day;
		setChanged();
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
		festivalMissed = tag.contains("festivalMissed") ? Nbt.getLong(tag, "festivalMissed") : -1;
		treasury = Nbt.getInt(tag, "treasury");
		treasuryTotal = Nbt.getLong(tag, "treasuryTotal");
		festivalCrowd = Nbt.getInt(tag, "festivalCrowd");
		founderMoodDay = Nbt.getLong(tag, "founderMoodDay");
		owner = Nbt.hasUuid(tag, "owner") ? Nbt.getUuid(tag, "owner") : null;
		ownerName = Nbt.getString(tag, "ownerName");
		protectedVillage = Nbt.getBoolean(tag, "protected");
		stewardPlaceChecked = Nbt.getBoolean(tag, "stewardPlaceChecked");
		plan = io.github.jcondedata.aliveworkplace.city.CityPlan.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("plan"))
			.result().orElse(io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY);
		lastTaxDay = tag.contains("lastTaxDay") ? Nbt.getLong(tag, "lastTaxDay") : -1;
		int r = Nbt.getInt(tag, "rank");
		rank = VillageRanks.Rank.values()[Math.max(0, Math.min(VillageRanks.Rank.values().length - 1, r))];
		research = io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("research"))
			.result().orElse(io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY);
		edicts = Edicts.InForce.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("edicts"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		reforms = Reforms.Progress.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("reforms"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		civic = null;
		extraMeals = Math.max(0, Math.min(99, Math.round(Nbt.getFloat(tag, "extraMeals") * 100f)));
		raidWorkUntil = Math.max(0, Nbt.getLong(tag, "raidWorkUntil"));
		stewardWishes = !tag.contains("steward") ? io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY
			: io.github.jcondedata.aliveworkplace.city.StewardWishes.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("steward"))
				.result().orElse(io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY);
		stewardDesk = !tag.contains("steward_desk") ? io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY
			: io.github.jcondedata.aliveworkplace.city.StewardDesk.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("steward_desk")).result().orElse(io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY);
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
		tag.putLong("festivalMissed", festivalMissed);
		tag.putInt("treasury", treasury);
		tag.putLong("treasuryTotal", treasuryTotal);
		tag.putInt("festivalCrowd", festivalCrowd);
		tag.putLong("founderMoodDay", founderMoodDay);
		if (owner != null) {
			Nbt.putUuid(tag, "owner", owner);
			tag.putString("ownerName", ownerName);
		}
		tag.putBoolean("protected", protectedVillage);
		tag.putBoolean("stewardPlaceChecked", stewardPlaceChecked);
		if (!plan.isEmpty() || plan.mode() != io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.ASK) {
			io.github.jcondedata.aliveworkplace.city.CityPlan.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, plan).result().ifPresent(t -> tag.put("plan", t));
		}
		tag.putLong("lastTaxDay", lastTaxDay);
		tag.putInt("rank", rank.ordinal());
		io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, research).result()
			.ifPresent(t -> tag.put("research", t));
		if (!edicts.isEmpty()) {
			Edicts.InForce.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, edicts).result().ifPresent(t -> tag.put("edicts", t));
		}
		if (!stewardWishes.equals(io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY)) {
			io.github.jcondedata.aliveworkplace.city.StewardWishes.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stewardWishes).result()
				.ifPresent(t -> tag.put("steward", t));
		}
		if (!stewardDesk.equals(io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY)) {
			io.github.jcondedata.aliveworkplace.city.StewardDesk.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stewardDesk).result().ifPresent(t -> tag.put("steward_desk", t));
		}
		if (!reforms.isEmpty()) {
			Reforms.Progress.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, reforms).result().ifPresent(t -> tag.put("reforms", t));
		}
		if (extraMeals > 0) {
			tag.putFloat("extraMeals", extraMeals / 100f);
		}
		if (raidWorkUntil > 0) {
			tag.putLong("raidWorkUntil", raidWorkUntil);
		}
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
		var carried = input.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.CITY_PLAN);
		if (carried != null) {
			plan = carried; // kept relative to the hall, so it is centred on the new spot
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CUSTOM_NAME, name);
		if (!plan.isEmpty() || plan.mode() != io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.ASK) {
			builder.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.CITY_PLAN, plan);
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove("CustomName");
		tag.remove("plan");
	}
}
