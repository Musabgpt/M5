package com.musabgpt.m5;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.text.Layout;
import android.graphics.Rect;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real native source editor: gutter line numbers and local syntax coloring.
 * No Monaco/WebView/server, no external paid APIs, no network requirement.
 */
public final class NativeCodeEditor extends EditText {
    private static final Pattern KEYWORDS=Pattern.compile(
        "\\b(?:def|class|return|if|else|elif|for|while|try|except|finally|import|from|async|await|function|const|let|var|new|switch|case|break|continue|export|default|public|private|static|void|int|boolean|true|false|null|None|True|False|print|throw|catch|interface|extends)\\b");
    private static final Pattern QUOTES=Pattern.compile(
        "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'",Pattern.DOTALL);
    private static final Pattern COMMENTS=Pattern.compile(
        "(?m)//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/");
    private static final Pattern TAGS=Pattern.compile("</?[A-Za-z][^>]*>");
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Paint numbers=new Paint(3);
    private final Paint gutter=new Paint(3);
    private int gutterPx;
    private boolean highlighting=false;
    private String filename="";
    private final Runnable highlightTask=this::applySyntax;

    public NativeCodeEditor(Context context){
        super(context);
        float density=getResources().getDisplayMetrics().density;
        gutterPx=(int)(56*density);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
        setTextColor(Color.rgb(230,238,253));
        setHintTextColor(Color.rgb(145,155,177));
        setBackgroundColor(Color.rgb(19,23,32));
        setGravity(android.view.Gravity.TOP|android.view.Gravity.LEFT);
        setTextDirection(View.TEXT_DIRECTION_LTR);
        setHorizontallyScrolling(false);
        setPadding(gutterPx+12,(int)(14*density),(int)(12*density),(int)(16*density));
        setLineSpacing(2*density,1.12f);
        numbers.setColor(Color.rgb(132,144,166));
        numbers.setTextSize(11*density);
        numbers.setTypeface(Typeface.MONOSPACE);
        numbers.setTextAlign(Paint.Align.RIGHT);
        gutter.setColor(Color.rgb(28,33,45));
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence t,int st,int before,int count){
                handler.removeCallbacks(highlightTask);
                handler.postDelayed(highlightTask,130);
            }
            @Override public void afterTextChanged(Editable e){
                postInvalidate();
            }
        });
    }

    public void setFilename(String name){
        filename=name==null?"":name;
        handler.removeCallbacks(highlightTask);
        handler.post(highlightTask);
    }

    private void applySyntax(){
        if(highlighting)return;
        Editable text=getText();
        if(text==null||text.length()>60000)return;
        highlighting=true;
        try{
            for(ForegroundColorSpan item:text.getSpans(0,text.length(),ForegroundColorSpan.class))
                text.removeSpan(item);
            String source=text.toString();
            colorPattern(text,source,KEYWORDS,Color.rgb(183,157,246),500);
            if(filename.endsWith(".html")||filename.endsWith(".xml"))
                colorPattern(text,source,TAGS,Color.rgb(122,190,254),350);
            colorPattern(text,source,QUOTES,Color.rgb(165,217,151),400);
            colorPattern(text,source,COMMENTS,Color.rgb(144,158,170),400);
        }finally{highlighting=false;}
    }

    private void colorPattern(Editable text,String source,Pattern regex,int color,int limit){
        Matcher match=regex.matcher(source);
        int count=0;
        while(match.find()&&count++<limit){
            if(match.end()>match.start())
                text.setSpan(new ForegroundColorSpan(color),match.start(),match.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        Layout layout=getLayout();
        if(layout==null)return;
        int height=getHeight();
        canvas.save();
        canvas.clipRect(0,0,gutterPx,height);
        canvas.drawRect(0,0,gutterPx,height,gutter);
        int first=Math.max(0,layout.getLineForVertical(Math.max(0,getScrollY())));
        int last=Math.min(layout.getLineCount()-1,layout.getLineForVertical(getScrollY()+height));
        for(int i=first;i<=last;i++){
            int baseline=getTotalPaddingTop()+layout.getLineBaseline(i)-getScrollY();
            if(baseline>=0&&baseline<=height+30)
                canvas.drawText(String.valueOf(i+1),gutterPx-10,baseline,numbers);
        }
        canvas.restore();
    }

    @Override protected void onDetachedFromWindow(){
        handler.removeCallbacks(highlightTask);
        super.onDetachedFromWindow();
    }
}
