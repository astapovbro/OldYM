package ru.oldym;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges OldYM to Android's system media controls without linking against
 * APIs newer than Android 2.0 (API 5). RemoteControlClient is loaded only on
 * API 14+, while media-button registration is loaded reflectively from API 8.
 */
final class SystemMediaControls {
    static final String ACTION_TOGGLE = "ru.oldym.action.MEDIA_TOGGLE";
    static final String ACTION_NEXT = "ru.oldym.action.MEDIA_NEXT";
    static final String ACTION_PREVIOUS = "ru.oldym.action.MEDIA_PREVIOUS";

    private final Context context;
    private AudioManager audio;
    private ComponentName receiver;
    private Object remote;
    private Class<?> remoteClass;
    private boolean buttonReceiverRegistered;
    private boolean remoteRegistered;

    SystemMediaControls(Context c) {
        context = c.getApplicationContext();
    }

    void start() {
        try {
            audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            receiver = new ComponentName(context, MediaButtonReceiver.class);

            // AudioManager's media-button registration appeared after API 5.
            if (Build.VERSION.SDK_INT >= 8 && audio != null) {
                Method register = AudioManager.class.getMethod(
                    "registerMediaButtonEventReceiver",
                    new Class[] { ComponentName.class }
                );
                register.invoke(audio, new Object[] { receiver });
                buttonReceiverRegistered = true;
            }

            // RemoteControlClient is the platform integration used by Android
            // 4.x lock-screen and external transport controls. Reflection keeps
            // the project compilable against its pinned API-5 android.jar.
            if (Build.VERSION.SDK_INT >= 14 && audio != null) {
                Intent mediaButton = new Intent(Intent.ACTION_MEDIA_BUTTON);
                mediaButton.setComponent(receiver);
                PendingIntent pending = PendingIntent.getBroadcast(
                    context, 4101, mediaButton, PendingIntent.FLAG_UPDATE_CURRENT
                );

                remoteClass = Class.forName("android.media.RemoteControlClient");
                Constructor<?> constructor = remoteClass.getConstructor(
                    new Class[] { PendingIntent.class }
                );
                remote = constructor.newInstance(new Object[] { pending });

                Method registerRemote = AudioManager.class.getMethod(
                    "registerRemoteControlClient",
                    new Class[] { remoteClass }
                );
                registerRemote.invoke(audio, new Object[] { remote });
                remoteRegistered = true;

                int flags = 1 | 4 | 8 | 16 | 128;
                Method setFlags = remoteClass.getMethod(
                    "setTransportControlFlags", new Class[] { Integer.TYPE }
                );
                setFlags.invoke(remote, new Object[] { Integer.valueOf(flags) });
            }
        } catch (Exception ignored) {
            // A vendor ROM may omit or alter optional media-control APIs.
            // Playback and the in-app controls must continue working regardless.
            remote = null;
            remoteClass = null;
        } catch (LinkageError ignored) {
            remote = null;
            remoteClass = null;
        }
    }

    void update(Api.Track track, boolean playing, boolean loading) {
        if (remote == null || remoteClass == null) return;
        try {
            Class<?> editorClass = Class.forName(
                "android.media.RemoteControlClient$MetadataEditor"
            );
            Object editor = remoteClass.getMethod(
                "editMetadata", new Class[] { Boolean.TYPE }
            ).invoke(remote, new Object[] { Boolean.TRUE });

            if (track != null) {
                putString(editor, editorClass, "METADATA_KEY_TITLE", track.title);
                putString(editor, editorClass, "METADATA_KEY_ARTIST", track.artist);
                putLong(editor, editorClass, "METADATA_KEY_DURATION", track.duration);
            }

            editorClass.getMethod("apply", new Class[0]).invoke(editor, new Object[0]);

            int state = loading ? 8 : (playing ? 3 : (track == null ? 1 : 2));
            remoteClass.getMethod(
                "setPlaybackState", new Class[] { Integer.TYPE }
            ).invoke(remote, new Object[] { Integer.valueOf(state) });
        } catch (Exception ignored) {
            // Ignore media metadata failures on vendor-specific Android builds.
        } catch (LinkageError ignored) {
        }
    }

    private void putString(Object editor, Class<?> editorClass,
                           String keyName, String value) throws Exception {
        int key = fieldValue(editorClass, keyName, -1);
        if (key < 0 || value == null) return;
        editorClass.getMethod(
            "putString", new Class[] { Integer.TYPE, String.class }
        ).invoke(editor, new Object[] { Integer.valueOf(key), value });
    }

    private void putLong(Object editor, Class<?> editorClass,
                         String keyName, long value) throws Exception {
        int key = fieldValue(editorClass, keyName, -1);
        if (key < 0) return;
        editorClass.getMethod(
            "putLong", new Class[] { Integer.TYPE, Long.TYPE }
        ).invoke(editor, new Object[] { Integer.valueOf(key), Long.valueOf(value) });
    }

    private int fieldValue(Class<?> type, String name, int fallback) {
        try {
            Field field = type.getField(name);
            return field.getInt(null);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    void stop() {
        if (audio != null && remoteRegistered && remoteClass != null && remote != null) {
            try {
                AudioManager.class.getMethod(
                    "unregisterRemoteControlClient", new Class[] { remoteClass }
                ).invoke(audio, new Object[] { remote });
            } catch (Exception ignored) {
            } catch (LinkageError ignored) {
            }
        }

        if (audio != null && buttonReceiverRegistered && receiver != null) {
            try {
                AudioManager.class.getMethod(
                    "unregisterMediaButtonEventReceiver",
                    new Class[] { ComponentName.class }
                ).invoke(audio, new Object[] { receiver });
            } catch (Exception ignored) {
            } catch (LinkageError ignored) {
            }
        }

        remote = null;
        remoteClass = null;
        audio = null;
        receiver = null;
        remoteRegistered = false;
        buttonReceiverRegistered = false;
    }
}
