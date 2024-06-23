/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.objectdetection

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.internal.NavigationMenuItemView
import com.google.mediapipe.examples.objectdetection.databinding.ActivityMainBinding
import com.google.mediapipe.examples.objectdetection.fragments.CameraFragment
import com.google.mediapipe.examples.objectdetection.fragments.GalleryFragment
import java.io.InputStream

/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private var recipes: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        activityMainBinding.navigation.setupWithNavController(navController)
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }
    }

    fun cameraItemClick(item: MenuItem)
    {
        val navView: BottomNavigationView = findViewById(R.id.navigation)
        //navView.selectedItemId = R.id.camera_fragment

        val cameraFragment = CameraFragment()
        supportFragmentManager.beginTransaction().replace(
            R.id.fragment_container, cameraFragment, cameraFragment.javaClass.simpleName
        ).addToBackStack(null).commit()
    }
    fun searchButtonClick(view: View?)
    {
        val navView: BottomNavigationView = findViewById(R.id.navigation)
        navView.performClick()
        navView.selectedItemId = R.id.results_fragment

        val resultsFragment = GalleryFragment()
        supportFragmentManager.beginTransaction().replace(
            R.id.fragment_container, resultsFragment, resultsFragment.javaClass.simpleName
        ).addToBackStack(null).commit()
    }

    override fun onBackPressed() {
        finish()
    }
}