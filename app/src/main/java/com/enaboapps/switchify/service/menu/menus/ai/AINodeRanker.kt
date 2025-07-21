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
            Analyze these UI elements and rank them by importance for accessibility users who want to interact with the most relevant elements on screen.

            UI Elements:
            $nodeDescriptions

            Requirements:
            - Score each element from 1-10 (10 = highest priority)
            - Prioritize: buttons, links, input fields, navigation elements
            - Lower priority: decorative text, images, labels
            - Consider user intent to complete tasks or navigate

            Respond in this exact format for each element:
            [number]: [score] - [brief reason]

            Example:
            1: 9 - Submit button for main action
            2: 3 - Decorative image, low interaction value
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
                                    score = score.coerceIn(1, 10),
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