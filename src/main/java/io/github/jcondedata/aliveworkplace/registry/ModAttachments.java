package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Extra data we store on vanilla villagers (saved with the entity). */
public final class ModAttachments {
	/** Which build site this villager is working on. */
	public static final AttachmentType<BuilderJob> BUILDER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("builder_job"), builder -> builder.persistent(BuilderJob.CODEC));

	/** The builder's tool bag: materials fetched from the supply chests, and rubble from clearing. */
	public static final AttachmentType<BuilderBag> BUILDER_BAG = AttachmentRegistry.create(
		AliveWorkplace.id("builder_bag"), builder -> builder.persistent(BuilderBag.CODEC).initializer(BuilderBag::new));

	/** Which quarry a miner is digging. */
	public static final AttachmentType<BuilderJob> MINER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("miner_job"), builder -> builder.persistent(BuilderJob.CODEC));

	/** How many trees a lumberjack has cut down (shown above its head). */
	public static final AttachmentType<Integer> TREES_FELLED = AttachmentRegistry.create(
		AliveWorkplace.id("trees_felled"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The tree farm a lumberjack keeps planted (a Field Marker's area; saved on the villager). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.farm.FieldJob> TREE_FARM = AttachmentRegistry.create(
		AliveWorkplace.id("tree_farm"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC));

	/** The orchard an Orchard Keeper keeps planted (a Field Marker's area; saved on the villager). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.farm.FieldJob> ORCHARD = AttachmentRegistry.create(
		AliveWorkplace.id("orchard"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC));

	/** How many saplings a lumberjack has planted on their tree farm (or seeds an orchard keeper in their orchard). */
	public static final AttachmentType<Integer> SAPLINGS_PLANTED = AttachmentRegistry.create(
		AliveWorkplace.id("saplings_planted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The balls a Ball Smith has been asked to make (item ids; none: anything they can). */
	public static final AttachmentType<java.util.List<net.minecraft.resources.ResourceLocation>> BALL_ORDERS = AttachmentRegistry.create(
		AliveWorkplace.id("ball_orders"), builder -> builder.persistent(net.minecraft.resources.ResourceLocation.CODEC.listOf()));

	/** How many Poké Balls a Ball Smith has made (shown above its head). */
	public static final AttachmentType<Integer> BALLS_MADE = AttachmentRegistry.create(
		AliveWorkplace.id("balls_made"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many things a Carpenter or Mason has made for the builders (shown above its head). */
	public static final AttachmentType<Integer> ITEMS_CRAFTED = AttachmentRegistry.create(
		AliveWorkplace.id("items_crafted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The fossils a Fossil Scientist is reviving (and finished ones waiting for their owners). */
	public static final AttachmentType<java.util.List<io.github.jcondedata.aliveworkplace.fossil.Revival>> FOSSIL_REVIVALS = AttachmentRegistry.create(
		AliveWorkplace.id("fossil_revivals"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.fossil.Revival.CODEC.listOf()));

	/** How many fossils a Fossil Scientist has revived (shown above its head). */
	public static final AttachmentType<Integer> FOSSILS_REVIVED = AttachmentRegistry.create(
		AliveWorkplace.id("fossils_revived"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many ingots an Armorer has smelted (shown above its head). */
	public static final AttachmentType<Integer> INGOTS_SMELTED = AttachmentRegistry.create(
		AliveWorkplace.id("ingots_smelted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many pieces of armor an Armorer has made for the guards. */
	public static final AttachmentType<Integer> ARMOR_MADE = AttachmentRegistry.create(
		AliveWorkplace.id("armor_made"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many pieces a Weaponsmith has mended (shown above its head). */
	public static final AttachmentType<Integer> ITEMS_MENDED = AttachmentRegistry.create(
		AliveWorkplace.id("items_mended"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many animals a Shepherd has sheared (shown above its head). */
	public static final AttachmentType<Integer> ANIMALS_SHEARED = AttachmentRegistry.create(
		AliveWorkplace.id("animals_sheared"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many buckets of milk a Butcher has collected (shown above its head). */
	public static final AttachmentType<Integer> MILK_COLLECTED = AttachmentRegistry.create(
		AliveWorkplace.id("milk_collected"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many potions a Cleric has brewed (shown above its head). */
	public static final AttachmentType<Integer> POTIONS_BREWED = AttachmentRegistry.create(
		AliveWorkplace.id("potions_brewed"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many pieces of gear a Librarian has enchanted (shown above its head). */
	public static final AttachmentType<Integer> ITEMS_ENCHANTED = AttachmentRegistry.create(
		AliveWorkplace.id("items_enchanted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The ground a builder is to turn into path (planned when they finished a building; see build/Paths). */
	public static final AttachmentType<java.util.List<net.minecraft.core.BlockPos>> PATH = AttachmentRegistry.create(
		AliveWorkplace.id("path"), builder -> builder.persistent(net.minecraft.core.BlockPos.CODEC.listOf()));

	/** How many research levels a Scholar has finished (shown above its head). */
	public static final AttachmentType<Integer> RESEARCH_DONE = AttachmentRegistry.create(
		AliveWorkplace.id("research_done"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many villagers an Undertaker has brought back (shown above its head). */
	public static final AttachmentType<Integer> VILLAGERS_REVIVED = AttachmentRegistry.create(
		AliveWorkplace.id("villagers_revived"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** A traveller staying at an inn (until hired or their stay is over). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.inn.Traveller> TRAVELLER = AttachmentRegistry.create(
		AliveWorkplace.id("traveller"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.inn.Traveller.CODEC));
	/** The level a hired traveller starts their first job at. */
	public static final AttachmentType<Integer> HEAD_START = AttachmentRegistry.create(
		AliveWorkplace.id("head_start"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));
	/** The day an Innkeeper last took in a traveller, and how many they've hosted. */
	public static final AttachmentType<Long> LAST_GUEST_DAY = AttachmentRegistry.create(
		AliveWorkplace.id("last_guest_day"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG));
	public static final AttachmentType<Integer> GUESTS_HOSTED = AttachmentRegistry.create(
		AliveWorkplace.id("guests_hosted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** Whether a villager went to school as a child (a Teacher's lessons), and how many ticks of lessons they've had. */
	public static final AttachmentType<Boolean> SCHOOLED = AttachmentRegistry.create(
		AliveWorkplace.id("schooled"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));
	public static final AttachmentType<Integer> LESSONS = AttachmentRegistry.create(
		AliveWorkplace.id("lessons"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));
	/** Set once a schooled villager has had their head start at their first job. */
	public static final AttachmentType<Boolean> SCHOOL_BONUS = AttachmentRegistry.create(
		AliveWorkplace.id("school_bonus"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));
	/** How many children a Teacher has seen through school (shown above its head). */
	public static final AttachmentType<Integer> PUPILS_TAUGHT = AttachmentRegistry.create(
		AliveWorkplace.id("pupils_taught"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** When a villager last ate (game time; a Village Hall feeds its villagers from the store once a day). */
	public static final AttachmentType<Long> LAST_MEAL = AttachmentRegistry.create(
		AliveWorkplace.id("last_meal"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG));

	/** A villager's parents (see {@code people/Families}). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.people.Families.Parents> PARENTS = AttachmentRegistry.create(
		AliveWorkplace.id("parents"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.people.Families.Parents.CODEC));

	/** When a villager fell ill (see {@code people/Sickness}); absent while they're well. */
	public static final AttachmentType<Long> ILL_SINCE = AttachmentRegistry.create(
		AliveWorkplace.id("ill_since"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG));

	/** How many blocks a Sifter has sifted (shown above its head). */
	public static final AttachmentType<Integer> BLOCKS_SIFTED = AttachmentRegistry.create(
		AliveWorkplace.id("blocks_sifted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many times a Tinkerer has patched up an iron golem (an ingot each). */
	public static final AttachmentType<Integer> GOLEM_REPAIRS = AttachmentRegistry.create(
		AliveWorkplace.id("golem_repairs"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** A Netherworker's expedition under way (see {@code nether/Netherworkers}); absent while they're home. */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.nether.Netherworkers.Trip> NETHER_TRIP = AttachmentRegistry.create(
		AliveWorkplace.id("nether_trip"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.nether.Netherworkers.Trip.CODEC));

	/** How many expeditions a Netherworker has made (shown above its head). */
	public static final AttachmentType<Integer> NETHER_TRIPS = AttachmentRegistry.create(
		AliveWorkplace.id("nether_trips"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many villagers a Nurse has cured. */
	public static final AttachmentType<Integer> VILLAGERS_CURED = AttachmentRegistry.create(
		AliveWorkplace.id("villagers_cured"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many horses a Rancher has tamed (shown above its head). */
	public static final AttachmentType<Integer> HORSES_TAMED = AttachmentRegistry.create(
		AliveWorkplace.id("horses_tamed"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many flowers a Florist has grown (shown above its head). */
	public static final AttachmentType<Integer> FLOWERS_GROWN = AttachmentRegistry.create(
		AliveWorkplace.id("flowers_grown"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many beehives a Beekeeper has harvested (shown above its head). */
	public static final AttachmentType<Integer> HIVES_HARVESTED = AttachmentRegistry.create(
		AliveWorkplace.id("hives_harvested"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many chores a Butcher has done for pastured Pokémon (milking, brushing...). */
	public static final AttachmentType<Integer> POKEMON_TENDED = AttachmentRegistry.create(
		AliveWorkplace.id("pokemon_tended"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many expeditions a Cartographer has come back from (shown above its head). */
	public static final AttachmentType<Integer> EXPEDITIONS = AttachmentRegistry.create(
		AliveWorkplace.id("expeditions"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many maps to places nearby a Cartographer has drawn. */
	public static final AttachmentType<Integer> MAPS_CHARTED = AttachmentRegistry.create(
		AliveWorkplace.id("maps_charted"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many items a Porter has carried to the storehouse (shown above its head). */
	public static final AttachmentType<Integer> ITEMS_CARRIED = AttachmentRegistry.create(
		AliveWorkplace.id("items_carried"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How much fruit an Orchard Keeper has picked (shown above its head). */
	public static final AttachmentType<Integer> FRUIT_PICKED = AttachmentRegistry.create(
		AliveWorkplace.id("fruit_picked"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** The player a builder works for (see {@code Friends}). */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.build.Employer> BUILDER_EMPLOYER = AttachmentRegistry.create(
		AliveWorkplace.id("builder_employer"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.build.Employer.CODEC));

	/** The field a Farmer was given to look after. */
	public static final AttachmentType<io.github.jcondedata.aliveworkplace.farm.FieldJob> FARM_FIELD = AttachmentRegistry.create(
		AliveWorkplace.id("farm_field"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.farm.FieldJob.CODEC));

	/** True once a player stopped a farmer's self-adopted farm: they don't take it on again by themselves. */
	public static final AttachmentType<Boolean> NO_AUTO_FARM = AttachmentRegistry.create(
		AliveWorkplace.id("no_auto_farm"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

	/** How many crops a farmer has harvested on their field (shown above its head). */
	public static final AttachmentType<Integer> FARM_HARVESTED = AttachmentRegistry.create(
		AliveWorkplace.id("farm_harvested"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** True for a Fisherman a player handed a fishing rod: they fish for the chests near their barrel. */
	public static final AttachmentType<Boolean> FISHER_JOB = AttachmentRegistry.create(
		AliveWorkplace.id("fisher_job"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

	/** How many catches a fisherman has reeled in (shown above its head). */
	public static final AttachmentType<Integer> FISH_CAUGHT = AttachmentRegistry.create(
		AliveWorkplace.id("fish_caught"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many parcels a postman has delivered (shown above its head). */
	public static final AttachmentType<Integer> MAIL_DELIVERED = AttachmentRegistry.create(
		AliveWorkplace.id("mail_delivered"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many monsters a guard has defeated (shown above its head). */
	public static final AttachmentType<Integer> GUARD_KILLS = AttachmentRegistry.create(
		AliveWorkplace.id("guard_kills"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many times a guard has hit a Training Dummy. */
	public static final AttachmentType<Integer> DUMMY_HITS = AttachmentRegistry.create(
		AliveWorkplace.id("dummy_hits"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** How many sales a shopkeeper has made (shown above its head). */
	public static final AttachmentType<Integer> SHOP_SALES = AttachmentRegistry.create(
		AliveWorkplace.id("shop_sales"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** A postman's courier routes (from Delivery Notes). */
	public static final AttachmentType<java.util.List<io.github.jcondedata.aliveworkplace.mail.RouteData>> COURIER_ROUTES = AttachmentRegistry.create(
		AliveWorkplace.id("courier_routes"), builder -> builder.persistent(io.github.jcondedata.aliveworkplace.mail.RouteData.CODEC.listOf()));

	/** How many battles a trainer has fought (shown above its head). */
	public static final AttachmentType<Integer> TRAINER_BATTLES = AttachmentRegistry.create(
		AliveWorkplace.id("trainer_battles"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** Player → the in-game day they were last paid for beating this trainer. */
	public static final AttachmentType<java.util.Map<java.util.UUID, Long>> TRAINER_REWARDS = AttachmentRegistry.create(
		AliveWorkplace.id("trainer_rewards"), builder -> builder.persistent(
			com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG)));

	/** Player → the in-game day they last challenged this Trainer Leader (one challenge a day). */
	public static final AttachmentType<java.util.Map<java.util.UUID, Long>> LEADER_CHALLENGES = AttachmentRegistry.create(
		AliveWorkplace.id("leader_challenges"), builder -> builder.persistent(
			com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG)));

	/** Lessons a Move Tutor has given. */
	public static final AttachmentType<Integer> TUTOR_LESSONS = AttachmentRegistry.create(
		AliveWorkplace.id("tutor_lessons"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	/** Player → the in-game day they last traded with this Pokémon Trader. */
	public static final AttachmentType<java.util.Map<java.util.UUID, Long>> POKEMON_TRADES = AttachmentRegistry.create(
		AliveWorkplace.id("pokemon_trades"), builder -> builder.persistent(
			com.mojang.serialization.Codec.unboundedMap(net.minecraft.core.UUIDUtil.STRING_CODEC, com.mojang.serialization.Codec.LONG)));

	/** Trades a Pokémon Trader has made. */
	public static final AttachmentType<Integer> POKEMON_TRADE_COUNT = AttachmentRegistry.create(
		AliveWorkplace.id("pokemon_trade_count"), builder -> builder.persistent(com.mojang.serialization.Codec.INT));

	public static void init() {
	}

	private ModAttachments() {
	}
}
