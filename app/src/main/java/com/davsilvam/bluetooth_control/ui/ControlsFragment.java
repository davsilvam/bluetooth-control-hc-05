package com.davsilvam.bluetooth_control.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.davsilvam.bluetooth_control.R;
import com.davsilvam.bluetooth_control.service.SerialListener;
import com.davsilvam.bluetooth_control.service.SerialService;
import com.davsilvam.bluetooth_control.utils.TextUtil;

import java.util.ArrayDeque;

public class ControlsFragment extends Fragment implements SerialListener, ServiceConnectionListener {
    private SerialService service;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActivity() instanceof TerminalActivity) {
            this.service = ((TerminalActivity) getActivity()).getService();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() instanceof TerminalActivity) {
            this.service = ((TerminalActivity) getActivity()).getService();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_controls, container, false);
        setupButtonListeners(view);
        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setupButtonListeners(View view) {
        view.findViewById(R.id.button_up).setOnTouchListener(
                (v, event) -> handleTouch(event, "F")
        );

        view.findViewById(R.id.button_down).setOnTouchListener(
                (v, event) -> handleTouch(event, "B")
        );

        view.findViewById(R.id.button_left).setOnTouchListener(
                (v, event) -> handleTouch(event, "L")
        );

        view.findViewById(R.id.button_right).setOnTouchListener(
                (v, event) -> handleTouch(event, "R")
        );

        view.findViewById(R.id.button_up_left).setOnTouchListener(
                (v, event) -> handleTouch(event, "Q")
        );

        view.findViewById(R.id.button_up_right).setOnTouchListener(
                (v, event) -> handleTouch(event, "P")
        );

        view.findViewById(R.id.button_down_left).setOnTouchListener(
                (v, event) -> handleTouch(event, "G")
        );

        view.findViewById(R.id.button_down_right).setOnTouchListener(
                (v, event) -> handleTouch(event, "H")
        );


        view.findViewById(R.id.button_stop).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                send("S");
            }

            return true;
        });
    }

    private boolean handleTouch(MotionEvent event, String onPressAction) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                send(onPressAction);
                return true;
            case MotionEvent.ACTION_UP:
                send("S");
                return true;
        }
        return false;
    }

    private void send(String str) {
        if (service == null || !service.isConnected()) {
            Toast.makeText(getActivity(), "Não conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String newline = TextUtil.newline_crlf;
            byte[] data = (str + newline).getBytes();
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    @Override
    public void onSerialConnect() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Conectado com sucesso", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onSerialRead(byte[] data) {
        // Não é necessário processar aqui
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
        // Não é necessário processar aqui
    }

    @Override
    public void onSerialIoError(Exception e) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Conexão perdida: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getActivity(), "Falha na conexão: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }
}