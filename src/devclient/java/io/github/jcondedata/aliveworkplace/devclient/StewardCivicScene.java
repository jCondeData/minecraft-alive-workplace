package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.BitSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=steward_civic (ROADMAP 27.12): a village with Homes, Gardens and Civic zones, three Builders and a Steward set to
 * Run the village. Morning 1: two beds in the Homes zone lie in the dark and the village has no beauty, so he starts a
 * Street Lamp by the darkest bed and a Well in the Gardens. Morning 2: three children have come, so he starts a
 * Schoolhouse in Civic. The camera hangs over all three zones.
 */
final class StewardCivicScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final int DAYS = 2;
	/** Client ticks a day may take at most: the builders usually finish sooner. */
	private static final int DAY_TICKS = 2400;
	/** Client ticks a day takes at least: time for the morning's plot searches and the desk. */
	private static final int MIN_DAY_TICKS = 300;
	private int tick;
	private int day;
	private int dayStart;
	private volatile boolean started;
	private volatile boolean built;
	private volatile Villager steward;
	private volatile ItemStack plan;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 30) {
			server.execute(() -> appoint(server));
		}
		if (tick < 40 || day > DAYS) {
			return;
		}
		// The last day runs its full length: the schoolhouse is the big build and the GIF should see it finished
		if (day == 0 || (built && day < DAYS && tick - dayStart >= MIN_DAY_TICKS) || tick - dayStart >= DAY_TICKS) {
			if (day > 0) {
				ScreenshotHarness.shot(mc, String.format("%02d_steward_civic_day_%d", day, day));
				int done = day;
				boolean began = started;
				server.execute(() -> Showcase.check(began, "day " + done + ": the Steward started what the village asked for"));
			}
			day++;
			dayStart = tick;
			started = false;
			built = false;
			if (day > DAYS) {
				server.execute(() -> {
					ServerLevel level = server.overworld();
					Set<ResourceLocation> done = BuildSiteManager.get(level).finishedNear(level, HALL, 48).stream()
						.map(f -> StewardConditions.family(f.structure())).collect(Collectors.toSet());
					for (String b : new String[] {"street_lamp", "well", "schoolhouse"}) {
						Showcase.check(done.contains(ResourceLocation.fromNamespaceAndPath("aliveworkplace", b)), "a " + b + " went up (finished: " + done + ")");
					}
				});
				mc.stop();
				return;
			}
			int morning = day;
			server.execute(() -> morning(server, morning));
			return;
		}
		if (tick % 10 == 0) {
			server.execute(() -> round(server));
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** A new morning; before the second, three children have come to the village. */
	private void morning(MinecraftServer server, int n) {
		ServerLevel level = server.overworld();
		level.setDayTime(n * 24000L + 1000);
		if (n == 2) {
			for (int i = 0; i < 3; i++) {
				Villager child = EntityType.VILLAGER.spawn(level, HALL.offset(-2 + 2 * i, 0, 6), MobSpawnType.COMMAND);
				child.setAge(-24000 * 5);
			}
		}
	}

	/** His morning at the hall, as {@code StewardWork}'s planner does it: the wishes, their plots, then the desk. */
	private void round(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!started) {
			if (StewardWishes.rankIfDue(level, HALL)) {
				System.out.println("[steward_civic] wishes: " + StewardWishes.of(level, HALL).wishes().stream().map(w -> w.rule().getPath()).toList());
			}
			for (StewardWishes.Wish wish : StewardWishes.of(level, HALL).wishes()) {
				StewardWishes.plotFor(level, HALL, wish).ifPresent(request -> Plots.request(level, HALL, request));
			}
			StewardDesk.plan(level, steward, HALL);
			started = !StewardDesk.openSites(level, HALL).isEmpty();
			return;
		}
		built = StewardDesk.openSites(level, HALL).isEmpty();
	}

	/** A few ticks after the hall is placed (its point of interest is registered a tick later), he's made Steward. */
	private void appoint(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Stewards.appoint(player(server), steward, plan);
		Showcase.check(Stewards.stewardOf(level, HALL) == steward, "the seasoned Builder is made Steward");
		Showcase.check(StewardDesk.mode(level, HALL) == CityPlan.Mode.RUN, "the Steward runs the village");
	}

	private static void bed(ServerLevel level, BlockPos foot) {
		level.setBlockAndUpdate(foot, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		level.setBlockAndUpdate(foot.south(), Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static void paint(BitSet cells, int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
	}

	/**
	 * The hall (the player's, a Village): Homes east with two beds in the dark, Gardens west, Civic south; a lit row of
	 * beds to the north for everyone else (so no homes are wanted), a scholar at his desk (so no library), a guard at his
	 * post, a Storehouse with bread, three Builders and a Steward in Run the village: only light, beauty and (on day 2) a
	 * school are wanting.
	 */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, server);
		rules.getRule(ModGameRules.BUILD_DELAY).set(1, server);
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, server);
		// No courting or sickness in the scene: a chapel or a clinic would crowd the three builds it shows
		io.github.jcondedata.aliveworkplace.people.Couples.ENABLED = false;
		io.github.jcondedata.aliveworkplace.people.Sickness.ENABLED = false;
		level.setDayTime(1000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		hall.setRank(VillageRanks.Rank.VILLAGE); // a Steward may keep two builds open in a Village
		BitSet homes = new BitSet();
		paint(homes, 8, 30, -8, 12);
		BitSet gardens = new BitSet();
		paint(gardens, -30, -8, -8, 12);
		BitSet civic = new BitSet();
		paint(civic, -12, 12, 16, 40);
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").addZone("gardens", "Gardens 1", "").addZone("civic", "Civic 1", "")
			.paint(0, homes).paint(1, gardens).paint(2, civic).withMode(CityPlan.Mode.RUN));
		// Two beds out in the Homes zone with no light by them
		bed(level, HALL.offset(14, 0, -2));
		bed(level, HALL.offset(14, 0, 4));
		// Everyone else sleeps in the lit row to the north
		for (int i = 0; i < 12; i++) {
			BlockPos foot = HALL.offset(-12 + 2 * i, 0, -22);
			bed(level, foot);
			level.setBlockAndUpdate(foot.north(), Blocks.GLOWSTONE.defaultBlockState());
		}
		for (int i = 0; i < 3; i++) {
			BlockPos table = HALL.offset(-6 + 3 * i, 0, -6);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
			Builders.employ(level, builder, table);
		}
		BlockPos desk = HALL.offset(4, 0, -6);
		level.setBlockAndUpdate(desk, ModBlocks.SCHOLARS_DESK.defaultBlockState());
		Villager scholar = EntityType.VILLAGER.spawn(level, desk.south(), MobSpawnType.COMMAND);
		scholar.setVillagerData(scholar.getVillagerData().setProfession(ModVillagers.SCHOLAR));
		scholar.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), desk));
		scholar.setNoAi(true);
		// A guard at his post, so guards aren't short
		BlockPos post = HALL.offset(7, 0, -6);
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager guard = EntityType.VILLAGER.spawn(level, post.south(), MobSpawnType.COMMAND);
		guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD));
		guard.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), post));
		guard.setNoAi(true);
		// A Storehouse with bread in its chest, so food and storage are seen to
		BlockPos storehouse = HALL.offset(-9, 0, -6);
		level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
		level.setBlockAndUpdate(storehouse.west(), Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(storehouse.west()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			for (int slot = 0; slot < 4; slot++) {
				chest.setItem(slot, new ItemStack(net.minecraft.world.item.Items.BREAD, 64));
			}
		}
		// Two villagers who want no job (nitwits), so the morning isn't about work
		for (int i = 0; i < 2; i++) {
			Villager v = EntityType.VILLAGER.spawn(level, HALL.offset(-3 + 6 * i, 0, 3), MobSpawnType.COMMAND);
			v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
		}
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(Stewards.MIN_BUILDER_LEVEL));
		steward.setVillagerXp(70); // a seasoned Builder (27.1a)
		this.plan = plan;
		ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(HALL).add(0, 22, 34), Vec3.atCenterOf(HALL.offset(0, 0, 10)));
	}
}
