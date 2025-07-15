package de.kai_morich.simple_bluetooth_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;
import java.util.Arrays;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;

    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

    private ImageButton buttonUp, buttonDown, buttonLeft, buttonRight;

    /*
     * Lifecycle
     */
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
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
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

    /*
     * UI
     */
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
//
//        View buttonUp = view.findViewById(R.id.button_up);
//        View buttonDown = view.findViewById(R.id.button_down);
//        View buttonLeft = view.findViewById(R.id.button_left);
//        View buttonRight = view.findViewById(R.id.button_right);
//        View buttonStop = view.findViewById(R.id.button_stop);
//
//        buttonUp.setOnClickListener(v -> send("F"));    // Frente
//        buttonDown.setOnClickListener(v -> send("B"));  // Trás (Back)
//        buttonLeft.setOnClickListener(v -> send("L"));  // Esquerda (Left)
//        buttonRight.setOnClickListener(v -> send("R")); // Direita (Right)
//        buttonStop.setOnClickListener(v -> send("S"));  // Parar (Stop)
//
//        View buttonUpLeft = view.findViewById(R.id.button_up_left);
//        View buttonUpRight = view.findViewById(R.id.button_up_right);
//        View buttonDownLeft = view.findViewById(R.id.button_down_left);
//        View buttonDownRight = view.findViewById(R.id.button_down_right);
//
//        buttonUpLeft.setOnClickListener(v -> send("I"));      // Diagonal Frente-Esquerda
//        buttonUpRight.setOnClickListener(v -> send("G"));     // Diagonal Frente-Direita
//        buttonDownLeft.setOnClickListener(v -> send("J"));    // Diagonal Trás-Esquerda
//        buttonDownRight.setOnClickListener(v -> send("H"));   // Diagonal Trás-Direita
//
//        return view;
//    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);

        // Mapeamento dos botões
        View buttonUp = view.findViewById(R.id.button_up);
        View buttonDown = view.findViewById(R.id.button_down);
        View buttonLeft = view.findViewById(R.id.button_left);
        View buttonRight = view.findViewById(R.id.button_right);
        View buttonStop = view.findViewById(R.id.button_stop);
        View buttonUpLeft = view.findViewById(R.id.button_up_left);
        View buttonUpRight = view.findViewById(R.id.button_up_right);
        View buttonDownLeft = view.findViewById(R.id.button_down_left);
        View buttonDownRight = view.findViewById(R.id.button_down_right);

        // Lógica de "pressionar e segurar"
        @SuppressLint("ClickableViewAccessibility") View.OnTouchListener touchListener = (v, event) -> {
            String command = "";
            if (v.getId() == R.id.button_up) command = "F";
            else if (v.getId() == R.id.button_down) command = "B";
            else if (v.getId() == R.id.button_left) command = "L";
            else if (v.getId() == R.id.button_right) command = "R";
            else if (v.getId() == R.id.button_stop) command = "S";
            else if (v.getId() == R.id.button_up_left) command = "I";
            else if (v.getId() == R.id.button_up_right) command = "G";
            else if (v.getId() == R.id.button_down_left) command = "J";
            else if (v.getId() == R.id.button_down_right) command = "H";

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Botão pressionado: envia o comando
                send(command);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                // Botão solto: envia o comando de parada
                send("S");
            }
            return true; // Indica que o evento foi consumido
        };

        // Aplica o mesmo listener a todos os botões
        buttonUp.setOnTouchListener(touchListener);
        buttonDown.setOnTouchListener(touchListener);
        buttonLeft.setOnTouchListener(touchListener);
        buttonRight.setOnTouchListener(touchListener);
        buttonStop.setOnTouchListener(touchListener);
        buttonUpLeft.setOnTouchListener(touchListener);
        buttonUpRight.setOnTouchListener(touchListener);
        buttonDownLeft.setOnTouchListener(touchListener);
        buttonDownRight.setOnTouchListener(touchListener);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

//    public void onPrepareOptionsMenu(@NonNull Menu menu) {
//        menu.findItem(R.id.hex).setChecked(hexEnabled);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            menu.findItem(R.id.backgroundNotification).setChecked(service != null && service.areNotificationsEnabled());
//        } else {
//            menu.findItem(R.id.backgroundNotification).setChecked(true);
//            menu.findItem(R.id.backgroundNotification).setEnabled(false);
//        }
//    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            // status("connecting..."); // Removido pois não há onde exibir
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

    private void send(String str) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "Não conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + newline).getBytes();
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        // Removido - não há mais um campo de texto para receber dados
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * starting with Android 14, notifications are not shown in notification bar by default when App is in background
     */

    private void showNotificationSettings() {
        Intent intent = new Intent();
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
        intent.putExtra("android.provider.extra.APP_PACKAGE", getActivity().getPackageName());
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (Arrays.equals(permissions, new String[]{Manifest.permission.POST_NOTIFICATIONS}) &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service.areNotificationsEnabled())
            showNotificationSettings();
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        // status("connected"); // Removido
        Toast.makeText(getActivity(), "Conectado", Toast.LENGTH_SHORT).show();
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        // status("connection failed: " + e.getMessage()); // Removido
        Toast.makeText(getActivity(), "Falha na conexão: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        // status("connection lost: " + e.getMessage()); // Removido
        Toast.makeText(getActivity(), "Conexão perdida: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        disconnect();
    }
}
