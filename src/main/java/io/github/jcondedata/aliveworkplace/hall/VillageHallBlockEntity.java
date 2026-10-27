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
	private PlayerBank.State playerBank = new PlayerBank.State();
	/** The day of the last trade fair (29.17), or -1 before the first is counted. */
	private long fairDay = -1;
	/** The day of the last banquet (29.18, the Grand Chef), or -1 before the first; whether its feast was eaten yet. */
	private long banquetDay = -1;
	private boolean banquetEaten;
	/** The village's anthem (29.19, the Bard Laureate), composed once; whether the owner has had the book. */
	private Anthems.Anthem anthem;
	private boolean anthemBookGiven;
	/** The trade fair's bunting (29.17): the banners put up round the square, taken down after the fair's day. */
	private final java.util.List<BlockPos> fairBunting = new java.util.ArrayList<>();
	private int festivalCrowd;
	/** Legends visiting as guests (29.8): who last came when, the guest staying now, and the day each place last rolled. */
	private io.github.jcondedata.aliveworkplace.legend.LegendGuests.State legendGuests = io.github.jcondedata.aliveworkplace.legend.LegendGuests.State.EMPTY;
	/** The Seer's dawn foretelling (29.16): tonight's raid, the next festival and market days, tomorrow's guest; empty in older halls. */
	private io.github.jcondedata.aliveworkplace.legend.Seer.State seer = io.github.jcondedata.aliveworkplace.legend.Seer.State.EMPTY;
	/** The Observatory's search for the Sky Chart and what it found (35.18); empty in halls saved before. */
	private io.github.jcondedata.aliveworkplace.wonder.SkyChart.Search skyChart = new io.github.jcondedata.aliveworkplace.wonder.SkyChart.Search();
	/** The village Pokédex (29.21, the Pokémon Professor): every species ever kept in its pastures, in the order logged; empty in older halls. */
	private final java.util.LinkedHashSet<String> pokedex = new java.util.LinkedHashSet<>();
	private long founderMoodDay;
	/** The Founder (29.23): Masters whose Founder's mood failed (comma-joined UUIDs), whether the Founder was made, the last wagon day. */
	private String founderTried = "";
	private boolean founderMade;
	private long founderWagonDay;
	/** Strange moods (29.10): the day they may come again after one failed, and the day the village last rolled for one. */
	private long noMoodUntil;
	private long moodRolledDay;
	/** The day of the last raid on the village (see {@code guard/VillageRaids}). */
	private long lastRaidDay = -100;
	/**
	 * The disaster under way or foretold (32.15; saved as {@code disaster}, absent: none), the day it begins (or began;
	 * {@code disasterSince}, -1), its last day ({@code disasterEnds}, absent or -1: until rain) and the day the last one
	 * ended ({@code lastDisasterDay}, -100: never).
	 */
	private net.minecraft.resources.ResourceLocation disaster;
	private long disasterSince = -1;
	private long disasterEnds = -1;
	private long lastDisasterDay = -100;
	/** The day of the village's next (or last) festival, when it last feasted, when a player last called one. */
	private long festivalDay = -1;
	/** A later festival that was already planned when an earlier one was brought in before it (a Wonder's, 35.8); -1: none. */
	private long festivalAfter = -1;
	private long feastDay = -1;
	private long festivalCalled = -100;
	/** Whether the next festival is a Noble's Ball (34.7: every other one in a village with a Noble), and the day of the last ball (-100: none). */
	private boolean ballTurn;
	private long ballDay = -100;
	/** Classes switched on here (34.22): 0 never seeded (halls saved before 1.8), 1 seeded with 1.8's rules; and the last day of settling in (0: none). */
	private int classesVersion;
	private long classGraceUntil;
	/** The village's own Habitat Block (ROADMAP 28.14), or null. */
	@org.jetbrains.annotations.Nullable
	private net.minecraft.core.BlockPos habitat;
	/** The day a festival was due and the treasury couldn't pay for it (Festival Season, 30.8); -1: never. */
	private long festivalMissed = -1;
	/** The treasury, in hundredths of an emerald, and the day it last took the village's takings (see {@link Treasury}). */
	private int treasury;
	@Nullable
	private java.util.UUID owner;
	private String ownerName = "";
	private boolean protectedVillage;
	/** At peace (32.21): no raids, lairs or disasters come; off in halls saved before. */
	private boolean atPeace;
	/** The day the village was set At peace (a lair standing leaves at the dawn after it); -1 when it isn't. */
	private long peaceSince = -1;
	/** The village's City Plan (27.2): empty until someone paints it. */
	private io.github.jcondedata.aliveworkplace.city.CityPlan plan = io.github.jcondedata.aliveworkplace.city.CityPlan.EMPTY;
	private long lastTaxDay = -1;
	/** The berries the village has found (ROADMAP 28.9, the Berry Breeder's book): none by default. */
	private final java.util.Set<net.minecraft.resources.ResourceLocation> berriesFound = new java.util.LinkedHashSet<>();
	/** The builds this village's Blueprint Table has unlocked (26.10, {@code table/BlueprintUnlocks}), by unstyled id. */
	private final java.util.Set<net.minecraft.resources.ResourceLocation> blueprintUnlocks = new java.util.LinkedHashSet<>();
	/** Whether the unlocks were seeded with what stands (false in a save from before 26.10: seeded on first use). */
	private boolean blueprintUnlocksSeeded;
	/** The day of the last market (see {@link MarketDays}). */
	private long lastMarketDay = -1;
	/** What happened in the village, oldest first (see {@link Chronicle}). */
	private java.util.List<Chronicle.Entry> chronicle = new java.util.ArrayList<>();
	/** The village's research (see {@code research/Research}). */
	private io.github.jcondedata.aliveworkplace.research.Research.State research = io.github.jcondedata.aliveworkplace.research.Research.State.EMPTY;
	/** The edicts in force, oldest first (see {@link Edicts}). */
	private java.util.List<Edicts.InForce> edicts = java.util.List.of();
	/** The village's chartered guilds (30.17, {@link Guilds}), oldest first. */
	private java.util.List<Guilds.Charter> guilds = java.util.List.of();
	/** Each edict's reform progress, also of edicts lifted since (see {@link Reforms}). */
	private java.util.List<Reforms.Progress> reforms = java.util.List.of();
	/** Their effects summed (not saved: summed again after a load, a data pack reload or the switch). */
	@Nullable
	private CivicEffects.Sum civic;
	private java.util.List<CivicEffects.Active> civicExtra = java.util.List.of();
	private int civicGeneration;
	/** Free Bread's running share of an extra meal (30.6), in hundredths (0 to 99); saved as {@code extraMeals}, 0 to 1. */
	private int extraMeals;
	/** Conscription (30.10): the game time work may start again after a raid (noon the next day); saved as {@code raidWorkUntil}, 0: none. */
	private long raidWorkUntil;
	/** The Work Horn (30.11): the day it was last blown here (-1: never) and the game time the rush ends (0: none). */
	private long hornDay = -1;
	private long rushUntil;

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
		if (hall.rushUntil > 0 && level instanceof net.minecraft.server.level.ServerLevel server) {
			WorkHorn.tick(server, pos, hall);
		}
		if (!hall.stewardPlaceChecked && level instanceof net.minecraft.server.level.ServerLevel server) {
			io.github.jcondedata.aliveworkplace.city.Stewards.fixTicket(server, pos);
			hall.stewardPlaceChecked = true;
			hall.setChanged();
		}
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			io.github.jcondedata.aliveworkplace.wonder.ObservatoryPowers.tick(server, pos, hall); // the Sky Chart's search (35.18)
		}
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			long cost = io.github.jcondedata.aliveworkplace.city.StewardCost.start(); // 27.22: counted as the Steward's work
			try {
				io.github.jcondedata.aliveworkplace.city.Roads.tick(server, pos, hall); // 27.15
				io.github.jcondedata.aliveworkplace.city.Walls.tick(server, pos, hall); // 27.18
			} finally {
				io.github.jcondedata.aliveworkplace.city.StewardCost.stop(cost, "roads and walls");
			}
		}
		if (level instanceof net.minecraft.server.level.ServerLevel server
			&& (hall.needs == null || Math.floorMod(level.getGameTime() + pos.hashCode(), VillageNeeds.CHECK_EVERY) == 0)) {
			hall.needs = VillageNeeds.check(server, pos);
			VillageQuests.tick(server, pos, hall);
			MarketDays.tick(server, pos, hall);
			io.github.jcondedata.aliveworkplace.camp.CampBeds.settle(server, pos); // the settlers' wagon is no home once the village stands (B207)
			VillageHalls.Census census = VillageHalls.census(server, pos);
			VillageRanks.round(server, pos, hall, census.villagers());
			Guilds.round(server, pos, hall);
			io.github.jcondedata.aliveworkplace.realm.Realms.round(server, pos, hall, census.villagers()); // what the realm's tab shows of it, and the realm's rank (33.11)
			io.github.jcondedata.aliveworkplace.trainer.LeaderBracelets.round(server, pos, census.workers()); // has it a Trainer Leader (38.6)
			io.github.jcondedata.aliveworkplace.realm.Leaderboard.round(server, pos, hall, census); // the week's wellbeing, Legends and Masters (33.19)
			Caravans.round(server, pos, census);
			io.github.jcondedata.aliveworkplace.trade.Economy.round(server, pos, census); // known for, short of, prices (33.2): after the caravans' list is up to date
			io.github.jcondedata.aliveworkplace.guard.Gates.round(server, pos, census.guards());
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.round(server, pos);
			io.github.jcondedata.aliveworkplace.bounty.Bounties.round(server, pos); // bounties posted and taken down (31.13)
			Festivals.round(server, pos, hall, census.villagers());
			io.github.jcondedata.aliveworkplace.cup.Cups.round(server, pos, hall); // 28.17: after the caravans' list is up to date
			io.github.jcondedata.aliveworkplace.legend.LegendSlots.round(server, pos);
			io.github.jcondedata.aliveworkplace.legend.LegendGuests.round(server, pos);
			io.github.jcondedata.aliveworkplace.legend.LegendNeeds.round(server, pos);
			io.github.jcondedata.aliveworkplace.legend.StrangeMoods.round(server, pos);
			io.github.jcondedata.aliveworkplace.people.Couples.round(server, pos);
			io.github.jcondedata.aliveworkplace.wonder.powers.UndertakersPower.morning(server, pos); // the night's dead back by morning (35.10)
			io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries.morning(server, pos); // the Great Wall's lost sentries replaced (35.16)
			io.github.jcondedata.aliveworkplace.people.ClassMigration.round(server, pos, hall); // classes switched on: 16 seeded a round (34.22)
			io.github.jcondedata.aliveworkplace.people.FamilyBook.round(server, pos, hall); // family names and the Book of Families (34.21)
			Services.round(server, pos, hall, census.workers()); // the day's service list (34.3), before the class check reads it
			io.github.jcondedata.aliveworkplace.people.SocialClasses.round(server, pos, hall); // the dawn class check (34.2)
			io.github.jcondedata.aliveworkplace.city.Signposts.round(server, pos, hall); // the signposts' boards brought up to date (36.4)
			VillageWaystones.round(server, pos); // the plaza's waystone a working one, with the village's name (41.1)
			VillageMoments.catchUp(server, pos, hall, java.util.stream.Stream.concat(census.workers().stream(), census.jobless().stream()).toList()); // moments passed before they were moments (36.2)
			if (Treasury.ENABLED) {
				Treasury.round(server, pos, hall, census.workers(), census.jobless()); // by class (34.7)
			}
			// Happy days counted, and an elder whose time has come passes in the night (34.19, 34.19a).
			java.util.List<net.minecraft.world.entity.npc.Villager> grown = java.util.stream.Stream.concat(census.workers().stream(), census.jobless().stream()).toList();
			io.github.jcondedata.aliveworkplace.people.LifeStages.round(server, pos, grown);
			io.github.jcondedata.aliveworkplace.people.Retirement.round(server, pos, grown); // retirement and apprentices (34.20)
			PlayerBank.round(server, pos, hall);
			TradeFairs.round(server, pos, hall);
			Banquets.round(server, pos, hall);
			Anthems.round(server, pos, hall);
			io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.round(server, pos, hall); // the village Pokédex (29.21)
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.tick(server, pos, census.villagers(), census.guards(), hall.lastRaidDay, day -> {
				hall.lastRaidDay = day;
				hall.setChanged();
			});
			io.github.jcondedata.aliveworkplace.guard.Warnings.round(server, pos, census.villagers(), census.guards()); // a day's warning (32.14)
			io.github.jcondedata.aliveworkplace.threat.Disasters.round(server, pos, hall, census); // the disaster engine (32.15)
			Conscription.round(server, pos, hall);
			Cradles.round(server, pos, VillageNeeds.CHECK_EVERY);
			if (VillageGrowth.grow(server, pos, hall.needs, hall.lastBirth) != null) {
				hall.lastBirth = level.getGameTime();
				hall.births++;
				hall.setChanged();
			}
		}
	}

	/** The village's colours (30.13), set with a Village Banner; null (none) in halls saved before. */
	@org.jetbrains.annotations.Nullable
	private VillageBanners.Colours colours;

	@org.jetbrains.annotations.Nullable
	public VillageBanners.Colours colours() {
		return colours;
	}

	public void setColours(@org.jetbrains.annotations.Nullable VillageBanners.Colours colours) {
		this.colours = colours;
		setChanged();
	}

	/** The chronicle, oldest first. */
	public java.util.List<Chronicle.Entry> chronicle() {
		return java.util.List.copyOf(chronicle);
	}

	/** The Book of Families (34.21): everyone who has lived here, up to 400; empty in halls saved before. */
	private final io.github.jcondedata.aliveworkplace.people.FamilyBook.Book families = new io.github.jcondedata.aliveworkplace.people.FamilyBook.Book();

	public io.github.jcondedata.aliveworkplace.people.FamilyBook.Book families() {
		return families;
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

	/** Looks the village's leader chose for its houses (23.10a); none in halls saved before. */
	private java.util.List<PieceLooks.Choice> pieceLooks = java.util.List.of();

	public java.util.List<PieceLooks.Choice> pieceLooks() {
		return pieceLooks;
	}

	public void setPieceLooks(java.util.List<PieceLooks.Choice> choices) {
		pieceLooks = java.util.List.copyOf(choices);
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

	/** The disaster under way or foretold (32.15), or null. */
	@org.jetbrains.annotations.Nullable
	public net.minecraft.resources.ResourceLocation disaster() {
		return disaster;
	}

	/** The day the disaster begins or began; -1 without one. */
	public long disasterSince() {
		return disasterSince;
	}

	/** The disaster's last day; -1: until rain. */
	public long disasterEnds() {
		return disasterEnds;
	}

	/** The day the last disaster ended; -100 if never. */
	public long lastDisasterDay() {
		return lastDisasterDay;
	}

	/** Sets the disaster under way ({@link io.github.jcondedata.aliveworkplace.threat.Disasters} only). */
	public void setDisaster(@org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation id, long since, long ends, long lastDay) {
		this.disaster = id;
		this.disasterSince = id == null ? -1 : since;
		this.disasterEnds = id == null ? -1 : ends;
		this.lastDisasterDay = lastDay;
		setChanged();
	}

	public io.github.jcondedata.aliveworkplace.research.Research.State research() {
		return research;
	}

	public void setResearch(io.github.jcondedata.aliveworkplace.research.Research.State research) {
		int before = this.research.totalLevels();
		this.research = research;
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			VillageMoments.researched(server, worldPosition, before, research.totalLevels()); // 3 and 7 levels done (36.2)
		}
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

	/** The village's chartered guilds, oldest first (30.17). */
	public java.util.List<Guilds.Charter> guilds() {
		return guilds;
	}

	/** Sets the chartered guilds (charters, founding and succession go through {@link Guilds}). */
	public void setGuilds(java.util.List<Guilds.Charter> guilds) {
		this.guilds = java.util.List.copyOf(guilds);
		setChanged();
	}

	/** The effects of the edicts in force, summed once and kept until they change. */
	public CivicEffects.Sum civicEffects() {
		CivicEffects.Sum sum = civic;
		int generation = Edicts.generation();
		java.util.List<CivicEffects.Active> extra = level instanceof net.minecraft.server.level.ServerLevel server
			? CivicEffects.EXTRA.at(server, worldPosition) : java.util.List.of();
		if (sum == null || civicGeneration != generation || extra != civicExtra) {
			java.util.Set<String> reformed = new java.util.HashSet<>();
			reforms.stream().filter(Reforms.Progress::reformed).forEach(p -> reformed.add(p.id()));
			sum = Edicts.sum(edicts, reformed);
			if (!extra.isEmpty()) {
				// the realm's research counts with the edicts (33.15)
				java.util.List<CivicEffects.Active> all = new java.util.ArrayList<>(sum.all());
				all.addAll(extra);
				sum = new CivicEffects.Sum(all);
			}
			civic = sum;
			civicGeneration = generation;
			civicExtra = extra;
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

	/** The day (by {@link Chronicle#day}) the Work Horn was last blown in the village; -1 if never. */
	public long hornDay() {
		return hornDay;
	}

	/** The game time the village's rush ends (0: no rush yet). */
	public long rushUntil() {
		return rushUntil;
	}

	/** A rush called on {@code day}, until {@code until} (game time); see {@link WorkHorn}. */
	public void startRush(long day, long until) {
		hornDay = day;
		rushUntil = Math.max(0, until);
		setChanged();
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

	public void questDone() {
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

	/** The later festival still to come after the next one (see {@link Festivals#before}); -1: none. */
	public long festivalAfter() {
		return festivalAfter;
	}

	public void setFestivalAfter(long day) {
		festivalAfter = day;
		setChanged();
	}

	public long feastDay() {
		return feastDay;
	}

	public void setFeastDay(long day) {
		feastDay = day;
		setChanged();
	}

	/** The village's own Habitat Block (ROADMAP 28.14), or null if it has none. */
	@org.jetbrains.annotations.Nullable
	public net.minecraft.core.BlockPos habitat() {
		return habitat;
	}

	public void setHabitat(@org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos pos) {
		habitat = pos == null ? null : pos.immutable();
		setChanged();
	}

	public boolean ballTurn() {
		return ballTurn;
	}

	public int classesVersion() {
		return classesVersion;
	}

	public long classGraceUntil() {
		return classGraceUntil;
	}

	/** Classes seeded (34.22) at {@code version}, settling in until {@code graceUntil}; version 0 seeds them again. */
	public void setClasses(int version, long graceUntil) {
		classesVersion = version;
		classGraceUntil = graceUntil;
		setChanged();
	}

	public void setBallTurn(boolean turn) {
		ballTurn = turn;
		setChanged();
	}

	public long ballDay() {
		return ballDay;
	}

	public void setBallDay(long day) {
		ballDay = day;
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

	/** Whether the owner set the village At peace (32.21): no raids, lairs or disasters come. */
	public boolean atPeace() {
		return atPeace;
	}

	/** The day the village was set At peace; -1 when it isn't. */
	public long peaceSince() {
		return peaceSince;
	}

	/** Sets the village At peace (since {@code day}) or not. */
	public void setAtPeace(boolean on, long day) {
		atPeace = on;
		peaceSince = on ? day : -1;
		setChanged();
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
	/** The players' deposits on the hall's bank page (29.17). */
	public PlayerBank.State playerBank() {
		return playerBank;
	}

	public long fairDay() {
		return fairDay;
	}

	/** The village's last wedding a Wonder blessed (35.10), whose mood everyone has for its days; null: none (and in halls saved before). */
	@org.jetbrains.annotations.Nullable
	private io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower.VillageBlessing wonderWedding;

	@org.jetbrains.annotations.Nullable
	public io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower.VillageBlessing wonderWedding() {
		return wonderWedding;
	}

	public void setWonderWedding(@org.jetbrains.annotations.Nullable io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower.VillageBlessing blessing) {
		wonderWedding = blessing;
		setChanged();
	}

	/** The day of the village's last banquet (29.18), -1 before the first. */
	public long banquetDay() {
		return banquetDay;
	}

	/** Whether that banquet's feast has been eaten (the village gathers first, then eats). */
	public boolean banquetEaten() {
		return banquetEaten;
	}

	/** The village's anthem (29.19), once a Bard Laureate has composed it. */
	public java.util.Optional<Anthems.Anthem> anthem() {
		return java.util.Optional.ofNullable(anthem);
	}

	public void setAnthem(@Nullable Anthems.Anthem anthem) {
		this.anthem = anthem;
		setChanged();
	}

	public boolean anthemBookGiven() {
		return anthemBookGiven;
	}

	public void setAnthemBookGiven(boolean given) {
		anthemBookGiven = given;
		setChanged();
	}

	public void setBanquet(long day, boolean eaten) {
		banquetDay = day;
		banquetEaten = eaten;
		setChanged();
	}

	public void setFairDay(long day) {
		fairDay = day;
		setChanged();
	}

	/** The banners of the trade fair's bunting still up (29.17); changed through {@link TradeFairs} only. */
	public java.util.List<BlockPos> fairBunting() {
		return java.util.Collections.unmodifiableList(fairBunting);
	}

	void setFairBunting(java.util.Collection<BlockPos> bunting) {
		fairBunting.clear();
		fairBunting.addAll(bunting);
		setChanged();
	}

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

	/** The village's Legend guests (29.8). */
	public io.github.jcondedata.aliveworkplace.legend.LegendGuests.State legendGuests() {
		return legendGuests;
	}

	public void setLegendGuests(io.github.jcondedata.aliveworkplace.legend.LegendGuests.State state) {
		legendGuests = state;
		setChanged();
	}

	/** The Observatory's search for the Sky Chart (35.18), and what it found. */
	public io.github.jcondedata.aliveworkplace.wonder.SkyChart.Search skyChart() {
		return skyChart;
	}

	/** The Seer's last dawn foretelling here (29.16). */
	public io.github.jcondedata.aliveworkplace.legend.Seer.State seer() {
		return seer;
	}

	public void setSeer(io.github.jcondedata.aliveworkplace.legend.Seer.State state) {
		seer = state;
		setChanged();
	}

	/** The species in the village Pokédex (29.21), in the order they were logged. */
	public java.util.Set<String> pokedex() {
		return java.util.Collections.unmodifiableSet(pokedex);
	}

	/** Logs {@code species} in the village Pokédex; returns the ones that were new, each logged once. */
	public java.util.List<String> logPokedex(java.util.Collection<String> species) {
		java.util.List<String> added = new java.util.ArrayList<>();
		for (String s : species) {
			if (pokedex.add(s)) {
				added.add(s);
			}
		}
		if (!added.isEmpty()) {
			setChanged();
		}
		return added;
	}

	/** The day the Founder's mood came (0: not yet). */
	public long founderMoodDay() {
		return founderMoodDay;
	}

	public void setFounderMoodDay(long day) {
		founderMoodDay = day;
		setChanged();
	}

	/** The Masters whose Founder's mood came and failed (29.23), in order. */
	public java.util.List<java.util.UUID> founderTried() {
		java.util.List<java.util.UUID> out = new java.util.ArrayList<>();
		for (String s : founderTried.split(",")) {
			try {
				if (!s.isEmpty()) {
					out.add(java.util.UUID.fromString(s));
				}
			} catch (IllegalArgumentException ignored) {
				// a damaged entry: skipped
			}
		}
		return out;
	}

	public void addFounderTried(java.util.UUID who) {
		if (!founderTried().contains(who)) {
			founderTried = founderTried.isEmpty() ? who.toString() : founderTried + "," + who;
			setChanged();
		}
	}

	/** Whether the village's Founder's mood ended in a Founder (then it never comes again). */
	public boolean founderMade() {
		return founderMade;
	}

	public void setFounderMade(boolean made) {
		founderMade = made;
		setChanged();
	}

	/** The Chronicle day the Founder last gave a Founder's Wagon (0: never). */
	public long founderWagonDay() {
		return founderWagonDay;
	}

	public void setFounderWagonDay(long day) {
		founderWagonDay = day;
		setChanged();
	}

	/** The day strange moods may come again after one failed (0: any day). */
	public long noMoodUntil() {
		return noMoodUntil;
	}

	public void setNoMoodUntil(long day) {
		noMoodUntil = day;
		setChanged();
	}

	/** The Chronicle day the village last rolled for a strange mood (0: never). */
	public long moodRolledDay() {
		return moodRolledDay;
	}

	public void setMoodRolledDay(long day) {
		moodRolledDay = day;
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

	/** Where the village's services were at the last daily count (34.3); empty in halls saved before. */
	private java.util.List<Services.Found> services = java.util.List.of();
	/** The day ({@link Chronicle#day}) the service list was worked out; 0 never. */
	private long servicesDay;

	public java.util.List<Services.Found> services() {
		return services;
	}

	public long servicesDay() {
		return servicesDay;
	}

	/** Keeps {@code found} as the service list worked out on {@code day}. */
	public void setServices(java.util.List<Services.Found> found, long day) {
		services = java.util.List.copyOf(found);
		servicesDay = day;
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
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			VillageWaystones.renamed(server, worldPosition); // the village's waystones take the new name (41.1)
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		name = Nbt.has(tag, "CustomName", 8) ? parseCustomNameSafe(Nbt.getString(tag, "CustomName"), registries) : null;
		lastBirth = Nbt.getLong(tag, "lastBirth");
		births = Nbt.getInt(tag, "births");
		colours = null;
		if (Nbt.has(tag, "bannerBase", 8)) {
			net.minecraft.world.item.DyeColor base = net.minecraft.world.item.DyeColor.byName(Nbt.getString(tag, "bannerBase"), net.minecraft.world.item.DyeColor.WHITE);
			net.minecraft.world.level.block.entity.BannerPatternLayers patterns = tag.contains("bannerPatterns")
				? net.minecraft.world.level.block.entity.BannerPatternLayers.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("bannerPatterns"))
					.result().orElse(net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY)
				: net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY;
			colours = new VillageBanners.Colours(base, patterns);
		}
		quests = java.util.List.copyOf(VillageQuests.readQuests(tag.get("quests")));
		lastQuestDay = tag.contains("lastQuestDay") ? Nbt.getLong(tag, "lastQuestDay") : -1;
		questsDone = Nbt.getInt(tag, "questsDone");
		lastMarketDay = tag.contains("lastMarketDay") ? Nbt.getLong(tag, "lastMarketDay") : -1;
		lastRaidDay = tag.contains("lastRaidDay") ? Nbt.getLong(tag, "lastRaidDay") : -100;
		disaster = tag.contains("disaster") ? net.minecraft.resources.ResourceLocation.tryParse(Nbt.getString(tag, "disaster")) : null;
		disasterSince = disaster != null && tag.contains("disasterSince") ? Nbt.getLong(tag, "disasterSince") : -1;
		disasterEnds = disaster != null && tag.contains("disasterEnds") ? Nbt.getLong(tag, "disasterEnds") : -1;
		lastDisasterDay = tag.contains("lastDisasterDay") ? Nbt.getLong(tag, "lastDisasterDay") : -100;
		festivalDay = tag.contains("festivalDay") ? Nbt.getLong(tag, "festivalDay") : -1;
		festivalAfter = tag.contains("festivalAfter") ? Nbt.getLong(tag, "festivalAfter") : -1;
		feastDay = tag.contains("feastDay") ? Nbt.getLong(tag, "feastDay") : -1;
		festivalCalled = tag.contains("festivalCalled") ? Nbt.getLong(tag, "festivalCalled") : -100;
		ballTurn = tag.contains("ballTurn") && Nbt.getBoolean(tag, "ballTurn");
		classesVersion = Nbt.getInt(tag, "classesVersion");
		classGraceUntil = Nbt.getLong(tag, "classGraceUntil");
		families.load(tag, registries);
		ballDay = tag.contains("ballDay") ? Nbt.getLong(tag, "ballDay") : -100;
		habitat = tag.contains("habitat") ? net.minecraft.core.BlockPos.of(Nbt.getLong(tag, "habitat")) : null;
		festivalMissed = tag.contains("festivalMissed") ? Nbt.getLong(tag, "festivalMissed") : -1;
		treasury = Nbt.getInt(tag, "treasury");
		treasuryTotal = Nbt.getLong(tag, "treasuryTotal");
		playerBank = PlayerBank.State.load(tag);
		fairDay = tag.contains("fairDay") ? Nbt.getLong(tag, "fairDay") : -1;
		banquetDay = tag.contains("banquetDay") ? Nbt.getLong(tag, "banquetDay") : -1;
		banquetEaten = Nbt.getBoolean(tag, "banquetEaten");
		wonderWedding = tag.contains("wonderWedding") ? io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower.VillageBlessing.CODEC
			.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("wonderWedding")).result().orElse(null) : null;
		String instrument = Nbt.getString(tag, "anthemInstrument");
		int[] notes = Nbt.getIntArray(tag, "anthemNotes");
		anthem = !instrument.isEmpty() && notes.length > 0 ? new Anthems.Anthem(instrument, notes) : null;
		anthemBookGiven = Nbt.getBoolean(tag, "anthemBook");
		fairBunting.clear();
		for (long p : Nbt.getLongArray(tag, "fairBunting")) {
			fairBunting.add(BlockPos.of(p));
		}
		festivalCrowd = Nbt.getInt(tag, "festivalCrowd");
		legendGuests = io.github.jcondedata.aliveworkplace.legend.LegendGuests.State.load(tag);
		seer = io.github.jcondedata.aliveworkplace.legend.Seer.State.load(tag);
		skyChart = tag.contains("skyChart") ? io.github.jcondedata.aliveworkplace.wonder.SkyChart.Search.load(tag.getCompound("skyChart"))
			: new io.github.jcondedata.aliveworkplace.wonder.SkyChart.Search();
		pokedex.clear();
		net.minecraft.nbt.ListTag dex = Nbt.getList(tag, "pokedex", net.minecraft.nbt.Tag.TAG_STRING);
		for (int i = 0; i < dex.size(); i++) {
			pokedex.add(Nbt.stringAt(dex, i));
		}
		founderMoodDay = Nbt.getLong(tag, "founderMoodDay");
		founderTried = Nbt.getString(tag, "founderTried");
		founderMade = Nbt.getBoolean(tag, "founderMade");
		founderWagonDay = Nbt.getLong(tag, "founderWagonDay");
		noMoodUntil = Nbt.getLong(tag, "noMoodUntil");
		moodRolledDay = Nbt.getLong(tag, "moodRolledDay");
		owner = Nbt.hasUuid(tag, "owner") ? Nbt.getUuid(tag, "owner") : null;
		ownerName = Nbt.getString(tag, "ownerName");
		protectedVillage = Nbt.getBoolean(tag, "protected");
		atPeace = Nbt.getBoolean(tag, "atPeace");
		peaceSince = atPeace && tag.contains("peaceSince") ? Nbt.getLong(tag, "peaceSince") : -1;
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
		guilds = Guilds.Charter.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("guilds"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		reforms = Reforms.Progress.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("reforms"))
			.result().map(java.util.List::copyOf).orElse(java.util.List.of());
		civic = null;
		extraMeals = Math.max(0, Math.min(99, Math.round(Nbt.getFloat(tag, "extraMeals") * 100f)));
		raidWorkUntil = Math.max(0, Nbt.getLong(tag, "raidWorkUntil"));
		hornDay = tag.contains("hornDay") ? Nbt.getLong(tag, "hornDay") : -1;
		rushUntil = Math.max(0, Nbt.getLong(tag, "rushUntil"));
		services = !tag.contains("services") ? java.util.List.of()
			: Services.Found.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("services")).result().map(java.util.List::copyOf)
				.orElse(java.util.List.of());
		servicesDay = Nbt.getLong(tag, "servicesDay");
		stewardWishes = !tag.contains("steward") ? io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY
			: io.github.jcondedata.aliveworkplace.city.StewardWishes.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("steward"))
				.result().orElse(io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY);
		stewardDesk = !tag.contains("steward_desk") ? io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY
			: io.github.jcondedata.aliveworkplace.city.StewardDesk.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("steward_desk")).result().orElse(io.github.jcondedata.aliveworkplace.city.StewardDesk.State.EMPTY);
		pieceLooks = !tag.contains("piece_looks") ? java.util.List.of()
			: PieceLooks.Choice.CODEC.listOf().parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("piece_looks")).result().map(java.util.List::copyOf)
				.orElse(java.util.List.of());
		berriesFound.clear();
		net.minecraft.nbt.ListTag berries = Nbt.getList(tag, "berriesFound", net.minecraft.nbt.Tag.TAG_STRING);
		for (int i = 0; i < berries.size(); i++) {
			net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(Nbt.stringAt(berries, i));
			if (id != null) {
				berriesFound.add(id);
			}
		}
		blueprintUnlocks.clear();
		net.minecraft.nbt.ListTag unlocks = Nbt.getList(tag, "blueprintUnlocks", net.minecraft.nbt.Tag.TAG_STRING);
		for (int i = 0; i < unlocks.size(); i++) {
			net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(Nbt.stringAt(unlocks, i));
			if (id != null) {
				blueprintUnlocks.add(id);
			}
		}
		blueprintUnlocksSeeded = Nbt.getBoolean(tag, "blueprintUnlocksSeeded");
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
		if (servicesDay != 0) {
			Services.Found.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, services).result().ifPresent(t -> tag.put("services", t));
			tag.putLong("servicesDay", servicesDay);
		}
		tag.putInt("births", births);
		if (colours != null) {
			tag.putString("bannerBase", colours.base().getName());
			net.minecraft.world.level.block.entity.BannerPatternLayers.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), colours.patterns())
				.result().ifPresent(t -> tag.put("bannerPatterns", t));
		}
		VillageQuests.Quest.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, quests).result().ifPresent(t -> tag.put("quests", t));
		tag.putLong("lastQuestDay", lastQuestDay);
		tag.putInt("questsDone", questsDone);
		tag.putLong("lastMarketDay", lastMarketDay);
		tag.putLong("lastRaidDay", lastRaidDay);
		if (disaster != null) {
			tag.putString("disaster", disaster.toString());
			tag.putLong("disasterSince", disasterSince);
			if (disasterEnds >= 0) {
				tag.putLong("disasterEnds", disasterEnds);
			}
		}
		if (lastDisasterDay != -100) {
			tag.putLong("lastDisasterDay", lastDisasterDay);
		}
		tag.putLong("festivalDay", festivalDay);
		tag.putLong("festivalAfter", festivalAfter);
		tag.putLong("feastDay", feastDay);
		tag.putLong("festivalCalled", festivalCalled);
		tag.putBoolean("ballTurn", ballTurn);
		tag.putInt("classesVersion", classesVersion);
		tag.putLong("classGraceUntil", classGraceUntil);
		families.save(tag, registries);
		tag.putLong("ballDay", ballDay);
		if (habitat != null) {
			tag.putLong("habitat", habitat.asLong());
		}
		tag.putLong("festivalMissed", festivalMissed);
		tag.putInt("treasury", treasury);
		tag.putLong("treasuryTotal", treasuryTotal);
		playerBank.save(tag);
		tag.putLong("fairDay", fairDay);
		tag.putLong("banquetDay", banquetDay);
		tag.putBoolean("banquetEaten", banquetEaten);
		if (wonderWedding != null) {
			io.github.jcondedata.aliveworkplace.wonder.powers.WeddingsPower.VillageBlessing.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, wonderWedding)
				.result().ifPresent(t -> tag.put("wonderWedding", t));
		}
		if (anthem != null) {
			tag.putString("anthemInstrument", anthem.instrument());
			tag.putIntArray("anthemNotes", anthem.notes());
			tag.putBoolean("anthemBook", anthemBookGiven);
		}
		if (!fairBunting.isEmpty()) {
			tag.put("fairBunting", new net.minecraft.nbt.LongArrayTag(fairBunting.stream().mapToLong(BlockPos::asLong).toArray()));
		}
		tag.putInt("festivalCrowd", festivalCrowd);
		legendGuests.save(tag);
		seer.save(tag);
		if (skyChart.week() >= 0 || !skyChart.found().isEmpty()) {
			tag.put("skyChart", skyChart.save());
		}
		if (!pokedex.isEmpty()) {
			net.minecraft.nbt.ListTag dex = new net.minecraft.nbt.ListTag();
			pokedex.forEach(sp -> dex.add(net.minecraft.nbt.StringTag.valueOf(sp)));
			tag.put("pokedex", dex);
		}
		tag.putLong("founderMoodDay", founderMoodDay);
		tag.putString("founderTried", founderTried);
		tag.putBoolean("founderMade", founderMade);
		tag.putLong("founderWagonDay", founderWagonDay);
		tag.putLong("noMoodUntil", noMoodUntil);
		tag.putLong("moodRolledDay", moodRolledDay);
		if (owner != null) {
			Nbt.putUuid(tag, "owner", owner);
			tag.putString("ownerName", ownerName);
		}
		tag.putBoolean("protected", protectedVillage);
		if (atPeace) {
			tag.putBoolean("atPeace", true);
			tag.putLong("peaceSince", peaceSince);
		}
		tag.putBoolean("stewardPlaceChecked", stewardPlaceChecked);
		if (!plan.isEmpty() || plan.mode() != io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.ASK) {
			io.github.jcondedata.aliveworkplace.city.CityPlan.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, plan).result().ifPresent(t -> tag.put("plan", t));
		}
		tag.putLong("lastTaxDay", lastTaxDay);
		tag.putInt("rank", rank.ordinal());
		io.github.jcondedata.aliveworkplace.research.Research.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, research).result()
			.ifPresent(t -> tag.put("research", t));
		if (!guilds.isEmpty()) {
			Guilds.Charter.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, guilds).result().ifPresent(t -> tag.put("guilds", t));
		}
		if (!edicts.isEmpty()) {
			Edicts.InForce.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, edicts).result().ifPresent(t -> tag.put("edicts", t));
		}
		if (!stewardWishes.equals(io.github.jcondedata.aliveworkplace.city.StewardWishes.State.EMPTY)) {
			io.github.jcondedata.aliveworkplace.city.StewardWishes.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, stewardWishes).result()
				.ifPresent(t -> tag.put("steward", t));
		}
		if (!pieceLooks.isEmpty()) {
			PieceLooks.Choice.CODEC.listOf().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pieceLooks).result().ifPresent(t -> tag.put("piece_looks", t));
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
		if (hornDay >= 0) {
			tag.putLong("hornDay", hornDay);
			tag.putLong("rushUntil", rushUntil);
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
		net.minecraft.nbt.ListTag unlocks = new net.minecraft.nbt.ListTag();
		blueprintUnlocks.forEach(id -> unlocks.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
		tag.put("blueprintUnlocks", unlocks);
		tag.putBoolean("blueprintUnlocksSeeded", blueprintUnlocksSeeded);
	}

	/** The builds the village's Blueprint Table has unlocked (26.10), seeded or not; {@code table/BlueprintUnlocks} reads them. */
	public java.util.Set<net.minecraft.resources.ResourceLocation> blueprintUnlocks() {
		return java.util.Collections.unmodifiableSet(blueprintUnlocks);
	}

	public boolean blueprintUnlocksSeeded() {
		return blueprintUnlocksSeeded;
	}

	/** Unlocks a build in the village's library; whether it's new. */
	public boolean unlockBlueprint(net.minecraft.resources.ResourceLocation id) {
		boolean added = blueprintUnlocks.add(id);
		if (added) {
			setChanged();
		}
		return added;
	}

	/** The first use of the unlocks (26.10): what already stands, is being built or starts unlocked. */
	public void seedBlueprintUnlocks(java.util.Collection<net.minecraft.resources.ResourceLocation> ids) {
		blueprintUnlocks.addAll(ids);
		blueprintUnlocksSeeded = true;
		setChanged();
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
