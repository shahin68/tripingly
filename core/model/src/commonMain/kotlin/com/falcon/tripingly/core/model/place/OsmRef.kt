package com.falcon.tripingly.core.model.place

/** An OpenStreetMap feature, so the server links stops at the same address to one place. */
data class OsmRef(val type: OsmType, val id: String)

enum class OsmType { Node, Way, Relation }
