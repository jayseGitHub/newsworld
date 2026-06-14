package io.newsworld.dashboard.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.time.format.DateTimeFormatter;

import io.newsworld.dashboard.R;
import io.newsworld.dashboard.model.LlmUsageDto;

public class LlmUsageAdapter extends ListAdapter<LlmUsageDto, LlmUsageAdapter.VH> {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LlmUsageAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull LlmUsageDto a, @NonNull LlmUsageDto b) {
                return a.id == b.id;
            }
            @Override
            public boolean areContentsTheSame(@NonNull LlmUsageDto a, @NonNull LlmUsageDto b) {
                return a.totalTokens == b.totalTokens && a.durationMs == b.durationMs;
            }
        });
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_llm_usage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView textModel, textPipeline, textTokens, textDuration, textTime;

        VH(@NonNull View v) {
            super(v);
            textModel    = v.findViewById(R.id.text_model);
            textPipeline = v.findViewById(R.id.text_pipeline);
            textTokens   = v.findViewById(R.id.text_tokens);
            textDuration = v.findViewById(R.id.text_duration);
            textTime     = v.findViewById(R.id.text_time);
        }

        void bind(LlmUsageDto u) {
            textModel.setText(u.model);
            textPipeline.setText(u.pipeline != null ? u.pipeline.toUpperCase() : "—");
            textTokens.setText(u.totalTokens + " tokens  (" + u.promptTokens + "p + " + u.completionTokens + "c)");
            textDuration.setText(u.durationMs + " ms");
            textTime.setText(u.calledAt != null ? TIME_FMT.format(u.calledAt) : "");
        }
    }
}
