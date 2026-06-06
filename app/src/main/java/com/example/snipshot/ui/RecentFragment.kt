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
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
                        intent.putExtra("storage_path", item.storagePath)
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
                if (item is FileItem.CloudImage) {
                    showImageContextMenu(item, view)
                }
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
                        if (!fname.startsWith("PREVIEW_")) {
                            items.add(
                                FileItem.CloudImage(
                                    id = obj.getInt("id"),
                                    filename = fname,
                                    url = obj.optString("public_url", ""),
                                    storagePath = obj.getString("storage_path")
                                )
                            )
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

    private fun showImageContextMenu(item: FileItem.CloudImage, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Edit image name")
        popup.menu.add(0, 2, 0, "Move to folder")
        popup.menu.add(0, 3, 0, "Delete")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showRenameImageDialog(item)
                    true
                }
                2 -> {
                    showMoveImageDialog(item)
                    true
                }
                3 -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete Image")
                        .setMessage("Delete \"${item.filename}\"? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            lifecycleScope.launch {
                                val result = ApiClient.deleteImage(item.id)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                                    loadData()
                                } else {
                                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameImageDialog(item: FileItem.CloudImage) {
        val input = EditText(requireContext()).apply {
            setText(item.filename)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit Image Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val result = ApiClient.renameImage(item.id, newName)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Image renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoveImageDialog(item: FileItem.CloudImage) {
        val bottomSheet = FolderPickerBottomSheet { targetParentId ->
            lifecycleScope.launch {
                val result = ApiClient.moveImage(item.id, targetParentId)
                if (result.isSuccess) {
                    Toast.makeText(context, "Image moved successfully", Toast.LENGTH_SHORT).show()
                    loadData()
                } else {
                    Toast.makeText(context, "Move failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomSheet.show(parentFragmentManager, "folder_picker")
    }
}
