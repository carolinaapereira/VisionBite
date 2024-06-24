package com.google.mediapipe.examples.objectdetection

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _itemList = MutableLiveData<ArrayList<String>>()
    val itemList: LiveData<ArrayList<String>> get() = _itemList

    init {
        _itemList.value = arrayListOf()
    }

    fun updateItemList(newList: ArrayList<String>) {
        _itemList.value = newList
    }

    fun addItem(item: String) {
        _itemList.value?.apply {
            add(item)
            _itemList.value = this
        }
    }

    fun hasItem(item: String): Boolean? {
        return _itemList.value?.contains(item)
    }

    fun removeItem(item: String) {
        _itemList.value?.apply {
            remove(item)
            _itemList.value = this
        }
    }
}
