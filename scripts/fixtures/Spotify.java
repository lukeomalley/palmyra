package com.spotify.tv.android;

// Emulator-only MediaSession producer. No account, network, audio or service.
public final class Main extends android.app.Activity {
    private static android.media.session.MediaSession session;
    private static boolean playing = true;
    private static boolean second = false;
    private static boolean limited = false;
    private static android.graphics.Bitmap artwork;

    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("Spotify MediaSession fixture");
        label.setTextSize(40);
        setContentView(label);
        ensureSession();
        control(getIntent());
    }

    private void ensureSession() {
        if (session == null) {
            session = new android.media.session.MediaSession(this, "Palmyra emulator fixture");
            session.setCallback(new android.media.session.MediaSession.Callback() {
                @Override public void onPlay() { playing = true; update(); }
                @Override public void onPause() { playing = false; update(); }
                @Override public void onSkipToNext() { second = !second; update(); }
            });
            artwork = android.graphics.Bitmap.createBitmap(88, 88, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(artwork);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new android.graphics.LinearGradient(0, 0, 88, 88,
                new int[] {0xffb0c5c1, 0xff42615c, 0xffd0bb95, 0xff203d37},
                null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 88, 88, paint);
            session.setActive(true);
        }
    }

    @Override public void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        ensureSession();
        control(intent);
    }

    private static void control(android.content.Intent intent) {
        String command = intent.getStringExtra("command");
        if ("pause".equals(command)) playing = false;
        if ("play".equals(command)) playing = true;
        limited = "limited".equals(command);
        update();
        if ("stop".equals(command)) session.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setState(android.media.session.PlaybackState.STATE_STOPPED, 0, 0).build());
        if ("destroy".equals(command)) { session.release(); session = null; }
    }

    private static void update() {
        session.setMetadata(new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, second ? "Everywhere" : "Dreams")
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, "Fleetwood Mac")
            .putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, artwork).build());
        long actions = limited ? 0 : android.media.session.PlaybackState.ACTION_PLAY
            | android.media.session.PlaybackState.ACTION_PAUSE
            | android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT;
        session.setPlaybackState(new android.media.session.PlaybackState.Builder()
            .setState(playing ? android.media.session.PlaybackState.STATE_PLAYING
                : android.media.session.PlaybackState.STATE_PAUSED, 0, playing ? 1 : 0)
            .setActions(actions).build());
    }
}
