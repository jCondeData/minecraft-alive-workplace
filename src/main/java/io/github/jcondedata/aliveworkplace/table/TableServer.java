package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFormatException;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/** Server side of the Blueprint Table: library listing, materials, handing out blueprints, uploads. */
public final class TableServer {
	private static final double MAX_DISTANCE = 8.0;
	private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

	private record Upload(String fileName, byte[] data, int received) {
	}

	public static void init() {
		Platform.get().clientbound(TablePayloads.Open.TYPE, TablePayloads.Open.CODEC);
		Platform.get().clientbound(TablePayloads.Details.TYPE, TablePayloads.Details.CODEC);
		Platform.get().clientbound(TablePayloads.UploadResult.TYPE, TablePayloads.UploadResult.CODEC);

		Platform.get().serverbound(TablePayloads.RequestDetails.TYPE, TablePayloads.RequestDetails.CODEC, (payload, player) -> details(player, payload.id()));
		Platform.get().serverbound(TablePayloads.Take.TYPE, TablePayloads.Take.CODEC, (payload, player) -> take(player, payload.table(), payload.id()));
		Platform.get().serverbound(TablePayloads.UploadChunk.TYPE, TablePayloads.UploadChunk.CODEC, (payload, player) -> upload(player, payload));
		Platform.get().onPlayerLeave(player -> UPLOADS.remove(player.getUUID()));
	}

	/** Opens (or refreshes) the table screen for {@code player}. */
	public static void open(ServerPlayer player, BlockPos table) {
		Platform.get().send(player, new TablePayloads.Open(table, listing(player.level().getServer()), canUpload(player)));
	}

	public static List<TablePayloads.Entry> listing(MinecraftServer server) {
		List<TablePayloads.Entry> entries = new ArrayList<>();
		for (ResourceLocation id : BlueprintLibrary.list(server, false)) {
			BlueprintLibrary.get(server, id).ifPresent(bp ->
				entries.add(new TablePayloads.Entry(id, bp.size().getX(), bp.size().getY(), bp.size().getZ(), bp.solidBlockCount())));
		}
		// Our own blueprints first, then everything else alphabetically.
		entries.sort(Comparator.comparing((TablePayloads.Entry e) -> !e.id().getNamespace().equals(AliveWorkplace.MOD_ID) || e.id().getPath().contains("/"))
			.thenComparing(e -> e.id().toString()));
		return entries;
	}

	public static List<TablePayloads.Material> materials(Blueprint blueprint) {
		BuildPlan plan = BuildPlan.create(blueprint, new BlueprintData.Placement(ResourceLocation.withDefaultNamespace("overworld"),
			BlockPos.ZERO, Rotation.NONE, Mirror.NONE));
		// Chipped/Rechiseled variants are listed as the plain block they are made from.
		Map<Item, Integer> grouped = new java.util.LinkedHashMap<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			grouped.merge(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.key(e.getKey()), e.getValue(), Integer::sum);
		}
		List<TablePayloads.Material> out = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : grouped.entrySet()) {
			out.add(new TablePayloads.Material(BuiltInRegistries.ITEM.getKey(e.getKey()), e.getValue()));
		}
		out.sort(Comparator.comparingInt(TablePayloads.Material::count).reversed());
		return out;
	}

	public static boolean canUpload(ServerPlayer player) {
		return player.hasPermissions(2) || Rules.on(Players.level(player), ModGameRules.ALLOW_UPLOADS);
	}

	private static void details(ServerPlayer player, ResourceLocation id) {
		BlueprintLibrary.get(player.level().getServer(), id).ifPresent(bp -> Platform.get().send(player, new TablePayloads.Details(id, materials(bp))));
	}

	private static boolean atTable(ServerPlayer player, BlockPos table) {
		return player.level().getBlockState(table).is(ModBlocks.BLUEPRINT_TABLE)
			&& player.position().distanceToSqr(Vec3.atCenterOf(table)) <= MAX_DISTANCE * MAX_DISTANCE;
	}

	/** Hands out a blueprint. Returns false (and tells the player why) if they can't have it. */
	public static boolean take(ServerPlayer player, BlockPos table, ResourceLocation id) {
		if (!atTable(player, table)) {
			return false;
		}
		Optional<Blueprint> blueprint = BlueprintLibrary.get(player.level().getServer(), id);
		if (blueprint.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.blueprint.unknown", id.toString()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.getAbilities().instabuild) {
			Inventory inventory = player.getInventory();
			int slot = -1;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(ModItems.BLANK_BLUEPRINT)) {
					slot = i;
					break;
				}
			}
			if (slot < 0) {
				Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.need_blank").withStyle(ChatFormatting.YELLOW));
				return false;
			}
			inventory.removeItem(slot, 1);
		}
		player.getInventory().placeItemBackInInventory(BlueprintItem.create(id, blueprint.get().size()));
		player.level().playSound(null, table, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.took", Blueprints.displayName(id)));
		return true;
	}

	/** Handles one upload packet (public for tests). */
	public static void upload(ServerPlayer player, TablePayloads.UploadChunk chunk) {
		UUID uuid = player.getUUID();
		if (!canUpload(player) || !atTable(player, chunk.table())) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_denied"), null);
			return;
		}
		if (chunk.totalBytes() <= 0 || chunk.totalBytes() > BlueprintFiles.MAX_FILE_BYTES) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.error.file_too_big",
				chunk.totalBytes() / 1024, BlueprintFiles.MAX_FILE_BYTES / 1024), null);
			return;
		}
		if (!BlueprintImporter.hasSupportedExtension(chunk.fileName())) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.error.unknown_format"), null);
			return;
		}
		Upload current = UPLOADS.get(uuid);
		if (chunk.offset() == 0) {
			current = new Upload(chunk.fileName(), new byte[chunk.totalBytes()], 0);
		} else if (current == null || current.data().length != chunk.totalBytes() || current.received() != chunk.offset()
			|| !current.fileName().equals(chunk.fileName())) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_broken"), null);
			return;
		}
		if (chunk.offset() + chunk.data().length > chunk.totalBytes()) {
			UPLOADS.remove(uuid);
			reply(player, false, Component.translatable("message.aliveworkplace.table.upload_broken"), null);
			return;
		}
		System.arraycopy(chunk.data(), 0, current.data(), chunk.offset(), chunk.data().length);
		int received = chunk.offset() + chunk.data().length;
		if (received < chunk.totalBytes()) {
			UPLOADS.put(uuid, new Upload(current.fileName(), current.data(), received));
			return;
		}
		UPLOADS.remove(uuid);
		try {
			String folder = "uploads/" + BlueprintImporter.sanitizeFolder(player.getGameProfile().getName());
			BlueprintImporter.Imported imported = BlueprintImporter.importBytes(player.level().getServer(), folder, current.fileName(), current.data());
			reply(player, true, imported.summary(), imported.id());
			open(player, chunk.table());
		} catch (BlueprintFormatException e) {
			reply(player, false, Component.translatable("message.aliveworkplace.import.failed", current.fileName(), e.toComponent()), null);
		}
	}

	private static void reply(ServerPlayer player, boolean ok, Component message, ResourceLocation id) {
		Platform.get().send(player, new TablePayloads.UploadResult(ok, message, Optional.ofNullable(id)));
	}

	private TableServer() {
	}
}
