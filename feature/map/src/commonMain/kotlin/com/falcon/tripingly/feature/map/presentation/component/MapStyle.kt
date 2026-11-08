package com.falcon.tripingly.feature.map.presentation.component

/**
 * The map's look: "Deep graphite" (G2 on the map styles canvas, Shahin 2026-11-08), Google's monochrome made ours.
 * Light: pale graphite land, white roads with graphite edges, grey highways, very light blue-grey water. Dark: deep
 * graphite land, roads like pencil strokes, black water. Google's own points of interest (shops, cafés, sights) and
 * their icons are hidden: places on our map come from our server only. The same JSON goes to Android
 * (`MapStyleOptions`) and iOS (`GMSMapStyle`).
 */
internal const val LIGHT_MAP_STYLE = """
[
  {"elementType": "geometry", "stylers": [{"color": "#ecedee"}]},
  {"elementType": "labels.icon", "stylers": [{"visibility": "off"}]},
  {"elementType": "labels.text.fill", "stylers": [{"color": "#50565c"}]},
  {"elementType": "labels.text.stroke", "stylers": [{"color": "#ecedee"}]},
  {"featureType": "administrative", "elementType": "labels.text.fill", "stylers": [{"color": "#7e848a"}]},
  {"featureType": "administrative.land_parcel", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi", "elementType": "geometry", "stylers": [{"color": "#e1e3e5"}]},
  {"featureType": "poi", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#e1e3e5"}]},
  {"featureType": "road", "elementType": "geometry.fill", "stylers": [{"color": "#ffffff"}]},
  {"featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#bcc1c5"}]},
  {"featureType": "road.arterial", "elementType": "geometry.fill", "stylers": [{"color": "#ffffff"}]},
  {"featureType": "road.arterial", "elementType": "labels.text.fill", "stylers": [{"color": "#7e848a"}]},
  {"featureType": "road.highway", "elementType": "geometry.fill", "stylers": [{"color": "#a9afb4"}]},
  {"featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#bcc1c5"}]},
  {"featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [{"color": "#50565c"}]},
  {"featureType": "road.local", "elementType": "labels.text.fill", "stylers": [{"color": "#7e848a"}]},
  {"featureType": "transit.line", "elementType": "geometry", "stylers": [{"color": "#b1b6ba"}]},
  {"featureType": "transit.station", "elementType": "geometry", "stylers": [{"color": "#e1e3e5"}]},
  {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#dae3e9"}]},
  {"featureType": "water", "elementType": "labels.text.fill", "stylers": [{"color": "#8194a1"}]}
]
"""

internal const val DARK_MAP_STYLE = """
[
  {"elementType": "geometry", "stylers": [{"color": "#1a1c1e"}]},
  {"elementType": "labels.icon", "stylers": [{"visibility": "off"}]},
  {"elementType": "labels.text.fill", "stylers": [{"color": "#a3a9af"}]},
  {"elementType": "labels.text.stroke", "stylers": [{"color": "#1a1c1e"}]},
  {"featureType": "administrative", "elementType": "labels.text.fill", "stylers": [{"color": "#80868c"}]},
  {"featureType": "administrative.land_parcel", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi", "elementType": "geometry", "stylers": [{"color": "#16181a"}]},
  {"featureType": "poi", "elementType": "labels", "stylers": [{"visibility": "off"}]},
  {"featureType": "poi.park", "elementType": "geometry", "stylers": [{"color": "#16181a"}]},
  {"featureType": "road", "elementType": "geometry.fill", "stylers": [{"color": "#3b3f43"}]},
  {"featureType": "road", "elementType": "geometry.stroke", "stylers": [{"color": "#25282b"}]},
  {"featureType": "road.arterial", "elementType": "geometry.fill", "stylers": [{"color": "#4c5055"}]},
  {"featureType": "road.arterial", "elementType": "labels.text.fill", "stylers": [{"color": "#80868c"}]},
  {"featureType": "road.highway", "elementType": "geometry.fill", "stylers": [{"color": "#62686d"}]},
  {"featureType": "road.highway", "elementType": "geometry.stroke", "stylers": [{"color": "#25282b"}]},
  {"featureType": "road.highway", "elementType": "labels.text.fill", "stylers": [{"color": "#a3a9af"}]},
  {"featureType": "road.local", "elementType": "labels.text.fill", "stylers": [{"color": "#80868c"}]},
  {"featureType": "transit.line", "elementType": "geometry", "stylers": [{"color": "#383b3f"}]},
  {"featureType": "transit.station", "elementType": "geometry", "stylers": [{"color": "#16181a"}]},
  {"featureType": "water", "elementType": "geometry", "stylers": [{"color": "#000000"}]},
  {"featureType": "water", "elementType": "labels.text.fill", "stylers": [{"color": "#34383c"}]}
]
"""
