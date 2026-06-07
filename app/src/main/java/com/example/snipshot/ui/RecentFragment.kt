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
import com.example.snipshot.utils.TranslationQueueManager
import com.example.snipshot.utils.TranslationTask
import com.example.snipshot.TranslationService
import kotlinx.coroutines.launch
import android.content.Intent

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
                when (item) {
                    is FileItem.CloudImage -> {
                        val intent = Intent(context, com.example.snipshot.ImageDetailActivity::class.java)
                        intent.putExtra("is_local", false)
                        intent.putExtra("image_id", item.id)
                        intent.putExtra("filename", item.filename)
                        intent.putExtra("path_or_url", item.url)
                        intent.putExtra("storage_path", item.storagePath)
                        startActivity(intent)
                    }
                    is FileItem.LocalImage -> {
                        val intent = Intent(context, com.example.snipshot.ImageDetailActivity::class.java)
                        intent.putExtra("is_local", true)
                        intent.putExtra("filename", item.file.name)
                        intent.putExtra("path_or_url", item.file.absolutePath)
                        startActivity(intent)
                    }
                    is FileItem.QueueItem -> {
                        val intent = Intent(context, com.example.snipshot.ImageDetailActivity::class.java)
                        intent.putExtra("is_queue", true)
                        intent.putExtra("task_id", item.task.id)
                        intent.putExtra("path_or_url", item.task.tempImagePath)
                        startActivity(intent)
                    }
                    is FileItem.Folder -> {
                        // Do nothing
                    }
                }
            },
            onFolderLongClick = { folder, view -> },
            onImageLongClick = { item, view ->
                showRecentImageContextMenu(item, view)
            },
            onImageSaveClick = { item, view ->
                val cloudImage = when (item) {
                    is FileItem.CloudImage -> item
                    is FileItem.QueueItem -> {
                        val task = item.task
                        if (task.status == TranslationTask.Status.COMPLETED && task.completedImageId != null) {
                            FileItem.CloudImage(
                                id = task.completedImageId!!,
                                filename = task.tempImagePath.substringAfterLast("/"),
                                url = task.completedUrl,
                                storagePath = task.completedStoragePath ?: ""
                            )
                        } else null
                    }
                    else -> null
                }
                if (cloudImage != null) {
                    showSaveImagePopup(cloudImage, view)
                }
            }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        wasLoggedIn = ApiClient.isLoggedIn()
        loadData()

        lifecycleScope.launch {
            TranslationQueueManager.tasks.collect {
                loadData()
            }
        }
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
        val completedItems = files.map { FileItem.LocalImage(it) }
        
        val activeTasks = TranslationQueueManager.tasks.value
        val queueItems = mutableListOf<FileItem.QueueItem>()
        for (task in activeTasks) {
            if (task.status == TranslationTask.Status.COMPLETED) {
                TranslationQueueManager.removeTask(requireContext(), task.id)
            } else {
                queueItems.add(FileItem.QueueItem(task))
            }
        }
        
        val combined = queueItems.sortedByDescending { it.task.timestamp } + completedItems
        adapter.updateData(combined)
        showEmptyState(combined.isEmpty())
    }

    private fun loadCloudRecent() {
        lifecycleScope.launch {
            val result = ApiClient.getImages()
            if (result.isSuccess) {
                val array = result.getOrNull()?.optJSONArray("images")
                val cloudItems = mutableListOf<FileItem.CloudImage>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val fname = obj.getString("filename")
                        if (fname.startsWith("PREVIEW_")) {
                            cloudItems.add(
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
                
                val activeTasks = TranslationQueueManager.tasks.value
                val queueItems = mutableListOf<FileItem.QueueItem>()
                for (task in activeTasks) {
                    if (task.status == TranslationTask.Status.COMPLETED) {
                        val alreadyInCloud = cloudItems.any { it.id == task.completedImageId }
                        if (alreadyInCloud) {
                            TranslationQueueManager.removeTask(requireContext(), task.id)
                        } else {
                            queueItems.add(FileItem.QueueItem(task))
                        }
                    } else {
                        queueItems.add(FileItem.QueueItem(task))
                    }
                }
                
                val combined = queueItems.sortedByDescending { it.task.timestamp } + cloudItems
                adapter.updateData(combined)
                showEmptyState(combined.isEmpty())
            } else {
                val activeTasks = TranslationQueueManager.tasks.value
                val queueItems = activeTasks.map { FileItem.QueueItem(it) }.sortedByDescending { it.task.timestamp }
                adapter.updateData(queueItems)
                showEmptyState(queueItems.isEmpty())
            }
        }
    }

    private fun showSaveImagePopup(item: FileItem.CloudImage, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Save to My Files")
        popup.menu.add(0, 2, 0, "Save to Folder")
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    saveCloudPreviewToFolder(item, null)
                    true
                }
                2 -> {
                    showRecentMoveImageDialog(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showRecentImageContextMenu(item: FileItem, anchor: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        if (item is FileItem.QueueItem) {
            when (item.task.status) {
                TranslationTask.Status.QUEUED -> {
                    popup.menu.add(0, 1, 0, "Cancel Translation")
                    popup.setOnMenuItemClickListener { menuItem ->
                        if (menuItem.itemId == 1) {
                            TranslationQueueManager.removeTask(requireContext(), item.task.id)
                            Toast.makeText(context, "Translation cancelled", Toast.LENGTH_SHORT).show()
                            true
                        } else false
                    }
                    popup.show()
                }
                TranslationTask.Status.PREPARING, TranslationTask.Status.TRANSLATING, TranslationTask.Status.UPLOADING -> {
                    Toast.makeText(context, "Translating... please wait", Toast.LENGTH_SHORT).show()
                }
                TranslationTask.Status.FAILED -> {
                    popup.menu.add(0, 1, 0, "Retry Translation")
                    popup.menu.add(0, 2, 0, "Delete")
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> {
                                retryTranslationTask(item.task)
                                true
                            }
                            2 -> {
                                TranslationQueueManager.removeTask(requireContext(), item.task.id)
                                Toast.makeText(context, "Removed from queue", Toast.LENGTH_SHORT).show()
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
                TranslationTask.Status.COMPLETED -> {
                    val cloudImage = FileItem.CloudImage(
                        id = item.task.completedImageId ?: -1,
                        filename = item.task.tempImagePath.substringAfterLast("/"),
                        url = item.task.completedUrl,
                        storagePath = item.task.completedStoragePath ?: ""
                    )
                    showSaveImagePopup(cloudImage, anchor)
                }
            }
        } else if (item is FileItem.CloudImage) {
            popup.menu.add(0, 1, 0, "Save as Unfiled")
            popup.menu.add(0, 2, 0, "Save to Folder")
            popup.menu.add(0, 3, 0, "Delete")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        saveCloudPreviewToFolder(item, null)
                        true
                    }
                    2 -> {
                        showRecentMoveImageDialog(item)
                        true
                    }
                    3 -> {
                        showDeletePreviewDialog(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        } else if (item is FileItem.LocalImage) {
            popup.menu.add(0, 3, 0, "Delete")
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    3 -> {
                        showDeleteLocalDialog(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun retryTranslationTask(task: TranslationTask) {
        TranslationQueueManager.updateTaskStatus(requireContext(), task.id, TranslationTask.Status.QUEUED)
        val serviceIntent = Intent(requireContext(), TranslationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
        Toast.makeText(context, "Retrying translation...", Toast.LENGTH_SHORT).show()
    }

    private fun saveCloudPreviewToFolder(item: FileItem.CloudImage, folderId: Int?) {
        lifecycleScope.launch {
            val cleanName = item.filename.removePrefix("PREVIEW_")
            val result = ApiClient.saveImageToFolder(item.id, cleanName, folderId)
            if (result.isSuccess) {
                if (folderId == null) {
                    Toast.makeText(context, "Saved to My Files", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Saved to folder", Toast.LENGTH_SHORT).show()
                }
                loadData()
            } else {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRecentMoveImageDialog(item: FileItem.CloudImage) {
        val bottomSheet = FolderPickerBottomSheet { folderId ->
            saveCloudPreviewToFolder(item, folderId)
        }
        bottomSheet.show(parentFragmentManager, "folder_picker")
    }

    private fun showDeletePreviewDialog(item: FileItem.CloudImage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Image")
            .setMessage("Delete preview \"${item.filename}\"? This cannot be undone.")
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
    }

    private fun showDeleteLocalDialog(item: FileItem.LocalImage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Image")
            .setMessage("Delete local image \"${item.file.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    val deleted = item.file.delete()
                    if (deleted) {
                        Toast.makeText(context, "Image deleted", Toast.LENGTH_SHORT).show()
                        loadData()
                    } else {
                        Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error deleting file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
