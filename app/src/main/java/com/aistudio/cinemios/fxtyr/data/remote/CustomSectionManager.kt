package com.aistudio.cinemios.fxtyr.data.remote

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class CustomSectionItem(
    val id: String = "",
    val displayType: String = "poster", // "poster", "landscape", "gradient"
    val targetAction: String = "details", // "details", "link", "downloads", "settings"
    val targetData: String = "", 
    val message: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object CustomSectionManager {
    private val db = FirebaseDatabase.getInstance().reference.child("custom_section")

    fun getItems(): Flow<List<CustomSectionItem>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CustomSectionItem>()
                for (child in snapshot.children) {
                    child.getValue(CustomSectionItem::class.java)?.let { list.add(it) }
                }
                list.sortByDescending { it.timestamp }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.addValueEventListener(listener)
        awaitClose { db.removeEventListener(listener) }
    }

    suspend fun saveItem(item: CustomSectionItem) {
        val key = if (item.id.isNotEmpty()) item.id else (db.push().key ?: return)
        db.child(key).setValue(item.copy(id = key)).await()
    }
    
    suspend fun deleteItem(id: String) {
        db.child(id).removeValue().await()
    }
}
