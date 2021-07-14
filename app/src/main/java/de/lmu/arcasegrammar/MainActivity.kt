/*
 * Copyright 2021 Fiona Draxler, Elena Wallwitz. All Rights Reserved.
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

package de.lmu.arcasegrammar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.*

class MainActivity : AppCompatActivity() {


    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var firebaseLogger: FirebaseLogger
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase logging
        firebaseLogger = FirebaseLogger.getInstance()

        val sharedPreferences = getSharedPreferences("casear", Context.MODE_PRIVATE)
        var userID = sharedPreferences.getString("userID", "")
        userID?.let {
            if (it.isEmpty()) {
                userID = UUID.randomUUID().toString().substring(IntRange(0, 7))
                sharedPreferences
                    .edit()
                    .putString("userID", userID)
                    .apply()
                // user ID should be set with the first log message!
                firebaseLogger.setUserId(userID!!)
            }
        }

        firebaseLogger.addLogMessage("app_started")

        // set layout
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController
        findViewById<BottomNavigationView>(R.id.nav_view)
            .setupWithNavController(navController)

    }

    override fun onPause() {
        firebaseLogger.addLogMessage("app_paused")
        super.onPause()
    }
}
