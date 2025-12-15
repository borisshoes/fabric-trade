package net.borisshoes.trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.GuiInterface;
import net.borisshoes.borislib.gui.GraphicalItem;
import net.borisshoes.borislib.gui.GuiHelper;
import net.borisshoes.borislib.utils.SoundUtils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Timer;
import java.util.TimerTask;

public class TradeSession implements ContainerListener {
   private final ServerPlayer tFrom,tTo;
   private final Trade.TradeRequest tr;
   private final TradeGui guiFrom,guiTo;
   private final SimpleContainer inv;
   private final int[] yourSlots = {10,11,12,19,20,21,28,29,30,37,38,39};
   private final int[] theirSlots = {14,15,16,23,24,25,32,33,34,41,42,43};
   private final int[] borderSlots = {0,1,3,4,5,7,8,45,46,47,48,50,51,52,53,18,27,36,26,35,44};
   private final int[] middleSlots = {13,22,31,40};
   private boolean closing = false;
   private boolean fromReady = false;
   private boolean toReady = false;
   private boolean updating = false;
   private final ItemStack[] readyState;
   
   public TradeSession(ServerPlayer tFrom, ServerPlayer tTo, Trade.TradeRequest tr){
      this.tFrom = tFrom;
      this.tTo = tTo;
      this.tr = tr;
      
      inv = new SimpleContainer(24);
      inv.addListener(this);
      
      readyState = new ItemStack[24];
      for(int i = 0; i<24;i++){readyState[i]= ItemStack.EMPTY;}
   
      guiFrom = new TradeGui(MenuType.GENERIC_9x6,tFrom,this);
      guiTo = new TradeGui(MenuType.GENERIC_9x6,tTo,this);
      guiFrom.setTitle(Component.translatable("gui.trade.title",tTo.getScoreboardName()));
      guiTo.setTitle(Component.translatable("gui.trade.title",tFrom.getScoreboardName()));
      buildBorder();
      
      for(int i=0; i<12;i++){
         guiFrom.setSlotRedirect(yourSlots[i], new Slot(inv,i,0,0));
         guiFrom.clearSlot(theirSlots[i]);
         
         guiTo.setSlotRedirect(yourSlots[i], new Slot(inv,i+12,0,0));
         guiTo.clearSlot(theirSlots[i]);
      }
      
      guiTo.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.confirm").withStyle(ChatFormatting.RED)));
      guiFrom.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.confirm").withStyle(ChatFormatting.RED)));
      guiTo.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.waiting").withStyle(ChatFormatting.RED)));
      guiFrom.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.waiting").withStyle(ChatFormatting.RED)));
      fromReady = false;
      toReady = false;
      
      GuiInterface curToGui = GuiHelpers.getCurrentGui(tTo);
      if(curToGui != null){
         curToGui.close();
      }
      GuiInterface curFromGui = GuiHelpers.getCurrentGui(tFrom);
      if(curFromGui != null){
         curFromGui.close();
      }
      
      guiFrom.open();
      guiTo.open();
   
      SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_GUITAR,1f,.5f);
      SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_GUITAR,1f,.5f);
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
         @Override
         public void run() {
            SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_GUITAR,1f,1f);
            SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_GUITAR,1f,1f);
         }
      }, 500);
   }
   
   public void buildBorder(){
      final int borderColor = 0x0EB22B;
      final int separatorColor = 0x638E68;
      GuiHelper.outlineGUI(guiFrom,borderColor, Component.literal(""));
      GuiHelper.outlineGUI(guiTo,borderColor, Component.literal(""));
      guiFrom.setSlot(4,GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP_CONNECTOR, borderColor)).hideTooltip());
      guiTo.setSlot(4,GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_TOP_CONNECTOR, borderColor)).hideTooltip());
      
      for(int i=0;i<middleSlots.length;i++){
         guiFrom.setSlot(middleSlots[i],GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_VERTICAL, separatorColor)).hideTooltip());
         guiTo.setSlot(middleSlots[i],GuiElementBuilder.from(GraphicalItem.withColor(GraphicalItem.MENU_VERTICAL, separatorColor)).hideTooltip());
      }
      
      GameProfile fromProfile = tFrom.getGameProfile();
      GuiElementBuilder fromHead = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(fromProfile);
      guiFrom.setSlot(2,fromHead.setName(Component.translatable("gui.trade.your_items")));
      guiTo.setSlot(6,fromHead.setName(Component.translatable("gui.trade.players_items",tFrom.getScoreboardName())));
      
      GameProfile toProfile = tTo.getGameProfile();
      GuiElementBuilder toHead = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(toProfile);
      guiTo.setSlot(2,toHead.setName(Component.translatable("gui.trade.your_items")));
      guiFrom.setSlot(6,toHead.setName(Component.translatable("gui.trade.players_items",tTo.getScoreboardName())));
      
      guiTo.setSlot(49,GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.CANCEL)).setName(Component.translatable("gui.trade.cancel_trade").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
      guiFrom.setSlot(49,GuiElementBuilder.from(GraphicalItem.with(GraphicalItem.CANCEL)).setName(Component.translatable("gui.trade.cancel_trade").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
   }
   
   public void updateGuis(){
      for(int i=0;i<12;i++){
         guiFrom.setSlot(theirSlots[i],inv.getItem(i+12));
         guiTo.setSlot(theirSlots[i],inv.getItem(i));
      }
      //System.out.println("updating guis");
      
      finishUpdate();
   }
   
   public void checkReadyStatus(){
      checkReadyStatus(tFrom);
      checkReadyStatus(tTo);
   }
   
   public void checkReadyStatus(ServerPlayer player){
      if(player.equals(tFrom)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check first 12 slots
         for(int i=0;i<12;i++){
            if(!readyState[i].equals(inv.getItem(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }else if(player.equals(tTo)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check to ready status
         for(int i=12;i<24;i++){
            if(!readyState[i].equals(inv.getItem(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }
   }
   
   public void setUnready(){
      guiTo.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.confirm").withStyle(ChatFormatting.RED)));
      guiFrom.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.confirm").withStyle(ChatFormatting.RED)));
      guiTo.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.waiting").withStyle(ChatFormatting.RED)));
      guiFrom.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.YELLOW_CONFIRM)).setName(Component.translatable("gui.trade.waiting").withStyle(ChatFormatting.RED)));
      fromReady = false;
      toReady = false;
      SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_HAT,1f,.5f);
      SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_HAT,1f,.5f);
   }
   
   public void setReady(ServerPlayer player){
      if(player.equals(tFrom)&&!fromReady){
         guiFrom.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.GREEN_CONFIRM)).setName(Component.translatable("gui.trade.confirmed").withStyle(ChatFormatting.GREEN)));
         guiTo.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.GREEN_CONFIRM)).setName(Component.translatable("gui.trade.ready").withStyle(ChatFormatting.GREEN)));
         fromReady = true;
         SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_PLING,1f,1f);
         SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_PLING,1f,2f);
         for(int i=0;i<12;i++){
            readyState[i] = inv.getItem(i);
         }
      }else if(player.equals(tTo)&&!toReady){
         guiTo.setSlot(9,GuiElementBuilder.from(GraphicalItem.with(Trade.GREEN_CONFIRM)).setName(Component.translatable("gui.trade.confirmed").withStyle(ChatFormatting.GREEN)));
         guiFrom.setSlot(17,GuiElementBuilder.from(GraphicalItem.with(Trade.GREEN_CONFIRM)).setName(Component.translatable("gui.trade.ready").withStyle(ChatFormatting.GREEN)));
         toReady = true;
         SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_PLING,1f,2f);
         SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_PLING,1f,1f);
         for(int i=12;i<24;i++){
            readyState[i] = inv.getItem(i);
         }
      }
      
      if(fromReady&&toReady){
         finalizeTrade();
         
         Timer timer = new Timer();
         timer.schedule(new TimerTask() {
            @Override
            public void run() {
               SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_BELL,1.5f,1f);
               SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_BELL,1.5f,1f);
               Timer timer2 = new Timer();
               timer2.schedule(new TimerTask() {
                  @Override
                  public void run() {
                     SoundUtils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_BELL,1.5f,2f);
                     SoundUtils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_BELL,1.5f,2f);
                  }
               }, 500);
            }
         }, 500);
      }
   }
   
   public void finalizeTrade(){
      //Give Items
      for(int i=0; i<24;i++){
         ItemStack stack = inv.getItem(i);
         if(!stack.isEmpty()){
            ServerPlayer player;
            if(i<12){
               player = tTo;
            }else{
               player = tFrom;
            }
   
            ItemEntity itemEntity;
            boolean bl = player.getInventory().add(stack);
            if (!bl || !stack.isEmpty()) {
               itemEntity = player.drop(stack, false);
               if (itemEntity == null) continue;
               itemEntity.setNoPickUpDelay();
               itemEntity.setTarget(player.getUUID());
               continue;
            }
            stack.setCount(1);
            itemEntity = player.drop(stack, false);
            if (itemEntity != null) {
               itemEntity.makeFakeItem();
            }
         }
      }
      
      tFrom.displayClientMessage(Component.translatable("gui.trade.completed").withStyle(ChatFormatting.GREEN), false);
      tTo.displayClientMessage(Component.translatable("gui.trade.completed").withStyle(ChatFormatting.GREEN), false);
      closing = true;
      guiFrom.close();
      guiTo.close();
      
      Trade.completeSession(tr);
   }
   
   public void cancelTrade(ServerPlayer player){
      if(!closing){
         closing = true;
   
         //Give Items back
         for(int i=0; i<24;i++){
            ItemStack stack = inv.getItem(i);
            if(!stack.isEmpty()){
               ServerPlayer player1;
               if(i>=12){
                  player1 = tTo;
               }else{
                  player1 = tFrom;
               }
               
               if(player1.hasDisconnected()){
                  player1.drop(stack, false);
               }else{
                  ItemEntity itemEntity;
                  boolean bl = player1.getInventory().add(stack);
                  if (!bl || !stack.isEmpty()) {
                     itemEntity = player1.drop(stack, false);
                     if (itemEntity == null) continue;
                     itemEntity.setNoPickUpDelay();
                     itemEntity.setTarget(player1.getUUID());
                     continue;
                  }
                  stack.setCount(1);
                  itemEntity = player1.drop(stack, false);
                  if (itemEntity != null) {
                     itemEntity.makeFakeItem();
                  }
               }
            }
         }
         
         player.displayClientMessage(Component.translatable("gui.trade.you_cancelled").withStyle(ChatFormatting.RED), false);
         ServerPlayer other = player.equals(tFrom) ? tTo : tFrom;
         other.displayClientMessage(Component.translatable("gui.trade.they_cancelled", Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.RED),false);
   
         guiFrom.close();
         guiTo.close();
         
         SoundUtils.playSongToPlayer(tFrom, Holder.direct(SoundEvents.FIRE_EXTINGUISH) ,1f,.5f);
         SoundUtils.playSongToPlayer(tTo, Holder.direct(SoundEvents.FIRE_EXTINGUISH),1f,.5f);
      }
   }
   
   @Override
   public void containerChanged(Container inv){
      if(!updating){
         updating = true;
         checkReadyStatus();
         updateGuis();
      }
   }
   
   public void finishUpdate(){
      updating = false;
   }
}
