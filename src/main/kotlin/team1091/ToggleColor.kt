package team1091

import java.awt.event.KeyEvent
import java.awt.event.KeyListener

class ToggleColor : KeyListener {
    override fun keyTyped(e: KeyEvent?) {

    }

    override fun keyPressed(e: KeyEvent?) {
        if (e == null) {
            return
        }

        if (e.keyCode == KeyEvent.VK_B) {
            teamColor = "blue"
        } else if (e.keyCode == KeyEvent.VK_R) {
            teamColor = "red"
        }
    }

    override fun keyReleased(e: KeyEvent?) {

    }
}
