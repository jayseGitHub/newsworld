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
import java.util.Set;

import io.newsworld.dashboard.MainActivity;
import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentClustersBinding;
import io.newsworld.dashboard.model.ClusterDto;
import io.newsworld.dashboard.ui.ClusterAdapter;
import io.newsworld.dashboard.ui.MonthlyCalendarAdapter;

public class ClustersFragment extends Fragment {

    private FragmentClustersBinding binding;
    private ClusterAdapter adapter;
    private MonthlyCalendarAdapter calendarAdapter;

    private Calendar currentMonth;
    private int selectedDay = -1;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentClustersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentMonth = Calendar.getInstance();
        currentMonth.set(Calendar.DAY_OF_MONTH, 1);
        selectedDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        adapter = new ClusterAdapter(cluster -> {
            if (getActivity() instanceof MainActivity ma) {
                ma.navigateDetail(ClusterDetailFragment.newInstance(cluster));
            }
        });
        binding.recyclerClusters.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerClusters.setAdapter(adapter);

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

        refresh();
    }

    private void shiftMonth(int delta) {
        currentMonth.add(Calendar.MONTH, delta);
        selectedDay = -1;
        refresh();
    }

    private void selectDay(int day) {
        selectedDay = (selectedDay == day) ? -1 : day;
        calendarAdapter.setSelectedDay(selectedDay);
        if (selectedDay > 0) loadDay(getSelectedDate());
        else adapter.submitList(Collections.emptyList());
    }

    private void refresh() {
        loadMonthDays();
        if (selectedDay > 0) loadDay(getSelectedDate());
        else adapter.submitList(Collections.emptyList());
    }

    private void loadMonthDays() {
        if (binding == null) return;
        YearMonth ym = toYearMonth(currentMonth);
        NewsWorldApi api = new NewsWorldApi(requireContext());
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                List<Integer> days = api.getClusterDays(ym);
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
                List<ClusterDto> clusters = api.getClusters(date);
                runOnUi(() -> {
                    adapter.submitList(clusters);
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

    private LocalDate getSelectedDate() {
        Calendar c = (Calendar) currentMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, selectedDay);
        return LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, selectedDay);
    }

    private YearMonth toYearMonth(Calendar c) {
        return YearMonth.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
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
