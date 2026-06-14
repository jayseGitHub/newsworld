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

import io.newsworld.dashboard.MainActivity;
import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentClustersBinding;
import io.newsworld.dashboard.model.ClusterDto;
import io.newsworld.dashboard.ui.ClusterAdapter;

public class ClustersFragment extends Fragment {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private FragmentClustersBinding binding;
    private ClusterAdapter adapter;
    private LocalDate currentDate = LocalDate.now();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentClustersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new ClusterAdapter(cluster -> {
            if (getActivity() instanceof MainActivity ma) {
                ma.navigateDetail(ClusterDetailFragment.newInstance(cluster));
            }
        });
        binding.recyclerClusters.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerClusters.setAdapter(adapter);

        updateDateLabel();
        binding.btnPrevDay.setOnClickListener(v -> { currentDate = currentDate.minusDays(1); updateDateLabel(); load(); });
        binding.btnNextDay.setOnClickListener(v -> { currentDate = currentDate.plusDays(1); updateDateLabel(); load(); });
        binding.swipeRefresh.setOnRefreshListener(this::load);

        load();
    }

    private void updateDateLabel() {
        if (binding != null) binding.textDate.setText(DATE_FMT.format(currentDate));
    }

    private void load() {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(true);
        NewsWorldApi api = new NewsWorldApi(requireContext());
        LocalDate date = currentDate;

        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                List<ClusterDto> clusters = api.getClusters(date);
                runOnUi(() -> {
                    adapter.submitList(clusters);
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
