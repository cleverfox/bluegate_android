package com.example.bluegate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.bluegate.databinding.FragmentManualControlBinding

class ManualControlFragment : Fragment() {

    private var _binding: FragmentManualControlBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualControlBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adminActivity = activity as AdminDashboardActivity

        binding.manualOpenButton.setOnClickListener {
            adminActivity.authenticate(2) { adminActivity.finish() } // 2 for Open
        }

        binding.manualCloseButton.setOnClickListener {
            adminActivity.authenticate(3) { adminActivity.finish() } // 3 for Close
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
