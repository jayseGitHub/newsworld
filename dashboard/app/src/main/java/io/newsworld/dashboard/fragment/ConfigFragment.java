package io.newsworld.dashboard.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentConfigBinding;

public class ConfigFragment extends Fragment {

    private FragmentConfigBinding binding;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentConfigBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ApiClient client = ApiClient.get(requireContext());
        binding.editServerUrl.setText(client.baseUrl());

        binding.btnSave.setOnClickListener(v -> {
            String url = binding.editServerUrl.getText().toString().trim();
            if (url.isEmpty()) { binding.editServerUrl.setError("URL requise"); return; }
            client.setBaseUrl(url);
            Toast.makeText(requireContext(), "Serveur mis à jour", Toast.LENGTH_SHORT).show();
        });

        binding.btnTest.setOnClickListener(v -> testConnection());

        binding.btnReset.setOnClickListener(v -> {
            binding.editServerUrl.setText(ApiClient.DEFAULT_URL);
            client.setBaseUrl(ApiClient.DEFAULT_URL);
            Toast.makeText(requireContext(), "Réinitialisé", Toast.LENGTH_SHORT).show();
        });
    }

    private void testConnection() {
        if (binding == null) return;
        String url = binding.editServerUrl.getText().toString().trim();
        if (url.isEmpty()) { binding.editServerUrl.setError("URL requise"); return; }

        binding.btnTest.setEnabled(false);
        binding.btnTest.setText("Test en cours…");
        binding.textTestResult.setVisibility(View.GONE);

        ApiClient.get(requireContext()).setBaseUrl(url);
        NewsWorldApi api = new NewsWorldApi(requireContext());

        ApiClient.get(requireContext()).executor().execute(() -> {
            String result;
            boolean ok;
            try {
                api.getStats();
                ok = true;
                result = "✓ Connexion réussie\n" + url;
            } catch (Exception e) {
                ok = false;
                result = "✗ Échec\n" + e.getMessage();
            }
            boolean finalOk = ok;
            String finalResult = result;
            runOnUi(() -> {
                binding.btnTest.setEnabled(true);
                binding.btnTest.setText("Tester la connexion");
                binding.textTestResult.setVisibility(View.VISIBLE);
                binding.textTestResult.setText(finalResult);
                binding.textTestResult.setTextColor(requireContext().getColor(
                        finalOk ? io.newsworld.dashboard.R.color.colorSuccess
                                : io.newsworld.dashboard.R.color.colorError));
            });
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
