package com.rikky.vaultstore;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private static final String SITE_URL = "https://uptodown.com";
    private static final int STORAGE_PERM_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        setupWebView();
        requestStoragePermission();

        if (savedInstanceState == null) {
            webView.loadUrl(SITE_URL);
        }

        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        swipeRefresh.setColorSchemeResources(R.color.accent_amber);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " VaultStoreApp");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                swipeRefresh.setRefreshing(false);
                injectRebranding(view);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(android.view.View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(android.view.View.GONE);
                }
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            downloadFile(url, userAgent, contentDisposition, mimeType);
        });
    }

    private void injectRebranding(WebView view) {
        String js =
            "(function(){" +
            "try{" +
                "function rebrand(){" +
                    "var walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false);" +
                    "var node;" +
                    "while(node=walker.nextNode()){" +
                        "if(node.nodeValue && /uptodown/i.test(node.nodeValue)){" +
                            "node.nodeValue=node.nodeValue.replace(/uptodown/ig,'Vault Store');" +
                        "}" +
                    "}" +
                    "document.querySelectorAll('img[alt*=\"ptodown\" i], img[title*=\"ptodown\" i], svg[aria-label*=\"ptodown\" i]').forEach(function(el){" +
                        "el.alt='Vault Store'; el.title='Vault Store';" +
                        "el.style.visibility='hidden';" +
                    "});" +
                    "document.querySelectorAll('a[href=\"/\"] svg, header svg, nav svg, .logo svg').forEach(function(el){" +
                        "el.style.display='none';" +
                    "});" +
                "}" +
                "function injectCss(){" +
                    "var style=document.getElementById('vault-store-theme');" +
                    "if(!style){" +
                        "style=document.createElement('style');" +
                        "style.id='vault-store-theme';" +
                        "style.innerHTML=" +
                            "'header, nav, .header, .navbar, .top-bar, [class*=\"header\" i], [class*=\"navbar\" i] {background-color:#121212 !important; background:#121212 !important;}' +" +
                            "'body {background-color:#121212 !important;}' +" +
                            "'a, .text-primary, .brand, .logo-text, [class*=\"brand\" i] {color:#FFB300 !important;}' +" +
                            "'.btn-primary, button[class*=\"primary\" i], .badge, .tag, [class*=\"editor\" i] {background-color:#FFB300 !important; border-color:#FFB300 !important; color:#121212 !important;}' +" +
                            "'[style*=\"background-color: rgb(0\"], [style*=\"background:#\"], .banner, [class*=\"banner\" i], [class*=\"bar\" i][class*=\"info\" i] {background-color:#1E1E1E !important; background:#1E1E1E !important;}' +" +
                            "'::selection {background:#FFB300;}';" +
                        "document.head.appendChild(style);" +
                    "}" +
                "}" +
                "rebrand();" +
                "injectCss();" +
                "if(!window.__vaultStoreObserver){" +
                    "window.__vaultStoreObserver=new MutationObserver(function(){rebrand();});" +
                    "window.__vaultStoreObserver.observe(document.body,{childList:true,subtree:true,characterData:true});" +
                "}" +
            "}catch(e){}" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void downloadFile(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
            request.setDescription("Downloading file...");
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                dm.enqueue(request);
                Toast.makeText(this, "Downloading: " + fileName, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERM_CODE);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
