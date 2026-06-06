package com.example.snipshot.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.ImageDetailActivity
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

class FolderDetailFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileItemAdapter
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var fabNewFolder: FloatingActionButton
    private var folderId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_folder_detail, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        tvTitle = view.findViewById(R.id.tv_folder_title)
        btnBack = view.findViewById(R.id.btn_back)
        fabNewFolder = view.findViewById(R.id.fab_new_folder)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        folderId = arguments?.getInt("folder_id", -1) ?: -1
        
        tvTitle.movementMethod = LinkMovementMethod.getInstance()

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        fabNewFolder.setOnClickListener {
            showCreateSubfolderDialog()
        }

        adapter = FileItemAdapter(
            items = emptyList(),
            isHorizontal = false,
            onFolderClick = { folder ->
                val fragment = newInstance(folder.id, folder.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack("folder_${folder.id}")
                    .commit()
            },
            onImageClick = { item ->
                val intent = Intent(context, ImageDetailActivity::class.java)
                if (item is FileItem.CloudImage) {
                    intent.putExtra("is_local", false)
                    intent.putExtra("image_id", item.id)
                    intent.putExtra("filename", item.filename)
                    intent.putExtra("path_or_url", item.url)
                    intent.putExtra("storage_path", item.storagePath)
                    startActivity(intent)
                }
            },
            onFolderLongClick = { folder, anchorView ->
                showFolderContextMenu(folder, anchorView)
            },
            onImageLongClick = { item, anchorView ->
                if (item is FileItem.CloudImage) {
                    showImageContextMenu(item, anchorView)
                }
            }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        if (folderId != -1) {
            loadFolderData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (folderId != -1) {
            loadFolderData()
        }
    }

    private fun loadFolderData() {
        lifecycleScope.launch {
            val foldersResult = ApiClient.getFolders()
            val imagesResult = ApiClient.getImages(folderId)

            val allFoldersList = mutableListOf<FileItem.Folder>()
            if (foldersResult.isSuccess) {
                val array = foldersResult.getOrNull()?.optJSONArray("folders")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fid = obj.getInt("id")
                        val fname = obj.getString("name")
                        val pid = if (obj.isNull("parent_folder_id")) null else obj.getInt("parent_folder_id")
                        allFoldersList.add(FileItem.Folder(fid, fname, parentFolderId = pid))
                    }
                }
            }

            // Build Clickable Breadcrumbs
            val breadcrumbBuilder = SpannableStringBuilder()
            
            // 1. Library Root Link
            val libStart = breadcrumbBuilder.length
            breadcrumbBuilder.append("Library")
            val libEnd = breadcrumbBuilder.length
            breadcrumbBuilder.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                }
            }, libStart, libEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            // 2. Ancestor folders
            val chain = mutableListOf<FileItem.Folder>()
            var current = allFoldersList.find { it.id == folderId }
            while (current != null) {
                chain.add(0, current)
                current = allFoldersList.find { it.id == current.parentFolderId }
            }

            for (folder in chain) {
                breadcrumbBuilder.append(" > ")
                val start = breadcrumbBuilder.length
                breadcrumbBuilder.append(folder.name)
                val end = breadcrumbBuilder.length
                
                if (folder.id != folderId) {
                    breadcrumbBuilder.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            parentFragmentManager.popBackStack("folder_${folder.id}", 0)
                        }
                    }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            tvTitle.text = breadcrumbBuilder

            // List of items in current folder:
            // Subfolders (parentFolderId == folderId) + Images (folderId == currentFolderId)
            val subfolders = allFoldersList.filter { it.parentFolderId == folderId }
            val images = mutableListOf<FileItem.CloudImage>()

            if (imagesResult.isSuccess) {
                val array = imagesResult.getOrNull()?.optJSONArray("images")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fname = obj.getString("filename")
                        if (!fname.startsWith("PREVIEW_")) {
                            images.add(
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

            val itemsToShow = subfolders + images
            adapter.updateData(itemsToShow)
        }
    }

    private fun showCreateSubfolderDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Subfolder name"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Subfolder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = ApiClient.createFolder(name, parentFolderId = folderId)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Folder \"$name\" created", Toast.LENGTH_SHORT).show()
                        loadFolderData()
                    } else {
                        Toast.makeText(context, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
            .also { dialog ->
                input.requestFocus()
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
                        loadFolderData()
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
                        loadFolderData()
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
                        loadFolderData()
                    } else {
                        Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                                    loadFolderData()
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
                        loadFolderData()
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
                    loadFolderData()
                } else {
                    Toast.makeText(context, "Move failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomSheet.show(parentFragmentManager, "folder_picker")
    }

    companion object {
        fun newInstance(folderId: Int, folderName: String): FolderDetailFragment {
            val fragment = FolderDetailFragment()
            val args = Bundle()
            args.putInt("folder_id", folderId)
            args.putString("folder_name", folderName)
            fragment.arguments = args
            return fragment
        }
    }
}
