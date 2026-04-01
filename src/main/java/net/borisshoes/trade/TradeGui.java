package net.borisshoes.trade;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;

public class TradeGui extends SimpleGui {
   private final TradeSession session;
   private final int[] yourSlots = {10,11,12,19,20,21,28,29,30,37,38,39};
   
   /**
    * Constructs a new simple container gui for the supplied player.
    *
    * @param type                        the screen handler that the client should display
    * @param player                      the player to server this gui to
    */
   public TradeGui(MenuType<?> type, ServerPlayer player, TradeSession session){
      super(type, player, false);
      this.session = session;
   }
   
   @Override
   public boolean open(){
      boolean openRet = super.open();
      this.wrappedMenu.addSlotListener(session);
      return openRet;
   }
   
   @Override
   public boolean onAnyClick(int index, ClickType type, ContainerInput action) {
      //System.out.println("Gui from "+player.getName().getString()+" has clicked slot "+index+" with clicktype "+type.name()+" and actiontype "+action.name());
      boolean checkSlots = false;
      for(int i=0;i<yourSlots.length;i++){
         if(yourSlots[i]==index){
            checkSlots = true;
            break;
         }
      }
      
      if(checkSlots || action == ContainerInput.PICKUP_ALL){
         //System.out.println("checking ready status for player: "+player.getEntityName());
         session.checkReadyStatus(player);
      }
      if(index==9 && action == ContainerInput.PICKUP){
         session.setReady(player);
      }
      if(index==49 && action == ContainerInput.PICKUP){
         session.cancelTrade(player);
      }
      return true;
   }
   
   @Override
   public void onRemoved(){
      session.cancelTrade(player);
   }
}
