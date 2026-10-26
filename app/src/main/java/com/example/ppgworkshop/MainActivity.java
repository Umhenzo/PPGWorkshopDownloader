package com.example.ppgworkshop;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText linkInput;
    private Button downloadButton;
    private TextView statusText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(42), dp(24), dp(24));
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(page);

        TextView logo = new TextView(this);
        logo.setText("PPG");
        logo.setTextColor(Color.rgb(79, 70, 229));
        logo.setTextSize(34);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        page.addView(logo, matchWrap());

        TextView title = new TextView(this);
        title.setText("Workshop Downloader");
        title.setTextColor(Color.rgb(25, 25, 35));
        title.setTextSize(23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = matchWrap();
        titleLp.topMargin = dp(4);
        page.addView(title, titleLp);

        TextView subtitle = new TextView(this);
        subtitle.setText("Baixe addons públicos do People Playground sem precisar entrar na Steam.");
        subtitle.setTextColor(Color.rgb(100, 100, 115));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(12);
        page.addView(subtitle, subLp);

        linkInput = new EditText(this);
        linkInput.setSingleLine(true);
        linkInput.setHint("Link da Workshop ou ID do addon");
        linkInput.setTextSize(15);
        linkInput.setPadding(dp(14), dp(13), dp(14), dp(13));
        linkInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.topMargin = dp(28);
        page.addView(linkInput, inputLp);

        downloadButton = new Button(this);
        downloadButton.setText("BAIXAR ADDON");
        downloadButton.setAllCaps(false);
        downloadButton.setTextSize(16);
        downloadButton.setTextColor(Color.WHITE);
        downloadButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(79, 70, 229)));
        LinearLayout.LayoutParams btnLp = matchWrap();
        btnLp.topMargin = dp(14);
        page.addView(downloadButton, btnLp);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progLp = matchWrap();
        progLp.topMargin = dp(18);
        page.addView(progressBar, progLp);

        statusText = new TextView(this);
        statusText.setText("Os arquivos serão salvos em Downloads/ppg.");
        statusText.setTextColor(Color.rgb(90, 90, 105));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(4), dp(18), dp(4), dp(4));
        page.addView(statusText, matchWrap());

        TextView note = new TextView(this);
        note.setText("O app tenta usar o acesso anônimo da Steam. Alguns itens podem exigir autorização ou não estar disponíveis.");
        note.setTextColor(Color.rgb(125, 125, 135));
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteLp = matchWrap();
        noteLp.topMargin = dp(24);
        page.addView(note, noteLp);

        downloadButton.setOnClickListener(v -> startDownload());
        setContentView(scroll);
    }

    private void startDownload() {
        String id = extractWorkshopId(linkInput.getText().toString().trim());
        if (id == null) {
            statusText.setText("Link ou ID inválido. Cole um link da Workshop ou apenas o ID numérico.");
            return;
        }

        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(linkInput.getWindowToken(), 0);
        downloadButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Preparando o download anônimo…");

        try {
            WorkshopDownloaderFacade.start(this, id, new WorkshopDownloaderFacade.Listener() {
                @Override
                public void onStatus(String message) {
                    statusText.setText(message);
                }

                @Override
                public void onCompleted(String message) {
                    statusText.setText(message);
                    finishUi();
                }

                @Override
                public void onError(String message) {
                    statusText.setText("Falha no download: " + message);
                    finishUi();
                }
            });
        } catch (Exception e) {
            statusText.setText(e.getMessage() == null ? "Não foi possível iniciar o download." : e.getMessage());
            finishUi();
        }
    }

    private void finishUi() {
        progressBar.setVisibility(View.GONE);
        downloadButton.setEnabled(true);
    }

    private String extractWorkshopId(String raw) {
        if (raw.matches("\\d{5,20}")) return raw;
        Matcher matcher = Pattern.compile("[?&]id=(\\d{5,20})").matcher(raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
