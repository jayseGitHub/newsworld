package io.newsworld.dashboard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import io.newsworld.dashboard.databinding.ActivityMainBinding;
import io.newsworld.dashboard.fragment.ClustersFragment;
import io.newsworld.dashboard.fragment.ConfigFragment;
import io.newsworld.dashboard.fragment.OperationsFragment;
import io.newsworld.dashboard.fragment.UsageFragment;
import io.newsworld.dashboard.fragment.WorldFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        hideSystemBars();

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            navigate(new WorldFragment());
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_world)      { navigate(new WorldFragment());      return true; }
            if (id == R.id.nav_clusters)   { navigate(new ClustersFragment());   return true; }
            if (id == R.id.nav_operations) { navigate(new OperationsFragment()); return true; }
            if (id == R.id.nav_usage)      { navigate(new UsageFragment());      return true; }
            if (id == R.id.nav_config)     { navigate(new ConfigFragment());     return true; }
            return false;
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.hide(WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /** Navigation principale (bottom nav) — sans back stack. */
    private void navigate(Fragment fragment) {
        getSupportFragmentManager().popBackStack(null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /** Navigation vers une vue détail — avec back stack (bouton retour fonctionne). */
    public void navigateDetail(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}
