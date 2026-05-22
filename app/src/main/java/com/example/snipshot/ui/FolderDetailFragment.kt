package com.example.snipshot.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.ImageDetailActivity
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import kotlinx.coroutines.launch

class FolderDetailFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileItemAdapter
    private lateinit var tvTitle: TextView
    private var folderId: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_folder_detail, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        tvTitle = view.findViewById(R.id.tv_folder_title)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        folderId = arguments?.getInt("folder_id", -1) ?: -1
        val folderName = arguments?.getString("folder_name", "Folder") ?: "Folder"
        tvTitle.text = folderName

        adapter = FileItemAdapter(emptyList(),
            onFolderClick = { },
            onImageClick = { item ->
                val intent = Intent(context, ImageDetailActivity::class.java)
                if (item is FileItem.CloudImage) {
                    intent.putExtra("is_local", false)
                    intent.putExtra("image_id", item.id)
                    intent.putExtra("filename", item.filename)
                    intent.putExtra("path_or_url", item.url)
                    startActivity(intent)
                }
            },
            onFolderLongClick = { folder, view -> },
            onImageLongClick = { item, view -> }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        if (folderId != -1) {
            loadFolderImages()
        }
    }

    private fun loadFolderImages() {
        lifecycleScope.launch {
            val result = ApiClient.getImages(folderId)
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
            } else {
                Toast.makeText(context, "Failed to load folder images", Toast.LENGTH_SHORT).show()
            }
        }
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
