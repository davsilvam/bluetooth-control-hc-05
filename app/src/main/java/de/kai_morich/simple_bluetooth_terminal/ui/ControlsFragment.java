package de.kai_morich.simple_bluetooth_terminal.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.bluetooth.SerialSocket;
import de.kai_morich.simple_bluetooth_terminal.service.SerialListener;
import de.kai_morich.simple_bluetooth_terminal.service.SerialService;
import de.kai_morich.simple_bluetooth_terminal.utils.TextUtil;

public class ControlsFragment extends Fragment implements ServiceConnection, SerialListener {
    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private TextView receiveText;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean pendingNewline = false;
    private final String newline = TextUtil.newline_crlf;

    private SerialService service;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() instanceof TerminalActivity) {
            service = ((TerminalActivity) getActivity()).getSerialService();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_controls, container, false);
//        setupDpadTouchListener(view);
        setupButtonListeners(view);
        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setupButtonListeners(View view) {
        view.findViewById(R.id.button_up).setOnTouchListener((v, event) ->
                handleTouch(event, "F")
        );

        view.findViewById(R.id.button_down).setOnTouchListener((v, event) ->
                handleTouch(event, "B")
        );

        view.findViewById(R.id.button_left).setOnTouchListener((v, event) ->
                handleTouch(event, "L")
        );

        view.findViewById(R.id.button_right).setOnTouchListener((v, event) ->
                handleTouch(event, "R")
        );

        view.findViewById(R.id.button_up_left).setOnTouchListener((v, event) ->
                handleTouch(event, "I")
        );

        view.findViewById(R.id.button_up_right).setOnTouchListener((v, event) ->
                handleTouch(event, "G")
        );

        view.findViewById(R.id.button_down_left).setOnTouchListener((v, event) ->
                handleTouch(event, "J")
        );

        view.findViewById(R.id.button_down_right).setOnTouchListener((v, event) ->
                handleTouch(event, "H")
        );

        view.findViewById(R.id.button_stop).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                send("S", true);
            }

            return true;
        });
    }

    private boolean handleTouch(MotionEvent event, String onPressAction) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                send(onPressAction, true);
                return true;
            case MotionEvent.ACTION_UP:
                send("S", true);
                return true;
        }
        return false;
    }

//    private void setupDpadTouchListener(View view) {
//        @SuppressLint("ClickableViewAccessibility")
//        View.OnTouchListener touchListener = (v, event) -> {
//            String command = "";
//            int id = v.getId();
//
//            if (id == R.id.button_up) {
//                command = "F";
//            } else if (id == R.id.button_down) {
//                command = "B";
//            } else if (id == R.id.button_left) {
//                command = "L";
//            } else if (id == R.id.button_right) {
//                command = "R";
//            } else if (id == R.id.button_stop) {
//                command = "S";
//            } else if (id == R.id.button_up_left) {
//                command = "I";
//            } else if (id == R.id.button_up_right) {
//                command = "G";
//            } else if (id == R.id.button_down_left) {
//                command = "J";
//            } else if (id == R.id.button_down_right) {
//                command = "H";
//            }
//
//            if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                send(command);
//            } else if (event.getAction() == MotionEvent.ACTION_UP) {
//                if (v.getId() != R.id.button_stop) {
//                    send("S");
//                }
//            }
//
//            return true;
//        };
//
//        view.findViewById(R.id.button_up).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_down).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_left).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_right).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_stop).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_up_left).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_up_right).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_down_left).setOnTouchListener(touchListener);
//        view.findViewById(R.id.button_down_right).setOnTouchListener(touchListener);
//    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("Conectando a " + device.getName() + "...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(requireActivity().getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    private void send(String str, boolean fromDpad) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "Não conectado", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (!fromDpad) {
                SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
                spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
            }

            String newline = TextUtil.newline_crlf;
            byte[] data = (str + newline).getBytes();
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            String msg = new String(data);

            if (!msg.isEmpty()) {
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);

                if (pendingNewline && msg.charAt(0) == '\n') {
                    if (receiveText.length() >= 2) {
                        receiveText.getEditableText().delete(receiveText.length() - 2, receiveText.length());
                    }
                }

                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }

            spn.append(TextUtil.toCaretString(msg, true));
        }

        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    @Override
    public void onSerialConnect() {
        status("Conectado");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("Falha na conexão: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("Conexão perdida: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            requireActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
}