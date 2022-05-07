package com.shekharkg.scanner.ui.main

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.shekharkg.scanner.R
import com.shekharkg.scanner.factory.ViewModelFactory
import kotlinx.android.synthetic.main.fragment_preview.*

class PreviewFragment : Fragment() {

    private lateinit var viewModel: SharedViewModel

    companion object {
        fun newInstance() = PreviewFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_preview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let {
            viewModel = ViewModelProvider(it, ViewModelFactory())[SharedViewModel::class.java]
        }

        previewImageView.setImageBitmap(viewModel.getImagePath().value)
    }


}