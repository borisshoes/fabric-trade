package net.borisshoes.trade;

import com.mojang.datafixers.util.Either;
import net.borisshoes.borislib.utils.MinecraftUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class TradeSlot extends Slot {
   public TradeSlot(Container container, int slot, int x, int y){
      super(container, slot, x, y);
   }
   
   @Override
   public boolean mayPlace(@NonNull ItemStack itemStack){
      List<String> blacklistRaw = Trade.CONFIG.getStringList(Trade.BLACKLIST_CFG);
      for(String str : blacklistRaw){
         Either<Item, TagKey<Item>> either = MinecraftUtils.parseItemOrTag(str);
         if(either == null) continue;
         if(either.left().isPresent() && itemStack.is(either.left().get())) return false;
         if(either.right().isPresent() && itemStack.is(either.right().get())) return false;
      }
      return true;
   }
}
