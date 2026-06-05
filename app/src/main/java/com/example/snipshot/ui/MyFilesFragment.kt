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
    private lateinit var scrollView: View
    private lateinit var rvFolders: RecyclerView
    private lateinit var rvImages: RecyclerView
    private lateinit var foldersSection: View
    private lateinit var btnViewAllFolders: Button
    private lateinit var fabNewFolder: FloatingActionButton
    private lateinit var emptyState: LinearLayout
    private lateinit var offlineBanner: LinearLayout
    private lateinit var btnLogin: Button

    private lateinit var foldersAdapter: FileItemAdapter
    private lateinit var imagesAdapter: FileItemAdapter
    private var wasLoggedIn: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_my_files, container, false)
        scrollView = view.findViewById(R.id.scroll_view)
        rvFolders = view.findViewById(R.id.rv_folders)
        rvImages = view.findViewById(R.id.rv_images)
        foldersSection = view.findViewById(R.id.folders_section)
        btnViewAllFolders = view.findViewById(R.id.btn_view_all_folders)
        fabNewFolder = view.findViewById(R.id.fab_new_folder)
        emptyState = view.findViewById(R.id.empty_state)
        offlineBanner = view.findViewById(R.id.offline_banner)
        btnLogin = view.findViewById(R.id.btn_login)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        foldersAdapter = FileItemAdapter(
            items = emptyList(),
            isHorizontal = true,
            onFolderClick = { folder ->
                val fragment = FolderDetailFragment.newInstance(folder.id, folder.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack("folder_${folder.id}")
                    .commit()
            },
            onImageClick = { _ -> },
            onFolderLongClick = { folder, anchorView ->
                showFolderContextMenu(folder, anchorView)
            },
            onImageLongClick = { _, _ -> }
        )
        rvFolders.adapter = foldersAdapter

        imagesAdapter = FileItemAdapter(
            items = emptyList(),
            isHorizontal = false,
            onFolderClick = { _ -> },
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
            onFolderLongClick = { _, _ -> },
            onImageLongClick = { item, anchorView ->
                showImageContextMenu(item, anchorView)
            }
        )
        rvImages.layoutManager = GridLayoutManager(context, 2)
        rvImages.adapter = imagesAdapter

        btnLogin.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }

        btnViewAllFolders.setOnClickListener {
            val fragment = AllFoldersFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
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
        popup.menu.add(0, 2, 1, "Move")
        popup.menu.add(0, 3, 2, "Delete")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { showRenameFolderDialog(folder); true }
                2 -> { showMoveFolderDialog(folder); true }
                3 -> { showDeleteFolderDialog(folder); true }
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

    private fun showMoveFolderDialog(folder: FileItem.Folder) {
        val bottomSheet = FolderPickerBottomSheet { targetParentId ->
            if (targetParentId == folder.id) {
                Toast.makeText(context, "Cannot move folder into itself", Toast.LENGTH_SHORT).show()
                return@FolderPickerBottomSheet
            }
            lifecycleScope.launch {
                val foldersResult = ApiClient.getFolders()
                if (foldersResult.isSuccess) {
                    val array = foldersResult.getOrNull()?.optJSONArray("folders")
                    val foldersMap = mutableMapOf<Int, Int?>()
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val fid = obj.getInt("id")
                            val pid = if (obj.isNull("parent_folder_id")) null else obj.getInt("parent_folder_id")
                            foldersMap[fid] = pid
                        }
                    }

                    if (targetParentId != null && isCircularAncestry(folder.id, targetParentId, foldersMap)) {
                        Toast.makeText(context, "Cannot move folder into its own subfolder", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val result = ApiClient.moveFolder(folder.id, targetParentId)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Folder moved successfully", Toast.LENGTH_SHORT).show()
                        loadCloudFiles()
                    } else {
                        Toast.makeText(context, "Failed to move folder", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Failed to fetch folders for validation", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomSheet.show(parentFragmentManager, "FolderPicker")
    }

    private fun isCircularAncestry(folderIdBeingMoved: Int, targetParentFolderId: Int, foldersMap: Map<Int, Int?>): Boolean {
        var currentId: Int? = targetParentFolderId
        while (currentId != null) {
            if (currentId == folderIdBeingMoved) {
                return true
            }
            currentId = foldersMap[currentId]
        }
        return false
    }

    private fun showDeleteFolderDialog(folder: FileItem.Folder) {
        val modes = arrayOf("Promote contents (reassign items)", "Delete recursively (delete all)")
        var selectedMode = 0
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Folder")
            .setSingleChoiceItems(modes, selectedMode) { _, which ->
                selectedMode = which
            }
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    val result = if (selectedMode == 0) {
                        ApiClient.deleteFolderPromote(folder.id, folder.parentFolderId)
                    } else {
                        ApiClient.deleteFolderRecursive(folder.id)
                    }
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

    private fun showEmptyState(empty: Boolean) {
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        scrollView.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun loadLocalFiles() {
        val files = StorageManager.getLocalFiles(requireContext())
        val items = files.map { FileItem.LocalImage(it) }
        imagesAdapter.updateData(items)
        foldersSection.visibility = View.GONE
        showEmptyState(items.isEmpty())
    }

    private fun loadCloudFiles() {
        lifecycleScope.launch {
            val foldersResult = ApiClient.getFolders()
            val imagesResult = ApiClient.getImages(folderId = null, isFolderIdNull = true)
            val folderItems = mutableListOf<FileItem.Folder>()
            val imageItems = mutableListOf<FileItem.CloudImage>()

            if (foldersResult.isSuccess) {
                val array = foldersResult.getOrNull()?.optJSONArray("folders")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fid = obj.getInt("id")
                        val fname = obj.getString("name")
                        val pid = if (obj.isNull("parent_folder_id")) null else obj.getInt("parent_folder_id")
                        folderItems.add(FileItem.Folder(fid, fname, parentFolderId = pid))
                    }
                }
            }

            if (imagesResult.isSuccess) {
                val array = imagesResult.getOrNull()?.optJSONArray("images")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fname = obj.getString("filename")
                        if (!fname.startsWith("[PREVIEW]_")) {
                            imageItems.add(
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
            }

            val rootFolders = folderItems.filter { it.parentFolderId == null }

            if (rootFolders.isNotEmpty()) {
                foldersSection.visibility = View.VISIBLE
                foldersAdapter.updateData(rootFolders.take(4))
            } else {
                foldersSection.visibility = View.GONE
            }

            imagesAdapter.updateData(imageItems)
            showEmptyState(rootFolders.isEmpty() && imageItems.isEmpty())
        }
    }
}
