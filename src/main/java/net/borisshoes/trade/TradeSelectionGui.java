package net.borisshoes.trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.gui.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class TradeSelectionGui extends PagedGui<ServerPlayer> {
   private final int pageColor = 0xd9c682;
   private int tickCounter = 0;
   
   private static List<ServerPlayer> getEntries(MinecraftServer server, ServerPlayer except){
      ArrayList<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
      players.remove(except);
      return players;
   }
   
   public TradeSelectionGui(ServerPlayer player){
      super(MenuType.GENERIC_9x6, player, getEntries(player.level().getServer(),player));
      action1TextColor(ChatFormatting.AQUA.getColor().intValue());
      action2TextColor(ChatFormatting.GREEN.getColor().intValue());
      action3TextColor(ChatFormatting.YELLOW.getColor().intValue());
      itemElemBuilder((p) -> {
         GameProfile profile = p.getGameProfile();
         GuiElementBuilder builder = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(profile);
         builder.setName(Component.literal("").withStyle(ChatFormatting.LIGHT_PURPLE).append(p.getFeedbackDisplayName()));
         builder.addLoreLine(Component.translatable("gui.trade.select_trade",
               Component.translatable("gui.borislib.click").withColor(getAction1TextColor())
         ).withColor(getPrimaryTextColor()));
         return builder;
      });
      blankItem(GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.PAGE_BG,pageColor)).hideTooltip());
      
      elemClickFunction((p, index, clickType) -> {
         Trade.tradeInit(player,p);
         if(!clickType.shift) close();
      });
      curSort(PlayerSort.ALPHABETICAL);
      curFilter(PlayerFilter.NONE);
      GuiHelper.outlineGUI(this,0x0E9621, Component.empty());
      setTitle(Component.translatable("gui.trade.select_title"));
      buildPage();
      open();
   }
   
   @Override
   public void onTick(){
      tickCounter++;
      if(tickCounter >= 100){
         items(getEntries(player.level().getServer(),player));
         buildPage();
         tickCounter = 0;
      }
      super.onTick();
   }
   
   private static class PlayerSort extends GuiSort<ServerPlayer> {
      public static final List<PlayerSort> SORTS = new ArrayList<>();
      
      public static final PlayerSort ALPHABETICAL = new PlayerSort("gui.borislib.alphabetical", ChatFormatting.AQUA.getColor().intValue(),
            Comparator.comparing(ServerPlayer::getScoreboardName));
      
      private PlayerSort(String key, int color, Comparator<ServerPlayer> comparator){
         super(key, color, comparator);
         SORTS.add(this);
      }
      
      @Override
      protected List<PlayerSort> getList(){
         return SORTS;
      }
      
      public PlayerSort getStaticDefault(){
         return ALPHABETICAL;
      }
   }
   
   private static class PlayerFilter extends GuiFilter<ServerPlayer> {
      public static final List<PlayerFilter> FILTERS = new ArrayList<>();
      
      public static final PlayerFilter NONE = new PlayerFilter("gui.borislib.none", ChatFormatting.WHITE.getColor().intValue(), entry -> true);
      
      private PlayerFilter(String key, int color, Predicate<ServerPlayer> predicate){
         super(key, color, predicate);
         FILTERS.add(this);
      }
      
      @Override
      protected List<PlayerFilter> getList(){
         return FILTERS;
      }
      
      public PlayerFilter getStaticDefault(){
         return NONE;
      }
   }
}
