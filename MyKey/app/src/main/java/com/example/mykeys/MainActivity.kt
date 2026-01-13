package com.example.mykeys

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EntryAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateLayout: LinearLayout
    private var allItems: MutableList<PasswordEntry> = mutableListOf()
    private var pagedItems: MutableList<PasswordEntry> = mutableListOf()
    private var currentPage = 0
    private var isLoading = false
    private var isLastPage = false
    private var isSearching = false
    private var searchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        
        // RecyclerView optimization
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        
        adapter = EntryAdapter(
            onEdit = { id ->
                val intent = Intent(this, EditEntryActivity::class.java)
                intent.putExtra("id", id)
                startActivity(intent)
            },
            onDelete = { entry ->
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete))
                    .setMessage(getString(R.string.delete_confirm))
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        lifecycleScope.launch {
                            PasswordRepository.delete(this@MainActivity, entry.id)
                            loadData()
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            },
            onLoadMore = {
                loadMoreItems()
            }
        )
        recyclerView.adapter = adapter
        
        // Setup Swipe Refresh
        swipeRefreshLayout.setOnRefreshListener {
            // Reset and reload data when pulled to refresh
            currentPage = 0
            pagedItems.clear()
            isLastPage = false
            loadData()
        }
        
        // Set Swipe Refresh colors
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_purple,
            android.R.color.holo_orange_light
        )
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(android.R.color.white)

        // Setup Search
        val searchInput = findViewById<TextInputEditText>(R.id.search_input)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                filter(searchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Setup FAB
        val fab = findViewById<FloatingActionButton>(R.id.fabAdd)
        fab.setOnClickListener {
            startActivity(Intent(this, EditEntryActivity::class.java))
        }
        
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Reset pagination and reload data when returning to main activity
        currentPage = 0
        pagedItems.clear()
        isLastPage = false
        loadData()
    }

    private fun loadData() {
        isSearching = false
        currentPage = 0
        pagedItems.clear()
        isLastPage = false
        isLoading = true
        
        // Show progress bar and hide empty state (UI operation must be on main thread)
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
            // Clear adapter data immediately to prevent RecyclerView inconsistency
            adapter.submit(emptyList(), false, true)
        }
        
        lifecycleScope.launch {
            // Get all entries to ensure we have the latest data including newly added ones
            val allItems = PasswordRepository.getAll(this@MainActivity)
            runOnUiThread {
                // Take only the first page (10 items)
                val firstPageItems = allItems.take(10)
                pagedItems.clear()
                pagedItems.addAll(firstPageItems)
                isLoading = false
                isLastPage = firstPageItems.isEmpty() || allItems.size <= 10
                progressBar.visibility = View.GONE
                
                // Update UI based on data availability
                updateEmptyStateVisibility(pagedItems.isEmpty())
                
                // Submit data to adapter with hasMore and isLoading flags
                adapter.submit(pagedItems, !isLastPage, false)
                
                // Stop swipe refresh animation if it's running
                swipeRefreshLayout.isRefreshing = false
                
                updateStats()
            }
        }
    }
    
    private fun loadMoreItems() {
        if (isLoading || isLastPage) return
        
        isLoading = true
        
        // Update adapter to show loading state
        adapter.submit(pagedItems, !isLastPage, true)
        
        currentPage++
        
        lifecycleScope.launch {
            // Get all items to know the total count
            val allItems = PasswordRepository.getAll(this@MainActivity)
            val moreItems = PasswordRepository.getPaged(this@MainActivity, currentPage)
            runOnUiThread {
                // Add new items to existing list
                pagedItems.addAll(moreItems)
                isLoading = false
                
                // Calculate if we've loaded all items
                // Check if pagedItems size is now equal to or greater than total items
                isLastPage = pagedItems.size >= allItems.size
                
                // Submit updated data to adapter with hasMore and isLoading flags
                adapter.submit(pagedItems, !isLastPage, false)
            }
        }
    }
    
    // Update empty state visibility based on data availability
    private fun updateEmptyStateVisibility(isEmpty: Boolean) {
        runOnUiThread {
            if (isEmpty && !isSearching) {
                // Clear adapter data before hiding RecyclerView to prevent inconsistency
                adapter.submit(emptyList(), false, false)
                emptyStateLayout.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyStateLayout.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun updateStats() {
        lifecycleScope.launch {
            val total = PasswordRepository.getTotalCount(this@MainActivity)
            val weak = PasswordRepository.getWeakPasswordCount(this@MainActivity)
            runOnUiThread {
                findViewById<TextView>(R.id.tv_total_count).text = total.toString()
                findViewById<TextView>(R.id.tv_weak_count).text = weak.toString()
            }
        }
    }

    private fun filter(q: String) {
        isSearching = q.isNotBlank()
        
        // Update UI on main thread
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
        }
        
        lifecycleScope.launch {
            val list = if (q.isBlank()) {
                // Reset to paged loading when search is cleared
                loadData()
                emptyList()
            } else {
                // Search all items, not just current page
                val allItems = PasswordRepository.getAll(this@MainActivity)
                val filtered = allItems.filter {
                    it.title.lowercase().contains(q.lowercase()) ||
                    it.account.lowercase().contains(q.lowercase()) ||
                    it.note.lowercase().contains(q.lowercase())
                }
                filtered
            }
            
            if (q.isNotBlank()) {
                runOnUiThread {
                    // In search mode, we show all results at once, no load more button
                    adapter.submit(list, false, false, true) // isSearch=true to hide load more
                    progressBar.visibility = View.GONE
                    
                    // Update empty state for search results
                    if (list.isEmpty()) {
                        // Show search empty state with large text
                        emptyStateLayout.visibility = View.VISIBLE
                        val emptyTitle = emptyStateLayout.findViewById<TextView>(R.id.tv_empty_title)
                        emptyTitle.text = getString(R.string.no_search_results, q)
                        emptyTitle.textSize = 20f // Same size as "暂无密码条目"
                    } else {
                        emptyStateLayout.visibility = View.GONE
                    }
                }
            }
        }
    }
}

private class EntryAdapter(
    val onEdit: (String) -> Unit,
    val onDelete: (PasswordEntry) -> Unit,
    val onLoadMore: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<PasswordEntry> = emptyList()
    private var hasMore = false
    private var isLoading = false
    private var isSearchMode = false
    
    fun submit(list: List<PasswordEntry>, hasMoreData: Boolean, isLoadingData: Boolean = false, isSearch: Boolean = false) {
        // Use notifyDataSetChanged() to ensure the list is fully refreshed
        items = list
        hasMore = hasMoreData
        isLoading = isLoadingData
        isSearchMode = isSearch
        notifyDataSetChanged()
    }
    
    companion object {
        const val VIEW_TYPE_ITEM = 0
        const val VIEW_TYPE_LOAD_MORE = 1
    }
    
    override fun getItemViewType(position: Int): Int {
        return if (position == items.size && !isSearchMode) VIEW_TYPE_LOAD_MORE else VIEW_TYPE_ITEM
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_LOAD_MORE) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_load_more, parent, false)
            LoadMoreViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_password, parent, false)
            EntryViewHolder(view)
        }
    }
    
    override fun getItemCount(): Int {
        // Show load more item if not in search mode and either:
        // 1. hasMore is true, OR
        // 2. we have loaded more than one page (items.size > 10)
        // This ensures:
        // - <10 items: no load more
        // - =10 items: no load more
        // - >10 items: show load more until all loaded, then show "没有了"
        return if (!isSearchMode && (hasMore || items.size > 10)) items.size + 1 else items.size
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is EntryViewHolder) {
            val item = items[position]
            holder.bind(item, onEdit, onDelete)
        } else if (holder is LoadMoreViewHolder) {
            holder.bind(isLoading, hasMore, onLoadMore)
        }
    }
    
    private class LoadMoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLoadMore: TextView = itemView.findViewById(R.id.tvLoadMore)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        
        fun bind(isLoading: Boolean, hasMore: Boolean, onLoadMore: () -> Unit) {
            if (isLoading) {
                tvLoadMore.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            } else {
                tvLoadMore.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
                
                if (hasMore) {
                    tvLoadMore.text = "... 加载更多 ..."
                    tvLoadMore.isClickable = true
                    tvLoadMore.setOnClickListener { onLoadMore() }
                    // Set dark color for "Load More"
                    tvLoadMore.setTextColor(itemView.context.getColor(R.color.md_theme_light_onSurface))
                } else {
                    tvLoadMore.text = "... 没有了 ..."
                    tvLoadMore.isClickable = false
                    tvLoadMore.setOnClickListener(null)
                    // Set light color for "No More"
                    tvLoadMore.setTextColor(itemView.context.getColor(R.color.md_theme_light_onSurfaceVariant))
                }
            }
        }
    }
}

private class PasswordEntryDiffCallback(
    private val oldList: List<PasswordEntry>,
    private val newList: List<PasswordEntry>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

private class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val title: TextView = itemView.findViewById(R.id.tvTitle)
    val account: TextView = itemView.findViewById(R.id.tvAccount)
    val password: TextView = itemView.findViewById(R.id.tvPassword)
    val btnToggle: ImageView = itemView.findViewById(R.id.btnTogglePassword)
    val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
    val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
    val tvDate: TextView = itemView.findViewById(R.id.tvDate)
    
    private var isPasswordVisible = false

    fun bind(item: PasswordEntry, onEdit: (String) -> Unit, onDelete: (PasswordEntry) -> Unit) {
        title.text = item.title
        account.text = item.account
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        tvDate.text = sdf.format(java.util.Date(item.createdAt))
        
        updatePasswordDisplay(item.password)
        
        btnToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            updatePasswordDisplay(item.password)
            val icon = if (isPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            btnToggle.setImageResource(icon)
        }
        
        btnEdit.setOnClickListener { onEdit(item.id) }
        btnDelete.setOnClickListener { onDelete(item) }
        
        // Also click on card to view details
        itemView.setOnClickListener { 
             val context = itemView.context
             val intent = Intent(context, ViewEntryActivity::class.java)
             intent.putExtra("id", item.id)
             context.startActivity(intent)
        }
    }
    
    private fun updatePasswordDisplay(pwd: String) {
        if (isPasswordVisible) {
            password.text = pwd
        } else {
            password.text = "••••••"
        }
    }
}
