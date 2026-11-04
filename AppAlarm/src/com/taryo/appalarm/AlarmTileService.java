package com.taryo.appalarm;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** Quick Settings tile showing the next alarm; tapping it opens the app. */
public class AlarmTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivityAndCollapse(i);
        } catch (Exception ex) {
            startActivity(i);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        AlarmStore store = new AlarmStore(this);
        long now = System.currentTimeMillis();
        AlarmEntry best = null;
        long bestAt = 0L;
        for (AlarmEntry e : store.all()) {
            if (!e.enabled) continue;
            long at = NextTime.nextTrigger(e, now);
            if (at <= 0L) continue;
            if (best == null || at < bestAt) {
                best = e;
                bestAt = at;
            }
        }

        if (best == null) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("アラームなし");
            if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(null);
        } else {
            LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(bestAt),
                    ZoneId.systemDefault());
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(String.format(Locale.JAPAN, "%02d:%02d", t.getHour(), t.getMinute()));
            if (Build.VERSION.SDK_INT >= 29) tile.setSubtitle(best.label);
        }
        tile.updateTile();
    }
}
