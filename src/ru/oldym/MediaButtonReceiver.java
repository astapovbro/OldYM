package ru.oldym;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Receives play/pause/next/previous events dispatched by Android or a headset. */
public final class MediaButtonReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()))
            return;

        KeyEvent event = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;

        String action = null;
        int key = event.getKeyCode();
        switch (key) {
            case 85:  // KEYCODE_MEDIA_PLAY_PAUSE
            case 126: // KEYCODE_MEDIA_PLAY
            case 127: // KEYCODE_MEDIA_PAUSE
                action = SystemMediaControls.ACTION_TOGGLE;
                break;
            case 87:  // KEYCODE_MEDIA_NEXT
                action = SystemMediaControls.ACTION_NEXT;
                break;
            case 88:  // KEYCODE_MEDIA_PREVIOUS
                action = SystemMediaControls.ACTION_PREVIOUS;
                break;
            default:
                return;
        }

        Intent command = new Intent(context, PlayerService.class);
        command.setAction(action);
        context.startService(command);

        if (isOrderedBroadcast()) abortBroadcast();
    }
}
