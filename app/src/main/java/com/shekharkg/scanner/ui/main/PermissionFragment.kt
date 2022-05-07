package com.shekharkg.scanner.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.shekharkg.scanner.R
import com.shekharkg.scanner.factory.ViewModelFactory
import com.shekharkg.scanner.model.State
import kotlinx.android.synthetic.main.fragment_permission.*

private const val PERMISSIONS_REQUEST_CODE = 7
private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

class PermissionFragment : Fragment() {

    private lateinit var viewModel: SharedViewModel

    companion object {
        fun newInstance() = PermissionFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let {
            viewModel = ViewModelProvider(it, ViewModelFactory())[SharedViewModel::class.java]
        }

        checkForPermission()

        action.setOnClickListener {
            checkForPermission()
        }

    }

    private fun checkForPermission() {
        if (hasCameraPermission(requireContext())) {
            updateForPermissionGranted()
        } else {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateForPermissionGranted()
            } else {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateForPermissionGranted() {
        if (this::viewModel.isInitialized) {
            viewModel.setState(State.PERMISSION_GRANTED)
        } else {
            Toast.makeText(
                activity,
                "Permission: Viewmodel is not initialized",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun hasCameraPermission(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


}