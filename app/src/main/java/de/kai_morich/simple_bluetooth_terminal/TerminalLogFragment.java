package de.kai_morich.simple_bluetooth_terminal;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayDeque;

public class TerminalLogFragment extends Fragment implements SerialListener {

    private TextView receiveText;
    private SerialService service;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;

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
        View view = inflater.inflate(R.layout.fragment_terminal_log, container, false);
        receiveText = view.findViewById(R.id.receive_text);
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

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
        if (service == null) {
            Toast.makeText(getActivity(), "Serviço não conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!text.isEmpty()) {
            try {
                SpannableStringBuilder spn = new SpannableStringBuilder(text + '\n');
                spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                receiveText.append(spn);
                byte[] data = (text + newline).getBytes();
                service.write(data);
                sendText.setText("");
            } catch (Exception e) {
                onSerialIoError(e);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
    }

    @Override
    public void onStop() {
        if (service != null)
            service.detach();
        super.onStop();
    }

    // Métodos do SerialListener
    @Override
    public void onSerialConnect() {
        status("Conectado");
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("Falha na conexão: " + e.getMessage());
    }

    @Override
    public void onSerialRead(ArrayDeque<byte[]> datas) {
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

    @Override
    public void onSerialRead(byte[] data) { // Adicionado para compatibilidade
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        onSerialRead(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("Conexão perdida: " + e.getMessage());
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }
}