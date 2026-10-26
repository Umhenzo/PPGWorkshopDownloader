package com.example.ppgworkshop;

import android.app.Activity;
import android.os.Bundle;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private EditText linkInput;
    private Button downloadButton;
    private TextView statusText;
    private ProgressBar progressBar;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        subtitle.setText("Cole o link de um addon público do People Playground.");
        subtitle.setTextColor(Color.rgb(100, 100, 115));
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(12);
        page.addView(subtitle, subLp);

        linkInput = new EditText(this);
        linkInput.setSingleLine(true);
        linkInput.setHint("Link da Steam Workshop ou ID");
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
        statusText.setText("Os arquivos baixados serão salvos em Downloads/ppg.");
        statusText.setTextColor(Color.rgb(90, 90, 105));
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(4), dp(18), dp(4), dp(4));
        page.addView(statusText, matchWrap());

        TextView note = new TextView(this);
        note.setText("Nota: a Steam só permite baixar anonimamente alguns itens. Se não houver link público direto, este app informará que o addon não está disponível para download direto.");
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
        String raw = linkInput.getText().toString().trim();
        String id = extractWorkshopId(raw);
        if (id == null) {
            statusText.setText("Link ou ID inválido. Cole um link com ?id=123456789 ou apenas o ID.");
            return;
        }

        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(linkInput.getWindowToken(), 0);
        downloadButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Consultando informações do addon na Steam…");

        executor.execute(() -> {
            try {
                JSONObject details = fetchDetails(id);
                int result = details.optInt("result", -1);
                if (result != 1) {
                    throw new Exception("A Steam não retornou um addon público válido (código " + result + ").");
                }
                String title = details.optString("title", "ppg-addon-" + id);
                String fileUrl = details.optString("file_url", "");
                if (fileUrl.isEmpty() || "null".equals(fileUrl)) {
                    throw new Exception("A Steam não forneceu um link público direto para este addon. Este app simples não consegue baixar esse item pelo protocolo interno da Steam.");
                }
                runOnUiThread(() -> statusText.setText("Baixando: " + title));
                String savedName = downloadToDownloads(fileUrl, title, id);
                runOnUiThread(() -> statusText.setText("Download concluído!\nSalvo em Downloads/ppg/" + savedName));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Erro inesperado durante o download." : e.getMessage();
                runOnUiThread(() -> statusText.setText(message));
            } finally {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    downloadButton.setEnabled(true);
                });
            }
        });
    }

    private JSONObject fetchDetails(String id) throws Exception {
        URL url = new URL("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        String body = "itemcount=1&publishedfileids%5B0%5D=" + URLEncoder.encode(id, "UTF-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder text = new StringBuilder();
        try (InputStream input = in) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) text.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
        } finally {
            conn.disconnect();
        }
        JSONObject root = new JSONObject(text.toString());
        JSONArray items = root.getJSONObject("response").optJSONArray("publishedfiledetails");
        if (items == null || items.length() == 0) throw new Exception("Não encontrei esse item na Steam Workshop.");
        return items.getJSONObject(0);
    }

    private String downloadToDownloads(String fileUrl, String title, String id) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "PPGWorkshopDownloader/1.0 Android");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new Exception("O servidor do arquivo respondeu com HTTP " + code + ".");
        }

        String ext = extensionFromUrl(fileUrl);
        String safeTitle = title.replaceAll("[\\\\/:*?\\"<>|]", "_").trim();
        if (safeTitle.isEmpty()) safeTitle = "ppg-addon-" + id;
        if (safeTitle.length() > 90) safeTitle = safeTitle.substring(0, 90);
        String name = safeTitle + ext;

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ppg");
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            conn.disconnect();
            throw new Exception("O Android não conseguiu criar o arquivo em Downloads/ppg.");
        }

        boolean ok = false;
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new Exception("Não foi possível abrir o arquivo para escrita.");
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            out.flush();
            ok = true;
        } finally {
            conn.disconnect();
            if (ok) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            } else {
                getContentResolver().delete(uri, null, null);
            }
        }
        return name;
    }

    private String extensionFromUrl(String value) {
        try {
            String path = new URL(value).getPath();
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && path.length() - dot <= 8) {
                String ext = path.substring(dot);
                if (ext.matches("\\.[A-Za-z0-9]{1,7}")) return ext;
            }
        } catch (Exception ignored) { }
        return ".bin";
    }

    private String extractWorkshopId(String raw) {
        if (raw.matches("\\d{5,20}")) return raw;
        try {
            java.net.URI uri = java.net.URI.create(raw);
            String query = uri.getRawQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] parts = pair.split("=", 2);
                    if (parts.length == 2 && "id".equals(parts[0]) && parts[1].matches("\\d{5,20}")) {
                        return parts[1];
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}