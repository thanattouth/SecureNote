package com.example.securenote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class DialogHelper {

    public interface PinCallback {
        void onPinEntered(String pin);
        void onCancelled();
    }

    public static void showPinDialog(Context ctx, PinCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_pin, null);
        builder.setView(view);
        builder.setCancelable(false);

        final EditText p1 = view.findViewById(R.id.pin1);
        final EditText p2 = view.findViewById(R.id.pin2);
        final EditText p3 = view.findViewById(R.id.pin3);
        final EditText p4 = view.findViewById(R.id.pin4);
        Button btnOk = view.findViewById(R.id.btnOk);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // ensure each field max length = 1 (use InputFilter)
        InputFilter[] oneChar = new InputFilter[] { new InputFilter.LengthFilter(1) };
        p1.setFilters(oneChar); p2.setFilters(oneChar); p3.setFilters(oneChar); p4.setFilters(oneChar);

        final AlertDialog dialog = builder.create();

        TextWatcher tw = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int b, int c) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                // move forward when typed
                if (s.length() == 1) {
                    if (p1.hasFocus()) p2.requestFocus();
                    else if (p2.hasFocus()) p3.requestFocus();
                    else if (p3.hasFocus()) p4.requestFocus();
                }
                // move backward when emptied (user pressed backspace)
                if (s.length() == 0) {
                    if (p4.hasFocus()) p3.requestFocus();
                    else if (p3.hasFocus()) p2.requestFocus();
                    else if (p2.hasFocus()) p1.requestFocus();
                }
            }
        };
        p1.addTextChangedListener(tw);
        p2.addTextChangedListener(tw);
        p3.addTextChangedListener(tw);
        p4.addTextChangedListener(tw);

        // show dialog first then request keyboard
        dialog.show();

        // after show, set keyboard to first field if we have Activity window
        p1.post(() -> {
            p1.requestFocus();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            } else {
                // fallback: try to show keyboard via Activity's window if context is Activity
                if (ctx instanceof Activity) {
                    ((Activity) ctx).getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                }
            }
        });

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (callback != null) callback.onCancelled();
        });

        btnOk.setOnClickListener(v -> {
            String pin = p1.getText().toString()
                    + p2.getText().toString()
                    + p3.getText().toString()
                    + p4.getText().toString();
            if (pin.length() < 4) {
                Toast.makeText(ctx, "กรุณากรอก 4 หลัก", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (callback != null) callback.onPinEntered(pin);
        });
    }
}
