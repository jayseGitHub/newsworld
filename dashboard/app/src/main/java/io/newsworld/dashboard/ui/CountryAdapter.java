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
import io.newsworld.dashboard.model.CountryDto;
import io.newsworld.dashboard.util.FlagUtils;

public class CountryAdapter extends ListAdapter<CountryDto, CountryAdapter.VH> {

    private final Consumer<CountryDto> onClick;

    public CountryAdapter(Consumer<CountryDto> onClick) {
        super(new DiffUtil.ItemCallback<>() {
            @Override public boolean areItemsTheSame(@NonNull CountryDto a, @NonNull CountryDto b) { return a.code != null && a.code.equals(b.code); }
            @Override public boolean areContentsTheSame(@NonNull CountryDto a, @NonNull CountryDto b) { return a.articleCount == b.articleCount && a.name != null && a.name.equals(b.name); }
        });
        this.onClick = onClick;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_country, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), onClick);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView textCode, textName, textArticles, textType;

        VH(@NonNull View v) {
            super(v);
            textCode     = v.findViewById(R.id.text_code);
            textName     = v.findViewById(R.id.text_name);
            textArticles = v.findViewById(R.id.text_articles);
            textType     = v.findViewById(R.id.text_type);
        }

        void bind(CountryDto c, Consumer<CountryDto> onClick) {
            String flag = FlagUtils.flag(c.code);
            textCode.setText(flag.isEmpty() ? c.code : flag + " " + c.code);
            textName.setText(c.name);
            textArticles.setText(c.articleCount + " art.");
            textType.setText(c.collectionType);
            itemView.setOnClickListener(v -> { if (onClick != null) onClick.accept(c); });
        }
    }
}
