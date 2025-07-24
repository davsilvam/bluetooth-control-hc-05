package de.kai_morich.simple_bluetooth_terminal.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.service.SerialService;
import de.kai_morich.simple_bluetooth_terminal.bluetooth.SerialSocket;

public class TerminalActivity extends AppCompatActivity implements ServiceConnection {
    private SerialService service;
    private String deviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getIntent().hasExtra("device")) {
            deviceAddress = getIntent().getStringExtra("device");
            Intent intent = new Intent(this, SerialService.class);
            bindService(intent, this, Context.BIND_AUTO_CREATE);
            startService(intent);
        }

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    if (position == 0) {
                        tab.setText("Controle");
                    } else {
                        tab.setText("Terminal");
                    }
                }
        ).attach();
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            unbindService(this);
        }
        super.onDestroy();
    }

    public SerialService getSerialService() {
        return service;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();

        try {
            SerialSocket socket = new SerialSocket(getApplicationContext(), service.getDevice(deviceAddress));
            service.connect(socket);
        } catch (Exception ignored) {

        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }
}