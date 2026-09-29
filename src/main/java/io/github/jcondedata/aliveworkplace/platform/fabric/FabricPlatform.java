package io.github.jcondedata.aliveworkplace.platform.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PointOfInterestHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** {@link Platform} on Fabric (Loader + Fabric API). Found through {@code META-INF/services}. */
public final class FabricPlatform implements Platform {
	private final ItemStores items = new FabricItemStores();

	@Override
	public String loaderName() {
		return "Fabric";
	}

	// ---- mods and files --------------------------------------------------------------------------------------------

	@Override
	public boolean isModLoaded(String modId) {
		return FabricLoader.getInstance().isModLoaded(modId);
	}

	@Override
	@Nullable
	public String modVersion(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	@Override
	public boolean isModLoaded(String modId, String versionRange) {
		return FabricLoader.getInstance().getModContainer(modId).map(mod -> {
			try {
				return VersionPredicate.parse(versionRange).test(mod.getMetadata().getVersion());
			} catch (VersionParsingException e) {
				throw new IllegalArgumentException("Bad version range: " + versionRange, e);
			}
		}).orElse(false);
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	// ---- events ----------------------------------------------------------------------------------------------------

	@Override
	public void onServerStarting(Consumer<MinecraftServer> action) {
		ServerLifecycleEvents.SERVER_STARTING.register(action::accept);
	}

	@Override
	public void onServerTick(Consumer<MinecraftServer> action) {
		ServerTickEvents.END_SERVER_TICK.register(action::accept);
	}

	@Override
	public void onLevelTick(Consumer<ServerLevel> action) {
		ServerTickEvents.END_WORLD_TICK.register(action::accept);
	}

	@Override
	public void onEntityLoad(BiConsumer<Entity, ServerLevel> action) {
		ServerEntityEvents.ENTITY_LOAD.register(action::accept);
	}

	@Override
	public void onPlayerJoin(Consumer<ServerPlayer> action) {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> action.accept(handler.getPlayer()));
	}

	@Override
	public void onPlayerLeave(Consumer<ServerPlayer> action) {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> action.accept(handler.getPlayer()));
	}

	@Override
	public void onUseEntity(UseEntity handler) {
		UseEntityCallback.EVENT.register(handler::use);
	}

	@Override
	public void allowBreakBlock(BreakBlock check) {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> check.allow(level, player, pos, state));
	}

	@Override
	public void onUseBlock(UseBlock handler) {
		UseBlockCallback.EVENT.register(handler::use);
	}

	@Override
	public void allowUseItem(UseItem check) {
		UseItemCallback.EVENT.register((player, level, hand) -> check.allow(player, level, hand)
			? InteractionResultHolder.pass(player.getItemInHand(hand)) : InteractionResultHolder.fail(player.getItemInHand(hand)));
	}

	@Override
	public void allowAttackEntity(AttackEntity check) {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> check.allow(player, level, entity)
			? InteractionResult.PASS : InteractionResult.FAIL);
	}

	@Override
	public void allowDamage(AllowDamage check) {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(check::allow);
	}

	@Override
	public void afterDamage(AfterDamage action) {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(action::after);
	}

	@Override
	public void afterDeath(BiConsumer<LivingEntity, DamageSource> action) {
		ServerLivingEntityEvents.AFTER_DEATH.register(action::accept);
	}

	@Override
	public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> action) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> action.accept(dispatcher));
	}

	@Override
	public void onTagsLoaded(Runnable action) {
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> action.run());
	}

	@Override
	public void onDataReload(ResourceLocation id, ResourceManagerReloadListener listener) {
		ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return id;
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				listener.onResourceManagerReload(manager);
			}
		});
	}

	// ---- network ---------------------------------------------------------------------------------------------------

	@Override
	public <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.playS2C().register(type, codec);
	}

	@Override
	public <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
			BiConsumer<T, ServerPlayer> handler) {
		PayloadTypeRegistry.playC2S().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
	}

	@Override
	public void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}

	@Override
	public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	@Override
	public Collection<ServerPlayer> tracking(Entity entity) {
		return PlayerLookup.tracking(entity);
	}

	// ---- registration ----------------------------------------------------------------------------------------------

	@Override
	public PoiType registerPoi(ResourceLocation id, int tickets, int range, Block... blocks) {
		return PointOfInterestHelper.register(id, tickets, range, blocks);
	}

	@Override
	public void addTrades(VillagerProfession profession, int level, Consumer<List<VillagerTrades.ItemListing>> trades) {
		TradeOfferHelper.registerVillagerOffers(profession, level, trades::accept);
	}

	@Override
	public CreativeModeTab.Builder creativeTab() {
		return FabricItemGroup.builder();
	}

	@Override
	public GameRules.Key<GameRules.BooleanValue> booleanRule(String name, GameRules.Category category, boolean value) {
		return GameRuleRegistry.register(name, category, GameRuleFactory.createBooleanRule(value));
	}

	@Override
	public GameRules.Key<GameRules.IntegerValue> intRule(String name, GameRules.Category category, int value, int min, int max) {
		return GameRuleRegistry.register(name, category, GameRuleFactory.createIntRule(value, min, max));
	}

	@Override
	public <T> Attachment<T> attachment(ResourceLocation id, Codec<T> codec, @Nullable Supplier<T> initial) {
		return new FabricAttachment<>(AttachmentRegistry.create(id, builder -> {
			builder.persistent(codec);
			if (initial != null) {
				builder.initializer(initial);
			}
		}));
	}

	@Override
	public <T extends AbstractContainerMenu, D> MenuType<T> menuWithData(MenuWithData<T, D> factory,
			StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
		return new ExtendedScreenHandlerType<>(factory::create, codec);
	}

	@Override
	public <D> void openMenu(ServerPlayer player, MenuProvider menu, D data) {
		player.openMenu(new ExtendedScreenHandlerFactory<D>() {
			@Override
			public D getScreenOpeningData(ServerPlayer p) {
				return data;
			}

			@Override
			public Component getDisplayName() {
				return menu.getDisplayName();
			}

			@Override
			@Nullable
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return menu.createMenu(id, inventory, p);
			}

			@Override
			public boolean shouldCloseCurrentScreen() {
				return menu.shouldCloseCurrentScreen();
			}
		});
	}

	// ---- containers ------------------------------------------------------------------------------------------------

	@Override
	public ItemStores items() {
		return items;
	}
}
