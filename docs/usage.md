# Using Palmyra

## Remote

Up and Down select apps. Right enters the nearest menu item; Left returns to the
selected app. Select launches. Returning from an app restores the selection,
even after the launcher process dies. Home resets to the first row. Back stays
on Home.

Art Mode hides the interface. Left and Right change the photo; any other key
exits and is consumed. All apps lists installed launchable apps. Settings opens
Android's system settings.

## Spotify

Enable notification access for **Palmyra Spotify playback** in Android settings.
Android grants broad notification access, but Palmyra does not read or store
notification contents. Its notification listener only enables access to Android's
media sessions. It filters for the Spotify TV package and observes callbacks
while Home is resumed. Android may keep the empty listener bound while hidden.

When Spotify exposes an active media session, a compact glass player shows the
song, artist, album artwork and supported controls. Album art is preferred over
display icons; missing artwork uses a Spotify glyph. Unsupported controls are
disabled. The card remains visible when paused and hides when the session ends,
playback stops or access is revoked. Art Mode hides it with the rest of the UI.

Down from Settings reaches Play/Pause. Up returns to Settings. Left reaches
Open Spotify, then the app list; Right reaches Next when available. Back returns
to the app list.

Song metadata, album art and Play/Pause have been tested with Spotify TV on a
Sony Android 9 television. The manual notification-access screen and Next action
have not been verified on that device. Device firmware and Spotify's media-session
support determine what is available.
