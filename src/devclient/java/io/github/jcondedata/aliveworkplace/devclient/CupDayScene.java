package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=cup_day (ROADMAP 28.19): Thornholm's Cup day, sped up. In the morning the delegates of Eastholm and Northby walk
 * in from the village edge on their home's side and the fair opens on the Arena's fair lane with the theme's wares; at
 * noon the villagers sit in the stands; the bouts go by (their results as the ring would fight them: without Cobblemon
 * the scene stands in for it) and the stands cheer Thornholm's win; after the final, fireworks in the theme's colours
 * and the champion in the chronicle; at dawn the delegates are gone. Checks: two delegates came, the fair sold the
 * theme's wares, villagers sat in the stands for the final, the hall's chronicle has the Cup, the delegates left by dawn.
 */
final class CupDayScene {
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos HALL = new BlockPos(12, -60, -14);
	private static final BlockPos EAST = new BlockPos(3000, -60, -14);
	private static final BlockPos NORTH = new BlockPos(12, -60, -3000);
	private static final ResourceLocation THEME = AliveWorkplace.id("showcase_cup_day");
	private int tick;
	private volatile boolean delegatesCame;
	private volatile boolean fairSells;
	private volatile boolean seated;
	private volatile boolean chronicled;
	private volatile boolean gone;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) { // the morning: delegates and the fair
			server.execute(() -> {
				ServerLevel level = server.overworld();
				CupData.Cup cup = cup(level);
				level.setDayTime((cup.day - 1) * 24000 + CupDays.ARRIVE);
				CupDays.tick(level);
				delegatesCame = CupDays.delegates(level, HALL).size() == 2;
				Arenas.Arena arena = arena(level);
				List<WanderingTrader> fair = level.getEntitiesOfClass(WanderingTrader.class, new AABB(arena.fairLane().orElseThrow()).inflate(8), WanderingTrader::isAlive);
				fairSells = !fair.isEmpty() && fair.stream().allMatch(t -> t.getOffers().stream().anyMatch(o -> o.getResult().is(Items.DIAMOND)));
				look(server, arena.fairLane().orElseThrow().offset(0, 0, -10), 9, 12);
			});
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_fair");
		}
		if (tick == 110) {
			server.execute(() -> look(server, HALL.offset(40, 0, 0), 12, 14));
		}
		if (tick == 150) {
			ScreenshotHarness.shot(mc, "02_delegate_arrives");
		}
		if (tick == 220) { // noon: the stands fill
			server.execute(() -> {
				ServerLevel level = server.overworld();
				CupData.Cup cup = cup(level);
				level.setDayTime((cup.day - 1) * 24000 + CupDays.STANDS + 200);
				for (Villager d : CupDays.delegates(level, HALL)) { // sped up: they've walked in
					Vec3 ring = Vec3.atBottomCenterOf(arena(level).ring());
					d.teleportTo(ring.x + (d.getX() > ring.x ? 4 : -4), ring.y, ring.z + 2);
				}
				CupDays.tick(level);
				look(server, arena(level).ring(), 10, 16);
			});
		}
		if (tick == 260) { // round 1, as the ring would fight it: Thornholm's Leader wins hers last
			server.execute(() -> {
				ServerLevel level = server.overworld();
				CupData.Cup cup = cup(level);
				UUID mira = mira(level);
				int mi = 0;
				for (int i = 0; i < cup.bracket.size(); i++) {
					if (cup.bracket.get(i) != null && cup.bracket.get(i).id().equals(mira)) {
						mi = i;
					}
				}
				int other = mi / 2 == 0 ? 2 : 0;
				cup.results.add(new CupData.Result(1, cup.bracket.get(other).id(), cup.bracket.get(other + 1).id()));
				cup.results.add(new CupData.Result(1, mira, cup.bracket.get(mi ^ 1).id()));
				CupDays.tick(level);
			});
		}
		if (tick == 280) {
			ScreenshotHarness.shot(mc, "03_stands");
		}
		if (tick == 320) { // the final
			server.execute(() -> {
				ServerLevel level = server.overworld();
				CupData.Cup cup = cup(level);
				CupDays.tick(level);
				seated = CupDays.seated(level, arena(level).ring()).size() >= 4;
				UUID mira = mira(level);
				UUID rival = cup.results.stream().filter(r -> !r.winner().equals(mira)).findFirst().orElseThrow().winner();
				level.setDayTime((cup.day - 1) * 24000 + 12500);
				cup.results.add(new CupData.Result(2, mira, rival));
				CupDays.tick(level);
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
				chronicled = hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP);
			});
		}
		if (tick == 345) {
			ScreenshotHarness.shot(mc, "04_champion");
		}
		if (tick == 400) { // dawn: the delegates have gone
			server.execute(() -> {
				ServerLevel level = server.overworld();
				CupData.Cup cup = cup(level);
				look(server, HALL.offset(0, 40, 0), 30, 20);
				level.setDayTime(cup.day * 24000 + 200);
				Cups.round(level, HALL, (VillageHallBlockEntity) level.getBlockEntity(HALL));
				CupDays.tick(level);
				gone = CupDays.delegates(level, HALL).isEmpty();
			});
		}
		if (tick == 460) {
			Showcase.check(delegatesCame, "two delegates came from their villages");
			Showcase.check(fairSells, "the fair's traders sold the theme's wares");
			Showcase.check(seated, "villagers sat in the stands for the final");
			Showcase.check(chronicled, "the hall's chronicle has the Cup");
			Showcase.check(gone, "the delegates were gone by dawn");
			mc.stop();
		}
	}

	private static CupData.Cup cup(ServerLevel level) {
		return CupData.get(level).cup(HALL);
	}

	private static UUID mira(ServerLevel level) {
		return level.getEntitiesOfClass(Villager.class, new AABB(HALL).inflate(48), v -> v.hasCustomName() && v.getCustomName().getString().equals("Mira"))
			.get(0).getUUID();
	}

	private static Arenas.Arena arena(ServerLevel level) {
		return Arenas.at(StarterBlueprints.ARENA_2.id(), new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE));
	}

	/** The camera {@code back} blocks south of and {@code up} above {@code at}, looking at it. */
	private static void look(MinecraftServer server, BlockPos at, int up, int back) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ScreenshotHarness.hoverLookingAt(player, Vec3.atBottomCenterOf(at).add(0, up, back), Vec3.atBottomCenterOf(at));
	}

	/** Arena II built and on record, Thornholm's hall (a Village), Eastholm and Northby far off on its trade routes, villagers to watch. */
	private static void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.getStructureManager().get(StarterBlueprints.ARENA_2.id()).orElseThrow()
			.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA_2.id(),
			new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setCustomName(Component.literal("Thornholm"));
		hall.setRank(VillageRanks.Rank.VILLAGE);
		level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new AABB(ORIGIN).inflate(96)).forEach(net.minecraft.world.entity.Entity::discard);
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(HALL, Component.literal("Thornholm"), List.of());
		for (Object[] v : new Object[][]{{EAST, "Eastholm", "Dara", 4, 300}, {NORTH, "Northby", "Ren", 3, 100}}) {
			BlockPos at = (BlockPos) v[0];
			data.setWants(at, Component.literal((String) v[1]), List.of());
			data.toggleRoute(HALL, at, 10);
			data.setLeader(at, new Caravans.Leader(UUID.nameUUIDFromBytes(((String) v[2]).getBytes()), (String) v[2], (int) v[3], (int) v[4], true));
		}
		Arenas.Arena arena = arena(level);
		villager(level, arena.ring().offset(-6, 0, 3), "Mira", ModVillagers.TRAINER_LEADER, 4);
		villager(level, arena.ring().offset(6, 0, 3), "Pip", ModVillagers.TRAINER, 2);
		for (int i = 0; i < 8 && i < arena.seats().size(); i++) { // the crowd, by the benches
			villager(level, arena.seats().get(i), null, null, 1);
		}
		CupThemes.setForTest(Map.of(THEME, new CupThemes.Theme(THEME, "cup.aliveworkplace.grand_cup", 1, "singles", 50, 3, List.of(), "any", List.of(),
			List.of(), 6000, 18000, List.of(new CupThemes.Ware(ResourceLocation.withDefaultNamespace("diamond"), 6),
				new CupThemes.Ware(ResourceLocation.withDefaultNamespace("golden_apple"), 4)), ResourceLocation.withDefaultNamespace("pumpkin_pie"),
			List.of(0xF5C542, 0xE83F3F), ResourceLocation.withDefaultNamespace("music_disc_cat"))));
		Cups.COBBLEMON = true;
		Cups.round(level, HALL, hall);
		CupData.Cup cup = cup(level);
		level.setDayTime((cup.day - 1) * 24000 + 500);
		Cups.round(level, HALL, hall);
		Showcase.check(cup.closed && cup.bracket.size() == 4 && cup.noCup.isEmpty(), "the Cup was drawn with four entrants");
		look(server, arena.ring(), 14, 22);
	}

	private static void villager(ServerLevel level, BlockPos at, String name, net.minecraft.world.entity.npc.VillagerProfession job, int lvl) {
		Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
		if (job != null) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
			v.setVillagerXp(10);
		}
		if (name != null) {
			v.setCustomName(Component.literal(name));
			v.setNoAi(true);
		}
	}
}
