package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Legends found in the world (ROADMAP 29.9, the {@code found} way). Every {@link #EVERY} ticks, for each player online,
 * {@code StructureManager.getStructureWithPieceAt} asks whether they stand in a structure of one of the three site tags
 * ({@code #aliveworkplace:legend_sites/ruined_portal|outpost|shipwreck}): three cheap lookups, no area scans, and none
 * with nobody online. A site holds a Legend only for a player who owns, or is a friend of the owner of, a Village Hall
 * within {@link #REACH} blocks that meets the conditions of a Legend whose {@code found} way names the site, with the
 * slot free. Then a small camp is set down (as {@code BanditCamps.found} sets down its camp) with the Legend in it,
 * and the structure start is marked in the {@link LegendRecord}, so each is used once:
 * <ul>
 *   <li>{@code ruined_portal}: a traveller's camp beside the portal; talk to them and they're free;</li>
 *   <li>{@code outpost}: a prisoner in an iron-bar cage at the foot of the tower; break a bar;</li>
 *   <li>{@code shipwreck}: a castaway's camp on the nearest beach; hand them a cooked meal ({@link #MEALS}).</li>
 * </ul>
 * Freed, they thank the player and walk off, and come to that village's hall the next morning as a guest (29.8: three
 * days to settle). {@code legendSites} in the config turns it all off, lookups included.
 */
public final class LegendSites {
	public static boolean ENABLED = true;
	/** How often each player is checked (ticks): 5 seconds. */
	public static final int EVERY = 100;
	/** How far from a qualifying hall a site may be (blocks). */
	public static final int REACH = 1500;
	/** A freed Legend is gone from their camp once no player is this close, or after {@link #WALK_TICKS}. */
	public static final int OUT_OF_SIGHT = 24;
	public static final int WALK_TICKS = 600;
	public static final String CAPTIVE_TAG = "aliveworkplace_legend_captive";

	public static final List<String> SITES = List.of("ruined_portal", "outpost", "shipwreck");
	/** What a castaway takes: a cooked meal (vanilla's cooked foods and stews, and the camp meals). */
	public static final TagKey<Item> MEALS = TagKey.create(Registries.ITEM, AliveWorkplace.id("cooked_meals"));

	/** Structure lookups made so far (tests: none with nobody online). */
	public static long lookups = 0;

	/** A site's tag, camp and where its Legend stands in the camp. */
	public record Site(String name, TagKey<Structure> tag, ResourceLocation camp, BlockPos spot) {
	}

	public static Site site(String name) {
		return switch (name) {
			case "ruined_portal" -> new Site(name, tag(name), AliveWorkplace.id("legend/traveller_camp"), new BlockPos(4, 1, 5));
			case "outpost" -> new Site(name, tag(name), AliveWorkplace.id("legend/prisoner_cage"), new BlockPos(2, 1, 2));
			case "shipwreck" -> new Site(name, tag(name), AliveWorkplace.id("legend/castaway_camp"), new BlockPos(4, 1, 4));
			default -> throw new IllegalArgumentException("unknown legend site " + name);
		};
	}

	private static TagKey<Structure> tag(String name) {
		return TagKey.create(Registries.STRUCTURE, AliveWorkplace.id("legend_sites/" + name));
	}

	/** Every server tick: the players checked every {@link #EVERY} ticks; freed Legends walk off. */
	public static void tick(MinecraftServer server) {
		if (!ENABLED || !Legends.ENABLED) {
			return;
		}
		if (server.getTickCount() % EVERY == 0) {
			scan(server.getPlayerList().getPlayers());
		}
		if (server.getTickCount() % 20 == 0) {
			walk(server);
		}
	}

	/** Checks {@code players} where they stand: three lookups each (none for nobody). */
	public static void scan(Collection<ServerPlayer> players) {
		if (!ENABLED || !Legends.ENABLED || Legends.all().stream().noneMatch(l -> !l.ways("found").isEmpty())) {
			return; // nobody to find: no lookups at all
		}
		for (ServerPlayer player : players) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.serverLevel();
			for (String name : SITES) {
				Site site = site(name);
				lookups++;
				StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), site.tag());
				if (start != null && start.isValid()) {
					BoundingBox anchor = start.getPieces().isEmpty() ? start.getBoundingBox() : start.getPieces().get(0).getBoundingBox();
					offer(level, player, name, key(level, start), start.getBoundingBox(), anchor, level.random);
					break;
				}
			}
		}
	}

	/** The structure start's key in the record: the dimension, the structure and its chunk. */
	public static String key(ServerLevel level, StructureStart start) {
		ResourceLocation structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		return level.dimension().location() + "|" + structure + "|" + start.getChunkPos().toLong();
	}

	/** A hall and the Legend it may have from a site. */
	public record Match(BlockPos hall, Legend legend) {
	}

	/**
	 * The hall and Legend a site may hold for {@code player} at {@code where}: the nearest hall within {@link #REACH} the
	 * player owns or is a friend of the owner of, whose village meets the conditions of a Legend whose {@code found} way
	 * names {@code site}, with its slot free and nobody already waiting for it at a camp.
	 */
	public static Optional<Match> qualifying(ServerLevel level, ServerPlayer player, String site, BlockPos where) {
		List<Legend> legends = Legends.all().stream().filter(l -> l.ways("found").stream().anyMatch(w -> names(w, site))).toList();
		if (legends.isEmpty()) {
			return Optional.empty();
		}
		LegendRecord record = LegendRecord.get(level);
		List<BlockPos> halls = new ArrayList<>();
		for (Caravans.Village v : Caravans.Data.get(level).villages()) {
			if (v.hall().distSqr(where) <= (double) REACH * REACH) {
				halls.add(v.hall());
			}
		}
		halls.sort(Comparator.comparingDouble(h -> h.distSqr(where)));
		Friends friends = Friends.get(level.getServer());
		for (BlockPos hall : halls) {
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || entity.owner() == null
				|| !friends.mayDirect(entity.owner(), player.getUUID())) {
				continue;
			}
			for (Legend legend : legends) {
				if (record.captives().stream().anyMatch(c -> c.id().equals(legend.id()))) {
					continue; // already waiting at a camp, or on their way
				}
				boolean met = true;
				for (Condition c : legend.conditions()) {
					met &= c.met(level, hall);
				}
				if (met && LegendSlots.whyNot(level, hall, legend, null).isEmpty()) {
					return Optional.of(new Match(hall, legend));
				}
			}
		}
		return Optional.empty();
	}

	private static boolean names(JsonObject way, String site) {
		return way.has("site") && site.equals(way.get("site").getAsString());
	}

	/**
	 * {@code player} stands in the structure start {@code key} of {@code site} (its whole box, and {@code anchor}: the
	 * tower or the portal): when it hasn't been used and the player qualifies, the camp is set down with its Legend, and
	 * the start is marked used. Returns the Legend, or null.
	 */
	@Nullable
	public static Villager offer(ServerLevel level, ServerPlayer player, String site, String key, BoundingBox whole, BoundingBox anchor, RandomSource random) {
		LegendRecord record = LegendRecord.get(level);
		if (!ENABLED || !Legends.ENABLED || record.siteUsed(key)) {
			return null;
		}
		Match match = qualifying(level, player, site, player.blockPosition()).orElse(null);
		if (match == null) {
			return null;
		}
		BlockPos ground = "shipwreck".equals(site) ? beach(level, whole) : beside(level, anchor);
		if (ground == null) {
			return null; // nowhere to set it down (yet): the start stays free
		}
		Villager legend = place(level, site(site), match, ground, random);
		if (legend == null) {
			return null;
		}
		record.useSite(key);
		Chat.chat(player, Component.translatable("message.aliveworkplace.legend_site.found." + site, legend.getDisplayName())
			.withStyle(ChatFormatting.GOLD));
		AliveWorkplace.LOG.info("Legend {} found at {} ({}) for the village hall at {}", match.legend().id(), ground, key, match.hall());
		return legend;
	}

	/** Beside the {@code anchor} (a portal, an outpost's tower): the side whose ground is nearest the anchor's foot. */
	@Nullable
	static BlockPos beside(ServerLevel level, BoundingBox anchor) {
		BlockPos center = anchor.getCenter();
		int dx = anchor.getXSpan() / 2 + 5;
		int dz = anchor.getZSpan() / 2 + 5;
		BlockPos best = null;
		for (BlockPos column : List.of(center.offset(dx, 0, 0), center.offset(-dx, 0, 0), center.offset(0, 0, dz), center.offset(0, 0, -dz))) {
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
				continue;
			}
			if (best == null || Math.abs(ground.getY() - anchor.minY()) < Math.abs(best.getY() - anchor.minY())) {
				best = ground;
			}
		}
		return best;
	}

	/** The nearest beach to a wreck: dry ground at sea level (or a little above), searched outwards in rings. */
	@Nullable
	static BlockPos beach(ServerLevel level, BoundingBox wreck) {
		BlockPos center = wreck.getCenter();
		int sea = level.getSeaLevel();
		for (int r = 8; r <= 64; r += 4) {
			for (int i = 0; i < 16; i++) {
				double angle = Math.PI * 2 * i / 16;
				BlockPos column = center.offset((int) Math.round(Math.cos(angle) * r), 0, (int) Math.round(Math.sin(angle) * r));
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				if (ground.getY() >= sea - 1 && ground.getY() <= sea + 3 && level.getFluidState(ground).isEmpty()
					&& level.getFluidState(ground.above()).isEmpty() && level.getBlockState(ground).isSolid()) {
					return ground;
				}
			}
		}
		return null;
	}

	/** Sets the site's camp down with its middle at {@code ground} and its Legend in it, waiting (as {@code BanditCamps.found}). */
	@Nullable
	public static Villager place(ServerLevel level, Site site, Match match, BlockPos ground, RandomSource random) {
		StructureTemplate template = level.getStructureManager().get(site.camp()).orElse(null);
		if (template == null) {
			return null;
		}
		Rotation rotation = Rotation.getRandom(random);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		var size = template.getSize(rotation);
		BlockPos origin = template.getZeroPositionWithTransform(ground.offset(-size.getX() / 2, 0, -size.getZ() / 2), Mirror.NONE, rotation);
		BoundingBox box = template.getBoundingBox(settings, origin);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(p).isAir()) {
				level.removeBlock(p, false); // the grass, a bush
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y > box.minY() - 4; y--) { // no floating ground
					BlockPos p = new BlockPos(x, y, z);
					if (!level.getBlockState(p).canBeReplaced()) {
						break;
					}
					level.setBlock(p, ("shipwreck".equals(site.name()) ? Blocks.SAND : Blocks.DIRT).defaultBlockState(), 2);
				}
			}
		}
		template.placeInWorld(level, origin, origin, settings, random, 2);
		BlockPos spot = origin.offset(StructureTemplate.calculateRelativePosition(settings, site.spot()));
		Villager villager = EntityType.VILLAGER.create(level);
		if (villager == null) {
			return null;
		}
		Legend legend = match.legend();
		villager.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
		villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		VillagerType[] types = {VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.TAIGA,
			VillagerType.JUNGLE, VillagerType.SWAMP};
		villager.setVillagerData(villager.getVillagerData().setType(types[random.nextInt(types.length)]).setProfession(VillagerProfession.NITWIT));
		String name = legend.names().isEmpty() ? "" : legend.names().get(random.nextInt(legend.names().size()));
		villager.setCustomName(name.isEmpty() ? legend.titleText() : Component.translatable(name));
		villager.setPersistenceRequired();
		villager.setNoAi(true); // they wait at their camp (the prisoner can't leave it)
		villager.addTag(CAPTIVE_TAG);
		level.addFreshEntityWithPassengers(villager);
		LegendRecord.get(level).putCaptive(new LegendRecord.Captive(villager.getUUID(), legend.id(), site.name(), Optional.of(match.hall()),
			level.dimension().location().toString(), new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
			-1, -1));
		return villager;
	}

	/** The found Legend {@code villager} waiting at their camp (not yet freed), or empty. */
	public static Optional<LegendRecord.Captive> waiting(Villager villager) {
		if (!villager.getTags().contains(CAPTIVE_TAG) || !(villager.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		return LegendRecord.get(level).captive(villager.getUUID()).filter(c -> !c.freed());
	}

	/** Whether {@code villager} is a found Legend still at their camp (their clicks are ours). */
	public static boolean isCaptive(Villager villager) {
		return villager.getTags().contains(CAPTIVE_TAG);
	}

	/**
	 * A player right-clicks a found Legend at their camp: the traveller is free for a word; the castaway for a cooked
	 * meal (one is taken from the hand); the prisoner only says the bars hold them.
	 */
	public static InteractionResult use(ServerPlayer player, Villager villager, InteractionHand hand) {
		LegendRecord.Captive captive = waiting(villager).orElse(null);
		if (captive == null) {
			return InteractionResult.PASS;
		}
		switch (captive.site()) {
			case "ruined_portal" -> free((ServerLevel) villager.level(), villager, captive, player);
			case OldSage.SITE -> {
				return OldSage.use(player, villager, hand, captive); // the riddle quest (29.14)
			}
			case "shipwreck" -> {
				ItemStack held = player.getItemInHand(hand);
				if (held.is(MEALS)) {
					held.consume(1, player);
					free((ServerLevel) villager.level(), villager, captive, player);
				} else {
					Chat.chat(player, Component.translatable("message.aliveworkplace.legend_site.hungry", villager.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
				}
			}
			default -> Chat.chat(player, Component.translatable("message.aliveworkplace.legend_site.caged", villager.getDisplayName())
				.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	/** Before a player breaks a block: an iron bar of a prisoner's cage frees them (the bar still breaks). */
	public static boolean onBreak(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		if (!state.is(Blocks.IRON_BARS) || !(player instanceof ServerPlayer sp)) {
			return true;
		}
		for (LegendRecord.Captive c : LegendRecord.get(level).captives()) {
			if (!c.freed() && "outpost".equals(c.site()) && c.dimension().equals(level.dimension().location().toString()) && c.holds(pos)
				&& level.getEntity(c.villager()) instanceof Villager villager) {
				free(level, villager, c, sp);
			}
		}
		return true;
	}

	/** The Legend is free: they thank the player, walk off, and come to the hall the next morning as a guest. */
	public static void free(ServerLevel level, Villager villager, LegendRecord.Captive captive, ServerPlayer player) {
		LegendRecord.get(level).putCaptive(new LegendRecord.Captive(captive.villager(), captive.id(), captive.site(), captive.hall(), captive.dimension(),
			captive.min(), captive.max(), Chronicle.day(level), level.getGameTime()));
		Component village = captive.hall().map(h -> VillageHalls.name(level, h)).orElse(Component.literal("?"));
		Chat.chat(player, Component.translatable("message.aliveworkplace.legend_site.thanks." + captive.site(), villager.getDisplayName(), village)
			.withStyle(ChatFormatting.GOLD));
		villager.removeTag(CAPTIVE_TAG);
		villager.setNoAi(false);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 15, 0.4, 0.5, 0.4, 0.05);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2f, 1f);
		Vec3 away = villager.position().subtract(player.position()).multiply(1, 0, 1);
		Vec3 to = villager.position().add(away.lengthSqr() < 0.01 ? new Vec3(16, 0, 0) : away.normalize().scale(16));
		villager.getNavigation().moveTo(to.x, to.y, to.z, 0.6);
	}

	/** Freed Legends still at their camp walk on; out of sight (or after {@link #WALK_TICKS}) they're gone, to the hall. */
	public static void walk(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			LegendRecord record = LegendRecord.get(level);
			String dim = level.dimension().location().toString();
			for (LegendRecord.Captive c : record.captives()) {
				if (!c.dimension().equals(dim)) {
					continue;
				}
				Entity e = level.getEntity(c.villager());
				if (!c.freed()) {
					if (e == null && level.isPositionEntityTicking(c.min()) && level.isPositionEntityTicking(c.max())
						|| e != null && !e.isAlive()) {
						record.forgetCaptive(c.villager()); // killed at their camp: the Legend may be found again
					}
					continue;
				}
				if (e instanceof Villager v && v.isAlive()
					&& (level.getNearestPlayer(v, OUT_OF_SIGHT) == null || level.getGameTime() - c.freedAt() >= WALK_TICKS)) {
					level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 0.5, v.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
					v.discard();
				}
			}
		}
	}

	/**
	 * The hall's round: in the morning, a Legend freed on an earlier day for this village comes to the hall as a guest
	 * (unless a guest is staying: then the next morning).
	 */
	public static void arrive(ServerLevel level, BlockPos hall) {
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time < LegendGuests.MORNING_FROM || time >= LegendGuests.MORNING_TO || !(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		long today = Chronicle.day(level);
		LegendRecord record = LegendRecord.get(level);
		for (LegendRecord.Captive c : record.captives()) {
			if (!c.freed() || c.freedDay() >= today || !c.hall().equals(Optional.of(hall))
				|| !c.dimension().equals(level.dimension().location().toString())) {
				continue;
			}
			if (LegendGuests.staying(level, hall, entity, today)) {
				return;
			}
			Legend legend = Legends.get(c.id()).orElse(null);
			Entity left = level.getEntity(c.villager());
			if (left != null) {
				left.discard(); // still at their camp: they've gone on ahead
			}
			record.forgetCaptive(c.villager());
			if (legend == null || !LegendSlots.whyNot(level, hall, legend, null).isEmpty()) {
				AliveWorkplace.LOG.info("Found Legend {} won't come to the hall at {}: unknown, or their slot is taken", c.id(), hall);
				continue;
			}
			LegendGuests.come(level, hall, legend, hall, level.random, "found:" + c.site());
			return;
		}
	}

	/** Who is waiting for {@code legend} at a camp, or on their way (tests). */
	public static Optional<UUID> waitingFor(ServerLevel level, ResourceLocation legend) {
		return LegendRecord.get(level).captives().stream().filter(c -> c.id().equals(legend)).map(LegendRecord.Captive::villager).findFirst();
	}

	private LegendSites() {
	}
}
