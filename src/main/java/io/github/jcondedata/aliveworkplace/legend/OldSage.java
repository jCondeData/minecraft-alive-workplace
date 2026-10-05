package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.research.TreeEffects;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Old Sage (ROADMAP 29.14), a Rare Legend {@code legends/old_sage.json}. Once a village has finished
 * {@code research_levels} 5, the hall's morning round sets a hermit's hut ({@code legend/hermit_hut}) down
 * {@link #NEAR}-{@link #FAR} blocks out, on dry, flat, loaded ground (as {@code BanditCamps.site} picks), with the Sage
 * waiting in it as a found Legend ({@link LegendSites}' captive, site {@code hermit_hut}); the village's players online
 * hear a rumour of it, with its distance and direction.
 * <p>
 * Right-click the Sage for the riddle quest: three riddles from a pool of {@link #RIDDLES}, one at a time, each answered
 * by handing over an item (the one in the hand). A wrong item gets a shake of the head, and after two misses on a riddle
 * a hint; three right answers free the Sage, who comes to the hall the next morning as a guest (29.8). Their progress is
 * saved on them ({@link ModAttachments#SAGE_RIDDLES}).
 * <p>
 * Their power is the Ancient Lore research tree ({@code research_trees/ancient_lore.json}, 29.11); the effects that
 * aren't from the shared toolbox are flags read where they act ({@link #flags}, and here: the Iron Pact's golems and
 * their thicker hides).
 */
public final class OldSage {
	public static final ResourceLocation ID = AliveWorkplace.id("old_sage");
	public static final String SITE = "hermit_hut";
	public static final ResourceLocation HUT = AliveWorkplace.id("legend/hermit_hut");
	/** Where the Sage stands in the hut (tools/blueprints/legend_sites.py, SAGE_SPOT). */
	public static final BlockPos SPOT = new BlockPos(4, 1, 5);
	public static final int NEAR = 150;
	public static final int FAR = 250;
	public static final int ASKED = 3;
	/** Misses on one riddle before the Sage gives a hint. */
	public static final int HINT_AFTER = 2;
	/** Days between the Iron Pact's golems, and villagers per golem. */
	public static final int PACT_DAYS = 5;
	public static final int VILLAGERS_PER_GOLEM = 8;
	/** How much of a hit golems and guards take under the Iron Pact. */
	public static final float PACT_DAMAGE = 0.75f;
	public static final String PACT_TAG = "aliveworkplace_iron_pact";

	/** A riddle: its number (lang {@code message.aliveworkplace.sage.riddle.<n>} and {@code .hint.<n>}) and what answers it. */
	public record Riddle(int number, Predicate<ItemStack> answer) {
	}

	public static final List<Riddle> RIDDLES = List.of(
		new Riddle(1, s -> s.is(Items.MAP) || s.is(Items.FILLED_MAP)),
		new Riddle(2, s -> s.is(Items.COMPASS)),
		new Riddle(3, s -> s.is(Items.CLOCK)),
		new Riddle(4, s -> s.is(Items.BOOK) || s.is(Items.WRITABLE_BOOK) || s.is(Items.WRITTEN_BOOK) || s.is(Items.ENCHANTED_BOOK)),
		new Riddle(5, s -> s.is(Items.SPONGE) || s.is(Items.WET_SPONGE)),
		new Riddle(6, s -> s.is(Items.TORCH)),
		new Riddle(7, s -> s.is(Items.WATER_BUCKET)),
		new Riddle(8, s -> s.is(Items.EMERALD)));

	/** The Sage's quest: the riddles asked (numbers, in order), how many are answered, and misses on the current one. */
	public record Riddles(List<Integer> asked, int solved, int misses) {
		public static final Codec<Riddles> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf().optionalFieldOf("asked", List.of()).forGetter(Riddles::asked),
			Codec.INT.optionalFieldOf("solved", 0).forGetter(Riddles::solved),
			Codec.INT.optionalFieldOf("misses", 0).forGetter(Riddles::misses)
		).apply(i, Riddles::new));

		/** The riddle being asked now, or null when all are answered. */
		@Nullable
		public Riddle current() {
			return solved >= asked.size() ? null : RIDDLES.get(Math.floorMod(asked.get(solved) - 1, RIDDLES.size()));
		}
	}

	/** Three riddles of the eight, none twice. */
	public static Riddles draw(RandomSource random) {
		List<Integer> pool = new ArrayList<>();
		for (Riddle r : RIDDLES) {
			pool.add(r.number());
		}
		List<Integer> asked = new ArrayList<>();
		for (int i = 0; i < ASKED; i++) {
			asked.add(pool.remove(random.nextInt(pool.size())));
		}
		return new Riddles(List.copyOf(asked), 0, 0);
	}

	/** The day each hall last looked for a hut site, and last had a pact golem (kept while the server runs). */
	private static final Map<Long, Long> LOOKED = new HashMap<>();
	private static final Map<Long, Long> PACT = new HashMap<>();

	/** The hall's round, in the morning: the hut, when the village qualifies; the Iron Pact's golem every few days. */
	public static void round(ServerLevel level, BlockPos hall) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time < LegendGuests.MORNING_FROM || time >= LegendGuests.MORNING_TO) {
			return;
		}
		long today = Chronicle.day(level);
		if (!Long.valueOf(today).equals(LOOKED.put(hall.asLong(), today)) && LegendSites.ENABLED) {
			BlockPos ground = qualifies(level, hall) ? BanditCamps.site(level, hall, NEAR, FAR, level.random) : null;
			if (ground != null) {
				placeHut(level, hall, ground, level.random);
			}
		}
		if (today % PACT_DAYS == 0 && !Long.valueOf(today).equals(PACT.get(hall.asLong())) && TreeEffects.flag(level, hall, "iron_pact")) {
			PACT.put(hall.asLong(), today);
			pactGolem(level, hall);
		}
	}

	/** Whether the village round {@code hall} may have the Sage's hut now: their conditions met, slot free, no hut out. */
	public static boolean qualifies(ServerLevel level, BlockPos hall) {
		Legend legend = Legends.get(ID).orElse(null);
		if (legend == null || !Legends.ENABLED || LegendSites.waitingFor(level, ID).isPresent()) {
			return false;
		}
		for (Condition c : legend.conditions()) {
			if (!c.met(level, hall)) {
				return false;
			}
		}
		return LegendSlots.whyNot(level, hall, legend, null).isEmpty();
	}

	/** Sets the hut down at {@code ground} with the Sage in it, and tells the village's players. Returns the Sage, or null. */
	@Nullable
	public static Villager placeHut(ServerLevel level, BlockPos hall, BlockPos ground, RandomSource random) {
		Legend legend = Legends.get(ID).orElse(null);
		if (legend == null) {
			return null;
		}
		Villager sage = LegendSites.place(level, new LegendSites.Site(SITE, null, HUT, SPOT), new LegendSites.Match(hall, legend), ground, random);
		if (sage == null) {
			return null;
		}
		ModAttachments.SAGE_RIDDLES.set(sage, draw(random));
		Component rumour = Component.translatable("message.aliveworkplace.sage.rumour", Pathfinder.distance(hall, ground),
			Pathfinder.direction(hall, ground), VillageHalls.name(level, hall)).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : villagePlayers(level, hall)) {
			Chat.chat(player, rumour);
		}
		AliveWorkplace.LOG.info("The Old Sage's hut is at {}, for the village hall at {}", ground, hall);
		return sage;
	}

	/** The players online who own the hall or are the owner's friends. */
	static List<ServerPlayer> villagePlayers(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.owner() == null) {
			return List.of();
		}
		Friends friends = Friends.get(level.getServer());
		return level.getServer().getPlayerList().getPlayers().stream().filter(p -> friends.mayDirect(entity.owner(), p.getUUID())).toList();
	}

	/**
	 * A player right-clicks the Sage at the hut: with an empty hand, the riddle; with the answer, it's taken (a bucket
	 * comes back empty) and the next riddle is asked, or after the last the Sage is free; with anything else, a shake of
	 * the head, and after {@link #HINT_AFTER} misses a hint.
	 */
	public static InteractionResult use(ServerPlayer player, Villager sage, InteractionHand hand, LegendRecord.Captive captive) {
		ServerLevel level = (ServerLevel) sage.level();
		Riddles riddles = ModAttachments.SAGE_RIDDLES.get(sage);
		if (riddles == null || riddles.asked().isEmpty()) {
			riddles = draw(level.random);
			ModAttachments.SAGE_RIDDLES.set(sage, riddles);
		}
		Riddle riddle = riddles.current();
		if (riddle == null) {
			LegendSites.free(level, sage, captive, player);
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty()) {
			ask(player, sage, riddles);
			return InteractionResult.SUCCESS;
		}
		if (riddle.answer().test(held)) {
			boolean bucket = held.is(Items.WATER_BUCKET);
			held.consume(1, player);
			if (bucket && !player.getInventory().add(new ItemStack(Items.BUCKET))) {
				player.drop(new ItemStack(Items.BUCKET), false);
			}
			riddles = new Riddles(riddles.asked(), riddles.solved() + 1, 0);
			ModAttachments.SAGE_RIDDLES.set(sage, riddles);
			level.sendParticles(ParticleTypes.ENCHANT, sage.getX(), sage.getY() + 1.8, sage.getZ(), 30, 0.4, 0.4, 0.4, 0.6);
			level.playSound(null, sage.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 0.8f);
			Chat.chat(player, Component.translatable("message.aliveworkplace.sage.right", sage.getDisplayName()).withStyle(ChatFormatting.GREEN));
			if (riddles.current() == null) {
				LegendSites.free(level, sage, captive, player);
			} else {
				ask(player, sage, riddles);
			}
			return InteractionResult.SUCCESS;
		}
		riddles = new Riddles(riddles.asked(), riddles.solved(), riddles.misses() + 1);
		ModAttachments.SAGE_RIDDLES.set(sage, riddles);
		sage.setUnhappyCounter(40); // the shake of the head
		level.playSound(null, sage.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 0.8f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.sage.wrong", sage.getDisplayName(), held.getHoverName())
			.withStyle(ChatFormatting.GRAY));
		if (riddles.misses() >= HINT_AFTER) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.sage.hint", sage.getDisplayName(),
				Component.translatable("message.aliveworkplace.sage.hint." + riddle.number())).withStyle(ChatFormatting.YELLOW));
		}
		return InteractionResult.SUCCESS;
	}

	static void ask(ServerPlayer player, Villager sage, Riddles riddles) {
		Riddle riddle = riddles.current();
		if (riddle == null) {
			return;
		}
		Chat.chat(player, Component.translatable("message.aliveworkplace.sage.riddle", sage.getDisplayName(), riddles.solved() + 1, riddles.asked().size(),
			Component.translatable("message.aliveworkplace.sage.riddle." + riddle.number()).withStyle(ChatFormatting.ITALIC)).withStyle(ChatFormatting.AQUA));
	}

	/** The Iron Pact: an iron golem walks into the village, while it has fewer than one per {@link #VILLAGERS_PER_GOLEM} villagers. */
	@Nullable
	public static IronGolem pactGolem(ServerLevel level, BlockPos hall) {
		int villagers = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive).size();
		int golems = level.getEntitiesOfClass(IronGolem.class, VillageHalls.area(hall), IronGolem::isAlive).size();
		if (golems >= villagers / VILLAGERS_PER_GOLEM) {
			return null;
		}
		IronGolem golem = EntityType.IRON_GOLEM.create(level);
		if (golem == null) {
			return null;
		}
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, 2));
		golem.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		golem.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		golem.setPlayerCreated(true);
		golem.addTag(PACT_TAG);
		level.addFreshEntityWithPassengers(golem);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + 1.5, golem.getZ(), 20, 0.6, 1, 0.6, 0.05);
		Chronicle.record(level, hall, Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.iron_pact"));
		return golem;
	}

	/** A hit on {@code entity}: golems and guards of a village under the Iron Pact take {@link #PACT_DAMAGE} of it. */
	public static float damage(LivingEntity entity, float amount) {
		if (amount <= 0 || !(entity.level() instanceof ServerLevel level)) {
			return amount;
		}
		if (entity instanceof IronGolem) {
			BlockPos at = entity.blockPosition();
			for (io.github.jcondedata.aliveworkplace.hall.Caravans.Village v : io.github.jcondedata.aliveworkplace.hall.Caravans.Data.get(level).villages()) {
				if (VillageHalls.area(v.hall()).contains(at.getCenter()) && TreeEffects.flag(level, v.hall(), "iron_pact")) {
					return amount * PACT_DAMAGE; // a golem in a village under the pact
				}
			}
			return amount;
		}
		if (entity instanceof Villager v && Guards.isGuard(v) && TreeEffects.flag(v, "iron_pact")) {
			return amount * PACT_DAMAGE;
		}
		return amount;
	}

	/** How many levels of the Ancient Lore flag {@code name} the village of {@code villager} has (for them). */
	public static int flags(Villager villager, String name) {
		return TreeEffects.flagCount(villager, name);
	}

	/** Forgets the days kept (tests). */
	public static void forget() {
		LOOKED.clear();
		PACT.clear();
	}

	private OldSage() {
	}
}
