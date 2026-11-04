package com.taryo.appalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** App-wide settings: snooze delay, snooze limit, backup and restore. */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        setContentView(buildRoot());
    }

    private ScrollView buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24));

        TextView title = new TextView(this);
        title.setText("設定");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TEXT);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = Ui.dp(this, 18);
        root.addView(title, titleLp);

        // --- appearance ---------------------------------------------------
        LinearLayout themeCard = card();
        themeCard.addView(caption("外観"));

        final RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.setOrientation(RadioGroup.VERTICAL);
        String[] themeLabels = {"システムに合わせる", "ライト", "ダーク"};
        int[] themeValues = {AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK};
        int currentTheme = AppSettings.themeMode(this);
        for (int i = 0; i < themeLabels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(3200 + i);
            rb.setText(themeLabels[i]);
            rb.setTextSize(15);
            rb.setTextColor(Ui.TEXT);
            rb.setTag(themeValues[i]);
            themeGroup.addView(rb);
            if (themeValues[i] == currentTheme) themeGroup.check(3200 + i);
        }
        themeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                Object tag = g.findViewById(checkedId).getTag();
                if (tag instanceof Integer) {
                    AppSettings.setThemeMode(SettingsActivity.this, (Integer) tag);
                    recreate();
                }
            }
        });
        themeCard.addView(themeGroup);

        final CheckBox dynamic = new CheckBox(this);
        dynamic.setText("システムの色を使う（Material You）");
        dynamic.setTextSize(15);
        dynamic.setChecked(AppSettings.useDynamicColor(this));
        boolean supportsDynamic = Build.VERSION.SDK_INT >= 31;
        dynamic.setEnabled(supportsDynamic);
        dynamic.setTextColor(supportsDynamic ? Ui.TEXT : Ui.MUTED);
        dynamic.setPadding(0, Ui.dp(this, 8), 0, 0);
        dynamic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppSettings.setUseDynamicColor(SettingsActivity.this, checked);
                recreate();
            }
        });
        themeCard.addView(dynamic);

        TextView dynamicNote = new TextView(this);
        dynamicNote.setText(supportsDynamic
                ? "Android 12 以降の壁紙から取れた色をアクセントに使います。"
                : "この端末（Android 11 以前）では使えません。");
        dynamicNote.setTextSize(12);
        dynamicNote.setTextColor(Ui.MUTED);
        dynamicNote.setPadding(0, Ui.dp(this, 2), 0, 0);
        themeCard.addView(dynamicNote);

        root.addView(themeCard, margin(0, 0, 0, 12));

        // --- alarm behaviour ----------------------------------------------
        LinearLayout alarmCard = card();
        alarmCard.addView(caption("アラーム鳴動"));

        final CheckBox fade = new CheckBox(this);
        fade.setText("音量を徐々に大きくする（フェードイン）");
        fade.setTextSize(15);
        fade.setTextColor(Ui.TEXT);
        fade.setChecked(AppSettings.fadeEnabled(this));
        fade.setPadding(0, Ui.dp(this, 8), 0, 0);
        fade.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppSettings.setFadeEnabled(SettingsActivity.this, checked);
            }
        });
        alarmCard.addView(fade);

        final TextView fadeRow = new TextView(this);
        fadeRow.setTextSize(14);
        fadeRow.setTextColor(Ui.ACCENT);
        fadeRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        fadeRow.setText("フェードインの長さ: " + AppSettings.fadeSeconds(this) + "秒");
        fadeRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] choices = AppSettings.FADE_CHOICES;
                String[] labels = new String[choices.length];
                for (int i = 0; i < choices.length; i++) labels[i] = choices[i] + "秒";
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("フェードインの長さ")
                        .setItems(labels, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                AppSettings.setFadeSeconds(SettingsActivity.this, choices[which]);
                                fadeRow.setText("フェードインの長さ: " + choices[which] + "秒");
                            }
                        })
                        .show();
            }
        });
        alarmCard.addView(fadeRow);

        final CheckBox history = new CheckBox(this);
        history.setText("履歴を記録する（停止・スヌーズなど）");
        history.setTextSize(15);
        history.setTextColor(Ui.TEXT);
        history.setChecked(AppSettings.historyEnabled(this));
        history.setPadding(0, Ui.dp(this, 8), 0, 0);
        history.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                AppSettings.setHistoryEnabled(SettingsActivity.this, checked);
            }
        });
        alarmCard.addView(history);

        Button openHistory = new Button(this);
        openHistory.setText("履歴と統計を見る");
        openHistory.setAllCaps(false);
        openHistory.setTextSize(14);
        openHistory.setTextColor(Ui.ACCENT);
        openHistory.setBackground(Ui.roundedStroke(Ui.CARD, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        openHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(SettingsActivity.this, HistoryActivity.class));
            }
        });
        LinearLayout.LayoutParams ohLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        ohLp.topMargin = Ui.dp(this, 8);
        alarmCard.addView(openHistory, ohLp);

        root.addView(alarmCard, margin(0, 0, 0, 12));

        // --- snooze delay -------------------------------------------------
        LinearLayout delayCard = card();
        delayCard.addView(caption("「再通知」までの時間"));
        TextView note = new TextView(this);
        note.setText("通知のボタンを押したあと、次にもう一度知らせるまでの間隔です。");
        note.setTextSize(13);
        note.setTextColor(Ui.TEXT);
        note.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        delayCard.addView(note);

        final RadioGroup delayGroup = new RadioGroup(this);
        delayGroup.setOrientation(RadioGroup.VERTICAL);
        int currentDelay = AppSettings.snoozeMinutes(this);
        for (int i = 0; i < AppSettings.SNOOZE_CHOICES.length; i++) {
            int minutes = AppSettings.SNOOZE_CHOICES[i];
            RadioButton rb = new RadioButton(this);
            rb.setId(3000 + i);
            rb.setText(minutes + "分後");
            rb.setTextSize(15);
            rb.setTextColor(Ui.TEXT);
            rb.setTag(minutes);
            delayGroup.addView(rb);
            if (minutes == currentDelay) delayGroup.check(3000 + i);
        }
        delayGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                Object tag = g.findViewById(checkedId).getTag();
                if (tag instanceof Integer) AppSettings.setSnoozeMinutes(SettingsActivity.this, (Integer) tag);
            }
        });
        delayCard.addView(delayGroup);
        root.addView(delayCard, margin(0, 0, 0, 12));

        // --- snooze limit -------------------------------------------------
        LinearLayout limitCard = card();
        limitCard.addView(caption("「再通知」の上限回数"));
        TextView limitNote = new TextView(this);
        limitNote.setText("1回の発火に対して何回まで「再通知」できるか。上限に達するとボタンが消えます。");
        limitNote.setTextSize(13);
        limitNote.setTextColor(Ui.TEXT);
        limitNote.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        limitCard.addView(limitNote);

        final RadioGroup limitGroup = new RadioGroup(this);
        limitGroup.setOrientation(RadioGroup.VERTICAL);
        int currentLimit = AppSettings.maxSnooze(this);
        for (int i = 0; i < AppSettings.SNOOZE_LIMIT_CHOICES.length; i++) {
            int value = AppSettings.SNOOZE_LIMIT_CHOICES[i];
            RadioButton rb = new RadioButton(this);
            rb.setId(3100 + i);
            rb.setText(value == AppSettings.SNOOZE_UNLIMITED ? "上限なし"
                    : value == AppSettings.SNOOZE_NONE ? "再通知しない" : value + "回まで");
            rb.setTextSize(15);
            rb.setTextColor(Ui.TEXT);
            rb.setTag(value);
            limitGroup.addView(rb);
            if (value == currentLimit) limitGroup.check(3100 + i);
        }
        limitGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                Object tag = g.findViewById(checkedId).getTag();
                if (tag instanceof Integer) AppSettings.setMaxSnooze(SettingsActivity.this, (Integer) tag);
            }
        });
        limitCard.addView(limitGroup);
        root.addView(limitCard, margin(0, 0, 0, 12));

        // --- backup / restore --------------------------------------------
        LinearLayout backupCard = card();
        backupCard.addView(caption("バックアップと復元"));

        TextView backupNote = new TextView(this);
        File dir = getExternalFilesDir(null);
        backupNote.setText("アラームの設定をJSONファイルに書き出します。\n保存先: "
                + (dir == null ? "(不明)" : dir.getAbsolutePath()));
        backupNote.setTextSize(12);
        backupNote.setTextColor(Ui.MUTED);
        backupNote.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        backupCard.addView(backupNote);

        Button export = new Button(this);
        export.setText("バックアップを書き出す");
        export.setAllCaps(false);
        export.setTextSize(15);
        export.setTextColor(0xFFFFFFFF);
        export.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 12)));
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportBackup();
            }
        });
        backupCard.addView(export, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        Button restore = new Button(this);
        restore.setText("バックアップから復元");
        restore.setAllCaps(false);
        restore.setTextSize(15);
        restore.setTextColor(0xFFDC2626);
        restore.setBackground(Ui.roundedStroke(0xFFFFFFFF, Ui.dp(this, 12), 0xFFFCA5A5, Ui.dp(this, 1)));
        LinearLayout.LayoutParams restoreLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        restoreLp.topMargin = Ui.dp(this, 10);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importBackup();
            }
        });
        backupCard.addView(restore, restoreLp);

        root.addView(backupCard, margin(0, 0, 0, 12));

        TextView hint = new TextView(this);
        hint.setText("通知そのものが画面に表示されている時間は Android 側の仕様で決まるため、"
                + "アプリからは変更できません。");
        hint.setTextSize(12);
        hint.setTextColor(Ui.MUTED);
        hint.setGravity(Gravity.START);
        hint.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4), 0);
        root.addView(hint);

        scroll.addView(root);
        return scroll;
    }

    private void exportBackup() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                Toast.makeText(this, "保存先が見つかりません", Toast.LENGTH_SHORT).show();
                return;
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.JAPAN).format(new Date());
            File file = new File(dir, "appalarm-backup-" + stamp + ".json");
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(new AlarmStore(this).exportJson().getBytes("UTF-8"));
            } finally {
                out.close();
            }
            Toast.makeText(this, "保存しました: " + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "書き出しに失敗しました: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importBackup() {
        final File dir = getExternalFilesDir(null);
        File[] found = dir == null ? null : dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return name.endsWith(".json");
            }
        });
        if (found == null || found.length == 0) {
            Toast.makeText(this, "バックアップが見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }
        final File[] files = found;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return b.getName().compareTo(a.getName());
            }
        });
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();

        new AlertDialog.Builder(this)
                .setTitle("復元するファイルを選択")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        restoreFrom(files[which]);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void restoreFrom(File file) {
        try {
            FileInputStream in = new FileInputStream(file);
            byte[] buf = new byte[(int) file.length()];
            int read = 0;
            try {
                while (read < buf.length) {
                    int n = in.read(buf, read, buf.length - read);
                    if (n <= 0) break;
                    read += n;
                }
            } finally {
                in.close();
            }
            String json = new String(buf, 0, read, "UTF-8");
            AlarmStore store = new AlarmStore(this);
            if (!store.importJson(json)) {
                Toast.makeText(this, "ファイルの内容が不正です", Toast.LENGTH_LONG).show();
                return;
            }
            AlarmScheduler.rescheduleAll(this);
            Toast.makeText(this, "復元しました（" + file.getName() + "）", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception ex) {
            Toast.makeText(this, "復元に失敗しました: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        c.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        c.setElevation(Ui.dp(this, 1));
        return c;
    }

    private TextView caption(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(Ui.MUTED);
        return t;
    }

    private LinearLayout.LayoutParams margin(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, l);
        lp.topMargin = Ui.dp(this, t);
        lp.rightMargin = Ui.dp(this, r);
        lp.bottomMargin = Ui.dp(this, b);
        return lp;
    }
}
