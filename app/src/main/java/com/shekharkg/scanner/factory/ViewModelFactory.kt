package com.shekharkg.scanner.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shekharkg.scanner.ui.main.SharedViewModel


class ViewModelFactory : ViewModelProvider.Factory {


    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SharedViewModel() as T
    }

}