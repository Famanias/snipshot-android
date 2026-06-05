package com.example.snipshot.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import kotlinx.coroutines.launch

class AllFoldersFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: FileItemAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_all_folders, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        btnBack = view.findViewById(R.id.btn_back)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        adapter = FileItemAdapter(
            items = emptyList(),
            isHorizontal = false,
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

        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        loadFolders()
    }

    private fun loadFolders() {
        lifecycleScope.launch {
            val foldersResult = ApiClient.getFolders()
            if (foldersResult.isSuccess) {
                val array = foldersResult.getOrNull()?.optJSONArray("folders")
                val items = mutableListOf<FileItem.Folder>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fid = obj.getInt("id")
                        val fname = obj.getString("name")
                        val pid = if (obj.isNull("parent_folder_id")) null else obj.getInt("parent_folder_id")
                        items.add(FileItem.Folder(fid, fname, parentFolderId = pid))
                    }
                }
                val rootFolders = items.filter { it.parentFolderId == null }
                adapter.updateData(rootFolders)
            } else {
                Toast.makeText(context, "Failed to load folders", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                        loadFolders()
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
                        loadFolders()
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
                        loadFolders()
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
