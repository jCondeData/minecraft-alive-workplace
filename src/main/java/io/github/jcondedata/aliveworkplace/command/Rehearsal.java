package io.github.jcondedata.aliveworkplace.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/**
 * The update rehearsal (ROADMAP 34.23, {@code REHEARSAL=true tools/packtest/run.sh}). {@code /workplace rehearsal seed}
 * lays out three villages with halls the way a server running the last release before 1.8 has them: a Hamlet of 6, a
 * Town of 22 with research and three married couples, and a City of 36 with every job a class gates in 1.8, children, a
 * build in progress, a rancher's daycare boarders, a Fossil Scientist reviving fossils and a parcel in the post.
 * {@code /workplace rehearsal report <label>} writes what each village holds (rank, treasury, research, each villager's
 * job, class and family, every container's items, the parcels) to {@code <world>/rehearsal/<label>.json}, which
 * {@code tools/packtest/rehearsal.py} compares. The same file is compiled into the old release's jar for the first half
 * (the run script copies it into a worktree of that tag), so it touches only what both jars have; what only 1.8 has
 * (the hall's {@code classesVersion}) it reads by reflection.
 *
 * <p>On the first ticks of a world that has a {@code before} report and no {@code opened} one, it writes the containers as
 * the new jar first loaded them ({@code opened-containers}), and from then until every hall is seeded it times each tick
 * and logs {@code Rehearsal seeding:} with the p50, p95 and worst. Only registered with {@code -Daliveworkplace.benchmark=true}.
 */
public final class Rehearsal {
	/** The three villages, west to east, 300 blocks apart (a hall reaches 64). */
	static final String[] NAMES = {"Hamlet", "Town", "City"};
	static final int SPACING = 300;
	static final int REACH = 44;
	static final String OWNER = "Jesse";
	static final UUID OWNER_ID = UUID.nameUUIDFromBytes("rehearsal-owner".getBytes(StandardCharsets.UTF_8));
	/** Every job a class gates in 1.8 (data/aliveworkplace/classes: artisan and burgher). */
	static final List<String> GATED = List.of("tinkerer", "chef", "netherworker", "nurse", "teacher", "shopkeeper", "innkeeper", "printer",
		"ball_smith", "tutor", "pokemon_trader", "fossil_scientist", "scholar", "undertaker", "jeweller", "trainer_leader");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static boolean watching;
	private static boolean openedWritten;
	private static final List<Long> seedingTicks = new ArrayList<>();
	private static int watchedTicks;
	private static int waitedTicks;

	private Rehearsal() {
	}

	public static void init() {
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		Platform.get().onServerTick(Rehearsal::tick);
		Platform.get().onRegisterCommands(dispatcher -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("rehearsal")
				.requires(s -> s.hasPermission(4))
				.then(Commands.literal("seed").executes(ctx -> seed(ctx.getSource())))
				.then(Commands.literal("report").then(Commands.argument("label", StringArgumentType.word())
					.executes(ctx -> report(ctx.getSource(), StringArgumentType.getString(ctx, "label"))))))));
	}

	static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("rehearsal");
	}

	// ---- seeding (the old jar) ----

	static int seed(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos origin = BlockPos.containing(source.getPosition());
		Rules.set(level, GameRules.RULE_DOMOBSPAWNING, false, server);
		JsonArray villages = new JsonArray();
		int[] sizes = {6, 22, 36};
		for (int i = 0; i < 3; i++) {
			int x = origin.getX() + SPACING * i;
			int z = origin.getZ();
			level.getChunk(x >> 4, z >> 4);
			BlockPos hall = new BlockPos(x, origin.getY(), z);
			String result = village(level, hall, i);
			JsonObject v = new JsonObject();
			v.addProperty("name", NAMES[i]);
			v.addProperty("x", hall.getX());
			v.addProperty("y", hall.getY());
			v.addProperty("z", hall.getZ());
			v.addProperty("size", sizes[i]);
			villages.add(v);
			AliveWorkplace.LOG.info("Rehearsal seed: {}", result);
		}
		write(server, "villages", villages.toString());
		source.sendSuccess(() -> Component.literal("Rehearsal: three villages seeded"), true);
		return 1;
	}

	private static String village(ServerLevel level, BlockPos hall, int index) {
		stations = 0;
		SeasonSoak.ground(level, hall);
		fence(level, hall);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<Villager> all = new ArrayList<>();
		VillageRanks.Rank rank = index == 0 ? VillageRanks.Rank.HAMLET : index == 1 ? VillageRanks.Rank.TOWN : VillageRanks.Rank.CITY;
		// Finished buildings on record, as many as the rank asks (the houses themselves aren't built: a rank on paper).
		for (int i = 0; i < rank.buildings; i++) {
			sites.recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement(level, hall.offset(-24 + 2 * i, 0, 40)), OWNER_ID);
		}
		beds(level, hall, index == 0 ? 8 : index == 1 ? 24 : 40);
		// Three chests a village: the store, the owner's valuables and his building stock.
		BlockPos store = hall.offset(-3, 0, 6);
		BlockPos valuables = hall.offset(0, 0, 6);
		BlockPos stock = hall.offset(3, 0, 6);
		for (BlockPos chest : List.of(store, valuables, stock)) {
			level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		}
		put(level, store, new ItemStack(Items.BREAD, 64), new ItemStack(Items.BAKED_POTATO, 32), new ItemStack(Items.COOKED_COD, 16),
			new ItemStack(Items.CARROT, 48));
		put(level, valuables, new ItemStack(Items.DIAMOND, 12 + index), new ItemStack(Items.EMERALD, 40), new ItemStack(Items.GOLD_INGOT, 20),
			new ItemStack(Items.ENCHANTED_BOOK, 2), new ItemStack(Items.NAME_TAG, 3));
		put(level, stock, new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.GLASS_PANE, 32),
			new ItemStack(Items.TORCH, 32));
		BlockPos spawnAt = hall.offset(0, 0, -6);
		station = hall.offset(-20, 0, 16);

		if (index == 0) {
			for (String job : List.of("farmer", "farmer", "fisherman", "shepherd")) {
				all.add(worker(level, spawnAt, job));
			}
			all.add(spawn(level, spawnAt));
			all.add(spawn(level, spawnAt));
		} else if (index == 1) {
			// A Town of 22: research under way, three married couples, two children.
			for (String job : List.of("aliveworkplace:scholar", "farmer", "farmer", "farmer", "librarian", "cleric", "armorer", "toolsmith", "mason",
				"fisherman", "shepherd", "cartographer", "aliveworkplace:builder", "fletcher")) {
				all.add(worker(level, spawnAt, job));
			}
			while (all.size() < 20) {
				all.add(spawn(level, spawnAt));
			}
			marry(level, all.get(1), all.get(2));
			marry(level, all.get(4), all.get(5));
			marry(level, all.get(14), all.get(15));
			all.add(child(level, spawnAt, all.get(1), all.get(2)));
			all.add(child(level, spawnAt, all.get(4), all.get(5)));
			entity.setResearch(new Research.State(Map.of("lore", 1, "architecture", 1, "logistics", 1), Optional.of("hearth"), 1200, true));
		} else {
			// A City of 36: every class-gated job, a builder mid-build, a rancher with boarders, a Fossil Scientist at work,
			// a postman with a parcel on the way, two couples and four children.
			for (String job : GATED) {
				Villager v = worker(level, spawnAt, "aliveworkplace:" + job);
				if (v != null) {
					all.add(v);
				}
			}
			for (String job : List.of("aliveworkplace:builder", "aliveworkplace:rancher", "aliveworkplace:postman", "farmer", "farmer", "farmer",
				"librarian", "cleric", "armorer", "butcher")) {
				all.add(worker(level, spawnAt, job));
			}
			while (all.size() < 32) {
				all.add(spawn(level, spawnAt));
			}
			marry(level, all.get(19), all.get(20));
			marry(level, all.get(26), all.get(27));
			all.add(child(level, spawnAt, all.get(19), all.get(20)));
			all.add(child(level, spawnAt, all.get(19), all.get(20)));
			all.add(child(level, spawnAt, all.get(26), all.get(27)));
			all.add(child(level, spawnAt, all.get(26), all.get(27)));
			entity.setResearch(new Research.State(Map.of("lore", 1, "architecture", 1, "logistics", 2, "fortification", 2, "warding", 1),
				Optional.empty(), 0, false));
			for (Villager v : all) {
				String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
				switch (job) {
					case "aliveworkplace:builder" -> {
						// A well 14 blocks east of the hall, its stock in the chest by it.
						BlockPos well = hall.offset(14, 0, -14);
						BlockPos chest = well.offset(-2, 0, 0);
						level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
						put(level, chest, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64),
							new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.STONE_BRICKS, 64));
						BuildSite site = sites.create(OWNER_ID, OWNER, StarterBlueprints.WELL.id(), placement(level, well));
						ModAttachments.BUILDER_JOB.set(v, new BuilderJob(site.id()));
					}
					case "aliveworkplace:rancher" -> {
						List<Daycare.Boarder> boarders = new ArrayList<>();
						for (String species : List.of("eevee", "pikachu")) {
							CompoundTag pokemon = new CompoundTag();
							pokemon.putString("Species", "cobblemon:" + species);
							pokemon.putInt("Level", 12);
							boarders.add(new Daycare.Boarder(UUID.nameUUIDFromBytes(species.getBytes(StandardCharsets.UTF_8)), OWNER, pokemon, 2L));
						}
						ModAttachments.DAYCARE.set(v, boarders);
					}
					case "aliveworkplace:fossil_scientist" -> ModAttachments.FOSSIL_REVIVALS.set(v, List.of(
						// Five days left: still reviving when the rehearsal ends.
						new Revival(OWNER_ID, OWNER, ResourceLocation.fromNamespaceAndPath("cobblemon", "kabuto"),
							List.of(ResourceLocation.fromNamespaceAndPath("cobblemon", "dome_fossil")), 6 * 24000, 5 * 24000)));
					case "aliveworkplace:postman" -> PostOffice.get(level.getServer()).post(OWNER_ID, OWNER,
						UUID.nameUUIDFromBytes("rehearsal-friend".getBytes(StandardCharsets.UTF_8)), "Alex",
						GlobalPos.of(level.dimension(), hall.offset(0, 0, 8)), level.getGameTime(),
						List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.WRITTEN_BOOK)));
					default -> {
					}
				}
			}
		}
		entity.setRank(rank);
		int adults = (int) all.stream().filter(v -> !v.isBaby()).count();
		entity.setNeeds(new VillageNeeds.Needs(adults, all.size(), all.size(), all.size(), all.size(), 0, 0, 1f));
		entity.setTreasury(2000);
		entity.setChanged();
		return NAMES[index] + " at " + hall.toShortString() + ": " + all.size() + " villagers, rank " + rank.name().toLowerCase();
	}

	private static void fence(ServerLevel level, BlockPos hall) {
		int r = REACH - 2;
		for (int d = -r; d <= r; d++) {
			for (BlockPos p : List.of(hall.offset(d, 0, -r), hall.offset(d, 0, r), hall.offset(-r, 0, d), hall.offset(r, 0, d))) {
				level.setBlock(p, Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
				level.setBlock(p.above(), Blocks.COBBLESTONE_WALL.defaultBlockState(), 2);
			}
		}
	}

	private static void beds(ServerLevel level, BlockPos hall, int count) {
		for (int i = 0; i < count; i++) {
			BlockPos foot = hall.offset(-18 + 4 * (i % 10), 0, -12 - 4 * (i / 10));
			BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
			level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
			level.setBlock(foot.north(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
			level.setBlockAndUpdate(foot.east(2), Blocks.TORCH.defaultBlockState());
		}
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE);
	}

	private static void put(ServerLevel level, BlockPos chest, ItemStack... stacks) {
		if (level.getBlockEntity(chest) instanceof Container box) {
			for (int i = 0; i < stacks.length; i++) {
				box.setItem(i, stacks[i]);
			}
		}
	}

	private static Villager spawn(ServerLevel level, BlockPos pos) {
		Villager v = EntityType.VILLAGER.spawn(level, pos, MobSpawnType.COMMAND);
		v.setPersistenceRequired();
		return v;
	}

	/** The next workstation's place in the village being seeded: rows of 14, 3 blocks apart, south of the hall. */
	private static BlockPos station;
	private static int stations;

	/**
	 * A villager in {@code job} at level 3 with their workstation (its first block state) in the row south of the hall, as
	 * a worker on a real server has: the hall counts only villagers with a job site as workers (their takings).
	 */
	private static Villager worker(ServerLevel level, BlockPos pos, String job) {
		VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(job));
		if (profession == null || profession == VillagerProfession.NONE) {
			AliveWorkplace.LOG.info("Rehearsal seed: no job {} in this jar", job);
			return spawn(level, pos);
		}
		Villager v = spawn(level, pos);
		v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(3));
		v.setVillagerXp(80);
		Holder.Reference<PoiType> poi = BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders().filter(h -> profession.heldJobSite().test(h))
			.findFirst().orElse(null);
		if (poi == null || poi.value().matchingStates().isEmpty()) {
			AliveWorkplace.LOG.info("Rehearsal seed: no workstation for {}", job);
			return v;
		}
		BlockState state = poi.value().matchingStates().stream().filter(st -> st == st.getBlock().defaultBlockState()).findFirst()
			.orElse(poi.value().matchingStates().iterator().next());
		BlockPos at = station.offset(3 * (stations % 14), 0, 3 * (stations / 14));
		stations++;
		level.setBlockAndUpdate(at, state);
		Jobs.employ(level, v, at, poi.key(), profession);
		return v;
	}

	private static void marry(ServerLevel level, Villager a, Villager b) {
		long today = level.getDayTime() / 24000;
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today, true));
	}

	private static Villager child(ServerLevel level, BlockPos pos, Villager mother, Villager father) {
		Villager v = spawn(level, pos);
		v.setBaby(true);
		v.setAge(-24000 * 12); // a child for the whole rehearsal
		ModAttachments.PARENTS.set(v, new Families.Parents(mother.getDisplayName(), father.getDisplayName(), "", "", false));
		return v;
	}

	// ---- the report (both jars) ----

	static int report(CommandSourceStack source, String label) {
		MinecraftServer server = source.getServer();
		JsonObject out = snapshot(server, true);
		write(server, label, GSON.toJson(out));
		AliveWorkplace.LOG.info("Rehearsal report {}: {} village(s)", label, out.getAsJsonArray("villages").size());
		source.sendSuccess(() -> Component.literal("Rehearsal report " + label + " written"), true);
		return 1;
	}

	static JsonObject snapshot(MinecraftServer server, boolean people) {
		ServerLevel level = server.overworld();
		JsonObject out = new JsonObject();
		out.addProperty("gameTime", level.getGameTime());
		out.addProperty("day", level.getDayTime() / 24000);
		JsonArray villages = new JsonArray();
		for (JsonObject v : villages(server)) {
			BlockPos hall = new BlockPos(v.get("x").getAsInt(), v.get("y").getAsInt(), v.get("z").getAsInt());
			JsonObject o = new JsonObject();
			o.addProperty("name", v.get("name").getAsString());
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				o.addProperty("rank", entity.rank().name());
				o.addProperty("treasury", entity.treasury());
				o.addProperty("treasuryTotal", entity.treasuryTotal());
				o.addProperty("research", entity.research().totalLevels());
				o.addProperty("classesVersion", classesVersion(entity));
				VillageNeeds.Needs needs = entity.needs();
				if (needs != null) {
					o.addProperty("wellbeing", needs.wellbeing());
					o.addProperty("adults", needs.adults());
					o.addProperty("fed", needs.fed());
					o.addProperty("housed", needs.housed());
					o.addProperty("lit", needs.lit());
				}
			}
			o.add("containers", containers(level, hall));
			if (people) {
				JsonArray list = new JsonArray();
				for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(hall).inflate(REACH, 32, REACH), Villager::isAlive)) {
					JsonObject p = new JsonObject();
					p.addProperty("uuid", villager.getUUID().toString());
					p.addProperty("job", BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString());
					p.addProperty("level", villager.getVillagerData().getLevel());
					p.addProperty("child", villager.isBaby());
					ResourceLocation cls = ModAttachments.SOCIAL_CLASS.get(villager);
					p.addProperty("class", cls == null ? "" : cls.getPath());
					Couples.Partner partner = ModAttachments.PARTNER.get(villager);
					p.addProperty("partner", partner == null ? "" : partner.id().toString());
					List<Daycare.Boarder> boarders = ModAttachments.DAYCARE.get(villager);
					p.addProperty("boarders", boarders == null ? 0 : boarders.size());
					List<Revival> revivals = ModAttachments.FOSSIL_REVIVALS.get(villager);
					p.addProperty("revivals", revivals == null ? 0 : revivals.size());
					p.addProperty("revivalLeft", revivals == null || revivals.isEmpty() ? -1 : revivals.get(0).left());
					p.addProperty("builderJob", ModAttachments.BUILDER_JOB.get(villager) != null);
					list.add(p);
				}
				o.add("villagers", list);
			}
			villages.add(o);
		}
		out.add("villages", villages);
		JsonArray parcels = new JsonArray();
		for (Parcel parcel : PostOffice.get(server).parcels()) {
			JsonObject p = new JsonObject();
			p.addProperty("id", parcel.id().toString());
			p.addProperty("items", parcel.count());
			p.addProperty("status", parcel.status().name());
			parcels.add(p);
		}
		out.add("parcels", parcels);
		JsonArray builds = new JsonArray();
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			JsonObject s = new JsonObject();
			s.addProperty("id", site.id().toString());
			s.addProperty("stage", site.stage().name());
			builds.add(s);
		}
		out.add("sites", builds);
		return out;
	}

	/** Every container within the village's ground: its items, by id, counted. */
	private static JsonObject containers(ServerLevel level, BlockPos hall) {
		JsonObject out = new JsonObject();
		for (int cx = (hall.getX() - REACH) >> 4; cx <= (hall.getX() + REACH) >> 4; cx++) {
			for (int cz = (hall.getZ() - REACH) >> 4; cz <= (hall.getZ() + REACH) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				List<BlockEntity> entities = new ArrayList<>(chunk.getBlockEntities().values());
				for (BlockEntity be : entities) {
					if (!(be instanceof Container box) || be instanceof VillageHallBlockEntity) {
						continue;
					}
					Map<String, Integer> items = new TreeMap<>();
					for (int i = 0; i < box.getContainerSize(); i++) {
						ItemStack stack = box.getItem(i);
						if (!stack.isEmpty()) {
							items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
						}
					}
					JsonObject o = new JsonObject();
					items.forEach(o::addProperty);
					BlockPos p = be.getBlockPos();
					out.add(p.getX() + "," + p.getY() + "," + p.getZ(), o);
				}
			}
		}
		return out;
	}

	private static boolean allLoaded(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (JsonObject v : villages(server)) {
			int x = v.get("x").getAsInt();
			int z = v.get("z").getAsInt();
			for (int cx = (x - REACH) >> 4; cx <= (x + REACH) >> 4; cx++) {
				for (int cz = (z - REACH) >> 4; cz <= (z + REACH) >> 4; cz++) {
					if (level.getChunkSource().getChunkNow(cx, cz) == null) {
						return false;
					}
				}
			}
		}
		return true;
	}

	/** The hall's {@code classesVersion} (1.8's), or -1 in a jar without it. */
	private static int classesVersion(VillageHallBlockEntity entity) {
		try {
			return (int) entity.getClass().getMethod("classesVersion").invoke(entity);
		} catch (ReflectiveOperationException e) {
			return -1;
		}
	}

	private static List<JsonObject> villages(MinecraftServer server) {
		List<JsonObject> out = new ArrayList<>();
		Path file = dir(server).resolve("villages.json");
		if (Files.exists(file)) {
			try {
				JsonArray array = GSON.fromJson(Files.readString(file), JsonArray.class);
				array.forEach(e -> out.add(e.getAsJsonObject()));
			} catch (IOException e) {
				AliveWorkplace.LOG.warn("Rehearsal: can't read {}", file, e);
			}
		}
		return out;
	}

	private static void write(MinecraftServer server, String label, String text) {
		try {
			Files.createDirectories(dir(server));
			Files.writeString(dir(server).resolve(label + ".json"), text);
		} catch (IOException e) {
			AliveWorkplace.LOG.warn("Rehearsal: can't write {}", label, e);
		}
	}

	// ---- the first ticks with the new jar ----

	private static void tick(MinecraftServer server) {
		if (!openedWritten) {
			Path dir = dir(server);
			if (!Files.exists(dir.resolve("before.json")) || Files.exists(dir.resolve("opened-containers.json"))) {
				openedWritten = true;
				return;
			}
			// The containers as this jar loaded them, once every chunk of the three villages is in (the forced chunks load
			// over the first ticks); the seeding is timed from then: the ticks before are the server starting up.
			waitedTicks++;
			if (!allLoaded(server) && waitedTicks < 2400) {
				return;
			}
			openedWritten = true;
			write(server, "opened-containers", GSON.toJson(snapshot(server, false)));
			AliveWorkplace.LOG.info("Rehearsal: the containers as this jar first loaded them are written (after {} ticks)", waitedTicks);
			watching = true;
			return;
		}
		if (!watching) {
			return;
		}
		long[] times = server.getTickTimesNanos();
		seedingTicks.add(times[(server.getTickCount() + times.length - 1) % times.length]);
		watchedTicks++;
		if (watchedTicks % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		boolean seeded = true;
		for (JsonObject v : villages(server)) {
			BlockPos hall = new BlockPos(v.get("x").getAsInt(), v.get("y").getAsInt(), v.get("z").getAsInt());
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && classesVersion(entity) < 1) {
				seeded = false;
			}
		}
		if (seeded || watchedTicks >= 24000) {
			watching = false;
			long[] sorted = seedingTicks.stream().mapToLong(Long::longValue).toArray();
			Arrays.sort(sorted);
			double p50 = sorted[sorted.length / 2] / 1e6;
			double p95 = sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))] / 1e6;
			double worst = sorted[sorted.length - 1] / 1e6;
			AliveWorkplace.LOG.info(String.format(java.util.Locale.ROOT,
				"Rehearsal seeding: %s after %d ticks; tick p50 %.1f ms, p95 %.1f ms, worst %.1f ms", seeded ? "every hall seeded" : "NOT every hall seeded",
				watchedTicks, p50, p95, worst));
		}
	}
}
