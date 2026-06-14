package io.newsworld.dashboard.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Map;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentOperationsBinding;
import io.newsworld.dashboard.model.PipelineStatusDto;

public class OperationsFragment extends Fragment {

    private FragmentOperationsBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentOperationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnEnrich.setOnClickListener(v -> runPipeline("enrich"));
        binding.btnTranslate.setOnClickListener(v -> runPipeline("translate"));
        binding.btnAnalyze.setOnClickListener(v -> runPipeline("analyze"));
        binding.btnRefreshStatus.setOnClickListener(v -> loadStatus());

        loadStatus();
    }

    private void runPipeline(String name) {
        setPipelineButtonsEnabled(false);
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                NewsWorldApi api = new NewsWorldApi(requireContext());
                Map<String, String> result = api.runPipeline(name);
                runOnUi(() -> {
                    setPipelineButtonsEnabled(true);
                    String status = result.getOrDefault("status", "?");
                    Toast.makeText(requireContext(), name + ": " + status, Toast.LENGTH_SHORT).show();
                    loadStatus();
                });
            } catch (Exception e) {
                runOnUi(() -> {
                    setPipelineButtonsEnabled(true);
                    Toast.makeText(requireContext(), "Erreur: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setPipelineButtonsEnabled(boolean enabled) {
        if (binding == null) return;
        binding.btnEnrich.setEnabled(enabled);
        binding.btnTranslate.setEnabled(enabled);
        binding.btnAnalyze.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.5f;
        binding.btnEnrich.setAlpha(alpha);
        binding.btnTranslate.setAlpha(alpha);
        binding.btnAnalyze.setAlpha(alpha);
    }

    private void loadStatus() {
        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                NewsWorldApi api = new NewsWorldApi(requireContext());
                Map<String, PipelineStatusDto> statuses = api.getPipelineStatus();
                runOnUi(() -> renderStatus(statuses));
            } catch (Exception e) {
                runOnUi(() -> { if (binding != null) binding.textStatus.setText("Erreur: " + e.getMessage()); });
            }
        });
    }

    private void renderStatus(Map<String, PipelineStatusDto> statuses) {
        if (binding == null) return;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, PipelineStatusDto> entry : statuses.entrySet()) {
            PipelineStatusDto s = entry.getValue();
            sb.append(entry.getKey().toUpperCase()).append(": ").append(s.status);
            if (s.message != null)    sb.append(" — ").append(s.message);
            if (s.startedAt != null)  sb.append("\n  Démarré: ").append(s.startedAt);
            if (s.finishedAt != null) sb.append("\n  Terminé: ").append(s.finishedAt);
            sb.append("\n\n");
        }
        binding.textStatus.setText(sb.isEmpty() ? "Aucun pipeline exécuté" : sb.toString().trim());
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
