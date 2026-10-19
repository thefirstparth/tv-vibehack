package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** The top edge in the Horizon background's colour (set once per look, never by focus). */
    private final Paint topEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density) {
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(0x1FFFFFFF);
        topEdge.setStrokeWidth(1f);
        topEdge.setColor(0);
    }

    /** The top edge's colour; the caller redraws the grid once. */
    void setTopEdge(int color) {
        topEdge.setShader(null);
        gradient = null;
        topEdge.setColor(color);
    }

    /** The weekend's top edge: these colours left to right (null: back to the single colour). */
    void setTopEdgeGradient(int[] colours) {
        gradient = colours;
        shaderFrom = shaderTo = -1;
        if (colours == null) topEdge.setShader(null);
        else topEdge.setColor(0xFFFFFFFF);
    }

    private int[] gradient;
    private float shaderFrom = -1, shaderTo = -1;

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        rect.inset(-pad, -pad);
        float radius = 26 * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
        float from = rect.left + radius, to = rect.right - radius;
        if (gradient != null && (from != shaderFrom || to != shaderTo)) {
            // made again only when the dock's width changes, not on every frame
            topEdge.setShader(new android.graphics.LinearGradient(from, 0, to, 0, gradient, null, android.graphics.Shader.TileMode.CLAMP));
            shaderFrom = from;
            shaderTo = to;
        }
        if (topEdge.getColor() != 0) c.drawLine(from, rect.top + 0.5f, to, rect.top + 0.5f, topEdge);
    }
}
