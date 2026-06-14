package io.newsworld.dashboard.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import io.newsworld.dashboard.R;
import io.newsworld.dashboard.model.ArticleDto;
import io.newsworld.dashboard.util.FlagUtils;

public class ArticleAdapter extends ListAdapter<ArticleDto, ArticleAdapter.VH> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final Consumer<ArticleDto> onClick;

    public ArticleAdapter(Consumer<ArticleDto> onClick) {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull ArticleDto a, @NonNull ArticleDto b) { return a.id == b.id; }
            @Override public boolean areContentsTheSame(@NonNull ArticleDto a, @NonNull ArticleDto b) { return a.id == b.id && a.translated == b.translated; }
        });
        this.onClick = onClick;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_article, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), onClick);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView textTitle, textSource, textDate, badgeTranslated, badgeLang;

        VH(@NonNull View v) {
            super(v);
            textTitle       = v.findViewById(R.id.text_title);
            textSource      = v.findViewById(R.id.text_source);
            textDate        = v.findViewById(R.id.text_date);
            badgeTranslated = v.findViewById(R.id.badge_translated);
            badgeLang       = v.findViewById(R.id.badge_lang);
        }

        void bind(ArticleDto a, Consumer<ArticleDto> onClick) {
            textTitle.setText(a.displayTitle());
            textSource.setText(a.sourceUrl != null ? extractDomain(a.sourceUrl) : "");
            textDate.setText(a.collectedAt != null ? FMT.format(a.collectedAt) : "");
            badgeTranslated.setVisibility(a.translated ? View.VISIBLE : View.GONE);

            String flag = FlagUtils.flag(a.countryCode);
            String lang = a.originalLanguage != null ? a.originalLanguage.toUpperCase() : "";
            badgeLang.setText(flag.isEmpty() ? lang : flag + " " + lang);

            itemView.setOnClickListener(v -> { if (onClick != null) onClick.accept(a); });
        }

        private static String extractDomain(String url) {
            try {
                String host = Uri.parse(url).getHost();
                return host != null ? host.replaceFirst("^www\\.", "") : url;
            } catch (Exception e) {
                return url;
            }
        }
    }
}
