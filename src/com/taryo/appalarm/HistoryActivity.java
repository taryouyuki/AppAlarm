package com.taryo.appalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Simple stats + recent firings. */
public class HistoryActivity extends Activity {

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
        title.setText("履歴と統計");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Ui.TEXT);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.bottomMargin = Ui.dp(this, 16);
        root.addView(title, titleLp);

        List<HistoryStore.Record> all = HistoryStore.recent(this);
        int[] counts = HistoryStore.counts(this);

        LinearLayout stats = card();
        stats.addView(caption("集計"));
        String[] labels = {"発火回数", "停止", "スヌーズ", "自動で起動", "通知のみ"};
        int[] values = {all.size(), counts[0], counts[1], counts[2], counts[3]};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            TextView k = new TextView(this);
            k.setText(labels[i]);
            k.setTextSize(14);
            k.setTextColor(Ui.MUTED);
            k.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView v = new TextView(this);
            v.setText(String.valueOf(values[i]));
            v.setTextSize(16);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            v.setTextColor(Ui.TEXT);
            row.addView(k);
            row.addView(v);
            stats.addView(row);
        }
        root.addView(stats, margin(0, 0, 0, 12));

        LinearLayout listCard = card();
        listCard.addView(caption("最近の履歴"));
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("まだ履歴がありません");
            empty.setTextSize(13);
            empty.setTextColor(Ui.MUTED);
            empty.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            listCard.addView(empty);
        } else {
            int limit = Math.min(all.size(), 50);
            for (int i = 0; i < limit; i++) {
                HistoryStore.Record r = all.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));

                TextView when = new TextView(this);
                when.setText(HistoryStore.formatTime(r.time));
                when.setTextSize(12);
                when.setTextColor(Ui.MUTED);
                when.setLayoutParams(new LinearLayout.LayoutParams(
                        Ui.dp(this, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(when);

                TextView name = new TextView(this);
                name.setText(r.label);
                name.setTextSize(14);
                name.setTextColor(Ui.TEXT);
                name.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(name);

                TextView action = new TextView(this);
                action.setText(HistoryStore.actionLabel(r.action));
                action.setTextSize(12);
                action.setTextColor(Ui.ACCENT);
                row.addView(action);

                listCard.addView(row);
            }
        }
        root.addView(listCard, margin(0, 0, 0, 16));

        Button clear = new Button(this);
        clear.setText("履歴を消去");
        clear.setAllCaps(false);
        clear.setTextSize(14);
        clear.setTextColor(0xFFDC2626);
        clear.setBackground(Ui.roundedStroke(Ui.CARD, Ui.dp(this, 12), Ui.BORDER, Ui.dp(this, 1)));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("履歴を消去しますか？")
                        .setPositiveButton("消去", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                HistoryStore.clear(HistoryActivity.this);
                                recreate();
                            }
                        })
                        .setNegativeButton("キャンセル", null)
                        .show();
            }
        });
        root.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48)));

        scroll.addView(root);
        return scroll;
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
