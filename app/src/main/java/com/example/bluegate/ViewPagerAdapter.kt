package com.example.bluegate

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ManagementFragment()
            1 -> ParametersFragment()
            2 -> ManualControlFragment()
            else -> throw IllegalStateException("Invalid position")
        }
    }
}
