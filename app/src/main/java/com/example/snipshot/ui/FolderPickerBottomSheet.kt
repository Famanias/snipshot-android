package com.example.snipshot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipshot.R
import com.example.snipshot.api.ApiClient
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.json.JSONObject

class FolderPickerBottomSheet(private val onFolderSelected: (Int?) -> Unit) : BottomSheetDialogFragment() {

    private lateinit var progressBar: ProgressBar
    private lateinit var rvFolders: RecyclerView
    private lateinit var adapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_folder_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progress_bar)
        rvFolders = view.findViewById(R.id.rv_folders)

        rvFolders.layoutManager = LinearLayoutManager(context)
        adapter = FolderAdapter(emptyList()) { folderId ->
            onFolderSelected(folderId)
            dismiss()
        }
        rvFolders.adapter = adapter

        loadFolders()
    }

    private fun loadFolders() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = ApiClient.getFolders()
            progressBar.visibility = View.GONE

            if (result.isSuccess) {
                val data = result.getOrNull()
                val foldersArray = data?.optJSONArray("folders")
                val foldersList = mutableListOf<FolderItem>()
                
                // Add "Unsorted" option
                foldersList.add(FolderItem(null, "No folder (Unsorted)"))

                if (foldersArray != null) {
                    for (i in 0 until foldersArray.length()) {
                        val folderObj = foldersArray.getJSONObject(i)
                        val pid = if (folderObj.isNull("parent_folder_id")) null else folderObj.getInt("parent_folder_id")
                        foldersList.add(
                            FolderItem(
                                id = folderObj.getInt("id"),
                                name = folderObj.getString("name"),
                                parentFolderId = pid
                            )
                        )
                    }
                }
                adapter.updateData(foldersList)
            } else {
                Toast.makeText(context, "Failed to load folders", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    data class FolderItem(val id: Int?, val name: String, val parentFolderId: Int? = null)

    inner class FolderAdapter(
        private var folders: List<FolderItem>,
        private val onClick: (Int?) -> Unit
    ) : RecyclerView.Adapter<FolderAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_folder_name)

            init {
                view.setOnClickListener {
                    onClick(folders[adapterPosition].id)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_folder_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvName.text = folders[position].name
        }

        override fun getItemCount() = folders.size

        fun updateData(newFolders: List<FolderItem>) {
            folders = newFolders
            notifyDataSetChanged()
        }
    }
}
