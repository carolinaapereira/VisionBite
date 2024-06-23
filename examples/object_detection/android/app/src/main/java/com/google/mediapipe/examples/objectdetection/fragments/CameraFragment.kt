/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.objectdetection.fragments

import IngredientAdapter
import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar.LayoutParams
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.mediapipe.examples.objectdetection.MainViewModel
import com.google.mediapipe.examples.objectdetection.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.R
import com.google.mediapipe.examples.objectdetection.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

data class Ingredient(val name: String, val quantity: String)
data class Recipe(val id: Int, val name: String, val ingredients: MutableList<Ingredient>, val instructions: MutableList<String>)

class CameraFragment : Fragment(), ObjectDetectorHelper.DetectorListener {

    private val TAG = "ObjectDetection"

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var objectDetectorHelper: ObjectDetectorHelper
    private lateinit var linearLayout: LinearLayoutCompat
    // private var ingredientList: MutableList<String> = mutableListOf()
    private val ingredientList = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var adapter: IngredientAdapter
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            )
                .navigate(CameraFragmentDirections.actionCameraToPermissions())
        }

        backgroundExecutor.execute {
            if (objectDetectorHelper.isClosed()) {
                objectDetectorHelper.setupObjectDetector()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // save ObjectDetector settings
        if(this::objectDetectorHelper.isInitialized) {
            viewModel.setModel(objectDetectorHelper.currentModel)
            viewModel.setDelegate(objectDetectorHelper.currentDelegate)
            viewModel.setThreshold(objectDetectorHelper.threshold)
            viewModel.setMaxResults(objectDetectorHelper.maxResults)
            // Close the object detector and release resources
            backgroundExecutor.execute { objectDetectorHelper.clearObjectDetector() }
        }

    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor.
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE,
            TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        linearLayout = fragmentCameraBinding.root.findViewById(R.id.linear_layout)

        val view = inflater.inflate(R.layout.fragment_my, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)

        adapter = IngredientAdapter(ingredientList) { position ->
            removeIngredient(position)
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Create the ObjectDetectionHelper that will handle the inference
        backgroundExecutor.execute {
            objectDetectorHelper =
                ObjectDetectorHelper(
                    context = requireContext(),
                    threshold = viewModel.currentThreshold,
                    currentDelegate = viewModel.currentDelegate,
                    currentModel = viewModel.currentModel,
                    maxResults = viewModel.currentMaxResults,
                    objectDetectorListener = this,
                    runningMode = RunningMode.LIVE_STREAM
                )

            // Wait for the views to be properly laid out
            fragmentCameraBinding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
        fragmentCameraBinding.overlay.setRunningMode(RunningMode.LIVE_STREAM)
    }

    private fun initBottomSheetControls() {
        // Init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.currentThreshold)

        // When clicked, lower detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (objectDetectorHelper.threshold >= 0.1) {
                objectDetectorHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (objectDetectorHelper.threshold <= 0.8) {
                objectDetectorHelper.threshold += 0.1f
                updateControlsUi()
            }
        }
    }

    // Update the values displayed in the bottom sheet. Reset detector.
    private fun updateControlsUi() {
        //fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text = objectDetectorHelper.maxResults.toString()
        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text = String.format("%.2f", objectDetectorHelper.threshold)

        backgroundExecutor.execute {
            objectDetectorHelper.clearObjectDetector()
            objectDetectorHelper.setupObjectDetector()
        }

        fragmentCameraBinding.overlay.clear()
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider =
            cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(
                        backgroundExecutor,
                        objectDetectorHelper::detectLivestreamFrame
                    )
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after objects have been detected. Extracts original image height/width
    // to scale and place bounding boxes properly through OverlayView
    override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // Pass necessary information to OverlayView for drawing on the canvas
                val detectionResult = resultBundle.results[0]
                if (isAdded) {
                    fragmentCameraBinding.overlay.setResults(
                        detectionResult,
                        resultBundle.inputImageHeight,
                        resultBundle.inputImageWidth,
                        resultBundle.inputImageRotation
                    )

                    var label = resultBundle.results[0]?.detections()!![0].categories()[0].categoryName();
                    if(!ingredientList.contains(label))
                    {
                        addIngredient(label)
                        adapter.notifyDataSetChanged()
                    }

                    // Force a redraw
                    fragmentCameraBinding.overlay.invalidate()
                }
            }
        }
    }

    private fun addIngredient(label: String)
    {
        val newLayout = LinearLayout(activity)
        newLayout.gravity = RelativeLayout.ALIGN_PARENT_START

        val newText = TextView(activity);
        val newDeleteButton = ImageButton(activity)

        // Customize text
        newText.text = label;
        newText.textSize = 20F;
        newText.setTextColor(resources.getColor(R.color.bottom_sheet_text_color));

        // Customize delete button
        newDeleteButton.setImageResource(R.drawable.ic_delete)
        newDeleteButton.setOnClickListener {
            removeIngredient(0)
        }
        newDeleteButton.contentDescription = "Delete"

        // Create layout settings
        val textParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.topMargin = 16;
        newText.layoutParams = textParams;

        val deleteBtnParams = LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        deleteBtnParams.topMargin = 16;
        deleteBtnParams.gravity = RelativeLayout.ALIGN_PARENT_END
        newDeleteButton.layoutParams = deleteBtnParams

        // Add to view
        newLayout.addView(newText);
        newLayout.addView(newDeleteButton);
        synchronized(ingredientList) {
            linearLayout.addView(newLayout);
        }
        println(ingredientList)
        ingredientList.add(label);
    }

    private fun removeIngredient(position: Int) {
        synchronized(ingredientList) {
            ingredientList.removeLast()
        }
        adapter.notifyItemRemoved(position)
        fragmentCameraBinding.overlay.invalidate()
    }

    fun AssetManager.readFile(fileName: String){
        open(fileName)
            .bufferedReader()
            .use { it.readText() }
    }

    fun searchRecipe(): MutableList<String> {
        //val file = File("assets/recipes.json").absoluteFile.inputStream()
        //val recipes = file.bufferedReader().use { it.readText() }
        synchronized(ingredientList) {
            println(ingredientList)
        }
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
        val ingredientList = arrayListOf("laptop", "mouse")

        // Iterate over each recipe
        for (recipe in recipesArray) {
            val recipeIngredients = recipe.ingredients.map { it.name }

            //Check if all ingredients in ingredientList are present in this recipe
            if (recipeIngredients.all { ingredientList.contains(it) }) {
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