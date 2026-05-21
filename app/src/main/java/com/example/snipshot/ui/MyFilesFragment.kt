package com.example.snipshot.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.LoginActivity
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import com.example.snipshot.utils.StorageManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MyFilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabNewFolder: FloatingActionButton
    private lateinit var emptyState: LinearLayout
    private lateinit var offlineBanner: LinearLayout
    private lateinit var btnLogin: Button

    private lateinit var adapter: FileItemAdapter
    private var wasLoggedIn: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_my_files, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        fabNewFolder = view.findViewById(R.id.fab_new_folder)
        emptyState = view.findViewById(R.id.empty_state)
        offlineBanner = view.findViewById(R.id.offline_banner)
        btnLogin = view.findViewById(R.id.btn_login)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FileItemAdapter(emptyList(),
            onFolderClick = { folder ->
                val fragment = FolderDetailFragment.newInstance(folder.id, folder.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            },
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
            onFolderLongClick = { folder, anchorView ->
                showFolderContextMenu(folder, anchorView)
            },
            onImageLongClick = { item, anchorView ->
                showImageContextMenu(item, anchorView)
            }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        btnLogin.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }

        fabNewFolder.setOnClickListener {
            showCreateFolderDialog()
        }

        wasLoggedIn = ApiClient.isLoggedIn()
        refreshAuthState()
    }

    override fun onResume() {
        super.onResume()
        val isLoggedIn = ApiClient.isLoggedIn()
        if (isLoggedIn != wasLoggedIn) {
            wasLoggedIn = isLoggedIn
            refreshAuthState()
        }
    }

    private fun refreshAuthState() {
        if (ApiClient.isLoggedIn()) {
            offlineBanner.visibility = View.GONE
            fabNewFolder.visibility = View.VISIBLE
            loadCloudFiles()
        } else {
            offlineBanner.visibility = View.VISIBLE
            fabNewFolder.visibility = View.GONE
            loadLocalFiles()
        }
    }

    // ── Folder creation ───────────────────────────────────────────────────────

    private fun showCreateFolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Folder name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = ApiClient.createFolder(name)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Folder \"$name\" created", Toast.LENGTH_SHORT).show()
                        loadCloudFiles()
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
            .also { dialog ->
                // Auto-focus keyboard
                input.requestFocus()
            }
    }

    // ── Context menus ─────────────────────────────────────────────────────────

    private fun showFolderContextMenu(folder: FileItem.Folder, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Rename")
        popup.menu.add(0, 2, 1, "Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { showRenameFolderDialog(folder); true }
                2 -> { showDeleteFolderDialog(folder); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRenameFolderDialog(folder: FileItem.Folder) {
        val input = EditText(requireContext()).apply {
            setText(folder.name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename Folder")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    val result = ApiClient.renameFolder(folder.id, newName)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Renamed to \"$newName\"", Toast.LENGTH_SHORT).show()
                        loadCloudFiles()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteFolderDialog(folder: FileItem.Folder) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Folder")
            .setMessage("Delete \"${folder.name}\"? Images inside will be moved to your main library.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = ApiClient.deleteFolder(folder.id, deleteImages = false)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Folder deleted", Toast.LENGTH_SHORT).show()
                        loadCloudFiles()
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showImageContextMenu(item: FileItem, anchor: View) {
        if (item !is FileItem.CloudImage) return
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Delete")
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == 1) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Image")
                    .setMessage("Delete \"${item.filename}\"? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            val result = ApiClient.deleteImage(item.id)
                            if (result.isSuccess) {
                                Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                                loadCloudFiles()
                            } else {
                                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            } else false
        }
        popup.show()
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private fun showEmptyState(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun loadLocalFiles() {
        val files = StorageManager.getLocalFiles(requireContext())
        val items = files.map { FileItem.LocalImage(it) }
        adapter.updateData(items)
        showEmptyState(items.isEmpty())
    }

    private fun loadCloudFiles() {
        lifecycleScope.launch {
            val foldersResult = ApiClient.getFolders()
            val imagesResult = ApiClient.getImages(folderId = null)
            val items = mutableListOf<FileItem>()

            if (foldersResult.isSuccess) {
                val array = foldersResult.getOrNull()?.optJSONArray("folders")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        items.add(FileItem.Folder(obj.getInt("id"), obj.getString("name")))
                    }
                }
            }

            if (imagesResult.isSuccess) {
                val array = imagesResult.getOrNull()?.optJSONArray("images")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        items.add(FileItem.CloudImage(obj.getInt("id"), obj.getString("filename"), obj.getString("public_url")))
                    }
                }
            }

            adapter.updateData(items)
            showEmptyState(items.isEmpty())
        }
    }
}
