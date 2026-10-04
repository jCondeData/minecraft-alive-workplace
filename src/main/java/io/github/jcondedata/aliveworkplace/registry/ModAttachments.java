package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.platform.Attachment;

/** Extra data we store on vanilla villagers (saved with the entity; see {@link Attachment}). */
public final class ModAttachments {
	/** Which build site this villager is working on. */
	public static final Attachment<BuilderJob> BUILDER_JOB = Attachment.saved("builder_job", BuilderJob.CODEC);

	/** The builder's tool bag: materials fetched from the supply chests, and rubble from clearing. */
	public static final Attachment<BuilderBag> BUILDER_BAG = Attachment.saved("builder_bag", BuilderBag.CODEC, BuilderBag::new);

	/** Which quarry a miner is digging. */
	public static final Attachment<BuilderJob> MINER_JOB = Attachment.saved("miner_job", BuilderJob.CODEC);

	/** How many trees a lumberjack has cut down (shown above its head). */
	public static final Attachment<Integer> TREES_FELLED = Attachment.saved("trees_felled", com.mojang.serialization.Codec.INT);

	/** The tree farm a lumberjack keeps planted (a Field Marker's area; saved on the villager). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.farm.FieldJob> TREE_FARM = Attachment.saved("tree_farm", io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC);

	/** The orchard an Orchard Keeper keeps planted (a Field Marker's area; saved on the villager). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.farm.FieldJob> ORCHARD = Attachment.saved("orchard", io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC);

	/** How many saplings a lumberjack has planted on their tree farm (or seeds an orchard keeper in their orchard). */
	public static final Attachment<Integer> SAPLINGS_PLANTED = Attachment.saved("saplings_planted", com.mojang.serialization.Codec.INT);

	/** The balls a Ball Smith has been asked to make (item ids; none: anything they can). */
	public static final Attachment<java.util.List<net.minecraft.resources.ResourceLocation>> BALL_ORDERS = Attachment.saved("ball_orders", net.minecraft.resources.ResourceLocation.CODEC.listOf());

	/** How many Poké Balls a Ball Smith has made (shown above its head). */
	public static final Attachment<Integer> BALLS_MADE = Attachment.saved("balls_made", com.mojang.serialization.Codec.INT);

	/** How many things a Carpenter or Mason has made for the builders (shown above its head). */
	public static final Attachment<Integer> ITEMS_CRAFTED = Attachment.saved("items_crafted", com.mojang.serialization.Codec.INT);

	/** The fossils a Fossil Scientist is reviving (and finished ones waiting for their owners). */
	public static final Attachment<java.util.List<io.github.jcondedata.aliveworkplace.fossil.Revival>> FOSSIL_REVIVALS = Attachment.saved("fossil_revivals", io.github.jcondedata.aliveworkplace.fossil.Revival.CODEC.listOf());

	/** How many fossils a Fossil Scientist has revived (shown above its head). */
	public static final Attachment<Integer> FOSSILS_REVIVED = Attachment.saved("fossils_revived", com.mojang.serialization.Codec.INT);

	/** How many ingots an Armorer has smelted (shown above its head). */
	public static final Attachment<Integer> INGOTS_SMELTED = Attachment.saved("ingots_smelted", com.mojang.serialization.Codec.INT);

	/** How many pieces of armor an Armorer has made for the guards. */
	public static final Attachment<Integer> ARMOR_MADE = Attachment.saved("armor_made", com.mojang.serialization.Codec.INT);

	/** How many pieces a Weaponsmith has mended (shown above its head). */
	public static final Attachment<Integer> ITEMS_MENDED = Attachment.saved("items_mended", com.mojang.serialization.Codec.INT);

	/** How many animals a Shepherd has sheared (shown above its head). */
	public static final Attachment<Integer> ANIMALS_SHEARED = Attachment.saved("animals_sheared", com.mojang.serialization.Codec.INT);

	/** How many buckets of milk a Butcher has collected (shown above its head). */
	public static final Attachment<Integer> MILK_COLLECTED = Attachment.saved("milk_collected", com.mojang.serialization.Codec.INT);

	/** How many potions a Cleric has brewed (shown above its head). */
	public static final Attachment<Integer> POTIONS_BREWED = Attachment.saved("potions_brewed", com.mojang.serialization.Codec.INT);

	/** How many pieces of gear a Librarian has enchanted (shown above its head). */
	public static final Attachment<Integer> ITEMS_ENCHANTED = Attachment.saved("items_enchanted", com.mojang.serialization.Codec.INT);

	/** The ground a builder is to turn into path (planned when they finished a building; see build/Paths). */
	public static final Attachment<java.util.List<net.minecraft.core.BlockPos>> PATH = Attachment.saved("path", net.minecraft.core.BlockPos.CODEC.listOf());

	/** How many research levels a Scholar has finished (shown above its head). */
	public static final Attachment<Integer> RESEARCH_DONE = Attachment.saved("research_done", com.mojang.serialization.Codec.INT);

	/** How many villagers an Undertaker has brought back (shown above its head). */
	public static final Attachment<Integer> VILLAGERS_REVIVED = Attachment.saved("villagers_revived", com.mojang.serialization.Codec.INT);

	/** A traveller staying at an inn (until hired or their stay is over). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.inn.Traveller> TRAVELLER = Attachment.saved("traveller", io.github.jcondedata.aliveworkplace.inn.Traveller.CODEC);
	/** The level a hired traveller starts their first job at. */
	public static final Attachment<Integer> HEAD_START = Attachment.saved("head_start", com.mojang.serialization.Codec.INT);
	/** The day an Innkeeper last took in a traveller, and how many they've hosted. */
	public static final Attachment<Long> LAST_GUEST_DAY = Attachment.saved("last_guest_day", com.mojang.serialization.Codec.LONG);
	public static final Attachment<Integer> GUESTS_HOSTED = Attachment.saved("guests_hosted", com.mojang.serialization.Codec.INT);

	/** Whether a villager went to school as a child (a Teacher's lessons), and how many ticks of lessons they've had. */
	public static final Attachment<Boolean> SCHOOLED = Attachment.saved("schooled", com.mojang.serialization.Codec.BOOL);
	public static final Attachment<Integer> LESSONS = Attachment.saved("lessons", com.mojang.serialization.Codec.INT);
	/** Set once a schooled villager has had their head start at their first job. */
	public static final Attachment<Boolean> SCHOOL_BONUS = Attachment.saved("school_bonus", com.mojang.serialization.Codec.BOOL);
	/** How many children a Teacher has seen through school (shown above its head). */
	public static final Attachment<Integer> PUPILS_TAUGHT = Attachment.saved("pupils_taught", com.mojang.serialization.Codec.INT);

	/** When a villager last ate (game time; a Village Hall feeds its villagers from the store once a day). */
	public static final Attachment<Long> LAST_MEAL = Attachment.saved("last_meal", com.mojang.serialization.Codec.LONG);

	/** The Pokémon in a rancher's daycare (see {@code ranch/Daycare}). */
	public static final Attachment<java.util.List<io.github.jcondedata.aliveworkplace.ranch.Daycare.Boarder>> DAYCARE = Attachment.saved("daycare", io.github.jcondedata.aliveworkplace.ranch.Daycare.Boarder.CODEC.listOf());

	/** Who a villager is courting or married to (see {@code people/Couples}). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.people.Couples.Partner> PARTNER = Attachment.saved("partner", io.github.jcondedata.aliveworkplace.people.Couples.Partner.CODEC);

	/** The day a villager's partner died (they mourn a while). */
	public static final Attachment<Long> WIDOWED_DAY = Attachment.saved("widowed_day", com.mojang.serialization.Codec.LONG);

	/** The last day a villager came to a festival (see {@code hall/Festivals}). */
	public static final Attachment<Long> FESTIVAL_DAY = Attachment.saved("festival_day", com.mojang.serialization.Codec.LONG);

	/** The last few kinds of meal a villager ate, newest last (see {@code people/Diet}). */
	public static final Attachment<java.util.List<net.minecraft.resources.ResourceLocation>> RECENT_MEALS = Attachment.saved("recent_meals", net.minecraft.resources.ResourceLocation.CODEC.listOf());

	/** A guard's patrol route, from a Patrol Map (see {@code guard/PatrolMapItem}). */
	public static final Attachment<java.util.List<net.minecraft.core.BlockPos>> PATROL_ROUTE = Attachment.saved("patrol_route", net.minecraft.core.BlockPos.CODEC.listOf());

	/** When a hired mercenary leaves (see {@code guard/Mercenaries}); absent for everyone else. */
	public static final Attachment<Long> MERCENARY_UNTIL = Attachment.saved("mercenary_until", com.mojang.serialization.Codec.LONG);

	/** A villager's parents (see {@code people/Families}). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.people.Families.Parents> PARENTS = Attachment.saved("parents", io.github.jcondedata.aliveworkplace.people.Families.Parents.CODEC);

	/** When a villager fell ill (see {@code people/Sickness}); absent while they're well. */
	public static final Attachment<Long> ILL_SINCE = Attachment.saved("ill_since", com.mojang.serialization.Codec.LONG);

	/** How many blocks a Sifter has sifted (shown above its head). */
	public static final Attachment<Integer> BLOCKS_SIFTED = Attachment.saved("blocks_sifted", com.mojang.serialization.Codec.INT);

	/** How many times a Tinkerer has patched up an iron golem (an ingot each). */
	public static final Attachment<Integer> GOLEM_REPAIRS = Attachment.saved("golem_repairs", com.mojang.serialization.Codec.INT);

	/** A Netherworker's expedition under way (see {@code nether/Netherworkers}); absent while they're home. */
	public static final Attachment<io.github.jcondedata.aliveworkplace.nether.Netherworkers.Trip> NETHER_TRIP = Attachment.saved("nether_trip", io.github.jcondedata.aliveworkplace.nether.Netherworkers.Trip.CODEC);

	/** How many expeditions a Netherworker has made (shown above its head). */
	public static final Attachment<Integer> NETHER_TRIPS = Attachment.saved("nether_trips", com.mojang.serialization.Codec.INT);

	/** Compost in a Composter's bin towards the next bone meal (see {@code compost/CompostWork}). */
	public static final Attachment<Float> COMPOST_LAYERS = Attachment.saved("compost_layers", com.mojang.serialization.Codec.FLOAT);

	/** How much bone meal a Composter has made (shown above its head). */
	public static final Attachment<Integer> BONE_MEAL_MADE = Attachment.saved("bone_meal_made", com.mojang.serialization.Codec.INT);

	/** How many villagers a Nurse has cured. */
	public static final Attachment<Integer> VILLAGERS_CURED = Attachment.saved("villagers_cured", com.mojang.serialization.Codec.INT);

	/** How many horses a Rancher has tamed (shown above its head). */
	public static final Attachment<Integer> HORSES_TAMED = Attachment.saved("horses_tamed", com.mojang.serialization.Codec.INT);

	/** How many flowers a Florist has grown (shown above its head). */
	public static final Attachment<Integer> FLOWERS_GROWN = Attachment.saved("flowers_grown", com.mojang.serialization.Codec.INT);

	/** The Berry Breeder's goal berry (ROADMAP 28.9; the step towards it is worked out afresh from what the village has). */
	public static final Attachment<net.minecraft.resources.ResourceLocation> BERRY_GOAL = Attachment.saved("berry_goal", net.minecraft.resources.ResourceLocation.CODEC);

	/** The Berry Breeder's plot, marked with a Field Marker (absent: the farmland round her composter). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.farm.FieldJob> BERRY_PLOT = Attachment.saved("berry_plot", io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC);

	/** How many dishes a Camp Cook has cooked (shown above her head; ROADMAP 28.8). */
	public static final Attachment<Integer> DISHES_COOKED = Attachment.saved("dishes_cooked", com.mojang.serialization.Codec.INT);

	/** How many berries a Berry Breeder has picked (shown above her head). */
	public static final Attachment<Integer> BERRIES_PICKED = Attachment.saved("berries_picked", com.mojang.serialization.Codec.INT);

	/** How many beehives a Beekeeper has harvested (shown above its head). */
	public static final Attachment<Integer> HIVES_HARVESTED = Attachment.saved("hives_harvested", com.mojang.serialization.Codec.INT);

	/** How many chores a Butcher has done for pastured Pokémon (milking, brushing...). */
	public static final Attachment<Integer> POKEMON_TENDED = Attachment.saved("pokemon_tended", com.mojang.serialization.Codec.INT);

	/** How many expeditions a Cartographer has come back from (shown above its head). */
	public static final Attachment<Integer> EXPEDITIONS = Attachment.saved("expeditions", com.mojang.serialization.Codec.INT);

	/** How many maps to places nearby a Cartographer has drawn. */
	public static final Attachment<Integer> MAPS_CHARTED = Attachment.saved("maps_charted", com.mojang.serialization.Codec.INT);

	/** How many items a Porter has carried to the storehouse (shown above its head). */
	public static final Attachment<Integer> ITEMS_CARRIED = Attachment.saved("items_carried", com.mojang.serialization.Codec.INT);

	/** How much fruit an Orchard Keeper has picked (shown above its head). */
	public static final Attachment<Integer> FRUIT_PICKED = Attachment.saved("fruit_picked", com.mojang.serialization.Codec.INT);

	/** The player a builder works for (see {@code Friends}). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.build.Employer> BUILDER_EMPLOYER = Attachment.saved("builder_employer", io.github.jcondedata.aliveworkplace.build.Employer.CODEC);

	/** The field a Farmer was given to look after. */
	public static final Attachment<io.github.jcondedata.aliveworkplace.farm.FieldJob> FARM_FIELD = Attachment.saved("farm_field", io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC);

	/** True once a player stopped a farmer's self-adopted farm: they don't take it on again by themselves. */
	public static final Attachment<Boolean> NO_AUTO_FARM = Attachment.saved("no_auto_farm", com.mojang.serialization.Codec.BOOL);

	/** How many crops a farmer has harvested on their field (shown above its head). */
	public static final Attachment<Integer> FARM_HARVESTED = Attachment.saved("farm_harvested", com.mojang.serialization.Codec.INT);

	/** True for a Fisherman a player handed a fishing rod: they fish for the chests near their barrel. */
	public static final Attachment<Boolean> FISHER_JOB = Attachment.saved("fisher_job", com.mojang.serialization.Codec.BOOL);

	/** How many catches a fisherman has reeled in (shown above its head). */
	public static final Attachment<Integer> FISH_CAUGHT = Attachment.saved("fish_caught", com.mojang.serialization.Codec.INT);

	/** How many parcels a postman has delivered (shown above its head). */
	public static final Attachment<Integer> MAIL_DELIVERED = Attachment.saved("mail_delivered", com.mojang.serialization.Codec.INT);

	/** How many monsters a guard has defeated (shown above its head). */
	public static final Attachment<Integer> GUARD_KILLS = Attachment.saved("guard_kills", com.mojang.serialization.Codec.INT);

	/** How many times a guard has hit a Training Dummy. */
	public static final Attachment<Integer> DUMMY_HITS = Attachment.saved("dummy_hits", com.mojang.serialization.Codec.INT);

	/** How many sales a shopkeeper has made (shown above its head). */
	public static final Attachment<Integer> SHOP_SALES = Attachment.saved("shop_sales", com.mojang.serialization.Codec.INT);

	/** A postman's courier routes (from Delivery Notes). */
	public static final Attachment<java.util.List<io.github.jcondedata.aliveworkplace.mail.RouteData>> COURIER_ROUTES = Attachment.saved("courier_routes", io.github.jcondedata.aliveworkplace.mail.RouteData.CODEC.listOf());

	/** How many battles a trainer has fought (shown above its head). */
	public static final Attachment<Integer> TRAINER_BATTLES = Attachment.saved("trainer_battles", com.mojang.serialization.Codec.INT);

	/** Player → the in-game day they were last paid for beating this trainer. */
	public static final Attachment<java.util.Map<java.util.UUID, Long>> TRAINER_REWARDS = Attachment.saved("trainer_rewards", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG));

	/** Player → the in-game day they last challenged this Trainer Leader (one challenge a day). */
	public static final Attachment<java.util.Map<java.util.UUID, Long>> LEADER_CHALLENGES = Attachment.saved("leader_challenges", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG));

	/** Lessons a Move Tutor has given. */
	public static final Attachment<Integer> TUTOR_LESSONS = Attachment.saved("tutor_lessons", com.mojang.serialization.Codec.INT);

	/** Player → the in-game day they last traded with this Pokémon Trader. */
	public static final Attachment<java.util.Map<java.util.UUID, Long>> POKEMON_TRADES = Attachment.saved("pokemon_trades", com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG));

	/** Trades a Pokémon Trader has made. */
	public static final Attachment<Integer> POKEMON_TRADE_COUNT = Attachment.saved("pokemon_trade_count", com.mojang.serialization.Codec.INT);

	/** The villager's job site and how often a workstation there had been broken when they got it (bug B15; see JobSiteTickets). */
	public static final Attachment<io.github.jcondedata.aliveworkplace.work.JobSiteTickets.Held> JOB_SITE_HELD = Attachment.saved("job_site_held", io.github.jcondedata.aliveworkplace.work.JobSiteTickets.Held.CODEC);

	public static void init() {
	}

	private ModAttachments() {
	}
}
