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

import java.util.List;

import io.newsworld.dashboard.MainActivity;
import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentWorldBinding;
import io.newsworld.dashboard.model.CountryDto;
import io.newsworld.dashboard.model.StatsDto;
import io.newsworld.dashboard.ui.CountryAdapter;

public class WorldFragment extends Fragment {

    private FragmentWorldBinding binding;
    private CountryAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWorldBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CountryAdapter(country -> {
            if (getActivity() instanceof MainActivity ma) {
                ma.navigateDetail(CountryDetailFragment.newInstance(country.code, country.name));
            }
        });
        binding.recyclerCountries.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerCountries.setAdapter(adapter);

        binding.swipeRefresh.setOnRefreshListener(this::load);
        load();
    }

    private void load() {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(true);
        NewsWorldApi api = new NewsWorldApi(requireContext());

        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                StatsDto stats = api.getStats();
                List<CountryDto> countries = api.getCountries();
                runOnUi(() -> {
                    binding.textTotalArticles.setText(String.valueOf(stats.totalArticles));
                    binding.textTodayArticles.setText(String.valueOf(stats.todayArticles));
                    binding.textPendingEnrich.setText(String.valueOf(stats.pendingEnrich));
                    binding.textPendingTranslate.setText(String.valueOf(stats.pendingTranslate));
                    adapter.submitList(countries);
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
