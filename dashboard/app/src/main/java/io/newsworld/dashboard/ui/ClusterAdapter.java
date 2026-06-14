package io.newsworld.dashboard.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.function.Consumer;

import io.newsworld.dashboard.R;
import io.newsworld.dashboard.model.ClusterDto;
import io.newsworld.dashboard.util.FlagUtils;

public class ClusterAdapter extends ListAdapter<ClusterDto, ClusterAdapter.VH> {

    private final Consumer<ClusterDto> onClick;

    public ClusterAdapter(Consumer<ClusterDto> onClick) {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull ClusterDto a, @NonNull ClusterDto b) { return a.id == b.id; }
            @Override public boolean areContentsTheSame(@NonNull ClusterDto a, @NonNull ClusterDto b) { return a.relevanceScore == b.relevanceScore && a.articleCount == b.articleCount; }
        });
        this.onClick = onClick;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cluster, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), onClick);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView textTopic, textScore, textCountries;

        VH(@NonNull View v) {
            super(v);
            textTopic     = v.findViewById(R.id.text_topic);
            textScore     = v.findViewById(R.id.text_score);
            textCountries = v.findViewById(R.id.text_countries);
        }

        void bind(ClusterDto c, Consumer<ClusterDto> onClick) {
            textTopic.setText(c.topic);
            textScore.setText(String.format("Score: %.0f  •  %d pays  •  %d articles",
                    c.relevanceScore, c.countryCount, c.articleCount));
            String flags = FlagUtils.flags(c.countriesList);
            textCountries.setText(flags.isEmpty() ? c.countriesList : flags);
            itemView.setOnClickListener(v -> { if (onClick != null) onClick.accept(c); });
        }
    }
}
