package io.newsworld.dashboard.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import io.newsworld.dashboard.R;
import io.newsworld.dashboard.model.ArticleDto;
import io.newsworld.dashboard.model.ClusterSourceDto;
import io.newsworld.dashboard.util.FlagUtils;

public class BottomSheetArticleFragment extends BottomSheetDialogFragment {

    private static final String A_FLAG    = "flag";
    private static final String A_TITLE   = "title";
    private static final String A_SUMMARY = "summary";
    private static final String A_URL     = "url";
    private static final String A_DATE    = "date";

    public static BottomSheetArticleFragment from(ArticleDto a) {
        String summary = a.translatedSummary != null && !a.translatedSummary.isBlank()
                ? a.translatedSummary : a.originalSummary;
        String date = a.collectedAt != null
                ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(a.collectedAt) : "";
        return build(FlagUtils.flag(a.countryCode) + " " + (a.countryCode != null ? a.countryCode : ""),
                a.displayTitle(), summary, a.sourceUrl, date);
    }

    public static BottomSheetArticleFragment from(ClusterSourceDto s) {
        return build(FlagUtils.flag(s.countryCode) + " " + (s.countryCode != null ? s.countryCode : ""),
                s.title, s.displaySummary(), s.sourceUrl, "");
    }

    private static BottomSheetArticleFragment build(String flag, String title, String summary, String url, String date) {
        Bundle b = new Bundle();
        b.putString(A_FLAG, flag);
        b.putString(A_TITLE, title);
        b.putString(A_SUMMARY, summary);
        b.putString(A_URL, url);
        b.putString(A_DATE, date);
        BottomSheetArticleFragment f = new BottomSheetArticleFragment();
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_article, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle b = requireArguments();

        ((TextView) view.findViewById(R.id.text_flag_code)).setText(b.getString(A_FLAG, ""));
        ((TextView) view.findViewById(R.id.text_title)).setText(b.getString(A_TITLE, ""));

        String summary = b.getString(A_SUMMARY);
        TextView textSummary = view.findViewById(R.id.text_summary);
        textSummary.setText(summary != null && !summary.isBlank() ? summary : "Aucun résumé disponible.");

        String date = b.getString(A_DATE, "");
        view.findViewById(R.id.text_date).setVisibility(date.isBlank() ? View.GONE : View.VISIBLE);
        ((TextView) view.findViewById(R.id.text_date)).setText(date);

        String url = b.getString(A_URL);
        Button btnOpen = view.findViewById(R.id.btn_open);
        if (url != null && !url.isBlank()) {
            btnOpen.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (Exception ignored) {}
                dismiss();
            });
        } else {
            btnOpen.setEnabled(false);
            btnOpen.setAlpha(0.4f);
        }

        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
    }
}
