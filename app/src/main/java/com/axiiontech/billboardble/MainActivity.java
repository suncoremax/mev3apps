package com.axiiontech.billboardble;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BleManager.ConnectionListener {

    private BleManager ble;

    // Header
    private LinearLayout connPill;
    private View connDot;
    private TextView connLabel;

    // Tabs
    private Button tabPicture, tabText, tabBillboard;
    private View panelPicture, panelText, panelBillboard;

    // Picture panel
    private ImageView imgPreview;
    private TextView txtPhotoStatus;
    private Button btnPickPhoto, btnSendPicture;
    private SeekBar seekDurationPicture;
    private TextView txtDurationPicture;
    private ProgressBar progressPicture;
    private TextView txtSendStatusPicture;
    private byte[] pendingImageBytes;

    // Text panel
    private EditText editSlideText;
    private LinearLayout colorSwatchRow, scrollSpeedRow;
    private SeekBar seekDurationText;
    private TextView txtDurationText;
    private Button btnSendText;
    private ProgressBar progressText;
    private TextView txtSendStatusText;
    private String selectedColorHex = "FFFF"; // white
    private int selectedScrollSpeed = 0;

    // Billboard panel
    private SeekBar seekBrightness;
    private TextView txtBrightness;
    private Button btnRefreshSlides;
    private RecyclerView recyclerSlides;
    private TextView txtNoSlides;
    private SlideAdapter slideAdapter;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean v : result.values()) if (!v) allGranted = false;
                if (allGranted) {
                    ble.connect();
                } else {
                    Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) handlePickedImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ble = new BleManager(this, this);
        ble.setRawStatusListener(this::onRawStatus);

        bindViews();
        setupTabs();
        setupPicturePanel();
        setupTextPanel();
        setupBillboardPanel();

        connPill.setOnClickListener(v -> {
            if (ble.isConnected()) {
                ble.disconnect();
            } else {
                requestPermissionsAndConnect();
            }
        });
    }

    private void bindViews() {
        connPill = findViewById(R.id.connPill);
        connDot = findViewById(R.id.connDot);
        connLabel = findViewById(R.id.connLabel);

        tabPicture = findViewById(R.id.tabPicture);
        tabText = findViewById(R.id.tabText);
        tabBillboard = findViewById(R.id.tabBillboard);
        panelPicture = findViewById(R.id.panelPicture);
        panelText = findViewById(R.id.panelText);
        panelBillboard = findViewById(R.id.panelBillboard);

        imgPreview = findViewById(R.id.imgPreview);
        txtPhotoStatus = findViewById(R.id.txtPhotoStatus);
        btnPickPhoto = findViewById(R.id.btnPickPhoto);
        btnSendPicture = findViewById(R.id.btnSendPicture);
        seekDurationPicture = findViewById(R.id.seekDurationPicture);
        txtDurationPicture = findViewById(R.id.txtDurationPicture);
        progressPicture = findViewById(R.id.progressPicture);
        txtSendStatusPicture = findViewById(R.id.txtSendStatusPicture);

        editSlideText = findViewById(R.id.editSlideText);
        colorSwatchRow = findViewById(R.id.colorSwatchRow);
        scrollSpeedRow = findViewById(R.id.scrollSpeedRow);
        seekDurationText = findViewById(R.id.seekDurationText);
        txtDurationText = findViewById(R.id.txtDurationText);
        btnSendText = findViewById(R.id.btnSendText);
        progressText = findViewById(R.id.progressText);
        txtSendStatusText = findViewById(R.id.txtSendStatusText);

        seekBrightness = findViewById(R.id.seekBrightness);
        txtBrightness = findViewById(R.id.txtBrightness);
        btnRefreshSlides = findViewById(R.id.btnRefreshSlides);
        recyclerSlides = findViewById(R.id.recyclerSlides);
        txtNoSlides = findViewById(R.id.txtNoSlides);
    }

    // ═══════════════════════════════════════════════════════════
    //  Tabs
    // ═══════════════════════════════════════════════════════════

    private void setupTabs() {
        tabPicture.setOnClickListener(v -> showTab(0));
        tabText.setOnClickListener(v -> showTab(1));
        tabBillboard.setOnClickListener(v -> showTab(2));
    }

    private void showTab(int index) {
        panelPicture.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelText.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelBillboard.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        tabPicture.setBackgroundResource(index == 0 ? R.drawable.bg_tab_active_green : R.drawable.bg_tab_inactive);
        tabPicture.setTextColor(ContextCompat.getColor(this, index == 0 ? R.color.black : R.color.txt));

        tabText.setBackgroundResource(index == 1 ? R.drawable.bg_tab_active_blue : R.drawable.bg_tab_inactive);
        tabText.setTextColor(ContextCompat.getColor(this, index == 1 ? R.color.black : R.color.txt));

        tabBillboard.setBackgroundResource(index == 2 ? R.drawable.bg_tab_active_purple : R.drawable.bg_tab_inactive);
        tabBillboard.setTextColor(ContextCompat.getColor(this, index == 2 ? R.color.white : R.color.txt));

        if (index == 2 && ble.isConnected()) requestSlideList();
    }

    // ═══════════════════════════════════════════════════════════
    //  Picture panel
    // ═══════════════════════════════════════════════════════════

    private void setupPicturePanel() {
        btnPickPhoto.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        seekDurationPicture.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            int seconds = progress + 1;
            txtDurationPicture.setText(bengaliDigits(seconds) + " সে");
        }));

        btnSendPicture.setOnClickListener(v -> {
            if (!ble.isConnected()) {
                toast(R.string.not_connected_error);
                return;
            }
            if (pendingImageBytes == null) {
                toast(R.string.no_photo);
                return;
            }
            int seconds = seekDurationPicture.getProgress() + 1;
            String id = String.valueOf(System.currentTimeMillis());
            progressPicture.setVisibility(View.VISIBLE);
            progressPicture.setProgress(0);
            txtSendStatusPicture.setText(R.string.sending);
            btnSendPicture.setEnabled(false);

            ble.sendImage(id, seconds * 1000, pendingImageBytes, new BleManager.OpCallback() {
                @Override
                public void onProgress(int percent) {
                    runOnUiThread(() -> progressPicture.setProgress(percent));
                }

                @Override
                public void onDone(boolean success, String message) {
                    runOnUiThread(() -> {
                        btnSendPicture.setEnabled(true);
                        progressPicture.setVisibility(View.GONE);
                        txtSendStatusPicture.setText(success ? R.string.send_success : R.string.send_failed);
                    });
                }
            });
        });
    }

    private void handlePickedImage(Uri uri) {
        try {
            Bitmap bitmap = decodeBitmap(uri);
            if (bitmap == null) return;
            Bitmap square = ImageUtils.toSquare128(bitmap);
            imgPreview.setImageBitmap(square);
            pendingImageBytes = ImageUtils.toDitheredRgb565(square);
            txtPhotoStatus.setText(R.string.photo_ready);
        } catch (Exception e) {
            Toast.makeText(this, R.string.send_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeBitmap(Uri uri) throws Exception {
        // Decode at a reasonable bound first so a 12MP photo doesn't get
        // fully loaded into memory before we've even cropped/resized it.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        int longSide = Math.max(bounds.outWidth, bounds.outHeight);
        while (longSide / (sample * 2) >= 512) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in, null, opts);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Text panel
    // ═══════════════════════════════════════════════════════════

    private void setupTextPanel() {
        String[] hexes = {"FFFF", "F800", "07E0", "001F", "FFE0", "07FF", "F81F"};
        int[] colorRes = {R.color.swatch_white, R.color.swatch_red, R.color.swatch_green,
                R.color.swatch_blue, R.color.swatch_yellow, R.color.swatch_cyan, R.color.swatch_magenta};
        List<View> swatchViews = new ArrayList<>();
        for (int i = 0; i < hexes.length; i++) {
            View swatch = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(34, 34);
            lp.setMargins(8, 0, 8, 0);
            swatch.setLayoutParams(lp);
            swatch.setBackgroundResource(R.drawable.bg_swatch);
            swatch.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes[i]));
            String hex = hexes[i];
            swatch.setOnClickListener(v -> {
                selectedColorHex = hex;
                for (View sv : swatchViews) sv.setScaleX(1f);
                for (View sv : swatchViews) sv.setScaleY(1f);
                v.setScaleX(1.3f);
                v.setScaleY(1.3f);
            });
            if (i == 0) { swatch.setScaleX(1.3f); swatch.setScaleY(1.3f); }
            swatchViews.add(swatch);
            colorSwatchRow.addView(swatch);
        }

        String[] speedLabels = {
                getString(R.string.scroll_static), getString(R.string.scroll_slow),
                getString(R.string.scroll_normal), getString(R.string.scroll_fast),
                getString(R.string.scroll_vfast)};
        int[] speedValues = {0, 2, 5, 8, 10};
        List<Button> speedButtons = new ArrayList<>();
        for (int i = 0; i < speedLabels.length; i++) {
            Button b = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(i == 0 ? 0 : 4, 0, 4, 0);
            b.setLayoutParams(lp);
            b.setText(speedLabels[i]);
            b.setTextSize(11);
            b.setAllCaps(false);
            b.setPadding(0, 0, 0, 0);
            b.setBackgroundResource(i == 0 ? R.drawable.bg_tab_active_blue : R.drawable.bg_tab_inactive);
            b.setTextColor(ContextCompat.getColor(this, i == 0 ? R.color.black : R.color.txt));
            int speedValue = speedValues[i];
            b.setOnClickListener(v -> {
                selectedScrollSpeed = speedValue;
                for (int j = 0; j < speedButtons.size(); j++) {
                    Button other = speedButtons.get(j);
                    boolean isThis = other == v;
                    other.setBackgroundResource(isThis ? R.drawable.bg_tab_active_blue : R.drawable.bg_tab_inactive);
                    other.setTextColor(ContextCompat.getColor(this, isThis ? R.color.black : R.color.txt));
                }
            });
            speedButtons.add(b);
            scrollSpeedRow.addView(b);
        }

        seekDurationText.setOnSeekBarChangeListener(new SimpleSeekListener(progress -> {
            int seconds = progress + 1;
            txtDurationText.setText(bengaliDigits(seconds) + " সে");
        }));

        btnSendText.setOnClickListener(v -> {
            if (!ble.isConnected()) {
                toast(R.string.not_connected_error);
                return;
            }
            String text = editSlideText.getText().toString().trim();
            if (text.isEmpty()) {
                toast(R.string.empty_text_error);
                return;
            }
            int seconds = seekDurationText.getProgress() + 1;
            try {
                JSONObject cmd = new JSONObject();
                cmd.put("op", "txt");
                cmd.put("id", String.valueOf(System.currentTimeMillis()));
                cmd.put("txt", text);
                cmd.put("dur", seconds * 1000);
                cmd.put("tc", selectedColorHex);
                cmd.put("ss", selectedScrollSpeed);

                progressText.setVisibility(View.VISIBLE);
                txtSendStatusText.setText(R.string.sending);
                btnSendText.setEnabled(false);

                ble.sendJsonOp(cmd, new BleManager.OpCallback() {
                    @Override
                    public void onDone(boolean success, String message) {
                        runOnUiThread(() -> {
                            btnSendText.setEnabled(true);
                            progressText.setVisibility(View.GONE);
                            txtSendStatusText.setText(success ? R.string.send_success : R.string.send_failed);
                            if (success) editSlideText.setText("");
                        });
                    }
                });
            } catch (JSONException e) {
                toast(R.string.send_failed);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  Billboard / manage panel
    // ═══════════════════════════════════════════════════════════

    private void setupBillboardPanel() {
        slideAdapter = new SlideAdapter(slide -> {
            try {
                JSONObject cmd = new JSONObject();
                cmd.put("op", "del");
                cmd.put("id", slide.id);
                ble.sendJsonOp(cmd, new BleManager.OpCallback() {
                    @Override
                    public void onDone(boolean success, String message) {
                        runOnUiThread(() -> requestSlideList());
                    }
                });
            } catch (JSONException ignored) {}
        });
        recyclerSlides.setLayoutManager(new LinearLayoutManager(this));
        recyclerSlides.setAdapter(slideAdapter);

        btnRefreshSlides.setOnClickListener(v -> requestSlideList());

        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 5;
                txtBrightness.setText(bengaliDigits(value));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!ble.isConnected()) return;
                int value = seekBar.getProgress() + 5;
                try {
                    JSONObject cmd = new JSONObject();
                    cmd.put("op", "brightness");
                    cmd.put("v", value);
                    ble.sendJsonOp(cmd, new BleManager.OpCallback() {
                        @Override
                        public void onDone(boolean success, String message) {}
                    });
                } catch (JSONException ignored) {}
            }
        });
    }

    private void requestSlideList() {
        try {
            JSONObject cmd = new JSONObject();
            cmd.put("op", "list");
            ble.sendJsonOp(cmd, new BleManager.OpCallback() {
                @Override
                public void onDone(boolean success, String message) {
                    if (!success || !message.startsWith("LIST:")) return;
                    runOnUiThread(() -> applySlideList(message.substring("LIST:".length())));
                }
            });
        } catch (JSONException ignored) {}
    }

    private void applySlideList(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("slides");
            List<SlideAdapter.Slide> list = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new SlideAdapter.Slide(
                            o.optString("id"), o.optString("txt"),
                            o.optInt("dur", 5000), o.optBoolean("img", false)));
                }
            }
            slideAdapter.setSlides(list);
            txtNoSlides.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        } catch (JSONException ignored) {}
    }

    private void onRawStatus(String line) {
        // Reserved for future use (e.g. live-updating the list when the
        // web UI adds a slide while the app is open on the Billboard tab).
    }

    // ═══════════════════════════════════════════════════════════
    //  Permissions
    // ═══════════════════════════════════════════════════════════

    private void requestPermissionsAndConnect() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (needed.isEmpty()) {
            ble.connect();
        } else {
            permissionLauncher.launch(needed.toArray(new String[0]));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  BleManager.ConnectionListener
    // ═══════════════════════════════════════════════════════════

    @Override
    public void onScanning() {
        runOnUiThread(() -> setConnState(getString(R.string.scanning), R.color.yellow));
    }

    @Override
    public void onConnecting() {
        runOnUiThread(() -> setConnState(getString(R.string.connecting), R.color.yellow));
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            setConnState(getString(R.string.connected), R.color.green_bright);
            if (panelBillboard.getVisibility() == View.VISIBLE) requestSlideList();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> setConnState(getString(R.string.not_connected), R.color.red));
    }

    @Override
    public void onDeviceNotFound() {
        runOnUiThread(() -> {
            setConnState(getString(R.string.not_connected), R.color.red);
            Toast.makeText(this, R.string.device_not_found, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBleUnavailable(String reason) {
        runOnUiThread(() -> {
            setConnState(getString(R.string.not_connected), R.color.red);
            int msgRes = "bt_off".equals(reason) ? R.string.bt_off : R.string.bt_not_supported;
            Toast.makeText(this, msgRes, Toast.LENGTH_LONG).show();
        });
    }

    private void setConnState(String label, int colorRes) {
        connLabel.setText(label);
        connDot.setBackgroundTintList(ContextCompat.getColorStateList(this, colorRes));
    }

    private void toast(int stringRes) {
        Toast.makeText(this, stringRes, Toast.LENGTH_SHORT).show();
    }

    private static String bengaliDigits(int n) {
        String[] digits = {"০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"};
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(n).toCharArray()) {
            sb.append(digits[c - '0']);
        }
        return sb.toString();
    }

    /** Small helper so SeekBar listeners don't need 3 boilerplate methods every time. */
    private interface OnProgress { void onProgress(int progress); }

    private static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        private final OnProgress callback;
        SimpleSeekListener(OnProgress callback) { this.callback = callback; }
        @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { callback.onProgress(progress); }
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }
}
