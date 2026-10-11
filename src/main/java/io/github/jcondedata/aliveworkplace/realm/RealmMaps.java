package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Realm Map (ROADMAP 33.21): at any member's hall the Realm tab draws the realm on an empty map, as 33.8's charter
 * map draws the land ({@link VillageMaps#colors}) but on a vanilla map item, locked like a cartography table's copy.
 * It is scaled so every member fits ({@link #scaleFor}: vanilla scales 2 to 4, 512 to 2,048 blocks a side) and centred
 * on the middle of the members; the land the server has loaded is drawn as on a vanilla map and the rest stays plain
 * parchment (nothing is loaded for it). Every member has a banner in the realm's colour ({@link #colour}: its arms'
 * base, white without arms), the capital's named "Thornholm (capital)"; colonies founded by a member that are not in
 * the realm (lime), allied villages (light blue) and rivals in a feud (red) have their own. The caravan routes of the
 * members are dotted lines between the halls ({@link #ROUTE}); a colony whose settlers are on the road is a red cross
 * on its spot. The tooltip has the legend, as the Village Map's does. Config {@code realmMap}.
 */
public final class RealmMaps {
	/** Config {@code realmMap}: off, the Realm tab has no map button. */
	public static boolean ENABLED = true;
	/** The Realm tab's map button: the last slot, after the members and the villages to add. */
	public static final int BUTTON = io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE - 1;
	/** The smallest and largest scale (vanilla's: a pixel is 4 to 16 blocks). */
	public static final int MIN_SCALE = 2, MAX_SCALE = 4;
	/** Blocks kept clear between the outermost hall and the map's edge, so its banner is drawn whole. */
	static final int MARGIN = 24;
	/** A caravan route's dot: dark brown ink, a shade vanilla never draws land in. */
	public static final byte ROUTE = MapColor.COLOR_BROWN.getPackedId(MapColor.Brightness.LOWEST);
	/** A dot every this many pixels along a route. */
	public static final int DOT_EVERY = 3;
	/** No dots this close (pixels) to either hall, where its banner sits. */
	static final int DOT_CLEAR = 3;
	public static final DyeColor COLONY = DyeColor.LIME, ALLY = DyeColor.LIGHT_BLUE, RIVAL = DyeColor.RED;

	/** What a banner on the map stands for. */
	public enum Kind {
		MEMBER, COLONY, ALLY, RIVAL
	}

	/** A banner on the realm map. */
	public record Mark(BlockPos pos, Kind kind, DyeColor colour, Component name) {
	}

	/** The realm's colour: its arms' base, white without arms (as the Realm tab shows its banner). */
	public static DyeColor colour(RealmData.Realm realm) {
		return realm.arms().map(VillageBanners.Colours::base).orElse(DyeColor.WHITE);
	}

	/** The smallest scale from 2 to 4 at which a map centred on {@code centre} holds every one of {@code points} (4 if none does). */
	public static int scaleFor(BlockPos centre, List<BlockPos> points) {
		int reach = 0;
		for (BlockPos p : points) {
			reach = Math.max(reach, Math.max(Math.abs(p.getX() - centre.getX()), Math.abs(p.getZ() - centre.getZ())));
		}
		for (int scale = MIN_SCALE; scale < MAX_SCALE; scale++) {
			if (reach + MARGIN <= (64 << scale)) {
				return scale;
			}
		}
		return MAX_SCALE;
	}

	/** The middle of {@code points}' bounding box (at the first one's height). */
	public static BlockPos centre(List<BlockPos> points) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : points) {
			minX = Math.min(minX, p.getX());
			maxX = Math.max(maxX, p.getX());
			minZ = Math.min(minZ, p.getZ());
			maxZ = Math.max(maxZ, p.getZ());
		}
		return new BlockPos(Math.floorDiv(minX + maxX, 2), points.get(0).getY(), Math.floorDiv(minZ + maxZ, 2));
	}

	/** The banners of {@code realm} in {@code level}'s dimension: the members (the capital first), then colonies, allies and rivals. */
	public static List<Mark> marks(ServerLevel level, RealmData.Realm realm) {
		MinecraftServer server = level.getServer();
		RealmData data = RealmData.get(server);
		DyeColor colour = colour(realm);
		Map<GlobalPos, Mark> out = new LinkedHashMap<>();
		List<GlobalPos> members = Realms.members(realm);
		for (GlobalPos m : members) {
			if (!m.dimension().equals(level.dimension())) {
				continue;
			}
			Component name = Realms.name(server, m);
			boolean capital = m.equals(realm.capital());
			out.put(m, new Mark(m.pos(), Kind.MEMBER, colour, capital ? Component.translatable("item.aliveworkplace.realm_map.capital", name) : name));
		}
		for (RealmData.Founded f : data.founded()) {
			if (members.contains(f.mother()) && !members.contains(f.hall()) && f.hall().dimension().equals(level.dimension()) && !Realms.gone(server, f.hall())) {
				out.putIfAbsent(f.hall(), new Mark(f.hall().pos(), Kind.COLONY, COLONY, Realms.name(server, f.hall())));
			}
		}
		for (GlobalPos m : members) {
			for (GlobalPos ally : Pacts.allies(server, m)) {
				if (ally.dimension().equals(level.dimension()) && !members.contains(ally)) {
					out.putIfAbsent(ally, new Mark(ally.pos(), Kind.ALLY, ALLY, Realms.name(server, ally)));
				}
			}
		}
		for (Feuds.Feud f : Feuds.of(server, realm.capital())) {
			GlobalPos other = Feuds.side(server, f.by()).contains(realm.capital()) ? f.on() : f.by();
			for (GlobalPos rival : Feuds.side(server, other)) {
				if (rival.dimension().equals(level.dimension()) && !members.contains(rival)) {
					out.putIfAbsent(rival, new Mark(rival.pos(), Kind.RIVAL, RIVAL, Realms.name(server, rival)));
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	/** The caravan routes of the members in {@code level}: each pair of halls once, a member first. */
	public static List<BlockPos[]> routes(ServerLevel level, RealmData.Realm realm) {
		Caravans.Data caravans = Caravans.Data.get(level);
		List<BlockPos[]> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (GlobalPos m : Realms.members(realm)) {
			if (!m.dimension().equals(level.dimension())) {
				continue;
			}
			for (BlockPos other : caravans.partners(m.pos())) {
				String a = m.pos().asLong() + ":" + other.asLong(), b = other.asLong() + ":" + m.pos().asLong();
				if (seen.add(a) && !seen.contains(b)) {
					out.add(new BlockPos[] {m.pos(), other});
				}
			}
		}
		return out;
	}

	/** The colony orders of the members whose settlers are on the road. */
	public static List<RealmData.Order> onTheRoad(ServerLevel level, RealmData.Realm realm) {
		List<GlobalPos> members = Realms.members(realm);
		return RealmData.get(level.getServer()).orders().stream()
			.filter(o -> members.contains(o.mother()) && o.mother().dimension().equals(level.dimension())
				&& (o.state().equals(RealmData.ON_ROAD) || o.state().equals(RealmData.ARRIVING)))
			.toList();
	}

	/** The map pixel (0 to 127, or outside) of block {@code x} on a map centred on {@code centre} at {@code scale}. */
	public static int pixel(int x, int centre, int scale) {
		return Math.floorDiv(x - centre, 1 << scale) + 64;
	}

	/** Dots {@code DOT_EVERY} pixels apart from {@code a} to {@code b} on {@code colors}, none within {@code DOT_CLEAR} of either end or off the map. */
	public static void dots(byte[] colors, int ax, int az, int bx, int bz) {
		int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
		for (int s = DOT_CLEAR; s <= steps - DOT_CLEAR; s += DOT_EVERY) {
			int x = ax + Math.round((bx - ax) * s / (float) steps);
			int z = az + Math.round((bz - az) * s / (float) steps);
			if (x >= 0 && z >= 0 && x < 128 && z < 128) {
				colors[x + z * 128] = ROUTE;
			}
		}
	}

	/** The realm map of the realm {@code hall} is in, or null outside one. */
	@Nullable
	public static ItemStack map(ServerLevel level, BlockPos hall) {
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (realm == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		List<Mark> marks = marks(level, realm);
		List<BlockPos> members = marks.stream().filter(m -> m.kind() == Kind.MEMBER).map(Mark::pos).toList();
		BlockPos centre = centre(members.isEmpty() ? List.of(hall) : members);
		int scale = scaleFor(centre, members);
		byte[] colors = VillageMaps.colors(level, centre, 64 << scale, 1 << scale);
		for (BlockPos[] route : routes(level, realm)) {
			dots(colors, pixel(route[0].getX(), centre.getX(), scale), pixel(route[0].getZ(), centre.getZ(), scale),
				pixel(route[1].getX(), centre.getX(), scale), pixel(route[1].getZ(), centre.getZ(), scale));
		}
		List<MapBanner> banners = new ArrayList<>();
		Map<Kind, Integer> counts = new LinkedHashMap<>();
		for (Mark mark : marks) {
			banners.add(new MapBanner(mark.pos(), mark.colour(), Optional.of(mark.name())));
			counts.merge(mark.kind(), 1, Integer::sum);
		}
		CompoundTag tag = new CompoundTag();
		tag.putString("dimension", Ids.of(level.dimension()).toString());
		tag.putInt("xCenter", centre.getX());
		tag.putInt("zCenter", centre.getZ());
		tag.putByte("scale", (byte) scale);
		tag.putBoolean("trackingPosition", true);
		tag.putBoolean("unlimitedTracking", false);
		tag.putBoolean("locked", true);
		tag.putByteArray("colors", colors);
		MapBanner.LIST_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), banners).result()
			.ifPresent(t -> tag.put("banners", t));
		tag.put("frames", new ListTag());
		MapItemSavedData data = MapItemSavedData.load(tag, level.registryAccess());
		MapId id = level.getFreeMapId();
		level.setMapData(id, data);
		ItemStack stack = new ItemStack(Items.FILLED_MAP);
		stack.set(DataComponents.MAP_ID, id);
		List<RealmData.Order> road = onTheRoad(level, realm);
		for (int i = 0; i < road.size(); i++) {
			MapItemSavedData.addTargetDecoration(stack, road.get(i).spot(), "aliveworkplace_colony_" + i, MapDecorationTypes.RED_X);
		}
		Component title = Realms.title(server, realm);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.aliveworkplace.realm_map", title).withStyle(s -> s.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("item.aliveworkplace.realm_map.drawn", Chronicle.day(level)).withStyle(ChatFormatting.GRAY));
		lore.add(legend(Component.translatable("color.minecraft." + colour(realm).getName()), Component.translatable("item.aliveworkplace.realm_map.members"),
			counts.getOrDefault(Kind.MEMBER, 0)));
		for (Kind kind : List.of(Kind.COLONY, Kind.ALLY, Kind.RIVAL)) {
			if (counts.containsKey(kind)) {
				DyeColor c = kind == Kind.COLONY ? COLONY : kind == Kind.ALLY ? ALLY : RIVAL;
				lore.add(legend(Component.translatable("color.minecraft." + c.getName()),
					Component.translatable("item.aliveworkplace.realm_map." + kind.name().toLowerCase(java.util.Locale.ROOT)), counts.get(kind)));
			}
		}
		List<BlockPos[]> routes = routes(level, realm);
		if (!routes.isEmpty()) {
			lore.add(Component.translatable("item.aliveworkplace.realm_map.routes", routes.size()).withStyle(ChatFormatting.DARK_GRAY));
		}
		if (!road.isEmpty()) {
			lore.add(Component.translatable("item.aliveworkplace.realm_map.on_road", road.size()).withStyle(ChatFormatting.DARK_GRAY));
		}
		lore.add(Component.translatable("item.aliveworkplace.realm_map.parchment").withStyle(ChatFormatting.DARK_GRAY));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	private static Component legend(Component colour, Component what, int n) {
		return Component.translatable("item.aliveworkplace.village_map.legend", colour, what, n).withStyle(ChatFormatting.DARK_GRAY);
	}

	/** Draws the realm map for an empty map from {@code player}'s inventory (none needed in creative); returns what to tell them. */
	public static Realms.Result draw(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!ENABLED || Realms.realmOf(level, hall) == null) {
			return new Realms.Result(Realms.Refusal.OFF, Component.translatable("message.aliveworkplace.realm.map_off").withStyle(ChatFormatting.YELLOW));
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
			if (player.getInventory().getItem(i).is(Items.MAP)) {
				slot = i;
			}
		}
		if (slot < 0 && !player.getAbilities().instabuild) {
			return new Realms.Result(Realms.Refusal.NO_MONEY, Component.translatable("message.aliveworkplace.realm.map_needs_map").withStyle(ChatFormatting.YELLOW));
		}
		ItemStack map = map(level, hall);
		if (slot >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(slot).shrink(1);
		}
		if (!player.getInventory().add(map)) {
			player.drop(map, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 1f);
		return new Realms.Result(null, Component.translatable("message.aliveworkplace.realm.map_drawn", Realms.title(level.getServer(), Realms.realmOf(level, hall)))
			.withStyle(ChatFormatting.GREEN));
	}

	/** The tab's map button: a filled map, what it shows and what it takes. */
	static ItemStack icon() {
		return VillageHallScreen.icon(Items.FILLED_MAP, Component.translatable("screen.aliveworkplace.realm.map"), ChatFormatting.AQUA,
			VillageHallScreen.line("screen.aliveworkplace.realm.map_about", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.realm.map_how", ChatFormatting.DARK_GRAY));
	}

	private RealmMaps() {
	}
}
