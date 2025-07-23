package com.enaboapps.switchify.service.menu.menus.ai

import android.graphics.Bitmap
import android.util.Log
import com.enaboapps.switchify.service.ai.FirebaseAIManager
import com.enaboapps.switchify.service.techniques.nodes.Node
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONObject
import org.json.JSONArray

/**
 * AINodeRanker is responsible for analyzing and ranking nodes based on their importance
 * and relevance for user interaction using Firebase AI Logic.
 */
object AINodeRanker {
    private const val TAG = "AINodeRanker"
    private const val MAX_NODES_TO_ANALYZE = 30
    private const val MAX_RANKED_RESULTS = 10

    /**
     * Data classes for JSON parsing
     */
    private data class AIRankingResponse(
        val rankings: List<RankingItem>
    )
    
    private data class RankingItem(
        val index: Int,
        val score: Int,
        val reason: String
    )

    /**
     * Data class representing a ranked node with its importance score
     */
    data class RankedNode(
        val node: Node,
        val score: Int,
        val reasoning: String = ""
    )

    /**
     * Analyzes and ranks nodes using AI based on their content descriptions
     * @param nodes The list of nodes to analyze and rank
     * @return List of ranked nodes sorted by importance (highest first)
     */
    suspend fun rankNodes(nodes: List<Node>): List<RankedNode> {
        if (nodes.isEmpty()) {
            Log.d(TAG, "No nodes to rank")
            return emptyList()
        }

        // Filter and limit nodes for analysis
        val nodesToAnalyze = nodes
            .filter { it.getContentDescription().isNotBlank() }
            .take(MAX_NODES_TO_ANALYZE)

        if (nodesToAnalyze.isEmpty()) {
            Log.d(TAG, "No nodes with content descriptions found")
            return emptyList()
        }

        Log.d(TAG, "Analyzing ${nodesToAnalyze.size} nodes with AI")

        return try {
            val aiManager = FirebaseAIManager()
            
            if (!aiManager.isAvailable()) {
                Log.w(TAG, "Firebase AI not available, using fallback ranking")
                return fallbackRanking(nodesToAnalyze)
            }

            val rankedNodes = analyzeNodesWithAI(aiManager, nodesToAnalyze)
            Log.d(TAG, "AI analysis complete, ranked ${rankedNodes.size} nodes")
            rankedNodes
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during AI ranking, falling back to basic ranking", e)
            fallbackRanking(nodesToAnalyze)
        }
    }

    /**
     * Analyzes nodes using Firebase AI to determine importance ranking
     */
    private suspend fun analyzeNodesWithAI(
        aiManager: FirebaseAIManager,
        nodes: List<Node>
    ): List<RankedNode> {
        val nodeDescriptions = nodes.mapIndexed { index, node ->
            val elementType = node.getElementType() ?: "Unknown"
            "${index + 1}. [$elementType] ${node.getContentDescription()}"
        }.joinToString("\n")

        val prompt = """
            You are helping users with motor disabilities who use adaptive switches to control their Android device. These users navigate by selecting from a menu of the most important interactive elements on screen.
            
            Context: This is an accessibility app called Switchify that helps users with limited mobility control their device using switches. Users cannot easily scan through many elements, so they need the most important actionable items prioritized for them.
            
            User Goals:
            - Complete common tasks (submit forms, make purchases, send messages)  
            - Navigate efficiently through apps
            - Access primary actions without scrolling through many options
            - Interact with elements that will advance their workflow
            
            UI Elements to Analyze:
            Each element is shown as: [ElementType] Description
            $nodeDescriptions

            Please rank each element by how valuable it would be for a user with motor disabilities who wants to accomplish their primary task on this screen.

            Scoring System:
            Assign a numerical score based on importance for users with motor disabilities. Use any number that reflects the element's value - higher numbers = more important.

            Element Type Priorities:
            - Button, ImageButton: Usually high priority actions (70-100+)
            - EditText, AutoCompleteTextView: Essential for data input (60-90)
            - Spinner, RadioButton, CheckBox: Important selections (50-80)
            - TextView: Variable priority based on content (10-60)
            - ImageView: Usually decorative unless clickable (5-40)
            - Unknown: Evaluate based on description (10-70)

            Content-Based Priorities:
            - Primary actions (Submit, Send, Buy, Save, Login): Very high scores (80-100+)
            - Input fields, search boxes: High scores (60-80)
            - Important navigation: High scores (60-80) 
            - Secondary navigation, settings: Medium scores (30-60)
            - Informational text, links: Lower scores (10-30)
            - Decorative elements: Very low scores (1-10)

            Consider:
            - Would this element help complete a primary task?
            - Is this a common action users perform?
            - Does this advance the user's workflow?
            - Is this element essential for app navigation?

            Respond with ONLY valid JSON in this exact format:
            {
              "rankings": [
                {"index": 1, "score": 95, "reason": "[Button] Submit - completes main task efficiently"},
                {"index": 2, "score": 70, "reason": "[EditText] Email input field - required for account creation"},
                {"index": 3, "score": 15, "reason": "[TextView] Terms of service link - secondary information"},
                {"index": 4, "score": 5, "reason": "[ImageView] Company logo - decorative only"}
              ]
            }
            
            IMPORTANT: Return ONLY the JSON object, no additional text or explanations.
        """.trimIndent()

        val response = aiManager.generateText(prompt)

        return if (response.isSuccess && !response.content.isNullOrBlank()) {
            parseAIResponse(response.content, nodes)
        } else {
            Log.w(TAG, "AI analysis failed, using fallback ranking")
            fallbackRanking(nodes)
        }
    }

    /**
     * Parses AI response and creates ranked node list
     */
    private fun parseAIResponse(aiResponse: String, originalNodes: List<Node>): List<RankedNode> {
        val rankedNodes = mutableListOf<RankedNode>()
        
        try {
            // First try to validate and parse as JSON
            if (!isValidJson(aiResponse)) {
                Log.w(TAG, "AI response is not valid JSON, attempting fallback parsing")
                return parseTextResponse(aiResponse, originalNodes)
            }
            
            val gson = Gson()
            val rankingResponse = gson.fromJson(aiResponse, AIRankingResponse::class.java)
            
            rankingResponse.rankings.forEach { ranking ->
                val nodeIndex = ranking.index - 1 // Convert from 1-based to 0-based indexing
                
                if (nodeIndex >= 0 && nodeIndex < originalNodes.size) {
                    rankedNodes.add(
                        RankedNode(
                            node = originalNodes[nodeIndex],
                            score = ranking.score.coerceAtLeast(1),
                            reasoning = ranking.reason
                        )
                    )
                } else {
                    Log.w(TAG, "Invalid node index in AI response: ${ranking.index}")
                }
            }
            
            Log.d(TAG, "Successfully parsed ${rankedNodes.size} rankings from JSON response")
            
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parsing failed, attempting fallback parsing", e)
            return parseTextResponse(aiResponse, originalNodes)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AI response", e)
            return fallbackRanking(originalNodes)
        }
        
        if (rankedNodes.isEmpty()) {
            Log.w(TAG, "No valid rankings parsed, using fallback")
            return fallbackRanking(originalNodes)
        }

        // Sort by score (highest first) and limit results
        return rankedNodes
            .sortedByDescending { it.score }
            .take(MAX_RANKED_RESULTS)
    }
    
    /**
     * Validates if a string is valid JSON
     */
    private fun isValidJson(jsonString: String): Boolean {
        return try {
            JSONObject(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Fallback parser for text-based responses (original format)
     */
    private fun parseTextResponse(aiResponse: String, originalNodes: List<Node>): List<RankedNode> {
        val rankedNodes = mutableListOf<RankedNode>()
        
        try {
            aiResponse.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty() && trimmedLine.contains(":")) {
                    val parts = trimmedLine.split(":", limit = 2)
                    if (parts.size == 2) {
                        val nodeIndex = parts[0].trim().toIntOrNull()?.minus(1)
                        val scoreAndReason = parts[1].trim()
                        
                        if (nodeIndex != null && nodeIndex >= 0 && nodeIndex < originalNodes.size) {
                            val scoreParts = scoreAndReason.split("-", limit = 2)
                            val score = scoreParts[0].trim().toIntOrNull() ?: 5
                            val reasoning = if (scoreParts.size > 1) scoreParts[1].trim() else ""
                            
                            rankedNodes.add(
                                RankedNode(
                                    node = originalNodes[nodeIndex],
                                    score = score.coerceAtLeast(1),
                                    reasoning = reasoning
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing text response", e)
            return fallbackRanking(originalNodes)
        }
        
        return rankedNodes.sortedByDescending { it.score }.take(MAX_RANKED_RESULTS)
    }

    /**
     * Fallback ranking system when AI is unavailable
     * Uses element types and heuristics to rank nodes by importance
     */
    private fun fallbackRanking(nodes: List<Node>): List<RankedNode> {
        return nodes.map { node ->
            val score = calculateFallbackScore(node)
            val reasoning = getFallbackReasoning(node, score)
            
            RankedNode(node = node, score = score, reasoning = reasoning)
        }
        .sortedByDescending { it.score }
        .take(MAX_RANKED_RESULTS)
    }

    /**
     * Calculates fallback score based on element type and content description
     */
    private fun calculateFallbackScore(node: Node): Int {
        val elementType = node.getElementType()
        val description = node.getContentDescription().lowercase()
        
        // Primary scoring based on actual element type
        val typeScore = when (elementType) {
            "Button", "ImageButton" -> 9
            "EditText", "AutoCompleteTextView" -> 8
            "Spinner", "RadioButton", "CheckBox" -> 7
            "ToggleButton", "Switch" -> 6
            "TextView" -> if (description.contains("link") || description.contains("clickable")) 5 else 3
            "ImageView" -> if (description.contains("button") || description.contains("clickable")) 4 else 2
            else -> null // Use content-based fallback
        }
        
        // Return element type score if available, otherwise use legacy keyword matching
        return typeScore ?: calculateLegacyScore(description)
    }
    
    /**
     * Legacy content description-based scoring for unknown element types
     */
    private fun calculateLegacyScore(description: String): Int {
        return when {
            // High priority interactive elements
            description.contains("button") || 
            description.contains("tap") ||
            description.contains("click") ||
            description.contains("submit") ||
            description.contains("send") -> 9

            // Input fields
            description.contains("field") ||
            description.contains("input") ||
            description.contains("search") ||
            description.contains("edit") -> 8

            // Navigation elements
            description.contains("menu") ||
            description.contains("nav") ||
            description.contains("tab") ||
            description.contains("link") -> 7

            // Actions
            description.contains("add") ||
            description.contains("create") ||
            description.contains("save") ||
            description.contains("delete") -> 6

            // Content with some interaction
            description.contains("select") ||
            description.contains("choose") -> 5

            // Basic content
            description.contains("text") ||
            description.contains("label") -> 3

            // Decorative elements
            description.contains("image") ||
            description.contains("icon") -> 2

            else -> 4 // Default medium priority
        }
    }

    /**
     * Provides reasoning for fallback scoring based on element type and score
     */
    private fun getFallbackReasoning(node: Node, score: Int): String {
        val elementType = node.getElementType()
        
        return when {
            elementType != null -> when (elementType) {
                "Button", "ImageButton" -> "$elementType - primary action element"
                "EditText", "AutoCompleteTextView" -> "$elementType - input field for data entry"
                "Spinner", "RadioButton", "CheckBox" -> "$elementType - selection control"
                "ToggleButton", "Switch" -> "$elementType - toggle control"
                "TextView" -> "$elementType - text display element"
                "ImageView" -> "$elementType - visual content"
                else -> "$elementType - UI element"
            }
            else -> when (score) {
                9 -> "High priority action element"
                8 -> "Interactive input element"
                7 -> "Navigation element"
                6 -> "Action element"
                5 -> "Selectable content"
                3 -> "Text content"
                2 -> "Decorative element"
                else -> "General UI element"
            }
        }
    }

    /**
     * Enhanced node ranking with visual context from screenshot
     * @param nodes The list of nodes to analyze and rank
     * @param screenshot Screenshot of the current screen for visual context
     * @param additionalContext Optional additional context about the current screen
     * @return List of ranked nodes sorted by importance (highest first)
     */
    suspend fun rankNodesWithVisualContext(
        nodes: List<Node>,
        screenshot: Bitmap,
        additionalContext: String? = null
    ): List<RankedNode> {
        if (nodes.isEmpty()) {
            Log.d(TAG, "No nodes to rank")
            return emptyList()
        }

        // Filter and limit nodes for analysis
        val nodesToAnalyze = nodes
            .filter { it.getContentDescription().isNotBlank() }
            .take(MAX_NODES_TO_ANALYZE)

        if (nodesToAnalyze.isEmpty()) {
            Log.d(TAG, "No nodes with content descriptions found")
            return emptyList()
        }

        Log.d(TAG, "Analyzing ${nodesToAnalyze.size} nodes with AI and visual context")

        return try {
            val aiManager = FirebaseAIManager()
            
            if (!aiManager.isAvailable()) {
                Log.w(TAG, "Firebase AI not available, using fallback ranking")
                return fallbackRanking(nodesToAnalyze)
            }

            val rankedNodes = analyzeNodesWithVisualAI(aiManager, nodesToAnalyze, screenshot, additionalContext)
            Log.d(TAG, "AI visual analysis complete, ranked ${rankedNodes.size} nodes")
            rankedNodes
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during AI visual ranking, falling back to basic ranking", e)
            fallbackRanking(nodesToAnalyze)
        }
    }

    /**
     * Analyzes nodes using Firebase AI with visual context to determine importance ranking
     */
    private suspend fun analyzeNodesWithVisualAI(
        aiManager: FirebaseAIManager,
        nodes: List<Node>,
        screenshot: Bitmap,
        additionalContext: String?
    ): List<RankedNode> {
        val nodeDescriptions = nodes.mapIndexed { index, node ->
            val elementType = node.getElementType() ?: "Unknown"
            "${index + 1}. [$elementType] ${node.getContentDescription()}"
        }.joinToString("\n")

        val contextualInfo = if (additionalContext?.isNotBlank() == true) {
            "\n\nAdditional Context: $additionalContext"
        } else ""

        val prompt = """
            You are helping users with motor disabilities who use adaptive switches to control their Android device. These users navigate by selecting from a menu of the most important interactive elements on screen.
            
            I'm showing you a screenshot of the current screen along with accessibility information about interactive elements. Your task is to rank these elements by importance for users with motor disabilities.
            
            Context: This is an accessibility app called Switchify that helps users with limited mobility control their device using switches. Users cannot easily scan through many elements, so they need the most important actionable items prioritized for them.
            
            User Goals:
            - Complete common tasks (submit forms, make purchases, send messages)  
            - Navigate efficiently through apps
            - Access primary actions without scrolling through many options
            - Interact with elements that will advance their workflow$contextualInfo
            
            UI Elements to Analyze:
            Each element is shown as: [ElementType] Description
            $nodeDescriptions

            Please analyze the screenshot to understand the visual context and layout, then rank each element by how valuable it would be for a user with motor disabilities who wants to accomplish their primary task on this screen.

            Consider both the visual layout and the element descriptions. Look for:
            - Primary call-to-action buttons that are visually prominent
            - Input fields that are part of main workflows
            - Navigation elements that lead to important sections
            - Elements that are visually emphasized or centrally placed
            - The overall purpose and context of the screen

            Scoring System:
            Assign a numerical score based on importance for users with motor disabilities. Higher numbers = more important.

            Visual Context Priorities:
            - Visually prominent primary actions: Very high scores (80-100+)
            - Main content area interactions: High scores (60-90)
            - Secondary actions that are still visible: Medium scores (40-70)
            - Background or less prominent elements: Lower scores (10-40)
            - Decorative or peripheral elements: Very low scores (1-15)

            Element Type Priorities:
            - Button, ImageButton: Usually high priority actions (70-100+)
            - EditText, AutoCompleteTextView: Essential for data input (60-90)
            - Spinner, RadioButton, CheckBox: Important selections (50-80)
            - TextView: Variable priority based on content and visual context (10-60)
            - ImageView: Usually decorative unless clickable (5-40)
            - Unknown: Evaluate based on description and visual context (10-70)

            Respond in this exact format for each element:
            [number]: [score] - [brief reason focusing on user benefit and visual context]

            Example:
            1: 95 - [Button] Submit button - visually prominent primary action in center
            2: 85 - [EditText] Email field - required input prominently displayed
            3: 25 - [TextView] Footer link - small text at bottom of screen
            4: 8 - [ImageView] Background decoration - purely decorative element
        """.trimIndent()

        val response = aiManager.analyzeScreenshot(screenshot, prompt)

        return if (response.isSuccess && !response.content.isNullOrBlank()) {
            parseAIResponse(response.content, nodes)
        } else {
            Log.w(TAG, "AI visual analysis failed, using fallback ranking")
            fallbackRanking(nodes)
        }
    }
}