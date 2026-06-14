package io.newsworld.dashboard.fragment;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import io.newsworld.dashboard.R;
import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentClusterDetailBinding;
import io.newsworld.dashboard.model.ClusterDto;
import io.newsworld.dashboard.model.ClusterSourceDto;
import io.newsworld.dashboard.util.FlagUtils;

public class ClusterDetailFragment extends Fragment {

    private static final String ARG_CLUSTER = "cluster";

    public static ClusterDetailFragment newInstance(ClusterDto cluster) {
        Bundle args = new Bundle();
        args.putSerializable(ARG_CLUSTER, cluster);
        ClusterDetailFragment f = new ClusterDetailFragment();
        f.setArguments(args);
        return f;
    }

    private FragmentClusterDetailBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentClusterDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ClusterDto c = (ClusterDto) requireArguments().getSerializable(ARG_CLUSTER);
        if (c == null) return;

        binding.btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        // Affichage immédiat des données déjà disponibles
        bindCluster(c);

        // Chargement async des sources
        loadSources(c.id);
    }

    private void bindCluster(ClusterDto c) {
        binding.textTopic.setText(c.topic);
        binding.textScore.setText(String.format("Score %.0f  •  %d articles  •  %d continents",
                c.relevanceScore, c.articleCount, c.continentCount));
        binding.textCountries.setText(c.countriesList != null ? c.countriesList : "");
        binding.textContinents.setText(c.continentsList != null ? c.continentsList : "");
        binding.textSynthesis.setText(
                c.synthesis != null && !c.synthesis.isBlank() ? c.synthesis : "Aucune synthèse disponible.");

        if (c.sources != null && !c.sources.isEmpty()) {
            renderSources(c.sources);
        }
    }

    private void loadSources(long clusterId) {
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                NewsWorldApi api = new NewsWorldApi(requireContext());
                ClusterDto detail = api.getCluster(clusterId);
                runOnUi(() -> {
                    if (detail.sources != null) renderSources(detail.sources);
                });
            } catch (Exception ignored) {}
        });
    }

    private void renderSources(List<ClusterSourceDto> sources) {
        if (binding == null) return;
        binding.sourcesContainer.removeAllViews();

        for (ClusterSourceDto s : sources) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_cluster_source, binding.sourcesContainer, false);

            TextView textFlag    = row.findViewById(R.id.text_flag);
            TextView textTitle   = row.findViewById(R.id.text_source_title);
            TextView textDomain  = row.findViewById(R.id.text_source_domain);

            String flag = FlagUtils.flag(s.countryCode);
            textFlag.setText(flag.isEmpty() ? "[" + s.countryCode + "]" : flag);
            textTitle.setText(s.title != null ? s.title : "");
            textDomain.setText(s.sourceUrl != null ? extractDomain(s.sourceUrl) : "");

            row.setOnClickListener(v ->
                    BottomSheetArticleFragment.from(s)
                            .show(getChildFragmentManager(), "source_sheet"));

            binding.sourcesContainer.addView(row);
        }
    }

    private static String extractDomain(String url) {
        try {
            String host = Uri.parse(url).getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : url;
        } catch (Exception e) {
            return url;
        }
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
