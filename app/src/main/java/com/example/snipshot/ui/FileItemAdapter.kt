package com.example.snipshot.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.snipshot.R
import java.io.File
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.snipshot.api.ApiClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

sealed class FileItem {
    data class Folder(val id: Int, val name: String, val count: Int = 0, val parentFolderId: Int? = null) : FileItem()
    data class CloudImage(val id: Int, val filename: String, val url: String?, val storagePath: String) : FileItem()
    data class LocalImage(val file: File) : FileItem()
    data class QueueItem(val task: com.example.snipshot.utils.TranslationTask) : FileItem()
}

class FileItemAdapter(
    private var items: List<FileItem>,
    private val isHorizontal: Boolean = false,
    private val onFolderClick: (FileItem.Folder) -> Unit,
    private val onImageClick: (FileItem) -> Unit,
    private val onFolderLongClick: (FileItem.Folder, View) -> Unit,
    private val onImageLongClick: (FileItem, View) -> Unit,
    private val onImageSaveClick: ((FileItem, View) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_FOLDER = 1
        const val TYPE_IMAGE = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is FileItem.Folder -> TYPE_FOLDER
            is FileItem.CloudImage, is FileItem.LocalImage, is FileItem.QueueItem -> TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            val view = inflater.inflate(R.layout.item_folder, parent, false)
            if (isHorizontal) {
                val density = parent.context.resources.displayMetrics.density
                val widthPx = (140 * density).toInt()
                val params = view.layoutParams
                if (params != null) {
                    params.width = widthPx
                    view.layoutParams = params
                }
            }
            FolderViewHolder(view)
        } else {
            ImageViewHolder(inflater.inflate(R.layout.item_image, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FileItem.Folder -> (holder as FolderViewHolder).bind(item)
            is FileItem.CloudImage -> (holder as ImageViewHolder).bindCloud(item)
            is FileItem.LocalImage -> (holder as ImageViewHolder).bindLocal(item)
            is FileItem.QueueItem -> (holder as ImageViewHolder).bindQueue(item)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tv_folder_name)
        
        fun bind(item: FileItem.Folder) {
            tvName.text = item.name
            itemView.setOnClickListener { onFolderClick(item) }
            itemView.setOnLongClickListener {
                onFolderLongClick(item, itemView)
                true
            }
        }
    }

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivThumb: ImageView = view.findViewById(R.id.iv_thumbnail)
        private val tvName: TextView = view.findViewById(R.id.tv_filename)
        private val btnSave: android.widget.ImageButton = view.findViewById(R.id.btn_save_image)
        private var loadJob: Job? = null

        fun bindCloud(item: FileItem.CloudImage) {
            tvName.text = item.filename
            loadJob?.cancel()
            ivThumb.setImageDrawable(null)

            if (onImageSaveClick != null && item.filename.startsWith("PREVIEW_")) {
                btnSave.visibility = View.VISIBLE
                btnSave.setOnClickListener { onImageSaveClick.invoke(item, btnSave) }
            } else {
                btnSave.visibility = View.GONE
            }

            val lifecycleOwner = itemView.findViewTreeLifecycleOwner() ?: (itemView.context as? androidx.lifecycle.LifecycleOwner)
            if (lifecycleOwner != null) {
                loadJob = lifecycleOwner.lifecycleScope.launch {
                    val signedUrl = ApiClient.getSignedUrl(item.storagePath)
                    if (signedUrl != null) {
                        ivThumb.load(signedUrl) {
                            crossfade(true)
                        }
                    }
                }
            } else {
                if (!item.url.isNullOrEmpty()) {
                    ivThumb.load(item.url) {
                        crossfade(true)
                    }
                }
            }
            itemView.setOnClickListener { onImageClick(item) }
            itemView.setOnLongClickListener { onImageLongClick(item, itemView); true }
        }

        fun bindLocal(item: FileItem.LocalImage) {
            tvName.text = item.file.name
            btnSave.visibility = View.GONE
            ivThumb.load(item.file) {
                crossfade(true)
            }
            itemView.setOnClickListener { onImageClick(item) }
            itemView.setOnLongClickListener { onImageLongClick(item, itemView); true }
        }

        fun bindQueue(item: FileItem.QueueItem) {
            tvName.text = when (item.task.status) {
                com.example.snipshot.utils.TranslationTask.Status.QUEUED -> {
                    val pos = item.task.queuePosition
                    "Queued (#$pos)"
                }
                com.example.snipshot.utils.TranslationTask.Status.PREPARING -> "Preparing..."
                com.example.snipshot.utils.TranslationTask.Status.TRANSLATING -> "Translating..."
                com.example.snipshot.utils.TranslationTask.Status.UPLOADING -> "Uploading..."
                com.example.snipshot.utils.TranslationTask.Status.FAILED -> "Failed"
                com.example.snipshot.utils.TranslationTask.Status.COMPLETED -> "Completed"
            }

            loadJob?.cancel()
            ivThumb.setImageDrawable(null)
            ivThumb.load(File(item.task.tempImagePath)) {
                crossfade(true)
            }

            if (onImageSaveClick != null && item.task.status == com.example.snipshot.utils.TranslationTask.Status.COMPLETED) {
                btnSave.visibility = View.VISIBLE
                btnSave.setOnClickListener { onImageSaveClick.invoke(item, btnSave) }
            } else {
                btnSave.visibility = View.GONE
            }

            itemView.setOnClickListener { onImageClick(item) }
            itemView.setOnLongClickListener { onImageLongClick(item, itemView); true }
        }
    }
}
