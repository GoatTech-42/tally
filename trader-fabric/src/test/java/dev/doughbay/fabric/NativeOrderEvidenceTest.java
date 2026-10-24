package dev.doughbay.fabric;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeOrderEvidenceTest {
    @Test void localInspectionRetainsOnlyQuantityNumbersAndStaticSchemaTypes() {
        CompoundTag nested=new CompoundTag();
        nested.putInt("requested",1024);nested.putInt("delivered",64);
        nested.putString("order_uuid","private-order-identifier");
        nested.putString("player_uuid","private-player-identifier");
        nested.putString("api_token","private-credential");
        nested.putString("home_name","private-home");
        nested.putLong("price",987654321);
        CompoundTag root=new CompoundTag();root.put("PublicBukkitValues",nested);
        var schema=NativeOrderEvidence.numericSchema(root);
        assertEquals(1024L,schema.get("PublicBukkitValues/requested"));
        assertEquals(64L,schema.get("PublicBukkitValues/delivered"));
        assertEquals("StringTag",schema.get("PublicBukkitValues/order_uuid"));
        String text=schema.toString();
        assertFalse(text.contains("private-"));assertFalse(text.contains("987654321"));
        assertFalse(text.contains("player_uuid"));assertFalse(text.contains("api_token"));assertFalse(text.contains("home_name"));
    }
}
