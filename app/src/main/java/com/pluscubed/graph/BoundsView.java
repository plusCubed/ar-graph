package com.pluscubed.graph;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;

public class BoundsView extends LinearLayout {
    @BindView(R.id.min)
    EditText min;
    @BindView(R.id.max)
    EditText max;
    @BindView(R.id.label)
    TextView labelText;

    public BoundsView(Context context) {
        this(context, null);
    }

    public BoundsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        View.inflate(context, R.layout.view_bounds, this);
        ButterKnife.bind(this);

        TypedArray a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.text});
        String label = "";
        try {
            label = a.getText(0).toString();
        } catch (Exception ignored) {
        } finally {
            a.recycle();

            labelText.setText(String.format("≤ %s ≤", label));
        }
    }

    public String[] getBounds() {
        return new String[]{
                min.getText().toString(),
                max.getText().toString()
        };
    }

    public void setBounds(String[] bounds) {
        min.setText(bounds[0]);
        max.setText(bounds[1]);
    }
}
