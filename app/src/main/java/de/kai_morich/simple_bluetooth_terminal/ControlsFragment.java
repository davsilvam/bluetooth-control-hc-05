package de.kai_morich.simple_bluetooth_terminal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ControlsFragment extends Fragment {
    private SerialService service;
    private String newline = TextUtil.newline_crlf;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Obtém o serviço da Activity principal
        if (getActivity() instanceof TerminalActivity) {
            service = ((TerminalActivity) getActivity()).getSerialService();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_controls, container, false);
        setupDpadTouchListener(view);
        return view;
    }

    private void setupDpadTouchListener(View view) {
        View.OnTouchListener touchListener = (v, event) -> {
            String command = "";
            int id = v.getId();
            if (id == R.id.button_up) command = "F";
            else if (id == R.id.button_down) command = "B";
            else if (id == R.id.button_left) command = "L";
            else if (id == R.id.button_right) command = "R";
            else if (id == R.id.button_stop) command = "S";
            else if (id == R.id.button_up_left) command = "I";
            else if (id == R.id.button_up_right) command = "G";
            else if (id == R.id.button_down_left) command = "J";
            else if (id == R.id.button_down_right) command = "H";

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                send(command);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (v.getId() != R.id.button_stop) {
                    send("S");
                }
            }
            return true;
        };
        // Aplica o listener a todos os botões
        view.findViewById(R.id.button_up).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_down).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_left).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_right).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_stop).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_up_left).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_up_right).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_down_left).setOnTouchListener(touchListener);
        view.findViewById(R.id.button_down_right).setOnTouchListener(touchListener);
    }

    private void send(String str) {
        if (service == null) {
            Toast.makeText(getActivity(), "Serviço não conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            byte[] data = (str + newline).getBytes();
            service.write(data);
        } catch (Exception e) {
            Toast.makeText(getActivity(), "Falha no envio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}