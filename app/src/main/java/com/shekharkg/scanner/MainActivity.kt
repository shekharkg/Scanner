package com.shekharkg.scanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.shekharkg.scanner.factory.ViewModelFactory
import com.shekharkg.scanner.model.State
import com.shekharkg.scanner.ui.main.PermissionFragment
import com.shekharkg.scanner.ui.main.PreviewFragment
import com.shekharkg.scanner.ui.main.ScannerFragment
import com.shekharkg.scanner.ui.main.SharedViewModel

class MainActivity : AppCompatActivity() {


    lateinit var viewModel: SharedViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        viewModel = ViewModelProvider(this, ViewModelFactory())[SharedViewModel::class.java]

        viewModel.getState().observe(this) { it ->
            it?.let { state ->
                when (state) {
                    State.CHECK_FOR_PERMISSION -> replaceFragment(PermissionFragment.newInstance())
                    State.PERMISSION_GRANTED -> replaceFragment(ScannerFragment.newInstance())
                    State.IMAGE_CAPTURED -> replaceFragment(PreviewFragment.newInstance())
                }
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

}