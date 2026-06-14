package io.newsworld.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.IOException;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.databinding.ActivitySplashBinding;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private Call pendingCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.hide(WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        binding.editUrl.setText(ApiClient.get(this).baseUrl());
        binding.btnRetry.setOnClickListener(v -> {
            String url = binding.editUrl.getText().toString().trim();
            if (!url.isEmpty()) ApiClient.get(this).setBaseUrl(url);
            ping();
        });

        ping();
    }

    private void ping() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.configPanel.setVisibility(View.GONE);
        binding.textStatus.setText("Connexion au serveur…");

        String url = ApiClient.get(this).baseUrl() + "/stats";
        Request req = new Request.Builder().url(url).get().build();
        pendingCall = ApiClient.get(this).http().newCall(req);

        pendingCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) runOnUiThread(() -> showError("Serveur inaccessible : " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        startActivity(new Intent(SplashActivity.this, MainActivity.class));
                        finish();
                    });
                } else {
                    runOnUiThread(() -> showError("Erreur serveur : " + response.code()));
                }
            }
        });
    }

    private void showError(String message) {
        if (isFinishing()) return;
        binding.progress.setVisibility(View.GONE);
        binding.textStatus.setText(message);
        binding.editUrl.setText(ApiClient.get(this).baseUrl());
        binding.configPanel.setVisibility(View.VISIBLE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingCall != null) pendingCall.cancel();
    }
}
