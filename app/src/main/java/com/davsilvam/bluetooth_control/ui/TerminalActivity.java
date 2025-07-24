package com.davsilvam.bluetooth_control.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.davsilvam.bluetooth_control.R;
import com.davsilvam.bluetooth_control.service.SerialListener;
import com.davsilvam.bluetooth_control.service.SerialService;
import com.davsilvam.bluetooth_control.bluetooth.SerialSocket;

public class TerminalActivity extends AppCompatActivity implements ServiceConnection {
    private SerialService service;
    private String deviceAddress;
    private ViewPagerAdapter viewPagerAdapter;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getIntent().hasExtra("device")) {
            deviceAddress = getIntent().getStringExtra("device");
        }

        viewPager = findViewById(R.id.view_pager);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Controle" : "Terminal");
        }).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (service != null) {
                    // Desanexa o listener antigo e anexa o novo (o fragment da aba atual)
                    service.detach();
                    Fragment currentFragment = viewPagerAdapter.getFragment(position);
                    if (currentFragment instanceof SerialListener) {
                        service.attach((SerialListener) currentFragment);
                    }
                }
            }
        });

        startService(new Intent(this, SerialService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        try {
            unbindService(this);
        } catch (Exception ignored) {
        }
        super.onStop();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();

        if (deviceAddress != null && !service.isConnected()) {
            try {
                SerialSocket socket = new SerialSocket(getApplicationContext(), service.getDevice(deviceAddress));
                service.connect(socket);
            } catch (Exception e) {
                Fragment currentFragment = viewPagerAdapter.getFragment(viewPager.getCurrentItem());
                if (currentFragment instanceof SerialListener) {
                    ((SerialListener) currentFragment).onSerialConnectError(e);
                }
            }
        }

        Fragment currentFragment = viewPagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment instanceof SerialListener) {
            service.attach((SerialListener) currentFragment);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    public SerialService getService() {
        return service;
    }
}