package eu.slomkowski.octoglow.view

interface FrontDisplayView {
    fun redrawDisplay()
    
    fun poolStateUpdate(): Boolean
}