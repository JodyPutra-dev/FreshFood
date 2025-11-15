package com.jody.freshfood.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.jody.freshfood.databinding.FragmentHomeBinding
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.jody.freshfood.data.repository.ScanRepository
import com.jody.freshfood.ui.home.adapter.ScanHistoryAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import android.content.Intent
import com.jody.freshfood.data.local.entity.ScanResultEntity
import com.jody.freshfood.data.model.ScanResult
import com.jody.freshfood.data.model.toScanResult
import kotlin.random.Random
import com.jody.freshfood.R
import android.widget.ImageButton

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var adapter: ScanHistoryAdapter? = null
    private lateinit var viewModel: HomeViewModel
    private var isGrid = false
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewHistory.adapter = null
        adapter = null
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = ScanRepository(requireContext())
        val factory = HomeViewModel.Factory(repository)
        viewModel = ViewModelProvider(this, factory).get(HomeViewModel::class.java)

        adapter = ScanHistoryAdapter()
        binding.recyclerViewHistory.adapter = adapter
        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())

        // Toggle view button
        binding.toolbar.findViewById<View>(R.id.buttonToggleView).setOnClickListener {
            toggleLayout()
        }

        // Observe data
        viewModel.scanHistory.observe(viewLifecycleOwner) { list ->
            adapter?.submitList(list)
            val empty = list.isNullOrEmpty()
            binding.textEmptyState.visibility = if (empty) View.VISIBLE else View.GONE
            binding.textEmptySubtitle.visibility = if (empty) View.VISIBLE else View.GONE
        }

        // Item click -> ResultActivity (to be created later)
        adapter?.setOnItemClickListener { item ->
            // Convert entity -> UI ScanResult and include generated insights
            val insights = buildString {
                append("HSV Hue: ${Random.nextInt(0, 360)}°\n")
                append("Saturation: ${Random.nextInt(40, 100)}%\n")
                append("Spot Ratio: ${Random.nextInt(5, 30)}%\n")
                append("Edge Density: ${Random.nextInt(10, 50)}%\n")
            }
            val converted: ScanResult = item.toScanResult(insights)
            val intent = Intent(requireContext(), Class.forName("com.jody.freshfood.ResultActivity"))
            intent.putExtra("SCAN_RESULT", converted)
            startActivity(intent)
        }

        // Swipe to delete
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val removed = adapter?.removeItem(pos)
                removed?.let { item ->
                    uiScope.launch {
                        viewModel.deleteScanResult(item)
                    }
                    Snackbar.make(binding.root, getString(R.string.home_item_deleted), Snackbar.LENGTH_LONG)
                        .setAction(R.string.home_undo) {
                            // Re-insert and also into DB
                            adapter?.insertItem(pos, item)
                            uiScope.launch { viewModel.insertScanResult(item) }
                        }.show()
                }
            }
        }

        ItemTouchHelper(touchHelperCallback).attachToRecyclerView(binding.recyclerViewHistory)

    }

    private fun toggleLayout() {
        isGrid = !isGrid
        if (isGrid) {
            binding.recyclerViewHistory.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.toolbar.findViewById<ImageButton>(R.id.buttonToggleView).setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_view_list))
        } else {
            binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
            binding.toolbar.findViewById<ImageButton>(R.id.buttonToggleView).setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_view_grid))
        }
    }
}
