package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;
    private TextView receiveText;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    //region Ciclo de Vida (Lifecycle)
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class));
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
    //endregion

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        receiveText = view.findViewById(R.id.receive_text);
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        // Configura os listeners para todos os botões do D-Pad
        setupButtonListeners(view);

        // Configura o terminal de texto
        EditText sendText = view.findViewById(R.id.send_text);
        ImageButton sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> sendFromText(sendText));
        sendText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromText(sendText);
                return true;
            }
            return false;
        });

        return view;
    }

    private void sendFromText(EditText sendText) {
        String text = sendText.getText().toString();
        if (!text.isEmpty()) {
            send(text, false);
            sendText.setText("");
        }
    }

    /**
     * Nova função para organizar os listeners, como no seu diff.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupButtonListeners(View view) {
        // Mapeamento dos botões
        view.findViewById(R.id.button_up).setOnTouchListener((v, event) ->
                handleTouch(event, "F", "S")
        );
        view.findViewById(R.id.button_down).setOnTouchListener((v, event) ->
                handleTouch(event, "B", "S")
        );
        view.findViewById(R.id.button_left).setOnTouchListener((v, event) ->
                handleTouch(event, "L", "S")
        );
        view.findViewById(R.id.button_right).setOnTouchListener((v, event) ->
                handleTouch(event, "R", "S")
        );
        view.findViewById(R.id.button_up_left).setOnTouchListener((v, event) ->
                handleTouch(event, "I", "S")
        );
        view.findViewById(R.id.button_up_right).setOnTouchListener((v, event) ->
                handleTouch(event, "G", "S")
        );
        view.findViewById(R.id.button_down_left).setOnTouchListener((v, event) ->
                handleTouch(event, "J", "S")
        );
        view.findViewById(R.id.button_down_right).setOnTouchListener((v, event) ->
                handleTouch(event, "H", "S")
        );

        // Listener especial para o botão STOP
        view.findViewById(R.id.button_stop).setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                send("S", true);
            }
            // Não faz nada no ACTION_UP para o botão de parada
            return true;
        });
    }

    /**
     * Nova função para lidar com o toque, como no seu diff.
     */
    private boolean handleTouch(MotionEvent event, String onPressAction, String onReleaseAction) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                send(onPressAction, true);
                return true;
            case MotionEvent.ACTION_UP:
                send(onReleaseAction, true);
                return true;
        }
        return false;
    }

    //region Serial Communication (send, receive, status, etc.)
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("Conectando a " + device.getName() + "...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
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
            if (newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
                if (pendingNewline && msg.charAt(0) == '\n') {
                    if (receiveText.length() >= 2) {
                        receiveText.getEditableText().delete(receiveText.length() - 2, receiveText.length());
                    }
                }
                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
            }
            spn.append(TextUtil.toCaretString(msg, newline.length() != 0));
        }
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorRecieveText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }
    //endregion

    //region SerialListener
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
    //endregion
}