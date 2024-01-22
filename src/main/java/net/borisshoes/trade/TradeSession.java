package net.borisshoes.trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.GuiHelpers;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.GuiInterface;
import net.borisshoes.trade.gui.TradeGui;
import net.borisshoes.trade.gui.TradeInventory;
import net.borisshoes.trade.gui.TradeInventoryListener;
import net.borisshoes.trade.utils.Utils;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Timer;
import java.util.TimerTask;

public class TradeSession {
   private final ServerPlayerEntity tFrom,tTo;
   private final Trade.TradeRequest tr;
   private final TradeGui guiFrom,guiTo;
   private final TradeInventory inv;
   private final TradeInventoryListener listener;
   private final int[] yourSlots = {10,11,12,19,20,21,28,29,30,37,38,39};
   private final int[] theirSlots = {14,15,16,23,24,25,32,33,34,41,42,43};
   private final int[] borderSlots = {0,1,3,4,5,7,8,45,46,47,48,50,51,52,53,18,27,36,26,35,44};
   private final int[] middleSlots = {13,22,31,40};
   private boolean closing = false;
   private boolean fromReady = false;
   private boolean toReady = false;
   private final ItemStack[] readyState;
   private final Trade main;
   
   public TradeSession(ServerPlayerEntity tFrom, ServerPlayerEntity tTo, Trade.TradeRequest tr, Trade main){
      this.tFrom = tFrom;
      this.tTo = tTo;
      this.tr = tr;
      this.main = main;
      
      inv = new TradeInventory();
      listener = new TradeInventoryListener(this);
      inv.addListener(listener);
      
      readyState = new ItemStack[24];
      for(int i = 0; i<24;i++){readyState[i]=ItemStack.EMPTY;}
   
      guiFrom = new TradeGui(ScreenHandlerType.GENERIC_9X6,tFrom,this);
      guiTo = new TradeGui(ScreenHandlerType.GENERIC_9X6,tTo,this);
      guiFrom.setTitle(Text.literal("Trading with "+tTo.getNameForScoreboard()));
      guiTo.setTitle(Text.literal("Trading with "+tFrom.getNameForScoreboard()));
      
      for(int i=0; i<12;i++){
         guiFrom.setSlotRedirect(yourSlots[i], new Slot(inv,i,0,0));
         guiTo.setSlotRedirect(yourSlots[i], new Slot(inv,i+12,0,0));
      }
   
      buildBorder();
      guiTo.setSlot(9,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Confirm").formatted(Formatting.RED)));
      guiFrom.setSlot(9,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Confirm").formatted(Formatting.RED)));
      guiTo.setSlot(17,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Waiting for player...").formatted(Formatting.RED)));
      guiFrom.setSlot(17,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Waiting for player...").formatted(Formatting.RED)));
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
   
      Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,1f,.5f);
      Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,1f,.5f);
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
         @Override
         public void run() {
            Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,1f,1f);
            Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_GUITAR,1f,1f);
         }
      }, 500);
   }
   
   public void buildBorder(){
      for(int i=0;i<borderSlots.length;i++){
         guiFrom.setSlot(borderSlots[i],new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.empty()));
         guiTo.setSlot(borderSlots[i],new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Text.empty()));
      }
      for(int i=0;i<middleSlots.length;i++){
         guiFrom.setSlot(middleSlots[i],new GuiElementBuilder(Items.IRON_BARS).setName(Text.empty()));
         guiTo.setSlot(middleSlots[i],new GuiElementBuilder(Items.IRON_BARS).setName(Text.empty()));
      }
      GameProfile fromProfile = new GameProfile(tFrom.getUuid(),null);
      GuiElementBuilder fromHead = new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(fromProfile,tFrom.server);
      guiFrom.setSlot(2,fromHead.setName((Text.literal("Your Items"))));
      guiTo.setSlot(6,fromHead.setName((Text.literal(tFrom.getNameForScoreboard()+"'s Items"))));
   
      GameProfile toProfile = new GameProfile(tTo.getUuid(),null);
      GuiElementBuilder toHead = new GuiElementBuilder(Items.PLAYER_HEAD).setSkullOwner(toProfile,tTo.server);
      guiTo.setSlot(2,toHead.setName((Text.literal("Your Items"))));
      guiFrom.setSlot(6,toHead.setName((Text.literal(tTo.getNameForScoreboard()+"'s Items"))));
   
      guiTo.setSlot(49,new GuiElementBuilder(Items.BARRIER).setName(Text.literal("Cancel Trade").formatted(Formatting.DARK_RED,Formatting.BOLD)));
      guiFrom.setSlot(49,new GuiElementBuilder(Items.BARRIER).setName(Text.literal("Cancel Trade").formatted(Formatting.DARK_RED,Formatting.BOLD)));
   }
   
   public void updateGuis(){
      for(int i=0;i<12;i++){
         guiFrom.setSlot(theirSlots[i],inv.getStack(i+12));
         guiTo.setSlot(theirSlots[i],inv.getStack(i));
      }
      //System.out.println("updating guis");
      
      listener.finishUpdate();
   }
   
   public void checkReadyStatus(){
      checkReadyStatus(tFrom);
      checkReadyStatus(tTo);
   }
   
   public void checkReadyStatus(ServerPlayerEntity player){
      if(player.equals(tFrom)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check first 12 slots
         for(int i=0;i<12;i++){
            if(!readyState[i].equals(inv.getStack(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }else if(player.equals(tTo)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check to ready status
         for(int i=12;i<24;i++){
            if(!readyState[i].equals(inv.getStack(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }
   }
   
   public void setUnready(){
      guiTo.setSlot(9,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Confirm").formatted(Formatting.RED)));
      guiFrom.setSlot(9,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Confirm").formatted(Formatting.RED)));
      guiTo.setSlot(17,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Waiting for player...").formatted(Formatting.RED)));
      guiFrom.setSlot(17,new GuiElementBuilder(Items.RED_CONCRETE).setName(Text.literal("Waiting for player...").formatted(Formatting.RED)));
      fromReady = false;
      toReady = false;
      Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_HAT,1f,.5f);
      Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_HAT,1f,.5f);
   }
   
   public void setReady(ServerPlayerEntity player){
      if(player.equals(tFrom)&&!fromReady){
         guiFrom.setSlot(9,new GuiElementBuilder(Items.GREEN_CONCRETE).setName(Text.literal("Confirmed!").formatted(Formatting.GREEN)));
         guiTo.setSlot(17,new GuiElementBuilder(Items.GREEN_CONCRETE).setName(Text.literal("Player Ready!").formatted(Formatting.GREEN)));
         fromReady = true;
         Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f,1f);
         Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f,2f);
         for(int i=0;i<12;i++){
            readyState[i] = inv.getStack(i);
         }
      }else if(player.equals(tTo)&&!toReady){
         guiTo.setSlot(9,new GuiElementBuilder(Items.GREEN_CONCRETE).setName(Text.literal("Confirmed!").formatted(Formatting.GREEN)));
         guiFrom.setSlot(17,new GuiElementBuilder(Items.GREEN_CONCRETE).setName(Text.literal("Player Ready!").formatted(Formatting.GREEN)));
         toReady = true;
         Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f,2f);
         Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_PLING,1f,1f);
         for(int i=12;i<24;i++){
            readyState[i] = inv.getStack(i);
         }
      }
      
      if(fromReady&&toReady){
         finalizeTrade();
         
         Timer timer = new Timer();
         timer.schedule(new TimerTask() {
            @Override
            public void run() {
               Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_BELL,1.5f,1f);
               Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_BELL,1.5f,1f);
               Timer timer2 = new Timer();
               timer2.schedule(new TimerTask() {
                  @Override
                  public void run() {
                     Utils.playSongToPlayer(tTo, SoundEvents.BLOCK_NOTE_BLOCK_BELL,1.5f,2f);
                     Utils.playSongToPlayer(tFrom, SoundEvents.BLOCK_NOTE_BLOCK_BELL,1.5f,2f);
                  }
               }, 500);
            }
         }, 500);
      }
   }
   
   public void finalizeTrade(){
      //Give Items
      for(int i=0; i<24;i++){
         ItemStack stack = inv.getStack(i);
         if(!stack.isEmpty()){
            ServerPlayerEntity player;
            if(i<12){
               player = tTo;
            }else{
               player = tFrom;
            }
   
            ItemEntity itemEntity;
            boolean bl = player.getInventory().insertStack(stack);
            if (!bl || !stack.isEmpty()) {
               itemEntity = player.dropItem(stack, false);
               if (itemEntity == null) continue;
               itemEntity.resetPickupDelay();
               itemEntity.setOwner(player.getUuid());
               continue;
            }
            stack.setCount(1);
            itemEntity = player.dropItem(stack, false);
            if (itemEntity != null) {
               itemEntity.setDespawnImmediately();
            }
         }
      }
      
      tFrom.sendMessage(Text.literal("Trade Completed!").formatted(Formatting.GREEN), false);
      tTo.sendMessage(Text.literal("Trade Completed!").formatted(Formatting.GREEN), false);
      closing = true;
      guiFrom.close();
      guiTo.close();
      
      main.completeSession(tFrom,tTo,tr);
   }
   
   public void cancelTrade(ServerPlayerEntity player){
      if(!closing){
         closing = true;
   
         //Give Items back
         for(int i=0; i<24;i++){
            ItemStack stack = inv.getStack(i);
            if(!stack.isEmpty()){
               ServerPlayerEntity player1;
               if(i>=12){
                  player1 = tTo;
               }else{
                  player1 = tFrom;
               }
         
               ItemEntity itemEntity;
               boolean bl = player1.getInventory().insertStack(stack);
               if (!bl || !stack.isEmpty()) {
                  itemEntity = player1.dropItem(stack, false);
                  if (itemEntity == null) continue;
                  itemEntity.resetPickupDelay();
                  itemEntity.setOwner(player1.getUuid());
                  continue;
               }
               stack.setCount(1);
               itemEntity = player1.dropItem(stack, false);
               if (itemEntity != null) {
                  itemEntity.setDespawnImmediately();
               }
            }
         }
         
         player.sendMessage(Text.literal("You have cancelled the trade request!").formatted(Formatting.RED), false);
         ServerPlayerEntity other = player.equals(tFrom) ? tTo : tFrom;
         other.sendMessage(Text.literal(player.getName().getString()).formatted(Formatting.AQUA)
               .append(Text.literal(" has cancelled the trade request!").formatted(Formatting.RED)), false);
   
         guiFrom.close();
         guiTo.close();
         
         
         Utils.playSongToPlayer(tFrom, RegistryEntry.of(SoundEvents.BLOCK_FIRE_EXTINGUISH) ,1f,.5f);
         Utils.playSongToPlayer(tTo, RegistryEntry.of(SoundEvents.BLOCK_FIRE_EXTINGUISH),1f,.5f);
      }
   }
}
