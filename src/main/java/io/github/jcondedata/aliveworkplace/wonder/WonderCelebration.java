package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A Wonder finished (35.8): every player on the server is told; the village holds a festival that evening
 * ({@link Festivals#before}; the next evening when this one's is over) whose fireworks are a bigger show, rockets going
 * up round the square and from the Wonder itself ({@link #show}); the chronicles of the village and of every village
 * linked to it ({@link Wonders#linked}: its realm, or its caravan partners) record it; and the Village Map marks it with
 * a gold banner ({@code hall/VillageMaps}). Its powers come on with it ({@link Wonders#power}).
 */
public final class WonderCelebration {
	/** Rockets the Wonder's show sends up each festival round (2 s) while its fireworks are on: at the square, and from the Wonder. */
	public static final int ROCKETS = 3;
	/** Gold, then the festival's brightest colours. */
	static final int[] GOLD = {0xF5C542, 0xFFD966, 0xFFF2B3, 0xE8A33F, 0xFFFFFF};

	/** The Wonder {@code site} has just been finished, on {@code day}: told, celebrated and written down. */
	public static void finished(ServerLevel level, WonderSite site, long day) {
		Component name = Wonders.name(site.wonder());
		Component village = VillageHalls.name(level, site.hall());
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.aliveworkplace.wonder.finished_announce", village, name)
			.withStyle(ChatFormatting.GOLD), false);
		Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.finished", name,
			Math.max(1, day - site.begun() + 1)));
		io.github.jcondedata.aliveworkplace.hall.VillageMoments.award(level, site.hall(), io.github.jcondedata.aliveworkplace.hall.VillageMoments.WONDER, null); // the questbook's Wonder quest
		for (GlobalPos other : Wonders.linked(level, site.hall())) {
			ServerLevel there = level.getServer().getLevel(other.dimension());
			if (there != null) {
				Chronicle.atHall(there, other.pos(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.finished_elsewhere", village, name));
			}
		}
		long festival = 0;
		if (level.getBlockEntity(site.hall()) instanceof VillageHallBlockEntity entity) {
			long timeOfDay = level.getDayTime() % VillageNeeds.DAY;
			long evening = timeOfDay < Curfew.festivalEnd(entity) ? day : day + 1; // this one's over: tomorrow evening's
			Festivals.before(level, site.hall(), entity, evening); // that evening, even with a later festival planned (B605)
			festival = entity.festivalDay() == evening ? evening : entity.festivalDay();
		}
		WonderManager.get(level).setKept(site.id(), new WonderSite.Kept(100, day, false, festival));
		ColossusPlaque.write(level, WonderManager.get(level).site(site.id()).orElse(site)); // the Colossus's plaque filled in (35.22)
		Wonders.forgetPowers();
		GreatWallSentries.man(level, WonderManager.get(level).site(site.id()).orElse(site)); // the Great Wall's sentries take up their posts (35.16)
		BlockPos at = site.stone().orElse(site.hall());
		level.playSound(null, at, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 2f, 1f);
		level.playSound(null, site.hall(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1f);
	}

	/** The finished Wonder of the village round {@code hall} whose festival is today, if any: its fireworks are the bigger show. */
	public static Optional<WonderSite> celebrating(ServerLevel level, BlockPos hall) {
		if (!Wonders.ENABLED) {
			return Optional.empty();
		}
		long today = Chronicle.day(level);
		return WonderManager.get(level).in(hall).stream().filter(s -> s.done() && s.kept().festival() == today).findFirst();
	}

	/**
	 * One round of a Wonder's festival fireworks (the festival's own call, every 2 s while they're on): {@link #ROCKETS}
	 * rockets, gold-led and larger, from round the square and from the Wonder's top. Returns how many went up (0: no
	 * Wonder's festival here today, so the usual show).
	 */
	public static int show(ServerLevel level, BlockPos hall, BlockPos square, RandomSource random) {
		Optional<WonderSite> site = celebrating(level, hall);
		if (site.isEmpty()) {
			return 0;
		}
		List<BlockPos> from = new ArrayList<>();
		for (int i = 0; i < ROCKETS - 1; i++) {
			BlockPos column = square.offset(random.nextInt(25) - 12, 0, random.nextInt(25) - 12);
			from.add(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
		}
		from.add(top(level, site.get()).orElse(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, square)));
		for (BlockPos pos : from) {
			launch(level, pos, random);
		}
		return from.size();
	}

	/**
	 * The Village Map's gold banner for {@code site} (35.8): at the middle of the finished Wonder, at the hall's height,
	 * with its name; empty while it's going up, or outside the map's {@code half} blocks round {@code hall}.
	 */
	public static Optional<io.github.jcondedata.aliveworkplace.hall.VillageMaps.Mark> mark(ServerLevel level, WonderSite site, BlockPos hall, int half) {
		if (!site.done() || site.placement().isEmpty() || !Wonders.ENABLED) {
			return Optional.empty();
		}
		BlockPos centre = BlueprintLibrary.get(level, WonderBlueprints.idOf(site.wonder()))
			.map(b -> BlueprintOutline.bounds(site.placement().get(), b.size()).getCenter())
			.orElse(site.placement().get().origin());
		if (Math.abs(centre.getX() - hall.getX()) > half - 1 || Math.abs(centre.getZ() - hall.getZ()) > half - 1) {
			return Optional.empty();
		}
		return Optional.of(new io.github.jcondedata.aliveworkplace.hall.VillageMaps.Mark(new BlockPos(centre.getX(), hall.getY(), centre.getZ()),
			io.github.jcondedata.aliveworkplace.hall.VillageMaps.Kind.WONDER, Wonders.name(site.wonder())));
	}

	/** Above the middle of the Wonder's top: where its own rocket goes up from. */
	static Optional<BlockPos> top(ServerLevel level, WonderSite site) {
		if (site.placement().isEmpty()) {
			return Optional.empty();
		}
		Optional<Blueprint> whole = BlueprintLibrary.get(level, WonderBlueprints.idOf(site.wonder()));
		if (whole.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = BlueprintOutline.bounds(site.placement().get(), whole.get().size());
		BlockPos centre = box.getCenter();
		return Optional.of(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre).above());
	}

	/** A Wonder's rocket: a large gold ball with a trail and a twinkle, and a star or a burst in the festival's colours. */
	static FireworkRocketEntity launch(ServerLevel level, BlockPos at, RandomSource random) {
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
		int a = GOLD[random.nextInt(GOLD.length)];
		int b = GOLD[random.nextInt(GOLD.length)];
		FireworkExplosion gold = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(GOLD[0], a), IntList.of(b), true, true);
		FireworkExplosion second = new FireworkExplosion(random.nextBoolean() ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.BURST,
			IntList.of(0xE83F3F, 0x3FA7F5, a), IntList.of(0xFFFFFF), random.nextBoolean(), true);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(gold, second)));
		FireworkRocketEntity firework = new FireworkRocketEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, rocket);
		level.addFreshEntity(firework);
		return firework;
	}

	private WonderCelebration() {
	}
}
