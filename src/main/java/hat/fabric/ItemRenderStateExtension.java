package hat.fabric;

import net.minecraft.item.ItemDisplayContext;
import net.minecraft.util.Identifier;

public interface ItemRenderStateExtension {
    void hat$setCustomTextureId(Identifier textureId);

    Identifier hat$getCustomTextureId();

    void hat$setCustomTextureScale(float scale);

    float hat$getCustomTextureScale();

    void hat$setDisplayContext(ItemDisplayContext displayContext);

    ItemDisplayContext hat$getDisplayContext();

    void hat$clearCustomTextureId();
}
