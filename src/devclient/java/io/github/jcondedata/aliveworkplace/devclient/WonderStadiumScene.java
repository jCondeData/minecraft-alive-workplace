package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.powers.StadiumDaysPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.TournamentHostPower;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=wonder_stadium (ROADMAP 35.24, filmed with Cobblemon: the Stadium's file loads only with it): the Stadium's
 * time-lapse, the bowl rising stage by stage round its field, its festival's fireworks; then the next Stadium Day's
 * afternoon: the visiting trainers in the two boxes, the village's off-work villagers walking into the stands and
 * sitting down ({@code 21_wonder_stadium_stands}), a bout between the village's Expert and a visitor settled in chat and
 * the chronicle, and the player challenging a visitor to a real Cobblemon battle from the field
 * ({@code 22_wonder_stadium_battle}). Its checks: two to four visitors in the boxes before the challenge, and after it
 * every visitor but the challenged one still in its box while the challenged one battles the player near its own (B643:
 * the battle sends Pokémon out beside it, so it alone is not held to its box's spot), the stands full (at least
 * {@link #FULL} seated), the bout in the chronicle, the battle under way, and both powers on.
 */
final class WonderStadiumScene {
	/** Villagers who come to watch (the village's off-work folk, set round the stands' gangways). */
	static final int FANS = 48;
	/** Seated villagers that make the stands "full" for the check. */
	static final int FULL = 30;
	/** How near its box's spot a visitor stands to count as in its box. */
	static final double IN_BOX = 2.5;
	/** How near its box the challenged visitor stays while it battles: the leash {@code StadiumDays.keep} walks it back within. */
	static final double NEAR_BOX = 10;
	private static volatile long stadiumDay;
	private static volatile Villager challenged;

	static WonderTimelapse create() {
		WonderTimelapse[] lapse = new WonderTimelapse[1];
		lapse[0] = new WonderTimelapse(StadiumDays.STADIUM, "wonder_stadium").then(new WonderTimelapse.Epilogue() {
			@Override
			public int length() {
				return 420;
			}

			@Override
			public void tick(Minecraft mc, MinecraftServer server, int t) {
				epilogue(mc, server, lapse[0], t);
			}
		});
		return lapse[0];
	}

	private static Optional<WonderSite> site(ServerLevel level) {
		return WonderManager.get(level).sites().stream().filter(s -> s.wonder().equals(StadiumDays.STADIUM) && s.done()).findFirst();
	}

	static void epilogue(Minecraft mc, MinecraftServer server, WonderTimelapse lapse, int t) {
		if (t == 0) {
			server.execute(() -> afternoon(server, lapse));
		}
		if (t > 0 && t % 20 == 0 && t < 360) {
			server.execute(() -> StadiumDays.tick(server.overworld(), stadiumDay, server.overworld().getDayTime() % 24000L));
		}
		if (t == 200) {
			ScreenshotHarness.shot(mc, "21_wonder_stadium_stands");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				boxes(server); // as the stands' shot shows them, before the bout and the challenge
				// the first bout, an hour on: the village's Expert against the first visitor
				level.setDayTime((stadiumDay - 1) * 24000L + StadiumDays.FIRST_BOUT);
				StadiumDays.tick(level, stadiumDay, StadiumDays.FIRST_BOUT);
				battle(server);
			});
		}
		if (t == 330) {
			ScreenshotHarness.shot(mc, "22_wonder_stadium_battle");
		}
		if (t == 340) {
			server.execute(() -> checks(server));
		}
	}

	/** The next Stadium Day's afternoon: the village's trainers and its folk out, the visitors come, the camera over the field. */
	private static void afternoon(MinecraftServer server, WonderTimelapse lapse) {
		ServerLevel level = server.overworld();
		WonderSite site = site(level).orElse(null);
		Showcase.check(site != null, "the Stadium finished");
		if (site == null) {
			return;
		}
		StadiumDaysPower power = StadiumDays.power(level, site);
		Optional<StadiumDays.Spots> found = StadiumDays.spots(level, site);
		Showcase.check(power != null && found.isPresent(), "its Stadium Days power on, its boxes and seats found in the drawing");
		if (power == null || found.isEmpty()) {
			return;
		}
		StadiumDays.Spots spots = found.get();
		long day = Chronicle.day(level);
		while (!power.on(day)) {
			day++;
		}
		stadiumDay = day;
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.setDayTime((day - 1) * 24000L + StadiumDays.AFTERNOON + 200);
		// the village's own trainers, by the players' tunnel
		String[] home = {"Rhea", "Toby"};
		for (int i = 0; i < home.length; i++) {
			Villager v = EntityType.VILLAGER.create(level);
			BlockPos at = spots.ring().offset(-2 + 4 * i, 0, 6);
			v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(4 - i));
			v.setVillagerXp(net.minecraft.world.entity.npc.VillagerData.getMinXpPerLevel(4 - i) + 1);
			v.setCustomName(Component.literal(home[i]));
			level.addFreshEntity(v);
		}
		// the village's folk off work, at the stands' feet and in the gangways, who walk up to the seats
		List<BlockPos> seats = spots.seats();
		for (int i = 0; i < FANS && i < seats.size(); i++) {
			BlockPos seat = seats.get(i * Math.max(1, seats.size() / FANS));
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(seat.getX() + 0.5, seat.getY() + 0.1, seat.getZ() + 0.5, 0, 0);
			level.addFreshEntity(v);
		}
		StadiumDays.tick(level, day, StadiumDays.AFTERNOON + 200);
		// the camera high over the east box, looking across the field at the west stands and the covered stand
		Vec3 ring = Vec3.atBottomCenterOf(spots.ring());
		Vec3 east = Vec3.atBottomCenterOf(spots.boxes().get(1));
		Vec3 out = east.subtract(ring).normalize();
		ScreenshotHarness.hoverLookingAt(player(server), east.add(out.scale(4)).add(0, 14, 0), ring.subtract(out.scale(14)).add(0, 3, 0));
	}

	/** The player, given a team, goes down to the field and challenges the visitor in the west box. */
	private static void battle(MinecraftServer server) {
		ServerLevel level = server.overworld();
		WonderSite site = site(level).orElse(null);
		if (site == null) {
			return;
		}
		StadiumDays.Spots spots = StadiumDays.spots(level, site).orElseThrow();
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		if (visitors.isEmpty()) {
			return;
		}
		ServerPlayer player = player(server);
		var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String p : List.of("pikachu level=60", "lucario level=60", "garchomp level=60")) {
			party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p, " ", "=").create());
		}
		Villager visitor = visitors.get(0);
		challenged = visitor;
		Vec3 box = visitor.position();
		Vec3 ring = Vec3.atBottomCenterOf(spots.ring());
		Vec3 stand = box.add(ring.subtract(box).normalize().scale(7));
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		Vec3 d = box.subtract(stand);
		player.teleportTo(level, stand.x, stand.y, stand.z, (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90), 5);
		Trainers.challenge(player, visitor);
	}

	/** How far {@code v} stands from its box's spot. */
	private static double fromBox(StadiumDays.Spots spots, Villager v) {
		BlockPos spot = spots.boxes().get(Math.floorMod(StadiumDays.visitor(v).spot(), spots.boxes().size()));
		return v.position().distanceTo(Vec3.atBottomCenterOf(spot));
	}

	/** Before the bout and the challenge: two to four visitors, every one in its box. */
	private static void boxes(MinecraftServer server) {
		ServerLevel level = server.overworld();
		WonderSite site = site(level).orElse(null);
		if (site == null) {
			return;
		}
		StadiumDays.Spots spots = StadiumDays.spots(level, site).orElseThrow();
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		long inBoxes = visitors.stream().filter(v -> fromBox(spots, v) < IN_BOX).count();
		Showcase.check(visitors.size() >= 2 && visitors.size() <= 4 && inBoxes == visitors.size(),
			"a Stadium Day: " + visitors.size() + " visiting trainers, " + inBoxes + " in the boxes ("
				+ visitors.stream().map(v -> Trainers.title(v).getString()).toList() + ")");
	}

	private static void checks(MinecraftServer server) {
		ServerLevel level = server.overworld();
		WonderSite site = site(level).orElse(null);
		if (site == null) {
			return;
		}
		StadiumDays.Spots spots = StadiumDays.spots(level, site).orElseThrow();
		List<Villager> visitors = StadiumDays.visitors(level, site, spots);
		// B643: the challenged visitor is in the battle (its Pokémon sent out beside it), so it is counted apart; the others stay put
		List<Villager> others = visitors.stream().filter(v -> challenged == null || !v.getUUID().equals(challenged.getUUID())).toList();
		long othersIn = others.stream().filter(v -> fromBox(spots, v) < IN_BOX).count();
		Showcase.check(visitors.size() >= 2 && visitors.size() <= 4 && others.size() == visitors.size() - 1 && othersIn == others.size(),
			"after the challenge: " + visitors.size() + " visiting trainers, " + othersIn + " of the " + others.size()
				+ " not challenged still in the boxes (" + others.stream().map(v -> Trainers.title(v).getString()).toList() + ")");
		int seated = CupDays.seated(level, spots.ring()).size();
		Showcase.check(seated >= FULL, "the stands full: " + seated + " villagers off work sitting down to watch");
		long bouts = level.getBlockEntity(site.hall()) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity
			? entity.chronicle().stream().filter(e -> e.text().getString().startsWith("At the Stadium, ")).count() : 0;
		Showcase.check(bouts >= 1, "the bout of the village's trainer with a visitor in the chronicle (" + bouts + ")");
		ServerPlayer player = player(server);
		boolean fighting = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player) != null;
		Showcase.check(fighting, "the player's battle against " + (challenged == null ? "a visitor" : Trainers.title(challenged).getString()) + " under way");
		Villager there = challenged == null ? null : visitors.stream().filter(v -> v.getUUID().equals(challenged.getUUID())).findFirst().orElse(null);
		double away = there == null ? -1 : fromBox(spots, there);
		Showcase.check(there != null && away < NEAR_BOX, "the challenged visitor battles from its box, not gone off ("
			+ (there == null ? "not at the Stadium" : String.format(java.util.Locale.ROOT, "%.1f blocks from its spot", away)) + ")");
		Showcase.check(TournamentHostPower.at(level, site.hall()).isPresent() && StadiumDays.power(level, site) != null,
			"its powers are on: Stadium Days every second afternoon, the Festival Cup held here");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private WonderStadiumScene() {
	}
}
