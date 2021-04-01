/*
 * Copyright 2021 Fiona Draxler. All Rights Reserved.
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

import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import de.lmu.arcasegrammar.model.LogEntry

class FirebaseLogger {

    companion object {
        private var myInstance: FirebaseLogger? = null

        fun getInstance() =
            myInstance?: synchronized(this) {
                myInstance?: FirebaseLogger().also {
                    myInstance = it
                }
            }
    }

//    TODO: activate for Firebase logging
//    private val database = Firebase.database.reference
    private var userID: String = ""

    fun setUserId(userID: String) {
        this.userID = userID
    }

    fun addLogMessage(event: String, eventInfo: String = "") {

        /* Logging ist currently deactivated
        Activate by uncommenting these lines and line 21. Also make sure to adjust the privacy policy in strings.xml!
         */
//        val reference = database.child("logs").push()
//        reference.setValue(LogEntry(System.currentTimeMillis(), userID, event, eventInfo))
    }
}