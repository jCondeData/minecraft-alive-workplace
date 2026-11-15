package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleStartResult;
import com.cobblemon.mod.common.battles.SuccessfulBattleStart;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * Bug test (28.20, lane-d-1115-1308): a player already in a battle who clicks [I'm ready] is told in chat "The bout
 * couldn't start (are you already in a battle?). Click [I'm ready] again.", and then the command's own reply says "You're
 * ready. The bout begins!": two lines that contradict each other. The reply should not say the bout begins when it didn't.
 */
public class QaCupReadyReply1115d2CompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch AREA '200' '"qaCupReadyReply1115d2"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCupReadyReply1115d2")
	public void readyDoesntSayTheBoutBeginsWhenItCouldnt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		CupThemes.Theme theme = CupMatchCompatTests.openSingles();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 24));
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Cobblemon.INSTANCE.getStorage().getParty(p).add(PokemonProperties.Companion.parse("bulbasaur level=20", " ", "=").create());
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		p.teleportTo(ring.getX() + 0.5, ring.getY(), ring.getZ() + 0.5);
		Villager mira = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 12));
		mira.setNoAi(true);
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		mira.setCustomName(Component.literal("Mira"));
		PokemonEntity wild = new PokemonEntity(level, PokemonProperties.Companion.parse("caterpie level=3", " ", "=").create(), CobblemonEntities.POKEMON);
		wild.moveTo(p.getX() + 2, p.getY(), p.getZ());
		wild.setNoAi(true);
		level.addFreshEntity(wild);
		try {
			BattleStartResult own = BattleBuilder.INSTANCE.pve(p, wild);
			helper.assertTrue(own instanceof SuccessfulBattleStart, "setup: the wild battle didn't start: " + own);
			CupData.Entrant me = new CupData.Entrant(CupData.Kind.PLAYER, p.getUUID(), "Ash", 0, 0, host);
			CupData.Entrant her = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 2, 0, host);
			CupData.Entrant dara = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("qa1115d2r-Dara".getBytes()), "Dara", 3, 0, host);
			CupData.Entrant kai = new CupData.Entrant(CupData.Kind.HOST_TRAINER, UUID.nameUUIDFromBytes("qa1115d2r-Kai".getBytes()), "Kai", 3, 0, host);
			CupData.Cup cup = CupData.get(level).cup(host);
			cup.theme = theme.id();
			cup.day = -1;
			cup.closed = true;
			cup.entrants.addAll(List.of(me, her, dara, kai));
			cup.bracket.addAll(List.of(me, her, dara, kai));
			CupMatches.call(level, host, cup, new CupBouts.Pairing(1, me, her), ring, ring.west(10), ring.east(10));
			String reply = CupMatches.ready(p).getString();
			helper.assertTrue(cup.call != null && !cup.call.fighting, "setup: the bout started though the player is in a battle");
			helper.assertTrue(!reply.equals("You're ready. The bout begins!"),
				"the bout couldn't start (the player was told to click [I'm ready] again), but [I'm ready] replied: " + reply);
		} finally {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
			if (left != null) {
				left.end();
			}
			wild.discard();
			CupData.get(level).forget(host);
		}
		helper.succeed();
	}
}
