package net.borisshoes.trade.gui;

import net.borisshoes.trade.TradeSession;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.item.ItemStack;

public class TradeInventoryListener implements InventoryChangedListener {
   
   TradeSession session;
   private boolean updating = false;
   
   public TradeInventoryListener(TradeSession session){
      this.session = session;
   }
   
   @Override
   public void onInventoryChanged(Inventory inv){
      if(!updating){
         updating = true;
         session.checkReadyStatus();
         session.updateGuis();
         
      }
   }
   
   public void finishUpdate(){
      updating = false;
   }
}
