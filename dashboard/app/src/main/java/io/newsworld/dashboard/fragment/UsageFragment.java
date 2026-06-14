package io.newsworld.dashboard.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentUsageBinding;
import io.newsworld.dashboard.model.LlmUsageDto;
import io.newsworld.dashboard.ui.LlmUsageAdapter;

public class UsageFragment extends Fragment {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private FragmentUsageBinding binding;
    private LlmUsageAdapter adapter;
    private LocalDate currentDate = LocalDate.now();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentUsageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new LlmUsageAdapter();
        binding.recyclerUsage.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerUsage.setAdapter(adapter);

        updateDateLabel();
        binding.btnPrevDay.setOnClickListener(v -> { currentDate = currentDate.minusDays(1); updateDateLabel(); loadDay(); });
        binding.btnNextDay.setOnClickListener(v -> { currentDate = currentDate.plusDays(1); updateDateLabel(); loadDay(); });
        binding.swipeRefresh.setOnRefreshListener(this::loadDay);

        loadTotal();
        loadDay();
    }

    private void updateDateLabel() {
        if (binding != null) binding.textDate.setText(DATE_FMT.format(currentDate));
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
    private void loadDay() {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(true);
        NewsWorldApi api = new NewsWorldApi(requireContext());
        LocalDate date = currentDate;

        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                Map<String, Object> summary = api.getLlmUsageSummary(date);
                List<LlmUsageDto> calls = api.getLlmUsage(date);
                runOnUi(() -> {
                    renderSummary(summary);
                    adapter.submitList(calls);
                    binding.swipeRefresh.setRefreshing(false);
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
        binding.textSummary.setText(sb.isEmpty() ? "Aucune donnée" : sb.toString().trim());
    }

    private long toLong(Object val) {
        if (val instanceof Double d) return d.longValue();
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
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
