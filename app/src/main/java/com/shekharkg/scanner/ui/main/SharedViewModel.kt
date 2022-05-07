package com.shekharkg.scanner.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.shekharkg.scanner.model.State

class SharedViewModel : ViewModel() {

    private var _state: MutableLiveData<State> = MutableLiveData(State.CHECK_FOR_PERMISSION)
    fun getState(): LiveData<State> = _state
    fun setState(state: State) {
        _state.value = state
    }

    private var _bitmap: MutableLiveData<Bitmap?> = MutableLiveData()
    fun getImagePath(): LiveData<Bitmap?> = _bitmap
    fun setImagePath(bitmap: Bitmap) {
        _bitmap.value = bitmap
    }

}