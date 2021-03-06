package io.github.mthli.Ninja.View;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.MailTo;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import io.github.mthli.Ninja.Browser.*;
import io.github.mthli.Ninja.Database.Record;
import io.github.mthli.Ninja.Database.RecordAction;
import io.github.mthli.Ninja.R;
import io.github.mthli.Ninja.Unit.BrowserUnit;
import io.github.mthli.Ninja.Unit.IntentUnit;
import io.github.mthli.Ninja.Unit.ViewUnit;

import java.net.URISyntaxException;

public class NinjaWebView extends WebView implements AlbumController {
    private Context context;
    private int flag = BrowserUnit.FLAG_NINJA;
    private int dimen144dp;
    private int dimen108dp;
    private int animTime;
    private String userAgentOriginal;

    private Album album;
    private NinjaWebViewClient webViewClient;
    private NinjaWebChromeClient webChromeClient;
    private NinjaDownloadListener downloadListener;
    private NinjaClickHandler clickHandler;
    private GestureDetector gestureDetector;

    private AdBlock adBlock;
    public AdBlock getAdBlock() {
        return adBlock;
    }

    private boolean foreground;
    public boolean isForeground() {
        return foreground;
    }

    private BrowserController browserController = null;
    public BrowserController getBrowserController() {
        return browserController;
    }
    public void setBrowserController(BrowserController browserController) {
        this.browserController = browserController;
        this.album.setBrowserController(browserController);
    }

    public NinjaWebView(Context context) {
        // Cannot create a dialog, the WebView context is not an Activity.
        super(context);

        this.context = context;
        this.dimen144dp = getResources().getDimensionPixelSize(R.dimen.layout_width_144dp);
        this.dimen108dp = getResources().getDimensionPixelSize(R.dimen.layout_height_108dp);
        this.animTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
        this.foreground = false;

        this.adBlock = new AdBlock(this.context);
        this.album = new Album(this.context, this, this.browserController);
        this.webViewClient = new NinjaWebViewClient(this);
        this.webChromeClient = new NinjaWebChromeClient(this);
        this.downloadListener = new NinjaDownloadListener(this.context);
        this.clickHandler = new NinjaClickHandler(this);
        this.gestureDetector = new GestureDetector(context, new NinjaGestureListener(this));

        initWebView();
        initWebSettings();
        initPreferences();
        initAlbum();
    }

    private synchronized void initWebView() {
        setAlwaysDrawnWithCacheEnabled(true);
        setAnimationCacheEnabled(true);
        setDrawingCacheBackgroundColor(0x00000000);
        setDrawingCacheEnabled(true);
        setWillNotCacheDrawing(false);
        setSaveEnabled(true);

        setBackground(null);
        getRootView().setBackground(null);
        setBackgroundColor(context.getResources().getColor(R.color.white));

        setFocusable(true);
        setFocusableInTouchMode(true);
        setScrollbarFadingEnabled(true);

        setWebViewClient(webViewClient);
        setWebChromeClient(webChromeClient);
        setDownloadListener(downloadListener);
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                gestureDetector.onTouchEvent(motionEvent);
                return false;
            }
        });
    }

    private synchronized void initWebSettings() {
        WebSettings webSettings = getSettings();
        userAgentOriginal = webSettings.getUserAgentString();

        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);

        webSettings.setAppCacheEnabled(true);
        webSettings.setAppCachePath(context.getCacheDir().toString());
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setGeolocationDatabasePath(context.getFilesDir().toString());

        webSettings.setDefaultTextEncodingName(BrowserUnit.URL_ENCODING);

        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webSettings.setLoadsImagesAutomatically(true);
        } else {
            webSettings.setLoadsImagesAutomatically(false);
        }
    }

    public synchronized void initPreferences() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        WebSettings webSettings = getSettings();

        webSettings.setBlockNetworkImage(!sp.getBoolean(context.getString(R.string.sp_images), true));
        webSettings.setJavaScriptEnabled(sp.getBoolean(context.getString(R.string.sp_javascript), true));
        webSettings.setJavaScriptCanOpenWindowsAutomatically(sp.getBoolean(context.getString(R.string.sp_javascript), true));
        webSettings.setGeolocationEnabled(sp.getBoolean(context.getString(R.string.sp_location), true));
        webSettings.setSupportMultipleWindows(sp.getBoolean(context.getString(R.string.sp_multiple_windows), true));
        webSettings.setSaveFormData(sp.getBoolean(context.getString(R.string.sp_passwords), true));

        if (sp.getBoolean(context.getString(R.string.sp_scroll_bar), true)) {
            setHorizontalScrollBarEnabled(true);
            setVerticalScrollBarEnabled(true);
        } else {
            setHorizontalScrollBarEnabled(false);
            setVerticalScrollBarEnabled(false);
        }

        int userAgent = Integer.valueOf(sp.getString(context.getString(R.string.sp_user_agent), "0"));
        if (userAgent != 0) {
            webSettings.setUserAgentString(BrowserUnit.UA_DESKTOP);
        } else {
            webSettings.setUserAgentString(userAgentOriginal);
        }

        webViewClient.enableAdBlock(sp.getBoolean(context.getString(R.string.sp_ad_block), true));

        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setTextZoom(100);
        webSettings.setUseWideViewPort(true);
    }

    private synchronized void initAlbum() {
        album.setAlbumCover(null);
        album.setAlbumTitle(context.getString(R.string.album_untitled));
        album.setBrowserController(browserController);
    }

    @Override
    public synchronized void loadUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            NinjaToast.show(context, R.string.toast_load_error);
            return;
        }
        url = BrowserUnit.queryWrapper(context, url.trim());

        if (url.startsWith(BrowserUnit.URL_SCHEME_MAIL_TO)) {
            Intent intent = IntentUnit.getEmailIntent(MailTo.parse(url));
            context.startActivity(intent);
            reload();
            return;
        } else if (url.startsWith(BrowserUnit.URL_SCHEME_INTENT)) {
            Intent intent;
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                context.startActivity(intent);
            } catch (URISyntaxException u) {}
            return;
        }

        webViewClient.updateWhite(adBlock.isWhite(url));
        super.loadUrl(url);
        if (browserController != null && foreground) {
            browserController.updateBookmarks();
        }
    }

    @Override
    public void reload() {
        webViewClient.updateWhite(adBlock.isWhite(getUrl()));
        super.reload();
    }

    @Override
    public int getFlag() {
        return flag;
    }

    @Override
    public void setFlag(int flag) {
        this.flag = flag;
    }

    @Override
    public View getAlbumView() {
        return album.getAlbumView();
    }

    @Override
    public void setAlbumCover(Bitmap bitmap) {
        album.setAlbumCover(bitmap);
    }

    @Override
    public String getAlbumTitle() {
        return album.getAlbumTitle();
    }

    @Override
    public void setAlbumTitle(String title) {
        album.setAlbumTitle(title);
    }

    @Override
    public synchronized void activate() {
        requestFocus();
        foreground = true;
        album.activate();
    }

    @Override
    public synchronized void deactivate() {
        clearFocus();
        foreground = false;
        album.deactivate();
    }

    public synchronized void update(int progress) {
        if (foreground) {
            browserController.updateProgress(progress);
        }

        setAlbumCover(ViewUnit.capture(this, dimen144dp, dimen108dp, false, Bitmap.Config.RGB_565));
        if (isLoadFinish()) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setAlbumCover(ViewUnit.capture(NinjaWebView.this, dimen144dp, dimen108dp, false, Bitmap.Config.RGB_565));
                }
            }, animTime);

            if (prepareRecord()) {
                RecordAction action = new RecordAction(context);
                action.open(true);
                action.addHistory(new Record(getTitle(), getUrl(), System.currentTimeMillis()));
                action.close();
                browserController.updateAutoComplete();
            }
        }
    }

    public synchronized void update(String title, String url) {
        album.setAlbumTitle(title);
        if (foreground) {
            browserController.updateBookmarks();
            browserController.updateInputBox(url);
        }
    }

    public synchronized void pause() {
        onPause();
        pauseTimers();
    }

    public synchronized void resume() {
        onResume();
        resumeTimers();
    }

    public synchronized void destroy() {
        stopLoading();
        onPause();
        clearHistory();
        setVisibility(GONE);
        removeAllViews();
        destroyDrawingCache();
    }

    public boolean isLoadFinish() {
        return getProgress() >= BrowserUnit.PROGRESS_MAX;
    }

    public void onLongPress() {
        Message click = clickHandler.obtainMessage();
        if (click != null) {
            click.setTarget(clickHandler);
        }
        requestFocusNodeHref(click);
    }

    private boolean prepareRecord() {
        String title = getTitle();
        String url = getUrl();

        if (title == null
                || title.isEmpty()
                || url == null
                || url.isEmpty()
                || url.startsWith(BrowserUnit.URL_SCHEME_ABOUT)
                || url.startsWith(BrowserUnit.URL_SCHEME_MAIL_TO)
                || url.startsWith(BrowserUnit.URL_SCHEME_INTENT)) {
            return false;
        }
        return true;
    }
}
