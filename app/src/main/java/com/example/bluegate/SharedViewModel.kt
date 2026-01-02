package com.example.bluegate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {

    private val _selectedParameter = MutableLiveData<Pair<Int, Int>>()
    val selectedParameter: LiveData<Pair<Int, Int>> = _selectedParameter

    fun selectParameter(parameter: Pair<Int, Int>) {
        _selectedParameter.value = parameter
    }
}
