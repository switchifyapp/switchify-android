package com.enaboapps.switchify.service.menu.menus.ai

import android.util.Log
import com.enaboapps.switchify.service.ai.FirebaseAIManager
import com.enaboapps.switchify.service.techniques.nodes.Node

/**
 * AINodeRanker is responsible for analyzing and ranking nodes based on their importance
 * and relevance for user interaction using Firebase AI Logic.
 */
object AINodeRanker {
    private const val TAG = "AINodeRanker"
    private const val MAX_NODES_TO_ANALYZE = 30
    private const val MAX_RANKED_RESULTS = 10

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
            "${index + 1}. ${node.getContentDescription()}"
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
            $nodeDescriptions

            Please rank each element by how valuable it would be for a user with motor disabilities who wants to accomplish their primary task on this screen.

            Scoring System:
            Assign a numerical score based on importance for users with motor disabilities. Use any number that reflects the element's value - higher numbers = more important.

            Element Priority Guidelines:
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

            Respond in this exact format for each element:
            [number]: [score] - [brief reason focusing on user benefit]

            Example:
            1: 95 - Submit button completes main task efficiently
            2: 70 - Email input field required for account creation
            3: 5 - Company logo, decorative only
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
                                    score = score.coerceAtLeast(1), // Allow any positive score
                                    reasoning = reasoning
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AI response", e)
            return fallbackRanking(originalNodes)
        }

        // Sort by score (highest first) and limit results
        return rankedNodes
            .sortedByDescending { it.score }
            .take(MAX_RANKED_RESULTS)
    }

    /**
     * Fallback ranking system when AI is unavailable
     * Uses heuristics to rank nodes by importance
     */
    private fun fallbackRanking(nodes: List<Node>): List<RankedNode> {
        return nodes.map { node ->
            val description = node.getContentDescription().lowercase()
            val score = calculateFallbackScore(description)
            val reasoning = getFallbackReasoning(description, score)
            
            RankedNode(node = node, score = score, reasoning = reasoning)
        }
        .sortedByDescending { it.score }
        .take(MAX_RANKED_RESULTS)
    }

    /**
     * Calculates fallback score based on content description keywords
     */
    private fun calculateFallbackScore(description: String): Int {
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
     * Provides reasoning for fallback scoring
     */
    private fun getFallbackReasoning(description: String, score: Int): String {
        return when (score) {
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