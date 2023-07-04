package org.pytorch.demo.objectdetection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;


public class ResultView extends View {

    private Paint mPaintRectangle;
    private ArrayList<Result> mResults;

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
    }

    protected void updateTextView(String newText) {
        TextView textViewDetect = getRootView().findViewById(R.id.textViewDetect);
        textViewDetect.setText(newText);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mResults == null || mResults.size() == 0)
            updateTextView("Please try another image <3");

        String textViewContent = "";
        for (Result result : mResults) {
            mPaintRectangle.setStrokeWidth(8);
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            mPaintRectangle.setColor(ContextCompat.getColor(getContext(), R.color.color_blue));
            canvas.drawRect(result.rect, mPaintRectangle);

            String labelDetected = PrePostProcessor.mClasses[result.classIndex];
            labelDetected = labelDetected.replace("_", " ");
            labelDetected = labelDetected.toUpperCase();
            textViewContent += labelDetected + "  " + String.valueOf(Math.round(result.score*10000)/100) + "%\n";
        }

        updateTextView(textViewContent);
    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }
}
