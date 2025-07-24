package com.davsilvam.bluetooth_control.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.HashMap;
import java.util.Map;

public class ViewPagerAdapter extends FragmentStateAdapter {
    private final Map<Integer, Fragment> fragments = new HashMap<>();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment fragment;
        if (position == 1) {
            fragment = new TerminalLogFragment();
        } else {
            fragment = new ControlsFragment();
        }

        fragments.put(position, fragment);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    public Fragment getFragment(int position) {
        return fragments.get(position);
    }
}