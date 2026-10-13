package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Colossus's plaque (ROADMAP 35.22): the row of wall signs over its front arch (drawn blank in 35.21) is filled in
 * once it is finished, in brass-yellow glowing letters, waxed so nobody edits it. Read left to right from the road:
 * "Raised in honor of / the Founder <name> / who founded / <the village> / on day <N> / Finished by its people on day
 * <M> / May it stand a thousand years". The name is the village's settled Founder's (M29), else the hall's owner's,
 * else left out ("...the Founder who founded..."); the founding day is the chronicle's first "founded" line, else
 * "long ago" (a chronicle that has forgotten it). Each sign is written once, when it stands blank and unwaxed: at the
 * finish, and on the round after (a sign broken and repaired, a part unloaded at the time). Nothing is saved but the
 * signs.
 */
public final class ColossusPlaque {
	public static final ResourceLocation COLOSSUS = AliveWorkplace.id("colossus");
	/** Ticks between rounds looking for a blank plaque. */
	public static final int EVERY = 100;
	/** The signs of each finished site's plaque, left to right as read from the front (worked out once per site). */
	private static final Map<UUID, List<BlockPos>> SIGNS = new ConcurrentHashMap<>();

	public static void init() {
		Platform.get().onLevelTick(ColossusPlaque::tick);
	}

	/**
	 * The plaque's signs of {@code site} as drawn, left to right for someone reading them: the wall signs of its blueprint
	 * in the row (the same facing and height) of the first; empty for a Wonder that isn't the Colossus or isn't placed.
	 */
	public static List<BlockPos> signs(ServerLevel level, WonderSite site) {
		if (!site.wonder().equals(COLOSSUS) || site.placement().isEmpty()) {
			return List.of();
		}
		List<BlockPos> known = SIGNS.get(site.id());
		if (known != null) {
			return known;
		}
		List<WonderUpkeep.Block> wall = WonderUpkeep.blocks(level, site).stream().filter(b -> b.state().getBlock() instanceof WallSignBlock).toList();
		if (wall.isEmpty()) {
			return List.of(); // its drawing isn't loaded: looked for again next round
		}
		Direction facing = wall.get(0).state().getValue(WallSignBlock.FACING);
		int y = wall.get(0).pos().getY();
		Direction right = facing.getOpposite().getClockWise(); // the reader faces the sign: their right hand
		List<BlockPos> row = new ArrayList<>();
		for (WonderUpkeep.Block b : wall) {
			if (b.pos().getY() == y && b.state().getValue(WallSignBlock.FACING) == facing) {
				row.add(b.pos().immutable());
			}
		}
		row.sort(Comparator.comparingInt(p -> p.getX() * right.getStepX() + p.getZ() * right.getStepZ()));
		List<BlockPos> out = List.copyOf(row);
		SIGNS.put(site.id(), out);
		return out;
	}

	/** The Founder named on the plaque: the village's settled Founder (M29), else the hall's owner; empty with neither. */
	public static Optional<Component> founder(ServerLevel level, BlockPos hall) {
		Optional<Villager> founder = Legends.in(level, hall, Founder.ID);
		if (founder.isPresent()) {
			Villager v = founder.get();
			return Optional.of((v.getCustomName() != null ? v.getCustomName() : v.getName()).copy());
		}
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.ownerName() != null && !entity.ownerName().isEmpty()) {
			return Optional.of(Component.literal(entity.ownerName()));
		}
		return Optional.empty();
	}

	/** The day the village round {@code hall} was founded, from its chronicle's first "founded" line (a colony it sent out doesn't count). */
	public static OptionalLong foundedOn(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return OptionalLong.empty();
		}
		return entity.chronicle().stream()
			.filter(e -> e.kind() == Chronicle.Kind.FOUNDED)
			.filter(e -> !(e.text().getContents() instanceof TranslatableContents t && t.getKey().equals("chronicle.aliveworkplace.colony.mother")))
			.mapToLong(Chronicle.Entry::day).min();
	}

	/** The plaque's seven signs' text, left to right. */
	public static List<SignText> texts(ServerLevel level, WonderSite site) {
		Optional<Component> founder = founder(level, site.hall());
		Component village = VillageHalls.name(level, site.hall()).copy().withStyle(ChatFormatting.BOLD);
		OptionalLong founded = foundedOn(level, site.hall());
		long finished = site.finished().orElse(Chronicle.day(level));
		List<SignText> out = new ArrayList<>();
		out.add(sign(line("raised"), line("honor")));
		out.add(sign(line("the_founder"), founder.map(n -> (Component) n.copy().withStyle(ChatFormatting.BOLD)).orElse(Component.empty())));
		out.add(sign(line("who_founded"), Component.empty()));
		out.add(sign(village, Component.empty()));
		out.add(founded.isPresent() ? sign(Component.translatable("sign.aliveworkplace.colossus.on_day", founded.getAsLong()), Component.empty())
			: sign(line("long_ago"), Component.empty()));
		out.add(sign(line("finished_by"), line("its_people"), Component.translatable("sign.aliveworkplace.colossus.on_day", finished)));
		out.add(sign(line("may_it_stand"), line("a_thousand"), line("years")));
		return out;
	}

	private static Component line(String key) {
		return Component.translatable("sign.aliveworkplace.colossus." + key);
	}

	/** A sign's front: the lines from the second down, brass-yellow and glowing. */
	private static SignText sign(Component... lines) {
		SignText text = new SignText().setColor(DyeColor.YELLOW).setHasGlowingText(true);
		for (int i = 0; i < lines.length && i < 3; i++) {
			text = text.setMessage(i + 1, lines[i]);
		}
		return text;
	}

	/**
	 * Writes the finished {@code site}'s plaque onto each of its signs that stands loaded, blank and unwaxed. Returns how
	 * many were written (0 for a Wonder that isn't the Colossus, or one going up).
	 */
	public static int write(ServerLevel level, WonderSite site) {
		if (!site.done()) {
			return 0;
		}
		List<BlockPos> signs = signs(level, site);
		if (signs.isEmpty()) {
			return 0;
		}
		List<SignText> texts = null;
		int written = 0;
		// a row of other than seven (a drawing changed by a server owner): the middle signs take the middle lines
		int offset = (signs.size() - 7) / 2;
		for (int i = 0; i < signs.size(); i++) {
			BlockPos pos = signs.get(i);
			if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || sign.isWaxed() || !blank(sign)) {
				continue;
			}
			if (texts == null) {
				texts = texts(level, site);
			}
			int at = i - offset;
			if (at < 0 || at >= texts.size()) {
				continue; // more signs than seven: those at the ends stay blank
			}
			sign.setText(texts.get(at), true);
			sign.setWaxed(true);
			sign.setChanged();
			BlockState state = level.getBlockState(pos);
			level.sendBlockUpdated(pos, state, state, 3);
			written++;
		}
		return written;
	}

	private static boolean blank(SignBlockEntity sign) {
		for (int i = 0; i < 4; i++) {
			if (!sign.getText(true).getMessage(i, false).getString().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	static void tick(ServerLevel level) {
		if (!Wonders.ENABLED || level.getGameTime() % EVERY != 0) {
			return;
		}
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (site.done() && site.wonder().equals(COLOSSUS)) {
				write(level, site);
			}
		}
	}

	/** Forgets the signs found in drawings (the files were read again). */
	public static void forget() {
		SIGNS.clear();
	}

	private ColossusPlaque() {
	}
}
