package njw.net.justchat.mixin;

import net.minecraft.client.gui.components.EditBox;
import njw.net.justchat.client.CustomChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CustomChatScreen.class)
public interface CustomChatScreenAccessor {
    @Accessor("messageInput")
    EditBox njwJustChat$messageInput();

    @Accessor("scrollOffset")
    int njwJustChat$scrollOffset();

    @Accessor("scrollOffset")
    void njwJustChat$setScrollOffset(int value);

    @Accessor("historyAnchorId")
    void njwJustChat$setHistoryAnchorId(long value);

    @Accessor("historyAnchorLineIndex")
    void njwJustChat$setHistoryAnchorLineIndex(int value);

    @Accessor("jumpingToLatest")
    void njwJustChat$setJumpingToLatest(boolean value);

    @Accessor("hasUnseenLiveMessages")
    void njwJustChat$setHasUnseenLiveMessages(boolean value);

    @Accessor("newMessageNoticeUntil")
    void njwJustChat$setNewMessageNoticeUntil(long value);
}
