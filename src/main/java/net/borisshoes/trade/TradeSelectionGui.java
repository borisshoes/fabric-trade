package net.borisshoes.trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import net.borisshoes.borislib.gui.*;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class TradeSelectionGui extends PagedGui<ServerPlayerEntity> {
   private final int pageColor = 0xd9c682;
   private int tickCounter = 0;
   
   private static List<ServerPlayerEntity> getEntries(MinecraftServer server, ServerPlayerEntity except){
      ArrayList<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
      players.remove(except);
      return players;
   }
   
   public TradeSelectionGui(ServerPlayerEntity player){
      super(ScreenHandlerType.GENERIC_9X6, player, getEntries(player.getEntityWorld().getServer(),player));
      action1TextColor(Formatting.AQUA.getColorValue().intValue());
      action2TextColor(Formatting.GREEN.getColorValue().intValue());
      action3TextColor(Formatting.YELLOW.getColorValue().intValue());
      itemElemBuilder((p) -> {
         GameProfile profile = p.getGameProfile();
         GuiElementBuilder builder = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(profile);
         builder.setName(Text.literal("").formatted(Formatting.LIGHT_PURPLE).append(p.getStyledDisplayName()));
         builder.addLoreLine(Text.translatable("gui.trade.select_trade",
               Text.translatable("gui.borislib.click").withColor(getAction1TextColor())
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
      GuiHelper.outlineGUI(this,0x0E9621,Text.empty());
      setTitle(Text.translatable("gui.trade.select_title"));
      buildPage();
      open();
   }
   
   @Override
   public void onTick(){
      tickCounter++;
      if(tickCounter >= 100){
         items(getEntries(player.getEntityWorld().getServer(),player));
         buildPage();
         tickCounter = 0;
      }
      super.onTick();
   }
   
   private static class PlayerSort extends GuiSort<ServerPlayerEntity> {
      public static final List<PlayerSort> SORTS = new ArrayList<>();
      
      public static final PlayerSort ALPHABETICAL = new PlayerSort("gui.borislib.alphabetical", Formatting.AQUA.getColorValue().intValue(),
            Comparator.comparing(ServerPlayerEntity::getNameForScoreboard));
      
      private PlayerSort(String key, int color, Comparator<ServerPlayerEntity> comparator){
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
   
   private static class PlayerFilter extends GuiFilter<ServerPlayerEntity> {
      public static final List<PlayerFilter> FILTERS = new ArrayList<>();
      
      public static final PlayerFilter NONE = new PlayerFilter("gui.borislib.none", Formatting.WHITE.getColorValue().intValue(), entry -> true);
      
      private PlayerFilter(String key, int color, Predicate<ServerPlayerEntity> predicate){
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
