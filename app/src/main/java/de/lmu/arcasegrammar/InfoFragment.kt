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

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.lmu.arcasegrammar.databinding.FragmentInfoBinding

class InfoFragment: Fragment() {

    // View binding
    private var _binding: FragmentInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_info, container, false)

        if (Build.VERSION.SDK_INT >= 24) {
            binding.contact.text = Html.fromHtml(getString(R.string.contact), Html.FROM_HTML_MODE_COMPACT)
        }
        else {
            binding.contact.text = Html.fromHtml(getString(R.string.contact))
        }

        root.isClickable = true

        return root
    }
}