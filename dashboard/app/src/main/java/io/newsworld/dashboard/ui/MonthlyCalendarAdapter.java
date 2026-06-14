package io.newsworld.dashboard.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.newsworld.dashboard.R;

public class MonthlyCalendarAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onPrevMonth();
        void onNextMonth();
        void onDaySelected(int dayOfMonth);
    }

    private static final int TYPE_HEADER   = 0;
    private static final int TYPE_DAY_NAME = 1;
    private static final int TYPE_DAY      = 2;

    private static final String[] DAY_NAMES = {"L", "M", "M", "J", "V", "S", "D"};
    private static final SimpleDateFormat SDF_MONTH = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

    private final Listener listener;
    private Calendar month = Calendar.getInstance();
    private int selectedDay = -1;
    private int todayDay    = -1;
    private Set<Integer> daysWithData = new HashSet<>();
    private final List<Integer> dayCells = new ArrayList<>();

    public MonthlyCalendarAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setMonth(Calendar c, int selectedDay, Set<Integer> daysWithData) {
        this.month = (Calendar) c.clone();
        this.month.set(Calendar.DAY_OF_MONTH, 1);
        this.selectedDay  = selectedDay;
        this.daysWithData = new HashSet<>(daysWithData);
        Calendar now = Calendar.getInstance();
        this.todayDay = (now.get(Calendar.YEAR)  == this.month.get(Calendar.YEAR) &&
                         now.get(Calendar.MONTH) == this.month.get(Calendar.MONTH))
                ? now.get(Calendar.DAY_OF_MONTH) : -1;
        computeDayCells();
        notifyDataSetChanged();
    }

    public void setSelectedDay(int day) {
        selectedDay = day;
        notifyDataSetChanged();
    }

    private void computeDayCells() {
        dayCells.clear();
        int offset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        for (int i = 0; i < offset; i++) dayCells.add(0);
        int days = month.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int d = 1; d <= days; d++) dayCells.add(d);
        while (dayCells.size() % 7 != 0) dayCells.add(0);
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) { return (position == 0) ? 7 : 1; }
        };
    }

    @Override public int getItemViewType(int position) {
        if (position == 0)  return TYPE_HEADER;
        if (position <= 7)  return TYPE_DAY_NAME;
        return TYPE_DAY;
    }

    @Override public int getItemCount() { return 1 + 7 + dayCells.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER)
            return new HeaderVH(inf.inflate(R.layout.item_calendar_month_header, parent, false));
        if (viewType == TYPE_DAY_NAME)
            return new DayNameVH(inf.inflate(R.layout.item_calendar_day_name, parent, false));
        return new DayVH(inf.inflate(R.layout.item_calendar_day_cell, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH)       bindHeader((HeaderVH) holder);
        else if (holder instanceof DayNameVH) ((DayNameVH) holder).tvName.setText(DAY_NAMES[position - 1]);
        else                                  bindDay((DayVH) holder, dayCells.get(position - 8));
    }

    private void bindHeader(HeaderVH h) {
        String title = SDF_MONTH.format(month.getTime());
        title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        h.tvMonthYear.setText(title);
        h.btnPrev.setOnClickListener(v -> listener.onPrevMonth());
        h.btnNext.setOnClickListener(v -> listener.onNextMonth());
    }

    private void bindDay(DayVH h, int day) {
        if (day == 0) {
            h.tvDay.setText("");
            h.tvDay.setBackground(null);
            h.dot.setVisibility(View.INVISIBLE);
            h.itemView.setClickable(false);
            h.itemView.setOnClickListener(null);
            return;
        }
        h.tvDay.setText(String.valueOf(day));
        h.itemView.setClickable(true);
        h.itemView.setOnClickListener(v -> listener.onDaySelected(day));
        h.dot.setVisibility(daysWithData.contains(day) ? View.VISIBLE : View.INVISIBLE);

        if (day == selectedDay) {
            h.tvDay.setBackgroundResource(R.drawable.bg_day_selected);
            h.tvDay.setTextColor(0xFF0A1628);   // colorOnPrimary — navy sur bleu
        } else if (day == todayDay) {
            h.tvDay.setBackgroundResource(R.drawable.bg_day_today);
            h.tvDay.setTextColor(0xFF4FC3F7);   // colorPrimary — bleu ciel
        } else {
            h.tvDay.setBackground(null);
            h.tvDay.setTextColor(0xFFE8EAF6);   // colorOnSurface
        }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView tvMonthYear, btnPrev, btnNext;
        HeaderVH(@NonNull View v) {
            super(v);
            tvMonthYear = v.findViewById(R.id.tv_month_year);
            btnPrev     = v.findViewById(R.id.btn_prev_month);
            btnNext     = v.findViewById(R.id.btn_next_month);
        }
    }

    static class DayNameVH extends RecyclerView.ViewHolder {
        final TextView tvName;
        DayNameVH(@NonNull View v) { super(v); tvName = v.findViewById(R.id.tv_day_name); }
    }

    static class DayVH extends RecyclerView.ViewHolder {
        final TextView tvDay;
        final View dot;
        DayVH(@NonNull View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day_number);
            dot   = v.findViewById(R.id.dot_data);
        }
    }
}
