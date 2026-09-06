# Just Chat v1.1.0-mc26.1.2

Just Chat v1.1.0-mc26.1.2 is a feature release that turns Just Chat into a recipient-aware persistent chat inbox with whisper support, filtered history views, and cleaner interaction with vanilla chat and command feedback.

## Compatibility

* Minecraft **26.1.2**
* NeoForge **26.1.2.97 or later**
* Just Chat **1.1.0-mc26.1.2**
* Java **25**
* Network protocol **7**

For multiplayer, install the same Just Chat version on both the client and the server.

## Highlights

* Reworked persistent chat storage around recipient-aware `ChatEntry` records with sender, audience, and origin metadata.
* Added persistent player-to-player whispers with an online-player target dropdown.
* Added click-to-reply for received whispers.
* Added four server-backed history filters: **All Chat**, **Global**, **Direct**, and **Whispers**.
* Added recipient-aware history pagination so private history is never broadcast to unrelated clients.
* Added persistent handling for targeted `/tellraw` messages and datapack-generated tellraw rewards, including BACAP-style messages.
* Command execution feedback and errors remain visible in vanilla chat but are excluded from persistent Just Chat history.
* Added a vanilla chat bridge so persistent messages keep timestamped vanilla history without overlapping the Custom Chat screen.
* Vanilla chat HUD is hidden while Custom Chat is open and restored automatically when it closes.
* Added a dimmed Custom Chat background without blur.
* Added pointing-hand cursor feedback when hovering other players' names in chat.

## Whisper UX

* Select `Everyone` or an online player from the target dropdown beside the input box.
* Whisper history is visible only to the sender and recipient.
* Clicking a received whisper selects its sender as the reply target.
* If a selected player goes offline before sending, the message is not sent or persisted; the target is refreshed back to `Everyone`.
* Long player names use a hover marquee animation in the target dropdown.

## History Filters

The Custom Chat screen now provides four independent views:

* **All Chat** — every persistent message visible to the player.
* **Global** — messages addressed to everyone.
* **Direct** — non-whisper messages addressed only to the current player.
* **Whispers** — received and sent whisper messages.

Filtering is performed by the server during history pagination rather than by trimming only the messages already loaded on the client.
The filter resets to **All Chat** whenever a new Custom Chat screen is opened.

## Other Changes

* New installs keep the chat screen open after sending by default.
* Existing configurations are migrated once to the new `closeChatAfterSend=false` default.
* Persistent chat segments now roll over less frequently while retaining unlimited history.
* Improved whisper target layout, hover behavior, and long-name handling.
* Added a third development client configuration with a 16-character username for UI testing.
* Improved Custom Chat alignment and spacing.

## Installation

1. Install Minecraft **26.1.2** with NeoForge **26.1.2.97 or later**.
2. Download `njw_just_chat-1.1.0-mc26.1.2.jar` from the **Assets** section below.
3. Place the JAR in your client's `mods` folder.
4. For multiplayer servers, place the same JAR in the server's `mods` folder.
5. Start Minecraft.

> The automatically generated `Source code (zip)` and `Source code (tar.gz)` files are not the installable mod.
>
> Use `njw_just_chat-1.1.0-mc26.1.2.jar` from the Assets section.
