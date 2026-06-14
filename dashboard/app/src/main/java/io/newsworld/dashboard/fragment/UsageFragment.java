package io.newsworld.dashboard.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentUsageBinding;
import io.newsworld.dashboard.model.LlmUsageDto;
import io.newsworld.dashboard.ui.LlmUsageAdapter;
import io.newsworld.dashboard.ui.MonthlyCalendarAdapter;

public class UsageFragment extends Fragment {

    private FragmentUsageBinding binding;
    private LlmUsageAdapter adapter;
    private MonthlyCalendarAdapter calendarAdapter;

    private Calendar currentMonth;
    private int selectedDay = -1;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUsageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        adapter = new LlmUsageAdapter();
        binding.recyclerUsage.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerUsage.setAdapter(adapter);

        calendarAdapter = new MonthlyCalendarAdapter(new MonthlyCalendarAdapter.Listener() {
            @Override public void onPrevMonth() { shiftMonth(-1); }
            @Override public void onNextMonth() { shiftMonth(1); }
            @Override public void onDaySelected(int day) { selectDay(day); }
        });
        GridLayoutManager glm = new GridLayoutManager(requireContext(), 7);
        glm.setSpanSizeLookup(calendarAdapter.getSpanSizeLookup());
        binding.recyclerCalendar.setLayoutManager(glm);
        binding.recyclerCalendar.setAdapter(calendarAdapter);

        binding.swipeRefresh.setOnRefreshListener(() -> {
            if (selectedDay > 0) loadDay(getSelectedDate());
            else loadMonthDays();
        });

        loadTotal();
        refresh();
    }

    private void shiftMonth(int delta) {
        currentMonth.add(Calendar.MONTH, delta);
        selectedDay = -1;
        hideSummary();
        refresh();
    }

    private void selectDay(int day) {
        selectedDay = (selectedDay == day) ? -1 : day;
        calendarAdapter.setSelectedDay(selectedDay);
        if (selectedDay > 0) loadDay(getSelectedDate());
        else {
            hideSummary();
            adapter.submitList(Collections.emptyList());
        }
    }

    private void refresh() {
        loadMonthDays();
        if (selectedDay > 0) loadDay(getSelectedDate());
        else adapter.submitList(Collections.emptyList());
    }

    private void loadTotal() {
        NewsWorldApi api = new NewsWorldApi(requireContext());
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                Map<String, Object> total = api.getLlmUsageTotal();
                runOnUi(() -> renderTotal(total));
            } catch (Exception ignored) {}
        });
    }

    private void loadMonthDays() {
        if (binding == null) return;
        YearMonth ym = toYearMonth(currentMonth);
        NewsWorldApi api = new NewsWorldApi(requireContext());
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                List<Integer> days = api.getUsageDays(ym);
                Set<Integer> daySet = new HashSet<>(days);
                runOnUi(() -> calendarAdapter.setMonth(currentMonth, selectedDay, daySet));
            } catch (Exception e) {
                runOnUi(() -> calendarAdapter.setMonth(currentMonth, selectedDay, Collections.emptySet()));
            }
        });
    }

    private void loadDay(LocalDate date) {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(true);
        NewsWorldApi api = new NewsWorldApi(requireContext());
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                Map<String, Object> summary = api.getLlmUsageSummary(date);
                List<LlmUsageDto> calls = api.getLlmUsage(date);
                runOnUi(() -> {
                    renderSummary(summary);
                    adapter.submitList(calls);
                    if (binding != null) binding.swipeRefresh.setRefreshing(false);
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    if (binding != null) binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(requireContext(), "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void renderTotal(Map<String, Object> total) {
        if (binding == null || total == null) return;
        long totalCalls  = toLong(total.get("totalCalls"));
        long totalTokens = toLong(total.get("totalTokens"));
        StringBuilder sb = new StringBuilder();
        sb.append("TOTAL  ").append(formatNum(totalTokens)).append(" tokens  •  ").append(totalCalls).append(" appels\n");
        Object byModelObj = total.get("byModel");
        if (byModelObj instanceof Map<?,?> byModel) {
            for (Map.Entry<?,?> entry : byModel.entrySet()) {
                if (entry.getValue() instanceof Map<?,?> stats) {
                    sb.append("  ").append(entry.getKey())
                      .append(": ").append(formatNum(toLong(stats.get("totalTokens"))))
                      .append(" tok  •  ").append(toLong(stats.get("calls"))).append(" appels\n");
                }
            }
        }
        binding.textTotal.setText(sb.toString().trim());
    }

    @SuppressWarnings("unchecked")
    private void renderSummary(Map<String, Object> summary) {
        if (binding == null || summary == null) return;
        StringBuilder sb = new StringBuilder();
        Object byModelObj = summary.get("byModel");
        if (byModelObj instanceof Map<?,?> byModel) {
            for (Map.Entry<?, ?> entry : byModel.entrySet()) {
                if (entry.getValue() instanceof Map<?,?> stats) {
                    sb.append(entry.getKey())
                      .append(": ").append(formatNum(toLong(stats.get("totalTokens"))))
                      .append(" tokens — ").append(toLong(stats.get("calls")))
                      .append(" appels\n");
                }
            }
        }
        if (sb.isEmpty()) {
            hideSummary();
        } else {
            binding.textSummary.setText(sb.toString().trim());
            binding.textSummary.setVisibility(View.VISIBLE);
            binding.dividerSummary.setVisibility(View.VISIBLE);
        }
    }

    private void hideSummary() {
        if (binding == null) return;
        binding.textSummary.setVisibility(View.GONE);
        binding.dividerSummary.setVisibility(View.GONE);
    }

    private LocalDate getSelectedDate() {
        Calendar c = (Calendar) currentMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, selectedDay);
        return LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, selectedDay);
    }

    private YearMonth toYearMonth(Calendar c) {
        return YearMonth.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    private long toLong(Object val) {
        if (val instanceof Double d)  return d.longValue();
        if (val instanceof Long l)    return l;
        if (val instanceof Number n)  return n.longValue();
        return 0L;
    }

    private String formatNum(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private void runOnUi(Runnable r) {
        if (binding == null || !isAdded()) return;
        requireActivity().runOnUiThread(() -> { if (binding != null) r.run(); });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
