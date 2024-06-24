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

package com.google.mediapipe.examples.objectdetection.fragments

import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.mediapipe.examples.objectdetection.MainViewModel
import com.google.mediapipe.examples.objectdetection.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.R
import com.google.mediapipe.examples.objectdetection.SharedViewModel
import com.google.mediapipe.examples.objectdetection.databinding.FragmentGalleryBinding
import com.google.mediapipe.examples.objectdetection.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class GalleryFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        // val view = inflater.inflate(R.layout.fragment_gallery, container, false)
        // val view = inflater.inflate(R.layout.fragment_gallery, container, false)
        val sharedViewModel = ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)

        // Observe the itemList LiveData
        sharedViewModel.itemList.observe(viewLifecycleOwner, Observer { itemList ->
            val recipes = searchRecipe(itemList)
            val textView = fragmentGalleryBinding.root.findViewById<TextView>(R.id.tvPlaceholder)
            if (recipes.isNotEmpty()) {
                textView.text = recipes.joinToString("\n\n\n")
            } else {
                textView.setText(R.string.tv_recipes_placeholder)
            }
        })

        return fragmentGalleryBinding.root
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        // no-op
    }

    fun searchRecipe(ingredientList: ArrayList<String>): MutableList<String> {
        //val file = File("assets/recipes.json").absoluteFile.inputStream()
        //val recipes = file.bufferedReader().use { it.readText() }
        println(ingredientList)
        val recipes = "[\n" +
                "    {\n" +
                "      \"id\": 1,\n" +
                "      \"name\": \"Chicken Fried Rice\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"chicken\", \"quantity\": \"200g\"},\n" +
                "        {\"name\": \"rice\", \"quantity\": \"1 cup\"},\n" +
                "        {\"name\": \"egg\", \"quantity\": \"2\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"carrot\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"mushroom\", \"quantity\": \"3\"},\n" +
                "        {\"name\": \"soy sauce\", \"quantity\": \"2 tbsp\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Cook the rice and set aside.\",\n" +
                "        \"In a pan, cook the diced chicken until browned.\",\n" +
                "        \"Add chopped onion, carrot, and mushroom to the pan and stir-fry until vegetables are tender.\",\n" +
                "        \"Push the ingredients to one side of the pan and scramble the egg on the other side.\",\n" +
                "        \"Mix everything together and add cooked rice.\",\n" +
                "        \"Pour in soy sauce and stir until everything is well combined.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 2,\n" +
                "      \"name\": \"Tomato and Lettuce Salad\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"lettuce\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"tomato\", \"quantity\": \"2\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"olive oil\", \"quantity\": \"2 tbsp\"},\n" +
                "        {\"name\": \"lemon juice\", \"quantity\": \"1 tbsp\"},\n" +
                "        {\"name\": \"salt\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"pepper\", \"quantity\": \"to taste\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Chop the lettuce, tomato, and onion.\",\n" +
                "        \"In a large bowl, combine the chopped vegetables.\",\n" +
                "        \"Drizzle with olive oil and lemon juice.\",\n" +
                "        \"Season with salt and pepper to taste.\",\n" +
                "        \"Toss everything together until well mixed.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 3,\n" +
                "      \"name\": \"Mushroom and Egg Stir-fry\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"mushroom\", \"quantity\": \"6\"},\n" +
                "        {\"name\": \"egg\", \"quantity\": \"3\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"soy sauce\", \"quantity\": \"1 tbsp\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"2 cloves\"},\n" +
                "        {\"name\": \"olive oil\", \"quantity\": \"2 tbsp\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Slice the mushrooms and chop the onion.\",\n" +
                "        \"Heat olive oil in a pan and sauté the onion until translucent.\",\n" +
                "        \"Add the mushrooms and cook until they release their juices and start to brown.\",\n" +
                "        \"Push the vegetables to one side of the pan and scramble the eggs on the other side.\",\n" +
                "        \"Mix everything together and add a splash of soy sauce.\",\n" +
                "        \"Stir well and serve hot.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 4,\n" +
                "      \"name\": \"Chicken and Carrot Stew\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"chicken\", \"quantity\": \"300g\"},\n" +
                "        {\"name\": \"carrot\", \"quantity\": \"2\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"tomato\", \"quantity\": \"2\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"3 cloves\"},\n" +
                "        {\"name\": \"chicken broth\", \"quantity\": \"4 cups\"},\n" +
                "        {\"name\": \"salt\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"pepper\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"thyme\", \"quantity\": \"1 tsp\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"In a large pot, heat some oil and sauté chopped onion and garlic until fragrant.\",\n" +
                "        \"Add diced chicken and cook until browned.\",\n" +
                "        \"Add chopped carrots and tomatoes to the pot.\",\n" +
                "        \"Pour in chicken broth and add thyme, salt, and pepper.\",\n" +
                "        \"Bring to a boil, then reduce heat and let simmer until vegetables are tender and chicken is cooked through.\",\n" +
                "        \"Serve hot.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 5,\n" +
                "      \"name\": \"Carrot and Tomato Soup\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"carrot\", \"quantity\": \"3\"},\n" +
                "        {\"name\": \"tomato\", \"quantity\": \"4\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"2 cloves\"},\n" +
                "        {\"name\": \"chicken broth\", \"quantity\": \"4 cups\"},\n" +
                "        {\"name\": \"olive oil\", \"quantity\": \"2 tbsp\"},\n" +
                "        {\"name\": \"salt\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"pepper\", \"quantity\": \"to taste\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Chop the carrots, tomatoes, and onion.\",\n" +
                "        \"In a pot, heat olive oil and sauté the onion and garlic until fragrant.\",\n" +
                "        \"Add the carrots and cook for a few minutes.\",\n" +
                "        \"Add the tomatoes and chicken broth.\",\n" +
                "        \"Bring to a boil, then reduce heat and let simmer until carrots are tender.\",\n" +
                "        \"Blend the soup until smooth and season with salt and pepper to taste.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 6,\n" +
                "      \"name\": \"Chicken Lettuce Wraps\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"chicken\", \"quantity\": \"200g\"},\n" +
                "        {\"name\": \"lettuce\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1/2\"},\n" +
                "        {\"name\": \"mushroom\", \"quantity\": \"3\"},\n" +
                "        {\"name\": \"soy sauce\", \"quantity\": \"2 tbsp\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"2 cloves\"},\n" +
                "        {\"name\": \"olive oil\", \"quantity\": \"2 tbsp\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Dice the chicken and chop the onion and mushrooms.\",\n" +
                "        \"Heat olive oil in a pan and sauté the onion and garlic until fragrant.\",\n" +
                "        \"Add the chicken and cook until browned.\",\n" +
                "        \"Add the mushrooms and cook until tender.\",\n" +
                "        \"Stir in soy sauce.\",\n" +
                "        \"Spoon the mixture into lettuce leaves and serve.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 7,\n" +
                "      \"name\": \"Tomato Egg Drop Soup\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"tomato\", \"quantity\": \"3\"},\n" +
                "        {\"name\": \"egg\", \"quantity\": \"2\"},\n" +
                "        {\"name\": \"chicken broth\", \"quantity\": \"4 cups\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"2 cloves\"},\n" +
                "        {\"name\": \"cornstarch\", \"quantity\": \"1 tbsp\"},\n" +
                "        {\"name\": \"water\", \"quantity\": \"2 tbsp\"},\n" +
                "        {\"name\": \"salt\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"pepper\", \"quantity\": \"to taste\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Chop the tomatoes and onion.\",\n" +
                "        \"In a pot, heat some oil and sauté the onion and garlic until fragrant.\",\n" +
                "        \"Add the tomatoes and cook until soft.\",\n" +
                "        \"Pour in the chicken broth and bring to a boil.\",\n" +
                "        \"Mix cornstarch with water to make a slurry and add to the soup.\",\n" +
                "        \"Beat the eggs and slowly drizzle them into the boiling soup while stirring to create egg ribbons.\",\n" +
                "        \"Season with salt and pepper to taste.\"\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": 8,\n" +
                "      \"name\": \"Stuffed Mushrooms\",\n" +
                "      \"ingredients\": [\n" +
                "        {\"name\": \"mushroom\", \"quantity\": \"6\"},\n" +
                "        {\"name\": \"onion\", \"quantity\": \"1\"},\n" +
                "        {\"name\": \"garlic\", \"quantity\": \"2 cloves\"},\n" +
                "        {\"name\": \"bread crumbs\", \"quantity\": \"1/2 cup\"},\n" +
                "        {\"name\": \"cheese\", \"quantity\": \"1/2 cup\"},\n" +
                "        {\"name\": \"olive oil\", \"quantity\": \"2 tbsp\"},\n" +
                "        {\"name\": \"salt\", \"quantity\": \"to taste\"},\n" +
                "        {\"name\": \"pepper\", \"quantity\": \"to taste\"}\n" +
                "      ],\n" +
                "      \"instructions\": [\n" +
                "        \"Preheat the oven to 180°C (350°F).\",\n" +
                "        \"Remove the stems from the mushrooms and chop them finely.\",\n" +
                "        \"In a pan, heat olive oil and sauté the chopped mushroom stems, onion, and garlic until tender.\",\n" +
                "        \"Mix in bread crumbs and cheese, and season with salt and pepper.\",\n" +
                "        \"Stuff the mushroom caps with the mixture.\",\n" +
                "        \"Place the stuffed mushrooms on a baking sheet and bake for 15-20 minutes or until the mushrooms are tender and the tops are golden.\"\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n"

        val recipesArray = Gson().fromJson(recipes, Array<Recipe>::class.java)
        val foundRecipes: MutableList<String> = mutableListOf()

        // Iterate over each recipe
        for (recipe in recipesArray) {
            val recipeIngredients = recipe.ingredients.map { it.name }

            //Check if any ingredient in ingredientList is present in this recipe
            if (ingredientList.any { recipeIngredients.contains(it) }) {
                println("Recipe '${recipe.name}' contains all ingredients: $ingredientList")
                val joinedInstructions = recipe.instructions.stream().collect(Collectors.joining("\n"));
                foundRecipes.add(recipe.name + "\n\n" + joinedInstructions)
            }
        }

        return foundRecipes
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == ObjectDetectorHelper.GPU_ERROR) {
                // Error handle
            }
        }
    }
}
