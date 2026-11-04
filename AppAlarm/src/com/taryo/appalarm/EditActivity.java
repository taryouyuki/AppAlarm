package com.taryo.appalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Create or edit a single alarm. Sections collapse so the form stays scannable. */
public class EditActivity extends Activity {

    private AlarmStore store;
    private AlarmEntry entry;
    private boolean isNew;

    private TextView timeView;
    private TextView appNameView;
    private ImageView appIconView;
    private RadioGroup modeGroup;
    private LinearLayout daysRow;
    private CheckBox holidayBox;
    private RadioGroup soundGroup;
    private TextView soundRow;
    private EditText voiceInput;
    private TextView dateRow;
    private CheckBox autoBox;
    private CheckBox alarmModeBox;
    private TextView alarmSoundRow;
    private TextView groupRow;
    private TextView snoozeIntervalRow;
    private TextView snoozeLimitRow;
    private LinearLayout timeCard;
    private LinearLayout perDayBox;
    private final TextView[] perDayButtons = new TextView[7];
    private final boolean[] daySelected = new boolean[]{true, true, true, true, true, false, false};

    private final Map<String, TextView> summaries = new HashMap<>();
    private final Map<String, LinearLayout> bodies = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyTheme(this);
        store = new AlarmStore(this);

        long id = getIntent().getLongExtra("id", -1L);
        if (id >= 0) entry = store.find(id);
        isNew = entry == null;
        if (isNew) {
            entry = new AlarmEntry();
            entry.id = System.currentTimeMillis();
            entry.hour = 7;
            entry.minute = 0;
            entry.mode = AlarmEntry.MODE_DAILY;
        } else {
            for (int i = 0; i < 7; i++) daySelected[i] = (entry.days & (1 << i)) != 0;
        }

        setContentView(buildRoot());
        updateTimeText();
        updateAppText();
        updateDayRowVisibility();
        updateSoundRows();
        refreshSummaries();
    }

    // ------------------------------------------------------------------ layout

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 24), Ui.dp(this, 18), Ui.dp(this, 24));

        TextView title = new TextView(this);
        title.setText(isNew ? "アラームを追加" : "アラームを編集");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TEXT);
        root.addView(title, margin(0, 0, 0, 18));

        // --- time (always visible) ---------------------------------------
        timeCard = card();
        timeCard.addView(caption("時刻"));
        timeView = new TextView(this);
        timeView.setTextSize(40);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setTextColor(Ui.TEXT);
        timeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime();
            }
        });
        timeCard.addView(timeView);
        root.addView(timeCard, margin(0, 0, 0, 12));

        // --- target app (always visible) ---------------------------------
        LinearLayout appCard = card();
        appCard.addView(caption("開くアプリ"));
        LinearLayout appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setGravity(Gravity.CENTER_VERTICAL);
        appRow.setPadding(0, Ui.dp(this, 8), 0, 0);

        appIconView = new ImageView(this);
        appRow.addView(appIconView, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

        appNameView = new TextView(this);
        appNameView.setTextSize(17);
        appNameView.setTextColor(Ui.TEXT);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.leftMargin = Ui.dp(this, 12);
        appRow.addView(appNameView, nameLp);

        TextView chevron = new TextView(this);
        chevron.setText("変更");
        chevron.setTextSize(13);
        chevron.setTextColor(Ui.ACCENT);
        appRow.addView(chevron);

        appCard.addView(appRow);

        groupRow = new TextView(this);
        groupRow.setTextSize(13);
        groupRow.setTextColor(Ui.ACCENT);
        groupRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        groupRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGroupPicker();
            }
        });
        appCard.addView(groupRow);
        updateGroupRow();
        appCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAppPicker();
            }
        });
        root.addView(appCard, margin(0, 0, 0, 12));

        // --- repeat (collapsible) ----------------------------------------
        root.addView(section("repeat", "繰り返し", entry.daySummary()), margin(0, 0, 0, 12));
        buildRepeat(body("repeat"));

        // --- notification style (collapsible) ----------------------------
        root.addView(section("sound", "通知のしかた", entry.soundSummary()), margin(0, 0, 0, 12));
        buildSound(body("sound"));

        // --- behaviour (collapsible) -------------------------------------
        root.addView(section("behavior", "起動のしかた", behaviorSummary()), margin(0, 0, 0, 12));
        buildBehavior(body("behavior"));

        // --- snooze (collapsible) ----------------------------------------
        root.addView(section("snooze", "再通知", snoozeSummary()), margin(0, 0, 0, 20));
        buildSnooze(body("snooze"));

        // --- test --------------------------------------------------------
        Button test = new Button(this);
        test.setText("テスト実行（通知を今すぐ出す）");
        test.setAllCaps(false);
        test.setTextSize(14);
        test.setTextColor(Ui.ACCENT);
        test.setBackground(Ui.roundedStroke(0xFFFFFFFF, Ui.dp(this, 12), 0xFFBFD3F7, Ui.dp(this, 1)));
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testRun();
            }
        });
        root.addView(test, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        // --- save --------------------------------------------------------
        Button save = new Button(this);
        save.setText(isNew ? "この内容で追加" : "保存する");
        save.setTextSize(16);
        save.setAllCaps(false);
        save.setTextColor(0xFFFFFFFF);
        save.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(this, 14)));
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        saveLp.topMargin = Ui.dp(this, 12);
        root.addView(save, saveLp);

        if (!isNew) {
            Button del = new Button(this);
            del.setText("このアラームを削除");
            del.setTextSize(14);
            del.setAllCaps(false);
            del.setTextColor(0xFFDC2626);
            del.setBackgroundColor(0x00000000);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(EditActivity.this)
                            .setTitle("削除しますか？")
                            .setPositiveButton("削除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    AlarmScheduler.cancel(EditActivity.this, entry.id);
                                    store.delete(entry.id);
                                    finish();
                                }
                            })
                            .setNegativeButton("キャンセル", null)
                            .show();
                }
            });
            root.addView(del, margin(0, 8, 0, 0));
        }

        scroll.addView(root);
        return scroll;
    }

    private void buildRepeat(LinearLayout body) {
        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        String[] labels = {"1回だけ（次のその時刻）", "毎日", "曜日を指定", "日付を指定", "曜日ごとに時刻を変える"};
        int[] values = {AlarmEntry.MODE_ONCE, AlarmEntry.MODE_DAILY, AlarmEntry.MODE_WEEKLY,
                AlarmEntry.MODE_DATE, AlarmEntry.MODE_PERDAY};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setTag(values[i]);
            rb.setTextColor(Ui.TEXT);
            modeGroup.addView(rb);
        }
        modeGroup.check(1000 + modeToIndex(entry.mode));
        modeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                entry.mode = (Integer) group.findViewById(checkedId).getTag();
                updateDayRowVisibility();
                if (entry.mode == AlarmEntry.MODE_PERDAY && allPerDayUnset()) {
                    for (int i = 0; i < 7; i++) {
                        entry.perDayHour[i] = entry.hour;
                        entry.perDayMinute[i] = entry.minute;
                    }
                    updatePerDayLabels();
                }
                refreshSummaries();
            }
        });
        body.addView(modeGroup);

        daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        daysRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        String[] dayNames = {"月", "火", "水", "木", "金", "土", "日"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(dayNames[i]);
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    daySelected[idx] = !daySelected[idx];
                    styleDayChip((TextView) v, daySelected[idx]);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            lp.rightMargin = Ui.dp(this, 4);
            lp.leftMargin = i == 0 ? 0 : Ui.dp(this, 2);
            daysRow.addView(chip, lp);
            styleDayChip(chip, daySelected[i]);
        }
        body.addView(daysRow);

        dateRow = new TextView(this);
        dateRow.setTextSize(15);
        dateRow.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        dateRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker();
            }
        });
        body.addView(dateRow);

        perDayBox = new LinearLayout(this);
        perDayBox.setOrientation(LinearLayout.VERTICAL);
        perDayBox.setPadding(0, Ui.dp(this, 6), 0, 0);
        String[] rowNames = {"月", "火", "水", "木", "金", "土", "日"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));

            TextView day = new TextView(this);
            day.setText(rowNames[i] + "曜");
            day.setTextSize(14);
            day.setTextColor(Ui.TEXT);
            row.addView(day, new LinearLayout.LayoutParams(Ui.dp(this, 52),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView timeBtn = new TextView(this);
            timeBtn.setTextSize(15);
            timeBtn.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            timeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int h = entry.perDayHour[idx] >= 0 ? entry.perDayHour[idx] : entry.hour;
                    int m = entry.perDayHour[idx] >= 0 ? entry.perDayMinute[idx] : entry.minute;
                    new TimePickerDialog(EditActivity.this, new TimePickerDialog.OnTimeSetListener() {
                        @Override
                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                            entry.perDayHour[idx] = hourOfDay;
                            entry.perDayMinute[idx] = minute;
                            updatePerDayLabels();
                            refreshSummaries();
                        }
                    }, h, m, true).show();
                }
            });
            row.addView(timeBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            perDayButtons[i] = timeBtn;

            TextView clear = new TextView(this);
            clear.setText("×");
            clear.setTextSize(16);
            clear.setTextColor(Ui.MUTED);
            clear.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    entry.perDayHour[idx] = -1;
                    updatePerDayLabels();
                    refreshSummaries();
                }
            });
            row.addView(clear);

            perDayBox.addView(row);
        }
        body.addView(perDayBox);

        holidayBox = new CheckBox(this);
        holidayBox.setText("日本の祝日はスキップする");
        holidayBox.setTextSize(14);
        holidayBox.setTextColor(Ui.TEXT);
        holidayBox.setChecked(entry.skipHolidays);
        holidayBox.setPadding(0, Ui.dp(this, 10), 0, 0);
        body.addView(holidayBox);
    }

    private void buildSound(LinearLayout body) {
        soundGroup = new RadioGroup(this);
        soundGroup.setOrientation(RadioGroup.VERTICAL);
        String[] labels = {"標準の通知音", "通知音なし", "通知音を選ぶ", "音声で読み上げ"};
        int[] values = {AlarmEntry.SOUND_DEFAULT, AlarmEntry.SOUND_SILENT,
                AlarmEntry.SOUND_CUSTOM, AlarmEntry.SOUND_VOICE};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(2000 + i);
            rb.setText(labels[i]);
            rb.setTextSize(15);
            rb.setTag(values[i]);
            rb.setTextColor(Ui.TEXT);
            soundGroup.addView(rb);
        }
        soundGroup.check(2000 + soundToIndex(entry.soundMode));
        soundGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                entry.soundMode = (Integer) group.findViewById(checkedId).getTag();
                updateSoundRows();
                refreshSummaries();
            }
        });
        body.addView(soundGroup);

        soundRow = new TextView(this);
        soundRow.setTextSize(13);
        soundRow.setTextColor(Ui.ACCENT);
        soundRow.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        soundRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSoundPicker();
            }
        });
        body.addView(soundRow);

        voiceInput = new EditText(this);
        voiceInput.setText(entry.voiceText);
        voiceInput.setHint("読み上げる文章");
        voiceInput.setTextSize(15);
        voiceInput.setTextColor(Ui.TEXT);
        voiceInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        voiceInput.setBackground(Ui.roundedStroke(0xFFF3F4F6, Ui.dp(this, 10), 0xFFD1D5DB, Ui.dp(this, 1)));
        body.addView(voiceInput);
    }

    private void buildBehavior(LinearLayout body) {
        autoBox = new CheckBox(this);
        autoBox.setText("確認せずに自動で開く");
        autoBox.setTextSize(15);
        autoBox.setTextColor(Ui.TEXT);
        autoBox.setChecked(entry.autoOpen);
        autoBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                entry.autoOpen = isChecked;
                if (isChecked && !Settings.canDrawOverlays(EditActivity.this)) {
                    askOverlayPermission();
                }
                refreshSummaries();
            }
        });
        body.addView(autoBox);

        TextView autoNote = new TextView(this);
        autoNote.setText("オンにすると「開きますか？」の通知を出さずにアプリを起動します。"
                + "初回は「他のアプリの上に重ねて表示」の許可が必要です。");
        autoNote.setTextSize(12);
        autoNote.setTextColor(Ui.MUTED);
        autoNote.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        body.addView(autoNote);

        alarmModeBox = new CheckBox(this);
        alarmModeBox.setText("アラームとして鳴らす（停止 / スヌーズ）");
        alarmModeBox.setTextSize(15);
        alarmModeBox.setTextColor(Ui.TEXT);
        alarmModeBox.setChecked(entry.alarmMode);
        alarmModeBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean c) {
                entry.alarmMode = c;
                if (c && !NotificationHelper.canUseFullScreen(EditActivity.this)) {
                    askFullScreenPermission();
                }
                refreshSummaries();
            }
        });
        body.addView(alarmModeBox);

        TextView fsNote = new TextView(this);
        fsNote.setText("時刻になると画面が点灯し、アラーム音が鳴ります。"
                + "「停止」でアプリを起動、「スヌーズ」で後でもう一度鳴ります。\n"
                + "ロック中の場合は、停止したあとロックを解除するとアプリが開きます。");
        fsNote.setTextSize(12);
        fsNote.setTextColor(Ui.MUTED);
        fsNote.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        body.addView(fsNote);

        alarmSoundRow = new TextView(this);
        alarmSoundRow.setTextSize(15);
        alarmSoundRow.setTextColor(Ui.ACCENT);
        alarmSoundRow.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        alarmSoundRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAlarmSoundPicker();
            }
        });
        body.addView(alarmSoundRow);
        updateAlarmSoundRow();

        alarmModeBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean c) {
                entry.alarmMode = c;
                if (c && !NotificationHelper.canUseFullScreen(EditActivity.this)) {
                    askFullScreenPermission();
                }
                updateAlarmSoundRow();
                refreshSummaries();
            }
        });
    }

    private void updateGroupRow() {
        if (groupRow == null) return;
        groupRow.setText(entry.group == null || entry.group.isEmpty()
                ? "グループ: なし"
                : "グループ: " + entry.group);
    }

    private void showGroupPicker() {
        final List<String> groups = new ArrayList<>();
        for (AlarmEntry e : store.all()) {
            if (e.group != null && !e.group.isEmpty() && !groups.contains(e.group)) {
                groups.add(e.group);
            }
        }
        final String[] items = new String[groups.size() + 2];
        items[0] = "なし";
        for (int i = 0; i < groups.size(); i++) items[i + 1] = groups.get(i);
        items[items.length - 1] = "新しいグループ…";

        new AlertDialog.Builder(this)
                .setTitle("グループ")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            entry.group = "";
                            updateGroupRow();
                        } else if (which == items.length - 1) {
                            askNewGroup();
                        } else {
                            entry.group = groups.get(which - 1);
                            updateGroupRow();
                        }
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void askNewGroup() {
        final EditText input = new EditText(this);
        input.setHint("例: 仕事");
        input.setTextSize(16);
        new AlertDialog.Builder(this)
                .setTitle("グループ名")
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String name = input.getText().toString().trim();
                        entry.group = name;
                        updateGroupRow();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void updateAlarmSoundRow() {
        if (alarmSoundRow == null) return;
        alarmSoundRow.setVisibility(entry.alarmMode ? View.VISIBLE : View.GONE);
        if (!entry.alarmMode) return;
        alarmSoundRow.setText(entry.alarmSoundUri.isEmpty()
                ? "アラーム音: 端末の既定"
                : "アラーム音: " + ringtoneTitle(entry.alarmSoundUri));
    }

    private void showAlarmSoundPicker() {
        final List<String> names = new ArrayList<>();
        final List<String> uris = new ArrayList<>();
        names.add("端末の既定のアラーム音");
        uris.add("");
        try {
            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(RingtoneManager.TYPE_ALARM | RingtoneManager.TYPE_RINGTONE
                    | RingtoneManager.TYPE_NOTIFICATION);
            android.database.Cursor cur = rm.getCursor();
            if (cur != null) {
                try {
                    while (cur.moveToNext()) {
                        String title = cur.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                        String base = cur.getString(RingtoneManager.URI_COLUMN_INDEX);
                        String rid = cur.getString(RingtoneManager.ID_COLUMN_INDEX);
                        names.add(title == null ? "(無題)" : title);
                        uris.add(base + "/" + rid);
                    }
                } finally {
                    cur.close();
                }
            }
        } catch (Exception ignored) {
        }

        new AlertDialog.Builder(this)
                .setTitle("アラーム音を選択")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        entry.alarmSoundUri = uris.get(which);
                        updateAlarmSoundRow();
                        try {
                            if (!entry.alarmSoundUri.isEmpty()) {
                                Ringtone r = RingtoneManager.getRingtone(
                                        EditActivity.this, Uri.parse(entry.alarmSoundUri));
                                if (r != null) r.play();
                            }
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void buildSnooze(LinearLayout body) {
        TextView caption1 = caption("再通知までの時間");
        body.addView(caption1);
        snoozeIntervalRow = new TextView(this);
        snoozeIntervalRow.setTextSize(16);
        snoozeIntervalRow.setTextColor(Ui.TEXT);
        snoozeIntervalRow.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        snoozeIntervalRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSnoozeInterval();
            }
        });
        body.addView(snoozeIntervalRow);

        TextView caption2 = caption("再通知できる回数");
        body.addView(caption2);
        snoozeLimitRow = new TextView(this);
        snoozeLimitRow.setTextSize(16);
        snoozeLimitRow.setTextColor(Ui.TEXT);
        snoozeLimitRow.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        snoozeLimitRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSnoozeLimit();
            }
        });
        body.addView(snoozeLimitRow);
        updateSnoozeRows();
    }

    // ------------------------------------------------------------------ helpers

    private LinearLayout section(String key, String title, String summary) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        box.setElevation(Ui.dp(this, 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Ui.TEXT);
        texts.addView(t);

        TextView s = new TextView(this);
        s.setText(summary);
        s.setTextSize(12);
        s.setTextColor(Ui.MUTED);
        texts.addView(s);

        final TextView chev = new TextView(this);
        chev.setText("▼");
        chev.setTextSize(13);
        chev.setTextColor(Ui.MUTED);
        chev.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), 0, Ui.dp(this, 6));

        header.addView(texts);
        header.addView(chev);

        final LinearLayout bodyBox = new LinearLayout(this);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        bodyBox.setVisibility(View.GONE);
        bodyBox.setPadding(0, Ui.dp(this, 8), 0, 0);

        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean show = bodyBox.getVisibility() != View.VISIBLE;
                bodyBox.setVisibility(show ? View.VISIBLE : View.GONE);
                chev.setText(show ? "▲" : "▼");
            }
        });

        box.addView(header);
        box.addView(bodyBox);

        summaries.put(key, s);
        bodies.put(key, bodyBox);
        return box;
    }

    private LinearLayout body(String key) {
        return bodies.get(key);
    }

    private void refreshSummaries() {
        setSummary("repeat", entry.daySummary());
        setSummary("sound", entry.soundSummary());
        setSummary("behavior", behaviorSummary());
        setSummary("snooze", snoozeSummary());
        updateSnoozeRows();
    }

    private void setSummary(String key, String text) {
        TextView v = summaries.get(key);
        if (v != null) v.setText(text);
    }

    private String behaviorSummary() {
        StringBuilder sb = new StringBuilder(entry.autoOpen ? "自動で開く" : "確認して開く");
        if (entry.alarmMode) sb.append(" ・ アラーム");
        return sb.toString();
    }

    private String limitText(int limit) {
        if (limit == AppSettings.SNOOZE_NONE) return "再通知しない";
        if (limit == AppSettings.SNOOZE_UNLIMITED) return "上限なし";
        return limit + "回まで";
    }

    private String snoozeSummary() {
        int minutes = entry.snoozeMinutes > 0 ? entry.snoozeMinutes : AppSettings.snoozeMinutes(this);
        int limit = entry.maxSnooze >= 0 || entry.maxSnooze == AppSettings.SNOOZE_NONE
                ? entry.maxSnooze : AppSettings.maxSnooze(this);
        String suffix = (entry.snoozeMinutes > 0 && entry.maxSnooze != -1) ? "" : "（設定に準拠）";
        return minutes + "分 ・ " + limitText(limit) + suffix;
    }

    private void updateSnoozeRows() {
        if (snoozeIntervalRow == null || snoozeLimitRow == null) return;
        int minutes = entry.snoozeMinutes > 0 ? entry.snoozeMinutes : AppSettings.snoozeMinutes(this);
        snoozeIntervalRow.setText(entry.snoozeMinutes > 0
                ? minutes + "分後"
                : minutes + "分後（アプリ設定：" + AppSettings.snoozeMinutes(this) + "分）");
        int limit = entry.maxSnooze != -1 ? entry.maxSnooze : AppSettings.maxSnooze(this);
        snoozeLimitRow.setText(entry.maxSnooze != -1
                ? limitText(limit)
                : limitText(limit) + "（アプリ設定：" + limitText(AppSettings.maxSnooze(this)) + "）");
    }

    private void pickSnoozeInterval() {
        final int[] choices = {0, 5, 10, 15, 30, 60};
        String[] labels = new String[choices.length];
        labels[0] = "アプリ設定に従う";
        for (int i = 1; i < choices.length; i++) labels[i] = choices[i] + "分";
        int current = entry.snoozeMinutes;
        int checked = 0;
        for (int i = 0; i < choices.length; i++) if (choices[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("再通知までの時間")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        entry.snoozeMinutes = choices[which];
                        updateSnoozeRows();
                        refreshSummaries();
                        d.dismiss();
                    }
                })
                .show();
    }

    private void pickSnoozeLimit() {
        final int[] choices = {-1, AppSettings.SNOOZE_UNLIMITED, 1, 2, 3, 5, AppSettings.SNOOZE_NONE};
        String[] labels = {"アプリ設定に従う", "上限なし", "1回まで", "2回まで", "3回まで", "5回まで", "再通知しない"};
        int current = entry.maxSnooze;
        int checked = 0;
        for (int i = 0; i < choices.length; i++) if (choices[i] == current) checked = i;
        new AlertDialog.Builder(this)
                .setTitle("再通知できる回数")
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        entry.maxSnooze = choices[which];
                        updateSnoozeRows();
                        refreshSummaries();
                        d.dismiss();
                    }
                })
                .show();
    }

    private void pickTime() {
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                entry.hour = hourOfDay;
                entry.minute = minute;
                updateTimeText();
            }
        }, entry.hour, entry.minute, true).show();
    }

    private int modeToIndex(int mode) {
        if (mode == AlarmEntry.MODE_ONCE) return 0;
        if (mode == AlarmEntry.MODE_WEEKLY) return 2;
        if (mode == AlarmEntry.MODE_DATE) return 3;
        if (mode == AlarmEntry.MODE_PERDAY) return 4;
        return 1;
    }

    private int soundToIndex(int mode) {
        switch (mode) {
            case AlarmEntry.SOUND_SILENT:
                return 1;
            case AlarmEntry.SOUND_CUSTOM:
                return 2;
            case AlarmEntry.SOUND_VOICE:
                return 3;
            default:
                return 0;
        }
    }

    private void updateSoundRows() {
        if (soundRow == null || voiceInput == null) return;
        boolean custom = entry.soundMode == AlarmEntry.SOUND_CUSTOM;
        soundRow.setVisibility(custom ? View.VISIBLE : View.GONE);
        if (custom) {
            soundRow.setText(entry.soundUri.isEmpty()
                    ? "タップして通知音を選ぶ"
                    : "選択中: " + ringtoneTitle(entry.soundUri));
        }
        voiceInput.setVisibility(entry.soundMode == AlarmEntry.SOUND_VOICE ? View.VISIBLE : View.GONE);
    }

    private String ringtoneTitle(String uri) {
        try {
            Ringtone r = RingtoneManager.getRingtone(this, Uri.parse(uri));
            if (r != null) {
                String t = r.getTitle(this);
                if (t != null && !t.isEmpty()) return t;
            }
        } catch (Exception ignored) {
        }
        return "選択した通知音";
    }

    private void updateDayRowVisibility() {
        boolean perDay = entry.mode == AlarmEntry.MODE_PERDAY;
        daysRow.setVisibility(entry.mode == AlarmEntry.MODE_WEEKLY ? View.VISIBLE : View.GONE);
        if (perDayBox != null) perDayBox.setVisibility(perDay ? View.VISIBLE : View.GONE);
        if (timeCard != null) timeCard.setVisibility(perDay ? View.GONE : View.VISIBLE);
        if (dateRow != null) {
            dateRow.setVisibility(entry.mode == AlarmEntry.MODE_DATE ? View.VISIBLE : View.GONE);
            if (entry.mode == AlarmEntry.MODE_DATE) updateDateText();
        }
        if (holidayBox != null) {
            holidayBox.setEnabled(entry.mode == AlarmEntry.MODE_DAILY
                    || entry.mode == AlarmEntry.MODE_WEEKLY
                    || entry.mode == AlarmEntry.MODE_PERDAY
                    || entry.mode == AlarmEntry.MODE_DATE);
        }
        if (perDay) updatePerDayLabels();
    }

    private void updatePerDayLabels() {
        if (perDayButtons[0] == null) return;
        for (int i = 0; i < 7; i++) {
            boolean set = entry.perDayHour[i] >= 0;
            perDayButtons[i].setText(set
                    ? String.format(java.util.Locale.JAPAN, "%02d:%02d", entry.perDayHour[i], entry.perDayMinute[i])
                    : "未設定");
            perDayButtons[i].setTextColor(set ? Ui.TEXT : Ui.MUTED);
        }
    }

    private boolean allPerDayUnset() {
        for (int i = 0; i < 7; i++) if (entry.perDayHour[i] >= 0) return false;
        return true;
    }

    private void updateDateText() {
        if (entry.year <= 0) {
            dateRow.setText("タップして日付を選ぶ");
            dateRow.setTextColor(Ui.ACCENT);
        } else {
            java.time.LocalDate d = java.time.LocalDate.of(entry.year, entry.month, entry.day);
            String[] wd = {"月", "火", "水", "木", "金", "土", "日"};
            dateRow.setText(String.format(java.util.Locale.JAPAN, "%d年%d月%d日（%s）",
                    entry.year, entry.month, entry.day, wd[d.getDayOfWeek().getValue() - 1]));
            dateRow.setTextColor(Ui.TEXT);
        }
    }

    private void showDatePicker() {
        java.time.LocalDate base = entry.year > 0
                ? java.time.LocalDate.of(entry.year, entry.month, entry.day)
                : java.time.LocalDate.now();
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(android.widget.DatePicker view, int y, int m, int d) {
                entry.year = y;
                entry.month = m + 1;
                entry.day = d;
                updateDateText();
                refreshSummaries();
            }
        }, base.getYear(), base.getMonthValue() - 1, base.getDayOfMonth()).show();
    }

    private void updateTimeText() {
        timeView.setText(entry.timeText());
    }

    private void updateAppText() {
        if (entry.pkg == null || entry.pkg.isEmpty()) {
            appNameView.setText("タップして選択");
            appNameView.setTextColor(Ui.MUTED);
            appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        appNameView.setText(entry.label);
        appNameView.setTextColor(Ui.TEXT);
        try {
            appIconView.setImageDrawable(getPackageManager().getApplicationIcon(entry.pkg));
        } catch (Exception ex) {
            appIconView.setImageResource(android.R.drawable.sym_def_app_icon);
        }
    }

    private void showAppPicker() {
        final PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> raw = pm.queryIntentActivities(query, 0);
        final List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : raw) {
            if (ri.activityInfo == null) continue;
            if (getPackageName().equals(ri.activityInfo.packageName)) continue;
            apps.add(ri);
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });

        ArrayAdapter<ResolveInfo> adapter = new ArrayAdapter<ResolveInfo>(this, 0, apps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(EditActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(EditActivity.this, 20), Ui.dp(EditActivity.this, 10),
                        Ui.dp(EditActivity.this, 20), Ui.dp(EditActivity.this, 10));

                ResolveInfo ri = getItem(position);
                ImageView icon = new ImageView(EditActivity.this);
                icon.setImageDrawable(ri.loadIcon(pm));
                row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(EditActivity.this, 32),
                        Ui.dp(EditActivity.this, 32)));

                TextView text = new TextView(EditActivity.this);
                text.setText(ri.loadLabel(pm));
                text.setTextSize(16);
                text.setTextColor(Ui.TEXT);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.leftMargin = Ui.dp(EditActivity.this, 14);
                row.addView(text, lp);
                return row;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle("開くアプリを選択")
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ResolveInfo ri = apps.get(which);
                        entry.pkg = ri.activityInfo.packageName;
                        entry.label = ri.loadLabel(pm).toString();
                        updateAppText();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void showSoundPicker() {
        final List<String> names = new ArrayList<>();
        final List<String> uris = new ArrayList<>();
        try {
            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(RingtoneManager.TYPE_NOTIFICATION | RingtoneManager.TYPE_ALARM);
            android.database.Cursor cur = rm.getCursor();
            if (cur != null) {
                try {
                    while (cur.moveToNext()) {
                        String title = cur.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                        String base = cur.getString(RingtoneManager.URI_COLUMN_INDEX);
                        String rid = cur.getString(RingtoneManager.ID_COLUMN_INDEX);
                        names.add(title == null ? "(無題)" : title);
                        uris.add(base + "/" + rid);
                    }
                } finally {
                    cur.close();
                }
            }
        } catch (Exception ignored) {
        }

        if (names.isEmpty()) {
            Toast.makeText(this, "選べる通知音が見つかりません", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("通知音を選択")
                .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        entry.soundUri = uris.get(which);
                        updateSoundRows();
                        refreshSummaries();
                        try {
                            Ringtone r = RingtoneManager.getRingtone(EditActivity.this, Uri.parse(entry.soundUri));
                            if (r != null) r.play();
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void askFullScreenPermission() {
        new AlertDialog.Builder(this)
                .setTitle("「全画面通知」の許可が必要です")
                .setMessage("ロック画面の上にアプリを開くには「全画面通知」の許可が必要です。\n\n"
                        + "Android 14 以降、ストア以外から入れたアプリでは初期状態でオフになっています。\n"
                        + "設定 → アプリ → アプリ起動アラーム → 「全画面通知」をオンにしてください。\n"
                        + "項目がグレーの場合は、先に「制限付き設定を許可」をオンにしてください。")
                .setPositiveButton("設定を開く", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            startActivity(new Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ex) {
                            try {
                                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception ignored) {
                                Toast.makeText(EditActivity.this, "設定画面を開けませんでした",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setNegativeButton("あとで", null)
                .show();
    }

    private void askOverlayPermission() {
        new AlertDialog.Builder(this)
                .setTitle("「重ねて表示」の許可が必要です")
                .setMessage("確認せずにアプリを開くには「他のアプリの上に重ねて表示」の許可が必要です。\n\n"
                        + "Android 13 以降、ストア以外から入れたアプリは「制限付き設定」でこの許可がブロックされ、"
                        + "設定画面で『アプリはアクセスを拒否されました』と出ることがあります。\n"
                        + "その場合は 設定 → アプリ → アプリ起動アラーム → 右上の ⋮ → 「制限付き設定を許可」"
                        + "をオンにしてから、もう一度お試しください。\n\n"
                        + "（許可しなくても、「ロック画面の上にアプリの画面を出す」を使えば通知なしで開けます）")
                .setPositiveButton("アプリ情報を開く", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ex) {
                            Toast.makeText(EditActivity.this, "設定画面を開けませんでした",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNeutralButton("重ねて表示の設定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception ex) {
                            Toast.makeText(EditActivity.this, "設定画面を開けませんでした",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    // ------------------------------------------------------------------ actions

    private void testRun() {
        if (entry.pkg == null || entry.pkg.isEmpty()) {
            Toast.makeText(this, "先に開くアプリを選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        AlarmEntry probe = AlarmEntry.fromJson(entry.toJson());
        probe.enabled = true;
        NotificationHelper.show(this, probe, 0);
        Toast.makeText(this, "テスト通知を出しました", Toast.LENGTH_SHORT).show();
    }

    private void save() {
        if (entry.pkg == null || entry.pkg.isEmpty()) {
            Toast.makeText(this, "開くアプリを選択してください", Toast.LENGTH_SHORT).show();
            return;
        }
        int checked = modeGroup.getCheckedRadioButtonId();
        if (checked != -1) entry.mode = (Integer) modeGroup.findViewById(checked).getTag();

        int bits = 0;
        for (int i = 0; i < 7; i++) if (daySelected[i]) bits |= (1 << i);
        entry.days = bits;
        entry.skipHolidays = (entry.mode == AlarmEntry.MODE_DAILY || entry.mode == AlarmEntry.MODE_WEEKLY
                || entry.mode == AlarmEntry.MODE_PERDAY || entry.mode == AlarmEntry.MODE_DATE)
                && holidayBox.isChecked();
        entry.enabled = true;
        entry.autoOpen = autoBox.isChecked();
        entry.alarmMode = alarmModeBox.isChecked();

        String spoken = voiceInput.getText().toString().trim();
        entry.voiceText = spoken.isEmpty() ? "アプリを開く時間です" : spoken;

        if (entry.mode == AlarmEntry.MODE_WEEKLY && bits == 0) {
            Toast.makeText(this, "曜日を1つ以上選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.mode == AlarmEntry.MODE_DATE && entry.year <= 0) {
            Toast.makeText(this, "日付を選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.mode == AlarmEntry.MODE_PERDAY && allPerDayUnset()) {
            Toast.makeText(this, "時刻を1つ以上設定してください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.soundMode == AlarmEntry.SOUND_CUSTOM && entry.soundUri.isEmpty()) {
            Toast.makeText(this, "通知音を選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        if (entry.alarmMode && !NotificationHelper.canUseFullScreen(this)) {
            Toast.makeText(this, "「全画面通知」が許可されていないため、通知での確認になります",
                    Toast.LENGTH_LONG).show();
        }

        store.upsert(entry);
        long at = AlarmScheduler.schedule(this, entry);
        if (at > 0) {
            java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(at), java.time.ZoneId.systemDefault());
            Toast.makeText(this, String.format(java.util.Locale.JAPAN, "次回 %02d/%02d %02d:%02d",
                    t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute()),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "保存しました（次回の予定は未定）", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    // ------------------------------------------------------------------ chrome

    private void styleDayChip(TextView chip, boolean selected) {
        chip.setBackground(Ui.rounded(selected ? Ui.ACCENT : 0xFFE5E7EB, Ui.dp(this, 10)));
        chip.setTextColor(selected ? 0xFFFFFFFF : Ui.MUTED);
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
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
