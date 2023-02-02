package net.borisshoes.trade.utils;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

public class Utils {
   public static MutableText simpleColoredText(String text, Formatting... formatting) {
      return MutableText.of(new LiteralTextContent(text)).setStyle(formatting != null ? Style.EMPTY.withFormatting(formatting) : Style.EMPTY);
   }
   
   public static void playSongToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> event, float vol, float pitch) {
      player.networkHandler.sendPacket(new PlaySoundS2CPacket(event, SoundCategory.PLAYERS, player.getPos().x,player.getPos().y, player.getPos().z, vol, pitch, 0));
   }
}
