package com.example.snipshot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import com.example.snipshot.utils.StorageManager
import kotlinx.coroutines.launch

class RecentFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var adapter: FileItemAdapter
    private var wasLoggedIn: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_recent, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        emptyState = view.findViewById(R.id.empty_state)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileItemAdapter(emptyList(),
            onFolderClick = { },
            onImageClick = { item ->
                val intent = android.content.Intent(context, com.example.snipshot.ImageDetailActivity::class.java)
                when (item) {
                    is FileItem.CloudImage -> {
                        intent.putExtra("is_local", false)
                        intent.putExtra("image_id", item.id)
                        intent.putExtra("filename", item.filename)
                        intent.putExtra("path_or_url", item.url)
                    }
                    is FileItem.LocalImage -> {
                        intent.putExtra("is_local", true)
                        intent.putExtra("filename", item.file.name)
                        intent.putExtra("path_or_url", item.file.absolutePath)
                    }
                    else -> return@FileItemAdapter
                }
                startActivity(intent)
            },
            onFolderLongClick = { folder, view -> },
            onImageLongClick = { item, view ->
                // Show popup menu
            }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        wasLoggedIn = ApiClient.isLoggedIn()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        val isLoggedIn = ApiClient.isLoggedIn()
        if (isLoggedIn != wasLoggedIn) {
            wasLoggedIn = isLoggedIn
            loadData()
        }
    }

    private fun loadData() {
        if (ApiClient.isLoggedIn()) {
            loadCloudRecent()
        } else {
            loadLocalRecent()
        }
    }

    private fun showEmptyState(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun loadLocalRecent() {
        val files = StorageManager.getLocalFiles(requireContext())
        val items = files.map { FileItem.LocalImage(it) }
        adapter.updateData(items)
        showEmptyState(items.isEmpty())
    }

    private fun loadCloudRecent() {
        lifecycleScope.launch {
            val result = ApiClient.getImages()
            if (result.isSuccess) {
                val array = result.getOrNull()?.optJSONArray("images")
                val items = mutableListOf<FileItem>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fname = obj.getString("filename")
                        if (!fname.startsWith("[PREVIEW]_")) {
                            items.add(FileItem.CloudImage(obj.getInt("id"), fname, obj.getString("public_url")))
                        }
                    }
                }
                adapter.updateData(items)
                showEmptyState(items.isEmpty())
            } else {
                showEmptyState(true)
            }
        }
    }
}
