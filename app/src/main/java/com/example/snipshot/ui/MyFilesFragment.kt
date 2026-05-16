package com.example.snipshot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import com.example.snipshot.utils.StorageManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MyFilesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabNewFolder: FloatingActionButton

    private lateinit var adapter: FileItemAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_my_files, container, false)
        recyclerView = view.findViewById(R.id.recycler_view)
        fabNewFolder = view.findViewById(R.id.fab_new_folder)
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
            onFolderLongClick = { folder, view ->
                // Show popup menu
            },
            onImageLongClick = { item, view ->
                // Show popup menu
            }
        )
        recyclerView.layoutManager = GridLayoutManager(context, 2)
        recyclerView.adapter = adapter

        if (ApiClient.isLoggedIn()) {
            fabNewFolder.visibility = View.VISIBLE
            loadCloudFiles()
            fabNewFolder.setOnClickListener {
                Toast.makeText(context, "New folder clicked", Toast.LENGTH_SHORT).show()
            }
        } else {
            fabNewFolder.visibility = View.GONE
            loadLocalFiles()
        }
    }

    private fun loadLocalFiles() {
        val files = StorageManager.getLocalFiles(requireContext())
        val items = files.map { FileItem.LocalImage(it) }
        adapter.updateData(items)
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
        }
    }
}
