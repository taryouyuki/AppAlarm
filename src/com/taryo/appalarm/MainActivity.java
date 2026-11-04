package com.taryo.appalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Shows every scheduled alarm and lets the user add, toggle, edit or delete them. */
public class MainActivity extends Activity {

    private AlarmStore store;
    private LinearLayout listContainer;
    private LinearLayout chips;
    private TextView emptyView;
    private String selectedGroup;
    private String themeSignature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        themeSignature = themeSignature();
        store = new AlarmStore(this);
        NotificationHelper.ensureChannel(this);
        setContentView(buildRoot());
    }

    private String themeSignature() {
        return AppSettings.themeMode(this) + "/" + AppSettings.useDynamicColor(this)
                + "/" + Ui.isDark(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!themeSignature().equals(themeSignature)) {
            recreate();
            return;
        }
        refresh();
        checkPermissions();
    }

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 28), Ui.dp(this, 18), Ui.dp(this, 28));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("アプリ起動アラーム");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TEXT);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);

        TextView history = new TextView(this);
        history.setText("履歴");
        history.setTextSize(14);
        history.setTextColor(Ui.ACCENT);
        history.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HistoryActivity.class));
            }
        });
        header.addView(history);

        TextView settings = new TextView(this);
        settings.setText("設定");
        settings.setTextSize(14);
        settings.setTextColor(Ui.ACCENT);
        settings.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, Ui.dp(this, 8));
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(settings);
        root.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("指定した時刻に、アプリを開くか通知で確認します");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Ui.MUTED);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = Ui.dp(this, 6);
        subLp.bottomMargin = Ui.dp(this, 20);
        root.addView(subtitle, subLp);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chips);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.bottomMargin = Ui.dp(this, 14);
        root.addView(chipScroll, chipLp);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyView = new TextView(this);
        emptyView.setText("まだアラームがありません\n下のボタンから追加してください");
        emptyView.setTextSize(14);
        emptyView.setTextColor(Ui.MUTED);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, Ui.dp(this, 40), 0, Ui.dp(this, 40));
        root.addView(emptyView);

        Button add = new Button(this);
        add.setText("＋ アラームを追加");
        add.setTextSize(16);
        add.setAllCaps(false);
        add.setTextColor(0xFFFFFFFF);
        add.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 14)));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        addLp.topMargin = Ui.dp(this, 12);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, EditActivity.class));
            }
        });
        root.addView(add, addLp);

        scroll.addView(root);
        return scroll;
    }

    private void refresh() {
        listContainer.removeAllViews();
        chips.removeAllViews();

        List<AlarmEntry> all = store.all();
        buildChips(all);

        List<AlarmEntry> items = new ArrayList<>();
        for (AlarmEntry e : all) {
            if (selectedGroup == null || selectedGroup.equals(e.group)) items.add(e);
        }
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        PackageManager pm = getPackageManager();
        for (final AlarmEntry e : items) {
            listContainer.addView(buildRow(e, pm));
        }
    }

    private void buildChips(List<AlarmEntry> all) {
        List<String> groups = new ArrayList<>();
        for (AlarmEntry e : all) {
            if (e.group != null && !e.group.isEmpty() && !groups.contains(e.group)) {
                groups.add(e.group);
            }
        }
        if (groups.isEmpty()) {
            chips.setVisibility(View.GONE);
            return;
        }
        chips.setVisibility(View.VISIBLE);
        addChip("すべて", null);
        for (String g : groups) addChip(g, g);
    }

    private void addChip(String text, final String key) {
        boolean selected = (key == null && selectedGroup == null)
                || (key != null && key.equals(selectedGroup));
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        chip.setBackground(Ui.rounded(selected ? Ui.ACCENT : Ui.CARD, Ui.dp(this, 18)));
        chip.setTextColor(selected ? 0xFFFFFFFF : Ui.MUTED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedGroup = key;
                refresh();
            }
        });
        chips.addView(chip, lp);
    }

    private View buildRow(final AlarmEntry e, PackageManager pm) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = Ui.dp(this, 10);
        row.setLayoutParams(rowLp);
        row.setElevation(Ui.dp(this, 1));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(pm.getApplicationIcon(e.pkg));
        } catch (Exception ex) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
        row.addView(icon, iconLp);

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Ui.dp(this, 12);

        TextView time = new TextView(this);
        time.setText(e.timeText());
        time.setTextSize(22);
        time.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        time.setTextColor(e.enabled ? Ui.TEXT : Ui.MUTED);
        middle.addView(time);

        TextView label = new TextView(this);
        label.setText(e.label);
        label.setTextSize(14);
        label.setTextColor(e.enabled ? Ui.TEXT : Ui.MUTED);
        middle.addView(label);

        TextView meta = new TextView(this);
        meta.setText(e.daySummary() + " ・ " + e.soundSummary() + (e.autoOpen ? " ・ 自動" : ""));
        meta.setTextSize(12);
        meta.setTextColor(Ui.MUTED);
        middle.addView(meta);

        row.addView(middle, midLp);

        Switch toggle = new Switch(this);
        toggle.setChecked(e.enabled);
        toggle.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                e.enabled = isChecked;
                store.upsert(e);
                if (isChecked) {
                    long at = AlarmScheduler.schedule(MainActivity.this, e);
                    if (at > 0) {
                        Toast.makeText(MainActivity.this, "次回 " + formatNext(at),
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    AlarmScheduler.cancel(MainActivity.this, e.id);
                }
                refresh();
            }
        });
        row.addView(toggle);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, EditActivity.class);
                i.putExtra("id", e.id);
                startActivity(i);
            }
        });

        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDelete(e);
                return true;
            }
        });

        return row;
    }

    private String formatNext(long millis) {
        java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
        return String.format(java.util.Locale.JAPAN, "%02d/%02d %02d:%02d",
                t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }

    private void confirmDelete(final AlarmEntry e) {
        new AlertDialog.Builder(this)
                .setTitle("削除しますか？")
                .setMessage(e.timeText() + " の「" + e.label + "」")
                .setPositiveButton("削除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        AlarmScheduler.cancel(MainActivity.this, e.id);
                        store.delete(e.id);
                        refresh();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canPostNotifications(this)) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        if (!AlarmScheduler.canScheduleExact(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("正確なアラームの許可が必要です")
                    .setMessage("指定した時刻どおりに通知を出すには「アラームとリマインダー」の権限を許可してください。")
                    .setPositiveButton("設定を開く", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            try {
                                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(i);
                            } catch (Exception ex) {
                                Toast.makeText(MainActivity.this, "設定画面を開けませんでした",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("あとで", null)
                    .show();
        }
    }
}
