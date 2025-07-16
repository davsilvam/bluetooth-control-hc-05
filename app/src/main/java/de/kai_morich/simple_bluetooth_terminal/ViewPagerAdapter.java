package de.kai_morich.simple_bluetooth_terminal;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {
    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new ControlsFragment();
            case 1:
                return new TerminalLogFragment();
            default:
                return new ControlsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Temos 2 abas
    }
}