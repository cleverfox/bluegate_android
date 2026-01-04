package org.cleverfox.bluegate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import org.cleverfox.bluegate.databinding.FragmentKeysBinding

class KeysFragment : Fragment() {

    private var _binding: FragmentKeysBinding? = null
    private val binding get() = _binding!!

    private lateinit var keyAdapter: KeyAdapter
    private lateinit var adminActivity: AdminDashboardActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeysBinding.inflate(inflater, container, false)
        adminActivity = activity as AdminDashboardActivity
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
    }

    fun updateKeys(keys: List<KeyInfo>) {
        keyAdapter.submitList(keys)
    }

    private fun setupRecyclerView() {
        keyAdapter = KeyAdapter { keyInfo ->
            adminActivity.selectKeyForManagement(keyInfo.keyHex)
        }
        binding.keysRecyclerView.adapter = keyAdapter
        binding.keysRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
