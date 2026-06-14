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

import java.util.List;

import io.newsworld.dashboard.api.ApiClient;
import io.newsworld.dashboard.api.NewsWorldApi;
import io.newsworld.dashboard.databinding.FragmentCountryDetailBinding;
import io.newsworld.dashboard.model.ArticleDto;
import io.newsworld.dashboard.ui.ArticleAdapter;
import io.newsworld.dashboard.util.FlagUtils;

public class CountryDetailFragment extends Fragment {

    private static final String ARG_CODE = "code";
    private static final String ARG_NAME = "name";

    public static CountryDetailFragment newInstance(String code, String name) {
        Bundle args = new Bundle();
        args.putString(ARG_CODE, code);
        args.putString(ARG_NAME, name);
        CountryDetailFragment f = new CountryDetailFragment();
        f.setArguments(args);
        return f;
    }

    private FragmentCountryDetailBinding binding;
    private ArticleAdapter adapter;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCountryDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String code = requireArguments().getString(ARG_CODE, "");
        String name = requireArguments().getString(ARG_NAME, code);

        String flag = FlagUtils.flag(code);
        binding.textCountryName.setText((flag.isEmpty() ? "" : flag + " ") + name + " (" + code + ")");
        binding.btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        adapter = new ArticleAdapter(article ->
                BottomSheetArticleFragment.from(article)
                        .show(getChildFragmentManager(), "article_sheet"));
        binding.recyclerArticles.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerArticles.setAdapter(adapter);
        binding.swipeRefresh.setOnRefreshListener(() -> load(code));

        load(code);
    }

    private void load(String code) {
        if (binding == null) return;
        binding.swipeRefresh.setRefreshing(true);
        NewsWorldApi api = new NewsWorldApi(requireContext());

        ApiClient.get(requireContext()).executor().execute(() -> {
            try {
                List<ArticleDto> articles = api.getCountryArticles(code, 0);
                runOnUi(() -> {
                    adapter.submitList(articles);
                    binding.textCount.setText(articles.size() + " articles");
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
